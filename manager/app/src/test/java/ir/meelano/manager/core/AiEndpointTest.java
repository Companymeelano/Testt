package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class AiEndpointTest {
    @Test
    public void acceptsAndNormalizesHttpsEndpointWithoutCredentials() {
        assertEquals("https://ai.example.test/ai-api/api.php",
                AiEndpoint.normalize("  https://ai.example.test/ai-api/api.php  "));
    }

    @Test
    public void rejectsHttpAndCredentialBearingUrls() {
        reject("http://ai.example.test/api.php");
        reject("https://test-user@ai.example.test/api.php");
        reject("https://ai.example.test/api.php?example=1");
        reject("https://ai.example.test/api.php#fragment");
        reject("not-a-url");
    }

    private static void reject(String value) {
        try {
            AiEndpoint.normalize(value);
            fail("Expected invalid endpoint to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: endpoint validation fails closed.
        }
    }
}
