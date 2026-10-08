package ir.meelano.manager;

import android.app.Activity;
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
 * Offline license gate, v17: 3D hero + Wi-Fi check → device code + request
 * form with DIRECT SMS send → activation pack with SMS AUTO-APPLY → automatic
 * server connection (seller's encrypted card, also SMS auto-applied) →
 * support footer. Blocks MainActivity until {@link LicenseStore#unlocked}.
 * Zero copy/paste: the request flies straight to the seller and the reply
 * applies itself the moment it lands.
 */
public class LicenseActivity extends Activity {

    private static final int REQ_RECV = 906;
    private static final int REQ_SCAN_PACK = 807;
    private static final int REQ_SCAN_CARD = 808;
    private static final int REQ_CAM = 809;

    private Kit kit;
    private Settings settings;
    private Repo repo;
    private String device = "";

    private TextView wifiDot;
    private TextView wifiTxt;
    private TextView connStatus;
    private TextView actSmsHint;

    private String pendingSmsPhone = "";
    private int pendingScan;
    private long lastCardNag;
    private String pendingSmsText = "";
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
        // The user may have just connected to Wi-Fi (or returned from the
        // dialer/browser) — refresh the Wi-Fi row in place (no rebuild, so
        // typed text in the form fields is never lost).
        updateWifiRow();
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
                    kit.toast("بدون دسترسی پیامک، از «ارسال با برنامه دیگر» استفاده کنید");
                }
                pendingSmsPhone = "";
                pendingSmsText = "";
            } else if (req == REQ_RECV) {
                updateActSmsHint();
            } else if (req == REQ_CAM) {
                boolean g = grants != null && grants.length > 0
                        && grants[0] == PackageManager.PERMISSION_GRANTED;
                int w = pendingScan;
                pendingScan = 0;
                if (g && w != 0) launchScan(w);
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
        Card3D.mount(box, Card3D.hero(this, kit, R.drawable.lic_shield, Theme.GOLD,
                "فعال‌سازی میلانو منیجر",
                "کمتر از ۲ دقیقه • بدون نیاز به اینترنت" + (s.ok ? "" : " • با پیامک مستقیم"),
                s.ok ? "✓ لایسنس فعال است" : "نیاز به فعال‌سازی",
                s.ok ? Theme.SUCCESS : Theme.WARNING));

        buildStatusCard(box, s);
        buildWifiCard(box);
        buildRequestCard(box);
        buildActivationCard(box);
        buildConnCard(box);
        buildSupportCard(box);

        if (Tamper.isRooted()) {
            box.addView(kit.hint("⚠ گوشی روت شده است؛ در صورت مشکل با پشتیبانی در میان بگذارید."),
                    kit.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(box);
        setContentView(sv);
        updateWifiRow();
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
                    ? "برنامه هنوز فعال نشده — مراحل زیر را به ترتیب انجام دهید."
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

    // ---- Wi-Fi ----

    private void buildWifiCard(LinearLayout box) {
        LinearLayout c = Card3D.card(this, Theme.TEAL);
        c.addView(Card3D.stepRow(this, kit, "۱", Theme.TEAL, "اتصال به وای‌فای فروشگاه (فقط فعال‌سازی)"),
                kit.lp(-1, -2));
        c.addView(kit.gap(6));
        LinearLayout row = kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        wifiDot = kit.text("●", 16, Theme.WARNING, true);
        row.addView(wifiDot, kit.lp(-2, -2));
        row.addView(kit.space(8));
        wifiTxt = kit.text("در حال بررسی…", 13.5f, Theme.TEXT, false);
        row.addView(wifiTxt, kit.wlp(1f));
        c.addView(row, kit.lp(-1, -2));
        c.addView(kit.hint("فعال‌سازی اولیه فقط با وای‌فای فروشگاه انجام می‌شود؛ "
                + "بعد از آن، برنامه با اینترنت گوشی هم کار می‌کند و دیگر نیازی به وای‌فای نیست. "
                + "اگر وصل نیستید، وصل شوید و «بررسی مجدد» را بزنید."),
                kit.lp(-1, -2));
        c.addView(kit.btnGhost("بررسی مجدد", Theme.TEAL, v -> updateWifiRow()), kit.lp(-1, -2));
        Card3D.mount(box, c);
    }

    private void updateWifiRow() {
        if (wifiDot == null || wifiTxt == null) return;
        try {
            if (Net.wifi(this)) {
                wifiDot.setTextColor(Theme.SUCCESS);
                wifiTxt.setText("متصل به وای‌فای ✓");
                wifiTxt.setTextColor(Theme.SUCCESS);
            } else if (Net.online(this)) {
                wifiDot.setTextColor(Theme.WARNING);
                wifiTxt.setText("به وای‌فای وصل نیستید (اینترنت موبایل جواب نمی‌دهد)");
                wifiTxt.setTextColor(Theme.WARNING);
            } else {
                wifiDot.setTextColor(Theme.DANGER);
                wifiTxt.setText("هیچ اتصالی نیست — وای‌فای را روشن کنید");
                wifiTxt.setTextColor(Theme.DANGER);
            }
        } catch (Exception ignored) { }
    }

    // ---- request ----

    private void buildRequestCard(LinearLayout box) {
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        LinearLayout head = kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Card3D.stepRow(this, kit, "۲", Theme.GOLD, "درخواست لایسنس از فروشنده"),
                kit.wlp(1f));
        head.addView(Card3D.glyph(this, R.drawable.lic_chat, Theme.VIOLET, 44));
        c.addView(head, kit.lp(-1, -2));
        c.addView(kit.gap(4));
        c.addView(kit.text("کد دستگاه شما", 12f, Theme.MUTED, true), kit.lp(-1, -2));
        TextView dev = kit.text(device.isEmpty() ? "—" : prettyDev(device), 26,
                Theme.GOLD_SOFT, true);
        dev.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        dev.setGravity(Gravity.CENTER);
        c.addView(dev, kit.lp(-1, -2));
        c.addView(kit.btnGhost("کپی کد دستگاه", Theme.GOLD, v -> {
            copyText("کد دستگاه میلانو", device);
            kit.toast("کد دستگاه کپی شد");
        }), kit.lp(-1, -2));
        final EditText fName = kit.edit("نام", "");
        final EditText fFamily = kit.edit("نام خانوادگی", "");
        final EditText fShop = kit.edit("نام فروشگاه", "");
        final EditText fPhone = kit.edit("شماره موبایل", "");
        try {
            fPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        } catch (Exception ignored) { }
        final EditText fCity = kit.edit("شهر", "");
        final EditText fSeller = kit.edit("شماره فروشنده (برای ارسال مستقیم پیامک)", "");
        try {
            fSeller.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            fSeller.setText(LicenseStore.sellerPhone(this));
        } catch (Exception ignored) { }
        for (EditText e : new EditText[]{fName, fFamily, fShop, fPhone, fCity, fSeller}) {
            LinearLayout.LayoutParams p = kit.lp(-1, -2);
            p.setMargins(0, Theme.dp(4), 0, Theme.dp(4));
            c.addView(e, p);
        }
        c.addView(kit.btnGold("✦ ارسال مستقیم با پیامک", v ->
                directSendRequest(txt(fName), txt(fFamily), txt(fShop), txt(fPhone),
                        txt(fCity), txt(fSeller))), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        c.addView(kit.btnGhost("ارسال با واتساپ / برنامه دیگر", Theme.MUTED, v ->
                shareRequest(txt(fName), txt(fFamily), txt(fShop), txt(fPhone),
                        txt(fCity), txt(fSeller))), kit.lp(-1, -2));
        Card3D.mount(box, c);
    }

    private String buildRequestMsg(String name, String family, String shop, String phone,
                                   String city) {
        String line = License.requestLine(device, name, family, shop, phone, city);
        String use = Usage.reportLine(this, device);
        return "درخواست فعال‌سازی میلانو منیجر\n" + line
                + (use.isEmpty() ? "" : ("\n" + use))
                + "\nنام: " + name + " " + family
                + "\nفروشگاه: " + shop + " — " + city
                + "\nموبایل: " + phone;
    }

    /** Zero-copy path: the request flies straight to the seller's phone. */
    private void directSendRequest(String name, String family, String shop, String phone,
                                   String city, String seller) {
        String dest = SmsIo.cleanPhone(seller);
        if (dest.length() < 10) {
            kit.toast("برای ارسال مستقیم، شماره فروشنده را کامل وارد کنید");
            return;
        }
        LicenseStore.setSellerPhone(this, seller);
        String msg = buildRequestMsg(name, family, shop, phone, city);
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

    private void shareRequest(String name, String family, String shop, String phone,
                              String city, String seller) {
        LicenseStore.setSellerPhone(this, seller);
        String msg = buildRequestMsg(name, family, shop, phone, city);
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

    // ---- activation ----

    private void buildActivationCard(LinearLayout box) {
        LinearLayout c = Card3D.card(this, Theme.VIOLET);
        LinearLayout head = kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Card3D.stepRow(this, kit, "۳", Theme.VIOLET, "وارد کردن کد فعال‌سازی"),
                kit.wlp(1f));
        head.addView(Card3D.glyph(this, R.drawable.lic_bolt, Theme.TEAL, 44));
        c.addView(head, kit.lp(-1, -2));
        c.addView(kit.gap(4));
        actSmsHint = kit.hint("");
        c.addView(actSmsHint, kit.lp(-1, -2));
        updateActSmsHint();
        final EditText fPack = kit.edit("کد ۳۹ حرفی فروشنده", "");
        try {
            fPack.setTypeface(Typeface.MONOSPACE);
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams p = kit.lp(-1, -2);
        p.setMargins(0, Theme.dp(4), 0, Theme.dp(8));
        c.addView(fPack, p);
        c.addView(kit.btnGold("فعال‌سازی", v -> doActivate(txt(fPack))), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        c.addView(kit.btnGhost("اسکن QR کد فعال‌سازی", Theme.VIOLET, v -> scanPack()), kit.lp(-1, -2));
        Card3D.mount(box, c);
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

    private void updateActSmsHint() {
        if (actSmsHint == null) return;
        try {
            if (recvGranted()) {
                actSmsHint.setText("✦ دریافت خودکار فعال است — پیامک فروشنده به‌محض رسیدن، خودش اعمال می‌شود؛ فقط منتظر بمانید.");
                actSmsHint.setTextColor(Theme.TEAL);
            } else {
                actSmsHint.setText("برای اعمال خودکار پیامک فروشنده، دسترسی خواندن پیامک لازم است — یا کد را دستی وارد کنید.");
                actSmsHint.setTextColor(Theme.MUTED);
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

    // ---- QR scan (offline pairing: seller shows, customer scans) ----

    private void scanPack() {
        if (!camGranted()) {
            pendingScan = REQ_SCAN_PACK;
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAM);
            return;
        }
        launchScan(REQ_SCAN_PACK);
    }

    private void scanCard() {
        if (!camGranted()) {
            pendingScan = REQ_SCAN_CARD;
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAM);
            return;
        }
        launchScan(REQ_SCAN_CARD);
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

    private void launchScan(int which) {
        try {
            Intent i = new Intent(this, ScanActivity.class);
            if (which == REQ_SCAN_PACK) {
                i.putExtra(ScanActivity.EXTRA_TITLE, "اسکن QR کد فعال‌سازی");
                i.putExtra(ScanActivity.EXTRA_HINT, "QR فروشنده را جلوی دوربین بگیرید…");
            } else {
                i.putExtra(ScanActivity.EXTRA_TITLE, "اسکن QR کارت اتصال");
                i.putExtra(ScanActivity.EXTRA_HINT, "QR کارت اتصال را جلوی دوربین بگیرید…");
            }
            startActivityForResult(i, which);
        } catch (Exception e) {
            kit.toast("باز کردن دوربین ممکن نشد");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        try {
            if (res != RESULT_OK || data == null) return;
            String code = data.getStringExtra(ScanActivity.EXTRA_CODE);
            if (code == null || code.isEmpty()) return;
            if (req == REQ_SCAN_PACK) doActivate(code);
            else if (req == REQ_SCAN_CARD) applyCard(code);
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
                if (!Net.wifi(this)) {
                    // Keep it staged: the next onResume retries automatically on Wi-Fi.
                    LicenseStore.addPendingSms(this, null, s.card);
                    long now = System.currentTimeMillis();
                    if (now - lastCardNag > 60000) {
                        lastCardNag = now;
                        kit.toast("کارت اتصال رسید؛ برای ثبت خودکار به وای‌فای فروشگاه وصل شوید");
                    }
                } else applyCard(s.card);
            }
        } catch (Exception ignored) { }
    }

    // ---- automatic server connection ----

    private void buildConnCard(LinearLayout box) {
        LinearLayout c = Card3D.card(this, Theme.SUCCESS);
        c.addView(Card3D.stepRow(this, kit, "۴", Theme.SUCCESS, "اتصال خودکار به سرور فروشگاه"),
                kit.lp(-1, -2));
        c.addView(kit.gap(6));
        connStatus = kit.text(connSummary(), 13, connConfiguredColor(), false);
        c.addView(connStatus, kit.lp(-1, -2));
        c.addView(kit.hint("✦ اگر فروشنده کارت اتصال را پیامک کند، خودکار ثبت و تست می‌شود — بدون چسباندن."),
                kit.lp(-1, -2));
        final EditText fCard = kit.edit("کارت اتصال فروشنده (یک متن طولانی)…", "");
        fCard.setMinLines(3);
        try {
            fCard.setTypeface(Typeface.MONOSPACE);
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams p = kit.lp(-1, -2);
        p.setMargins(0, Theme.dp(6), 0, Theme.dp(6));
        c.addView(fCard, p);
        LinearLayout row = kit.h();
        row.addView(kit.btnGhost("چسباندن", Theme.MUTED, v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.getPrimaryClip() != null
                        && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                    if (t != null) fCard.setText(t.toString().trim());
                }
            } catch (Exception ignored) { }
        }), kit.wlp(1f));
        row.addView(kit.space(8));
        row.addView(kit.btnGold("ثبت و تست اتصال", v -> applyCard(txt(fCard))), kit.wlp(1f));
        c.addView(row, kit.lp(-1, -2));
        c.addView(kit.gap(6));
        c.addView(kit.btnGhost("اسکن QR کارت اتصال", Theme.SUCCESS, v -> scanCard()), kit.lp(-1, -2));
        c.addView(kit.hint("کارت اتصال را فروشنده برایتان می‌فرستد؛ با یک لمس، "
                + "آدرس سرور و همه تنظیمات به‌صورت خودکار ثبت و تست می‌شود — "
                + "نیازی به وارد کردن هیچ عددی نیست."), kit.lp(-1, -2));
        Card3D.mount(box, c);
    }

    private String connSummary() {
        try {
            if (settings.connConfigured()) {
                return "✓ اتصال قبلاً تنظیم شده (سرور " + settings.maskedHost() + ")";
            }
        } catch (Exception ignored) { }
        return "هنوز تنظیم نشده — کارت را بالا بچسبانید یا QR آن را اسکن کنید.";
    }

    private int connConfiguredColor() {
        try {
            return settings.connConfigured() ? Theme.SUCCESS : Theme.MUTED;
        } catch (Exception e) {
            return Theme.MUTED;
        }
    }

    private void applyCard(String pasted) {
        if (!Net.wifi(this)) {
            kit.toast("ثبت کارت اتصال فقط با وای‌فای فروشگاه ممکن است — به وای‌فای وصل شوید");
            return;
        }
        if (pasted.isEmpty()) {
            kit.toast("کارت اتصال را بچسبانید");
            return;
        }
        License.DbProfile prof = License.parseDbCard(pasted, device);
        if (prof == null) {
            kit.toast("کارت معتبر نیست؛ مطمئن شوید کارت همین گوشی را چسبانده‌اید");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(prof.port.trim());
        } catch (Exception e) {
            kit.toast("کارت خراب است؛ از فروشنده کارت تازه بخواهید");
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
                try {
                    if (connStatus != null) {
                        connStatus.setText(connSummary());
                        connStatus.setTextColor(connConfiguredColor());
                    }
                } catch (Exception ignored) { }
            }

            @Override
            public void fail(String faError) {
                kit.toast(faError == null || faError.isEmpty()
                        ? "اتصال برقرار نشد؛ به وای‌فای فروشگاه وصل شوید و دوباره تلاش کنید"
                        : faError);
            }
        });
    }

    // ---- support ----

    private void buildSupportCard(LinearLayout box) {
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        c.addView(Card3D.stepRow(this, kit, "✦", Theme.GOLD, "پشتیبانی میلانو"),
                kit.lp(-1, -2));
        c.addView(kit.gap(6));
        c.addView(kit.btnGhost("🌐  " + Brand.SITE_LABEL, Theme.GOLD,
                v -> Brand.openSite(this)), kit.lp(-1, -2));
        for (String ph : Brand.PHONES) {
            final String fph = ph;
            c.addView(kit.btnGhost("📞  " + Money.fa(ph), Theme.TEAL,
                    v -> Brand.dial(this, fph)), kit.lp(-1, -2));
        }
        TextView tag = kit.text(Brand.TAGLINE, 11.5f, Theme.MUTED, false);
        tag.setGravity(Gravity.CENTER);
        c.addView(tag, kit.lp(-1, -2));
        Card3D.mount(box, c);
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
