package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyncStateTest {
    @Test public void failureSeparatesOfflineStaleAndErrorStates() {
        assertEquals(SyncState.OFFLINE, SyncState.afterFailure(false, false));
        assertEquals(SyncState.OFFLINE, SyncState.afterFailure(true, false));
        assertEquals(SyncState.ERROR, SyncState.afterFailure(false, true));
        assertEquals(SyncState.STALE, SyncState.afterFailure(true, true));
    }
}
