package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import ir.meelano.manager.core.CacheStore;
import android.widget.ScrollView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** System users: roles, activity and login history. */
public class UsersScreen extends Screen {
    public UsersScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "users"; }

    @Override
    public String title() { return "کاربران"; }

    @Override
    public String glyph() { return "⛉"; }

    @Override
    public int accent() { return Theme.STEEL; }

    private static final class Data {
        List<Row> users = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال دریافت کاربران…"), a.kit.lp(-1, -2));

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.users = soft(d.notes, "کاربران", () -> Repo.exec(c, MasterQueries.usersList(m)));
            if (d.users == null) throw new Exception(firstNote(d.notes));
            return d;
        }, new Repo.Cb<Data>() {
            @Override
            public void ok(Data d) {
                saveCache(d);
                build(content, d);
            }

            @Override
            public void fail(String faError) {
                String[] lab = {""};
                Data cached = loadCache(lab);
                if (cached != null) {
                    build(content, cached);
                    offlineBanner(content, lab[0], faError);
                    return;
                }
                content.removeAllViews();
                content.addView(heroCard(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(12));
                content.addView(a.kit.error(faError, () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    private String firstNote(Map<String, String> notes) {
        for (String v : notes.values()) return v;
        return "داده‌ای دریافت نشد";
    }

    private void saveCache(Data d) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("users", d.users);
        CacheStore.saveData(a, "cx_users", cacheNow(), m);
    }

    private Data loadCache(String[] lab) {
        java.util.Map<String, Object> m = CacheStore.loadData(a, "cx_users", lab);
        if (m == null) return null;
        Data d = new Data();
        d.users = CacheStore.rows(m, "users");
        return d;
    }
    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        int activeToday = 0;
        if (d.users != null) for (Row r : d.users) if (r.l("todayN") > 0) activeToday++;
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("کاربران", Money.fa(String.valueOf(d.users == null ? 0 : d.users.size())), "نفر", Theme.GOLD));
        kpis.add(new Kit.Kpi("فعال امروز", Money.fa(String.valueOf(activeToday)), "نفر", Theme.SUCCESS));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        if (d.users == null || d.users.isEmpty()) {
            content.addView(a.kit.empty("کاربری یافت نشد", null), a.kit.lp(-1, -2));
            return;
        }
        for (Row r : d.users) {
            final Row row = r;
            boolean on = isOn(row.s("active"));
            String sub = row.s("role");
            if (!row.s("lastLogin").isEmpty()) sub += (sub.isEmpty() ? "" : " • ") + "آخرین ورود " + Money.fa(Jalali.faDate(row.s("lastLogin")));
            View v = a.kit.personRow(row.s("name"), sub.isEmpty() ? "—" : sub,
                    row.l("todayN") > 0 ? Money.fa(String.valueOf(row.l("todayN"))) + " اقدام امروز" : "—",
                    on ? "فعال" : "غیرفعال", on ? Theme.SUCCESS : Theme.MUTED,
                    v2 -> openLogins(row));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }

        renderNotes(content, d.notes);
    }

    private boolean isOn(String active) {
        if (active == null || active.trim().isEmpty()) return true;
        String t = active.trim().toUpperCase(java.util.Locale.US);
        // usersList already returns «✓ فعال» / «✕ غیرفعال»; still accept raw flags.
        if (t.contains("غیرفعال")) return false;
        if (t.contains("فعال")) return true;
        return !"F".equals(t) && !"0".equals(t) && !"FALSE".equals(t) && !"N".equals(t);
    }

    private void openLogins(final Row user) {
        a.kit.toast("در حال دریافت ورودها…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.userLogins(m, user.s("id")));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                LinearLayout body = a.kit.v();
                body.addView(a.kit.kv("کاربر", user.s("name"), Theme.TEXT), a.kit.lp(-1, -2));
                if (!user.s("role").isEmpty()) body.addView(a.kit.kv("نقش", user.s("role"), Theme.TEXT), a.kit.lp(-1, -2));
                if (!user.s("phone").isEmpty()) body.addView(a.kit.kv("تلفن", Money.fa(user.s("phone")), Theme.TEXT), a.kit.lp(-1, -2));
                addLoginMgmt(body, user);
                if (rows.isEmpty()) {
                    body.addView(a.kit.hint("سابقه ورودی ثبت نشده است"), a.kit.lp(-1, -2));
                } else {
                    body.addView(a.kit.text("آخرین ورودها", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
                    ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                            new ReportCatalog.Col("at", "زمان", ReportCatalog.T_TEXT),
                            new ReportCatalog.Col("ip", "سیستم", ReportCatalog.T_TEXT),
                            new ReportCatalog.Col("descrip", "شرح", ReportCatalog.T_TEXT),
                    };
                    body.addView(a.kit.dataTable(cols, rows, null), a.kit.lp(-1, -2));
                }
                ScrollView sv = new ScrollView(a);
                sv.addView(body);
                AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                        .setView(sv).create();
                if (dlg.getWindow() != null)
                    dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
                body.addView(a.kit.gap(8));
                body.addView(a.kit.btn("بستن", v -> dlg.dismiss()), a.kit.lp(-1, -2));
                body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
                dlg.show();
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    // ================= Atiran login management (v26, manager only) =================

    private int uidOf(Row user) {
        try {
            return Integer.parseInt(Money.en(user.s("id")).trim());
        } catch (Exception e) {
            return -999;
        }
    }

    /** PIN + app-role + staff-link rows for one Atiran user. */
    private void addLoginMgmt(LinearLayout body, final Row user) {
        final int uid = uidOf(user);
        if (uid == -999) return;
        body.addView(a.kit.gap(6));
        body.addView(a.kit.text("ورود به اپ", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final String autoRole = AtiranAuth.mapRole(0, user.s("role"));
        String ov = AtiranAuth.overrideRole(a, uid);
        final android.widget.TextView[] pinV = new android.widget.TextView[1];
        final android.widget.TextView[] roleV = new android.widget.TextView[1];
        final android.widget.TextView[] linkV = new android.widget.TextView[1];
        final Runnable paint = () -> {
            pinV[0].setText(AtiranAuth.pinSet(a, uid) ? "رمزدار \uD83D\uDD12" : "بدون رمز");
            String o = AtiranAuth.overrideRole(a, uid);
            roleV[0].setText(RoleStore.faName(o.isEmpty() ? autoRole : o)
                    + (o.isEmpty() ? " (خودکار)" : ""));
            linkV[0].setText(AtiranAuth.faStaffLink(a, uid));
        };
        pinV[0] = mgmtRow(body, "رمز ورود اپ", v -> pinDialog(user, uid, paint));
        roleV[0] = mgmtRow(body, "نقش در اپ", v -> roleDialog(user, uid, autoRole, paint));
        linkV[0] = mgmtRow(body, "اتصال پرسنلی", v -> linkDialog(user, uid, paint));
        paint.run();
        body.addView(a.kit.hint("در فاز ۱ ورود فقط با رمز واقعی آتیران است؛ این رمز و نقش از فاز ۲ به بعد برای نسخه‌های هر نقش به کار می‌روند. نقش «خودکار» از روی نام نقش آتیران تشخیص داده می‌شود."),
                a.kit.lp(-1, -2));
    }

    private android.widget.TextView mgmtRow(LinearLayout body, String label,
            View.OnClickListener onClick) {
        LinearLayout row = a.kit.h();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(a.kit.text(label, 13f, Theme.MUTED, false), a.kit.wlp(1f));
        android.widget.TextView v = a.kit.text("—", 13f, Theme.TEXT, true);
        row.addView(v, a.kit.lp(-2, -2));
        row.addView(a.kit.space(8));
        row.addView(a.kit.btnGhost("تغییر", Theme.GOLD, onClick), a.kit.lp(-2, -2));
        body.addView(row, a.kit.lp(-1, -2));
        return v;
    }

    private void pinDialog(Row user, final int uid, final Runnable paint) {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        b.addView(a.kit.kv("کاربر", user.s("name"), Theme.TEXT), a.kit.lp(-1, -2));
        b.addView(a.kit.kv("وضعیت", AtiranAuth.pinSet(a, uid) ? "رمزدار \uD83D\uDD12" : "بدون رمز",
                Theme.TEXT), a.kit.lp(-1, -2));
        final android.widget.EditText e = a.kit.editPin("رمز ۴ رقمی جدید", "");
        b.addView(e, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(a.kit.btn("ذخیره رمز", v -> {
            String pin = e.getText().toString();
            if (Money.en(pin).trim().length() < 4) {
                a.kit.toast("رمز حداقل ۴ رقم باشد");
                return;
            }
            AtiranAuth.setPin(a, uid, pin);
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            paint.run();
            a.kit.toast("رمز ورود ذخیره شد");
        }), a.kit.lp(-1, -2));
        b.addView(a.kit.btnGhost("حذف رمز", Theme.DANGER, v -> {
            AtiranAuth.setPin(a, uid, null);
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            paint.run();
            a.kit.toast("رمز حذف شد");
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("رمز ورود اپ", b, true);
        box[0].show();
    }

    private void roleDialog(Row user, final int uid, String autoRole, final Runnable paint) {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        b.addView(a.kit.kv("کاربر", user.s("name"), Theme.TEXT), a.kit.lp(-1, -2));
        String ator = user.s("role");
        b.addView(a.kit.kv("نقش آتیران", ator.isEmpty() ? "—" : ator, Theme.TEXT),
                a.kit.lp(-1, -2));
        b.addView(a.kit.kv("تشخیص خودکار", RoleStore.faName(autoRole), Theme.TEXT),
                a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        for (String r0 : RoleStore.ALL) {
            final String r = r0;
            String cur = AtiranAuth.overrideRole(a, uid);
            String label = RoleStore.faName(r) + " — " + RoleStore.faDesc(r)
                    + (r.equals(cur) ? " (فعلی)" : "");
            b.addView(r.equals(cur) || (cur.isEmpty() && r.equals(autoRole))
                    ? a.kit.btn(label, v -> pickRole(box, uid, r, paint))
                    : a.kit.btnGhost(label, Theme.GOLD, v -> pickRole(box, uid, r, paint)),
                    a.kit.lp(-1, -2));
        }
        b.addView(a.kit.btnGhost("بازگشت به خودکار", Theme.TEXT, v -> pickRole(box, uid, null, paint)),
                a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("نقش در اپ", a.kit.scrollWrap(b, 420), true);
        box[0].show();
    }

    private void pickRole(AlertDialog[] box, int uid, String role, Runnable paint) {
        AtiranAuth.setOverrideRole(a, uid, role);
        try {
            box[0].dismiss();
        } catch (Exception ignored) { }
        paint.run();
        a.kit.toast("نقش ذخیره شد؛ در ورود بعدی اعمال می‌شود");
    }

    private void linkDialog(Row user, final int uid, final Runnable paint) {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        b.addView(a.kit.kv("کاربر", user.s("name"), Theme.TEXT), a.kit.lp(-1, -2));
        b.addView(a.kit.hint("این کاربر آتیران معادل کدام ویزیتور/انبار است؟ (برای شخصی‌سازی بعدی داده‌ها)"),
                a.kit.lp(-1, -2));
        final android.widget.EditText eV = a.kit.editNum("کد ویزیتور (vis_rdf)", "");
        final android.widget.EditText eA = a.kit.editNum("کد انبار (rdf_anbar)", "");
        b.addView(eV, a.kit.lp(-1, -2));
        b.addView(eA, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(a.kit.btn("ذخیره اتصال", v -> {
            AtiranAuth.setStaffLink(a, uid, eV.getText().toString(), eA.getText().toString());
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            paint.run();
            a.kit.toast("اتصال پرسنلی ذخیره شد");
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("اتصال پرسنلی", b, true);
        box[0].show();
    }
}
