package ir.meelano.manager.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ir.meelano.manager.data.Settings;

/** AlarmManager and boot receiver for scheduled notifications. */
public final class NotifyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        final PendingResult pr;
        try {
            pr = goAsync();
        } catch (Exception e) {
            return;
        }
        try {
            Settings s = new Settings(c);
            String act = intent == null || intent.getAction() == null ? "" : intent.getAction();
            if (Notify.ACT_BACKUP.equals(act)) {
                if (s.backupOn()) {
                    Notify.scheduleWeekly(c);
                    Notify.showBackup(c);
                }
                finishPr(pr);
                return;
            }
            if (Notify.ACT_WEEKLY.equals(act)) {
                if (s.weeklyOn()) {
                    Notify.scheduleWeeklyReport(c);
                    Notify.weekNow(c, () -> finishPr(pr));
                } else finishPr(pr);
                return;
            }
            if (Notify.ACT_EOD.equals(act)) {
                if (s.eodOn()) {
                    Notify.scheduleEod(c);
                    Notify.eodNow(c, () -> finishPr(pr));
                } else finishPr(pr);
                return;
            }
            if (Notify.ACT_GUARD.equals(act)) {
                if (s.guardOn()) {
                    Notify.scheduleGuard(c);
                    Guard.checkNow(c, () -> finishPr(pr));
                } else finishPr(pr);
                return;
            }
            if (Notify.ACT_NEWINV.equals(act)) {
                if (s.whNewOn()) {
                    Notify.scheduleNewInv(c);
                    InvoiceWatch.checkNow(c, () -> finishPr(pr));
                } else finishPr(pr);
                return;
            }
            if (!s.notifOn()) {
                finishPr(pr);
                return;
            }
            Notify.scheduleDaily(c);
            if (s.backupOn()) Notify.scheduleWeekly(c);
            if (s.weeklyOn()) Notify.scheduleWeeklyReport(c);
            if (s.eodOn()) Notify.scheduleEod(c);
            if (s.guardOn()) Notify.scheduleGuard(c);
            if (s.whNewOn()) Notify.scheduleNewInv(c);
            AutoBackup.reschedule(c);
            Notify.checkNow(c, () -> finishPr(pr));
        } catch (Exception ignored) {
            finishPr(pr);
        }
    }

    private void finishPr(PendingResult pr) {
        try {
            pr.finish();
        } catch (Exception ignored) { Diag.ignored("Notify:652", ignored); }
    }
}
