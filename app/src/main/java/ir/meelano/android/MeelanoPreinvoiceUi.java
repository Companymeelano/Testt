package ir.meelano.android;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * صفحهٔ «پیش‌فاکتور خرید» نسخهٔ یکپارچه — زبان درخشان، ورود سریع با کیبورد/IME و
 * محاسبهٔ زندهٔ قیمت، اختلاف، تخفیف‌های پله‌ای، مالیات و عوارض؛ ثبت در دفترچهٔ محلی و
 * اشتراک PDF/تصویر/اکسل/ورد/CSV/متن با شیت اندروید (شبکه‌های اجتماعی و بلوتوث شامل می‌شود).
 */
final class MeelanoPreinvoiceUi {

    private MeelanoPreinvoiceUi() { }

    private static MeelanoPreinvoice.Draft draft = new MeelanoPreinvoice.Draft();
    private static MeelanoUiHost lastHost;
    private static final List<TextView[]> LINE_REFS = new ArrayList<>();
    private static final List<TextView> TOTAL_REFS = new ArrayList<>();

    static void build(final MeelanoUiHost h, final LinearLayout page, final Runnable rerun) {
        LINE_REFS.clear();
        TOTAL_REFS.clear();
        lastHost = h;
        MeelanoPalette p = h.colors();
        page.addView(MeelanoUiKit.sectionHeader(h, "پیش‌فاکتور خرید",
                "کالاهایی که هنوز فاکتور خریدشان ثبت نشده؛ قیمت، اختلاف، تخفیف پله‌ای، مالیات و عوارض",
                "▤", p.accent), MeelanoUiKit.lp(-1, -2));

        LinearLayout pick = MeelanoUiKit.row(h);
        pick.addView(MeelanoUiKit.button(h, "＋ پیش‌فاکتور تازه", p.accent, false, new Runnable() {
            @Override public void run() { draft = new MeelanoPreinvoice.Draft(); rerun.run(); }
        }), MeelanoUiKit.lp(-2, -2));
        List<MeelanoPreinvoice.Draft> saved = MeelanoPreinvoiceStore.load(h.ctx());
        int shown = 0;
        for (final MeelanoPreinvoice.Draft d : saved) {
            if (shown++ >= 4) break;
            String label = (d.docNo.isEmpty() ? "بی‌شماره" : d.docNo) + " • "
                    + (d.supplierName.isEmpty() ? "بی‌فروشنده" : d.supplierName);
            pick.addView(MeelanoUiKit.button(h, label, p.accent, d.id.equals(draft.id), new Runnable() {
                @Override public void run() { draft = d; rerun.run(); }
            }), MeelanoUiKit.lp(-2, -2));
        }
        if (!draft.id.isEmpty()) {
            pick.addView(MeelanoUiKit.button(h, "حذف سند", p.danger, false, new Runnable() {
                @Override public void run() {
                    MeelanoPreinvoiceStore.delete(h.ctx(), draft.id);
                    draft = new MeelanoPreinvoice.Draft();
                    rerun.run();
                }
            }), MeelanoUiKit.lp(-2, -2));
        }
        page.addView(pick, MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);

        page.addView(parties(h, rerun), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);
        page.addView(lines(h, rerun), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);
        page.addView(totals(h), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);

        LinearLayout acts = MeelanoUiKit.row(h);
        acts.addView(MeelanoUiKit.button(h, "ثبت پیش‌فاکتور در سامانه", p.accent, true, new Runnable() {
            @Override public void run() { save(h, rerun); }
        }), MeelanoUiKit.lp(0, h.dp(44), 1f));
        acts.addView(MeelanoUiKit.button(h, "خروجی و اشتراک", p.accent, false, new Runnable() {
            @Override public void run() { shareDialog(h, rerun); }
        }), MeelanoUiKit.lp(0, h.dp(44), 1f));
        page.addView(acts, MeelanoUiKit.lp(-1, -2));
        refresh(h);
    }

    private static LinearLayout parties(final MeelanoUiHost h, final Runnable rerun) {
        MeelanoPalette p = h.colors();
        LinearLayout c = MeelanoUiKit.card(h, p.accent);
        c.addView(MeelanoUiKit.text(h, "فروشنده و خریدار", 12.5f, p.text, true), MeelanoUiKit.lp(-1, -2));
        c.addView(watch(h, MeelanoUiKit.field(h, "نام فروشنده / تامین‌کننده", draft.supplierName, false),
                new Set() { @Override public void set(String v) { draft.supplierName = v; } }), MeelanoUiKit.lp(-1, -2));
        c.addView(pair(h,
                watch(h, MeelanoUiKit.field(h, "کد فروشنده", draft.supplierCode, false),
                        new Set() { @Override public void set(String v) { draft.supplierCode = v; } }),
                watch(h, MeelanoUiKit.field(h, "شناسه ملی", draft.supplierNatId, true),
                        new Set() { @Override public void set(String v) { draft.supplierNatId = v; } })));
        c.addView(pair(h,
                watch(h, MeelanoUiKit.field(h, "تلفن فروشنده", draft.supplierPhone, true),
                        new Set() { @Override public void set(String v) { draft.supplierPhone = v; } }),
                watch(h, MeelanoUiKit.field(h, "شماره سند", draft.docNo, false),
                        new Set() { @Override public void set(String v) { draft.docNo = v; } })));
        c.addView(watch(h, MeelanoUiKit.field(h, "نشانی فروشنده", draft.supplierAddress, false),
                new Set() { @Override public void set(String v) { draft.supplierAddress = v; } }), MeelanoUiKit.lp(-1, -2));
        c.addView(pair(h,
                watch(h, MeelanoUiKit.field(h, "خریدار (فروشگاه)", draft.buyerName, false),
                        new Set() { @Override public void set(String v) { draft.buyerName = v; } }),
                watch(h, MeelanoUiKit.field(h, "کد خریدار", draft.buyerCode, false),
                        new Set() { @Override public void set(String v) { draft.buyerCode = v; } })));
        c.addView(pair(h,
                watch(h, MeelanoUiKit.field(h, "سررسید (روز)", String.valueOf(draft.dueDays), true),
                        new Set() { @Override public void set(String v) { draft.dueDays = (int) num(v); } }),
                watch(h, MeelanoUiKit.field(h, "تخفیف نقدی پایانی ٪", str(draft.cashDiscPct), true),
                        new Set() { @Override public void set(String v) { draft.cashDiscPct = num(v); } })));
        LinearLayout modes = MeelanoUiKit.row(h);
        modes.addView(MeelanoUiKit.button(h, "مالیات: " + ("incl".equals(draft.taxMode) ? "شامل قیمت" : "افزوده‌شده"),
                p.accent, false, new Runnable() {
            @Override public void run() {
                draft.taxMode = MeelanoPreinvoice.TAX_INCLUSIVE.equals(draft.taxMode)
                        ? MeelanoPreinvoice.TAX_ADD : MeelanoPreinvoice.TAX_INCLUSIVE;
                rerun.run();
            }
        }), MeelanoUiKit.lp(-2, -2));
        modes.addView(MeelanoUiKit.button(h, "گردکردن: " + roundFa(), p.accent, false, new Runnable() {
            @Override public void run() { draft.rounding = nextRounding(); rerun.run(); }
        }), MeelanoUiKit.lp(-2, -2));
        c.addView(modes, MeelanoUiKit.lp(-1, -2));
        c.addView(MeelanoUiKit.text(h, "تاریخ سند: " + MeelanoPreinvoiceExport.dateFa(draft.dateTs),
                10.5f, p.muted, false), MeelanoUiKit.lp(-1, -2));
        return c;
    }

    private static LinearLayout lines(final MeelanoUiHost h, final Runnable rerun) {
        MeelanoPalette p = h.colors();
        LinearLayout c = MeelanoUiKit.card(h, p.accent);
        c.addView(MeelanoUiKit.text(h, "اقلام کالا", 12.5f, p.text, true), MeelanoUiKit.lp(-1, -2));
        if (draft.lines.isEmpty()) {
            c.addView(MeelanoUiKit.emptyState(h, "▤", "هنوز قلمی ثبت نشده",
                    "با «افزودن قلم» اولین کالا را وارد کنید.", null, null), MeelanoUiKit.lp(-1, -2));
        }
        for (int i = 0; i < draft.lines.size(); i++) c.addView(lineBlock(h, i, rerun), MeelanoUiKit.lp(-1, -2));
        c.addView(MeelanoUiKit.button(h, "＋ افزودن قلم", p.accent, false, new Runnable() {
            @Override public void run() { draft.lines.add(new MeelanoPreinvoice.Line()); rerun.run(); }
        }), MeelanoUiKit.lp(-1, -2));
        return c;
    }

    private static LinearLayout lineBlock(final MeelanoUiHost h, final int i, final Runnable rerun) {
        final MeelanoPalette p = h.colors();
        final MeelanoPreinvoice.Line l = draft.lines.get(i);
        LinearLayout box = MeelanoUiKit.card(h, p.accent);
        box.addView(MeelanoUiKit.text(h, "قلم " + h.fa(String.valueOf(i + 1)), 11.5f, p.text, true),
                MeelanoUiKit.lp(-1, -2));
        box.addView(pair(h,
                watch(h, MeelanoUiKit.field(h, "کد کالا", l.code, false),
                        new Set() { @Override public void set(String v) { l.code = v; } }),
                watch(h, MeelanoUiKit.field(h, "نام کالا", l.name, false),
                        new Set() { @Override public void set(String v) { l.name = v; } })));
        box.addView(pair(h,
                watch(h, MeelanoUiKit.field(h, "واحد شمارش", l.unit, false),
                        new Set() { @Override public void set(String v) { l.unit = v; } }),
                watch(h, MeelanoUiKit.field(h, "تعداد", str(l.qty), true),
                        new Set() { @Override public void set(String v) { l.qty = num(v); } })));
        box.addView(pair(h,
                watch(h, MeelanoUiKit.field(h, "قیمت واحد (ریال)", str(l.price), true),
                        new Set() { @Override public void set(String v) { l.price = num(v); } }),
                watch(h, MeelanoUiKit.field(h, "قیمت مرجع (ریال)", str(l.refPrice), true),
                        new Set() { @Override public void set(String v) { l.refPrice = num(v); } })));
        box.addView(pair(h,
                watch(h, MeelanoUiKit.field(h, "مالیات ٪", str(l.taxPct), true),
                        new Set() { @Override public void set(String v) { l.taxPct = num(v); } }),
                watch(h, MeelanoUiKit.field(h, "عوارض ٪", str(l.dutyPct), true),
                        new Set() { @Override public void set(String v) { l.dutyPct = num(v); } })));
        LinearLayout act = MeelanoUiKit.row(h);
        act.addView(MeelanoUiKit.button(h, "تخفیف‌ها (" + h.fa(String.valueOf(l.discs.size())) + ")", p.accent2, false,
                new Runnable() { @Override public void run() { discountDialog(h, l, rerun); } }),
                MeelanoUiKit.lp(-2, -2));
        act.addView(MeelanoUiKit.button(h, "حذف قلم", p.danger, false, new Runnable() {
            @Override public void run() { draft.lines.remove(i); rerun.run(); }
        }), MeelanoUiKit.lp(-2, -2));
        box.addView(act, MeelanoUiKit.lp(-1, -2));
        TextView total = MeelanoUiKit.text(h, "", 11.5f, p.text, true);
        TextView diff = MeelanoUiKit.text(h, "", 10.5f, p.muted, false);
        box.addView(total, MeelanoUiKit.lp(-1, -2));
        box.addView(diff, MeelanoUiKit.lp(-1, -2));
        LINE_REFS.add(new TextView[]{total, diff});
        return box;
    }

    private static void discountDialog(final MeelanoUiHost h, final MeelanoPreinvoice.Line l, final Runnable rerun) {
        LinearLayout body = MeelanoUiKit.column(h);
        final Spinner type = new Spinner(h.ctx());
        String[] names = {"درصد ردیف", "ریالی هر واحد", "پله‌ای تعداد", "پله‌ای مبلغ", "درصد کل فاکتور", "کد تخفیف"};
        type.setAdapter(new ArrayAdapter<>(h.ctx(), android.R.layout.simple_spinner_dropdown_item, names));
        body.addView(type, MeelanoUiKit.lp(-1, -2));
        final EditText v1 = MeelanoUiKit.field(h, "درصد یا مبلغ هر واحد", "", true);
        body.addView(v1, MeelanoUiKit.lp(-1, -2));
        final EditText v2 = MeelanoUiKit.field(h, "سقف ریالی (کد تخفیف؛ ۰ = بدون سقف)", "", true);
        body.addView(v2, MeelanoUiKit.lp(-1, -2));
        final EditText spec = MeelanoUiKit.field(h, "پله‌ها مانند 10:5,50:10 یا نام کد", "", false);
        body.addView(spec, MeelanoUiKit.lp(-1, -2));
        body.addView(MeelanoUiKit.text(h, "ترتیب: ریالی هر واحد ← پله‌ای تعداد ← پله‌ای مبلغ ← درصد ردیف ← کد؛ درصد کل پس از همه روی سند.",
                10f, h.colors().muted, false), MeelanoUiKit.lp(-1, -2));
        for (int d = 0; d < l.discs.size(); d++) {
            final int at = d;
            body.addView(MeelanoUiKit.button(h, l.discs.get(d).type.fa + " • حذف", h.colors().danger, false,
                    new Runnable() { @Override public void run() { l.discs.remove(at); rerun.run(); } }),
                    MeelanoUiKit.lp(-1, -2));
        }
        body.addView(MeelanoUiKit.button(h, "افزودن تخفیف", h.colors().accent, true, new Runnable() {
            @Override public void run() {
                MeelanoPreinvoice.DiscType t = MeelanoPreinvoice.DiscType.values()[type.getSelectedItemPosition()];
                l.discs.add(new MeelanoPreinvoice.Disc(t, num(v1.getText().toString()),
                        num(v2.getText().toString()), spec.getText().toString().trim()));
                rerun.run();
            }
        }), MeelanoUiKit.lp(-1, -2));
        ScrollView sv = new ScrollView(h.ctx());
        sv.addView(body);
        new AlertDialog.Builder(h.ctx()).setView(sv).create().show();
    }

    private static LinearLayout totals(MeelanoUiHost h) {
        MeelanoPalette p = h.colors();
        LinearLayout c = MeelanoUiKit.card(h, p.accent);
        c.addView(MeelanoUiKit.text(h, "جمع‌بندی سند", 12.5f, p.text, true), MeelanoUiKit.lp(-1, -2));
        for (int i = 0; i < 8; i++) {
            TextView t = MeelanoUiKit.text(h, "—", 11.5f, p.text, false);
            c.addView(t, MeelanoUiKit.lp(-1, -2));
            TOTAL_REFS.add(t);
        }
        return c;
    }

    private static void refresh(MeelanoUiHost h) {
        MeelanoPalette p = h.colors();
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(draft);
        for (int i = 0; i < draft.lines.size() && i < LINE_REFS.size(); i++) {
            MeelanoPreinvoice.Line l = draft.lines.get(i);
            TextView[] r = LINE_REFS.get(i);
            r[0].setText("جمع قلم: " + h.fa(String.valueOf(Math.round(l.total))) + " ریال");
            if (l.refPrice > 0) {
                r[1].setText("اختلاف با مرجع: " + h.fa(String.valueOf(Math.round(l.diffAmt))) + " ریال ("
                        + h.fa(String.format(Locale.US, "%.1f", l.diffPct)) + "٪)");
                r[1].setTextColor(l.diffAmt > 0 ? p.danger : l.diffAmt < 0 ? p.success : p.muted);
            } else {
                r[1].setText("قیمت مرجع وارد نشده؛ اختلاف محاسبه نمی‌شود.");
                r[1].setTextColor(p.muted);
            }
        }
        String[] vals = {
                "جمع اقلام: " + h.fa(String.valueOf(Math.round(t.goods))) + " ریال",
                "تخفیفات: " + h.fa(String.valueOf(Math.round(t.discSum))) + " ریال ("
                        + h.fa(String.format(Locale.US, "%.1f", t.discPct)) + "٪)",
                "مالیات: " + h.fa(String.valueOf(Math.round(t.tax))) + " ریال",
                "عوارض: " + h.fa(String.valueOf(Math.round(t.duty))) + " ریال",
                "جمع کل: " + h.fa(String.valueOf(Math.round(t.grand))) + " ریال",
                "تخفیف نقدی پایانی: " + h.fa(String.valueOf(Math.round(t.cashDisc))) + " ریال",
                "قابل پرداخت: " + h.fa(String.valueOf(Math.round(t.payable))) + " ریال",
                "اختلاف کل با مرجع: " + h.fa(String.valueOf(Math.round(t.diff))) + " ریال"};
        for (int i = 0; i < TOTAL_REFS.size() && i < vals.length; i++) TOTAL_REFS.get(i).setText(vals[i]);
    }

    private static void save(MeelanoUiHost h, Runnable rerun) {
        if (draft.supplierName.trim().isEmpty()) { h.notify("نام فروشنده را وارد کنید"); return; }
        if (draft.lines.isEmpty()) { h.notify("دست‌کم یک قلم کالا لازم است"); return; }
        MeelanoPreinvoiceStore.save(h.ctx(), draft);
        h.notify("پیش‌فاکتور در دفترچهٔ محلی برنامه ثبت شد");
        rerun.run();
    }

    private static void shareDialog(final MeelanoUiHost h, final Runnable rerun) {
        final MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(draft);
        LinearLayout body = MeelanoUiKit.column(h);
        final String[][] opts = {{"PDF چاپ", "pdf"}, {"تصویر PNG", "png"}, {"اکسل", "xls"},
                {"ورد", "doc"}, {"CSV", "csv"}, {"متن گفتگو", "txt"}};
        for (final String[] o : opts) {
            body.addView(MeelanoUiKit.button(h, o[0], h.colors().accent, false, new Runnable() {
                @Override public void run() { share(h, o[1], t, rerun); }
            }), MeelanoUiKit.lp(-1, -2));
        }
        body.addView(MeelanoUiKit.text(h, "شیت اشتراک اندروید: شبکه‌های اجتماعی نصب‌شده، بلوتوث، ایمیل و همهٔ گزینه‌ها.",
                10f, h.colors().muted, false), MeelanoUiKit.lp(-1, -2));
        ScrollView sv = new ScrollView(h.ctx());
        sv.addView(body);
        new AlertDialog.Builder(h.ctx()).setView(sv).create().show();
    }

    private static void share(final MeelanoUiHost h, String kind, MeelanoPreinvoice.Totals t, Runnable rerun) {
        String title = "پیش‌فاکتور خرید " + draft.docNo;
        if ("pdf".equals(kind)) {
            try {
                File out = new File(h.ctx().getCacheDir(), "cheque-preinvoice-report.pdf");
                FileOutputStream fos = new FileOutputStream(out);
                MeelanoDailyReportPdf.write(h.ctx(), MeelanoPreinvoiceExport.pdfData(draft, t),
                        h.font(false), h.font(true), fos);
                fos.close();
                byte[] bytes = java.nio.file.Files.readAllBytes(out.toPath());
                h.shareGeneratedFile("preinvoice-" + draft.docNo + ".pdf", "application/pdf", bytes, title);
                markSent(h, rerun);
            } catch (Exception e) {
                h.notify("ساخت PDF ممکن نشد: " + e.getMessage());
            }
            return;
        }
        if ("png".equals(kind)) {
            try {
                File png = renderPng(h, t);
                byte[] bytes = java.nio.file.Files.readAllBytes(png.toPath());
                h.shareGeneratedFile("preinvoice-" + draft.docNo + ".png", "image/png", bytes, title);
                markSent(h, rerun);
            } catch (Exception e) {
                h.notify("ساخت تصویر ممکن نشد: " + e.getMessage());
            }
            return;
        }
        try {
            byte[] data; String name; String mime;
            if ("xls".equals(kind)) { data = MeelanoPreinvoiceExport.xls(draft, t); name = "preinvoice.xls"; mime = "application/vnd.ms-excel"; }
            else if ("doc".equals(kind)) { data = MeelanoPreinvoiceExport.doc(draft, t); name = "preinvoice.doc"; mime = "application/msword"; }
            else if ("csv".equals(kind)) { data = MeelanoPreinvoiceExport.csv(draft, t); name = "preinvoice.csv"; mime = "text/csv"; }
            else { data = MeelanoPreinvoiceExport.summary(draft, t).getBytes("UTF-8"); name = "preinvoice.txt"; mime = "text/plain"; }
            h.shareGeneratedFile(name, mime, data, title);
            markSent(h, rerun);
        } catch (Exception e) {
            h.notify("ساخت فایل ممکن نشد: " + e.getMessage());
        }
    }

    /** تصویر PNG خود سند: پس‌زمینهٔ تم + سطرها با قلم برنامه، بدون هیچ کتابخانهٔ بیرونی. */
    private static File renderPng(MeelanoUiHost h, MeelanoPreinvoice.Totals t) throws Exception {
        String[] lines = MeelanoPreinvoiceExport.summary(draft, t).split("\n");
        float scale = h.ctx().getResources().getDisplayMetrics().density;
        int w = Math.round(720 * scale);
        int lineH = Math.round(34 * scale);
        int pad = Math.round(28 * scale);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w,
                pad * 2 + lineH * lines.length, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        MeelanoPalette p = h.colors();
        cv.drawColor(p.light ? 0xFFF7F3EA : 0xFF04120D);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(p.text);
        paint.setTextSize(20 * scale);
        paint.setTypeface(h.font(false));
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) { paint.setTypeface(h.font(true)); paint.setTextSize(24 * scale); }
            cv.drawText(lines[i], pad, pad + lineH * (i + 1) - 10 * scale, paint);
        }
        File out = new File(h.ctx().getCacheDir(), "preinvoice.png");
        FileOutputStream fos = new FileOutputStream(out);
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
        return out;
    }

    private static void markSent(MeelanoUiHost h, Runnable rerun) {
        if (!draft.id.isEmpty()) {
            draft.status = "sent";
            MeelanoPreinvoiceStore.save(h.ctx(), draft);
        }
    }

    // --------------------------------------------------------------- ابزارها
    private interface Set { void set(String v); }

    private static EditText watch(MeelanoUiHost h, EditText e, final Set set) {
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                set.set(s.toString().trim());
                if (lastHost != null) refresh(lastHost);
            }
        });
        return e;
    }

    private static LinearLayout pair(MeelanoUiHost h, EditText a1, EditText a2) {
        LinearLayout r = MeelanoUiKit.row(h);
        r.addView(a1, MeelanoUiKit.lp(0, -2, 1f));
        r.addView(a2, MeelanoUiKit.lp(0, -2, 1f));
        return r;
    }

    private static String roundFa() {
        if ("toman".equals(draft.rounding)) return "تومان";
        if ("hundred".equals(draft.rounding)) return "صد ریال";
        if ("thousand".equals(draft.rounding)) return "هزار ریال";
        if ("rial".equals(draft.rounding)) return "ریال";
        return "بدون گرد";
    }

    private static String nextRounding() {
        String[] r = MeelanoPreinvoice.ROUNDINGS;
        for (int i = 0; i < r.length; i++) if (r[i].equals(draft.rounding)) return r[(i + 1) % r.length];
        return r[0];
    }

    private static double num(String s) {
        try { return Double.parseDouble(s == null ? "" : s.replace(",", "").trim()); }
        catch (RuntimeException e) { return 0; }
    }

    private static String str(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }
}
