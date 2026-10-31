package ir.meelano.manager.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The command room is the manager's map of the whole app. A screen that is registered but
 * missing from the index would be unreachable, so the index is checked here.
 */
public class CommandRoomTest {
    /** Every screen the app registers (MainActivity), except the command room itself. */
    private static final String[] REGISTERED = {
            "home", "assistant", "sales", "buy", "dar_in", "dar_out", "cheques", "cash",
            "products", "product_file", "customers", "visitors", "visitor_work", "visitor_orders",
            "users", "profit", "production", "dues", "reports", "analytics", "settings", "search",
            "voice", "anbar", "tahvil", "resid", "takmil", "shomarsh", "score", "closeout",
            "manager_alerts", "sync_queue", "pick_wave"};

    /** The tv display is not a screen of the app, it is an action of the command room. */
    private static final String TV = "tv";

    @Test public void everyRegisteredScreenIsReachableFromTheCommandRoom() {
        List<String> ids = CommandRoom.sectionIds();
        for (String id : REGISTERED)
            assertTrue("screen not listed in the command room: " + id, ids.contains(id));
        // Nothing but the registered screens and the tv display may be listed.
        for (String id : ids)
            assertTrue("unknown section in the command room: " + id,
                    TV.equals(id) || Arrays.asList(REGISTERED).contains(id));
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
            assertTrue("section not indexed: " + id, entry != null);
            // The tv display opens its own window, so it carries no section artwork.
            if (TV.equals(id)) continue;
            assertTrue("no artwork for " + id, !entry.art.isEmpty());
        }
    }

    @Test public void theVisitorIndexOnlyHoldsWorkspacePages() {
        List<String> pages = CommandRoom.visitorSectionIds();
        assertEquals(7, pages.size());
        for (String page : pages) {
            assertTrue(page, CommandRoom.isVisitorPage(page));
            assertTrue("visitor page missing from the visitor index: " + page,
                    CommandRoom.findVisitor(page) != null);
        }
    }
}
