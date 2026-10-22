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
import ir.meelano.manager.core.UpdateCenter;
import ir.meelano.manager.ui.WhatsNew;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.core.SmartLink;
import ir.meelano.manager.ui.LinkPanel;
import ir.meelano.manager.data.NetRoute;
import ir.meelano.manager.core.Money;
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
 * Bottom nav (5 hubs) + header (filter / refresh / connection dot) + content.
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
    private ScrollView scroll;
    private LinearLayout bottomBar;
    /** Host of the bottom bar: holds the sliding selection capsule behind the tabs. */
    private android.widget.FrameLayout bottomHost;
    private View navCapsule;
    private android.widget.ImageView linkIcon;
    private android.net.ConnectivityManager.NetworkCallback linkCb = null;
    private boolean linkCbReg = false;
    private final android.os.Handler linkHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable linkPending = null;
    private TextView licChip;
    private View backFab;
    private TextView backLabel;
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
    private TextView userChip;
    private android.widget.ImageView compactUserAction;
    private boolean unlocked = false;

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
        // Persian voice is a prerequisite: guide the install before the app is used.
        if (!isFinishing() && LicenseStore.unlocked(this) && new Settings(this).connConfigured()) {
            ir.meelano.manager.core.Speaker.onResume(this);
        }
        if (!isFinishing() && AtiranAuth.gate(this)) toLogin();
        Usage.touch(this);
        if (repo != null && !isFinishing() && RoleStore.WAREHOUSE.equals(effRole()))
            WarehouseSync.retry(this, null);
        refreshLicChip();
        refreshUserChip();
        dispatchShown(true);
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

        // header (two rows): row 1 = logo, company name (up to two lines, never squeezed
        // to a stub) and the status line (link, licence, user); row 2 = a full-width
        // toolbar with equal-width, labelled buttons. Nothing competes for the same width.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int widthDp = dm.widthPixels * 160 / dm.densityDpi;
        final boolean tiny = widthDp < 360;
        final boolean compact = widthDp < 520;
        LinearLayout header = kit.v();
        header.setPadding(Theme.dp(12), Theme.dp(10), Theme.dp(12), Theme.dp(8));

        // ---- row 1: brand ----
        LinearLayout brand = kit.h();
        brand.setGravity(Gravity.CENTER_VERTICAL);
        logoBox = new android.widget.FrameLayout(this);
        logoBox.setBackground(Theme.accentAvatar(Theme.GOLD));
        logoGlyph = kit.text("♛", 20, Theme.onAccent(), true);
        logoGlyph.setGravity(Gravity.CENTER);
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
        brand.addView(logoBox, new LinearLayout.LayoutParams(Theme.dp(80), Theme.dp(80)));
        brand.addView(kit.space(10));

        LinearLayout titleCol = kit.v();
        companyName = kit.text(companyTitle(), 22, Theme.TEXT, true);
        companyName.setMaxLines(2);
        companyName.setEllipsize(android.text.TextUtils.TruncateAt.END);
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

        // status line: link state, licence chip, Atiran user chip (small, always visible)
        LinearLayout status = kit.h();
        status.setGravity(Gravity.CENTER_VERTICAL);
        linkIcon = new android.widget.ImageView(this);
        try {
            linkIcon.setImageResource(R.drawable.link_probe);
        } catch (Exception ignored) { Diag.ignored("MainActivity:528", ignored); }
        linkIcon.setContentDescription("وضعیت اتصال — لمس برای جزئیات");
        linkIcon.setPadding(Theme.dp(1), Theme.dp(1), Theme.dp(1), Theme.dp(1));
        Theme.pressable(linkIcon);
        linkIcon.setOnClickListener(v -> {
            try {
                LinkPanel.showStatus(this, settings, () -> checkConn());
            } catch (Exception ignored) { Diag.ignored("MainActivity:535", ignored); }
        });
        status.addView(linkIcon, new LinearLayout.LayoutParams(Theme.dp(20), Theme.dp(20)));
        status.addView(kit.space(6));
        licChip = kit.text("", 10.5f, Theme.GOLD_SOFT, true);
        licChip.setBackground(Theme.pill(Theme.GOLD));
        licChip.setPadding(Theme.dp(8), Theme.dp(3), Theme.dp(8), Theme.dp(3));
        licChip.setSingleLine(true);
        licChip.setMaxWidth(Theme.dp(120));
        licChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Theme.pressable(licChip);
        licChip.setOnClickListener(v -> openLicense());
        status.addView(licChip, kit.lp(-2, -2));
        status.addView(kit.space(6));
        userChip = kit.text("", 10.5f, Theme.GOLD_SOFT, true);
        userChip.setBackground(Theme.pill(Theme.GOLD));
        userChip.setPadding(Theme.dp(8), Theme.dp(3), Theme.dp(8), Theme.dp(3));
        userChip.setSingleLine(true);
        userChip.setMaxWidth(Theme.dp(120));
        userChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Theme.pressable(userChip);
        userChip.setOnClickListener(v -> userMenu());
        status.addView(userChip, kit.lp(-2, -2));
        // Gold tag badge at the end of the chips row, as in the reference header (decorative).
        android.widget.ImageView tagBadge = new android.widget.ImageView(this);
        try {
            tagBadge.setImageResource(R.drawable.mi_sell);
            tagBadge.setColorFilter(Theme.GOLD);
        } catch (Exception ignored) { Diag.ignored("MainActivity:tagBadge", ignored); }
        tagBadge.setPadding(Theme.dp(5), Theme.dp(5), Theme.dp(5), Theme.dp(5));
        tagBadge.setBackground(Theme.ghostButton(Theme.GOLD));
        tagBadge.setClickable(false);
        status.addView(kit.space(8));
        status.addView(tagBadge, new LinearLayout.LayoutParams(Theme.dp(30), Theme.dp(30)));
        titleCol.addView(status, kit.lp(-2, -2));
        brand.addView(titleCol, kit.wlp(1f));
        header.addView(brand, kit.lp(-1, -2));
        refreshUserChip();
        header.addView(kit.space(8));

        // ---- row 2: toolbar (equal-width cells with Persian labels) ----
        LinearLayout tools = kit.h();
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackground(Theme.ghostButton(Theme.GOLD));
        tools.setPadding(Theme.dp(3), Theme.dp(3), Theme.dp(3), Theme.dp(3));

        if (!tiny) tools.addView(toolCell(toolIcon(R.drawable.mi_search), "جستجو", "جستجو", () -> nav("search")), toolLp());

        final android.widget.ImageView themeIcon = toolIcon(R.drawable.mi_contrast);
        if (!tiny) tools.addView(toolCell(themeIcon, "تم", "تغییر تم روشن / تیره", () -> {
            themeIcon.animate().rotationBy(180f).setDuration(420).start();
            boolean toLight = !"light".equals(settings.themeMode());
            settings.setThemeMode(toLight ? "light" : "dark");
            kit.toast(toLight ? "تم روشن فعال شد" : "تم تیره فعال شد");
            refreshTheme();
        }), toolLp());

        if (!compact) {
            android.widget.ImageView zoom = toolIcon(R.drawable.mi_zoom);
            tools.addView(toolCell(zoom, "بزرگ‌نمایی", "بزرگ‌نمایی متن", () -> cycleZoom()), toolLp());
        }

        final android.widget.ImageView refreshIcon = toolIcon(R.drawable.mi_refresh);
        tools.addView(toolCell(refreshIcon, "تازه‌سازی", "تازه‌سازی", () -> {
            refreshIcon.animate().rotationBy(360f).setDuration(600).start();
            renderCurrent();
        }), toolLp());
        tools.addView(toolCell(toolIcon(R.drawable.mi_settings), "تنظیمات", "تنظیمات", () -> nav("settings")), toolLp());

        android.widget.ImageView account = new android.widget.ImageView(this);
        try {
            account.setImageResource(R.drawable.mi_person);
            account.setColorFilter(Theme.GOLD_SOFT);
        } catch (Exception ignored) { Diag.ignored("MainActivity:595", ignored); }
        compactUserAction = account;
        tools.addView(toolCell(account, "حساب", "حساب کاربری؛ خروج یا تغییر کاربر", () -> userMenu()), toolLp());

        for (int i = tools.getChildCount() - 1; i > 0; i--) {
            tools.addView(vdiv(), i, new LinearLayout.LayoutParams(Theme.dp(1), Theme.dp(40)));
        }
        header.addView(tools, kit.lp(-1, -2));
        refreshUserChip();
        root.addView(header, kit.lp(-1, -2));
        refreshLicChip();

        // content (centered max-width column on tablets / wide screens)
        scroll = new ScrollView(this);
        contentWrap = kit.h();
        contentWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(contentWrap, new ScrollView.LayoutParams(-1, -2));
        content = newPage();
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // bottom bar
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomBar.setPadding(Theme.dp(6), Theme.dp(8), Theme.dp(6), Theme.dp(10));
        bottomHost = new android.widget.FrameLayout(this);
        bottomHost.setBackground(Theme.bottomBar());
        navCapsule = new View(this);
        navCapsule.setVisibility(View.GONE);
        navCapsule.setBackground(Theme.bottomNavSelected());
        bottomHost.addView(navCapsule, new android.widget.FrameLayout.LayoutParams(Theme.dp(40), Theme.dp(40),
                Gravity.TOP | Gravity.LEFT));
        bottomHost.addView(bottomBar, new android.widget.FrameLayout.LayoutParams(-1, -2));
        root.addView(bottomHost, kit.lp(-1, -2));

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
        reg(new UsersScreen(this));
        reg(new ProfitScreen(this));
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
        syncBackFab();
        watchLink();
    }

    /** One toolbar cell: icon above a small Persian label, equal width via {@link #toolLp()}. */
    /** Gold vector icon for the header toolbar (replaces text glyphs, which rendered unevenly). */
    private android.widget.ImageView toolIcon(int res) {
        android.widget.ImageView iv = new android.widget.ImageView(this);
        try {
            int res3 = Theme.icon3d(res);
            iv.setImageResource(res3);
            if (!Theme.isIcon3d(res3)) iv.setColorFilter(Theme.GOLD_SOFT);
        } catch (Exception ignored) { Diag.ignored("MainActivity:694", ignored); }
        return iv;
    }

    private LinearLayout toolCell(View icon, String label, String desc, Runnable onTap) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setContentDescription(desc);
        cell.setMinimumHeight(Theme.dp(62));
        Theme.pressable(cell);
        cell.setOnClickListener(v -> onTap.run());
        icon.setClickable(false);
        cell.addView(icon, new LinearLayout.LayoutParams(Theme.dp(28), Theme.dp(28)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11 * Theme.fontScale());
        t.setTextColor(Theme.DARK ? Theme.GOLD_SOFT : Theme.MUTED);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        cell.addView(t, new LinearLayout.LayoutParams(-2, -2));
        return cell;
    }

    private LinearLayout.LayoutParams toolLp() {
        return new LinearLayout.LayoutParams(0, Theme.dp(62), 1f);
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
        for (final String id : tabs) {
            final Screen s = screens.get(id);
            if (s == null) continue;
            LinearLayout b = kit.v();
            b.setGravity(Gravity.CENTER);
            b.setMinimumHeight(Theme.dp(64));
            b.setPadding(Theme.dp(2), Theme.dp(4), Theme.dp(2), Theme.dp(2));
            LinearLayout badge = new LinearLayout(this);
            badge.setGravity(Gravity.CENTER);
            int iconRes = MeelanoIcons.iconFor(s.glyph());
            // A 3D bitmap must not be tinted; remember that on the symbol.
            boolean is3d = iconRes != 0 && Theme.isIcon3d(Theme.icon3d(iconRes));
            if (iconRes != 0) {
                android.widget.ImageView symbol = kit.icon(iconRes, Theme.MUTED, 22);
                symbol.setTag(is3d ? Boolean.TRUE : Boolean.FALSE);
                badge.addView(symbol, new LinearLayout.LayoutParams(Theme.dp(24), Theme.dp(24)));
            } else {
                TextView symbol = kit.text(s.glyph(), 20, Theme.MUTED, true);
                symbol.setGravity(Gravity.CENTER);
                badge.addView(symbol, kit.lp(-2, -2));
            }
            b.addView(badge, new LinearLayout.LayoutParams(Theme.dp(46), Theme.dp(36)));
            String tl = RoleStore.tabLabel(id);
            TextView l = kit.text(tl != null ? tl : s.title(), 10.5f, Theme.MUTED, true);
            l.setGravity(Gravity.CENTER);
            l.setSingleLine(true);
            l.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.addView(l, kit.lp(-1, -2));
            View marker = new View(this);
            marker.setVisibility(View.INVISIBLE);
            marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Theme.dp(18), Theme.dp(2));
            mp.setMargins(0, Theme.dp(3), 0, 0);
            b.addView(marker, mp);
            b.setTag(id);
            b.setContentDescription((tl != null ? tl : s.title()) + "، رفتن به بخش");
            if (!RoleStore.allowed(this, id)) b.setAlpha(0.35f);
            Theme.pressable(b);
            b.setOnClickListener(v -> nav(id));
            bottomBar.addView(b, kit.wlp(1f));
        }
        paintBottom();
    }

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
            if (animate) {
                navCapsule.animate().translationX(x).setDuration(280)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            } else {
                navCapsule.setTranslationX(x);
            }
        };
        if (tab.getWidth() > 0) go.run(); else tab.post(go);
    }

    private void paintBottom() {
        selectedNavIndex = -1;
        for (int i = 0; i < bottomBar.getChildCount(); i++) {
            LinearLayout b = (LinearLayout) bottomBar.getChildAt(i);
            boolean on = i < tabs.length && (tabs[i].equals(currentId)
                    || ("more".equals(tabs[i]) && !isTab(currentId)));
            LinearLayout badge = (LinearLayout) b.getChildAt(0);
            badge.setBackground(on ? Theme.bottomNavIconSelected() : null);
            // Gold underline under the selected tab (the marker was never shown before).
            View under = b.getChildCount() > 2 ? b.getChildAt(2) : null;
            if (under != null) {
                under.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
                under.setBackground(Theme.pill(Theme.GOLD));
            }
            // Gold halo under the selected capsule (API 28+ coloured shadow; older devices keep the rim).
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                badge.setElevation(on ? Theme.dp(6) : 0f);
                badge.setOutlineSpotShadowColor(Theme.GOLD);
                badge.setOutlineAmbientShadowColor(Theme.GOLD);
            }
            View symbol = badge.getChildAt(0);
            if (symbol instanceof android.widget.ImageView) {
                if (!Boolean.TRUE.equals(symbol.getTag()))
                    ((android.widget.ImageView) symbol).setColorFilter(on ? Theme.selectedNavInk() : Theme.MUTED);
            } else if (symbol instanceof TextView) {
                ((TextView) symbol).setTextColor(on ? Theme.selectedNavInk() : Theme.MUTED);
            }
            TextView label = (TextView) b.getChildAt(1);
            label.setTextColor(on ? Theme.GOLD_SOFT : Theme.MUTED);
            label.setTextSize(on ? 11f : 10.5f);
            b.getChildAt(2).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            b.getChildAt(2).setBackground(on ? Theme.bottomNavMarker() : null);
            b.setBackground(null); // the sliding capsule marks the selected tab instead
            if (on) selectedNavIndex = i;
            b.setSelected(on);
            b.setElevation(on ? Theme.dp(2) : 0f);
            b.setContentDescription(label.getText() + (on ? "، بخش فعال" : "، رفتن به بخش"));
            Theme.pressable(b); // Re-wrap the selected-state background with touch feedback.
        }
        moveCapsule(true);
    }

    private boolean isTab(String id) {
        for (String t : tabs) if (t.equals(id)) return true;
        return false;
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
                logoImg.setImageResource(R.drawable.meelano_3d);
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
        renderGen++;
        // A new page for every render: a slow query that returns after the user has moved to
        // another section fills the old, detached page and can no longer repaint the screen on display.
        content = newPage();
        refreshChrome();
        scroll.scrollTo(0, 0);
        s.render(content);
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
        // Bottom room so the back capsule never covers the last row.
        page.setPadding(Theme.dp(12), Theme.dp(4), Theme.dp(12), Theme.dp(128));
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

    /** Repaint bottom bar + back FAB (screens call this after internal state changes). */
    public void refreshChrome() {
        Screen s = screens.get(currentId);
        if (s == null) return;
        paintBottom();
        if (backFab != null) backFab.setVisibility("home".equals(currentId) ? View.GONE : View.VISIBLE);
        updateBackLabel();
    }

    /** The back capsule names the screen it will actually return to. */
    private void updateBackLabel() {
        if (backLabel == null) return;
        String target = history.isEmpty() ? "home" : history.get(history.size() - 1);
        Screen t = screens.get(target);
        backLabel.setText(t == null ? "بازگشت" : "بازگشت به «" + t.title() + "»");
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

    /** Shared back logic for the system back key and the golden back FAB. */
    public void goBack() {
        Screen s = screens.get(currentId);
        if (s != null && s.onBack()) return;
        if (!history.isEmpty()) {
            currentId = history.remove(history.size() - 1);
            renderCurrent();
            animateContent(false);
        } else nav("home");
    }

    /** Golden circular back button, floating above the bottom bar on every section but Home. */
    /**
     * The single back control for every screen: one gold capsule with a round arrow
     * and the word «بازگشت», docked bottom-centre just above the tab bar. It stays in
     * the same place on every page so the user always knows where to find it.
     */
    private void syncBackFab() {
        try {
            if (backFab != null && backFab.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) backFab.getParent()).removeView(backFab);
        } catch (Exception ignored) { Diag.ignored("MainActivity:1079", ignored); }
        backFab = null;
        // Floating gold disc: one arrow, no label, a soft gold glow underneath.
        final int ink = Theme.onAccent(Theme.GOLD);
        android.graphics.drawable.GradientDrawable disc = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.GOLD_SOFT, Theme.GOLD});
        disc.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        disc.setStroke(Theme.dp(1), Theme.alpha(ink, 60));
        TextView arrow = kit.text("\u2192", 22f, ink, true);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(disc);
        arrow.setElevation(Theme.dp(14));
        arrow.setContentDescription("بازگشت به صفحه قبلی");
        Theme.pressable(arrow);
        arrow.setOnClickListener(v -> goBack());
        backLabel = null;
        // Compact disc in the bottom corner, above the tab bar, so list rows stay readable.
        android.widget.FrameLayout.LayoutParams p = new android.widget.FrameLayout.LayoutParams(
                Theme.dp(46), Theme.dp(46), Gravity.BOTTOM | Gravity.END);
        p.setMargins(Theme.dp(14), 0, Theme.dp(14), Theme.dp(96));
        try {
            addContentView(arrow, p);
        } catch (Exception ignored) {
            return;
        }
        backFab = arrow;
        backFab.setVisibility("home".equals(currentId) ? View.GONE : View.VISIBLE);
        updateBackLabel();
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
            // Location is optional: redraw the home card so weather follows the new permission at once.
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
                licChip.setTextColor(Theme.DANGER);
            } else if (s.daysLeft <= 7) {
                ir.meelano.manager.ui.MeelanoIcons.set(licChip, "📅 " + Money.fa(String.valueOf(s.daysLeft)) + " روز");
                licChip.setTextColor(Theme.WARNING);
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

    public void refreshUserChip() {
        try {
            boolean hasSession = AtiranAuth.hasSession(this);
            if (userChip != null) {
                if (!hasSession) {
                    userChip.setVisibility(View.GONE);
                } else {
                    userChip.setVisibility(View.VISIBLE);
                    String nm = AtiranAuth.sessionName(this);
                    if (nm.isEmpty()) nm = AtiranAuth.sessionUser(this);
                    if (nm.isEmpty()) nm = RoleStore.faName(AtiranAuth.sessionRole(this));
                    ir.meelano.manager.ui.MeelanoIcons.set(userChip, "\uD83D\uDC64 " + nm);
                }
            }
            if (compactUserAction != null) {
                compactUserAction.setVisibility(hasSession ? View.VISIBLE : View.GONE);
                compactUserAction.setContentDescription(hasSession
                        ? "حساب کاربری؛ خروج یا تغییر کاربر" : "ورود به حساب کاربری");
            }
        } catch (Exception ignored) { Diag.ignored("MainActivity:1399", ignored); }
    }

    /** Who am I + logout (+ PIN-management shortcut for the manager). */
    private void userMenu() {
        try {
            if (!AtiranAuth.hasSession(this)) return;
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
            String nm = AtiranAuth.sessionName(this);
            if (nm.isEmpty()) nm = AtiranAuth.sessionUser(this);
            body.addView(kit.kv("کاربر", nm.isEmpty() ? "—" : nm, Theme.TEXT), kit.lp(-1, -2));
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
        setLinkIcon("probe");
        repo.run(c -> Repo.one(c, new Queries.Q("SELECT 1 AS ok")), new Repo.Cb<Row>() {
            @Override
            public void ok(Row v) {
                String last = "";
                try {
                    last = settings.linkLast();
                } catch (Exception ignored) { Diag.ignored("MainActivity:1444", ignored); }
                setLinkIcon(last.isEmpty() ? SmartLink.LAN : last);
            }

            @Override
            public void fail(String faError) {
                setLinkIcon("off");
            }
        });
    }

    /** Header link icon: inside / outside / probing (spins) / offline. */
    private void setLinkIcon(String kind) {
        if (linkIcon == null) return;
        try {
            linkIcon.clearAnimation();
            if ("probe".equals(kind)) {
                linkIcon.setImageResource(R.drawable.link_probe);
                android.view.animation.RotateAnimation spin =
                        new android.view.animation.RotateAnimation(0, 360,
                                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
                spin.setDuration(900);
                spin.setRepeatCount(android.view.animation.Animation.INFINITE);
                spin.setInterpolator(new android.view.animation.LinearInterpolator());
                linkIcon.startAnimation(spin);
                linkIcon.setContentDescription("در حال بررسی اتصال");
            } else if (SmartLink.WAN.equals(kind)) {
                linkIcon.setImageResource(R.drawable.link_wan);
                linkIcon.setContentDescription("متصل از خارج شبکه — لمس برای جزئیات");
            } else if (SmartLink.LAN.equals(kind)) {
                linkIcon.setImageResource(R.drawable.link_lan);
                linkIcon.setContentDescription("متصل از داخل شبکه — لمس برای جزئیات");
            } else {
                linkIcon.setImageResource(R.drawable.link_off);
                linkIcon.setContentDescription("قطع — لمس برای عیب‌یابی");
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
        b.setTextColor(accent);
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
}
