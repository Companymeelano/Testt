package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
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
        a.kit.addCard(content, p);

        // ---- about ----
        LinearLayout ab = a.kit.card(Theme.VIOLET);
        ab.addView(a.kit.text("درباره", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        ab.addView(a.kit.kv("نسخه", appVersion() + " • ویرایش مدیریت", Theme.TEXT), a.kit.lp(-1, -2));
        ab.addView(a.kit.kv("منبع داده", "SQL Server آتیران (اتصال مستقیم)", Theme.TEXT), a.kit.lp(-1, -2));
        ab.addView(a.kit.hint("همه بخش‌ها داده زنده نمایش می‌دهند؛ بدون اتصال، اطلاع‌رسانی می‌شود."), a.kit.lp(-1, -2));
        a.kit.addCard(content, ab);
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
