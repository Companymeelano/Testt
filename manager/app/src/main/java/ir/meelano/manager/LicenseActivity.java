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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ir.meelano.manager.core.Brand;
import ir.meelano.manager.core.DeviceId;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LanDiscover;
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
import ir.meelano.manager.data.NetRoute;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.ui.Card3D;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;
import ir.meelano.licensing.License;

/**
 * Offline license gate (v31 steps): page 1 asks ONLY the name + contact
 * number, then a beautiful fixed waiting page holds until the license
 * lands; page 2 asks ONLY the server address + DB name and finds the
 * reachable SQL Server itself (typed host, transplanted subnet, gateway,
 * common octets — port 1433 hidden). Everything activates silently:
 * no mechanism talk, no notification. Remote revoke/block enforcement
 * stays, plus the luxury gold designer signature on every page.
 * Blocks MainActivity until {@link LicenseStore#unlocked}.
 */
public class LicenseActivity extends Activity {

    private static final int REQ_RECV = 906;

    private Kit kit;
    private Settings settings;
    private Repo repo;
    private String device = "";

    private TextView recvHint;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TextView dotsView;
    private android.widget.ImageView waitLogo;
    private int dotsN;
    private boolean logoBig;
    private final Runnable dotsTick = new Runnable() {
        @Override
        public void run() {
            try {
                dotsN = (dotsN + 1) % 4;
                if (dotsView != null) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < dotsN; i++) b.append("●");
                    for (int i = dotsN; i < 3; i++) b.append("○");
                    dotsView.setText(b.toString());
                }
                if (waitLogo != null) {
                    logoBig = !logoBig;
                    float sc = logoBig ? 1.07f : 1.0f;
                    waitLogo.animate().scaleX(sc).scaleY(sc).setDuration(650).start();
                }
            } catch (Exception ignored) { }
            uiHandler.postDelayed(dotsTick, 700);
        }
    };

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
        try {
            if (dotsView != null) {
                uiHandler.removeCallbacks(dotsTick);
                uiHandler.post(dotsTick);
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onPause() {
        try {
            uiHandler.removeCallbacks(dotsTick);
        } catch (Exception ignored) { }
        try {
            unregisterReceiver(smsPing);
        } catch (Exception ignored) { }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopDots();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        try {
            if (req == REQ_RECV) {
                updateRecvHint();
                buildUi();
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
        stopDots();
        boolean conn = connConfigured();
        if (!s.ok && !LicenseStore.requested(this)) buildRequestPage(box);
        else if (!s.ok) buildWaitPage(box);
        else if (!conn) buildConnPage(box, s);
        else buildDonePage(box, s);

        if (Tamper.isRooted()) {
            box.addView(kit.hint("⚠ گوشی روت شده است؛ در صورت مشکل با پشتیبانی در میان بگذارید."),
                    kit.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(box);
        setContentView(sv);
    }

    private void stopDots() {
        try {
            uiHandler.removeCallbacks(dotsTick);
        } catch (Exception ignored) { }
        dotsView = null;
        waitLogo = null;
    }

    // ================= page 1: name + contact, nothing else =================

    private void buildRequestPage(LinearLayout box) {
        Card3D.mount(box, Card3D.hero(this, kit, R.drawable.lic_shield, Theme.GOLD,
                "میلانو", "قدم ۱ از ۲ • فعال‌سازی", "✦ شروع", Theme.GOLD));
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        c.addView(Card3D.stepRow(this, kit, "✦", Theme.GOLD, "خوش آمدید"), kit.lp(-1, -2));
        c.addView(kit.text("نام و شماره‌تان را بنویسید؛ بقیه‌اش با ما.",
                13f, Theme.TEXT, false), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        c.addView(deviceCodeView(), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        final EditText fName = kit.edit("نام", LicenseStore.distName(this));
        final EditText fPhone = kit.edit("شماره تماس", LicenseStore.contactPhone(this));
        try {
            fPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        } catch (Exception ignored) { }
        for (EditText e : new EditText[]{fName, fPhone}) {
            LinearLayout.LayoutParams lp = kit.lp(-1, -2);
            lp.setMargins(0, Theme.dp(4), 0, Theme.dp(4));
            c.addView(e, lp);
        }
        c.addView(kit.btnGold("✦ شروع", v -> requestStart(txt(fName), txt(fPhone))),
                kit.lp(-1, -2));
        Card3D.mount(box, c);
        buildDevFooter(box);
    }

    /** Small tap-to-copy device code, shared by pages 1 and wait. */
    private LinearLayout deviceCodeView() {
        LinearLayout v = kit.v();
        TextView l = kit.text("کد دستگاه (برای کپی لمس کنید)", 11.5f, Theme.MUTED, true);
        l.setGravity(Gravity.CENTER);
        v.addView(l, kit.lp(-1, -2));
        TextView dev = kit.text(device.isEmpty() ? "—" : prettyDev(device), 19,
                Theme.GOLD_SOFT, true);
        try {
            dev.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        } catch (Exception ignored) { }
        dev.setGravity(Gravity.CENTER);
        Theme.pressable(dev);
        dev.setOnClickListener(vv -> {
            copyText("کد دستگاه میلانو", device);
            kit.toast("کد دستگاه کپی شد");
        });
        v.addView(dev, kit.lp(-1, -2));
        return v;
    }

    private void requestStart(String dist, String phone) {
        if (dist == null || dist.trim().isEmpty()) {
            kit.toast("نام را وارد کنید");
            return;
        }
        if (SmsIo.cleanPhone(phone).length() < 10) {
            kit.toast("شماره تماس معتبر وارد کنید (مثلاً 09123456789)");
            return;
        }
        LicenseStore.setDistName(this, dist);
        LicenseStore.setContactPhone(this, phone);
        LicenseStore.setRequested(this, true);
        shareRequest(dist, phone);
        buildUi();
    }

    private void shareRequest(String name, String phone) {
        String msg = buildRequestMsg(name, phone);
        copyText("درخواست فعال‌سازی", msg);
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, msg);
            startActivity(Intent.createChooser(i, "ارسال درخواست"));
        } catch (Exception e) {
            kit.toast("متن درخواست در حافظه کپی شد");
        }
    }

    // ================= wait: fixed until the license lands =================

    private void buildWaitPage(LinearLayout box) {
        Card3D.mount(box, Card3D.hero(this, kit, R.drawable.lic_shield, Theme.TEAL,
                "میلانو", "قدم ۱ از ۲ • فعال‌سازی", "✦ در راه است", Theme.TEAL));
        LinearLayout c = Card3D.card(this, Theme.TEAL);
        waitLogo = kit.logo(96);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(Theme.dp(96), Theme.dp(96));
        lp.gravity = Gravity.CENTER;
        c.addView(waitLogo, lp);
        c.addView(kit.gap(6));
        LinearLayout whead = kit.h();
        whead.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = kit.text("در انتظار دریافت لایسنس", 17f, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        whead.addView(t, kit.wlp(1f));
        whead.addView(refreshIconView(), kit.lp(-2, -2));
        c.addView(whead, kit.lp(-1, -2));
        dotsView = kit.text("○○○", 22, Theme.GOLD, true);
        dotsView.setGravity(Gravity.CENTER);
        dotsView.setHeight(Theme.dp(30));
        c.addView(dotsView, kit.lp(-1, -2));
        TextView sub = kit.text("کلید طلایی‌تان در راه است؛ کمی صبر کنید ✦",
                13f, Theme.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        c.addView(sub, kit.lp(-1, -2));
        recvHint = kit.hint("");
        recvHint.setGravity(Gravity.CENTER);
        c.addView(recvHint, kit.lp(-1, -2));
        updateRecvHint();
        if (!recvGranted()) {
            c.addView(kit.btnGhost("فعال‌سازی دریافت خودکار", Theme.TEAL,
                    v -> ensureRecvPerm()), kit.lp(-1, -2));
        }
        c.addView(kit.gap(6));
        c.addView(deviceCodeView(), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        LinearLayout row = kit.h();
        row.addView(kit.btnGhost("↻ ارسال مجدد", Theme.MUTED, v -> shareRequest(
                LicenseStore.distName(this), LicenseStore.contactPhone(this))), kit.wlp(1f));
        row.addView(kit.space(8));
        final android.widget.Button[] recall = new android.widget.Button[1];
        recall[0] = kit.btnGold("✦ دریافت کلید", v -> recallFlow(recall[0]));
        row.addView(recall[0], kit.wlp(1f));
        c.addView(row, kit.lp(-1, -2));
        Card3D.mount(box, c);
        buildDevFooter(box);
        startDots();
    }

    private void startDots() {
        stopDots();
        dotsN = 0;
        uiHandler.post(dotsTick);
    }


    // ================= page 2: server + DB, the app finds the rest =================

    /**
     * Luxury manual recall (v32): no mechanism talk — the button says the
     * key is on its way, and either the license lands or one smart line
     * explains the wait. Also sweeps the clipboard invisibly.
     */
    private void recallFlow(final android.widget.Button btn) {
        if (!recvGranted()) {
            ensureRecvPerm();
            return;
        }
        try {
            btn.setEnabled(false);
            btn.setText("در حال فعال‌سازی…");
        } catch (Exception ignored) { }
        uiHandler.postDelayed(() -> {
            try {
                consumeSms();
                if (LicenseStore.check(LicenseActivity.this).ok) {
                    buildUi();
                    return;
                }
                String cl = clipText();
                if (cl != null && !cl.isEmpty()
                        && (extractPackToken(cl) != null || extractCardLine(cl) != null)) {
                    smartReceive(cl);
                    return;
                }
                kit.toast("هنوز کلیدی نرسیده؛ کمی صبر کنید ✦");
            } catch (Exception ignored) { }
            try {
                btn.setEnabled(true);
                btn.setText("✦ دریافت کلید");
            } catch (Exception ignored2) { }
        }, 1200);
    }

    /**
     * One-tap refresh (v32): re-apply anything staged, verdict the network,
     * rebuild only when the state actually moved (typed text is preserved).
     */
    private void refreshFlow() {
        boolean licBefore = false;
        boolean connBefore = false;
        try {
            licBefore = LicenseStore.check(this).ok;
            connBefore = connConfigured();
        } catch (Exception ignored) { }
        try {
            consumeSms();
        } catch (Exception ignored) { }
        updateRecvHint();
        boolean moved = false;
        try {
            moved = LicenseStore.check(this).ok != licBefore
                    || connConfigured() != connBefore;
        } catch (Exception ignored) { }
        if (moved) {
            buildUi();
            return;
        }
        try {
            kit.toast(Net.online(this) ? "✓ بررسی شد"
                    : "اینترنت قطع است — وصل شوید و دوباره بزنید");
        } catch (Exception ignored) { }
    }

    /** Circular gold refresh icon for the card headers. */
    private TextView refreshIconView() {
        TextView v = kit.text("↻", 22, Theme.GOLD_SOFT, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Theme.ghostButton(Theme.GOLD));
        int p = Theme.dp(8);
        v.setPadding(p, Theme.dp(2), p, Theme.dp(2));
        try {
            v.setMinWidth(Theme.dp(44));
            v.setMinHeight(Theme.dp(44));
        } catch (Exception ignored) { }
        Theme.pressable(v);
        v.setOnClickListener(vv -> refreshFlow());
        return v;
    }

    private void buildConnPage(LinearLayout box, LicenseStore.Status s) {
        Card3D.mount(box, Card3D.hero(this, kit, R.drawable.lic_bolt, Theme.GOLD,
                "میلانو", "قدم ۲ از ۲ • اتصال", "✓ فعال شد", Theme.SUCCESS));
        LinearLayout lc = Card3D.card(this, Theme.SUCCESS);
        lc.addView(Card3D.stepRow(this, kit, "✓", Theme.SUCCESS, "لایسنس"), kit.lp(-1, -2));
        lc.addView(kit.kv("وضعیت", "✓ فعال", Theme.SUCCESS), kit.lp(-1, -2));
        lc.addView(kit.kv("طرح", "«" + License.planFa(s.plan) + "»", Theme.GOLD_SOFT),
                kit.lp(-1, -2));
        if (s.plan == License.P_PERM) {
            lc.addView(kit.kv("اعتبار", "دائمی ♾", Theme.SUCCESS), kit.lp(-1, -2));
        } else {
            lc.addView(kit.kv("اعتبار", Money.fa(String.valueOf(s.daysLeft)) + " روز",
                    leftColor(s.daysLeft)), kit.lp(-1, -2));
        }
        Card3D.mount(box, lc);
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        LinearLayout chead = kit.h();
        chead.setGravity(Gravity.CENTER_VERTICAL);
        chead.addView(Card3D.stepRow(this, kit, "✦", Theme.GOLD, "اتصال به فروشگاه"), kit.wlp(1f));
        chead.addView(refreshIconView(), kit.lp(-2, -2));
        c.addView(chead, kit.lp(-1, -2));
        c.addView(kit.text("کلید رسید ✦ حالا آدرس فروشگاه را بنویسید.",
                13f, Theme.TEXT, false), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        final EditText fIp = kit.edit("آدرس سرور", "");
        try {
            fIp.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        } catch (Exception ignored) { }
        final EditText fDb = kit.edit("نام دیتابیس", "");
        for (EditText e : new EditText[]{fIp, fDb}) {
            LinearLayout.LayoutParams lp = kit.lp(-1, -2);
            lp.setMargins(0, Theme.dp(4), 0, Theme.dp(4));
            c.addView(e, lp);
        }
        final android.widget.Button[] btn = new android.widget.Button[1];
        btn[0] = kit.btnGold("✦ اتصال", v -> connectSmart(txt(fIp), txt(fDb), btn[0]));
        c.addView(btn[0], kit.lp(-1, -2));
        Card3D.mount(box, c);
        buildDevFooter(box);
    }

    /**
     * One address in, a working SQL Server out: the typed host first, then
     * the phone's own subnet (transplanted octet, gateway, common octets).
     * Login probes run in parallel — the first winner is saved as lan/wan
     * by its nature. Port 1433 is never shown.
     */
    private void connectSmart(String ip, String db, final android.widget.Button btn) {
        final String fIp = LanDiscover.clean(ip);
        final String fDb = db == null ? "" : db.trim();
        if (fIp.isEmpty()) {
            kit.toast("آدرس سرور را وارد کنید");
            return;
        }
        if (fDb.isEmpty()) {
            kit.toast("نام دیتابیس را وارد کنید");
            return;
        }
        if (!Net.online(this)) {
            kit.toast("اینترنت قطع است — وصل شوید و دوباره بزنید");
            return;
        }
        try {
            btn.setEnabled(false);
            btn.setText("در حال یافتن سرور…");
        } catch (Exception ignored) { }
        new Thread(() -> {
            List<String> cands = LanDiscover.find(fIp, LanDiscover.PORT);
            String winner = null;
            int tables = -1;
            if (!cands.isEmpty()) {
                ExecutorService pool =
                        Executors.newFixedThreadPool(Math.min(6, cands.size()));
                try {
                    List<Future<int[]>> fs = new ArrayList<>();
                    for (final String h : cands) {
                        fs.add(pool.submit(() -> {
                            try {
                                int n = SmartLink.probeTables(h, LanDiscover.PORT, fDb,
                                        License.SQL_USER, License.SQL_PASS);
                                return new int[]{n};
                            } catch (Exception e) {
                                return new int[]{-1};
                            }
                        }));
                    }
                    long deadline = System.currentTimeMillis() + 20000;
                    for (int i = 0; i < fs.size(); i++) {
                        long left = deadline - System.currentTimeMillis();
                        if (left <= 0) break;
                        try {
                            int[] r = fs.get(i).get(left, TimeUnit.MILLISECONDS);
                            if (r != null && r[0] >= 0) {
                                winner = cands.get(i);
                                tables = r[0];
                                break;
                            }
                        } catch (Exception ignored) { }
                    }
                } finally {
                    pool.shutdownNow();
                }
            }
            final String win = winner;
            final int winN = tables;
            runOnUiThread(() -> {
                try {
                    btn.setEnabled(true);
                    btn.setText("✦ اتصال");
                } catch (Exception ignored) { }
                if (isFinishing()) return;
                if (win == null) {
                    boolean vpn = NetRoute.isVpnActive(LicenseActivity.this);
                    kit.toast("سرور پیدا نشد");
                    SmartLink.diagnoseExplicit(LicenseActivity.this, "", fIp,
                            LanDiscover.PORT, fDb,
                            License.SQL_USER, License.SQL_PASS, vpn, r -> {
                                if (isFinishing()) return;
                                LinkPanel.showFixSheet(LicenseActivity.this, r,
                                        () -> connectSmart(fIp, fDb, btn),
                                        () -> LinkPanel.showDiagnoseExplicit(
                                                LicenseActivity.this, "", fIp,
                                                LanDiscover.PORT, fDb,
                                                License.SQL_USER, License.SQL_PASS));
                            });
                    return;
                }
                try {
                    Boolean priv = SmartLink.isPrivate(win);
                    boolean wifi = Net.wifi(LicenseActivity.this);
                    String lan = (priv == null || priv) ? win : "";
                    String wan = (priv != null && !priv) ? win : "";
                    if (!lan.isEmpty() && !win.equals(fIp)) {
                        Boolean tp = SmartLink.isPrivate(fIp);
                        if (tp != null && !tp) wan = fIp;
                    }
                    settings.saveSmart(lan, wan, String.valueOf(LanDiscover.PORT), fDb,
                            License.SQL_USER, License.SQL_PASS);
                    settings.setLinkLast(!lan.isEmpty() && (wifi || wan.isEmpty())
                            ? SmartLink.LAN : SmartLink.WAN);
                } catch (Exception ignored) { }
                kit.toast("✓ وصل شد • " + Money.fa(String.valueOf(winN)) + " جدول در دسترس");
                copySiteLine(win, LanDiscover.PORT, fDb);
                buildUi();
            });
        }).start();
    }

    /** The typed site, copied for the seller (no seller number is asked anymore). */
    private void copySiteLine(String ip, int port, String db) {
        try {
            String line = License.siteLine(device, ip, String.valueOf(port), db);
            String dist = LicenseStore.distName(this);
            copyText("مشخصات اتصال",
                    "مشخصات اتصال مشتری\n" + line + (dist.isEmpty() ? "" : "\nپخش: " + dist));
            kit.toast("مشخصات اتصال کپی شد؛ برای فروشنده بفرستید");
        } catch (Exception ignored) { }
    }

    // ================= done =================

    private void buildDonePage(LinearLayout box, LicenseStore.Status s) {
        Card3D.mount(box, Card3D.hero(this, kit, R.drawable.lic_shield, Theme.SUCCESS,
                "میلانو", "✓ آماده ورود", "✓ آماده", Theme.SUCCESS));
        LinearLayout c = Card3D.card(this, Theme.SUCCESS);
        c.addView(Card3D.stepRow(this, kit, "✓", Theme.SUCCESS, "همه‌چیز آماده است"),
                kit.lp(-1, -2));
        c.addView(kit.kv("لایسنس", "✓ فعال • «" + License.planFa(s.plan) + "»", Theme.SUCCESS),
                kit.lp(-1, -2));
        if (s.plan == License.P_PERM) {
            c.addView(kit.kv("اعتبار", "دائمی ♾", Theme.SUCCESS), kit.lp(-1, -2));
        } else {
            c.addView(kit.kv("زمان باقی‌مانده",
                    Money.fa(String.valueOf(s.daysLeft)) + " روز", leftColor(s.daysLeft)),
                    kit.lp(-1, -2));
        }
        c.addView(kit.kv("اتصال به سرور", "✓ تنظیم شده", Theme.SUCCESS), kit.lp(-1, -2));
        c.addView(kit.gap(4));
        c.addView(kit.btnGold("ورود به برنامه ⇤", v -> enterApp()), kit.lp(-1, -2));
        Card3D.mount(box, c);
        buildDevFooter(box);
    }

    private boolean connConfigured() {
        try {
            return settings.connConfigured();
        } catch (Exception e) {
            return false;
        }
    }

    // ---- status ----


    private static int leftColor(long daysLeft) {
        if (daysLeft <= 7) return Theme.DANGER;
        if (daysLeft <= 30) return Theme.WARNING;
        return Theme.SUCCESS;
    }

    // ---- network row (lives inside the master frame) ----


    // ---- the one luxury frame: request + receive + network, all in one ----


    private String buildRequestMsg(String dist, String phone) {
        String line = License.requestLine(device, dist, "", dist, phone, "");
        String use = Usage.reportLine(this, device);
        StringBuilder b = new StringBuilder("درخواست فعال‌سازی میلانو\n").append(line);
        if (!use.isEmpty()) b.append('\n').append(use);
        b.append("\nنام: ").append(dist.trim());
        b.append("\nتماس: ").append(phone.trim());
        return b.toString();
    }

    /** One button: validate, then direct SMS when the seller number is filled, share sheet otherwise. */

    /** Zero-copy path: the request flies straight to the seller's phone. */




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
            kit.toast("کلیدی پیدا نشد");
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
                kit.toast("برای دریافت خودکار کلید، دسترسی را تأیید کنید");
                requestPermissions(new String[]{android.Manifest.permission.RECEIVE_SMS}, REQ_RECV);
            }
        } catch (Exception ignored) { }
    }

    private void updateRecvHint() {
        if (recvHint == null) return;
        try {
            if (recvGranted()) {
                recvHint.setText("✦ دریافت خودکار فعال است — فقط منتظر بمانید.");
                recvHint.setTextColor(Theme.TEAL);
            } else {
                recvHint.setText("برای دریافت خودکار کلید، اجازه را تأیید کنید.");
                recvHint.setTextColor(Theme.MUTED);
            }
        } catch (Exception ignored) { }
    }

    private void doActivate(String in) {
        if (in.isEmpty()) {
            kit.toast("کلید را وارد کنید");
            return;
        }
        LicenseStore.Status st = LicenseStore.activate(this, in);
        if (st.ok) {
            kit.toast("✦ فعال شد — خوش آمدید!");
            buildUi();
        } else {
            kit.toast(st.fa);
        }
    }

    /** Manual retry: take a staged card/pack and try it right now. */





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
                    kit.toast("✦ فعال شد — خوش آمدید!");
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
            kit.toast("کارتی پیدا نشد");
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
        luxurySig(c);
        Card3D.mount(box, c);
    }

    /** Metallic-gold English designer signature with a soft 3D shadow. */
    private void luxurySig(LinearLayout c) {
        try {
            c.addView(kit.gap(6));
            TextView over = kit.text("D E S I G N E D   B Y", 10f, Theme.MUTED, true);
            over.setGravity(Gravity.CENTER);
            c.addView(over, kit.lp(-1, -2));
            final TextView name = kit.text("Milad Yaghoubi", 22, Theme.GOLD_SOFT, true);
            name.setGravity(Gravity.CENTER);
            try {
                name.setTypeface(Theme.face(true));
                name.setLetterSpacing(0.06f);
                name.setShadowLayer(5, 0, 3, 0x80000000);
            } catch (Exception ignored) { }
            c.addView(name, kit.lp(-1, -2));
            name.post(() -> {
                try {
                    int h = name.getHeight();
                    if (h <= 0) h = Theme.dp(28);
                    name.getPaint().setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                            new int[]{0xFFFFF6DE, 0xFFE9C37C, 0xFF8A6420, 0xFFF1D493, 0xFFFFF6DE},
                            new float[]{0f, 0.35f, 0.55f, 0.75f, 1f},
                            android.graphics.Shader.TileMode.CLAMP));
                    name.invalidate();
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
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





    // ---------- step 2: the customer's own server ----------


    /** Prefer the winner matching the current transport for SITE1 + last-path. */


    /** Report the typed site back to the seller (full customer record). */

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
