package ir.meelano.manager.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.R;
import ir.meelano.manager.core.Net;
import ir.meelano.manager.core.SmartLink;
import ir.meelano.manager.data.NetRoute;
import ir.meelano.manager.data.Settings;

/**
 * The smart-link panel behind the header icon: live status (transport +
 * active path + mode) and the deep Persian troubleshooter with fixes.
 * Raw addresses are NEVER shown here (only masked); the seller-ready
 * clipboard copy is the only place carrying real values.
 */
public final class LinkPanel {
    private LinkPanel() { }

    /** Status sheet. onModeChanged refreshes the caller (e.g. re-probe). */
    public static void showStatus(Activity a, Settings s, Runnable onModeChanged) {
        try {
            Kit kit = new Kit(a);
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(18), Theme.dp(4), Theme.dp(18), Theme.dp(12));
            String last = s.linkLast();
            int icon = SmartLink.WAN.equals(last) ? R.drawable.link_wan
                    : (SmartLink.LAN.equals(last) ? R.drawable.link_lan : R.drawable.link_probe);
            ImageView iv = new ImageView(a);
            try {
                iv.setImageResource(icon);
            } catch (Exception ignored) { }
            try {
                iv.setElevation(Theme.dp(6));
            } catch (Exception ignored) { }
            LinearLayout.LayoutParams ip =
                    new LinearLayout.LayoutParams(Theme.dp(76), Theme.dp(76));
            ip.gravity = Gravity.CENTER;
            body.addView(iv, ip);
            body.addView(kit.gap(6));
            String vpn = NetRoute.isVpnActive(a) ? " + فیلترشکن" : "";
            TextView sub = kit.text(Net.transportFa(a) + vpn + " • "
                    + ("auto".equals(s.linkMode()) ? "حالت خودکار"
                    : ("فقط " + SmartLink.kindShort(s.linkMode()))),
                    12.5f, Theme.MUTED, false);
            sub.setGravity(Gravity.CENTER);
            body.addView(sub, kit.lp(-1, -2));
            body.addView(kit.gap(8));
            LinearLayout card = kit.card(Theme.GOLD);
            card.addView(kit.kv("شبکه گوشی", Net.transportFa(a) + vpn, Theme.TEXT), kit.lp(-1, -2));
            String active = SmartLink.WAN.equals(last) ? "خارج شبکه • " + s.maskedWan()
                    : (SmartLink.LAN.equals(last) ? "داخل شبکه • " + s.maskedLan() : "—");
            card.addView(kit.kv("مسیر فعال", active, Theme.GOLD_SOFT), kit.lp(-1, -2));
            card.addView(kit.kv("داخل شبکه", s.maskedLan().isEmpty() ? "✕ ثبت نشده" : "✓ " + s.maskedLan(),
                    s.maskedLan().isEmpty() ? Theme.DANGER : Theme.SUCCESS), kit.lp(-1, -2));
            card.addView(kit.kv("خارج شبکه", s.maskedWan().isEmpty() ? "✕ ثبت نشده" : "✓ " + s.maskedWan(),
                    s.maskedWan().isEmpty() ? Theme.DANGER : Theme.SUCCESS), kit.lp(-1, -2));
            body.addView(card, kit.lp(-1, -2));
            body.addView(kit.gap(8));
            body.addView(kit.text("حالت اتصال", 12f, Theme.MUTED, true), kit.lp(-1, -2));
            int sel = "lan".equals(s.linkMode()) ? 1 : ("wan".equals(s.linkMode()) ? 2 : 0);
            final AlertDialog[] box = new AlertDialog[1];
            body.addView(kit.chips(new String[]{"خودکار ✦", "داخل شبکه", "خارج شبکه"}, sel, idx -> {
                try {
                    s.setLinkMode(idx == 1 ? "lan" : (idx == 2 ? "wan" : "auto"));
                    kit.toast(idx == 0 ? "حالت خودکار فعال شد" : ("فقط " + SmartLink.kindShort(idx == 1 ? "lan" : "wan")));
                    if (box[0] != null) box[0].dismiss();
                    showStatus(a, s, onModeChanged);
                    if (onModeChanged != null) onModeChanged.run();
                } catch (Exception ignored) { }
            }), kit.lp(-1, -2));
            body.addView(kit.gap(8));
            body.addView(kit.btnGold("عیب‌یابی هوشمند", v -> {
                try {
                    if (box[0] != null) box[0].dismiss();
                } catch (Exception ignored) { }
                showDiagnose(a, s);
            }), kit.lp(-1, -2));
            body.addView(kit.gap(8));
            body.addView(kit.btnGhost("بستن", Theme.MUTED, v -> {
                try {
                    if (box[0] != null) box[0].dismiss();
                } catch (Exception ignored) { }
            }), kit.lp(-1, -2));
            signature(kit, body);
            AlertDialog d = kit.dialog("اتصال هوشمند", kit.scrollWrap(body, 460), true);
            box[0] = d;
            d.show();
        } catch (Exception ignored) { }
    }

    /** Troubleshoot saved profiles. */
    public static void showDiagnose(Activity a, Settings s) {
        try {
            Kit kit = new Kit(a);
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(18), Theme.dp(6), Theme.dp(18), Theme.dp(14));
            TextView t = kit.text("در حال عیب‌یابی مسیرها…", 14f, Theme.TEXT, false);
            t.setGravity(Gravity.CENTER);
            body.addView(t, kit.lp(-1, -2));
            AlertDialog d = kit.dialog("عیب‌یابی هوشمند", body, true);
            d.show();
            boolean vpn = NetRoute.isVpnActive(a);
            SmartLink.diagnose(a, s, vpn, r -> {
                try {
                    d.dismiss();
                } catch (Exception ignored) { }
                if (finished(a)) return;
                renderReport(a, r, () -> showDiagnose(a, s));
            });
        } catch (Exception ignored) { }
    }

    /** Troubleshoot explicit (unsaved) values from the activation screen. */
    public static void showDiagnoseExplicit(Activity a, String lan, String wan, int port, String db,
                                            String user, String pass) {
        try {
            Kit kit = new Kit(a);
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(18), Theme.dp(6), Theme.dp(18), Theme.dp(14));
            TextView t = kit.text("در حال عیب‌یابی مسیرها…", 14f, Theme.TEXT, false);
            t.setGravity(Gravity.CENTER);
            body.addView(t, kit.lp(-1, -2));
            AlertDialog d = kit.dialog("عیب‌یابی هوشمند", body, true);
            d.show();
            boolean vpn = NetRoute.isVpnActive(a);
            SmartLink.diagnoseExplicit(a, lan, wan, port, db, user, pass, vpn, r -> {
                try {
                    d.dismiss();
                } catch (Exception ignored) { }
                if (finished(a)) return;
                renderReport(a, r, () -> showDiagnoseExplicit(a, lan, wan, port, db, user, pass));
            });
        } catch (Exception ignored) { }
    }

    private static void renderReport(Activity a, SmartLink.Report r, Runnable retry) {
        try {
            Kit kit = new Kit(a);
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(18), Theme.dp(4), Theme.dp(18), Theme.dp(12));
            LinearLayout vc = kit.card(r.anyOk ? Theme.SUCCESS : Theme.DANGER);
            vc.addView(kit.text(r.verdict, 13.5f, Theme.TEXT, true), kit.lp(-1, -2));
            vc.addView(kit.text("شبکه گوشی: " + r.transportFa + (r.vpnOn ? " + فیلترشکن" : "")
                    + " • " + r.portDb, 12f, Theme.MUTED, false), kit.lp(-1, -2));
            body.addView(vc, kit.lp(-1, -2));
            body.addView(kit.gap(8));
            for (SmartLink.Prof p : r.profs) {
                LinearLayout pc = kit.card(SmartLink.WAN.equals(p.kind) ? Theme.INFO : Theme.GOLD);
                LinearLayout head = kit.h();
                head.setGravity(Gravity.CENTER_VERTICAL);
                ImageView iv = new ImageView(a);
                try {
                    iv.setImageResource(SmartLink.WAN.equals(p.kind)
                            ? R.drawable.link_wan : R.drawable.link_lan);
                } catch (Exception ignored) { }
                head.addView(iv, new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40)));
                head.addView(kit.space(8));
                LinearLayout copy = kit.v();
                copy.addView(kit.text((p.ok ? "✓ " : "✕ ") + SmartLink.kindFa(p.kind),
                        13.5f, Theme.TEXT, true), kit.lp(-1, -2));
                copy.addView(kit.text(SmartLink.maskHost(p.host), 12f, Theme.MUTED, false),
                        kit.lp(-1, -2));
                head.addView(copy, kit.wlp(1f));
                pc.addView(head, kit.lp(-1, -2));
                pc.addView(kit.gap(6));
                for (SmartLink.Stage st : p.stages) {
                    if (st.skipped) continue;
                    LinearLayout row = kit.h();
                    TextView mark = kit.text(st.ok ? "✓" : "✕", 14,
                            st.ok ? Theme.SUCCESS : Theme.DANGER, true);
                    row.addView(mark, kit.lp(-2, -2));
                    row.addView(kit.space(6));
                    LinearLayout tx = kit.v();
                    tx.addView(kit.text(st.name + (st.detail.isEmpty() ? "" : " — " + st.detail),
                            12.5f, Theme.TEXT, false), kit.lp(-1, -2));
                    if (!st.ok && !st.fix.isEmpty())
                        tx.addView(kit.text("راه‌حل: " + st.fix, 12f, Theme.GOLD_SOFT, false),
                                kit.lp(-1, -2));
                    row.addView(tx, kit.wlp(1f));
                    pc.addView(row, kit.lp(-1, -2));
                    pc.addView(kit.gap(4));
                }
                LinearLayout.LayoutParams pp = kit.lp(-1, -2);
                pp.setMargins(0, 0, 0, Theme.dp(10));
                body.addView(pc, pp);
            }
            final AlertDialog[] box = new AlertDialog[1];
            LinearLayout row = kit.h();
            row.addView(kit.btnGold("تلاش مجدد", v -> {
                try {
                    if (box[0] != null) box[0].dismiss();
                } catch (Exception ignored) { }
                if (retry != null) retry.run();
            }), kit.wlp(1f));
            row.addView(kit.space(8));
            row.addView(kit.btnGhost("کپی گزارش", Theme.TEAL, v -> copyReport(a, r)), kit.wlp(1f));
            body.addView(row, kit.lp(-1, -2));
            body.addView(kit.gap(8));
            body.addView(kit.btnGhost("بستن", Theme.MUTED, v -> {
                try {
                    if (box[0] != null) box[0].dismiss();
                } catch (Exception ignored) { }
            }), kit.lp(-1, -2));
            signature(kit, body);
            AlertDialog d = kit.dialog("عیب‌یابی هوشمند", kit.scrollWrap(body, 460), true);
            box[0] = d;
            d.show();
        } catch (Exception ignored) { }
    }

    private static void copyReport(Activity a, SmartLink.Report r) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            cm.setPrimaryClip(ClipData.newPlainText("meelano-link", r.copyText));
            new Kit(a).toast("گزارش کپی شد؛ برای فروشنده بفرستید");
        } catch (Exception e) {
            new Kit(a).toast("کپی ممکن نشد");
        }
    }

    /** Metallic-gold English signature with a soft 3D shadow. */
    private static void signature(Kit kit, LinearLayout body) {
        try {
            body.addView(kit.gap(10));
            TextView over = kit.text("S M A R T   L I N K", 10.5f, Theme.MUTED, true);
            over.setGravity(Gravity.CENTER);
            try {
                over.setTypeface(Theme.face(true));
            } catch (Exception ignored) { }
            body.addView(over, kit.lp(-1, -2));
            final TextView name = kit.text("Milad Yaghoobi", 21, Theme.GOLD_SOFT, true);
            name.setGravity(Gravity.CENTER);
            try {
                name.setTypeface(Theme.face(true));
                name.setLetterSpacing(0.06f);
                name.setShadowLayer(5, 0, 3, 0x80000000);
            } catch (Exception ignored) { }
            body.addView(name, kit.lp(-1, -2));
            name.post(() -> {
                try {
                    int h = name.getHeight();
                    if (h <= 0) h = Theme.dp(28);
                    name.getPaint().setShader(new LinearGradient(0, 0, 0, h,
                            new int[]{0xFFFFF6DE, 0xFFE9C37C, 0xFF8A6420, 0xFFF1D493, 0xFFFFF6DE},
                            new float[]{0f, 0.35f, 0.55f, 0.75f, 1f}, Shader.TileMode.CLAMP));
                    name.invalidate();
                } catch (Exception ignored) { }
            });
            TextView under = kit.text("معماری اتصال هوشمند", 11f, Theme.MUTED, false);
            under.setGravity(Gravity.CENTER);
            body.addView(under, kit.lp(-1, -2));
        } catch (Exception ignored) { }
    }

    private static boolean finished(Activity a) {
        try {
            return a == null || a.isFinishing() || a.isDestroyed();
        } catch (Exception e) {
            return true;
        }
    }
}
