package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Pure routing logic: order merging, cooldown policy and order parsing (no Android needed). */
public class AiRouterLogicTest {

    @Test
    public void primaryIsAlwaysFirstAndRegistryFillsTheRest() {
        List<String> order = AiRouter.mergeOrder(AiClient.GAPGPT, null);
        assertEquals(AiClient.GAPGPT, order.get(0));
        assertEquals(AiProviders.ALL.length, order.size());
        // no duplicates
        for (int i = 0; i < order.size(); i++) {
            assertEquals("duplicate " + order.get(i), i, order.indexOf(order.get(i)));
        }
    }

    @Test
    public void configuredFallbackOrderIsRespectedAfterPrimary() {
        List<String> order = AiRouter.mergeOrder(AiClient.GEMINI,
                Arrays.asList(AiClient.GROQ, AiClient.DEEPSEEK));
        assertEquals(AiClient.GEMINI, order.get(0));
        assertEquals(AiClient.GROQ, order.get(1));
        assertEquals(AiClient.DEEPSEEK, order.get(2));
        // GapGPT still present later (registry fill), but not first because primary is Gemini.
        assertTrue(order.contains(AiClient.GAPGPT));
        assertEquals(AiProviders.ALL.length, order.size());
    }

    @Test
    public void duplicatesBetweenPrimaryAndConfiguredAreCollapsed() {
        List<String> order = AiRouter.mergeOrder(AiClient.GAPGPT,
                Arrays.asList(AiClient.GAPGPT, AiClient.GROQ, AiClient.GROQ));
        assertEquals(AiClient.GAPGPT, order.get(0));
        assertEquals(AiClient.GROQ, order.get(1));
        assertEquals(AiProviders.ALL.length, order.size());
    }

    @Test
    public void unknownIdsAreIgnored() {
        List<String> order = AiRouter.mergeOrder("not-a-provider",
                Arrays.asList("also-fake", AiClient.GROQ));
        assertEquals(AiClient.GAPGPT, order.get(0)); // registry default fills first
        assertTrue(order.contains(AiClient.GROQ));
        assertTrue(!order.contains("not-a-provider"));
        assertTrue(!order.contains("also-fake"));
    }

    @Test
    public void nullPrimaryFallsBackToRegistryOrder() {
        List<String> order = AiRouter.mergeOrder(null, null);
        assertEquals(AiClient.GAPGPT, order.get(0));
        assertEquals(AiProviders.ALL.length, order.size());
    }

    @Test
    public void authIsPausedLongestThenRateThenServer() {
        long auth = AiRouter.cooldownMs(AiClient.KIND_AUTH);
        long rate = AiRouter.cooldownMs(AiClient.KIND_RATE);
        long server = AiRouter.cooldownMs(AiClient.KIND_SERVER);
        assertTrue("auth must pause longer than rate", auth > rate);
        assertTrue("rate must pause longer than server", rate > server);
        assertTrue(server > 0);
        assertEquals(60 * 60_000L, auth);
        assertEquals(10 * 60_000L, rate);
    }

    @Test
    public void networkAndClientErrorsAreRetryableWithoutCooldown() {
        assertEquals(0L, AiRouter.cooldownMs(AiClient.KIND_NET));
        assertEquals(0L, AiRouter.cooldownMs(AiClient.KIND_CLIENT));
        assertEquals(0L, AiRouter.cooldownMs(AiClient.KIND_OK));
    }

    @Test
    public void parseOrderTrimsAndDeduplicates() {
        assertEquals(Arrays.asList("a", "b", "c"), AiRouter.parseOrder("a,b,c"));
        assertEquals(Arrays.asList("a", "b"), AiRouter.parseOrder(" a , b , a "));
        assertTrue(AiRouter.parseOrder("").isEmpty());
        assertTrue(AiRouter.parseOrder(null).isEmpty());
    }
}