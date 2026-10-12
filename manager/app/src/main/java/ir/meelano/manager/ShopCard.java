package ir.meelano.manager;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Qr;
import ir.meelano.manager.ui.Theme;

import java.io.File;
import java.io.FileOutputStream;

/** Digital shop card: a luxurious shareable PNG (logo + shop + date + developer credit). */
public final class ShopCard {
    private ShopCard() { }

    public static void show(final MainActivity a) {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(build(a, false), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(10));
        body.addView(a.kit.btn("⇪ اشتراک‌گذاری کارت", v -> sharePng(a)), a.kit.lp(-1, -2));
        AlertDialog d = a.kit.dialog("کارت ویزیت دیجیتال", a.kit.scrollWrap(body, 540), true);
        d.show();
    }

    private static LinearLayout build(MainActivity a, boolean hiRes) {
        Kit k = a.kit;
        LinearLayout c = k.v();
        c.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.shade(Theme.GOLD, -0.32f), Theme.GOLD, Theme.shade(Theme.GOLD, 0.18f)});
        bg.setCornerRadius(hiRes ? 84 : Theme.dp(28));
        bg.setStroke(hiRes ? 4 : Theme.dp(1), Theme.alpha(0xFFFFFFFF, 120));
        c.setBackground(bg);
        int pad = hiRes ? 84 : Theme.dp(26);
        c.setPadding(pad, pad, pad, pad);
        int ink = Theme.onAccent();
        int inkSoft = Theme.alpha(ink, 200);
        String shop = Company.get(a).displayName(a);

        ImageView logo = k.logo(hiRes ? 0 : 76);
        if (hiRes) logo.setLayoutParams(new LinearLayout.LayoutParams(216, 216));
        else logo.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(76), Theme.dp(76)));
        c.addView(logo);
        TextView nm = k.text(shop, hiRes ? 64 : 23, ink, true);
        if (hiRes) nm.setTextSize(TypedValue.COMPLEX_UNIT_PX, 64);
        nm.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = k.lp(-1, -2);
        np.setMargins(0, hiRes ? 30 : Theme.dp(10), 0, 0);
        c.addView(nm, np);
        View line = new View(a);
        line.setBackgroundColor(Theme.alpha(ink, 140));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(hiRes ? 420 : Theme.dp(140), hiRes ? 5 : Theme.dp(2));
        llp.gravity = Gravity.CENTER;
        llp.setMargins(0, hiRes ? 36 : Theme.dp(12), 0, hiRes ? 36 : Theme.dp(12));
        c.addView(line, llp);
        TextView t1 = k.text("میلانو • داشبورد مدیریتی آتیران", hiRes ? 34 : 12.5f, inkSoft, true);
        if (hiRes) t1.setTextSize(TypedValue.COMPLEX_UNIT_PX, 34);
        t1.setGravity(Gravity.CENTER);
        c.addView(t1, k.lp(-1, -2));
        TextView t2 = k.text(k.todayLine(), hiRes ? 30 : 11.5f, inkSoft, false);
        if (hiRes) t2.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30);
        t2.setGravity(Gravity.CENTER);
        c.addView(t2, k.lp(-1, -2));
        String phone = Company.get(a).phonesLine(a);
        String addr = Company.get(a).displayAddr(a);
        if (!phone.isEmpty()) {
            TextView tp = k.text(phone, hiRes ? 32 : 12f, ink, true);
            if (hiRes) tp.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32);
            tp.setGravity(Gravity.CENTER);
            c.addView(tp, k.lp(-1, -2));
        }
        if (!addr.isEmpty()) {
            TextView ta = k.text(addr, hiRes ? 28 : 11f, inkSoft, false);
            if (hiRes) ta.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28);
            ta.setGravity(Gravity.CENTER);
            c.addView(ta, k.lp(-1, -2));
        }
        Bitmap qr = Qr.make(qrText(shop, phone, addr), hiRes ? 300 : 132);
        if (qr != null) {
            ImageView qv = new ImageView(a);
            qv.setImageBitmap(qr);
            qv.setBackgroundColor(0xFFFFFFFF);
            int qp = hiRes ? 18 : Theme.dp(8);
            qv.setPadding(qp, qp, qp, qp);
            LinearLayout.LayoutParams qlp = hiRes ? new LinearLayout.LayoutParams(336, 336)
                    : new LinearLayout.LayoutParams(Theme.dp(148), Theme.dp(148));
            qlp.gravity = Gravity.CENTER;
            qlp.setMargins(0, hiRes ? 30 : Theme.dp(12), 0, 0);
            c.addView(qv, qlp);
        }
        TextView dv = k.text("Milad Yaghoobi • طراح و توسعه‌دهنده", hiRes ? 28 : 11f, inkSoft, false);
        if (hiRes) dv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28);
        dv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dvp = k.lp(-1, -2);
        dvp.setMargins(0, hiRes ? 30 : Theme.dp(10), 0, 0);
        c.addView(dv, dvp);
        return c;
    }

    private static String qrText(String shop, String phone, String addr) {
        StringBuilder b = new StringBuilder(shop);
        if (phone != null && !phone.isEmpty()) b.append('\n').append(phone);
        if (addr != null && !addr.isEmpty()) b.append('\n').append(addr);
        return b.toString();
    }

    private static void sharePng(final MainActivity a) {
        a.kit.toast("در حال ساخت کارت…");
        new Thread(() -> {
            try {
                final Bitmap bmp = render(a);
                File dir = ShareProvider.shareDir(a);
                final File out = new File(dir, "shop-card-" + System.currentTimeMillis() + ".png");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                a.runOnUiThread(() -> {
                    try {
                        ShareProvider.share(a, out, "image/png", "کارت ویزیت دیجیتال");
                    } catch (Exception e) {
                        a.kit.toast("اشتراک ممکن نشد");
                    }
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> a.kit.toast("ساخت کارت ممکن نشد"));
            }
        }).start();
    }

    /** Render the hi-res card off-screen. Must run on the UI thread. */
    private static Bitmap render(final MainActivity a) throws Exception {
        final Bitmap[] out = new Bitmap[1];
        final Exception[] err = new Exception[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        a.runOnUiThread(() -> {
            try {
                LinearLayout card = build(a, true);
                int wSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
                int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                card.measure(wSpec, hSpec);
                int w = card.getMeasuredWidth();
                int h = Math.max(1, card.getMeasuredHeight());
                card.layout(0, 0, w, h);
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                card.draw(new Canvas(bmp));
                out[0] = bmp;
            } catch (Exception e) {
                err[0] = e;
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (err[0] != null) throw err[0];
        return out[0];
    }

}
