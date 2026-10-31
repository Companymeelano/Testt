package ir.meelano.manager.screens;

import ir.meelano.manager.core.Diag;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import ir.meelano.manager.R;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.ShopCard;
import ir.meelano.manager.core.Backup;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.data.NetRoute;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.ui.DesignGallery;
import ir.meelano.manager.ui.Theme;

/**
 * Settings, grouped into eleven sections. Only one section is on screen at a time, so
 * the page no longer stacks every option into one long scroll. Each switch is a row
 * with a title, a one-line explanation and a coloured on/off badge.
 */
public class SettingsScreen extends Screen {

    /** Section keys, Persian labels and glyphs, in display order. */
    private static final String[][] SECTIONS = {
            {"connection", "اتصال", "🔌"},
            {"theme", "ظاهر", "🎨"},
            {"preview", "گالری طراحی", "◐"},
            {"ai", "هوش مصنوعی", "✦"},
            {"alerts", "هشدارها", "🔔"},
            {"shop", "فروشگاه", "🏪"},
            {"backup", "پشتیبان", "💾"},
            {"security", "امنیت", "🔐"},
            {"roles", "انبار و نقش‌ها", "👥"},
            {"dashboard", "داشبورد", "🧭"},
            {"about", "درباره", "ℹ"},
    };

    /** Currently open section (the screen instance is kept for the app's lifetime). */
    private String section = "connection";
    /** Set by other screens to open a section directly (read once on the next render). */
    public static String pendingSection = null;

    public SettingsScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "settings"; }

    @Override
    public String title() { return "تنظیمات"; }

    @Override
    public String glyph() { return "⚙"; }

    @Override
    public int accent() { return Theme.GOLD; }

    @Override
    public void render(final LinearLayout content) {
        if (warehouseRole()) section = "alerts";
        content.removeAllViews();
        if (pendingSection != null) {
            section = pendingSection;
            pendingSection = null;
        }
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(sectionNav(content), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        switch (section) {
            case "theme": buildTheme(content); break;
            case "preview": buildPreview(content); break;
            case "ai": AiSettings.build(a, content, () -> render(content)); break;
            case "alerts": buildAlerts(content); break;
            case "shop": buildShop(content); break;
            case "backup": buildBackup(content); break;
            case "security": buildSecurity(content); break;
            case "roles": buildRoles(content); break;
            case "dashboard": buildDashboard(content); break;
            case "about": buildAbout(content); break;
            default: buildConnection(content); break;
        }
    }

    // ================= navigation =================

    /** Horizontal strip of section pills; the open one is filled with the accent colour. */
    private View sectionNav(final LinearLayout content) {
        HorizontalScrollView sv = new HorizontalScrollView(a);
        sv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(2), Theme.dp(2), Theme.dp(2), Theme.dp(2));
        for (String[] s : visibleSections()) {
            final String key = s[0];
            final boolean on = key.equals(section);
            TextView pill = a.kit.text(s[2] + "  " + s[1], 12f, on ? Theme.onAccent() : Theme.TEXT, on);
            pill.setSingleLine(true);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(Theme.dp(14), Theme.dp(9), Theme.dp(14), Theme.dp(9));
            pill.setBackground(on ? Theme.accentAvatar(Theme.GOLD) : Theme.ghostButton(Theme.MUTED));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, Theme.dp(8), 0);
            Theme.pressable(pill);
            pill.setOnClickListener(v -> {
                section = key;
                render(content);
            });
            row.addView(pill, lp);
        }
        sv.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        return sv;
    }

    private static String roleNow(MainActivity a) {
        String role = ir.meelano.manager.core.AtiranAuth.sessionRole(a);
        if (role == null || role.isEmpty()) role = ir.meelano.manager.core.RoleStore.current(a);
        return role == null ? "" : role;
    }

    private boolean warehouseRole() {
        return ir.meelano.manager.core.RoleStore.WAREHOUSE.equals(roleNow(a));
    }

    /** The warehouse keeper only sees the alerts section; everything else stays with the manager. */
    private String[][] visibleSections() {
        if (!warehouseRole()) return SECTIONS;
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String[] s : SECTIONS) if ("alerts".equals(s[0])) out.add(s);
        return out.toArray(new String[0][]);
    }

    /** Shared section header: one duotone icon, title and one line of guidance. */
    private LinearLayout sectionHead(String title, String subtitle, int accent) {
        int split = title == null ? -1 : title.indexOf(' ');
        String glyph = split > 0 ? title.substring(0, split) : "";
        String words = split > 0 ? title.substring(split + 1) : (title == null ? "" : title);
        int icon = ir.meelano.manager.ui.MeelanoIcons.iconFor(glyph);
        int sectionHue = a.kit.visualAccent(words, accent);
        LinearLayout card = a.kit.luxCard(sectionHue);
        card.addView(a.kit.sectionHeader(words, icon, sectionHue, null, null), a.kit.lp(-1, -2));
        if (subtitle != null && !subtitle.isEmpty())
            card.addView(a.kit.hint(subtitle), a.kit.lp(-1, -2));
        return card;
    }

    // ================= reusable rows =================

    /**
     * One switch as a row: title + one-line explanation on the right, coloured
     * «روشن/خاموش» badge on the left. Tapping anywhere on the row toggles it.
     */
    private View toggleRow(String title, String desc, boolean on, int accent, View.OnClickListener onTap) {
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.ghostButton(on ? accent : Theme.MUTED));
        row.setPadding(Theme.dp(12), Theme.dp(10), Theme.dp(12), Theme.dp(10));
        LinearLayout col = a.kit.v();
        col.addView(a.kit.text(title, 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        if (desc != null && !desc.isEmpty()) {
            TextView d = a.kit.text(desc, 10.5f, Theme.MUTED, false);
            col.addView(d, a.kit.lp(-1, -2));
        }
        row.addView(col, a.kit.wlp(1f));
        TextView badge = a.kit.text(on ? "روشن" : "خاموش", 11f,
                on ? Theme.onAccent() : Theme.MUTED, true);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setPadding(Theme.dp(10), Theme.dp(5), Theme.dp(10), Theme.dp(5));
        badge.setBackground(on ? Theme.accentAvatar(accent) : Theme.ghostButton(Theme.MUTED));
        row.addView(a.kit.space(8));
        row.addView(badge, a.kit.lp(-2, -2));
        Theme.pressable(row);
        row.setOnClickListener(onTap);
        return row;
    }

    /** Adds a row with a small gap beneath it. */
    private void addRow(LinearLayout parent, View row) {
        LinearLayout.LayoutParams lp = a.kit.lp(-1, -2);
        lp.setMargins(0, 0, 0, Theme.dp(8));
        parent.addView(row, lp);
    }

    // ================= sections =================

    private void buildConnection(final LinearLayout content) {
        content.addView(sectionHead("🔌 اتصال به آتیران",
                "وضعیت شبکه، آدرس سرور و حالت اتصال. آدرس‌ها را فروشنده تنظیم می‌کند؛ همین‌جا فقط نمایش و تست می‌شود.",
                Theme.GOLD), a.kit.lp(-1, -2));
        LinearLayout c = a.kit.card(Theme.GOLD);
        final boolean cfg = a.settings.connConfigured();
        c.addView(a.kit.kv("وضعیت", cfg ? "✓ تنظیم‌شده توسط فروشنده" : "✕ هنوز تنظیم نشده",
                cfg ? Theme.SUCCESS : Theme.DANGER), a.kit.lp(-1, -2));
        if (cfg) c.addView(a.kit.kv("سرور", a.settings.maskedHost(), Theme.MUTED), a.kit.lp(-1, -2));
        String lanM = a.settings.maskedLan();
        String wanM = a.settings.maskedWan();
        c.addView(a.kit.kv("داخل شبکه", lanM.isEmpty() ? "✕ ثبت نشده" : "✓ " + lanM,
                lanM.isEmpty() ? Theme.DANGER : Theme.SUCCESS), a.kit.lp(-1, -2));
        c.addView(a.kit.kv("خارج شبکه", wanM.isEmpty() ? "✕ ثبت نشده" : "✓ " + wanM,
                wanM.isEmpty() ? Theme.DANGER : Theme.SUCCESS), a.kit.lp(-1, -2));
        boolean vpn = NetRoute.isVpnActive(a);
        c.addView(a.kit.kv("فیلترشکن", vpn ? "فعال" : "غیرفعال", vpn ? Theme.WARNING : Theme.SUCCESS),
                a.kit.lp(-1, -2));
        c.addView(a.kit.gap(6));

        c.addView(a.kit.text("حالت مسیر اتصال", 12f, Theme.MUTED, true), a.kit.lp(-1, -2));
        String lm = a.settings.linkMode();
        c.addView(a.kit.chips(new String[]{"خودکار ✦", "فقط داخل شبکه", "فقط خارج شبکه"},
                "lan".equals(lm) ? 1 : ("wan".equals(lm) ? 2 : 0), idx -> {
                    a.settings.setLinkMode(idx == 1 ? "lan" : (idx == 2 ? "wan" : "auto"));
                    a.kit.toast(idx == 0 ? "حالت خودکار فعال شد" : "حالت دستی فعال شد");
                    try {
                        a.checkConn();
                    } catch (Exception ignored) { Diag.ignored("SettingsScreen:188", ignored); }
                    render(content);
                }), a.kit.lp(-1, -2));
        c.addView(a.kit.gap(6));

        c.addView(a.kit.btn("تست اتصال", v -> {
            a.kit.toast("در حال تست اتصال…");
            a.testConnection(new Repo.Cb<String>() {
                @Override
                public void ok(String v2) {
                    a.kit.toast(v2);
                }

                @Override
                public void fail(String faError) {
                    a.kit.toast(faError);
                }
            });
        }), a.kit.lp(-1, -2));
        c.addView(a.kit.btnGhost(cfg ? "✦ تغییر IP / دیتابیس" : "✦ تنظیم IP / دیتابیس",
                Theme.GOLD, v -> a.openConnectionSetup()), a.kit.lp(-1, -2));
        c.addView(a.kit.btnGhost("🛠 عیب‌یابی هوشمند اتصال", Theme.GOLD, v -> {
            try {
                ir.meelano.manager.ui.LinkPanel.showDiagnose(a, a.settings);
            } catch (Exception e) {
                a.kit.toast("ممکن نشد");
            }
        }), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);

        LinearLayout adv = a.kit.card(Theme.INFO);
        adv.addView(a.kit.text("گزینه‌های پیشرفته", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean direct = a.settings.directConn();
        addRow(adv, toggleRow("اتصال مستقیم", "اگر فیلترشکن مسیر سرور را می‌بندد، روشن کنید", direct, Theme.INFO, v -> {
            a.settings.setDirectConn(!direct);
            a.kit.toast(direct ? "اتصال مستقیم خاموش شد" : "اتصال مستقیم روشن شد");
            render(content);
        }));
        if (cfg) {
            adv.addView(a.kit.btnGhost("⨯ قطع اتصال و پاک‌کردن IP ذخیره‌شده", Theme.DANGER, v -> {
                new AlertDialog.Builder(a)
                        .setTitle("پاک‌کردن اتصال ذخیره‌شده؟")
                        .setMessage("IPهای سرور و دیتابیس انتخاب‌شده پاک می‌شوند. لایسنس، حساب محلی و اطلاعات ورود SQL حفظ خواهند شد.")
                        .setNegativeButton("انصراف", null)
                        .setPositiveButton("پاک‌کردن IP", (dialog, which) -> a.disconnectConnection())
                        .show();
            }), a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, adv);
    }

    private void buildTheme(final LinearLayout content) {
        content.addView(sectionHead("🎨 ظاهر لاکچری",
                "سه قالب طراحی‌شده و واحد نمایش. تغییرات فوراً روی همهٔ بخش‌ها اعمال می‌شود.",
                Theme.GOLD), a.kit.lp(-1, -2));
        LinearLayout th = a.kit.card(Theme.GOLD);
        th.addView(a.kit.text("قالب", 12f, Theme.MUTED, true), a.kit.lp(-1, -2));
        final boolean isLight = "light".equals(a.settings.themeMode());
        final boolean isNuts = "nuts".equals(a.settings.themeMode());
        final int themeIdx = isNuts ? 2 : isLight ? 1 : 0;
        th.addView(a.kit.segmentedControl(
                new String[]{"تیره • ابسیدین طلایی", "روشن • عاج سلطنتی", "آجیل و خشکبار"},
                themeIdx,
                new int[]{R.drawable.mi_dark_mode, R.drawable.mi_light_mode, R.drawable.mi_storefront},
                Theme.GOLD, idx -> {
                    a.settings.setThemeMode(idx == 2 ? "nuts" : idx == 1 ? "light" : "dark");
                    a.refreshTheme();
                }), a.kit.lp(-1, -2));
        th.addView(a.kit.gap(8));
        th.addView(a.kit.text("نمایش", 12f, Theme.MUTED, true), a.kit.lp(-1, -2));
        th.addView(a.kit.btnGhost("🔍 بزرگ‌نمایی متن: " + MainActivity.zoomLabel(a.settings.zoomIdx()), Theme.GOLD, v -> {
            a.cycleZoom();
            render(content);
        }), a.kit.lp(-1, -2));
        final boolean toman = Money.isToman();
        th.addView(a.kit.btnGhost("💰 واحد نمایش مبالغ: " + (toman ? "تومان" : "ریال"), Theme.SUCCESS, v -> {
            int next = toman ? Money.UNIT_RIAL : Money.UNIT_TOMAN;
            a.settings.setMoneyUnit(next);
            Money.setUnit(next);
            a.kit.toast(next == Money.UNIT_TOMAN ? "مبالغ به تومان نمایش داده می‌شود" : "مبالغ به ریال نمایش داده می‌شود");
            a.refreshTheme();
        }), a.kit.lp(-1, -2));
        th.addView(a.kit.gap(6));
        th.addView(a.kit.hint("قالب انتخاب‌شده فوراً روی همهٔ بخش‌ها، جدول‌ها، نمودارها و آیکن‌ها اعمال می‌شود."),
                a.kit.lp(-1, -2));
        a.kit.addCard(content, th);
    }

    private void buildPreview(final LinearLayout content) {
        content.addView(a.kit.sectionHeader("پیش‌نمایش Light و Dark",
                R.drawable.mi_contrast, Theme.GOLD, null, null), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("هدر جمع‌شونده، داک پنج‌مقصدی و اجزای مشترک را در هر دو پالت ببینید. "
                + "این نمونه‌ها تنظیم فعلی دستگاه را تغییر نمی‌دهند."), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        DesignGallery.addPreviews(a, content);
    }

    private void buildAlerts(final LinearLayout content) {
        content.addView(sectionHead("🔔 هشدارها و گزارش‌ها",
                "اعلان‌های خودکار. هر گزینه زمان بررسی خودش را نشان می‌دهد.",
                Theme.INFO), a.kit.lp(-1, -2));
        LinearLayout nt = a.kit.card(Theme.INFO);

        final boolean non = a.settings.notifOn();
        addRow(nt, toggleRow("هشدار هوشمند سررسید", "هر روز ساعت ۸: سررسیدها، معوق‌ها، چک‌های برگشتی", non, Theme.INFO, v -> {
            a.settings.setNotifOn(!non);
            if (!non) {
                ir.meelano.manager.core.Notify.boot(a);
                a.kit.toast("هشدار هوشمند روشن شد؛ هر روز ساعت ۸ بررسی می‌شود");
            } else {
                ir.meelano.manager.core.Notify.cancelDaily(a);
                a.kit.toast("هشدار هوشمند خاموش شد");
            }
            render(content);
        }));
        final boolean mon = a.settings.morningOn();
        addRow(nt, toggleRow("گزارش صبحگاهی", "خلاصهٔ فروش و دریافت دیروز به همراه هشدار ۸ صبح", mon, Theme.GOLD, v -> {
            a.settings.setMorningOn(!mon);
            a.kit.toast(!mon ? "گزارش صبحگاهی روشن شد" : "گزارش صبحگاهی خاموش شد");
            render(content);
        }));
        final boolean bkp = a.settings.backupOn();
        addRow(nt, toggleRow("یادآوری پشتیبان‌گیری هفتگی", "جمعه‌ها ساعت ۹", bkp, Theme.GOLD, v -> {
            a.settings.setBackupOn(!bkp);
            if (!bkp) ir.meelano.manager.core.Notify.scheduleWeekly(a);
            else ir.meelano.manager.core.Notify.cancelWeekly(a);
            a.kit.toast(!bkp ? "یادآوری هفتگی روشن شد (جمعه‌ها ساعت ۹)" : "یادآوری هفتگی خاموش شد");
            render(content);
        }));
        final boolean wk = a.settings.weeklyOn();
        addRow(nt, toggleRow("گزارش هفتگی", "پنجشنبه‌ها ساعت ۲۰", wk, Theme.VIOLET, v -> {
            a.settings.setWeeklyOn(!wk);
            if (!wk) ir.meelano.manager.core.Notify.scheduleWeeklyReport(a);
            else ir.meelano.manager.core.Notify.cancelWeeklyReport(a);
            a.kit.toast(!wk ? "گزارش هفتگی روشن شد" : "گزارش هفتگی خاموش شد");
            render(content);
        }));
        final boolean stk = a.settings.stockOn();
        addRow(nt, toggleRow("هشدار کالای کم‌موجود", "وقتی موجودی از نقطهٔ سفارش پایین‌تر رفت", stk, Theme.WARNING, v -> {
            a.settings.setStockOn(!stk);
            a.kit.toast(!stk ? "هشدار کم‌موجودی روشن شد" : "هشدار کم‌موجودی خاموش شد");
            render(content);
        }));
        final boolean eod = a.settings.eodOn();
        addRow(nt, toggleRow("جمع‌بندی پایان روز", "هر شب ساعت ۲۱", eod, Theme.VIOLET, v -> {
            a.settings.setEodOn(!eod);
            if (!eod) ir.meelano.manager.core.Notify.scheduleEod(a);
            else ir.meelano.manager.core.Notify.cancelEod(a);
            a.kit.toast(!eod ? "جمع‌بندی پایان روز روشن شد" : "جمع‌بندی پایان روز خاموش شد");
            render(content);
        }));
        final boolean grd = a.settings.guardOn();
        addRow(nt, toggleRow("نگهبان فروش لحظه‌ای", "هر ۳۰ دقیقه بررسی می‌شود", grd, Theme.DANGER, v -> {
            a.settings.setGuardOn(!grd);
            if (!grd) ir.meelano.manager.core.Notify.scheduleGuard(a);
            else ir.meelano.manager.core.Notify.cancelGuard(a);
            a.kit.toast(!grd ? "نگهبان فروش روشن شد؛ هر ۳۰ دقیقه بررسی می‌شود" : "نگهبان فروش خاموش شد");
            render(content);
        }));
        final boolean whn = a.settings.whNewOn();
        addRow(nt, toggleRow("اعلان فاکتور فروش جدید", "انبار: هر ۱۵ دقیقه بررسی می‌شود", whn, Theme.SUCCESS, v -> {
            a.settings.setWhNewOn(!whn);
            if (!whn) ir.meelano.manager.core.Notify.scheduleNewInv(a);
            else ir.meelano.manager.core.Notify.cancelNewInv(a);
            a.kit.toast(!whn ? "اعلان فاکتور جدید روشن شد؛ هر ۱۵ دقیقه بررسی می‌شود" : "اعلان فاکتور جدید خاموش شد");
            render(content);
        }));
        if (warehouseRole()) {
            addRow(nt, toggleRow("🔔 نمایش نمونهٔ اعلان بار", "یک اعلان نمونه با صدای آلارم نشان داده می‌شود", true,
                    Theme.GOLD, v -> {
                        ir.meelano.manager.core.InvoiceWatch.preview(a);
                        a.kit.toast("نمونهٔ اعلان ارسال شد");
                    }));
        }
        a.kit.addCard(content, nt);
    }

    private void buildShop(final LinearLayout content) {
        content.addView(sectionHead("🏪 فروشگاه و نمایش",
                "مشخصات شرکت از آتیران خوانده می‌شود. کارت ویزیت و حالت تلویزیون را از همین‌جا باز کنید.",
                Theme.SUCCESS), a.kit.lp(-1, -2));
        LinearLayout co = a.kit.card(Theme.GOLD);
        companyInfo(co, content);
        a.kit.addCard(content, co);

        LinearLayout sh = a.kit.card(Theme.SUCCESS);
        sh.addView(a.kit.btnGhost("🪪 ساخت کارت ویزیت دیجیتال", Theme.VIOLET, v -> ShopCard.show(a)), a.kit.lp(-1, -2));
        sh.addView(a.kit.btnGhost("📺 حالت تلویزیون فروشگاه", Theme.SUCCESS, v -> a.startTv()), a.kit.lp(-1, -2));
        a.kit.addCard(content, sh);
    }

    private void buildBackup(final LinearLayout content) {
        content.addView(sectionHead("💾 پشتیبان‌گیری هوشمند",
                "بکاپ کامل جداول آتیران در یک فایل ZIP داخل Downloads ذخیره می‌شود.",
                Theme.GOLD), a.kit.lp(-1, -2));
        LinearLayout bk = a.kit.card(Theme.GOLD);
        String bl = a.settings.backupLast();
        if (!bl.isEmpty())
            bk.addView(a.kit.kv("آخرین بکاپ", bl + (a.settings.backupSize().isEmpty() ? "" : " • " + a.settings.backupSize()), Theme.TEXT),
                    a.kit.lp(-1, -2));
        bk.addView(a.kit.btn("⛁ بکاپ هوشمند روی گوشی", v -> runBackup(content)), a.kit.lp(-1, -2));
        bk.addView(a.kit.hint("۷۸ جدول آتیران (همه جداول به‌جز تصاویر حجیم کالا و مشتری) به‌صورت CSV داخل یک فایل ZIP ذخیره و برای اشتراک آماده می‌شود."),
                a.kit.lp(-1, -2));
        a.kit.addCard(content, bk);

        LinearLayout auto = a.kit.card(Theme.GOLD);
        final boolean abo = a.settings.abOn();
        addRow(auto, toggleRow("بکاپ خودکار شبانه", "هر شب سر ساعت، بی‌نیاز از اقدام دستی", abo, Theme.GOLD, v -> {
            a.settings.setAbOn(!abo);
            if (!abo) ir.meelano.manager.core.AutoBackup.schedule(a);
            else ir.meelano.manager.core.AutoBackup.cancel(a);
            a.kit.toast(!abo ? "بکاپ خودکار روشن شد (هر شب ساعت " + Money.fa(String.valueOf(a.settings.abHour())) + ")"
                    : "بکاپ خودکار خاموش شد");
            render(content);
        }));
        if (abo) {
            LinearLayout hrow = a.kit.h();
            hrow.setGravity(Gravity.CENTER_VERTICAL);
            hrow.addView(a.kit.btnGhost("−", Theme.GOLD, v -> {
                a.settings.setAbHour(a.settings.abHour() - 1);
                ir.meelano.manager.core.AutoBackup.schedule(a);
                render(content);
            }), a.kit.lp(Theme.dp(56), -2));
            TextView ht = a.kit.text("ساعت " + Money.fa(String.valueOf(a.settings.abHour())), 15f, Theme.TEXT, true);
            ht.setGravity(Gravity.CENTER);
            hrow.addView(ht, a.kit.wlp(1f));
            hrow.addView(a.kit.btnGhost("＋", Theme.GOLD, v -> {
                a.settings.setAbHour(a.settings.abHour() + 1);
                ir.meelano.manager.core.AutoBackup.schedule(a);
                render(content);
            }), a.kit.lp(Theme.dp(56), -2));
            auto.addView(hrow, a.kit.lp(-1, -2));
            auto.addView(a.kit.hint("ساعت اجرا را با + و − تنظیم کنید. بکاپ با وای‌فای یا اینترنت گوشی انجام می‌شود."),
                    a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, auto);

        LinearLayout off = a.kit.card(Theme.GOLD);
        final boolean offm = a.settings.offlineMode();
        addRow(off, toggleRow("حالت آفلاین (حافظه گوشی)", "گزارش‌ها از آخرین بکاپ شبانه روی گوشی خوانده می‌شوند", offm, Theme.GOLD, v -> {
            a.settings.setOfflineMode(!offm);
            a.kit.toast(!offm ? "حالت آفلاین روشن شد" : "حالت آفلاین خاموش شد");
            render(content);
        }));
        a.kit.addCard(content, off);
    }

    private void buildSecurity(final LinearLayout content) {
        content.addView(sectionHead("🔐 امنیت و ورود",
                "قفل محلی برنامه و ورود نقش‌محور با حساب واقعی آتیران.",
                Theme.INFO), a.kit.lp(-1, -2));

        LinearLayout p = a.kit.card(Theme.INFO);
        p.addView(a.kit.kv("قفل ۴ رقمی", a.settings.pinEnabled() ? "فعال" : "غیرفعال",
                a.settings.pinEnabled() ? Theme.SUCCESS : Theme.MUTED), a.kit.lp(-1, -2));
        LinearLayout prow = a.kit.h();
        prow.addView(a.kit.btn(a.settings.pinEnabled() ? "تغییر رمز" : "فعال‌سازی", v -> pinDialog()), a.kit.wlp(1f));
        if (a.settings.pinEnabled()) {
            prow.addView(a.kit.space(8));
            prow.addView(a.kit.btnGhost("غیرفعال", Theme.DANGER, v -> {
                a.settings.setPin(null);
                a.kit.toast("قفل غیرفعال شد");
                render(content);
            }), a.kit.wlp(1f));
        }
        p.addView(prow, a.kit.lp(-1, -2));
        if (ir.meelano.manager.core.Finger.supported(a)) {
            final boolean fp = a.settings.fpOn();
            p.addView(a.kit.gap(6));
            addRow(p, toggleRow("ورود با اثر انگشت", "فقط وقتی قفل ۴ رقمی فعال باشد", fp, Theme.GOLD, v -> {
                a.settings.setFpOn(!fp);
                a.kit.toast(!fp ? "ورود با اثر انگشت روشن شد" : "ورود با اثر انگشت خاموش شد");
                render(content);
            }));
        }
        a.kit.addCard(content, p);

        LinearLayout lg = a.kit.card(Theme.GOLD);
        lg.addView(a.kit.text("حساب ورود", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        lg.addView(a.kit.kv("وضعیت", "ورود با حساب آتیران الزامی است", Theme.SUCCESS), a.kit.lp(-1, -2));
        if (ir.meelano.manager.core.AtiranAuth.hasSession(a)) {
            lg.addView(a.kit.kv("کاربر فعلی",
                    ir.meelano.manager.core.AtiranAuth.sessionName(a) + " («"
                            + ir.meelano.manager.core.RoleStore.faName(
                                    ir.meelano.manager.core.AtiranAuth.sessionRole(a))
                            + "»)", Theme.TEXT), a.kit.lp(-1, -2));
            lg.addView(a.kit.btnGhost("خروج از حساب فعلی", Theme.DANGER, v -> a.logout()), a.kit.lp(-1, -2));
        }
        lg.addView(a.kit.hint("پس از فعال‌سازی لایسنس و انتخاب دیتابیس، همهٔ نقش‌ها از جمله مدیریت و انبارداری باید با حساب فعال خود در آتیران وارد شوند."),
                a.kit.lp(-1, -2));
        a.kit.addCard(content, lg);
    }

    private void buildRoles(final LinearLayout content) {
        content.addView(sectionHead("👥 انبار و نقش‌های کاربری",
                "نقش‌ها، رمز هر نقش و ثبت مستقیم انبار در آتیران.",
                Theme.VIOLET), a.kit.lp(-1, -2));

        LinearLayout r = a.kit.card(Theme.VIOLET);
        final boolean ren = ir.meelano.manager.core.RoleStore.enabled(a);
        r.addView(a.kit.kv("نقش فعلی", "«" + ir.meelano.manager.core.RoleStore.faName(
                ir.meelano.manager.core.RoleStore.current(a)) + "»", Theme.TEXT), a.kit.lp(-1, -2));
        addRow(r, toggleRow("نقش‌های کاربری", "روشن: ورود بعدی با انتخاب نقش است", ren, Theme.VIOLET, v -> {
            ir.meelano.manager.core.RoleStore.setEnabled(a, !ren);
            a.kit.toast(!ren ? "نقش‌ها روشن شد؛ ورود بعدی با انتخاب نقش است" : "نقش‌ها خاموش شد");
            render(content);
        }));
        if (ren) {
            String[][] rr = {{"admin", "مدیر مجموعه"}, {"sales_manager", "مدیر فروش"},
                    {"seller", "فروشنده"}, {"accountant", "حسابدار"}, {"visitor", "ویزیتور"},
                    {"distributor", "موزع"}, {"warehouse", "انباردار"}, {"moadian", "مودیان"}};
            for (String[] role : rr) {
                final String rk = role[0];
                final String rn = role[1];
                boolean locked = ir.meelano.manager.core.RoleStore.pinSet(a, rk);
                LinearLayout rrow = a.kit.h();
                rrow.setGravity(Gravity.CENTER_VERTICAL);
                rrow.setPadding(0, Theme.dp(4), 0, Theme.dp(4));
                rrow.addView(a.kit.text(rn + " • " + (locked ? "رمزدار \uD83D\uDD12" : "بدون رمز"),
                        13f, Theme.TEXT, true), a.kit.wlp(1f));
                rrow.addView(a.kit.btnGhost("تعیین رمز", Theme.GOLD,
                        v -> rolePinDialog(content, rk, rn)), a.kit.lp(-2, -2));
                r.addView(rrow, a.kit.lp(-1, -2));
            }
            r.addView(a.kit.btnGhost("تعویض نقش", Theme.TEXT, v -> a.openRoleGate()), a.kit.lp(-1, -2));
            r.addView(a.kit.hint("بخش‌های حساس (تنظیمات، کاربران و سود) فقط برای مدیر است. رمزهای نقش PIN داخلیِ جداگانه‌اند."),
                    a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, r);

        LinearLayout wh = a.kit.card(Theme.SUCCESS);
        wh.addView(a.kit.text("انبار", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean wdir = a.settings.whDirect();
        addRow(wh, toggleRow("ثبت مستقیم در آتیران", wdir ? "اسناد مستقیم در پیش‌فاکتورهای آتیران ثبت می‌شوند"
                : "خاموش: اسناد فقط پیش‌نویس محلی می‌مانند (امن)", wdir, Theme.SUCCESS, v -> {
            a.settings.setWhDirect(!wdir);
            a.kit.toast(!wdir ? "ثبت مستقیم روشن شد" : "ثبت مستقیم خاموش شد؛ فقط پیش‌نویس");
            render(content);
        }));
        wh.addView(a.kit.hint("پیش از روشن‌کردن، با «پیش‌نمایش SQL» در صفحه رسید کالا بررسی کنید."),
                a.kit.lp(-1, -2));
        a.kit.addCard(content, wh);
    }

    private void buildDashboard(final LinearLayout content) {
        content.addView(sectionHead("🧭 ویرایش خانه",
                "خوش‌آمد، Hero فروش و KPI در ابتدای صفحه ثابت‌اند؛ بخش‌های پایین را بکشید، مرتب کنید یا پنهان کنید.",
                Theme.GOLD), a.kit.lp(-1, -2));
        LinearLayout editor = a.kit.card(Theme.GOLD);
        final boolean simple = a.settings.homeSimpleMode();
        addRow(editor, toggleRow(simple ? "حالت ساده روشن" : "حالت کامل روشن",
                simple ? "نمای ساده تا سه بخش انتخابیِ پایین را نشان می‌دهد؛ سایر کارت‌ها در حالت کامل دیده می‌شوند."
                        : "نمای کامل همهٔ بخش‌های انتخاب‌شده را نشان می‌دهد.",
                simple, Theme.GOLD, v -> {
                    a.settings.setHomeSimpleMode(!simple);
                    render(content);
                }));
        editor.addView(a.kit.hint("تغییرات خودکار ذخیره می‌شوند. برای جابه‌جایی، دستگیره را لمس طولانی کنید و بکشید؛ فلش‌ها جایگزین دسترس‌پذیر هستند."),
                a.kit.lp(-1, -2));

        final java.util.List<String> order = new java.util.ArrayList<>();
        for (String key : a.settings.homeOrder().split(",")) {
            String clean = key == null ? "" : key.trim();
            if (!clean.isEmpty() && !order.contains(clean)) order.add(clean);
        }
        for (String key : ir.meelano.manager.data.Settings.HOME_ORDER_DEFAULT.split(","))
            if (!order.contains(key)) order.add(key);
        final java.util.List<String> selected = new java.util.ArrayList<>();
        for (String key : a.settings.homeSections().split(",")) {
            String clean = key == null ? "" : key.trim();
            if (!clean.isEmpty() && !selected.contains(clean)) selected.add(clean);
        }

        for (int i = 0; i < order.size(); i++) {
            final int index = i;
            final String key = order.get(i);
            final String label = homeSectionLabel(key);
            final boolean enabled = selected.contains(key);
            int selectedBefore = 0;
            for (int j = 0; j < i; j++) if (selected.contains(order.get(j))) selectedBefore++;
            boolean beyondSimpleLimit = simple && enabled && selectedBefore >= 3;
            LinearLayout row = a.kit.h();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setPadding(Theme.dp(4), Theme.dp(3), Theme.dp(4), Theme.dp(3));
            LinearLayout copy = a.kit.v();
            copy.addView(a.kit.text(Money.fa(String.valueOf(i + 1)) + " · " + label,
                    11.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            TextView state = a.kit.text(!enabled ? "پنهان"
                            : beyondSimpleLimit ? "فعال · در حالت کامل نمایش داده می‌شود" : "نمایش داده می‌شود",
                    9f, Theme.MUTED, false);
            copy.addView(state, a.kit.lp(-1, -2));
            row.addView(copy, a.kit.wlp(1f));

            View handle = a.kit.iconBadge(R.drawable.mi_swap_vert, Theme.GOLD, 28);
            handle.setMinimumWidth(Theme.dp(48));
            handle.setMinimumHeight(Theme.dp(48));
            handle.setFocusable(true);
            handle.setContentDescription("دستگیرهٔ جابه‌جایی «" + label + "»؛ لمس طولانی و کشیدن");
            Theme.pressable(handle);
            handle.setOnLongClickListener(v -> startHomeDrag(row, key));
            row.addView(handle, a.kit.lp(Theme.dp(48), Theme.dp(48)));

            android.widget.Button up = a.kit.btnGhost("↑", Theme.GOLD,
                    v -> moveHome(order, index, -1, content));
            up.setContentDescription("جابجایی «" + label + "» به جایگاه بالاتر");
            row.addView(up, a.kit.lp(Theme.dp(48), Theme.dp(48)));
            android.widget.Button down = a.kit.btnGhost("↓", Theme.GOLD,
                    v -> moveHome(order, index, 1, content));
            down.setContentDescription("جابجایی «" + label + "» به جایگاه پایین‌تر");
            row.addView(down, a.kit.lp(Theme.dp(48), Theme.dp(48)));

            android.widget.Button visibility = a.kit.btnGhost(enabled ? "✓" : "×",
                    enabled ? Theme.GOLD : Theme.MUTED, v -> {
                        if (selected.contains(key)) selected.remove(key);
                        else selected.add(key);
                        a.settings.setHomeSections(joinHomeKeys(selected));
                        if (simple && selected.size() > 3)
                            a.kit.toast("بخش ذخیره شد؛ سه بخش نخست پایین در نمای ساده دیده می‌شوند. برای نمایش همه، حالت کامل را روشن کنید.");
                        render(content);
                    });
            visibility.setContentDescription((enabled ? "پنهان‌کردن " : "نمایش ") + label);
            row.addView(visibility, a.kit.lp(Theme.dp(48), Theme.dp(48)));
            row.setOnDragListener((target, event) -> handleHomeDrop(event, key, order, content, target));
            addRow(editor, row);
        }

        editor.addView(a.kit.btnGhost("بازنشانی خانه", Theme.MUTED, v -> {
            a.settings.setHomeOrder(ir.meelano.manager.data.Settings.HOME_ORDER_DEFAULT);
            a.settings.setHomeSections(ir.meelano.manager.data.Settings.HOME_SECTIONS_DEFAULT);
            a.settings.setHomeSimpleMode(true);
            render(content);
        }), a.kit.lp(-1, -2));
        a.kit.addCard(content, editor);
    }

    private String homeSectionLabel(String key) {
        if ("smart_report".equals(key)) return "گزارش هوشمند امروز";
        if ("closeout".equals(key)) return "بستن روز";
        if ("cash_position".equals(key)) return "موقعیت نقدی";
        if ("top_performers".equals(key)) return "بهترین مشتری‌ها و کالاها";
        if ("sales_goal".equals(key)) return "هدف فروش ماه";
        if ("gross_profit".equals(key)) return "سود ناخالص";
        if ("seasonal_calendar".equals(key)) return "تقویم آجیل‌وفروشی";
        if ("quick_actions".equals(key)) return "اقدام سریع";
        if ("forecast_7d".equals(key)) return "پیش‌بینی فروش ۷روزه";
        if ("alerts".equals(key)) return "مرکز توجه";
        if ("cash_mix".equals(key)) return "ترکیب دریافت";
        if ("market".equals(key)) return "بازار (جمع‌شده پیش‌فرض)";
        if ("due".equals(key)) return "جزئیات سررسید چک‌ها";
        if ("returns".equals(key)) return "جزئیات برگشتی‌ها";
        if ("eod".equals(key)) return "جمع‌بندی پایان روز";
        return key;
    }

    private String joinHomeKeys(java.util.List<String> keys) {
        StringBuilder csv = new StringBuilder();
        for (String key : keys) {
            if (csv.length() > 0) csv.append(',');
            csv.append(key);
        }
        return csv.toString();
    }

    private boolean startHomeDrag(View row, String key) {
        row.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        android.content.ClipData data = android.content.ClipData.newPlainText("home-section", key);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(row);
        return row.startDragAndDrop(data, shadow, key, 0);
    }

    private boolean handleHomeDrop(android.view.DragEvent event, String targetKey,
                                  java.util.List<String> order, LinearLayout content, View target) {
        switch (event.getAction()) {
            case android.view.DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof String;
            case android.view.DragEvent.ACTION_DRAG_ENTERED:
                target.setAlpha(0.78f);
                return true;
            case android.view.DragEvent.ACTION_DRAG_EXITED:
            case android.view.DragEvent.ACTION_DRAG_ENDED:
                target.setAlpha(1f);
                return true;
            case android.view.DragEvent.ACTION_DROP:
                target.setAlpha(1f);
                Object state = event.getLocalState();
                if (!(state instanceof String)) return false;
                int from = order.indexOf((String) state);
                int targetIndex = order.indexOf(targetKey);
                if (from < 0 || targetIndex < 0 || from == targetIndex) return true;
                int insertAt = targetIndex + (event.getY() >= target.getHeight() / 2f ? 1 : 0);
                String moved = order.remove(from);
                if (from < insertAt) insertAt--;
                order.add(Math.max(0, Math.min(insertAt, order.size())), moved);
                a.settings.setHomeOrder(joinHomeKeys(order));
                render(content);
                return true;
            default:
                return true;
        }
    }

    private void buildAbout(final LinearLayout content) {
        content.addView(sectionHead("ℹ درباره و پشتیبانی", null, Theme.VIOLET), a.kit.lp(-1, -2));

        // Local-only crash breadcrumb; users copy it deliberately when support asks.
        if (ir.meelano.manager.core.CrashReporter.hasReport(a)) {
            LinearLayout crash = a.kit.card(Theme.DANGER);
            crash.addView(a.kit.text("گزارش خطای اخیر", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            crash.addView(a.kit.hint("گزارش فقط شامل نوع خطا، نسخه و محل کد است؛ پیام استثنا، IP، رمزها و اطلاعات فروش ذخیره نمی‌شوند. کپی به‌صورت دستی انجام می‌شود."),
                    a.kit.lp(-1, -2));
            crash.addView(a.kit.btnGhost("کپی گزارش فنی برای پشتیبانی", Theme.INFO, v -> {
                String report = ir.meelano.manager.core.CrashReporter.readReport(a);
                String diag = ir.meelano.manager.core.Diag.readRecent(a);
                if (!diag.isEmpty()) report = (report.isEmpty() ? "" : report + "\n\n") + "--- رویدادهای بازیابی‌شده ---\n" + diag;
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    if (cm == null || report.isEmpty()) {
                        a.kit.toast("گزارش فنی در دسترس نیست");
                        return;
                    }
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("گزارش خطای میلانو", report));
                    a.kit.toast("گزارش کپی شد؛ آن را برای پشتیبانی بفرستید");
                } catch (Exception e) {
                    a.kit.toast("کپی گزارش ممکن نشد");
                }
            }), a.kit.lp(-1, -2));
            crash.addView(a.kit.btnGhost("پاک‌کردن گزارش ذخیره‌شده", Theme.DANGER, v -> {
                ir.meelano.manager.core.CrashReporter.clearReport(a);
                a.kit.toast("گزارش پاک شد");
                render(content);
            }), a.kit.lp(-1, -2));
            a.kit.addCard(content, crash);
        }

        LinearLayout ab = a.kit.card(Theme.VIOLET);
        ab.setGravity(Gravity.CENTER);
        ab.addView(a.kit.logo(84), new LinearLayout.LayoutParams(Theme.dp(84), Theme.dp(84)));
        TextView dn = a.kit.text("Milad Yaghoobi", 23, Theme.GOLD, true);
        dn.setGravity(Gravity.CENTER);
        ab.addView(dn, a.kit.lp(-1, -2));
        TextView dr = a.kit.text("✦ طراح و توسعه‌دهنده ✦", 13f, Theme.TEXT, true);
        dr.setGravity(Gravity.CENTER);
        ab.addView(dr, a.kit.lp(-1, -2));
        TextView tag = a.kit.text("میلانو • داشبورد مدیریتی آتیران", 11f, Theme.MUTED, false);
        tag.setGravity(Gravity.CENTER);
        ab.addView(tag, a.kit.lp(-1, -2));
        ab.addView(a.kit.gap(4));
        ab.addView(a.kit.kv("نسخه", appVersion() + " • " + editionNow(), Theme.TEXT), a.kit.lp(-1, -2));
        addUpdateCard(ab);
        ab.addView(a.kit.kv("منبع داده", "SQL Server آتیران (اتصال مستقیم)", Theme.TEXT), a.kit.lp(-1, -2));
        ab.addView(a.kit.text("✦ پشتیبانی میلانو", 13f, Theme.GOLD_SOFT, true), a.kit.lp(-1, -2));
        ab.addView(a.kit.btnGhost("🌐  " + ir.meelano.manager.core.Brand.SITE_LABEL, Theme.GOLD, v ->
                ir.meelano.manager.core.Brand.openSite(a)), a.kit.lp(-1, -2));
        for (String ph : ir.meelano.manager.core.Brand.PHONES) {
            final String fph = ph;
            ab.addView(a.kit.btnGhost("📞  " + Money.fa(ph), Theme.GOLD, v ->
                    ir.meelano.manager.core.Brand.dial(a, fph)), a.kit.lp(-1, -2));
        }
        ab.addView(a.kit.hint("همه بخش‌ها داده زنده نمایش می‌دهند؛ بدون اتصال، اطلاع‌رسانی می‌شود."), a.kit.lp(-1, -2));
        a.kit.addCard(content, ab);
    }

    /** Themed update card: manual check, status, and a manual release-page fallback. */
    private void addUpdateCard(LinearLayout ab) {
        ab.addView(a.kit.gap(6), a.kit.lp(-1, -2));
        LinearLayout updateHead = a.kit.h();
        updateHead.setGravity(Gravity.CENTER_VERTICAL);
        View updateArt = a.kit.iconBadge(R.drawable.mi_cloud_download, Theme.GOLD_METAL, 34);
        updateHead.addView(updateArt, a.kit.lp(Theme.dp(34), Theme.dp(34)));
        updateHead.addView(a.kit.space(8));
        updateHead.addView(a.kit.text("بروزرسانی برنامه", 14f, Theme.GOLD, true), a.kit.wlp(1f));
        ab.addView(updateHead, a.kit.lp(-1, -2));
        if (!ir.meelano.manager.core.UpdateCenter.releaseUpdateSupported(a)) {
            ab.addView(a.kit.hint("این نسخهٔ آزمایشی است؛ نصب مستقیم درون‌برنامه‌ای فقط در نسخهٔ انتشارِ رسمی با امضای پایدار فعال می‌شود."), a.kit.lp(-1, -2));
            ab.addView(a.kit.btnGhost("📦  صفحه نسخه‌های منتشرشده", Theme.GOLD, v -> openReleasesPage()),
                    a.kit.lp(-1, -2));
            return;
        }
        ab.addView(a.kit.hint(ir.meelano.manager.core.UpdateCenter.statusLine(a)), a.kit.lp(-1, -2));
        ab.addView(a.kit.hint(ir.meelano.manager.core.UpdateCenter.lastCheckLine(a)), a.kit.lp(-1, -2));
        ab.addView(a.kit.btnGold("📥  بررسی و دریافت نسخه جدید", v ->
                ir.meelano.manager.core.UpdateCenter.manualCheck(a)), a.kit.lp(-1, -2));
        ab.addView(a.kit.btnGhost("📦  صفحه نسخه‌های منتشرشده", Theme.GOLD, v -> openReleasesPage()),
                a.kit.lp(-1, -2));
        ab.addView(a.kit.hint("دانلود امن، بررسی امضا و نصب روی همین گوشی انجام می‌شود؛ داده‌ها حفظ می‌شوند. "
                + "اگر اینترنت قطع بود، از صفحه نسخه‌ها دستی هم می‌توانید دریافت کنید."), a.kit.lp(-1, -2));
    }

    private void openReleasesPage() {
        try {
            a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(ir.meelano.manager.core.UpdateCenter.releasesPage())));
        } catch (Exception e) {
            Diag.ignored("SettingsScreen:openReleases", e);
            a.kit.toast("مرورگری برای باز کردن صفحه پیدا نشد");
        }
    }

    /** Full version history from the bundled changelog; the newest versions open by default. */
    private void buildChangelog(LinearLayout content) {
        ir.meelano.manager.ui.Changelog.Ver[] all = ir.meelano.manager.ui.Changelog.ALL;
        LinearLayout box = a.kit.card(Theme.GOLD);
        box.addView(a.kit.text("تاریخچهٔ نسخه‌ها", 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
        box.addView(a.kit.hint("هر نسخه را لمس کنید تا تغییرات آن باز شود. نسخهٔ فعلی: " + appVersion()),
                a.kit.lp(-1, -2));
        for (int i = 0; i < all.length; i++) {
            final ir.meelano.manager.ui.Changelog.Ver v = all[i];
            final boolean open = i < 3;
            final LinearLayout body = a.kit.v();
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            for (ir.meelano.manager.ui.Changelog.Item it : v.items) {
                LinearLayout row = a.kit.v();
                row.setPadding(0, Theme.dp(6), 0, Theme.dp(6));
                row.addView(a.kit.text(it.glyph + "  " + it.title, 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
                row.addView(a.kit.hint(it.brief), a.kit.lp(-1, -2));
                body.addView(row, a.kit.lp(-1, -2));
            }
            String head = v.name + "  •  " + v.dateFa + "  •  " + v.headline;
            TextView hd = a.kit.text(head, 13f, Theme.GOLD_SOFT, true);
            hd.setPadding(0, Theme.dp(12), 0, Theme.dp(4));
            hd.setOnClickListener(x -> body.setVisibility(body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
            box.addView(hd, a.kit.lp(-1, -2));
            box.addView(body, a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, box);
    }

    /** Company profile auto-loaded from Atiran (no manual entry needed). */
    private void companyInfo(LinearLayout nt, LinearLayout content) {
        Company.Info co = Company.get(a);
        nt.addView(a.kit.text("مشخصات شرکت (خودکار از آتیران)", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        nt.addView(a.kit.kv("نام", co.displayName(a), Theme.TEXT), a.kit.lp(-1, -2));
        if (co.director != null && !co.director.trim().isEmpty())
            nt.addView(a.kit.kv("مدیرعامل", co.director.trim(), Theme.TEXT), a.kit.lp(-1, -2));
        String ph = co.phonesLine(a);
        if (!ph.isEmpty()) nt.addView(a.kit.kv("تلفن‌ها", Money.fa(ph), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        String ad = co.displayAddr(a);
        if (!ad.isEmpty()) nt.addView(a.kit.kv("آدرس", ad, Theme.TEXT), a.kit.lp(-1, -2));
        if (co.meli != null && !co.meli.trim().isEmpty())
            nt.addView(a.kit.kv("شناسه ملی", Money.fa(co.meli.trim()), Theme.MUTED), a.kit.lp(-1, -2));
        if (co.egh != null && !co.egh.trim().isEmpty())
            nt.addView(a.kit.kv("کد اقتصادی", Money.fa(co.egh.trim()), Theme.MUTED), a.kit.lp(-1, -2));
        if (co.pos != null && !co.pos.trim().isEmpty())
            nt.addView(a.kit.kv("کد پستی", Money.fa(co.pos.trim()), Theme.MUTED), a.kit.lp(-1, -2));
        nt.addView(a.kit.btnGhost("⇅ به‌روزرسانی از آتیران", Theme.GOLD, v -> {
            a.kit.toast("در حال به‌روزرسانی مشخصات…");
            a.refreshCompany(() -> render(content));
        }), a.kit.lp(-1, -2));
    }

    private void runBackup(LinearLayout content) {
        final TextView st = a.kit.text("آماده‌سازی…", 13f, Theme.TEXT, false);
        st.setGravity(Gravity.CENTER);
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(20), Theme.dp(20), Theme.dp(20), Theme.dp(20));
        body.addView(st, a.kit.lp(-1, -2));
        final AlertDialog dlg = a.kit.dialog("بکاپ هوشمند", body, false);
        dlg.show();
        Backup.export(a, a.settings, (fa, doneN, total) ->
                st.setText(a.getString(R.string.backup_progress, fa,
                        Money.fa(doneN + " از " + total))), new Backup.Done() {
            @Override
            public void onDone(java.io.File zip, long bytes, String notes) {
                try {
                    dlg.dismiss();
                } catch (Exception ignored) { Diag.ignored("SettingsScreen:715", ignored); }
                a.settings.saveBackupInfo(Jalali.faDate(Jalali.todayStr()), Backup.sizeFa(bytes));
                a.kit.toast("بکاپ ساخته شد (" + Backup.sizeFa(bytes) + ") • " + notes);
                render(content);
                try {
                    java.io.File shared = Backup.toShareDir(a, zip);
                    if (shared != null) ShareProvider.share(a, shared, "application/zip", "بکاپ هوشمند میلانو");
                    else a.kit.toast("فایل در پوشه Downloads ذخیره شد");
                } catch (Exception e) {
                    a.kit.toast("فایل در پوشه Downloads ذخیره شد");
                }
            }

            @Override
            public void onFail(String faError) {
                try {
                    dlg.dismiss();
                } catch (Exception ignored) { Diag.ignored("SettingsScreen:732", ignored); }
                a.kit.toast(faError == null ? "بکاپ ممکن نشد" : faError);
            }
        });
    }

    private void moveHome(java.util.List<String> order, int idx, int delta, LinearLayout content) {
        int j = idx + delta;
        if (idx < 0 || idx >= order.size() || j < 0 || j >= order.size()) return;
        String t = order.get(idx);
        order.set(idx, order.get(j));
        order.set(j, t);
        StringBuilder b = new StringBuilder();
        for (String k : order) {
            if (b.length() > 0) b.append(',');
            b.append(k);
        }
        a.settings.setHomeOrder(b.toString());
        render(content);
    }

    private void rolePinDialog(final LinearLayout content, final String role, final String fa) {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.text("رمز نقش «" + fa + "»", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final EditText e = a.kit.editPin("۴ رقم (خالی = بدون رمز)", "");
        body.addView(e, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(8));
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(a.kit.btn("ثبت", v -> {
            String pin = e.getText().toString().trim();
            if (!pin.isEmpty() && pin.length() != 4) {
                a.kit.toast("رمز باید ۴ رقم باشد یا خالی بماند");
                return;
            }
            ir.meelano.manager.core.RoleStore.setRolePin(a, role, pin.isEmpty() ? null : pin);
            a.kit.toast("رمز نقش «" + fa + "» ثبت شد");
            box[0].dismiss();
            render(content);
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("رمز نقش", body, true);
        box[0].show();
    }

    private void pinDialog() {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.text("رمز ۴ رقمی", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final EditText e1 = a.kit.editPin("رمز جدید", "");
        final EditText e2 = a.kit.editPin("تکرار رمز", "");
        body.addView(e1, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(8));
        body.addView(e2, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(8));
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(a.kit.btn("ثبت", v -> {
            String p1 = e1.getText().toString().trim();
            String p2 = e2.getText().toString().trim();
            if (p1.length() != 4 || !p1.equals(p2)) {
                a.kit.toast("رمز ۴ رقمی و تکرار آن باید یکسان باشد");
                return;
            }
            a.settings.setPin(p1);
            a.kit.toast("قفل فعال شد");
            box[0].dismiss();
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("قفل مدیریتی", body, true);
        box[0].show();
    }

    private String editionNow() {
        try {
            String r = ir.meelano.manager.core.AtiranAuth.sessionRole(a);
            if (r == null || r.isEmpty()) r = ir.meelano.manager.core.RoleStore.current(a);
            return ir.meelano.manager.core.RoleStore.editionFa(r);
        } catch (Exception e) {
            return "نسخه مدیر";
        }
    }

    private String appVersion() {
        try {
            String v = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
            return Money.fa(v == null || v.isEmpty() ? "—" : v);
        } catch (Exception e) {
            return "—";
        }
    }
}
