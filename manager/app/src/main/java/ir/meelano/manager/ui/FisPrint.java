package ir.meelano.manager.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.data.Row;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Thermal receipt printing over Bluetooth (ESC/POS raster, 80mm).
 * The receipt is rendered by Android itself, so Persian text prints perfectly.
 */
public final class FisPrint {
    private FisPrint() { }

    public static final int BT_REQ = 904;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int W = 576;

    private static final class Pending {
        Row head;
        List<Row> lines;
        boolean sales;
    }

    private static Pending pending;

    /** Print an invoice: pick a paired printer, connect, send. */
    public static void print(final MainActivity a, final Row head, final List<Row> lines, final boolean sales) {
        try {
            BluetoothManager bm = (BluetoothManager) a.getSystemService(android.content.Context.BLUETOOTH_SERVICE);
            final BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
            if (ad == null) {
                a.kit.toast("این گوشی بلوتوث ندارد");
                return;
            }
            if (Build.VERSION.SDK_INT >= 31 && a.checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Pending p = new Pending();
                p.head = head;
                p.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
                p.sales = sales;
                pending = p;
                a.requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, BT_REQ);
                return;
            }
            if (!ad.isEnabled()) {
                a.kit.toast("بلوتوث را روشن کنید");
                return;
            }
            Set<BluetoothDevice> bonded;
            try {
                bonded = ad.getBondedDevices();
            } catch (SecurityException se) {
                a.kit.toast("دسترسی بلوتوث داده نشد");
                return;
            }
            final List<BluetoothDevice> devs = new ArrayList<>();
            if (bonded != null) for (BluetoothDevice d : bonded) if (d != null) devs.add(d);
            if (devs.isEmpty()) {
                a.kit.toast("پرینتری جفت نشده است؛ اول در تنظیمات بلوتوث جفت کنید");
                return;
            }
            LinearLayout body = a.kit.v();
            body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
            body.addView(a.kit.text("چاپگر را انتخاب کنید", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(a.kit.gap(8));
            final android.app.AlertDialog[] box = new android.app.AlertDialog[1];
            for (final BluetoothDevice d : devs) {
                String nm;
                try {
                    nm = d.getName();
                } catch (Exception e) {
                    nm = null;
                }
                if (nm == null || nm.isEmpty()) nm = "دستگاه بلوتوثی";
                final String label = nm;
                body.addView(a.kit.btnGhost("🖨 " + label, Theme.GOLD, v -> {
                    box[0].dismiss();
                    send(a, d, head, lines, sales);
                }), a.kit.lp(-1, -2));
            }
            box[0] = a.kit.dialog("چاپ فیش", a.kit.scrollWrap(body, 480), true);
            box[0].show();
        } catch (Exception e) {
            a.kit.toast("چاپ ممکن نشد");
        }
    }

    /** Continue a permission-gated print. Called from MainActivity. */
    public static void onPermissionResult(MainActivity a, boolean granted) {
        Pending p = pending;
        pending = null;
        if (granted && p != null) print(a, p.head, p.lines, p.sales);
        else if (!granted) a.kit.toast("برای چاپ دسترسی بلوتوث لازم است");
    }

    private static void send(final MainActivity a, final BluetoothDevice dev,
                             final Row head, final List<Row> lines, final boolean sales) {
        a.kit.toast("در حال اتصال به چاپگر…");
        new Thread(() -> {
            android.bluetooth.BluetoothSocket sock = null;
            try {
                try {
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                } catch (Exception ignored) { }
                sock = dev.createRfcommSocketToServiceRecord(SPP);
                sock.connect();
                OutputStream out = sock.getOutputStream();
                out.write(buildReceipt(a, head, lines, sales));
                out.flush();
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) { }
                try {
                    sock.close();
                } catch (Exception ignored) { }
                a.runOnUiThread(() -> a.kit.toast("فیش چاپ شد ✓"));
            } catch (Exception e) {
                try {
                    if (sock != null) sock.close();
                } catch (Exception ignored) { }
                a.runOnUiThread(() -> a.kit.toast("اتصال به چاپگر ممکن نشد"));
            }
        }).start();
    }

    // ---------------- receipt ----------------
    private static final class Block {
        final StaticLayout lay;
        final int gapAfter;

        Block(StaticLayout lay, int gapAfter) {
            this.lay = lay;
            this.gapAfter = gapAfter;
        }
    }

    private static StaticLayout lay(String text, int px, boolean bold, Layout.Alignment align) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setTextSize(px);
        p.setTypeface(Theme.face(bold));
        return StaticLayout.Builder.obtain(text, 0, text.length(), p, W - 32)
                .setAlignment(align)
                .setLineSpacing(4f, 1f)
                .setIncludePad(true)
                .build();
    }

    private static String dash() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 32; i++) b.append('-');
        return b.toString();
    }

    private static byte[] buildReceipt(MainActivity a, Row head, List<Row> lines, boolean sales) throws Exception {
        Company.Info co = Company.get(a);
        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block(lay(co.displayName(a), 44, true, Layout.Alignment.ALIGN_CENTER), 4));
        String ph = co.phonesLine(a);
        if (!ph.isEmpty()) blocks.add(new Block(lay(Money.fa(ph), 26, false, Layout.Alignment.ALIGN_CENTER), 2));
        String ad = co.displayAddr(a);
        if (!ad.isEmpty()) blocks.add(new Block(lay(ad, 24, false, Layout.Alignment.ALIGN_CENTER), 2));
        blocks.add(new Block(lay(dash(), 24, false, Layout.Alignment.ALIGN_CENTER), 6));
        String kind = sales ? "فاکتور فروش" : "فاکتور خرید";
        blocks.add(new Block(lay(kind + "  " + Money.fa(head.s("no")), 32, true, Layout.Alignment.ALIGN_CENTER), 4));
        blocks.add(new Block(lay("تاریخ: " + Jalali.dispFa(head.s("date")), 26, false, Layout.Alignment.ALIGN_NORMAL), 2));
        blocks.add(new Block(lay((sales ? "مشتری: " : "طرف‌حساب: ") + head.s("customer"), 28, true, Layout.Alignment.ALIGN_NORMAL), 2));
        String vis = head.s("visitor");
        if (sales && !vis.isEmpty() && !"بدون ویزیتور".equals(vis))
            blocks.add(new Block(lay("ویزیتور: " + vis, 26, false, Layout.Alignment.ALIGN_NORMAL), 2));
        blocks.add(new Block(lay(dash(), 24, false, Layout.Alignment.ALIGN_CENTER), 6));
        if (lines != null) {
            for (Row r : lines) {
                blocks.add(new Block(lay(r.s("naka"), 28, true, Layout.Alignment.ALIGN_NORMAL), 0));
                double q = Math.abs(r.d("qtyVah")) < 0.0005 && Math.abs(r.d("qtyJoz")) > 0.0005
                        ? r.d("qtyJoz") : r.d("qtyVah");
                double pr = Math.abs(r.d("vahPrice")) < 0.005 && Math.abs(r.d("jozPrice")) > 0.005
                        ? r.d("jozPrice") : r.d("vahPrice");
                String l2 = Money.fa(trimNum(q)) + " × " + Money.rial(pr) + " = " + Money.rial(r.d("lineSum"));
                blocks.add(new Block(lay(l2, 26, false, Layout.Alignment.ALIGN_NORMAL), 6));
            }
        }
        blocks.add(new Block(lay(dash(), 24, false, Layout.Alignment.ALIGN_CENTER), 6));
        if (head.has("tafif") && head.d("tafif") > 0)
            blocks.add(new Block(lay("تخفیف: " + Money.rial(head.d("tafif")), 26, false, Layout.Alignment.ALIGN_NORMAL), 2));
        if (head.has("tax") && head.d("tax") > 0)
            blocks.add(new Block(lay("مالیات: " + Money.rial(head.d("tax")), 26, false, Layout.Alignment.ALIGN_NORMAL), 2));
        blocks.add(new Block(lay("جمع: " + Money.rial(head.d("total")), 38, true, Layout.Alignment.ALIGN_CENTER), 4));
        blocks.add(new Block(lay((sales ? "دریافتی: " : "پرداختی: ") + Money.rial(head.d("paid")), 26, false, Layout.Alignment.ALIGN_NORMAL), 2));
        double remain = head.d("total") - head.d("paid");
        if (Math.abs(remain) > 0.5)
            blocks.add(new Block(lay("مانده: " + Money.rial(remain), 28, true, Layout.Alignment.ALIGN_NORMAL), 2));
        blocks.add(new Block(lay("سپاس از خرید شما", 28, true, Layout.Alignment.ALIGN_CENTER), 2));
        blocks.add(new Block(lay(Jalali.todayStr() + " • میلانو", 22, false, Layout.Alignment.ALIGN_CENTER), 0));

        int h = 16;
        for (Block b : blocks) h += b.lay.getHeight() + b.gapAfter;
        Bitmap bmp = Bitmap.createBitmap(W, h + 16, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(Color.WHITE);
        int y = 16;
        for (Block b : blocks) {
            cv.save();
            cv.translate(16, y);
            b.lay.draw(cv);
            cv.restore();
            y += b.lay.getHeight() + b.gapAfter;
        }
        return raster(bmp);
    }

    private static String trimNum(double v) {
        if (Math.abs(v - Math.round(v)) < 0.0005) return String.valueOf(Math.round(v));
        return String.valueOf(Math.round(v * 100) / 100.0);
    }

    /** Bitmap → ESC/POS raster (GS v 0) + init + feed + cut. */
    private static byte[] raster(Bitmap bmp) throws Exception {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int bands = (h + 7) / 8;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x1B);
        out.write(0x40); // init
        out.write(0x1D);
        out.write(0x76);
        out.write(0x30);
        out.write(0x00); // m=0
        out.write(w / 8);
        out.write(0x00);
        out.write(bands & 0xFF);
        out.write((bands >> 8) & 0xFF);
        int[] all = new int[w * h];
        bmp.getPixels(all, 0, w, 0, 0, w, h);
        for (int band = 0; band < bands; band++) {
            for (int x = 0; x < w; x++) {
                int byteVal = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int yy = band * 8 + bit;
                    int lum = 255;
                    if (yy < h) {
                        int c = all[yy * w + x];
                        lum = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3;
                    }
                    if (lum < 128) byteVal |= (1 << (7 - bit));
                }
                out.write(byteVal);
            }
        }
        out.write(0x0A);
        out.write(0x0A);
        out.write(0x0A);
        out.write(0x1D);
        out.write(0x56);
        out.write(0x01); // partial cut
        return out.toByteArray();
    }
}
