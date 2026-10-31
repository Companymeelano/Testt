package ir.meelano.manager;

import ir.meelano.manager.core.Diag;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ir.meelano.manager.core.Brand;
import ir.meelano.manager.core.CrashReporter;
import ir.meelano.manager.core.DeviceId;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LanDiscover;
import ir.meelano.manager.core.LicenseRecovery;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.SmartLink;
import ir.meelano.manager.ui.LinkPanel;
import ir.meelano.manager.core.Net;
import ir.meelano.manager.core.SmsIo;
import ir.meelano.manager.core.SmsReceiver;
import ir.meelano.manager.core.Tamper;
import ir.meelano.manager.core.Usage;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.NetRoute;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.ui.Card3D;
import ir.meelano.manager.ui.ConfettiView;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.RadarView;
import ir.meelano.manager.ui.Ui;
import ir.meelano.manager.ui.Theme;
import ir.meelano.licensing.License;

/**
 * Offline license gate: page 1 uses the device code automatically and asks only for a contact
 * number; the seller records the customer's name while issuing the license. A fixed waiting page
 * holds until the license arrives; page 2 asks for the server address and lists compatible databases,
 * and lets the user choose one before entering the app. Everything activates silently:
 * no mechanism talk, no notification. Remote revoke/block enforcement
 * stays, plus the luxury gold designer signature on every page.
 * Blocks MainActivity until {@link LicenseStore#unlocked}.
 */
public class LicenseActivity extends Activity {

    private static final int REQ_RECV = 906;
    private static final int REQ_READ_SMS = 907;
    private static final int REQ_PHONE = 908;
    /** One automatic permission/recovery prompt per screen entry — never nags. */
    private boolean autoRecoveryAsked;
    private boolean phonePermAsked;

    /** Fixed issuer line: requests fly straight here, no chooser, no field. */
    private static final String DEFAULT_SELLER = "09355559305";
    private static final String[] WAIT_LINES = {
            "کلید طلایی‌تان در راه است؛ کمی صبر کنید ✦",
            "داریم کلید را تراش می‌دهیم…",
            "کمی مانده — همین‌جا منتظر بمانید…",
            "به‌زودی وارد دنیای میلانو می‌شوید…",
    };
    private String pendingSmsPhone = "";
    private String pendingSmsText = "";
    private boolean editingConnection;
    private EditText connectionIp;
    private EditText connectionPort;
    private String restoredConnectionIp;
    private String restoredConnectionPort;

    /** Intent extra used by Settings to reopen server/database selection. */
    public static final String EXTRA_EDIT_CONNECTION = "ir.meelano.manager.EDIT_CONNECTION";

    private Kit kit;
    private Settings settings;
    private Repo repo;
    private String device = "";

    private TextView recvHint;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TextView dotsView;
    private android.widget.ImageView waitLogo;
    private int dotsN;
    private boolean logoBig;
    private android.widget.Button glowBtn;
    private boolean sending;
    private TextView waitTimer;
    private RadarView connRadar;
    private TextView connStage;
    private long waitStartMs;
    private int tickCount;
    private TextView waitCreative;
    private final Runnable dotsTick = new Runnable() {
        @Override
        public void run() {
            boolean motion = Ui.motionOk(LicenseActivity.this);
            if (motion) try {
                dotsN = (dotsN + 1) % 4;
                if (dotsView != null) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < dotsN; i++) b.append("●");
                    for (int i = dotsN; i < 3; i++) b.append("○");
                    dotsView.setText(b.toString());
                }
                if (waitLogo != null) {
                    logoBig = !logoBig;
                    float sc = logoBig ? 1.07f : 1.0f;
                    waitLogo.animate().scaleX(sc).scaleY(sc).setDuration(650).start();
                }
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:125", ignored); }
            try {
                tickCount++;
                if (waitTimer != null && waitStartMs > 0) {
                    long sec = (System.currentTimeMillis() - waitStartMs) / 1000;
                    waitTimer.setText("زمان انتظار: " + Money.fa(String.format(
                            java.util.Locale.US, "%d:%02d", sec / 60, sec % 60)));
                }
                if (motion && waitCreative != null && tickCount % 7 == 0 && tickCount > 0) {
                    waitCreative.setText(WAIT_LINES[(tickCount / 7) % WAIT_LINES.length]);
                }
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:136", ignored); }
            uiHandler.postDelayed(dotsTick, 700);
        }
    };

    private long lastCardTry;
    private BroadcastReceiver smsPing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Theme.init(this);
        try {
            getWindow().setStatusBarColor(Theme.BG);
            getWindow().setNavigationBarColor(Theme.BG);
            Theme.lightBars(getWindow());
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:152", ignored); }
        kit = new Kit(this);
        settings = new Settings(this);
        editingConnection = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_EDIT_CONNECTION, false);
        if (savedInstanceState != null && savedInstanceState.containsKey("manager_connection_ip"))
            restoredConnectionIp = savedInstanceState.getString("manager_connection_ip", "");
        if (savedInstanceState != null && savedInstanceState.containsKey("manager_connection_port"))
            restoredConnectionPort = savedInstanceState.getString("manager_connection_port", "");
        repo = new Repo(this, settings);
        try {
            device = DeviceId.code(this);
        } catch (Exception e) {
            device = "";
        }
        buildUi();
        ensureRecvPerm();
        // Reinstall case: silently restore the device-bound license from the
        // SMS inbox (one permission prompt at most — see attemptRecovery).
        attemptRecovery(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            if (connectionIp != null) outState.putString("manager_connection_ip",
                    connectionIp.getText().toString());
            if (connectionPort != null) outState.putString("manager_connection_port",
                    connectionPort.getText().toString());
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:174", ignored); }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (smsPing == null) {
                smsPing = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent i) {
                        consumeSms();
                    }
                };
            }
            IntentFilter ff = new IntentFilter(SmsReceiver.ACTION_INTERNAL);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(smsPing, ff, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(smsPing, ff);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:193", ignored); }
        // A reply SMS may have landed while we were away — apply it instantly.
        consumeSms();
        try {
            if (dotsView != null) {
                uiHandler.removeCallbacks(dotsTick);
                uiHandler.post(dotsTick);
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:201", ignored); }
    }

    @Override
    protected void onPause() {
        try {
            uiHandler.removeCallbacks(dotsTick);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:208", ignored); }
        try {
            unregisterReceiver(smsPing);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:211", ignored); }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopDots();
        try {
            if (repo != null) repo.close();
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:220", ignored); }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        try {
            if (req == SmsIo.REQ_SEND) {
                boolean g = grants != null && grants.length > 0
                        && grants[0] == PackageManager.PERMISSION_GRANTED;
                String d = pendingSmsPhone;
                String t = pendingSmsText;
                pendingSmsPhone = "";
                pendingSmsText = "";
                if (g && t != null && !t.isEmpty()) doSmsSend(d, t);
                else if (!g) {
                    if (SmsIo.permaDenied(LicenseActivity.this)) permSettingsDialog();
                    else shareRequest(LicenseStore.contactPhone(this));
                }
            } else if (req == REQ_RECV) {
                updateRecvHint();
                buildUi();
            } else if (req == REQ_READ_SMS) {
                if (readSmsGranted()) attemptRecovery(true);
                else buildUi();
            } else if (req == REQ_PHONE) {
                buildUi();
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:245", ignored); }
    }

    // ---------- UI ----------

    private void buildUi() {
        try {
            if (connectionIp != null) restoredConnectionIp = connectionIp.getText().toString();
            if (connectionPort != null) restoredConnectionPort = connectionPort.getText().toString();
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:253", ignored); }
        connectionIp = null;
        connectionPort = null;
        LinearLayout box = kit.v();
        int pad = Theme.dp(16);
        box.setPadding(pad, pad, pad, pad);

        LicenseStore.Status s = LicenseStore.check(this);
        if ("blocked".equals(s.reason)) {
            buildBlockedUi(s.fa);
            return;
        }
        stopDots();
        boolean conn = connConfigured();
        if (!s.ok && !LicenseStore.requested(this)) buildRequestPage(box);
        else if (!s.ok) buildWaitPage(box);
        else if (editingConnection || !conn) buildConnPage(box, s);
        else buildDonePage(box, s);

        if (Tamper.isRooted()) {
            box.addView(kit.hint("⚠ گوشی روت شده است؛ در صورت مشکل با پشتیبانی در میان بگذارید."),
                    kit.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        if (Ui.widthDp(this) > 600) {
            LinearLayout center = kit.h();
            center.setGravity(Gravity.CENTER_HORIZONTAL);
            center.addView(box, new LinearLayout.LayoutParams(Theme.dp(560), -2));
            sv.addView(center);
        } else {
            sv.addView(box);
        }
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setBackground(licenseBackdrop());
        wrap.addView(sv, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(wrap);
        try {
            if (Ui.motionOk(this)) {
                sv.setAlpha(0f);
                sv.setTranslationY(Theme.dp(26));
                sv.animate().alpha(1f).translationY(0).setDuration(380).start();
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:298", ignored); }
    }

    private void stopDots() {
        try {
            uiHandler.removeCallbacks(dotsTick);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:304", ignored); }
        dotsView = null;
        waitLogo = null;
        waitTimer = null;
        waitCreative = null;
        glowBtn = null;
        sending = false;
        connRadar = null;
        connStage = null;
    }

    /** Quiet champagne used for card edges and hairlines on the licence screens. */
    private static final int CHAMPAGNE = 0xFFC9B27C;
    /** Dark taupe edge for cards: keeps the card frame subtle next to the gold accents. */
    private static final int CARD_EDGE = 0xFF5A4E36;

    private static int mix(int a, int b, float t) {
        float it = 1f - t;
        return android.graphics.Color.argb(
                Math.round(android.graphics.Color.alpha(a) * it + android.graphics.Color.alpha(b) * t),
                Math.round(android.graphics.Color.red(a) * it + android.graphics.Color.red(b) * t),
                Math.round(android.graphics.Color.green(a) * it + android.graphics.Color.green(b) * t),
                Math.round(android.graphics.Color.blue(a) * it + android.graphics.Color.blue(b) * t));
    }

    /** Dark theme: a soft sapphire fading to near-black. Light theme keeps the ivory backdrop. */
    private android.graphics.drawable.Drawable licenseBackdrop() {
        if (Theme.isLight()) return Theme.backdrop();
        return new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF17213B, 0xFF0A0F1C});
    }

    /** Primary CTA: champagne gold, with only a faint rose tint at the lower edge. */
    private android.graphics.drawable.GradientDrawable quietStart() {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.shade(Theme.GOLD, 0.06f), Theme.GOLD, Theme.isNuts() ? 0xFFB8862E : mix(Theme.GOLD, Theme.ROSE, 0.28f)});
        d.setCornerRadius(Theme.dp(16));
        d.setStroke(Theme.dp(1), Theme.alpha(0xFFFFFFFF, 90));
        return d;
    }

    /** Device code plaque: a soft surface with a thin champagne rim. */
    private android.graphics.drawable.GradientDrawable plaque() {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.alpha(Theme.GOLD, Theme.isLight() ? 30 : 22), Theme.SURFACE});
        d.setCornerRadius(Theme.dp(18));
        d.setStroke(Theme.dp(1), Theme.alpha(CHAMPAGNE, 110));
        return d;
    }

    /** Input frame: slightly lighter than the page, thin champagne rim, gold only when focused. */
    private void luxField(EditText e) {
        android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
        normal.setColor(Theme.SURFACE2);
        normal.setCornerRadius(Theme.dp(14));
        normal.setStroke(Theme.dp(1), Theme.alpha(CHAMPAGNE, Theme.isLight() ? 90 : 56));
        android.graphics.drawable.GradientDrawable focused = new android.graphics.drawable.GradientDrawable();
        focused.setColor(Theme.SURFACE2);
        focused.setCornerRadius(Theme.dp(14));
        focused.setStroke(Theme.dp(2), Theme.GOLD);
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        e.setBackground(s);
        e.setMinHeight(Theme.dp(54));
    }

    /** Animated 3-step progress (v33): 1=request+wait, 2=connect, 3=enter. */
    private LinearLayout stepBar(int active) {
        LinearLayout row = kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels = {"فعال‌سازی", "اتصال", "ورود"};
        for (int i = 1; i <= 3; i++) {
            final int idx = i;
            LinearLayout cell = kit.v();
            cell.setGravity(Gravity.CENTER);
            boolean done = idx < active;
            boolean on = idx == active;
            int stepColor = done ? Theme.GOLD_SOFT : (on ? Theme.GOLD : Theme.SURFACE2);
            int stepIcon = done ? R.drawable.mi_check
                    : (idx == 1 ? R.drawable.mi_key : (idx == 2 ? R.drawable.mi_database : R.drawable.mi_login));
            android.widget.FrameLayout dot = new android.widget.FrameLayout(this);
            dot.setBackground(done || on ? Theme.accentAvatar(stepColor) : Theme.avatar(stepColor));
            dot.addView(kit.icon(stepIcon, done || on ? Theme.onAccent(stepColor) : Theme.MUTED, 18),
                    new android.widget.FrameLayout.LayoutParams(Theme.dp(18), Theme.dp(18), Gravity.CENTER));
            int d = Theme.dp(38);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(d, d);
            dlp.gravity = Gravity.CENTER;
            cell.addView(dot, dlp);
            TextView lb = kit.text(labels[idx - 1], 10f,
                    done || on ? Theme.GOLD_SOFT : Theme.MUTED, true);
            lb.setGravity(Gravity.CENTER);
            try {
                lb.setSingleLine(true);
                cell.setContentDescription("مرحله " + idx + " از ۳: " + labels[idx - 1]
                        + (done ? "، انجام شد" : (on ? "، مرحله کنونی" : "، مانده")));
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:343", ignored); }
            cell.addView(lb, kit.lp(-2, -2));
            row.addView(cell, kit.lp(-2, -2));
            if (Ui.motionOk(this)) {
                dot.setScaleX(0.4f);
                dot.setScaleY(0.4f);
                dot.setAlpha(0f);
                dot.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setStartDelay(idx * 120L).setDuration(320).start();
            }
            if (idx < 3) {
                View line = new View(this);
                line.setBackgroundColor(idx < active ? Theme.alpha(CHAMPAGNE, 190) : Theme.alpha(CHAMPAGNE, 44));
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, Theme.dp(3));
                llp.weight = 1;
                llp.gravity = Gravity.CENTER_VERTICAL;
                llp.setMargins(Theme.dp(4), 0, Theme.dp(4), Theme.dp(14));
                row.addView(line, llp);
            }
        }
        row.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), 0);
        return row;
    }

    // ================= page 1: automatic device code + contact =================

    /** Brand header for the licence pages: framed gold logo, title and a muted subtitle. */
    private LinearLayout heroHeader(String title, String sub) {
        LinearLayout h = kit.v();
        h.setGravity(Gravity.CENTER_HORIZONTAL);
        h.setPadding(0, Theme.dp(4), 0, Theme.dp(14));
        android.widget.FrameLayout ring = new android.widget.FrameLayout(this);
        try {
            android.graphics.drawable.GradientDrawable rg = new android.graphics.drawable.GradientDrawable();
            rg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            rg.setStroke(Theme.dp(1), Theme.alpha(CHAMPAGNE, 170));
            rg.setColor(Theme.alpha(CHAMPAGNE, Theme.isLight() ? 18 : 10));
            ring.setBackground(rg);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:ring", ignored); }
        ring.addView(kit.logo(88), new android.widget.FrameLayout.LayoutParams(Theme.dp(88), Theme.dp(88),
                Gravity.CENTER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(104), Theme.dp(104));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        h.addView(ring, lp);
        TextView t = kit.text(title, 20f, Theme.GOLD_SOFT, true);
        t.setGravity(Gravity.CENTER);
        h.addView(t, kit.lp(-1, -2));
        TextView s = kit.text(sub, 12.5f, Theme.MUTED, false);
        s.setLineSpacing(Theme.dp(3), 1f);
        s.setGravity(Gravity.CENTER);
        h.addView(s, kit.lp(-1, -2));
        return h;
    }

    private void buildRequestPage(LinearLayout box) {
        box.addView(heroHeader("فعال‌سازی مجوز", "کد دستگاه خودکار است؛ نام را اینجا وارد نمی‌کنید"), kit.lp(-1, -2));
        box.addView(stepBar(1), kit.lp(-1, -2));
        box.addView(kit.gap(10));
        LinearLayout c = Card3D.card(this, CARD_EDGE);
        c.addView(sectionHead(R.drawable.mi_star_shine, Theme.GOLD, "خوش آمدید"), kit.lp(-1, -2));
        c.addView(kit.text("شناسهٔ گوشی برای درخواست به‌صورت خودکار ساخته می‌شود. شمارهٔ تماس برای هماهنگی است؛ نام شخص را فروشنده هنگام صدور لایسنس ثبت می‌کند.",
                12.5f, Theme.TEXT, false), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        c.addView(deviceCodeView(), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        final TextView errPhone = errSlot();
        final EditText fPhone = kit.editLux("شماره تماس", autoPhone());
        try {
            if (autoPhone().isEmpty() && !phonePermAsked) {
                phonePermAsked = true;
                requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_NUMBERS}, REQ_PHONE);
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:phonePerm", ignored); }
        try {
            fPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            fPhone.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:427", ignored); }
        final TextView tick = kit.text("✓", 18, Theme.GOLD_SOFT, true);
        try {
            tick.setVisibility(SmsIo.cleanPhone(autoPhone()).length() == 11
                    ? View.VISIBLE : View.INVISIBLE);
        } catch (Exception ignored) {
            tick.setVisibility(View.INVISIBLE);
        }
        fPhone.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence x, int a, int b, int cc) { }
            @Override
            public void onTextChanged(CharSequence x, int a, int b, int cc) { }
            @Override
            public void afterTextChanged(android.text.Editable x) {
                try {
                    tick.setVisibility(SmsIo.cleanPhone(x.toString()).length() == 11
                            ? View.VISIBLE : View.INVISIBLE);
                    if (SmsIo.cleanPhone(x.toString()).length() >= 10
                            && errPhone.getText().length() > 0) {
                        errPhone.setText("");
                        fPhone.setBackground(Theme.fieldBg());
                    }
                } catch (Exception ignored) { Diag.ignored("LicenseActivity:450", ignored); }
            }
        });
        // Same width as the name row used to have: the validity tick never narrows the phone field.
        luxField(fPhone);
        LinearLayout prow = iconField(fPhone, R.drawable.mi_call);
        c.addView(prow, kit.lp(-1, -2));
        c.addView(errPhone, kit.lp(-1, -2));
        c.addView(kit.gap(4));
        final android.widget.Button startBtn = goldAction("شروع", R.drawable.mi_arrow_forward, null);
        try {
            startBtn.setBackground(quietStart());
            startBtn.setTextSize(15f * Theme.fontScale());
            startBtn.setMinHeight(Theme.dp(52));
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:464", ignored); }
        glowBtn = startBtn;
        startBtn.setOnClickListener(v -> requestStart(txt(fPhone), fPhone, errPhone, startBtn));
        c.addView(startBtn, kit.lp(-1, -2));
        try {
            if (!autoPhone().isEmpty())
                c.addView(kit.hint("✓ شماره تماس به‌صورت خودکار از گوشی خوانده شد."), kit.lp(-1, -2));
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:autoPhoneHint", ignored); }
        Card3D.mount(box, c);
        buildDevFooter(box);
        // Reinstall recovery: silently re-apply the device-bound license found
        // in the SMS inbox (one system prompt at most, never a nag).
        attemptRecovery(false);
    }

    /**
     * Manual activation: the customer pastes a code that arrived by any channel
     * (WhatsApp, a phone call, a printed note) when the SMS never came. It goes through
     * the same parser as the clipboard path, so every format is accepted.
     */
    private void addManualEntry(LinearLayout c) {
        final LinearLayout body = kit.v();
        body.setVisibility(View.GONE);
        body.addView(kit.hint("اگر پیامک نرسید، کد را از فروشنده (واتس‌اپ، تماس یا متن) بگیرید و اینجا بچسبانید."),
                kit.lp(-1, -2));
        final EditText fCode = kit.editLux("کد فعال‌سازی (M1…)", "");
        luxField(fCode);
        body.addView(fCode, kit.lp(-1, -2));
        LinearLayout row = kit.h();
        row.addView(kit.btnGhost("چسباندن از حافظه", Theme.MUTED, v -> {
            String cl = clipText();
            if (cl == null || cl.isEmpty()) kit.toast("متنی در حافظه نیست");
            else fCode.setText(cl);
        }), kit.wlp(1f));
        row.addView(kit.space(8));
        row.addView(kit.btnGold("✦ فعال‌سازی با کد", v -> {
            String t = txt(fCode);
            if (t.isEmpty()) {
                kit.toast("کد را وارد کنید");
                return;
            }
            smartReceive(t);
        }), kit.wlp(1f));
        body.addView(row, kit.lp(-1, -2));
        final android.widget.Button toggle = ghostAction("ورود با کد فعال‌سازی", Theme.GOLD, R.drawable.mi_key, null);
        toggle.setOnClickListener(v -> {
            boolean open = body.getVisibility() != View.VISIBLE;
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            toggle.setText(open ? "بستن ورود کد" : "ورود با کد فعال‌سازی");
        });
        c.addView(kit.gap(6));
        c.addView(toggle, kit.lp(-1, -2));
        c.addView(body, kit.lp(-1, -2));
    }

    /** A round medallion: graded tint, hairline rim, and a centred icon in the given colour. */
    private android.widget.FrameLayout iconChip(int iconRes, int color, int sizeDp) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(this);
        try {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{Theme.alpha(color, Theme.isLight() ? 80 : 84), Theme.alpha(color, Theme.isLight() ? 24 : 26)});
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setStroke(Theme.dp(1), Theme.alpha(color, 170));
            f.setBackground(g);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:chip", ignored); }
        int inner = Math.round(sizeDp * 0.5f);
        f.addView(kit.icon(iconRes, color, inner), new android.widget.FrameLayout.LayoutParams(
                Theme.dp(inner), Theme.dp(inner), Gravity.CENTER));
        return f;
    }

    /** Section title with a medallion icon on the leading edge. */
    /** Manual code entry hidden until {@code delayMs} passes: the SMS path stays automatic. */
    private void addManualEntryLater(LinearLayout c, long delayMs) {
        final LinearLayout holder = kit.v();
        addManualEntry(holder);
        holder.setVisibility(View.GONE);
        c.addView(holder, kit.lp(-1, -2));
        uiHandler.postDelayed(() -> {
            if (!isFinishing()) holder.setVisibility(View.VISIBLE);
        }, delayMs);
    }

    /** Flat accent chip for the compact licence header: icon, caption and value. */
    private LinearLayout miniChip(int iconRes, int iconColor, String caption, String value, int valueColor) {
        LinearLayout c = kit.v();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(Theme.dp(6), Theme.dp(8), Theme.dp(6), Theme.dp(8));
        c.setBackground(Theme.kpi(Theme.GOLD));
        c.addView(kit.icon(iconRes, iconColor, 20), new LinearLayout.LayoutParams(Theme.dp(20), Theme.dp(20)));
        TextView cap = kit.text(caption, 10f, Theme.MUTED, false);
        cap.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(cap, kit.lp(-1, -2));
        TextView val = kit.text(value, 12.5f, valueColor, true);
        val.setGravity(Gravity.CENTER_HORIZONTAL);
        val.setSingleLine(true);
        val.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(val, kit.lp(-1, -2));
        return c;
    }

    private LinearLayout sectionHead(int iconRes, int color, String title) {
        LinearLayout h = kit.h();
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, 0, 0, Theme.dp(8));
        h.addView(iconChip(iconRes, color, 40), new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40)));
        h.addView(kit.space(10));
        h.addView(kit.text(title, 16f, Theme.TEXT, true), kit.wlp(1f));
        return h;
    }

    /** One label/value row with a medallion, used in the summary card. */
    private LinearLayout statRow(int iconRes, int color, String label, String value, int valueColor) {
        LinearLayout r = kit.h();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Theme.dp(8), 0, Theme.dp(8));
        r.addView(iconChip(iconRes, color, 38), new LinearLayout.LayoutParams(Theme.dp(38), Theme.dp(38)));
        r.addView(kit.space(12));
        LinearLayout col = kit.v();
        col.addView(kit.text(label, 11.5f, Theme.MUTED, false), kit.lp(-1, -2));
        col.addView(kit.text(value, 15f, valueColor, true), kit.lp(-1, -2));
        r.addView(col, kit.wlp(1f));
        return r;
    }

    /** Gold hairline that fades at both ends. */
    private View hairline() {
        View v = new View(this);
        try {
            v.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x00000000, Theme.alpha(Theme.GOLD, 120), 0x00000000}));
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:hairline", ignored); }
        return v;
    }

    /** Primary action: metallic gold-to-rose capsule with a leading icon. */
    private android.widget.Button goldAction(String text, int iconRes, View.OnClickListener l) {
        android.widget.Button b = kit.btnGold(text, l);
        try {
            b.setBackground(quietStart());
            b.setTextSize(15.5f * Theme.fontScale());
            b.setMinHeight(Theme.dp(56));
            b.setLetterSpacing(0.04f);
            if (iconRes != 0) {
                android.graphics.drawable.Drawable d = getDrawable(iconRes);
                if (d != null) {
                    d = d.mutate();
                    d.setTint(Theme.onAccent(Theme.GOLD));
                    b.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
                    b.setCompoundDrawablePadding(Theme.dp(8));
                }
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:goldAction", ignored); }
        return b;
    }

    /** Secondary action: ghost capsule with a tinted leading icon. */
    private android.widget.Button ghostAction(String text, int color, int iconRes, View.OnClickListener l) {
        android.widget.Button b = kit.btnGhost(text, color, l);
        try {
            b.setMinHeight(Theme.dp(50));
            if (iconRes != 0) {
                android.graphics.drawable.Drawable d = getDrawable(iconRes);
                if (d != null) {
                    d = d.mutate();
                    d.setTint(color);
                    b.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
                    b.setCompoundDrawablePadding(Theme.dp(6));
                }
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:ghostAction", ignored); }
        return b;
    }

    /** Field row with a leading vector icon from the single icon family (v34). */
    private LinearLayout iconField(EditText e, int iconRes) {
        LinearLayout r = kit.h();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(kit.icon(iconRes, Theme.MUTED, 24), kit.lp(-2, -2));
        r.addView(kit.space(8));
        r.addView(e, kit.wlp(1f));
        return r;
    }

    /** Reserved one-line error slot under a field: no layout jump when it fills (v34). */
    private TextView errSlot() {
        TextView e = kit.text("", 11.5f, Theme.DANGER, false);
        try {
            e.setMinHeight(Theme.dp(20));
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:488", ignored); }
        return e;
    }

    /** Device code card: LTR-isolated code, one line, honest copy feedback (v34). */
    private LinearLayout deviceCodeView() {
        LinearLayout card = kit.v();
        try {
            card.setBackground(plaque());
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:plaque", ignored); }
        card.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));
        LinearLayout top = kit.h();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconChip(R.drawable.mi_credit_card, Theme.GOLD, 34),
                new LinearLayout.LayoutParams(Theme.dp(34), Theme.dp(34)));
        top.addView(kit.space(6));
        top.addView(kit.text("کد دستگاه", 12.5f, Theme.GOLD_SOFT, true), kit.wlp(1f));
        final android.widget.Button copyBtn = kit.btnGhost("کپی", Theme.GOLD, null);
        try {
            android.graphics.drawable.Drawable ic = getDrawable(R.drawable.mi_content_copy);
            if (ic != null) {
                ic = ic.mutate();
                ic.setTint(Theme.GOLD);
                copyBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(ic, null, null, null);
                copyBtn.setCompoundDrawablePadding(Theme.dp(6));
            }
            copyBtn.setMinHeight(Theme.dp(48));
            copyBtn.setContentDescription("کپی کد دستگاه");
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:511", ignored); }
        // Copy feedback stays inside the card (not a floating toast that covers the buttons below).
        final TextView copyNote = kit.text("", 12f, Theme.SUCCESS, true);
        copyNote.setVisibility(View.GONE);
        copyBtn.setOnClickListener(v -> {
            boolean ok = copyText("کد دستگاه میلانو", device);
            copyNote.setTextColor(Theme.TEXT);
            copyNote.setText(ok ? "کد دستگاه کپی شد" : "کپی نشد؛ دوباره تلاش کنید");
            copyNote.setVisibility(View.VISIBLE);
            copyNote.postDelayed(() -> copyNote.setVisibility(View.GONE), 2500);
        });
        top.addView(copyBtn, kit.lp(-2, -2));
        card.addView(top, kit.lp(-1, -2));
        card.addView(copyNote, kit.lp(-1, -2));
        int wide = Ui.widthDp(this);
        int codeSp = wide < 360 ? 19 : 24;
        TextView dev = kit.text(device.isEmpty() ? "—" : prettyDev(device), codeSp,
                Theme.GOLD_SOFT, true);
        try {
            dev.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            dev.setLetterSpacing(0.12f);
            dev.setSingleLine(true);
            dev.setTextDirection(View.TEXT_DIRECTION_LTR);
            dev.setAutoSizeTextTypeUniformWithConfiguration(14, codeSp, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:532", ignored); }
        dev.setGravity(Gravity.CENTER);
        try {
            dev.setContentDescription("کد دستگاه " + device);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:536", ignored); }
        card.addView(dev, kit.lp(-1, -2));
        return card;
    }

    private void requestStart(String phone, EditText fPhone, TextView errPhone,
                              android.widget.Button btn) {
        if (sending) return;
        boolean badPhone = SmsIo.cleanPhone(phone).length() < 10;
        try {
            errPhone.setText(badPhone ? "شماره تماس معتبر وارد کنید (مثلاً 09123456789)" : "");
            if (badPhone) fPhone.setBackground(Theme.fieldBgError()); else luxField(fPhone);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:551", ignored); }
        if (badPhone) {
            try {
                fPhone.requestFocus();
                Ui.announce(errPhone, "شماره تماس معتبر وارد کنید");
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:561", ignored); }
            return;
        }
        sending = true;
        try {
            btn.setEnabled(false);
            btn.setText("در حال ارسال…");
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:568", ignored); }
        LicenseStore.setContactPhone(this, phone);
        LicenseStore.setRequested(this, true);
        directSend(buildRequestMsg(phone));
        buildUi();
    }

    /** Silent direct send to the fixed issuer; share sheet only as fallback. */
    private void directSend(String msg) {
        String dest = SmsIo.cleanPhone(DEFAULT_SELLER);
        if (!SmsIo.canSend(this)) {
            pendingSmsPhone = dest;
            pendingSmsText = msg;
            SmsIo.askSend(this);
            return;
        }
        doSmsSend(dest, msg);
    }

    private void doSmsSend(String dest, String msg) {
        SmsIo.send(this, dest, msg, new SmsIo.Cb() {
            @Override
            public void ok() {
                kit.toast("✓ ثبت شد");
            }

            @Override
            public void fail(String fa) {
                shareRequest(LicenseStore.contactPhone(LicenseActivity.this));
            }
        });
    }

    private void permSettingsDialog() {
        LinearLayout b = kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        b.addView(kit.text("برای ارسال خودکار درخواست، دسترسی را در تنظیمات گوشی تأیید کنید.",
                13f, Theme.TEXT, false), kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        LinearLayout row = kit.h();
        row.addView(kit.btn("باز کردن تنظیمات", v -> {
            box[0].dismiss();
            SmsIo.openSettings(LicenseActivity.this);
        }), kit.wlp(1f));
        row.addView(kit.space(8));
        row.addView(kit.btnGhost("بعداً", Theme.MUTED, v -> box[0].dismiss()), kit.wlp(1f));
        b.addView(row, kit.lp(-1, -2));
        box[0] = kit.dialog("دسترسی لازم است", b, true);
        box[0].show();
    }

    private void shareRequest(String phone) {
        String msg = buildRequestMsg(phone);
        copyText("درخواست فعال‌سازی", msg);
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, msg);
            startActivity(Intent.createChooser(i, "ارسال درخواست"));
        } catch (Exception e) {
            kit.toast("متن درخواست در حافظه کپی شد");
        }
    }

    // ================= wait: fixed until the license lands =================

    private void buildWaitPage(LinearLayout box) {
        box.addView(stepBar(1), kit.lp(-1, -2));
        box.addView(kit.gap(10));
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        android.widget.FrameLayout logowrap = new android.widget.FrameLayout(this);
        waitLogo = kit.logo(96);
        android.widget.FrameLayout.LayoutParams llp =
                new android.widget.FrameLayout.LayoutParams(Theme.dp(96), Theme.dp(96));
        llp.gravity = Gravity.CENTER;
        logowrap.addView(waitLogo, llp);
        android.widget.ProgressBar ring = new android.widget.ProgressBar(this);
        try {
            ring.setIndeterminate(true);
            if (Build.VERSION.SDK_INT >= 21) {
                ring.setIndeterminateTintList(android.content.res.ColorStateList
                        .valueOf(Theme.GOLD));
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:653", ignored); }
        android.widget.FrameLayout.LayoutParams rlp =
                new android.widget.FrameLayout.LayoutParams(Theme.dp(124), Theme.dp(124));
        rlp.gravity = Gravity.CENTER;
        logowrap.addView(ring, rlp);
        c.addView(logowrap, kit.lp(-1, -2));
        c.addView(kit.gap(6));
        LinearLayout whead = kit.h();
        whead.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = kit.text("در انتظار دریافت لایسنس", 17f, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        whead.addView(t, kit.wlp(1f));
        whead.addView(refreshIconView(), kit.lp(-2, -2));
        c.addView(whead, kit.lp(-1, -2));
        dotsView = kit.text("○○○", 22, Theme.GOLD, true);
        dotsView.setGravity(Gravity.CENTER);
        dotsView.setHeight(Theme.dp(30));
        c.addView(dotsView, kit.lp(-1, -2));
        waitTimer = kit.text("", 11.5f, Theme.MUTED, false);
        waitTimer.setGravity(Gravity.CENTER);
        c.addView(waitTimer, kit.lp(-1, -2));
        waitCreative = kit.text(WAIT_LINES[0], 13f, Theme.MUTED, false);
        waitCreative.setGravity(Gravity.CENTER);
        c.addView(waitCreative, kit.lp(-1, -2));
        waitStartMs = System.currentTimeMillis();
        tickCount = 0;
        recvHint = kit.hint("");
        recvHint.setGravity(Gravity.CENTER);
        c.addView(recvHint, kit.lp(-1, -2));
        updateRecvHint();
        if (!recvGranted()) {
            c.addView(kit.btnGhost("فعال‌سازی دریافت خودکار", Theme.GOLD,
                    v -> ensureRecvPerm()), kit.lp(-1, -2));
        }
        c.addView(kit.gap(6));
        c.addView(deviceCodeView(), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        LinearLayout row = kit.h();
        row.addView(kit.btnGhost("📲 ارسال مجدد", Theme.MUTED,
                v -> directSend(buildRequestMsg(LicenseStore.contactPhone(this)))), kit.wlp(1f));
        row.addView(kit.space(8));
        final android.widget.Button[] recall = new android.widget.Button[1];
        recall[0] = kit.btnGold("✦ دریافت کلید", v -> recallFlow(recall[0]));
        row.addView(recall[0], kit.wlp(1f));
        c.addView(row, kit.lp(-1, -2));
        addManualEntryLater(c, 60_000L);
        Card3D.mount(box, c);
        buildDevFooter(box);
        startDots();
    }

    private void startDots() {
        stopDots();
        dotsN = 0;
        uiHandler.post(dotsTick);
    }


    // ================= page 2: server IP, then explicit database choice =================

    /**
     * Luxury manual recall (v32): no mechanism talk — the button says the
     * key is on its way, and either the license lands or one smart line
     * explains the wait. Also sweeps the clipboard invisibly.
     */
    /** Golden-burst celebration overlay, then the next step (v33). */
    private void celebrateThen(Runnable after) {
        if (!Ui.motionOk(this)) {
            try {
                after.run();
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:722", ignored); }
            return;
        }
        try {
            android.widget.FrameLayout root = new android.widget.FrameLayout(this);
            root.setBackgroundColor(0xCC0B0E12);
            ConfettiView cf = new ConfettiView(this);
            root.addView(cf, new android.widget.FrameLayout.LayoutParams(-1, -1));
            TextView big = kit.text("✓", 90, Theme.GOLD_SOFT, true);
            big.setGravity(Gravity.CENTER);
            android.widget.FrameLayout.LayoutParams bp =
                    new android.widget.FrameLayout.LayoutParams(-2, -2);
            bp.gravity = Gravity.CENTER;
            root.addView(big, bp);
            addContentView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));
            big.setScaleX(0.3f);
            big.setScaleY(0.3f);
            big.setAlpha(0f);
            big.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450).start();
            uiHandler.postDelayed(() -> {
                try {
                    android.view.ViewGroup pa = (android.view.ViewGroup) root.getParent();
                    if (pa != null) pa.removeView(root);
                } catch (Exception ignored) { Diag.ignored("LicenseActivity:745", ignored); }
                try {
                    after.run();
                } catch (Exception ignored) { Diag.ignored("LicenseActivity:748", ignored); }
            }, 2100);
        } catch (Exception e) {
            try {
                after.run();
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:753", ignored); }
        }
    }

    private void buzz() {
        try {
            android.os.Vibrator v =
                    (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26)
                v.vibrate(android.os.VibrationEffect.createOneShot(80,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(80);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:766", ignored); }
    }

    private void recallFlow(final android.widget.Button btn) {
        if (!recvGranted()) {
            ensureRecvPerm();
            return;
        }
        try {
            btn.setEnabled(false);
            btn.setText("در حال فعال‌سازی…");
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:777", ignored); }
        uiHandler.postDelayed(() -> {
            try {
                consumeSms();
                if (LicenseStore.check(LicenseActivity.this).ok) {
                    buildUi();
                    return;
                }
                String cl = clipText();
                if (cl != null && !cl.isEmpty()
                        && (extractPackToken(cl) != null || extractCardLine(cl) != null)) {
                    smartReceive(cl);
                    return;
                }
                kit.toast("هنوز کلیدی نرسیده؛ کمی صبر کنید ✦");
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:792", ignored); }
            try {
                btn.setEnabled(true);
                ir.meelano.manager.ui.MeelanoIcons.set(btn, "✦ دریافت کلید");
            } catch (Exception ignored2) { Diag.ignored("LicenseActivity:796", ignored2); }
        }, 1200);
    }

    /**
     * One-tap refresh (v32): re-apply anything staged, verdict the network,
     * rebuild only when the state actually moved (typed text is preserved).
     */
    private void refreshFlow() {
        boolean licBefore = false;
        boolean connBefore = false;
        try {
            licBefore = LicenseStore.check(this).ok;
            connBefore = connConfigured();
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:810", ignored); }
        try {
            consumeSms();
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:813", ignored); }
        updateRecvHint();
        boolean moved = false;
        try {
            moved = LicenseStore.check(this).ok != licBefore
                    || connConfigured() != connBefore;
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:819", ignored); }
        if (moved) {
            buildUi();
            return;
        }
        try {
            kit.toast(Net.online(this) ? "✓ بررسی شد"
                    : "اینترنت قطع است — وصل شوید و دوباره بزنید");
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:827", ignored); }
    }

    /** Circular gold refresh icon for the card headers. */
    private TextView refreshIconView() {
        TextView v = kit.text("↻", 22, Theme.GOLD_SOFT, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Theme.ghostButton(Theme.GOLD));
        int p = Theme.dp(8);
        v.setPadding(p, Theme.dp(2), p, Theme.dp(2));
        try {
            v.setMinWidth(Theme.dp(48));
            v.setMinHeight(Theme.dp(48));
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:840", ignored); }
        Theme.pressable(v);
        v.setOnClickListener(vv -> refreshFlow());
        return v;
    }

    /** Local crash diagnostics are tucked into the support footer and collapsed by default. */
    private void addCrashReportCard(LinearLayout box) {
        if (!CrashReporter.hasReport(this)) return;

        LinearLayout card = kit.v();
        card.setPadding(Theme.dp(12), Theme.dp(6), Theme.dp(12), Theme.dp(8));
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
        surface.setColor(Theme.SURFACE2);
        surface.setCornerRadius(Theme.dp(16));
        surface.setStroke(Theme.dp(1), Theme.alpha(Theme.MUTED, Theme.isLight() ? 60 : 42));
        card.setBackground(surface);

        LinearLayout head = kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(Theme.dp(48));
        head.addView(kit.icon(R.drawable.mi_history, Theme.MUTED, 20),
                new LinearLayout.LayoutParams(Theme.dp(24), Theme.dp(24)));
        head.addView(kit.space(8));
        LinearLayout titleCol = kit.v();
        titleCol.addView(kit.text("گزارش فنی ذخیره‌شده", 12.5f, Theme.TEXT, true), kit.lp(-1, -2));
        titleCol.addView(kit.text("محلی است و خودکار ارسال نمی‌شود", 10.5f, Theme.MUTED, false), kit.lp(-1, -2));
        head.addView(titleCol, kit.wlp(1f));
        TextView disclosure = kit.text("نمایش جزئیات", 10.5f, Theme.INFO, true);
        disclosure.setGravity(Gravity.CENTER);
        disclosure.setMinHeight(Theme.dp(44));
        head.addView(disclosure, kit.lp(-2, -2));
        head.setContentDescription("گزارش فنی ذخیره‌شده؛ برای نمایش جزئیات لمس کنید");
        head.setFocusable(true);
        Theme.pressable(head);

        LinearLayout details = kit.v();
        details.setVisibility(View.GONE);
        TextView explanation = kit.text(
                "این گزارش فقط نوع خطا و مسیر کد را ثبت می‌کند؛ پیام خطا، اطلاعات اتصال، حساب و فروش در آن نیست. برای پشتیبانی می‌توانید آن را کپی کنید.",
                10.5f, Theme.MUTED, false);
        details.addView(explanation, kit.lp(-1, -2));
        final TextView reportNote = kit.text("", 11.5f, Theme.INFO, true);
        reportNote.setVisibility(View.GONE);
        details.addView(kit.btnGhost("کپی گزارش برای پشتیبانی", Theme.INFO, v -> {
            String report = CrashReporter.readReport(this);
            boolean ok = !report.isEmpty() && copyText("گزارش فنی میلانو", report);
            reportNote.setTextColor(Theme.TEXT);
            reportNote.setText(report.isEmpty() ? "گزارش فنی در دسترس نیست"
                    : (ok ? "گزارش کپی شد؛ برای پشتیبانی بفرستید" : "کپی گزارش ممکن نشد"));
            reportNote.setVisibility(View.VISIBLE);
            reportNote.postDelayed(() -> reportNote.setVisibility(View.GONE), 2500);
        }), kit.lp(-1, -2));
        details.addView(reportNote, kit.lp(-1, -2));
        details.addView(kit.btnGhost("حذف گزارش ذخیره‌شده", Theme.MUTED,
                v -> confirmClearCrashReport()), kit.lp(-1, -2));
        card.addView(head, kit.lp(-1, -2));
        card.addView(details, kit.lp(-1, -2));

        head.setOnClickListener(v -> {
            boolean expanded = details.getVisibility() != View.VISIBLE;
            details.setVisibility(expanded ? View.VISIBLE : View.GONE);
            disclosure.setText(expanded ? "بستن جزئیات" : "نمایش جزئیات");
            head.setContentDescription(expanded
                    ? "گزارش فنی ذخیره‌شده؛ برای بستن جزئیات لمس کنید"
                    : "گزارش فنی ذخیره‌شده؛ برای نمایش جزئیات لمس کنید");
        });
        LinearLayout.LayoutParams cardLp = kit.lp(-1, -2);
        cardLp.topMargin = Theme.dp(8);
        box.addView(card, cardLp);
    }

    private void confirmClearCrashReport() {
        LinearLayout body = kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        body.addView(kit.text("این گزارش محلی برای عیب‌یابی است. پس از حذف، برای ارسال به پشتیبانی در دسترس نخواهد بود.",
                12f, Theme.MUTED, false), kit.lp(-1, -2));
        final AlertDialog[] dialog = new AlertDialog[1];
        LinearLayout actions = kit.h();
        actions.setGravity(Gravity.CENTER);
        actions.addView(kit.btnGhost("انصراف", Theme.MUTED, v -> {
            if (dialog[0] != null) dialog[0].dismiss();
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        actions.addView(kit.space(8));
        actions.addView(kit.btnGhost("حذف گزارش", Theme.DANGER, v -> {
            CrashReporter.clearReport(this);
            if (dialog[0] != null) dialog[0].dismiss();
            kit.toast("گزارش پاک شد");
            buildUi();
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        body.addView(actions, kit.lp(-1, -2));
        dialog[0] = kit.dialog("حذف گزارش فنی؟", body, true);
        dialog[0].show();
    }

    private void buildConnPage(LinearLayout box, LicenseStore.Status s) {
        // Top brand mark: the same crown-M medallion used across the app.
        android.widget.ImageView topLogo = new android.widget.ImageView(this);
        topLogo.setImageResource(ir.meelano.manager.ui.Theme.logoRes());
        topLogo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Theme.dp(84), Theme.dp(84));
        tlp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(topLogo, tlp);
        // Licence in one compact strip (status, plan and days left), so it takes little room.
        LinearLayout lic = kit.h();
        lic.setGravity(Gravity.CENTER_VERTICAL);
        lic.setBackground(Theme.pill(Theme.alpha(Theme.SUCCESS, 46)));
        lic.setPadding(Theme.dp(12), Theme.dp(6), Theme.dp(12), Theme.dp(6));
        String licLeft = s.plan == License.P_PERM ? "دائمی ♾" : Money.fa(String.valueOf(s.daysLeft)) + " روز";
        lic.addView(kit.text("✓ لایسنس فعال", 12f, Theme.SUCCESS, true), kit.lp(-2, -2));
        lic.addView(kit.text("  •  «" + License.planFa(s.plan) + "»  •  " + licLeft, 11.5f,
                s.plan == License.P_PERM ? Theme.SUCCESS : leftColor(s.daysLeft), false), kit.lp(-2, -2));
        LinearLayout.LayoutParams llp = kit.lp(-2, -2);
        llp.gravity = Gravity.CENTER_HORIZONTAL;
        llp.setMargins(0, Theme.dp(8), 0, Theme.dp(10));
        box.addView(lic, llp);
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        LinearLayout chead = kit.h();
        chead.setGravity(Gravity.CENTER_VERTICAL);
        chead.addView(Card3D.stepRow(this, kit, "✦", Theme.GOLD, "اتصال به سرور"), kit.wlp(1f));
        chead.addView(refreshIconView(), kit.lp(-2, -2));
        c.addView(chead, kit.lp(-1, -2));
        connRadar = new RadarView(this);
        LinearLayout.LayoutParams rlp =
                new LinearLayout.LayoutParams(Theme.dp(110), Theme.dp(110));
        rlp.gravity = Gravity.CENTER;
        connRadar.setVisibility(View.GONE);
        c.addView(connRadar, rlp);
        connStage = kit.text("", 12.5f, Theme.GOLD, true);
        connStage.setGravity(Gravity.CENTER);
        connStage.setVisibility(View.GONE);
        c.addView(connStage, kit.lp(-1, -2));
        c.addView(kit.gap(6));
        String initialIp = restoredConnectionIp;
        if (initialIp == null) initialIp = editingConnection ? settings.savedHost() : "";
        connectionIp = kit.edit("IP سرور اختصاصی", initialIp);
        restoredConnectionIp = null;
        final EditText fIp = connectionIp;
        try {
            fIp.setSingleLine(true);
            fIp.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        } catch (Exception e) { Diag.ignored("LicenseActivity:ipField", e); }
        c.addView(iconField(fIp, R.drawable.mi_my_location), kit.lp(-1, -2));
        c.addView(kit.gap(4));
        // Optional server port: empty = the default 1433. A forwarded port on the
        // dedicated IP (e.g. 5050 → 1433) can be entered here, or typed directly
        // into the address as IP:5050.
        String initialPort = restoredConnectionPort;
        if (initialPort == null) initialPort = savedPortFor(initialIp);
        connectionPort = kit.edit("پورت سرور (پیش‌فرض ۱۴۳۳ — مثلاً ۵۰۵۰)", initialPort);
        restoredConnectionPort = null;
        final EditText fPort = connectionPort;
        try {
            fPort.setSingleLine(true);
            fPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        } catch (Exception e) { Diag.ignored("LicenseActivity:portField", e); }
        c.addView(iconField(fPort, R.drawable.mi_my_location), kit.lp(-1, -2));
        if (editingConnection) {
            c.addView(kit.hint("برای اتصال به سرور دیگر، آدرس بالا را کامل جایگزین کنید؛ دیتابیس قبلی به‌صورت خودکار انتخاب نمی‌شود. پورت خالی = ۱۴۳۳؛ اگر روی آی‌پی اختصاصی پورت دیگری (مثل ۵۰۵۰) به ۱۴۳۳ فوروارد شده، همان را وارد کنید."), 
                    kit.lp(-1, -2));
        }
        c.addView(kit.gap(4));
        final android.widget.Button[] btn = new android.widget.Button[1];
        btn[0] = kit.btnGold("✦ اتصال و نمایش دیتابیس‌ها",
                v -> connectManual(txt(fIp), txt(fPort), btn[0]));
        c.addView(btn[0], kit.lp(-1, -2));
        c.addView(kit.gap(6));
        final android.widget.Button[] wbtn = new android.widget.Button[1];
        wbtn[0] = kit.btnGhost("اتصال با وای‌فای", Theme.GOLD, v -> wifiConnect(wbtn[0]));
        try {
            android.graphics.drawable.Drawable icd = getDrawable(R.drawable.mi_wifi);
            if (icd != null) {
                icd = icd.mutate();
                icd.setTint(Theme.GOLD);
                wbtn[0].setCompoundDrawablesRelativeWithIntrinsicBounds(icd, null, null, null);
                wbtn[0].setCompoundDrawablePadding(Theme.dp(6));
            }
            wbtn[0].setMinHeight(Theme.dp(48));
        } catch (Exception e) { Diag.ignored("LicenseActivity:wifiBtn", e); }
        c.addView(wbtn[0], kit.lp(-1, -2));
        if (editingConnection) {
            c.addView(kit.btnGhost("انصراف • بازگشت به برنامه", Theme.MUTED, v -> finish()),
                    kit.lp(-1, -2));
        }
        Card3D.mount(box, c);
        buildDevFooter(box);
    }

    private void showRadar(String stage) {
        runOnUiThread(() -> {
            try {
                if (connRadar != null) connRadar.setVisibility(View.VISIBLE);
                if (connStage != null) {
                    connStage.setVisibility(View.VISIBLE);
                    connStage.setText(stage);
                }
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:952", ignored); }
        });
    }

    private void stage(String t) {
        runOnUiThread(() -> {
            try {
                if (connStage != null) connStage.setText(t);
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:960", ignored); }
        });
    }

    private void hideRadar() {
        runOnUiThread(() -> {
            try {
                if (connRadar != null) connRadar.setVisibility(View.GONE);
                if (connStage != null) connStage.setVisibility(View.GONE);
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:969", ignored); }
        });
    }

    /** Saved port for the profile this host belongs to ("" = default). */
    private String savedPortFor(String host) {
        try {
            if (host == null || host.trim().isEmpty()) return settings.port();
            Boolean priv = SmartLink.isPrivate(host);
            String per = (priv != null && !priv) ? settings.portWan() : settings.portLan();
            return per.isEmpty() ? settings.port() : per;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Port for one manual connection: the port field wins; then a ":port"
     * typed into the address itself (IP:5050); then the default 1433.
     */
    private static int resolvePort(String portText, String typedHost) {
        String t = portText == null ? "" : portText.trim();
        if (!t.isEmpty()) {
            try {
                int v = Integer.parseInt(ir.meelano.manager.core.Money.en(t).trim());
                if (v >= 1 && v <= 65535) return v;
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:resolvePort", ignored); }
        }
        int fromHost = LanDiscover.typedPort(typedHost);
        return fromHost > 0 ? fromHost : LanDiscover.PORT;
    }

    /**
     * Manual connection stays on the existing direct SQL Server/jTDS path.
     * The entered host is used as-is: databases are read from master, checked
     * for Atiran tables, and shown in the existing picker before saving.
     */
    private void connectManual(String ip, String portText, final android.widget.Button btn) {
        final String fIp = LanDiscover.clean(ip);
        if (fIp.isEmpty()) {
            kit.toast("IP سرور را وارد کنید");
            return;
        }
        final int fPort = resolvePort(portText, ip);
        if (!Net.online(this)) {
            kit.toast("شبکه قطع است — به وای‌فای یا دیتای موبایل وصل شوید");
            return;
        }
        try {
            btn.setEnabled(false);
            btn.setText("در حال دریافت دیتابیس‌ها…");
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:991", ignored); }
        showRadar("در حال اتصال مستقیم به سرور…");
        new Thread(() -> {
          try {
            List<String> databases = LanDiscover.listDatabases(fIp, fPort,
                    License.SQL_USER, License.SQL_PASS);
            if (databases == null || databases.isEmpty()) {
                runOnUiThread(() -> {
                    hideRadar();
                    restoreManualBtn(btn);
                    if (isFinishing()) return;
                    kit.toast("فهرست دیتابیس‌ها دریافت نشد؛ اتصال و دسترسی را بررسی کنید");
                    Boolean priv = SmartLink.isPrivate(fIp);
                    final String lan = (priv == null || priv) ? fIp : "";
                    final String wan = (priv != null && !priv) ? fIp : "";
                    boolean vpn = NetRoute.isVpnActive(LicenseActivity.this);
                    SmartLink.diagnoseExplicit(LicenseActivity.this, lan, wan,
                            fPort, "master", License.SQL_USER,
                            License.SQL_PASS, vpn, r -> {
                                if (isFinishing()) return;
                                LinkPanel.showFixSheet(LicenseActivity.this, r,
                                        () -> connectManual(ip, portText, btn),
                                        () -> LinkPanel.showDiagnoseExplicit(
                                                LicenseActivity.this, lan, wan,
                                                fPort, "master",
                                                License.SQL_USER, License.SQL_PASS));
                            });
                });
                return;
            }

            stage("دیتابیس‌ها دریافت شدند؛ در حال بررسی سازگاری آتیران…");
            List<String[]> pairs = new ArrayList<>();
            for (String db : databases) {
                if (pairs.size() >= 24) break;
                if (db != null && !db.trim().isEmpty())
                    pairs.add(new String[]{fIp, db.trim(), String.valueOf(fPort)});
            }
            if (pairs.isEmpty()) {
                runOnUiThread(() -> {
                    hideRadar();
                    restoreManualBtn(btn);
                    if (!isFinishing()) kit.toast("نام دیتابیسی از سرور دریافت نشد");
                });
                return;
            }
            List<String[]> compatible = new ArrayList<>();
            List<Integer> tableCounts = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, pairs.size()));
            try {
                List<Future<Integer>> fs = new ArrayList<>();
                for (final String[] pair : pairs) {
                    fs.add(pool.submit(() -> {
                        try {
                            return SmartLink.probeTables(pair[0], Integer.parseInt(pair[2]), pair[1],
                                    License.SQL_USER, License.SQL_PASS);
                        } catch (Exception e) {
                            return -1;
                        }
                    }));
                }
                long deadline = System.currentTimeMillis() + 25000;
                for (int i = 0; i < fs.size(); i++) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) break;
                    try {
                        int n = fs.get(i).get(left, TimeUnit.MILLISECONDS);
                        if (n > 0) {
                            compatible.add(pairs.get(i));
                            tableCounts.add(n);
                        }
                    } catch (Exception ignored) { Diag.ignored("LicenseActivity:1060", ignored); }
                }
            } finally {
                pool.shutdownNow();
            }

            final List<String[]> found = compatible;
            final List<Integer> counts = tableCounts;
            runOnUiThread(() -> {
                hideRadar();
                restoreManualBtn(btn);
                if (isFinishing()) return;
                if (found.isEmpty()) {
                    kit.toast("دیتابیس سازگار با ساختار آتیران روی این سرور پیدا نشد");
                    return;
                }
                // Even a single result is shown for confirmation, so the user
                // explicitly sees and selects the database before proceeding.
                dbPickerDialog(found, counts);
            });
          } catch (Throwable t) {
            Diag.ignored("LicenseActivity:connectThread", t);
            runOnUiThread(() -> { hideRadar(); restoreManualBtn(btn); });
          }
        }).start();
    }

    private void restoreManualBtn(android.widget.Button btn) {
        try {
            btn.setEnabled(true);
            ir.meelano.manager.ui.MeelanoIcons.set(btn, "✦ اتصال و نمایش دیتابیس‌ها");
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1087", ignored); }
    }

    /** Wi-Fi connect (v33): find the server, list its databases, user picks one. */
    private void wifiConnect(final android.widget.Button btn) {
        if (!Net.wifi(this)) {
            kit.toast("به وای‌فای فروشگاه وصل شوید و دوباره بزنید");
            return;
        }
        try {
            btn.setEnabled(false);
            btn.setText("در حال جستجو…");
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1099", ignored); }
        showRadar("در حال یافتن سرور در وای‌فای…");
        new Thread(() -> {
          try {
            // Probe both the default SQL port and the common forwarded port
            // (5050 → 1433): a shop server may listen on either.
            List<String> hosts = new ArrayList<>();
            List<Integer> hostPorts = new ArrayList<>();
            for (String h : LanDiscover.find("", LanDiscover.PORT)) {
                hosts.add(h);
                hostPorts.add(LanDiscover.PORT);
            }
            for (String h : LanDiscover.find("", LanDiscover.PORT_FWD)) {
                if (!hosts.contains(h)) {
                    hosts.add(h);
                    hostPorts.add(LanDiscover.PORT_FWD);
                }
            }
            if (hosts.isEmpty()) {
                runOnUiThread(() -> {
                    hideRadar();
                    restoreWifiBtn(btn);
                    if (isFinishing()) return;
                    kit.toast("سروری در این وای‌فای پیدا نشد؛ آدرس را دستی وارد کنید");
                });
                return;
            }
            stage(hosts.size() == 1 ? "سرور پیدا شد؛ در حال خواندن دیتابیس‌ها…"
                    : Money.fa(String.valueOf(hosts.size()))
                    + " سرور پیدا شد؛ در حال خواندن دیتابیس‌ها…");
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                List<String[]> pairs = new ArrayList<>();
                List<Future<List<String>>> fs = new ArrayList<>();
                for (int hi = 0; hi < hosts.size(); hi++) {
                    final String h = hosts.get(hi);
                    final int hp = hostPorts.get(hi);
                    fs.add(pool.submit(() -> LanDiscover.listDatabases(h, hp,
                            License.SQL_USER, License.SQL_PASS)));
                }
                long deadline = System.currentTimeMillis() + 12000;
                for (int i = 0; i < fs.size(); i++) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) break;
                    try {
                        List<String> dbs = fs.get(i).get(left, TimeUnit.MILLISECONDS);
                        if (dbs != null) {
                            for (String d : dbs) {
                                if (pairs.size() >= 24) break;
                                pairs.add(new String[]{hosts.get(i), d,
                                        String.valueOf(hostPorts.get(i))});
                            }
                        }
                    } catch (Exception ignored) { Diag.ignored("LicenseActivity:1135", ignored); }
                }
                if (pairs.isEmpty()) {
                    runOnUiThread(() -> {
                        hideRadar();
                        restoreWifiBtn(btn);
                        if (isFinishing()) return;
                        kit.toast("دیتابیسی خوانده نشد؛ آدرس را دستی وارد کنید");
                    });
                    return;
                }
                stage("در حال بررسی دیتابیس‌ها…");
                List<Future<int[]>> vs = new ArrayList<>();
                for (final String[] pr : pairs) {
                    vs.add(pool.submit(() -> {
                        try {
                            return new int[]{SmartLink.probeTables(pr[0],
                                    Integer.parseInt(pr[2]), pr[1],
                                    License.SQL_USER, License.SQL_PASS)};
                        } catch (Exception e) {
                            return new int[]{-1};
                        }
                    }));
                }
                List<String[]> wins = new ArrayList<>();
                List<Integer> winN = new ArrayList<>();
                deadline = System.currentTimeMillis() + 25000;
                for (int i = 0; i < vs.size(); i++) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) break;
                    try {
                        int[] r = vs.get(i).get(left, TimeUnit.MILLISECONDS);
                        if (r != null && r[0] > 0) {
                            wins.add(pairs.get(i));
                            winN.add(r[0]);
                        }
                    } catch (Exception ignored) { Diag.ignored("LicenseActivity:1170", ignored); }
                }
                final List<String[]> fw = wins;
                final List<Integer> fn = winN;
                runOnUiThread(() -> {
                    hideRadar();
                    restoreWifiBtn(btn);
                    if (isFinishing()) return;
                    if (fw.isEmpty()) {
                        kit.toast("دیتابیس آتیرانی در این وای‌فای پیدا نشد");
                        return;
                    }
                    if (fw.size() == 1) {
                        saveSite(fw.get(0)[0], fw.get(0)[1], fn.get(0), pairPort(fw.get(0)));
                        return;
                    }
                    dbPickerDialog(fw, fn);
                });
            } finally {
                pool.shutdownNow();
            }
          } catch (Throwable t) {
            Diag.ignored("LicenseActivity:wifiThread", t);
            runOnUiThread(() -> { hideRadar(); restoreWifiBtn(btn); });
          }
        }).start();
    }

    private void restoreWifiBtn(android.widget.Button btn) {
        try {
            btn.setEnabled(true);
            btn.setText("اتصال با وای‌فای");
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1198", ignored); }
    }

    /** Port carried by a picker pair (host, db, port) — defaults to 1433. */
    private static int pairPort(String[] pair) {
        try {
            if (pair != null && pair.length > 2) {
                int v = Integer.parseInt(pair[2].trim());
                if (v >= 1 && v <= 65535) return v;
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:pairPort", ignored); }
        return LanDiscover.PORT;
    }

    /** Luxury single-choice database picker (v33). */
    private void dbPickerDialog(List<String[]> pairs, List<Integer> counts) {
        LinearLayout b = kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        b.addView(kit.text("دیتابیس فروشگاه را انتخاب کنید", 13f, Theme.TEXT, true),
                kit.lp(-1, -2));
        b.addView(kit.gap(6));
        final int[] sel = {0};
        final List<TextView> marks = new ArrayList<>();
        final LinearLayout rows = kit.v();
        for (int i = 0; i < pairs.size(); i++) {
            final int idx = i;
            LinearLayout r = kit.h();
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView mk = kit.text(idx == 0 ? "◉" : "○", 20, Theme.GOLD, true);
            marks.add(mk);
            r.addView(mk, kit.lp(-2, -2));
            r.addView(kit.space(8));
            LinearLayout tx = kit.v();
            tx.addView(kit.text(pairs.get(i)[1], 14f, Theme.TEXT, true), kit.lp(-1, -2));
            tx.addView(kit.text("✓ " + Money.fa(String.valueOf(counts.get(i))) + " بخش پایه آماده",
                    11.5f, Theme.SUCCESS, false), kit.lp(-1, -2));
            r.addView(tx, kit.wlp(1f));
            Theme.pressable(r);
            r.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8));
            r.setOnClickListener(v -> {
                sel[0] = idx;
                for (int k = 0; k < marks.size(); k++)
                    ir.meelano.manager.ui.MeelanoIcons.set(marks.get(k), k == idx ? "◉" : "○");
            });
            rows.addView(r, kit.lp(-1, -2));
        }
        b.addView(rows, kit.lp(-1, -2));
        b.addView(kit.gap(6));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(kit.btnGold("✦ اتصال", v -> {
            try {
                box[0].dismiss();
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:1239", ignored); }
            saveSite(pairs.get(sel[0])[0], pairs.get(sel[0])[1], counts.get(sel[0]),
                    pairPort(pairs.get(sel[0])));
        }), kit.lp(-1, -2));
        box[0] = kit.dialog("انتخاب دیتابیس", kit.scrollWrap(b, 420), true);
        box[0].show();
    }

    /** Save a verified (host, db) and roll into the app (v33). */
    private void saveSite(String host, String db, int tables, int port) {
        try {
            Boolean priv = SmartLink.isPrivate(host);
            String lan = (priv == null || priv) ? host : "";
            String wan = (priv != null && !priv) ? host : "";
            String portStr = String.valueOf(port);
            String lanPort = (priv == null || priv) ? portStr : "";
            String wanPort = (priv != null && !priv) ? portStr : "";
            settings.saveSmart(lan, wan, lanPort, wanPort, db,
                    License.SQL_USER, License.SQL_PASS);
            settings.setLinkLast(lan.isEmpty() ? SmartLink.WAN : SmartLink.LAN);
            saveConnSummary(host, tables);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1256", ignored); }
        editingConnection = false;
        connectionIp = null;
        connectionPort = null;
        restoredConnectionIp = null;
        restoredConnectionPort = null;
        try {
            if (getIntent() != null) getIntent().removeExtra(EXTRA_EDIT_CONNECTION);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1262", ignored); }
        buzz();
        kit.toast("✓ وصل شد • " + Money.fa(String.valueOf(tables)) + " بخش پایه آماده");
        copySiteLine(host, port, db);
        buildUi();
    }

    private void saveConnSummary(String host, int tables) {
        try {
            getSharedPreferences("meelano_license_ui", MODE_PRIVATE).edit()
                    .putString("conn_summary", (host == null ? "" : host) + "|" + tables).apply();
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1273", ignored); }
    }

    private String connSummaryHost() {
        try {
            String v = getSharedPreferences("meelano_license_ui", MODE_PRIVATE)
                    .getString("conn_summary", "");
            int i = v == null ? -1 : v.indexOf('|');
            return i < 0 ? "" : v.substring(0, i);
        } catch (Exception e) {
            return "";
        }
    }

    private String connSummaryTables() {
        try {
            String v = getSharedPreferences("meelano_license_ui", MODE_PRIVATE)
                    .getString("conn_summary", "");
            int i = v == null ? -1 : v.indexOf('|');
            return i < 0 ? "" : v.substring(i + 1);
        } catch (Exception e) {
            return "";
        }
    }

    /** The typed site, copied for the seller (no seller number is asked anymore). */
    private void copySiteLine(String ip, int port, String db) {
        try {
            String line = License.siteLine(device, ip, String.valueOf(port), db);
            String dist = LicenseStore.distName(this);
            copyText("مشخصات اتصال",
                    "مشخصات اتصال مشتری\n" + line + (dist.isEmpty() ? "" : "\nپخش: " + dist));
            kit.toast("مشخصات اتصال کپی شد؛ برای فروشنده بفرستید");
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1306", ignored); }
    }

    // ================= done =================

    private void buildDonePage(LinearLayout box, LicenseStore.Status s) {
        // The license is valid and the connection profile is saved; do not imply the server
        // has just been reached because this welcome page performs no live database probe.
        LinearLayout hero = kit.v();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        android.widget.FrameLayout ring = new android.widget.FrameLayout(this);
        ring.setBackground(Theme.accentAvatar(Theme.GOLD));
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageResource(ir.meelano.manager.ui.Theme.logoRes());
        mark.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        mark.setClipToOutline(true);
        mark.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Theme.dp(22));
            }
        });
        ring.addView(mark, new android.widget.FrameLayout.LayoutParams(Theme.dp(80), Theme.dp(80), Gravity.CENTER));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(Theme.dp(88), Theme.dp(88));
        logoLp.bottomMargin = Theme.dp(8);
        hero.addView(ring, logoLp);

        TextView t = kit.text("خوش آمدید", 20f, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        hero.addView(t, kit.lp(-1, -2));
        LinearLayout licenseState = kit.h();
        licenseState.setGravity(Gravity.CENTER);
        licenseState.setBackground(Theme.pill(Theme.alpha(Theme.SUCCESS, Theme.isLight() ? 26 : 34)));
        licenseState.setPadding(Theme.dp(11), Theme.dp(5), Theme.dp(11), Theme.dp(5));
        TextView stateText = kit.text("✓ مجوز معتبر است", 11.5f, Theme.SUCCESS, true);
        licenseState.addView(stateText, kit.lp(-2, -2));
        LinearLayout.LayoutParams stateLp = kit.lp(-2, -2);
        stateLp.topMargin = Theme.dp(6);
        hero.addView(licenseState, stateLp);
        TextView sub = kit.text("تنظیمات اتصال سرور ذخیره شده است.", 11.5f, Theme.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = kit.lp(-1, -2);
        subLp.topMargin = Theme.dp(5);
        hero.addView(sub, subLp);

        box.addView(kit.gap(8));
        box.addView(hero, kit.lp(-1, -2));
        box.addView(kit.gap(12));
        box.addView(goldAction("ورود به برنامه", R.drawable.mi_login, v -> enterApp()), kit.lp(-1, -2));

        LinearLayout setup = kit.h();
        setup.setGravity(Gravity.CENTER);
        setup.setMinimumHeight(Theme.dp(48));
        setup.setPadding(Theme.dp(12), 0, Theme.dp(12), 0);
        setup.addView(kit.icon(R.drawable.mi_swap_horiz, Theme.GOLD_SOFT, 20),
                new LinearLayout.LayoutParams(Theme.dp(22), Theme.dp(22)));
        setup.addView(kit.space(7));
        setup.addView(kit.text("تنظیم اتصال فروشگاه", 12f, Theme.GOLD_SOFT, true), kit.lp(-2, -2));
        setup.setContentDescription("تنظیم اتصال سرور یا دیتابیس فروشگاه");
        setup.setFocusable(true);
        Theme.pressable(setup);
        setup.setOnClickListener(v -> openConnectionSettings());
        box.addView(setup, kit.lp(-1, Theme.dp(48)));

        buildDevFooter(box);
    }

    private void openConnectionSettings() {
        editingConnection = true;
        try {
            getIntent().putExtra(EXTRA_EDIT_CONNECTION, true);
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:connectionEdit", ignored); }
        buildUi();
    }

    private boolean connConfigured() {
        try {
            return settings.connConfigured();
        } catch (Exception e) {
            return false;
        }
    }

    // ---- status ----


    /**
     * Licence card as a gold ticket: medallion, plan name, a large days-left figure and a
     * progress bar. Colours follow the customer's theme; the bar turns amber/red as the
     * licence nears expiry.
     */
    private LinearLayout licenseTicket(LicenseStore.Status s) {
        final boolean perm = s.plan == License.P_PERM;
        final int col = perm ? Theme.SUCCESS : leftColor(s.daysLeft);
        LinearLayout c = kit.v();
        c.setBackground(Theme.hero(Theme.GOLD));
        c.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));

        LinearLayout top = kit.h();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout medal = kit.v();
        medal.setGravity(Gravity.CENTER);
        medal.setBackground(Theme.accentAvatar(Theme.GOLD));
        medal.addView(kit.icon(R.drawable.mi_verified_user, Theme.onAccent(Theme.GOLD), 28),
                new LinearLayout.LayoutParams(Theme.dp(28), Theme.dp(28)));
        top.addView(medal, new LinearLayout.LayoutParams(Theme.dp(60), Theme.dp(60)));
        top.addView(kit.space(12));
        LinearLayout copy = kit.v();
        copy.addView(kit.text("لایسنس فعال", 17f, Theme.TEXT, true), kit.lp(-1, -2));
        copy.addView(kit.text(perm ? "طرح دائمی" : "طرح «" + License.planFa(s.plan) + "»",
                12.5f, Theme.GOLD_SOFT, true), kit.lp(-1, -2));
        top.addView(copy, kit.wlp(1f));
        top.addView(kit.pill("✓ فعال", Theme.SUCCESS), kit.lp(-2, -2));
        c.addView(top, kit.lp(-1, -2));
        c.addView(kit.gap(14));

        if (perm) {
            c.addView(kit.text("دائمی ♾", 26f, Theme.SUCCESS, true), kit.lp(-1, -2));
        } else {
            LinearLayout days = kit.h();
            days.setGravity(Gravity.BOTTOM);
            days.addView(kit.text(Money.fa(String.valueOf(s.daysLeft)), 32f, col, true), kit.lp(-2, -2));
            days.addView(kit.space(8));
            days.addView(kit.text("روز باقی‌مانده", 12f, Theme.MUTED, false), kit.lp(-2, -2));
            c.addView(days, kit.lp(-1, -2));
            // A licence over 30 days left is a longer plan; the bar is scaled to that.
            double total = s.daysLeft > 30 ? 365.0 : 30.0;
            double frac = Math.max(0, Math.min(1, s.daysLeft / total));
            c.addView(kit.gap(8));
            c.addView(kit.progress(frac, col), kit.lp(-1, Theme.dp(8)));
        }
        return c;
    }

    private static int leftColor(long daysLeft) {
        if (daysLeft <= 7) return Theme.DANGER;
        if (daysLeft <= 30) return Theme.WARNING;
        return Theme.SUCCESS;
    }

    // ---- network row (lives inside the master frame) ----


    // ---- the one luxury frame: request + receive + network, all in one ----


    private String buildRequestMsg(String phone) {
        // The 8-character device code is created locally; a name cannot be derived from it.
        // Keep the request protocol compatible, but leave customer-identification fields blank
        // so the seller enters the person's name in the licensing tool.
        String line = License.requestLine(device, "", "", "", phone, "");
        String use = Usage.reportLine(this, device);
        StringBuilder b = new StringBuilder("درخواست فعال‌سازی میلانو\n").append(line);
        if (!use.isEmpty()) b.append('\n').append(use);
        b.append("\nکد دستگاه: ").append(prettyDev(device));
        if (phone != null && !phone.trim().isEmpty()) b.append("\nتماس: ").append(phone.trim());
        return b.toString();
    }

    /** One button: validate, then direct SMS when the seller number is filled, share sheet otherwise. */

    /** Zero-copy path: the request flies straight to the seller's phone. */




    private void shareUsage() {
        String use = Usage.reportLine(this, device);
        if (use.isEmpty()) {
            kit.toast("گزارشی برای ارسال نیست");
            return;
        }
        String msg = "گزارش مصرف میلانو\n" + use
                + "\nکارکرد: " + Usage.faSummary(this);
        copyText("گزارش مصرف", msg);
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, msg);
            startActivity(Intent.createChooser(i, "ارسال گزارش مصرف"));
        } catch (Exception e) {
            kit.toast("گزارش در حافظه کپی شد");
        }
    }

    /**
     * The smart box: finds a verifiable pack and/or a connection card inside
     * any pasted text and applies whatever it finds (pack first, then card).
     */
    private void smartReceive(String in) {
        if (in != null && !in.isEmpty()) {
            if (License.parseRevoke(in, device)) {
                LicenseStore.revoke(this);
                kit.toast("لایسنس لغو شد؛ برای فعال‌سازی مجدد اقدام کنید");
                buildUi();
                return;
            }
            String br = License.parseBlock(in, device);
            if (br != null) {
                LicenseStore.setBlocked(this, br);
                buildUi();
                return;
            }
            if (License.parseUnblock(in, device)) {
                LicenseStore.clearBlocked(this);
                kit.toast("مسدودی برداشته شد؛ برنامه را فعال کنید");
                buildUi();
                return;
            }
        }
        if (in == null || in.isEmpty()) {
            // Imperceptible: pull the clipboard automatically, then process.
            String clip = clipText();
            if (clip != null && !clip.isEmpty()) {
                smartReceive(clip);
                return;
            }
            kit.toast("کلیدی پیدا نشد");
            return;
        }
        String pack = extractPackToken(in);
        String card = extractCardLine(in);
        if (pack == null && card == null) {
            // Let the proper parser explain what is wrong with this text.
            if (in.contains(License.DB_PREFIX) || in.contains(License.NET_PREFIX)) applyCard(in);
            else doActivate(in);
            return;
        }
        if (pack != null) doActivate(pack);
        if (card != null) applyCard(card);
    }

    /** First verifiable 39-char M1… pack token in the text (dashes tolerated). */
    private static String extractPackToken(String body) {
        try {
            for (String tok : body.split("\\s+")) {
                String n = License.normalize(tok);
                if (n.length() == License.PACK_LEN && n.startsWith("M1")) {
                    License.Result r = License.parse(n);
                    if (r.ok) return r.pack;
                }
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1516", ignored); }
        return null;
    }

    /** The connection line (MILANO-DB1 card or MILANO-NET1 mini line), or null. */
    private static String extractCardLine(String body) {
        try {
            for (String raw : body.split("\n")) {
                String line = raw.trim();
                if (line.startsWith(License.DB_PREFIX + "|")) return line;
                if (line.startsWith(License.NET_PREFIX + "|")) return line;
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1528", ignored); }
        return null;
    }

    private boolean recvGranted() {
        try {
            if (Build.VERSION.SDK_INT < 23) return true;
            return checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureRecvPerm() {
        try {
            if (!recvGranted()) {
                kit.toast("برای دریافت خودکار کلید، دسترسی را تأیید کنید");
                requestPermissions(new String[]{android.Manifest.permission.RECEIVE_SMS}, REQ_RECV);
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1548", ignored); }
    }

    private boolean readSmsGranted() {
        try {
            return Build.VERSION.SDK_INT < 23 || checkSelfPermission(android.Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean phoneGranted() {
        try {
            return Build.VERSION.SDK_INT < 26 || checkSelfPermission(android.Manifest.permission.READ_PHONE_NUMBERS)
                    == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Contact number of this phone (SIM line), canonical 09xxxxxxxxx — or ""
     * when the phone does not expose one. Read automatically during activation
     * so the customer never types it.
     */
    private String simPhone() {
        try {
            if (!phoneGranted()) return "";
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager)
                    getSystemService(TELEPHONY_SERVICE);
            if (tm == null) return "";
            String raw = null;
            try {
                raw = tm.getLine1Number();
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:line1", ignored); }
            String p = SmsIo.normalizeMobile(raw == null ? "" : raw);
            return p.length() >= 10 ? p : "";
        } catch (Exception e) {
            Diag.ignored("LicenseActivity:simPhone", e);
            return "";
        }
    }

    /** Saved contact number; when none, the number read automatically from the phone/SIM. */
    private String autoPhone() {
        String saved = LicenseStore.contactPhone(this);
        if (saved != null && !saved.trim().isEmpty()) return saved.trim();
        return simPhone();
    }

    /**
     * Automatic license recovery from the SMS inbox (reinstall case): the
     * signed pack is device-bound and carries its own dates, so the elapsed
     * time keeps counting. Silent when the permission is already there;
     * otherwise one system prompt explains and asks.
     */
    private void attemptRecovery(boolean fromGrant) {
        try {
            if (LicenseStore.check(this).ok) return;
            if (!readSmsGranted()) {
                if (!autoRecoveryAsked && !fromGrant) {
                    autoRecoveryAsked = true;
                    kit.toast("برای فعال‌سازی خودکار از پیامک‌های قبلی، دسترسی پیامک را تأیید کنید");
                    requestPermissions(new String[]{android.Manifest.permission.READ_SMS}, REQ_READ_SMS);
                }
                return;
            }
            new Thread(() -> {
                String pack = LicenseRecovery.tryRecover(LicenseActivity.this);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (!pack.isEmpty()) {
                        buzz();
                        kit.toast("✦ لایسنس به‌صورت خودکار فعال شد — خوش آمدید!");
                        buildUi();
                    } else if (fromGrant) {
                        kit.toast("پیامک فعال‌سازی قبلی پیدا نشد؛ درخواست را دوباره بفرستید");
                    }
                });
            }).start();
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:attemptRecovery", ignored); }
    }

    private void updateRecvHint() {
        if (recvHint == null) return;
        try {
            if (recvGranted()) {
                ir.meelano.manager.ui.MeelanoIcons.set(recvHint, "✦ دریافت خودکار فعال است — فقط منتظر بمانید.");
                recvHint.setTextColor(Theme.GOLD);
            } else {
                recvHint.setText("برای دریافت خودکار کلید، اجازه را تأیید کنید.");
                recvHint.setTextColor(Theme.MUTED);
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1561", ignored); }
    }

    private void doActivate(String in) {
        if (in.isEmpty()) {
            kit.toast("کلید را وارد کنید");
            return;
        }
        LicenseStore.Status st = LicenseStore.activate(this, in);
        if (st.ok) {
            buzz();
            kit.toast("✦ فعال شد — خوش آمدید!");
            celebrateThen(() -> buildUi());
        } else {
            kit.toast(st.fa);
        }
    }

    /** Manual retry: take a staged card/pack and try it right now. */





    // ---- SMS auto-apply ----

    /**
     * Apply whatever the seller's SMS staged (pack and/or connection card).
     * Called on every resume + instantly while open via the internal ping.
     */
    private void consumeSms() {
        try {
            LicenseStore.SmsPending s = LicenseStore.takePendingSms(this);
            boolean havePack = s.pack != null && !s.pack.isEmpty();
            boolean haveCard = s.card != null && !s.card.isEmpty();
            if (!havePack && !haveCard) return;
            if (havePack) {
                LicenseStore.Status st = LicenseStore.activate(this, s.pack);
                if (st.ok) {
                    buzz();
                    kit.toast("✦ فعال شد — خوش آمدید!");
                    celebrateThen(() -> buildUi());
                } else {
                    kit.toast(st.fa);
                }
            }
            if (haveCard) {
                long now = System.currentTimeMillis();
                if (!Net.online(this)) {
                    // No internet at all: keep it staged; the next onResume retries.
                    LicenseStore.addPendingSms(this, null, s.card);
                    if (now - lastCardTry > 60000) {
                        lastCardTry = now;
                        kit.toast("کارت اتصال رسید و ذخیره شد؛ با وصل شدن اینترنت خودکار ثبت می‌شود");
                    }
                } else if (now - lastCardTry > 10000) {
                    // Online: test it live (fails fast on LAN-only servers), throttled.
                    lastCardTry = now;
                    applyCard(s.card);
                } else {
                    LicenseStore.addPendingSms(this, null, s.card);
                }
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1624", ignored); }
    }

    // ---- automatic server connection ----

    private void applyCard(String pasted) {
        if (pasted == null || pasted.isEmpty()) {
            kit.toast("کارتی پیدا نشد");
            return;
        }
        License.DbProfile prof = License.parseAnyCard(pasted, device);
        if (prof == null) {
            kit.toast("کارت معتبر نیست؛ مطمئن شوید کارت همین گوشی را وارد کرده‌اید");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(prof.port.trim());
        } catch (Exception e) {
            kit.toast("کارت خراب است؛ از فروشنده کارت تازه بخواهید");
            return;
        }
        if (!Net.online(this)) {
            // A valid card but no internet at all — stage it and retry automatically.
            LicenseStore.addPendingSms(this, null, pasted);
            kit.toast("کارت معتبر است و ذخیره شد؛ با وصل شدن اینترنت خودکار ثبت می‌شود");
            return;
        }
        kit.toast("در حال ثبت و تست اتصال…");
        final String fh = prof.host;
        final String fd = prof.db;
        final String fu = prof.user;
        final String fp = prof.pass;
        repo.runWith(fh, port, fd, fu, fp, conn -> {
            Meta m = new Meta(conn);
            int n = 0;
            for (String t : new String[]{"sailfact", "buyfact", "dar", "getchk",
                    "putchk", "CUSTOMERS", "inventory", "visitors"})
                if (m.table(t)) n++;
            return "اتصال برقرار شد • " + Money.fa(String.valueOf(n)) + " جدول اصلی در دسترس";
        }, new Repo.Cb<String>() {
            @Override
            public void ok(String v) {
                try {
                    settings.placeHost(fh, String.valueOf(port), fd, fu, fp);
                } catch (Exception ignored) { Diag.ignored("LicenseActivity:1669", ignored); }
                kit.toast("✓ " + v);
                buildUi();
            }

            @Override
            public void fail(String faError) {
                // Server unreachable (often a LAN-only server while on mobile data):
                // keep the card staged — every resume + the retry button try again.
                LicenseStore.addPendingSms(LicenseActivity.this, null, pasted);
                kit.toast(faError == null || faError.isEmpty()
                        ? "سرور در دسترس نیست؛ اگر سرور فقط روی وای‌فای داخلی است به وای‌فای فروشگاه وصل شوید — کارت ذخیره شد و خودکار تلاش می‌شود"
                        : faError);
            }
        });
    }

    // ---- developer signature + support (always on page one) ----

    /** Frameless floating footer: a gold light-line, then floating signature. */
    private void buildDevFooter(LinearLayout box) {
        box.addView(kit.gap(16));
        View div = new View(this);
        try {
            android.graphics.drawable.GradientDrawable gd =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                            new int[]{0x00000000, Theme.shade(Theme.GOLD, -0.45f), 0x00000000});
            div.setBackground(gd);
        } catch (Exception ignored) {
            div.setBackgroundColor(Theme.GOLD);
        }
        box.addView(div, new LinearLayout.LayoutParams(-1, Theme.dp(2)));
        box.addView(kit.gap(8));
        TextView tag = kit.text(Brand.TAGLINE, 10.5f, Theme.MUTED, false);
        tag.setGravity(Gravity.CENTER);
        tag.setMaxLines(2);
        box.addView(tag, kit.lp(-1, -2));
        box.addView(kit.gap(4));
        box.addView(kit.btnGhost("پشتیبانی", Theme.GOLD, v -> supportDialog()), kit.lp(-1, -2));
        addCrashReportCard(box);
        TextView ver = kit.text(appVer(), 10.5f, Theme.MUTED, false);
        ver.setGravity(Gravity.CENTER);
        box.addView(ver, kit.lp(-1, -2));
        luxurySig(box);
    }

    /** Metallic-gold English designer signature with a soft 3D shadow. */
    private void luxurySig(LinearLayout c) {
        try {
            c.addView(kit.gap(4));
            TextView over = kit.text("D E S I G N E D   B Y", 8.5f, Theme.MUTED, true);
            over.setGravity(Gravity.CENTER);
            c.addView(over, kit.lp(-1, -2));
            final TextView name = kit.text("Milad Yaghoubi", 14.5f, Theme.GOLD_SOFT, true);
            name.setGravity(Gravity.CENTER);
            try {
                name.setTypeface(Theme.face(true));
                name.setLetterSpacing(0.06f);
                name.setShadowLayer(3, 0, 2, 0x40000000);
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:1739", ignored); }
            c.addView(name, kit.lp(-1, -2));
            name.post(() -> {
                try {
                    int h = name.getHeight();
                    if (h <= 0) h = Theme.dp(28);
                    int[] sigCols = Theme.isLight()
                            ? new int[]{Theme.shade(Theme.GOLD, -0.5f), Theme.shade(Theme.GOLD, -0.3f), Theme.shade(Theme.GOLD, -0.6f), Theme.shade(Theme.GOLD, -0.3f), Theme.shade(Theme.GOLD, -0.5f)}
                            : new int[]{Theme.shade(Theme.GOLD, 0.6f), Theme.GOLD, Theme.shade(Theme.GOLD, 0.2f), Theme.GOLD, Theme.shade(Theme.GOLD, 0.6f)};
                    name.getPaint().setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                            sigCols,
                            new float[]{0f, 0.35f, 0.55f, 0.75f, 1f},
                            android.graphics.Shader.TileMode.CLAMP));
                    name.invalidate();
                } catch (Exception ignored) { Diag.ignored("LicenseActivity:1753", ignored); }
            });
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1755", ignored); }
    }

    private void supportDialog() {
        LinearLayout body = kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(8));
        body.addView(kit.btnGhost(Brand.SITE_LABEL, Theme.GOLD,
                v -> Brand.openSite(this)), kit.lp(-1, -2));
        for (String ph : Brand.PHONES) {
            final String fph = ph;
            final android.widget.Button pb = kit.btnGhost(Money.fa(ph), Theme.GOLD,
                    v -> Brand.dial(this, fph));
            try {
                android.graphics.drawable.Drawable icd = getDrawable(R.drawable.mi_call);
                if (icd != null) {
                    icd = icd.mutate();
                    icd.setTint(Theme.GOLD);
                    pb.setCompoundDrawablesRelativeWithIntrinsicBounds(icd, null, null, null);
                    pb.setCompoundDrawablePadding(Theme.dp(6));
                }
                pb.setMinHeight(Theme.dp(48));
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:1776", ignored); }
            body.addView(pb, kit.lp(-1, -2));
        }
        final AlertDialog[] dlgH = new AlertDialog[1];
        body.addView(kit.gap(4));
        body.addView(kit.btn("بستن", v -> {
            if (dlgH[0] != null) dlgH[0].dismiss();
        }), kit.lp(-1, -2));
        dlgH[0] = kit.dialog("پشتیبانی میلانو", body, true);
        dlgH[0].show();
    }

    private String clipText() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                if (t != null) return t.toString().trim();
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1796", ignored); }
        return null;
    }

    /** Full-stop violation screen: no code, no activation, just the notice. */
    private void buildBlockedUi(String reason) {
        LinearLayout box = kit.v();
        box.setBackgroundColor(Theme.BG);
        int pad = Theme.dp(16);
        box.setPadding(pad, pad, pad, pad);
        Card3D.mount(box, Card3D.hero(this, kit, R.drawable.lic_shield, Theme.DANGER,
                "⛔ دسترسی مسدود شد",
                "اجرای برنامه متوقف شده است",
                "مسدود", Theme.DANGER));
        LinearLayout c = Card3D.card(this, Theme.DANGER);
        c.addView(kit.text("این گوشی توسط فروشنده مسدود شده است.", 14f, Theme.TEXT, true),
                kit.lp(-1, -2));
        c.addView(kit.gap(4));
        c.addView(kit.kv("علت", reason == null || reason.isEmpty() ? "تخلف از قوانین استفاده" : reason,
                Theme.DANGER), kit.lp(-1, -2));
        c.addView(kit.hint("برای رفع مسدودی با فروشنده تماس بگیرید. بدون رفع مسدودی، فعال‌سازی جدید ممکن نیست."),
                kit.lp(-1, -2));
        Card3D.mount(box, c);
        buildDevFooter(box);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(box);
        setContentView(sv);
    }

    // ---------- 3D wait: the seller is issuing your code ----------





    // ---------- step 2: the customer's own server ----------


    /** Prefer the winner matching the current transport for SITE1 + last-path. */


    /** Report the typed site back to the seller (full customer record). */

    // ---------- misc ----------

    private void enterApp() {
        try {
            if (!LicenseStore.enteredOnce(this)) {
                LicenseStore.setEnteredOnce(this);
                LicenseStore.setTourPending(this, true);
            }
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1848", ignored); }
        try {
            LinearLayout box = kit.v();
            box.setPadding(Theme.dp(28), Theme.dp(28), Theme.dp(28), Theme.dp(28));
            box.setGravity(Gravity.CENTER);
            android.widget.ImageView lv = kit.logo(84);
            LinearLayout.LayoutParams llp =
                    new LinearLayout.LayoutParams(Theme.dp(84), Theme.dp(84));
            llp.gravity = Gravity.CENTER;
            box.addView(lv, llp);
            box.addView(kit.gap(10));
            TextView t = kit.text("در حال چیدن میز کار شما…", 14f, Theme.GOLD_SOFT, true);
            t.setGravity(Gravity.CENTER);
            box.addView(t, kit.lp(-1, -2));
            final AlertDialog d = kit.dialog("میلانو", box, false);
            try {
                d.setCancelable(false);
                d.setCanceledOnTouchOutside(false);
            } catch (Exception ignored) { Diag.ignored("LicenseActivity:1866", ignored); }
            d.show();
            uiHandler.postDelayed(() -> {
                try {
                    d.dismiss();
                } catch (Exception ignored) { Diag.ignored("LicenseActivity:1871", ignored); }
                enterAppNow();
            }, 1300);
        } catch (Exception e) {
            enterAppNow();
        }
    }

    private void enterAppNow() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        } catch (Exception ignored) { Diag.ignored("LicenseActivity:1885", ignored); }
    }

    private static String txt(EditText e) {
        try {
            return e.getText().toString().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private String appVer() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return "نسخه " + Money.fa(v == null || v.isEmpty() ? "—" : v);
        } catch (Exception e) {
            return "نسخه —";
        }
    }

    private static String prettyDev(String code) {
        if (code == null || code.length() != 8) return code == null ? "" : code;
        return code.substring(0, 4) + "-" + code.substring(4);
    }

    private boolean copyText(String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return false;
            cm.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
