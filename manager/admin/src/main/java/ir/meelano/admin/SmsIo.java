package ir.meelano.admin;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Direct SMS channel for the seller app — zero copy/paste: the activation
 * pack and the connection card fly straight to the customer's phone, and the
 * customer's request SMS is caught automatically by {@link SmsReceiver}.
 */
public final class SmsIo {
    private SmsIo() { }

    public static final int REQ_SEND = 905;

    public interface Cb {
        void ok();

        void fail(String fa);
    }

    public static boolean canSend(Context c) {
        try {
            return c.checkSelfPermission(android.Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    public static void askSend(Activity a) {
        try {
            a.requestPermissions(new String[]{android.Manifest.permission.SEND_SMS}, REQ_SEND);
        } catch (Exception ignored) { }
    }

    /** Keep digits and a leading + (SmsManager accepts 09… / +98… / 98… as-is). */
    public static String cleanPhone(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        String t = raw.trim();
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch >= '۰' && ch <= '۹') ch = (char) ('0' + (ch - '۰'));
            else if (ch >= '٠' && ch <= '٩') ch = (char) ('0' + (ch - '٠'));
            if ((ch >= '0' && ch <= '9') || (ch == '+' && i == 0)) b.append(ch);
        }
        return b.toString();
    }

    /**
     * Send (possibly long) text as one SMS — multipart when needed — with a
     * real sent-confirmation from the radio. cb always fires exactly once.
     */
    @SuppressWarnings("deprecation")
    public static void send(Context ctx, String phone, String text, final Cb cb) {
        final Context app = ctx.getApplicationContext();
        final Cb safe = cb == null ? new Cb() {
            @Override public void ok() { }
            @Override public void fail(String fa) { }
        } : cb;
        try {
            if (phone == null || phone.length() < 10) {
                safe.fail("شماره مقصد معتبر نیست");
                return;
            }
            if (text == null || text.trim().isEmpty()) {
                safe.fail("متن پیامک خالی است");
                return;
            }
            final SmsManager sms = SmsManager.getDefault();
            final ArrayList<String> parts = sms.divideMessage(text);
            if (parts == null || parts.isEmpty()) {
                safe.fail("متن پیامک خالی است");
                return;
            }
            final String action = "ir.meelano.admin.SMS_SENT." + System.currentTimeMillis();
            final AtomicInteger left = new AtomicInteger(parts.size());
            final AtomicBoolean done = new AtomicBoolean(false);
            final boolean[] failed = {false};
            final BroadcastReceiver[] box = new BroadcastReceiver[1];
            box[0] = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    if (getResultCode() != Activity.RESULT_OK) failed[0] = true;
                    if (left.decrementAndGet() <= 0 && done.compareAndSet(false, true)) {
                        try {
                            app.unregisterReceiver(box[0]);
                        } catch (Exception ignored) { }
                        if (failed[0]) safe.fail("ارسال پیامک ناموفق بود؛ آنتن و اعتبار سیم‌کارت را بررسی کنید");
                        else safe.ok();
                    }
                }
            };
            IntentFilter f = new IntentFilter(action);
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(box[0], f, Context.RECEIVER_EXPORTED);
            else app.registerReceiver(box[0], f);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (done.compareAndSet(false, true)) {
                    try {
                        app.unregisterReceiver(box[0]);
                    } catch (Exception ignored) { }
                    safe.fail("پاسخی از شبکه دریافت نشد؛ دوباره تلاش کنید");
                }
            }, 45000);
            ArrayList<PendingIntent> sent = new ArrayList<>();
            int base = (int) (System.currentTimeMillis() % 100000);
            for (int i = 0; i < parts.size(); i++) {
                sent.add(PendingIntent.getBroadcast(app, base + i, new Intent(action),
                        PendingIntent.FLAG_IMMUTABLE));
            }
            if (parts.size() == 1) sms.sendTextMessage(phone, null, parts.get(0), sent.get(0), null);
            else sms.sendMultipartTextMessage(phone, null, parts, sent, null);
        } catch (Exception e) {
            safe.fail("ارسال پیامک ممکن نشد");
        }
    }
}
