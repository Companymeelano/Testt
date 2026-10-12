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
 * Live sales guard: a periodic background check (fresh bounced cheques +
 * big invoices of the day). Each event notifies exactly once.
 */
public final class Guard {
    private Guard() { }

    /** Single-invoice alert threshold in Rial (≈ ۱۰ میلیون تومان). */
    public static final long BIG_INVOICE = 100000000L;

    private static final class Found {
        long bounced;
        List<String> big = new ArrayList<>();
    }

    public static void checkNow(final Context appCtx, final Runnable onDone) {
        final Context c = appCtx.getApplicationContext();
        try {
            Settings s = new Settings(c);
            if (!s.guardOn()) {
                finishCb(onDone);
                return;
            }
            final Repo repo = new Repo(c, s);
            repo.run(conn -> {
                Meta m = new Meta(conn);
                Found f = new Found();
                try {
                    f.bounced = Repo.one(conn, MoneyQueries.bouncedTotal(m)).l("count");
                } catch (Exception ignored) { }
                String today = Jalali.todayStr();
                for (boolean sales : new boolean[]{true, false}) {
                    try {
                        Filter qf = new Filter();
                        qf.preset = Filter.P_CUSTOM;
                        qf.from = today;
                        qf.to = today;
                        qf.top = 500;
                        qf.page = 0;
                        List<Row> rows = Repo.exec(conn, Queries.factorList(m, sales, qf));
                        for (Row r : rows) {
                            if (r.d("total") >= BIG_INVOICE) {
                                f.big.add((sales ? "S" : "B") + "|" + r.s("no") + "|"
                                        + r.s("customer") + "|" + ((long) r.d("total")));
                            }
                        }
                    } catch (Exception ignored) { }
                }
                return f;
            }, new Repo.Cb<Found>() {
                @Override
                public void ok(Found v) {
                    try {
                        repo.close();
                    } catch (Exception ignored) { }
                    finishCb(onDone);
                    if (v == null) return;
                    try {
                        Settings s2 = new Settings(c);
                        long lastB = s2.guardBounced();
                        long fresh = lastB < 0 ? 0 : Math.max(0, v.bounced - lastB);
                        s2.setGuardBounced(v.bounced);
                        Set<String> seen = new LinkedHashSet<>();
                        String old = s2.guardSeen();
                        if (old != null && !old.isEmpty()) {
                            for (String k : old.split(";")) if (!k.isEmpty()) seen.add(k);
                        }
                        List<String> freshBig = new ArrayList<>();
                        for (String b : v.big) {
                            String k = keyOf(b);
                            if (!seen.contains(k)) freshBig.add(b);
                            seen.add(k);
                        }
                        while (seen.size() > 200) seen.remove(seen.iterator().next());
                        StringBuilder sb = new StringBuilder();
                        for (String k : seen) {
                            if (sb.length() > 0) sb.append(';');
                            sb.append(k);
                        }
                        s2.setGuardSeen(sb.toString());
                        if (fresh == 0 && freshBig.isEmpty()) return;
                        notifyGuard(c, fresh, freshBig);
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

    private static String keyOf(String b) {
        int i = b.indexOf('|');
        int j = b.indexOf('|', i + 1);
        return j < 0 ? b : b.substring(0, j);
    }

    private static void finishCb(Runnable r) {
        try {
            if (r != null) r.run();
        } catch (Exception ignored) { }
    }

    private static void notifyGuard(Context c, long freshBounced, List<String> freshBig) {
        try {
            Notify.channel(c);
            List<String> parts = new ArrayList<>();
            if (freshBounced > 0)
                parts.add(Money.fa(String.valueOf(freshBounced)) + " چک برگشتی تازه");
            for (String b : freshBig) {
                String[] p = b.split("\\|", -1);
                String kind = p.length > 0 && "B".equals(p[0]) ? "خرید" : "فروش";
                String no = p.length > 1 ? p[1] : "";
                String cust = p.length > 2 ? p[2] : "";
                long amt = 0;
                try {
                    amt = Long.parseLong(p[3]);
                } catch (Exception ignored) { }
                parts.add("فاکتور " + kind + " " + Money.fa(no)
                        + (cust.isEmpty() ? "" : " («" + cust + "»)")
                        + " — " + Money.compactRial(amt));
            }
            StringBuilder b = new StringBuilder();
            for (String p : parts) {
                if (b.length() > 0) b.append(" • ");
                b.append(p);
            }
            b.append("؛ برای جزئیات لمس کنید.");
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, Notify.GUARD_NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, Notify.CH)
                    .setContentTitle("🛡 نگهبان فروش میلانو")
                    .setContentText(b.toString())
                    .setStyle(new Notification.BigTextStyle().bigText(b.toString()))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(0xFFD9AE5A)
                    .build();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(Notify.GUARD_NOTIF_ID, n);
        } catch (Exception ignored) { }
    }
}
