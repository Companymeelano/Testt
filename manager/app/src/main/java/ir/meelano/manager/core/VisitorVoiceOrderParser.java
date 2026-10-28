package ir.meelano.manager.core;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe, deterministic first-pass parsing for a visitor's spoken/text order line. */
public final class VisitorVoiceOrderParser {
    private static final Pattern LEADING_NUMBER = Pattern.compile("^([0-9]{1,5}(?:[.,][0-9]{1,3})?)(?:\\s+|$)(.*)$");
    private static final String[] PREFIXES = {
            "میلو", "لطفاً", "لطفا", "برای سبد", "برای من", "به سبد", "برام", "میشه", "می شه", "می‌شود", "می تونی", "می‌تونی", "میتونی", "بفرست", "اضافه کن", "اضافه بکن", "بذار", "بگذار", "ثبت کن", "میخوام", "می خوام", "می‌خوام", "میخواهم", "می خواهم", "یه", "یک"
    };
    private static final String[] SUFFIXES = {
            "لطفاً اضافه کن", "لطفا اضافه کن", "اضافه کن", "اضافه بکن", "به سبد اضافه کن", "به سبد", "برای سبد", "بذار", "بگذار", "بفرست", "ممنون", "لطفاً", "لطفا"
    };

    private VisitorVoiceOrderParser() { }

    public static final class Request {
        public final String productQuery;
        public final double quantity;
        public final String requestedUnit;
        public final boolean hasExplicitQuantity;

        Request(String productQuery, double quantity, String requestedUnit, boolean hasExplicitQuantity) {
            this.productQuery = productQuery;
            this.quantity = quantity;
            this.requestedUnit = requestedUnit;
            this.hasExplicitQuantity = hasExplicitQuantity;
        }

        public boolean isValid() { return productQuery != null && productQuery.trim().length() >= 2 && quantity > 0 && quantity <= 10000; }
    }

    public static Request parse(String raw) {
        String text = normalize(raw);
        if (text.isEmpty()) return new Request("", 1, "", false);
        text = stripLeadingPhrases(text, PREFIXES);
        double quantity = 1d;
        boolean explicit = false;
        String unit = "";

        Matcher numeric = LEADING_NUMBER.matcher(text);
        if (numeric.matches()) {
            try {
                quantity = Double.parseDouble(numeric.group(1).replace(',', '.'));
                explicit = true;
                text = numeric.group(2).trim();
            } catch (NumberFormatException ignored) { }
        } else {
            String[] words = text.split("\\s+", 3);
            if (words.length >= 2) {
                double spoken = spokenNumber(words[0]);
                if (spoken > 0) {
                    quantity = spoken;
                    explicit = true;
                    text = text.substring(words[0].length()).trim();
                }
            }
        }

        String[] unitSplit = removeLeadingUnit(text);
        if (unitSplit != null) { unit = unitSplit[0]; text = unitSplit[1]; }
        text = stripTrailingPhrases(text, SUFFIXES);
        text = text.replaceAll("^[،,؛;\\-\\s]+|[،,؛;\\-\\s]+$", "").replaceAll("\\s+", " ").trim();
        if (!Double.isFinite(quantity) || quantity <= 0 || quantity > 10000) return new Request(text, 0, unit, true);
        return new Request(text, quantity, unit, explicit);
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace('ي', 'ی').replace('ك', 'ک').replace('\u0640', ' ')
                .replace('\u200c', ' ').replace('\u200f', ' ').replace('\u200e', ' ')
                .toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '۰' && c <= '۹') out.append((char) ('0' + c - '۰'));
            else if (c >= '٠' && c <= '٩') out.append((char) ('0' + c - '٠'));
            else out.append(c);
        }
        return out.toString().replaceAll("[\\p{M}]", "").trim();
    }

    private static String stripLeadingPhrases(String value, String[] phrases) {
        String out = value == null ? "" : value.trim();
        boolean changed;
        do {
            changed = false;
            for (String phrase : phrases) {
                String p = normalize(phrase);
                if (out.equals(p)) return "";
                if (out.startsWith(p + " ")) { out = out.substring(p.length()).trim(); changed = true; break; }
            }
        } while (changed);
        return out;
    }

    private static String stripTrailingPhrases(String value, String[] phrases) {
        String out = value == null ? "" : value.trim();
        boolean changed;
        do {
            changed = false;
            for (String phrase : phrases) {
                String p = normalize(phrase);
                if (out.equals(p)) return "";
                if (out.endsWith(" " + p)) { out = out.substring(0, out.length() - p.length()).trim(); changed = true; break; }
            }
        } while (changed);
        return out;
    }

    private static String[] removeLeadingUnit(String value) {
        if (value == null || value.isEmpty()) return null;
        String[] units = {"کیلوگرم", "کیلو", "کیلوئی", "کیلوگرمی", "گرم", "بسته", "کارتن", "عدد", "دانه", "تا", "لیتر", "جعبه", "پک", "کیسه", "قلم"};
        for (String unit : units) {
            String normalizedUnit = normalize(unit);
            if (value.equals(normalizedUnit)) return new String[]{normalizedUnit, ""};
            if (value.startsWith(normalizedUnit + " ")) return new String[]{normalizedUnit, value.substring(normalizedUnit.length()).trim()};
        }
        return null;
    }

    private static double spokenNumber(String token) {
        String t = normalize(token);
        if ("یک".equals(t) || "یکی".equals(t) || "یه".equals(t)) return 1;
        if ("دو".equals(t) || "دوتا".equals(t)) return 2;
        if ("سه".equals(t) || "سهتا".equals(t)) return 3;
        if ("چهار".equals(t)) return 4;
        if ("پنج".equals(t)) return 5;
        if ("شش".equals(t)) return 6;
        if ("هفت".equals(t)) return 7;
        if ("هشت".equals(t)) return 8;
        if ("نه".equals(t)) return 9;
        if ("ده".equals(t)) return 10;
        if ("نیم".equals(t) || "نصف".equals(t)) return .5d;
        return 0;
    }
}
