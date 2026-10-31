package ir.meelano.manager.screens;

import android.view.View;
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
        content.addView(a.kit.gap(12));
        content.addView(a.kit.sectionHead("خلاصهٔ امروز", null, null), a.kit.lp(-1, -2));
        final LinearLayout kpiBox = a.kit.v();
        content.addView(kpiBox, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(14));

        content.addView(a.kit.sectionHead("عملیات پرکاربرد", "همه بخش‌ها", v -> a.nav("more")),
                a.kit.lp(-1, -2));
        String[][] links = {
                {"tahvil", "تحویل فاکتور به مشتری و موزع + رسید"},
                {"pick_wave", "برداشت دسته‌ای بر اساس موزع یا مسیر + اسکن"},
                {"sync_queue", "صف بادوام تحویل و شمارش؛ "
                        + ir.meelano.manager.core.Money.fa(String.valueOf(
                        ir.meelano.manager.core.WarehouseSync.pendingCount(a))) + " مورد منتظر"},
                {"resid", "رسید کالا و پیش‌نویس‌های انبار"},
                {"shomarsh", "شمارش کور و مغایرت‌گیری با تأیید مدیر"},
                {"score", "ارزیابی و امتیاز تأمین‌کنندگان"},
                {"products", "جست‌وجو و گردش موجودی کالا"},
                {"customers", "پرونده مشتریان و تأمین‌کنندگان"},
        };
        addActionGrid(content, links);
        content.addView(a.kit.gap(14));
        content.addView(a.kit.sectionHead("وضعیت موجودی", null, null), a.kit.lp(-1, -2));
        final LinearLayout shortBox = a.kit.v();
        content.addView(shortBox, a.kit.lp(-1, -2));
        loadShortage(shortBox);
        content.addView(a.kit.gap(8));
        content.addView(a.kit.btnGold("📊 گزارش پایان روز انبار", v -> eodReport()),
                a.kit.lp(-1, -2));
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
                kpis.add(new Kit.Kpi("مبلغ امروز", Money.compactMoney(sum), "", Theme.SUCCESS));
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

    /** Responsive two/three-column shortcuts for the warehouse workbench. */
    private void addActionGrid(LinearLayout parent, String[][] links) {
        int cols = a.kit.isWide() ? 3 : 2;
        LinearLayout grid = a.kit.v();
        LinearLayout row = null;
        int shown = 0;
        for (String[] link : links) {
            final String id = link[0];
            final String description = link[1];
            final Screen destination = a.screen(id);
            if (destination == null || !ir.meelano.manager.core.RoleStore.allowed(a, id)) continue;
            if (shown % cols == 0) {
                row = a.kit.h();
                LinearLayout.LayoutParams rp = a.kit.lp(-1, -2);
                if (shown > 0) rp.setMargins(0, Theme.dp(8), 0, 0);
                grid.addView(row, rp);
            } else {
                row.addView(a.kit.space(8));
            }
            row.addView(actionTile(destination, description), a.kit.wlp(1f));
            shown++;
        }
        if (shown % cols != 0 && row != null) row.addView(a.kit.space(8), a.kit.wlp(1f));
        if (shown == 0) grid.addView(a.kit.empty("بخشی برای این نقش در دسترس نیست", null), a.kit.lp(-1, -2));
        parent.addView(grid, a.kit.lp(-1, -2));
    }

    /** Large touch-friendly tile; the screen glyph is rendered from the shared vector icon set. */
    private View actionTile(final Screen destination, String description) {
        int accent = destination.accent();
        LinearLayout tile = a.kit.card(accent);
        tile.setGravity(android.view.Gravity.CENTER);
        tile.setMinimumHeight(Theme.dp(126));
        tile.setPadding(Theme.dp(10), Theme.dp(12), Theme.dp(10), Theme.dp(10));

        android.widget.TextView icon = a.kit.text(destination.glyph(), 27f, accent, true);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setBackground(Theme.avatar(Theme.alpha(accent, 48)));
        tile.addView(icon, new LinearLayout.LayoutParams(Theme.dp(46), Theme.dp(46)));
        tile.addView(a.kit.gap(5));

        android.widget.TextView title = a.kit.text(destination.title(), 13f, Theme.TEXT, true);
        title.setGravity(android.view.Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(title, a.kit.lp(-1, -2));

        android.widget.TextView detail = a.kit.text(description, 10f, Theme.MUTED, false);
        detail.setGravity(android.view.Gravity.CENTER);
        detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(detail, a.kit.lp(-1, -2));

        tile.setContentDescription(destination.title() + "؛ " + description + "؛ باز کردن");
        Theme.pressable(tile);
        tile.setOnClickListener(v -> a.nav(destination.id()));
        return tile;
    }

    // ================= shortage alerts + auto buy draft =================

    private List<Row> shortage = new ArrayList<>();

    private void loadShortage(final LinearLayout box) {
        box.removeAllViews();
        box.addView(a.kit.loading("در حال بررسی نقطه‌های سفارش…"), a.kit.lp(-1, -2));
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whShortage(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                shortage = rows == null ? new ArrayList<Row>() : rows;
                box.removeAllViews();
                final boolean clear = shortage.isEmpty();
                final int accent = clear ? Theme.SUCCESS : Theme.WARNING;
                LinearLayout card = a.kit.card(accent);

                LinearLayout head = a.kit.h();
                head.setGravity(android.view.Gravity.CENTER_VERTICAL);
                android.widget.TextView mark = a.kit.text(clear ? "✓" : "⚠", 21f, accent, true);
                mark.setGravity(android.view.Gravity.CENTER);
                mark.setBackground(Theme.avatar(Theme.alpha(accent, 48)));
                head.addView(mark, new LinearLayout.LayoutParams(Theme.dp(42), Theme.dp(42)));
                head.addView(a.kit.space(9));
                android.widget.TextView title = a.kit.text(
                        clear ? "وضعیت موجودی مطلوب" : "نیازمند سفارش مجدد",
                        14f, Theme.TEXT, true);
                title.setMaxLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                head.addView(title, a.kit.wlp(1f));
                if (!clear) {
                    head.addView(a.kit.pill(Money.fa(String.valueOf(shortage.size())) + " کالا", accent),
                            a.kit.lp(-2, -2));
                }
                card.addView(head, a.kit.lp(-1, -2));
                card.addView(a.kit.gap(6));

                if (clear) {
                    card.addView(a.kit.text("همهٔ اقلام بالاتر از نقطهٔ سفارش‌اند.",
                            12.5f, Theme.SUCCESS, false), a.kit.lp(-1, -2));
                } else {
                    for (Row r : shortage) {
                        String detail = "موجودی " + Money.fa(fmtNum(r.d("stock")))
                                + " • نقطه سفارش " + Money.fa(fmtNum(r.d("reopoint")))
                                + (r.s("unit").isEmpty() ? "" : " " + r.s("unit"));
                        card.addView(a.kit.kv(r.s("name"), detail, Theme.WARNING), a.kit.lp(-1, -2));
                    }
                    card.addView(a.kit.gap(8));
                    card.addView(a.kit.btnGold("🧾 ساخت پیش‌نویس خرید از کسری‌ها",
                            v -> autoBuyDraft()), a.kit.lp(-1, -2));
                }
                a.kit.addCard(box, card);
            }

            @Override
            public void fail(String faError) {
                box.removeAllViews();
                LinearLayout card = a.kit.card(Theme.DANGER);
                card.addView(a.kit.text("وضعیت موجودی دریافت نشد", 14f, Theme.TEXT, true),
                        a.kit.lp(-1, -2));
                card.addView(a.kit.hint(faError == null || faError.trim().isEmpty()
                        ? "ارتباط را بررسی و دوباره تلاش کنید." : faError), a.kit.lp(-1, -2));
                card.addView(a.kit.btnGhost("⇅ تلاش دوباره", Theme.DANGER,
                        v -> loadShortage(box)), a.kit.lp(-1, -2));
                a.kit.addCard(box, card);
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
        String keeper = AtiranAuth.sessionName(a);
        if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
        String sub = "امروز " + Money.fa(Jalali.todayStr());
        if (!keeper.isEmpty()) sub += " • انباردار: " + keeper;
        return a.kit.hero("📦", "میزکار انبار", sub, Theme.GOLD);
    }
}
