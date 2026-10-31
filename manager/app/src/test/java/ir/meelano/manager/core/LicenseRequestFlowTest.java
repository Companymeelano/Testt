package ir.meelano.manager.core;

import ir.meelano.licensing.License;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** The client sends a device code; the seller enters a new customer's name in the issuer app. */
public class LicenseRequestFlowTest {

    @Test
    public void requestWithoutCustomerNameStillCarriesDeviceAndContact() {
        String line = License.requestLine("ABCD1234", "", "", "", "09123456789", "");
        License.Req request = License.parseRequest("درخواست فعال‌سازی\n" + line);

        assertNotNull(request);
        assertEquals("ABCD1234", request.dev);
        assertEquals("", request.name);
        assertEquals("", request.family);
        assertEquals("", request.shop);
        assertEquals("09123456789", request.phone);
    }

    @Test
    public void legacyNamedRequestsRemainReadable() {
        String line = License.requestLine("ABCD1234", "سارا", "احمدی", "فروشگاه", "09123456789", "تهران");
        License.Req request = License.parseRequest(line);

        assertNotNull(request);
        assertEquals("سارا", request.name);
        assertEquals("احمدی", request.family);
        assertEquals("فروشگاه", request.shop);
    }
}
