package ir.meelano.manager.core;

/** Classifies assistant work for the server-side AvalAI request budget. */
public final class AiTask {
    private AiTask() { }

    public static String forQuestion(String question) {
        String q = question == null ? "" : question.replace('ي', 'ی').replace('ك', 'ک');
        String[] writing = {"پیام", "متن", "نامه", "یادداشت", "بنویس", "پیامک", "ایمیل", "اطلاعیه"};
        String[] analysis = {"چرا", "تحلیل", "گزارش", "پیش‌بینی", "پیش بینی", "روند", "سود", "مقایسه",
                "ریسک", "توصیه", "علت", "راهکار", "بررسی"};
        for (String word : writing) if (q.contains(word)) return "writing";
        for (String word : analysis) if (q.contains(word)) return "analysis";
        return q.trim().isEmpty() || q.trim().split("\\s+").length <= 6 ? "fast" : "general";
    }
}
