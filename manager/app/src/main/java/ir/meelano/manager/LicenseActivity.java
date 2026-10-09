package ir.meelano.manager;

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
import ir.meelano.manager.core.DeviceId;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LanDiscover;
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
import ir.meelano.manager.ui.ParticlesView;
import ir.meelano.manager.ui.RadarView;
import ir.meelano.manager.ui.Ui;
import ir.meelano.manager.ui.Theme;
import ir.meelano.licensing.License;

/**
 * Offline license gate (v31 steps): page 1 asks ONLY the name + contact
 * number, then a beautiful fixed waiting page holds until the license
 * lands; page 2 asks ONLY the server address + DB name and finds the
 * reachable SQL Server itself (typed host, transplanted subnet, gateway,
 * common octets — port 1433 hidden). Everything activates silently:
 * no mechanism talk, no notification. Remote revoke/block enforcement
 * stays, plus the luxury gold designer signature on every page.
 * Blocks MainActivity until {@link LicenseStore#unlocked}.
 */
public class LicenseActivity extends Activity {

    private static final int REQ_RECV = 906;

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
            } catch (Exception ignored) { }
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
            } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
        kit = new Kit(this);
        settings = new Settings(this);
        repo = new Repo(this, settings);
        try {
            device = DeviceId.code(this);
        } catch (Exception e) {
            device = "";
        }
        buildUi();
        ensureRecvPerm();
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
        } catch (Exception ignored) { }
        // A reply SMS may have landed while we were away — apply it instantly.
        consumeSms();
        try {
            if (dotsView != null) {
                uiHandler.removeCallbacks(dotsTick);
                uiHandler.post(dotsTick);
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onPause() {
        try {
            uiHandler.removeCallbacks(dotsTick);
        } catch (Exception ignored) { }
        try {
            unregisterReceiver(smsPing);
        } catch (Exception ignored) { }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopDots();
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
                    else shareRequest(LicenseStore.distName(this),
                            LicenseStore.contactPhone(this));
                }
            } else if (req == REQ_RECV) {
                updateRecvHint();
                buildUi();
            }
        } catch (Exception ignored) { }
    }

    // ---------- UI ----------

    private void buildUi() {
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
        else if (!conn) buildConnPage(box, s);
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
        wrap.addView(new ParticlesView(this),
                new android.widget.FrameLayout.LayoutParams(-1, -1));
        wrap.addView(sv, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(wrap);
        try {
            if (Ui.motionOk(this)) {
                sv.setAlpha(0f);
                sv.setTranslationY(Theme.dp(26));
                sv.animate().alpha(1f).translationY(0).setDuration(380).start();
            }
        } catch (Exception ignored) { }
    }

    private void stopDots() {
        try {
            uiHandler.removeCallbacks(dotsTick);
        } catch (Exception ignored) { }
        dotsView = null;
        waitLogo = null;
        waitTimer = null;
        waitCreative = null;
        glowBtn = null;
        sending = false;
        connRadar = null;
        connStage = null;
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
            TextView dot = kit.text(done ? "✓" : String.valueOf(idx), 14,
                    done || on ? 0xFF0B1220 : Theme.MUTED, true);
            dot.setGravity(Gravity.CENTER);
            dot.setBackground(Theme.avatar(done ? Theme.SUCCESS
                    : (on ? Theme.TEAL : Theme.SURFACE2)));
            int d = Theme.dp(34);
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
            } catch (Exception ignored) { }
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
                line.setBackgroundColor(idx < active ? Theme.SUCCESS : Theme.SURFACE2);
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

    // ================= page 1: name + contact, nothing else =================

    private void buildRequestPage(LinearLayout box) {
        box.addView(stepBar(1), kit.lp(-1, -2));
        box.addView(kit.gap(10));
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        c.addView(Card3D.stepRow(this, kit, "✦", Theme.GOLD, "خوش آمدید"), kit.lp(-1, -2));
        c.addView(kit.text("نام و شماره‌تان را بنویسید؛ بقیه‌اش با ما.",
                13f, Theme.TEXT, false), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        c.addView(deviceCodeView(), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        final TextView errName = errSlot();
        final TextView errPhone = errSlot();
        final EditText fName = kit.editLux("نام", LicenseStore.distName(this));
        try {
            fName.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        } catch (Exception ignored) { }
        fName.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence x, int a, int b, int cc) { }
            @Override
            public void onTextChanged(CharSequence x, int a, int b, int cc) { }
            @Override
            public void afterTextChanged(android.text.Editable x) {
                try {
                    if (x != null && x.toString().trim().length() > 0
                            && errName.getText().length() > 0) {
                        errName.setText("");
                        fName.setBackground(Theme.fieldBg());
                    }
                } catch (Exception ignored) { }
            }
        });
        c.addView(iconField(fName, R.drawable.mi_person), kit.lp(-1, -2));
        c.addView(errName, kit.lp(-1, -2));
        final EditText fPhone = kit.editLux("شماره تماس", LicenseStore.contactPhone(this));
        try {
            fPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            fPhone.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        } catch (Exception ignored) { }
        final TextView tick = kit.text("✓", 18, Theme.SUCCESS, true);
        try {
            tick.setVisibility(SmsIo.cleanPhone(LicenseStore.contactPhone(this)).length() == 11
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
                } catch (Exception ignored) { }
            }
        });
        LinearLayout prow = iconField(fPhone, R.drawable.mi_call);
        prow.addView(kit.space(6));
        prow.addView(tick, kit.lp(-2, -2));
        c.addView(prow, kit.lp(-1, -2));
        c.addView(errPhone, kit.lp(-1, -2));
        c.addView(kit.gap(4));
        final android.widget.Button startBtn = kit.btnGold("✦ شروع", null);
        try {
            startBtn.setBackground(Theme.startButton());
            startBtn.setTextSize(15f);
            startBtn.setMinHeight(Theme.dp(52));
        } catch (Exception ignored) { }
        glowBtn = startBtn;
        startBtn.setOnClickListener(v -> requestStart(txt(fName), txt(fPhone),
                fName, fPhone, errName, errPhone, startBtn));
        c.addView(startBtn, kit.lp(-1, -2));
        Card3D.mount(box, c);
        buildDevFooter(box);
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
        } catch (Exception ignored) { }
        return e;
    }

    /** Device code card: LTR-isolated code, one line, honest copy feedback (v34). */
    private LinearLayout deviceCodeView() {
        LinearLayout card = Card3D.card(this, Theme.TEAL);
        LinearLayout top = kit.h();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(kit.icon(R.drawable.mi_credit_card, Theme.GOLD_SOFT, 20), kit.lp(-2, -2));
        top.addView(kit.space(6));
        top.addView(kit.text("کد دستگاه", 12.5f, Theme.GOLD_SOFT, true), kit.wlp(1f));
        final android.widget.Button copyBtn = kit.btnGhost("کپی", Theme.TEAL, null);
        try {
            android.graphics.drawable.Drawable ic = getDrawable(R.drawable.mi_content_copy);
            if (ic != null) {
                ic = ic.mutate();
                ic.setTint(Theme.TEAL);
                copyBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(ic, null, null, null);
                copyBtn.setCompoundDrawablePadding(Theme.dp(6));
            }
            copyBtn.setMinHeight(Theme.dp(48));
            copyBtn.setContentDescription("کپی کد دستگاه");
        } catch (Exception ignored) { }
        copyBtn.setOnClickListener(v -> {
            if (copyText("کد دستگاه میلانو", device)) {
                kit.toast("کد دستگاه کپی شد");
            } else {
                kit.toast("کپی نشد؛ دوباره تلاش کنید");
            }
        });
        top.addView(copyBtn, kit.lp(-2, -2));
        card.addView(top, kit.lp(-1, -2));
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
        } catch (Exception ignored) { }
        dev.setGravity(Gravity.CENTER);
        try {
            dev.setContentDescription("کد دستگاه " + device);
        } catch (Exception ignored) { }
        card.addView(dev, kit.lp(-1, -2));
        return card;
    }

    private void requestStart(String dist, String phone, EditText fName, EditText fPhone,
                                TextView errName, TextView errPhone, android.widget.Button btn) {
        if (sending) return;
        boolean badName = dist == null || dist.trim().isEmpty();
        boolean badPhone = SmsIo.cleanPhone(phone).length() < 10;
        try {
            errName.setText(badName ? "نام را وارد کنید" : "");
            errPhone.setText(badPhone ? "شماره تماس معتبر وارد کنید (مثلاً 09123456789)" : "");
            fName.setBackground(badName ? Theme.fieldBgError() : Theme.fieldBg());
            fPhone.setBackground(badPhone ? Theme.fieldBgError() : Theme.fieldBg());
        } catch (Exception ignored) { }
        if (badName || badPhone) {
            try {
                if (badName) {
                    fName.requestFocus();
                    Ui.announce(errName, "نام را وارد کنید");
                } else {
                    fPhone.requestFocus();
                    Ui.announce(errPhone, "شماره تماس معتبر وارد کنید");
                }
            } catch (Exception ignored) { }
            return;
        }
        sending = true;
        try {
            btn.setEnabled(false);
            btn.setText("در حال ارسال…");
        } catch (Exception ignored) { }
        LicenseStore.setDistName(this, dist);
        LicenseStore.setContactPhone(this, phone);
        LicenseStore.setRequested(this, true);
        directSend(buildRequestMsg(dist, phone));
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
                shareRequest(LicenseStore.distName(LicenseActivity.this),
                        LicenseStore.contactPhone(LicenseActivity.this));
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

    private void shareRequest(String name, String phone) {
        String msg = buildRequestMsg(name, phone);
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
        LinearLayout c = Card3D.card(this, Theme.TEAL);
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
        } catch (Exception ignored) { }
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
            c.addView(kit.btnGhost("فعال‌سازی دریافت خودکار", Theme.TEAL,
                    v -> ensureRecvPerm()), kit.lp(-1, -2));
        }
        c.addView(kit.gap(6));
        c.addView(deviceCodeView(), kit.lp(-1, -2));
        c.addView(kit.gap(6));
        LinearLayout row = kit.h();
        row.addView(kit.btnGhost("↻ ارسال مجدد", Theme.MUTED, v -> directSend(buildRequestMsg(
                LicenseStore.distName(this), LicenseStore.contactPhone(this)))), kit.wlp(1f));
        row.addView(kit.space(8));
        final android.widget.Button[] recall = new android.widget.Button[1];
        recall[0] = kit.btnGold("✦ دریافت کلید", v -> recallFlow(recall[0]));
        row.addView(recall[0], kit.wlp(1f));
        c.addView(row, kit.lp(-1, -2));
        Card3D.mount(box, c);
        buildDevFooter(box);
        startDots();
    }

    private void startDots() {
        stopDots();
        dotsN = 0;
        uiHandler.post(dotsTick);
    }


    // ================= page 2: server + DB, the app finds the rest =================

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
            } catch (Exception ignored) { }
            return;
        }
        try {
            android.widget.FrameLayout root = new android.widget.FrameLayout(this);
            root.setBackgroundColor(0xCC0B0E12);
            ConfettiView cf = new ConfettiView(this);
            root.addView(cf, new android.widget.FrameLayout.LayoutParams(-1, -1));
            TextView big = kit.text("✓", 90, Theme.SUCCESS, true);
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
                } catch (Exception ignored) { }
                try {
                    after.run();
                } catch (Exception ignored) { }
            }, 2100);
        } catch (Exception e) {
            try {
                after.run();
            } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
    }

    private void recallFlow(final android.widget.Button btn) {
        if (!recvGranted()) {
            ensureRecvPerm();
            return;
        }
        try {
            btn.setEnabled(false);
            btn.setText("در حال فعال‌سازی…");
        } catch (Exception ignored) { }
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
            } catch (Exception ignored) { }
            try {
                btn.setEnabled(true);
                btn.setText("✦ دریافت کلید");
            } catch (Exception ignored2) { }
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
        } catch (Exception ignored) { }
        try {
            consumeSms();
        } catch (Exception ignored) { }
        updateRecvHint();
        boolean moved = false;
        try {
            moved = LicenseStore.check(this).ok != licBefore
                    || connConfigured() != connBefore;
        } catch (Exception ignored) { }
        if (moved) {
            buildUi();
            return;
        }
        try {
            kit.toast(Net.online(this) ? "✓ بررسی شد"
                    : "اینترنت قطع است — وصل شوید و دوباره بزنید");
        } catch (Exception ignored) { }
    }

    /** Circular gold refresh icon for the card headers. */
    private TextView refreshIconView() {
        TextView v = kit.text("↻", 22, Theme.GOLD_SOFT, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Theme.ghostButton(Theme.GOLD));
        int p = Theme.dp(8);
        v.setPadding(p, Theme.dp(2), p, Theme.dp(2));
        try {
            v.setMinWidth(Theme.dp(44));
            v.setMinHeight(Theme.dp(44));
        } catch (Exception ignored) { }
        Theme.pressable(v);
        v.setOnClickListener(vv -> refreshFlow());
        return v;
    }

    private void buildConnPage(LinearLayout box, LicenseStore.Status s) {
        box.addView(stepBar(2), kit.lp(-1, -2));
        box.addView(kit.gap(10));
        LinearLayout lc = Card3D.card(this, Theme.SUCCESS);
        lc.addView(Card3D.stepRow(this, kit, "✓", Theme.SUCCESS, "لایسنس"), kit.lp(-1, -2));
        lc.addView(kit.kv("وضعیت", "✓ فعال", Theme.SUCCESS), kit.lp(-1, -2));
        lc.addView(kit.kv("طرح", "«" + License.planFa(s.plan) + "»", Theme.GOLD_SOFT),
                kit.lp(-1, -2));
        if (s.plan == License.P_PERM) {
            lc.addView(kit.kv("اعتبار", "دائمی ♾", Theme.SUCCESS), kit.lp(-1, -2));
        } else {
            lc.addView(kit.kv("اعتبار", Money.fa(String.valueOf(s.daysLeft)) + " روز",
                    leftColor(s.daysLeft)), kit.lp(-1, -2));
        }
        Card3D.mount(box, lc);
        LinearLayout c = Card3D.card(this, Theme.GOLD);
        LinearLayout chead = kit.h();
        chead.setGravity(Gravity.CENTER_VERTICAL);
        chead.addView(Card3D.stepRow(this, kit, "✦", Theme.GOLD, "اتصال به فروشگاه"), kit.wlp(1f));
        chead.addView(refreshIconView(), kit.lp(-2, -2));
        c.addView(chead, kit.lp(-1, -2));
        c.addView(kit.text("کلید رسید ✦ حالا آدرس فروشگاه را بنویسید.",
                13f, Theme.TEXT, false), kit.lp(-1, -2));
        connRadar = new RadarView(this);
        LinearLayout.LayoutParams rlp =
                new LinearLayout.LayoutParams(Theme.dp(110), Theme.dp(110));
        rlp.gravity = Gravity.CENTER;
        connRadar.setVisibility(View.GONE);
        c.addView(connRadar, rlp);
        connStage = kit.text("", 12.5f, Theme.TEAL, true);
        connStage.setGravity(Gravity.CENTER);
        connStage.setVisibility(View.GONE);
        c.addView(connStage, kit.lp(-1, -2));
        c.addView(kit.gap(6));
        final EditText fIp = kit.edit("آدرس سرور", "");
        try {
            fIp.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        } catch (Exception ignored) { }
        c.addView(iconField(fIp, R.drawable.mi_my_location), kit.lp(-1, -2));
        final EditText fDb = kit.edit("نام دیتابیس", "");
        c.addView(iconField(fDb, R.drawable.mi_database), kit.lp(-1, -2));
        c.addView(kit.gap(4));
        final android.widget.Button[] btn = new android.widget.Button[1];
        btn[0] = kit.btnGold("✦ اتصال", v -> connectSmart(txt(fIp), txt(fDb), btn[0]));
        c.addView(btn[0], kit.lp(-1, -2));
        c.addView(kit.gap(6));
        final android.widget.Button[] wbtn = new android.widget.Button[1];
        wbtn[0] = kit.btnGhost("اتصال با وای‌فای", Theme.TEAL, v -> wifiConnect(wbtn[0]));
        try {
            android.graphics.drawable.Drawable icd = getDrawable(R.drawable.mi_wifi);
            if (icd != null) {
                icd = icd.mutate();
                icd.setTint(Theme.TEAL);
                wbtn[0].setCompoundDrawablesRelativeWithIntrinsicBounds(icd, null, null, null);
                wbtn[0].setCompoundDrawablePadding(Theme.dp(6));
            }
            wbtn[0].setMinHeight(Theme.dp(48));
        } catch (Exception ignored) { }
        c.addView(wbtn[0], kit.lp(-1, -2));
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
            } catch (Exception ignored) { }
        });
    }

    private void stage(String t) {
        runOnUiThread(() -> {
            try {
                if (connStage != null) connStage.setText(t);
            } catch (Exception ignored) { }
        });
    }

    private void hideRadar() {
        runOnUiThread(() -> {
            try {
                if (connRadar != null) connRadar.setVisibility(View.GONE);
                if (connStage != null) connStage.setVisibility(View.GONE);
            } catch (Exception ignored) { }
        });
    }

    /**
     * One address in, a working SQL Server out: the typed host first, then
     * the phone's own subnet (transplanted octet, gateway, common octets).
     * Login probes run in parallel — the first winner is saved as lan/wan
     * by its nature. Port 1433 is never shown.
     */
    private void connectSmart(String ip, String db, final android.widget.Button btn) {
        final String fIp = LanDiscover.clean(ip);
        final String fDb = db == null ? "" : db.trim();
        if (fIp.isEmpty()) {
            kit.toast("آدرس سرور را وارد کنید");
            return;
        }
        if (fDb.isEmpty()) {
            kit.toast("نام دیتابیس را وارد کنید");
            return;
        }
        if (!Net.online(this)) {
            kit.toast("اینترنت قطع است — وصل شوید و دوباره بزنید");
            return;
        }
        try {
            btn.setEnabled(false);
            btn.setText("در حال یافتن سرور…");
        } catch (Exception ignored) { }
        showRadar("در حال بررسی آدرس شما…");
        new Thread(() -> {
            List<String> cands = LanDiscover.find(fIp, LanDiscover.PORT);
            if (cands.isEmpty()) stage("در حال گشتن در شبکه فروشگاه…");
            else stage(Money.fa(String.valueOf(cands.size())) + " مسیر پیدا شد؛ در حال ورود…");
            String winner = null;
            int tables = -1;
            if (!cands.isEmpty()) {
                ExecutorService pool =
                        Executors.newFixedThreadPool(Math.min(6, cands.size()));
                try {
                    List<Future<int[]>> fs = new ArrayList<>();
                    for (final String h : cands) {
                        fs.add(pool.submit(() -> {
                            try {
                                int n = SmartLink.probeTables(h, LanDiscover.PORT, fDb,
                                        License.SQL_USER, License.SQL_PASS);
                                return new int[]{n};
                            } catch (Exception e) {
                                return new int[]{-1};
                            }
                        }));
                    }
                    long deadline = System.currentTimeMillis() + 20000;
                    for (int i = 0; i < fs.size(); i++) {
                        long left = deadline - System.currentTimeMillis();
                        if (left <= 0) break;
                        try {
                            int[] r = fs.get(i).get(left, TimeUnit.MILLISECONDS);
                            if (r != null && r[0] >= 0) {
                                winner = cands.get(i);
                                tables = r[0];
                                break;
                            }
                        } catch (Exception ignored) { }
                    }
                } finally {
                    pool.shutdownNow();
                }
            }
            final String win = winner;
            final int winN = tables;
            runOnUiThread(() -> {
                try {
                    btn.setEnabled(true);
                    btn.setText("✦ اتصال");
                } catch (Exception ignored) { }
                if (isFinishing()) return;
                hideRadar();
                if (win == null) {
                    boolean vpn = NetRoute.isVpnActive(LicenseActivity.this);
                    kit.toast("سرور پیدا نشد");
                    SmartLink.diagnoseExplicit(LicenseActivity.this, "", fIp,
                            LanDiscover.PORT, fDb,
                            License.SQL_USER, License.SQL_PASS, vpn, r -> {
                                if (isFinishing()) return;
                                LinkPanel.showFixSheet(LicenseActivity.this, r,
                                        () -> connectSmart(fIp, fDb, btn),
                                        () -> LinkPanel.showDiagnoseExplicit(
                                                LicenseActivity.this, "", fIp,
                                                LanDiscover.PORT, fDb,
                                                License.SQL_USER, License.SQL_PASS));
                            });
                    return;
                }
                try {
                    Boolean priv = SmartLink.isPrivate(win);
                    boolean wifi = Net.wifi(LicenseActivity.this);
                    String lan = (priv == null || priv) ? win : "";
                    String wan = (priv != null && !priv) ? win : "";
                    if (!lan.isEmpty() && !win.equals(fIp)) {
                        Boolean tp = SmartLink.isPrivate(fIp);
                        if (tp != null && !tp) wan = fIp;
                    }
                    settings.saveSmart(lan, wan, String.valueOf(LanDiscover.PORT), fDb,
                            License.SQL_USER, License.SQL_PASS);
                    settings.setLinkLast(!lan.isEmpty() && (wifi || wan.isEmpty())
                            ? SmartLink.LAN : SmartLink.WAN);
                } catch (Exception ignored) { }
                saveConnSummary(win, winN);
                buzz();
                kit.toast("✓ وصل شد • " + Money.fa(String.valueOf(winN)) + " جدول در دسترس");
                copySiteLine(win, LanDiscover.PORT, fDb);
                buildUi();
            });
        }).start();
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
        } catch (Exception ignored) { }
        showRadar("در حال یافتن سرور در وای‌فای…");
        new Thread(() -> {
            List<String> hosts = LanDiscover.find("", LanDiscover.PORT);
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
                for (final String h : hosts) {
                    fs.add(pool.submit(() -> LanDiscover.listDatabases(h, LanDiscover.PORT,
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
                                pairs.add(new String[]{hosts.get(i), d});
                            }
                        }
                    } catch (Exception ignored) { }
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
                            return new int[]{SmartLink.probeTables(pr[0], LanDiscover.PORT, pr[1],
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
                    } catch (Exception ignored) { }
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
                        saveSite(fw.get(0)[0], fw.get(0)[1], fn.get(0));
                        return;
                    }
                    dbPickerDialog(fw, fn);
                });
            } finally {
                pool.shutdownNow();
            }
        }).start();
    }

    private void restoreWifiBtn(android.widget.Button btn) {
        try {
            btn.setEnabled(true);
            btn.setText("اتصال با وای‌فای");
        } catch (Exception ignored) { }
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
            tx.addView(kit.text("✓ " + Money.fa(String.valueOf(counts.get(i))) + " جدول آتیران",
                    11.5f, Theme.SUCCESS, false), kit.lp(-1, -2));
            r.addView(tx, kit.wlp(1f));
            Theme.pressable(r);
            r.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8));
            r.setOnClickListener(v -> {
                sel[0] = idx;
                for (int k = 0; k < marks.size(); k++)
                    marks.get(k).setText(k == idx ? "◉" : "○");
            });
            rows.addView(r, kit.lp(-1, -2));
        }
        b.addView(rows, kit.lp(-1, -2));
        b.addView(kit.gap(6));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(kit.btnGold("✦ اتصال", v -> {
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            saveSite(pairs.get(sel[0])[0], pairs.get(sel[0])[1], counts.get(sel[0]));
        }), kit.lp(-1, -2));
        box[0] = kit.dialog("انتخاب دیتابیس", kit.scrollWrap(b, 420), true);
        box[0].show();
    }

    /** Save a verified (host, db) and roll into the app (v33). */
    private void saveSite(String host, String db, int tables) {
        try {
            Boolean priv = SmartLink.isPrivate(host);
            String lan = (priv == null || priv) ? host : "";
            String wan = (priv != null && !priv) ? host : "";
            settings.saveSmart(lan, wan, String.valueOf(LanDiscover.PORT), db,
                    License.SQL_USER, License.SQL_PASS);
            settings.setLinkLast(lan.isEmpty() ? SmartLink.WAN : SmartLink.LAN);
            saveConnSummary(host, tables);
        } catch (Exception ignored) { }
        buzz();
        kit.toast("✓ وصل شد • " + Money.fa(String.valueOf(tables)) + " جدول در دسترس");
        copySiteLine(host, LanDiscover.PORT, db);
        buildUi();
    }

    private void saveConnSummary(String host, int tables) {
        try {
            getSharedPreferences("meelano_license_ui", MODE_PRIVATE).edit()
                    .putString("conn_summary", (host == null ? "" : host) + "|" + tables).apply();
        } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
    }

    // ================= done =================

    private void buildDonePage(LinearLayout box, LicenseStore.Status s) {
        box.addView(stepBar(3), kit.lp(-1, -2));
        box.addView(kit.gap(10));
        LinearLayout c = Card3D.card(this, Theme.SUCCESS);
        c.addView(Card3D.stepRow(this, kit, "✓", Theme.SUCCESS, "همه‌چیز آماده است"),
                kit.lp(-1, -2));
        c.addView(kit.kv("لایسنس", "✓ فعال • «" + License.planFa(s.plan) + "»", Theme.SUCCESS),
                kit.lp(-1, -2));
        if (s.plan == License.P_PERM) {
            c.addView(kit.kv("اعتبار", "دائمی ♾", Theme.SUCCESS), kit.lp(-1, -2));
        } else {
            c.addView(kit.kv("زمان باقی‌مانده",
                    Money.fa(String.valueOf(s.daysLeft)) + " روز", leftColor(s.daysLeft)),
                    kit.lp(-1, -2));
        }
        c.addView(kit.kv("اتصال به سرور", "✓ تنظیم شده", Theme.SUCCESS), kit.lp(-1, -2));
        String sh = connSummaryHost();
        String stn = connSummaryTables();
        if (!sh.isEmpty()) {
            String masked = sh;
            try {
                masked = SmartLink.maskHost(sh);
            } catch (Exception ignored) { }
            c.addView(kit.kv("سرور", "✓ " + masked, Theme.GOLD_SOFT), kit.lp(-1, -2));
        }
        if (!stn.isEmpty())
            c.addView(kit.kv("جدول‌های آتیران", Money.fa(stn), Theme.TEXT), kit.lp(-1, -2));
        c.addView(kit.gap(4));
        c.addView(kit.btnGold("ورود به برنامه ⇤", v -> enterApp()), kit.lp(-1, -2));
        Card3D.mount(box, c);
        buildDevFooter(box);
    }

    private boolean connConfigured() {
        try {
            return settings.connConfigured();
        } catch (Exception e) {
            return false;
        }
    }

    // ---- status ----


    private static int leftColor(long daysLeft) {
        if (daysLeft <= 7) return Theme.DANGER;
        if (daysLeft <= 30) return Theme.WARNING;
        return Theme.SUCCESS;
    }

    // ---- network row (lives inside the master frame) ----


    // ---- the one luxury frame: request + receive + network, all in one ----


    private String buildRequestMsg(String dist, String phone) {
        String line = License.requestLine(device, dist, "", dist, phone, "");
        String use = Usage.reportLine(this, device);
        StringBuilder b = new StringBuilder("درخواست فعال‌سازی میلانو\n").append(line);
        if (!use.isEmpty()) b.append('\n').append(use);
        b.append("\nنام: ").append(dist.trim());
        b.append("\nتماس: ").append(phone.trim());
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
        } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
    }

    private void updateRecvHint() {
        if (recvHint == null) return;
        try {
            if (recvGranted()) {
                recvHint.setText("✦ دریافت خودکار فعال است — فقط منتظر بمانید.");
                recvHint.setTextColor(Theme.TEAL);
            } else {
                recvHint.setText("برای دریافت خودکار کلید، اجازه را تأیید کنید.");
                recvHint.setTextColor(Theme.MUTED);
            }
        } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
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
                } catch (Exception ignored) { }
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
                            new int[]{0x00000000, 0xFF8A6420, 0x00000000});
            div.setBackground(gd);
        } catch (Exception ignored) {
            div.setBackgroundColor(Theme.GOLD);
        }
        box.addView(div, new LinearLayout.LayoutParams(-1, Theme.dp(2)));
        box.addView(kit.gap(8));
        TextView tag = kit.text(Brand.TAGLINE, 11.5f, Theme.MUTED, false);
        tag.setGravity(Gravity.CENTER);
        box.addView(tag, kit.lp(-1, -2));
        box.addView(kit.gap(4));
        if (Ui.widthDp(this) < 360) {
            box.addView(kit.btnGhost(Brand.SITE_LABEL, Theme.GOLD,
                    v -> Brand.openSite(this)), kit.lp(-1, -2));
            box.addView(kit.gap(6));
            box.addView(kit.btnGhost("پشتیبانی", Theme.TEAL, v -> supportDialog()), kit.lp(-1, -2));
        } else {
            LinearLayout row = kit.h();
            row.addView(kit.btnGhost(Brand.SITE_LABEL, Theme.GOLD,
                    v -> Brand.openSite(this)), kit.wlp(1f));
            row.addView(kit.space(8));
            row.addView(kit.btnGhost("پشتیبانی", Theme.TEAL, v -> supportDialog()), kit.wlp(1f));
            box.addView(row, kit.lp(-1, -2));
        }
        TextView ver = kit.text(appVer(), 11f, Theme.MUTED, false);
        ver.setGravity(Gravity.CENTER);
        box.addView(ver, kit.lp(-1, -2));
        luxurySig(box);
    }

    /** Metallic-gold English designer signature with a soft 3D shadow. */
    private void luxurySig(LinearLayout c) {
        try {
            c.addView(kit.gap(6));
            TextView over = kit.text("D E S I G N E D   B Y", 10f, Theme.MUTED, true);
            over.setGravity(Gravity.CENTER);
            c.addView(over, kit.lp(-1, -2));
            final TextView name = kit.text("Milad Yaghoubi", 20, Theme.GOLD_SOFT, true);
            name.setGravity(Gravity.CENTER);
            try {
                name.setTypeface(Theme.face(true));
                name.setLetterSpacing(0.06f);
                name.setShadowLayer(3, 0, 2, 0x40000000);
            } catch (Exception ignored) { }
            c.addView(name, kit.lp(-1, -2));
            name.post(() -> {
                try {
                    int h = name.getHeight();
                    if (h <= 0) h = Theme.dp(28);
                    int[] sigCols = Theme.isLight()
                            ? new int[]{0xFF7A5410, 0xFFA86F14, 0xFF5C3F0C, 0xFFA86F14, 0xFF7A5410}
                            : new int[]{0xFFF3E3B8, 0xFFE9C37C, 0xFFB08A3E, 0xFFE9C37C, 0xFFF3E3B8};
                    name.getPaint().setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                            sigCols,
                            new float[]{0f, 0.35f, 0.55f, 0.75f, 1f},
                            android.graphics.Shader.TileMode.CLAMP));
                    name.invalidate();
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    private void supportDialog() {
        LinearLayout body = kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(8));
        body.addView(kit.btnGhost(Brand.SITE_LABEL, Theme.GOLD,
                v -> Brand.openSite(this)), kit.lp(-1, -2));
        for (String ph : Brand.PHONES) {
            final String fph = ph;
            final android.widget.Button pb = kit.btnGhost(Money.fa(ph), Theme.TEAL,
                    v -> Brand.dial(this, fph));
            try {
                android.graphics.drawable.Drawable icd = getDrawable(R.drawable.mi_call);
                if (icd != null) {
                    icd = icd.mutate();
                    icd.setTint(Theme.TEAL);
                    pb.setCompoundDrawablesRelativeWithIntrinsicBounds(icd, null, null, null);
                    pb.setCompoundDrawablePadding(Theme.dp(6));
                }
                pb.setMinHeight(Theme.dp(48));
            } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
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
            } catch (Exception ignored) { }
            d.show();
            uiHandler.postDelayed(() -> {
                try {
                    d.dismiss();
                } catch (Exception ignored) { }
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
        } catch (Exception ignored) { }
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
