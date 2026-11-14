package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AiTaskTest {
    @Test public void classifiesShortAndLongQuestions() {
        assertEquals("fast", AiTask.forQuestion("فروش امروز؟"));
        assertEquals("general", AiTask.forQuestion("لطفاً وضعیت فروش و موجودی کالاهای امروز را برایم روشن کن"));
    }

    @Test public void prioritizesWritingAndAnalysisTasks() {
        assertEquals("writing", AiTask.forQuestion("یک پیام کوتاه بنویس"));
        assertEquals("analysis", AiTask.forQuestion("گزارش روند سود را تحلیل کن"));
    }

    @Test public void normalizesArabicPersianVariants() {
        assertEquals("analysis", AiTask.forQuestion("تحلیل سود"));
        assertEquals("fast", AiTask.forQuestion(null));
    }
}
