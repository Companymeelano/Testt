package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Registry integrity: every required service exists, GapGPT is primary, wire formats are correct. */
public class AiProvidersTest {

    @Test
    public void containsAllNineRequiredServicesPlusCustom() {
        String[] required = {
                AiClient.GAPGPT, AiClient.GROQ, AiClient.DEEPSEEK, AiClient.GEMINI, AiClient.OPENAI,
                AiClient.GROK, AiClient.OPENROUTER, AiClient.CEREBRAS, AiClient.HUGGINGFACE, AiClient.CUSTOM
        };
        for (String id : required) {
            assertTrue("missing provider " + id, AiProviders.isKnown(id));
            assertNotNull(AiProviders.byId(id));
        }
        assertEquals(required.length, AiProviders.ALL.length);
    }

    @Test
    public void gapGptIsTheDefaultPrimaryAndFirstInRegistry() {
        assertEquals(AiClient.GAPGPT, AiProviders.defaultPrimary());
        assertEquals(AiClient.GAPGPT, AiProviders.ALL[0].id);
        assertEquals(AiClient.GAPGPT, AiProviders.ids().get(0));
    }

    @Test
    public void baseUrlsMatchTheSpecifiedEndpoints() {
        assertEquals("https://api.gapgpt.app/v1", AiClient.baseUrl(AiClient.GAPGPT, ""));
        assertEquals("https://api.groq.com/openai/v1", AiClient.baseUrl(AiClient.GROQ, ""));
        assertEquals("https://api.deepseek.com/v1", AiClient.baseUrl(AiClient.DEEPSEEK, ""));
        assertEquals("https://generativelanguage.googleapis.com/v1beta", AiClient.baseUrl(AiClient.GEMINI, ""));
        assertEquals("https://api.x.ai/v1", AiClient.baseUrl(AiClient.GROK, ""));
        assertEquals("https://api.openai.com/v1", AiClient.baseUrl(AiClient.OPENAI, ""));
        assertEquals("https://openrouter.ai/api/v1", AiClient.baseUrl(AiClient.OPENROUTER, ""));
        assertEquals("https://api.cerebras.ai/v1", AiClient.baseUrl(AiClient.CEREBRAS, ""));
        assertEquals("https://router.huggingface.co/v1", AiClient.baseUrl(AiClient.HUGGINGFACE, ""));
    }

    @Test
    public void onlyGeminiUsesTheNativeWireFormat() {
        assertTrue(AiClient.isGemini(AiClient.GEMINI));
        assertEquals(AiProviders.WIRE_GEMINI, AiProviders.wire(AiClient.GEMINI));
        String[] openAiCompatible = {
                AiClient.GAPGPT, AiClient.GROQ, AiClient.DEEPSEEK, AiClient.OPENAI,
                AiClient.GROK, AiClient.OPENROUTER, AiClient.CEREBRAS, AiClient.HUGGINGFACE, AiClient.CUSTOM
        };
        for (String id : openAiCompatible) {
            assertFalse(id + " must not use the Gemini wire format", AiClient.isGemini(id));
            assertEquals(AiProviders.WIRE_OPENAI, AiProviders.wire(id));
        }
    }

    @Test
    public void customUsesTheUserTypedBaseUrl() {
        assertEquals("https://my.proxy/v1", AiClient.baseUrl(AiClient.CUSTOM, "  https://my.proxy/v1  "));
        assertEquals("", AiClient.baseUrl(AiClient.CUSTOM, null));
    }

    @Test
    public void everyNonCustomServiceHasADefaultModel() {
        for (AiProviders.Spec s : AiProviders.ALL) {
            if (AiClient.CUSTOM.equals(s.id)) continue;
            assertFalse("no default model for " + s.id, AiClient.defaultModel(s.id).isEmpty());
        }
    }

    @Test
    public void analysisUsesTheStrongerModelWhereDefined() {
        assertEquals("gemini-2.5-pro", AiClient.modelFor(AiClient.GEMINI, "analysis"));
        assertEquals("gemini-2.5-flash", AiClient.modelFor(AiClient.GEMINI, "fast"));
        assertEquals("gpt-4o", AiClient.modelFor(AiClient.OPENAI, "analysis"));
        assertEquals("deepseek-reasoner", AiClient.modelFor(AiClient.DEEPSEEK, "analysis"));
        assertEquals("deepseek-chat", AiClient.modelFor(AiClient.DEEPSEEK, "general"));
    }

    @Test
    public void speechIsOfferedOnlyByGeminiAndOpenAi() {
        assertTrue(AiClient.hasSpeech(AiClient.GEMINI));
        assertTrue(AiClient.hasSpeech(AiClient.OPENAI));
        assertFalse(AiClient.hasSpeech(AiClient.GROK));
        assertFalse(AiClient.hasSpeech(AiClient.GAPGPT));
        assertFalse(AiClient.defaultTtsModel(AiClient.GEMINI).isEmpty());
    }

    @Test
    public void unknownProviderIsRejected() {
        assertFalse(AiProviders.isKnown("does-not-exist"));
        assertNull(AiProviders.byId("does-not-exist"));
        assertFalse(AiProviders.isKnown(null));
    }

    @Test
    public void namesAndIdsStayInSync() {
        String[][] names = AiClient.NAMES;
        assertEquals(AiProviders.ALL.length, names.length);
        for (int i = 0; i < names.length; i++) {
            assertEquals(AiProviders.ALL[i].id, names[i][0]);
            assertEquals(AiProviders.ALL[i].name, names[i][1]);
        }
    }
}