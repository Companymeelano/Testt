package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
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
    public int accent() { return Theme.MUTED; }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        // ---- connection ----
        LinearLayout c = a.kit.card(Theme.GOLD);
        c.addView(a.kit.text("اتصال به آتیران", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final EditText host = a.kit.edit("آدرس سرور", a.settings.effHost());
        final EditText port = a.kit.edit("پورت", String.valueOf(a.settings.effPort()));
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
        }), a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(a.kit.btnGhost("تست اتصال", Theme.SUCCESS, v -> {
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
        c.addView(row, a.kit.lp(-1, -2));
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
        ab.addView(a.kit.kv("نسخه", "۷ • ویرایش مدیریت", Theme.TEXT), a.kit.lp(-1, -2));
        ab.addView(a.kit.kv("منبع داده", "SQL Server آتیران (اتصال مستقیم)", Theme.TEXT), a.kit.lp(-1, -2));
        ab.addView(a.kit.hint("همه بخش‌ها داده زنده نمایش می‌دهند؛ بدون اتصال، اطلاع‌رسانی می‌شود."), a.kit.lp(-1, -2));
        a.kit.addCard(content, ab);
    }

    private void pinDialog() {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.text("رمز ۴ رقمی", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final EditText e1 = a.kit.edit("رمز جدید", "", true);
        final EditText e2 = a.kit.edit("تکرار رمز", "", true);
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
}
