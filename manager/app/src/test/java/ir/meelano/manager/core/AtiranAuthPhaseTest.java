package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AtiranAuthPhaseTest {
    @Test public void managerWarehouseAndVisitorRolesAreLiveInTheSameApp() {
        assertTrue(AtiranAuth.phaseOpen(null, RoleStore.ADMIN));
        assertTrue(AtiranAuth.phaseOpen(null, RoleStore.WAREHOUSE));
        assertTrue(AtiranAuth.phaseOpen(null, RoleStore.VISITOR));
    }

    @Test public void unfinishedRolesRemainExplicitlyGated() {
        assertFalse(AtiranAuth.phaseOpen(null, RoleStore.SELLER));
        assertFalse(AtiranAuth.phaseOpen(null, RoleStore.ACCOUNTANT));
        assertFalse(AtiranAuth.phaseOpen(null, RoleStore.DISTRIBUTOR));
        assertFalse(AtiranAuth.phaseOpen(null, RoleStore.MOADIAN));
        assertFalse(AtiranAuth.phaseOpen(null, "unknown"));
    }
}
