package ir.meelano.manager;

import ir.meelano.manager.core.Diag;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.AtiranPreflight;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Finger;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.MarketWeather;
import ir.meelano.manager.core.UpdateCenter;
import ir.meelano.manager.ui.WhatsNew;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.core.SmartLink;
import ir.meelano.manager.ui.LinkPanel;
import ir.meelano.manager.data.NetRoute;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.PersianText;
import ir.meelano.manager.core.Notify;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.WarehouseSync;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.SmsIo;
import ir.meelano.manager.core.Usage;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.screens.AnbarScreen;
import ir.meelano.manager.screens.AssistantScreen;
import ir.meelano.manager.screens.CashScreen;
import ir.meelano.manager.screens.CloseoutScreen;
import ir.meelano.manager.screens.ManagerAlertsScreen;
import ir.meelano.manager.screens.PickWaveScreen;
import ir.meelano.manager.screens.SyncQueueScreen;
import ir.meelano.manager.screens.ChequesScreen;
import ir.meelano.manager.screens.CustomersScreen;
import ir.meelano.manager.screens.DuesScreen;
import ir.meelano.manager.screens.HomeScreen;
import ir.meelano.manager.screens.MoneyScreen;
import ir.meelano.manager.screens.MoreScreen;
import ir.meelano.manager.screens.ProductsScreen;
import ir.meelano.manager.screens.ProfitScreen;
import ir.meelano.manager.screens.ProductionScreen;
import ir.meelano.manager.screens.ReportsScreen;
import ir.meelano.manager.screens.Screen;
import ir.meelano.manager.screens.ResidScreen;
import ir.meelano.manager.screens.ScoreScreen;
import ir.meelano.manager.screens.SearchScreen;
import ir.meelano.manager.screens.SettingsScreen;
import ir.meelano.manager.screens.TradeScreen;
import ir.meelano.manager.screens.AnalyticsScreen;
import ir.meelano.manager.screens.ProductFileScreen;
import ir.meelano.manager.screens.ShomarshScreen;
import ir.meelano.manager.screens.TahvilScreen;
import ir.meelano.manager.screens.TakmilScreen;
import ir.meelano.manager.screens.UsersScreen;
import ir.meelano.manager.screens.VisitorsScreen;
import ir.meelano.manager.screens.VisitorDeskScreen;
import ir.meelano.manager.screens.VisitorOrdersScreen;
import ir.meelano.manager.screens.VoiceScreen;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.FisPrint;
import ir.meelano.manager.ui.MeelanoIcons;
import ir.meelano.manager.ui.Pdf;
import ir.meelano.manager.ui.Theme;
import ir.meelano.licensing.License;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MEELANO Manager v7 — management-only shell.
 * Bottom nav (5 hubs) + collapsible brand/sync header + content.
 */
public class MainActivity extends Activity {
    public Kit kit;
    public Repo repo;
    public Settings settings;

    private LinearLayout content;
    /** Column that holds the current page; each render swaps in a fresh page. */
    private LinearLayout contentWrap;
    /** Incremented on every render. Async work captures it and stops once it changes. */
    private int renderGen = 0;
    private PullToRefreshScrollView scroll;
    /** Product search/scanner stays visible above the scrolling catalogue. */
    private LinearLayout productStickyHost;
    /** Sticky brand/sync header collapses as any screen's scroll content moves. */
    private View brandHeaderBlock;
    private int brandHeaderExpandedHeight;
    private boolean brandHeaderCollapsed;
    private android.animation.ValueAnimator brandHeaderAnimator;
    private LinearLayout headerRoot;
    private LinearLayout headerTools;
    private LinearLayout brandRow;
    private LinearLayout brandTitleColumn;
    private LinearLayout headerStatusRow;
    private View headerToolbarGap;
    private View compactMenuGap;
    private android.widget.FrameLayout headerSyncHost;
    private android.widget.FrameLayout compactSyncButton;
    private android.widget.ImageView compactSyncIcon;
    private android.view.View compactMenuButton;
    private int headerWidthDp;
    private int brandMarkExpandedDp;
    private int titleExpandedWidthDp;
    private boolean compactHeaderLayout;
    private String linkKind = "probe";
    private LinearLayout bottomBar;
    /** Host of the bottom bar: holds the sliding selection capsule behind the tabs. */
    private android.widget.FrameLayout bottomHost;
    private View navCapsule;

    private android.net.ConnectivityManager.NetworkCallback linkCb = null;
    private boolean linkCbReg = false;
    private final android.os.Handler linkHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable linkPending = null;
    private TextView licChip;
    /** Separate back utility beside the role-specific destinations, never over the content. */
    private LinearLayout backCell;
    private TextView companyName;
    private android.widget.FrameLayout logoBox;
    private android.widget.ImageView logoImg;
    private TextView logoGlyph;

    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final List<String> history = new ArrayList<>();
    private String currentId = "home";
    /** Intent extra: open this screen instead of the default one. */
    public static final String EXTRA_SCREEN = "ir.meelano.manager.SCREEN";
    private boolean rolePicked;
    private boolean shellStarted;
    private boolean restoredScreen;
    private android.widget.ImageView compactUserAction;
    private android.view.View compactUserCell;
    private boolean unlocked = false;
    private boolean crashRecoveryMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            String savedScreen = savedInstanceState.getString("manager_current_screen", "");
            if (!savedScreen.isEmpty()) {
                currentId = savedScreen;
                restoredScreen = true;
            }
            ArrayList<String> savedHistory = savedInstanceState.getStringArrayList("manager_screen_history");
            if (savedHistory != null) history.addAll(savedHistory);
        }
        if (!LicenseStore.unlocked(this)) {
            toLicense();
            return;
        }
        // License → explicit server/database selection → account login.
        if (!new Settings(this).connConfigured()) {
            toLicense();
            return;
        }
        if (AtiranAuth.gate(this)) {
            toLogin();
            return;
        }
        Theme.init(this);
        Usage.opened(this);
        kit = new Kit(this);
        settings = new Settings(this);
        // Amounts are stored in Rials everywhere; pick up the chosen display unit
        // before anything renders so no screen shows the wrong one.
        Money.setUnit(settings.moneyUnit());
        repo = new Repo(this, settings);
        // A previous fatal startup error would otherwise put the user straight back into
        // the same crash loop. Let them copy the privacy-safe stack report before retrying.
        if (ir.meelano.manager.core.CrashReporter.hasReport(this)) {
            crashRecoveryMode = true;
            showCrashRecovery();
            return;
        }
        splash();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-validate the license every time the app comes to the foreground
        // (expired / clock-tampered devices fall back to the activation screen).
        if (!isFinishing() && !LicenseStore.unlocked(this)) toLicense();
        if (!isFinishing() && LicenseStore.unlocked(this)
                && !new Settings(this).connConfigured()) toLicense();
        // Secure updater / voice / sync run only after a normal launch; recovery stays quiet.
        if (!crashRecoveryMode && !isFinishing() && LicenseStore.unlocked(this)) UpdateCenter.autoCheck(this);
        if (!crashRecoveryMode && !isFinishing() && LicenseStore.unlocked(this)
                && new Settings(this).connConfigured()) {
            ir.meelano.manager.core.Speaker.onResume(this);
        }
        if (!isFinishing() && AtiranAuth.gate(this)) toLogin();
        if (!crashRecoveryMode) Usage.touch(this);
        if (!crashRecoveryMode && repo != null && !isFinishing()
                && RoleStore.WAREHOUSE.equals(effRole())) WarehouseSync.retry(this, null);
        refreshLicChip();
        refreshUserChip();
        if (!crashRecoveryMode) dispatchShown(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("manager_current_screen", currentId);
        outState.putStringArrayList("manager_screen_history", new ArrayList<>(history));
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        dispatchShown(false);
        Usage.paused(this);
        super.onPause();
    }

    private void toLicense() {
        try {
            Intent i = new Intent(this, LicenseActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { Diag.ignored("MainActivity:189", ignored); }
        finish();
    }

    private void toLogin() {
        try {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { Diag.ignored("MainActivity:198", ignored); }
        finish();
    }

    /** A safe landing page after a previous crash; the report stays local until explicitly copied. */
    private void showCrashRecovery() {
        styleBars();
        LinearLayout root = kit.v();
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(Theme.dp(22), Theme.dp(24), Theme.dp(22), Theme.dp(24));
        root.setBackgroundColor(Theme.BG);
        root.addView(kit.logo(82), new LinearLayout.LayoutParams(Theme.dp(82), Theme.dp(82)));
        TextView title = kit.text("گزارش فنی اجرای قبلی", 19f, Theme.TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, kit.lp(-1, -2));
        TextView explain = kit.text(
                "برنامه در اجرای قبلی با خطای فنی روبه‌رو شد. گزارش محلی فقط نسخه، نوع خطا و مسیر کد را دارد؛ رمز، نشانی سرور و اطلاعات فروش در آن نیست و چیزی خودکار ارسال نمی‌شود.",
                12.5f, Theme.MUTED, false);
        explain.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ep = kit.lp(-1, -2);
        ep.setMargins(0, Theme.dp(8), 0, Theme.dp(12));
        root.addView(explain, ep);
        LinearLayout card = kit.card(Theme.DANGER);
        card.addView(kit.text("برای عیب‌یابی، ابتدا گزارش را کپی و برای پشتیبانی بفرستید.",
                12f, Theme.TEXT, true), kit.lp(-1, -2));
        card.addView(kit.btnGhost("کپی گزارش فنی", Theme.INFO, v -> copyCrashReport()),
                kit.lp(-1, -2));
        card.addView(kit.gap(6));
        card.addView(kit.btnGold("ادامه و تلاش دوباره", v -> {
            // Clear only the old report. If the retry crashes again, the uncaught handler
            // writes a fresh report and this recovery screen appears again.
            ir.meelano.manager.core.CrashReporter.clearReport(MainActivity.this);
            crashRecoveryMode = false;
            splash();
        }), kit.lp(-1, -2));
        root.addView(card, kit.lp(-1, -2));
        ScrollView safeScroll = new ScrollView(this);
        safeScroll.setFillViewport(true);
        safeScroll.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -2));
        setContentView(safeScroll);
    }

    private void copyCrashReport() {
        try {
            String report = ir.meelano.manager.core.CrashReporter.readReport(this);
            if (report.isEmpty()) {
                kit.toast("گزارش فنی در دسترس نیست");
                return;
            }
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) {
                kit.toast("کپی گزارش ممکن نشد");
                return;
            }
            cm.setPrimaryClip(android.content.ClipData.newPlainText("گزارش فنی میلانو", report));
            kit.toast("گزارش کپی شد؛ آن را برای پشتیبانی بفرستید");
        } catch (Exception e) {
            kit.toast("کپی گزارش ممکن نشد");
        }
    }

    /** Log out the Atiran user and return to the login screen. */
    public void logout() {
        try {
            AtiranAuth.clearSession(this);
        } catch (Exception ignored) { Diag.ignored("MainActivity:206", ignored); }
        toLogin();
    }

    /** Luxury launch splash with the developer signature, then PIN gate / shell. */
    private void splash() {
        styleBars();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(Theme.dp(28), Theme.dp(28), Theme.dp(28), Theme.dp(28));
        android.widget.ImageView logoV = kit.logo(110);
        root.addView(logoV, new LinearLayout.LayoutParams(Theme.dp(110), Theme.dp(110)));
        TextView t = kit.text("میلانو", 26, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t, kit.lp(-1, -2));
        String splashSub = "داشبورد مدیریتی آتیران";
        try {
            String sr = AtiranAuth.sessionRole(this);
            if (RoleStore.WAREHOUSE.equals(sr)) splashSub = "نسخه انباردار • آتیران";
        } catch (Exception ignored) { Diag.ignored("MainActivity:227", ignored); }
        TextView s = kit.text(splashSub, 12.5f, Theme.MUTED, false);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = kit.lp(-1, -2);
        sp.setMargins(0, Theme.dp(4), 0, Theme.dp(14));
        root.addView(s, sp);
        View line = new View(this);
        line.setBackgroundColor(Theme.alpha(Theme.GOLD, 160));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Theme.dp(120), Theme.dp(2));
        llp.gravity = Gravity.CENTER;
        root.addView(line, llp);
        TextView dev = kit.text("Milad Yaghoobi", 21, Theme.GOLD, true);
        dev.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dp = kit.lp(-1, -2);
        dp.setMargins(0, Theme.dp(12), 0, 0);
        root.addView(dev, dp);
        TextView role = kit.text("طراح و توسعه‌دهنده", 12f, Theme.MUTED, false);
        role.setGravity(Gravity.CENTER);
        root.addView(role, kit.lp(-1, -2));
        TextView ver = kit.text(splashVersion(), 10.5f, Theme.MUTED, false);
        ver.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vp = kit.lp(-1, -2);
        vp.setMargins(0, Theme.dp(10), 0, 0);
        root.addView(ver, vp);
        setContentView(root);
        // Staged luxury intro: the logo pops, lines rise one by one, the gold rule sweeps.
        logoV.setScaleX(0.55f);
        logoV.setScaleY(0.55f);
        logoV.setAlpha(0f);
        logoV.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(650)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
        t.setAlpha(0f);
        t.setTranslationY(Theme.dp(14));
        t.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(250).start();
        s.setAlpha(0f);
        s.animate().alpha(1f).setDuration(500).setStartDelay(450).start();
        line.setPivotX(0f);
        line.setScaleX(0f);
        line.animate().scaleX(1f).setDuration(600).setStartDelay(600).start();
        dev.setAlpha(0f);
        dev.setScaleX(0.92f);
        dev.setScaleY(0.92f);
        dev.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).setStartDelay(750).start();
        role.setAlpha(0f);
        ver.setAlpha(0f);
        role.animate().alpha(1f).setDuration(500).setStartDelay(950).start();
        ver.animate().alpha(1f).setDuration(500).setStartDelay(1100).start();
        root.postDelayed(() -> {
            if (settings.pinEnabled()) pinGate();
            else {
                unlocked = true;
                shell();
            }
        }, 1750);
    }

    private String splashVersion() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return "نسخه " + Money.fa(v == null || v.isEmpty() ? "—" : v);
        } catch (Exception e) {
            return "";
        }
    }

    /** Paint status/navigation bars for the current skin. */
    private void styleBars() {
        try {
            // Keep the app shell inside system-bar insets (including Android 15+ devices).
            if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);
            getWindow().setStatusBarColor(Theme.statusBar());
            getWindow().setNavigationBarColor(Theme.navBar());
            int vis = getWindow().getDecorView().getSystemUiVisibility();
            if (Theme.isLight())
                vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else
                vis &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            getWindow().getDecorView().setSystemUiVisibility(vis);
        } catch (Exception ignored) { Diag.ignored("MainActivity:303", ignored); }
    }

    /** Re-apply the luxury theme and rebuild the shell on the current screen. */
    public void refreshTheme() {
        Theme.apply(this);
        history.clear();
        shell();
    }

    @Override
    protected void onDestroy() {
        try {
            if (linkCbReg) {
                android.net.ConnectivityManager cm =
                        (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                if (cm != null && linkCb != null) cm.unregisterNetworkCallback(linkCb);
            }
        } catch (Exception ignored) { Diag.ignored("MainActivity:321", ignored); }
        linkCbReg = false;
        try {
            if (linkPending != null) linkHandler.removeCallbacks(linkPending);
        } catch (Exception ignored) { Diag.ignored("MainActivity:325", ignored); }
        try { repo.close(); } catch (Exception ignored) { Diag.ignored("MainActivity:326", ignored); }
        super.onDestroy();
    }

    /** Re-probe the smart link shortly after any network change (debounced). */
    private void watchLink() {
        if (linkCbReg) return;
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return;
            linkCb = new android.net.ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network n) { linkChanged(); }
                @Override
                public void onLost(android.net.Network n) { linkChanged(); }
                @Override
                public void onCapabilitiesChanged(android.net.Network n,
                                                  android.net.NetworkCapabilities c) { linkChanged(); }
            };
            cm.registerDefaultNetworkCallback(linkCb);
            linkCbReg = true;
        } catch (Exception ignored) { Diag.ignored("MainActivity:348", ignored); }
    }

    private void linkChanged() {
        try {
            if (linkPending != null) linkHandler.removeCallbacks(linkPending);
            linkPending = () -> {
                try {
                    if (!isFinishing()) checkConn();
                } catch (Exception ignored) { Diag.ignored("MainActivity:357", ignored); }
            };
            linkHandler.postDelayed(linkPending, 1500);
        } catch (Exception ignored) { Diag.ignored("MainActivity:360", ignored); }
    }

    // ================= PIN gate =================
    private void pinGate() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(Theme.dp(28), Theme.dp(28), Theme.dp(28), Theme.dp(28));
        root.addView(kit.logo(120), new LinearLayout.LayoutParams(Theme.dp(120), Theme.dp(120)));
        TextView t = kit.text("میلانو", 22, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t, kit.lp(-1, -2));
        TextView s = kit.text("رمز عبور مدیریتی را وارد کنید", 12.5f, Theme.MUTED, false);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = kit.lp(-1, -2);
        sp.setMargins(0, Theme.dp(6), 0, Theme.dp(18));
        root.addView(s, sp);
        final TextView dots = kit.text("○ ○ ○ ○", 30, Theme.GOLD, true);
        dots.setGravity(Gravity.CENTER);
        root.addView(dots, kit.lp(-1, -2));
        final StringBuilder pin = new StringBuilder();
        final Runnable paint = () -> {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (i > 0) b.append("  ");
                b.append(i < pin.length() ? "●" : "○");
            }
            dots.setText(b.toString());
            MeelanoIcons.iconize(dots);
        };
        final Runnable submit = () -> {
            if (pin.length() < 4) {
                kit.toast("رمز ۴ رقمی را کامل وارد کنید");
                return;
            }
            if (settings.checkPin(pin.toString())) {
                unlocked = true;
                shell();
            } else {
                kit.toast("رمز اشتباه است");
                pin.setLength(0);
                paint.run();
            }
        };
        int[][] keys = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {-1, 0, -2}};
        for (int[] rowKeys : keys) {
            LinearLayout row = kit.h();
            row.setGravity(Gravity.CENTER);
            for (int k : rowKeys) {
                final int key = k;
                TextView b = kit.text(k == -1 ? "⌫" : (k == -2 ? "✓" : Money.fa(String.valueOf(k))),
                        22, Theme.TEXT, true);
                b.setGravity(Gravity.CENTER);
                b.setBackground(Theme.card());
                b.setPadding(0, Theme.dp(12), 0, Theme.dp(12));
                Theme.pressable(b);
                b.setOnClickListener(v -> {
                    if (key >= 0 && pin.length() < 4) {
                        pin.append((char) ('0' + key));
                        paint.run();
                        if (pin.length() == 4) submit.run();
                    } else if (key == -1 && pin.length() > 0) {
                        pin.setLength(pin.length() - 1);
                        paint.run();
                    } else if (key == -2) {
                        submit.run();
                    }
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Theme.dp(88), -2);
                p.setMargins(Theme.dp(6), Theme.dp(6), Theme.dp(6), Theme.dp(6));
                row.addView(b, p);
            }
            root.addView(row, kit.lp(-1, -2));
        }
        if (settings.fpOn() && Finger.supported(this)) {
            android.widget.Button fp = kit.btnGhost("◉ ورود با اثر انگشت", Theme.GOLD, v -> fingerLogin());
            LinearLayout.LayoutParams flp = kit.lp(-1, -2);
            flp.setMargins(Theme.dp(28), Theme.dp(14), Theme.dp(28), 0);
            root.addView(fp, flp);
            root.post(() -> fingerLogin());
        }
        setContentView(root);
    }

    private void fingerLogin() {
        Finger.auth(this, new Finger.Cb() {
            @Override
            public void ok() {
                unlocked = true;
                shell();
            }

            @Override
            public void fail(String msg) {
                kit.toast(msg == null || msg.isEmpty() ? "اثر انگشت تأیید نشد" : msg);
            }
        });
    }

    // ================= shell =================
    private void shell() {
        if (brandHeaderAnimator != null) {
            brandHeaderAnimator.cancel();
            brandHeaderAnimator = null;
        }
        if (!shellStarted) {
            shellStarted = true;
            AtiranAuth.applySessionRole(this);
            if (AtiranAuth.hasSession(this)) {
                rolePicked = true;
                if (!restoredScreen) currentId = AtiranAuth.startScreen(this);
            }
        }
        if (RoleStore.enabled(this) && !rolePicked) {
            roleGate(null);
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(Theme.background());

        // Expanded header: one brand row with a real SyncPill, then a compact labelled tool row.
        // Scrolling replaces it with a one-line 56dp brand/sync/menu strip.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int widthDp = dm.widthPixels * 160 / dm.densityDpi;
        final boolean tiny = widthDp < 360;
        final boolean compact = widthDp < 520;
        headerWidthDp = widthDp;
        compactHeaderLayout = compact;
        brandMarkExpandedDp = compact ? 56 : 64;
        titleExpandedWidthDp = Math.max(104, widthDp - 24 - brandMarkExpandedDp - 10
                - (compact ? 52 : 0));
        headerRoot = kit.v();
        headerRoot.setPadding(Theme.dp(12), Theme.dp(compact ? 6 : 9), Theme.dp(12), Theme.dp(5));

        // ---- one persistent brand row; the same logo view scales instead of being duplicated ----
        brandRow = kit.h();
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setMinimumHeight(Theme.dp(48));
        brandRow.setClipChildren(false);
        logoBox = new android.widget.FrameLayout(this);
        logoBox.setBackground(Theme.accentAvatar(Theme.GOLD));
        logoGlyph = kit.text("M", 20, Theme.onAccent(Theme.GOLD), true);
        logoGlyph.setGravity(Gravity.CENTER);
        logoGlyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        logoBox.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        logoBox.addView(logoGlyph, new android.widget.FrameLayout.LayoutParams(-1, -1));
        logoImg = new android.widget.ImageView(this);
        logoImg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        try {
            logoImg.setClipToOutline(true);
            logoImg.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View v, android.graphics.Outline o) {
                    o.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
        } catch (Exception ignored) { Diag.ignored("MainActivity:508", ignored); }
        int lpad = Theme.dp(2);
        logoImg.setPadding(lpad, lpad, lpad, lpad);
        logoImg.setVisibility(View.GONE);
        logoBox.addView(logoImg, new android.widget.FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(
                Theme.dp(brandMarkExpandedDp), Theme.dp(brandMarkExpandedDp));
        brandRow.addView(logoBox, logoLp);
        brandRow.addView(kit.space(10));

        // Compute text room instead of letting long names slide underneath the logo.
        LinearLayout titleCol = kit.v();
        brandTitleColumn = titleCol;
        companyName = kit.text(companyTitle(), 22, Theme.TEXT, true);
        companyName.setMaxLines(2);
        companyName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        companyName.setMaxWidth(Theme.dp(titleExpandedWidthDp));
        titleCol.addView(companyName, kit.lp(-1, -2));
        if (Theme.DARK) {
            // Gold-to-champagne gradient across the company name, as in the reference header.
            companyName.addOnLayoutChangeListener((title_v, x1, x2, x3, x4, x5, x6, x7, x8) -> {
                android.graphics.Shader sh = new android.graphics.LinearGradient(0, 0, 0, title_v.getHeight(),
                        Theme.GOLD_SOFT, Theme.GOLD, android.graphics.Shader.TileMode.CLAMP);
                companyName.getPaint().setShader(sh);
                title_v.invalidate();
            });
        }

        // Expanded status line: the reusable SyncPill and a 48dp licence shortcut.
        // Account actions live in the toolbar/menu, avoiding a crowded brand row.
        headerStatusRow = kit.h();
        headerStatusRow.setGravity(Gravity.CENTER_VERTICAL);
        headerStatusRow.setMinimumHeight(Theme.dp(48));
        headerSyncHost = new android.widget.FrameLayout(this);
        headerSyncHost.setMinimumWidth(Theme.dp(compact ? 80 : 104));
        headerStatusRow.addView(headerSyncHost, new LinearLayout.LayoutParams(0, -1, 1f));
        headerStatusRow.addView(kit.space(6));
        licChip = kit.text("", 10.5f, Theme.GOLD_SOFT, true);
        licChip.setBackground(Theme.pill(Theme.GOLD));
        licChip.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), Theme.dp(4));
        licChip.setSingleLine(true);
        licChip.setMinWidth(Theme.dp(48));
        licChip.setMinHeight(Theme.dp(48));
        licChip.setMaxWidth(Theme.dp(64));
        licChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Theme.pressable(licChip);
        licChip.setOnClickListener(v -> openLicense());
        headerStatusRow.addView(licChip, kit.lp(-2, -1));
        titleCol.addView(headerStatusRow, kit.lp(-1, -2));
        brandRow.addView(titleCol, new LinearLayout.LayoutParams(Theme.dp(titleExpandedWidthDp), -2));

        compactSyncButton = new android.widget.FrameLayout(this);
        compactSyncButton.setBackground(Theme.kpi(Theme.PRIMARY));
        compactSyncButton.setContentDescription("وضعیت اتصال؛ لمس برای جزئیات");
        compactSyncIcon = kit.icon(R.drawable.mi_sync, Theme.MUTED, 24);
        compactSyncButton.addView(compactSyncIcon, new android.widget.FrameLayout.LayoutParams(
                Theme.dp(24), Theme.dp(24), Gravity.CENTER));
        compactSyncButton.setVisibility(View.GONE);
        Theme.pressable(compactSyncButton);
        compactSyncButton.setOnClickListener(v -> showLinkPanel());
        brandRow.addView(compactSyncButton, new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(48)));
        compactMenuGap = kit.space(4);
        compactMenuGap.setVisibility(compact ? View.VISIBLE : View.GONE);
        brandRow.addView(compactMenuGap);
        android.widget.FrameLayout menuButton = new android.widget.FrameLayout(this);
        menuButton.setBackground(Theme.ghostButton(Theme.bottomNavAccent()));
        menuButton.setContentDescription("ابزارها، تنظیمات و حساب کاربری");
        android.widget.ImageView menuIcon = kit.icon(R.drawable.mi_more_horiz,
                Theme.sectionText(Theme.bottomNavAccent()), 24);
        menuButton.addView(menuIcon, new android.widget.FrameLayout.LayoutParams(
                Theme.dp(24), Theme.dp(24), Gravity.CENTER));
        menuButton.setVisibility(compact ? View.VISIBLE : View.GONE);
        Theme.pressable(menuButton);
        menuButton.setOnClickListener(v -> showHeaderMenu());
        compactMenuButton = menuButton;
        brandRow.addView(menuButton, new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(48)));

        LinearLayout brandBlock = kit.v();
        brandBlock.setClipChildren(false);
        brandBlock.addView(brandRow, kit.lp(-1, -2));
        brandHeaderBlock = brandBlock;
        brandHeaderExpandedHeight = 0;
        brandHeaderCollapsed = false;
        brandBlock.post(() -> {
            if (brandHeaderBlock == brandBlock && brandBlock.getHeight() > 0)
                brandHeaderExpandedHeight = brandBlock.getHeight();
        });
        headerRoot.addView(brandBlock, kit.lp(-1, -2));
        refreshUserChip();
        updateHeaderSyncPill(linkKind);
        headerToolbarGap = kit.space(compact ? 5 : 8);
        headerRoot.addView(headerToolbarGap);

        // ---- row 2: compact, role-aware tools (the refresh action is intentionally absent) ----
        LinearLayout tools = kit.h();
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackground(Theme.toolbarPanel());
        tools.setPadding(Theme.dp(3), Theme.dp(3), Theme.dp(3), Theme.dp(3));

        // Role-aware toolbar: each person only sees the tools their own role may open.
        if (!tiny && RoleStore.allowed(this, "search"))
            tools.addView(toolCell(toolIcon(R.drawable.mi_search), "جستجو", "جستجو", () -> nav("search")), toolLp());

        final android.widget.ImageView themeIcon = toolIcon(R.drawable.mi_contrast);
        if (!tiny) tools.addView(toolCell(themeIcon, "تم", "تغییر تم روشن / تیره", () -> {
            if (ir.meelano.manager.ui.Ui.motionOk(this))
                themeIcon.animate().rotationBy(180f).setDuration(320).start();
            cycleTheme();
        }), toolLp());

        if (!compact) {
            android.widget.ImageView zoom = toolIcon(R.drawable.mi_zoom);
            tools.addView(toolCell(zoom, "بزرگ‌نمایی", "بزرگ‌نمایی متن", () -> cycleZoom()), toolLp());
        }

        if (RoleStore.allowed(this, "settings"))
            tools.addView(toolCell(toolIcon(R.drawable.mi_settings), "تنظیمات", "تنظیمات", () -> nav("settings")), toolLp());

        // Keep the account action in the same flat duotone icon family as the toolbar.
        android.widget.ImageView account = toolIcon(R.drawable.mi_person);
        compactUserAction = account;
        LinearLayout accountCell = toolCell(account, "حساب", "حساب کاربری، خروج یا تغییر کاربر", () -> userMenu());
        compactUserCell = accountCell;
        tools.addView(accountCell, toolLp());

        for (int i = tools.getChildCount() - 1; i > 0; i--) {
            tools.addView(vdiv(), i, new LinearLayout.LayoutParams(Theme.dp(1), Theme.dp(40)));
        }
        headerTools = tools;
        headerRoot.addView(tools, kit.lp(-1, -2));
        refreshUserChip();
        root.addView(headerRoot, kit.lp(-1, -2));
        LinearLayout productStickyWrap = kit.h();
        productStickyWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        productStickyHost = kit.v();
        productStickyHost.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        productStickyHost.setVisibility(View.GONE);
        LinearLayout.LayoutParams stickyLp = new LinearLayout.LayoutParams(
                widthDp > 720 ? Theme.dp(616) : -1, -2);
        if (widthDp <= 720)
            stickyLp.setMargins(Theme.dp(12), 0, Theme.dp(12), 0);
        productStickyWrap.addView(productStickyHost, stickyLp);
        root.addView(productStickyWrap, kit.lp(-1, -2));
        refreshLicChip();

        // Center the content on wide screens. The sibling dock stays inside the system-safe
        // app window; decor fitting supplies navigation-bar insets, so adding them here would double them.
        scroll = new PullToRefreshScrollView(this);
        scroll.setOnRefreshListener(() -> {
            if ("home".equals(currentId)) renderCurrent();
        });
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            setBrandHeaderCollapsed(y > Theme.dp(48));
        });
        contentWrap = kit.h();
        contentWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(contentWrap, new ScrollView.LayoutParams(-1, -2));
        content = newPage();
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Bottom dock: it is a sibling of the scroll viewport, never an overlay on page content.
        // Decor fitting has already applied the system navigation/gesture inset to this app window.
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomBar.setMinimumHeight(Theme.dp(72));
        bottomBar.setClipChildren(false);
        bottomBar.setClipToPadding(false);
        bottomBar.setPadding(Theme.dp(6), Theme.dp(4), Theme.dp(6), Theme.dp(4));
        bottomHost = new android.widget.FrameLayout(this);
        bottomHost.setBackground(Theme.isNuts() ? Theme.navDock() : Theme.bottomBar());
        bottomHost.setClipChildren(false);
        bottomHost.setClipToPadding(false);
        Theme.lift(bottomHost, Theme.dp(2));
        navCapsule = new View(this);
        navCapsule.setVisibility(View.GONE);
        navCapsule.setBackground(Theme.bottomNavSelected());
        navCapsule.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bottomHost.addView(navCapsule, new android.widget.FrameLayout.LayoutParams(Theme.dp(40), Theme.dp(40),
                Gravity.TOP | Gravity.LEFT));
        bottomHost.addView(bottomBar, new android.widget.FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams hostLp = kit.lp(-1, -2);
        if (Theme.isNuts()) hostLp.setMargins(Theme.dp(10), 0, Theme.dp(10), Theme.dp(6));
        root.addView(bottomHost, hostLp);

        setContentView(root);

        // screens
        reg(new HomeScreen(this));
        reg(new AssistantScreen(this));
        reg(new TradeScreen(this, true));
        reg(new TradeScreen(this, false));
        reg(new MoneyScreen(this, 0));
        reg(new MoneyScreen(this, 1));
        reg(new ChequesScreen(this));
        reg(new CashScreen(this));
        reg(new ProductsScreen(this));
        reg(new ProductFileScreen(this));
        reg(new CustomersScreen(this));
        reg(new VisitorsScreen(this));
        reg(new VisitorDeskScreen(this));
        reg(new VisitorOrdersScreen(this));
        reg(new UsersScreen(this));
        reg(new ProfitScreen(this));
        reg(new ProductionScreen(this));
        reg(new DuesScreen(this));
        reg(new ReportsScreen(this));
        reg(new AnalyticsScreen(this));
        reg(new SettingsScreen(this));
        reg(new MoreScreen(this));
        reg(new SearchScreen(this));
        reg(new VoiceScreen(this));
        reg(new AnbarScreen(this));
        reg(new TahvilScreen(this));
        reg(new ResidScreen(this));
        reg(new TakmilScreen(this));
        reg(new ShomarshScreen(this));
        reg(new ScoreScreen(this));
        reg(new CloseoutScreen(this));
        reg(new ManagerAlertsScreen(this));
        reg(new SyncQueueScreen(this));
        reg(new PickWaveScreen(this));

        buildBottom();
        nav(screens.containsKey(currentId) ? currentId : "home");
        // Deep link from a notification (a new sales invoice opens the handover
        // list). Applied after nav() so every screen is registered and the
        // content view is already built.
        String want = null;
        try {
            android.content.Intent gi = getIntent();
            if (gi != null) want = gi.getStringExtra(EXTRA_SCREEN);
        } catch (Exception ignored) { Diag.ignored("MainActivity:667", ignored); }
        if (want != null && !want.isEmpty() && screens.containsKey(want)) nav(want);
        styleBars();
        checkConn();
        refreshCompany();
        // No startup announcements (what's-new tour or update prompt): the flag is cleared silently.
        try {
            ir.meelano.manager.core.LicenseStore.setTourPending(this, false);
        } catch (Exception ignored) { Diag.ignored("MainActivity:677", ignored); }
        Notify.boot(this);
        syncBackControl();
        watchLink();
    }

    /** Consistent duotone vector icon for the header toolbar. */
    private android.widget.ImageView toolIcon(int res) {
        android.widget.ImageView iv = new android.widget.ImageView(this);
        try {
            iv.setImageResource(res);
            iv.setColorFilter(Theme.sectionText(Theme.PRIMARY));
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        } catch (Exception ignored) { Diag.ignored("MainActivity:toolIcon", ignored); }
        return iv;
    }

    private LinearLayout toolCell(View icon, String label, String desc, Runnable onTap) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setContentDescription(desc);
        cell.setMinimumHeight(Theme.dp(Theme.isNuts() ? 64 : 54));
        Theme.pressable(cell);
        cell.setOnClickListener(v -> onTap.run());
        icon.setClickable(false);
        int ic = Theme.isNuts() ? 38 : 24;
        cell.addView(icon, new LinearLayout.LayoutParams(Theme.dp(ic), Theme.dp(ic)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize((Theme.isNuts() ? 10.5f : 10f) * Theme.fontScale());
        t.setTextColor(Theme.DARK ? Theme.GOLD_SOFT : Theme.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        cell.addView(t, new LinearLayout.LayoutParams(-2, -2));
        return cell;
    }

    /** Cycle through dark, light and the preserved Nuts skin. */
    private void cycleTheme() {
        String current = settings.themeMode();
        String next = "dark".equals(current) ? "light" : "light".equals(current) ? "nuts" : "dark";
        settings.setThemeMode(next);
        kit.toast("nuts".equals(next) ? "تم آجیل و خشکبار فعال شد"
                : "light".equals(next) ? "تم روشن فعال شد" : "تم تیره فعال شد");
        refreshTheme();
    }

    /** Accessible overflow for header controls when the full tool row is collapsed or compact. */
    private void showHeaderMenu() {
        LinearLayout body = kit.v();
        body.setPadding(Theme.dp(14), Theme.dp(10), Theme.dp(14), Theme.dp(14));
        final AlertDialog[] dialog = new AlertDialog[1];
        if (RoleStore.allowed(this, "search"))
            addHeaderMenuAction(body, "جستجو", R.drawable.mi_search, dialog, () -> nav("search"));
        addHeaderMenuAction(body, "تغییر تم روشن، تیره یا آجیل و خشکبار",
                R.drawable.mi_contrast, dialog, this::cycleTheme);
        addHeaderMenuAction(body, "بزرگ‌نمایی متن · " + zoomLabel(settings.zoomIdx()),
                R.drawable.mi_zoom, dialog, this::cycleZoom);
        if (RoleStore.allowed(this, "settings"))
            addHeaderMenuAction(body, "تنظیمات", R.drawable.mi_settings, dialog, () -> nav("settings"));
        if (AtiranAuth.hasSession(this))
            addHeaderMenuAction(body, "حساب کاربری · " + displayName(),
                    R.drawable.mi_person, dialog, this::userMenu);
        addHeaderMenuAction(body, "مجوز و فعال‌سازی", R.drawable.mi_verified_user,
                dialog, this::openLicense);
        addHeaderMenuAction(body, "وضعیت اتصال", R.drawable.mi_wifi,
                dialog, this::showLinkPanel);
        dialog[0] = kit.dialog("ابزارها", body, true);
        dialog[0].show();
    }

    private void addHeaderMenuAction(LinearLayout body, String label, int icon,
                                     AlertDialog[] dialog, Runnable action) {
        LinearLayout item = kit.h();
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(Theme.dp(12), Theme.dp(4), Theme.dp(12), Theme.dp(4));
        item.setMinimumHeight(Theme.dp(48));
        item.setBackground(Theme.ghostButton(Theme.bottomNavAccent()));
        item.addView(kit.icon(icon, Theme.sectionText(Theme.bottomNavAccent()), 24),
                new LinearLayout.LayoutParams(Theme.dp(24), Theme.dp(24)));
        item.addView(kit.space(10));
        TextView text = kit.text(label, 12.5f, Theme.TEXT, true);
        text.setMaxLines(2);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(text, kit.wlp(1f));
        item.setContentDescription(label);
        Theme.pressable(item);
        item.setOnClickListener(v -> {
            if (dialog[0] != null) dialog[0].dismiss();
            action.run();
        });
        LinearLayout.LayoutParams p = kit.lp(-1, -2);
        p.bottomMargin = Theme.dp(6);
        body.addView(item, p);
    }

    private void showLinkPanel() {
        try {
            LinkPanel.showStatus(this, settings, this::checkConn);
        } catch (Exception ignored) { Diag.ignored("MainActivity:linkPanel", ignored); }
    }

    private LinearLayout.LayoutParams toolLp() {
        return new LinearLayout.LayoutParams(0, Theme.dp(Theme.isNuts() ? 64 : 54), 1f);
    }

    /** Hairline divider between header tool buttons. */
    private View vdiv() {
        View d = new View(this);
        d.setBackgroundColor(Theme.alpha(Theme.GOLD, 70));
        return d;
    }

    private void reg(Screen s) {
        screens.put(s.id(), s);
    }

    public Screen screen(String id) {
        return screens.get(id);
    }

    /** The ids of every registered screen, so the command room can list them all. */
    public List<String> screenIds() {
        return new ArrayList<>(screens.keySet());
    }

    // ================= navigation =================
    /** Bottom tabs of the current edition (v30: per role, single app). */
    private String[] tabs = RoleStore.tabsFor(RoleStore.ADMIN);

    private String effRole() {
        try {
            String r = AtiranAuth.sessionRole(this);
            if (r != null && !r.isEmpty()) return r;
        } catch (Exception ignored) { Diag.ignored("MainActivity:745", ignored); }
        return RoleStore.current(this);
    }

    private void buildBottom() {
        bottomBar.removeAllViews();
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String t : RoleStore.tabsFor(effRole())) {
            if (screens.containsKey(t)) ids.add(t);
        }
        if (ids.isEmpty()) ids.add("home");
        tabs = ids.toArray(new String[0]);
        int availableWidthDp = getResources().getDisplayMetrics().widthPixels * 160
                / getResources().getDisplayMetrics().densityDpi;
        boolean compactDock = availableWidthDp < (12 + 52 + tabs.length * 48);
        for (final String id : tabs) {
            final Screen s = screens.get(id);
            if (s == null) continue;
            LinearLayout b = kit.v();
            b.setGravity(Gravity.CENTER);
            b.setMinimumWidth(Theme.dp(48));
            b.setMinimumHeight(Theme.dp(64)); // Every destination remains a 48dp+ touch target.
            b.setPadding(Theme.dp(2), Theme.dp(3), Theme.dp(2), Theme.dp(3));
            b.setClipChildren(false);
            LinearLayout badge = new LinearLayout(this);
            badge.setGravity(Gravity.CENTER);
            badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int iconRes = MeelanoIcons.iconFor(s.glyph());
            // Navigation uses the same 24dp line icon family in every skin.
            if (iconRes != 0) {
                android.widget.ImageView symbol = kit.icon(iconRes, Theme.MUTED, 24);
                symbol.setTag(Boolean.FALSE);
                badge.addView(symbol, new LinearLayout.LayoutParams(Theme.dp(24), Theme.dp(24)));
            } else {
                TextView symbol = kit.text(s.glyph(), 20, Theme.MUTED, true);
                symbol.setGravity(Gravity.CENTER);
                badge.addView(symbol, kit.lp(-2, -2));
            }
            b.addView(badge, new LinearLayout.LayoutParams(Theme.dp(46), Theme.dp(Theme.isNuts() ? 44 : 36)));
            String tl = RoleStore.tabLabel(id);
            if ("more".equals(id) && RoleStore.VISITOR.equals(effRole())) tl = "بیشتر";
            TextView l = kit.text(tl != null ? tl : s.title(), Theme.isNuts() ? 11.5f : 11f,
                    Theme.MUTED, true);
            l.setGravity(Gravity.CENTER);
            l.setSingleLine(true);
            l.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.addView(l, kit.lp(-1, -2));
            View marker = new View(this);
            marker.setVisibility(View.INVISIBLE);
            marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Theme.dp(18), Theme.dp(2));
            mp.setMargins(0, Theme.dp(Theme.isNuts() ? 2 : 3), 0, 0);
            b.addView(marker, mp);
            b.setTag(id);
            b.setContentDescription((tl != null ? tl : s.title()) + "، رفتن به بخش");
            if (!RoleStore.allowed(this, id)) b.setAlpha(0.35f);
            Theme.pressable(b);
            b.setOnClickListener(v -> nav(id));
            bottomBar.addView(b, kit.wlp(1f));
        }
        // Separate utility action (not a sixth destination) keeps in-app back one tap away.
        LinearLayout back = kit.v();
        back.setGravity(Gravity.CENTER);
        back.setTag(BACK_CELL_TAG);
        // On narrow screens, reserve 48dp for each destination and leave Back to Android's
        // system gesture/key rather than shrinking the role-specific targets.
        if (compactDock) back.setVisibility(View.GONE);
        back.setMinimumWidth(Theme.dp(48));
        back.setMinimumHeight(Theme.dp(64));
        back.setContentDescription("بازگشت به صفحه قبلی");
        android.widget.FrameLayout backSurface = new android.widget.FrameLayout(this);
        backSurface.setBackground(Theme.bottomNavIconSelected());
        backSurface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        android.widget.ImageView backArrow = kit.icon(R.drawable.mi_arrow_forward,
                Theme.selectedNavInk(), 24);
        backArrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        backSurface.addView(backArrow, new android.widget.FrameLayout.LayoutParams(
                Theme.dp(48), Theme.dp(48), Gravity.CENTER));
        back.addView(backSurface, new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(48)));
        Theme.pressable(back);
        back.setOnClickListener(v -> goBack());
        bottomBar.addView(back, 0, kit.lp(Theme.dp(52), -1));
        backCell = back;
        paintBottom();
        updateBackControl();
    }

    /** Tag of the back control inside the tab bar, so the tab painter skips it. */
    private static final String BACK_CELL_TAG = "back-cell";

    private int selectedNavIndex = -1;

    /** Slides the selection capsule to the selected tab (animated after the first layout). */
    private void moveCapsule(final boolean animate) {
        if (navCapsule == null || bottomBar == null) return;
        if (selectedNavIndex < 0 || selectedNavIndex >= bottomBar.getChildCount()) {
            navCapsule.setVisibility(View.GONE);
            return;
        }
        final View tab = bottomBar.getChildAt(selectedNavIndex);
        Runnable go = () -> {
            if (tab.getWidth() <= 0 || tab.getHeight() <= 0) return;
            android.widget.FrameLayout.LayoutParams lp =
                    (android.widget.FrameLayout.LayoutParams) navCapsule.getLayoutParams();
            lp.width = tab.getWidth() - Theme.dp(8);
            lp.height = tab.getHeight() - Theme.dp(4);
            lp.topMargin = tab.getTop() + Theme.dp(2);
            lp.leftMargin = 0;
            navCapsule.setLayoutParams(lp);
            float x = tab.getLeft() + Theme.dp(4);
            navCapsule.setVisibility(View.VISIBLE);
            if (animate && ir.meelano.manager.ui.Ui.motionOk(this)) {
                navCapsule.animate().cancel();
                navCapsule.animate().translationX(x).setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            } else {
                navCapsule.animate().cancel();
                navCapsule.setTranslationX(x);
            }
        };
        if (tab.getWidth() > 0) go.run(); else tab.post(go);
    }

    private void paintBottom() {
        selectedNavIndex = -1;
        int tabIndex = 0;
        for (int i = 0; i < bottomBar.getChildCount(); i++) {
            LinearLayout b = (LinearLayout) bottomBar.getChildAt(i);
            if (b == null) continue;
            if (BACK_CELL_TAG.equals(b.getTag())) continue;   // the back control is not a tab
            boolean on = tabIndex < tabs.length && (tabs[tabIndex].equals(currentId)
                    || ("more".equals(tabs[tabIndex]) && !isTab(currentId)));
            LinearLayout badge = (LinearLayout) b.getChildAt(0);
            badge.setBackground(on ? Theme.bottomNavIconSelected() : null);
            View symbol = badge.getChildAt(0);
            if (symbol instanceof android.widget.ImageView) {
                if (!Boolean.TRUE.equals(symbol.getTag()))
                    ((android.widget.ImageView) symbol).setColorFilter(on ? Theme.selectedNavInk() : Theme.MUTED);
            } else if (symbol instanceof TextView) {
                ((TextView) symbol).setTextColor(on ? Theme.selectedNavInk() : Theme.MUTED);
            }
            TextView label = (TextView) b.getChildAt(1);
            label.setTextColor(on ? Theme.selectedNavInk() : Theme.MUTED);
            label.setTextSize((on ? 11.5f : 11f) * Theme.fontScale());
            View marker = b.getChildAt(2);
            marker.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            marker.setBackground(on ? Theme.bottomNavMarker() : null);
            if (on) selectedNavIndex = i;
            b.setSelected(on);
            b.setContentDescription(label.getText() + (on ? "، بخش فعال" : "، رفتن به بخش"));
            tabIndex++;
        }
        moveCapsule(true);
    }

    private boolean isTab(String id) {
        for (String t : tabs) if (t.equals(id)) return true;
        return false;
    }

    /** Capture a handled UI failure and leave a retry/copy path instead of closing the app. */
    public void reportRenderFailure(LinearLayout target, String area, Throwable error) {
        ir.meelano.manager.core.CrashReporter.recordHandled(this, area, error);
        ir.meelano.manager.core.Diag.warn(area, error);
        if (target == null || kit == null) return;
        try {
            target.removeAllViews();
            LinearLayout card = kit.card(Theme.DANGER);
            card.addView(kit.text("ساخت این صفحه با خطای فنی روبه‌رو شد؛ برنامه باز می‌ماند.",
                    13f, Theme.TEXT, true), kit.lp(-1, -2));
            card.addView(kit.hint("گزارش امن محلی ذخیره شد. اگر مشکل تکرار شد، گزارش را برای پشتیبانی کپی کنید."),
                    kit.lp(-1, -2));
            card.addView(kit.btnGhost("کپی گزارش فنی", Theme.INFO, v -> copyCrashReport()),
                    kit.lp(-1, -2));
            card.addView(kit.btnGold("تلاش دوباره", v -> {
                ir.meelano.manager.core.CrashReporter.clearReport(MainActivity.this);
                renderCurrent();
            }), kit.lp(-1, -2));
            target.addView(card, kit.lp(-1, -2));
        } catch (RuntimeException fallbackError) {
            ir.meelano.manager.core.Diag.warn("MainActivity:renderFailureFallback", fallbackError);
        }
    }

    /** Install the product search/scanner in the shell, outside the catalogue ScrollView. */
    public void setProductStickyBar(View bar) {
        if (productStickyHost == null) return;
        productStickyHost.removeAllViews();
        if (bar == null) {
            productStickyHost.setVisibility(View.GONE);
            return;
        }
        productStickyHost.addView(bar, kit.lp(-1, -2));
        productStickyHost.setVisibility(View.VISIBLE);
    }

    private void setBrandHeaderCollapsed(boolean collapsed) {
        final View block = brandHeaderBlock;
        if (block == null || brandHeaderCollapsed == collapsed) return;
        if (brandHeaderExpandedHeight <= 0 && block.getHeight() > 0)
            brandHeaderExpandedHeight = block.getHeight();
        if (brandHeaderExpandedHeight <= 0) return;

        if (brandHeaderAnimator != null) {
            brandHeaderAnimator.cancel();
            brandHeaderAnimator = null;
        }
        applyHeaderDensity(collapsed);
        android.view.ViewGroup.LayoutParams params = block.getLayoutParams();
        if (params == null) return;
        int start = Math.max(0, block.getHeight());
        int target = collapsed ? Theme.dp(48) : brandHeaderExpandedHeight;
        brandHeaderCollapsed = collapsed;
        block.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (start == target || !ir.meelano.manager.ui.Ui.motionOk(this)) {
            params.height = target;
            block.setLayoutParams(params);
            return;
        }
        brandHeaderAnimator = android.animation.ValueAnimator.ofInt(start, target);
        brandHeaderAnimator.setDuration(180);
        brandHeaderAnimator.addUpdateListener(anim -> {
            params.height = (Integer) anim.getAnimatedValue();
            block.setLayoutParams(params);
        });
        brandHeaderAnimator.start();
    }

    /** Set the compact header's controls and line count without duplicating the brand mark. */
    private void applyHeaderDensity(boolean collapsed) {
        if (headerRoot == null || brandRow == null) return;
        int verticalPad = collapsed ? 4 : (compactHeaderLayout ? 6 : 9);
        headerRoot.setPadding(Theme.dp(12), Theme.dp(verticalPad), Theme.dp(12),
                Theme.dp(collapsed ? 4 : 5));
        if (headerTools != null) headerTools.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        if (headerToolbarGap != null) headerToolbarGap.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        if (headerStatusRow != null) headerStatusRow.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        if (compactSyncButton != null) compactSyncButton.setVisibility(collapsed ? View.VISIBLE : View.GONE);
        boolean showMenu = collapsed || compactHeaderLayout;
        if (compactMenuButton != null)
            compactMenuButton.setVisibility(showMenu ? View.VISIBLE : View.GONE);
        if (compactMenuGap != null) compactMenuGap.setVisibility(showMenu ? View.VISIBLE : View.GONE);

        android.view.ViewGroup.LayoutParams logo = logoBox == null ? null : logoBox.getLayoutParams();
        if (logo != null) {
            int size = Theme.dp(collapsed ? 40 : brandMarkExpandedDp);
            logo.width = size;
            logo.height = size;
            logoBox.setLayoutParams(logo);
        }
        if (companyName != null) {
            companyName.setSingleLine(collapsed);
            companyName.setMaxLines(collapsed ? 1 : 2);
            companyName.setTextSize((collapsed ? 16f : 22f) * Theme.fontScale() * Theme.typeScale());
        }
        if (brandTitleColumn != null) {
            int width = collapsed
                    ? headerWidthDp - 24 - 40 - 10 - 48 - 4 - 48
                    : titleExpandedWidthDp;
            android.view.ViewGroup.LayoutParams title = brandTitleColumn.getLayoutParams();
            if (title != null) {
                title.width = Theme.dp(Math.max(72, width));
                brandTitleColumn.setLayoutParams(title);
            }
        }
    }

    public void nav(String id) {
        if (!screens.containsKey(id)) return;
        if (!RoleStore.allowed(this, id)) {
            denyRole(id);
            return;
        }
        if (!id.equals(currentId)) {
            history.add(currentId);
            if (history.size() > 30) history.remove(0);
        }
        currentId = id;
        renderCurrent();
        animateContent(true);
    }

    // ================= user roles =================
    /** Open the role picker (role switch from Settings). No-op when roles are off. */
    public void openRoleGate() {
        if (AtiranAuth.hasSession(this)) {
            kit.toast("با ورود کاربر، نقش از آتیران می‌آید؛ برای تعویض نقش خارج شوید");
            return;
        }
        if (RoleStore.enabled(this)) roleGate(null);
    }

    /** Full-screen role picker; thenNav is opened after the pick (when allowed). */
    private void roleGate(final String thenNav) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(Theme.dp(28), Theme.dp(28), Theme.dp(28), Theme.dp(28));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Theme.dp(96), Theme.dp(96));
        llp.gravity = Gravity.CENTER;
        root.addView(kit.logo(96), llp);
        TextView t = kit.text("ورود با نقش", 22, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t, kit.lp(-1, -2));
        TextView sub = kit.text("نقش خود را انتخاب کنید", 12.5f, Theme.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = kit.lp(-1, -2);
        sp.setMargins(0, Theme.dp(6), 0, Theme.dp(18));
        root.addView(sub, sp);
        String cur = RoleStore.current(this);
        for (String role0 : RoleStore.ALL) {
            final String role = role0;
            String label = (RoleStore.pinSet(this, role) ? "\uD83D\uDD12 " : "")
                    + RoleStore.faName(role) + " — " + RoleStore.faDesc(role)
                    + (role.equals(cur) ? " (فعلی)" : "");
            android.widget.Button b = role.equals(cur)
                    ? kit.btn(label, v -> pickRole(role, thenNav))
                    : kit.btnGhost(label, Theme.GOLD, v -> pickRole(role, thenNav));
            LinearLayout.LayoutParams bp = kit.lp(-1, -2);
            bp.setMargins(0, Theme.dp(5), 0, Theme.dp(5));
            root.addView(b, bp);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private void pickRole(String role, String thenNav) {
        if (RoleStore.pinSet(this, role)) rolePinDialog(role, thenNav);
        else applyRole(role, thenNav);
    }

    private void rolePinDialog(final String role, final String thenNav) {
        LinearLayout body = kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(kit.text("رمز نقش «" + RoleStore.faName(role) + "»", 13f, Theme.TEXT, true),
                kit.lp(-1, -2));
        final android.widget.EditText e = kit.editPin("رمز ۴ رقمی", "");
        body.addView(e, kit.lp(-1, -2));
        body.addView(kit.gap(8));
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(kit.btn("ورود", v -> {
            if (RoleStore.checkRole(MainActivity.this, role, e.getText().toString())) {
                box[0].dismiss();
                applyRole(role, thenNav);
            } else {
                kit.toast("رمز اشتباه است");
            }
        }), kit.lp(-1, -2));
        box[0] = kit.dialog("ورود با نقش", body, true);
        box[0].show();
    }

    private void applyRole(String role, String thenNav) {
        RoleStore.setCurrent(this, role);
        rolePicked = true;
        currentId = "home";
        shell();
        if (thenNav != null && RoleStore.allowed(this, thenNav)) nav(thenNav);
    }

    /** Access denied: explain + offer a role switch. */
    private void denyRole(final String id) {
        kit.toast("نقش «" + RoleStore.faName(RoleStore.current(this)) + "» به این بخش دسترسی ندارد");
        LinearLayout body = kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(kit.text("برای ورود به این بخش، به نقش دیگری بروید.", 13f, Theme.TEXT, false),
                kit.lp(-1, -2));
        body.addView(kit.gap(8));
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(kit.btn("تعویض نقش", v -> {
            box[0].dismiss();
            roleGate(id);
        }), kit.lp(-1, -2));
        box[0] = kit.dialog("محدودیت نقش", body, true);
        box[0].show();
    }

    /** Direction-aware content slide (RTL: forward enters from the left). */
    private void animateContent(boolean forward) {
        try {
            if (content == null) return;
            if (!ir.meelano.manager.ui.Ui.motionOk(this)) {
                content.setTranslationX(0f);
                content.setAlpha(1f);
                return;
            }
            content.animate().cancel();
            content.setTranslationX((forward ? -1 : 1) * Theme.dp(48));
            content.setAlpha(0.35f);
            content.animate().translationX(0f).alpha(1f).setDuration(220).start();
            ir.meelano.manager.ui.Ui.reveal(content);
        } catch (Exception ignored) { Diag.ignored("MainActivity:954", ignored); }
    }

    /** Paint the company header from cache, then refresh from Atiran in background. */
    public void refreshCompany() {
        refreshCompany(null);
    }

    public void refreshCompany(final Runnable after) {
        paintCompany();
        try {
            Company.refresh(this, settings, repo, () -> {
                paintCompany();
                if (after != null) after.run();
            });
        } catch (Exception ignored) { Diag.ignored("MainActivity:969", ignored); }
    }

    private String companyTitle() {
        String nm;
        try {
            nm = Company.get(this).displayName(this);
        } catch (Exception e) {
            nm = "میلانو";
        }
        String g = Theme.seasonGlyph(this);
        return g.isEmpty() ? nm : nm + " " + g;
    }

    private void paintCompany() {
        try {
            if (companyName != null) ir.meelano.manager.ui.MeelanoIcons.set(companyName, companyTitle());
            android.graphics.Bitmap b = Company.logo(this);
            if (logoImg != null && logoGlyph != null) {
                // Same crown-M medallion as the dashboard crest (b is intentionally not shown here).
                logoImg.setImageResource(Theme.logoRes());
                logoImg.setVisibility(View.VISIBLE);
                logoGlyph.setVisibility(View.GONE);
            }
        } catch (Exception ignored) { Diag.ignored("MainActivity:997", ignored); }
    }

    /**
     * Hand the foreground/foreground change to the current screen so it can
     * start or stop live refreshing. One screen at a time, and never held
     * across a pause — whatever a screen starts in onShown() it must stop
     * in onHidden().
     */
    private void dispatchShown(boolean shown) {
        try {
            Screen s = screens.get(currentId);
            if (s == null) return;
            if (shown) s.onShown();
            else s.onHidden();
        } catch (Exception ignored) { Diag.ignored("MainActivity:1012", ignored); }
    }

    private void renderCurrent() {
        Screen s = screens.get(currentId);
        if (s == null) return;
        if (!"products".equals(currentId)) setProductStickyBar(null);
        setBrandHeaderCollapsed(false);
        renderGen++;
        // A new page for every render: a slow query that returns after the user has moved to
        // another section fills the old, detached page and can no longer repaint the screen on display.
        content = newPage();
        refreshChrome();
        scroll.scrollTo(0, 0);
        try {
            s.render(content);
        } catch (RuntimeException renderError) {
            reportRenderFailure(content, "MainActivity:renderCurrent", renderError);
        }
        playPageIn(content);
        playEntrance(content);
    }

    /** Section change: the new page slides in 18dp from the start side and fades up (220ms). */
    private void playPageIn(LinearLayout page) {
        try {
            if (!ir.meelano.manager.ui.Ui.motionOk(this)) return;
            page.setAlpha(0f);
            page.setTranslationX(Theme.dp(18));
            page.animate().alpha(1f).translationX(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } catch (Exception e) {
            Diag.ignored("MainActivity:pageIn", e);
        }
    }

    /**
     * Cards rise in one after another on each section change (fade + 10dp rise, 35ms apart,
     * first 8 only). Skipped when the system animations are off (reduce motion).
     */
    private void playEntrance(LinearLayout page) {
        try {
            if (!ir.meelano.manager.ui.Ui.motionOk(this)) return;
            int n = Math.min(page.getChildCount(), 8);
            for (int i = 0; i < n; i++) {
                android.view.View v = page.getChildAt(i);
                v.setAlpha(0f);
                v.setTranslationY(Theme.dp(10));
                v.animate().alpha(1f).translationY(0f).setDuration(280)
                        .setStartDelay(i * 35L).setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            }
        } catch (Exception e) {
            Diag.ignored("MainActivity:entrance", e);
        }
    }

    /** Builds an empty, centred page column and puts it on screen in place of the previous one. */
    private LinearLayout newPage() {
        LinearLayout page = kit.v();
        // The dock is a separate sibling below the scroll viewport, not an overlay on page rows.
        // Keep just a compact end gutter rather than the old 128dp spacer, which created a blank tail.
        page.setPadding(Theme.dp(12), Theme.dp(4), Theme.dp(12), Theme.dp(20));
        int sw = getResources().getDisplayMetrics().widthPixels;
        if (contentWrap != null) {
            contentWrap.removeAllViews();
            contentWrap.addView(page, new LinearLayout.LayoutParams(sw > Theme.dp(720) ? Theme.dp(640) : -1, -2));
        }
        return page;
    }

    /** Current render generation; changes whenever a different page or a re-render replaces what is on screen. */
    public int renderGen() { return renderGen; }

    /** Id of the screen that is on display right now. */
    public String currentScreenId() { return currentId; }

    /** Reset the product list and expand its brand row after an in-place page change. */
    public void scrollProductsToTop() {
        if (scroll != null) scroll.scrollTo(0, 0);
        setBrandHeaderCollapsed(false);
    }

    /** Repaint bottom bar and back utility after a page or role-state change. */
    public void refreshChrome() {
        Screen s = screens.get(currentId);
        if (s == null) return;
        paintBottom();
        updateBackControl();
    }

    public void openFilter() {
        final Screen s = screens.get(currentId);
        if (s == null || s.filterConfig() == null || s.filter() == null) return;
        FilterSheet.show(kit, repo, s.filter(), s.filterConfig(), f -> {
            s.applyFilter(f);
            renderCurrent();
        });
    }

    /** Open the shop TV dashboard (landscape). */
    public void startTv() {
        try {
            startActivity(new android.content.Intent(this, TvActivity.class));
        } catch (Exception e) {
            kit.toast("حالت تلویزیون ممکن نشد");
        }
    }

    /** Shared back logic for the system back gesture and the dock's separate back utility. */
    public void goBack() {
        Screen s = screens.get(currentId);
        if (s != null && s.onBack()) return;
        if (!history.isEmpty()) {
            currentId = history.remove(history.size() - 1);
            renderCurrent();
            animateContent(false);
        } else nav("home");
    }

    /** Reconcile the dock's back utility with the current page after shell construction. */
    private void syncBackControl() { updateBackControl(); }

    /** Keep the separate back utility visible, clear, and genuinely disabled at the root page. */
    private void updateBackControl() {
        if (backCell == null) return;
        boolean can = !history.isEmpty() || !"home".equals(currentId);
        backCell.setEnabled(can);
        backCell.setAlpha(can ? 1f : 0.48f);
        backCell.setContentDescription(can ? "بازگشت به صفحه قبلی" : "صفحه اصلی؛ بازگشتی وجود ندارد");
    }

    // ================= Persian voice search =================
    private static final int VOICE_REQ = 901;
    private java.util.function.Consumer<String> voiceCb;

    /** Start Persian speech recognition; the transcript is delivered to cb. */
    /** Asks for the microphone (used by live assistant conversation). */
    public void requestMic(java.util.function.Consumer<Boolean> cb) {
        micCb = cb;
        requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MIC_PERM);
    }

    private static final int MIC_PERM = 4471;
    private static final int LOC_PERM = 4472;

    /** Asks for approximate and precise location, used only by the weather card. */
    public void requestLocationAccess() {
        requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION}, LOC_PERM);
    }
    private java.util.function.Consumer<Boolean> micCb;

    public void startVoiceSearch(java.util.function.Consumer<String> cb) {
        voiceCb = cb;
        try {
            android.content.Intent i = new android.content.Intent(
                    android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
            i.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "…بگویید چه چیزی جستجو شود");
            startActivityForResult(i, VOICE_REQ);
        } catch (Exception e) {
            kit.toast("جستجوی صوتی در این گوشی پشتیبانی نمی‌شود");
            voiceCb = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UpdateCenter.REQ_UNKNOWN) {
            try {
                UpdateCenter.onUnknownSourcesReturn(this);
            } catch (Exception ignored) { Diag.ignored("MainActivity:1145", ignored); }
            return;
        }
        if (requestCode == VOICE_REQ && resultCode == Activity.RESULT_OK && data != null && voiceCb != null) {
            try {
                java.util.ArrayList<String> out = data.getStringArrayListExtra(
                        android.speech.RecognizerIntent.EXTRA_RESULTS);
                if (out != null && !out.isEmpty() && out.get(0) != null && !out.get(0).trim().isEmpty())
                    voiceCb.accept(out.get(0).trim());
                else kit.toast("چیزی شنیده نشد");
            } catch (Exception e) {
                kit.toast("جستجوی صوتی ممکن نشد");
            }
        }
        voiceCb = null;
        if (requestCode == SCAN_REQ && scanCb != null) {
            try {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String code = data.getStringExtra(ScanActivity.EXTRA_CODE);
                    if (code != null && !code.trim().isEmpty()) scanCb.accept(code.trim());
                    else kit.toast("کدی خوانده نشد");
                }
            } catch (Exception e) {
                kit.toast("اسکن ممکن نشد");
            }
            scanCb = null;
        }
        if (requestCode == DOC_REQ && docCb != null) {
            try {
                if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                    java.io.InputStream in = getContentResolver().openInputStream(data.getData());
                    StringBuilder b = new StringBuilder();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) b.append(new String(buf, 0, n, "UTF-8"));
                    try {
                        in.close();
                    } catch (Exception ignored) { Diag.ignored("MainActivity:1182", ignored); }
                    docCb.accept(b.toString());
                } else kit.toast("فایلی انتخاب نشد");
            } catch (Exception e) {
                kit.toast("خواندن فایل ممکن نشد");
            }
            docCb = null;
        }
        if (requestCode == PHOTO_REQ && photoCb != null) {
            try {
                if (resultCode == Activity.RESULT_OK && photoUri != null) photoCb.accept(photoUri);
                else kit.toast("عکسی گرفته نشد");
            } catch (Exception e) {
                kit.toast("دوربین ممکن نشد");
            }
            photoCb = null;
            photoUri = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Notification permission is best-effort; the app works fully without it.
        boolean granted = grantResults != null && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (requestCode == LOC_PERM) {
            // A grant records the user's opt-in; a denial removes any location-derived cache.
            if (ir.meelano.manager.core.DeviceLocation.granted(this))
                MarketWeather.setWeatherLocationEnabled(this, true);
            else MarketWeather.setWeatherLocationEnabled(this, false);
            if ("home".equals(currentId)) renderCurrent();
            return;
        }
        if (requestCode == MIC_PERM) {
            java.util.function.Consumer<Boolean> cb = micCb;
            micCb = null;
            if (cb != null) cb.accept(granted);
            return;
        }
        if (requestCode == SCAN_PERM) {
            if (granted && scanCb != null) launchScanner();
            else {
                kit.toast("برای اسکن بارکد دسترسی دوربین لازم است");
                scanCb = null;
            }
        } else if (requestCode == SmsIo.REQ_SEND) {
            try {
                Screen s = screens.get(currentId);
                if (s != null) s.onSmsPermission(granted);
            } catch (Exception ignored) { Diag.ignored("MainActivity:1218", ignored); }
        } else if (requestCode == FisPrint.BT_REQ) {
            FisPrint.onPermissionResult(this, granted);
        }
    }

    // ================= barcode scanner =================
    private static final int SCAN_REQ = 902;
    private static final int SCAN_PERM = 903;
    private java.util.function.Consumer<String> scanCb;

    /** Open the barcode scanner; the decoded text is delivered to cb. */
    public void startScan(java.util.function.Consumer<String> cb) {
        scanCb = cb;
        try {
            if (checkSelfPermission(android.Manifest.permission.CAMERA)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchScanner();
            } else {
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, SCAN_PERM);
            }
        } catch (Exception e) {
            kit.toast("دوربین در دسترس نیست");
            scanCb = null;
        }
    }

    private void launchScanner() {
        try {
            startActivityForResult(new android.content.Intent(this, ScanActivity.class), SCAN_REQ);
        } catch (Exception e) {
            kit.toast("اسکنر باز نشد");
            scanCb = null;
        }
    }

    // ================= document pick + photo =================
    private static final int DOC_REQ = 904;
    private static final int PHOTO_REQ = 905;
    private java.util.function.Consumer<String> docCb;
    private java.util.function.Consumer<android.net.Uri> photoCb;
    private android.net.Uri photoUri;

    /** Pick a JSON file; its text is delivered to cb. */
    public void startDocPick(java.util.function.Consumer<String> cb) {
        docCb = cb;
        try {
            android.content.Intent i = new android.content.Intent(
                    android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
                    new String[]{"application/json", "text/plain"});
            startActivityForResult(i, DOC_REQ);
        } catch (Exception e) {
            kit.toast("انتخاب فایل ممکن نشد");
            docCb = null;
        }
    }

    /** Take a photo (saved to the gallery); its Uri is delivered to cb. */
    public void startPhoto(java.util.function.Consumer<android.net.Uri> cb) {
        photoCb = cb;
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    "tahvil-" + System.currentTimeMillis() + ".jpg");
            cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            photoUri = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (photoUri == null) throw new Exception("media");
            android.content.Intent i = new android.content.Intent(
                    android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri);
            i.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i, PHOTO_REQ);
        } catch (Exception e) {
            kit.toast("دوربین باز نشد");
            photoCb = null;
            photoUri = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (!unlocked) {
            super.onBackPressed();
            return;
        }
        Screen s = screens.get(currentId);
        if (s != null && s.onBack()) return;
        if (!history.isEmpty()) {
            currentId = history.remove(history.size() - 1);
            renderCurrent();
            animateContent(false);
        } else super.onBackPressed();
    }

    // ================= helpers for screens =================
    /** Paint the header license chip (remaining time; red when ≤ 7 days). */
    public void refreshLicChip() {
        if (licChip == null) return;
        try {
            LicenseStore.Status s = LicenseStore.check(this);
            if (!s.ok) {
                licChip.setVisibility(View.GONE);
                return;
            }
            licChip.setVisibility(View.VISIBLE);
            if (s.plan == License.P_PERM) {
                ir.meelano.manager.ui.MeelanoIcons.set(licChip, "📅 دائمی");
                licChip.setTextColor(Theme.GOLD_SOFT);
            } else if (s.daysLeft <= 2) {
                ir.meelano.manager.ui.MeelanoIcons.set(licChip, "📅 " + Money.fa(String.valueOf(s.daysLeft)) + " روز!");
                licChip.setTextColor(Theme.TEXT);
            } else if (s.daysLeft <= 7) {
                ir.meelano.manager.ui.MeelanoIcons.set(licChip, "📅 " + Money.fa(String.valueOf(s.daysLeft)) + " روز");
                licChip.setTextColor(Theme.TEXT);
            } else {
                ir.meelano.manager.ui.MeelanoIcons.set(licChip, "📅 " + Money.fa(String.valueOf(s.daysLeft)) + " روز");
                licChip.setTextColor(Theme.GOLD_SOFT);
            }
        } catch (Exception e) {
            try {
                licChip.setVisibility(View.GONE);
            } catch (Exception ignored) { Diag.ignored("MainActivity:1340", ignored); }
        }
    }

    private void openLicense() {
        try {
            startActivity(new Intent(this, LicenseActivity.class));
        } catch (Exception ignored) { Diag.ignored("MainActivity:1347", ignored); }
    }

    /** Reset the shared content viewport after an in-screen visitor destination changes. */
    public void scrollToTop() {
        try {
            if (scroll != null) scroll.post(() -> scroll.scrollTo(0, 0));
        } catch (Exception ignored) { Diag.ignored("MainActivity:visitorScrollTop", ignored); }
    }

    /** Open the license connection flow directly in edit mode, preserving the current session. */
    public void openConnectionSetup() {
        openConnectionSetup(false, settings != null && settings.connConfigured());
    }

    private void openConnectionSetup(boolean clearTask, boolean editExisting) {
        try {
            Intent i = new Intent(this, LicenseActivity.class);
            i.putExtra(LicenseActivity.EXTRA_EDIT_CONNECTION, editExisting);
            if (clearTask) i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            if (clearTask) finish();
        } catch (Exception e) {
            kit.toast("صفحهٔ تنظیم اتصال باز نشد");
        }
    }

    /** Forget the saved IP/database and return to setup without touching the license or account. */
    public void disconnectConnection() {
        try {
            settings.clearSavedServer();
            getSharedPreferences("meelano_license_ui", MODE_PRIVATE).edit()
                    .remove("conn_summary").apply();
            kit.toast("آدرس‌های سرور پاک شد؛ اطلاعات ورود SQL و حساب محلی حفظ شدند");
            openConnectionSetup(true, false);
        } catch (Exception e) {
            kit.toast("پاک‌کردن آدرس سرور ممکن نشد");
        }
    }

    /**
     * The header iconizer maps «◆» to a diamond vector, so a damaged name containing a valid
     * letter plus «◆?» could pass the old any-letter check and appear as an icon/question mark.
     * Reject the whole display value and fall back to the account/role label; never rewrite the
     * persisted Atiran name or username.
     */
    private static String cleanName(String s) {
        if (!PersianText.isUsablePersonName(s)) return "";
        return PersianText.cleanDisplayLabel(s).trim();
    }

    private String displayName() {
        String nm = cleanName(AtiranAuth.sessionName(this));
        if (nm.isEmpty()) nm = cleanName(AtiranAuth.sessionUser(this));
        if (nm.isEmpty()) nm = cleanName(RoleStore.faName(AtiranAuth.sessionRole(this)));
        return nm;
    }

    public void refreshUserChip() {
        try {
            boolean hasSession = AtiranAuth.hasSession(this);
            if (compactUserAction != null)
                compactUserAction.setContentDescription(hasSession
                        ? "حساب کاربری؛ خروج یا تغییر کاربر" : "حساب کاربری");
            if (compactUserCell != null) {
                compactUserCell.setVisibility(hasSession ? View.VISIBLE : View.GONE);
                compactUserCell.setContentDescription(hasSession
                        ? "حساب کاربری؛ خروج یا تغییر کاربر" : "حساب کاربری");
            }
        } catch (Exception ignored) { Diag.ignored("MainActivity:1399", ignored); }
    }

    /** Who am I + logout (+ PIN-management shortcut for the manager). */
    private void userMenu() {
        try {
            if (!AtiranAuth.hasSession(this)) return;
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
            String nm = cleanName(AtiranAuth.sessionName(this));
            if (nm.isEmpty()) nm = cleanName(AtiranAuth.sessionUser(this));
            body.addView(kit.kv("کاربر", nm.isEmpty() ? displayName() : nm, Theme.TEXT), kit.lp(-1, -2));
            body.addView(kit.kv("نقش", RoleStore.faName(AtiranAuth.sessionRole(this)), Theme.TEXT),
                    kit.lp(-1, -2));
            if (!AtiranAuth.sessionUser(this).isEmpty())
                body.addView(kit.kv("نام کاربری آتیران", AtiranAuth.sessionUser(this), Theme.TEXT),
                        kit.lp(-1, -2));
            final AlertDialog[] box = new AlertDialog[1];
            body.addView(kit.gap(8));
            if (RoleStore.allowed(this, "users"))
                body.addView(kit.btnGhost("تعیین رمز کاربران", Theme.GOLD, v -> {
                    try {
                        box[0].dismiss();
                    } catch (Exception ignored) { Diag.ignored("MainActivity:1422", ignored); }
                    nav("users");
                }), kit.lp(-1, -2));
            body.addView(kit.btnGhost("↪ خروج از حساب / تغییر کاربر", Theme.DANGER, v -> {
                try {
                    box[0].dismiss();
                } catch (Exception ignored) { Diag.ignored("MainActivity:1428", ignored); }
                logout();
            }), kit.lp(-1, -2));
            box[0] = kit.dialog("حساب کاربری", body, true);
            box[0].show();
        } catch (Exception ignored) { Diag.ignored("MainActivity:1433", ignored); }
    }

    public void checkConn() {
        setConnectionState("probe");
        repo.run(c -> Repo.one(c, new Queries.Q("SELECT 1 AS ok")), new Repo.Cb<Row>() {
            @Override
            public void ok(Row v) {
                String last = "";
                try {
                    last = settings.linkLast();
                } catch (Exception ignored) { Diag.ignored("MainActivity:1444", ignored); }
                setConnectionState(last.isEmpty() ? SmartLink.LAN : last);
            }

            @Override
            public void fail(String faError) {
                setConnectionState("off");
            }
        });
    }

    /** Persist and repaint the reusable SyncPill in both expanded and compact header states. */
    private void setConnectionState(String kind) {
        linkKind = kind == null ? "off" : kind;
        updateHeaderSyncPill(linkKind);
    }

    private void updateHeaderSyncPill(String kind) {
        int icon;
        int accent;
        String title;
        String detail;
        boolean busy = "probe".equals(kind);
        if (busy) {
            icon = R.drawable.mi_sync;
            accent = Theme.PRIMARY;
            title = "در حال بررسی اتصال";
            detail = "شبکهٔ فروشگاه";
        } else if (SmartLink.WAN.equals(kind)) {
            icon = R.drawable.mi_check_circle;
            accent = Theme.SUCCESS;
            title = "اتصال برقرار";
            detail = "از خارج شبکه";
        } else if (SmartLink.LAN.equals(kind)) {
            icon = R.drawable.mi_wifi;
            accent = Theme.SUCCESS;
            title = "اتصال برقرار";
            detail = "در شبکهٔ داخلی";
        } else {
            icon = R.drawable.mi_wifi_off;
            accent = Theme.WARNING;
            title = "ارتباط قطع";
            detail = "برای عیب‌یابی لمس کنید";
        }
        String spoken = title + "؛ " + detail + "؛ لمس برای جزئیات اتصال";
        try {
            if (headerSyncHost != null) {
                headerSyncHost.removeAllViews();
                View pill = kit.syncPill(icon, title, detail, accent, busy, null);
                pill.setContentDescription(spoken);
                Theme.pressable(pill);
                pill.setOnClickListener(v -> showLinkPanel());
                headerSyncHost.addView(pill, new android.widget.FrameLayout.LayoutParams(
                        -1, -2, Gravity.CENTER_VERTICAL));
            }
            if (compactSyncButton != null) {
                compactSyncButton.setBackground(Theme.kpi(accent));
                compactSyncButton.setContentDescription(spoken);
                Theme.pressable(compactSyncButton);
            }
            if (compactSyncIcon != null) {
                compactSyncIcon.setImageResource(icon);
                compactSyncIcon.setColorFilter(Theme.sectionText(accent));
            }
        } catch (Exception ignored) { Diag.ignored("MainActivity:1481", ignored); }
    }

    public void testConnection(final Repo.Cb<String> cb) {
        boolean vpn = false;
        try {
            vpn = NetRoute.isVpnActive(this);
        } catch (Exception ignored) { Diag.ignored("MainActivity:1488", ignored); }
        SmartLink.diagnose(this, settings, vpn, r -> {
            try {
                if (r.anyOk) {
                    repo.run(c -> AtiranPreflight.inspect(c).persianSummary()
                            + " • مسیر " + SmartLink.kindShort(r.activeKind), cb);
                } else {
                    cb.fail(r.verdict);
                }
            } catch (Exception ignored) { Diag.ignored("MainActivity:1497", ignored); }
        });
    }

    /** Probe explicit (possibly unsaved) connection values. */
    public void testConnection(String host, int port, String db, String user, String pass, final Repo.Cb<String> cb) {
        repo.runWith(host, port, db, user, pass,
                c -> AtiranPreflight.inspect(c).persianSummary(), cb);
    }

    public void sharePdf(final String title, final String subtitle, final ReportCatalog.Col[] cols, final List<Row> rows) {
        kit.toast("در حال ساخت PDF…");
        new Thread(() -> {
            try {
                final File f = Pdf.build(this, title, subtitle, cols, rows, 400);
                runOnUiThread(() -> {
                    try {
                        ShareProvider.share(this, f, "application/pdf", title);
                    } catch (Exception e) {
                        kit.toast("اشتراک ممکن نشد");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> kit.toast("ساخت PDF ممکن نشد"));
            }
        }).start();
    }

    /** Same report plus an image appendix (signature / photo pages). */
    public void sharePdfImages(final String title, final String subtitle,
            final ReportCatalog.Col[] cols, final List<Row> rows,
            final java.util.List<Pdf.Img> images) {
        kit.toast("در حال ساخت PDF…");
        new Thread(() -> {
            try {
                final File f = Pdf.buildFull(this, title, subtitle, cols, rows, 400, images);
                runOnUiThread(() -> {
                    try {
                        ShareProvider.share(this, f, "application/pdf", title);
                    } catch (Exception e) {
                        kit.toast("اشتراک ممکن نشد");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> kit.toast("ساخت PDF ممکن نشد"));
            }
        }).start();
    }

    public void shareXlsx(final String title, final String subtitle, final ReportCatalog.Col[] cols, final List<Row> rows) {
        kit.toast("در حال ساخت اکسل…");
        new Thread(() -> {
            try {
                final File f = ir.meelano.manager.ui.Xlsx.build(this, title, subtitle, cols, rows, 2000);
                runOnUiThread(() -> {
                    try {
                        ShareProvider.share(this, f, ir.meelano.manager.ui.Xlsx.MIME, title);
                    } catch (Exception e) {
                        kit.toast("اشتراک ممکن نشد");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> kit.toast("ساخت اکسل ممکن نشد"));
            }
        }).start();
    }

    /** System print (printer picker) of the same report PDF. */
    public void printPdf(final String title, final String subtitle, final ReportCatalog.Col[] cols, final List<Row> rows) {
        kit.toast("در حال آماده‌سازی چاپ…");
        new Thread(() -> {
            try {
                final File f = Pdf.build(this, title, subtitle, cols, rows, 400);
                runOnUiThread(() -> {
                    try {
                        ir.meelano.manager.ui.PrintKit.printPdf(this, f, title);
                    } catch (Exception e) {
                        kit.toast("چاپ ممکن نشد");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> kit.toast("ساخت PDF ممکن نشد"));
            }
        }).start();
    }

    public void shareDocx(final String title, final String subtitle, final ReportCatalog.Col[] cols, final List<Row> rows) {
        kit.toast("در حال ساخت ورد…");
        new Thread(() -> {
            try {
                final File f = ir.meelano.manager.ui.Docx.build(this, title, subtitle, cols, rows, 2000);
                runOnUiThread(() -> {
                    try {
                        ShareProvider.share(this, f, ir.meelano.manager.ui.Docx.MIME, title);
                    } catch (Exception e) {
                        kit.toast("اشتراک ممکن نشد");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> kit.toast("ساخت ورد ممکن نشد"));
            }
        }).start();
    }

    public void sharePng(final String title, final String subtitle, final ReportCatalog.Col[] cols, final List<Row> rows) {
        kit.toast("در حال ساخت عکس…");
        new Thread(() -> {
            try {
                final File f = ir.meelano.manager.ui.TableShot.build(this, title, subtitle, cols, rows, 120);
                runOnUiThread(() -> {
                    try {
                        ShareProvider.share(this, f, "image/png", title);
                    } catch (Exception e) {
                        kit.toast("اشتراک ممکن نشد");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> kit.toast("ساخت عکس ممکن نشد"));
            }
        }).start();
    }

    /** Two-row export bar: PDF + Excel + Word / image + print (share sheet covers social apps + Bluetooth). */
    public android.view.View exportBar(final String title, final String subtitle,
                                       final ReportCatalog.Col[] cols, final List<Row> rows) {
        return exportBar(title, subtitle, cols, rows, true);
    }

    /** Same bar; pass withPrint=false where the screen already has its own print action. */
    public android.view.View exportBar(final String title, final String subtitle,
                                       final ReportCatalog.Col[] cols, final List<Row> rows,
                                       boolean withPrint) {
        // One tidy row of equal tiles (no 14dp button padding), so every label fits on a phone
        // and nothing is clipped at the sheet edge.
        LinearLayout box = kit.v();
        box.setBackground(Theme.card());
        box.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8));
        LinearLayout row = kit.h();
        row.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        addExportTile(row, "PDF", Theme.DANGER, v -> sharePdf(title, subtitle, cols, rows), true);
        addExportTile(row, "اکسل", Theme.SUCCESS, v -> shareXlsx(title, subtitle, cols, rows), true);
        addExportTile(row, "ورد", Theme.INFO, v -> shareDocx(title, subtitle, cols, rows), true);
        addExportTile(row, "عکس", Theme.VIOLET, v -> sharePng(title, subtitle, cols, rows), true);
        if (withPrint)
            addExportTile(row, "چاپ", Theme.GOLD, v -> printPdf(title, subtitle, cols, rows), true);
        box.addView(row, kit.lp(-1, -2));
        return box;
    }

    private void addExportTile(LinearLayout row, String label, int accent,
                               android.view.View.OnClickListener click, boolean gapBefore) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setTextSize(12f * Theme.fontScale());
        boolean outcome = accent == Theme.SUCCESS || accent == Theme.DANGER
                || accent == Theme.WARNING || accent == Theme.INFO;
        b.setTextColor(outcome ? Theme.TEXT : Theme.sectionText(accent));
        b.setTypeface(Theme.face(true));
        b.setBackground(Theme.ghostButton(accent));
        b.setGravity(android.view.Gravity.CENTER);
        b.setElevation(Theme.dp(1));
        b.setPadding(Theme.dp(2), Theme.dp(8), Theme.dp(2), Theme.dp(8));
        b.setMinHeight(Theme.dp(44));
        b.setMinimumHeight(Theme.dp(44));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setOnClickListener(click);
        if (gapBefore && row.getChildCount() > 0) row.addView(kit.space(5));
        row.addView(b, kit.wlp(1f));
    }

    /** Zoom button: cycle ۱۰۰٪ → ۱۱۵٪ → ۱۳۰٪ → ۹۰٪ → ۱۰۰٪. */
    public void cycleZoom() {
        int next = (settings.zoomIdx() + 1) % 4;
        settings.setZoomIdx(next);
        kit.toast("بزرگ‌نمایی " + zoomLabel(next));
        refreshTheme();
    }

    public static String zoomLabel(int idx) {
        if (idx == 0) return "٪۹۰";
        if (idx == 2) return "٪۱۱۵";
        if (idx == 3) return "٪۱۳۰";
        return "٪۱۰۰";
    }

    /** Share a bitmap (shop card) via the FileProvider. */
    public void shareImage(final android.graphics.Bitmap bmp, final String title) {
        if (bmp == null) {
            kit.toast("تصویری برای اشتراک نیست");
            return;
        }
        kit.toast("در حال آماده‌سازی کارت…");
        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "share");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, "shop-card.png");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                    out.flush();
                }
                runOnUiThread(() -> {
                    try {
                        ShareProvider.share(this, f, "image/png", title);
                    } catch (Exception e) {
                        kit.toast("اشتراک ممکن نشد");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> kit.toast("ساخت تصویر ممکن نشد"));
            }
        }).start();
    }

    /** Detects a downward pull at the top without intercepting ordinary child gestures. */
    private static final class PullToRefreshScrollView extends ScrollView {
        private Runnable refreshListener;
        private float startX;
        private float startY;
        private boolean startedAtTop;
        private boolean pullReachedThreshold;
        private final float triggerDistance;

        PullToRefreshScrollView(android.content.Context context) {
            super(context);
            triggerDistance = 84f * getResources().getDisplayMetrics().density;
        }

        void setOnRefreshListener(Runnable listener) {
            refreshListener = listener;
        }

        @Override
        public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                startX = event.getX();
                startY = event.getY();
                startedAtTop = getScrollY() <= 0;
                pullReachedThreshold = false;
            } else if (action == android.view.MotionEvent.ACTION_MOVE
                    && startedAtTop && getScrollY() <= 0) {
                float dy = event.getY() - startY;
                float dx = event.getX() - startX;
                if (dy >= triggerDistance && dy > Math.abs(dx) * 1.2f) {
                    pullReachedThreshold = true;
                }
            }

            boolean shouldRefresh = action == android.view.MotionEvent.ACTION_UP
                    && pullReachedThreshold && startedAtTop && refreshListener != null;
            Runnable callback = shouldRefresh ? refreshListener : null;
            boolean handled = super.dispatchTouchEvent(event);
            if (action == android.view.MotionEvent.ACTION_UP
                    || action == android.view.MotionEvent.ACTION_CANCEL) {
                startedAtTop = false;
                pullReachedThreshold = false;
            }
            if (callback != null) post(callback);
            return handled;
        }
    }

}
