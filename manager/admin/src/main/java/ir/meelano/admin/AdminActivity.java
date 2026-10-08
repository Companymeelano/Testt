package ir.meelano.admin;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import ir.meelano.licensing.License;

/**
 * Seller app: paste a customer request → mint a signed pack → share it back.
 * Fully offline; everything lives in a local SQLite database.
 */
public class AdminActivity extends Activity {

    private AdminDb db;
    private final Deque<Runnable> stack = new ArrayDeque<>();
    private int licFilter = 0;
    private String custQuery = "";

    /** Draft carried from «new request» / customer / extend into the mint screen. */
    private static final class MintCtx {
        long customerId;
        String dev = "", name = "", family = "", shop = "", phone = "", city = "", note = "";
        char plan = License.P_MONTHLY;
        long extendOf;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        try {
            getWindow().setStatusBarColor(AdminKit.BG);
            getWindow().setNavigationBarColor(AdminKit.BG);
        } catch (Exception ignored) { }
        db = new AdminDb(this);
        show(this::showHome);
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            stack.pop();
            try {
                stack.peek().run();
            } catch (Exception e) {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    // ---------- navigation ----------

    private void show(Runnable s) {
        stack.push(s);
        s.run();
    }

    private void reload() {
        Runnable s = stack.peek();
        if (s != null) s.run();
    }

    private void home() {
        stack.clear();
        licFilter = 0;
        custQuery = "";
        show(this::showHome);
    }

    private void root(LinearLayout box) {
        box.setBackgroundColor(AdminKit.BG);
        setContentView(AdminKit.scroll(this, box, 16));
    }

    // ---------- home ----------

    private void showHome() {
        LinearLayout box = AdminKit.vbox(this);
        box.addView(AdminKit.titleBar(this, "میلانو • فروش لایسنس", null));

        long today = License.today();
        int nc = db.customerCount();
        int na = db.activeCount(today);
        List<AdminDb.Lic> soon = db.expiringSoon(today, 30);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("مشتریان", AdminKit.fa(nc), R.drawable.mi_person),
                w(1f));
        stats.addView(statCard("لایسنس فعال", AdminKit.fa(na), R.drawable.mi_check_circle),
                w(1f));
        stats.addView(statCard("رو به اتمام", AdminKit.fa(soon.size()), R.drawable.mi_calendar_month),
                w(1f));
        box.addView(stats);

        Button bReq = AdminKit.btn(this, "＋  درخواست جدید (چسباندن متن مشتری)", true);
        bReq.setOnClickListener(v -> show(this::showRequest));
        box.addView(bReq);

        Button bCust = AdminKit.btn(this, "مشتریان", false);
        bCust.setOnClickListener(v -> show(() -> showCustomers()));
        box.addView(bCust);

        Button bLic = AdminKit.btn(this, "لایسنس‌ها", false);
        bLic.setOnClickListener(v -> show(() -> showLicenses()));
        box.addView(bLic);

        Button bExp = AdminKit.btn(this, "خروجی CSV (مشتریان + لایسنس‌ها)", false);
        bExp.setOnClickListener(v -> exportCsv());
        box.addView(bExp);

        box.addView(AdminKit.text(this, "آخرین لایسنس‌ها", 15, AdminKit.GOLD_SOFT, true));
        List<AdminDb.Lic> recent = db.licenses(0, today);
        if (recent.isEmpty()) {
            box.addView(AdminKit.text(this, "هنوز کدی صادر نشده است.", 13,
                    AdminKit.MUTED, false));
        } else {
            int n = Math.min(8, recent.size());
            for (int i = 0; i < n; i++) box.addView(licRow(recent.get(i), today));
        }
        root(box);
    }

    private LinearLayout.LayoutParams w(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        int m = AdminKit.dp(this, 4);
        lp.setMargins(m, 0, m, AdminKit.dp(this, 10));
        return lp;
    }

    private View statCard(String label, String value, int icon) {
        LinearLayout c = (LinearLayout) AdminKit.card(this);
        c.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        try {
            iv.setImageResource(icon);
            iv.setColorFilter(AdminKit.GOLD);
        } catch (Exception ignored) { }
        int s = AdminKit.dp(this, 24);
        c.addView(iv, new LinearLayout.LayoutParams(s, s));
        TextView v = AdminKit.text(this, value, 20, AdminKit.TEXT, true);
        v.setGravity(Gravity.CENTER);
        c.addView(v);
        TextView l = AdminKit.text(this, label, 11.5f, AdminKit.MUTED, false);
        l.setGravity(Gravity.CENTER);
        c.addView(l);
        return c;
    }

    // ---------- new request ----------

    private void showRequest() {
        LinearLayout box = AdminKit.vbox(this);
        box.addView(AdminKit.titleBar(this, "درخواست جدید", this::goBack));

        box.addView(AdminKit.text(this,
                "متن ارسالی مشتری را اینجا بچسبانید و «تحلیل» را بزنید.", 13,
                AdminKit.MUTED, false));
        EditText paste = AdminKit.field(this, "متن درخواست مشتری…");
        paste.setMinLines(4);
        paste.setGravity(Gravity.TOP);
        box.addView(paste);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button bPaste = AdminKit.btn(this, "چسباندن", false);
        bPaste.setLayoutParams(w(1f));
        bPaste.setOnClickListener(v -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                if (cm != null && cm.getPrimaryClip() != null
                        && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                    if (t != null) paste.setText(t.toString().trim());
                }
            } catch (Exception ignored) { }
        });
        Button bParse = AdminKit.btn(this, "تحلیل درخواست", true);
        bParse.setLayoutParams(w(1f));
        btnRow.addView(bPaste);
        btnRow.addView(bParse);
        box.addView(btnRow);

        LinearLayout out = AdminKit.vbox(this);
        box.addView(out);

        bParse.setOnClickListener(v -> {
            out.removeAllViews();
            License.Req req = License.parseRequest(AdminKit.txt(paste));
            if (req == null) {
                out.addView(AdminKit.text(this,
                        "✕ متن معتبر نیست. خط MILANO-REQ1 داخل آن پیدا نشد؛ از مشتری بخواهید متن کامل را بفرستد.",
                        14, AdminKit.RED, false));
                return;
            }
            MintCtx ctx = new MintCtx();
            ctx.dev = req.dev;
            ctx.name = req.name;
            ctx.family = req.family;
            ctx.shop = req.shop;
            ctx.phone = req.phone;
            ctx.city = req.city;

            LinearLayout card = (LinearLayout) AdminKit.card(this);
            AdminKit.cardMargin(card, this);
            AdminKit.infoRow(card, this, "کد دستگاه", prettyDev(req.dev), true);
            AdminKit.infoRow(card, this, "نام", (req.name + " " + req.family).trim(), false);
            AdminKit.infoRow(card, this, "فروشگاه", req.shop, false);
            AdminKit.infoRow(card, this, "موبایل", req.phone, false);
            AdminKit.infoRow(card, this, "شهر", req.city, false);
            out.addView(card);

            AdminDb.Customer dup = db.byDev(req.dev);
            if (dup != null) {
                ctx.customerId = dup.id;
                TextView warn = AdminKit.text(this,
                        "⚠ این دستگاه قبلاً به نام «" + dup.full() + "» ثبت شده؛ صدور کد جدید برای همان مشتری انجام می‌شود.",
                        13.5f, AdminKit.GOLD_SOFT, false);
                warn.setPadding(0, 0, 0, AdminKit.dp(this, 8));
                out.addView(warn);
            } else {
                out.addView(AdminKit.text(this, "✓ درخواست معتبر است؛ مشتری جدید.",
                        14, AdminKit.GREEN, false));
            }
            Button go = AdminKit.btn(this, "ادامه → انتخاب طرح و صدور کد", true);
            go.setOnClickListener(x -> show(() -> showMint(ctx)));
            out.addView(go);
        });
        root(box);
    }

    // ---------- mint ----------

    private void showMint(final MintCtx ctx) {
        LinearLayout box = AdminKit.vbox(this);
        box.addView(AdminKit.titleBar(this,
                ctx.extendOf > 0 ? "تمدید لایسنس" : "صدور کد فعال‌سازی", this::goBack));

        LinearLayout card = (LinearLayout) AdminKit.card(this);
        AdminKit.cardMargin(card, this);
        EditText fDev = AdminKit.field(this, "کد دستگاه (۸ حرف)");
        fDev.setText(ctx.dev);
        fDev.setTypeface(AdminKit.mon(this));
        card.addView(fDev);
        EditText fName = AdminKit.field(this, "نام");
        fName.setText(ctx.name);
        card.addView(fName);
        EditText fFamily = AdminKit.field(this, "نام خانوادگی");
        fFamily.setText(ctx.family);
        card.addView(fFamily);
        EditText fShop = AdminKit.field(this, "نام فروشگاه");
        fShop.setText(ctx.shop);
        card.addView(fShop);
        EditText fPhone = AdminKit.field(this, "موبایل");
        fPhone.setText(ctx.phone);
        card.addView(fPhone);
        EditText fCity = AdminKit.field(this, "شهر");
        fCity.setText(ctx.city);
        card.addView(fCity);
        EditText fNote = AdminKit.field(this, "یادداشت فروش (اختیاری)");
        fNote.setText(ctx.note);
        card.addView(fNote);
        box.addView(card);

        box.addView(AdminKit.text(this, "طرح لایسنس", 15, AdminKit.GOLD_SOFT, true));
        char[] plans = {License.P_TRIAL, License.P_WEEKLY, License.P_MONTHLY,
                License.P_YEARLY, License.P_PERM};
        for (char p : plans) {
            boolean sel = ctx.plan == p;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout inner = (LinearLayout) AdminKit.card(this);
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(Gravity.CENTER_VERTICAL);
            AdminKit.cardMargin(inner, this);
            ImageView iv = new ImageView(this);
            try {
                iv.setImageResource(sel ? R.drawable.mi_check_circle
                        : R.drawable.mi_radio_button_unchecked);
                iv.setColorFilter(sel ? AdminKit.GOLD : AdminKit.MUTED);
            } catch (Exception ignored) { }
            int s = AdminKit.dp(this, 22);
            inner.addView(iv, new LinearLayout.LayoutParams(s, s));
            LinearLayout tt = AdminKit.vbox(this);
            tt.setPadding(AdminKit.dp(this, 10), 0, 0, 0);
            tt.addView(AdminKit.text(this, "«" + License.planFa(p) + "»", 15,
                    sel ? AdminKit.TEXT : AdminKit.MUTED, sel));
            tt.addView(AdminKit.text(this, planDesc(p), 12, AdminKit.MUTED, false));
            inner.addView(tt);
            final char fp = p;
            AdminKit.rowTap(inner, () -> {
                ctx.plan = fp;
                ctx.dev = AdminKit.txt(fDev);
                ctx.name = AdminKit.txt(fName);
                ctx.family = AdminKit.txt(fFamily);
                ctx.shop = AdminKit.txt(fShop);
                ctx.phone = AdminKit.txt(fPhone);
                ctx.city = AdminKit.txt(fCity);
                ctx.note = AdminKit.txt(fNote);
                reload();
            });
            row.addView(inner, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            box.addView(row);
        }

        TextView expPrev = AdminKit.text(this, expiryPreview(ctx.plan), 13.5f,
                AdminKit.GOLD_SOFT, false);
        expPrev.setGravity(Gravity.CENTER);
        expPrev.setPadding(0, AdminKit.dp(this, 4), 0, AdminKit.dp(this, 4));
        box.addView(expPrev);

        if (ctx.extendOf > 0) {
            box.addView(AdminKit.text(this,
                    "تمدید: کد تازه صادر می‌شود و رکورد قبلی در تاریخچه می‌ماند.",
                    12.5f, AdminKit.MUTED, false));
        }

        Button issue = AdminKit.btn(this, "صدور کد", true);
        issue.setOnClickListener(v -> {
            ctx.dev = AdminKit.txt(fDev).toUpperCase()
                    .replaceAll("[^A-Z0-9]", "");
            ctx.name = AdminKit.txt(fName);
            ctx.family = AdminKit.txt(fFamily);
            ctx.shop = AdminKit.txt(fShop);
            ctx.phone = AdminKit.txt(fPhone);
            ctx.city = AdminKit.txt(fCity);
            ctx.note = AdminKit.txt(fNote);
            if (ctx.dev.length() != 8) {
                AdminKit.toast(this, "کد دستگاه باید ۸ حرف باشد");
                return;
            }
            String pack;
            try {
                pack = License.mint(ctx.dev, ctx.plan);
            } catch (Exception e) {
                AdminKit.toast(this, "صدور ناموفق بود");
                return;
            }
            License.Result r = License.parse(pack);
            if (!r.ok) {
                AdminKit.toast(this, "خطای داخلی در ساخت کد");
                return;
            }
            long cid = db.upsertCustomer(ctx.name, ctx.family, ctx.shop,
                    ctx.phone, ctx.city, ctx.dev);
            long lid = db.insertLicense(cid, ctx.dev, ctx.plan, r.expDay,
                    r.iatDay, r.pack,
                    ctx.extendOf > 0 ? ("تمدید #" + ctx.extendOf
                            + (ctx.note.isEmpty() ? "" : " — " + ctx.note))
                            : ctx.note);
            if (lid < 0) {
                AdminKit.toast(this, "ذخیره در دیتابیس ناموفق بود");
                return;
            }
            final long flid = lid;
            show(() -> showMintResult(flid));
        });
        box.addView(issue);
        root(box);
    }

    private String planDesc(char p) {
        if (p == License.P_TRIAL) return "تست و آشنایی مشتری";
        if (p == License.P_WEEKLY) return "۷ روزه، مناسب مغازه‌های سیار";
        if (p == License.P_MONTHLY) return "۳۰ روزه — پرفروش‌ترین";
        if (p == License.P_YEARLY) return "۳۶۵ روزه، یک‌سال کامل";
        return "بدون تاریخ انقضا (ویژه)";
    }

    private String expiryPreview(char plan) {
        if (plan == License.P_PERM) return "انقضا: بدون انقضا (دائمی)";
        long exp = License.today() + License.planDays(plan);
        return "انقضا: " + AdminKit.fa(jalali(exp)) + "  ("
                + AdminKit.fa(License.planDays(plan)) + " روز دیگر)";
    }

    private void showMintResult(long licId) {
        AdminDb.Lic l = db.licById(licId);
        if (l == null) {
            home();
            return;
        }
        LinearLayout box = AdminKit.vbox(this);
        box.addView(AdminKit.titleBar(this, "کد صادر شد ✓", null));

        LinearLayout card = (LinearLayout) AdminKit.card(this);
        AdminKit.cardMargin(card, this);
        card.addView(AdminKit.text(this, "کد فعال‌سازی «" + l.customer + "»", 13,
                AdminKit.MUTED, false));
        TextView pack = AdminKit.text(this, AdminKit.prettifyPack(l.pack), 15,
                AdminKit.GOLD_SOFT, true);
        pack.setTypeface(AdminKit.mon(this));
        try {
            pack.setTextIsSelectable(true);
        } catch (Exception ignored) { }
        pack.setGravity(Gravity.CENTER);
        pack.setPadding(0, AdminKit.dp(this, 10), 0, AdminKit.dp(this, 10));
        card.addView(pack);
        AdminKit.infoRow(card, this, "طرح",
                "«" + License.planFa(planOf(l)) + "»", false);
        AdminKit.infoRow(card, this, "انقضا", expText(l), false);
        box.addView(card);

        Button bCopy = AdminKit.btn(this, "کپی کد", true);
        bCopy.setOnClickListener(v -> {
            AdminKit.copy(this, "کد فعال‌سازی", l.pack);
            AdminKit.toast(this, "کد کپی شد");
        });
        box.addView(bCopy);

        Button bShare = AdminKit.btn(this, "اشتراک‌گذاری برای مشتری", false);
        bShare.setOnClickListener(v -> AdminKit.share(this, "ارسال کد فعال‌سازی",
                shareText(l)));
        box.addView(bShare);

        Button bHome = AdminKit.btn(this, "بازگشت به خانه", false);
        bHome.setOnClickListener(v -> home());
        box.addView(bHome);
        root(box);
    }

    private String shareText(AdminDb.Lic l) {
        return "کد فعال‌سازی میلانو منیجر\n"
                + AdminKit.prettifyPack(l.pack) + "\n"
                + "طرح: «" + License.planFa(planOf(l)) + "»\n"
                + "انقضا: " + expText(l) + "\n"
                + "راهنما: در برنامه، بخش ۳ (وارد کردن کد فعال‌سازی)، این کد را وارد کنید.";
    }

    // ---------- customers ----------

    private void showCustomers() {
        LinearLayout box = AdminKit.vbox(this);
        box.addView(AdminKit.titleBar(this, "مشتریان", this::goBack));

        EditText q = AdminKit.field(this, "جستجو: نام، فروشگاه، موبایل، شهر، کد دستگاه…");
        q.setText(custQuery);
        box.addView(q);
        Button bGo = AdminKit.btn(this, "جستجو", false);
        LinearLayout list = AdminKit.vbox(this);
        box.addView(list);
        bGo.setOnClickListener(v -> {
            custQuery = AdminKit.txt(q);
            reload();
        });
        box.addView(bGo, 2);

        List<AdminDb.Customer> rows = db.searchCustomers(custQuery);
        if (rows.isEmpty()) {
            list.addView(AdminKit.text(this, "مشتری‌ای پیدا نشد.", 13,
                    AdminKit.MUTED, false));
        } else {
            list.addView(AdminKit.text(this,
                    AdminKit.fa(rows.size()) + " مشتری", 12.5f, AdminKit.MUTED, false));
            for (AdminDb.Customer c : rows) {
                LinearLayout card = (LinearLayout) AdminKit.card(this);
                AdminKit.cardMargin(card, this);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(AdminKit.icon(this, R.drawable.mi_person, AdminKit.GOLD));
                LinearLayout tt = AdminKit.vbox(this);
                tt.addView(AdminKit.text(this, c.full(), 15.5f, AdminKit.TEXT, true));
                String sub = c.shop.isEmpty() ? c.city
                        : (c.shop + (c.city.isEmpty() ? "" : " • " + c.city));
                if (!sub.isEmpty()) {
                    tt.addView(AdminKit.text(this, sub, 12.5f, AdminKit.MUTED, false));
                }
                TextView dev = AdminKit.text(this, prettyDev(c.dev), 12,
                        AdminKit.GOLD_SOFT, false);
                dev.setTypeface(AdminKit.mon(this));
                tt.addView(dev);
                row.addView(tt);
                card.addView(row);
                final long cid = c.id;
                AdminKit.rowTap(card, () -> show(() -> showCustomer(cid)));
                list.addView(card);
            }
        }

        Button bManual = AdminKit.btn(this, "＋ صدور دستی (بدون متن درخواست)", false);
        bManual.setOnClickListener(v -> show(() -> showMint(new MintCtx())));
        box.addView(bManual);
        root(box);
    }

    private void showCustomer(long id) {
        AdminDb.Customer c = db.byId(id);
        if (c == null) {
            goBack();
            return;
        }
        LinearLayout box = AdminKit.vbox(this);
        box.addView(AdminKit.titleBar(this, c.full(), this::goBack));

        LinearLayout card = (LinearLayout) AdminKit.card(this);
        AdminKit.cardMargin(card, this);
        AdminKit.infoRow(card, this, "فروشگاه", c.shop, false);
        AdminKit.infoRow(card, this, "موبایل", c.phone, false);
        AdminKit.infoRow(card, this, "شهر", c.city, false);
        AdminKit.infoRow(card, this, "کد دستگاه", prettyDev(c.dev), true);
        box.addView(card);

        Button bNew = AdminKit.btn(this, "کد جدید برای این مشتری", true);
        bNew.setOnClickListener(v -> {
            MintCtx ctx = new MintCtx();
            ctx.customerId = c.id;
            ctx.dev = c.dev;
            ctx.name = c.name;
            ctx.family = c.family;
            ctx.shop = c.shop;
            ctx.phone = c.phone;
            ctx.city = c.city;
            show(() -> showMint(ctx));
        });
        box.addView(bNew);

        box.addView(AdminKit.text(this, "لایسنس‌های این مشتری", 15,
                AdminKit.GOLD_SOFT, true));
        long today = License.today();
        List<AdminDb.Lic> lics = db.licensesForCustomer(c.id);
        if (lics.isEmpty()) {
            box.addView(AdminKit.text(this, "هنوز کدی صادر نشده است.", 13,
                    AdminKit.MUTED, false));
        } else {
            for (AdminDb.Lic l : lics) box.addView(licRow(l, today));
        }

        Button bDel = AdminKit.btn(this, "حذف مشتری", false);
        if (lics.isEmpty()) {
            confirmStep(bDel, "تأیید حذف مشتری؟", () -> {
                if (db.deleteCustomer(c.id)) {
                    AdminKit.toast(this, "مشتری حذف شد");
                    goBack();
                }
            });
        } else {
            bDel.setOnClickListener(v -> AdminKit.toast(this,
                    "اول لایسنس‌های این مشتری را حذف کنید"));
        }
        box.addView(bDel);
        root(box);
    }

    // ---------- licenses ----------

    private void showLicenses() {
        LinearLayout box = AdminKit.vbox(this);
        box.addView(AdminKit.titleBar(this, "لایسنس‌ها", this::goBack));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[] titles = {"همه", "فعال", "منقضی", "لغوشده"};
        for (int i = 0; i < titles.length; i++) {
            final int fi = i;
            Button ch = AdminKit.btn(this, titles[i], licFilter == i);
            ch.setLayoutParams(w(1f));
            ch.setOnClickListener(v -> {
                licFilter = fi;
                reload();
            });
            chips.addView(ch);
        }
        box.addView(chips);

        long today = License.today();
        List<AdminDb.Lic> rows = db.licenses(licFilter, today);
        if (rows.isEmpty()) {
            box.addView(AdminKit.text(this, "موردی نیست.", 13, AdminKit.MUTED, false));
        } else {
            for (AdminDb.Lic l : rows) box.addView(licRow(l, today));
        }
        root(box);
    }

    private View licRow(AdminDb.Lic l, long today) {
        LinearLayout card = (LinearLayout) AdminKit.card(this);
        AdminKit.cardMargin(card, this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int st = licState(l, today);
        row.addView(AdminKit.icon(this, R.drawable.mi_key,
                st == 0 ? AdminKit.GREEN : (st == 2 ? AdminKit.RED : AdminKit.MUTED)));
        LinearLayout tt = AdminKit.vbox(this);
        tt.addView(AdminKit.text(this, l.customer, 14.5f, AdminKit.TEXT, true));
        tt.addView(AdminKit.text(this,
                "«" + License.planFa(planOf(l)) + "» • " + statusText(l, today),
                12.5f, st == 0 ? AdminKit.GREEN
                        : (st == 2 ? AdminKit.RED : AdminKit.MUTED),
                false));
        row.addView(tt);
        card.addView(row);
        final long lid = l.id;
        AdminKit.rowTap(card, () -> show(() -> showLicense(lid)));
        return card;
    }

    private void showLicense(long id) {
        AdminDb.Lic l = db.licById(id);
        if (l == null) {
            goBack();
            return;
        }
        long today = License.today();
        LinearLayout box = AdminKit.vbox(this);
        box.addView(AdminKit.titleBar(this, "لایسنس «" + l.customer + "»", this::goBack));

        LinearLayout card = (LinearLayout) AdminKit.card(this);
        AdminKit.cardMargin(card, this);
        TextView pack = AdminKit.text(this, AdminKit.prettifyPack(l.pack), 14.5f,
                AdminKit.GOLD_SOFT, true);
        pack.setTypeface(AdminKit.mon(this));
        try {
            pack.setTextIsSelectable(true);
        } catch (Exception ignored) { }
        pack.setGravity(Gravity.CENTER);
        pack.setPadding(0, AdminKit.dp(this, 8), 0, AdminKit.dp(this, 8));
        card.addView(pack);
        AdminKit.infoRow(card, this, "وضعیت", statusText(l, today), false);
        AdminKit.infoRow(card, this, "طرح", "«" + License.planFa(planOf(l)) + "»", false);
        AdminKit.infoRow(card, this, "انقضا", expText(l), false);
        AdminKit.infoRow(card, this, "کد دستگاه", prettyDev(l.dev), true);
        if (!l.note.isEmpty()) AdminKit.infoRow(card, this, "یادداشت", l.note, false);
        box.addView(card);

        Button bCopy = AdminKit.btn(this, "کپی کد", false);
        bCopy.setOnClickListener(v -> {
            AdminKit.copy(this, "کد فعال‌سازی", l.pack);
            AdminKit.toast(this, "کد کپی شد");
        });
        box.addView(bCopy);

        Button bShare = AdminKit.btn(this, "اشتراک‌گذاری برای مشتری", false);
        bShare.setOnClickListener(v -> AdminKit.share(this, "ارسال کد فعال‌سازی",
                shareText(l)));
        box.addView(bShare);

        Button bCust = AdminKit.btn(this, "نمایش مشتری", false);
        bCust.setOnClickListener(v -> {
            if (l.customerId > 0) show(() -> showCustomer(l.customerId));
            else AdminKit.toast(this, "مشتری لینک‌شده‌ای نیست");
        });
        box.addView(bCust);

        Button bExtend = AdminKit.btn(this, "تمدید (صدور کد تازه)", true);
        bExtend.setOnClickListener(v -> {
            MintCtx ctx = new MintCtx();
            ctx.extendOf = l.id;
            ctx.customerId = l.customerId;
            ctx.dev = l.dev;
            ctx.plan = planOf(l);
            AdminDb.Customer c = db.byId(l.customerId);
            if (c == null) c = db.byDev(l.dev);
            if (c != null) {
                ctx.customerId = c.id;
                ctx.name = c.name;
                ctx.family = c.family;
                ctx.shop = c.shop;
                ctx.phone = c.phone;
                ctx.city = c.city;
            }
            show(() -> showMint(ctx));
        });
        box.addView(bExtend);

        Button bRevoke = AdminKit.btn(this, l.revoked ? "رفع لغو" : "لغو لایسنس", false);
        confirmStep(bRevoke, l.revoked ? "تأیید رفع لغو؟" : "تأیید لغو؟", () -> {
            if (db.setRevoked(l.id, !l.revoked)) {
                AdminKit.toast(this, l.revoked ? "لغو برداشته شد" : "لایسنس لغو شد");
                reload();
            }
        });
        box.addView(bRevoke);

        Button bDel = AdminKit.btn(this, "حذف رکورد", false);
        confirmStep(bDel, "تأیید حذف رکورد؟", () -> {
            if (db.deleteLicense(l.id)) {
                AdminKit.toast(this, "رکورد حذف شد");
                goBack();
            }
        });
        box.addView(bDel);
        root(box);
    }

    // ---------- license helpers ----------

    private static char planOf(AdminDb.Lic l) {
        return (l.plan == null || l.plan.isEmpty()) ? License.P_TRIAL : l.plan.charAt(0);
    }

    /** 0 = active, 1 = expired, 2 = revoked. */
    private static int licState(AdminDb.Lic l, long today) {
        if (l.revoked) return 2;
        if (planOf(l) == License.P_PERM) return 0;
        return l.exp >= today ? 0 : 1;
    }

    private static String statusText(AdminDb.Lic l, long today) {
        if (l.revoked) return "⛔ لغوشده";
        if (planOf(l) == License.P_PERM) return "✓ دائمی";
        if (l.exp < today) return "منقضی‌شده";
        long left = l.exp - today;
        return "✓ فعال • " + AdminKit.fa(left) + " روز مانده";
    }

    private String expText(AdminDb.Lic l) {
        if (planOf(l) == License.P_PERM) return "بدون انقضا (دائمی)";
        return AdminKit.fa(jalali(l.exp));
    }

    private static String prettyDev(String dev) {
        if (dev == null || dev.length() != 8) return dev == null ? "" : dev;
        return dev.substring(0, 4) + "-" + dev.substring(4);
    }

    // ---------- export ----------

    private void exportCsv() {
        StringBuilder b = new StringBuilder("\uFEFF");
        b.append("نوع,id,نام,نام خانوادگی,فروشگاه,موبایل,شهر,کد دستگاه,طرح,انقضا(شمسی),وضعیت,کد فعال‌سازی,یادداشت\n");
        for (AdminDb.Customer c : db.searchCustomers("")) {
            b.append("customer,").append(c.id).append(',')
                    .append(csv(c.name)).append(',').append(csv(c.family)).append(',')
                    .append(csv(c.shop)).append(',').append(csv(c.phone)).append(',')
                    .append(csv(c.city)).append(',').append(csv(c.dev))
                    .append(",,,,\n");
        }
        long today = License.today();
        for (AdminDb.Lic l : db.licenses(0, today)) {
            AdminDb.Customer c = db.byId(l.customerId);
            b.append("license,").append(l.id).append(',')
                    .append(csv(c == null ? "" : c.name)).append(',')
                    .append(csv(c == null ? "" : c.family)).append(',')
                    .append(csv(c == null ? "" : c.shop)).append(',')
                    .append(csv(c == null ? "" : c.phone)).append(',')
                    .append(csv(c == null ? "" : c.city)).append(',')
                    .append(csv(l.dev)).append(',')
                    .append(csv("«" + License.planFa(planOf(l)) + "»")).append(',')
                    .append(csv(planOf(l) == License.P_PERM ? "دائمی" : jalali(l.exp))).append(',')
                    .append(csv(statusText(l, today))).append(',')
                    .append(csv(l.pack)).append(',').append(csv(l.note)).append('\n');
        }
        AdminKit.share(this, "خروجی CSV", b.toString());
    }

    private static String csv(String s) {
        if (s == null) return "";
        String t = s.replace("\"", "\"\"");
        return (t.contains(",") || t.contains("\n") || t.contains("\""))
                ? ("\"" + t + "\"") : t;
    }

    // ---------- misc ----------

    private void goBack() {
        onBackPressed();
    }

    private void confirmStep(Button b, String armed, Runnable action) {
        b.setOnClickListener(v -> {
            if (armed.equals(b.getText().toString())) {
                try {
                    action.run();
                } catch (Exception e) {
                    AdminKit.toast(this, "ناموفق بود");
                }
            } else {
                b.setText(armed);
                AdminKit.toast(this, "برای تأیید، دوباره بزنید");
            }
        });
    }

    // ---------- Jalali date (verified port; brute-force tested 2020–2032) ----------

    private static boolean isLeapY(int y) {
        return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0;
    }

    private static int divJ(int a, int b) {
        return (int) Math.floor((double) a / b);
    }

    private static int modJ(int a, int b) {
        return a - divJ(a, b) * b;
    }

    private static final int[] J_BREAKS = {-61, 9, 38, 199, 426, 686, 756, 818,
            1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178};

    private static int epochDayOf(int y, int m, int d) {
        int[] ml = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int days = d - 1;
        for (int i = 1; i < m; i++) {
            days += ml[i - 1];
            if (i == 2 && isLeapY(y)) days++;
        }
        for (int yy = 1970; yy < y; yy++) days += isLeapY(yy) ? 366 : 365;
        return days;
    }

    /** Returns {leap, gy, march}. */
    private static int[] jalCal(int jy) {
        int gy = jy + 621, leapJ = -14, jp = J_BREAKS[0], jump = 0;
        for (int i = 1; i < J_BREAKS.length; i++) {
            int jm = J_BREAKS[i];
            jump = jm - jp;
            if (jy < jm) break;
            leapJ += divJ(jump, 33) * 8 + divJ(modJ(jump, 33), 4);
            jp = jm;
        }
        int n = jy - jp;
        leapJ += divJ(n, 33) * 8 + divJ(modJ(n, 33) + 3, 4);
        if (modJ(jump, 33) == 4 && jump - n == 4) leapJ++;
        int leapG = divJ(gy, 4) - divJ((divJ(gy, 100) + 1) * 3, 4) - 150;
        int march = 20 + leapJ - leapG;
        if (jump - n < 6) n = n - jump + divJ(jump + 4, 33) * 33;
        int leap = modJ(modJ(n + 1, 33) - 1, 4);
        if (leap == -1) leap = 4;
        return new int[]{leap, gy, march};
    }

    /** epochDay (UTC day number, as in {@link License#today}) → "1405/7/16". */
    private static String jalali(long epochDay) {
        try {
            int y = 1970;
            long r = epochDay;
            while (true) {
                int len = isLeapY(y) ? 366 : 365;
                if (r < len) break;
                r -= len;
                y++;
            }
            int jy = y - 621;
            int[] rc = jalCal(jy);
            long k = epochDay - epochDayOf(y, 3, rc[2]);
            int jm, jd;
            if (k >= 0) {
                if (k <= 185) {
                    return jy + "/" + (1 + divJ((int) k, 31)) + "/"
                            + (modJ((int) k, 31) + 1);
                }
                k -= 186;
            } else {
                jy -= 1;
                k += 179;
                if (rc[0] == 1) k++;
            }
            jm = 7 + divJ((int) k, 30);
            jd = modJ((int) k, 30) + 1;
            return jy + "/" + jm + "/" + jd;
        } catch (Exception e) {
            return "";
        }
    }
}
