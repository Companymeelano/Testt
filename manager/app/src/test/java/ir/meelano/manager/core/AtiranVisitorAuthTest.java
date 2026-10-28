package ir.meelano.manager.core;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AtiranVisitorAuthTest {
    @Test public void verifiesVisitorTextCredentialWithoutSysUserHashColumn() {
        String sample = "visitor-test-secret";
        assertTrue(AtiranAuth.verifyVisitorPassword(sample, sample, "sample-visitor"));
        assertFalse(AtiranAuth.verifyVisitorPassword(sample, "different-test-secret", "sample-visitor"));
    }

    @Test public void verifiesStandardHexHashStoredInVisitorsPassword() throws Exception {
        String sample = "visitor-hash-test";
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(sample.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
        assertTrue(AtiranAuth.verifyVisitorPassword(hex.toString(), sample, "sample-visitor"));
        assertFalse(AtiranAuth.verifyVisitorPassword("", sample, "sample-visitor"));
    }
}
