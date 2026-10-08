package ir.meelano.manager.core;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.data.Settings;

import java.util.Calendar;
import java.util.List;

/**
 * Smart due-date alerts: a daily 8 AM check (cheques due in 3 days + overdue
 * invoices) plus one check on every app launch. Fully dependency-free
 * (AlarmManager + Notification.Builder, no AndroidX).
 */
public final class Notify {
    private Notify() { }

    private static final String CH = "meelano_due";
    private static final int ALARM_REQ = 7001;
    private static final int NOTIF_ID = 7002;
    private static boolean launched = false;

    /** Create channel, schedule the daily alarm, ask permission, run one launch check. */
    public static void boot(Activity a) {
        try {
            Settings s = new Settings(a);
            if (!s.notifOn()) return;
            channel(a);
            scheduleDaily(a);
            if (Build.VERSION.SDK_INT >= 33
                    && a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 77);
            }
            if (!launched) {
                launched = true;
                checkNow(a.getApplicationContext());
            }
        } catch (Exception ignored) { }
    }

    public static void channel(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CH, "هشدار سررسید", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("سررسید چک‌ها و فاکتورهای معوق");
            nm.createNotificationChannel(ch);
        } catch (Exception ignored) { }
    }

    /** Every day at 8:00 (inexact, battery-friendly). */
    public static void scheduleDaily(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, NotifyReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 8);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) { }
    }

    public static void cancelDaily(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { }
    }

    /** Query Atiran in background; notify only when there is something to warn about. */
    public static void checkNow(final Context appCtx) {
        final Context c = appCtx.getApplicationContext();
        try {
            Settings s = new Settings(c);
            if (!s.notifOn()) return;
            final Repo repo = new Repo(c, s);
            repo.run(conn -> {
                Meta m = new Meta(conn);
                long inN = 0, outN = 0, odN = 0;
                double inSum = 0, outSum = 0, odSum = 0;
                try {
                    List<Row> in = Repo.exec(conn, MoneyQueries.chequeDue(m, true, 3));
                    inN = in.size();
                    for (Row r : in) inSum += r.d("amount");
                } catch (Exception ignored) { }
                try {
                    List<Row> out = Repo.exec(conn, MoneyQueries.chequeDue(m, false, 3));
                    outN = out.size();
                    for (Row r : out) outSum += r.d("amount");
                } catch (Exception ignored) { }
                try {
                    List<Row> od = Repo.exec(conn, Queries.overdueInvoices(m, 200));
                    odN = od.size();
                    for (Row r : od) odSum += r.d("amount");
                } catch (Exception ignored) { }
                return new long[]{(long) inSum, (long) outSum, (long) odSum, inN, outN, odN};
            }, new Repo.Cb<long[]>() {
                @Override
                public void ok(long[] v) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { }
                    if (v == null || (v[3] == 0 && v[4] == 0 && v[5] == 0)) return;
                    show(c, v[3], v[0], v[4], v[1], v[5], v[2]);
                }

                @Override
                public void fail(String faError) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { }
                }
            });
        } catch (Exception ignored) { }
    }

    private static void show(Context c, long inN, long inSum, long outN, long outSum, long odN, long odSum) {
        try {
            channel(c);
            StringBuilder b = new StringBuilder();
            if (inN > 0) b.append(Money.fa(String.valueOf(inN))).append(" فقره چک دریافتی (").append(Money.compactRial(inSum)).append(")");
            if (outN > 0) {
                if (b.length() > 0) b.append(" • ");
                b.append(Money.fa(String.valueOf(outN))).append(" فقره چک پرداختی (").append(Money.compactRial(outSum)).append(")");
            }
            if (odN > 0) {
                if (b.length() > 0) b.append(" • ");
                b.append(Money.fa(String.valueOf(odN))).append(" فاکتور معوق (").append(Money.compactRial(odSum)).append(")");
            }
            b.append("؛ برای جزئیات لمس کنید.");
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CH)
                    .setContentTitle("⚠ هشدار سررسید میلانو")
                    .setContentText(b.toString())
                    .setStyle(new Notification.BigTextStyle().bigText(b.toString()))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(0xFFD9AE5A)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, n);
        } catch (Exception ignored) { }
    }
}

/** AlarmManager + boot receiver: reschedule + run the due check. Top-level so the manifest can see it. */
final class NotifyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        try {
            Settings s = new Settings(c);
            if (!s.notifOn()) return;
            Notify.scheduleDaily(c);
            Notify.checkNow(c);
        } catch (Exception ignored) { }
    }
}
