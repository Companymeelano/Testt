package ir.meelano.manager.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import ir.meelano.manager.core.CacheStore;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dues calendar: every cheque + invoice due, on a Jalali month grid. Tap a day for details. */
public class DuesScreen extends Screen {
    private static final String[] WD = {"شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"};

    private int jy;
    private int jm;
    private LinearLayout contentRef;
    private List<Row> monthRows = new ArrayList<>();

    public DuesScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "dues"; }

    @Override
    public String title() { return "تقویم سررسید"; }

    @Override
    public String glyph() { return "📅"; }

    @Override
    public int accent() { return Theme.WARNING; }

    @Override
    public void render(final LinearLayout content) {
        contentRef = content;
        if (jy <= 0) {
            try {
                String t = Jalali.todayStr();
                jy = Integer.parseInt(t.substring(0, 4));
                jm = Integer.parseInt(t.substring(5, 7));
            } catch (Exception e) {
                jy = 1404;
                jm = 1;
            }
        }
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(navRow(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.loading("در حال دریافت سررسیدها…"), a.kit.lp(-1, -2));

        final String from = fmt(jy, jm, 1);
        final String to = fmt(jy, jm, Jalali.daysInMonth(jy, jm));
        final Map<String, String> notes = new LinkedHashMap<>();
        a.repo.run(c -> {
            Meta m = new Meta(c);
            List<Row> all = new ArrayList<>();
            List<Row> in = soft(notes, "دریافتی", () -> Repo.exec(c, MasterQueries.duesRaw(m, true, from, to)));
            List<Row> out = soft(notes, "پرداختی", () -> Repo.exec(c, MasterQueries.duesRaw(m, false, from, to)));
            List<Row> inv = soft(notes, "فاکتور", () -> Repo.exec(c, MasterQueries.duesInvRaw(m, from, to)));
            if (in != null) all.addAll(in);
            if (out != null) all.addAll(out);
            if (inv != null) all.addAll(inv);
            return all;
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
java.util.Map<String, Object> sm = new java.util.LinkedHashMap<>();
                sm.put("rows", rows);
                CacheStore.saveData(a, "cx_dues_" + jy + "_" + jm, cacheNow(), sm);
                monthRows = rows == null ? new ArrayList<>() : rows;
                buildGrid(content);
                renderNotes(content, notes);
            }

            @Override
            public void fail(String faError) {
                String[] lab = {""};
                java.util.Map<String, Object> cm = CacheStore.loadData(a, "cx_dues_" + jy + "_" + jm, lab);
                if (cm != null) {
                    monthRows = CacheStore.rows(cm, "rows");
                    buildGrid(content);
                    offlineBanner(content, lab[0], faError);
                    return;
                }
                content.removeAllViews();
                content.addView(heroCard(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(10));
                content.addView(navRow(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(10));
                content.addView(a.kit.error(faError == null ? "دریافت سررسیدها ممکن نشد" : faError,
                        () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    private static String fmt(int y, int m, int d) {
        return String.format(Locale.US, "%04d/%02d/%02d", y, m, d);
    }

    /** Normalize any stored form (Jalali text / Gregorian datetime) to «YYYY/MM/DD». */
    private static String day10(String raw) {
        try {
            String d = Jalali.disp(raw == null ? "" : raw);
            return d.length() >= 10 ? d.substring(0, 10) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private View navRow() {
        LinearLayout r = a.kit.h();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(a.kit.btnGhost("› قبل", Theme.GOLD, v -> {
            jm--;
            if (jm < 1) { jm = 12; jy--; }
            if (contentRef != null) render(contentRef);
        }), a.kit.lp(-2, -2));
        r.addView(a.kit.space(8));
        String mt = "";
        try {
            mt = Jalali.MONTHS[jm - 1] + " " + Money.fa(String.valueOf(jy));
        } catch (Exception ignored) { }
        TextView t = a.kit.text(mt, 15f, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        r.addView(t, a.kit.wlp(1f));
        r.addView(a.kit.space(8));
        r.addView(a.kit.btnGhost("امروز", Theme.TEAL, v -> {
            jy = 0;
            if (contentRef != null) render(contentRef);
        }), a.kit.lp(-2, -2));
        r.addView(a.kit.space(8));
        r.addView(a.kit.btnGhost("بعد ‹", Theme.GOLD, v -> {
            jm++;
            if (jm > 12) { jm = 1; jy++; }
            if (contentRef != null) render(contentRef);
        }), a.kit.lp(-2, -2));
        return r;
    }

    private void buildGrid(LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(navRow(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // summary
        double sum = 0;
        int overdueN = 0;
        String today = Jalali.todayStr();
        Map<String, List<Row>> byDay = new LinkedHashMap<>();
        for (Row r : monthRows) {
            String d = day10(r.s("sardate"));
            if (d.isEmpty()) continue;
            List<Row> l = byDay.get(d);
            if (l == null) { l = new ArrayList<>(); byDay.put(d, l); }
            l.add(r);
            sum += r.d("amount");
            if (d.compareTo(today) < 0) overdueN++;
        }
        LinearLayout sc = a.kit.card(Theme.GOLD);
        sc.addView(a.kit.kv("سررسید این ماه", Money.fa(String.valueOf(monthRows.size())) + " مورد", Theme.TEXT), a.kit.lp(-1, -2));
        sc.addView(a.kit.kv("جمع مبالغ", Money.compactRial(sum), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        if (overdueN > 0)
            sc.addView(a.kit.kv("معوق (روزهای گذشته)", Money.fa(String.valueOf(overdueN)) + " مورد", Theme.DANGER), a.kit.lp(-1, -2));
        a.kit.addCard(content, sc);

        // weekday header
        LinearLayout cal = a.kit.card(Theme.WARNING);
        LinearLayout hw = a.kit.h();
        for (String w : WD) {
            TextView h = a.kit.text(w, 10f, Theme.MUTED, true);
            h.setGravity(Gravity.CENTER);
            hw.addView(h, a.kit.wlp(1f));
        }
        cal.addView(hw, a.kit.lp(-1, -2));
        cal.addView(a.kit.gap(6));

        int dim = Jalali.daysInMonth(jy, jm);
        int lead = Jalali.weekdayIndex(fmt(jy, jm, 1));
        if (lead < 0) lead = 0;
        LinearLayout row = null;
        int cells = 0;
        for (int i = 0; i < lead + dim; i++) {
            if (cells % 7 == 0) {
                row = a.kit.h();
                LinearLayout.LayoutParams rp = a.kit.lp(-1, -2);
                if (cells > 0) rp.setMargins(0, Theme.dp(6), 0, 0);
                cal.addView(row, rp);
            }
            if (i < lead) {
                row.addView(a.kit.space(1), a.kit.wlp(1f));
            } else {
                final String day = fmt(jy, jm, i - lead + 1);
                final List<Row> dl = byDay.get(day);
                int n = dl == null ? 0 : dl.size();
                boolean isToday = day.equals(today);
                boolean overdue = n > 0 && day.compareTo(today) < 0;
                LinearLayout cell = a.kit.v();
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(0, Theme.dp(7), 0, Theme.dp(7));
                cell.setBackground(n == 0 ? Theme.card()
                        : overdue ? Theme.cardAccent(Theme.DANGER) : Theme.cardAccent(Theme.GOLD));
                TextView dn = a.kit.text(Money.fa(String.valueOf(i - lead + 1)),
                        13f, isToday ? Theme.GOLD : Theme.TEXT, isToday || n > 0);
                dn.setGravity(Gravity.CENTER);
                cell.addView(dn, a.kit.lp(-1, -2));
                if (n > 0) {
                    double ds = 0;
                    for (Row r : dl) ds += r.d("amount");
                    TextView nn = a.kit.text(Money.fa(String.valueOf(n)) + " • " + Money.compactRial(ds),
                            9f, overdue ? Theme.DANGER : Theme.GOLD_SOFT, true);
                    nn.setGravity(Gravity.CENTER);
                    nn.setSingleLine(true);
                    cell.addView(nn, a.kit.lp(-1, -2));
                    Theme.pressable(cell);
                    cell.setOnClickListener(v -> dayDialog(day, dl));
                } else {
                    TextView nn = a.kit.text("—", 9f, Theme.MUTED, false);
                    nn.setGravity(Gravity.CENTER);
                    cell.addView(nn, a.kit.lp(-1, -2));
                }
                LinearLayout.LayoutParams cp = a.kit.wlp(1f);
                if (cells % 7 != 6) cp.setMarginEnd(Theme.dp(6));
                row.addView(cell, cp);
            }
            cells++;
        }
        a.kit.addCard(content, cal);
        content.addView(a.kit.hint("فقط سررسیدهای باز (وصول/پاس‌نشده) نمایش داده می‌شود • لمس روز: جزئیات"), a.kit.lp(-1, -2));
    }

    private void dayDialog(String day, List<Row> dl) {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.text(Jalali.dispFa(day), 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(8));
        double sum = 0;
        for (Row r : dl) {
            sum += r.d("amount");
            String k = r.s("kind");
            String kl = "in".equals(k) ? "چک دریافتی" : "out".equals(k) ? "چک پرداختی" : "فاکتور";
            int kc = "in".equals(k) ? Theme.SUCCESS : "out".equals(k) ? Theme.WARNING : Theme.INFO;
            LinearLayout card = a.kit.card(kc);
            card.addView(a.kit.text(kl + " • " + Money.fa(r.s("num")), 12.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            card.addView(a.kit.text(r.s("party"), 11.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
            card.addView(a.kit.kv("مبلغ", Money.rial(r.d("amount")), kc), a.kit.lp(-1, -2));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            body.addView(card, p);
        }
        body.addView(a.kit.kv("جمع روز", Money.rial(sum), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        a.kit.dialog("سررسیدهای روز", a.kit.scrollWrap(body, 520), true).show();
    }
}
