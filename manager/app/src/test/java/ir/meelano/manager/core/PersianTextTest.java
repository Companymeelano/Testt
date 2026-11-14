package ir.meelano.manager.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersianTextTest {
    @Test public void searchKeyFoldsArabicPersianLettersAndDigits() {
        assertEquals("علی کالا ۱۲۳", PersianText.cleanDisplayLabel("علی کالا ۱۲۳"));
        assertEquals("علیکالا123", PersianText.searchKey("علي كالا ۱۲۳"));
        assertEquals(PersianText.searchKey("کالا۱۲۳"), PersianText.searchKey("كالا١٢٣"));
    }

    @Test public void searchKeyIgnoresSpacesJoinersMarksAndDecoration() {
        assertEquals("نیمفاصله", PersianText.searchKey("نیم فاصله"));
        assertEquals("نیمفاصله", PersianText.searchKey("نیم\u200Cفاصله"));
        assertEquals("سلام", PersianText.searchKey("سَلام*"));
        assertEquals("abc12", PersianText.searchKey("A B-C* ۱۲"));
    }

    @Test public void productSearchTokensPreserveZwnjAsAWordJoinerAndSplitSpaces() {
        assertEquals(Arrays.asList("نیمفاصله", "12"), PersianText.productSearchTokens("نیم\u200Cفاصله ۱۲"));
        assertEquals(Arrays.asList("نیم", "فاصله"), PersianText.productSearchTokens("نیم فاصله"));
        assertTrue(PersianText.productSearchTokens("*** \u200C").isEmpty());
    }

    @Test public void cleanLabelsRemoveStarsAndPlaceholderGroups() {
        assertEquals("کالای ممتاز", PersianText.cleanDisplayLabel(" ** کالای  ممتاز* "));
        assertEquals("", PersianText.cleanGroupLabel(" -- "));
        assertEquals("خشکبار", PersianText.cleanGroupLabel(" خشکبار "));
    }

    @Test public void damagedNamesAreRejectedForSafeHeaderFallback() {
        assertTrue(PersianText.isUsablePersonName("نام کاربر"));
        assertFalse(PersianText.isUsablePersonName("◆?"));
        assertFalse(PersianText.isUsablePersonName("علی◆?"));
        assertFalse(PersianText.isUsablePersonName("علی?"));
        assertFalse(PersianText.isUsablePersonName("علی\uFFFD"));
    }

    @Test public void truncationKeepsCombiningMarksAndJoinerSequencesTogether() {
        assertEquals("اَ…", PersianText.truncateGraphemes("اَب", 1));
        assertEquals("👩‍💻…", PersianText.truncateGraphemes("👩‍💻نام", 1));
        assertEquals("👍🏽…", PersianText.truncateGraphemes("👍🏽نام", 1));
        assertEquals("🇪🇪…", PersianText.truncateGraphemes("🇪🇪نام", 1));
        assertEquals("ا\u200Cب…", PersianText.truncateGraphemes("ا\u200Cبج", 1));
        assertEquals("نام", PersianText.truncateGraphemes("نام", 10));
    }
}
