package ir.meelano.manager.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ir.meelano.manager.data.Settings;

import java.io.File;

/** Alarm receiver for the silent nightly backup. */
public final class AutoBackupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        final PendingResult pr;
        try {
            pr = goAsync();
        } catch (Exception e) {
            return;
        }
        try {
            final Context app = c.getApplicationContext();
            Settings s = new Settings(app);
            if (!s.abOn()) {
                finishPr(pr);
                return;
            }
            AutoBackup.schedule(app); // re-arm first: the export may take a while
            if (!Net.online(app)) {
                AutoBackup.notifyResult(app, false, "اینترنت قطع است");
                finishPr(pr);
                return;
            }
            final Settings fs = s;
            Backup.export(app, fs, (faName, done, total) -> {
            }, new Backup.Done() {
                @Override
                public void onDone(File zip, long bytes, String notes) {
                    try {
                        fs.saveBackupInfo(Jalali.faDate(Jalali.todayStr()), Backup.sizeFa(bytes));
                        AutoBackup.notifyResult(app, true, Backup.sizeFa(bytes));
                        new Thread(() -> {
                            try { OfflineReports.refresh(app); } catch (Exception ignored) { Diag.ignored("AutoBackup:offline", ignored); }
                        }, "offline-copy").start();
                    } catch (Exception ignored) { Diag.ignored("AutoBackup:128", ignored); } finally {
                        finishPr(pr);
                    }
                }

                @Override
                public void onFail(String faError) {
                    try {
                        AutoBackup.notifyResult(app, false,
                                faError == null || faError.isEmpty() ? "خطای اتصال" : faError);
                    } catch (Exception ignored) { Diag.ignored("AutoBackup:138", ignored); } finally {
                        finishPr(pr);
                    }
                }
            });
        } catch (Exception ignored) {
            finishPr(pr);
        }
    }

    private void finishPr(PendingResult pr) {
        try {
            pr.finish();
        } catch (Exception ignored) { Diag.ignored("AutoBackup:151", ignored); }
    }
}
