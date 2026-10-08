package ir.meelano.manager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.core.Brand;
import ir.meelano.manager.core.DeviceId;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Net;
import ir.meelano.manager.core.Tamper;
import ir.meelano.manager.core.Usage;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;
import ir.meelano.licensing.License;

/**
 * Offline license gate, v16: Wi-Fi check → device code + request form →
 * activation pack → automatic server connection (seller's encrypted card) →
 * support footer. Blocks MainActivity until {@link LicenseStore#unlocked}.
 */
public class LicenseActivity extends Activity {

    private Kit kit;
    private Settings settings;
    private Repo repo;
    private String device = "";

    private TextView wifiDot;
    private TextView wifiTxt;
    private TextView connStatus;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have just connected to Wi-Fi (or returned from the
        // dialer/browser) — refresh the Wi-Fi row in place (no rebuild, so
        // typed text in the form fields is never lost).
        updateWifiRow();
    }

    // ---------- UI ----------

    private void buildUi() {
        LinearLayout box = kit.v();
        box.setBackgroundColor(Theme.BG);
        int pad = Theme.dp(16);
        box.setPadding(pad, pad, pad, pad);

        LinearLayout.LayoutParams llp =
                new LinearLayout.LayoutParams(Theme.dp(76), Theme.dp(76));
        llp.gravity = Gravity.CENTER;
        box.addView(kit.logo(76), llp);
        TextView title = kit.text("فعال‌سازی میلانو منیجر", 21, Theme.TEXT, true);
        title.setGravity(Gravity.CENTER);
        box.addView(title, kit.lp(-1, -2));
        TextView sub = kit.text("کمتر از ۲ دقیقه • بدون نیاز به اینترنت", 12,
                Theme.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub, kit.lp(-1, -2));
        box.addView(kit.gap(10));

        buildStatusCard(box);
        buildWifiCard(box);
        buildRequestCard(box);
        buildActivationCard(box);
        buildConnCard(box);
        buildSupportCard(box);

        if (Tamper.isRooted()) {
            box.addView(kit.hint("⚠ گوشی روت شده است؛ در صورت مشکل با پشتیبانی در میان بگذارید."),
                    kit.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(box);
        setContentView(sv);
        updateWifiRow();
    }

    // ---- status ----

    private void buildStatusCard(LinearLayout box) {
        LicenseStore.Status s = LicenseStore.check(this);
        LinearLayout c = kit.card(s.ok ? Theme.GOLD : Theme.DANGER);
        if (s.ok) {
            c.addView(kit.text("✓ لایسنس فعال است", 15, Theme.TEXT, true), kit.lp(-1, -2));
            c.addView(kit.kv("طرح", "«" + License.planFa(s.plan) + "»", Theme.GOLD_SOFT),
                    kit.lp(-1, -2));
            if (s.plan == License.P_PERM) {
                c.addView(kit.kv("اعتبار", "دائمی ♾", Theme.SUCCESS), kit.lp(-1, -2));
            } else {
                c.addView(kit.kv("زمان باقی‌مانده",
                        Money.fa(String.valueOf(s.daysLeft)) + " روز", leftColor(s.daysLeft)),
                        kit.lp(-1, -2));
                c.addView(kit.kv("اعتبار تا",
                        Money.fa(Jalali.format((int) (s.expDay + 2440588L))), Theme.TEXT),
                        kit.lp(-1, -2));
            }
            String use = Usage.faSummary(this);
            if (!use.isEmpty()) {
                c.addView(kit.kv("کارکرد شما", use, Theme.MUTED), kit.lp(-1, -2));
            }
            c.addView(kit.btnGold("ورود به برنامه ⇤", v -> enterApp()), kit.lp(-1, -2));
            c.addView(kit.gap(6));
            c.addView(kit.btnGhost("ارسال گزارش مصرف به فروشنده", Theme.TEAL, v -> shareUsage()),
                    kit.lp(-1, -2));
        } else {
            c.addView(kit.text("فعال‌سازی لازم است", 15, Theme.TEXT, true), kit.lp(-1, -2));
            String msg = "none".equals(s.reason)
                    ? "برنامه هنوز فعال نشده — مراحل زیر را به ترتیب انجام دهید."
                    : s.fa;
            c.addView(kit.hint(msg), kit.lp(-1, -2));
        }
        kit.addCard(box, c);
    }

    private static int leftColor(long daysLeft) {
        if (daysLeft <= 7) return Theme.DANGER;
        if (daysLeft <= 30) return Theme.WARNING;
        return Theme.SUCCESS;
    }

    // ---- Wi-Fi ----

    private void buildWifiCard(LinearLayout box) {
        LinearLayout c = kit.card(Theme.TEAL);
        c.addView(kit.text("۱) اتصال به وای‌فای فروشگاه", 14.5f, Theme.TEXT, true),
                kit.lp(-1, -2));
        LinearLayout row = kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        wifiDot = kit.text("●", 16, Theme.WARNING, true);
        row.addView(wifiDot, kit.lp(-2, -2));
        row.addView(kit.space(8));
        wifiTxt = kit.text("در حال بررسی…", 13.5f, Theme.TEXT, false);
        row.addView(wifiTxt, kit.wlp(1f));
        c.addView(row, kit.lp(-1, -2));
        c.addView(kit.hint("سرور فروشگاه فقط از طریق وای‌فای داخلی در دسترس است؛ "
                + "اگر به وای‌فای وصل نیستید، ابتدا وصل شوید و «بررسی مجدد» را بزنید."),
                kit.lp(-1, -2));
        c.addView(kit.btnGhost("بررسی مجدد", Theme.TEAL, v -> updateWifiRow()), kit.lp(-1, -2));
        kit.addCard(box, c);
    }

    private void updateWifiRow() {
        if (wifiDot == null || wifiTxt == null) return;
        try {
            if (Net.wifi(this)) {
                wifiDot.setTextColor(Theme.SUCCESS);
                wifiTxt.setText("متصل به وای‌فای ✓");
                wifiTxt.setTextColor(Theme.SUCCESS);
            } else if (Net.online(this)) {
                wifiDot.setTextColor(Theme.WARNING);
                wifiTxt.setText("به وای‌فای وصل نیستید (اینترنت موبایل جواب نمی‌دهد)");
                wifiTxt.setTextColor(Theme.WARNING);
            } else {
                wifiDot.setTextColor(Theme.DANGER);
                wifiTxt.setText("هیچ اتصالی نیست — وای‌فای را روشن کنید");
                wifiTxt.setTextColor(Theme.DANGER);
            }
        } catch (Exception ignored) { }
    }

    // ---- request ----

    private void buildRequestCard(LinearLayout box) {
        LinearLayout c = kit.card(Theme.GOLD);
        c.addView(kit.text("۲) درخواست لایسنس از فروشنده", 14.5f, Theme.TEXT, true),
                kit.lp(-1, -2));
        c.addView(kit.text("کد دستگاه شما", 12f, Theme.MUTED, true), kit.lp(-1, -2));
        TextView dev = kit.text(device.isEmpty() ? "—" : prettyDev(device), 26,
                Theme.GOLD_SOFT, true);
        dev.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        dev.setGravity(Gravity.CENTER);
        c.addView(dev, kit.lp(-1, -2));
        c.addView(kit.btnGhost("کپی کد دستگاه", Theme.GOLD, v -> {
            copyText("کد دستگاه میلانو", device);
            kit.toast("کد دستگاه کپی شد");
        }), kit.lp(-1, -2));
        final EditText fName = kit.edit("نام", "");
        final EditText fFamily = kit.edit("نام خانوادگی", "");
        final EditText fShop = kit.edit("نام فروشگاه", "");
        final EditText fPhone = kit.edit("شماره موبایل", "");
        try {
            fPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        } catch (Exception ignored) { }
        final EditText fCity = kit.edit("شهر", "");
        final EditText fSeller = kit.edit("شماره فروشنده (اختیاری، برای ارسال سریع)", "");
        try {
            fSeller.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            fSeller.setText(LicenseStore.sellerPhone(this));
        } catch (Exception ignored) { }
        for (EditText e : new EditText[]{fName, fFamily, fShop, fPhone, fCity, fSeller}) {
            LinearLayout.LayoutParams p = kit.lp(-1, -2);
            p.setMargins(0, Theme.dp(4), 0, Theme.dp(4));
            c.addView(e, p);
        }
        c.addView(kit.btnGold("ساخت و ارسال متن درخواست", v ->
                shareRequest(txt(fName), txt(fFamily), txt(fShop), txt(fPhone),
                        txt(fCity), txt(fSeller))), kit.lp(-1, -2));
        kit.addCard(box, c);
    }

    private void shareRequest(String name, String family, String shop, String phone,
                              String city, String seller) {
        String line = License.requestLine(device, name, family, shop, phone, city);
        LicenseStore.setSellerPhone(this, seller);
        String use = Usage.reportLine(this, device);
        String msg = "درخواست فعال‌سازی میلانو منیجر\n" + line
                + (use.isEmpty() ? "" : ("\n" + use))
                + "\nنام: " + name + " " + family
                + "\nفروشگاه: " + shop + " — " + city
                + "\nموبایل: " + phone;
        copyText("درخواست فعال‌سازی", msg);
        try {
            String sms = seller.replaceAll("[^0-9+]", "");
            if (sms.length() >= 10) {
                try {
                    Intent s = new Intent(Intent.ACTION_SENDTO,
                            android.net.Uri.parse("smsto:" + sms));
                    s.putExtra("sms_body", msg);
                    startActivity(Intent.createChooser(s, "ارسال درخواست"));
                    kit.toast("متن درخواست کپی شد؛ آن را برای فروشنده بفرستید");
                    return;
                } catch (Exception ignored) { }
            }
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, msg);
            startActivity(Intent.createChooser(i, "ارسال درخواست"));
        } catch (Exception e) {
            kit.toast("متن درخواست در حافظه کپی شد");
        }
    }

    private void shareUsage() {
        String use = Usage.reportLine(this, device);
        if (use.isEmpty()) {
            kit.toast("گزارشی برای ارسال نیست");
            return;
        }
        String msg = "گزارش مصرف میلانو منیجر\n" + use
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

    // ---- activation ----

    private void buildActivationCard(LinearLayout box) {
        LinearLayout c = kit.card(Theme.VIOLET);
        c.addView(kit.text("۳) وارد کردن کد فعال‌سازی", 14.5f, Theme.TEXT, true),
                kit.lp(-1, -2));
        final EditText fPack = kit.edit("کد ۳۹ حرفی فروشنده", "");
        try {
            fPack.setTypeface(Typeface.MONOSPACE);
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams p = kit.lp(-1, -2);
        p.setMargins(0, Theme.dp(4), 0, Theme.dp(8));
        c.addView(fPack, p);
        c.addView(kit.btnGold("فعال‌سازی", v -> doActivate(txt(fPack))), kit.lp(-1, -2));
        kit.addCard(box, c);
    }

    private void doActivate(String in) {
        if (in.isEmpty()) {
            kit.toast("کد فعال‌سازی را وارد کنید");
            return;
        }
        LicenseStore.Status s = LicenseStore.activate(this, in);
        if (s.ok) {
            String left = s.plan == License.P_PERM ? "دائمی ♾"
                    : ("«" + License.planFa(s.plan) + "» • "
                    + Money.fa(String.valueOf(s.daysLeft)) + " روز");
            kit.toast("فعال شد! لایسنس " + left);
            buildUi();
        } else {
            kit.toast(s.fa);
        }
    }

    // ---- automatic server connection ----

    private void buildConnCard(LinearLayout box) {
        LinearLayout c = kit.card(Theme.SUCCESS);
        c.addView(kit.text("۴) اتصال خودکار به سرور فروشگاه", 14.5f, Theme.TEXT, true),
                kit.lp(-1, -2));
        connStatus = kit.text(connSummary(), 13, connConfiguredColor(), false);
        c.addView(connStatus, kit.lp(-1, -2));
        final EditText fCard = kit.edit("کارت اتصال فروشنده (یک متن طولانی)…", "");
        fCard.setMinLines(3);
        try {
            fCard.setTypeface(Typeface.MONOSPACE);
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams p = kit.lp(-1, -2);
        p.setMargins(0, Theme.dp(6), 0, Theme.dp(6));
        c.addView(fCard, p);
        LinearLayout row = kit.h();
        row.addView(kit.btnGhost("چسباندن", Theme.MUTED, v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.getPrimaryClip() != null
                        && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                    if (t != null) fCard.setText(t.toString().trim());
                }
            } catch (Exception ignored) { }
        }), kit.wlp(1f));
        row.addView(kit.space(8));
        row.addView(kit.btnGold("ثبت و تست اتصال", v -> applyCard(txt(fCard))), kit.wlp(1f));
        c.addView(row, kit.lp(-1, -2));
        c.addView(kit.hint("کارت اتصال را فروشنده برایتان می‌فرستد؛ با یک لمس، "
                + "آدرس سرور و همه تنظیمات به‌صورت خودکار ثبت و تست می‌شود — "
                + "نیازی به وارد کردن هیچ عددی نیست."), kit.lp(-1, -2));
        kit.addCard(box, c);
    }

    private String connSummary() {
        try {
            if (settings.connConfigured()) {
                return "✓ اتصال قبلاً تنظیم شده (سرور " + settings.maskedHost() + ")";
            }
        } catch (Exception ignored) { }
        return "هنوز تنظیم نشده — کارت اتصال را از فروشنده بگیرید و بالا بچسبانید.";
    }

    private int connConfiguredColor() {
        try {
            return settings.connConfigured() ? Theme.SUCCESS : Theme.MUTED;
        } catch (Exception e) {
            return Theme.MUTED;
        }
    }

    private void applyCard(String pasted) {
        if (pasted.isEmpty()) {
            kit.toast("کارت اتصال را بچسبانید");
            return;
        }
        License.DbProfile prof = License.parseDbCard(pasted, device);
        if (prof == null) {
            kit.toast("کارت معتبر نیست؛ مطمئن شوید کارت همین گوشی را چسبانده‌اید");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(prof.port.trim());
        } catch (Exception e) {
            kit.toast("کارت خراب است؛ از فروشنده کارت تازه بخواهید");
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
                    settings.saveConnection(fh, String.valueOf(port), fd, fu, fp);
                } catch (Exception ignored) { }
                kit.toast("✓ " + v);
                try {
                    if (connStatus != null) {
                        connStatus.setText(connSummary());
                        connStatus.setTextColor(connConfiguredColor());
                    }
                } catch (Exception ignored) { }
            }

            @Override
            public void fail(String faError) {
                kit.toast(faError == null || faError.isEmpty()
                        ? "اتصال برقرار نشد؛ به وای‌فای فروشگاه وصل شوید و دوباره تلاش کنید"
                        : faError);
            }
        });
    }

    // ---- support ----

    private void buildSupportCard(LinearLayout box) {
        LinearLayout c = kit.card(Theme.GOLD);
        c.addView(kit.text("✦ پشتیبانی میلانو", 14.5f, Theme.TEXT, true), kit.lp(-1, -2));
        c.addView(kit.btnGhost("🌐  " + Brand.SITE_LABEL, Theme.GOLD,
                v -> Brand.openSite(this)), kit.lp(-1, -2));
        for (String ph : Brand.PHONES) {
            final String fph = ph;
            c.addView(kit.btnGhost("📞  " + Money.fa(ph), Theme.TEAL,
                    v -> Brand.dial(this, fph)), kit.lp(-1, -2));
        }
        TextView tag = kit.text(Brand.TAGLINE, 11.5f, Theme.MUTED, false);
        tag.setGravity(Gravity.CENTER);
        c.addView(tag, kit.lp(-1, -2));
        kit.addCard(box, c);
    }

    // ---------- misc ----------

    private void enterApp() {
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

    private static String prettyDev(String code) {
        if (code == null || code.length() != 8) return code == null ? "" : code;
        return code.substring(0, 4) + "-" + code.substring(4);
    }

    private void copyText(String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        } catch (Exception ignored) { }
    }
}
