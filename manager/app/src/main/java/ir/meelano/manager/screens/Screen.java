package ir.meelano.manager.screens;

import android.view.View;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.data.Atiran;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.Theme;

import java.util.Map;

/** Base of every management section. */
public abstract class Screen {
    protected final MainActivity a;

    public Screen(MainActivity a) {
        this.a = a;
    }

    public abstract String id();

    public abstract String title();

    public abstract String glyph();

    public abstract int accent();

    public String subtitle() {
        Filter f = filter();
        if (f == null) return "";
        String s = f.rangeFa();
        if (f.search != null && !f.search.trim().isEmpty()) s += " • «" + f.search.trim() + "»";
        return s;
    }

    /** The screen's filter, or null when the screen has none. */
    public Filter filter() {
        return null;
    }

    /** Filter sheet configuration, or null for no filter button. */
    public FilterSheet.Config filterConfig() {
        return null;
    }

    public void applyFilter(Filter f) {
        Filter mine = filter();
        if (mine != null) mine.copyFrom(f);
    }

    /** Full render into the shell's content column. */
    public abstract void render(LinearLayout content);

    /** Return true when the screen consumed the back press (and re-rendered itself). */
    public boolean onBack() {
        return false;
    }

    /** SEND_SMS permission result (screens with a pending send override this). */
    public void onSmsPermission(boolean granted) {
    }

    /**
     * MainActivity came to the foreground. Override to start live refreshing;
     * the default does nothing, so every other screen is untouched.
     */
    public void onShown() {
    }

    /**
     * MainActivity left the foreground. Override to stop any timer started in
     * {@link #onShown()} — nothing may keep running while the user is away.
     */
    public void onHidden() {
    }

    // ---------------- soft parts ----------------
    public interface Soft<T> {
        T run() throws Exception;
    }

    /** Run one part; on failure record a Persian note and return null (screen keeps working). */
    protected <T> T soft(Map<String, String> notes, String key, Soft<T> t) {
        try {
            return t.run();
        } catch (Queries.Missing m) {
            notes.put(key, m.getMessage());
            return null;
        } catch (Exception e) {
            notes.put(key, Atiran.diagnose(e));
            return null;
        }
    }

    protected void renderNotes(LinearLayout content, Map<String, String> notes) {
        if (notes == null || notes.isEmpty()) return;
        LinearLayout c = a.kit.card(Theme.WARNING);
        c.addView(a.kit.text("⚠ بخش‌های در دسترس نیست", 13f, Theme.TEXT, true),
                a.kit.lp(-1, -2));
        for (Map.Entry<String, String> e : notes.entrySet())
            c.addView(a.kit.kv(e.getKey(), e.getValue(), Theme.WARNING), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    /** In-content search row that opens the filter sheet (no-op when the screen has no filter). */
    protected void addSearchRow(LinearLayout content) {
        FilterSheet.Config cfg = filterConfig();
        if (cfg == null || !cfg.search) return;
        String hint = cfg.searchHint == null || cfg.searchHint.isEmpty() ? "جستجو…" : cfg.searchHint;
        content.addView(a.kit.searchBar(hint, v -> a.openFilter()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
    }

    /** Offline banner card above cached data (timestamp + original error). */
    protected void offlineBanner(LinearLayout content, String label, String err) {
        LinearLayout bc = a.kit.card(Theme.WARNING);
        bc.addView(a.kit.text("📴 حالت آفلاین — آخرین داده ذخیره‌شده", 13f, Theme.TEXT, true),
                a.kit.lp(-1, -2));
        if (label != null && !label.isEmpty())
            bc.addView(a.kit.kv("آخرین به‌روزرسانی", label, Theme.WARNING), a.kit.lp(-1, -2));
        if (err != null && !err.isEmpty())
            bc.addView(a.kit.kv("خطا", err, Theme.MUTED), a.kit.lp(-1, -2));
        content.addView(bc, 0);
        content.addView(a.kit.gap(10), 1);
    }

    /** Persian timestamp label for cache saves («… ساعت ۱۲:۳۰»). */
    protected String cacheNow() {
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            String hm = String.format(java.util.Locale.US, "%02d:%02d",
                    c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
            return a.kit.todayLine() + " ساعت " + hm;
        } catch (Exception e) {
            return "";
        }
    }

    protected View heroCard() {
        LinearLayout w = a.kit.v();
        w.addView(a.kit.hero(glyph(), title(), subtitle(), accent()), a.kit.lp(-1, -2));
        if (filterConfig() != null && filter() != null) {
            w.addView(a.kit.gap(8));
            Filter f = filter();
            w.addView(a.kit.filterBar(f.describeFa(), !f.isDefault(), v -> a.openFilter()), a.kit.lp(-1, -2));
        }
        return w;
    }
}
