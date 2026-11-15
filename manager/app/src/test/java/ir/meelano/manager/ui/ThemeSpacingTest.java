package ir.meelano.manager.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ThemeSpacingTest {
    @Test public void spacesSnapToTheSharedFourDpGrid() {
        assertEquals(0, Theme.snapSpacingDp(0));
        assertEquals(0, Theme.snapSpacingDp(-1));
        assertEquals(4, Theme.snapSpacingDp(1));
        assertEquals(4, Theme.snapSpacingDp(4));
        assertEquals(8, Theme.snapSpacingDp(6));
        assertEquals(12, Theme.snapSpacingDp(10));
        assertEquals(32, Theme.snapSpacingDp(30));
    }
}
