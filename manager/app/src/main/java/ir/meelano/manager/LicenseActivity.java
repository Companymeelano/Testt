package ir.meelano.manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.core.Brand;
import ir.meelano.manager.core.DeviceId;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.SmartLink;
import ir.meelano.manager.ui.LinkPanel;
import ir.meelano.manager.core.Net;
import ir.meelano.manager.core.SmsIo;
import ir.meelano.manager.core.SmsReceiver;
import ir.meelano.manager.core.Tamper;
import ir.meelano.manager.core.Usage;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.ui.Card3D;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;
import ir.meelano.licensing.License;

/**
 * Offline license gate, v23: step ۱ (device code + distributor name + mobile →
 * direct SMS + 3D wait for the seller's reply), step ۲ (the customer's own
 * server IP + DB name, saved once and hidden forever, reported to the seller),
 * remote revoke/block enforcement, and the developer signature on page one.
 * ۲) smart receive (paste/scan anything — pack or connection line — it
 * detects and applies it). No Wi-Fi needed: the pack activates on any network
 * (even fully offline); a connection line is tested live and, while the server
 * is unreachable, staged and retried automatically. Only the name + contact
 * number are asked; everything else happens imperceptibly (auto SMS, auto
 * clipboard pull, auto retry).
 * SMS auto-apply still works underneath, so most users just wait.
 * Blocks MainActivity until {@link LicenseStore#unlocked}.
 */
public class LicenseActivity extends Activity {

    private static final int REQ_RECV = 906;
    private static final int REQ_SCAN = 807;
    private static final int REQ_CAM = 809;

    private Kit kit;
    private Settings settings;
    private Repo repo;
    private String device = "";

    private TextView netDot;
    private TextView netTxt;
    private TextView recvHint;

    private String pendingSmsPhone = "";
    private String pendingSmsText = "";
    private String pendingKind = "";
    private AlertDialog waitDlg;
    private TextView waitStage;
    private TextView waitTime;
    private long waitStart;
    private final Handler waitHandler = new Handler(Looper.getMainLooper());
    private final Runnable waitTick = new Runnable() {
        @Override
        public void run() {
            tickWait();
        }
    };
    private boolean wantScan;
    private long lastCardTry;
    private BroadcastReceiver smsPing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Theme.init(this);
        try {
            getWindow().setStatusBarColor(Theme.BG);
            getWindow().setNavigationBarColor(Theme.BG);
        } catch (Exception ignored) { }
        kit = new Kit(this);
        settings = new Settings(this);
        repo = new Repo(this, settings);
        try {
            device = DeviceId.code(this);
        } catch (Exception e) {
            device = "";
        }
        buildUi();
        ensureRecvPerm();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The network may have changed while away (or the user returned from the
        // dialer/browser) — refresh the network row in place (no rebuild, so
        // typed text in the form fields is never lost).
        updateNetRow();
        try {
            if (smsPing == null) {
                smsPing = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent i) {
                        consumeSms();
                    }
                };
            }
            IntentFilter ff = new IntentFilter(SmsReceiver.ACTION_INTERNAL);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(smsPing, ff, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(smsPing, ff);
        } catch (Exception ignored) { }
        // A reply SMS may have landed while we were away — apply it instantly.
        consumeSms();
    }

    @Override
    protected void onPause() {
        try {
            unregisterReceiver(smsPing);
        } catch (Exception ignored) { }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        try {
            if (req == SmsIo.REQ_SEND) {
                boolean g = grants != null && grants.length > 0
                        && grants[0] == PackageManager.PERMISSION_GRANTED;
                if (g && pendingSmsText != null && !pendingSmsText.isEmpty()) {
                    doSmsSend(pendingSmsPhone, pendingSmsText);
                } else if (!g) {
                    if (SmsIo.permaDenied(LicenseActivity.this)) permSettingsDialog();
                    else kit.toast("بدون دسترسی پیامک، شماره فروشنده را خالی بگذارید و دوباره بزنید");
                }
                pendingSmsPhone = "";
                pendingSmsText = "";
            } else if (req == REQ_RECV) {
                updateRecvHint();
            } else if (req == REQ_CAM) {
                boolean g = grants != null && grants.length > 0
                        && grants[0] == PackageManager.PERMISSION_GRANTED;
                boolean w = wantScan;
                wantScan = false;
                if (g && w) launchScan();
                else if (!g) kit.toast("بدون دسترسی دوربین، کد را دستی وارد کنید");
            }
        } catch (Exception ignored) { }
    }

    // ---------- UI ----------

    private void buildUi() {
        LinearLayout box = kit.v();
        box.setBackgroundColor(Theme.BG);
        int pad = Theme.dp(16);
        box.setPadding(pad, pad, pad, pad);

        LicenseStore.Status s = LicenseStore.check(this);
        if ("blocked".equals(s.reason)) {
            buildBlockedUi(s.fa);
            return;
        }
        boolean conn = connConfigured();
        int done = (s.ok ? 1 : 0) + (conn ? 1 : 0);
        String pill = done >= 2 ? "✓ آماده ورود"
                : ("قدم " + Money.fa(String.valueOf(done + 1)) + " از ۲");
        Card3D.mount(box, Card3D.hero(this, kit, R.drawable.lic_shield, Theme.GOLD,
                "میلانو",
                "۲ قدم ساده • همه‌چیز در یک قاب",
                pill, done >= 2 ? Theme.SUCCESS : Theme.WARNING));

        netDot = null;
        netTxt = null;
        buildStatusCard(box, s);
        if (!s.ok || !conn) buildMasterCard(box, s.ok, conn);
        buildDevFooter(box);

        if (Tamper.isRooted()) {
            box.addView(kit.hint("⚠ گوشی روت شده است؛ در صورت مشکل با پشتیبانی در میان بگذارید."),
                    kit.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(box);
        setContentView(sv);
        updateNetRow();
    }

    private boolean connConfigured() {
        try {
            return settings.connConfigured();
        } catch (Exception e) {
            return false;
        }
    }

    // ---- status ----

    private void buildStatusCard(LinearLayout box, LicenseStore.Status s) {
        LinearLayout c = Card3D.card(this, s.ok ? Theme.SUCCESS : Theme.DANGER);
        c.addView(Card3D.stepRow(this, kit, "✦", s.ok ? Theme.SUCCESS : Theme.DANGER,
                s.ok ? "وضعیت لایسنس" : "فعال‌سازی لازم است"), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        if (s.ok) {
            c.addView(kit.kv("طرح", "«" + License.planFa(s.plan) + "»", Theme.GOLD_SOFT),
                    kit.lp(-1, -2));
            if (s.plan == License.P_PERM) {
                c.addView(kit.kv("اعتبار", "دائمی ♾", Theme.SUCCESS), kit.lp(-1, -2));
            } else {
                c.addView(kit.kv("زمان باقی‌مانده",
                        Money.fa(String.valueOf(s.daysLeft)) + " روز", leftColor(s.daysLeft)),
                        kit.lp(-1, -2));
                c.addView(kit.kv("اعتبار تا",
                        Money.fa(Jalali.format((int) (s.expDay + 2440588L))), Theme.TEXT),
                        kit.lp(-1, -2));
            }
            c.addView(kit.btnGold("ورود به برنامه ⇤", v -> enterApp()), kit.lp(-1, -2));
        } else {
            String msg = "none".equals(s.reason)
                    ? "برنامه هنوز فعال نشده — قاب زیر را تکمیل کنید."
                    : s.fa;
            c.addView(kit.hint(msg), kit.lp(-1, -2));
        }
        Card3D.mount(box, c);
    }

    private static int leftColor(long daysLeft) {
        if (daysLeft <= 7) return Theme.DANGER;
        if (daysLeft <= 30) return Theme.WARNING;
        return Theme.SUCCESS;
    }

    // ---- network row (lives inside the master frame) ----

    private void updateNetRow() {
        if (netDot == null || netTxt == null) return;
        try {
            if (Net.wifi(this)) {
                netDot.setTextColor(Theme.SUCCESS);
                netTxt.setText("متصل به وای‌فای ✓");
                netTxt.setTextColor(Theme.SUCCESS);
            } else if (Net.online(this)) {
                netDot.setTextColor(Theme.TEAL);
                netTxt.setText("اینترنت گوشی ✓ — فعال‌سازی ممکن است");
                netTxt.setTextColor(Theme.TEAL);
            } else {
                netDot.setTextColor(Theme.DANGER);
                netTxt.setText("اینترنت قطع است — وصل شوید");
                netTxt.setTextColor(Theme.DANGER);
            }
        } catch (Exception ignored) { }
    }

    // ---- the one luxury frame: request + receive + network, all in one ----

    private void buildMasterCard(LinearLayout box, boolean licOk, boolean connOk) {
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        LinearLayout head = kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Card3D.stepRow(this, kit, "✦", Theme.GOLD, "فعال‌سازی هوشمند"), kit.wlp(1f));
        head.addView(Card3D.glyph(this, R.drawable.lic_bolt, Theme.TEAL, 44));
        c.addView(head, kit.lp(-1, -2));
        c.addView(kit.gap(4));

        if (!licOk) {
            // Device code: tap to copy (no extra button).
            c.addView(kit.text("کد دستگاه شما (برای کپی لمس کنید)", 12f, Theme.MUTED, true),
                    kit.lp(-1, -2));
            TextView dev = kit.text(device.isEmpty() ? "—" : prettyDev(device), 20,
                    Theme.GOLD_SOFT, true);
            dev.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            dev.setGravity(Gravity.CENTER);
            Theme.pressable(dev);
            dev.setOnClickListener(v -> {
                copyText("کد دستگاه میلانو", device);
                kit.toast("کد دستگاه کپی شد");
            });
            c.addView(dev, kit.lp(-1, -2));
            final EditText fName = kit.edit("نام پخش (اجباری)", LicenseStore.distName(this));
            final EditText fPhone = kit.edit("شماره موبایل (0912…)", "");
            try {
                fPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            } catch (Exception ignored) { }
            final EditText fSeller = kit.edit("شماره فروشنده (اختیاری — پاسخ خودکار)", "");
            try {
                fSeller.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
                fSeller.setText(LicenseStore.sellerPhone(this));
            } catch (Exception ignored) { }
            for (EditText e : new EditText[]{fName, fPhone, fSeller}) {
                LinearLayout.LayoutParams lp = kit.lp(-1, -2);
                lp.setMargins(0, Theme.dp(4), 0, Theme.dp(4));
                c.addView(e, lp);
            }
            c.addView(kit.btnGold("✦ دریافت کد فعال‌سازی", v ->
                    smartSend(txt(fName), txt(fPhone), txt(fSeller))), kit.lp(-1, -2));
            c.addView(kit.hint(SmsIo.statusLine(this)), kit.lp(-1, -2));
            c.addView(kit.gap(4));
        }

        // ---- step 2: the customer's own server (typed once, hidden forever) ----
        if (licOk && !connOk) {
            c.addView(kit.text("اتصال به سرور فروشگاه", 14f, Theme.TEXT, true),
                    kit.lp(-1, -2));
            final LinearLayout manualBox = kit.v();
            manualBox.setVisibility(View.GONE);
            final EditText fLan = kit.edit("آی‌پی داخل فروشگاه (مثلاً 192.168.1.10)", "");
            final EditText fWan = kit.edit("آدرس اینترنتی (اختیاری)", "");
            final EditText fDb = kit.edit("نام دیتابیس", "");
            final EditText fPort = kit.edit("پورت", "1433");
            for (EditText e : new EditText[]{fLan, fWan, fDb, fPort}) {
                LinearLayout.LayoutParams lp2 = kit.lp(-1, -2);
                lp2.setMargins(0, Theme.dp(4), 0, Theme.dp(4));
                manualBox.addView(e, lp2);
            }
            manualBox.addView(kit.btnGold("💾 ذخیره و اتصال", v ->
                    saveSiteManual(txt(fLan), txt(fWan), txt(fPort), txt(fDb))), kit.lp(-1, -2));
            c.addView(manualBox, kit.lp(-1, -2));
            c.addView(kit.btnGhost("⚙ ورود دستی آدرس سرور", Theme.MUTED, v ->
                    manualBox.setVisibility(manualBox.getVisibility() == View.VISIBLE
                            ? View.GONE : View.VISIBLE)), kit.lp(-1, -2));
            c.addView(kit.hint("کد اتصال فروشنده را بچسبانید یا اسکن کنید."), kit.lp(-1, -2));
        }

        // ---- receive (pack or connection line, auto-detected) ----
        recvHint = kit.hint("");
        c.addView(recvHint, kit.lp(-1, -2));
        updateRecvHint();
        final EditText fIn = kit.edit("کد فروشنده را اینجا بچسبانید…", "");
        fIn.setMinLines(2);
        try {
            fIn.setTypeface(Typeface.MONOSPACE);
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams ip = kit.lp(-1, -2);
        ip.setMargins(0, Theme.dp(4), 0, Theme.dp(8));
        c.addView(fIn, ip);
        LinearLayout row = kit.h();
        row.addView(kit.btnGold("✦ ثبت خودکار", v -> smartReceive(txt(fIn))), kit.wlp(1f));
        row.addView(kit.space(8));
        row.addView(kit.btnGhost("◧ اسکن QR", Theme.TEAL, v -> scanCode()), kit.wlp(1f));
        c.addView(row, kit.lp(-1, -2));
        // ---- network footer (only while the server link is missing) ----
        if (!connOk) {
            c.addView(kit.gap(6));
            LinearLayout nrow = kit.h();
            nrow.setGravity(Gravity.CENTER_VERTICAL);
            netDot = kit.text("●", 14, Theme.WARNING, true);
            nrow.addView(netDot, kit.lp(-2, -2));
            nrow.addView(kit.space(8));
            netTxt = kit.text("در حال بررسی…", 12.5f, Theme.TEXT, false);
            nrow.addView(netTxt, kit.wlp(1f));
            nrow.addView(kit.space(8));
            nrow.addView(kit.btnGhost("↻", Theme.TEAL, v -> {
                updateNetRow();
                retryStagedCard();
            }), kit.lp(-2, -2));
            c.addView(nrow, kit.lp(-1, -2));
        }
        Card3D.mount(box, c);
    }

    private String buildRequestMsg(String dist, String phone) {
        String line = License.requestLine(device, dist, "", dist, phone, "");
        String use = Usage.reportLine(this, device);
        StringBuilder b = new StringBuilder("درخواست فعال‌سازی میلانو\n").append(line);
        if (!use.isEmpty()) b.append('\n').append(use);
        b.append("\nنام پخش: ").append(dist.trim());
        b.append("\nتماس: ").append(phone.trim());
        return b.toString();
    }

    /** One button: validate, then direct SMS when the seller number is filled, share sheet otherwise. */
    private void smartSend(String dist, String phone, String seller) {
        if (dist == null || dist.trim().isEmpty()) {
            kit.toast("نام پخش را وارد کنید");
            return;
        }
        if (SmsIo.cleanPhone(phone).length() < 10) {
            kit.toast("شماره موبایل معتبر وارد کنید (مثلاً 09123456789)");
            return;
        }
        LicenseStore.setDistName(this, dist);
        if (SmsIo.cleanPhone(seller).length() >= 10) {
            directSendRequest(dist, phone, seller);
        } else {
            shareRequest(dist, phone, seller);
        }
    }

    /** Zero-copy path: the request flies straight to the seller's phone. */
    private void directSendRequest(String name, String phone, String seller) {
        String dest = SmsIo.cleanPhone(seller);
        if (dest.length() < 10) {
            kit.toast("برای ارسال مستقیم، شماره فروشنده را کامل وارد کنید");
            return;
        }
        LicenseStore.setSellerPhone(this, seller);
        String msg = buildRequestMsg(name, phone);
        pendingKind = "req";
        if (!SmsIo.canSend(this)) {
            pendingSmsPhone = dest;
            pendingSmsText = msg;
            kit.toast("برای ارسال مستقیم پیامک، دسترسی را تأیید کنید");
            SmsIo.askSend(this);
            return;
        }
        doSmsSend(dest, msg);
    }

    private void doSmsSend(String dest, String msg) {
        kit.toast("در حال ارسال پیامک…");
        final String kind = pendingKind == null ? "" : pendingKind;
        pendingKind = "";
        SmsIo.send(this, dest, msg, new SmsIo.Cb() {
            @Override
            public void ok() {
                if ("site".equals(kind)) {
                    kit.toast("✓ مشخصات اتصال برای فروشنده ارسال شد");
                } else {
                    kit.toast("✓ درخواست ارسال شد؛ منتظر پاسخ فروشنده باشید");
                    showWaitDialog();
                }
            }

            @Override
            public void fail(String fa) {
                kit.toast(fa);
                if (!"site".equals(kind) && SmsIo.permaDenied(LicenseActivity.this)) permSettingsDialog();
            }
        });
    }

    private void permSettingsDialog() {
        LinearLayout b = kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        b.addView(kit.text("برای ارسال مستقیم پیامک، از تنظیمات گوشی اجازه «پیامک» را به میلانو بدهید.",
                13f, Theme.TEXT, false), kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        LinearLayout row = kit.h();
        row.addView(kit.btn("باز کردن تنظیمات", v -> {
            box[0].dismiss();
            SmsIo.openSettings(LicenseActivity.this);
        }), kit.wlp(1f));
        row.addView(kit.space(8));
        row.addView(kit.btnGhost("بعداً", Theme.MUTED, v -> box[0].dismiss()), kit.wlp(1f));
        b.addView(row, kit.lp(-1, -2));
        box[0] = kit.dialog("دسترسی پیامک", b, true);
        box[0].show();
    }

    private void shareRequest(String name, String phone, String seller) {
        LicenseStore.setSellerPhone(this, seller);
        String msg = buildRequestMsg(name, phone);
        copyText("درخواست فعال‌سازی", msg);
        try {
            String sms = seller.replaceAll("[^0-9+]", "");
            if (sms.length() >= 10) {
                try {
                    Intent s = new Intent(Intent.ACTION_SENDTO,
                            android.net.Uri.parse("smsto:" + sms));
                    s.putExtra("sms_body", msg);
                    startActivity(Intent.createChooser(s, "ارسال درخواست"));
                    kit.toast("متن درخواست کپی شد؛ آن را برای فروشنده بفرستید");
                    return;
                } catch (Exception ignored) { }
            }
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, msg);
            startActivity(Intent.createChooser(i, "ارسال درخواست"));
        } catch (Exception e) {
            kit.toast("متن درخواست در حافظه کپی شد");
        }
    }

    private void shareUsage() {
        String use = Usage.reportLine(this, device);
        if (use.isEmpty()) {
            kit.toast("گزارشی برای ارسال نیست");
            return;
        }
        String msg = "گزارش مصرف میلانو\n" + use
                + "\nکارکرد: " + Usage.faSummary(this);
        copyText("گزارش مصرف", msg);
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, msg);
            startActivity(Intent.createChooser(i, "ارسال گزارش مصرف"));
        } catch (Exception e) {
            kit.toast("گزارش در حافظه کپی شد");
        }
    }

    /**
     * The smart box: finds a verifiable pack and/or a connection card inside
     * any pasted text and applies whatever it finds (pack first, then card).
     */
    private void smartReceive(String in) {
        if (in != null && !in.isEmpty()) {
            if (License.parseRevoke(in, device)) {
                LicenseStore.revoke(this);
                kit.toast("لایسنس لغو شد؛ برای فعال‌سازی مجدد اقدام کنید");
                buildUi();
                return;
            }
            String br = License.parseBlock(in, device);
            if (br != null) {
                LicenseStore.setBlocked(this, br);
                buildUi();
                return;
            }
            if (License.parseUnblock(in, device)) {
                LicenseStore.clearBlocked(this);
                kit.toast("مسدودی برداشته شد؛ برنامه را فعال کنید");
                buildUi();
                return;
            }
        }
        if (in == null || in.isEmpty()) {
            // Imperceptible: pull the clipboard automatically, then process.
            String clip = clipText();
            if (clip != null && !clip.isEmpty()) {
                smartReceive(clip);
                return;
            }
            kit.toast("کد فروشنده را بچسبانید یا اسکن کنید");
            return;
        }
        String pack = extractPackToken(in);
        String card = extractCardLine(in);
        if (pack == null && card == null) {
            // Let the proper parser explain what is wrong with this text.
            if (in.contains(License.DB_PREFIX) || in.contains(License.NET_PREFIX)) applyCard(in);
            else doActivate(in);
            return;
        }
        if (pack != null) doActivate(pack);
        if (card != null) applyCard(card);
    }

    /** First verifiable 39-char M1… pack token in the text (dashes tolerated). */
    private static String extractPackToken(String body) {
        try {
            for (String tok : body.split("\\s+")) {
                String n = License.normalize(tok);
                if (n.length() == License.PACK_LEN && n.startsWith("M1")) {
                    License.Result r = License.parse(n);
                    if (r.ok) return r.pack;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** The connection line (MILANO-DB1 card or MILANO-NET1 mini line), or null. */
    private static String extractCardLine(String body) {
        try {
            for (String raw : body.split("\n")) {
                String line = raw.trim();
                if (line.startsWith(License.DB_PREFIX + "|")) return line;
                if (line.startsWith(License.NET_PREFIX + "|")) return line;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private boolean recvGranted() {
        try {
            if (Build.VERSION.SDK_INT < 23) return true;
            return checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureRecvPerm() {
        try {
            if (!recvGranted()) {
                kit.toast("برای اعمال خودکار کد فعال‌سازی، دسترسی پیامک را تأیید کنید");
                requestPermissions(new String[]{android.Manifest.permission.RECEIVE_SMS}, REQ_RECV);
            }
        } catch (Exception ignored) { }
    }

    private void updateRecvHint() {
        if (recvHint == null) return;
        try {
            if (recvGranted()) {
                recvHint.setText("✦ دریافت خودکار فعال است — پیامک فروشنده خودش اعمال می‌شود؛ فقط منتظر بمانید.");
                recvHint.setTextColor(Theme.TEAL);
            } else {
                recvHint.setText("اگر دسترسی پیامک را بدهید، پاسخ فروشنده خودکار ثبت می‌شود — وگرنه دستی بچسبانید یا اسکن کنید.");
                recvHint.setTextColor(Theme.MUTED);
            }
        } catch (Exception ignored) { }
    }

    private void doActivate(String in) {
        if (in.isEmpty()) {
            kit.toast("کد فعال‌سازی را وارد کنید");
            return;
        }
        LicenseStore.Status s = LicenseStore.activate(this, in);
        if (s.ok) {
            String left = s.plan == License.P_PERM ? "دائمی ♾"
                    : ("«" + License.planFa(s.plan) + "» • "
                    + Money.fa(String.valueOf(s.daysLeft)) + " روز");
            kit.toast("فعال شد! لایسنس " + left);
            buildUi();
        } else {
            kit.toast(s.fa);
        }
    }

    /** Manual retry: take a staged card/pack and try it right now. */
    private void retryStagedCard() {
        try {
            LicenseStore.SmsPending s = LicenseStore.takePendingSms(this);
            boolean havePack = s.pack != null && !s.pack.isEmpty();
            boolean haveCard = s.card != null && !s.card.isEmpty();
            if (havePack) doActivate(s.pack);
            if (haveCard) applyCard(s.card);
            if (!havePack && !haveCard)
                kit.toast("کارت اتصال هنوز ثبت نشده — از باکس «ثبت خودکار» استفاده کنید");
        } catch (Exception ignored) { }
    }

    // ---- QR scan (offline pairing: seller shows, customer scans) ----

    private void scanCode() {
        if (!camGranted()) {
            wantScan = true;
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAM);
            return;
        }
        launchScan();
    }

    private boolean camGranted() {
        try {
            if (Build.VERSION.SDK_INT < 23) return true;
            return checkSelfPermission(android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private void launchScan() {
        try {
            Intent i = new Intent(this, ScanActivity.class);
            i.putExtra(ScanActivity.EXTRA_TITLE, "اسکن کد فروشنده");
            i.putExtra(ScanActivity.EXTRA_HINT, "QR فروشنده (کد یا کارت اتصال) را جلوی دوربین بگیرید…");
            startActivityForResult(i, REQ_SCAN);
        } catch (Exception e) {
            kit.toast("باز کردن دوربین ممکن نشد");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        try {
            if (req != REQ_SCAN || res != RESULT_OK || data == null) return;
            String code = data.getStringExtra(ScanActivity.EXTRA_CODE);
            if (code == null || code.isEmpty()) return;
            smartReceive(code);
        } catch (Exception ignored) { }
    }

    // ---- SMS auto-apply ----

    /**
     * Apply whatever the seller's SMS staged (pack and/or connection card).
     * Called on every resume + instantly while open via the internal ping.
     */
    private void consumeSms() {
        try {
            LicenseStore.SmsPending s = LicenseStore.takePendingSms(this);
            boolean havePack = s.pack != null && !s.pack.isEmpty();
            boolean haveCard = s.card != null && !s.card.isEmpty();
            if (!havePack && !haveCard) return;
            if (havePack) {
                LicenseStore.Status st = LicenseStore.activate(this, s.pack);
                if (st.ok) {
                    kit.toast("✦ کد فعال‌سازی از پیامک اعمال شد!");
                    waitSuccess();
                    buildUi();
                } else {
                    kit.toast(st.fa);
                }
            }
            if (haveCard) {
                long now = System.currentTimeMillis();
                if (!Net.online(this)) {
                    // No internet at all: keep it staged; the next onResume retries.
                    LicenseStore.addPendingSms(this, null, s.card);
                    if (now - lastCardTry > 60000) {
                        lastCardTry = now;
                        kit.toast("کارت اتصال رسید و ذخیره شد؛ با وصل شدن اینترنت خودکار ثبت می‌شود");
                    }
                } else if (now - lastCardTry > 10000) {
                    // Online: test it live (fails fast on LAN-only servers), throttled.
                    lastCardTry = now;
                    applyCard(s.card);
                } else {
                    LicenseStore.addPendingSms(this, null, s.card);
                }
            }
        } catch (Exception ignored) { }
    }

    // ---- automatic server connection ----

    private void applyCard(String pasted) {
        if (pasted == null || pasted.isEmpty()) {
            kit.toast("کارت اتصال را بچسبانید");
            return;
        }
        License.DbProfile prof = License.parseAnyCard(pasted, device);
        if (prof == null) {
            kit.toast("کارت معتبر نیست؛ مطمئن شوید کارت همین گوشی را وارد کرده‌اید");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(prof.port.trim());
        } catch (Exception e) {
            kit.toast("کارت خراب است؛ از فروشنده کارت تازه بخواهید");
            return;
        }
        if (!Net.online(this)) {
            // A valid card but no internet at all — stage it and retry automatically.
            LicenseStore.addPendingSms(this, null, pasted);
            kit.toast("کارت معتبر است و ذخیره شد؛ با وصل شدن اینترنت خودکار ثبت می‌شود");
            return;
        }
        kit.toast("در حال ثبت و تست اتصال…");
        final String fh = prof.host;
        final String fd = prof.db;
        final String fu = prof.user;
        final String fp = prof.pass;
        repo.runWith(fh, port, fd, fu, fp, conn -> {
            Meta m = new Meta(conn);
            int n = 0;
            for (String t : new String[]{"sailfact", "buyfact", "dar", "getchk",
                    "putchk", "CUSTOMERS", "inventory", "visitors"})
                if (m.table(t)) n++;
            return "اتصال برقرار شد • " + Money.fa(String.valueOf(n)) + " جدول اصلی در دسترس";
        }, new Repo.Cb<String>() {
            @Override
            public void ok(String v) {
                try {
                    settings.placeHost(fh, String.valueOf(port), fd, fu, fp);
                } catch (Exception ignored) { }
                kit.toast("✓ " + v);
                buildUi();
            }

            @Override
            public void fail(String faError) {
                // Server unreachable (often a LAN-only server while on mobile data):
                // keep the card staged — every resume + the retry button try again.
                LicenseStore.addPendingSms(LicenseActivity.this, null, pasted);
                kit.toast(faError == null || faError.isEmpty()
                        ? "سرور در دسترس نیست؛ اگر سرور فقط روی وای‌فای داخلی است به وای‌فای فروشگاه وصل شوید — کارت ذخیره شد و خودکار تلاش می‌شود"
                        : faError);
            }
        });
    }

    // ---- developer signature + support (always on page one) ----

    private void buildDevFooter(LinearLayout box) {
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        c.addView(Card3D.stepRow(this, kit, "✦", Theme.GOLD, "میلانو"),
                kit.lp(-1, -2));
        TextView tag = kit.text(Brand.TAGLINE, 11.5f, Theme.MUTED, false);
        tag.setGravity(Gravity.CENTER);
        c.addView(tag, kit.lp(-1, -2));
        c.addView(kit.gap(4));
        LinearLayout row = kit.h();
        row.addView(kit.btnGhost("🌐  " + Brand.SITE_LABEL, Theme.GOLD,
                v -> Brand.openSite(this)), kit.wlp(1f));
        row.addView(kit.space(8));
        row.addView(kit.btnGhost("📞 پشتیبانی", Theme.TEAL, v -> supportDialog()), kit.wlp(1f));
        c.addView(row, kit.lp(-1, -2));
        TextView ver = kit.text(appVer(), 11f, Theme.MUTED, false);
        ver.setGravity(Gravity.CENTER);
        c.addView(ver, kit.lp(-1, -2));
        TextView sig = kit.text("Designed by Milad Yaghoubi", 9.5f, Theme.MUTED, false);
        sig.setGravity(Gravity.CENTER);
        c.addView(sig, kit.lp(-1, -2));
        Card3D.mount(box, c);
    }

    private void supportDialog() {
        LinearLayout body = kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(8));
        body.addView(kit.btnGhost("🌐  " + Brand.SITE_LABEL, Theme.GOLD,
                v -> Brand.openSite(this)), kit.lp(-1, -2));
        for (String ph : Brand.PHONES) {
            final String fph = ph;
            body.addView(kit.btnGhost("📞  " + Money.fa(ph), Theme.TEAL,
                    v -> Brand.dial(this, fph)), kit.lp(-1, -2));
        }
        final AlertDialog[] dlgH = new AlertDialog[1];
        body.addView(kit.gap(4));
        body.addView(kit.btn("بستن", v -> {
            if (dlgH[0] != null) dlgH[0].dismiss();
        }), kit.lp(-1, -2));
        dlgH[0] = kit.dialog("پشتیبانی میلانو", body, true);
        dlgH[0].show();
    }

    private String clipText() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                if (t != null) return t.toString().trim();
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Full-stop violation screen: no code, no activation, just the notice. */
    private void buildBlockedUi(String reason) {
        LinearLayout box = kit.v();
        box.setBackgroundColor(Theme.BG);
        int pad = Theme.dp(16);
        box.setPadding(pad, pad, pad, pad);
        Card3D.mount(box, Card3D.hero(this, kit, R.drawable.lic_shield, Theme.DANGER,
                "⛔ دسترسی مسدود شد",
                "اجرای برنامه متوقف شده است",
                "مسدود", Theme.DANGER));
        LinearLayout c = Card3D.card(this, Theme.DANGER);
        c.addView(kit.text("این گوشی توسط فروشنده مسدود شده است.", 14f, Theme.TEXT, true),
                kit.lp(-1, -2));
        c.addView(kit.gap(4));
        c.addView(kit.kv("علت", reason == null || reason.isEmpty() ? "تخلف از قوانین استفاده" : reason,
                Theme.DANGER), kit.lp(-1, -2));
        c.addView(kit.hint("برای رفع مسدودی با فروشنده تماس بگیرید. بدون رفع مسدودی، فعال‌سازی جدید ممکن نیست."),
                kit.lp(-1, -2));
        Card3D.mount(box, c);
        buildDevFooter(box);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(box);
        setContentView(sv);
    }

    // ---------- 3D wait: the seller is issuing your code ----------

    private void showWaitDialog() {
        dismissWait();
        LinearLayout body = kit.v();
        body.setPadding(Theme.dp(20), Theme.dp(16), Theme.dp(20), Theme.dp(8));
        body.addView(kit.loading("در انتظار پاسخ فروشنده…"), kit.lp(-1, -2));
        waitStage = kit.text("✓ درخواست ارسال شد", 13f, Theme.SUCCESS, true);
        waitStage.setGravity(Gravity.CENTER);
        body.addView(waitStage, kit.lp(-1, -2));
        waitTime = kit.text("", 12f, Theme.MUTED, false);
        waitTime.setGravity(Gravity.CENTER);
        body.addView(waitTime, kit.lp(-1, -2));
        body.addView(kit.hint("می‌توانید خارج شوید؛ پاسخ فروشنده خودکار اعمال می‌شود."),
                kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(kit.btn("انصراف", v -> {
            if (box[0] != null) box[0].dismiss();
        }), kit.lp(-1, -2));
        waitDlg = kit.dialog("✦ دریافت کد فعال‌سازی", body, true);
        box[0] = waitDlg;
        waitDlg.setOnDismissListener(d -> waitDlg = null);
        waitStart = System.currentTimeMillis();
        waitDlg.show();
        waitHandler.post(waitTick);
    }

    private void tickWait() {
        if (waitDlg == null || waitTime == null) return;
        long sec = (System.currentTimeMillis() - waitStart) / 1000;
        waitTime.setText("زمان انتظار: " + Money.fa(String.format(java.util.Locale.US,
                "%d:%02d", sec / 60, sec % 60)));
        waitHandler.postDelayed(waitTick, 1000);
    }

    private void waitSuccess() {
        if (waitDlg == null) return;
        try {
            if (waitStage != null) waitStage.setText("✓✓ فعال شد! وارد مرحله بعد می‌شوید…");
        } catch (Exception ignored) { }
        final AlertDialog d = waitDlg;
        waitHandler.postDelayed(() -> {
            try {
                d.dismiss();
            } catch (Exception ignored) { }
        }, 1500);
    }

    private void dismissWait() {
        try {
            if (waitDlg != null) waitDlg.dismiss();
        } catch (Exception ignored) { }
        waitDlg = null;
    }

    // ---------- step 2: the customer's own server ----------

    private void saveSiteManual(String lan, String wan, String portStr, String db) {
        final String fLan = lan == null ? "" : lan.trim();
        final String fWan = wan == null ? "" : wan.trim();
        if (fLan.isEmpty() && fWan.isEmpty()) {
            kit.toast("دست‌کم یک آدرس (داخل یا خارج فروشگاه) را وارد کنید");
            return;
        }
        if (db == null || db.isEmpty()) {
            kit.toast("نام دیتابیس را وارد کنید");
            return;
        }
        int port = 1433;
        try {
            port = Integer.parseInt(portStr.trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (Exception e) {
            kit.toast("پورت معتبر نیست (۱ تا ۶۵۵۳۵)");
            return;
        }
        if (!Net.online(this)) {
            kit.toast("اینترنت قطع است — وصل شوید و دوباره بزنید");
            return;
        }
        kit.toast("در حال تست مسیرها…");
        final String fDb = db.trim();
        final int fPort = port;
        new Thread(() -> {
            final int[] lanN = {-1};
            final int[] wanN = {-1};
            if (!fLan.isEmpty()) {
                try {
                    lanN[0] = SmartLink.probeTables(fLan, fPort, fDb,
                            License.SQL_USER, License.SQL_PASS);
                } catch (Exception ignored) { lanN[0] = -1; }
            }
            if (!fWan.isEmpty()) {
                try {
                    wanN[0] = SmartLink.probeTables(fWan, fPort, fDb,
                            License.SQL_USER, License.SQL_PASS);
                } catch (Exception ignored) { wanN[0] = -1; }
            }
            runOnUiThread(() -> {
                if (lanN[0] < 0 && wanN[0] < 0) {
                    // Nothing worked: open the troubleshooter with these exact values.
                    kit.toast("هیچ مسیری وصل نشد؛ عیب‌یابی باز می‌شود");
                    try {
                        LinkPanel.showDiagnoseExplicit(LicenseActivity.this, fLan, fWan,
                                fPort, fDb, License.SQL_USER, License.SQL_PASS);
                    } catch (Exception ignored) { }
                    return;
                }
                try {
                    settings.saveSmart(fLan, fWan, String.valueOf(fPort), fDb,
                            License.SQL_USER, License.SQL_PASS);
                    settings.setLinkLast(pickLast(lanN[0], wanN[0]));
                } catch (Exception ignored) { }
                String v = "داخل: " + (fLan.isEmpty() ? "—" : (lanN[0] >= 0 ? "✓" : "✕"))
                        + " • خارج: " + (fWan.isEmpty() ? "—" : (wanN[0] >= 0 ? "✓" : "✕"));
                int n = Math.max(lanN[0], wanN[0]);
                kit.toast("✓ ذخیره شد (" + v + ") • "
                        + Money.fa(String.valueOf(n)) + " جدول اصلی در دسترس");
                sendSiteSms(pickHost(fLan, lanN[0], fWan, wanN[0]), fPort, fDb);
                buildUi();
            });
        }).start();
    }

    /** Prefer the winner matching the current transport for SITE1 + last-path. */
    private String pickLast(int lanN, int wanN) {
        boolean wifi = false;
        try {
            wifi = Net.wifi(this);
        } catch (Exception ignored) { }
        if (wifi && lanN >= 0) return SmartLink.LAN;
        if (!wifi && wanN >= 0) return SmartLink.WAN;
        if (lanN >= 0) return SmartLink.LAN;
        return SmartLink.WAN;
    }

    private String pickHost(String lan, int lanN, String wan, int wanN) {
        String last = pickLast(lanN, wanN);
        if (SmartLink.LAN.equals(last) && lanN >= 0) return lan;
        if (SmartLink.WAN.equals(last) && wanN >= 0) return wan;
        return lanN >= 0 ? lan : wan;
    }

    /** Report the typed site back to the seller (full customer record). */
    private void sendSiteSms(String ip, int port, String db) {
        String dest = SmsIo.cleanPhone(LicenseStore.sellerPhone(this));
        if (dest.length() < 10) {
            kit.toast("شماره فروشنده ثبت نیست؛ مشخصات اتصال را به او اطلاع دهید");
            return;
        }
        String line;
        try {
            line = License.siteLine(device, ip, String.valueOf(port), db);
        } catch (Exception e) {
            return;
        }
        String dist = LicenseStore.distName(this);
        String msg = "مشخصات اتصال مشتری\n" + line
                + (dist.isEmpty() ? "" : "\nپخش: " + dist);
        pendingKind = "site";
        if (!SmsIo.canSend(this)) {
            pendingSmsPhone = dest;
            pendingSmsText = msg;
            SmsIo.askSend(this);
            return;
        }
        doSmsSend(dest, msg);
    }

    // ---------- misc ----------

    private void enterApp() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        } catch (Exception ignored) { }
    }

    private static String txt(EditText e) {
        try {
            return e.getText().toString().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private String appVer() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return "نسخه " + Money.fa(v == null || v.isEmpty() ? "—" : v);
        } catch (Exception e) {
            return "نسخه —";
        }
    }

    private static String prettyDev(String code) {
        if (code == null || code.length() != 8) return code == null ? "" : code;
        return code.substring(0, 4) + "-" + code.substring(4);
    }

    private void copyText(String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        } catch (Exception ignored) { }
    }
}
