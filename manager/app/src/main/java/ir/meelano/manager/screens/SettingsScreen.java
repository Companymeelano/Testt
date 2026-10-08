package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.ShopCard;
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
        c.addView(a.kit.text("اتصال به آتیران", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final EditText host = a.kit.edit("آدرس سرور", a.settings.effHost());
        final EditText port = a.kit.editNum("پورت", String.valueOf(a.settings.effPort()));
        final EditText db = a.kit.edit("نام دیتابیس", a.settings.effDb());
        final EditText user = a.kit.edit("نام کاربری", a.settings.effUser());
        final EditText pass = a.kit.edit("رمز عبور", a.settings.effPass(), true);
        for (EditText e : new EditText[]{host, port, db, user, pass}) {
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, Theme.dp(5), 0, Theme.dp(5));
            c.addView(e, p);
        }
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btn("ذخیره", v -> {
            a.settings.saveConnection(host.getText().toString(), port.getText().toString(),
                    db.getText().toString(), user.getText().toString(), pass.getText().toString());
            a.kit.toast("تنظیمات ذخیره شد");
            a.checkConn();
        }), a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(a.kit.btnGhost("تست اتصال", Theme.SUCCESS, v -> {
            String h = host.getText().toString().trim();
            if (h.isEmpty()) {
                a.kit.toast("آدرس سرور را وارد کنید");
                return;
            }
            int tp;
            try {
                tp = Integer.parseInt(Money.en(port.getText().toString()).trim());
            } catch (Exception e) {
                a.kit.toast("پورت معتبر نیست");
                return;
            }
            a.kit.toast("در حال تست اتصال…");
            // Tests the on-screen values — no need to save first.
            a.testConnection(h, tp, db.getText().toString().trim(), user.getText().toString().trim(),
                    pass.getText().toString(), new Repo.Cb<String>() {
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
        c.addView(row, a.kit.lp(-1, -2));
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
        final android.widget.EditText shop = a.kit.edit("نام فروشگاه (برای کارت ویزیت دیجیتال)", a.settings.shopName());
        LinearLayout.LayoutParams shp = a.kit.lp(-1, -2);
        shp.setMargins(0, Theme.dp(6), 0, 0);
        nt.addView(shop, shp);
        final android.widget.EditText shopPhone = a.kit.edit("تلفن فروشگاه", a.settings.shopPhone());
        LinearLayout.LayoutParams spp = a.kit.lp(-1, -2);
        spp.setMargins(0, Theme.dp(6), 0, 0);
        nt.addView(shopPhone, spp);
        final android.widget.EditText shopAddr = a.kit.edit("آدرس فروشگاه", a.settings.shopAddr());
        LinearLayout.LayoutParams sap = a.kit.lp(-1, -2);
        sap.setMargins(0, Theme.dp(6), 0, 0);
        nt.addView(shopAddr, sap);
        nt.addView(a.kit.btnGhost("🪪 ساخت کارت ویزیت دیجیتال", Theme.VIOLET, v -> {
            String nm = shop.getText().toString().trim();
            if (!nm.isEmpty()) a.settings.setShopName(nm);
            a.settings.setShopPhone(shopPhone.getText().toString());
            a.settings.setShopAddr(shopAddr.getText().toString());
            a.kit.toast(nm.isEmpty() ? "نام فروشگاه را وارد کنید" : "مشخصات فروشگاه ذخیره شد");
            if (!nm.isEmpty()) ShopCard.show(a);
        }), a.kit.lp(-1, -2));
        nt.addView(a.kit.btnGhost("📺 حالت تلویزیون فروشگاه", Theme.SUCCESS, v -> a.startTv()), a.kit.lp(-1, -2));
        nt.addView(a.kit.hint("هشدار هوشمند هر روز ساعت ۸ سررسیدها، معوق‌ها و چک‌های برگشتی تازه را اعلان می‌کند؛ گزارش صبحگاهی خلاصه فروش و دریافت دیروز را اضافه می‌کند."), a.kit.lp(-1, -2));
        a.kit.addCard(content, nt);

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
        ab.addView(a.kit.kv("منبع داده", "SQL Server آتیران (اتصال مستقیم)", Theme.TEXT), a.kit.lp(-1, -2));
        ab.addView(a.kit.hint("همه بخش‌ها داده زنده نمایش می‌دهند؛ بدون اتصال، اطلاع‌رسانی می‌شود."), a.kit.lp(-1, -2));
        a.kit.addCard(content, ab);
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
