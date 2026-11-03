package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Guards against accidentally shipping third-party API credentials in the APK. */
public class BuiltinKeysTest {
    @Test
    public void noProviderHasABundledCredential() {
        for (String[] provider : AiClient.NAMES) {
            assertEquals(provider[0], "", BuiltinKeys.get(provider[0]));
        }
    }

    @Test
    public void unknownProviderAlsoReturnsNoCredential() {
        assertEquals("", BuiltinKeys.get("unrecognized-provider"));
    }
}
