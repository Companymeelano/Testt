package ir.meelano.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class VisitorAddressReviewTest {
    @Test public void newCustomerWithExistingAddressRequiresReview() {
        assertTrue(VisitorAddressReview.required("10", "11", "نشانی قبلی"));
    }
    @Test public void sameCustomerKeepsManualAddressWithoutPrompt() {
        assertFalse(VisitorAddressReview.required(" 10 ", "10", "نشانی دستی"));
    }
    @Test public void unknownCodeCannotProveSameCustomer() {
        assertTrue(VisitorAddressReview.required(null, "10", "نشانی"));
        assertTrue(VisitorAddressReview.required("", "", "نشانی"));
        assertTrue(VisitorAddressReview.required("10", null, "نشانی"));
    }
    @Test public void noAddressNeedsNoReview() {
        assertFalse(VisitorAddressReview.required("10", "11", null));
        assertFalse(VisitorAddressReview.required("10", "11", "  "));
    }
}
