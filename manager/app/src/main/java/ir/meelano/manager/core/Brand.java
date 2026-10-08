package ir.meelano.manager.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Official MEELANO contact points, shown in the license screen footer and the
 * Settings «about» card. Tapping the site opens the browser; tapping a number
 * opens the dialer with the number ready (ACTION_DIAL needs no permission).
 */
public final class Brand {
    private Brand() { }

    public static final String SITE_URL = "https://www.Meelano.ir";
    public static final String SITE_LABEL = "Meelano.ir";

    /** Official support lines (shown exactly as given, in this order). */
    public static final String[] PHONES = {
            "09120219593",
            "09991858005",
            "09366663370",
            "0935559305",
    };

    public static final String TAGLINE = "پشتیبانی میلانو • همراه همیشگی کسب‌وکار شما";

    public static void openSite(Context c) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(SITE_URL));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            try {
                Toast.makeText(c, SITE_LABEL, Toast.LENGTH_LONG).show();
            } catch (Exception ignored) { }
        }
    }

    public static void dial(Context c, String phone) {
        String p = phone == null ? "" : phone.replaceAll("[^0-9+]", "");
        if (p.isEmpty()) return;
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            try {
                Toast.makeText(c, phone, Toast.LENGTH_LONG).show();
            } catch (Exception ignored) { }
        }
    }
}
