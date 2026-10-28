package ir.meelano.manager;

import ir.meelano.manager.core.Diag;
import ir.meelano.manager.core.Finger;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.sql.Connection;

import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.core.SmartLink;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

/**
 * Manager-app account gate, shown only after a valid license and an explicitly
 * selected server/database. Every role, including manager and warehouse, is
 * authenticated against Atiran's own user table; synthetic app-local IDs are
 * never accepted as a substitute for an Atiran login.
 */
public class LoginActivity extends Activity {

    private Kit kit;
    private Settings settings;
    private EditText eUser;
    private EditText ePass;
    private TextView err;
    private android.widget.Button loginBtn;
    private TextView rememberV;
    private boolean remember = true;
    private boolean busy;
    private int hintUid = -1;
    private boolean fpReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.init(this);
        try {
            getWindow().setStatusBarColor(Theme.BG);
            Theme.lightBars(getWindow());
            getWindow().setNavigationBarColor(Theme.BG);
        } catch (Exception ignored) { Diag.ignored("LoginActivity:50", ignored); }
        kit = new Kit(this);
        settings = new Settings(this);
        if (!LicenseStore.unlocked(this) || !settings.connConfigured()) {
            toLicenseSetup();
            return;
        }

        ScrollView sv = new ScrollView(this);
        // Backdrop: a warm gold light falling from the top onto the graphite/ivory base.
        sv.setBackground(Theme.backdrop());
        LinearLayout root = kit.v();
        root.setGravity(Gravity.CENTER);
        root.setPadding(Theme.dp(26), Theme.dp(44), Theme.dp(26), Theme.dp(30));
        sv.addView(root);

        // Logo inside a soft gold halo.
        android.widget.FrameLayout halo = new android.widget.FrameLayout(this);
        android.graphics.drawable.GradientDrawable glow = new android.graphics.drawable.GradientDrawable();
        glow.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        // Nut theme: a warm gold halo (GOLD is emerald there, which read as grey mud on ivory).
        int haloRgb = Theme.isNuts() ? 0xFFE2BE5E : Theme.GOLD;
        glow.setColors(new int[]{Theme.alpha(haloRgb, Theme.isNuts() ? 110 : 90), Theme.alpha(haloRgb, 0)});
        glow.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(Theme.dp(96));
        halo.setBackground(glow);
        android.widget.FrameLayout.LayoutParams hp = new android.widget.FrameLayout.LayoutParams(
                Theme.dp(92), Theme.dp(92), Gravity.CENTER);
        halo.addView(kit.logo(92), hp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(190), Theme.dp(170));
        lp.gravity = Gravity.CENTER;
        root.addView(halo, lp);
        TextView t = kit.text("ورود به میلانو", 26, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        try {
            t.setLetterSpacing(0.04f);
            t.post(() -> {
                int h = t.getHeight();
                if (h <= 0) return;
                int[] cols = Theme.isLight()
                        ? new int[]{Theme.shade(Theme.GOLD, -0.4f), Theme.GOLD, Theme.shade(Theme.GOLD, -0.5f)}
                        : new int[]{0xFFFFFFFF, Theme.GOLD, 0xFFA9B0BC};
                t.getPaint().setShader(new android.graphics.LinearGradient(0, 0, 0, h, cols,
                        null, android.graphics.Shader.TileMode.CLAMP));
                t.invalidate();
            });
        } catch (Exception ignored) { Diag.ignored("LoginActivity:metal", ignored); }
        root.addView(t, kit.lp(-1, -2));
        View hair = new View(this);
        hair.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, Theme.alpha(Theme.GOLD, 150), 0x00000000}));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Theme.dp(120), Theme.dp(1));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.setMargins(0, Theme.dp(8), 0, 0);
        root.addView(hair, hlp);
        String[] hint = lastHint();
        hintUid = hint == null ? -1 : hintPrefs().getInt("uid", -1);
        LinearLayout.LayoutParams sp = kit.lp(-1, -2);
        sp.setMargins(0, Theme.dp(6), 0, Theme.dp(16));
        if (hint == null) {
            TextView s = kit.text("پس از انتخاب دیتابیس، با حساب مدیریت یا انبارداری وارد شوید",
                    13.5f, Theme.TEXT, false);
            s.setGravity(Gravity.CENTER);
            root.addView(s, sp);
        } else {
            root.addView(returningCard(hint[1], hint[2]), sp);
        }

        // Returning user on this device: one primary action (fingerprint) when it is set up,
        // otherwise the password form with the last username already filled in.
        fpReady = hintUid >= 0 && settings.fpOn() && Finger.supported(this)
                && AtiranAuth.cachedPw(this, hintUid) != null;
        if (fpReady) {
            android.widget.Button fp = kit.btnGold("◉  ورود با اثر انگشت", v -> quickFinger());
            try {
                fp.setBackground(Theme.startButton());
                fp.setMinHeight(Theme.dp(56));
                fp.setLetterSpacing(0.04f);
            } catch (Exception ignored) { Diag.ignored("LoginActivity:fp", ignored); }
            LinearLayout.LayoutParams fpl = kit.lp(-1, -2);
            fpl.setMargins(0, 0, 0, Theme.dp(14));
            root.addView(fp, fpl);
        }
        LinearLayout card = kit.card(Theme.GOLD);
        card.setBackground(Theme.hero(Theme.GOLD));
        card.setPadding(Theme.dp(22), Theme.dp(22), Theme.dp(22), Theme.dp(18));
        eUser = kit.edit("نام کاربری", hint == null ? "" : hint[0]);
        try {
            eUser.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        } catch (Exception ignored) { Diag.ignored("LoginActivity:82", ignored); }
        eUser.setMinHeight(Theme.dp(56));
        eUser.setCompoundDrawablePadding(Theme.dp(10));
        eUser.setCompoundDrawablesRelativeWithIntrinsicBounds(glyph(R.drawable.mi_person), null, null, null);
        card.addView(eUser, kit.lp(-1, -2));
        card.addView(kit.gap(10));
        // Password row: lock glyph at the start, show/hide toggle at the end, Go on the keyboard logs in.
        LinearLayout passRow = kit.h();
        passRow.setGravity(Gravity.CENTER_VERTICAL);
        ePass = kit.edit("رمز عبور", "", true);
        ePass.setMinHeight(Theme.dp(56));
        ePass.setCompoundDrawablePadding(Theme.dp(10));
        ePass.setCompoundDrawablesRelativeWithIntrinsicBounds(glyph(R.drawable.mi_lock), null, null, null);
        ePass.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        ePass.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                doLogin();
                return true;
            }
            return false;
        });
        passRow.addView(ePass, kit.wlp(1f));
        final android.widget.ImageView eye = new android.widget.ImageView(this);
        eye.setImageDrawable(glyph(R.drawable.mi_visibility));
        eye.setContentDescription("نمایش رمز عبور");
        android.widget.FrameLayout eyeBox = new android.widget.FrameLayout(this);
        eyeBox.setBackground(Theme.ghostButton(Theme.GOLD));
        eyeBox.addView(eye, new android.widget.FrameLayout.LayoutParams(Theme.dp(22), Theme.dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams eyeLp = new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(48));
        eyeLp.setMargins(Theme.dp(8), 0, 0, 0);
        Theme.pressable(eyeBox);
        final boolean[] shown = {false};
        eyeBox.setOnClickListener(v -> {
            shown[0] = !shown[0];
            ePass.setTransformationMethod(shown[0] ? null
                    : android.text.method.PasswordTransformationMethod.getInstance());
            eye.setImageDrawable(glyph(shown[0] ? R.drawable.mi_visibility_off : R.drawable.mi_visibility));
            ePass.setSelection(ePass.getText().length());
        });
        passRow.addView(eyeBox, eyeLp);
        card.addView(passRow, kit.lp(-1, -2));
        card.addView(kit.gap(6));
        rememberV = kit.text("", 12.5f, Theme.GOLD_SOFT, true);
        rememberV.setGravity(Gravity.CENTER);
        Theme.pressable(rememberV);
        rememberV.setOnClickListener(v -> {
            remember = !remember;
            paintRemember();
        });
        card.addView(rememberV, kit.lp(-1, -2));
        paintRemember();
        kit.addCard(root, card);

        err = kit.text("", 12.5f, Theme.DANGER, true);
        err.setGravity(Gravity.CENTER);
        err.setVisibility(View.GONE);
        root.addView(err, kit.lp(-1, -2));
        root.addView(kit.gap(12));

        loginBtn = kit.btnGold("ورود  ✦", v -> doLogin());
        try {
            loginBtn.setBackground(Theme.startButton());
            loginBtn.setTextSize(16f * Theme.fontScale());
            loginBtn.setMinHeight(Theme.dp(56));
            loginBtn.setLetterSpacing(0.05f);
        } catch (Exception ignored) { Diag.ignored("LoginActivity:btn", ignored); }
        root.addView(loginBtn, kit.lp(-1, -2));
        root.addView(kit.gap(10));
        root.addView(kit.hint("با نام کاربری و رمز آتیران وارد شوید؛ محیط متناسب با نقش فعال حساب شما باز می‌شود."),
                kit.lp(-1, -2));
        root.addView(kit.gap(6));
        TextView verLine = kit.text("میلانو • " + appVer(), 10.5f, Theme.MUTED, false);
        verLine.setGravity(Gravity.CENTER);
        root.addView(verLine, kit.lp(-1, -2));

        if (fpReady && savedInstanceState == null) root.postDelayed(this::quickFinger, 600);
        if (getResources().getConfiguration().smallestScreenWidthDp >= 600
                || getResources().getDisplayMetrics().widthPixels > Theme.dp(720)) {
            android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
            frame.setBackgroundColor(Theme.BG);
            android.widget.FrameLayout.LayoutParams fp = new android.widget.FrameLayout.LayoutParams(
                    Math.min(Theme.dp(480), getResources().getDisplayMetrics().widthPixels), -1,
                    Gravity.CENTER);
            frame.addView(sv, fp);
            setContentView(frame);
        } else {
            setContentView(sv);
        }
    }

    // ================= returning user =================

    private android.content.SharedPreferences hintPrefs() {
        return getSharedPreferences("login_hint", MODE_PRIVATE);
    }

    /** {user, name, role} of the last successful login on this device, or null. */
    private String[] lastHint() {
        try {
            android.content.SharedPreferences p = hintPrefs();
            String user = p.getString("user", "");
            if (user == null || user.isEmpty()) return null;
            String name = p.getString("name", "");
            return new String[]{user, name == null || name.isEmpty() ? user : name, p.getString("role", "")};
        } catch (Exception e) {
            return null;
        }
    }

    private void saveHint(int uid, String user, String name, String role) {
        try {
            hintPrefs().edit().putInt("uid", uid).putString("user", user == null ? "" : user)
                    .putString("name", name == null ? "" : name)
                    .putString("role", role == null ? "" : role).apply();
        } catch (Exception ignored) { Diag.ignored("LoginActivity:hint", ignored); }
    }

    private android.graphics.drawable.Drawable glyph(int res) {
        android.graphics.drawable.Drawable d = getDrawable(res).mutate();
        d.setTint(Theme.GOLD_SOFT);
        d.setBounds(0, 0, Theme.dp(20), Theme.dp(20));
        return d;
    }

    private View returningCard(String name, String role) {
        LinearLayout box = kit.h();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(16), Theme.dp(12));
        box.setBackground(Theme.ghostButton(Theme.GOLD));
        String ini = name == null || name.trim().isEmpty() ? "م" : name.trim().substring(0, 1);
        TextView mark = kit.text(ini, 20f, Theme.GOLD, true);
        mark.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        ring.setColor(Theme.alpha(Theme.GOLD, 40));
        ring.setStroke(Theme.dp(1), Theme.alpha(Theme.GOLD, 160));
        mark.setBackground(ring);
        box.addView(mark, new LinearLayout.LayoutParams(Theme.dp(52), Theme.dp(52)));
        LinearLayout copy = kit.v();
        copy.setPadding(Theme.dp(12), 0, 0, 0);
        copy.addView(kit.text("خوش برگشتید، " + name, 16f, Theme.TEXT, true), kit.lp(-1, -2));
        copy.addView(kit.text(RoleStore.faName(role) + " • ورود قبلی روی همین دستگاه", 11.5f,
                Theme.MUTED, false), kit.lp(-1, -2));
        box.addView(copy, kit.wlp(1f));
        return box;
    }

    /** Fingerprint unlock for the remembered Atiran account (the password cache is device-bound). */
    private void quickFinger() {
        if (busy || hintUid < 0 || isFinishing()) return;
        Finger.auth(this, new Finger.Cb() {
            @Override
            public void ok() {
                quickComplete();
            }

            @Override
            public void fail(String msg) {
                showErr(msg == null || msg.isEmpty() ? "اثر انگشت تأیید نشد" : msg);
            }
        });
    }

    private void quickComplete() {
        String[] h = lastHint();
        if (h == null) return;
        if (AtiranAuth.cachedPw(this, hintUid) == null) {
            showErr("برای ورود سریع یک بار با رمز وارد شوید");
            return;
        }
        uiSuccess(hintUid, h[0], h[1], h[2], true, false, "خوش آمدید " + h[1]);
    }

    private void paintRemember() {
        try {
            ir.meelano.manager.ui.MeelanoIcons.set(rememberV, (remember ? "☑ " : "○ ") + "مرا به خاطر بسپار");
        } catch (Exception ignored) { Diag.ignored("LoginActivity:132", ignored); }
    }

    private void showErr(String msg) {
        try {
            err.setText(msg == null ? "" : msg);
            err.setVisibility(msg == null || msg.isEmpty() ? View.GONE : View.VISIBLE);
        } catch (Exception ignored) { Diag.ignored("LoginActivity:139", ignored); }
    }

    private void setBusy(boolean b) {
        busy = b;
        try {
            loginBtn.setEnabled(!b);
            loginBtn.setText(b ? "در حال بررسی…" : "ورود");
        } catch (Exception ignored) { Diag.ignored("LoginActivity:147", ignored); }
    }

    // ================= login flow =================

    private void doLogin() {
        if (busy) return;
        final String user = eUser.getText().toString().trim();
        final String pass = ePass.getText().toString();
        if (user.isEmpty()) {
            showErr("نام کاربری را وارد کنید");
            return;
        }
        if (pass.isEmpty()) {
            showErr("رمز عبور را وارد کنید");
            return;
        }
        showErr(null);
        final boolean rem = remember;
        setBusy(true);
        new Thread(() -> {
            // Use cached Atiran credentials only when opening the SQL connection itself fails.
            // Query/schema/permission errors must not be disguised as an offline login failure.
            final Connection connection;
            try {
                connection = SmartLink.open(getApplicationContext(), settings);
            } catch (Exception connectionError) {
                offlineTry(user, pass, rem);
                return;
            }
            try (Connection c = connection) {
                AtiranAuth.AuthUser u = AtiranAuth.fetchUser(c, user);
                if (u == null) {
                    uiFail("این نام کاربری در جدول کاربران آتیران پیدا نشد.");
                    return;
                }
                if (u.uid <= 0) {
                    uiFail("شناسهٔ حساب آتیران معتبر نیست؛ ورود بدون شناسهٔ واقعی مجاز نیست.");
                    return;
                }
                if (u.locked) {
                    uiFail("این حساب در آتیران قفل شده است.");
                    return;
                }
                if (!u.active) {
                    uiFail("این حساب در آتیران غیرفعال است.");
                    return;
                }
                // STRICT: the real Atiran password, and nothing else.
                boolean serverOk = AtiranAuth.verifyPasswordServer(c, u.userName, pass);
                u.serverCompared = serverOk;
                if (!serverOk && !AtiranAuth.verifyPassword(u.pw, pass, u.userName)) {
                    uiPwFail(u, false);
                    return;
                }
                String role = AtiranAuth.effectiveRole(LoginActivity.this, u.uid, u.appRole);
                if (u.roleName == null || u.roleName.trim().isEmpty()) {
                    if (AtiranAuth.overrideRole(LoginActivity.this, u.uid).isEmpty()) {
                        uiFail("ورود رمز تأیید شد، اما عنوان نقش از جدول Roles/role قابل خواندن نیست (role_id="
                                + u.roleId + "). ساختار یا مجوز خواندن جدول نقش‌های آتیران را بررسی کنید.");
                        return;
                    }
                }
                if (!AtiranAuth.phaseOpen(LoginActivity.this, role)) {
                    uiFail("نقش آتیران «" + (u.roleName == null || u.roleName.trim().isEmpty()
                            ? RoleStore.faName(role) : u.roleName.trim())
                            + "» هنوز در این نسخه فعال نشده است.");
                    return;
                }
                AtiranAuth.putPwCache(LoginActivity.this, u.uid, u.pw);
                uiSuccess(u.uid, u.userName, u.displayName, role, rem, false,
                        "خوش آمدید " + u.displayName);
            } catch (AtiranAuth.NoTable nt) {
                uiFail("جدول یا ستون نام کاربری آتیران پیدا نشد؛ دیتابیس انتخاب‌شده را بررسی کنید.");
            } catch (Exception authError) {
                Diag.ignored("LoginActivity:AtiranAuth", authError);
                String detail = authError.getMessage();
                uiFail("خواندن حساب/نقش از آتیران انجام نشد"
                        + (detail == null || detail.trim().isEmpty() ? "." : ": " + detail));
            }
        }).start();
    }

    private void offlineTry(String user, String pass, boolean rem) {
        try {
            int uid = AtiranAuth.cachedUid(LoginActivity.this, user);
            if (uid != -999) {
                String role = AtiranAuth.cachedRole(LoginActivity.this, uid);
                if (role.isEmpty()) {
                    uiFail("نقش ذخیره‌شدهٔ این حساب معتبر نیست؛ برای بازبینی نقش، آنلاین وارد شوید.");
                    return;
                }
                byte[] cached = AtiranAuth.cachedPw(LoginActivity.this, uid);
                if (cached != null && AtiranAuth.verifyPassword(cached, pass, user)) {
                    if (!AtiranAuth.phaseOpen(LoginActivity.this, role)) {
                        uiFail("نسخه «" + RoleStore.faName(role)
                                + "» به‌زودی به همین برنامه اضافه می‌شود 🌱");
                        return;
                    }
                    String nm = AtiranAuth.cachedName(LoginActivity.this, uid);
                    if (nm.isEmpty()) nm = user;
                    uiSuccess(uid, user, nm, role, rem, false,
                            "ورود آفلاین — خوش آمدید " + nm);
                    return;
                }
                if (cached != null) {
                    uiFail("رمز آتیران اشتباه است");
                    return;
                }
            }
        } catch (Exception ignored) { Diag.ignored("LoginActivity:274", ignored); }
        uiFail("ارتباط با سرور ممکن نشد؛ برای اولین ورود، اینترنت لازم است");
    }

    /**
     * Password failure, but an honest one: "wrong password" used to be shown for four very
     * different situations (missing column, unreadable value, unknown hash algorithm, or a
     * genuinely mistyped password). Naming the real reason is what makes it fixable.
     */
    private void uiPwFail(AtiranAuth.AuthUser u, boolean serverOk) {
        String msg;
        if (u == null) msg = "رمز آتیران اشتباه است";
        else if (!u.pwColFound) msg = "ستونِ رمز در جدول کاربرانِ آتیران یافت نشد";
        else if (u.pw == null || u.pw.length == 0)
            msg = "رمز این کاربر در آتیران ثبت نشده یا خوانده نمی‌شود";
        else msg = "رمز آتیران اشتباه است • قالبِ ذخیره‌شده: "
                + String.valueOf(u.pw.length) + " بایت";
        final String report = pwReport(u, serverOk);
        runOnUiThread(() -> {
            if (isFinishing()) return;
            setBusy(false);
            showErr(msg);
            try {
                // Tap the message to copy a diagnostics report for support.
                err.setOnClickListener(v -> {
                    boolean ok = copyText("گزارش ورود میلانو", report);
                    kit.toast(ok ? "گزارش کپی شد" : "کپی نشد");
                });
            } catch (Exception ignored) { Diag.ignored("LoginActivity:302", ignored); }
        });
    }

    /** Diagnostics a developer can act on — no password, only what is stored. */
    private String pwReport(AtiranAuth.AuthUser u, boolean serverOk) {
        StringBuilder b = new StringBuilder();
        b.append("Meelano login diagnostics\n");
        if (u == null) {
            b.append("user_row: missing\n");
            return b.toString();
        }
        b.append("uid_valid: ").append(u.uid > 0).append('\n');
        b.append("pw_column_found: ").append(u.pwColFound).append('\n');
        b.append("stored_length: ").append(u.pw == null ? -1 : u.pw.length).append('\n');
        b.append("password_server_check: ").append(serverOk).append('\n');
        b.append("active: ").append(u.active).append("; locked: ").append(u.locked).append('\n');
        b.append("role_id: ").append(u.roleId).append('\n');
        b.append("role_name: ").append(u.roleName == null ? "" : u.roleName).append('\n');
        b.append("mapped_role: ").append(u.appRole == null ? "" : u.appRole).append('\n');
        return b.toString();
    }

    private boolean copyText(String label, String text) {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) return false;
            cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void uiFail(final String msg) {
        runOnUiThread(() -> {
            if (isFinishing()) return;
            setBusy(false);
            showErr(msg);
        });
    }

    private void uiSuccess(final int uid, final String user, final String name,
            final String role, final boolean rem, final boolean boot, final String hello) {
        runOnUiThread(() -> {
            if (isFinishing()) return;
            setBusy(false);
            try {
                AtiranAuth.clearNoTable(LoginActivity.this);
                AtiranAuth.putCache(LoginActivity.this, uid, user, name, role);
                AtiranAuth.saveSession(LoginActivity.this, uid, user, name, role, rem, boot);
                saveHint(uid, user, name, role);
                RoleStore.setCurrent(LoginActivity.this, role);
            } catch (Exception ignored) { Diag.ignored("LoginActivity:365", ignored); }
            kit.toast(hello);
            goMain();
        });
    }

    private String appVer() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return "نسخه " + ir.meelano.manager.core.Money.fa(v == null || v.isEmpty() ? "—" : v);
        } catch (Exception e) {
            return "";
        }
    }

    private void toLicenseSetup() {
        try {
            Intent i = new Intent(this, LicenseActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { Diag.ignored("LoginActivity:385", ignored); }
        finish();
    }

    private void goMain() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { Diag.ignored("LoginActivity:394", ignored); }
        finish();
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        super.onBackPressed();
    }
}
