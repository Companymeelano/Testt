package ir.meelano.manager.core;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.screens.ManagerAlertsScreen;
import ir.meelano.manager.R;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.ui.Theme;

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
    /** Warehouse «بار جدید» channel: its own id so the custom chime is applied (sound is fixed per channel). */
    public static final String CH_WH = "meelano_wh_signature";
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
    static final String ACT_NEWINV = "ir.meelano.manager.NEWINV";
    private static final int NEWINV_REQ = 7011;
    static final int NEWINV_NOTIF_ID = 7012;
    private static boolean launched = false;

    private static final class DailyCheck {
        final long[] values;
        final List<AlertEngine.Alert> alerts;
        final boolean morningSummaryAvailable;
        DailyCheck(long[] values, List<AlertEngine.Alert> alerts, boolean morningSummaryAvailable) {
            this.values = values;
            this.alerts = alerts == null ? java.util.Collections.<AlertEngine.Alert>emptyList() : alerts;
            this.morningSummaryAvailable = morningSummaryAvailable;
        }
    }

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
            if (s.whNewOn()) scheduleNewInv(a);
            if (Build.VERSION.SDK_INT >= 33
                    && a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 77);
            }
            if (!launched) {
                launched = true;
                checkNow(a.getApplicationContext());
            }
        } catch (Exception ignored) { Diag.ignored("Notify:73", ignored); }
    }

    public static void channel(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CH, "هشدار سررسید", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("سررسید چک‌ها و فاکتورهای معوق");
            nm.createNotificationChannel(ch);
        } catch (Exception ignored) { Diag.ignored("Notify:83", ignored); }
    }

    /** Every day at 8:00 (inexact, battery-friendly). */
    /** Creates the warehouse channel with the custom alarm chime, vibration and gold light. */
    public static void whChannel(Context c) {
        try {
            if (android.os.Build.VERSION.SDK_INT < 26) return;
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CH_WH, "بار جدید انبار",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("اعلان بار جدید برای تحویل، با نام مشتری و ثبت‌کننده");
            android.net.Uri sound = android.net.Uri.parse("android.resource://" + c.getPackageName() + "/raw/meelano_signature");
            android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ch.setSound(sound, attrs);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 90, 70, 90, 70, 260});
            ch.enableLights(true);
            ch.setLightColor(0xFFE8C77A);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        } catch (Exception e) {
            Diag.ignored("Notify:whChannel", e);
        }
    }

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
        } catch (Exception ignored) { Diag.ignored("Notify:101", ignored); }
    }

    public static void cancelDaily(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { Diag.ignored("Notify:111", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("Notify:131", ignored); }
    }

    public static void cancelWeekly(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_BACKUP);
            PendingIntent pi = PendingIntent.getBroadcast(c, BACKUP_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { Diag.ignored("Notify:142", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("Notify:162", ignored); }
    }

    public static void cancelWeeklyReport(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_WEEKLY);
            PendingIntent pi = PendingIntent.getBroadcast(c, WEEKLY_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { Diag.ignored("Notify:173", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("Notify:192", ignored); }
    }

    public static void cancelEod(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_EOD);
            PendingIntent pi = PendingIntent.getBroadcast(c, EOD_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { Diag.ignored("Notify:203", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("Notify:218", ignored); }
    }

    public static void cancelGuard(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_GUARD);
            PendingIntent pi = PendingIntent.getBroadcast(c, GUARD_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { Diag.ignored("Notify:229", ignored); }
    }

    /**
     * New-sales-invoice watch. Fifteen minutes is the finest inexact repeating
     * interval Android offers, which is as close to "live" as a battery-friendly
     * alarm gets; the list itself refreshes on screen, so this only has to cover
     * the case where the app is not open at all.
     */
    public static void scheduleNewInv(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_NEWINV);
            PendingIntent pi = PendingIntent.getBroadcast(c, NEWINV_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                    AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi);
        } catch (Exception ignored) { Diag.ignored("Notify:249", ignored); }
    }

    public static void cancelNewInv(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, NotifyReceiver.class);
            i.setAction(ACT_NEWINV);
            PendingIntent pi = PendingIntent.getBroadcast(c, NEWINV_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) { Diag.ignored("Notify:260", ignored); }
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
                } catch (Exception ignored) { Diag.ignored("Notify:282", ignored); }
                try {
                    inAmt = Repo.one(conn, MoneyQueries.darOn(m, 0, today)).d("total");
                } catch (Exception ignored) { Diag.ignored("Notify:285", ignored); }
                try {
                    outAmt = Repo.one(conn, MoneyQueries.darOn(m, 1, today)).d("total");
                } catch (Exception ignored) { Diag.ignored("Notify:288", ignored); }
                return new long[]{(long) sales, docs, (long) inAmt, (long) outAmt};
            }, new Repo.Cb<long[]>() {
                @Override
                public void ok(long[] v) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { Diag.ignored("Notify:295", ignored); }
                    finishCb(onDone);
                    if (v == null || v.length < 4) return;
                    String msg = "فروش " + Money.compactRial(v[0])
                            + (v[1] > 0 ? " (" + Money.fa(String.valueOf(v[1])) + " فاکتور)" : "")
                            + " • دریافت " + Money.compactRial(v[2])
                            + " • پرداخت " + Money.compactRial(v[3]);
                    try {
                        new Settings(c).saveEod(msg, today);
                    } catch (Exception ignored) { Diag.ignored("Notify:304", ignored); }
                    showEod(c, msg);
                }

                @Override
                public void fail(String faError) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { Diag.ignored("Notify:312", ignored); }
                    finishCb(onDone);
                }
            });
        } catch (Exception ignored) {
            finishCb(onDone);
        }
    }

    /** Sends the assistant's daily summary as a notification (manual «ارسال خلاصه»). */
    public static void sendSummary(Context c, String msg) {
        showEod(c, msg);
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
                    .setColor(Theme.VIOLET)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(EOD_NOTIF_ID, n);
        } catch (Exception ignored) { Diag.ignored("Notify:340", ignored); }
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
                } catch (Exception ignored) { Diag.ignored("Notify:363", ignored); }
                try {
                    List<Row> ds = Repo.exec(conn, MoneyQueries.darDaily(m, 0, from, to));
                    for (Row r : ds) inAmt += r.d("total");
                } catch (Exception ignored) { Diag.ignored("Notify:367", ignored); }
                try {
                    bounced = Repo.one(conn, MoneyQueries.bouncedTotal(m)).l("count");
                } catch (Exception ignored) { Diag.ignored("Notify:370", ignored); }
                try {
                    due7 += Repo.exec(conn, MoneyQueries.chequeDue(m, true, 7)).size();
                } catch (Exception ignored) { Diag.ignored("Notify:373", ignored); }
                try {
                    due7 += Repo.exec(conn, MoneyQueries.chequeDue(m, false, 7)).size();
                } catch (Exception ignored) { Diag.ignored("Notify:376", ignored); }
                return new long[]{(long) sales, docs, (long) inAmt, bounced, due7};
            }, new Repo.Cb<long[]>() {
                @Override
                public void ok(long[] v) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { Diag.ignored("Notify:383", ignored); }
                    finishCb(onDone);
                    if (v == null || v.length < 5) return;
                    showWeek(c, v);
                }

                @Override
                public void fail(String faError) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { Diag.ignored("Notify:393", ignored); }
                    finishCb(onDone);
                }
            });
        } catch (Exception ignored) { Diag.ignored("Notify:397", ignored); }
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
                    .setColor(Theme.GOLD)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(WEEKLY_NOTIF_ID, n);
        } catch (Exception ignored) { Diag.ignored("Notify:423", ignored); }
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
            final boolean dueAlertsOn = s.homeDueAlertsOn();
            final Repo repo = new Repo(c, s);
            repo.run(conn -> {
                Meta m = new Meta(conn);
                long inN = 0, outN = 0, odN = 0;
                double inSum = 0, outSum = 0, odSum = 0;
                List<Row> incoming = null;
                List<Row> outgoing = null;
                if (dueAlertsOn) {
                    try {
                        incoming = Repo.exec(conn, MoneyQueries.chequeDue(m, true, 3));
                        inN = incoming.size();
                        for (Row r : incoming) inSum += r.d("amount");
                    } catch (Exception ignored) { Diag.ignored("Notify:445", ignored); }
                    try {
                        outgoing = Repo.exec(conn, MoneyQueries.chequeDue(m, false, 3));
                        outN = outgoing.size();
                        for (Row r : outgoing) outSum += r.d("amount");
                    } catch (Exception ignored) { Diag.ignored("Notify:450", ignored); }
                    try {
                        List<Row> od = Repo.exec(conn, Queries.overdueInvoices(m, 200));
                        odN = od.size();
                        for (Row r : od) odSum += r.d("amount");
                    } catch (Exception ignored) { Diag.ignored("Notify:455", ignored); }
                }
                String yesterday = Jalali.addDays(Jalali.todayStr(), -1);
                double ySales = 0, yIn = 0;
                boolean ySalesAvailable = false, yInAvailable = false;
                try {
                    Row ys = Repo.one(conn, Queries.salesOn(m, true, yesterday));
                    ySales = ys.d("total");
                    ySalesAvailable = true;
                } catch (Exception ignored) { Diag.ignored("Notify:460", ignored); }
                try {
                    Row yi = Repo.one(conn, MoneyQueries.darOn(m, 0, yesterday));
                    yIn = yi.d("total");
                    yInAvailable = true;
                } catch (Exception ignored) { Diag.ignored("Notify:464", ignored); }
                long bN = 0;
                double bSum = 0;
                try {
                    Row bb = Repo.one(conn, MoneyQueries.bouncedTotal(m));
                    bN = bb.l("count");
                    bSum = bb.d("total");
                } catch (Exception ignored) { Diag.ignored("Notify:471", ignored); }
                long lowN = 0, zeroN = 0;
                try {
                    Row ps = Repo.one(conn, MasterQueries.productsSummary(m));
                    lowN = ps.l("low");
                    zeroN = ps.l("out");
                } catch (Exception ignored) { Diag.ignored("Notify:477", ignored); }
                List<AlertEngine.Alert> alerts = evaluateDailyHomeAlerts(c, conn, m, yesterday, incoming, outgoing);
                return new DailyCheck(new long[]{(long) inSum, (long) outSum, (long) odSum, inN, outN, odN,
                        (long) ySales, (long) yIn, bN, (long) bSum, lowN, zeroN}, alerts,
                        ySalesAvailable && yInAvailable);
            }, new Repo.Cb<DailyCheck>() {
                @Override
                public void ok(DailyCheck result) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { Diag.ignored("Notify:485", ignored); }
                    finishCb(onDone);
                    if (result == null || result.values == null || result.values.length < 12) return;
                    long[] v = result.values;
                    Settings s2 = new Settings(c);
                    boolean dueAlertsOn = s2.homeDueAlertsOn();
                    List<AlertEngine.Alert> homeAlerts = result.alerts;
                    if (!dueAlertsOn) {
                        v = v.clone();
                        v[0] = v[1] = v[2] = v[3] = v[4] = v[5] = 0;
                        List<AlertEngine.Alert> withoutDue = new java.util.ArrayList<>();
                        if (homeAlerts != null) for (AlertEngine.Alert alert : homeAlerts) {
                            if (!"cheque_in_due".equals(alert.code)
                                    && !"cheque_out_due".equals(alert.code)) withoutDue.add(alert);
                        }
                        homeAlerts = withoutDue;
                    }
                    long lastB = s2.lastBouncedN();
                    long bNew = lastB < 0 ? 0 : Math.max(0, v[8] - lastB);
                    s2.setLastBouncedN(v[8]);
                    boolean morning = s2.morningOn() && result.morningSummaryAvailable
                            && RoleStore.allowed(c, "sales");
                    boolean showStock = s2.stockOn() && RoleStore.allowed(c, "products")
                            && v[10] + v[11] > 0;
                    boolean canCheques = RoleStore.allowed(c, "cheques");
                    boolean showDue = dueAlertsOn && canCheques && (v[3] > 0 || v[4] > 0);
                    boolean showOverdue = dueAlertsOn && RoleStore.allowed(c, "sales") && v[5] > 0;
                    boolean showBounced = canCheques && bNew > 0;
                    boolean hasHomeAlert = homeAlerts != null && !homeAlerts.isEmpty();
                    if (!showDue && !showOverdue && !showBounced && !morning
                            && !showStock && !hasHomeAlert) {
                        cancelSmartNotification(c);
                        return;
                    }
                    Calendar now = Calendar.getInstance();
                    int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
                    if (HomeNotificationPolicy.isQuietTime(s2.homeQuietHoursOn(),
                            s2.homeQuietStartMinute(), s2.homeQuietEndMinute(), minute)) return;
                    show(c, v, bNew, morning, homeAlerts);
                }

                @Override
                public void fail(String faError) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { Diag.ignored("Notify:502", ignored); }
                    finishCb(onDone);
                }
            });
        } catch (Exception ignored) { Diag.ignored("Notify:506", ignored); }
    }

    private static List<AlertEngine.Alert> evaluateDailyHomeAlerts(Context context,
                                                                    java.sql.Connection connection,
                                                                    Meta meta, String yesterday,
                                                                    List<Row> incoming,
                                                                    List<Row> outgoing) {
        SalesComparison.DayResult sales = null;
        String historyStart = Jalali.addDays(yesterday, -7 * SalesComparison.DEFAULT_COMPARABLE_WEEKS);
        try {
            List<Row> history = Repo.exec(connection, Queries.factorDaily(meta,
                    true, historyStart, yesterday));
            SalesSnapshot snapshot = SalesSnapshot.fromRows(history, yesterday, historyStart);
            sales = SalesComparison.evaluate(snapshot, yesterday, SalesComparison.MINUTES_PER_DAY,
                    SalesComparison.DEFAULT_COMPARABLE_WEEKS);
        } catch (Exception ignored) { Diag.ignored("Notify:homeSales", ignored); }
        List<AlertEngine.DueItem> due = new java.util.ArrayList<>();
        String today = Jalali.todayStr();
        addAlertDue(due, incoming, true, today);
        addAlertDue(due, outgoing, false, today);
        List<AlertEngine.Alert> evaluated = AlertEngine.evaluate(null, null, sales,
                ManagerAlertsScreen.salesDropThreshold(context), due);
        List<AlertEngine.Alert> visible = new java.util.ArrayList<>();
        for (AlertEngine.Alert alert : evaluated) {
            String screen = HomeNotificationPolicy.screenFor(alert.action);
            if (!screen.isEmpty() && RoleStore.allowed(context, screen)) visible.add(alert);
        }
        return visible;
    }

    private static void addAlertDue(List<AlertEngine.DueItem> target, List<Row> rows,
                                    boolean incoming, String today) {
        if (target == null || rows == null) return;
        for (Row row : rows) {
            if (row == null) continue;
            String date = Jalali.disp(row.s("sardate"));
            if (date.isEmpty()) continue;
            target.add(new AlertEngine.DueItem(incoming, Jalali.diffDays(today, date), row.d("amount")));
        }
    }

    private static void finishCb(Runnable r) {
        try {
            if (r != null) r.run();
        } catch (Exception ignored) { Diag.ignored("Notify:512", ignored); }
    }

    private static void cancelSmartNotification(Context context) {
        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(NOTIF_ID);
        } catch (Exception ignored) { Diag.ignored("Notify:smartCancel", ignored); }
    }

    private static void show(Context c, long[] v, long bNew, boolean morning,
                             List<AlertEngine.Alert> homeAlerts) {
        try {
            channel(c);
            long inN = v[3], outN = v[4], odN = v[5];
            boolean canSales = RoleStore.allowed(c, "sales");
            boolean canCheques = RoleStore.allowed(c, "cheques");
            boolean canProducts = RoleStore.allowed(c, "products");
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (morning && canSales)
                parts.add("فروش دیروز: " + Money.compactRial(v[6]) + " • دریافت دیروز: " + Money.compactRial(v[7]));
            boolean engineIncoming = hasAlertCode(homeAlerts, "cheque_in_due");
            boolean engineOutgoing = hasAlertCode(homeAlerts, "cheque_out_due");
            if (homeAlerts != null) for (AlertEngine.Alert alert : homeAlerts) {
                String line = notificationLine(alert);
                if (!line.isEmpty()) parts.add(line);
            }
            if (canCheques && inN > 0 && !engineIncoming)
                parts.add("چک دریافتی: " + Money.fa(String.valueOf(inN)) + " فقره • " + Money.compactRial(v[0]));
            if (canCheques && outN > 0 && !engineOutgoing)
                parts.add("چک پرداختی: " + Money.fa(String.valueOf(outN)) + " فقره • " + Money.compactRial(v[1]));
            if (canSales && odN > 0)
                parts.add("فاکتور معوق: " + Money.fa(String.valueOf(odN)) + " فاکتور • " + Money.compactRial(v[2]));
            if (canCheques && bNew > 0)
                parts.add("چک برگشتی تازه: " + Money.fa(String.valueOf(bNew)) + " فقره");
            boolean stockOn = true;
            try { stockOn = new Settings(c).stockOn(); } catch (Exception ignored) { Diag.ignored("Notify:531", ignored); }
            boolean stockAlert = stockOn && canProducts && v.length >= 12 && v[10] + v[11] > 0;
            if (stockAlert)
                parts.add("کالای کم‌موجود یا ناموجود: " + Money.fa(String.valueOf(v[10] + v[11])) + " قلم");
            boolean onlyMorning = morning && parts.size() == 1;
            String destination = firstAlertScreen(homeAlerts);
            if (destination.isEmpty()) {
                if (canCheques && (inN > 0 || outN > 0 || bNew > 0)) destination = "cheques";
                else if (canSales && (morning || odN > 0)) destination = "sales";
                else if (stockAlert) destination = "products";
                else destination = "home";
            }
            if (!RoleStore.allowed(c, destination)) destination = "home";
            Notification.InboxStyle inbox = new Notification.InboxStyle();
            for (String p : parts) inbox.addLine(p);
            String title = onlyMorning ? "☀ گزارش صبحگاهی میلانو" : "⚠ هشدار هوشمند میلانو";
            inbox.setBigContentTitle(title);
            inbox.setSummaryText(Money.fa(String.valueOf(parts.size())) + " مورد نیازمند توجه • برای جزئیات لمس کنید");
            Intent open = new Intent(c, MainActivity.class);
            open.putExtra(MainActivity.EXTRA_SCREEN, destination);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CH)
                    .setContentTitle(title)
                    .setContentText(parts.isEmpty() ? "" : parts.get(0))
                    .setStyle(inbox)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(Theme.GOLD)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, n);
        } catch (Exception ignored) { Diag.ignored("Notify:556", ignored); }
    }

    private static boolean hasAlertCode(List<AlertEngine.Alert> alerts, String code) {
        if (alerts == null) return false;
        for (AlertEngine.Alert alert : alerts) if (alert != null && code.equals(alert.code)) return true;
        return false;
    }

    private static String firstAlertScreen(List<AlertEngine.Alert> alerts) {
        if (alerts == null) return "";
        for (AlertEngine.Alert alert : alerts) {
            if (alert == null) continue;
            String screen = HomeNotificationPolicy.screenFor(alert.action);
            if (!screen.isEmpty()) return screen;
        }
        return "";
    }

    private static String notificationLine(AlertEngine.Alert alert) {
        if (alert == null) return "";
        if ("sales_drop".equals(alert.code))
            return "افت فروش دیروز " + HomeNumberFormatter.percentMagnitude(alert.changePercent)
                    + " کمتر از روزهای مشابه • فروش " + Money.compactRial((long) alert.value)
                    + " در برابر " + Money.compactRial((long) alert.referenceValue);
        if ("cheque_in_due".equals(alert.code) || "cheque_out_due".equals(alert.code)) {
            String kind = "cheque_in_due".equals(alert.code) ? "دریافتی" : "پرداختی";
            String nearest = alert.daysUntilDue <= 0 ? "امروز"
                    : alert.daysUntilDue == 1 ? "فردا"
                    : "تا " + HomeNumberFormatter.integer(alert.daysUntilDue) + " روز";
            return "چک " + kind + " نزدیک سررسید: " + HomeNumberFormatter.integer(alert.count)
                    + " فقره • " + Money.compactRial((long) alert.value) + " • نزدیک‌ترین " + nearest;
        }
        return "";
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
                    .setColor(Theme.SUCCESS)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(BACKUP_NOTIF_ID, n);
        } catch (Exception ignored) { Diag.ignored("Notify:579", ignored); }
    }
}
