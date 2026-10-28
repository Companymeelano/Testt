package ir.meelano.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AtiranVisitorPricePolicyTest {
    @Test public void visitorDefaultGroupMapsDirectlyToAtiranSalePriceColumn() {
        for (int group = 1; group <= 5; group++) {
            assertEquals("forosh" + group, AtiranVisitorPricePolicy.priceColumn(group));
        }
    }

    @Test public void invalidOrUnconfiguredGroupsDoNotFallBackToCompanyPrice() {
        assertNull(AtiranVisitorPricePolicy.priceColumn(0));
        assertNull(AtiranVisitorPricePolicy.priceColumn(6));
        assertFalse(AtiranVisitorPricePolicy.hasUnambiguousPrice(0, 1, 125000));
        assertFalse(AtiranVisitorPricePolicy.hasUnambiguousPrice(3, 0, 125000));
        assertFalse(AtiranVisitorPricePolicy.hasUnambiguousPrice(3, 2, 125000));
        assertFalse(AtiranVisitorPricePolicy.hasUnambiguousPrice(3, 1, 0));
        assertTrue(AtiranVisitorPricePolicy.hasUnambiguousPrice(3, 1, 125000));
    }
}
