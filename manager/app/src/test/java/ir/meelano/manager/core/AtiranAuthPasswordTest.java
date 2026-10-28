package ir.meelano.manager.core;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.assertTrue;

public class AtiranAuthPasswordTest {
    @Test public void acceptsSha512RawAndHexVerifiers() throws Exception {
        String sample = "unit-test-only-password";
        byte[] digest = MessageDigest.getInstance("SHA-512")
                .digest(sample.getBytes(StandardCharsets.UTF_8));
        assertTrue(AtiranAuth.verifyPassword(digest, sample, "sample-user"));

        StringBuilder hex = new StringBuilder(128);
        for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
        assertTrue(AtiranAuth.verifyPassword(hex.toString().getBytes(StandardCharsets.US_ASCII),
                sample, "sample-user"));
    }
}
