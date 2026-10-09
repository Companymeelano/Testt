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
 * Offline license gate, v21: ONE luxury frame («فعال‌سازی هوشمند») — ۱) smart request
 * (direct SMS when the seller number is filled, share sheet otherwise),
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
                    kit.toast("بدون دسترسی پیامک، شماره فروشنده را خالی بگذارید و دوباره بزنید");
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
        boolean conn = connConfigured();
        int done = (s.ok ? 1 : 0) + (conn ? 1 : 0);
        String pill = done >= 2 ? "✓ آماده ورود"
                : ("قدم " + Money.fa(String.valueOf(done + 1)) + " از ۲");
        Card3D.mount(box, Card3D.hero(this, kit, R.drawable.lic_shield, Theme.GOLD,
                "فعال‌سازی میلانو منیجر",
                "۲ قدم ساده • همه‌چیز در یک قاب",
                pill, done >= 2 ? Theme.SUCCESS : Theme.WARNING));

        netDot = null;
        netTxt = null;
        buildStatusCard(box, s);
        if (!s.ok || !conn) buildMasterCard(box, s.ok, conn);
        buildSupportCard(box);

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
            String use = Usage.faSummary(this);
            if (!use.isEmpty()) {
                c.addView(kit.kv("کارکرد شما", use, Theme.MUTED), kit.lp(-1, -2));
            }
            c.addView(kit.btnGold("ورود به برنامه ⇤", v -> enterApp()), kit.lp(-1, -2));
            c.addView(kit.gap(6));
            c.addView(kit.btnGhost("ارسال گزارش مصرف به فروشنده", Theme.TEAL, v -> shareUsage()),
                    kit.lp(-1, -2));
        } else {
            String msg = "none".equals(s.reason)
                    ? "برنامه هنوز فعال نشده — قاب «فعال‌سازی هوشمند» را تکمیل کنید."
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
            TextView dev = kit.text(device.isEmpty() ? "—" : prettyDev(device), 24,
                    Theme.GOLD_SOFT, true);
            dev.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            dev.setGravity(Gravity.CENTER);
            Theme.pressable(dev);
            dev.setOnClickListener(v -> {
                copyText("کد دستگاه میلانو", device);
                kit.toast("کد دستگاه کپی شد");
            });
            c.addView(dev, kit.lp(-1, -2));
            final EditText fName = kit.edit("نام", "");
            final EditText fPhone = kit.edit("شماره تماس (0912…)", "");
            try {
                fPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            } catch (Exception ignored) { }
            final EditText fSeller = kit.edit("شماره فروشنده (اختیاری)", "");
            try {
                fSeller.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
                fSeller.setText(LicenseStore.sellerPhone(this));
            } catch (Exception ignored) { }
            for (EditText e : new EditText[]{fName, fPhone, fSeller}) {
                LinearLayout.LayoutParams lp = kit.lp(-1, -2);
                lp.setMargins(0, Theme.dp(4), 0, Theme.dp(4));
                c.addView(e, lp);
            }
            c.addView(kit.btnGold("✦ ارسال درخواست", v ->
                    smartSend(txt(fName), txt(fPhone), txt(fSeller))), kit.lp(-1, -2));
            c.addView(kit.hint("با شماره فروشنده مستقیم پیامک می‌شود؛ بدون آن از واتساپ یا برنامه دیگر بفرستید."),
                    kit.lp(-1, -2));
            TextView div = kit.text("— پاسخ فروشنده —", 12f, Theme.MUTED, true);
            div.setGravity(Gravity.CENTER);
            c.addView(div, kit.lp(-1, -2));
        }

        // ---- receive (pack or connection line, auto-detected) ----
        recvHint = kit.hint("");
        c.addView(recvHint, kit.lp(-1, -2));
        updateRecvHint();
        c.addView(kit.kv("لایسنس", licOk ? "✓ فعال" : "— هنوز نشده",
                licOk ? Theme.SUCCESS : Theme.MUTED), kit.lp(-1, -2));
        c.addView(kit.kv("اتصال به سرور", connOk ? "✓ تنظیم شده" : "— هنوز نشده",
                connOk ? Theme.SUCCESS : Theme.MUTED), kit.lp(-1, -2));
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
        c.addView(kit.hint("فرقی نمی‌کند فروشنده چه فرستاده — کد یا اتصال؛ خودش تشخیص می‌دهد و ثبت می‌کند."),
                kit.lp(-1, -2));

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
            c.addView(nrow, kit.lp(-1, -2));
            c.addView(kit.btnGhost("↻ تلاش مجدد اتصال", Theme.TEAL, v -> {
                updateNetRow();
                retryStagedCard();
            }), kit.lp(-1, -2));
        }
        Card3D.mount(box, c);
    }

    private String buildRequestMsg(String name, String phone) {
        String line = License.requestLine(device, name, "", "", phone, "");
        String use = Usage.reportLine(this, device);
        StringBuilder b = new StringBuilder("درخواست فعال‌سازی میلانو منیجر\n").append(line);
        if (!use.isEmpty()) b.append('\n').append(use);
        b.append("\nنام: ").append(name.trim());
        b.append("\nتماس: ").append(phone.trim());
        return b.toString();
    }

    /** One button: validate, then direct SMS when the seller number is filled, share sheet otherwise. */
    private void smartSend(String name, String phone, String seller) {
        if (name == null || name.trim().isEmpty()) {
            kit.toast("نام را وارد کنید");
            return;
        }
        if (SmsIo.cleanPhone(phone).length() < 10) {
            kit.toast("شماره تماس معتبر وارد کنید (مثلاً 09123456789)");
            return;
        }
        if (SmsIo.cleanPhone(seller).length() >= 10) {
            directSendRequest(name, phone, seller);
        } else {
            shareRequest(name, phone, seller);
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
        SmsIo.send(this, dest, msg, new SmsIo.Cb() {
            @Override
            public void ok() {
                kit.toast("✓ درخواست با پیامک ارسال شد؛ منتظر پاسخ فروشنده باشید");
            }

            @Override
            public void fail(String fa) {
                kit.toast(fa);
            }
        });
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
        String msg = "گزارش مصرف میلانو منیجر\n" + use
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
                    settings.saveConnection(fh, String.valueOf(port), fd, fu, fp);
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

    // ---- support (slim: one button, the numbers live in a dialog) ----

    private void buildSupportCard(LinearLayout box) {
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        c.addView(Card3D.stepRow(this, kit, "✦", Theme.GOLD, "پشتیبانی میلانو"),
                kit.lp(-1, -2));
        c.addView(kit.gap(4));
        c.addView(kit.btnGhost("📞 تماس با پشتیبانی", Theme.TEAL, v -> supportDialog()),
                kit.lp(-1, -2));
        TextView tag = kit.text(Brand.TAGLINE, 11.5f, Theme.MUTED, false);
        tag.setGravity(Gravity.CENTER);
        c.addView(tag, kit.lp(-1, -2));
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
