package ir.meelano.manager.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes obvious credentials from user text and bounded report facts before any model request. */
public final class AiPromptRedactor {
    private AiPromptRedactor() { }

    private static final String REDACTED = "[محرمانه حذف شد]";
    private static final Pattern[] PATTERNS = {
            Pattern.compile("(?i)\\bsk-(?:ant-)?[A-Za-z0-9_-]{16,}\\b"),
            Pattern.compile("(?i)\\bAIza[A-Za-z0-9_-]{25,}\\b"),
            Pattern.compile("(?i)\\bgsk_[A-Za-z0-9_-]{16,}\\b"),
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~-]{16,}"),
            Pattern.compile("(?i)\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"),
            Pattern.compile("(?i)(?:api[_ -]?key|password|passwd|secret|token|credential|authorization|رمز|گذرواژه)\\s*[:=：]\\s*[^\\s,;]+")
    };

    public static String redact(String value) {
        if (value == null || value.isEmpty()) return "";
        String redacted = value;
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(redacted);
            redacted = matcher.replaceAll(REDACTED);
        }
        return redacted;
    }
}
