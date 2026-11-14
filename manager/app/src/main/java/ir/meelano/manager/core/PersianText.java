package ir.meelano.manager.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Shared Persian/Arabic text cleanup and search normalization. */
public final class PersianText {
    private PersianText() { }

    /**
     * A compact key for in-memory matching. Arabic/Persian letter and digit variants,
     * spaces, ZWNJ and punctuation all fold to one searchable representation.
     */
    public static String searchKey(String value) {
        if (value == null || value.isEmpty()) return "";
        String source = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(source.length());
        for (int i = 0; i < source.length();) {
            int cp = source.codePointAt(i);
            i += Character.charCount(cp);
            int folded = foldCodePoint(cp);
            if (isArabicMark(folded) || isDirectionalOrJoinControl(folded)) continue;
            if (Character.isLetterOrDigit(folded)) out.appendCodePoint(Character.toLowerCase(folded));
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * One token for the product SQL search. Separators, ZWNJ, decoration stars and
     * Arabic combining marks are ignored; other punctuation remains literal.
     */
    public static String productSearchNeedle(String value) {
        if (value == null || value.isEmpty()) return "";
        String source = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(source.length());
        for (int i = 0; i < source.length();) {
            int cp = source.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)
                    || cp == '*' || isArabicMark(cp) || isDirectionalOrJoinControl(cp)) continue;
            out.appendCodePoint(foldCodePoint(cp));
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /** Split on ordinary whitespace; ZWNJ remains a joiner inside a Persian word. */
    public static List<String> productSearchTokens(String value) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (value == null) return new ArrayList<>();
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty()) return new ArrayList<>();
        for (String part : normalized.split("\\s+")) {
            String token = productSearchNeedle(part);
            if (!token.isEmpty()) unique.add(token);
        }
        return new ArrayList<>(unique);
    }

    /** Remove decorative stars and collapse visible whitespace without stripping ZWNJ. */
    public static String cleanDisplayLabel(String value) {
        if (value == null || value.isEmpty()) return "";
        String source = Normalizer.normalize(value, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(source.length());
        boolean pendingSpace = false;
        for (int i = 0; i < source.length();) {
            int cp = source.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '*') continue;
            if (Character.isISOControl(cp)) continue;
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (pendingSpace) out.append(' ');
            pendingSpace = false;
            out.appendCodePoint(cp);
        }
        return out.toString().trim();
    }

    /** Empty/placeholder group labels are not useful metadata and should not occupy a row. */
    public static String cleanGroupLabel(String value) {
        String label = cleanDisplayLabel(value);
        String key = label.replace(" ", "").trim();
        if (key.isEmpty() || "--".equals(key) || "—".equals(key)
                || "–".equals(key) || "-".equals(key)) return "";
        return label;
    }

    /** A name containing the Unicode replacement character is already damaged upstream. */
    public static boolean isUsablePersonName(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        boolean letter = false;
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xFFFD || Character.isISOControl(cp)) return false;
            if (Character.isLetter(cp)) letter = true;
        }
        return letter;
    }

    /** Canonical fold shared by the Java and SQL search paths. */
    public static int foldCodePoint(int cp) {
        switch (cp) {
            case 0x064A: // Arabic yeh
            case 0x0649: // alef maksura
                return 0x06CC; // Persian yeh
            case 0x0643: // Arabic kaf
                return 0x06A9; // Persian kaf
            case 0x0629: // ta marbuta
                return 0x0647; // heh
            case 0x0624: // waw with hamza
                return 0x0648;
            case 0x0622: // alef madda
            case 0x0623: // alef hamza above
            case 0x0625: // alef hamza below
            case 0x0671: // alef wasla
                return 0x0627;
            default:
                if (cp >= 0x06F0 && cp <= 0x06F9) return '0' + cp - 0x06F0;
                if (cp >= 0x0660 && cp <= 0x0669) return '0' + cp - 0x0660;
                return cp;
        }
    }

    public static boolean isArabicMark(int cp) {
        return (cp >= 0x064B && cp <= 0x065F) || cp == 0x0670 || cp == 0x06D6
                || cp == 0x06D7 || cp == 0x06D8 || cp == 0x06D9 || cp == 0x06DA
                || cp == 0x06DB || cp == 0x06DC || cp == 0x06DF || cp == 0x06E0
                || cp == 0x06E1 || cp == 0x06E2 || cp == 0x06E3 || cp == 0x06E4
                || cp == 0x06E7 || cp == 0x06E8 || cp == 0x06EA || cp == 0x06EB
                || cp == 0x06EC || cp == 0x06ED
                || Character.getType(cp) == Character.NON_SPACING_MARK
                || Character.getType(cp) == Character.COMBINING_SPACING_MARK
                || Character.getType(cp) == Character.ENCLOSING_MARK;
    }

    public static boolean isDirectionalOrJoinControl(int cp) {
        return cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0x200E
                || cp == 0x200F || cp == 0x202A || cp == 0x202B || cp == 0x202C
                || cp == 0x202D || cp == 0x202E || cp == 0x2066 || cp == 0x2067
                || cp == 0x2068 || cp == 0x2069 || cp == 0xFEFF;
    }
}
