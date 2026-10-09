package ir.meelano.admin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

import ir.meelano.licensing.License;

/**
 * Catches the customer's SMS: requests (MILANO-REQ1 + MILANO-USE1) land in
 * the inbox + usage ledger, and connection reports (MILANO-SITE1) update the
 * customer's saved site — the seller never pastes text.
 */
public class SmsReceiver extends BroadcastReceiver {

    /** Internal ping (dynamic receivers only): a fresh request SMS just landed. */
    public static final String ACTION_INTERNAL = "ir.meelano.admin.SMS_MILANO";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            if (intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;
            String body = collect(intent);
            if (body == null || body.isEmpty()) return;
            License.Req req = License.parseRequest(body);
            License.Use use = License.parseUse(body);
            License.NetProfile site = License.parseSite(body);
            String siteDev = site == null ? "" : License.parseSiteDev(body);
            if (site != null && siteDev.isEmpty()) site = null;
            if (req == null && use == null && site == null) return;
            Context app = ctx.getApplicationContext();
            // Usage lands in the DB immediately, even if the app is never opened.
            if (use != null) {
                try {
                    AdminDb db = new AdminDb(app);
                    if (db.byDev(use.dev) != null) db.updateUsage(use.dev, use.totalMin, use.opens, use.lastDay);
                    try {
                        db.close();
                    } catch (Exception ignored) { }
                } catch (Exception ignored) { }
            }
            if (req != null && reqLine(body) != null) {
                try {
                    AdminDb db = new AdminDb(app);
                    db.clearInboxForDev(req.dev);
                    db.addInbox(req.dev, (req.name + " " + req.family).trim(), req.phone,
                            req.shop, body);
                    try {
                        db.close();
                    } catch (Exception ignored) { }
                } catch (Exception ignored) { }
            }
            String siteSaved = "";
            if (site != null) {
                try {
                    AdminDb db = new AdminDb(app);
                    AdminDb.Customer c = db.byDev(siteDev);
                    if (c != null) {
                        db.saveSite(c.id, site.host, site.port, site.db,
                                License.SQL_USER, License.SQL_PASS);
                        siteSaved = c.full();
                    }
                    try {
                        db.close();
                    } catch (Exception ignored) { }
                } catch (Exception ignored) { }
            }
            try {
                Intent ping = new Intent(ACTION_INTERNAL);
                ping.setPackage(ctx.getPackageName());
                ctx.sendBroadcast(ping);
            } catch (Exception ignored) { }
            notifySms(app, req, use, siteSaved);
        } catch (Exception ignored) { }
    }

    private static String reqLine(String body) {
        for (String raw : body.split("\n")) {
            String line = raw.trim();
            if (line.startsWith(License.REQ_PREFIX + "|")) return line;
        }
        return null;
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

    private static void notifySms(Context app, License.Req req, License.Use use, String siteSaved) {
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
            String who = req != null ? ((req.name + " " + req.family).trim()) : "";
            if (who.isEmpty() && use != null) who = "دستگاه " + use.dev;
            String txt = req != null
                    ? ("درخواست لایسنس" + (who.isEmpty() ? "" : " «" + who + "»") + " رسید — برای صدور لمس کنید")
                    : (!siteSaved.isEmpty()
                    ? ("مشخصات اتصال «" + siteSaved + "» رسید و ذخیره شد")
                    : ("گزارش مصرف «" + who + "» ثبت شد"));
            Intent i = new Intent(app, AdminActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(app, 7702, i,
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
            nm.notify(7702, nb.build());
        } catch (Exception ignored) { }
    }
}
