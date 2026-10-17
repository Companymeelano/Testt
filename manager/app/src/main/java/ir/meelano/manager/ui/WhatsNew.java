package ir.meelano.manager.ui;

import ir.meelano.manager.core.Diag;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.core.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen «What's New» — shown once after every upgrade, in the
 * customer's own theme (dark/light skin × their accent color). Short,
 * respectful copy; every item has a details button with the full story
 * plus exactly where to find it; «ورود به برنامه» skips everything.
 */
public final class WhatsNew {
    private WhatsNew() { }

    private static final String PREFS = "meelano_update";
    private static boolean shownThisProcess = false;

    /**
     * Show on upgrade (nothing when there is nothing new), then run next
     * (the silent update check) after the user enters. Safe to call on
     * every shell build — it fires at most once per process.
     */
    public static void maybeShow(Activity a, Runnable next) {
        Runnable done = next == null ? () -> { } : next;
        try {
            if (shownThisProcess) {
                done.run();
                return;
            }
            shownThisProcess = true;
            int cur = appCode(a);
            if (isAutoOff(a)) {
                // The user ticked «دیگر این پیغام را نشان نده»: stay quiet, but remember the version.
                markSeen(a, cur);
                done.run();
                return;
            }
            int last = prefs(a).getInt("last_news_code", 0);
            if (cur <= 0) {
                done.run();
                return;
            }
            List<Changelog.Ver> vers = Changelog.since(last, cur);
            if (vers.isEmpty()) {
                markSeen(a, cur);
                done.run();
                return;
            }
            show(a, vers, cur, done);
        } catch (Exception e) {
            try {
                done.run();
            } catch (Exception ignored) { Diag.ignored("WhatsNew:73", ignored); }
        }
    }

    /** True when the «تازه‌های نسخه» popup will open at this launch (so other startup popups must wait). */
    public static boolean pending(Activity a) {
        try {
            int cur = appCode(a);
            if (cur <= 0 || isAutoOff(a)) return false;
            int last = prefs(a).getInt("last_news_code", 0);
            return !Changelog.since(last, cur).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** True when the user ticked «دیگر این پیغام را نشان نده». */
    public static boolean isAutoOff(Context c) {
        return prefs(c).getBoolean("auto_off", false);
    }

    /** Settings switch: false re-enables the automatic popup after upgrades. */
    public static void setAutoOff(Context c, boolean off) {
        prefs(c).edit().putBoolean("auto_off", off).apply();
    }

    /** Re-open the current version's notes (Settings » تازه‌های نسخه). */
    public static void showCurrent(Activity a) {
        try {
            int cur = appCode(a);
            if (cur <= 0) return;
            List<Changelog.Ver> vers = Changelog.since(cur - 1, cur);
            if (vers.isEmpty()) return;
            show(a, vers, cur, null);
        } catch (Exception ignored) { Diag.ignored("WhatsNew:95", ignored); }
    }

    private static void show(Activity a, List<Changelog.Ver> vers, int cur, Runnable onDone) {
        Kit kit = new Kit(a);
        Dialog d = new Dialog(a);
        try {
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        } catch (Exception ignored) { Diag.ignored("WhatsNew:103", ignored); }

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.SURFACE, Theme.BG}));

        // Gold glow rule under the top edge.
        View glow = new View(a);
        glow.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, Theme.GOLD, 0x00000000}));
        root.addView(glow, new LinearLayout.LayoutParams(-1, Theme.dp(3)));

        ScrollView sv = new ScrollView(a);
        LinearLayout content = kit.v();
        content.setPadding(Theme.dp(20), Theme.dp(16), Theme.dp(20), Theme.dp(16));

        // ---- hero: gold medallion, version chip and a count of changes ----
        LinearLayout hero = kit.v();
        hero.setGravity(Gravity.CENTER);
        hero.setBackground(Theme.hero(Theme.GOLD));
        hero.setPadding(Theme.dp(18), Theme.dp(22), Theme.dp(18), Theme.dp(18));
        TextView badge = kit.text("✦", 34, Theme.onAccent(Theme.GOLD), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Theme.accentAvatar(Theme.GOLD));
        try {
            badge.setElevation(Theme.dp(12));
        } catch (Exception ignored) { Diag.ignored("WhatsNew:131", ignored); }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Theme.dp(84), Theme.dp(84));
        bp.gravity = Gravity.CENTER;
        hero.addView(badge, bp);
        hero.addView(kit.gap(12));
        TextView title = kit.text("تازه‌های نسخه", 22, Theme.TEXT, true);
        title.setGravity(Gravity.CENTER);
        hero.addView(title, kit.lp(-1, -2));
        Changelog.Ver latest = vers.get(0);
        TextView chip = kit.text("✦ نسخه " + Money.fa(latest.name) + " • " + latest.dateFa,
                12.5f, Theme.onAccent(Theme.GOLD), true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(Theme.pill(Theme.GOLD));
        chip.setPadding(Theme.dp(14), Theme.dp(6), Theme.dp(14), Theme.dp(6));
        LinearLayout.LayoutParams cgp = new LinearLayout.LayoutParams(-2, -2);
        cgp.gravity = Gravity.CENTER;
        cgp.setMargins(0, Theme.dp(10), 0, Theme.dp(6));
        hero.addView(chip, cgp);
        int total = 0;
        for (Changelog.Ver v : vers) for (Object o : v.items) total++;
        TextView count = kit.text(Money.fa(String.valueOf(total)) + " بهبود و قابلیت تازه در این به‌روزرسانی",
                12f, Theme.MUTED, false);
        count.setGravity(Gravity.CENTER);
        hero.addView(count, kit.lp(-1, -2));
        content.addView(hero, kit.lp(-1, -2));
        content.addView(kit.gap(14));

        // ---- version sections + 3D item cards ----
        List<View> cards = new ArrayList<>();
        for (Changelog.Ver v : vers) {
            LinearLayout secHead = kit.h();
            secHead.setGravity(Gravity.CENTER_VERTICAL);
            TextView pill = kit.text("نسخه " + Money.fa(v.name), 12.5f, Theme.GOLD_SOFT, true);
            pill.setBackground(Theme.ghostButton(Theme.GOLD));
            pill.setPadding(Theme.dp(12), Theme.dp(4), Theme.dp(12), Theme.dp(4));
            pill.setGravity(Gravity.CENTER);
            secHead.addView(pill, kit.lp(-2, -2));
            secHead.addView(kit.space(8));
            TextView head = kit.text(v.headline, 13f, Theme.TEXT, true);
            secHead.addView(head, kit.wlp(1f));
            content.addView(secHead, kit.lp(-1, -2));
            content.addView(kit.gap(8));
            for (Changelog.Item it : v.items) {
                LinearLayout card = kit.card(Changelog.kindColor(it.kind));
                try {
                    card.setElevation(Theme.dp(6));
                } catch (Exception ignored) { Diag.ignored("WhatsNew:177", ignored); }
                LinearLayout row = kit.h();
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView g = kit.text(it.glyph, 21, Changelog.kindColor(it.kind), true);
                g.setGravity(Gravity.CENTER);
                g.setBackground(Theme.avatar(Theme.alpha(Changelog.kindColor(it.kind), 60)));
                row.addView(g, new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(48)));
                row.addView(kit.space(10));
                LinearLayout copy = kit.v();
                copy.addView(kit.text(it.title, 14.5f, Theme.TEXT, true), kit.lp(-1, -2));
                TextView kp = kit.text(Changelog.kindFa(it.kind), 11f,
                        Changelog.kindColor(it.kind), true);
                copy.addView(kp, kit.lp(-1, -2));
                row.addView(copy, kit.wlp(1f));
                card.addView(row, kit.lp(-1, -2));
                card.addView(kit.gap(6));
                card.addView(kit.text(it.brief, 12.8f, Theme.MUTED, false), kit.lp(-1, -2));
                card.addView(kit.gap(8));
                final Changelog.Item fit = it;
                card.addView(kit.btnGhost("نمایش جزئیات", Changelog.kindColor(it.kind),
                        vv -> detailDialog(a, fit)), kit.lp(-1, -2));
                LinearLayout.LayoutParams cp = kit.lp(-1, -2);
                cp.setMargins(0, 0, 0, Theme.dp(12));
                content.addView(card, cp);
                cards.add(card);
            }
        }
        sv.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- sticky footer: never-show tick, then the one-tap way in ----
        LinearLayout foot = kit.v();
        foot.setPadding(Theme.dp(20), Theme.dp(8), Theme.dp(20), Theme.dp(16));
        final boolean[] never = {false};
        CheckBox cb = new CheckBox(a);
        cb.setText("دیگر این پیغام را نشان نده");
        cb.setTextColor(Theme.TEXT);
        cb.setTextSize(12.5f);
        cb.setButtonTintList(ColorStateList.valueOf(Theme.GOLD));
        cb.setOnCheckedChangeListener((x, on) -> never[0] = on);
        foot.addView(cb, kit.lp(-1, -2));
        foot.addView(kit.btnGold("✦ ورود به برنامه", v -> {
            try {
                d.dismiss();
            } catch (Exception ignored) { Diag.ignored("WhatsNew:221", ignored); }
        }), kit.lp(-1, -2));
        TextView re = kit.text("همیشه در: تنظیمات » درباره » تازه‌های نسخه",
                11f, Theme.MUTED, false);
        re.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rp = kit.lp(-1, -2);
        rp.setMargins(0, Theme.dp(6), 0, 0);
        foot.addView(re, rp);
        root.addView(foot, kit.lp(-1, -2));

        d.setContentView(root);
        try {
            if (d.getWindow() != null)
                d.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
        } catch (Exception ignored) { Diag.ignored("WhatsNew:235", ignored); }
        d.setCancelable(true);
        d.setOnDismissListener(dd -> {
            markSeen(a, cur);
            if (never[0]) setAutoOff(a, true);
            if (onDone != null) {
                try {
                    onDone.run();
                } catch (Exception ignored) { Diag.ignored("WhatsNew:243", ignored); }
            }
        });
        d.show();
        try {
            if (d.getWindow() != null)
                d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
        } catch (Exception ignored) { Diag.ignored("WhatsNew:251", ignored); }
        // Staged entrance: badge pops, cards rise one by one.
        try {
            badge.setScaleX(0.5f);
            badge.setScaleY(0.5f);
            badge.setAlpha(0f);
            badge.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f)).start();
            for (int i = 0; i < cards.size(); i++) {
                View c = cards.get(i);
                c.setAlpha(0f);
                c.setTranslationY(Theme.dp(18));
                c.animate().alpha(1f).translationY(0f).setDuration(320)
                        .setStartDelay(150 + i * 70).start();
            }
        } catch (Exception ignored) { Diag.ignored("WhatsNew:266", ignored); }
    }

    /** Full story of one item + exactly where to find it in the app. */
    private static void detailDialog(Activity a, Changelog.Item it) {
        try {
            Kit kit = new Kit(a);
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(18), Theme.dp(4), Theme.dp(18), Theme.dp(12));
            TextView kp = kit.text(Changelog.kindFa(it.kind), 12f,
                    Changelog.kindColor(it.kind), true);
            kp.setGravity(Gravity.CENTER);
            body.addView(kp, kit.lp(-1, -2));
            body.addView(kit.gap(6));
            body.addView(kit.text(it.detail, 13.5f, Theme.TEXT, false), kit.lp(-1, -2));
            body.addView(kit.gap(10));
            LinearLayout where = kit.card(Theme.GOLD);
            where.addView(kit.text("📍 کجای برنامه؟", 13f, Theme.TEXT, true), kit.lp(-1, -2));
            where.addView(kit.text(it.where, 13f, Theme.GOLD_SOFT, true), kit.lp(-1, -2));
            body.addView(where, kit.lp(-1, -2));
            body.addView(kit.gap(10));
            final AlertDialog[] box = new AlertDialog[1];
            body.addView(kit.btnGold("فهمیدم", v -> {
                try {
                    if (box[0] != null) box[0].dismiss();
                } catch (Exception ignored) { Diag.ignored("WhatsNew:291", ignored); }
            }), kit.lp(-1, -2));
            AlertDialog d = kit.dialog(it.title, kit.scrollWrap(body, 420), true);
            box[0] = d;
            d.show();
        } catch (Exception ignored) { Diag.ignored("WhatsNew:296", ignored); }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, 0);
    }

    private static void markSeen(Context c, int code) {
        try {
            prefs(c).edit().putInt("last_news_code", code).apply();
        } catch (Exception ignored) { Diag.ignored("WhatsNew:306", ignored); }
    }

    private static int appCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            if (pi == null) return 0;
            if (android.os.Build.VERSION.SDK_INT >= 28) return (int) pi.getLongVersionCode();
            @SuppressWarnings("deprecation")
            int v = pi.versionCode;
            return v;
        } catch (Exception e) {
            return 0;
        }
    }
}
