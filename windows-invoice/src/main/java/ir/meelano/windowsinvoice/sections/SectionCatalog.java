package ir.meelano.windowsinvoice.sections;

import ir.meelano.windowsinvoice.WindowsRoleMenus;
import ir.meelano.windowsinvoice.data.AtiranRoles;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionVariant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Manager section catalog for the Windows client: which sections exist on the desktop, which
 * reports each one offers, and the exact columns, derived columns and KPI chips of every report.
 *
 * The catalog is the single source of truth shared by three callers: the SQL gateway (it selects the
 * columns in this order), the demo gateway (it produces the same columns from sample data) and the
 * desktop window (it builds the table, chips and filters from it). Column keys are unique per report.
 */
public final class SectionCatalog {
    private SectionCatalog() { }

    /** Sections that are implemented on the desktop; every one of them is read-only. */
    public static final List<String> DESKTOP_SECTIONS = List.of(
            WindowsRoleMenus.STOCK_LEDGER, WindowsRoleMenus.CASH, WindowsRoleMenus.CHEQUES,
            WindowsRoleMenus.REPORTS, WindowsRoleMenus.PROFIT, WindowsRoleMenus.PRODUCTION,
            WindowsRoleMenus.PERSONNEL, WindowsRoleMenus.USERS);

    /** How a derived column is computed from other columns of the same row. */
    public enum Derivation {
        NONE, AGEING_BUCKET, CHEQUE_DUE_STATE, MARGIN_PERCENT, NET_AMOUNT, STOCK_STATE,
        RATE_PERCENT, DAYS_AGO, DESKTOP_ROLE, MENU_COUNT, MENU_SCOPE
    }

    public enum KpiOp {
        COUNT, COUNT_NONZERO, SUM, AVG, MAX, SUM_DIFF, RATIO_PERCENT, MARGIN_OF, COUNT_BELOW, COUNT_MATCHES
    }

    /** One KPI chip definition; {@code match} is only used by COUNT_MATCHES. */
    public record Kpi(String label, KpiOp op, String columnKey, String secondKey, String match, String hint) {
        public Kpi {
            label = clean(label);
            columnKey = clean(columnKey);
            secondKey = clean(secondKey);
            match = clean(match);
            hint = clean(hint);
            op = op == null ? KpiOp.COUNT : op;
        }

        public static Kpi count(String label) { return new Kpi(label, KpiOp.COUNT, "", "", "", ""); }
        public static Kpi count(String label, String hint) { return new Kpi(label, KpiOp.COUNT, "", "", "", hint); }
        public static Kpi sum(String label, String columnKey) { return new Kpi(label, KpiOp.SUM, columnKey, "", "", ""); }
        public static Kpi sum(String label, String columnKey, String hint) {
            return new Kpi(label, KpiOp.SUM, columnKey, "", "", hint);
        }
        public static Kpi avg(String label, String columnKey) { return new Kpi(label, KpiOp.AVG, columnKey, "", "", ""); }
        public static Kpi max(String label, String columnKey) { return new Kpi(label, KpiOp.MAX, columnKey, "", "", ""); }
        /** positiveKey − negativeKey, e.g. receipts − payments. */
        public static Kpi diff(String label, String positiveKey, String negativeKey) {
            return new Kpi(label, KpiOp.SUM_DIFF, positiveKey, negativeKey, "", "");
        }
        /** part / whole × 100 over the whole page, e.g. collected / sales. */
        public static Kpi ratio(String label, String partKey, String wholeKey) {
            return new Kpi(label, KpiOp.RATIO_PERCENT, partKey, wholeKey, "", "");
        }
        /** (sales − cost) / sales × 100 over the whole page. */
        public static Kpi margin(String label, String salesKey, String costKey) {
            return new Kpi(label, KpiOp.MARGIN_OF, salesKey, costKey, "", "");
        }
        public static Kpi matches(String label, String columnKey, String value) {
            return new Kpi(label, KpiOp.COUNT_MATCHES, columnKey, "", value, "");
        }
        public static Kpi below(String label, String columnKey, String hint) {
            return new Kpi(label, KpiOp.COUNT_BELOW, columnKey, "", "", hint);
        }
    }

    /** A column whose value is computed in Java from other columns of the same row. */
    public record Derived(String columnKey, Derivation kind, List<String> sources, BigDecimal threshold) {
        public Derived {
            columnKey = clean(columnKey);
            kind = kind == null ? Derivation.NONE : kind;
            sources = sources == null ? List.of() : List.copyOf(sources);
        }

        public static Derived of(String columnKey, Derivation kind, String... sources) {
            return new Derived(columnKey, kind, List.of(sources), null);
        }
    }

    /** Everything needed to render and to query one report. Immutable; built with {@link Builder}. */
    public static final class VariantSpec {
        private final String sectionId;
        private final String id;
        private final String label;
        private final String hint;
        private final List<SectionColumn> columns;
        private final List<Kpi> kpis;
        private final List<Derived> derived;
        private final String dateKey;
        private final String sortKey;
        private final boolean sortDescending;
        private final int maxRows;
        private final int defaultDays;
        private final BigDecimal defaultThreshold;
        private final String queryPrompt;
        private final String contextPrompt;
        private final boolean supportsDateRange;
        private final boolean supportsDays;
        private final boolean supportsThreshold;
        private final boolean dueWindow;

        private VariantSpec(Builder builder) {
            this.sectionId = builder.sectionId;
            this.id = builder.id;
            this.label = builder.label;
            this.hint = builder.hint;
            this.columns = List.copyOf(builder.columns);
            this.kpis = List.copyOf(builder.kpis);
            this.derived = List.copyOf(builder.derived);
            this.dateKey = builder.dateKey;
            this.sortKey = builder.sortKey;
            this.sortDescending = builder.sortDescending;
            this.maxRows = Math.max(20, Math.min(20000, builder.maxRows));
            this.defaultDays = Math.max(0, builder.defaultDays);
            this.defaultThreshold = builder.defaultThreshold;
            this.queryPrompt = builder.queryPrompt;
            this.contextPrompt = builder.contextPrompt;
            this.supportsDateRange = builder.supportsDateRange;
            this.supportsDays = builder.supportsDays;
            this.supportsThreshold = builder.supportsThreshold;
            this.dueWindow = builder.dueWindow;
            List<String> keys = new ArrayList<>();
            for (SectionColumn column : columns) {
                if (keys.contains(column.key()))
                    throw new IllegalStateException("کلید ستون تکراری در گزارش " + id + ": " + column.key());
                keys.add(column.key());
            }
            for (Derived item : derived) {
                if (!keys.contains(item.columnKey()))
                    throw new IllegalStateException("ستون مشتق‌شدهٔ ناموجود در گزارش " + id + ": " + item.columnKey());
            }
        }

        public String sectionId() { return sectionId; }
        public String id() { return id; }
        public String label() { return label; }
        public String hint() { return hint; }
        public List<SectionColumn> columns() { return columns; }
        public List<Kpi> kpis() { return kpis; }
        public List<Derived> derived() { return derived; }
        public String dateKey() { return dateKey; }
        public String sortKey() { return sortKey; }
        public boolean sortDescending() { return sortDescending; }
        public int maxRows() { return maxRows; }
        public int defaultDays() { return defaultDays; }
        public BigDecimal defaultThreshold() { return defaultThreshold; }
        public String queryPrompt() { return queryPrompt; }
        public String contextPrompt() { return contextPrompt; }
        public boolean supportsDateRange() { return supportsDateRange; }
        public boolean supportsDays() { return supportsDays; }
        public boolean supportsThreshold() { return supportsThreshold; }
        /** True when the day window is applied to the due date ahead of today (cheque collection list). */
        public boolean dueWindow() { return dueWindow; }

        public int columnIndex(String key) {
            for (int i = 0; i < columns.size(); i++) if (columns.get(i).key().equals(key)) return i;
            return -1;
        }

        public boolean isDerived(String key) {
            for (Derived item : derived) if (item.columnKey().equals(key)) return true;
            return false;
        }

        public SectionVariant asVariant() { return new SectionVariant(id, label, hint); }

        public static Builder builder(String sectionId, String id, String label) {
            return new Builder(sectionId, id, label);
        }

        /** Fluent builder; every report is declared once, in one readable place. */
        public static final class Builder {
            private final String sectionId;
            private final String id;
            private final String label;
            private String hint = "";
            private final List<SectionColumn> columns = new ArrayList<>();
            private final List<Kpi> kpis = new ArrayList<>();
            private final List<Derived> derived = new ArrayList<>();
            private String dateKey = "";
            private String sortKey = "";
            private boolean sortDescending = true;
            private int maxRows = 2000;
            private int defaultDays;
            private BigDecimal defaultThreshold;
            private String queryPrompt = "";
            private String contextPrompt = "";
            private boolean supportsDateRange = true;
            private boolean supportsDays;
            private boolean supportsThreshold;
            private boolean dueWindow;

            private Builder(String sectionId, String id, String label) {
                this.sectionId = sectionId;
                this.id = id;
                this.label = label;
            }

            public Builder hint(String value) { this.hint = value == null ? "" : value; return this; }
            public Builder column(SectionColumn column) { this.columns.add(column); return this; }
            public Builder columns(SectionColumn... values) { Collections.addAll(this.columns, values); return this; }
            public Builder kpi(Kpi value) { this.kpis.add(value); return this; }
            public Builder kpis(Kpi... values) { Collections.addAll(this.kpis, values); return this; }
            public Builder derived(Derived value) { this.derived.add(value); return this; }
            public Builder derived(Derived... values) { Collections.addAll(this.derived, values); return this; }
            public Builder dateKey(String value) { this.dateKey = value == null ? "" : value; return this; }
            public Builder sort(String key, boolean descending) {
                this.sortKey = key == null ? "" : key;
                this.sortDescending = descending;
                return this;
            }
            public Builder maxRows(int value) { this.maxRows = value; return this; }
            public Builder defaultDays(int value) {
                this.defaultDays = value;
                this.supportsDays = value > 0;
                return this;
            }
            public Builder defaultThreshold(String percent) {
                this.defaultThreshold = percent == null ? null : new BigDecimal(percent);
                this.supportsThreshold = this.defaultThreshold != null;
                return this;
            }
            public Builder query(String prompt) {
                this.queryPrompt = prompt == null ? "" : prompt;
                return this;
            }
            public Builder context(String prompt) {
                this.contextPrompt = prompt == null ? "" : prompt;
                return this;
            }
            public Builder noDateRange() { this.supportsDateRange = false; return this; }
            public Builder dueWindow() { this.dueWindow = true; this.supportsDays = true; return this; }

            public VariantSpec build() { return new VariantSpec(this); }
        }
    }

    /** A desktop section: menu id, Persian title, one-line description and its reports. */
    public record SectionSpec(String id, String title, String caption, List<SectionVariant> variants) {
        public SectionSpec {
            id = clean(id);
            title = clean(title);
            caption = clean(caption);
            variants = variants == null ? List.of() : List.copyOf(variants);
        }

        public String defaultVariant() { return variants.isEmpty() ? "" : variants.get(0).id(); }
    }

    private static final Map<String, List<VariantSpec>> VARIANTS = new LinkedHashMap<>();
    private static final Map<String, String> TITLES = new LinkedHashMap<>();
    private static final Map<String, String> CAPTIONS = new LinkedHashMap<>();

    static {
        declareCash();
        declareCheques();
        declareStock();
        declareReports();
        declareProfit();
        declareProduction();
        declarePersonnel();
        declareUsers();
    }

    // ------------------------------------------------------------------ صندوق و دریافتی‌ها

    private static void declareCash() {
        String section = WindowsRoleMenus.CASH;
        List<VariantSpec> list = new ArrayList<>();
        list.add(VariantSpec.builder(section, "daily", "خلاصهٔ روزانهٔ صندوق")
                .hint("جمع دریافتی و پرداختی هر روز از قبوض آتیران (dbo.dar).")
                .columns(SectionColumn.date("date", "تاریخ", 0.9),
                        SectionColumn.number("receipts_count", "تعداد دریافت", 0.8),
                        SectionColumn.money("receipts", "جمع دریافت", 1.2),
                        SectionColumn.number("payments_count", "تعداد پرداخت", 0.8),
                        SectionColumn.money("payments", "جمع پرداخت", 1.2),
                        SectionColumn.money("net", "خالص روز", 1.2))
                .derived(Derived.of("net", Derivation.NET_AMOUNT, "receipts", "payments"))
                .kpis(Kpi.sum("جمع دریافتی دوره", "receipts"), Kpi.sum("جمع پرداختی دوره", "payments"),
                        Kpi.diff("خالص صندوق", "receipts", "payments"), Kpi.max("بیشترین دریافت روز", "receipts"),
                        Kpi.count("روزهای دارای گردش"))
                .dateKey("date").sort("date", true).maxRows(400).defaultDays(62)
                .build());
        list.add(VariantSpec.builder(section, "receipts", "قبوض دریافت")
                .hint("فهرست قبوض دریافت با طرف حساب و روش دریافت؛ فقط‌خواندنی.")
                .columns(SectionColumn.text("doc", "شمارهٔ قبض", 0.8),
                        SectionColumn.date("date", "تاریخ", 0.9),
                        SectionColumn.text("party", "طرف حساب", 1.6),
                        SectionColumn.status("method", "روش دریافت", 1.0, "settlement"),
                        SectionColumn.money("amount", "مبلغ", 1.2),
                        SectionColumn.text("note", "شرح", 1.8))
                .kpis(Kpi.count("تعداد قبض"), Kpi.sum("جمع دریافتی", "amount"),
                        Kpi.avg("میانگین هر قبض", "amount"), Kpi.matches("قبوض چکی", "method", "چک"))
                .dateKey("date").sort("date", true).maxRows(3000)
                .query("شمارهٔ قبض، طرف حساب یا شرح…")
                .build());
        list.add(VariantSpec.builder(section, "payments", "قبوض پرداخت")
                .hint("فهرست قبوض پرداخت با طرف حساب و روش پرداخت.")
                .columns(SectionColumn.text("doc", "شمارهٔ قبض", 0.8),
                        SectionColumn.date("date", "تاریخ", 0.9),
                        SectionColumn.text("party", "طرف حساب", 1.6),
                        SectionColumn.status("method", "روش پرداخت", 1.0, "settlement"),
                        SectionColumn.money("amount", "مبلغ", 1.2),
                        SectionColumn.text("note", "شرح", 1.8))
                .kpis(Kpi.count("تعداد قبض"), Kpi.sum("جمع پرداختی", "amount"),
                        Kpi.avg("میانگین هر قبض", "amount"))
                .dateKey("date").sort("date", true).maxRows(3000)
                .query("شمارهٔ قبض، طرف حساب یا شرح…")
                .build());
        list.add(VariantSpec.builder(section, "banks", "گردش بانک‌ها")
                .hint("واریز و برداشت هر حساب بانکی در بازهٔ انتخابی (dbo.ban_act).")
                .columns(SectionColumn.text("bank", "حساب بانکی", 1.8),
                        SectionColumn.number("deposits_count", "تعداد واریز", 0.8),
                        SectionColumn.money("deposits", "واریزی", 1.2),
                        SectionColumn.number("withdrawals_count", "تعداد برداشت", 0.8),
                        SectionColumn.money("withdrawals", "برداشت", 1.2),
                        SectionColumn.money("balance", "ماندهٔ دوره", 1.2))
                .derived(Derived.of("balance", Derivation.NET_AMOUNT, "deposits", "withdrawals"))
                .kpis(Kpi.sum("جمع واریزی", "deposits"), Kpi.sum("جمع برداشت", "withdrawals"),
                        Kpi.diff("خالص بانک‌ها", "deposits", "withdrawals"), Kpi.count("حساب‌های دارای گردش"))
                .sort("deposits", true).maxRows(200).defaultDays(62)
                .query("نام بانک یا شمارهٔ حساب…")
                .build());
        register(section, "صندوق و دریافتی‌ها",
                "گردش نقد، قبوض دریافت و پرداخت و بانک‌ها · فقط‌خواندنی", list);
    }

    // ------------------------------------------------------------------ چک‌ها

    private static void declareCheques() {
        String section = WindowsRoleMenus.CHEQUES;
        List<VariantSpec> list = new ArrayList<>();
        list.add(VariantSpec.builder(section, "incoming", "چک‌های دریافتی")
                .hint("چک‌های دریافتی جاری و سنوات قبل با وضعیت آتیران (۱ صندوق، ۲ بانک، ۳ وصول، ۴ استرداد، ۵ خرج‌شده).")
                .columns(SectionColumn.text("num", "شمارهٔ چک", 0.9),
                        SectionColumn.text("sayad", "شناسهٔ صیاد", 1.0),
                        SectionColumn.text("party", "طرف حساب", 1.6),
                        SectionColumn.text("bank", "بانک", 1.2),
                        SectionColumn.money("amount", "مبلغ", 1.2),
                        SectionColumn.date("issue_date", "تاریخ دریافت", 0.9),
                        SectionColumn.date("due_date", "سررسید", 0.9),
                        SectionColumn.status("status", "وضعیت", 1.0, "cheque_in"),
                        SectionColumn.status("bounced", "برگشتی", 0.8, "bounced"),
                        SectionColumn.text("due_state", "وضعیت سررسید", 1.3))
                .derived(Derived.of("due_state", Derivation.CHEQUE_DUE_STATE, "due_date"))
                .kpis(Kpi.count("تعداد چک"), Kpi.sum("جمع مبلغ", "amount"),
                        Kpi.matches("برگشتی‌ها", "bounced", "برگشتی"),
                        Kpi.matches("نزد صندوق", "status", "نزد صندوق"))
                .dateKey("issue_date").sort("due_date", false).maxRows(2000)
                .query("شمارهٔ چک، شناسهٔ صیاد، بانک یا طرف حساب…")
                .build());
        list.add(VariantSpec.builder(section, "outgoing", "چک‌های پرداختی")
                .hint("چک‌های پرداختی با وضعیت آتیران (۰ سفید، ۱ جاری، ۲ پاس‌شده).")
                .columns(SectionColumn.text("num", "شمارهٔ چک", 0.9),
                        SectionColumn.text("sayad", "شناسهٔ صیاد", 1.0),
                        SectionColumn.text("party", "طرف حساب", 1.6),
                        SectionColumn.text("bank", "بانک/گیرنده", 1.2),
                        SectionColumn.money("amount", "مبلغ", 1.2),
                        SectionColumn.date("issue_date", "تاریخ صدور", 0.9),
                        SectionColumn.date("due_date", "سررسید", 0.9),
                        SectionColumn.status("status", "وضعیت", 1.0, "cheque_out"),
                        SectionColumn.text("due_state", "وضعیت سررسید", 1.3))
                .derived(Derived.of("due_state", Derivation.CHEQUE_DUE_STATE, "due_date"))
                .kpis(Kpi.count("تعداد چک"), Kpi.sum("جمع مبلغ", "amount"),
                        Kpi.matches("جاری", "status", "جاری"), Kpi.matches("پاس‌شده", "status", "پاس‌شده"))
                .dateKey("issue_date").sort("due_date", false).maxRows(2000)
                .query("شمارهٔ چک، شناسهٔ صیاد یا طرف حساب…")
                .build());
        list.add(VariantSpec.builder(section, "due", "سررسیدهای پیش‌رو")
                .hint("چک‌های دریافتی که سررسیدشان در بازهٔ روزهای انتخابی می‌رسد.")
                .columns(SectionColumn.text("num", "شمارهٔ چک", 0.9),
                        SectionColumn.text("party", "طرف حساب", 1.7),
                        SectionColumn.text("bank", "بانک", 1.2),
                        SectionColumn.money("amount", "مبلغ", 1.2),
                        SectionColumn.date("due_date", "سررسید", 0.9),
                        SectionColumn.text("due_state", "فاصله تا سررسید", 1.4),
                        SectionColumn.status("status", "وضعیت", 1.0, "cheque_in"))
                .derived(Derived.of("due_state", Derivation.CHEQUE_DUE_STATE, "due_date"))
                .kpis(Kpi.count("چک‌های در انتظار وصول"), Kpi.sum("جمع مبلغ", "amount"),
                        Kpi.matches("سررسید گذشته", "due_state", "سررسید گذشته"))
                .sort("due_date", false).maxRows(1000).defaultDays(30).dueWindow()
                .query("شمارهٔ چک یا طرف حساب…")
                .build());
        list.add(VariantSpec.builder(section, "bounced", "چک‌های برگشتی")
                .hint("چک‌های دریافتی با نشانهٔ برگشتی در آتیران (back='t').")
                .columns(SectionColumn.text("num", "شمارهٔ چک", 0.9),
                        SectionColumn.text("party", "طرف حساب", 1.7),
                        SectionColumn.text("bank", "بانک", 1.2),
                        SectionColumn.money("amount", "مبلغ", 1.2),
                        SectionColumn.date("due_date", "سررسید", 0.9),
                        SectionColumn.text("overdue", "مدت از سررسید", 1.2),
                        SectionColumn.status("status", "وضعیت", 1.0, "cheque_in"))
                .derived(Derived.of("overdue", Derivation.DAYS_AGO, "due_date"))
                .kpis(Kpi.count("تعداد برگشتی"), Kpi.sum("جمع مبلغ برگشتی", "amount"),
                        Kpi.avg("میانگین مبلغ", "amount"))
                .sort("amount", true).maxRows(1000)
                .query("شمارهٔ چک یا طرف حساب…")
                .build());
        register(section, "چک‌های دریافتی و پرداختی",
                "چک‌های جاری و سنوات قبل، سررسیدها و برگشتی‌ها · فقط‌خواندنی", list);
    }

    // ------------------------------------------------------------------ انبار

    private static void declareStock() {
        String section = WindowsRoleMenus.STOCK_LEDGER;
        List<VariantSpec> list = new ArrayList<>();
        list.add(VariantSpec.builder(section, "stock", "موجودی کالاها")
                .hint("موجودی بسته و جزء هر کالا با بهای خرید و ارزش موجودی.")
                .columns(SectionColumn.text("code", "کد کالا", 0.9),
                        SectionColumn.text("name", "نام کالا", 2.0),
                        SectionColumn.text("group", "گروه", 1.0),
                        SectionColumn.text("unit", "واحد", 0.7),
                        SectionColumn.number("packs", "موجودی بسته", 0.9),
                        SectionColumn.number("loose", "موجودی جزء", 0.9),
                        SectionColumn.number("total", "جمع موجودی", 0.9),
                        SectionColumn.money("buy_price", "بهای خرید", 1.1),
                        SectionColumn.money("value", "ارزش موجودی", 1.2),
                        SectionColumn.text("state", "وضعیت", 1.0))
                .derived(Derived.of("state", Derivation.STOCK_STATE, "total"))
                .kpis(Kpi.count("تعداد کالا"), Kpi.sum("ارزش کل موجودی", "value"),
                        Kpi.matches("کسری موجودی", "state", "کسری"),
                        Kpi.matches("اتمام موجودی", "state", "اتمام"))
                .sort("value", true).maxRows(3000).noDateRange()
                .query("نام، کد یا بارکد کالا…")
                .build());
        list.add(VariantSpec.builder(section, "kardex", "گردش کالا (کاردکس)")
                .hint("ورود و خروج هر کالا از گردش انبار آتیران (dbo.ka_act) با نام عملیات.")
                .columns(SectionColumn.date("date", "تاریخ", 0.9),
                        SectionColumn.text("code", "کد کالا", 0.9),
                        SectionColumn.text("name", "نام کالا", 1.8),
                        SectionColumn.text("doc", "سند", 0.8),
                        SectionColumn.text("operation", "عملیات", 1.3),
                        SectionColumn.text("warehouse", "انبار", 0.9),
                        SectionColumn.number("incoming", "ورود", 0.8),
                        SectionColumn.number("outgoing", "خروج", 0.8),
                        SectionColumn.number("net", "خالص", 0.8))
                .derived(Derived.of("net", Derivation.NET_AMOUNT, "incoming", "outgoing"))
                .kpis(Kpi.sum("جمع ورود", "incoming"), Kpi.sum("جمع خروج", "outgoing"),
                        Kpi.diff("خالص گردش", "incoming", "outgoing"), Kpi.count("تعداد ردیف گردش"))
                .dateKey("date").sort("date", true).maxRows(3000).defaultDays(62)
                .query("کد یا نام کالا…")
                .build());
        list.add(VariantSpec.builder(section, "alerts", "کسری و هشدار موجودی")
                .hint("کالاهایی که موجودی‌شان به نقطهٔ سفارش رسیده یا منفی است، با مقدار پیشنهادی.")
                .columns(SectionColumn.text("code", "کد کالا", 0.9),
                        SectionColumn.text("name", "نام کالا", 1.9),
                        SectionColumn.text("group", "گروه", 1.0),
                        SectionColumn.number("total", "موجودی", 0.9),
                        SectionColumn.number("point", "نقطهٔ سفارش", 0.9),
                        SectionColumn.number("needed", "مقدار پیشنهادی", 1.0),
                        SectionColumn.date("last_sale", "آخرین فروش", 0.9),
                        SectionColumn.text("idle", "بی‌حرکت", 0.9),
                        SectionColumn.text("state", "وضعیت", 1.0))
                .derived(Derived.of("idle", Derivation.DAYS_AGO, "last_sale"),
                        Derived.of("state", Derivation.STOCK_STATE, "total", "point"))
                .kpis(Kpi.count("تعداد هشدار"), Kpi.matches("کسری", "state", "کسری"),
                        Kpi.matches("نیاز به سفارش", "state", "نیاز به سفارش"),
                        Kpi.sum("جمع مقدار پیشنهادی", "needed"))
                .sort("needed", true).maxRows(1000).noDateRange()
                .query("نام یا کد کالا…")
                .build());
        list.add(VariantSpec.builder(section, "dead", "کالای راکد")
                .hint("کالاهایی که در بازهٔ روزهای انتخابی فروش نداشته‌اند.")
                .columns(SectionColumn.text("code", "کد کالا", 0.9),
                        SectionColumn.text("name", "نام کالا", 2.0),
                        SectionColumn.text("group", "گروه", 1.0),
                        SectionColumn.number("total", "موجودی", 0.9),
                        SectionColumn.money("value", "ارزش موجودی", 1.2),
                        SectionColumn.date("last_sale", "آخرین فروش", 1.0),
                        SectionColumn.text("idle", "مدت رکود", 1.0))
                .derived(Derived.of("idle", Derivation.DAYS_AGO, "last_sale"))
                .kpis(Kpi.count("کالای راکد"), Kpi.sum("ارزش کالای راکد", "value"),
                        Kpi.avg("میانگین ارزش", "value"))
                .sort("value", true).maxRows(1500).defaultDays(180)
                .query("نام یا کد کالا…")
                .build());
        register(section, "گردش و موجودی انبار",
                "موجودی، کاردکس، کسری و کالای راکد · فقط‌خواندنی", list);
    }

    // ------------------------------------------------------------------ گزارش‌ها

    private static void declareReports() {
        String section = WindowsRoleMenus.REPORTS;
        List<VariantSpec> list = new ArrayList<>();
        list.add(VariantSpec.builder(section, "sales_daily", "فروش روزانه")
                .hint("جمع فروش، تخفیف، مالیات و وصولی هر روز از فاکتورهای فروش آتیران.")
                .columns(SectionColumn.date("date", "تاریخ", 0.9),
                        SectionColumn.number("docs", "تعداد فاکتور", 0.8),
                        SectionColumn.money("gross", "جمع فروش", 1.2),
                        SectionColumn.money("discount", "تخفیف", 1.0),
                        SectionColumn.money("tax", "مالیات", 1.0),
                        SectionColumn.money("collected", "دریافت‌شده", 1.2),
                        SectionColumn.text("rate", "نرخ وصول", 0.9))
                .derived(Derived.of("rate", Derivation.RATE_PERCENT, "collected", "gross"))
                .kpis(Kpi.sum("جمع فروش", "gross"), Kpi.sum("تعداد فاکتور", "docs"),
                        Kpi.avg("میانگین فروش روز", "gross"), Kpi.ratio("نرخ وصول دوره", "collected", "gross"),
                        Kpi.sum("جمع تخفیف", "discount"))
                .dateKey("date").sort("date", true).maxRows(400).defaultDays(62)
                .build());
        list.add(VariantSpec.builder(section, "sales_monthly", "فروش ماهانه")
                .hint("جمع فروش هر ماه شمسی با مقایسهٔ وصولی.")
                .columns(SectionColumn.text("month", "ماه", 0.9),
                        SectionColumn.number("docs", "تعداد فاکتور", 0.8),
                        SectionColumn.money("gross", "جمع فروش", 1.2),
                        SectionColumn.money("discount", "تخفیف", 1.0),
                        SectionColumn.money("collected", "دریافت‌شده", 1.2),
                        SectionColumn.text("rate", "نرخ وصول", 0.9))
                .derived(Derived.of("rate", Derivation.RATE_PERCENT, "collected", "gross"))
                .kpis(Kpi.sum("جمع فروش", "gross"), Kpi.max("بیشترین ماه", "gross"),
                        Kpi.ratio("نرخ وصول", "collected", "gross"))
                .sort("month", true).maxRows(60).defaultDays(0)
                .build());
        list.add(VariantSpec.builder(section, "top_customers", "مشتریان برتر")
                .hint("پرفروش‌ترین مشتریان بازهٔ انتخابی با ماندهٔ حساب.")
                .columns(SectionColumn.text("code", "کد مشتری", 0.8),
                        SectionColumn.text("name", "مشتری", 1.9),
                        SectionColumn.number("docs", "تعداد فاکتور", 0.8),
                        SectionColumn.money("gross", "جمع خرید", 1.2),
                        SectionColumn.money("collected", "وصولی", 1.1),
                        SectionColumn.money("balance", "ماندهٔ حساب", 1.1),
                        SectionColumn.date("last", "آخرین خرید", 0.9),
                        SectionColumn.text("rate", "نرخ وصول", 0.9))
                .derived(Derived.of("rate", Derivation.RATE_PERCENT, "collected", "gross"))
                .kpis(Kpi.sum("جمع فروش مشتریان", "gross"), Kpi.count("تعداد مشتری"),
                        Kpi.avg("میانگین خرید", "gross"), Kpi.max("بیشترین خرید", "gross"))
                .sort("gross", true).maxRows(500).defaultDays(90)
                .query("نام یا کد مشتری…")
                .build());
        list.add(VariantSpec.builder(section, "top_products", "کالاهای پرفروش")
                .hint("پرفروش‌ترین کالاها با بها و سود ناخالص همان بازه.")
                .columns(SectionColumn.text("code", "کد کالا", 0.8),
                        SectionColumn.text("name", "کالا", 1.9),
                        SectionColumn.text("group", "گروه", 1.0),
                        SectionColumn.number("qty", "مقدار فروش", 0.9),
                        SectionColumn.number("docs", "تعداد سند", 0.8),
                        SectionColumn.money("gross", "جمع فروش", 1.2),
                        SectionColumn.money("cogs", "بهای تمام‌شده", 1.2),
                        SectionColumn.money("profit", "سود ناخالص", 1.1),
                        SectionColumn.text("margin", "حاشیهٔ سود", 0.9))
                .derived(Derived.of("profit", Derivation.NET_AMOUNT, "gross", "cogs"),
                        Derived.of("margin", Derivation.MARGIN_PERCENT, "gross", "cogs"))
                .kpis(Kpi.sum("جمع فروش", "gross"), Kpi.diff("سود ناخالص", "gross", "cogs"),
                        Kpi.margin("حاشیهٔ سود کل", "gross", "cogs"), Kpi.count("تعداد کالا"))
                .sort("gross", true).maxRows(500).defaultDays(90)
                .query("نام یا کد کالا…")
                .build());
        list.add(VariantSpec.builder(section, "aging", "سن‌سنجی مطالبات")
                .hint("ماندهٔ بدهکاران بر اساس فاصلهٔ آخرین گردش تا امروز.")
                .columns(SectionColumn.text("code", "کد مشتری", 0.8),
                        SectionColumn.text("name", "مشتری", 2.0),
                        SectionColumn.money("balance", "ماندهٔ بدهی", 1.2),
                        SectionColumn.number("docs", "تعداد سند", 0.8),
                        SectionColumn.date("last", "آخرین گردش", 1.0),
                        SectionColumn.text("bucket", "سن بدهی", 1.2))
                .derived(Derived.of("bucket", Derivation.AGEING_BUCKET, "last"))
                .kpis(Kpi.count("بدهکاران"), Kpi.sum("جمع مطالبات", "balance"),
                        Kpi.max("بیشترین بدهی", "balance"),
                        Kpi.matches("بیش از ۱۸۰ روز", "bucket", "بیش از ۱۸۰ روز"))
                .sort("balance", true).maxRows(2000).noDateRange()
                .query("نام یا کد مشتری…")
                .build());
        register(section, "گزارش‌های فروش و مالی",
                "فروش روزانه و ماهانه، مشتریان و کالاهای برتر، سن مطالبات · فقط‌خواندنی", list);
    }

    // ------------------------------------------------------------------ سود

    private static void declareProfit() {
        String section = WindowsRoleMenus.PROFIT;
        List<VariantSpec> list = new ArrayList<>();
        list.add(VariantSpec.builder(section, "daily", "سود روزانه")
                .hint("فروش خالص منهای بهای تمام‌شدهٔ کالای فروش‌رفته (بهای خرید آتیران) در هر روز.")
                .columns(SectionColumn.date("date", "تاریخ", 0.9),
                        SectionColumn.number("docs", "تعداد فاکتور", 0.8),
                        SectionColumn.money("net", "فروش خالص", 1.2),
                        SectionColumn.money("cogs", "بهای تمام‌شده", 1.2),
                        SectionColumn.money("profit", "سود ناخالص", 1.2),
                        SectionColumn.text("margin", "حاشیهٔ سود", 0.9))
                .derived(Derived.of("profit", Derivation.NET_AMOUNT, "net", "cogs"),
                        Derived.of("margin", Derivation.MARGIN_PERCENT, "net", "cogs"))
                .kpis(Kpi.sum("فروش خالص", "net"), Kpi.sum("بهای تمام‌شده", "cogs"),
                        Kpi.diff("سود ناخالص", "net", "cogs"), Kpi.margin("حاشیهٔ سود دوره", "net", "cogs"))
                .dateKey("date").sort("date", true).maxRows(400).defaultDays(62)
                .build());
        list.add(VariantSpec.builder(section, "monthly", "سود ماهانه")
                .hint("سود و حاشیهٔ هر ماه شمسی.")
                .columns(SectionColumn.text("month", "ماه", 0.9),
                        SectionColumn.number("docs", "تعداد فاکتور", 0.8),
                        SectionColumn.money("net", "فروش خالص", 1.2),
                        SectionColumn.money("cogs", "بهای تمام‌شده", 1.2),
                        SectionColumn.money("profit", "سود ناخالص", 1.2),
                        SectionColumn.text("margin", "حاشیهٔ سود", 0.9))
                .derived(Derived.of("profit", Derivation.NET_AMOUNT, "net", "cogs"),
                        Derived.of("margin", Derivation.MARGIN_PERCENT, "net", "cogs"))
                .kpis(Kpi.sum("فروش خالص", "net"), Kpi.diff("سود ناخالص", "net", "cogs"),
                        Kpi.margin("حاشیهٔ سود", "net", "cogs"), Kpi.max("بیشترین ماه", "profit"))
                .sort("month", true).maxRows(60).defaultDays(0)
                .build());
        list.add(VariantSpec.builder(section, "by_product", "سود به تفکیک کالا")
                .hint("سود هر کالا در بازهٔ انتخابی، مرتب بر اساس بیشترین سود.")
                .columns(SectionColumn.text("code", "کد کالا", 0.8),
                        SectionColumn.text("name", "کالا", 1.9),
                        SectionColumn.text("group", "گروه", 1.0),
                        SectionColumn.number("qty", "مقدار فروش", 0.9),
                        SectionColumn.money("net", "فروش خالص", 1.2),
                        SectionColumn.money("cogs", "بهای تمام‌شده", 1.2),
                        SectionColumn.money("profit", "سود", 1.2),
                        SectionColumn.text("margin", "حاشیهٔ سود", 0.9))
                .derived(Derived.of("profit", Derivation.NET_AMOUNT, "net", "cogs"),
                        Derived.of("margin", Derivation.MARGIN_PERCENT, "net", "cogs"))
                .kpis(Kpi.sum("فروش خالص", "net"), Kpi.diff("سود کل", "net", "cogs"),
                        Kpi.margin("حاشیهٔ سود کل", "net", "cogs"), Kpi.count("تعداد کالا"))
                .sort("profit", true).maxRows(1000).defaultDays(90)
                .query("نام یا کد کالا…")
                .build());
        list.add(VariantSpec.builder(section, "low_margin", "کالاهای کم‌حاشیه")
                .hint("کالاهایی که حاشیهٔ سودشان از آستانهٔ درصدی انتخابی کمتر است.")
                .columns(SectionColumn.text("code", "کد کالا", 0.8),
                        SectionColumn.text("name", "کالا", 1.9),
                        SectionColumn.number("qty", "مقدار فروش", 0.9),
                        SectionColumn.money("net", "فروش خالص", 1.2),
                        SectionColumn.money("cogs", "بهای تمام‌شده", 1.2),
                        SectionColumn.money("profit", "سود", 1.1),
                        SectionColumn.text("margin", "حاشیهٔ سود", 0.9))
                .derived(Derived.of("profit", Derivation.NET_AMOUNT, "net", "cogs"),
                        Derived.of("margin", Derivation.MARGIN_PERCENT, "net", "cogs"))
                .kpis(Kpi.count("کالای کم‌حاشیه"), Kpi.diff("سود این کالاها", "net", "cogs"),
                        Kpi.margin("حاشیهٔ میانگین", "net", "cogs"))
                .sort("margin", true).maxRows(1000).defaultDays(90).defaultThreshold("10")
                .query("نام یا کد کالا…")
                .build());
        list.add(VariantSpec.builder(section, "costs", "هزینه‌های جانبی")
                .hint("هزینه‌های ثبت‌شده در آتیران (ExternalCosts و IndirectCost).")
                .columns(SectionColumn.date("date", "تاریخ", 0.9),
                        SectionColumn.text("kind", "نوع هزینه", 1.2),
                        SectionColumn.text("title", "شرح", 2.0),
                        SectionColumn.text("doc", "سند", 0.8),
                        SectionColumn.money("amount", "مبلغ", 1.2))
                .kpis(Kpi.count("تعداد هزینه"), Kpi.sum("جمع هزینه‌ها", "amount"),
                        Kpi.avg("میانگین هزینه", "amount"), Kpi.max("بزرگ‌ترین هزینه", "amount"))
                .dateKey("date").sort("date", true).maxRows(1500).defaultDays(180)
                .query("شرح یا نوع هزینه…")
                .build());
        register(section, "سود و بهای تمام‌شده",
                "سود روزانه، ماهانه، به تفکیک کالا، کم‌حاشیه و هزینه‌ها · فقط‌خواندنی", list);
    }

    // ------------------------------------------------------------------ تولید و شمارش

    private static void declareProduction() {
        String section = WindowsRoleMenus.PRODUCTION;
        List<VariantSpec> list = new ArrayList<>();
        list.add(VariantSpec.builder(section, "documents", "اسناد تولید")
                .hint("اسناد ثبت‌شدهٔ تولید آتیران با مقدار و بهای تولید.")
                .columns(SectionColumn.text("doc", "شمارهٔ سند", 0.9),
                        SectionColumn.date("date", "تاریخ", 0.9),
                        SectionColumn.text("product", "کالای تولیدی", 1.8),
                        SectionColumn.number("qty", "مقدار تولید", 0.9),
                        SectionColumn.money("cost", "بهای تولید", 1.2),
                        SectionColumn.money("unit_cost", "بهای واحد", 1.1),
                        SectionColumn.text("note", "شرح", 1.4))
                .kpis(Kpi.count("تعداد سند تولید"), Kpi.sum("جمع مقدار تولید", "qty"),
                        Kpi.sum("جمع بهای تولید", "cost"), Kpi.avg("میانگین بهای واحد", "unit_cost"))
                .dateKey("date").sort("date", true).maxRows(1500).defaultDays(180)
                .query("شمارهٔ سند یا کالای تولیدی…")
                .build());
        list.add(VariantSpec.builder(section, "formulation", "فرمولاسیون")
                .hint("ترکیب مواد اولیهٔ هر محصول ثبت‌شده در آتیران.")
                .columns(SectionColumn.text("product", "محصول", 1.8),
                        SectionColumn.text("component", "مادهٔ اولیه", 1.8),
                        SectionColumn.number("qty", "مقدار مصرف", 0.9),
                        SectionColumn.text("unit", "واحد", 0.7),
                        SectionColumn.money("cost", "بهای واحد", 1.1),
                        SectionColumn.money("total", "جمع بها", 1.2))
                .kpis(Kpi.count("تعداد ردیف فرمول"), Kpi.sum("جمع بهای فرمول‌ها", "total"),
                        Kpi.avg("میانگین بها", "total"))
                .sort("product", true).maxRows(1500).noDateRange()
                .query("نام محصول یا مادهٔ اولیه…")
                .build());
        list.add(VariantSpec.builder(section, "costs", "بهای تولید")
                .hint("سرفصل‌های بهای تمام‌شدهٔ تولید (ProductionCost).")
                .columns(SectionColumn.text("doc", "سند تولید", 0.9),
                        SectionColumn.date("date", "تاریخ", 0.9),
                        SectionColumn.text("title", "سرفصل هزینه", 2.0),
                        SectionColumn.money("amount", "مبلغ", 1.2),
                        SectionColumn.text("note", "شرح", 1.4))
                .kpis(Kpi.count("تعداد سرفصل"), Kpi.sum("جمع بهای تولید", "amount"),
                        Kpi.avg("میانگین سرفصل", "amount"))
                .dateKey("date").sort("date", true).maxRows(1500).defaultDays(180)
                .query("سرفصل یا سند تولید…")
                .build());
        list.add(VariantSpec.builder(section, "count_diff", "مغایرت شمارش انبار")
                .hint("تفاوت موجودی انبار و موجودی سیستمی کالاها؛ مبنای شمارش و اصلاح.")
                .columns(SectionColumn.text("code", "کد کالا", 0.9),
                        SectionColumn.text("name", "نام کالا", 2.0),
                        SectionColumn.text("warehouse", "انبار", 1.0),
                        SectionColumn.number("system_qty", "موجودی سیستم", 1.0),
                        SectionColumn.number("warehouse_qty", "موجودی انبار", 1.0),
                        SectionColumn.number("diff", "مغایرت", 0.9),
                        SectionColumn.money("value", "ارزش مغایرت", 1.2))
                .derived(Derived.of("diff", Derivation.NET_AMOUNT, "warehouse_qty", "system_qty"))
                .kpis(Kpi.count("تعداد مغایرت"), Kpi.sum("جمع ارزش مغایرت", "value"),
                        Kpi.max("بزرگ‌ترین مغایرت", "value"))
                .sort("value", true).maxRows(1500).noDateRange()
                .query("نام یا کد کالا…")
                .build());
        register(section, "تولید و شمارش",
                "اسناد تولید، فرمولاسیون، بهای تولید و مغایرت شمارش · فقط‌خواندنی", list);
    }

    // ------------------------------------------------------------------ پرسنل و مأموریت‌ها

    private static void declarePersonnel() {
        String section = WindowsRoleMenus.PERSONNEL;
        List<VariantSpec> list = new ArrayList<>();
        list.add(VariantSpec.builder(section, "visitors", "ویزیتورها و عملکرد")
                .hint("عملکرد هر ویزیتور در بازهٔ انتخابی: فاکتور، فروش، وصولی و هدف.")
                .columns(SectionColumn.text("code", "کد", 0.7),
                        SectionColumn.text("name", "ویزیتور", 1.6),
                        SectionColumn.text("phone", "تماس", 1.0),
                        SectionColumn.status("active", "وضعیت", 0.8, "active"),
                        SectionColumn.number("docs", "فاکتور", 0.7),
                        SectionColumn.money("sales", "فروش", 1.2),
                        SectionColumn.money("collected", "وصولی", 1.1),
                        SectionColumn.number("customers", "مشتریان فروشنده", 0.9),
                        SectionColumn.money("goal", "هدف فروش", 1.1),
                        SectionColumn.text("rate", "نرخ وصول", 0.9),
                        SectionColumn.text("goal_rate", "تحقق هدف", 0.9))
                .derived(Derived.of("rate", Derivation.RATE_PERCENT, "collected", "sales"),
                        Derived.of("goal_rate", Derivation.RATE_PERCENT, "sales", "goal"))
                .kpis(Kpi.count("تعداد ویزیتور"), Kpi.sum("جمع فروش", "sales"),
                        Kpi.sum("جمع وصولی", "collected"), Kpi.ratio("نرخ وصول کل", "collected", "sales"),
                        Kpi.max("بیشترین فروش", "sales"))
                .sort("sales", true).maxRows(200).defaultDays(90)
                .query("نام یا کد ویزیتور…")
                .build());
        list.add(VariantSpec.builder(section, "daily", "روند روزانهٔ ویزیتورها")
                .hint("فروش روزانهٔ هر ویزیتور؛ برای مقایسهٔ مأموریت‌ها.")
                .columns(SectionColumn.date("date", "تاریخ", 0.9),
                        SectionColumn.text("name", "ویزیتور", 1.6),
                        SectionColumn.number("docs", "فاکتور", 0.8),
                        SectionColumn.money("sales", "فروش", 1.2),
                        SectionColumn.money("collected", "وصولی", 1.1),
                        SectionColumn.number("customers", "مشتریان", 0.8))
                .kpis(Kpi.sum("جمع فروش", "sales"), Kpi.avg("میانگین روز", "sales"),
                        Kpi.max("بیشترین فروش روز", "sales"), Kpi.count("تعداد ردیف"))
                .dateKey("date").sort("date", true).maxRows(2000).defaultDays(62)
                .context("کد یا نام ویزیتور (اختیاری)…")
                .build());
        list.add(VariantSpec.builder(section, "customers", "مشتریان هر ویزیتور")
                .hint("مشتریان ویزیتور انتخابی با جمع خرید و ماندهٔ حساب.")
                .columns(SectionColumn.text("code", "کد مشتری", 0.8),
                        SectionColumn.text("name", "مشتری", 1.9),
                        SectionColumn.text("visitor", "ویزیتور", 1.2),
                        SectionColumn.number("docs", "فاکتور", 0.8),
                        SectionColumn.money("sales", "جمع خرید", 1.2),
                        SectionColumn.money("balance", "مانده", 1.1),
                        SectionColumn.date("last", "آخرین خرید", 0.9))
                .kpis(Kpi.count("تعداد مشتری"), Kpi.sum("جمع خرید", "sales"),
                        Kpi.sum("جمع مانده", "balance"), Kpi.avg("میانگین خرید", "sales"))
                .sort("sales", true).maxRows(2000).defaultDays(180)
                .query("نام یا کد مشتری…")
                .context("کد یا نام ویزیتور…")
                .build());
        list.add(VariantSpec.builder(section, "routes", "مسیرها و مناطق")
                .hint("تعداد مشتری و فروش هر مسیر پخش.")
                .columns(SectionColumn.text("route", "مسیر", 1.4),
                        SectionColumn.text("region", "منطقه", 1.2),
                        SectionColumn.number("customers", "تعداد مشتری", 0.9),
                        SectionColumn.number("active", "مشتری فعال", 0.9),
                        SectionColumn.number("docs", "فاکتور", 0.8),
                        SectionColumn.money("sales", "فروش مسیر", 1.2))
                .kpis(Kpi.count("تعداد مسیر"), Kpi.sum("جمع مشتری", "customers"),
                        Kpi.sum("جمع فروش", "sales"))
                .sort("sales", true).maxRows(300).defaultDays(180)
                .query("نام مسیر یا منطقه…")
                .build());
        register(section, "پرسنل و مأموریت‌ها",
                "ویزیتورها، روند روزانه، مشتریان و مسیرها · فقط‌خواندنی", list);
    }

    // ------------------------------------------------------------------ کاربران و دسترسی

    private static void declareUsers() {
        String section = WindowsRoleMenus.USERS;
        List<VariantSpec> list = new ArrayList<>();
        list.add(VariantSpec.builder(section, "list", "کاربران و دسترسی")
                .hint("کاربران آتیران با نقش، وضعیت و آخرین ورود؛ هیچ تغییری در دسترسی داده نمی‌شود.")
                .columns(SectionColumn.text("code", "کد کاربر", 0.8),
                        SectionColumn.text("name", "نام کاربر", 1.6),
                        SectionColumn.text("role", "نقش آتیران", 1.2),
                        SectionColumn.text("desktop_role", "نقش دسکتاپ", 1.1),
                        SectionColumn.number("menus", "منوهای قابل‌دیدن", 0.9),
                        SectionColumn.status("active", "وضعیت", 0.8, "active"),
                        SectionColumn.text("phone", "تماس", 1.0),
                        SectionColumn.text("last_login", "آخرین ورود", 1.1),
                        SectionColumn.number("today", "رویدادهای امروز", 0.9))
                .derived(Derived.of("desktop_role", Derivation.DESKTOP_ROLE, "role", "name"),
                        Derived.of("menus", Derivation.MENU_COUNT, "desktop_role"))
                .kpis(Kpi.count("تعداد کاربر"), Kpi.matches("کاربران فعال", "active", "فعال"),
                        Kpi.matches("غیرفعال", "active", "غیرفعال"))
                .sort("name", true).maxRows(500).noDateRange()
                .query("نام کاربر یا نقش…")
                .build());
        list.add(VariantSpec.builder(section, "roles", "نقش‌ها و دامنهٔ دسترسی")
                .hint("نقش‌های آتیران و اینکه همان نقش روی این ویندوز کدام منوها را می‌بیند.")
                .columns(SectionColumn.text("code", "کد نقش", 0.8),
                        SectionColumn.text("name", "نقش آتیران", 1.4),
                        SectionColumn.number("users", "تعداد کاربر", 0.9),
                        SectionColumn.text("desktop_role", "نقش دسکتاپ", 1.1),
                        SectionColumn.number("menus", "تعداد منو", 0.8),
                        SectionColumn.text("scope", "منوهای قابل‌دیدن", 2.4))
                .derived(Derived.of("desktop_role", Derivation.DESKTOP_ROLE, "name"),
                        Derived.of("menus", Derivation.MENU_COUNT, "desktop_role"),
                        Derived.of("scope", Derivation.MENU_SCOPE, "desktop_role"))
                .kpis(Kpi.count("تعداد نقش"), Kpi.sum("جمع کاربران", "users"))
                .sort("users", true).maxRows(200).noDateRange()
                .query("نام نقش…")
                .build());
        list.add(VariantSpec.builder(section, "logins", "ورودهای اخیر")
                .hint("آخرین ورودهای ثبت‌شده در آتیران با نام سیستم و آدرس شبکه.")
                .columns(SectionColumn.text("user", "کاربر", 1.4),
                        SectionColumn.date("at", "زمان ورود", 1.3),
                        SectionColumn.text("computer", "نام سیستم", 1.4),
                        SectionColumn.text("ip", "آدرس شبکه", 1.1),
                        SectionColumn.text("note", "شرح", 1.6))
                .kpis(Kpi.count("تعداد ورود ثبت‌شده", "سقف خواندن ۵۰۰ ردیف؛ برای ورودهای قدیمی‌تر بازهٔ جست‌وجو را محدود کن"))
                .sort("at", true).maxRows(500).noDateRange()
                .query("کاربر، نام سیستم یا آدرس شبکه…")
                .build());
        register(section, "مدیریت کاربران و دسترسی",
                "کاربران، نقش‌ها و ورودهای اخیر · فقط‌خواندنی، بدون تغییر دسترسی", list);
    }

    private static void register(String sectionId, String title, String caption, List<VariantSpec> variants) {
        VARIANTS.put(sectionId, List.copyOf(variants));
        TITLES.put(sectionId, title);
        CAPTIONS.put(sectionId, caption);
    }

    // ------------------------------------------------------------------ lookup API

    /** True when this menu id has a real desktop implementation (not just a menu entry). */
    public static boolean isDesktopSection(String menuId) {
        return menuId != null && VARIANTS.containsKey(menuId.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> sectionIds() {
        return Collections.unmodifiableList(new ArrayList<>(VARIANTS.keySet()));
    }

    /** The desktop sections one role may open, in menu order. */
    public static List<String> sectionsFor(String role) {
        List<String> visible = WindowsRoleMenus.idsFor(role);
        List<String> result = new ArrayList<>();
        for (String id : DESKTOP_SECTIONS) if (visible.contains(id)) result.add(id);
        return List.copyOf(result);
    }

    public static List<SectionVariant> variantsFor(String sectionId) {
        List<VariantSpec> specs = VARIANTS.get(normalizeId(sectionId));
        if (specs == null) return List.of();
        List<SectionVariant> result = new ArrayList<>();
        for (VariantSpec spec : specs) result.add(spec.asVariant());
        return List.copyOf(result);
    }

    public static List<VariantSpec> specsFor(String sectionId) {
        List<VariantSpec> specs = VARIANTS.get(normalizeId(sectionId));
        return specs == null ? List.of() : specs;
    }

    public static String titleOf(String sectionId) {
        String title = TITLES.get(normalizeId(sectionId));
        return title == null ? WindowsRoleMenus.labelOf(sectionId) : title;
    }

    public static String captionOf(String sectionId) {
        String caption = CAPTIONS.get(normalizeId(sectionId));
        return caption == null ? "" : caption;
    }

    public static SectionSpec section(String sectionId) {
        String id = normalizeId(sectionId);
        if (!VARIANTS.containsKey(id))
            throw new IllegalArgumentException("بخش «" + WindowsRoleMenus.labelOf(sectionId) + "» در نسخهٔ دسکتاپ پیاده‌سازی نشده است.");
        return new SectionSpec(id, titleOf(id), captionOf(id), variantsFor(id));
    }

    public static String defaultVariant(String sectionId) {
        List<VariantSpec> specs = specsFor(sectionId);
        return specs.isEmpty() ? "" : specs.get(0).id();
    }

    /** Resolves one report; an unknown or empty variant falls back to the section's first report. */
    public static VariantSpec spec(String sectionId, String variantId) {
        List<VariantSpec> specs = specsFor(sectionId);
        if (specs.isEmpty())
            throw new IllegalArgumentException("بخش «" + WindowsRoleMenus.labelOf(sectionId) + "» در نسخهٔ دسکتاپ پیاده‌سازی نشده است.");
        String wanted = variantId == null ? "" : variantId.trim().toLowerCase(Locale.ROOT);
        if (!wanted.isEmpty()) {
            for (VariantSpec spec : specs) if (spec.id().equals(wanted)) return spec;
            throw new IllegalArgumentException("گزارش «" + variantId + "» در بخش «" + titleOf(sectionId) + "» وجود ندارد.");
        }
        return specs.get(0);
    }

    /** The effective threshold of a report: the typed percent, else the catalog default. */
    public static BigDecimal thresholdOf(VariantSpec spec, BigDecimal typed) {
        if (typed != null) return typed;
        return spec.defaultThreshold() == null ? BigDecimal.ZERO : spec.defaultThreshold();
    }

    /**
     * The effective look-back window in days: the typed value, else the catalog default. Reports that
     * declare no day window always return 0, so a stale typed value can never silently narrow them.
     */
    public static int daysOf(VariantSpec spec, int typed) {
        if (spec == null || !spec.supportsDays()) return 0;
        return typed > 0 ? typed : spec.defaultDays();
    }

    /** Atiran role name → the desktop application role, exposed as a report column. */
    public static String desktopRole(String atiranRoleName, String userName) {
        String mapped = AtiranRoles.mapRoleName(0, atiranRoleName);
        if (mapped.isEmpty()) mapped = AtiranRoles.managerRoleForUserName(userName);
        return mapped.isEmpty() ? AtiranRoles.SELLER : mapped;
    }

    /** Persian label of a desktop role, matching the desk sidebar wording. */
    public static String desktopRoleLabel(String desktopRole) {
        switch (desktopRole == null ? "" : desktopRole) {
            case AtiranRoles.ADMIN: return "مدیر";
            case AtiranRoles.SALES_MANAGER: return "مدیر فروش";
            case AtiranRoles.ACCOUNTANT: return "حسابدار";
            case AtiranRoles.WAREHOUSE: return "انباردار";
            case AtiranRoles.VISITOR: return "ویزیتور";
            case AtiranRoles.DISTRIBUTOR: return "نمایندهٔ پخش";
            case AtiranRoles.MOADIAN: return "کاربر مودیان";
            case AtiranRoles.SELLER: return "فروشنده";
            default: return "کاربر";
        }
    }

    /** Menu labels a desktop role can see, joined for the access-scope column. */
    public static String menuScope(String desktopRole) {
        List<WindowsRoleMenus.Item> items = WindowsRoleMenus.itemsFor(desktopRole);
        List<String> labels = new ArrayList<>();
        for (WindowsRoleMenus.Item item : items) labels.add(item.label);
        return String.join("، ", labels);
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
