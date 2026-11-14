package ir.meelano.manager.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * On-device Persian intent router for the assistant. Pure Java: no network, no model, no Android.
 *
 * <p>Pipeline: normalise the Persian text → match a fixed, typed tool (the whitelist below) →
 * check the tool's screen permission (done by the caller with {@link RoleStore}) → run the
 * tool on the store snapshot that was read from the real database ({@link Assistant}).
 * The engine never builds SQL, never runs model-written code and never invents numbers: when
 * the server snapshot is missing it says so instead of answering.
 *
 * <p>Unmatched text returns {@link Reply#handled()} = false so the caller can use the optional
 * online model (only when online and a key exists) or show the help text.
 */
public final class AssistantEngine {
    private AssistantEngine() { }

    /** The complete list of tools. Adding a tool is a code change, never a model output. */
    public enum Tool {
        HELP(null, "راهنما"),
        SUMMARY("home", "خلاصه امروز"),
        SUGGESTIONS("home", "پیشنهاد اقدام"),
        SALES("sales", "فروش"),
        CASH_IN("dar_in", "دریافت"),
        CASH_OUT("dar_out", "پرداخت"),
        PURCHASE("buy", "خرید"),
        DEBTORS("dues", "بدهکار"),
        OVERDUE("dues", "معوق"),
        CHEQUES("cheques", "چک"),
        STOCK("products", "موجودی"),
        VISITORS("visitors", "ویزیتور");

        /** Screen id whose permission guards this data (see {@link RoleStore#allowed}). */
        public final String screen;
        /** The canonical question given to {@link Assistant#answer}. */
        public final String canonical;

        Tool(String screen, String canonical) {
            this.screen = screen;
            this.canonical = canonical;
        }
    }

    /** Result of one turn. */
    public static final class Reply {
        private final boolean handled;
        private final Tool tool;
        private final String text;

        Reply(boolean handled, Tool tool, String text) {
            this.handled = handled;
            this.tool = tool;
            this.text = text;
        }

        public boolean handled() { return handled; }
        public Tool tool() { return tool; }
        public String text() { return text; }
    }

    /** Short-term context: the last few turns of this conversation only (bounded). */
    public static final class Session {
        static final int MAX_TURNS = 6;
        final Deque<String[]> turns = new ArrayDeque<>();
        Tool lastTool = null;

        void remember(String question, Tool tool) {
            if (tool == null) return;
            turns.addLast(new String[]{question, tool.name()});
            while (turns.size() > MAX_TURNS) turns.removeFirst();
            lastTool = tool;
        }

        public void clear() {
            turns.clear();
            lastTool = null;
        }

        public int size() { return turns.size(); }
    }

    /** Persian normalisation: Arabic letters, ZWNJ, Persian/Arabic digits, spacing, case. */
    public static String normalize(String t) {
        if (t == null) return "";
        StringBuilder b = new StringBuilder(t.length());
        for (char ch : t.toCharArray()) {
            if (ch == '\u200c' || ch == '\u200f' || ch == '\u200e' || ch == '\u0640') {
                b.append(' ');
            } else if (ch == 'ي' || ch == 'ى') {
                b.append('ی');
            } else if (ch == 'ك') {
                b.append('ک');
            } else if (ch == 'ۀ' || ch == 'ة') {
                b.append('ه');
            } else if (ch >= '۰' && ch <= '۹') {
                b.append((char) ('0' + (ch - '۰')));
            } else if (ch >= '٠' && ch <= '٩') {
                b.append((char) ('0' + (ch - '٠')));
            } else {
                b.append(ch);
            }
        }
        return b.toString().replaceAll("[\\s]+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /** Keyword rules, first match wins. Order matters: more specific words come first. */
    private static final String[][] RULES = {
            {"help", "راهنما", "کمک", "چیکار", "چه کار", "چه سوالی", "راهنمایی"},
            {"suggest", "پیشنهاد", "چه کنم", "اقدام"},
            {"summary", "خلاصه", "گزارش امروز", "وضعیت امروز", "روزانه"},
            {"overdue", "معوق", "سررسید", "سر رسید", "فاکتور عقب"},
            {"debtors", "بدهکار", "مطالبه", "طلب", "وصول"},
            {"cheques", "چک"},
            {"stock", "موجودی", "کالا", "انبار", "ناموجود"},
            {"visitors", "ویزیتور", "ویزیت", "بازاریاب"},
            {"purchase", "خرید"},
            {"cash_out", "پرداخت"},
            {"cash_in", "دریافت", "وصولی", "نقد"},
            {"sales", "فروش", "فاکتور"},
    };

    private static final String[] YESTERDAY = {"دیروز"};
    private static final String[] TODAY = {"امروز"};

    static Tool toolFor(String key) {
        switch (key) {
            case "help": return Tool.HELP;
            case "suggest": return Tool.SUGGESTIONS;
            case "summary": return Tool.SUMMARY;
            case "overdue": return Tool.OVERDUE;
            case "debtors": return Tool.DEBTORS;
            case "cheques": return Tool.CHEQUES;
            case "stock": return Tool.STOCK;
            case "visitors": return Tool.VISITORS;
            case "purchase": return Tool.PURCHASE;
            case "cash_out": return Tool.CASH_OUT;
            case "cash_in": return Tool.CASH_IN;
            case "sales": return Tool.SALES;
            default: return null;
        }
    }

    /** Which tool a question maps to, without running it. Null when nothing matches. */
    public static Tool match(String question) {
        String q = normalize(question);
        for (String[] rule : RULES) {
            for (int i = 1; i < rule.length; i++) {
                if (q.contains(normalize(rule[i]))) return toolFor(rule[0]);
            }
        }
        return null;
    }

    /**
     * Answers one question. {@code snap} is null when the server could not be read (offline or
     * down); data tools then refuse instead of answering with invented zeros.
     */
    public static Reply respond(Assistant.Snap snap, String question, Session session) {
        String q = normalize(question);
        if (q.isEmpty()) return new Reply(false, null, "");

        boolean wantsYesterday = containsAny(q, YESTERDAY);
        boolean followUp = session != null && session.lastTool != null && isOnlyDayWord(q);
        Tool tool = followUp ? session.lastTool : match(q);

        if (tool == null) return new Reply(false, null, "");
        if (tool == Tool.HELP) {
            return remember(session, q, new Reply(true, tool, Assistant.help()));
        }
        if (snap == null) {
            return remember(session, q, new Reply(true, tool,
                    "سرور در دسترس نیست و اطلاعات کسب‌وکار الان قابل خواندن نیست.\n"
                            + "وقتی شبکه یا سرور برقرار شد دوباره بپرسید. هیچ عددی نشان داده نمی‌شود."));
        }
        if (wantsYesterday && tool == Tool.SALES) {
            return remember(session, q, new Reply(true, tool, salesYesterday(snap)));
        }
        if (wantsYesterday) {
            // Only sales has a yesterday figure in the snapshot; say so rather than guess.
            return remember(session, q, new Reply(true, tool,
                    "در دستیار فعلاً فقط فروش دیروز موجود است. برای این بخش، گزارش دیروز را از همان بخش ببینید."));
        }
        String answer = Assistant.answer(snap, tool.canonical);
        return remember(session, q, new Reply(true, tool, withFreshness(answer, snap)));
    }

    private static Reply remember(Session session, String q, Reply r) {
        if (session != null) session.remember(q, r.tool());
        return r;
    }

    private static boolean isOnlyDayWord(String q) {
        String rest = q.replace("دیروز", "").replace("امروز", "").replace("?", "")
                .replace("؟", "").replace("و", "").trim();
        return rest.isEmpty() && (q.contains("دیروز") || q.contains("امروز"));
    }

    private static boolean containsAny(String q, String[] words) {
        for (String w : words) if (q.contains(w)) return true;
        return false;
    }

    private static String salesYesterday(Assistant.Snap s) {
        double total = s.salesYesterday.d("total");
        long docs = s.salesYesterday.l("docs");
        return "فروش دیروز: " + Money.compactRial(total)
                + "\nتعداد فاکتور: " + Money.fa(String.valueOf(docs));
    }

    /** Adds the snapshot's age so a user never mistakes old data for live data. */
    static String withFreshness(String answer, Assistant.Snap s) {
        if (s == null || s.loadedAt <= 0) return answer;
        long mins = Math.max(0, (System.currentTimeMillis() - s.loadedAt) / 60_000L);
        if (mins < 5) return answer;
        return answer + "\n\n⏱ اطلاعات " + Money.fa(String.valueOf(mins)) + " دقیقه پیش از سرور خوانده شده است.";
    }
}
