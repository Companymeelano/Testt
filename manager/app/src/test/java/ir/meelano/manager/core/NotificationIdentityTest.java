package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

/** Regression coverage for notification IDs that share the app's default channel. */
public final class NotificationIdentityTest {
    @Test public void automaticBackupDoesNotReplaceEndOfDaySummary() {
        assertFalse("Auto-backup and end-of-day notifications must remain independently visible",
                AutoBackup.NOTIF_ID == Notify.EOD_NOTIF_ID);
    }
}
