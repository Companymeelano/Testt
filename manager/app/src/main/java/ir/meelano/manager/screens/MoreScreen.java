package ir.meelano.manager.screens;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.CommandRoom;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.ui.Card3D;
import ir.meelano.manager.ui.MeelanoIcons;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Role-aware, searchable and grouped section index. */
public class MoreScreen extends Screen {
    private LinearLayout contentRef;
    private String searchText = "";
    private String expandedGroup;

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
        boolean visitor = isVisitorRole();
        if (expandedGroup == null) {
            String role = AtiranAuth.sessionRole(a);
            if (role == null || role.isEmpty()) role = RoleStore.current(a);
            expandedGroup = RoleStore.WAREHOUSE.equals(role) ? "stock" : "operations";
            // The manager and the visitor come for the sections, not for a report hub: both
            // roles land directly on the index that holds every section they may open.
            if (RoleStore.VISITOR.equals(role) || RoleStore.ADMIN.equals(role)) seg = SEG_ALL;
            // The visitor has no report hub, so its group starts open.
            if (visitor) expandedGroup = "visitor_sales";
        }
        content.removeAllViews();
        // The visitor's room is the field-sales workspace, not the manager's command room.
        if (visitor) {
            content.addView(a.kit.hero("🧾", "همهٔ بخش‌ها",
                    "کارهای روزانهٔ حساب ویزیتور شما", Theme.GOLD), a.kit.lp(-1, -2));
        } else {
            content.addView(a.kit.hero("▣", "اتاق فرمان",
                    "گزارش‌ها و همهٔ بخش‌ها در یک جا", Theme.GOLD), a.kit.lp(-1, -2));
        }
        content.addView(a.kit.gap(10));

        // The report hub belongs to the manager; for the visitor this switch would be dead.
        if (!visitor) {
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
        }

        LinearLayout inner = a.kit.v();
        content.addView(inner, a.kit.lp(-1, -2));
        if (seg == SEG_REPORTS && !visitor) {
            renderReportsHub(inner);
        } else {
            renderAll(inner);
        }
    }

    private static final String[][] REPORT_GROUPS = {
            {"ویژه مدیر", "⭐"}, {"فروش", "🧾"}, {"خرید", "🧺"}, {"خزانه‌داری", "🏦"},
            {"کالا و انبار", "📦"}, {"مشتریان", "👥"}, {"تحلیل ویزیتور و مسیر", "🚚"},
            {"سود و زیان", "📈"}, {"برگشتی و ابطالی", "↩"}, {"هوشمند", "✦"},
            {"گزارش‌های اختصاصی", "🔎"}, {"کاربران و نظارت", "🛡"}
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
        if (ProductionScreen.managerAllowed(a)) {
            c.addView(a.kit.gap(8));
            c.addView(a.kit.btnGold("⚙ تولید محصول", v -> a.nav("production")), a.kit.lp(-1, -2));
        }
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
        tile.addView(iconBadge(goldReportGlyph(group[0], group[1]), accent, 36), new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
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

    /** Dedicated report-family symbols in the dark/gold theme; other skins keep their original glyphs. */
    private static String goldReportGlyph(String title, String fallback) {
        if (Theme.isLight()) return fallback;
        if ("ویژه مدیر".equals(title)) return "🏅";
        if ("فروش".equals(title)) return "🛍";
        if ("خرید".equals(title)) return "🛒";
        if ("خزانه‌داری".equals(title)) return "🏦";
        if ("تحلیل ویزیتور و مسیر".equals(title)) return "🚚";
        if ("سود و زیان".equals(title)) return "💹";
        if ("برگشتی و ابطالی".equals(title)) return "↪";
        if ("کاربران و نظارت".equals(title)) return "♛";
        return fallback;
    }

    /** Fixed colours for report groups: sales and purchases stand out from the theme gold. */
    private static int groupColor(String group) {
        if ("فروش".equals(group)) return Theme.SALES_HUE;
        if ("خرید".equals(group)) return Theme.BUY_HUE;
        if ("خزانه‌داری".equals(group)) return Theme.GOLD;
        if ("دریافتی‌ها و پرداخت".equals(group)) return Theme.SUCCESS;
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
        for (CommandRoom.Group group : index()) shown += addGroup(content, group);
        if (shown == 0) {
            content.addView(a.kit.empty("بخشی با این عبارت پیدا نشد", "نام کوتاه‌تری جست‌وجو کنید."),
                    a.kit.lp(-1, -2));
        }
    }

    /** The index the signed-in role sees. */
    private CommandRoom.Group[] index() {
        if (isVisitorRole()) return CommandRoom.visitorGroups();
        List<CommandRoom.Group> groups = new ArrayList<>(Arrays.asList(CommandRoom.groups()));
        CommandRoom.Group extra = extraGroup();
        if (extra != null) groups.add(extra);
        return groups.toArray(new CommandRoom.Group[0]);
    }

    /**
     * Sections that exist but are not in the index yet. They are listed as well, so a newly
     * registered screen can never stay unreachable for the manager.
     */
    private CommandRoom.Group extraGroup() {
        List<CommandRoom.Entry> extra = new ArrayList<>();
        for (String id : a.screenIds()) {
            if ("more".equals(id) || CommandRoom.find(id) != null) continue;
            Screen s = a.screen(id);
            if (s == null) continue;
            extra.add(new CommandRoom.Entry(id, s.title(), "بخش برنامه", false));
        }
        if (extra.isEmpty()) return null;
        return new CommandRoom.Group("other", "سایر بخش‌ها", "بخش‌هایی که هنوز در گروه‌ها جا نگرفته‌اند",
                "◈", "steel", extra.toArray(new CommandRoom.Entry[0]));
    }

    private int addGroup(LinearLayout content, final CommandRoom.Group group) {
        final List<CommandRoom.Entry> visible = new ArrayList<>();
        String q = normalize(searchText);
        boolean groupMatch = !q.isEmpty()
                && (normalize(group.title).contains(q) || normalize(group.subtitle).contains(q));
        for (CommandRoom.Entry entry : group.entries) {
            if (!isAllowed(entry)) continue;
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

    private void addTiles(LinearLayout parent, List<CommandRoom.Entry> entries) {
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
            final CommandRoom.Entry entry = entries.get(i);
            row.addView(menuTile(entry), a.kit.wlp(1f));
        }
        if (entries.size() % cols != 0 && row != null)
            row.addView(a.kit.space(8), a.kit.wlp(1f));
    }

    private View menuTile(final CommandRoom.Entry entry) {
        final String title;
        final String glyph;
        final int accent;
        int art = 0;
        if ("tv".equals(entry.id)) {
            title = "تلویزیون فروشگاه";
            glyph = "📺";
            accent = Theme.GOLD;
        } else if (entry.visitor) {
            // A page of the visitor workspace: it opens inside «فروش میدانی».
            title = entry.title;
            glyph = "";
            accent = Theme.GOLD;
            art = artRes(entry.art);
        } else {
            Screen destination = a.screen(entry.id);
            if (destination == null) return a.kit.space(1);
            title = destination.title();
            glyph = destination.glyph();
            accent = destination.accent();
            art = artRes(entry.art);
        }
        LinearLayout tile = a.kit.card(accent);
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumHeight(Theme.dp(122));
        tile.setPadding(Theme.dp(10), Theme.dp(12), Theme.dp(10), Theme.dp(10));

        int vector = art > 0 && !Theme.isIcon3d(art) ? art : MeelanoIcons.iconFor(glyph);
        if (vector == 0) vector = iconFor(title, entry.id);
        if (vector != 0) {
            tile.addView(a.kit.iconBadge(vector, accent, 36),
                    new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
        } else {
            tile.addView(iconBadge(goldReportGlyph(title, glyph), accent, 36),
                    new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
        }
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
            else if (entry.visitor) {
                VisitorDeskScreen.pendingPage = entry.id;
                a.nav("visitor_work");
            } else a.nav(entry.id);
        });
        return tile;
    }

    /** Drawable names in the command index point to vectors, not display artwork. */
    private int artRes(String name) {
        if (name == null || name.trim().isEmpty()) return 0;
        try {
            return a.getResources().getIdentifier(name.trim(), "drawable", a.getPackageName());
        } catch (Exception e) {
            return 0;
        }
    }

    private int iconFor(String title, String id) {
        if ("tv".equals(id)) return R.drawable.mi_apps;
        if ("assistant".equals(id) || "voice".equals(id)) return R.drawable.mi_star_shine;
        if ("products".equals(id) || "product_file".equals(id)) return R.drawable.mi_grid_view;
        if ("customers".equals(id)) return R.drawable.mi_group;
        if ("home".equals(id)) return R.drawable.mi_home;
        if (title != null && title.contains("انبار")) return R.drawable.mi_inventory_2;
        return MeelanoIcons.iconFor(goldReportGlyph(title, ""));
    }

    private View artBadge(int vector, int accent, int sizeDp) {
        return a.kit.iconBadge(vector, accent, sizeDp);
    }

    private View iconBadge(String glyph, int accent, int sizeDp) {
        int iconRes = MeelanoIcons.iconFor(glyph);
        if (iconRes != 0) return a.kit.iconBadge(iconRes, accent, sizeDp);
        android.widget.FrameLayout badge = new android.widget.FrameLayout(a);
        badge.setBackground(Theme.avatar(Theme.alpha(accent, Theme.isLight() ? 28 : 42)));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        TextView fallback = a.kit.text(glyph, sizeDp * 0.42f, Theme.sectionText(accent), true);
        fallback.setGravity(Gravity.CENTER);
        badge.addView(fallback, new android.widget.FrameLayout.LayoutParams(-1, -1));
        return badge;
    }

    /** True when the signed-in role is the field visitor. */
    private boolean isVisitorRole() {
        String role = AtiranAuth.sessionRole(a);
        if (role == null || role.isEmpty()) role = RoleStore.current(a);
        return RoleStore.VISITOR.equals(role);
    }

    private boolean isAllowed(CommandRoom.Entry entry) {
        if (entry.visitor) return true;                 // pages of the visitor's own workspace
        if ("tv".equals(entry.id)) return true;
        return a.screen(entry.id) != null && RoleStore.allowed(a, entry.id);
    }

    private boolean entryMatches(CommandRoom.Entry entry, String q) {
        if ("tv".equals(entry.id))
            return normalize("تلویزیون فروشگاه نمایش تمام‌صفحه").contains(q);
        if (entry.visitor)
            return normalize(entry.title + " " + entry.description).contains(q);
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
        if ("success".equals(accent)) return Theme.SECTION_RECEIVE;
        if ("info".equals(accent)) return Theme.SECTION_WAREHOUSE;
        if ("teal".equals(accent)) return Theme.SECTION_MARKET;
        if ("violet".equals(accent)) return Theme.SECTION_RETURNS;
        if ("steel".equals(accent)) return Theme.MUTED;
        return Theme.SECTION_SALES;
    }
}
