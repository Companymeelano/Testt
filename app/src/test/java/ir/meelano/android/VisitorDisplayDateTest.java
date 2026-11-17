package ir.meelano.android;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class VisitorDisplayDateTest {
    @Test public void convertsStoredTimestampToJalali() {
        assertEquals("1403/01/01 13:45", MeelanoJalali.displayGregorianTimestamp("2024/03/20 13:45"));
    }
    @Test public void preservesUnknownOrInvalidInputs() {
        assertEquals("ثبت نشده", MeelanoJalali.displayGregorianTimestamp("ثبت نشده"));
        assertEquals("2024/02/31 13:45", MeelanoJalali.displayGregorianTimestamp("2024/02/31 13:45"));
    }
}
