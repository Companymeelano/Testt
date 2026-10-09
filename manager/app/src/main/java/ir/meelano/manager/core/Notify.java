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

    static final String CH = "meelano_due";
    private static final int ALARM_REQ = 7001;
    private static final int NOTIF_ID = 7002;
    static final String ACT_BACKUP = "ir.meelano.manager.BACKUP";
    private static final int BACKUP_REQ = 7003;
    private static final int BACKUP_NOTIF_ID = 7004;
    static final String ACT_WEEKLY = "ir.meelano.manager.WEEKLY";
    private static final int WEEKLY_REQ = 7005;
    private static final int WEEKLY_NOTIF_ID = 7006;
    static final String ACT_EOD = "ir.meelano.manager.EOD";
    private static final int EOD_REQ = 7007;
    private static final int EOD_NOTIF_ID = 7008;
    static final String ACT_GUARD = "ir.meelano.manager.GUARD";
    private static final int GUARD_REQ = 7009;
    static final int GUARD_NOTIF_ID = 7010;
    private static boolean launched = false;

    /** Create channel, schedule the daily alarm, ask permission, run one launch check. */
    public static void boot(Activity a) {
        try {
            Settings s = new Settings(a);
            if (!s.notifOn()) return;
            channel(a);
            scheduleDaily(a);
            if (s.backupOn()) scheduleWeekly(a);
            if (s.weeklyOn()) scheduleWeeklyReport(a);
            if (s.eodOn()) scheduleEod(a);
            if (s.guardOn()) scheduleGuard(a);
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

    /** Every Friday at 9:00 (inexact, battery-friendly). */
    public static void scheduleWeekly(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_BACKUP);
            PendingIntent pi = PendingIntent.getBroadcast(c, BACKUP_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 7);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY * 7, pi);
        } catch (Exception ignored) { }
    }

    public static void cancelWeekly(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_BACKUP);
            PendingIntent pi = PendingIntent.getBroadcast(c, BACKUP_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { }
    }

    /** Every Thursday at 20:00 (inexact, battery-friendly): the week in numbers. */
    public static void scheduleWeeklyReport(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_WEEKLY);
            PendingIntent pi = PendingIntent.getBroadcast(c, WEEKLY_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY);
            cal.set(Calendar.HOUR_OF_DAY, 20);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 7);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY * 7, pi);
        } catch (Exception ignored) { }
    }

    public static void cancelWeeklyReport(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_WEEKLY);
            PendingIntent pi = PendingIntent.getBroadcast(c, WEEKLY_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { }
    }

    /** Every night at 21:00 (inexact, battery-friendly): the day in numbers. */
    public static void scheduleEod(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_EOD);
            PendingIntent pi = PendingIntent.getBroadcast(c, EOD_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 21);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) { }
    }

    public static void cancelEod(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_EOD);
            PendingIntent pi = PendingIntent.getBroadcast(c, EOD_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { }
    }

    /** Every 30 minutes (inexact, battery-friendly): the live sales guard. */
    public static void scheduleGuard(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_GUARD);
            PendingIntent pi = PendingIntent.getBroadcast(c, GUARD_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR,
                    AlarmManager.INTERVAL_HALF_HOUR, pi);
        } catch (Exception ignored) { }
    }

    public static void cancelGuard(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_GUARD);
            PendingIntent pi = PendingIntent.getBroadcast(c, GUARD_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { }
    }

    /** Tonight's numbers, saved for the home card + a nightly summary notification. */
    public static void eodNow(final Context appCtx, final Runnable onDone) {
        final Context c = appCtx.getApplicationContext();
        try {
            Settings s = new Settings(c);
            if (!s.eodOn()) {
                finishCb(onDone);
                return;
            }
            final Repo repo = new Repo(c, s);
            final String today = Jalali.todayStr();
            repo.run(conn -> {
                Meta m = new Meta(conn);
                double sales = 0, inAmt = 0, outAmt = 0;
                long docs = 0;
                try {
                    Row r = Repo.one(conn, Queries.salesOn(m, true, today));
                    sales = r.d("total");
                    docs = r.l("docs");
                } catch (Exception ignored) { }
                try {
                    inAmt = Repo.one(conn, MoneyQueries.darOn(m, 0, today)).d("total");
                } catch (Exception ignored) { }
                try {
                    outAmt = Repo.one(conn, MoneyQueries.darOn(m, 1, today)).d("total");
                } catch (Exception ignored) { }
                return new long[]{(long) sales, docs, (long) inAmt, (long) outAmt};
            }, new Repo.Cb<long[]>() {
                @Override
                public void ok(long[] v) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { }
                    finishCb(onDone);
                    if (v == null || v.length < 4) return;
                    String msg = "فروش " + Money.compactRial(v[0])
                            + (v[1] > 0 ? " (" + Money.fa(String.valueOf(v[1])) + " فاکتور)" : "")
                            + " • دریافت " + Money.compactRial(v[2])
                            + " • پرداخت " + Money.compactRial(v[3]);
                    try {
                        new Settings(c).saveEod(msg, today);
                    } catch (Exception ignored) { }
                    showEod(c, msg);
                }

                @Override
                public void fail(String faError) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { }
                    finishCb(onDone);
                }
            });
        } catch (Exception ignored) {
            finishCb(onDone);
        }
    }

    private static void showEod(Context c, String msg) {
        try {
            channel(c);
            String full = msg + "؛ برای جزئیات لمس کنید.";
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, EOD_NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CH)
                    .setContentTitle("🌙 جمع‌بندی روز میلانو")
                    .setContentText(full)
                    .setStyle(new Notification.BigTextStyle().bigText(full))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(0xFF9B7BFF)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(EOD_NOTIF_ID, n);
        } catch (Exception ignored) { }
    }

    /** Last-7-days numbers, then a Thursday-night summary notification. */
    public static void weekNow(final Context appCtx, final Runnable onDone) {
        final Context c = appCtx.getApplicationContext();
        try {
            Settings s = new Settings(c);
            if (!s.weeklyOn()) return;
            final Repo repo = new Repo(c, s);
            repo.run(conn -> {
                Meta m = new Meta(conn);
                String to = Jalali.todayStr();
                String from = Jalali.addDays(to, -6);
                Filter f = new Filter();
                f.from = from;
                f.to = to;
                double sales = 0, inAmt = 0;
                long docs = 0, bounced = 0, due7 = 0;
                try {
                    Row r = Repo.one(conn, Queries.factorSummary(m, true, f));
                    sales = r.d("total");
                    docs = r.l("docs");
                } catch (Exception ignored) { }
                try {
                    List<Row> ds = Repo.exec(conn, MoneyQueries.darDaily(m, 0, from, to));
                    for (Row r : ds) inAmt += r.d("total");
                } catch (Exception ignored) { }
                try {
                    bounced = Repo.one(conn, MoneyQueries.bouncedTotal(m)).l("count");
                } catch (Exception ignored) { }
                try {
                    due7 += Repo.exec(conn, MoneyQueries.chequeDue(m, true, 7)).size();
                } catch (Exception ignored) { }
                try {
                    due7 += Repo.exec(conn, MoneyQueries.chequeDue(m, false, 7)).size();
                } catch (Exception ignored) { }
                return new long[]{(long) sales, docs, (long) inAmt, bounced, due7};
            }, new Repo.Cb<long[]>() {
                @Override
                public void ok(long[] v) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { }
                    finishCb(onDone);
                    if (v == null || v.length < 5) return;
                    showWeek(c, v);
                }

                @Override
                public void fail(String faError) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { }
                    finishCb(onDone);
                }
            });
        } catch (Exception ignored) { }
    }

    private static void showWeek(Context c, long[] v) {
        try {
            channel(c);
            String msg = "فروش هفته " + Money.compactRial(v[0]) + " (" + Money.fa(String.valueOf(v[1])) + " فاکتور)"
                    + " • دریافت هفته " + Money.compactRial(v[2])
                    + " • چک برگشتی " + Money.fa(String.valueOf(v[3]))
                    + " • سررسید ۷ روز آینده " + Money.fa(String.valueOf(v[4])) + " فقره"
                    + "؛ برای جزئیات لمس کنید.";
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, WEEKLY_NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CH)
                    .setContentTitle("📊 گزارش هفتگی میلانو")
                    .setContentText(msg)
                    .setStyle(new Notification.BigTextStyle().bigText(msg))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(0xFFD9AE5A)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(WEEKLY_NOTIF_ID, n);
        } catch (Exception ignored) { }
    }

    /** Query Atiran in background; notify only when there is something to warn about. */
    public static void checkNow(final Context appCtx) {
        checkNow(appCtx, null);
    }

    public static void checkNow(final Context appCtx, final Runnable onDone) {
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
                double ySales = 0, yIn = 0;
                try {
                    Row ys = Repo.one(conn, Queries.salesOn(m, true, Jalali.addDays(Jalali.todayStr(), -1)));
                    ySales = ys.d("total");
                } catch (Exception ignored) { }
                try {
                    Row yi = Repo.one(conn, MoneyQueries.darOn(m, 0, Jalali.addDays(Jalali.todayStr(), -1)));
                    yIn = yi.d("total");
                } catch (Exception ignored) { }
                long bN = 0;
                double bSum = 0;
                try {
                    Row bb = Repo.one(conn, MoneyQueries.bouncedTotal(m));
                    bN = bb.l("count");
                    bSum = bb.d("total");
                } catch (Exception ignored) { }
                long lowN = 0, zeroN = 0;
                try {
                    Row ps = Repo.one(conn, MasterQueries.productsSummary(m));
                    lowN = ps.l("low");
                    zeroN = ps.l("out");
                } catch (Exception ignored) { }
                return new long[]{(long) inSum, (long) outSum, (long) odSum, inN, outN, odN,
                        (long) ySales, (long) yIn, bN, (long) bSum, lowN, zeroN};
            }, new Repo.Cb<long[]>() {
                @Override
                public void ok(long[] v) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { }
                    finishCb(onDone);
                    if (v == null || v.length < 12) return;
                    Settings s2 = new Settings(c);
                    long lastB = s2.lastBouncedN();
                    long bNew = lastB < 0 ? 0 : Math.max(0, v[8] - lastB);
                    s2.setLastBouncedN(v[8]);
                    boolean morning = s2.morningOn() && (v[6] > 0 || v[7] > 0);
                    boolean showStock = s2.stockOn() && v[10] + v[11] > 0;
                    if (v[3] == 0 && v[4] == 0 && v[5] == 0 && bNew == 0 && !morning && !showStock) return;
                    show(c, v, bNew, morning);
                }

                @Override
                public void fail(String faError) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { }
                    finishCb(onDone);
                }
            });
        } catch (Exception ignored) { }
    }

    private static void finishCb(Runnable r) {
        try {
            if (r != null) r.run();
        } catch (Exception ignored) { }
    }

    private static void show(Context c, long[] v, long bNew, boolean morning) {
        try {
            channel(c);
            long inN = v[3], outN = v[4], odN = v[5];
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (morning)
                parts.add("☀ فروش دیروز " + Money.compactRial(v[6]) + " • دریافت دیروز " + Money.compactRial(v[7]));
            if (inN > 0)
                parts.add(Money.fa(String.valueOf(inN)) + " فقره چک دریافتی (" + Money.compactRial(v[0]) + ")");
            if (outN > 0)
                parts.add(Money.fa(String.valueOf(outN)) + " فقره چک پرداختی (" + Money.compactRial(v[1]) + ")");
            if (odN > 0)
                parts.add(Money.fa(String.valueOf(odN)) + " فاکتور معوق (" + Money.compactRial(v[2]) + ")");
            if (bNew > 0)
                parts.add(Money.fa(String.valueOf(bNew)) + " چک برگشتی تازه");
            boolean stockOn = true;
            try { stockOn = new Settings(c).stockOn(); } catch (Exception ignored) { }
            if (stockOn && v.length >= 12 && v[10] + v[11] > 0)
                parts.add(Money.fa(String.valueOf(v[10] + v[11])) + " کالا کم‌موجود/ناموجود");
            StringBuilder b = new StringBuilder();
            for (String p : parts) {
                if (b.length() > 0) b.append(" • ");
                b.append(p);
            }
            b.append("؛ برای جزئیات لمس کنید.");
            boolean onlyMorning = morning && parts.size() == 1;
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CH)
                    .setContentTitle(onlyMorning ? "☀ گزارش صبحگاهی میلانو" : "⚠ هشدار سررسید میلانو")
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

    /** Weekly backup reminder (no database access needed). */
    static void showBackup(Context c) {
        try {
            channel(c);
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, BACKUP_NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String msg = "امروز جمعه است؛ از دیتابیس آتیران نسخه پشتیبان بگیرید تا داده فروشگاه امن بماند.";
            Notification n = new Notification.Builder(c, CH)
                    .setContentTitle("⛁ یادآوری پشتیبان‌گیری میلانو")
                    .setContentText(msg)
                    .setStyle(new Notification.BigTextStyle().bigText(msg))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(0xFF3ED6C5)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(BACKUP_NOTIF_ID, n);
        } catch (Exception ignored) { }
    }
}

/** AlarmManager + boot receiver: reschedule + run the due check. Top-level so the manifest can see it. */
final class NotifyReceiver extends BroadcastReceiver {
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
            if (!s.notifOn()) {
                finishPr(pr);
                return;
            }
            Notify.scheduleDaily(c);
            if (s.backupOn()) Notify.scheduleWeekly(c);
            if (s.weeklyOn()) Notify.scheduleWeeklyReport(c);
            if (s.eodOn()) Notify.scheduleEod(c);
            if (s.guardOn()) Notify.scheduleGuard(c);
            AutoBackup.reschedule(c);
            Notify.checkNow(c, () -> finishPr(pr));
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
