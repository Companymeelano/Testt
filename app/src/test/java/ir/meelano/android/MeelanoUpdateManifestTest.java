package ir.meelano.android;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MeelanoUpdateManifestTest {
    private JSONObject manifest() throws Exception {
        JSONObject json = new JSONObject();
        json.put("versionCode", 203);
        json.put("versionName", "7.0.3");
        json.put("packageName", MeelanoUpdateManifest.PACKAGE_NAME);
        json.put("url", "https://github.com/Companymeelano/Testt/releases/download/apk-v7-0-3/MEELANO-v7.0.3-release.apk");
        json.put("sha256", "a".repeat(64));
        json.put("signerSha256", MeelanoUpdateManifest.RELEASE_SIGNER_SHA256);
        json.put("size", 2_700_000);
        return json;
    }

    @Test public void acceptsNewerWellFormedRelease() throws Exception {
        MeelanoUpdateManifest parsed = MeelanoUpdateManifest.parse(manifest().toString(), 202);
        assertEquals(203, parsed.versionCode);
        assertEquals("7.0.3", parsed.versionName);
        assertEquals(2_700_000, parsed.size);
    }

    @Test public void rejectsSameOrOlderVersion() throws Exception {
        assertRejected(manifest().toString(), 203);
        assertRejected(manifest().toString(), 204);
    }

    @Test public void rejectsUnexpectedPackageHostAndSigner() throws Exception {
        JSONObject json = manifest();
        json.put("packageName", "ir.attacker.app");
        assertRejected(json.toString(), 200);

        json = manifest();
        json.put("url", "https://example.com/app.apk");
        assertRejected(json.toString(), 200);

        json = manifest();
        json.put("signerSha256", "b".repeat(64));
        assertRejected(json.toString(), 200);
    }

    @Test public void rejectsMissingOrMalformedIntegrityMetadata() throws Exception {
        JSONObject json = manifest();
        json.put("sha256", "not-a-digest");
        assertRejected(json.toString(), 200);

        json = manifest();
        json.put("size", MeelanoUpdateManifest.MAX_APK_BYTES + 1);
        assertRejected(json.toString(), 200);

        json = manifest();
        json.remove("signerSha256");
        assertRejected(json.toString(), 200);
    }

    private static void assertRejected(String json, long installedCode) throws Exception {
        try {
            MeelanoUpdateManifest.parse(json, installedCode);
            fail("Invalid update manifest was accepted");
        } catch (Exception expected) {
            // Expected rejection by validation policy.
        }
    }
}
