package ir.meelano.manager.core;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VisitorWorkflowTest {
    @Test public void visitorPriceGroupSelectsTheMatchingForoshColumnOnly() {
        assertEquals("forosh1", VisitorPricePolicy.column(1));
        assertEquals("forosh5", VisitorPricePolicy.column(5));
        assertEquals(null, VisitorPricePolicy.column(0));
        assertEquals(null, VisitorPricePolicy.column(6));
        assertTrue(VisitorPricePolicy.isUnambiguous(3, 1, 125000));
        assertFalse(VisitorPricePolicy.isUnambiguous(3, 2, 125000));
        assertFalse(VisitorPricePolicy.isUnambiguous(3, 0, 125000));
        assertFalse(VisitorPricePolicy.isUnambiguous(3, 1, 0));
    }

    @Test public void visitorIdentityRejectsDisabledAndMalformedFlags() {
        assertTrue(VisitorIdentity.isActive("T"));
        assertTrue(VisitorIdentity.isActive("1"));
        assertFalse(VisitorIdentity.isActive(null));
        assertFalse(VisitorIdentity.isActive(""));
        assertFalse(VisitorIdentity.isActive("F"));
        assertFalse(VisitorIdentity.isActive("0"));
        assertFalse(VisitorIdentity.isActive("false"));
    }

    @Test public void voiceOrderParsingConvertsPersianNumeralsAndProductPhrase() {
        VisitorVoiceOrderParser.Request request = VisitorVoiceOrderParser.parse("۲ بسته پسته اکبری لطفاً اضافه کن");
        assertTrue(request.isValid());
        assertEquals(2d, request.quantity, 0.0001d);
        assertEquals("بسته", request.requestedUnit);
        assertEquals("پسته اکبری", request.productQuery);
    }

    @Test public void preinvoiceLineTotalUsesRialPrecision() {
        VisitorPrefactorWriter.Line line = new VisitorPrefactorWriter.Line("12", "کالا", "عدد",
                new BigDecimal("2.5"), new BigDecimal("123.45"), 2, BigDecimal.TEN);
        assertEquals(new BigDecimal("308.63"), line.total());
    }

    @Test public void visitorCannotOpenUnscopedManagementListsOrGenericAssistant() {
        assertArrayEquals(new String[]{"visitor_work","more"},RoleStore.tabsFor(RoleStore.VISITOR));
        assertEquals("visitor_work",RoleStore.homeFor(RoleStore.VISITOR));
        assertTrue(RoleStore.allowedForRole(RoleStore.VISITOR,"visitor_work"));
        assertTrue(RoleStore.allowedForRole(RoleStore.VISITOR,"more"));
        assertFalse(RoleStore.allowedForRole(RoleStore.VISITOR,"sales"));
        assertFalse(RoleStore.allowedForRole(RoleStore.VISITOR,"customers"));
        assertFalse(RoleStore.allowedForRole(RoleStore.VISITOR,"dues"));
        assertFalse(RoleStore.allowedForRole(RoleStore.VISITOR,"assistant"));
        assertFalse(RoleStore.allowedForRole(RoleStore.VISITOR,"visitor_orders"));
    }

    @Test public void productionRejectsSyntheticLocalIdsBeforeDatabaseAccess() {
        for (int uid : new int[]{-9001, 0}) {
            try {
                ProductionWriter.resolveAtiranUserId(null, uid);
                fail("Synthetic or missing user IDs must not reach production posting");
            } catch (Exception expected) {
                assertTrue(expected.getMessage().contains("نشست معتبر آتیران"));
            }
        }
    }

    /**
     * Atiran may keep several DeviceSettings rows per visitor (one per server or sales
     * line). They must all carry the same price group; two different groups are never
     * guessed between, and a row without a usable group refuses the whole set.
     */
    @Test public void theVisitorPriceGroupMustBeUniqueAcrossAllOfItsRows() {
        assertEquals(3, VisitorPricePolicy.resolveUniqueGroup(java.util.Arrays.asList(3, 3, 3)));
        assertEquals(1, VisitorPricePolicy.resolveUniqueGroup(java.util.Arrays.asList(1)));
        assertEquals(0, VisitorPricePolicy.resolveUniqueGroup(java.util.Arrays.asList(3, 4)));
        assertEquals(0, VisitorPricePolicy.resolveUniqueGroup(java.util.Arrays.asList(3, null)));
        assertEquals(0, VisitorPricePolicy.resolveUniqueGroup(java.util.Arrays.asList(0)));
        assertEquals(0, VisitorPricePolicy.resolveUniqueGroup(java.util.Arrays.asList(6)));
        assertEquals(0, VisitorPricePolicy.resolveUniqueGroup(new java.util.ArrayList<Integer>()));
    }

    /**
     * The session id of a visitor is the sys_vis.UserID, so the account is resolved from
     * the Atiran links: the login name wins, the access grant is the fallback, and two
     * different visitors behind one account are reported instead of being guessed.
     */
    @Test public void theVisitorAccountIsResolvedFromTheAtiranLinks() throws Exception {
        assertEquals(7, VisitorIdentity.pickPrimary(java.util.Arrays.asList(7), java.util.Arrays.asList(7)));
        assertEquals(7, VisitorIdentity.pickPrimary(java.util.Arrays.asList(7), new java.util.ArrayList<Integer>()));
        assertEquals(7, VisitorIdentity.pickPrimary(new java.util.ArrayList<Integer>(), java.util.Arrays.asList(7)));
        try {
            VisitorIdentity.pickPrimary(new java.util.ArrayList<Integer>(), java.util.Arrays.asList(7, 8));
            fail("One account behind two visitors must be reported, not guessed");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("چند ویزیتور"));
        }
        try {
            VisitorIdentity.pickPrimary(java.util.Arrays.asList(7, 8), new java.util.ArrayList<Integer>());
            fail("A duplicated login name must be reported, not guessed");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("چند ردیف"));
        }
    }
}
