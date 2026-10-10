package ir.meelano.manager;

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
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.core.SmartLink;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

/**
 * Atiran user login (v26). Shown after license + connection setup, before
 * the shell. Username + password are checked against the shop's own
 * `sys_users` table; the in-app role comes from the Atiran role name.
 *
 * Strict since v27: login accepts ONLY the real Atiran password (server
 * pwdcompare() + the full standard-hash try) — no bootstrap, no app-PIN
 * fallback here. After the first online login the verifier is cached, so
 * the same real-password check also works offline.
 * One app, many editions (v30): the Atiran role routes each user to his
 * own edition (admin -> management, warehouse -> anbar …). Roles whose
 * edition is not published yet get a «coming soon» gate instead of an
 * error (see AtiranAuth.phaseOpen).
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.init(this);
        try {
            getWindow().setStatusBarColor(Theme.BG);
            getWindow().setNavigationBarColor(Theme.BG);
        } catch (Exception ignored) { }
        kit = new Kit(this);
        settings = new Settings(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Theme.BG);
        LinearLayout root = kit.v();
        root.setGravity(Gravity.CENTER);
        root.setPadding(Theme.dp(26), Theme.dp(30), Theme.dp(26), Theme.dp(30));
        sv.addView(root);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(92), Theme.dp(92));
        lp.gravity = Gravity.CENTER;
        root.addView(kit.logo(92), lp);
        TextView t = kit.text("ورود مدیر", 23, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t, kit.lp(-1, -2));
        TextView s = kit.text("نام کاربری و رمز واقعی آتیران", 12.5f, Theme.MUTED, false);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = kit.lp(-1, -2);
        sp.setMargins(0, Theme.dp(6), 0, Theme.dp(16));
        root.addView(s, sp);

        LinearLayout card = kit.card(Theme.GOLD);
        eUser = kit.edit("نام کاربری آتیران", "");
        try {
            eUser.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        } catch (Exception ignored) { }
        card.addView(eUser, kit.lp(-1, -2));
        card.addView(kit.gap(10));
        ePass = kit.edit("رمز عبور", "", true);
        card.addView(ePass, kit.lp(-1, -2));
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

        loginBtn = kit.btn("ورود", v -> doLogin());
        root.addView(loginBtn, kit.lp(-1, -2));
        root.addView(kit.gap(10));
        root.addView(kit.hint("با نام کاربری و رمز آتیران خودتان وارد شوید؛ به نسخه مخصوص نقش‌تان می‌روید."),
                kit.lp(-1, -2));
        root.addView(kit.gap(6));
        TextView verLine = kit.text("میلانو • " + appVer(), 10.5f, Theme.MUTED, false);
        verLine.setGravity(Gravity.CENTER);
        root.addView(verLine, kit.lp(-1, -2));

        setContentView(sv);
    }

    private void paintRemember() {
        try {
            ir.meelano.manager.ui.MeelanoIcons.set(rememberV, (remember ? "☑ " : "○ ") + "مرا به خاطر بسپار");
        } catch (Exception ignored) { }
    }

    private void showErr(String msg) {
        try {
            err.setText(msg == null ? "" : msg);
            err.setVisibility(msg == null || msg.isEmpty() ? View.GONE : View.VISIBLE);
        } catch (Exception ignored) { }
    }

    private void setBusy(boolean b) {
        busy = b;
        try {
            loginBtn.setEnabled(!b);
            loginBtn.setText(b ? "در حال بررسی…" : "ورود");
        } catch (Exception ignored) { }
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
        setBusy(true);
        final boolean rem = remember;
        new Thread(() -> {
            // ---- online attempt ----
            try {
                Connection c = SmartLink.open(getApplicationContext(), settings);
                AtiranAuth.AuthUser u = null;
                boolean serverOk = false;
                try {
                    u = AtiranAuth.fetchUser(c, user);
                    // Server check while the connection is still open.
                    if (u != null) {
                        serverOk = AtiranAuth.verifyPasswordServer(c, u.userName, pass);
                        u.serverCompared = serverOk;
                    }
                } finally {
                    try {
                        c.close();
                    } catch (Exception ignored) { }
                }
                if (u == null) {
                    uiFail("کاربری با این نام در آتیران یافت نشد");
                    return;
                }
                if (u.locked) {
                    uiFail("این حساب در آتیران قفل شده است");
                    return;
                }
                if (!u.active) {
                    uiFail("این حساب در آتیران غیرفعال است");
                    return;
                }
                // Cache the verifier now: enables the same real-password check offline.
                AtiranAuth.putPwCache(LoginActivity.this, u.uid, u.pw);
                String role = AtiranAuth.effectiveRole(LoginActivity.this, u.uid, u.appRole);
                if (!AtiranAuth.phaseOpen(LoginActivity.this, role)) {
                    uiFail("نسخه «" + RoleStore.faName(role)
                            + "» به‌زودی به همین برنامه اضافه می‌شود 🌱");
                    return;
                }
                // STRICT: the real Atiran password, and nothing else.
                if (serverOk || AtiranAuth.verifyPassword(u.pw, pass, u.userName)) {
                    uiSuccess(u.uid, u.userName, u.displayName, role, rem, false,
                            "خوش آمدید " + u.displayName);
                    return;
                }
                uiPwFail(u, serverOk);
            } catch (AtiranAuth.NoTable nt) {
                // This database has no login tables: don't trap the user here.
                try {
                    AtiranAuth.setNoTable(LoginActivity.this);
                } catch (Exception ignored) { }
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    setBusy(false);
                    kit.toast("ورود آتیران در این دیتابیس پشتیبانی نمی‌شود؛ بدون ورود وارد شدید");
                    goMain();
                });
            } catch (Exception connErr) {
                // ---- offline fallback: cached user + app PIN ----
                offlineTry(user, pass, rem);
            }
        }).start();
    }

    private void offlineTry(String user, String pass, boolean rem) {
        try {
            int uid = AtiranAuth.cachedUid(LoginActivity.this, user);
            if (uid != -999) {
                String role = AtiranAuth.cachedRole(LoginActivity.this, uid);
                if (role.isEmpty()) role = RoleStore.ADMIN;
                if (!AtiranAuth.phaseOpen(LoginActivity.this, role)) {
                    uiFail("نسخه «" + RoleStore.faName(role)
                            + "» به‌زودی به همین برنامه اضافه می‌شود 🌱");
                    return;
                }
                byte[] cached = AtiranAuth.cachedPw(LoginActivity.this, uid);
                if (cached != null && AtiranAuth.verifyPassword(cached, pass, user)) {
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
        } catch (Exception ignored) { }
        uiFail("ارتباط با سرور ممکن نشد؛ برای اولین ورود، اینترنت لازم است");
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
                RoleStore.setCurrent(LoginActivity.this, role);
            } catch (Exception ignored) { }
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

    private void goMain() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { }
        finish();
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        super.onBackPressed();
    }
}
