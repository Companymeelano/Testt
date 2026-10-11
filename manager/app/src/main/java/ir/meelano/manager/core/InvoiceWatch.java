package ir.meelano.manager.core;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.data.Filter;
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
                    } catch (Exception ignored) { }
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
                    } catch (Exception ignored) { }
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

    private static void notifyNew(Context c, List<Row> fresh) {
        try {
            Notify.channel(c);
            String title = fresh.size() == 1
                    ? "🧾 فاکتور فروش جدید"
                    : "🧾 " + Money.fa(String.valueOf(fresh.size())) + " فاکتور فروش جدید";
            StringBuilder b = new StringBuilder();
            int shown = Math.min(fresh.size(), 4);
            for (int i = 0; i < shown; i++) {
                Row r = fresh.get(i);
                if (b.length() > 0) b.append(" • ");
                b.append("فاکتور ").append(Money.fa(r.s("no")));
                String cust = r.s("customer");
                if (cust != null && !cust.isEmpty() && !"—".equals(cust))
                    b.append(" («").append(cust).append("»)");
                b.append(" — ").append(Money.compactRial(r.d("total")));
            }
            if (fresh.size() > shown)
                b.append(" • و ").append(Money.fa(String.valueOf(fresh.size() - shown))).append(" مورد دیگر");
            b.append("؛ برای تحویل لمس کنید.");
            String body = b.toString();

            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra(MainActivity.EXTRA_SCREEN, "tahvil");
            PendingIntent pi = PendingIntent.getActivity(c, Notify.NEWINV_NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, Notify.CH)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(0xFF3ED6C5)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(Notify.NEWINV_NOTIF_ID, n);
        } catch (Exception ignored) { }
    }

    private static void finishCb(Runnable r) {
        try {
            if (r != null) r.run();
        } catch (Exception ignored) { }
    }
}
