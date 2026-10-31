package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeNotificationPolicyTest {
    @Test public void overnightQuietHoursIncludeStartAndExcludeEnd() {
        int start = 22 * 60;
        int end = 8 * 60;
        assertTrue(HomeNotificationPolicy.isQuietTime(true, start, end, 22 * 60));
        assertTrue(HomeNotificationPolicy.isQuietTime(true, start, end, 23 * 60 + 59));
        assertTrue(HomeNotificationPolicy.isQuietTime(true, start, end, 0));
        assertTrue(HomeNotificationPolicy.isQuietTime(true, start, end, 7 * 60 + 59));
        assertFalse(HomeNotificationPolicy.isQuietTime(true, start, end, 8 * 60));
        assertFalse(HomeNotificationPolicy.isQuietTime(true, start, end, 12 * 60));
    }

    @Test public void disabledOrEmptyQuietIntervalNeverSuppressesNotifications() {
        assertFalse(HomeNotificationPolicy.isQuietTime(false, 22 * 60, 8 * 60, 23 * 60));
        assertFalse(HomeNotificationPolicy.isQuietTime(true, 9 * 60, 9 * 60, 9 * 60));
    }

    @Test public void alertActionsMapOnlyToKnownInternalScreens() {
        assertEquals("products", HomeNotificationPolicy.screenFor(AlertEngine.Action.PRODUCT_FILE));
        assertEquals("sales", HomeNotificationPolicy.screenFor(AlertEngine.Action.SALES));
        assertEquals("buy", HomeNotificationPolicy.screenFor(AlertEngine.Action.PURCHASES));
        assertEquals("cheques", HomeNotificationPolicy.screenFor(AlertEngine.Action.CHEQUES));
        assertEquals("", HomeNotificationPolicy.screenFor(null));
    }
}
