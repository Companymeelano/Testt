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
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.screens.CashScreen;
import ir.meelano.manager.screens.ChequesScreen;
import ir.meelano.manager.screens.CustomersScreen;
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
    private TextView filterBtn;
    private TextView rangeLine;

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
        root.addView(kit.logo(110), new LinearLayout.LayoutParams(Theme.dp(110), Theme.dp(110)));
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
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(450).start();
        dev.setScaleX(0.92f);
        dev.setScaleY(0.92f);
        dev.animate().scaleX(1f).scaleY(1f).setDuration(900).start();
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
            getWindow().setNavigationBarColor(Theme.statusBar());
            int vis = getWindow().getDecorView().getSystemUiVisibility();
            if (Theme.isLight()) vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
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
        root.setBackgroundColor(Theme.BG);

        // header
        LinearLayout header = kit.h();
        header.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(8));
        connDot = kit.text("●", 13, Theme.WARNING, true);
        header.addView(connDot, kit.lp(-2, -2));
        header.addView(kit.space(8));
        LinearLayout titleBox = kit.v();
        TextView app = kit.text("♛ مدیریت میلانو", 16, Theme.TEXT, true);
        titleBox.addView(app, kit.lp(-1, -2));
        rangeLine = kit.text(kit.todayLine(), 10.5f, Theme.MUTED, false);
        titleBox.addView(rangeLine, kit.lp(-1, -2));
        header.addView(titleBox, kit.wlp(1f));
        filterBtn = kit.text("◈ فیلتر", 12, Theme.GOLD_SOFT, true);
        filterBtn.setPadding(Theme.dp(12), Theme.dp(8), Theme.dp(12), Theme.dp(8));
        filterBtn.setBackground(Theme.ghostButton(Theme.GOLD));
        Theme.pressable(filterBtn);
        filterBtn.setOnClickListener(v -> openFilter());
        header.addView(filterBtn, kit.lp(-2, -2));
        header.addView(kit.space(8));
        TextView gsearch = kit.text("⌕", 19, Theme.GOLD_SOFT, true);
        gsearch.setPadding(Theme.dp(10), Theme.dp(4), Theme.dp(10), Theme.dp(4));
        gsearch.setBackground(Theme.ghostButton(Theme.GOLD));
        Theme.pressable(gsearch);
        gsearch.setOnClickListener(v -> nav("search"));
        header.addView(gsearch, kit.lp(-2, -2));
        header.addView(kit.space(8));
        TextView refresh = kit.text("⟳", 19, Theme.GOLD_SOFT, true);
        refresh.setPadding(Theme.dp(10), Theme.dp(4), Theme.dp(10), Theme.dp(4));
        refresh.setBackground(Theme.ghostButton(Theme.GOLD));
        Theme.pressable(refresh);
        refresh.setOnClickListener(v -> renderCurrent());
        header.addView(refresh, kit.lp(-2, -2));
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
        reg(new ReportsScreen(this));
        reg(new SettingsScreen(this));
        reg(new MoreScreen(this));
        reg(new SearchScreen(this));

        buildBottom();
        nav(screens.containsKey(currentId) ? currentId : "home");
        styleBars();
        checkConn();
        Notify.boot(this);
    }

    private void reg(Screen s) {
        screens.put(s.id(), s);
    }

    public Screen screen(String id) {
        return screens.get(id);
    }

    // ================= navigation =================
    private static final String[] TABS = {"home", "sales", "cheques", "customers", "more"};

    private void buildBottom() {
        bottomBar.removeAllViews();
        for (final String id : TABS) {
            final Screen s = screens.get(id);
            LinearLayout b = kit.v();
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, Theme.dp(4), 0, Theme.dp(2));
            TextView g = kit.text(s.glyph(), 21, Theme.MUTED, true);
            g.setGravity(Gravity.CENTER);
            b.addView(g, kit.lp(-1, -2));
            TextView l = kit.text(s.id().equals("sales") ? "فروش" : s.title(), 10f, Theme.MUTED, true);
            l.setGravity(Gravity.CENTER);
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
            int col = on ? Theme.GOLD : Theme.MUTED;
            ((TextView) b.getChildAt(0)).setTextColor(on ? Theme.GOLD_SOFT : Theme.MUTED);
            ((TextView) b.getChildAt(1)).setTextColor(col);
            b.setBackground(on ? Theme.pill(Theme.GOLD) : null);
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
    }

    private void renderCurrent() {
        Screen s = screens.get(currentId);
        if (s == null) return;
        refreshChrome();
        scroll.scrollTo(0, 0);
        s.render(content);
    }

    /** Repaint bottom bar + filter button + title line (screens call this after internal state changes). */
    public void refreshChrome() {
        Screen s = screens.get(currentId);
        if (s == null) return;
        paintBottom();
        filterBtn.setVisibility(s.filterConfig() == null ? View.GONE : View.VISIBLE);
        Filter f = s.filter();
        if (f != null && !f.isDefault()) {
            filterBtn.setText("◈ فیلتر •");
            filterBtn.setTextColor(Theme.GOLD);
        } else {
            filterBtn.setText("◈ فیلتر");
            filterBtn.setTextColor(Theme.GOLD_SOFT);
        }
        MeelanoIcons.iconize(filterBtn);
        rangeLine.setText(kit.todayLine() + "  •  " + s.title());
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Notification permission is best-effort; the app works fully without it.
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
