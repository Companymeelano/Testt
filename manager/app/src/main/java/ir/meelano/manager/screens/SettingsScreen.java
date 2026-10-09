package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.ShopCard;
import ir.meelano.manager.core.Backup;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.data.NetRoute;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.ui.Theme;

/** Connection settings, PIN lock and about. */
public class SettingsScreen extends Screen {
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
    public int accent() { return Theme.TEAL; }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        // ---- connection ----
        LinearLayout c = a.kit.card(Theme.GOLD);
        c.addView(a.kit.text("🔒 اتصال به آتیران", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        // Locked by design: the server address and credentials live only inside the
        // seller's encrypted connection card and are never shown or typed here.
        final boolean cfg = a.settings.connConfigured();
        c.addView(a.kit.kv("وضعیت", cfg ? "✓ تنظیم‌شده توسط فروشنده" : "✕ هنوز تنظیم نشده",
                cfg ? Theme.SUCCESS : Theme.DANGER), a.kit.lp(-1, -2));
        if (cfg) {
            c.addView(a.kit.kv("سرور", a.settings.maskedHost(), Theme.MUTED), a.kit.lp(-1, -2));
        }
        String lanM = a.settings.maskedLan();
        String wanM = a.settings.maskedWan();
        c.addView(a.kit.kv("داخل شبکه", lanM.isEmpty() ? "✕ ثبت نشده" : "✓ " + lanM,
                lanM.isEmpty() ? Theme.DANGER : Theme.SUCCESS), a.kit.lp(-1, -2));
        c.addView(a.kit.kv("خارج شبکه", wanM.isEmpty() ? "✕ ثبت نشده" : "✓ " + wanM,
                wanM.isEmpty() ? Theme.DANGER : Theme.SUCCESS), a.kit.lp(-1, -2));
        String lm = a.settings.linkMode();
        c.addView(a.kit.chips(new String[]{"خودکار ✦", "فقط داخل شبکه", "فقط خارج شبکه"},
                "lan".equals(lm) ? 1 : ("wan".equals(lm) ? 2 : 0), idx -> {
            a.settings.setLinkMode(idx == 1 ? "lan" : (idx == 2 ? "wan" : "auto"));
            a.kit.toast(idx == 0 ? "حالت خودکار فعال شد" : "حالت دستی فعال شد");
            try {
                a.checkConn();
            } catch (Exception ignored) { }
            render(content);
        }), a.kit.lp(-1, -2));
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btn("تست اتصال", v -> {
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
        }), a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(a.kit.btnGhost("تنظیم با کارت فروشنده", Theme.GOLD, v -> {
            try {
                a.startActivity(new android.content.Intent(a,
                        ir.meelano.manager.LicenseActivity.class));
            } catch (Exception e) {
                a.kit.toast("ممکن نشد");
            }
        }), a.kit.wlp(1f));
        c.addView(row, a.kit.lp(-1, -2));
        c.addView(a.kit.btnGhost("🛠 عیب‌یابی هوشمند اتصال", Theme.TEAL, v -> {
            try {
                ir.meelano.manager.ui.LinkPanel.showDiagnose(a, a.settings);
            } catch (Exception e) {
                a.kit.toast("ممکن نشد");
            }
        }), a.kit.lp(-1, -2));
        boolean vpn = NetRoute.isVpnActive(a);
        c.addView(a.kit.kv("فیلترشکن", vpn ? "فعال" : "غیرفعال", vpn ? Theme.WARNING : Theme.SUCCESS), a.kit.lp(-1, -2));
        final boolean direct = a.settings.directConn();
        c.addView(a.kit.btnGhost("◉ اتصال مستقیم: " + (direct ? "روشن" : "خاموش"), Theme.INFO, v -> {
            a.settings.setDirectConn(!direct);
            a.kit.toast(direct ? "اتصال مستقیم خاموش شد" : "اتصال مستقیم روشن شد");
            render(content);
        }), a.kit.lp(-1, -2));
        c.addView(a.kit.hint("اگر فیلترشکن مسیر سرور را می‌بندد، «اتصال مستقیم» را روشن کنید تا برنامه از شبکه عادی عبور کند."), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);

        // ---- luxury theme ----
        LinearLayout th = a.kit.card(Theme.GOLD);
        th.addView(a.kit.text("✦ ظاهر لاکچری", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        th.addView(a.kit.text("قالب", 12f, Theme.MUTED, true), a.kit.lp(-1, -2));
        final boolean isLight = "light".equals(a.settings.themeMode());
        th.addView(a.kit.chips(new String[]{"◐ تیره • طلای نیمه‌شب", "◑ روشن • عاج سلطنتی"}, isLight ? 1 : 0, idx -> {
            a.settings.setThemeMode(idx == 1 ? "light" : "dark");
            a.refreshTheme();
        }), a.kit.lp(-1, -2));
        th.addView(a.kit.gap(8));
        th.addView(a.kit.text("رنگ اصلی برنامه", 12f, Theme.MUTED, true), a.kit.lp(-1, -2));
        LinearLayout sw = a.kit.h();
        sw.setGravity(android.view.Gravity.CENTER);
        final String cur = a.settings.themeAccent();
        for (String[] acc : Theme.accents()) {
            final String key = acc[0];
            android.widget.TextView dot = new android.widget.TextView(a);
            boolean on = key.equals(cur);
            dot.setBackground(Theme.swatch(Theme.accentPreview(key), on));
            int sz = Theme.dp(on ? 46 : 38);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.setMargins(Theme.dp(5), Theme.dp(4), Theme.dp(5), Theme.dp(4));
            dot.setLayoutParams(lp);
            android.widget.LinearLayout cell = a.kit.v();
            cell.setGravity(android.view.Gravity.CENTER);
            cell.addView(dot, lp);
            android.widget.TextView lb = a.kit.text(acc[1], 9.5f, on ? Theme.TEXT : Theme.MUTED, on);
            lb.setGravity(android.view.Gravity.CENTER);
            cell.addView(lb, a.kit.lp(-2, -2));
            cell.setPadding(Theme.dp(2), 0, Theme.dp(2), 0);
            Theme.pressable(cell);
            cell.setOnClickListener(v -> {
                a.settings.setThemeAccent(key);
                a.refreshTheme();
            });
            sw.addView(cell, a.kit.wlp(1f));
        }
        th.addView(sw, a.kit.lp(-1, -2));
        th.addView(a.kit.btnGhost("🔍 بزرگ‌نمایی متن: " + MainActivity.zoomLabel(a.settings.zoomIdx()) + " (برای تغییر بزنید)", Theme.GOLD, v -> {
            a.cycleZoom();
            render(content);
        }), a.kit.lp(-1, -2));
        final boolean sn = a.settings.seasonalOn();
        th.addView(a.kit.btnGhost("🌸 تم مناسبتی خودکار: " + (sn ? "روشن" : "خاموش"), Theme.TEAL, v -> {
            a.settings.setSeasonalOn(!sn);
            a.kit.toast(!sn ? "تم مناسبتی روشن شد" : "تم مناسبتی خاموش شد");
            a.refreshTheme();
        }), a.kit.lp(-1, -2));
        th.addView(a.kit.hint("قالب و رنگ انتخابی فوراً روی همه بخش‌ها، جدول‌ها، نمودارها و آیکن‌ها اعمال می‌شود."), a.kit.lp(-1, -2));
        a.kit.addCard(content, th);

        // ---- notifications + shop ----
        LinearLayout nt = a.kit.card(Theme.INFO);
        nt.addView(a.kit.text("هشدارها و فروشگاه", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean non = a.settings.notifOn();
        nt.addView(a.kit.btnGhost("🔊 هشدار هوشمند سررسید: " + (non ? "روشن" : "خاموش"), Theme.INFO, v -> {
            a.settings.setNotifOn(!non);
            if (!non) {
                ir.meelano.manager.core.Notify.boot(a);
                a.kit.toast("هشدار هوشمند روشن شد؛ هر روز ساعت ۸ بررسی می‌شود");
            } else {
                ir.meelano.manager.core.Notify.cancelDaily(a);
                a.kit.toast("هشدار هوشمند خاموش شد");
            }
            render(content);
        }), a.kit.lp(-1, -2));
        final boolean mon = a.settings.morningOn();
        nt.addView(a.kit.btnGhost("☀ گزارش صبحگاهی: " + (mon ? "روشن" : "خاموش"), Theme.GOLD, v -> {
            a.settings.setMorningOn(!mon);
            a.kit.toast(!mon ? "گزارش صبحگاهی روشن شد" : "گزارش صبحگاهی خاموش شد");
            render(content);
        }), a.kit.lp(-1, -2));
        final boolean bkp = a.settings.backupOn();
        nt.addView(a.kit.btnGhost("⛁ یادآوری پشتیبان‌گیری هفتگی: " + (bkp ? "روشن" : "خاموش"), Theme.TEAL, v -> {
            a.settings.setBackupOn(!bkp);
            if (!bkp) ir.meelano.manager.core.Notify.scheduleWeekly(a);
            else ir.meelano.manager.core.Notify.cancelWeekly(a);
            a.kit.toast(!bkp ? "یادآوری هفتگی روشن شد (جمعه‌ها ساعت ۹)" : "یادآوری هفتگی خاموش شد");
            render(content);
        }), a.kit.lp(-1, -2));
        final boolean wk = a.settings.weeklyOn();
        nt.addView(a.kit.btnGhost("📊 گزارش هفتگی (پنجشنبه‌ها ساعت ۲۰): " + (wk ? "روشن" : "خاموش"), Theme.VIOLET, v -> {
            a.settings.setWeeklyOn(!wk);
            if (!wk) ir.meelano.manager.core.Notify.scheduleWeeklyReport(a);
            else ir.meelano.manager.core.Notify.cancelWeeklyReport(a);
            a.kit.toast(!wk ? "گزارش هفتگی روشن شد" : "گزارش هفتگی خاموش شد");
            render(content);
        }), a.kit.lp(-1, -2));
        final boolean stk = a.settings.stockOn();
        nt.addView(a.kit.btnGhost("\uD83D\uDCE6 هشدار کالای کم‌موجود: " + (stk ? "روشن" : "خاموش"), Theme.WARNING, v -> {
            a.settings.setStockOn(!stk);
            a.kit.toast(!stk ? "هشدار کم‌موجودی روشن شد" : "هشدار کم‌موجودی خاموش شد");
            render(content);
        }), a.kit.lp(-1, -2));
        final boolean eod = a.settings.eodOn();
        nt.addView(a.kit.btnGhost("🌙 جمع‌بندی پایان روز (هر شب ساعت ۲۱): " + (eod ? "روشن" : "خاموش"), Theme.VIOLET, v -> {
            a.settings.setEodOn(!eod);
            if (!eod) ir.meelano.manager.core.Notify.scheduleEod(a);
            else ir.meelano.manager.core.Notify.cancelEod(a);
            a.kit.toast(!eod ? "جمع‌بندی پایان روز روشن شد" : "جمع‌بندی پایان روز خاموش شد");
            render(content);
        }), a.kit.lp(-1, -2));
        final boolean grd = a.settings.guardOn();
        nt.addView(a.kit.btnGhost("🛡 نگهبان فروش لحظه‌ای: " + (grd ? "روشن" : "خاموش"), Theme.DANGER, v -> {
            a.settings.setGuardOn(!grd);
            if (!grd) ir.meelano.manager.core.Notify.scheduleGuard(a);
            else ir.meelano.manager.core.Notify.cancelGuard(a);
            a.kit.toast(!grd ? "نگهبان فروش روشن شد؛ هر ۳۰ دقیقه بررسی می‌شود" : "نگهبان فروش خاموش شد");
            render(content);
        }), a.kit.lp(-1, -2));
        companyInfo(nt, content);
        nt.addView(a.kit.btnGhost("🪪 ساخت کارت ویزیت دیجیتال", Theme.VIOLET, v -> ShopCard.show(a)), a.kit.lp(-1, -2));
        nt.addView(a.kit.btnGhost("📺 حالت تلویزیون فروشگاه", Theme.SUCCESS, v -> a.startTv()), a.kit.lp(-1, -2));
        nt.addView(a.kit.hint("هشدار هوشمند هر روز ساعت ۸ سررسیدها، معوق‌ها، چک‌های برگشتی تازه و کالاهای کم‌موجود را اعلان می‌کند؛ گزارش صبحگاهی خلاصه فروش و دریافت دیروز را اضافه می‌کند."), a.kit.lp(-1, -2));
        a.kit.addCard(content, nt);

        // ---- smart backup ----
        LinearLayout bk = a.kit.card(Theme.TEAL);
        bk.addView(a.kit.text("پشتیبان‌گیری هوشمند", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String bl = a.settings.backupLast();
        if (!bl.isEmpty())
            bk.addView(a.kit.kv("آخرین بکاپ", bl + (a.settings.backupSize().isEmpty() ? "" : " • " + a.settings.backupSize()), Theme.TEXT), a.kit.lp(-1, -2));
        bk.addView(a.kit.btn("⛁ بکاپ هوشمند روی گوشی", v -> runBackup(content)), a.kit.lp(-1, -2));
        final boolean abo = a.settings.abOn();
        bk.addView(a.kit.btnGhost("⛁ بکاپ خودکار شبانه: " + (abo ? "روشن" : "خاموش"), Theme.TEAL, v -> {
            a.settings.setAbOn(!abo);
            if (!abo) ir.meelano.manager.core.AutoBackup.schedule(a);
            else ir.meelano.manager.core.AutoBackup.cancel(a);
            a.kit.toast(!abo ? "بکاپ خودکار روشن شد (هر شب ساعت " + Money.fa(String.valueOf(a.settings.abHour())) + ")"
                    : "بکاپ خودکار خاموش شد");
            render(content);
        }), a.kit.lp(-1, -2));
        if (abo) {
            LinearLayout hrow = a.kit.h();
            hrow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            hrow.addView(a.kit.btnGhost("−", Theme.GOLD, v -> {
                a.settings.setAbHour(a.settings.abHour() - 1);
                ir.meelano.manager.core.AutoBackup.schedule(a);
                render(content);
            }), a.kit.lp(Theme.dp(56), -2));
            android.widget.TextView ht = a.kit.text("ساعت " + Money.fa(String.valueOf(a.settings.abHour())), 15f, Theme.TEXT, true);
            ht.setGravity(android.view.Gravity.CENTER);
            hrow.addView(ht, a.kit.wlp(1f));
            hrow.addView(a.kit.btnGhost("＋", Theme.GOLD, v -> {
                a.settings.setAbHour(a.settings.abHour() + 1);
                ir.meelano.manager.core.AutoBackup.schedule(a);
                render(content);
            }), a.kit.lp(Theme.dp(56), -2));
            bk.addView(hrow, a.kit.lp(-1, -2));
            bk.addView(a.kit.hint("هر شب سر ساعت، بکاپ کامل در Downloads ذخیره و نتیجه اعلان می‌شود؛ با وای‌فای یا اینترنت گوشی کار می‌کند."), a.kit.lp(-1, -2));
        }
        bk.addView(a.kit.hint("۷۸ جدول آتیران (همه جداول به‌جز تصاویر حجیم کالا و مشتری) به‌صورت CSV داخل یک فایل ZIP در پوشه Downloads ذخیره و برای اشتراک آماده می‌شود."), a.kit.lp(-1, -2));
        a.kit.addCard(content, bk);

        // ---- PIN ----
        LinearLayout p = a.kit.card(Theme.INFO);
        p.addView(a.kit.text("قفل مدیریتی", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        p.addView(a.kit.kv("وضعیت", a.settings.pinEnabled() ? "فعال" : "غیرفعال",
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
            p.addView(a.kit.btnGhost("◉ ورود با اثر انگشت: " + (fp ? "روشن" : "خاموش"), Theme.GOLD, v -> {
                a.settings.setFpOn(!fp);
                a.kit.toast(!fp ? "ورود با اثر انگشت روشن شد" : "ورود با اثر انگشت خاموش شد");
                render(content);
            }), a.kit.lp(-1, -2));
            p.addView(a.kit.hint("اثر انگشت فقط وقتی کار می‌کند که قفل ۴ رقمی هم فعال باشد"), a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, p);

        // ---- roles ----
        LinearLayout r = a.kit.card(Theme.VIOLET);
        r.addView(a.kit.text("نقش‌های کاربری", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean ren = ir.meelano.manager.core.RoleStore.enabled(a);
        r.addView(a.kit.kv("نقش فعلی", "«" + ir.meelano.manager.core.RoleStore.faName(
                ir.meelano.manager.core.RoleStore.current(a)) + "»", Theme.TEXT), a.kit.lp(-1, -2));
        r.addView(a.kit.btnGhost("◉ نقش‌ها: " + (ren ? "روشن" : "خاموش"), Theme.VIOLET, v -> {
            ir.meelano.manager.core.RoleStore.setEnabled(a, !ren);
            a.kit.toast(!ren ? "نقش‌ها روشن شد؛ ورود بعدی با انتخاب نقش است" : "نقش‌ها خاموش شد");
            render(content);
        }), a.kit.lp(-1, -2));
        if (ren) {
            String[][] rr = {{"admin", "مدیر"}, {"seller", "فروشنده"}, {"accountant", "حسابدار"}, {"visitor", "ویزیتور"}, {"distributor", "موزع"}, {"warehouse", "انباردار"}, {"moadian", "مودیان"}};
            for (String[] role : rr) {
                final String rk = role[0];
                final String rn = role[1];
                boolean locked = ir.meelano.manager.core.RoleStore.pinSet(a, rk);
                LinearLayout rrow = a.kit.h();
                rrow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                rrow.addView(a.kit.text(rn + " • " + (locked ? "رمزدار \uD83D\uDD12" : "بدون رمز"),
                        13f, Theme.TEXT, true), a.kit.wlp(1f));
                rrow.addView(a.kit.btnGhost("تعیین رمز", Theme.GOLD,
                        v -> rolePinDialog(content, rk, rn)), a.kit.lp(-2, -2));
                r.addView(rrow, a.kit.lp(-1, -2));
            }
            r.addView(a.kit.btnGhost("تعویض نقش", Theme.TEXT, v -> a.openRoleGate()), a.kit.lp(-1, -2));
            r.addView(a.kit.hint("رمز پیش‌فرض مدیر ۱۲۳۴ است؛ حتماً عوضش کنید. بخش‌های حساس (تنظیمات، کاربران و سود) فقط برای مدیر است. وقتی «ورود کاربر آتیران» روشن است، نقش هر نفر از آتیران می‌آید و این انتخاب‌گر کنار می‌رود."), a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, r);

        // ---- Atiran user login (v26) ----
        LinearLayout lg = a.kit.card(Theme.GOLD);
        lg.addView(a.kit.text("ورود کاربر آتیران", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean lon = ir.meelano.manager.core.AtiranAuth.enabled(a);
        if (ir.meelano.manager.core.AtiranAuth.hasSession(a)) {
            lg.addView(a.kit.kv("کاربر فعلی",
                    ir.meelano.manager.core.AtiranAuth.sessionName(a) + " («"
                            + ir.meelano.manager.core.RoleStore.faName(
                                    ir.meelano.manager.core.AtiranAuth.sessionRole(a))
                            + "»)", Theme.TEXT), a.kit.lp(-1, -2));
        }
        lg.addView(a.kit.btnGhost("◉ صفحه ورود: " + (lon ? "روشن" : "خاموش"), Theme.GOLD, v -> {
            ir.meelano.manager.core.AtiranAuth.setEnabled(a, !lon);
            if (lon) ir.meelano.manager.core.AtiranAuth.clearSession(a);
            a.kit.toast(!lon ? "صفحه ورود روشن شد" : "صفحه ورود خاموش شد");
            render(content);
        }), a.kit.lp(-1, -2));
        if (ir.meelano.manager.core.AtiranAuth.hasSession(a))
            lg.addView(a.kit.btnGhost("خروج از حساب فعلی", Theme.DANGER, v -> a.logout()),
                    a.kit.lp(-1, -2));
        lg.addView(a.kit.hint("وقتی روشن است، بعد از فعال‌سازی هر نفر با نام کاربری آتیران خودش وارد می‌شود و فقط بخش‌های نقش خودش را می‌بیند. رمز هر کاربر از بخش «کاربران» تعیین می‌شود."),
                a.kit.lp(-1, -2));
        a.kit.addCard(content, lg);

        // ---- warehouse direct posting (v28) ----
        LinearLayout wh = a.kit.card(Theme.SUCCESS);
        wh.addView(a.kit.text("انبار", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean wdir = a.settings.whDirect();
        wh.addView(a.kit.btnGhost("◉ ثبت مستقیم در آتیران: " + (wdir ? "روشن" : "خاموش"),
                Theme.SUCCESS, v -> {
                    a.settings.setWhDirect(!wdir);
                    a.kit.toast(!wdir ? "ثبت مستقیم روشن شد" : "ثبت مستقیم خاموش شد؛ فقط پیش‌نویس");
                    render(content);
                }), a.kit.lp(-1, -2));
        wh.addView(a.kit.hint("خاموش = اسناد انبار فقط پیش‌نویس محلی می‌مانند (امن). روشن = ثبت مستقیم در پیش‌فاکتورهای آتیران؛ اول با «پیش‌نمایش SQL» در صفحه رسید کالا بررسی کنید."),
                a.kit.lp(-1, -2));
        a.kit.addCard(content, wh);

        // ---- home dashboard order ----
        LinearLayout ho = a.kit.card(Theme.GOLD);
        ho.addView(a.kit.text("ترتیب داشبورد خانه", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final String[][] secs = {
                {"kpis", "شاخص‌های روز"},
                {"alerts", "هشدار امروز"},
                {"trend", "روند ۱۴ روزه فروش"},
                {"donut", "ترکیب دریافت"},
                {"debtors", "بدهکاران اولویت‌دار"},
                {"visitors", "عملکرد ویزیتورها"},
                {"due", "سررسید چک‌ها"},
                {"eod", "جمع‌بندی پایان روز"},
                {"shortcuts", "دسترسی سریع"},
        };
        final java.util.List<String> order = new java.util.ArrayList<>();
        for (String k : a.settings.homeOrder().split(","))
            if (k != null && !k.trim().isEmpty() && !order.contains(k.trim())) order.add(k.trim());
        for (String[] s2 : secs) if (!order.contains(s2[0])) order.add(s2[0]);
        for (int i = 0; i < order.size(); i++) {
            final int idx = i;
            String ot = order.get(i);
            for (String[] s2 : secs) if (s2[0].equals(ot)) ot = s2[1];
            LinearLayout orow = a.kit.h();
            orow.addView(a.kit.text(Money.fa(String.valueOf(i + 1)) + " • " + ot, 12.5f, Theme.TEXT, true), a.kit.wlp(1f));
            orow.addView(a.kit.btnGhost("↑", Theme.GOLD, v -> moveHome(order, idx, -1, content)), a.kit.lp(Theme.dp(52), -2));
            orow.addView(a.kit.btnGhost("↓", Theme.GOLD, v -> moveHome(order, idx, 1, content)), a.kit.lp(Theme.dp(52), -2));
            ho.addView(orow, a.kit.lp(-1, -2));
        }
        ho.addView(a.kit.btnGhost("بازنشانی ترتیب پیش‌فرض", Theme.MUTED, v -> {
            a.settings.setHomeOrder(ir.meelano.manager.data.Settings.HOME_ORDER_DEFAULT);
            render(content);
        }), a.kit.lp(-1, -2));
        a.kit.addCard(content, ho);

        // ---- about + developer signature ----
        LinearLayout ab = a.kit.card(Theme.VIOLET);
        ab.setGravity(android.view.Gravity.CENTER);
        ab.addView(a.kit.logo(84), new LinearLayout.LayoutParams(Theme.dp(84), Theme.dp(84)));
        android.widget.TextView dn = a.kit.text("Milad Yaghoobi", 23, Theme.GOLD, true);
        dn.setGravity(android.view.Gravity.CENTER);
        ab.addView(dn, a.kit.lp(-1, -2));
        android.widget.TextView dr = a.kit.text("✦ طراح و توسعه‌دهنده ✦", 13f, Theme.TEXT, true);
        dr.setGravity(android.view.Gravity.CENTER);
        ab.addView(dr, a.kit.lp(-1, -2));
        android.widget.TextView tag = a.kit.text("مدیریت میلانو • داشبورد مدیریتی آتیران", 11f, Theme.MUTED, false);
        tag.setGravity(android.view.Gravity.CENTER);
        ab.addView(tag, a.kit.lp(-1, -2));
        ab.addView(a.kit.gap(4));
        ab.addView(a.kit.kv("نسخه", appVersion() + " • ویرایش مدیریت", Theme.TEXT), a.kit.lp(-1, -2));
        ab.addView(a.kit.btnGhost("✦  تازه‌های نسخه", Theme.GOLD, v ->
                ir.meelano.manager.ui.WhatsNew.showCurrent(a)), a.kit.lp(-1, -2));
        ab.addView(a.kit.btnGhost("⬆  بررسی بروزرسانی", Theme.TEAL, v ->
                ir.meelano.manager.core.UpdateCenter.manualCheck(a)), a.kit.lp(-1, -2));
        ab.addView(a.kit.hint(ir.meelano.manager.core.UpdateCenter.lastCheckLine(a)), a.kit.lp(-1, -2));
        ab.addView(a.kit.kv("منبع داده", "SQL Server آتیران (اتصال مستقیم)", Theme.TEXT), a.kit.lp(-1, -2));
        ab.addView(a.kit.text("✦ پشتیبانی میلانو", 13f, Theme.GOLD_SOFT, true), a.kit.lp(-1, -2));
        ab.addView(a.kit.btnGhost("🌐  " + ir.meelano.manager.core.Brand.SITE_LABEL, Theme.GOLD, v ->
                ir.meelano.manager.core.Brand.openSite(a)), a.kit.lp(-1, -2));
        for (String ph : ir.meelano.manager.core.Brand.PHONES) {
            final String fph = ph;
            ab.addView(a.kit.btnGhost("📞  " + Money.fa(ph), Theme.TEAL, v ->
                    ir.meelano.manager.core.Brand.dial(a, fph)), a.kit.lp(-1, -2));
        }
        ab.addView(a.kit.hint("همه بخش‌ها داده زنده نمایش می‌دهند؛ بدون اتصال، اطلاع‌رسانی می‌شود."), a.kit.lp(-1, -2));
        a.kit.addCard(content, ab);
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
        nt.addView(a.kit.btnGhost("⟳ به‌روزرسانی از آتیران", Theme.TEAL, v -> {
            a.kit.toast("در حال به‌روزرسانی مشخصات…");
            a.refreshCompany(() -> render(content));
        }), a.kit.lp(-1, -2));
    }

    private void runBackup(LinearLayout content) {
        final android.widget.TextView st = a.kit.text("آماده‌سازی…", 13f, Theme.TEXT, false);
        st.setGravity(android.view.Gravity.CENTER);
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(20), Theme.dp(20), Theme.dp(20), Theme.dp(20));
        body.addView(st, a.kit.lp(-1, -2));
        final AlertDialog dlg = a.kit.dialog("بکاپ هوشمند", body, false);
        dlg.show();
        Backup.export(a, a.settings, (fa, doneN, total) ->
                st.setText("در حال ذخیره " + fa + " (" + Money.fa(doneN + " از " + total) + ")"), new Backup.Done() {
            @Override
            public void onDone(java.io.File zip, long bytes, String notes) {
                try {
                    dlg.dismiss();
                } catch (Exception ignored) { }
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
                } catch (Exception ignored) { }
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

    private String appVersion() {
        try {
            String v = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
            return Money.fa(v == null || v.isEmpty() ? "—" : v);
        } catch (Exception e) {
            return "—";
        }
    }
}
