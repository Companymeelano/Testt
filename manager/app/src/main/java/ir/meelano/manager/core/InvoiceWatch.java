package ir.meelano.manager.core;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.data.Settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * New-sales-invoice alert for the warehouse keeper.
 *
 * Runs from the same background alarm as {@link Guard}, reads today's sales
 * invoices straight from Atiran, and notifies once per invoice number it has
 * not announced before — so the keeper hears about a freshly registered invoice
 * even while the app is closed.
 *
 * Two things keep it from being noisy. The set of already-announced numbers is
 * seeded silently on the first run and at the start of every new day, otherwise
 * opening the app at noon would fire one notification per invoice the morning
 * had already produced. And it only speaks on a device signed in as the
 * warehouse role, because it exists to tell the keeper what to hand over.
 *
 * Dependency-free, like the rest of the notification stack: AlarmManager plus
 * Notification.Builder, no AndroidX.
 */
public final class InvoiceWatch {
    private InvoiceWatch() { }

    /** Keep at most this many announced numbers per day. */
    private static final int MAX_SEEN = 300;

    public static void checkNow(final Context appCtx, final Runnable onDone) {
        final Context c = appCtx.getApplicationContext();
        try {
            final Settings s = new Settings(c);
            if (!s.whNewOn() || !isWarehouse(c)) {
                finishCb(onDone);
                return;
            }
            final Repo repo = new Repo(c, s);
            repo.run(conn -> {
                Meta m = new Meta(conn);
                Filter qf = new Filter();
                qf.preset = Filter.P_CUSTOM;
                qf.from = Jalali.todayStr();
                qf.to = Jalali.todayStr();
                qf.top = 500;
                qf.page = 0;
                return new ArrayList<>(Repo.exec(conn, Queries.factorList(m, true, qf)));
            }, new Repo.Cb<List<Row>>() {
                @Override
                public void ok(List<Row> rows) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { Diag.ignored("InvoiceWatch:67", ignored); }
                    finishCb(onDone);
                    if (rows == null) return;
                    try {
                        Settings s2 = new Settings(c);
                        String today = Jalali.todayStr();
                        Set<String> seen = split(s2.whSeen());
                        if (!today.equals(s2.whSeenDay())) {
                            // First run, or the day rolled over: adopt whatever is
                            // already on the books without announcing any of it.
                            Set<String> fresh = new LinkedHashSet<>();
                            for (Row r : rows) fresh.add(key(r));
                            s2.setWhSeen(join(fresh));
                            s2.setWhSeenDay(today);
                            return;
                        }
                        List<Row> fresh = new ArrayList<>();
                        for (Row r : rows) {
                            String k = key(r);
                            if (!k.isEmpty() && !seen.contains(k)) {
                                fresh.add(r);
                                seen.add(k);
                            }
                        }
                        while (seen.size() > MAX_SEEN) seen.remove(seen.iterator().next());
                        s2.setWhSeen(join(seen));
                        if (!fresh.isEmpty()) notifyNew(c, fresh);
                    } catch (Exception ignored) { Diag.ignored("InvoiceWatch:94", ignored); }
                }

                @Override
                public void fail(String faError) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { Diag.ignored("InvoiceWatch:101", ignored); }
                    finishCb(onDone);
                }
            });
        } catch (Exception ignored) {
            finishCb(onDone);
        }
    }

    /** Only the warehouse keeper needs to be told what to hand over. */
    private static boolean isWarehouse(Context c) {
        try {
            String role = AtiranAuth.sessionRole(c);
            if (role == null || role.isEmpty()) role = RoleStore.current(c);
            return RoleStore.WAREHOUSE.equals(role);
        } catch (Exception e) {
            return false;
        }
    }

    private static String key(Row r) {
        String no = r.s("no");
        return no == null ? "" : no.trim();
    }

    private static Set<String> split(String v) {
        Set<String> out = new LinkedHashSet<>();
        if (v == null || v.isEmpty()) return out;
        for (String k : v.split(";")) if (!k.isEmpty()) out.add(k);
        return out;
    }

    private static String join(Set<String> s) {
        StringBuilder sb = new StringBuilder();
        for (String k : s) {
            if (sb.length() > 0) sb.append(';');
            sb.append(k);
        }
        return sb.toString();
    }

    /** Sample loads for the «نمایش نمونه» button in Settings (no database access). */
    public static void preview(Context c) {
        List<Row> rows = new ArrayList<>();
        Row a = new Row();
        a.put("no", "1047");
        a.put("customer", "فروشگاه نمونه آذر");
        a.put("total", 12500000d);
        a.put("registrant", "علی رضایی");
        rows.add(a);
        Row b = new Row();
        b.put("no", "1048");
        b.put("customer", "داروخانه مهر");
        b.put("total", 4800000d);
        b.put("registrant", "مریم احمدی");
        rows.add(b);
        notifyNew(c.getApplicationContext(), rows);
    }

    /**
     * The warehouse alert: who the load is for, its value, who registered it, and a closing
     * line that depends on the time of day. Several loads become an inbox-style list.
     */
    private static void notifyNew(Context c, List<Row> fresh) {
        try {
            Notify.whChannel(c);
            int n = fresh.size();
            String now = Money.fa(new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                    .format(new java.util.Date()));
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            double sum = 0;
            List<String> lines = new ArrayList<>();
            for (Row r : fresh) {
                sum += r.d("total");
                lines.add(loadLine(r));
            }
            String title = n == 1
                    ? "🚚 بار جدید برای تحویل"
                    : "🚚 " + Money.fa(String.valueOf(n)) + " بار جدید برای تحویل";
            String summary = "جمع ارزش " + Money.compactRial(sum) + " • ثبت شد ساعت " + now;
            String closing = hour < 12
                    ? "بار امروز را پیش از ظهر آماده و با فاکتور کنترل کنید."
                    : "پیش از خروج، بار را با فاکتور و امضای تحویل کنترل کنید.";

            Notification.Style style;
            if (n == 1) {
                style = new Notification.BigTextStyle()
                        .setBigContentTitle(title)
                        .setSummaryText(summary)
                        .bigText(lines.get(0) + "\n\n" + closing);
            } else {
                Notification.InboxStyle inbox = new Notification.InboxStyle()
                        .setBigContentTitle(title)
                        .setSummaryText(summary);
                int shown = Math.min(lines.size(), 5);
                for (int i = 0; i < shown; i++) inbox.addLine(lines.get(i));
                if (lines.size() > shown) {
                    inbox.addLine("… و " + Money.fa(String.valueOf(lines.size() - shown)) + " بار دیگر");
                }
                inbox.addLine(closing);
                style = inbox;
            }

            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra(MainActivity.EXTRA_SCREEN, "tahvil");
            PendingIntent pi = PendingIntent.getActivity(c, Notify.NEWINV_NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder b = new Notification.Builder(c, Notify.CH_WH)
                    .setContentTitle(title)
                    .setContentText(lines.get(0))
                    .setSubText("انبار • دانه درخشان")
                    .setTicker(title)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setColor(0xFFE8C77A)
                    .setGroup("meelano_warehouse")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setStyle(style);
            if (android.os.Build.VERSION.SDK_INT >= 24) b.setColorized(true);
            try {
                b.addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(c, R.mipmap.ic_launcher),
                        "مشاهده بار", pi).build());
            } catch (Exception ignored) { Diag.ignored("InvoiceWatch:action", ignored); }
            try {
                b.setLargeIcon(loadBadge(c, n));
            } catch (Exception ignored) { Diag.ignored("InvoiceWatch:large", ignored); }
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(Notify.NEWINV_NOTIF_ID, b.build());
        } catch (Exception ignored) { Diag.ignored("InvoiceWatch:180", ignored); }
    }

    /** Black-and-gold badge with the number of loads, used as the notification's large icon. */
    private static android.graphics.Bitmap loadBadge(Context c, int count) {
        int size = 256;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        float cx = size / 2f, cy = size / 2f, r = size / 2f - 8;
        p.setShader(new android.graphics.RadialGradient(cx - 30, cy - 40, r * 1.6f,
                new int[]{0xFF2A2A2E, 0xFF0B0B0D}, null, android.graphics.Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r, p);
        p.setShader(null);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(10);
        p.setColor(0xFFE8C77A);
        cv.drawCircle(cx, cy, r - 12, p);
        p.setStyle(android.graphics.Paint.Style.FILL);
        p.setColor(0xFFF2F2F4);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        p.setTextSize(count > 9 ? 84 : 104);
        String txt = String.valueOf(Math.min(count, 99));
        android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(txt, cx, cy - (fm.ascent + fm.descent) / 2f, p);
        return bmp;
    }

    /** One load as a readable line: «🧾 ۱۰۴۷ • مشتری «…» • ۱۲٫۵ میلیون ریال • ثبت: …». */
    private static String loadLine(Row r) {
        StringBuilder b = new StringBuilder("🧾 ").append(Money.fa(r.s("no")));
        String cust = r.s("customer");
        if (cust != null && !cust.isEmpty() && !"—".equals(cust)) b.append(" • مشتری «").append(cust).append("»");
        b.append(" • ").append(Money.compactRial(r.d("total")));
        String reg = r.s("registrant");
        if (reg != null && !reg.trim().isEmpty()) b.append(" • ثبت: ").append(reg.trim());
        return b.toString();
    }

    private static void finishCb(Runnable r) {
        try {
            if (r != null) r.run();
        } catch (Exception ignored) { Diag.ignored("InvoiceWatch:186", ignored); }
    }
}
