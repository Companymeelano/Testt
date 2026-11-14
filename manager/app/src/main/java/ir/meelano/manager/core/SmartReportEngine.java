package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, availability-aware copy for the three-sentence Home smart report. */
public final class SmartReportEngine {
    private static final long URGENT_OVERDUE_DAYS = 7L;

    private SmartReportEngine() { }

    public enum FollowUpPriority { INCOMPLETE, URGENT, FOLLOW_UP, CLEAR }

    /** One independently labeled follow-up, with urgency attached to the relevant row. */
    public static final class FollowUpItem {
        public final String key;
        public final String text;
        public final FollowUpPriority priority;

        private FollowUpItem(String key, String text, FollowUpPriority priority) {
            this.key = key;
            this.text = text;
            this.priority = priority;
        }
    }

    public static final class Input {
        public boolean salesAvailable;
        public boolean salesDayComplete;
        public double salesRials;
        public long documents;
        public boolean comparisonAvailable;
        public boolean comparisonProjected;
        public double projectedSalesRials = Double.NaN;
        public double salesBaselineRials = Double.NaN;
        /** Elapsed local minutes used by the current-day estimate; -1 means unknown. */
        public int salesElapsedMinutes = -1;
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

    /**
     * Return follow-up rows for the dashboard. Each known item carries its own urgency; an
     * unavailable source is a separate incomplete row instead of being folded into the badge.
     */
    public static List<FollowUpItem> followUpItems(Input input) {
        Input in = input == null ? new Input() : input;
        List<FollowUpItem> items = new ArrayList<>();
        List<String> missing = missingSources(in);

        if (in.overdueAvailable && in.overdueInvoices > 0) {
            String count = (in.overdueCountExact ? "" : "حداقل ")
                    + HomeNumberFormatter.integer(in.overdueInvoices);
            String text = count + " فاکتور معوق";
            if (!in.overdueCountExact) text += " در فهرست بررسی‌شده";
            if (in.oldestOverdueDays >= 0)
                text += "؛ کهنه‌ترین مورد "
                        + HomeNumberFormatter.integer(in.oldestOverdueDays) + " روز";
            FollowUpPriority priority = in.oldestOverdueDays >= URGENT_OVERDUE_DAYS
                    ? FollowUpPriority.URGENT : FollowUpPriority.FOLLOW_UP;
            items.add(new FollowUpItem("overdue", text, priority));
        }
        if (in.dueInAvailable && in.dueInCheques > 0)
            items.add(new FollowUpItem("due-in", HomeNumberFormatter.integer(in.dueInCheques)
                    + " چک دریافتی در سه روز آینده", FollowUpPriority.FOLLOW_UP));
        if (in.dueOutAvailable && in.dueOutCheques > 0)
            items.add(new FollowUpItem("due-out", HomeNumberFormatter.integer(in.dueOutCheques)
                    + " چک پرداختی در سه روز آینده", FollowUpPriority.FOLLOW_UP));
        if (in.returnsAvailable && in.returnDocuments > 0)
            items.add(new FollowUpItem("returns", HomeNumberFormatter.integer(in.returnDocuments)
                    + " سند برگشتی از فروش در ۳۰ روز گذشته", FollowUpPriority.FOLLOW_UP));

        if (!missing.isEmpty())
            items.add(new FollowUpItem("incomplete", "گزارش کامل نیست؛ منبعِ " + join(missing)
                    + " در دسترس نیست", FollowUpPriority.INCOMPLETE));
        if (items.isEmpty())
            items.add(new FollowUpItem("clear", "در داده‌های کامل، موردی برای پیگیری ثبت نشده است",
                    FollowUpPriority.CLEAR));
        return Collections.unmodifiableList(items);
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
            if (in.comparisonAvailable && finite(in.salesBaselineRials)
                    && in.salesBaselineRials > 0d) {
                if (in.comparisonProjected && finite(in.projectedSalesRials)) {
                    String estimate = "برآورد پایان روز "
                            + HomeNumberFormatter.compactMoney(in.projectedSalesRials);
                    if (in.salesElapsedMinutes >= 0) {
                        int hour = Math.min(23, Math.max(0, in.salesElapsedMinutes / 60));
                        int minute = Math.min(59, Math.max(0, in.salesElapsedMinutes % 60));
                        estimate += " بر پایهٔ فروش تا ساعت " + HomeNumberFormatter.time(hour, minute);
                    }
                    sale += "؛ " + estimate + "؛ " + HomeNumberFormatter.changeLabel(
                            in.projectedSalesRials, in.salesBaselineRials, in.changePercent,
                            "میانگین روزهای هم‌نام (" + HomeNumberFormatter.compactMoney(
                                    in.salesBaselineRials) + ")");
                } else if (in.salesDayComplete) {
                    sale += "؛ " + HomeNumberFormatter.changeLabel(in.salesRials,
                            in.salesBaselineRials, in.changePercent,
                            "میانگین روزهای هم‌نام (" + HomeNumberFormatter.compactMoney(
                                    in.salesBaselineRials) + ")");
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
        List<String> missing = missingSources(in);
        for (FollowUpItem item : followUpItems(in)) {
            if (!"incomplete".equals(item.key) && !"clear".equals(item.key))
                followUps.add(item.text);
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

    private static List<String> missingSources(Input in) {
        List<String> missing = new ArrayList<>();
        if (!in.overdueAvailable) missing.add("فاکتورهای معوق");
        if (!in.dueInAvailable) missing.add("چک‌های دریافتی");
        if (!in.dueOutAvailable) missing.add("چک‌های پرداختی");
        if (!in.returnsAvailable) missing.add("آمار اسناد برگشتی ۳۰روزه");
        return missing;
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
