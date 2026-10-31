package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, availability-aware copy for the three-sentence Home smart report. */
public final class SmartReportEngine {
    private SmartReportEngine() { }

    public static final class Input {
        public boolean salesAvailable;
        public double salesRials;
        public long documents;
        public boolean comparisonAvailable;
        public double changePercent = Double.NaN;

        public boolean productsAvailable;
        public String leadingProduct = "";
        public double leadingProductSalesRials;

        public boolean overdueAvailable;
        public long overdueInvoices;
        public boolean dueChequesAvailable;
        public long dueCheques;
        public boolean returnsAvailable;
        public long returnDocuments;
    }

    /** Always returns exactly three short sentences; unavailable sources never become zeroes. */
    public static List<String> compose(Input input) {
        Input in = input == null ? new Input() : input;
        List<String> out = new ArrayList<>(3);

        if (!in.salesAvailable) {
            out.add("گزارش فروش امروز در دسترس نیست.");
        } else if (in.documents <= 0 && Math.abs(in.salesRials) < 0.005d) {
            out.add("تا این لحظه فاکتوری برای امروز ثبت نشده است.");
        } else {
            String sale = "فروش امروز " + HomeNumberFormatter.compactMoney(in.salesRials)
                    + " در " + HomeNumberFormatter.integer(Math.max(0, in.documents)) + " فاکتور ثبت شد";
            if (in.comparisonAvailable && finite(in.changePercent))
                sale += "؛ تغییر نسبت به میانگین همان روز هفته "
                        + HomeNumberFormatter.signedPercent(in.changePercent);
            out.add(sale + ".");
        }

        String product = safe(in.leadingProduct);
        if (!in.productsAvailable) {
            out.add("ریز فروش کالا برای امروز در دسترس نیست.");
        } else if (product.isEmpty() || Math.abs(in.leadingProductSalesRials) < 0.005d) {
            out.add("برای امروز کالای پیشتازی در فهرست فروش ثبت نشده است.");
        } else {
            out.add("پیشتاز امروز «" + HomeNumberFormatter.localizeText(product) + "» با "
                    + HomeNumberFormatter.compactMoney(in.leadingProductSalesRials) + " فروش است.");
        }

        if (!in.overdueAvailable || !in.dueChequesAvailable) {
            out.add("دادهٔ پیگیری کامل نیست؛ معوق‌ها و سررسیدها را جداگانه بررسی کنید.");
        } else {
            List<String> followUps = new ArrayList<>();
            if (in.overdueInvoices > 0)
                followUps.add("فاکتور معوق در فهرست بررسی‌شده: "
                        + HomeNumberFormatter.integer(in.overdueInvoices));
            if (in.dueCheques > 0)
                followUps.add(HomeNumberFormatter.integer(in.dueCheques) + " چک در سه روز آینده");
            if (in.returnsAvailable && in.returnDocuments > 0)
                followUps.add(HomeNumberFormatter.integer(in.returnDocuments) + " سند برگشتی در ۳۰ روز");
            if (followUps.isEmpty()) {
                out.add("در داده‌های سررسید و معوق، مورد فوری ثبت نشده است.");
            } else {
                StringBuilder sentence = new StringBuilder("برای پیگیری: ");
                for (int i = 0; i < followUps.size(); i++) {
                    if (i > 0) sentence.append("، ");
                    sentence.append(followUps.get(i));
                }
                out.add(sentence.append('.').toString());
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
