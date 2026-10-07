package ir.meelano.manager.ui;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Advanced filter sheet used by EVERY section: range + Jalali dates + lookups + status + sort. */
public final class FilterSheet {
    private FilterSheet() { }

    public static final class Opt {
        public final String key;
        public final String label;

        public Opt(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    public static final class Config {
        public boolean search = true;
        public boolean range = true;
        public boolean visitors;
        public boolean routes;
        public boolean custGroups;
        public boolean kalaGroups;
        public boolean warehouses;
        public boolean banks;
        public String searchHint = "جستجو…";
        public String statusTitle = "وضعیت";
        public Opt[] status;
        public String sortTitle = "مرتب‌سازی";
        public Opt[] sort;
    }

    public interface Done {
        void onApply(Filter f);
    }

    public static void show(final Kit kit, final Repo repo, final Filter current, final Config cfg, final Done done) {
        final Filter f = current.copy();
        LinearLayout root = kit.v();
        root.setPadding(Theme.dp(16), Theme.dp(14), Theme.dp(16), Theme.dp(10));

        // ---- search ----
        if (cfg.search) {
            final EditText search = new EditText(kit.a);
            search.setHint(cfg.searchHint);
            search.setText(f.search == null ? "" : f.search);
            search.setTextSize(12.5f);
            search.setTextColor(Theme.TEXT);
            search.setHintTextColor(Theme.MUTED);
            search.setTypeface(Theme.face(false));
            search.setBackground(Theme.searchBar());
            search.setPadding(Theme.dp(14), Theme.dp(10), Theme.dp(14), Theme.dp(10));
            search.setSingleLine(true);
            root.addView(search, kit.lp(-1, -2));
            root.addView(kit.gap(10));
            pendingSearch[0] = "";
            search.addTextChangedListener(new SimpleWatcher() {
                @Override
                public void onText(String s) {
                    pendingSearch[0] = s;
                }
            });
        }

        // ---- range presets ----
        final LinearLayout customBox = kit.v();
        if (cfg.range) {
            root.addView(kit.text("بازه تاریخ", 12.5f, Theme.GOLD_SOFT, true), kit.lp(-1, -2));
            root.addView(kit.gap(6));
            final int[] presets = {Filter.P_TODAY, Filter.P_YESTERDAY, Filter.P_LAST7, Filter.P_LAST30, Filter.P_MONTH, Filter.P_YEAR, Filter.P_ALL};
            final List<String> names = new ArrayList<>();
            for (int p : presets) names.add(Filter.presetName(p));
            names.add("دلخواه");
            int sel = names.size() - 1;
            for (int i = 0; i < presets.length; i++) if (presets[i] == f.preset) sel = i;
            final int[] selIdx = {sel};
            final Runnable renderCustom = () -> {
                customBox.removeAllViews();
                if (selIdx[0] == names.size() - 1) {
                    customBox.addView(kit.gap(4));
                    customBox.addView(kit.text("از تاریخ", 11f, Theme.MUTED, true), kit.lp(-1, -2));
                    customBox.addView(dateRow(kit, f.from.isEmpty() ? Jalali.todayStr() : f.from, true, f), kit.lp(-1, -2));
                    customBox.addView(kit.gap(4));
                    customBox.addView(kit.text("تا تاریخ", 11f, Theme.MUTED, true), kit.lp(-1, -2));
                    customBox.addView(dateRow(kit, f.to.isEmpty() ? Jalali.todayStr() : f.to, false, f), kit.lp(-1, -2));
                }
            };
            // chips (manual, to control re-render)
            LinearLayout chips = kit.h();
            chips.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.HorizontalScrollView sv = new android.widget.HorizontalScrollView(kit.a);
            sv.setHorizontalScrollBarEnabled(false);
            final LinearLayout[] rowHolder = {kit.h()};
            sv.addView(rowHolder[0], new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
            final Runnable paintChips = new Runnable() {
                @Override
                public void run() {
                    rowHolder[0].removeAllViews();
                    for (int i = 0; i < names.size(); i++) {
                        final int idx = i;
                        View ch = kit.chip(names.get(i), idx == selIdx[0], Theme.GOLD, v -> {
                            selIdx[0] = idx;
                            if (idx < presets.length) {
                                f.preset = presets[idx];
                                f.applyPreset();
                            } else {
                                f.preset = Filter.P_CUSTOM;
                                if (f.from.isEmpty()) f.from = Jalali.todayStr();
                                if (f.to.isEmpty()) f.to = Jalali.todayStr();
                            }
                            run();
                            renderCustom.run();
                        });
                        LinearLayout.LayoutParams p = kit.lp(-2, -2);
                        p.setMarginEnd(Theme.dp(8));
                        rowHolder[0].addView(ch, p);
                    }
                }
            };
            paintChips.run();
            root.addView(sv, kit.lp(-1, -2));
            root.addView(customBox, kit.lp(-1, -2));
            renderCustom.run();
            root.addView(kit.gap(6));
        }

        // ---- lookups ----
        if (cfg.visitors) addLookup(kit, repo, root, "ویزیتور", f.visitor, MasterQueries::lookupVisitors, id -> f.visitor = id);
        if (cfg.routes) addLookup(kit, repo, root, "مسیر", f.route, MasterQueries::lookupRoutes, id -> f.route = id);
        if (cfg.custGroups) addLookup(kit, repo, root, "گروه مشتری", f.custGroup, MasterQueries::lookupCustGroups, id -> f.custGroup = id);
        if (cfg.kalaGroups) addLookup(kit, repo, root, "گروه کالا", f.kalaGroup, MasterQueries::lookupKalaGroups, id -> f.kalaGroup = id);
        if (cfg.warehouses) addLookup(kit, repo, root, "انبار", f.warehouse, MasterQueries::lookupWarehouses, id -> f.warehouse = id);
        if (cfg.banks) addLookup(kit, repo, root, "بانک", f.bank, MasterQueries::lookupBanks, id -> f.bank = id);

        // ---- status ----
        if (cfg.status != null && cfg.status.length > 0) {
            root.addView(kit.text(cfg.statusTitle, 12.5f, Theme.GOLD_SOFT, true), kit.lp(-1, -2));
            root.addView(kit.gap(6));
            LinearLayout box = kit.h();
            box.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.HorizontalScrollView s2 = new android.widget.HorizontalScrollView(kit.a);
            s2.setHorizontalScrollBarEnabled(false);
            final LinearLayout rr = kit.h();
            s2.addView(rr, new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
            final Runnable paint = new Runnable() {
                @Override
                public void run() {
                    rr.removeAllViews();
                    List<Opt> all = new ArrayList<>();
                    all.add(new Opt("", "همه"));
                    for (Opt o : cfg.status) all.add(o);
                    for (final Opt o : all) {
                        boolean on = (f.status == null ? "" : f.status).equals(o.key);
                        View ch = kit.chip(o.label, on, Theme.INFO, v -> {
                            f.status = o.key;
                            run();
                        });
                        LinearLayout.LayoutParams p = kit.lp(-2, -2);
                        p.setMarginEnd(Theme.dp(8));
                        rr.addView(ch, p);
                    }
                }
            };
            paint.run();
            root.addView(s2, kit.lp(-1, -2));
            root.addView(kit.gap(8));
        }

        // ---- sort ----
        if (cfg.sort != null && cfg.sort.length > 0) {
            root.addView(kit.text(cfg.sortTitle, 12.5f, Theme.GOLD_SOFT, true), kit.lp(-1, -2));
            root.addView(kit.gap(6));
            android.widget.HorizontalScrollView s3 = new android.widget.HorizontalScrollView(kit.a);
            s3.setHorizontalScrollBarEnabled(false);
            final LinearLayout rs = kit.h();
            s3.addView(rs, new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
            final Runnable paint = new Runnable() {
                @Override
                public void run() {
                    rs.removeAllViews();
                    List<Opt> all = new ArrayList<>();
                    all.add(new Opt("", "پیش‌فرض"));
                    for (Opt o : cfg.sort) all.add(o);
                    for (final Opt o : all) {
                        boolean on = (f.sort == null ? "" : f.sort).equals(o.key);
                        View ch = kit.chip(o.label, on, Theme.VIOLET, v -> {
                            f.sort = o.key;
                            run();
                        });
                        LinearLayout.LayoutParams p = kit.lp(-2, -2);
                        p.setMarginEnd(Theme.dp(8));
                        rs.addView(ch, p);
                    }
                }
            };
            paint.run();
            root.addView(s3, kit.lp(-1, -2));
            root.addView(kit.gap(8));
        }

        // ---- actions ----
        LinearLayout actions = kit.h();
        actions.addView(kit.btnGold("✓ اعمال فیلتر", v -> {
            dlg[0].dismiss();
            if (cfg.search) f.search = pendingSearch[0] == null ? "" : pendingSearch[0].trim();
            if (cfg.range && f.preset == Filter.P_CUSTOM && !f.from.isEmpty() && !f.to.isEmpty() && f.from.compareTo(f.to) > 0) {
                String x = f.from;
                f.from = f.to;
                f.to = x;
            }
            f.page = 0;
            done.onApply(f);
        }), kit.wlp(1f));
        actions.addView(kit.space(8));
        actions.addView(kit.btnGhost("پاک‌سازی", Theme.MUTED, v -> {
            dlg[0].dismiss();
            Filter d = new Filter();
            done.onApply(d);
        }), kit.lp(-2, -2));
        root.addView(actions, kit.lp(-1, -2));

        ScrollView sv = new ScrollView(kit.a);
        sv.addView(root, new ScrollView.LayoutParams(-1, -2));
        LinearLayout wrap = kit.v();
        wrap.setPadding(Theme.dp(4), Theme.dp(4), Theme.dp(4), Theme.dp(4));
        wrap.addView(sv, kit.lp(-1, Theme.dp(460)));
        final AlertDialog[] d = {kit.dialog("فیلتر پیشرفته", wrap, true)};
        dlg[0] = d[0];
        d[0].show();
    }

    private static final String[] pendingSearch = {""};
    private static final AlertDialog[] dlg = {null};

    private interface LookupQuery {
        Queries.Q build(Meta m) throws Exception;
    }

    private interface LookupPick {
        void onPick(int id);
    }

    private static void addLookup(final Kit kit, final Repo repo, LinearLayout root, final String title,
                                  int selected, final LookupQuery lq, final LookupPick pick) {
        root.addView(kit.text(title, 12.5f, Theme.GOLD_SOFT, true), kit.lp(-1, -2));
        root.addView(kit.gap(6));
        final LinearLayout box = kit.v();
        box.addView(kit.text("در حال بارگذاری…", 11f, Theme.MUTED, false), kit.lp(-1, -2));
        root.addView(box, kit.lp(-1, -2));
        root.addView(kit.gap(8));
        final int[] sel = {selected};
        repo.run(c -> Repo.exec(c, lq.build(new Meta(c))), new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                box.removeAllViews();
                android.widget.HorizontalScrollView sv = new android.widget.HorizontalScrollView(kit.a);
                sv.setHorizontalScrollBarEnabled(false);
                final LinearLayout r = kit.h();
                sv.addView(r, new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
                final Runnable paint = new Runnable() {
                    @Override
                    public void run() {
                        r.removeAllViews();
                        List<String> ids = new ArrayList<>();
                        List<String> names = new ArrayList<>();
                        ids.add("-1");
                        names.add("همه");
                        for (Row row : rows) {
                            ids.add(row.s("id"));
                            names.add(row.s("name"));
                        }
                        for (int i = 0; i < ids.size(); i++) {
                            final int id;
                            try { id = Integer.parseInt(Money.en(ids.get(i)).trim()); }
                            catch (Exception e) { continue; }
                            boolean on = sel[0] == id;
                            View ch = kit.chip(names.get(i), on, Theme.GOLD, v -> {
                                sel[0] = id;
                                pick.onPick(id);
                                run();
                            });
                            LinearLayout.LayoutParams p = kit.lp(-2, -2);
                            p.setMarginEnd(Theme.dp(8));
                            r.addView(ch, p);
                        }
                    }
                };
                paint.run();
                box.addView(sv, kit.lp(-1, -2));
            }

            @Override
            public void fail(String faError) {
                box.removeAllViews();
                box.addView(kit.text(title + ": " + faError, 10.5f, Theme.DANGER, false), kit.lp(-1, -2));
            }
        });
    }

    /** Jalali y/m/d pickers bound to f.from (isFrom) or f.to. */
    private static View dateRow(Kit kit, String current, final boolean isFrom, final Filter f) {
        int[] j;
        try {
            int day = Jalali.parse(current);
            j = day < 0 ? Jalali.fromDay(Jalali.today()) : Jalali.fromDay(day);
        } catch (Exception e) {
            j = Jalali.fromDay(Jalali.today());
        }
        LinearLayout r = kit.h();
        r.setGravity(Gravity.CENTER);
        final NumberPicker y = picker(kit, 1350, 1480, j[0]);
        final NumberPicker mo = picker(kit, 1, 12, j[1]);
        final NumberPicker d = picker(kit, 1, 31, j[2]);
        NumberPicker.OnValueChangeListener sync = (p, oldV, newV) -> {
            String v = String.format(Locale.US, "%04d/%02d/%02d", y.getValue(), mo.getValue(), d.getValue());
            if (isFrom) f.from = v;
            else f.to = v;
        };
        y.setOnValueChangedListener(sync);
        mo.setOnValueChangedListener(sync);
        d.setOnValueChangedListener(sync);
        if (isFrom) f.from = String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]);
        else f.to = String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]);
        r.addView(wrapPicker(kit, y, "سال"), kit.wlp(1f));
        r.addView(wrapPicker(kit, mo, "ماه"), kit.wlp(1f));
        r.addView(wrapPicker(kit, d, "روز"), kit.wlp(1f));
        return r;
    }

    private static NumberPicker picker(Kit kit, int min, int max, int val) {
        NumberPicker p = new NumberPicker(kit.a);
        p.setMinValue(min);
        p.setMaxValue(max);
        p.setValue(Math.max(min, Math.min(max, val)));
        p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        try {
            int count = max - min + 1;
            String[] disp = new String[count];
            for (int i = 0; i < count; i++) disp[i] = Money.fa(String.valueOf(min + i));
            p.setDisplayedValues(disp);
        } catch (Exception ignored) { }
        return p;
    }

    private static View wrapPicker(Kit kit, NumberPicker p, String label) {
        LinearLayout c = kit.v();
        c.setGravity(Gravity.CENTER);
        c.addView(kit.text(label, 10f, Theme.MUTED, true), kit.lp(-2, -2));
        c.addView(p, kit.lp(-2, Theme.dp(110)));
        return c;
    }

    private abstract static class SimpleWatcher implements android.text.TextWatcher {
        public abstract void onText(String s);

        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) { onText(s.toString()); }

        @Override
        public void afterTextChanged(android.text.Editable s) { }
    }
}
