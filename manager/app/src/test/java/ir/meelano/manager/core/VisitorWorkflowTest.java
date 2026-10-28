package ir.meelano.manager.core;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test public void localModirProductionStillRequiresExactLocalSession() {
        assertTrue(ProductionWriter.isLocalModirProductionIdentity("Modir", -9001, RoleStore.ADMIN));
        assertFalse(ProductionWriter.isLocalModirProductionIdentity("modir", -9001, RoleStore.SELLER));
    }
}
