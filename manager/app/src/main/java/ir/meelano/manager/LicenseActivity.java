package ir.meelano.manager;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import java.util.Locale;

import ir.meelano.manager.core.DeviceId;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.Tamper;
import ir.meelano.licensing.License;

/**
 * Offline license gate: device-code view, customer request form, activation box.
 * Blocks MainActivity until {@link LicenseStore#unlocked} is true.
 */
public class LicenseActivity extends Activity {

    private EditText fName, fFamily, fShop, fPhone, fCity, fSeller, fPack;
    private TextView tvDevice, tvReason;
    private String device = "";

    private static final String FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹";

    private static String fa(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            b.append((ch >= '0' && ch <= '9') ? FA_DIGITS.charAt(ch - '0') : ch);
        }
        return b.toString();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ScrollWrap root = new ScrollWrap(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad, pad, pad);
        root.addView(box);
        setContentView(root);

        try {
            device = DeviceId.code(this);
        } catch (Exception e) {
            device = "";
        }

        TextView title = new TextView(this);
        title.setText("فعال‌سازی میلانو منیجر");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        tvReason = new TextView(this);
        tvReason.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvReason.setGravity(Gravity.CENTER);
        tvReason.setPadding(0, dp(6), 0, dp(2));
        box.addView(tvReason);
        refreshStatus(null);

        box.addView(section("۱) کد دستگاه شما (برای فروشنده)"));
        tvDevice = new TextView(this);
        tvDevice.setText(device.isEmpty() ? "—" : prettify(device));
        tvDevice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        tvDevice.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvDevice.setGravity(Gravity.CENTER);
        tvDevice.setPadding(0, dp(8), 0, dp(8));
        box.addView(tvDevice);
        Button bCopy = btn(box, "کپی کد دستگاه");
        bCopy.setOnClickListener(v -> {
            copyText("کد دستگاه میلانو", device);
            toast("کد دستگاه کپی شد");
        });

        box.addView(section("۲) فرم درخواست لایسنس"));
        fName = field(box, "نام");
        fFamily = field(box, "نام خانوادگی");
        fShop = field(box, "نام فروشگاه");
        fPhone = field(box, "شماره موبایل");
        fPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        fCity = field(box, "شهر");
        Button bReq = btn(box, "ساخت و ارسال متن درخواست");
        bReq.setOnClickListener(v -> shareRequest());

        box.addView(section("۳) وارد کردن کد فعال‌سازی"));
        fPack = field(box, "کد فعال‌سازی فروشنده (۳۹ حرف)");
        fPack.setTypeface(Typeface.MONOSPACE);
        Button bAct = btn(box, "فعال‌سازی");
        bAct.setOnClickListener(v -> doActivate());

        box.addView(section("شماره فروشنده (اختیاری، برای ارسال سریع)"));
        fSeller = field(box, "مثلاً 0912...");
        fSeller.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        try {
            fSeller.setText(LicenseStore.sellerPhone(this));
        } catch (Exception ignored) { }

        if (Tamper.rooted()) {
            TextView w = new TextView(this);
            w.setText("⚠ گوشی روت شده است؛ در صورت مشکل با فروشنده در میان بگذارید.");
            w.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            w.setPadding(0, dp(14), 0, 0);
            box.addView(w);
        }

        TextView hint = new TextView(this);
        hint.setText("بدون اینترنت هم کار می‌کند؛ فقط کد فعال‌سازی را از فروشنده بگیرید.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(16), 0, 0);
        box.addView(hint);
    }

    private void refreshStatus(LicenseStore.Status cached) {
        LicenseStore.Status s = cached != null ? cached : LicenseStore.check(this);
        String msg;
        if (s.ok) {
            msg = "✓ فعال است";
        } else if ("none".equals(s.reason)) {
            msg = "برنامه فعال نشده است — مراحل زیر را انجام دهید";
        } else {
            msg = s.fa;
        }
        tvReason.setText(msg);
    }

    private void shareRequest() {
        String line = License.requestLine(device,
                txt(fName), txt(fFamily), txt(fShop), txt(fPhone), txt(fCity));
        String seller = txt(fSeller);
        LicenseStore.setSellerPhone(this, seller);
        String msg = "درخواست فعال‌سازی میلانو منیجر\n" + line
                + "\nنام: " + txt(fName) + " " + txt(fFamily)
                + "\nفروشگاه: " + txt(fShop) + " — " + txt(fCity)
                + "\nموبایل: " + txt(fPhone);
        copyText("درخواست فعال‌سازی", msg);
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, msg);
            String sms = seller.replaceAll("[^0-9+]", "");
            if (sms.length() >= 10) {
                try {
                    Intent s = new Intent(Intent.ACTION_SENDTO,
                            android.net.Uri.parse("smsto:" + sms));
                    s.putExtra("sms_body", msg);
                    startActivity(Intent.createChooser(s, "ارسال درخواست"));
                    toast("متن درخواست کپی شد؛ آن را برای فروشنده بفرستید");
                    return;
                } catch (Exception ignored) {
                }
            }
            startActivity(Intent.createChooser(i, "ارسال درخواست"));
        } catch (Exception e) {
            toast("متن درخواست در حافظه کپی شد");
        }
    }

    private void doActivate() {
        String in = txt(fPack);
        if (in.isEmpty()) {
            toast("کد فعال‌سازی را وارد کنید");
            return;
        }
        LicenseStore.Status s = LicenseStore.activate(this, in);
        refreshStatus(s);
        if (s.ok) {
            String left = s.plan == License.P_PERM
                    ? "دائمی"
                    : ("«" + License.planFa(s.plan) + "» تا "
                    + fa(String.format(Locale.US, "%,d", s.daysLeft)) + " روز دیگر");
            toast("فعال شد! لایسنس " + left);
            try {
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            } catch (Exception ignored) {
            }
        } else {
            toast(s.fa);
        }
    }

    private TextView section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        v.setTypeface(null, Typeface.BOLD);
        v.setPadding(0, dp(18), 0, dp(6));
        return v;
    }

    private Button btn(LinearLayout box, String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        box.addView(b);
        return b;
    }

    private EditText field(LinearLayout box, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        box.addView(e, lp);
        return e;
    }

    private static String txt(EditText e) {
        try {
            return e.getText().toString().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private static String prettify(String code) {
        if (code == null || code.length() != 8) return code;
        return code.substring(0, 4) + "-" + code.substring(4);
    }

    private void copyText(String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        } catch (Exception ignored) { }
    }

    private void toast(String m) {
        try {
            Toast.makeText(this, m, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) { }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Minimal vertical ScrollView subclass to keep this file self-contained. */
    private static final class ScrollWrap extends android.widget.ScrollView {
        ScrollWrap(Context c) {
            super(c);
            setFillViewport(true);
        }
    }
}
