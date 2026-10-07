package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
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
                build(content, d);
            }

            @Override
            public void fail(String faError) {
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
        String t = active.trim().toUpperCase();
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
}
