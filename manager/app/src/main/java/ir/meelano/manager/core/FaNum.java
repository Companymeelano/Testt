package ir.meelano.manager.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Parse a spoken/typed quantity: Persian/Arabic/Latin digits plus Persian
 * number words («دوازده»، «صد و پنجاه»…). Used by voice quantity entry.
 */
public final class FaNum {
    private FaNum() { }

    private static final Map<String, Long> WORDS = new HashMap<>();

    static {
        WORDS.put("صفر", 0L);
        WORDS.put("یک", 1L);
        WORDS.put("یه", 1L);
        WORDS.put("دو", 2L);
        WORDS.put("سه", 3L);
        WORDS.put("چهار", 4L);
        WORDS.put("پنج", 5L);
        WORDS.put("شش", 6L);
        WORDS.put("شیش", 6L);
        WORDS.put("هفت", 7L);
        WORDS.put("هشت", 8L);
        WORDS.put("نه", 9L);
        WORDS.put("ده", 10L);
        WORDS.put("یازده", 11L);
        WORDS.put("دوازده", 12L);
        WORDS.put("سیزده", 13L);
        WORDS.put("چهارده", 14L);
        WORDS.put("پانزده", 15L);
        WORDS.put("پونزده", 15L);
        WORDS.put("شانزده", 16L);
        WORDS.put("شونزده", 16L);
        WORDS.put("هفده", 17L);
        WORDS.put("هیجده", 18L);
        WORDS.put("هجده", 18L);
        WORDS.put("نوزده", 19L);
        WORDS.put("بیست", 20L);
        WORDS.put("سی", 30L);
        WORDS.put("چهل", 40L);
        WORDS.put("پنجاه", 50L);
        WORDS.put("شصت", 60L);
        WORDS.put("هفتاد", 70L);
        WORDS.put("هشتاد", 80L);
        WORDS.put("نود", 90L);
        WORDS.put("صد", 100L);
        WORDS.put("یکصد", 100L);
        WORDS.put("دویست", 200L);
        WORDS.put("سیصد", 300L);
        WORDS.put("چهارصد", 400L);
        WORDS.put("پانصد", 500L);
        WORDS.put("ششصد", 600L);
        WORDS.put("هفتصد", 700L);
        WORDS.put("هشتصد", 800L);
        WORDS.put("نهصد", 900L);
        WORDS.put("هزار", 1000L);
    }

    /**
     * Best-effort quantity from free text. Returns null when nothing
     * numeric is found. Understands «12»، «۱۲»، «12.5»، «دوازده»،
     * «صد و پنجاه»، «دو هزار» (multiplies follow-ups under 1000 logic).
     */
    public static Double parse(String text) {
        if (text == null) return null;
        String t = Money.en(text).replace(",", "").replace("٬", "").trim();
        if (t.isEmpty()) return null;
        // Plain number anywhere in the text wins.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)").matcher(t);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (Exception ignored) { }
        }
        // Persian words: split on spaces/و.
        String w = t.replace("ي", "ی").replace("ك", "ک").replace("‌", " ")
                .replace("و", " ").replaceAll("\\s+", " ").trim();
        if (w.isEmpty()) return null;
        long total = 0, cur = 0;
        boolean any = false;
        for (String part : w.split(" ")) {
            Long v = WORDS.get(part.trim());
            if (v == null) continue;
            any = true;
            if (v == 1000) {
                cur = (cur == 0 ? 1 : cur) * 1000;
                total += cur;
                cur = 0;
            } else if (v == 100) {
                cur = (cur == 0 ? 1 : cur) * 100;
            } else if (v >= 200 && v % 100 == 0) {
                cur += v;
            } else {
                cur += v;
            }
        }
        if (!any) return null;
        return (double) (total + cur);
    }
}
