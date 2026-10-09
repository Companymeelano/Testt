package ir.meelano.manager.screens;

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
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/** Warehouse home (v28, phase 2): today's pulse + doors to the warehouse flows. */
public class AnbarScreen extends Screen {
    public AnbarScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "anbar"; }

    @Override
    public String title() { return "انبار"; }

    @Override
    public String glyph() { return "📦"; }

    @Override
    public int accent() { return Theme.GOLD; }

    @Override
    public void render(LinearLayout content) {
        content.removeAllViews();
        content.addView(topCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        final LinearLayout kpiBox = a.kit.v();
        content.addView(kpiBox, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.text("کارهای انبار", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(6));
        String[][] links = {
                {"tahvil", "تحویل فاکتور به مشتری و موزع + رسید"},
                {"resid", "رسید کالا: خرید و برگشتی‌ها (فقط تعداد)"},
                {"shomarsh", "شمارش قفسه و مغایرت‌گیری"},
                {"score", "امتیاز تأمین‌کنندگان"},
                {"products", "کالاها و موجودی"},
                {"customers", "مشتریان و تأمین‌کنندگان"},
        };
        for (String[] l : links) {
            final String lid = l[0];
            Screen s = a.screen(lid);
            if (s == null) continue;
            content.addView(a.kit.navRow(s.glyph(), s.title(), l[1], s.accent(), v -> a.nav(lid)),
                    a.kit.lp(-1, -2));
        }
        content.addView(a.kit.gap(12));
        final LinearLayout shortBox = a.kit.v();
        content.addView(shortBox, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.btnGhost("📊 گزارش پایان روز انبار", Theme.INFO, v -> eodReport()),
                a.kit.lp(-1, -2));
        loadShortage(shortBox);
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whTodayStats(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                Row r = (rows == null || rows.isEmpty()) ? null : rows.get(0);
                long n = r == null ? 0 : r.l("salesN");
                double sum = r == null ? 0 : r.d("salesSum");
                long pish = r == null ? 0 : r.l("pishN");
                List<Kit.Kpi> kpis = new ArrayList<>();
                kpis.add(new Kit.Kpi("فاکتور امروز", Money.fa(String.valueOf(n)), "فقره", Theme.GOLD));
                kpis.add(new Kit.Kpi("مبلغ امروز", Money.fa(Money.compact(sum)), "تومان", Theme.SUCCESS));
                kpis.add(new Kit.Kpi("پیش‌فاکتور خرید باز", Money.fa(String.valueOf(pish)), "سند", Theme.INFO));
                kpiBox.removeAllViews();
                kpiBox.addView(a.kit.kpiGrid(kpis, 3), a.kit.lp(-1, -2));
            }

            @Override
            public void fail(String faError) {
                kpiBox.removeAllViews();
                kpiBox.addView(a.kit.hint("آمار امروز در دسترس نیست (آفلاین؟)"), a.kit.lp(-1, -2));
            }
        });
    }

    // ================= shortage alerts + auto buy draft =================

    private List<Row> shortage = new ArrayList<>();

    private void loadShortage(final LinearLayout box) {
        box.addView(a.kit.text("⚠ کسری‌ها (موجودی ≤ نقطه سفارش)", 14f, Theme.TEXT, true),
                a.kit.lp(-1, -2));
        final LinearLayout list = a.kit.v();
        box.addView(list, a.kit.lp(-1, -2));
        list.addView(a.kit.hint("در حال بررسی…"), a.kit.lp(-1, -2));
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whShortage(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                shortage = rows == null ? new ArrayList<Row>() : rows;
                list.removeAllViews();
                if (shortage.isEmpty()) {
                    list.addView(a.kit.hint("✓ کسری ندارید؛ همه قلم‌ها بالای نقطه سفارش‌اند"),
                            a.kit.lp(-1, -2));
                    return;
                }
                for (Row r : shortage) {
                    list.addView(a.kit.kv(r.s("name"),
                            "موجودی " + Money.fa(fmtNum(r.d("stock"))) + " / سفارش از "
                                    + Money.fa(fmtNum(r.d("reopoint")))
                                    + (r.s("unit").isEmpty() ? "" : " " + r.s("unit")),
                            Theme.TEXT), a.kit.lp(-1, -2));
                }
                list.addView(a.kit.gap(6));
                list.addView(a.kit.btn("🧾 ساخت پیش‌نویس خرید از کسری‌ها",
                        v -> autoBuyDraft()), a.kit.lp(-1, -2));
            }

            @Override
            public void fail(String faError) {
                list.removeAllViews();
                list.addView(a.kit.hint("بررسی کسری ممکن نشد"), a.kit.lp(-1, -2));
            }
        });
    }

    private String fmtNum(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) return String.valueOf(Math.round(v));
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    /** Build a BUY draft prefilled with suggested order quantities. */
    private void autoBuyDraft() {
        if (shortage.isEmpty()) return;
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put(WarehouseWriter.D_TYPE, WarehouseWriter.BUY);
            d.put(WarehouseWriter.D_DATE, Jalali.todayStr());
            d.put(WarehouseWriter.D_PSHMO, "");
            d.put(WarehouseWriter.D_PNAME, "");
            d.put(WarehouseWriter.D_DESC, "پیشنهاد خودکار از کسری‌ها");
            String keeper = AtiranAuth.sessionName(a);
            if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
            d.put(WarehouseWriter.D_USER, keeper);
            org.json.JSONArray ls = new org.json.JSONArray();
            for (Row r : shortage) {
                double stock = r.d("stock");
                double re = r.d("reopoint");
                double sug = Math.max(re * 2 - stock, re);
                if (sug <= 0) sug = 1;
                org.json.JSONObject ln = new org.json.JSONObject();
                ln.put(WarehouseWriter.L_SHKA, r.s("code"));
                ln.put(WarehouseWriter.L_NAKA, r.s("name"));
                ln.put(WarehouseWriter.L_QTY, fmtNum(sug));
                ln.put(WarehouseWriter.L_UNIT, r.s("unit"));
                ls.put(ln);
            }
            d.put(WarehouseWriter.D_LINES, ls);
            int no = WarehouseWriter.saveDraft(a, d);
            a.kit.toast("پیش‌نویس خرید " + Money.fa(String.valueOf(no))
                    + " ساخته شد؛ تأمین‌کننده را انتخاب کنید");
            a.nav("resid");
        } catch (Exception e) {
            a.kit.toast("ساخت پیش‌نویس ممکن نشد");
        }
    }

    // ================= end-of-day report =================

    private void eodReport() {
        a.kit.toast("در حال ساخت گزارش…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whTodayDocs(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                Row r = (rows == null || rows.isEmpty()) ? null : rows.get(0);
                long buyN = r == null ? 0 : r.l("buyN");
                long retN = r == null ? 0 : r.l("retN");
                org.json.JSONArray log = WarehouseWriter.handlog(a);
                String today = Jalali.todayStr();
                List<Row> out = new ArrayList<>();
                int handN = 0;
                for (int i = log.length() - 1; i >= 0 && out.size() < 80; i--) {
                    org.json.JSONObject e = log.optJSONObject(i);
                    if (e == null || !today.equals(e.optString("dt", ""))) continue;
                    handN++;
                    Row o = new Row();
                    o.put("no", e.optString("no", ""));
                    o.put("cust", e.optString("cust", ""));
                    o.put("receiver", e.optString("receiver", ""));
                    out.add(o);
                }
                int drafts = WarehouseWriter.listDrafts(a).size();
                String sub = "تاریخ: " + today + "  •  پیش‌فاکتور خرید ثبت‌شده: "
                        + buyN + "  •  برگشتی ثبت‌شده: " + retN + "  •  تحویل‌ها: "
                        + handN + "  •  پیش‌نویس‌های باز: " + drafts;
                ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                        new ReportCatalog.Col("no", "فاکتور", ReportCatalog.T_TEXT),
                        new ReportCatalog.Col("cust", "مشتری", ReportCatalog.T_TEXT),
                        new ReportCatalog.Col("receiver", "تحویل به", ReportCatalog.T_TEXT),
                };
                a.sharePdf("گزارش پایان روز انبار", sub, cols, out);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private android.view.View topCard() {
        LinearLayout card = a.kit.card(Theme.GOLD);
        String keeper = AtiranAuth.sessionName(a);
        if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
        card.addView(a.kit.text("📦 انبار", 17f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String sub = "امروز " + Money.fa(Jalali.todayStr());
        if (!keeper.isEmpty()) sub += " • انباردار: " + keeper;
        card.addView(a.kit.text(sub, 12f, Theme.MUTED, false), a.kit.lp(-1, -2));
        return card;
    }
}
