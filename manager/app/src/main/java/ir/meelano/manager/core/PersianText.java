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
            // MeelanoIcons deliberately replaces «◆» with a diamond drawing. If a corrupt
            // JDBC/session name contains that marker or a literal replacement '?', it becomes
            // a misleading «◆?» badge instead of an obviously damaged string. Reject such a
            // name as a whole so MainActivity can fall back to the account/role label.
            if (cp == 0xFFFD || cp == 0xFFFC || cp == 0x25C6 || cp == 0x25A1
                    || cp == 0x25A0 || cp == '?' || Character.getType(cp) == Character.SURROGATE
                    || Character.isISOControl(cp)) return false;
            if (Character.isLetter(cp)) letter = true;
        }
        return letter;
    }

    /** UTF-16 offset after at most {@code count} extended grapheme-like clusters. */
    public static int graphemeBoundary(String value, int count) {
        if (value == null || value.isEmpty() || count <= 0) return 0;
        int offset = 0;
        for (int i = 0; offset < value.length() && i < count; i++) {
            int next = nextGraphemeBoundary(value, offset);
            if (next <= offset) break;
            offset = next;
        }
        return offset;
    }

    public static int graphemeCount(String value) {
        if (value == null || value.isEmpty()) return 0;
        int count = 0;
        int offset = 0;
        while (offset < value.length()) {
            int next = nextGraphemeBoundary(value, offset);
            if (next <= offset) break;
            offset = next;
            count++;
        }
        return count;
    }

    /** Truncate only at a grapheme boundary and add one visible ellipsis when needed. */
    public static String truncateGraphemes(String value, int maxGraphemes) {
        if (value == null || value.isEmpty()) return "";
        int end = graphemeBoundary(value, maxGraphemes);
        return end >= value.length() ? value : value.substring(0, end) + "…";
    }

    private static int nextGraphemeBoundary(String value, int start) {
        int first = value.codePointAt(start);
        int offset = start + Character.charCount(first);
        int previousBase = first;

        // Pair regional indicators (flag emoji) instead of splitting a flag in two.
        if (isRegionalIndicator(first) && offset < value.length()) {
            int next = value.codePointAt(offset);
            if (isRegionalIndicator(next)) {
                offset += Character.charCount(next);
                previousBase = next;
            }
        }

        while (offset < value.length()) {
            int next = value.codePointAt(offset);
            if (isGraphemeExtend(next)) {
                offset += Character.charCount(next);
                // A joiner belongs with the character on both sides; a virama joins the
                // following consonant in scripts that use conjunct glyphs. Permit a ZWJ
                // between the virama and consonant as well.
                if (next == 0x200C || next == 0x200D || isVirama(next)) {
                    while (offset < value.length()) {
                        int linked = value.codePointAt(offset);
                        if (linked == 0x200C || linked == 0x200D) {
                            offset += Character.charCount(linked);
                            continue;
                        }
                        previousBase = linked;
                        offset += Character.charCount(linked);
                        break;
                    }
                }
                continue;
            }
            if (hangulContinues(previousBase, next)) {
                previousBase = next;
                offset += Character.charCount(next);
                continue;
            }
            break;
        }
        return offset;
    }

    private static boolean isGraphemeExtend(int cp) {
        return isArabicMark(cp) || cp == 0x200C || cp == 0x200D
                || (cp >= 0xFE00 && cp <= 0xFE0F)
                || (cp >= 0xE0100 && cp <= 0xE01EF)
                || (cp >= 0x1F3FB && cp <= 0x1F3FF)
                || (cp >= 0xE0020 && cp <= 0xE007F);
    }

    private static boolean isRegionalIndicator(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    private static boolean isVirama(int cp) {
        switch (cp) {
            case 0x094D: case 0x09CD: case 0x0A4D: case 0x0ACD: case 0x0B4D:
            case 0x0BCD: case 0x0C4D: case 0x0CCD: case 0x0D3B: case 0x0D3C:
            case 0x0D4D: case 0x0DCA: case 0x1039: case 0x103A: case 0x17D2:
            case 0x1A60: case 0x1B44: case 0x1BAA: case 0xA806: case 0xA8C4:
            case 0xA953: case 0xAAF6: case 0xABED:
                return true;
            default:
                return false;
        }
    }

    private static boolean hangulContinues(int previous, int next) {
        int before = hangulClass(previous);
        int after = hangulClass(next);
        // L × (L|V|LV|LVT), (LV|V) × (V|T), (LVT|T) × T.
        if (before == 1) return after == 1 || after == 2 || after == 4 || after == 5;
        if (before == 2 || before == 4) return after == 2 || after == 3;
        return (before == 3 || before == 5) && after == 3;
    }

    private static int hangulClass(int cp) {
        if ((cp >= 0x1100 && cp <= 0x115F) || (cp >= 0xA960 && cp <= 0xA97C)) return 1; // L
        if ((cp >= 0x1160 && cp <= 0x11A7) || (cp >= 0xD7B0 && cp <= 0xD7C6)) return 2; // V
        if ((cp >= 0x11A8 && cp <= 0x11FF) || (cp >= 0xD7CB && cp <= 0xD7FB)) return 3; // T
        if (cp >= 0xAC00 && cp <= 0xD7A3) return (cp - 0xAC00) % 28 == 0 ? 4 : 5; // LV / LVT
        return 0;
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
