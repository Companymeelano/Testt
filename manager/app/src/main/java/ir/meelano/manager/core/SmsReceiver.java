package ir.meelano.manager.core;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

import ir.meelano.manager.LicenseActivity;
import ir.meelano.manager.R;
import ir.meelano.licensing.License;

/**
 * Catches the seller's reply SMS (activation pack / connection line
 * MILANO-DB1 or MILANO-NET1), validates it, and stages it for instant auto-apply — the customer
 * never copies or pastes anything. Also enforces signed remote-control lines
 * (revoke / block / unblock) on the spot. Fires an internal ping so an open
 * LicenseActivity applies it on the spot; otherwise a notification + onResume
 * pickup covers it.
 */
public class SmsReceiver extends BroadcastReceiver {

    /** Internal ping (dynamic receivers only): a fresh MILANO SMS just landed. */
    public static final String ACTION_INTERNAL = "ir.meelano.manager.SMS_MILANO";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            if (intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;
            String body = collect(intent);
            if (body == null || body.isEmpty()) return;
            String pack = extractPack(body);
            String card = extractCard(body);
            String revoke = findLine(body, License.REVOKE_PREFIX);
            String block = findLine(body, License.BLOCK_PREFIX);
            String unblock = findLine(body, License.UNBLOCK_PREFIX);
            if (pack == null && card == null && revoke == null && block == null
                    && unblock == null) return;
            // Validate against THIS phone before staging anything.
            String dev;
            try {
                dev = DeviceId.code(ctx.getApplicationContext());
            } catch (Exception e) {
                dev = "";
            }
            if (pack != null) {
                License.Result r = License.parse(pack);
                if (!r.ok || !r.dev.equals(dev)) pack = null;
            }
            if (card != null && License.parseAnyCard(card, dev) == null) card = null;
            Context app = ctx.getApplicationContext();
            // Remote control applies immediately (signature + device verified).
            if (revoke != null && License.parseRevoke(revoke, dev)) {
                LicenseStore.revoke(app);
                notifyControl(app, "لایسنس شما توسط فروشنده لغو شد؛ برای فعال‌سازی مجدد اقدام کنید");
            }
            String reason = block != null ? License.parseBlock(block, dev) : null;
            if (reason != null) {
                LicenseStore.setBlocked(app, reason);
                notifyControl(app, "اجرای برنامه مسدود شد: " + reason);
            }
            if (unblock != null && License.parseUnblock(unblock, dev)) {
                LicenseStore.clearBlocked(app);
                notifyControl(app, "مسدودی برداشته شد؛ برنامه را فعال کنید");
            }
            if (pack == null && card == null) {
                // Control-only SMS: still ping so an open screen rebuilds now.
                if (revoke != null || reason != null || unblock != null) ping(app, ctx);
                return;
            }
            LicenseStore.addPendingSms(app, pack, card);
            ping(app, ctx);
            // v31: activation applies silently — no notification, no noise.
        } catch (Exception ignored) { }
    }

    /** Reassemble the (possibly multipart) SMS body. */
    private static String collect(Intent intent) {
        try {
            Bundle b = intent.getExtras();
            if (b == null) return "";
            Object[] pdus = (Object[]) b.get("pdus");
            if (pdus == null || pdus.length == 0) return "";
            String format = b.getString("format");
            StringBuilder sb = new StringBuilder();
            for (Object pdu : pdus) {
                if (!(pdu instanceof byte[])) continue;
                SmsMessage m;
                try {
                    m = Build.VERSION.SDK_INT >= 23
                            ? SmsMessage.createFromPdu((byte[]) pdu, format)
                            : SmsMessage.createFromPdu((byte[]) pdu);
                } catch (Exception e) {
                    continue;
                }
                if (m != null && m.getMessageBody() != null) sb.append(m.getMessageBody());
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** First verifiable 39-char M1… pack token in the text (dashes tolerated). */
    private static String extractPack(String body) {
        try {
            for (String tok : body.split("\\s+")) {
                String n = License.normalize(tok);
                if (n.length() == License.PACK_LEN && n.startsWith("M1")) {
                    License.Result r = License.parse(n);
                    if (r.ok) return r.pack;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static void ping(Context app, Context ctx) {
        try {
            Intent ping = new Intent(ACTION_INTERNAL);
            ping.setPackage(ctx.getPackageName());
            ctx.sendBroadcast(ping);
        } catch (Exception ignored) { }
    }

    private static String findLine(String body, String prefix) {
        try {
            for (String raw : body.split("\n")) {
                String line = raw.trim();
                if (line.startsWith(prefix + "|")) return line;
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** The connection line (MILANO-DB1 card or MILANO-NET1 mini line), or null. */
    private static String extractCard(String body) {
        try {
            for (String raw : body.split("\n")) {
                String line = raw.trim();
                if (line.startsWith(License.DB_PREFIX + "|")) return line;
                if (line.startsWith(License.NET_PREFIX + "|")) return line;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static void notifyControl(Context app, String text) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                try {
                    nm.createNotificationChannel(new NotificationChannel("milano_sms",
                            "پیامک میلانو", NotificationManager.IMPORTANCE_HIGH));
                } catch (Exception ignored) { }
            }
            Intent i = new Intent(app, LicenseActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(app, 7702, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            android.app.Notification.Builder nb = android.os.Build.VERSION.SDK_INT >= 26
                    ? new android.app.Notification.Builder(app, "milano_sms")
                    : new android.app.Notification.Builder(app);
            nb.setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("⛔ پیام مدیریتی میلانو")
                    .setContentText(text)
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            nm.notify(7702, nb.build());
        } catch (Exception ignored) { }
    }


}
