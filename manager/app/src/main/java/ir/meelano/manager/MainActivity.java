package ir.meelano.manager;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Finger;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Notify;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.data.Settings;
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
import ir.meelano.manager.screens.SearchScreen;
import ir.meelano.manager.screens.SettingsScreen;
import ir.meelano.manager.screens.TradeScreen;
import ir.meelano.manager.screens.UsersScreen;
import ir.meelano.manager.screens.VisitorsScreen;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.FisPrint;
import ir.meelano.manager.ui.MeelanoIcons;
import ir.meelano.manager.ui.Pdf;
import ir.meelano.manager.ui.Theme;

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
    private TextView connDot;
    private TextView backFab;
    private TextView companyName;
    private android.widget.FrameLayout logoBox;
    private android.widget.ImageView logoImg;
    private TextView logoGlyph;

    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final List<String> history = new ArrayList<>();
    private String currentId = "home";
    private boolean unlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.init(this);
        kit = new Kit(this);
        settings = new Settings(this);
        repo = new Repo(this, settings);
        splash();
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
        TextView t = kit.text("مدیریت میلانو", 26, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t, kit.lp(-1, -2));
        TextView s = kit.text("داشبورد مدیریتی آتیران", 12.5f, Theme.MUTED, false);
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
        try { repo.close(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    // ================= PIN gate =================
    private void pinGate() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(Theme.dp(28), Theme.dp(28), Theme.dp(28), Theme.dp(28));
        root.addView(kit.logo(120), new LinearLayout.LayoutParams(Theme.dp(120), Theme.dp(120)));
        TextView t = kit.text("مدیریت میلانو", 22, Theme.TEXT, true);
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Theme.BG);

        // header: connection dot + company logo (from Atiran) + company name + tools
        LinearLayout header = kit.h();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Theme.dp(14), Theme.dp(10), Theme.dp(14), Theme.dp(8));
        connDot = kit.text("●", 13, Theme.WARNING, true);
        header.addView(connDot, kit.lp(-2, -2));
        header.addView(kit.space(8));
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
        header.addView(tools, kit.lp(-2, -2));
        root.addView(header, kit.lp(-1, -2));

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

        buildBottom();
        nav(screens.containsKey(currentId) ? currentId : "home");
        styleBars();
        checkConn();
        refreshCompany();
        Notify.boot(this);
        syncBackFab();
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
    private static final String[] TABS = {"home", "sales", "products", "customers", "reports", "more"};

    private void buildBottom() {
        bottomBar.removeAllViews();
        for (final String id : TABS) {
            final Screen s = screens.get(id);
            LinearLayout b = kit.v();
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, Theme.dp(4), 0, Theme.dp(2));
            LinearLayout badge = new LinearLayout(this);
            badge.setGravity(Gravity.CENTER);
            TextView g = kit.text(s.glyph(), 20, Theme.MUTED, true);
            g.setGravity(Gravity.CENTER);
            badge.addView(g, kit.lp(-2, -2));
            b.addView(badge, new LinearLayout.LayoutParams(Theme.dp(52), Theme.dp(40)));
            TextView l = kit.text(s.id().equals("sales") ? "فروش" : s.title(), 9.5f, Theme.MUTED, true);
            l.setGravity(Gravity.CENTER);
            l.setSingleLine(true);
            b.addView(l, kit.lp(-1, -2));
            b.setTag(id);
            Theme.pressable(b);
            b.setOnClickListener(v -> nav(id));
            bottomBar.addView(b, kit.wlp(1f));
        }
        paintBottom();
    }

    private void paintBottom() {
        for (int i = 0; i < bottomBar.getChildCount(); i++) {
            LinearLayout b = (LinearLayout) bottomBar.getChildAt(i);
            boolean on = TABS[i].equals(currentId)
                    || ("more".equals(TABS[i]) && !isTab(currentId));
            LinearLayout badge = (LinearLayout) b.getChildAt(0);
            badge.setBackground(on ? Theme.avatar(Theme.GOLD) : null);
            ((TextView) badge.getChildAt(0)).setTextColor(on ? 0xFFFFFFFF : Theme.MUTED);
            ((TextView) b.getChildAt(1)).setTextColor(on ? Theme.GOLD : Theme.MUTED);
            b.setBackground(null);
            Theme.pressable(b); // setBackground replaced the ripple — wrap it again
        }
    }

    private boolean isTab(String id) {
        for (String t : TABS) if (t.equals(id)) return true;
        return false;
    }

    public void nav(String id) {
        if (!screens.containsKey(id)) return;
        if (!id.equals(currentId)) {
            history.add(currentId);
            if (history.size() > 30) history.remove(0);
        }
        currentId = id;
        renderCurrent();
        animateContent(true);
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
            nm = "مدیریت میلانو";
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
    public void checkConn() {
        repo.run(c -> Repo.one(c, new Queries.Q("SELECT 1 AS ok")), new Repo.Cb<Row>() {
            @Override
            public void ok(Row v) {
                connDot.setTextColor(Theme.SUCCESS);
            }

            @Override
            public void fail(String faError) {
                connDot.setTextColor(Theme.DANGER);
            }
        });
    }

    public void testConnection(final Repo.Cb<String> cb) {
        testConnection(settings.effHost(), settings.effPort(), settings.effDb(), settings.effUser(), settings.effPass(), cb);
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
