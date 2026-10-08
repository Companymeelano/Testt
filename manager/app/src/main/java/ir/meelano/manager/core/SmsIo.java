package ir.meelano.manager.core;

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
 * Direct SMS channel — zero copy/paste: the license request flies straight to
 * the seller's phone, and the seller's reply (activation pack / connection
 * card) is caught automatically by {@link SmsReceiver} and applied on arrival.
 * Works fully offline (no internet); needs SEND_SMS only when the user taps
 * «send by SMS». Sideload builds only — Play Store forbids SMS permissions
 * for non-default-SMS apps, so this path stays as long as we ship direct APKs.
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
        String t = Money.en(raw).replaceAll("[^0-9+]", "");
        if (t.startsWith("+")) return "+" + t.substring(1).replace("+", "");
        return t.replace("+", "");
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
            final String action = "ir.meelano.manager.SMS_SENT." + System.currentTimeMillis();
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
            // Safety net: some ROMs never call back — never leave the user hanging.
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
