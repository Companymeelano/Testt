package ir.meelano.manager.core;

import ir.meelano.licensing.License;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Reinstall recovery: the signed pack is device-bound and date-bearing, so a
 * pack found again in the SMS inbox restores the SAME license with the SAME
 * expiry — and a pack minted for another phone is never accepted.
 */
public class LicenseRecoveryTest {

    @Test
    public void findsTheDeviceBoundPackInsideSmsText() {
        String pack = License.generate("ABCD1234", License.P_MONTHLY, 30);
        String body = "کلید فعال‌سازی میلانو\n" + pack + "\nموفق باشید";
        assertEquals(License.normalize(pack), LicenseRecovery.packIn(body, "ABCD1234"));
        assertNull("pack for another phone is ignored",
                LicenseRecovery.packIn(body, "ZZZZ9999"));
    }

    @Test
    public void keepsTheOriginalDatesSoElapsedTimeStillCounts() {
        String pack = License.generate("ABCD1234", License.P_MONTHLY, 30);
        License.Result r = License.parse(LicenseRecovery.packIn(pack, "ABCD1234"));
        assertEquals("ABCD1234", r.dev);
        assertEquals("expiry travels with the pack",
                License.parse(pack).expDay, r.expDay);
        assertEquals("issue day travels with the pack",
                License.parse(pack).iatDay, r.iatDay);
    }

    @Test
    public void ignoresGarbageAndForeignTokens() {
        assertNull(LicenseRecovery.packIn("M1HELLO WORLD this is not a key", "ABCD1234"));
        assertNull(LicenseRecovery.packIn("", "ABCD1234"));
        assertNull(LicenseRecovery.packIn(null, "ABCD1234"));
        assertNull(LicenseRecovery.packIn("M1ABC", ""));
        assertNull(LicenseRecovery.packIn("M1ABC", null));
    }

    @Test
    public void dashedDisplayFormsAreAccepted() {
        String pack = License.generate("ABCD1234", License.P_PERM, 3650);
        String dashed = License.display(pack);
        assertNotNull(LicenseRecovery.packIn("کلید: " + dashed, "ABCD1234"));
    }

    @Test
    public void mobileNumbersNormalizeToCanonicalForm() {
        assertEquals("09123456789", SmsIo.normalizeMobile("+989123456789"));
        assertEquals("09123456789", SmsIo.normalizeMobile("989123456789"));
        assertEquals("09123456789", SmsIo.normalizeMobile("9123456789"));
        assertEquals("09123456789", SmsIo.normalizeMobile("09123456789"));
        assertEquals("09123456789", SmsIo.normalizeMobile(" 0912 345 6789 "));
        assertEquals("", SmsIo.normalizeMobile(""));
        assertEquals("", SmsIo.normalizeMobile(null));
    }
}
