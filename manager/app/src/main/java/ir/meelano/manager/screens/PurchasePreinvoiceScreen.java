package ir.meelano.manager.screens;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.PreinvoiceExport;
import ir.meelano.manager.core.PurchasePreinvoice;
import ir.meelano.manager.core.PurchasePreinvoiceStore;
import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

/**
 * «پیش‌فاکتور خرید» — ثبت کالاهایی که هنوز فاکتور خریدشان در سامانه نخورده است.
 *
 * <p>قیمت هر قلم محاسبه می‌شود، اختلاف با قیمت مرجع لحظه‌ای دیده می‌شود، شش نوع تخفیف
 * (دو مورد پله‌ای) به ترتیب حسابداری اعمال می‌گردد و مالیات/عوارض و تخفیف نقدی پایانی
 * جمع کل را می‌سازند. سند در دفترچهٔ محلی برنامه ثبت می‌شود و با PDF/تصویر/اکسل/ورد/CSV
 * و شیت اشتراک اندروید (شبکه‌های اجتماعی، بلوتوث، ایمیل…) ارسال می‌شود.</p>
 */
public class PurchasePreinvoiceScreen extends Screen {

    private PurchasePreinvoice.Draft draft = new PurchasePreinvoice.Draft();
    private LinearLayout live;
    private final List<LineRefs> lineRefs = new ArrayList<>();
    private final List<TextView> totalRefs = new ArrayList<>();

    /** نگه‌دارندهٔ اشاره‌گرهای زندهٔ هر ردیف برای به‌روزرسانی بدون از دست رفتن فوکوس. */
    private static final class LineRefs {
        TextView total, diff;
    }

    public PurchasePreinvoiceScreen(MainActivity a) {
        super(a);
    }

    @Override public String id() { return "preinvoice"; }
    @Override public String title() { return "پیش‌فاکتور خرید"; }
    @Override public String glyph() { return "▤"; }
    @Override public int accent() { return Theme.INFO; }

    @Override
    public void render(LinearLayout content) {
        content.removeAllViews();
        lineRefs.clear();
        totalRefs.clear();
        live = content;

        LinearLayout head = a.kit.card(Theme.INFO);
        head.addView(a.kit.text("▤ پیش‌فاکتور خرید", 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
        head.addView(a.kit.hint("کالاهایی که هنوز فاکتور خریدشان ثبت نشده؛ قیمت، اختلاف، تخفیف پله‌ای، مالیات و عوارض در یک سند."),
                a.kit.lp(-1, -2));
        head.addView(a.kit.gap(8));
        head.addView(draftPicker(), a.kit.lp(-1, -2));
        content.addView(head, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        content.addView(partiesCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(linesCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(totalsCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(actionsRow(), a.kit.lp(-1, -2));
        refreshNumbers();
    }

    // --------------------------------------------------------------- سربرگ و دفترچه
    private LinearLayout draftPicker() {
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btnGhost("＋ پیش‌فاکتور تازه", Theme.INFO, v -> {
            draft = new PurchasePreinvoice.Draft();
            render(live);
        }), a.kit.lp(-2, -2));
        List<PurchasePreinvoice.Draft> saved = PurchasePreinvoiceStore.load(a);
        int shown = 0;
        for (final PurchasePreinvoice.Draft d : saved) {
            if (shown++ >= 5) break;
            String label = (d.docNo.isEmpty() ? "بی‌شماره" : d.docNo) + " • "
                    + (d.supplierName.isEmpty() ? "بی‌فروشنده" : d.supplierName);
            row.addView(a.kit.chip(label, d.id.equals(draft.id), Theme.INFO, v -> {
                draft = d;
                render(live);
            }), a.kit.lp(-2, -2));
        }
        if (!draft.id.isEmpty()) {
            row.addView(a.kit.btnGhost("حذف این سند", Theme.DANGER, v -> {
                PurchasePreinvoiceStore.delete(a, draft.id);
                draft = new PurchasePreinvoice.Draft();
                render(live);
            }), a.kit.lp(-2, -2));
        }
        return row;
    }

    private LinearLayout partiesCard() {
        LinearLayout c = a.kit.card(Theme.INFO);
        c.addView(a.kit.text("فروشنده و خریدار", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        c.addView(field("نام فروشنده / تامین‌کننده", draft.supplierName, v -> draft.supplierName = v, false));
        c.addView(pair(field("کد فروشنده", draft.supplierCode, v -> draft.supplierCode = v, false),
                field("شناسه ملی", draft.supplierNatId, v -> draft.supplierNatId = v, true)));
        c.addView(pair(field("تلفن فروشنده", draft.supplierPhone, v -> draft.supplierPhone = v, true),
                field("شماره سند", draft.docNo, v -> draft.docNo = v, false)));
        c.addView(field("نشانی فروشنده", draft.supplierAddress, v -> draft.supplierAddress = v, false));
        c.addView(pair(field("خریدار (فروشگاه)", draft.buyerName, v -> draft.buyerName = v, false),
                field("کد خریدار", draft.buyerCode, v -> draft.buyerCode = v, false)));
        c.addView(pair(field("سررسید (روز)", String.valueOf(draft.dueDays), v -> draft.dueDays = (int) num(v), true),
                field("تخفیف نقدی پایانی (٪)", str(draft.cashDiscPct), v -> draft.cashDiscPct = num(v), true)));
        c.addView(a.kit.gap(6));
        LinearLayout modes = a.kit.h();
        modes.addView(a.kit.text("مالیات: " + ("incl".equals(draft.taxMode) ? "قیمت شامل مالیات" : "افزوده‌شده"),
                11.5f, Theme.MUTED, false), a.kit.lp(-2, -2));
        modes.addView(a.kit.btnGhost("تغییر حالت مالیات", Theme.INFO, v -> {
            draft.taxMode = "incl".equals(draft.taxMode) ? PurchasePreinvoice.TAX_ADD : PurchasePreinvoice.TAX_INCLUSIVE;
            render(live);
        }), a.kit.lp(-2, -2));
        modes.addView(a.kit.btnGhost("گردکردن: " + roundFa(), Theme.INFO, v -> {
            draft.rounding = nextRounding();
            render(live);
        }), a.kit.lp(-2, -2));
        c.addView(modes, a.kit.lp(-1, -2));
        c.addView(a.kit.hint("تاریخ سند: " + Jalali.dispFa(String.valueOf(draft.dateTs))), a.kit.lp(-1, -2));
        return c;
    }

    private String roundFa() {
        if ("toman".equals(draft.rounding)) return "تومان";
        if ("hundred".equals(draft.rounding)) return "صد ریال";
        if ("thousand".equals(draft.rounding)) return "هزار ریال";
        if ("rial".equals(draft.rounding)) return "ریال";
        return "بدون گرد";
    }

    private String nextRounding() {
        String[] r = PurchasePreinvoice.ROUNDINGS;
        for (int i = 0; i < r.length; i++) if (r[i].equals(draft.rounding)) return r[(i + 1) % r.length];
        return r[0];
    }

    // --------------------------------------------------------------- اقلام
    private LinearLayout linesCard() {
        LinearLayout c = a.kit.card(Theme.INFO);
        c.addView(a.kit.text("اقلام کالا", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        if (draft.lines.isEmpty()) {
            c.addView(a.kit.empty("هنوز قلمی ثبت نشده", "با «افزودن قلم» اولین کالا را وارد کنید."), a.kit.lp(-1, -2));
        }
        for (int i = 0; i < draft.lines.size(); i++) c.addView(lineBlock(i), a.kit.lp(-1, -2));
        c.addView(a.kit.gap(6));
        c.addView(a.kit.btnGhost("＋ افزودن قلم", Theme.INFO, v -> {
            draft.lines.add(new PurchasePreinvoice.Line());
            render(live);
        }), a.kit.lp(-1, -2));
        return c;
    }

    private LinearLayout lineBlock(final int i) {
        final PurchasePreinvoice.Line l = draft.lines.get(i);
        LinearLayout box = a.kit.card(Theme.INFO);
        box.addView(a.kit.text("قلم " + Money.fa(String.valueOf(i + 1)), 12f, Theme.TEXT, true), a.kit.lp(-1, -2));
        box.addView(pair(field("کد کالا", l.code, v -> l.code = v, false),
                field("نام کالا", l.name, v -> l.name = v, false)));
        box.addView(pair(field("واحد شمارش", l.unit, v -> l.unit = v, false),
                field("تعداد", str(l.qty), v -> l.qty = num(v), true)));
        box.addView(pair(field("قیمت واحد (ریال)", str(l.price), v -> l.price = num(v), true),
                field("قیمت مرجع (ریال)", str(l.refPrice), v -> l.refPrice = num(v), true)));
        box.addView(pair(field("مالیات ٪", str(l.taxPct), v -> l.taxPct = num(v), true),
                field("عوارض ٪", str(l.dutyPct), v -> l.dutyPct = num(v), true)));
        LinearLayout act = a.kit.h();
        act.addView(a.kit.btnGhost("تخفیف‌ها (" + Money.fa(String.valueOf(l.discs.size())) + ")", Theme.GOLD,
                v -> discountDialog(l)), actParams());
        act.addView(a.kit.btnGhost("حذف قلم", Theme.DANGER, v -> {
            draft.lines.remove(i);
            render(live);
        }), actParams());
        box.addView(act, a.kit.lp(-1, -2));
        LineRefs refs = new LineRefs();
        refs.total = a.kit.text("", 12f, Theme.TEXT, true);
        refs.diff = a.kit.text("", 11.5f, Theme.MUTED, false);
        box.addView(refs.total, a.kit.lp(-1, -2));
        box.addView(refs.diff, a.kit.lp(-1, -2));
        lineRefs.add(refs);
        return box;
    }

    private LinearLayout.LayoutParams actParams() {
        LinearLayout.LayoutParams p = a.kit.lp(-2, -2);
        p.setMarginEnd(Theme.dp(8));
        return p;
    }

    private void discountDialog(final PurchasePreinvoice.Line l) {
        LinearLayout body = a.kit.v();
        final Spinner type = new Spinner(a);
        final String[] names = {"درصد ردیف", "ریالی هر واحد", "پله‌ای تعداد", "پله‌ای مبلغ", "درصد کل فاکتور", "کد تخفیف"};
        type.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, names));
        body.addView(type, a.kit.lp(-1, -2));
        final EditText v1 = a.kit.edit("درصد یا مبلغ هر واحد", "");
        v1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        body.addView(v1, a.kit.lp(-1, -2));
        final EditText v2 = a.kit.edit("سقف ریالی (فقط کد تخفیف؛ ۰ = بدون سقف)", "");
        v2.setInputType(InputType.TYPE_CLASS_NUMBER);
        body.addView(v2, a.kit.lp(-1, -2));
        final EditText spec = a.kit.edit("پله‌ها مانند 10:5,50:10 یا نام کد تخفیف", "");
        body.addView(spec, a.kit.lp(-1, -2));
        body.addView(a.kit.hint("ترتیب اعمال: ریالی هر واحد ← پله‌ای تعداد ← پله‌ای مبلغ ← درصد ردیف ← کد تخفیف؛ درصد کل فاکتور پس از همه بر جمع سند اعمال می‌شود."),
                a.kit.lp(-1, -2));
        for (int d = 0; d < l.discs.size(); d++) {
            final int at = d;
            PurchasePreinvoice.Disc x = l.discs.get(d);
            body.addView(a.kit.btnGhost(x.type.fa + " • حذف", Theme.DANGER, v -> {
                l.discs.remove(at);
                render(live);
            }), a.kit.lp(-1, -2));
        }
        body.addView(a.kit.btn("افزودن تخفیف", v -> {
            PurchasePreinvoice.DiscType t = PurchasePreinvoice.DiscType.values()[type.getSelectedItemPosition()];
            l.discs.add(new PurchasePreinvoice.Disc(t, num(v1.getText().toString()),
                    num(v2.getText().toString()), spec.getText().toString().trim()));
            render(live);
        }), a.kit.lp(-1, -2));
        a.kit.dialog("تخفیف‌های قلم", body, true).show();
    }

    // --------------------------------------------------------------- جمع‌ها و اقدام‌ها
    private LinearLayout totalsCard() {
        LinearLayout c = a.kit.card(Theme.INFO);
        c.addView(a.kit.text("جمع‌بندی سند", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String[] labels = {"جمع اقلام", "تخفیفات (ریال و ٪)", "مالیات", "عوارض", "جمع کل",
                "تخفیف نقدی پایانی", "قابل پرداخت", "اختلاف کل با مرجع"};
        for (String s : labels) {
            TextView t = a.kit.text("—", 12.5f, Theme.TEXT, false);
            t.setTag(s);
            c.addView(t, a.kit.lp(-1, -2));
            totalRefs.add(t);
        }
        return c;
    }

    private LinearLayout actionsRow() {
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btnGold("ثبت پیش‌فاکتور در سامانه", v -> save()), actParams());
        row.addView(a.kit.btnGhost("خروجی و اشتراک", Theme.INFO, v -> shareDialog()), actParams());
        return row;
    }

    private void save() {
        if (draft.supplierName.trim().isEmpty()) { a.kit.toast("نام فروشنده را وارد کنید"); return; }
        if (draft.lines.isEmpty()) { a.kit.toast("دست‌کم یک قلم کالا لازم است"); return; }
        try { draft.savedBy = String.valueOf(ir.meelano.manager.core.AtiranAuth.sessionRole(a)); }
        catch (RuntimeException e) { draft.savedBy = ""; }
        PurchasePreinvoiceStore.save(a, draft);
        a.kit.toast("پیش‌فاکتور در دفترچهٔ محلی برنامه ثبت شد");
        render(live);
    }

    private void shareDialog() {
        final PurchasePreinvoice.Totals t = PurchasePreinvoice.compute(draft);
        LinearLayout body = a.kit.v();
        String[][] opts = {
                {"PDF چاپ", "pdf"}, {"تصویر PNG", "png"}, {"اکسل", "xls"},
                {"ورد", "doc"}, {"CSV", "csv"}, {"متن گفتگو", "txt"}};
        for (final String[] o : opts) {
            body.addView(a.kit.btn(o[0], v -> share(o[1], t)), a.kit.lp(-1, -2));
        }
        body.addView(a.kit.hint("شیت اشتراک اندروید باز می‌شود: شبکه‌های اجتماعی نصب‌شده، بلوتوث، ایمیل و همهٔ گزینه‌های دستگاه در دسترس‌اند."),
                a.kit.lp(-1, -2));
        a.kit.dialog("خروجی و اشتراک پیش‌فاکتور", body, true).show();
    }

    private void share(final String kind, final PurchasePreinvoice.Totals t) {
        final String title = "پیش‌فاکتور خرید " + draft.docNo;
        final String subtitle = PreinvoiceExport.subtitle(draft, t);
        if ("pdf".equals(kind)) { a.sharePdf(title, subtitle, PreinvoiceExport.cols(), PreinvoiceExport.rows(draft)); markSent(); return; }
        if ("png".equals(kind)) { a.sharePng(title, subtitle, PreinvoiceExport.cols(), PreinvoiceExport.rows(draft)); markSent(); return; }
        new Thread(() -> {
            try {
                byte[] data; String name; String mime;
                if ("xls".equals(kind)) { data = PreinvoiceExport.xls(draft, t); name = "preinvoice.xls"; mime = "application/vnd.ms-excel"; }
                else if ("doc".equals(kind)) { data = PreinvoiceExport.doc(draft, t); name = "preinvoice.doc"; mime = "application/msword"; }
                else if ("csv".equals(kind)) { data = PreinvoiceExport.csv(draft, t); name = "preinvoice.csv"; mime = "text/csv"; }
                else { data = PreinvoiceExport.summaryText(draft, t).getBytes("UTF-8"); name = "preinvoice.txt"; mime = "text/plain"; }
                File f = new File(a.getCacheDir(), name);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(data);
                fos.close();
                a.runOnUiThread(() -> {
                    try {
                        ShareProvider.share(a, f, mime, title);
                        markSent();
                    } catch (Exception e) {
                        a.kit.toast("اشتراک ممکن نشد");
                    }
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> a.kit.toast("ساخت فایل ممکن نشد"));
            }
        }).start();
    }

    private void markSent() {
        if (!draft.id.isEmpty()) {
            draft.status = "sent";
            PurchasePreinvoiceStore.save(a, draft);
        }
    }

    // --------------------------------------------------------------- به‌روزرسانی زنده
    private void refreshNumbers() {
        PurchasePreinvoice.Totals t = PurchasePreinvoice.compute(draft);
        for (int i = 0; i < draft.lines.size() && i < lineRefs.size(); i++) {
            PurchasePreinvoice.Line l = draft.lines.get(i);
            LineRefs r = lineRefs.get(i);
            r.total.setText("جمع قلم: " + Money.fa(String.valueOf(Math.round(l.total))) + " ریال");
            if (l.refPrice > 0) {
                r.diff.setText("اختلاف با مرجع: " + Money.fa(String.valueOf(Math.round(l.diffAmt))) + " ریال ("
                        + Money.fa(String.format(Locale.US, "%.1f", l.diffPct)) + "٪)");
                r.diff.setTextColor(l.diffAmt > 0 ? Theme.DANGER : l.diffAmt < 0 ? Theme.SUCCESS : Theme.MUTED);
            } else {
                r.diff.setText("قیمت مرجع وارد نشده؛ اختلاف محاسبه نمی‌شود.");
                r.diff.setTextColor(Theme.MUTED);
            }
        }
        String[] vals = {
                "جمع اقلام: " + Money.fa(String.valueOf(Math.round(t.goods))) + " ریال",
                "تخفیفات: " + Money.fa(String.valueOf(Math.round(t.discSum))) + " ریال ("
                        + Money.fa(String.format(Locale.US, "%.1f", t.discPct)) + "٪)",
                "مالیات: " + Money.fa(String.valueOf(Math.round(t.tax))) + " ریال",
                "عوارض: " + Money.fa(String.valueOf(Math.round(t.duty))) + " ریال",
                "جمع کل: " + Money.fa(String.valueOf(Math.round(t.grand))) + " ریال",
                "تخفیف نقدی پایانی: " + Money.fa(String.valueOf(Math.round(t.cashDisc))) + " ریال",
                "قابل پرداخت: " + Money.fa(String.valueOf(Math.round(t.payable))) + " ریال",
                "اختلاف کل با مرجع: " + Money.fa(String.valueOf(Math.round(t.diff))) + " ریال"};
        for (int i = 0; i < totalRefs.size() && i < vals.length; i++) totalRefs.get(i).setText(vals[i]);
    }

    // --------------------------------------------------------------- ابزارهای فرم
    private interface Set { void set(String v); }

    private EditText field(String hint, String value, final Set set, boolean numeric) {
        final EditText e = a.kit.edit(hint, value == null ? "" : value);
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                set.set(s.toString().trim());
                refreshNumbers();
            }
        });
        return e;
    }

    private LinearLayout pair(EditText a1, EditText a2) {
        LinearLayout row = a.kit.h();
        row.addView(a1, a.kit.wlp(1f));
        row.addView(a.kit.space(8), a.kit.lp(-2, -2));
        row.addView(a2, a.kit.wlp(1f));
        return row;
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s == null ? "" : s.replace(",", "").trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String str(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }
}
