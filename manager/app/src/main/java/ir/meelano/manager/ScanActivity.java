package ir.meelano.manager;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Gravity;
import android.view.TextureView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Barcode / QR scanner (Camera2 + ZXing core, no extra dependencies). */
public class ScanActivity extends Activity {
    public static final String EXTRA_CODE = "code";

    private Kit kit;
    private TextureView preview;
    private TextView status;
    private HandlerThread thread;
    private Handler bg;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private final MultiFormatReader decoder = new MultiFormatReader();
    private final AtomicBoolean decoding = new AtomicBoolean(false);
    private long lastTry;
    private boolean done;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.apply(this);
        kit = new Kit(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        TextView t = kit.text("اسکن بارکد کالا", 17, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t, kit.lp(-1, -2));
        status = kit.text("بارکد را جلوی دوربین بگیرید…", 12f, Theme.MUTED, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, kit.lp(-1, -2));
        root.addView(kit.gap(10));
        preview = new TextureView(this);
        preview.setBackground(Theme.card());
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(kit.gap(10));
        root.addView(kit.btnGhost("انصراف", Theme.MUTED, v -> finish()), kit.lp(-1, -2));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        done = false;
        thread = new HandlerThread("scan");
        thread.start();
        bg = new Handler(thread.getLooper());
        if (preview.isAvailable()) openCamera();
        else preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { openCamera(); }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture s) { }
        });
    }

    @Override
    protected void onPause() {
        closeAll();
        try {
            if (thread != null) thread.quitSafely();
        } catch (Exception ignored) { }
        thread = null;
        super.onPause();
    }

    private void openCamera() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cm == null) { fail("دوربین یافت نشد"); return; }
            String back = null;
            for (String id : cm.getCameraIdList()) {
                Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { back = id; break; }
            }
            if (back == null) { fail("دوربین پشتی یافت نشد"); return; }
            try {
                cm.openCamera(back, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice d) {
                        camera = d;
                        startPreview();
                    }

                    @Override
                    public void onDisconnected(CameraDevice d) { closeAll(); }

                    @Override
                    public void onError(CameraDevice d, int err) {
                        closeAll();
                        fail("خطای دوربین");
                    }
                }, bg);
            } catch (SecurityException se) {
                fail("دسترسی دوربین داده نشد");
            }
        } catch (Exception e) {
            fail("باز کردن دوربین ممکن نشد");
        }
    }

    private void startPreview() {
        try {
            if (camera == null || !preview.isAvailable()) return;
            SurfaceTexture st = preview.getSurfaceTexture();
            st.setDefaultBufferSize(1280, 720);
            android.view.Surface ps = new android.view.Surface(st);
            reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(r -> {
                Image img = null;
                try {
                    img = r.acquireLatestImage();
                } catch (Exception ignored) { }
                if (img == null) return;
                try {
                    long now = System.currentTimeMillis();
                    if (done || decoding.get() || now - lastTry < 350) return;
                    lastTry = now;
                    decoding.set(true);
                    decode(img);
                } finally {
                    try {
                        img.close();
                    } catch (Exception ignored) { }
                    decoding.set(false);
                }
            }, bg);
            final CameraDevice cd = camera;
            cd.createCaptureSession(Arrays.asList(ps, reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            try {
                                CaptureRequest.Builder b = cd.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                b.addTarget(ps);
                                b.addTarget(reader.getSurface());
                                b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                s.setRepeatingRequest(b.build(), null, bg);
                            } catch (CameraAccessException e) {
                                fail("شروع پیش‌نمایش ممکن نشد");
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession s) {
                            fail("پیکربندی دوربین ممکن نشد");
                        }
                    }, bg);
        } catch (Exception e) {
            fail("شروع پیش‌نمایش ممکن نشد");
        }
    }

    /** Decode one YUV frame (runs on the camera thread). */
    private void decode(Image img) {
        try {
            Image.Plane y = img.getPlanes()[0];
            ByteBuffer bb = y.getBuffer();
            int w = img.getWidth(), h = img.getHeight(), stride = y.getRowStride();
            byte[] data;
            if (stride == w) {
                data = new byte[bb.remaining()];
                bb.get(data);
            } else {
                data = new byte[w * h];
                byte[] row = new byte[stride];
                for (int r = 0; r < h; r++) {
                    bb.get(row, 0, stride);
                    System.arraycopy(row, 0, data, r * w, w);
                }
            }
            PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(
                    data, w, h, 0, 0, w, h, false);
            Result res = decoder.decodeWithState(new BinaryBitmap(new HybridBinarizer(src)));
            try {
                decoder.reset();
            } catch (Exception ignore) { }
            if (res != null && res.getText() != null && !res.getText().trim().isEmpty()) {
                final String code = res.getText().trim();
                runOnUiThread(() -> finishWith(code));
            }
        } catch (com.google.zxing.NotFoundException nfe) {
            try {
                decoder.reset();
            } catch (Exception ignore) { }
        } catch (Exception ignored) {
            try {
                decoder.reset();
            } catch (Exception ignore) { }
        }
    }

    private void finishWith(String code) {
        if (done) return;
        done = true;
        try {
            android.content.Intent i = new android.content.Intent();
            i.putExtra(EXTRA_CODE, code);
            setResult(RESULT_OK, i);
        } catch (Exception ignored) { }
        finish();
    }

    private void fail(String msg) {
        runOnUiThread(() -> {
            try {
                if (status != null) status.setText(msg);
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
            } catch (Exception ignored) { }
        });
    }

    private void closeAll() {
        try {
            if (session != null) session.close();
        } catch (Exception ignored) { }
        session = null;
        try {
            if (camera != null) camera.close();
        } catch (Exception ignored) { }
        camera = null;
        try {
            if (reader != null) reader.close();
        } catch (Exception ignored) { }
        reader = null;
    }
}
