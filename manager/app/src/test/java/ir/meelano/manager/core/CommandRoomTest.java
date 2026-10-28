package ir.meelano.manager.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The command room is the manager's map of the whole app. A screen that is registered but
 * missing from the index would be unreachable, so the index is checked here.
 */
public class CommandRoomTest {
    /** Every screen the app registers (MainActivity) must be listed in the command room. */
    private static final String[] REGISTERED = {
            "home", "assistant", "sales", "buy", "dar_in", "dar_out", "cheques", "cash",
            "products", "product_file", "customers", "visitors", "visitor_work", "visitor_orders",
            "users", "profit", "production", "dues", "reports", "analytics", "settings", "search",
            "voice", "anbar", "tahvil", "resid", "takmil", "shomarsh", "score", "closeout",
            "manager_alerts", "sync_queue", "pick_wave"};

    @Test public void everyRegisteredScreenIsReachableFromTheCommandRoom() {
        List<String> ids = CommandRoom.sectionIds();
        for (String id : REGISTERED)
            assertTrue("screen not listed in the command room: " + id, ids.contains(id));
        assertEquals("the index must list every screen and nothing else",
                REGISTERED.length, ids.size());
    }

    @Test public void theIndexListsNoSectionTwice() {
        assertTrue("duplicate sections: " + CommandRoom.duplicates(), CommandRoom.duplicates().isEmpty());
    }

    @Test public void theManagerMayOpenEverySectionOfTheIndex() {
        for (String id : CommandRoom.sectionIds())
            assertTrue(id, RoleStore.allowedForRole(RoleStore.ADMIN, id));
    }

    @Test public void everySectionHasItsOwnArtwork() {
        for (String id : CommandRoom.sectionIds()) {
            CommandRoom.Entry entry = CommandRoom.find(id);
            assertTrue("no artwork for " + id, entry != null && !entry.art.isEmpty());
        }
    }

    @Test public void theVisitorIndexOnlyHoldsWorkspacePages() {
        List<String> pages = CommandRoom.visitorSectionIds();
        assertEquals(7, pages.size());
        for (String page : pages) {
            assertTrue(page, CommandRoom.isVisitorPage(page));
            assertFalse(page, CommandRoom.find(page) != null);
        }
    }
}
