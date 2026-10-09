package ir.meelano.manager;

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
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.SmsIo;
import ir.meelano.manager.core.Usage;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.screens.AnbarScreen;
import ir.meelano.manager.screens.CashScreen;
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
    private ScrollView scroll;
    private LinearLayout bottomBar;
    private android.widget.ImageView linkIcon;
    private android.net.ConnectivityManager.NetworkCallback linkCb = null;
    private boolean linkCbReg = false;
    private final android.os.Handler linkHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable linkPending = null;
    private TextView licChip;
    private TextView backFab;
    private TextView companyName;
    private android.widget.FrameLayout logoBox;
    private android.widget.ImageView logoImg;
    private TextView logoGlyph;

    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final List<String> history = new ArrayList<>();
    private String currentId = "home";
    private boolean rolePicked;
    private boolean shellStarted;
    private TextView userChip;
    private boolean unlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!LicenseStore.unlocked(this)) {
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
        repo = new Repo(this, settings);
        splash();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-validate the license every time the app comes to the foreground
        // (expired / clock-tampered devices fall back to the activation screen).
        if (!isFinishing() && !LicenseStore.unlocked(this)) toLicense();
        if (!isFinishing() && AtiranAuth.gate(this)) toLogin();
        Usage.touch(this);
        refreshLicChip();
        refreshUserChip();
    }

    @Override
    protected void onPause() {
        Usage.paused(this);
        super.onPause();
    }

    private void toLicense() {
        try {
            Intent i = new Intent(this, LicenseActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { }
        finish();
    }

    private void toLogin() {
        try {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { }
        finish();
    }

    /** Log out the Atiran user and return to the login screen. */
    public void logout() {
        try {
            AtiranAuth.clearSession(this);
        } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
        linkCbReg = false;
        try {
            if (linkPending != null) linkHandler.removeCallbacks(linkPending);
        } catch (Exception ignored) { }
        try { repo.close(); } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
    }

    private void linkChanged() {
        try {
            if (linkPending != null) linkHandler.removeCallbacks(linkPending);
            linkPending = () -> {
                try {
                    if (!isFinishing()) checkConn();
                } catch (Exception ignored) { }
            };
            linkHandler.postDelayed(linkPending, 1500);
        } catch (Exception ignored) { }
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
                currentId = AtiranAuth.startScreen(this);
            }
        }
        if (RoleStore.enabled(this) && !rolePicked) {
            roleGate(null);
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Theme.BG);

        // header: connection dot + company logo (from Atiran) + company name + tools
        LinearLayout header = kit.h();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Theme.dp(14), Theme.dp(10), Theme.dp(14), Theme.dp(8));
        linkIcon = new android.widget.ImageView(this);
        try {
            linkIcon.setImageResource(R.drawable.link_probe);
        } catch (Exception ignored) { }
        linkIcon.setContentDescription("وضعیت اتصال — لمس برای جزئیات");
        linkIcon.setPadding(Theme.dp(2), Theme.dp(2), Theme.dp(2), Theme.dp(2));
        Theme.pressable(linkIcon);
        linkIcon.setOnClickListener(v -> {
            try {
                LinkPanel.showStatus(this, settings, () -> checkConn());
            } catch (Exception ignored) { }
        });
        header.addView(linkIcon, new LinearLayout.LayoutParams(Theme.dp(30), Theme.dp(30)));
        header.addView(kit.space(8));
        // License chip: subtle remaining-time pill, taps through to activation/support.
        licChip = kit.text("", 10.5f, Theme.GOLD_SOFT, true);
        licChip.setBackground(Theme.ghostButton(Theme.GOLD));
        licChip.setPadding(Theme.dp(9), Theme.dp(4), Theme.dp(9), Theme.dp(4));
        licChip.setSingleLine(true);
        Theme.pressable(licChip);
        licChip.setOnClickListener(v -> openLicense());
        header.addView(licChip, kit.lp(-2, -2));
        header.addView(kit.space(8));
        // Atiran user chip: who is logged in, taps through to logout.
        userChip = kit.text("", 10.5f, Theme.GOLD_SOFT, true);
        userChip.setBackground(Theme.ghostButton(Theme.GOLD));
        userChip.setPadding(Theme.dp(9), Theme.dp(4), Theme.dp(9), Theme.dp(4));
        userChip.setSingleLine(true);
        userChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Theme.pressable(userChip);
        userChip.setOnClickListener(v -> userMenu());
        header.addView(userChip, kit.lp(-2, -2));
        header.addView(kit.space(8));
        refreshUserChip();
        logoBox = new android.widget.FrameLayout(this);
        logoBox.setBackground(Theme.avatar(Theme.GOLD));
        logoGlyph = kit.text("♛", 20, 0xFFFFFFFF, true);
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
        } catch (Exception ignored) { }
        int lpad = Theme.dp(2);
        logoImg.setPadding(lpad, lpad, lpad, lpad);
        logoImg.setVisibility(View.GONE);
        logoBox.addView(logoImg, new android.widget.FrameLayout.LayoutParams(-1, -1));
        header.addView(logoBox, new LinearLayout.LayoutParams(Theme.dp(46), Theme.dp(46)));
        header.addView(kit.space(8));
        companyName = kit.text(companyTitle(), 16, Theme.TEXT, true);
        companyName.setSingleLine(true);
        companyName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(companyName, kit.wlp(1f));
        LinearLayout tools = kit.h();
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackground(Theme.ghostButton(Theme.GOLD));
        tools.setPadding(Theme.dp(2), Theme.dp(2), Theme.dp(2), Theme.dp(2));
        TextView gsearch = kit.text("⌕", 18, Theme.GOLD_SOFT, true);
        gsearch.setPadding(Theme.dp(11), Theme.dp(6), Theme.dp(11), Theme.dp(6));
        Theme.pressable(gsearch);
        gsearch.setOnClickListener(v -> nav("search"));
        MeelanoIcons.iconize(gsearch);
        tools.addView(gsearch, kit.lp(-2, -2));
        tools.addView(vdiv(), kit.lp(Theme.dp(1), Theme.dp(22)));
        TextView themeBtn = kit.text("◐", 18, Theme.GOLD, true);
        themeBtn.setPadding(Theme.dp(11), Theme.dp(6), Theme.dp(11), Theme.dp(6));
        themeBtn.setContentDescription("تغییر تم روشن / تیره");
        Theme.pressable(themeBtn);
        themeBtn.setOnClickListener(v -> {
            boolean toLight = !"light".equals(settings.themeMode());
            settings.setThemeMode(toLight ? "light" : "dark");
            kit.toast(toLight ? "تم روشن فعال شد" : "تم تیره فعال شد");
            refreshTheme();
        });
        MeelanoIcons.iconize(themeBtn);
        tools.addView(themeBtn, kit.lp(-2, -2));
        tools.addView(vdiv(), kit.lp(Theme.dp(1), Theme.dp(22)));
        TextView refresh = kit.text("⟳", 18, Theme.GOLD_SOFT, true);
        refresh.setPadding(Theme.dp(11), Theme.dp(6), Theme.dp(11), Theme.dp(6));
        Theme.pressable(refresh);
        refresh.setOnClickListener(v -> renderCurrent());
        MeelanoIcons.iconize(refresh);
        tools.addView(refresh, kit.lp(-2, -2));
        tools.addView(vdiv(), kit.lp(Theme.dp(1), Theme.dp(22)));
        TextView sett = kit.text("⚙", 18, Theme.GOLD_SOFT, true);
        sett.setPadding(Theme.dp(11), Theme.dp(6), Theme.dp(11), Theme.dp(6));
        sett.setContentDescription("تنظیمات");
        Theme.pressable(sett);
        sett.setOnClickListener(v -> nav("settings"));
        MeelanoIcons.iconize(sett);
        tools.addView(sett, kit.lp(-2, -2));
        tools.addView(vdiv(), kit.lp(Theme.dp(1), Theme.dp(22)));
        android.widget.ImageView zoom = new android.widget.ImageView(this);
        try {
            zoom.setImageResource(R.drawable.mi_zoom);
            zoom.setColorFilter(Theme.GOLD_SOFT);
        } catch (Exception ignored) { }
        zoom.setPadding(Theme.dp(11), Theme.dp(8), Theme.dp(11), Theme.dp(8));
        zoom.setContentDescription("بزرگ‌نمایی متن");
        Theme.pressable(zoom);
        zoom.setOnClickListener(v -> cycleZoom());
        tools.addView(zoom, new LinearLayout.LayoutParams(Theme.dp(42), Theme.dp(36)));
        header.addView(tools, kit.lp(-2, -2));
        root.addView(header, kit.lp(-1, -2));
        refreshLicChip();

        // content (centered max-width column on tablets / wide screens)
        scroll = new ScrollView(this);
        LinearLayout centerWrap = kit.h();
        centerWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        content = kit.v();
        content.setPadding(Theme.dp(12), Theme.dp(4), Theme.dp(12), Theme.dp(16));
        scroll.addView(centerWrap, new ScrollView.LayoutParams(-1, -2));
        int sw = getResources().getDisplayMetrics().widthPixels;
        centerWrap.addView(content, new LinearLayout.LayoutParams(sw > Theme.dp(720) ? Theme.dp(640) : -1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // bottom bar
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomBar.setBackground(Theme.bottomBar());
        bottomBar.setPadding(Theme.dp(6), Theme.dp(8), Theme.dp(6), Theme.dp(10));
        root.addView(bottomBar, kit.lp(-1, -2));

        setContentView(root);

        // screens
        reg(new HomeScreen(this));
        reg(new TradeScreen(this, true));
        reg(new TradeScreen(this, false));
        reg(new MoneyScreen(this, 0));
        reg(new MoneyScreen(this, 1));
        reg(new ChequesScreen(this));
        reg(new CashScreen(this));
        reg(new ProductsScreen(this));
        reg(new CustomersScreen(this));
        reg(new VisitorsScreen(this));
        reg(new UsersScreen(this));
        reg(new ProfitScreen(this));
        reg(new DuesScreen(this));
        reg(new ReportsScreen(this));
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

        buildBottom();
        nav(screens.containsKey(currentId) ? currentId : "home");
        styleBars();
        checkConn();
        refreshCompany();
        Notify.boot(this);
        syncBackFab();
        // v24: What's New on upgrade (once), then the silent licensed update check.
        try {
            WhatsNew.maybeShow(this, () -> UpdateCenter.autoCheck(this));
        } catch (Exception ignored) { }
        watchLink();
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
        } catch (Exception ignored) { }
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
            b.setPadding(0, Theme.dp(4), 0, Theme.dp(2));
            LinearLayout badge = new LinearLayout(this);
            badge.setGravity(Gravity.CENTER);
            TextView g = kit.text(s.glyph(), 20, Theme.MUTED, true);
            g.setGravity(Gravity.CENTER);
            badge.addView(g, kit.lp(-2, -2));
            b.addView(badge, new LinearLayout.LayoutParams(Theme.dp(52), Theme.dp(40)));
            String tl = RoleStore.tabLabel(id);
            TextView l = kit.text(tl != null ? tl : s.title(), 9.5f, Theme.MUTED, true);
            l.setGravity(Gravity.CENTER);
            l.setSingleLine(true);
            b.addView(l, kit.lp(-1, -2));
            b.setTag(id);
            if (!RoleStore.allowed(this, id)) b.setAlpha(0.35f);
            Theme.pressable(b);
            b.setOnClickListener(v -> nav(id));
            bottomBar.addView(b, kit.wlp(1f));
        }
        paintBottom();
    }

    private void paintBottom() {
        for (int i = 0; i < bottomBar.getChildCount(); i++) {
            LinearLayout b = (LinearLayout) bottomBar.getChildAt(i);
            boolean on = i < tabs.length && (tabs[i].equals(currentId)
                    || ("more".equals(tabs[i]) && !isTab(currentId)));
            LinearLayout badge = (LinearLayout) b.getChildAt(0);
            badge.setBackground(on ? Theme.avatar(Theme.GOLD) : null);
            ((TextView) badge.getChildAt(0)).setTextColor(on ? 0xFFFFFFFF : Theme.MUTED);
            ((TextView) b.getChildAt(1)).setTextColor(on ? Theme.GOLD : Theme.MUTED);
            b.setBackground(null);
            Theme.pressable(b); // setBackground replaced the ripple — wrap it again
        }
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
            content.animate().cancel();
            content.setTranslationX((forward ? -1 : 1) * Theme.dp(48));
            content.setAlpha(0.35f);
            content.animate().translationX(0f).alpha(1f).setDuration(220).start();
        } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
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
            if (companyName != null) companyName.setText(companyTitle());
            android.graphics.Bitmap b = Company.logo(this);
            if (logoImg != null && logoGlyph != null) {
                if (b != null) {
                    logoImg.setImageBitmap(b);
                    logoImg.setVisibility(View.VISIBLE);
                    logoGlyph.setVisibility(View.GONE);
                } else {
                    logoImg.setVisibility(View.GONE);
                    logoGlyph.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception ignored) { }
    }

    private void renderCurrent() {
        Screen s = screens.get(currentId);
        if (s == null) return;
        refreshChrome();
        scroll.scrollTo(0, 0);
        s.render(content);
    }

    /** Repaint bottom bar + back FAB (screens call this after internal state changes). */
    public void refreshChrome() {
        Screen s = screens.get(currentId);
        if (s == null) return;
        paintBottom();
        if (backFab != null) backFab.setVisibility("home".equals(currentId) ? View.GONE : View.VISIBLE);
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
    private void syncBackFab() {
        try {
            if (backFab != null && backFab.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) backFab.getParent()).removeView(backFab);
        } catch (Exception ignored) {
        }
        backFab = null;
        TextView f = kit.text("\u2192", 26, 0xFFFFFFFF, true);
        f.setGravity(Gravity.CENTER);
        f.setBackground(Theme.avatar(Theme.GOLD));
        f.setElevation(Theme.dp(6));
        f.setContentDescription("برگشت");
        Theme.pressable(f);
        f.setOnClickListener(v -> goBack());
        android.widget.FrameLayout.LayoutParams p = new android.widget.FrameLayout.LayoutParams(
                Theme.dp(58), Theme.dp(58), Gravity.BOTTOM | Gravity.LEFT);
        p.setMargins(Theme.dp(16), 0, 0, Theme.dp(100));
        try {
            addContentView(f, p);
        } catch (Exception ignored) {
            return;
        }
        backFab = f;
        backFab.setVisibility("home".equals(currentId) ? View.GONE : View.VISIBLE);
    }

    // ================= Persian voice search =================
    private static final int VOICE_REQ = 901;
    private java.util.function.Consumer<String> voiceCb;

    /** Start Persian speech recognition; the transcript is delivered to cb. */
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
            } catch (Exception ignored) { }
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
                    } catch (Exception ignored) { }
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
            } catch (Exception ignored) { }
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
                licChip.setText("◈ دائمی");
                licChip.setTextColor(Theme.GOLD_SOFT);
            } else if (s.daysLeft <= 7) {
                licChip.setText("◈ " + Money.fa(String.valueOf(s.daysLeft)) + " روز!");
                licChip.setTextColor(Theme.DANGER);
            } else {
                licChip.setText("◈ " + Money.fa(String.valueOf(s.daysLeft)) + " روز");
                licChip.setTextColor(Theme.GOLD_SOFT);
            }
        } catch (Exception e) {
            try {
                licChip.setVisibility(View.GONE);
            } catch (Exception ignored) { }
        }
    }

    private void openLicense() {
        try {
            startActivity(new Intent(this, LicenseActivity.class));
        } catch (Exception ignored) { }
    }

    public void refreshUserChip() {
        try {
            if (userChip == null) return;
            if (!AtiranAuth.hasSession(this)) {
                userChip.setVisibility(View.GONE);
                return;
            }
            userChip.setVisibility(View.VISIBLE);
            String nm = AtiranAuth.sessionName(this);
            if (nm.isEmpty()) nm = AtiranAuth.sessionUser(this);
            if (nm.isEmpty()) nm = RoleStore.faName(AtiranAuth.sessionRole(this));
            userChip.setText("\uD83D\uDC64 " + nm);
        } catch (Exception ignored) { }
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
                    } catch (Exception ignored) { }
                    nav("users");
                }), kit.lp(-1, -2));
            body.addView(kit.btn("خروج / تغییر کاربر", v -> {
                try {
                    box[0].dismiss();
                } catch (Exception ignored) { }
                logout();
            }), kit.lp(-1, -2));
            box[0] = kit.dialog("حساب کاربری", body, true);
            box[0].show();
        } catch (Exception ignored) { }
    }

    public void checkConn() {
        setLinkIcon("probe");
        repo.run(c -> Repo.one(c, new Queries.Q("SELECT 1 AS ok")), new Repo.Cb<Row>() {
            @Override
            public void ok(Row v) {
                String last = "";
                try {
                    last = settings.linkLast();
                } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
    }

    public void testConnection(final Repo.Cb<String> cb) {
        boolean vpn = false;
        try {
            vpn = NetRoute.isVpnActive(this);
        } catch (Exception ignored) { }
        SmartLink.diagnose(this, settings, vpn, r -> {
            try {
                if (r.anyOk) cb.ok("✓ متصل از مسیر " + SmartLink.kindShort(r.activeKind));
                else cb.fail(r.verdict);
            } catch (Exception ignored) { }
        });
    }

    /** Probe explicit (possibly unsaved) connection values. */
    public void testConnection(String host, int port, String db, String user, String pass, final Repo.Cb<String> cb) {
        repo.runWith(host, port, db, user, pass, c -> {
            Meta m = new Meta(c);
            int n = 0;
            for (String t : new String[]{"sailfact", "buyfact", "dar", "getchk", "putchk", "CUSTOMERS", "inventory", "visitors"})
                if (m.table(t)) n++;
            return "اتصال برقرار شد • " + Money.fa(String.valueOf(n)) + " جدول اصلی در دسترس";
        }, cb);
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
        LinearLayout box = kit.v();
        LinearLayout r1 = kit.h();
        r1.addView(kit.btnGhost("▤ PDF", Theme.DANGER, v -> sharePdf(title, subtitle, cols, rows)), kit.wlp(1f));
        r1.addView(kit.space(8));
        r1.addView(kit.btnGhost("▦ اکسل", Theme.SUCCESS, v -> shareXlsx(title, subtitle, cols, rows)), kit.wlp(1f));
        r1.addView(kit.space(8));
        r1.addView(kit.btnGhost("✎ ورد", Theme.INFO, v -> shareDocx(title, subtitle, cols, rows)), kit.wlp(1f));
        box.addView(r1, kit.lp(-1, -2));
        box.addView(kit.gap(8));
        LinearLayout r2 = kit.h();
        r2.addView(kit.btnGhost("▧ عکس", Theme.VIOLET, v -> sharePng(title, subtitle, cols, rows)), kit.wlp(1f));
        r2.addView(kit.space(8));
        r2.addView(kit.btnGhost("🖨 چاپ", Theme.GOLD, v -> printPdf(title, subtitle, cols, rows)), kit.wlp(1f));
        box.addView(r2, kit.lp(-1, -2));
        return box;
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
