package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.DeviceId;
import ir.meelano.manager.core.HandoverDb;
import ir.meelano.manager.core.WarehouseWriter;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Pdf;
import ir.meelano.manager.ui.SignView;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invoice handover (v28): sales invoices to walk-in customers and to the
 * distributor, with a gorgeous PDF receipt. The warehouse only DELIVERS
 * here — nothing is typed except who received it.
 */
public class TahvilScreen extends Screen {
    public TahvilScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "tahvil"; }

    @Override
    public String title() { return "تحویل فاکتور"; }

    @Override
    public String glyph() { return "🛻"; }

    @Override
    public int accent() { return Theme.SUCCESS; }

    private List<Row> factors = new ArrayList<>();
    private LinearLayout content;
    /** The list container only, so a live refresh does not rebuild the header. */
    private LinearLayout listBox;
    /** Live refresh interval while the screen is in the foreground. */
    private static final long REFRESH_MS = 30000L;
    private final android.os.Handler watch = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable watchTick;
    /** Day being worked, «YYYY/MM/DD». Empty means "not chosen yet" -> today. */
    private String day = "";

    @Override
    public void render(LinearLayout content) {
        this.content = content;
        if (day == null || day.isEmpty()) day = Jalali.todayStr();
        content.removeAllViews();
        content.addView(topCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        final LinearLayout box = a.kit.v();
        content.addView(dateRow(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(box, a.kit.lp(-1, -2));
        listBox = box;
        load(box);
    }

    /**
     * Day strip. The warehouse works today's invoices, so today is the default; the arrows
     * step to an earlier or later day when a past delivery still has to be recorded.
     */
    private View dateRow() {
        LinearLayout row = a.kit.h();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(a.kit.text("تاریخ فاکتورها", 13f, Theme.TEXT, true), a.kit.wlp(1f));
        row.addView(a.kit.btnGhost("\u25c0", Theme.GOLD, v -> {
            day = Jalali.addDays(day, -1);
            if (content != null) render(content);
        }), new LinearLayout.LayoutParams(Theme.dp(46), -2));
        row.addView(a.kit.space(6));
        boolean isToday = Jalali.todayStr().equals(day);
        TextView lbl = a.kit.text(Jalali.disp(day) + (isToday ? " (امروز)" : ""),
                13f, Theme.GOLD_SOFT, true);
        lbl.setGravity(android.view.Gravity.CENTER);
        row.addView(lbl, a.kit.lp(-2, -2));
        row.addView(a.kit.space(6));
        row.addView(a.kit.btnGhost("\u25b6", Theme.GOLD, v -> {
            if (Jalali.todayStr().equals(day)) return;
            day = Jalali.addDays(day, 1);
            if (content != null) render(content);
        }), new LinearLayout.LayoutParams(Theme.dp(46), -2));
        row.addView(a.kit.space(6));
        row.addView(a.kit.btnGhost("امروز", isToday ? Theme.MUTED : Theme.SUCCESS, v -> {
            day = Jalali.todayStr();
            if (content != null) render(content);
        }), a.kit.lp(-2, -2));
        return row;
    }

    private void load(final LinearLayout box) {
        load(box, false);
    }

    /**
     * @param quiet skip the "loading" placeholder. The live refresh uses this so
     *              the list does not blink every 30 s while the keeper reads it.
     */
    /** Rows for the day plus the hand-over marks, shared and local merged. */
    private static final class Loaded {
        List<Row> rows = new ArrayList<>();
        Set<String> handed = new HashSet<>();
    }

    private void load(final LinearLayout box, final boolean quiet) {
        box.removeAllViews();
        if (!quiet) box.addView(a.kit.hint("در حال دریافت فاکتورها…"), a.kit.lp(-1, -2));
        final String want = day;
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Loaded d = new Loaded();
            d.rows = Repo.exec(c, MasterQueries.whDeliveries(m, want));
            // Shared first, then this device's own marks. Either source alone can
            // be incomplete; together they are correct on every device.
            Set<String> shared = HandoverDb.markedSet(c, want);
            if (shared != null) d.handed.addAll(shared);
            d.handed.addAll(WarehouseWriter.handedOn(a, want));
            return d;
        }, new Repo.Cb<Loaded>() {
            @Override
            public void ok(Loaded d) {
                factors = d.rows == null ? new ArrayList<Row>() : d.rows;
                box.removeAllViews();
                if (factors.isEmpty()) {
                    box.addView(a.kit.empty("فاکتوری برای این تاریخ یافت نشد", null), a.kit.lp(-1, -2));
                    return;
                }
                List<Kit.Kpi> kpis = new ArrayList<>();
                kpis.add(new Kit.Kpi("فاکتورها", Money.fa(String.valueOf(factors.size())), "فقره",
                        Theme.SUCCESS));
                box.addView(a.kit.kpiGrid(kpis, 1), a.kit.lp(-1, -2));
                box.addView(a.kit.gap(10));
                for (Row r : factors) {
                    final Row row = r;
                    String no = row.s("id");
                    boolean handed = d.handed.contains(no);
                    String sub = row.s("cust");
                    if (!row.s("visitor").isEmpty()) sub += " \u2022 " + row.s("visitor");
                    sub += " \u2022 " + Money.fa(Jalali.disp(row.s("dt")));
                    View v = a.kit.personRow("فاکتور " + Money.fa(no),
                            sub.isEmpty() ? "\u2014" : sub,
                            Money.fa(Money.compact(row.d("amount"))) + " تومان",
                            handed ? "\u2713 تحویل شد" : "منتظر تحویل",
                            handed ? Theme.SUCCESS : Theme.GOLD,
                            v2 -> openHandover(row));
                    LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                    p.setMargins(0, 0, 0, Theme.dp(10));
                    box.addView(v, p);
                }
            }

            @Override
            public void fail(String faError) {
                box.removeAllViews();
                box.addView(a.kit.empty("دریافت فاکتورها ممکن نشد", faError), a.kit.lp(-1, -2));
            }
        });
    }

    private View topCard() {
        LinearLayout card = a.kit.card(Theme.SUCCESS);
        card.addView(a.kit.text("🛻 تحویل فاکتور", 17f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.text("فاکتور را به مشتری حضوری یا موزع تحویل بدهید و رسید بگیرید",
                12f, Theme.MUTED, false), a.kit.lp(-1, -2));
        return card;
    }

    // ================= handover =================

    /**
     * Live list. The warehouse has to see a sale the moment it is registered,
     * so while this screen is in the foreground the list re-reads from Atiran on
     * a timer. Only the list is rebuilt — the handover form is a separate dialog
     * and is never disturbed. The timer is dropped the instant we lose focus.
     */
    @Override
    public void onShown() {
        stopWatch();
        watchTick = new Runnable() {
            @Override
            public void run() {
                if (listBox != null && day != null && !day.isEmpty()) load(listBox, true);
                watch.postDelayed(this, REFRESH_MS);
            }
        };
        watch.postDelayed(watchTick, REFRESH_MS);
    }

    @Override
    public void onHidden() {
        stopWatch();
    }

    private void stopWatch() {
        if (watchTick != null) {
            try {
                watch.removeCallbacks(watchTick);
            } catch (Exception ignored) { }
            watchTick = null;
        }
    }

    private void openHandover(final Row f) {
        a.kit.toast("در حال دریافت اقلام…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whDeliveryItems(m, f.s("id")));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> items) {
                handoverDialog(f, items == null ? new ArrayList<Row>() : items);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private void handoverDialog(final Row f, final List<Row> items) {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.kv("فاکتور", Money.fa(f.s("id")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مشتری", f.s("cust").isEmpty() ? "—" : f.s("cust"), Theme.TEXT),
                a.kit.lp(-1, -2));
        if (!f.s("phone").isEmpty())
            body.addView(a.kit.kv("تلفن", Money.fa(f.s("phone")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مبلغ", Money.fa(Money.compact(f.d("amount"))) + " تومان", Theme.TEXT),
                a.kit.lp(-1, -2));
                final boolean[] marked = new boolean[items.size()];
        for (int x = 0; x < marked.length; x++) marked[x] = true;
if (!items.isEmpty()) {
            LinearLayout head = a.kit.h();
            head.addView(a.kit.text("اقلام فاکتور (علامت بزنید)", 13f, Theme.TEXT, true),
                    a.kit.wlp(1f));
            final TextView tally = a.kit.text("", 12f, Theme.MUTED, false);
            head.addView(tally, a.kit.lp(-2, -2));
            body.addView(head, a.kit.lp(-1, -2));
            final LinearLayout itemBox = a.kit.v();
            final Runnable repaint = () -> {
                itemBox.removeAllViews();
                for (int x = 0; x < items.size(); x++) {
                    final int idx = x;
                    final Row it = items.get(x);
                    String qty = it.s("qty");
                    String unit = it.s("unit");
                    String label = it.s("name")
                            + (qty.isEmpty() ? "" : "  × " + Money.fa(qty))
                            + (unit.isEmpty() ? "" : " " + unit);
                    android.widget.CheckBox cb = new android.widget.CheckBox(a);
                    cb.setChecked(marked[idx]);
                    cb.setText(label);
                    cb.setTextColor(Theme.TEXT);
                    cb.setTextSize(13f * Theme.fontScale() * Theme.typeScale());
                    cb.setTypeface(Theme.face(false));
                    try { cb.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); } catch (Exception ignored) { }
                    cb.setOnCheckedChangeListener((btn, on) -> marked[idx] = on);
                    itemBox.addView(cb, a.kit.lp(-1, -2));
                }
                int n = 0;
                for (boolean bb : marked) if (bb) n++;
                tally.setText(Money.fa(String.valueOf(n)) + " از "
                        + Money.fa(String.valueOf(items.size())));
            };
            repaint.run();
            body.addView(itemBox, a.kit.lp(-1, -2));
            LinearLayout allRow = a.kit.h();
            allRow.addView(a.kit.btnGhost("✓ انتخاب همه", Theme.SUCCESS, v3 -> {
                for (int x = 0; x < marked.length; x++) marked[x] = true;
                repaint.run();
            }), a.kit.wlp(1f));
            allRow.addView(a.kit.space(8));
            allRow.addView(a.kit.btnGhost("○ هیچ‌کدام", Theme.MUTED, v4 -> {
                for (int x = 0; x < marked.length; x++) marked[x] = false;
                repaint.run();
            }), a.kit.wlp(1f));
            body.addView(allRow, a.kit.lp(-1, -2));
} else {
            body.addView(a.kit.hint("قلمی برای این فاکتور یافت نشد"), a.kit.lp(-1, -2));
        }
        body.addView(a.kit.gap(8));
        body.addView(a.kit.text("تحویل به", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final String[] mode = {"in"}; // in = walk-in customer, out = distributor
        final String[] distName = {""};
        final String[] distCell = {""};
        LinearLayout row = a.kit.h();
        final android.widget.Button[] bIn = new android.widget.Button[1];
        final android.widget.Button[] bOut = new android.widget.Button[1];
        final Runnable paint = () -> {
            boolean isIn = "in".equals(mode[0]);
            ir.meelano.manager.ui.MeelanoIcons.set(bIn[0], "🧍 مشتری حضوری" + (isIn ? " ✓" : ""));
            ir.meelano.manager.ui.MeelanoIcons.set(bOut[0], "🛻 موزع" + (!isIn ? " ✓" : ""));
            bIn[0].setAlpha(isIn ? 1f : 0.55f);
            bOut[0].setAlpha(!isIn ? 1f : 0.55f);
        };
        final String[] walkName = {f.s("cust")};
        final LinearLayout walkBox = a.kit.v();
        final android.widget.TextView walkLabel = a.kit.text(
                walkName[0].isEmpty() ? "انتخاب نشده" : walkName[0], 12.5f, Theme.TEXT, false);
        walkBox.addView(a.kit.btnGhost("انتخاب مشتری حضوری", Theme.GOLD, vw -> {
            a.kit.toast("در حال دریافت مشتریان…");
            a.repo.run(c -> {
                Meta m = new Meta(c);
                return Repo.exec(c, MasterQueries.whCustomers(m, ""));
            }, new Repo.Cb<List<Row>>() {
                @Override public void ok(List<Row> rows) {
                    if (rows == null || rows.isEmpty()) {
                        a.kit.toast("مشتری‌ای یافت نشد");
                        return;
                    }
                    a.kit.searchPicker("انتخاب مشتری حضوری", rows, new String[]{"name", "cell"},
                            new Kit.PickerListener() {
                                @Override public void onPick(Row r) {
                                    walkName[0] = r.s("name");
                                    walkLabel.setText(walkName[0]);
                                    walkLabel.setTextColor(Theme.TEXT);
                                }
                            }).show();
                }
                @Override public void fail(String faError) { a.kit.toast(faError); }
            });
        }), a.kit.lp(-1, -2));
        walkBox.addView(walkLabel, a.kit.lp(-1, -2));
        final Runnable syncWalk = () -> walkBox.setVisibility("in".equals(mode[0]) ? View.VISIBLE : View.GONE);
        bIn[0] = a.kit.btn("🧍 مشتری حضوری ✓", v -> {
            mode[0] = "in";
            paint.run();
            syncWalk.run();
        });
        bOut[0] = a.kit.btnGhost("🛻 موزع", Theme.GOLD, v -> {
            mode[0] = "out";
            paint.run();
            syncWalk.run();
            pickDistributor(distName, distCell, () -> {
            });
        });
        row.addView(bIn[0], a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(bOut[0], a.kit.wlp(1f));
        body.addView(row, a.kit.lp(-1, -2));
        body.addView(walkBox, a.kit.lp(-1, -2));
        syncWalk.run();
        guessWalkIn(walkName, walkLabel);
        body.addView(a.kit.gap(6));
        // Walk-in customer. Defaults to the standing account of the logged-in user and can
        // be changed to any other customer.
        body.addView(a.kit.gap(6));
        body.addView(a.kit.gap(6));
        final String[] worker = {""};
        body.addView(a.kit.text("تحویل‌دهنده (کارگر)", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final android.widget.TextView workerLabel = a.kit.text("انتخاب نشده", 12.5f, Theme.MUTED, false);
        body.addView(a.kit.btnGhost("کارگر را انتخاب کن", Theme.SUCCESS, v2 -> {
            a.kit.toast("در حال دریافت کارکنان…");
            a.repo.run(c -> {
                Meta m = new Meta(c);
                return Repo.exec(c, MasterQueries.lookupUsers(m));
            }, new Repo.Cb<List<Row>>() {
                @Override public void ok(List<Row> rows) {
                    if (rows == null || rows.isEmpty()) {
                        a.kit.toast("کاربری در آتیران یافت نشد");
                        return;
                    }
                    List<Row> all = new ArrayList<>();
                    Row none = new Row();
                    none.put("id", "");
                    none.put("name", "بدون کارگر");
                    all.add(none);
                    all.addAll(rows);
                    a.kit.searchPicker("انتخاب کارگر", all, new String[]{"name"},
                            new Kit.PickerListener() {
                                @Override public void onPick(Row r) {
                                    worker[0] = r.s("name");
                                    if ("بدون کارگر".equals(worker[0])) worker[0] = "";
                                    workerLabel.setText(worker[0].isEmpty() ? "انتخاب نشده" : worker[0]);
                                    workerLabel.setTextColor(worker[0].isEmpty() ? Theme.MUTED : Theme.TEXT);
                                }
                            }).show();
                }
                @Override public void fail(String faError) { a.kit.toast(faError); }
            });
        }), a.kit.lp(-1, -2));
        body.addView(workerLabel, a.kit.lp(-1, -2));
        body.addView(a.kit.text("مدرک تحویل (اختیاری)", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final android.graphics.Bitmap[] signBmp = {null};
        final android.graphics.Bitmap[] photoBmp = {null};
        final android.widget.TextView proofStatus = a.kit.text("امضا: — • عکس: —",
                12f, Theme.MUTED, false);
        final Runnable paintProof = () -> proofStatus.setText(
                "امضا: " + (signBmp[0] == null ? "—" : "✓")
                        + " • عکس: " + (photoBmp[0] == null ? "—" : "✓"));
        LinearLayout proofRow = a.kit.h();
        proofRow.addView(a.kit.btnGhost("🖊 امضای گیرنده", Theme.GOLD, v -> signDialog(signBmp,
                paintProof)), a.kit.wlp(1f));
        proofRow.addView(a.kit.space(8));
        proofRow.addView(a.kit.btnGhost("📷 عکس تحویل", Theme.GOLD, v -> {
            try {
                a.startPhoto(uri -> {
                    try {
                        photoBmp[0] = loadScaled(uri, 1200);
                        paintProof.run();
                        a.kit.toast(photoBmp[0] == null ? "خواندن عکس ممکن نشد" : "عکس ثبت شد");
                    } catch (Exception e) {
                        a.kit.toast("خواندن عکس ممکن نشد");
                    }
                });
            } catch (Exception e) {
                a.kit.toast("دوربین باز نشد");
            }
        }), a.kit.wlp(1f));
        body.addView(proofRow, a.kit.lp(-1, -2));
        body.addView(proofStatus, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(a.kit.gap(8));
        // The receipt is opt-in. Making the keeper route through a PDF share on every
        // handover was the slowest part of the job; the default path now records the
        // delivery and drops straight back to the invoice list.
        final android.widget.CheckBox pdfBox = new android.widget.CheckBox(a);
        pdfBox.setChecked(false);
        pdfBox.setText("دریافت رسید PDF (اختیاری)");
        pdfBox.setTextColor(Theme.MUTED);
        pdfBox.setTextSize(12.5f * Theme.fontScale() * Theme.typeScale());
        pdfBox.setTypeface(Theme.face(false));
        try { pdfBox.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); } catch (Exception ignored) { }
        body.addView(pdfBox, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btn("ثبت تحویل", v -> {
            String receiver;
            if ("out".equals(mode[0])) {
                if (distName[0].isEmpty()) {
                    a.kit.toast("موزع را انتخاب کنید");
                    return;
                }
                receiver = "موزع: " + distName[0]
                        + (distCell[0].isEmpty() ? "" : " (" + distCell[0] + ")");
            } else {
                receiver = "مشتری حضوری: "
                        + (walkName[0].isEmpty() ? (f.s("cust").isEmpty() ? "—" : f.s("cust")) : walkName[0]);
            }
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            String keeper = AtiranAuth.sessionName(a);
            if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
            // Only the ticked items go on the receipt and into the handover record, so a
            // partly delivered invoice is exactly that - partly delivered.
            List<Row> taken = new ArrayList<>();
            StringBuilder takenText = new StringBuilder();
            for (int x = 0; x < items.size(); x++) {
                if (!marked[x]) continue;
                Row it = items.get(x);
                taken.add(it);
                if (takenText.length() > 0) takenText.append("، ");
                takenText.append(it.s("name"))
                        .append(it.s("qty").isEmpty() ? "" : " × " + it.s("qty"));
            }
            if (taken.isEmpty()) {
                a.kit.toast("هیچ کالایی علامت نخورده است");
                return;
            }
            String workerName = worker[0] == null ? "" : worker[0];
            WarehouseWriter.markHanded(a, f.s("id"), f.s("cust"), receiver, keeper,
                    workerName, takenText.toString());
            // Share the mark so every device — the shop TV included — shows the
            // same tick state. Local storage above is immediate and works offline;
            // this is the part that makes the other devices agree.
            final String shNo = f.s("id");
            final String shCust = f.s("cust");
            final String shReceiver = receiver;
            final String shWorker = workerName;
            final String shItems = takenText.toString();
            final String shKeeper = keeper;
            a.repo.run(c -> {
                HandoverDb.mark(c, Jalali.todayStr(), shNo, shCust, shReceiver,
                        shWorker, shItems, shKeeper, DeviceId.code(a));
                return Boolean.TRUE;
            }, new Repo.Cb<Boolean>() {
                @Override
                public void ok(Boolean v) { }

                @Override
                public void fail(String faError) {
                    a.kit.toast("تحویل ثبت شد، ولی همگام‌سازی با سرور انجام نشد");
                }
            });
            String sub = "مشتری: " + f.s("cust") + "  •  " + receiver
                    + (workerName.isEmpty() ? "" : "  •  کارگر: " + workerName)
                    + "  •  تحویل‌دهنده: " + keeper + "  •  تاریخ: " + Jalali.todayStr()
                    + "  •  مبلغ: " + Money.compact(f.d("amount")) + " تومان";
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("qty", "تعداد", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("unit", "واحد", ReportCatalog.T_TEXT),
            };
            List<Pdf.Img> images = new ArrayList<>();
            if (signBmp[0] != null) {
                savePng("tahvil-" + f.s("id") + "-sign.png", signBmp[0]);
                images.add(new Pdf.Img(signBmp[0], "امضای گیرنده"));
            }
            if (photoBmp[0] != null) {
                savePng("tahvil-" + f.s("id") + "-photo.png", photoBmp[0]);
                images.add(new Pdf.Img(photoBmp[0], "عکس تحویل"));
            }
            // Record first, drop back to the list, then share only if it was asked for.
            if (content != null) render(content);
            a.kit.toast("تحویل فاکتور " + Money.fa(f.s("id")) + " ثبت شد");
            if (pdfBox.isChecked()) {
                if (images.isEmpty()) a.sharePdf("رسید تحویل فاکتور " + f.s("id"), sub, cols, taken);
                else a.sharePdfImages("رسید تحویل فاکتور " + f.s("id"), sub, cols, taken, images);
            }
        }), a.kit.lp(-1, -2));
        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        box[0] = dlg;
        dlg.show();
    }

    private void signDialog(final android.graphics.Bitmap[] out, final Runnable done) {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        b.addView(a.kit.text("گیرنده اینجا امضا کند", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final SignView sign = new SignView(a);
        b.addView(sign, new LinearLayout.LayoutParams(-1, Theme.dp(220)));
        final AlertDialog[] box = new AlertDialog[1];
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btnGhost("پاک", Theme.DANGER, v -> sign.clear()), a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(a.kit.btn("ثبت امضا", v -> {
            if (sign.isEmpty()) {
                a.kit.toast("امضایی کشیده نشده است");
                return;
            }
            out[0] = sign.bitmap();
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            done.run();
        }), a.kit.wlp(1f));
        b.addView(row, a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("امضای گیرنده", b, true);
        box[0].show();
    }

    private android.graphics.Bitmap loadScaled(android.net.Uri uri, int maxDim) {
        java.io.InputStream in = null;
        try {
            in = a.getContentResolver().openInputStream(uri);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeStream(in, null, o);
            try {
                in.close();
            } catch (Exception ignored) { }
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / s > maxDim) s *= 2;
            android.graphics.BitmapFactory.Options o2 =
                    new android.graphics.BitmapFactory.Options();
            o2.inSampleSize = Math.max(1, s);
            in = a.getContentResolver().openInputStream(uri);
            return android.graphics.BitmapFactory.decodeStream(in, null, o2);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) { }
        }
    }

    private void savePng(String name, android.graphics.Bitmap bmp) {
        try {
            java.io.File f = new java.io.File(ShareProvider.shareDir(a), name);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (Exception ignored) { }
    }

    /**
     * Pre-select the standing walk-in account for whoever is logged in.
     *
     * The counter users each keep one («مشتریان ویزیتور محمودی»، «مشتریان ویزیتور نظری»), so
     * walk-in sales do not have to be typed in by hand every time. A match is only used as a
     * default - it stays editable through the picker.
     */
    private void guessWalkIn(String[] out, final android.widget.TextView lbl) {
        String me = AtiranAuth.sessionName(a);
        if (me == null || me.trim().isEmpty()) me = AtiranAuth.sessionUser(a);
        if (me == null || me.trim().isEmpty()) return;
        String[] parts = me.trim().split("\\s+");
        final String last = parts.length > 0 ? parts[parts.length - 1] : "";
        if (last.isEmpty()) return;
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whCustomers(m, last));
        }, new Repo.Cb<List<Row>>() {
            @Override public void ok(List<Row> rows) {
                if (rows == null || rows.isEmpty()) return;
                Row best = null;
                for (Row r : rows) {
                    if (r == null) continue;
                    if (r.s("name").contains("\u0645\u0634\u062a\u0631\u06cc\u0627\u0646 \u0648\u06cc\u0632\u06cc\u062a\u0648\u0631")) { best = r; break; }
                    if (best == null) best = r;
                }
                if (best == null) return;
                out[0] = best.s("name");
                lbl.setText(out[0]);
                lbl.setTextColor(Theme.TEXT);
            }
            @Override public void fail(String faError) { }
        });
    }

    private void pickDistributor(final String[] name, final String[] cell, final Runnable done) {
        a.kit.toast("در حال دریافت موزع‌ها…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whVisitors(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                if (rows == null || rows.isEmpty()) {
                    a.kit.toast("موزعی یافت نشد");
                    return;
                }
                // Only distributors. The warehouse hands loads to distributors, so offering
                // every visitor on file was wrong. Falls back to the full list when no name
                // reads as a distributor, so the picker can never come up empty by mistake.
                List<Row> dist = new ArrayList<>();
                for (Row r : rows) {
                    if (r != null && AtiranAuth.isDistributorName(r.s("name"))) dist.add(r);
                }
                if (dist.isEmpty()) dist = rows;
                a.kit.searchPicker("انتخاب موزع", dist, new String[]{"name", "cell"},
                        new Kit.PickerListener() {
                            @Override public void onPick(Row r) {
                                name[0] = r.s("name");
                                cell[0] = r.s("cell");
                                a.kit.toast("موزع: " + name[0]);
                                done.run();
                            }
                        }).show();
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }
}
