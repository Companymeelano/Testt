package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds a bounded, in-memory conversation prompt with only the facts needed for this turn. */
public final class AssistantPrompt {
    private AssistantPrompt() { }

    public static final int MAX_PROMPT_CHARS = 16_000;
    public static final int MAX_CONVERSATION_MESSAGES = 20;
    private static final int MAX_FACT_CHARS = 8_000;
    private static final int MAX_HISTORY_MESSAGES = MAX_CONVERSATION_MESSAGES - 1;
    private static final int MAX_USER_MESSAGE_CHARS = 700;
    private static final int MAX_ASSISTANT_MESSAGE_CHARS = 900;

    /** A proxy request keeps extracted facts apart from the role-labeled, in-memory conversation. */
    public static final class Conversation {
        public final String context;
        public final List<String[]> messages;

        private Conversation(String context, List<String[]> messages) {
            this.context = context;
            List<String[]> copy = new ArrayList<>();
            for (String[] message : messages) copy.add(message.clone());
            this.messages = Collections.unmodifiableList(copy);
        }
    }

    /**
     * The supplied history is not persisted; callers pass only this screen's current messages.
     * Only user/assistant roles are sent, and the current question appears once at the end.
     */
    public static Conversation buildConversation(String facts, List<String[]> sourceMessages,
                                                 String currentQuestion) {
        String context = sanitize(facts, MAX_FACT_CHARS);
        String question = sanitize(currentQuestion, 1_500);
        List<String[]> history = new ArrayList<>();
        if (sourceMessages != null && !sourceMessages.isEmpty()) {
            int firstUser = 0;
            while (firstUser < sourceMessages.size() && !isRole(sourceMessages.get(firstUser), "user")) {
                firstUser++;
            }
            int currentIndex = lastCurrentQuestion(sourceMessages, currentQuestion);
            for (int i = sourceMessages.size() - 1; i >= firstUser && history.size() < MAX_HISTORY_MESSAGES; i--) {
                if (i == currentIndex) continue;
                String[] entry = sourceMessages.get(i);
                String role = normalizedRole(entry);
                if (role == null) continue;
                String text = sanitize(entry[1], "user".equals(role)
                        ? MAX_USER_MESSAGE_CHARS : MAX_ASSISTANT_MESSAGE_CHARS);
                if (text.isEmpty() || "…".equals(text)) continue;
                history.add(0, new String[]{role, text});
            }
        }
        history.add(new String[]{"user", question});
        return new Conversation(context, history);
    }

    /**
     * Compatibility prompt for providers that accept a single user string. The same bounded
     * conversation is rendered as plain Persian text; the secure PHP proxy sends roles separately.
     */
    public static String build(String facts, List<String[]> messages, String currentQuestion) {
        Conversation conversation = buildConversation(facts, messages, currentQuestion);
        StringBuilder prefix = new StringBuilder();
        if (!conversation.context.isEmpty()) {
            prefix.append("منبع دادهٔ مجاز برای همین پرسش:\n")
                    .append(conversation.context).append('\n');
        }
        prefix.append("گفت‌وگوی اخیر (قدیمی به جدید؛ فقط همین نشست):\n");

        List<String> history = new ArrayList<>();
        for (int i = 0; i < conversation.messages.size() - 1; i++) {
            String[] entry = conversation.messages.get(i);
            history.add(("user".equals(entry[0]) ? "کاربر: " : "دستیار: ") + entry[1]);
        }
        String currentLine = "پرسش فعلی کاربر: "
                + conversation.messages.get(conversation.messages.size() - 1)[1];
        String omission = "بخشی از تاریخچهٔ قدیمی‌تر برای محدودیت طول حذف شد.\n";
        int historyLimit = MAX_PROMPT_CHARS - prefix.length() - currentLine.length() - 1;
        int historyLength = historyLength(history);
        boolean omitted = false;
        while (!history.isEmpty() && historyLength > historyLimit) {
            historyLength -= history.remove(0).length() + 1;
            omitted = true;
        }
        if (omitted) {
            historyLimit -= omission.length();
            while (!history.isEmpty() && historyLength > historyLimit) {
                historyLength -= history.remove(0).length() + 1;
            }
        }

        StringBuilder prompt = new StringBuilder(prefix);
        if (omitted) prompt.append(omission);
        for (String line : history) prompt.append(line).append('\n');
        prompt.append(currentLine);
        return prompt.toString();
    }

    private static int historyLength(List<String> history) {
        int total = 0;
        for (String line : history) total += line.length() + 1;
        return total;
    }

    private static int lastCurrentQuestion(List<String[]> messages, String question) {
        String q = question == null ? "" : question.trim();
        for (int i = messages.size() - 1; i >= 0; i--) {
            String[] row = messages.get(i);
            if (isRole(row, "user") && q.equals(row[1] == null ? "" : row[1].trim())) return i;
        }
        return -1;
    }

    private static String normalizedRole(String[] message) {
        if (isRole(message, "user")) return "user";
        if (isRole(message, "bot") || isRole(message, "ai")) return "assistant";
        return null;
    }

    private static boolean isRole(String[] message, String role) {
        return message != null && message.length > 1 && role.equals(message[0]);
    }

    private static String sanitize(String value, int limit) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(Math.min(value.length(), limit));
        for (int i = 0; i < value.length() && out.length() < limit; i++) {
            char ch = value.charAt(i);
            if (ch == '\r' || ch == '\t' || ch == '\n' || !Character.isISOControl(ch)) out.append(ch);
        }
        return AiPromptRedactor.redact(out.toString().trim());
    }
}
