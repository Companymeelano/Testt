package ir.meelano.manager.screens;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.CacheStore;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.PersianText;
import ir.meelano.manager.core.ProductStockStatus;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Products: catalogue, stock, low/zero alerts, group value, turnover dossier. */
public class ProductsScreen extends Screen {
    private final Filter filter = new Filter();
    private int tab;
    /** Reject stale async results after tab/filter/page changes. */
    private int loadGen;

    public ProductsScreen(MainActivity a) {
        super(a);
        filter.top = 50;
        filter.status = "active";
    }

    @Override
    public String id() { return "products"; }

    @Override
    public String title() { return "کالاها"; }

    @Override
    public String glyph() { return "▦"; }

    @Override
    public int accent() { return Theme.VIOLET; }

    @Override
    public Filter filter() { return filter; }

    @Override
    public FilterSheet.Config filterConfig() {
        if (tab == 2 || tab == 4) return null;
        FilterSheet.Config c = new FilterSheet.Config();
        boolean catalogue = tab == 0 || tab == 1;
        c.searchHint = "نام، کد یا شِکالا…";
        c.search = catalogue;
        c.range = tab == 3;
        c.warehouses = catalogue;
        c.kalaGroups = catalogue;
        if (tab == 0) {
            c.statusTitle = "وضعیت موجودی";
            c.status = new FilterSheet.Opt[]{
                    // FilterSheet adds «همه»; active is the safe default for the product catalogue.
                    new FilterSheet.Opt("active", "فعال"),
                    new FilterSheet.Opt("inactive", "غیرفعال"),
                    new FilterSheet.Opt("low", "کم‌موجودی"),
                    new FilterSheet.Opt("out", "ناموجود"),
                    new FilterSheet.Opt("ok", "موجودی سالم"),
                    new FilterSheet.Opt("reorder", "زیر نقطه سفارش"),
            };
            c.sortTitle = "مرتب‌سازی";
            c.sort = new FilterSheet.Opt[]{
                    new FilterSheet.Opt("", "نام کالا"),
                    new FilterSheet.Opt("stock_asc", "کمترین موجودی"),
                    new FilterSheet.Opt("stock_desc", "بیشترین موجودی"),
                    new FilterSheet.Opt("price_desc", "گران‌ترین"),
            };
        }
        return c;
    }

    /** Product-specific summary avoids repeating the date, connection and status line. */
    @Override
    protected View heroCard() {
        LinearLayout w = a.kit.v();
        String sub = tab == 3 ? "گردش فروش کالا" : tab == 2 ? "ارزش موجودی به تفکیک گروه"
                : tab == 4 ? "بدون فروش در ۹۰ روز" : "فهرست، جست‌وجو و وضعیت موجودی";
        w.addView(a.kit.hero(glyph(), title(), sub, accent()), a.kit.lp(-1, -2));
        if (filterConfig() != null) {
            w.addView(a.kit.gap(8));
            w.addView(a.kit.filterBar(productFilterSummary(), productFiltersActive(),
                    v -> a.openFilter()), a.kit.lp(-1, -2));
        }
        return w;
    }

    private boolean productFiltersActive() {
        if (tab == 0) {
            boolean statusChanged = filter.status != null && !filter.status.isEmpty()
                    && !"active".equals(filter.status);
            return statusChanged || (filter.search != null && !filter.search.trim().isEmpty())
                    || filter.kalaGroup >= 0 || filter.warehouse >= 0
                    || (filter.sort != null && !filter.sort.isEmpty());
        }
        if (tab == 1) return (filter.search != null && !filter.search.trim().isEmpty())
                || filter.kalaGroup >= 0 || filter.warehouse >= 0;
        return tab == 3 && filter.preset != filter.defaultPreset;
    }

    private String productFilterSummary() {
        List<String> labels = new ArrayList<>();
        if (tab == 3) {
            labels.add(filter.rangeFa());
        } else if (tab == 0 || tab == 1) {
            if (filter.search != null && !filter.search.trim().isEmpty())
                labels.add("«" + filter.search.trim() + "»");
            if (tab == 0 && filter.status != null && !filter.status.isEmpty()) {
                String status;
                switch (filter.status) {
                    case "active": status = "فعال"; break;
                    case "inactive": status = "غیرفعال"; break;
                    case "low": status = "کم‌موجودی"; break;
                    case "out": status = "ناموجود"; break;
                    case "ok": status = "موجودی سالم"; break;
                    case "reorder": status = "زیر نقطه سفارش"; break;
                    default: status = "وضعیت انتخابی"; break;
                }
                labels.add(status);
            } else if (tab == 1) {
                labels.add("کم‌موجودی");
            }
            if (filter.kalaGroup >= 0) labels.add("گروه انتخاب‌شده");
            if (filter.warehouse >= 0) labels.add("انبار انتخاب‌شده");
            if (tab == 0 && filter.sort != null && !filter.sort.isEmpty()) {
                String sort = "stock_asc".equals(filter.sort) ? "کمترین موجودی"
                        : "stock_desc".equals(filter.sort) ? "بیشترین موجودی"
                        : "price_desc".equals(filter.sort) ? "گران‌ترین" : "مرتب‌سازی انتخابی";
                labels.add(sort);
            }
        } else if (tab == 2) {
            labels.add("ارزش کل موجودی");
        } else {
            labels.add("۹۰ روز بدون فروش");
        }
        if (labels.isEmpty()) return "فهرست کالاها";
        StringBuilder summary = new StringBuilder();
        for (String label : labels) {
            if (summary.length() > 0) summary.append(" • ");
            summary.append(label);
        }
        return summary.toString();
    }

    /** Sticky search, explicit submit, clear, and barcode scan in one compact control. */
    private View productStickySearch(final LinearLayout content) {
        LinearLayout row = a.kit.h();
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(10), Theme.dp(7), Theme.dp(10), Theme.dp(7));
        row.setBackground(Theme.card());

        final EditText input = new EditText(a);
        input.setHint("نام، کد یا بارکد کالا…");
        input.setText(filter.search == null ? "" : filter.search);
        input.setTextSize(14f * Theme.fontScale());
        input.setTextColor(Theme.TEXT);
        input.setHintTextColor(Theme.MUTED);
        input.setTypeface(Theme.face(false));
        input.setBackground(Theme.searchBar());
        input.setPadding(Theme.dp(12), Theme.dp(8), Theme.dp(12), Theme.dp(8));
        input.setMinimumHeight(Theme.dp(48));
        input.setSingleLine(true);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setContentDescription("جست‌وجوی نام، کد یا بارکد کالا");
        row.addView(input, a.kit.wlp(1f));
        row.addView(a.kit.space(6));

        Button clear = a.kit.btnGhost("×", Theme.MUTED, v -> {
            input.setText("");
            filter.search = "";
            filter.page = 0;
            a.kit.hideKeyboard(input);
            a.refreshChrome();
            render(content);
        });
        clear.setContentDescription("پاک کردن جست‌وجو");
        clear.setVisibility(filter.search == null || filter.search.isEmpty() ? View.GONE : View.VISIBLE);
        row.addView(clear, a.kit.lp(-2, -2));
        row.addView(a.kit.space(6));

        Button search = a.kit.btn("⌕", v -> applyProductSearch(input, content));
        search.setContentDescription("اجرای جست‌وجوی کالا");
        row.addView(search, a.kit.lp(-2, -2));
        row.addView(a.kit.space(6));

        Button scan = a.kit.btnGhost("▦", Theme.VIOLET, v -> {
            a.kit.hideKeyboard(input);
            a.startScan(code -> {
                if (code == null || !id().equals(a.currentScreenId())) return;
                filter.search = code.trim();
                filter.page = 0;
                a.refreshChrome();
                render(content);
            });
        });
        scan.setContentDescription("اسکن بارکد کالا");
        row.addView(scan, a.kit.lp(-2, -2));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyProductSearch(input, content);
                return true;
            }
            return false;
        });
        return row;
    }

    private void applyProductSearch(EditText input, LinearLayout content) {
        filter.search = input.getText() == null ? "" : input.getText().toString().trim();
        filter.page = 0;
        a.kit.hideKeyboard(input);
        a.refreshChrome();
        render(content);
    }

    private static final class Data {
        int snapTab;
        String listFilterKey = "";
        long listTotal = -1;
        Row summary = new Row();
        List<Row> list = new ArrayList<>();
        List<Row> low = new ArrayList<>();
        List<Row> groups = new ArrayList<>();
        List<Row> top = new ArrayList<>();
        List<Row> dead = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        final int requestGen = ++loadGen;
        a.scrollProductsToTop();
        content.removeAllViews();
        a.setProductStickyBar(tab == 0 || tab == 1 ? productStickySearch(content) : null);
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.loading("در حال دریافت کالاها…"), a.kit.lp(-1, -2));
        final int myTab = tab;
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.snapTab = myTab;
            d.summary = soft(d.notes, "خلاصه", () -> Repo.one(c, MasterQueries.productsSummary(m)));
            if (myTab == 0) {
                d.listFilterKey = productFilterKey(f);
                d.list = soft(d.notes, "فهرست", () -> Repo.exec(c, MasterQueries.productsList(m, f)));
                Row total = soft(d.notes, "شمارش فهرست", () -> Repo.one(c, MasterQueries.productsCount(m, f)));
                if (total != null) d.listTotal = Math.max(0, total.l("total"));
            } else if (myTab == 1) {
                final Filter lf = f.copy();
                lf.status = "low";
                lf.sort = "stock_asc";
                lf.top = 300;
                lf.page = 0;
                d.low = soft(d.notes, "کم‌موجودی", () -> Repo.exec(c, MasterQueries.productsList(m, lf)));
            } else if (myTab == 2) {
                d.groups = soft(d.notes, "ارزش گروه‌ها", () -> Repo.exec(c, MasterQueries.stockValueByGroup(m)));
            } else if (myTab == 3) {
                d.top = soft(d.notes, "پرفروش‌ها", () -> Repo.exec(c, Queries.factorTopProducts(m, true, f, 50)));
            } else {
                d.dead = soft(d.notes, "راکد", () -> Repo.exec(c, MasterQueries.deadStock(m, 90, 100)));
            }
            if (d.summary == null && d.list == null) throw new Exception(firstNote(d.notes));
            return d;
        }, new Repo.Cb<Data>() {
            @Override
            public void ok(Data d) {
                if (!isCurrentRequest(requestGen)) return;
                saveCache(d);
                build(content, d);
            }

            @Override
            public void fail(String faError) {
                if (!isCurrentRequest(requestGen)) return;
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
                revealContent(content);
            }
        });
    }

    private boolean isCurrentRequest(int requestGen) {
        return requestGen == loadGen && id().equals(a.currentScreenId());
    }

    private void revealContent(LinearLayout content) {
        if (!ir.meelano.manager.ui.Ui.motionOk(a)) return;
        content.animate().cancel();
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private static String productFilterKey(Filter filter) {
        Filter f = filter == null ? new Filter() : filter;
        StringBuilder key = new StringBuilder();
        appendKeyPart(key, f.status);
        appendKeyPart(key, f.search);
        appendKeyPart(key, String.valueOf(f.kalaGroup));
        appendKeyPart(key, String.valueOf(f.warehouse));
        appendKeyPart(key, f.sort);
        appendKeyPart(key, String.valueOf(f.top));
        appendKeyPart(key, String.valueOf(f.page));
        return key.toString();
    }

    private static void appendKeyPart(StringBuilder key, String value) {
        String part = value == null ? "" : value;
        key.append(part.length()).append(':').append(part).append(';');
    }

    private String firstNote(Map<String, String> notes) {
        for (String v : notes.values()) return v;
        return "داده‌ای دریافت نشد";
    }

    private void saveCache(Data d) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("sum", d.summary);
        m.put("list", d.list);
        m.put("low", d.low);
        m.put("groups", d.groups);
        m.put("top", d.top);
        m.put("dead", d.dead);
        m.put("tab", d.snapTab);
        if (d.snapTab == 0) m.put("listFilterKey", d.listFilterKey);
        if (d.listTotal >= 0) m.put("total", d.listTotal);
        CacheStore.saveData(a, "cx_prod", cacheNow(), m);
    }

    private Data loadCache(String[] lab) {
        java.util.Map<String, Object> m = CacheStore.loadData(a, "cx_prod", lab);
        if (m == null) return null;
        Data d = new Data();
        d.summary = CacheStore.row(m, "sum");
        d.list = CacheStore.rows(m, "list");
        d.low = CacheStore.rows(m, "low");
        d.groups = CacheStore.rows(m, "groups");
        d.top = CacheStore.rows(m, "top");
        d.dead = CacheStore.rows(m, "dead");
        d.listTotal = m.containsKey("total") ? CacheStore.num(m, "total") : -1;
        d.listFilterKey = String.valueOf(m.get("listFilterKey") == null ? "" : m.get("listFilterKey"));
        d.snapTab = (int) CacheStore.num(m, "tab");
        if (d.snapTab != tab) return null;
        if (d.snapTab == 0 && !productFilterKey(filter).equals(d.listFilterKey)) return null;
        return d;
    }
    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        Row s = d.summary == null ? new Row() : d.summary;
        boolean activeKnown = s.i("activeKnown") != 0;
        boolean reorderKnown = s.i("reorderKnown") != 0;
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi(activeKnown ? "کالای فعال" : "اقلام",
                Money.num(activeKnown ? s.l("active") : s.l("count")), "نمایش فهرست",
                accent(), activeKnown ? () -> showListStatus(content, "active") : null));
        if (activeKnown) {
            long inactive = Math.max(0, s.l("count") - s.l("active"));
            kpis.add(new Kit.Kpi("کالای غیرفعال", Money.num(inactive), "نمایش فهرست",
                    Theme.MUTED, () -> showListStatus(content, "inactive")));
        }
        Kit.Kpi stockValue = new Kit.Kpi("ارزش موجودی", Money.compactRial(s.d("value")), "به قیمت خرید", Theme.GOLD);
        stockValue.fullValue = Money.rialText(s.d("value"));
        kpis.add(stockValue);
        kpis.add(new Kit.Kpi("ناموجود", Money.num(s.l("out")), "فهرست ناموجودها", Theme.DANGER,
                () -> showListStatus(content, "out")));
        kpis.add(new Kit.Kpi("کم‌موجودی", Money.num(s.l("low")), "فهرست کم‌موجودها", Theme.WARNING,
                () -> showListStatus(content, "low")));
        if (reorderKnown) kpis.add(new Kit.Kpi("نقطه سفارش", Money.num(s.l("reorderN")), "زیر نقطه سفارش", Theme.INFO,
                () -> showListStatus(content, "reorder")));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        if (!activeKnown) {
            content.addView(a.kit.hint("ستون وضعیت فعال در این دیتابیس موجود نیست؛ شمارش کلی به‌جای شمارش فعال نمایش داده می‌شود."), a.kit.lp(-1, -2));
        }
        content.addView(a.kit.gap(12));

        content.addView(a.kit.chips(new String[]{"فهرست", "کم‌موجودی", "ارزش گروه‌ها", "پرفروش‌ها", "راکد"}, d.snapTab, idx -> {
            tab = idx;
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        if (d.snapTab == 0) buildList(content, d);
        else if (d.snapTab == 1) buildLow(content, d);
        else if (d.snapTab == 2) buildGroups(content, d);
        else if (d.snapTab == 3) buildTop(content, d);
        else buildDead(content, d);

        renderNotes(content, d.notes);
        revealContent(content);
    }

    private void showListStatus(LinearLayout content, String status) {
        tab = 0;
        filter.status = status;
        filter.page = 0;
        render(content);
    }

    private void buildList(LinearLayout content, Data d) {
        List<Row> rows = d.list == null ? new ArrayList<>() : d.list;
        int pageSize = Queries.clampTop(filter.top);
        long pageOffset = (long) Math.max(0, filter.page) * pageSize;
        if (d.listTotal >= 0) {
            long first = rows.isEmpty() ? 0 : pageOffset + 1;
            long last = rows.isEmpty() ? 0 : Math.min(d.listTotal, pageOffset + rows.size());
            content.addView(a.kit.hint("نمایش " + Money.num(first) + " تا " + Money.num(last)
                    + " از " + Money.num(d.listTotal) + " کالا"), a.kit.lp(-1, -2));
        } else {
            content.addView(a.kit.hint(Money.num(rows.size()) + " کالا در این صفحه • شمارش کل در دسترس نیست"), a.kit.lp(-1, -2));
        }
        if (rows.isEmpty()) {
            boolean laterPage = d.listTotal > 0 && filter.page > 0;
            content.addView(a.kit.empty(laterPage ? "در این صفحه کالایی نیست" : "کالایی یافت نشد",
                    laterPage ? "صفحه قبل یا فیلتر دیگری را امتحان کنید" : "فیلتر را تغییر دهید"), a.kit.lp(-1, -2));
            if (filter.page > 0) content.addView(a.kit.pager(filter.page, d.listTotal, pageSize,
                    d.listTotal >= 0, false,
                    () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                    () -> { filter.page = filter.page + 1; render(content); }), a.kit.lp(-1, -2));
            return;
        }
        for (Row r : rows) {
            final String shka = r.s("shka");
            String name = PersianText.cleanDisplayLabel(r.s("naka"));
            if (name.isEmpty()) name = "بدون نام کالا";
            String unit = PersianText.cleanDisplayLabel(r.s("unit"));
            String group = PersianText.cleanGroupLabel(r.s("groupName"));
            String extra = group;
            if (!unit.isEmpty()) extra += (extra.isEmpty() ? "" : " • ") + unit;

            boolean warehouse = filter.warehouse >= 0;
            double majorQty = warehouse ? r.d("anbarQty") : r.d("vah");
            double looseQty = warehouse ? r.d("anbarJoz") : r.d("joz");
            double pieces = warehouse ? r.d("anbarPieces") : r.d("stockPieces");
            boolean stockKnown = warehouse ? (r.has("anbarQty") || r.has("anbarJoz"))
                    : (r.has("vah") || r.has("stockPieces"));
            String stock = stockKnown ? (warehouse ? "انبار " : "موجودی ") + Money.qtyText(majorQty)
                    : "موجودی نامشخص";
            if (stockKnown && !unit.isEmpty()) stock += " " + unit;
            if (stockKnown && Math.abs(looseQty) > 0.000001d)
                stock += " + " + Money.qtyText(looseQty) + " عدد";

            ProductStockStatus.Band band = ProductStockStatus.classify(stockKnown, majorQty, pieces,
                    r.d("reorderPoint"), AtiranSchema.LOW_STOCK_VAH);
            boolean activeKnownForRow = r.i("activeKnown") != 0;
            boolean inactive = activeKnownForRow && !ProductStockStatus.isActive(r.s("active"));
            String status = inactive ? "غیرفعال" : ProductStockStatus.label(band);
            int tone = inactive ? Theme.MUTED : stockTone(band);

            String code = PersianText.cleanDisplayLabel(r.s("code"));
            if (code.isEmpty() || "--".equals(code) || "—".equals(code) || "-".equals(code)) code = shka;
            boolean saleKnown = r.i("saleKnown") != 0;
            boolean priceWarning = !saleKnown || !r.has("sale") || r.d("sale") <= 0;
            String price;
            if (!saleKnown) price = "قیمت فروش نامشخص";
            else if (!r.has("sale") || r.d("sale") <= 0)
                price = r.d("sale") < 0 ? "قیمت فروش نامعتبر" : "قیمت فروش ثبت نشده";
            else price = Money.rialText(r.d("sale"));
            int priceTone = r.has("sale") && r.d("sale") < 0 ? Theme.DANGER
                    : priceWarning ? Theme.WARNING : Theme.GOLD_SOFT;

            View v = a.kit.invRowTone(name, "کد کالا " + Money.ltrIsolate(Money.fa(code)), extra, stock,
                    price, status, tone, priceTone, true, v2 -> openProduct(shka));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            content.addView(v, p);
        }
        boolean hasMore = d.listTotal >= 0
                ? pageOffset + rows.size() < d.listTotal
                : rows.size() >= pageSize;
        content.addView(a.kit.pager(filter.page, d.listTotal, pageSize, d.listTotal >= 0, hasMore,
                () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                () -> { if (hasMore) { filter.page = filter.page + 1; render(content); } }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        ReportCatalog.Col[] expCols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("naka", "کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("shka", "کد کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("groupName", "گروه", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("vah", "موجودی", ReportCatalog.T_NUM),
                new ReportCatalog.Col("sale", "قیمت فروش", ReportCatalog.T_MONEY),
        };
        content.addView(a.exportBar("فهرست کالاها", "", expCols, rows), a.kit.lp(-1, -2));
    }

    private int stockTone(ProductStockStatus.Band band) {
        switch (band) {
            case NEGATIVE:
            case OUT: return Theme.DANGER;
            case REORDER: return Theme.INFO;
            case LOW: return Theme.WARNING;
            case NORMAL: return Theme.SUCCESS;
            case UNKNOWN:
            default: return Theme.MUTED;
        }
    }

    private void buildLow(LinearLayout content, Data d) {
        if (d.summary != null && d.summary.l("reorderN") > 0) {
            LinearLayout rc = a.kit.card(Theme.WARNING);
            rc.addView(a.kit.kv("زیر نقطه سفارش", Money.fa(String.valueOf(d.summary.l("reorderN"))) + " قلم", Theme.WARNING), a.kit.lp(-1, -2));
            rc.addView(a.kit.hint("فهرست کامل را با فیلتر «زیر نقطه سفارش» در تب فهرست ببینید"), a.kit.lp(-1, -2));
            a.kit.addCard(content, rc);
        }
        if (d.low == null || d.low.isEmpty()) {
            content.addView(a.kit.empty("کالای کم‌موجودی وجود ندارد", "موجودی همه اقلام بالای آستانه است"), a.kit.lp(-1, -2));
            return;
        }
        final List<Row> chartRows = new ArrayList<>(d.low.subList(0, Math.min(10, d.low.size())));
        LinearLayout chartCard = a.kit.card(Theme.WARNING);
        chartCard.addView(a.kit.text("نمودار موجودی اقلام کم‌موجود", 13.5f, Theme.TEXT, true),
                a.kit.lp(-1, -2));
        chartCard.addView(a.kit.hint("برای باز کردن شناسنامه، یک نوار را لمس کنید"), a.kit.lp(-1, -2));
        Charts.HBars stockChart = new Charts.HBars(a);
        List<Charts.Point> stockPoints = new ArrayList<>();
        StringBuilder chartDescription = new StringBuilder("نمودار تعاملی موجودی کالاهای کم‌موجود. ");
        for (int i = 0; i < chartRows.size(); i++) {
            Row row = chartRows.get(i);
            String name = PersianText.cleanDisplayLabel(row.s("naka"));
            stockPoints.add(new Charts.Point(name, row.d("vah"),
                    row.d("vah") < 0 ? Theme.DANGER : Charts.palette(i)));
            if (i > 0) chartDescription.append("؛ ");
            chartDescription.append(name).append(": ").append(Money.qtyText(row.d("vah")));
        }
        stockChart.setData(stockPoints, Charts.COMPACT);
        stockChart.setContentDescription(chartDescription.toString());
        stockChart.setListener((idx, point) -> {
            if (idx >= 0 && idx < chartRows.size()) openProduct(chartRows.get(idx).s("shka"));
        });
        chartCard.addView(stockChart, a.kit.lp(-1, -2));
        a.kit.addCard(content, chartCard);

        int cap = Math.min(d.low.size(), 120);
        for (int i = 0; i < cap; i++) {
            Row r = d.low.get(i);
            final String shka = r.s("shka");
            boolean zero = r.d("vah") <= 0;
            View v = a.kit.alertRow(r.s("naka"), "کد " + Money.fa(shka) + " • موجودی " + Money.qty(r.d("vah")),
                    zero ? Theme.DANGER : Theme.WARNING, v2 -> openProduct(shka));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
        if (d.low.size() > cap)
            content.addView(a.kit.hint("+" + Money.fa(String.valueOf(d.low.size() - cap)) + " قلم دیگر…"), a.kit.lp(-1, -2));
    }

    private void buildGroups(LinearLayout content, Data d) {
        if (d.groups == null || d.groups.isEmpty()) {
            content.addView(a.kit.empty("ارزش گروهی قابل محاسبه نیست", null), a.kit.lp(-1, -2));
            return;
        }
        LinearLayout c = a.kit.card(accent());
        c.addView(a.kit.text("ارزش موجودی به تفکیک گروه", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.Donut dn = new Charts.Donut(a);
        List<Charts.Point> pts = new ArrayList<>();
        double positiveTotal = 0;
        boolean hasNegative = false;
        for (Row row : d.groups) {
            double value = row.d("value");
            if (value < 0) {
                hasNegative = true;
                continue;
            }
            if (value <= 0) continue;
            String label = PersianText.cleanGroupLabel(row.s("label"));
            if (label.isEmpty()) label = "بدون گروه";
            pts.add(new Charts.Point(label, value, Charts.palette(pts.size())));
            positiveTotal += value;
        }
        dn.setData(pts, hasNegative ? "ارزش مثبت" : "ارزش کل", Money.compactRial(positiveTotal));
        dn.setContentDescription(groupDonutDescription(pts, positiveTotal, hasNegative));
        dn.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        c.addView(dn, new LinearLayout.LayoutParams(-1, Theme.dp(300)));
        if (hasNegative) {
            c.addView(a.kit.hint("گروه‌های با ارزش منفی از سهم‌های نمودار کنار گذاشته شده‌اند؛ مقدار دقیق همه گروه‌ها در جدول آمده است."),
                    a.kit.lp(-1, -2));
        }
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("label", "گروه", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("value", "ارزش", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("count", "اقلام", ReportCatalog.T_NUM),
        };
        c.addView(a.kit.dataTable(cols, d.groups, null), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    private String groupDonutDescription(List<Charts.Point> points, double total, boolean hasNegative) {
        StringBuilder description = new StringBuilder("سهم مثبت ارزش موجودی به تفکیک گروه. ");
        if (points.isEmpty()) {
            description.append("مقدار مثبتی برای نمایش نیست.");
        } else {
            List<Charts.Point> sorted = new ArrayList<>(points);
            java.util.Collections.sort(sorted, (left, right) -> Double.compare(right.value, left.value));
            int top = Math.min(7, sorted.size());
            for (int i = 0; i < top; i++) {
                Charts.Point point = sorted.get(i);
                if (i > 0) description.append("؛ ");
                description.append(point.label).append(": ").append(Money.rialText(point.value))
                        .append("، ").append(Money.pct(total > 0 ? point.value * 100.0 / total : 0));
            }
            double rest = 0;
            for (int i = top; i < sorted.size(); i++) rest += sorted.get(i).value;
            if (rest > 0.5) {
                if (top > 0) description.append("؛ ");
                description.append("سایر: ").append(Money.rialText(rest))
                        .append("، ").append(Money.pct(total > 0 ? rest * 100.0 / total : 0));
            }
        }
        description.append(". مجموع سهم‌های مثبت: ").append(Money.rialText(total)).append(".");
        if (hasNegative) description.append(" گروه‌های منفی در جدول زیر دیده می‌شوند، نه در دونات.");
        return description.toString();
    }

    private void buildTop(LinearLayout content, Data d) {
        if (d.top == null || d.top.isEmpty()) {
            content.addView(a.kit.empty("فروشی در این بازه ثبت نشده است", null), a.kit.lp(-1, -2));
            return;
        }
        LinearLayout c = a.kit.card(Theme.GOLD);
        c.addView(a.kit.text("پرفروش‌ترین کالاها", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.HBars hb = new Charts.HBars(a);
        List<Charts.Point> pts = new ArrayList<>();
        for (int i = 0; i < Math.min(12, d.top.size()); i++)
            pts.add(new Charts.Point(d.top.get(i).s("label"), d.top.get(i).d("total")));
        hb.setData(pts, Charts.COMPACT);
        c.addView(hb, a.kit.lp(-1, -2));
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("label", "کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("total", "مبلغ", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("qty", "مقدار", ReportCatalog.T_NUM),
        };
        c.addView(a.kit.dataTable(cols, d.top, null), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    private void buildDead(LinearLayout content, Data d) {
        if (d.dead == null || d.dead.isEmpty()) {
            content.addView(a.kit.empty("کالای راکدی وجود ندارد", "همه اقلام موجود در ۹۰ روز گذشته فروش داشته‌اند"), a.kit.lp(-1, -2));
            return;
        }
        double stuck = 0;
        for (Row r : d.dead) stuck += r.d("buyValue");
        LinearLayout c = a.kit.card(Theme.WARNING);
        c.addView(a.kit.kv("اقلام راکد (۹۰ روز بدون فروش)", Money.fa(String.valueOf(d.dead.size())) + " قلم", Theme.TEXT), a.kit.lp(-1, -2));
        c.addView(a.kit.kv("سرمایه خوابیده ≈", Money.rial(stuck), Theme.WARNING), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
        int cap = Math.min(d.dead.size(), 120);
        for (int i = 0; i < cap; i++) {
            Row r = d.dead.get(i);
            final String shka = r.s("shka");
            String last = r.s("lastSale");
            String sub = "کد " + Money.fa(shka) + " • موجودی " + Money.qty(r.d("vah"))
                    + (last.isEmpty() ? " • هرگز فروش نرفته" : " • آخرین فروش " + Jalali.shortLabel(last));
            View v = a.kit.alertRow(r.s("label"), sub, last.isEmpty() ? Theme.DANGER : Theme.WARNING, v2 -> openProduct(shka));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
        if (d.dead.size() > cap)
            content.addView(a.kit.hint("+" + Money.fa(String.valueOf(d.dead.size() - cap)) + " قلم دیگر…"), a.kit.lp(-1, -2));
    }

    // ---------------- product dossier ----------------
    public void openProduct(final String shka) {
        ProductFileScreen.open(a, shka);
    }
}
