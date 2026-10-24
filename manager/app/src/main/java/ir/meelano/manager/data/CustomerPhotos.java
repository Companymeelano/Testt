package ir.meelano.manager.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.ui.Theme;

/**
 * Customer pictures from dbo.cus_image (linked to CUSTOMERS by SHMO).
 *
 * <p>Pictures are fetched in one batched, read-only query, cached as files, and shown as a
 * circular avatar. A customer without a picture gets an empty marker file, so it is not asked
 * for again until the marker is a week old. Until a picture is available the avatar shows the
 * customer's initials on the theme accent, so the layout never waits on the network.
 */
public final class CustomerPhotos {
    private CustomerPhotos() { }

    /** Maximum customers requested in one query (keeps the IN list and the payload bounded). */
    private static final int MAX_FETCH = 60;
    private static final long FRESH_MS = 7L * 24 * 3600 * 1000;
    private static final int MAX_SIDE_PX = 256;
    private static final Map<String, Bitmap> MEM = new HashMap<>();
    private static final int MEM_LIMIT = 120;

    public interface Done { void run(); }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "cust_photos");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static File file(Context c, String code) {
        return new File(dir(c), code + ".img");
    }

    private static boolean validCode(String code) {
        return code != null && code.matches("\\d{1,12}");
    }

    /** Sampled picture for a customer, or null when none is cached. Never throws. */
    public static synchronized Bitmap get(Context c, String code) {
        if (!validCode(code)) return null;
        Bitmap hit = MEM.get(code);
        if (hit != null) return hit;
        try {
            File f = file(c, code);
            if (!f.exists() || f.length() < 16) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while ((o.outWidth / s > MAX_SIDE_PX || o.outHeight / s > MAX_SIDE_PX) && s < 16) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (b != null) {
                if (MEM.size() >= MEM_LIMIT) MEM.clear();
                MEM.put(code, b);
            }
            return b;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Writes a picture (or an empty marker when the customer has none). Runs on a worker thread. */
    private static synchronized void write(Context c, String code, byte[] img) {
        File f = file(c, code);
        try (FileOutputStream out = new FileOutputStream(f)) {
            if (img != null && img.length >= 16) out.write(img);
        } catch (Exception ignored) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        MEM.remove(code);
    }

    /**
     * Loads the missing or stale pictures for the codes in one query. onDone runs on the UI thread
     * after the files are written, whether or not anything new arrived.
     */
    public static void refresh(Repo repo, Context c, List<String> codes, final Done onDone) {
        final Context app = c.getApplicationContext();
        final List<Long> need = new ArrayList<>();
        final long now = System.currentTimeMillis();
        if (codes != null) {
            for (String code : codes) {
                if (!validCode(code) || need.size() >= MAX_FETCH) continue;
                File f = file(app, code);
                if (f.exists() && now - f.lastModified() < FRESH_MS) continue;
                if (need.contains(Long.valueOf(code))) continue;
                need.add(Long.valueOf(code));
            }
        }
        if (need.isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }
        repo.run(conn -> {
            List<Row> rows = Repo.exec(conn, MasterQueries.customerPhotos(new Meta(conn), need));
            Set<String> got = new HashSet<>();
            for (Row r : rows) {
                String code = r.s("code");
                if (!validCode(code)) continue;
                got.add(code);
                write(app, code, r.bytes("img"));
            }
            for (Long id : need) {
                String k = String.valueOf(id);
                if (!got.contains(k)) write(app, k, null);
            }
            return Boolean.TRUE;
        }, new Repo.Cb<Boolean>() {
            @Override
            public void ok(Boolean v) {
                if (onDone != null) onDone.run();
            }

            @Override
            public void fail(String faError) {
                if (onDone != null) onDone.run();
            }
        });
    }

    /**
     * Circular avatar: the picture when cached, otherwise the initials on the accent colour.
     * A missing picture is registered in pending so fill() can paint it later.
     */
    public static View avatar(Context c, String code, String initials, int accent, int sizeDp,
                              Map<String, ImageView> pending) {
        FrameLayout box = new FrameLayout(c);
        int px = Theme.dp(sizeDp);
        TextView letters = new TextView(c);
        letters.setText(initials == null ? "" : initials);
        letters.setTextColor(Theme.onAccent(accent));
        letters.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeDp >= 64 ? 22f : 15f);
        letters.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        letters.setGravity(android.view.Gravity.CENTER);
        letters.setBackground(Theme.accentAvatar(accent));
        box.addView(letters, new FrameLayout.LayoutParams(px, px));
        Bitmap b = get(c, code);
        if (b != null) {
            box.addView(round(c, b, px), new FrameLayout.LayoutParams(px, px));
        } else if (pending != null && validCode(code)) {
            ImageView slot = new ImageView(c);
            slot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(slot, new FrameLayout.LayoutParams(px, px));
            pending.put(code, slot);
            roundOutline(slot);
        }
        return box;
    }

    /** Paints the pictures that arrived for pending slots, then forgets those slots. */
    public static void fill(Map<String, ImageView> pending, Context c) {
        if (pending == null || pending.isEmpty()) return;
        for (Map.Entry<String, ImageView> e : new ArrayList<>(pending.entrySet())) {
            Bitmap b = get(c, e.getKey());
            if (b != null) {
                e.getValue().setImageBitmap(b);
                pending.remove(e.getKey());
            }
        }
    }

    private static ImageView round(Context c, Bitmap b, int px) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(b);
        roundOutline(iv);
        return iv;
    }

    private static void roundOutline(ImageView iv) {
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
    }
}
