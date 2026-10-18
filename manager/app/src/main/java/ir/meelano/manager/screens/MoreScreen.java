package ir.meelano.manager.screens;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.ui.Card3D;
import ir.meelano.manager.ui.MeelanoIcons;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Role-aware, searchable and grouped section index. */
public class MoreScreen extends Screen {
    private LinearLayout contentRef;
    private String searchText = "";
    private String expandedGroup;

    private static final class Entry {
        final String id;
        final String description;
        Entry(String id, String description) {
            this.id = id;
            this.description = description;
        }
    }

    private static final class Group {
        final String id;
        final String title;
        final String subtitle;
        final String glyph;
        final String accent;
        final Entry[] entries;
        Group(String id, String title, String subtitle, String glyph, String accent, Entry[] entries) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.glyph = glyph;
            this.accent = accent;
            this.entries = entries;
        }
    }

    private static Entry e(String id, String description) { return new Entry(id, description); }

    /** One source of truth for the manager and role-specific section index. */
    private static final Group[] GROUPS = {
            new Group("operations", "مدیریت روزانه و فروش", "فاکتورها، مشتریان و پیگیری روزانه", "♛", "gold",
                    new Entry[]{
                            e("home", "شاخص‌ها و هشدارهای امروز"),
                            e("closeout", "تطبیق روزانهٔ فروش، دریافت، پرداخت، چک، برگشتی و تحویل باز"),
                            e("manager_alerts", "آستانه‌های هشدار قابل تنظیم با دسترسی مستقیم به اقدام"),
                            e("sales", "فاکتورهای فروش و پیگیری‌ها"),
                            e("buy", "فاکتورهای خرید و طرف‌حساب‌ها"),
                            e("customers", "پرونده و گردش حساب مشتریان"),
                            e("visitors", "عملکرد ویزیتورها و وصول"),
                    }),
            new Group("finance", "مالی و خزانه", "دریافت، پرداخت و کنترل نقدینگی", "💳", "success",
                    new Entry[]{
                            e("dar_in", "قبوض دریافت و روش‌های تسویه"),
                            e("dar_out", "قبوض پرداخت و گردش کارت"),
                            e("cheques", "چک‌ها و وضعیت وصول"),
                            e("dues", "سررسید چک‌ها و فاکتورها"),
                            e("cash", "پیش‌بینی نقدینگی و خالص روزانه"),
                    }),
            new Group("stock", "کالا و عملیات انبار", "موجودی، رسید، تحویل و شمارش", "📦", "info",
                    new Entry[]{
                            e("products", "موجودی، کم‌موجودی و گردش کالا"),
                            e("anbar", "میزکار و هشدارهای انبار"),
                            e("tahvil", "تحویل فاکتور و ثبت گیرنده"),
                            e("pick_wave", "برداشت چند فاکتور، جمع کالا و اسکن بارگیری"),
                            e("sync_queue", "تحویل و شمارش‌های منتظر همگام‌سازی"),
                            e("resid", "رسید کالا و پیش‌نویس‌های انبار"),
                            e("takmil", "تکمیل پیش‌فاکتور"),
                            e("shomarsh", "شمارش انبار و مغایرت‌گیری"),
                            e("score", "ارزیابی تأمین‌کنندگان"),
                    }),
            new Group("insights", "تحلیل و گزارش", "گزارش‌های مدیریتی و بینش کسب‌وکار", "📊", "teal",
                    new Entry[]{
                            e("reports", "مرکز گزارش‌های مدیریتی"),
                            e("profit", "سود، هزینه و حاشیه سود"),
                            e("voice", "گزارش صوتی فارسی"),
                    }),
            new Group("system", "مدیریت سیستم و ابزارها", "کاربران، تنظیمات و ابزارهای کمکی", "⚙", "steel",
                    new Entry[]{
                            e("users", "کاربران و فعالیت‌های سیستم"),
                            e("settings", "اتصال، ظاهر، اعلان و پشتیبان‌گیری"),
                            e("search", "جست‌وجو در بخش‌های برنامه"),
                            e("tv", "نمایش تمام‌صفحه برای فروشگاه"),
                    }),
    };

    public MoreScreen(MainActivity a) {
        super(a);
    }

    /** Hub segments: 0 = reports (default), 1 = all sections. The assistant has its own home icon. */
    private static final int SEG_REPORTS = 0, SEG_ALL = 1;
    private static final String[] SEG_LABELS = {"📊 گزارش‌ها", "▦ همه بخش‌ها"};
    private int seg = SEG_REPORTS;

    @Override public String id() { return "more"; }
    @Override public String title() { return "اتاق فرمان"; }
    @Override public String glyph() { return "▣"; }
    @Override public int accent() { return Theme.GOLD; }

    /** The command room: reports and every section in one place. */
    @Override
    public void render(final LinearLayout content) {
        contentRef = content;
        if (expandedGroup == null) {
            String role = AtiranAuth.sessionRole(a);
            if (role == null || role.isEmpty()) role = RoleStore.current(a);
            expandedGroup = RoleStore.WAREHOUSE.equals(role) ? "stock" : "operations";
        }
        content.removeAllViews();
        content.addView(a.kit.hero("▣", "اتاق فرمان",
                "گزارش‌ها و همهٔ بخش‌ها در یک جا", Theme.GOLD), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        LinearLayout segRow = a.kit.h();
        for (int i = 0; i < SEG_LABELS.length; i++) {
            final int idx = i;
            boolean on = seg == i;
            segRow.addView(a.kit.chip(SEG_LABELS[i], on, Theme.GOLD, v -> {
                seg = idx;
                render(contentRef);
            }), a.kit.wlp(1f));
        }
        content.addView(segRow, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        LinearLayout inner = a.kit.v();
        content.addView(inner, a.kit.lp(-1, -2));
        if (seg == SEG_REPORTS) {
            renderReportsHub(inner);
        } else {
            renderAll(inner);
        }
    }

    private static final String[][] REPORT_GROUPS = {
            {"ویژه مدیر", "⭐"}, {"فروش", "🧾"}, {"خرید", "🧺"}, {"دریافت و پرداخت", "💰"},
            {"چک‌ها", "📝"}, {"کالا و انبار", "📦"}, {"مشتریان", "👥"}, {"تحلیل ویزیتور و مسیر", "🚚"},
            {"سود و زیان", "📈"}, {"هزینه‌ها", "🧾"}, {"بانک و صندوق", "🏦"}, {"برگشتی و ابطالی", "↩"},
            {"هوشمند", "✦"}, {"گزارش‌های اختصاصی", "🔎"}, {"کاربران و نظارت", "🛡"}, {"سرفصل‌ها", "🗂"}
    };

    private void renderReportsHub(LinearLayout c) {
        c.addView(a.kit.hint("یک بخش را انتخاب کنید تا گزارش‌های همان بخش باز شود."), a.kit.lp(-1, -2));
        c.addView(a.kit.gap(8));
        for (int i = 0; i < REPORT_GROUPS.length; i += 2) {
            LinearLayout row = a.kit.h();
            row.addView(groupTile(REPORT_GROUPS[i]), a.kit.wlp(1f));
            row.addView(a.kit.space(8));
            if (i + 1 < REPORT_GROUPS.length) row.addView(groupTile(REPORT_GROUPS[i + 1]), a.kit.wlp(1f));
            else row.addView(a.kit.space(0), a.kit.wlp(1f));
            c.addView(row, a.kit.lp(-1, -2));
            c.addView(a.kit.gap(8));
        }
        c.addView(a.kit.btnGold("▤ همهٔ گزارش‌ها", v -> a.nav("reports")), a.kit.lp(-1, -2));
    }

    /** Two-line card tile: long section names wrap instead of being cut off. */
    private View groupTile(final String[] group) {
        int accent = groupColor(group[0]);
        LinearLayout tile = a.kit.card(accent);
        // A row: badge on the start side, the label beside it. (The card is vertical by default.)
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setMinimumHeight(Theme.dp(60));
        tile.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10));
        tile.addView(iconBadge(group[1], accent, 36), new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
        tile.addView(a.kit.space(8));
        TextView label = a.kit.text(group[0], 12.5f, Theme.TEXT, true);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(label, a.kit.wlp(1f));
        tile.setContentDescription(group[0] + "؛ باز کردن گزارش‌ها");
        Theme.pressable(tile);
        tile.setOnClickListener(v -> {
            ReportsScreen.pendingCategory = group[0];
            a.nav("reports");
        });
        return tile;
    }

    /** Fixed colours for report groups: sales and purchases stand out from the theme gold. */
    private static int groupColor(String group) {
        if ("فروش".equals(group)) return Theme.SALES_HUE;
        if ("خرید".equals(group)) return Theme.BUY_HUE;
        if ("دریافت و پرداخت".equals(group)) return Theme.SUCCESS;
        if ("چک‌ها".equals(group)) return Theme.INFO;
        if ("کالا و انبار".equals(group)) return Theme.VIOLET;
        if ("مشتریان".equals(group)) return Theme.GOLD;
        if ("تحلیل ویزیتور و مسیر".equals(group)) return Theme.WARNING;
        if ("ویژه مدیر".equals(group)) return Theme.GOLD;
        if ("هزینه‌ها".equals(group)) return Theme.ROSE;
        if ("بانک و صندوق".equals(group)) return Theme.INFO;
        if ("گزارش‌های اختصاصی".equals(group)) return Theme.VIOLET;
        if ("کاربران و نظارت".equals(group)) return Theme.STEEL;
        if ("سود و زیان".equals(group)) return Theme.GOLD_SOFT;
        if ("برگشتی و ابطالی".equals(group)) return Theme.DANGER;
        return Theme.STEEL;
    }

    /** «همه بخش‌ها»: searchable, role-filtered list of every section. */
    private void renderAll(final LinearLayout content) {
        content.removeAllViews();
        content.addView(a.kit.searchBar("جست‌وجوی بخش یا کار…", searchText, q -> {
            searchText = q == null ? "" : q.trim();
            renderAll(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        int shown = 0;
        for (Group group : GROUPS) shown += addGroup(content, group);
        if (shown == 0) {
            content.addView(a.kit.empty("بخشی با این عبارت پیدا نشد", "نام کوتاه‌تری جست‌وجو کنید."),
                    a.kit.lp(-1, -2));
        }
    }

    private int addGroup(LinearLayout content, final Group group) {
        final List<Entry> visible = new ArrayList<>();
        String q = normalize(searchText);
        boolean groupMatch = !q.isEmpty()
                && (normalize(group.title).contains(q) || normalize(group.subtitle).contains(q));
        for (Entry entry : group.entries) {
            if (!isAllowed(entry.id)) continue;
            if (q.isEmpty() || groupMatch || entryMatches(entry, q)) visible.add(entry);
        }
        if (visible.isEmpty()) return 0;

        final int accent = groupAccent(group.accent);
        final boolean searching = !q.isEmpty();
        final boolean open = searching || group.id.equals(expandedGroup);
        LinearLayout panel = a.kit.card(accent);
        LinearLayout header = a.kit.h();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(Theme.dp(56));
        header.setClickable(true);
        header.setFocusable(true);
        header.setContentDescription(group.title + "، " + (open ? "بستن" : "بازکردن"));

        header.addView(iconBadge(group.glyph, accent, 46),
                new LinearLayout.LayoutParams(Theme.dp(46), Theme.dp(46)));
        header.addView(a.kit.space(10));

        LinearLayout copy = a.kit.v();
        TextView title = a.kit.text(group.title, 14f, Theme.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title, a.kit.lp(-1, -2));
        TextView subtitle = a.kit.text(group.subtitle, 10.5f, Theme.MUTED, false);
        subtitle.setMaxLines(2);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(subtitle, a.kit.lp(-1, -2));
        header.addView(copy, a.kit.wlp(1f));
        header.addView(a.kit.space(8));
        header.addView(a.kit.pill(Money.fa(String.valueOf(visible.size())) + " بخش", accent),
                a.kit.lp(-2, -2));
        header.addView(a.kit.space(6));
        ImageView toggle = new ImageView(a);
        toggle.setImageResource(open ? ir.meelano.manager.R.drawable.mi_remove
                : ir.meelano.manager.R.drawable.mi_add);
        toggle.setColorFilter(accent);
        toggle.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(toggle, new LinearLayout.LayoutParams(Theme.dp(32), Theme.dp(40)));
        Theme.pressable(header);
        header.setOnClickListener(v -> {
            if (!searchText.isEmpty()) searchText = "";
            expandedGroup = open && group.id.equals(expandedGroup) ? "" : group.id;
            render(contentRef);
        });
        panel.addView(header, a.kit.lp(-1, -2));

        if (open) {
            panel.addView(a.kit.gap(8));
            addTiles(panel, visible);
        }
        LinearLayout.LayoutParams pp = a.kit.lp(-1, -2);
        pp.setMargins(0, 0, 0, Theme.dp(10));
        content.addView(panel, pp);
        return visible.size();
    }

    private void addTiles(LinearLayout parent, List<Entry> entries) {
        int cols = a.kit.isWide() ? 3 : 2;
        LinearLayout row = null;
        for (int i = 0; i < entries.size(); i++) {
            if (i % cols == 0) {
                row = a.kit.h();
                LinearLayout.LayoutParams rp = a.kit.lp(-1, -2);
                if (i > 0) rp.setMargins(0, Theme.dp(8), 0, 0);
                parent.addView(row, rp);
            }
            if (i % cols > 0) row.addView(a.kit.space(8));
            final Entry entry = entries.get(i);
            row.addView(menuTile(entry), a.kit.wlp(1f));
        }
        if (entries.size() % cols != 0 && row != null)
            row.addView(a.kit.space(8), a.kit.wlp(1f));
    }

    private View menuTile(final Entry entry) {
        final String title;
        final String glyph;
        final int accent;
        if ("tv".equals(entry.id)) {
            title = "تلویزیون فروشگاه";
            glyph = "📺";
            accent = Theme.GOLD;
        } else {
            Screen destination = a.screen(entry.id);
            if (destination == null) return a.kit.space(1);
            title = destination.title();
            glyph = destination.glyph();
            accent = destination.accent();
        }
        LinearLayout tile = a.kit.card(accent);
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumHeight(Theme.dp(122));
        tile.setPadding(Theme.dp(10), Theme.dp(12), Theme.dp(10), Theme.dp(10));

        tile.addView(iconBadge(glyph, accent, 44),
                new LinearLayout.LayoutParams(Theme.dp(44), Theme.dp(44)));
        tile.addView(a.kit.gap(5));

        TextView label = a.kit.text(title, 12.5f, Theme.TEXT, true);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(label, a.kit.lp(-1, -2));

        String detail = entry.description;
        if ("sync_queue".equals(entry.id)) {
            int pending = ir.meelano.manager.core.WarehouseSync.pendingCount(a);
            detail += " • " + Money.fa(String.valueOf(pending)) + " منتظر";
        }
        TextView desc = a.kit.text(detail, 10f, Theme.MUTED, false);
        desc.setGravity(Gravity.CENTER);
        desc.setMaxLines(2);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(desc, a.kit.lp(-1, -2));
        tile.setContentDescription(title + "؛ " + entry.description + "؛ باز کردن");
        Theme.pressable(tile);
        tile.setOnClickListener(v -> {
            if ("tv".equals(entry.id)) a.startTv();
            else a.nav(entry.id);
        });
        return tile;
    }

    private View iconBadge(String glyph, int accent, int sizeDp) {
        android.widget.FrameLayout badge = new android.widget.FrameLayout(a);
        badge.setBackground(Theme.avatar(Theme.alpha(accent, Theme.isLight() ? 34 : 46)));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconRes = MeelanoIcons.iconFor(glyph);
        if (iconRes != 0) {
            ImageView image = new ImageView(a);
            image.setImageResource(iconRes);
            image.setColorFilter(accent);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int inside = Math.max(18, Math.round(sizeDp * 0.52f));
            android.widget.FrameLayout.LayoutParams ip = new android.widget.FrameLayout.LayoutParams(
                    Theme.dp(inside), Theme.dp(inside), Gravity.CENTER);
            badge.addView(image, ip);
        } else {
            TextView fallback = a.kit.text(glyph, sizeDp * 0.48f, accent, true);
            fallback.setGravity(Gravity.CENTER);
            badge.addView(fallback, new android.widget.FrameLayout.LayoutParams(-1, -1));
        }
        return badge;
    }

    private boolean isAllowed(String id) {
        return "tv".equals(id) || (a.screen(id) != null && RoleStore.allowed(a, id));
    }

    private boolean entryMatches(Entry entry, String q) {
        if ("tv".equals(entry.id))
            return normalize("تلویزیون فروشگاه نمایش تمام‌صفحه").contains(q);
        Screen screen = a.screen(entry.id);
        return screen != null && (normalize(screen.title()).contains(q)
                || normalize(entry.description).contains(q));
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", "")
                .toLowerCase(Locale.ROOT).trim();
    }

    private int groupAccent(String accent) {
        if ("success".equals(accent)) return Theme.SUCCESS;
        if ("info".equals(accent)) return Theme.INFO;
        if ("teal".equals(accent)) return Theme.GOLD;
        if ("steel".equals(accent)) return Theme.STEEL;
        return Theme.GOLD;
    }
}
