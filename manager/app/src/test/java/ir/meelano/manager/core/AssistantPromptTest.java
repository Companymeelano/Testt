package ir.meelano.manager.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AssistantPromptTest {
    @Test
    public void excludesWelcomeAndDoesNotDuplicateCurrentQuestion() {
        List<String[]> messages = new ArrayList<>();
        messages.add(new String[]{"bot", "خوش‌آمدگوییِ آغازین"});
        messages.add(new String[]{"user", "فروش امروز چقدر است؟"});
        messages.add(new String[]{"bot", "فروش ثبت‌شده در منبع ۵۰۰ است."});
        messages.add(new String[]{"user", "دیروز چطور؟"});

        String prompt = AssistantPrompt.build("منبع: گزارش روزانه", messages, "دیروز چطور؟");
        assertFalse(prompt.contains("خوش‌آمدگوییِ آغازین"));
        assertTrue(prompt.contains("فروش امروز چقدر است؟"));
        assertTrue(prompt.contains("فروش ثبت‌شده در منبع ۵۰۰ است."));
        assertTrue(prompt.endsWith("پرسش فعلی کاربر: دیروز چطور؟"));
        assertEqualsOne(prompt, "دیروز چطور؟");
    }

    @Test
    public void promptAndFactsAreBounded() {
        StringBuilder facts = new StringBuilder();
        for (int i = 0; i < 30_000; i++) facts.append('x');
        List<String[]> history = new ArrayList<>();
        for (int i = 0; i < 40; i++) history.add(new String[]{"user", "پرسش " + i});
        history.add(new String[]{"user", "پرسش نهایی"});
        String prompt = AssistantPrompt.build(facts.toString(), history, "پرسش نهایی");
        assertTrue(prompt.length() <= AssistantPrompt.MAX_PROMPT_CHARS);
        assertTrue(prompt.contains("پرسش نهایی"));
        assertTrue(prompt.contains("پرسش 39"));
        assertFalse(prompt.contains("پرسش 0\n"));
    }

    @Test
    public void credentialLikeTextIsRemovedBeforeBuildingThePrompt() {
        StringBuilder marker = new StringBuilder("sk-");
        for (int i = 0; i < 24; i++) marker.append('X');
        String prompt = AssistantPrompt.build("", new ArrayList<String[]>(), "متن "+ marker);
        assertFalse(prompt.contains(marker));
        assertTrue(prompt.contains("محرمانه حذف شد"));
    }

    @Test
    public void currentQuestionSurvivesLargeFactsAndHistory() {
        StringBuilder facts = new StringBuilder();
        for (int i = 0; i < 8_500; i++) facts.append('د');
        List<String[]> history = new ArrayList<>();
        for (int i = 0; i < 12; i++) history.add(new String[]{"ai", "پاسخ " + i + " " + facts.substring(0, 900)});
        history.add(new String[]{"user", "پرسش پایانی"});
        String prompt = AssistantPrompt.build(facts.toString(), history, "پرسش پایانی");
        assertTrue(prompt.length() <= AssistantPrompt.MAX_PROMPT_CHARS);
        assertTrue(prompt, prompt.contains("پرسش پایانی"));
        assertTrue(prompt, prompt.contains("پاسخ 11"));
        assertTrue(prompt, prompt.contains("حذف شد"));
    }

    private static void assertEqualsOne(String text, String needle) {
        int first = text.indexOf(needle);
        assertTrue(first >= 0);
        assertTrue(text.indexOf(needle, first + needle.length()) < 0);
    }
}
