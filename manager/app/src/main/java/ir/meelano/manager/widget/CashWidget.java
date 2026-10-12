package ir.meelano.manager.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.data.Settings;

/** Home-screen widget: today's sales / receipts / payments from the offline cache. */
public class CashWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateOne(ctx, mgr, id);
    }

    public static void updateOne(Context ctx, AppWidgetManager mgr, int appWidgetId) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_cash);
        try {
            String s = new Settings(ctx).homeCache();
            v.setTextViewText(R.id.w_sales, Money.compactRial(num(sec(s, "sales"), "total")));
            v.setTextViewText(R.id.w_in, Money.compactRial(num(sec(s, "in"), "total")));
            v.setTextViewText(R.id.w_out, Money.compactRial(num(sec(s, "out"), "total")));
            String ts = unesc(sec(s, "ts"));
            v.setTextViewText(R.id.w_ts, ts.isEmpty() ? "—" : Money.fa(ts));
            v.setTextViewText(R.id.w_date, Jalali.shortLabel(Jalali.todayStr()));
        } catch (Exception ignored) { }
        try {
            Intent i = new Intent(ctx, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.w_root, pi);
        } catch (Exception ignored) { }
        try {
            mgr.updateAppWidget(appWidgetId, v);
        } catch (Exception ignored) { }
    }

    /** Raw section body of the |name=...| cache block. */
    private static String sec(String cache, String name) {
        if (cache == null) return "";
        for (String part : cache.split("\\|")) {
            if (part.startsWith(name + "=")) return part.substring(name.length() + 1);
        }
        return "";
    }

    private static double num(String section, String key) {
        if (section == null) return 0;
        for (String p : section.split(";")) {
            int eq = p.indexOf('=');
            if (eq > 0 && key.equals(p.substring(0, eq))) {
                try {
                    return Double.parseDouble(p.substring(eq + 1));
                } catch (Exception ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static String unesc(String s) {
        return s.replace("%7C", "|").replace("%3D", "=").replace("%3B", ";").replace("%25", "%");
    }
}
