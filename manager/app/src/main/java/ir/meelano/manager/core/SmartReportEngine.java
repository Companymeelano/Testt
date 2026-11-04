package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, availability-aware copy for the three-sentence Home smart report. */
public final class SmartReportEngine {
    private static final long URGENT_OVERDUE_DAYS = 7L;

    private SmartReportEngine() { }

    public enum FollowUpPriority { INCOMPLETE, URGENT, FOLLOW_UP, CLEAR }

    public static final class Input {
        public boolean salesAvailable;
        public boolean salesDayComplete;
        public double salesRials;
        public long documents;
        public boolean comparisonAvailable;
        public boolean comparisonProjected;
        public double projectedSalesRials = Double.NaN;
        public double changePercent = Double.NaN;

        public boolean productsAvailable;
        public String leadingProduct = "";
        public double leadingProductSalesRials;

        public boolean overdueAvailable;
        public long overdueInvoices;
        /** False when only a limited preview count is known. */
        public boolean overdueCountExact = true;
        /** -1 means the source could not determine the age. */
        public long oldestOverdueDays = -1L;

        public boolean dueInAvailable;
        public long dueInCheques;
        public boolean dueOutAvailable;
        public long dueOutCheques;
        public boolean returnsAvailable;
        public long returnDocuments;
    }

    /**
     * Priority is based on known counts and age. A fresh overdue item needs follow-up;
     * it is only urgent after a week overdue. Missing sources are never called clear.
     */
    public static FollowUpPriority followUpPriority(Input input) {
        if (input == null) return FollowUpPriority.INCOMPLETE;
        if (input.overdueAvailable && input.overdueInvoices > 0
                && input.oldestOverdueDays >= URGENT_OVERDUE_DAYS)
            return FollowUpPriority.URGENT;

        boolean hasKnownFollowUp = (input.overdueAvailable && input.overdueInvoices > 0)
                || (input.dueInAvailable && input.dueInCheques > 0)
                || (input.dueOutAvailable && input.dueOutCheques > 0)
                || (input.returnsAvailable && input.returnDocuments > 0);
        if (hasKnownFollowUp) return FollowUpPriority.FOLLOW_UP;

        if (!input.overdueAvailable || !input.dueInAvailable || !input.dueOutAvailable
                || !input.returnsAvailable)
            return FollowUpPriority.INCOMPLETE;
        return FollowUpPriority.CLEAR;
    }

    /** Always returns exactly three grounded sentences; unavailable sources never become zeroes. */
    public static List<String> compose(Input input) {
        Input in = input == null ? new Input() : input;
        List<String> out = new ArrayList<>(3);

        if (!in.salesAvailable) {
            out.add("گزارش فروش امروز در دسترس نیست.");
        } else if (in.documents <= 0 && Math.abs(in.salesRials) < 0.005d) {
            out.add(in.salesDayComplete
                    ? "امروز فاکتوری برای فروش ثبت نشده است."
                    : "تا این لحظه فاکتوری برای فروش امروز ثبت نشده است.");
        } else {
            String period = in.salesDayComplete ? "فروش امروز " : "فروش ثبت‌شده تا این لحظه ";
            String sale = period + HomeNumberFormatter.compactMoney(in.salesRials)
                    + " در " + HomeNumberFormatter.integer(Math.max(0, in.documents)) + " فاکتور ثبت شد";
            if (in.comparisonAvailable && finite(in.changePercent)) {
                if (in.comparisonProjected && finite(in.projectedSalesRials)) {
                    sale += "؛ برآورد خطی فروش پایان روز "
                            + HomeNumberFormatter.compactMoney(in.projectedSalesRials)
                            + " (" + HomeNumberFormatter.signedPercent(in.changePercent)
                            + ") نسبت به میانگین همان روز هفته";
                } else {
                    sale += "؛ تغییر نسبت به میانگین همان روز هفته "
                            + HomeNumberFormatter.signedPercent(in.changePercent);
                }
            }
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

        List<String> followUps = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (in.overdueAvailable) {
            if (in.overdueInvoices > 0) {
                String count = (in.overdueCountExact ? "" : "حداقل ")
                        + HomeNumberFormatter.integer(in.overdueInvoices);
                String overdue = count + " فاکتور معوق";
                if (!in.overdueCountExact) overdue += " در فهرست بررسی‌شده";
                if (in.oldestOverdueDays >= 0)
                    overdue += "؛ کهنه‌ترین مورد "
                            + HomeNumberFormatter.integer(in.oldestOverdueDays) + " روز";
                followUps.add(overdue);
            }
        } else {
            missing.add("فاکتورهای معوق");
        }

        if (in.dueInAvailable) {
            if (in.dueInCheques > 0)
                followUps.add(HomeNumberFormatter.integer(in.dueInCheques)
                        + " چک دریافتی در سه روز آینده");
        } else {
            missing.add("چک‌های دریافتی");
        }

        if (in.dueOutAvailable) {
            if (in.dueOutCheques > 0)
                followUps.add(HomeNumberFormatter.integer(in.dueOutCheques)
                        + " چک پرداختی در سه روز آینده");
        } else {
            missing.add("چک‌های پرداختی");
        }

        if (in.returnsAvailable) {
            if (in.returnDocuments > 0)
                followUps.add(HomeNumberFormatter.integer(in.returnDocuments)
                        + " سند برگشتی در ۳۰ روز گذشته");
        } else {
            missing.add("آمار اسناد برگشتی ۳۰روزه");
        }

        if (followUps.isEmpty() && missing.isEmpty()) {
            out.add("در داده‌های کامل پیگیری، موردی ثبت نشده است.");
        } else if (followUps.isEmpty()) {
            out.add("نتیجهٔ پیگیری کامل نیست؛ منبعِ " + join(missing) + " در دسترس نیست.");
        } else {
            String sentence = "برای پیگیری: " + join(followUps);
            if (!missing.isEmpty()) sentence += "؛ منبعِ " + join(missing) + " نامشخص است";
            out.add(sentence + ".");
        }
        return Collections.unmodifiableList(out);
    }

    private static String join(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (joined.length() > 0) joined.append("، ");
            joined.append(value);
        }
        return joined.toString();
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
