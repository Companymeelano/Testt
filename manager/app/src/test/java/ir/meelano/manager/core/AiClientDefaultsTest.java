package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Keeps the simple setup on one known provider/model so entering a key is sufficient. */
public final class AiClientDefaultsTest {
    @Test
    public void freshSetupDefaultsToOpenAiAndOneModelForEveryTask() {
        assertEquals(AiClient.OPENAI, AiClient.DEFAULT_PROVIDER);
        assertEquals(AiClient.OPENAI, AiClient.NAMES[0][0]);
        assertEquals("ChatGPT", AiClient.NAMES[0][1]);
        assertEquals("gpt-4o-mini", AiClient.defaultModel(AiClient.DEFAULT_PROVIDER));
        assertEquals(AiClient.OPENAI_DEFAULT_MODEL, AiClient.modelFor(AiClient.OPENAI, "fast"));
        assertEquals(AiClient.OPENAI_DEFAULT_MODEL, AiClient.modelFor(AiClient.OPENAI, "analysis"));
        assertEquals(AiClient.OPENAI_DEFAULT_MODEL, AiClient.modelFor(AiClient.OPENAI, "writing"));
        assertEquals("https://api.openai.com/v1", AiClient.baseUrl(AiClient.DEFAULT_PROVIDER, ""));
    }
}
