package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalAccountsTest {

    @Test
    public void reservedNamesAreMatchedWithoutDisclosingCredentials() {
        assertTrue(LocalAccounts.isLocal("Modir"));
        assertTrue(LocalAccounts.isLocal(" anbar "));
        assertFalse(LocalAccounts.isLocal("other-user"));
    }

    @Test
    public void onlyMatchingLocalIdentityCanKeepManagerOrWarehouseSession() {
        assertTrue(LocalAccounts.isLocalSession("modir", -9001, RoleStore.ADMIN));
        assertTrue(LocalAccounts.isLocalSession("ANBAR", -9002, RoleStore.WAREHOUSE));
        assertFalse(LocalAccounts.isLocalSession("modir", 123, RoleStore.ADMIN));
        assertFalse(LocalAccounts.isLocalSession("anbar", -9002, RoleStore.ADMIN));
        assertFalse(LocalAccounts.isLocalSession("other-user", -9001, RoleStore.ADMIN));
    }
}
