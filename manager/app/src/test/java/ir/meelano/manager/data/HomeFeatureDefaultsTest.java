package ir.meelano.manager.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeFeatureDefaultsTest {
    @Test public void firstThreeNewHomeFeaturesAreEnabledAndLaterOnesStayOff() {
        String[] order = Settings.HOME_ORDER_DEFAULT.split(",");
        String[] selected = Settings.HOME_SECTIONS_DEFAULT.split(",");
        Set<String> enabled = new HashSet<>(Arrays.asList(selected));

        assertEquals("smart_report", order[0]);
        assertEquals("closeout", order[1]);
        assertEquals("cash_position", order[2]);
        assertTrue(enabled.contains("smart_report"));
        assertTrue(enabled.contains("closeout"));
        assertTrue(enabled.contains("cash_position"));
        assertEquals("only the first three features are enabled by default", 3, enabled.size());
        for (int i = 3; i < 9; i++) assertTrue("unexpected default-on feature: " + order[i], !enabled.contains(order[i]));
    }
}
