package ir.meelano.android;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AtiranProductionAuthTest {
    @Test public void recognizesManagerRoleNamesOnly() {
        assertEquals(AtiranProductionAuth.ADMIN, AtiranProductionAuth.mapRole("مدیرکل"));
        assertEquals(AtiranProductionAuth.ADMIN, AtiranProductionAuth.mapRole("System Administrator"));
        assertEquals("user", AtiranProductionAuth.mapRole("انباردار"));
        assertEquals("user", AtiranProductionAuth.mapRole("فروشنده"));
        assertEquals("user", AtiranProductionAuth.mapRole(""));
    }
}
