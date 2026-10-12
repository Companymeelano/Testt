package ir.meelano.manager.core;

import android.app.Activity;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

import java.util.concurrent.Executor;

/** Fingerprint login (framework BiometricPrompt, API 29+; no AndroidX). */
public final class Finger {
    private Finger() { }

    /** True when the device can authenticate with biometrics right now. */
    public static boolean supported(Context c) {
        if (c == null || Build.VERSION.SDK_INT < 29) return false;
        try {
            BiometricManager bm = c.getSystemService(BiometricManager.class);
            if (bm == null) return false;
            return bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }

    public interface Cb {
        void ok();

        void fail(String msg);
    }

    /** Show the system fingerprint prompt; callbacks run on the main thread. */
    public static void auth(final Activity a, final Cb cb) {
        try {
            final Executor ex = a.getMainExecutor();
            BiometricPrompt bp = new BiometricPrompt.Builder(a)
                    .setTitle("ورود مدیریتی میلانو")
                    .setSubtitle("اثر انگشت خود را تأیید کنید")
                    .setNegativeButton("انصراف", ex, (d, w) -> cb.fail("لغو شد"))
                    .build();
            bp.authenticate(new CancellationSignal(), ex, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    cb.ok();
                }

                @Override
                public void onAuthenticationFailed() { }

                @Override
                public void onAuthenticationError(int code, CharSequence msg) {
                    cb.fail(msg == null || msg.length() == 0 ? "خطای اثر انگشت" : msg.toString());
                }
            });
        } catch (Exception e) {
            cb.fail("اثر انگشت در دسترس نیست");
        }
    }
}
