package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Error classification (auth vs rate vs server vs network) and the no-embedded-keys rule. */
public class AiErrorClassificationTest {

    @Test
    public void authCodesAreClassifiedAsAuth() {
        assertEquals(AiClient.KIND_AUTH, AiClient.classify(401));
        assertEquals(AiClient.KIND_AUTH, AiClient.classify(403));
    }

    @Test
    public void rateLimitIsClassifiedAsRate() {
        assertEquals(AiClient.KIND_RATE, AiClient.classify(429));
    }

    @Test
    public void serverErrorsAreClassifiedAsServer() {
        assertEquals(AiClient.KIND_SERVER, AiClient.classify(500));
        assertEquals(AiClient.KIND_SERVER, AiClient.classify(502));
        assertEquals(AiClient.KIND_SERVER, AiClient.classify(503));
        assertEquals(AiClient.KIND_SERVER, AiClient.classify(599));
    }

    @Test
    public void otherClientErrorsAreClassifiedAsClient() {
        assertEquals(AiClient.KIND_CLIENT, AiClient.classify(400));
        assertEquals(AiClient.KIND_CLIENT, AiClient.classify(404));
        assertEquals(AiClient.KIND_CLIENT, AiClient.classify(422));
    }

    @Test
    public void successIsOk() {
        assertEquals(AiClient.KIND_OK, AiClient.classify(200));
        assertEquals(AiClient.KIND_OK, AiClient.classify(201));
    }

    @Test
    public void authIsNeverConfusedWithTransientErrors() {
        assertFalse(AiClient.classify(401).equals(AiClient.classify(503)));
        assertFalse(AiClient.classify(403).equals(AiClient.classify(429)));
        assertFalse(AiClient.classify(429).equals(AiClient.classify(500)));
    }

    @Test
    public void kindLabelsAreHumanReadableAndNonSecret() {
        assertTrue(AiClient.kindLabel(AiClient.KIND_AUTH).contains("نامعتبر"));
        assertTrue(AiClient.kindLabel(AiClient.KIND_RATE).contains("محدودیت"));
        assertFalse(AiClient.kindLabel(AiClient.KIND_OK).isEmpty());
        assertFalse(AiClient.kindLabel("untested").isEmpty());
    }

    @Test
    public void noApiKeysAreEmbeddedInTheApp() {
        // Security regression: the assistant must never fall back to a built-in key.
        for (String[] n : AiClient.NAMES) {
            assertEquals("embedded key for " + n[0], "", BuiltinKeys.get(n[0]));
        }
        assertEquals("", BuiltinKeys.get(null));
        assertEquals("", BuiltinKeys.get("unknown"));
    }
}