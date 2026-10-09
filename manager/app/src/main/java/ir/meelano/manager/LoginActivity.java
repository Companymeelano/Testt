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
import ir.meelano.manager.core.Money;
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
 * Layers: (1) the real Atiran password when it is a standard hash,
 * (2) the manager-set 4-digit app PIN (works offline too),
 * (3) manager bootstrap with the admin PIN (default 1234) to set PINs.
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
        TextView t = kit.text("ورود کاربر", 23, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t, kit.lp(-1, -2));
        TextView s = kit.text("نام کاربری و رمز آتیران را وارد کنید", 12.5f, Theme.MUTED, false);
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
        root.addView(kit.gap(8));
        root.addView(kit.btnGhost("🛠 ورود راه‌اندازی مدیر", Theme.VIOLET, v -> bootDialog()),
                kit.lp(-1, -2));
        root.addView(kit.gap(10));
        root.addView(kit.hint("رمز همان رمز آتیران است؛ اگر قبول نکرد، مدیر از بخش «کاربران» برای شما رمز ۴ رقمی تعیین می‌کند. ورود با رمز اپ حتی بدون اینترنت هم کار می‌کند."),
                kit.lp(-1, -2));

        setContentView(sv);
    }

    private void paintRemember() {
        try {
            rememberV.setText((remember ? "☑ " : "○ ") + "مرا به خاطر بسپار");
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
                try {
                    u = AtiranAuth.fetchUser(c, user);
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
                String role = AtiranAuth.effectiveRole(LoginActivity.this, u.uid, u.appRole);
                if (AtiranAuth.verifyPassword(u.pw, pass)) {
                    // Real Atiran password — zero setup needed.
                    uiSuccess(u.uid, u.userName, u.displayName, role, rem, false,
                            "خوش آمدید " + u.displayName);
                    return;
                }
                if (AtiranAuth.pinSet(LoginActivity.this, u.uid)) {
                    if (AtiranAuth.checkPin(LoginActivity.this, u.uid, pass)) {
                        uiSuccess(u.uid, u.userName, u.displayName, role, rem, false,
                                "خوش آمدید " + u.displayName);
                    } else {
                        uiFail("رمز اشتباه است");
                    }
                    return;
                }
                uiFail("برای این کاربر رمزی تعیین نشده؛ از مدیر بخواهید از بخش «کاربران» رمز تعیین کند");
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
            if (uid != -999 && AtiranAuth.pinSet(LoginActivity.this, uid)
                    && AtiranAuth.checkPin(LoginActivity.this, uid, pass)) {
                String nm = AtiranAuth.cachedName(LoginActivity.this, uid);
                String role = AtiranAuth.cachedRole(LoginActivity.this, uid);
                if (nm.isEmpty()) nm = user;
                if (role.isEmpty()) role = RoleStore.SELLER;
                uiSuccess(uid, user, nm, role, rem, false,
                        "ورود آفلاین — خوش آمدید " + nm);
                return;
            }
            if (uid != -999 && AtiranAuth.pinSet(LoginActivity.this, uid)) {
                uiFail("رمز اشتباه است");
                return;
            }
        } catch (Exception ignored) { }
        uiFail("ارتباط با سرور ممکن نشد؛ اینترنت/شبکه را بررسی کنید");
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

    private void goMain() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { }
        finish();
    }

    // ================= manager bootstrap =================

    /** First-time setup: the admin PIN opens a manager session to set user PINs. */
    private void bootDialog() {
        if (busy) return;
        LinearLayout body = kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(kit.text("ورود راه‌اندازی مدیر", 13.5f, Theme.TEXT, true), kit.lp(-1, -2));
        body.addView(kit.hint("رمز نقش مدیر (پیش‌فرض " + Money.fa("1234") + ") را وارد کنید؛ بعد از ورود، از بخش «کاربران» برای هر کاربر رمز تعیین کنید."),
                kit.lp(-1, -2));
        final EditText e = kit.editPin("رمز مدیر", "");
        body.addView(e, kit.lp(-1, -2));
        body.addView(kit.gap(8));
        final android.app.AlertDialog[] box = new android.app.AlertDialog[1];
        body.addView(kit.btn("ورود مدیر", v -> {
            if (RoleStore.checkRole(LoginActivity.this, RoleStore.ADMIN,
                    e.getText().toString())) {
                try {
                    box[0].dismiss();
                } catch (Exception ignored) { }
                AtiranAuth.saveSession(LoginActivity.this, -1, "", "مدیر راه‌اندازی",
                        RoleStore.ADMIN, false, true);
                RoleStore.setCurrent(LoginActivity.this, RoleStore.ADMIN);
                kit.toast("به‌عنوان مدیر وارد شدید");
                goMain();
            } else {
                kit.toast("رمز اشتباه است");
            }
        }), kit.lp(-1, -2));
        box[0] = kit.dialog("راه‌اندازی", body, true);
        box[0].show();
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        super.onBackPressed();
    }
}
