package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.WarehouseWriter;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cycle counting (v29): count the shelf, see the variance vs the system
 * live, save/share a count report. Adjustment posting stays a draft until
 * the vendor names the adjustment table.
 */
public class ShomarshScreen extends Screen {
    public ShomarshScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "shomarsh"; }

    @Override
    public String title() { return "شمارش انبار"; }

    @Override
    public String glyph() { return "🔢"; }

    @Override
    public int accent() { return Theme.VIOLET; }

    private LinearLayout content;
    private String anbarId = "";
    private String anbarName = "";
    private JSONArray lines = new JSONArray();

    @Override
    public void render(LinearLayout content) {
        this.content = content;
        content.removeAllViews();
        LinearLayout hero = a.kit.card(Theme.VIOLET);
        hero.addView(a.kit.text("🔢 شمارش انبار", 17f, Theme.TEXT, true), a.kit.lp(-1, -2));
        hero.addView(a.kit.text("بشمارید؛ مغایرت با سیستم را همان‌جا ببینید",
                12f, Theme.MUTED, false), a.kit.lp(-1, -2));
        content.addView(hero, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        LinearLayout ab = a.kit.card(Theme.STEEL);
        LinearLayout row = a.kit.h();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(a.kit.text("انبار: " + (anbarName.isEmpty() ? "— سراسری —" : anbarName),
                13f, Theme.TEXT, true), a.kit.wlp(1f));
        row.addView(a.kit.btnGhost("انتخاب", Theme.GOLD, v -> pickAnbar()), a.kit.lp(-2, -2));
        ab.addView(row, a.kit.lp(-1, -2));
        content.addView(ab, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        LinearLayout card = a.kit.card(Theme.SUCCESS);
        card.addView(a.kit.text("شمارش‌ها (" + Money.fa(String.valueOf(lines.length())) + ")",
                13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        for (int i = 0; i < lines.length(); i++) {
            final int idx = i;
            JSONObject ln = lines.optJSONObject(i);
            if (ln == null) continue;
            double counted = ln.optDouble(WarehouseWriter.L_COUNTED, 0);
            double stock = ln.optDouble(WarehouseWriter.L_STOCK, 0);
            double diff = counted - stock;
            String pill = Math.abs(diff) < 0.001 ? "✓" : Money.fa(fmtNum(diff));
            View v = a.kit.personRow(ln.optString(WarehouseWriter.L_NAKA, "—"),
                    "سیستم " + Money.fa(fmtNum(stock)) + " • شمرده " + Money.fa(fmtNum(counted)),
                    "مغایرت " + pill,
                    "✕", Math.abs(diff) < 0.001 ? Theme.SUCCESS : Theme.DANGER,
                    v2 -> {
                        JSONArray keep = new JSONArray();
                        for (int k = 0; k < lines.length(); k++) {
                            if (k != idx) keep.put(lines.optJSONObject(k));
                        }
                        lines = keep;
                        if (content != null) render(content);
                    });
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            card.addView(v, p);
        }
        card.addView(a.kit.btn("➕ افزودن شمارش", v -> addCount()), a.kit.lp(-1, -2));
        content.addView(card, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        LinearLayout act = a.kit.card(Theme.GOLD);
        act.addView(a.kit.btn("💾 ذخیره گزارش شمارش", v -> saveCount()), a.kit.lp(-1, -2));
        act.addView(a.kit.gap(6));
        act.addView(a.kit.btnGhost("🧾 گزارش PDF", Theme.INFO, v -> countPdf()), a.kit.lp(-1, -2));
        content.addView(act, a.kit.lp(-1, -2));
    }

    private String fmtNum(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) return String.valueOf(Math.round(v));
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private void pickAnbar() {
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whAnbars(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                if (rows == null || rows.isEmpty()) {
                    a.kit.toast("انباری یافت نشد");
                    return;
                }
                LinearLayout b = a.kit.v();
                b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
                final AlertDialog[] box = new AlertDialog[1];
                for (Row r : rows) {
                    final Row row = r;
                    b.addView(a.kit.btnGhost(row.s("name"), Theme.GOLD, v -> {
                        anbarId = row.s("id");
                        anbarName = row.s("name");
                        try {
                            box[0].dismiss();
                        } catch (Exception ignored) { }
                        if (content != null) render(content);
                    }), a.kit.lp(-1, -2));
                }
                box[0] = a.kit.dialog("انتخاب انبار", a.kit.scrollWrap(b, 420), true);
                box[0].show();
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private void addCount() {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        final EditText e = a.kit.edit("نام یا کد کالا…", "");
        b.addView(e, a.kit.lp(-1, -2));
        final LinearLayout results = a.kit.v();
        b.addView(results, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        final Runnable search = () -> {
            final String q = e.getText().toString();
            a.repo.run(c -> {
                Meta m = new Meta(c);
                return Repo.exec(c, MasterQueries.whProducts(m, q));
            }, new Repo.Cb<List<Row>>() {
                @Override
                public void ok(List<Row> rows) {
                    results.removeAllViews();
                    if (rows == null || rows.isEmpty()) {
                        results.addView(a.kit.hint("یافت نشد"), a.kit.lp(-1, -2));
                        return;
                    }
                    for (Row r : rows) {
                        final Row row = r;
                        String label = row.s("name") + " • سیستم: "
                                + Money.fa(fmtNum(row.d("stock")));
                        results.addView(a.kit.btnGhost(label, Theme.GOLD, v -> {
                            try {
                                box[0].dismiss();
                            } catch (Exception ignored) { }
                            countedDialog(row.s("code"), row.s("name"),
                                    row.s("unit"), row.d("stock"));
                        }), a.kit.lp(-1, -2));
                    }
                }

                @Override
                public void fail(String faError) {
                    a.kit.toast(faError);
                }
            });
        };
        b.addView(a.kit.btn("🔍 جستجو", v -> search.run()), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("انتخاب کالا", a.kit.scrollWrap(b, 440), true);
        box[0].show();
        search.run();
    }

    private void countedDialog(final String code, final String name, final String unit,
            final double stock) {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        b.addView(a.kit.kv("کالا", name.isEmpty() ? "—" : name, Theme.TEXT), a.kit.lp(-1, -2));
        b.addView(a.kit.kv("موجودی سیستم", Money.fa(fmtNum(stock)) + " " + unit, Theme.TEXT),
                a.kit.lp(-1, -2));
        final EditText e = a.kit.editNum("تعداد شمرده‌شده *", "");
        b.addView(e, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(a.kit.btn("ثبت شمارش", v -> {
            double c = 0;
            try {
                c = Double.parseDouble(Money.en(e.getText().toString()).trim());
            } catch (Exception ignored) { }
            try {
                JSONObject ln = new JSONObject();
                ln.put(WarehouseWriter.L_SHKA, code);
                ln.put(WarehouseWriter.L_NAKA, name);
                ln.put(WarehouseWriter.L_UNIT, unit == null ? "" : unit);
                ln.put(WarehouseWriter.L_STOCK, stock);
                ln.put(WarehouseWriter.L_COUNTED, c);
                lines.put(ln);
                try {
                    box[0].dismiss();
                } catch (Exception ignored) { }
                if (content != null) render(content);
                double diff = c - stock;
                if (Math.abs(diff) > 0.001)
                    a.kit.toast("⚠ مغایرت: " + Money.fa(fmtNum(diff)));
            } catch (Exception ex) {
                a.kit.toast("ثبت ممکن نشد");
            }
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("شمارش", b, true);
        box[0].show();
    }

    private void saveCount() {
        if (lines.length() == 0) {
            a.kit.toast("شمارشی ثبت نشده است");
            return;
        }
        try {
            JSONObject d = new JSONObject();
            d.put(WarehouseWriter.D_TYPE, WarehouseWriter.COUNT);
            d.put(WarehouseWriter.D_DATE, Jalali.todayStr());
            d.put(WarehouseWriter.D_ANBAR, anbarId);
            d.put(WarehouseWriter.D_ANBARNAME, anbarName);
            String keeper = AtiranAuth.sessionName(a);
            if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
            d.put(WarehouseWriter.D_USER, keeper);
            d.put(WarehouseWriter.D_LINES, new JSONArray(lines.toString()));
            int no = WarehouseWriter.saveDraft(a, d);
            a.kit.toast("گزارش شمارش " + Money.fa(String.valueOf(no)) + " ذخیره شد");
            lines = new JSONArray();
            if (content != null) render(content);
        } catch (Exception e) {
            a.kit.toast("ذخیره ممکن نشد");
        }
    }

    private void countPdf() {
        if (lines.length() == 0) {
            a.kit.toast("شمارشی ثبت نشده است");
            return;
        }
        try {
            String sub = "انبار: " + (anbarName.isEmpty() ? "سراسری" : anbarName)
                    + "  •  تاریخ: " + Jalali.todayStr();
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < lines.length(); i++) {
                JSONObject ln = lines.optJSONObject(i);
                if (ln == null) continue;
                double stock = ln.optDouble(WarehouseWriter.L_STOCK, 0);
                double counted = ln.optDouble(WarehouseWriter.L_COUNTED, 0);
                Row r = new Row();
                r.put("name", ln.optString(WarehouseWriter.L_NAKA, ""));
                r.put("stock", Money.fa(fmtNum(stock)));
                r.put("counted", Money.fa(fmtNum(counted)));
                r.put("diff", Money.fa(fmtNum(counted - stock)));
                rows.add(r);
            }
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("stock", "سیستم", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("counted", "شمرده", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("diff", "مغایرت", ReportCatalog.T_TEXT),
            };
            a.sharePdf("گزارش شمارش انبار", sub, cols, rows);
        } catch (Exception e) {
            a.kit.toast("ساخت گزارش ممکن نشد");
        }
    }
}
