package ir.meelano.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VisitorVoiceOrderParserTest {
    @Test public void parsesPersianAndArabicDigitsAndRemovesUnitsAndCommands() {
        VisitorVoiceOrderParser.Request fa = VisitorVoiceOrderParser.parse("میلو لطفاً ۲ بسته پسته اکبری اضافه کن");
        assertTrue(fa.isValid());
        assertEquals(2d, fa.quantity, 0.0001d);
        assertEquals("بسته", fa.requestedUnit);
        assertEquals("پسته اکبری", fa.productQuery);

        VisitorVoiceOrderParser.Request ar = VisitorVoiceOrderParser.parse("٣ عدد بادام هندی به سبد اضافه کن");
        assertTrue(ar.isValid());
        assertEquals(3d, ar.quantity, 0.0001d);
        assertEquals("عدد", ar.requestedUnit);
        assertEquals("بادام هندی", ar.productQuery);

        VisitorVoiceOrderParser.Request conversational = VisitorVoiceOrderParser.parse("لطفا برام ۳ تا بادام درختی بذار");
        assertTrue(conversational.isValid());
        assertEquals(3d, conversational.quantity, 0.0001d);
        assertEquals("بادام درختی", conversational.productQuery);
    }

    @Test public void parsesSpokenPersianQuantityAndDoesNotTreatEmbeddedPackageSizeAsQuantity() {
        VisitorVoiceOrderParser.Request spoken = VisitorVoiceOrderParser.parse("دو کیلو پسته اکبری");
        assertTrue(spoken.isValid());
        assertEquals(2d, spoken.quantity, 0.0001d);
        assertEquals("کیلو", spoken.requestedUnit);
        assertEquals("پسته اکبری", spoken.productQuery);

        VisitorVoiceOrderParser.Request productSize = VisitorVoiceOrderParser.parse("پسته ۵۰۰ گرمی");
        assertTrue(productSize.isValid());
        assertEquals(1d, productSize.quantity, 0.0001d);
        assertEquals("پسته 500 گرمی", productSize.productQuery);
    }

    @Test public void rejectsEmptyAndOutOfRangeQuantitiesInsteadOfSilentlyAddingOne() {
        assertFalse(VisitorVoiceOrderParser.parse("").isValid());
        assertFalse(VisitorVoiceOrderParser.parse("۰ بسته پسته").isValid());
        assertFalse(VisitorVoiceOrderParser.parse("10001 بسته پسته").isValid());
    }
}
