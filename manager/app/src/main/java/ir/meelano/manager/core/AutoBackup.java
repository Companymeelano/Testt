package ir.meelano.manager.core;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.data.Settings;

import java.io.File;
import java.util.Calendar;

/**
 * Automatic nightly backup (v18): runs the full 78-table smart export every
 * day at the configured hour (default 2 AM), saves to Downloads exactly like
 * a manual backup, and reports the result in one notification. Battery-safe
 * (inexact alarm), works on any connection (Wi-Fi or mobile data).
 */
public final class AutoBackup {
    private AutoBackup() { }

    static final String ACT_AUTO = "ir.meelano.manager.AUTO_BACKUP";
    private static final int REQ = 7007;
    private static final int NOTIF_ID = 7008;
    private static final String CH = "meelano_due";

    /** Arm (or re-arm) the daily alarm at the user's hour. */
    public static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, AutoBackupReceiver.class);
            i.setAction(ACT_AUTO);
            PendingIntent pi = PendingIntent.getBroadcast(c, REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, new Settings(c).abHour());
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) { }
    }

    public static void cancel(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, AutoBackupReceiver.class);
            i.setAction(ACT_AUTO);
            PendingIntent pi = PendingIntent.getBroadcast(c, REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { }
    }

    /** Re-arm after boot (alarms never survive a reboot). */
    public static void reschedule(Context c) {
        try {
            if (new Settings(c).abOn()) schedule(c);
        } catch (Exception ignored) { }
    }

    static void notifyResult(Context c, boolean ok, String detail) {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            Notify.channel(c);
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String msg = ok ? ("بکاپ خودکار ساخته شد (" + detail + ") و در Downloads ذخیره شد.")
                    : ("بکاپ خودکار ناموفق بود (" + detail + ")؛ امشب دوباره تلاش می‌شود.");
            Notification n = new Notification.Builder(c, CH)
                    .setContentTitle(ok ? "⛁ بکاپ خودکار میلانو ✓" : "⛁ بکاپ خودکار میلانو ✕")
                    .setContentText(msg)
                    .setStyle(new Notification.BigTextStyle().bigText(msg))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(ok ? 0xFF3ED6C5 : 0xFFE5484D)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, n);
        } catch (Exception ignored) { }
    }
}

/** AlarmManager receiver: runs the silent nightly export. Top-level so the manifest can see it. */
final class AutoBackupReceiver extends BroadcastReceiver {
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
                    } catch (Exception ignored) { } finally {
                        finishPr(pr);
                    }
                }

                @Override
                public void onFail(String faError) {
                    try {
                        AutoBackup.notifyResult(app, false,
                                faError == null || faError.isEmpty() ? "خطای اتصال" : faError);
                    } catch (Exception ignored) { } finally {
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
        } catch (Exception ignored) { }
    }
}
