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
 * Catches the seller's reply SMS (activation pack / MILANO-DB1 connection
 * card), validates it, and stages it for instant auto-apply — the customer
 * never copies or pastes anything. Fires an internal ping so an open
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
            if (pack == null && card == null) return;
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
            if (card != null && License.parseDbCard(card, dev) == null) card = null;
            if (pack == null && card == null) return;
            LicenseStore.addPendingSms(ctx.getApplicationContext(), pack, card);
            try {
                Intent ping = new Intent(ACTION_INTERNAL);
                ping.setPackage(ctx.getPackageName());
                ctx.sendBroadcast(ping);
            } catch (Exception ignored) { }
            notifySms(ctx.getApplicationContext(), pack != null, card != null);
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

    /** The MILANO-DB1 card line, or null. */
    private static String extractCard(String body) {
        try {
            for (String raw : body.split("\n")) {
                String line = raw.trim();
                if (line.startsWith(License.DB_PREFIX + "|")) return line;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static void notifySms(Context app, boolean hasPack, boolean hasCard) {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("milano_sms", "پیامک میلانو",
                        NotificationManager.IMPORTANCE_HIGH);
                try {
                    nm.createNotificationChannel(ch);
                } catch (Exception ignored) { }
            }
            String txt = hasPack && hasCard ? "کد فعال‌سازی و کارت اتصال رسید — برای اعمال خودکار لمس کنید"
                    : hasPack ? "کد فعال‌سازی رسید — برای اعمال خودکار لمس کنید"
                    : "کارت اتصال رسید — برای اعمال خودکار لمس کنید";
            Intent i = new Intent(app, LicenseActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(app, 7701, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            android.app.Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                    ? new android.app.Notification.Builder(app, "milano_sms")
                    : new android.app.Notification.Builder(app);
            nb.setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("✦ پیامک میلانو")
                    .setContentText(txt)
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            try {
                nb.setChannelId("milano_sms");
            } catch (Exception ignored) { }
            nm.notify(7701, nb.build());
        } catch (Exception ignored) { }
    }
}
