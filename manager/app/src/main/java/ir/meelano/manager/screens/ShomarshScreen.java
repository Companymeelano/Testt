package ir.meelano.manager.screens;

import ir.meelano.manager.core.Diag;
import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.DeviceId;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.WarehouseWriter;
import ir.meelano.manager.core.WarehouseCountDb;
import ir.meelano.manager.core.WarehouseCountDraft;
import ir.meelano.manager.core.WarehouseSync;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cycle counting (v29): count the shelf, see the variance vs the system
 * live, save/share a count report. Adjustment posting stays a draft until
 * the vendor names the adjustment table.
 */
public class ShomarshScreen extends Screen {
    public ShomarshScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "shomarsh"; }

    @Override
    public String title() { return "شمارش انبار"; }

    @Override
    public String glyph() { return Theme.isLight() ? "🔢" : "📦"; }

    @Override
    public int accent() { return Theme.VIOLET; }

    private LinearLayout content;
    private String anbarId = "";
    private String anbarName = "";
    private String countDate = Jalali.todayStr();
    private JSONArray lines = new JSONArray();
    private boolean localDraftLoaded;
    private boolean restoredLocalDraft;
    private long restoredAt;

    private String activeRole() {
        String role = AtiranAuth.sessionRole(a);
        if (role == null || role.isEmpty())
            role = RoleStore.enabled(a) ? RoleStore.current(a) : RoleStore.ADMIN;
        return role;
    }

    private boolean isManager() { return RoleStore.ADMIN.equals(activeRole()); }
    private boolean blindMode() { return RoleStore.WAREHOUSE.equals(activeRole()); }

    private void restoreLocalDraft() {
        if (localDraftLoaded) return;
        localDraftLoaded = true;
        try {
            String raw = a.getSharedPreferences(WarehouseCountDraft.PREFS,
                    android.content.Context.MODE_PRIVATE).getString(WarehouseCountDraft.KEY, "");
            WarehouseCountDraft.State state = WarehouseCountDraft.decode(raw, Jalali.todayStr());
            if (!state.hasWork()) return;
            countDate = state.day;
            anbarId = state.anbarId;
            anbarName = state.anbarName;
            lines = state.lines;
            restoredAt = state.updatedAt;
            restoredLocalDraft = lines.length() > 0;
        } catch (Exception ignored) { Diag.ignored("ShomarshScreen:86", ignored); }
    }

    private void persistLocalDraft() {
        try {
            android.content.SharedPreferences p = a.getSharedPreferences(WarehouseCountDraft.PREFS,
                    android.content.Context.MODE_PRIVATE);
            if (lines.length() == 0 && anbarId.isEmpty() && anbarName.isEmpty()) {
                p.edit().remove(WarehouseCountDraft.KEY).apply();
                restoredLocalDraft = false;
                restoredAt = 0;
                return;
            }
            String json = WarehouseCountDraft.encode(countDate, anbarId, anbarName,
                    lines, System.currentTimeMillis());
            if (!json.isEmpty()) p.edit().putString(WarehouseCountDraft.KEY, json).apply();
            restoredLocalDraft = false;
            restoredAt = 0;
        } catch (Exception ignored) { Diag.ignored("ShomarshScreen:104", ignored); }
    }

    private void clearLocalDraft() {
        try {
            a.getSharedPreferences(WarehouseCountDraft.PREFS,
                    android.content.Context.MODE_PRIVATE).edit().remove(WarehouseCountDraft.KEY).apply();
        } catch (Exception ignored) { Diag.ignored("ShomarshScreen:111", ignored); }
        restoredLocalDraft = false;
        restoredAt = 0;
    }

    private void chooseCountDate() {
        a.kit.dateDialog("تاریخ شمارش", countDate, picked -> {
            countDate = Jalali.normalizeDate(picked);
            persistLocalDraft();
            if (content != null) render(content);
        });
    }

    private void confirmClearSession() {
        new AlertDialog.Builder(a)
                .setTitle("پاک‌کردن شمارش ذخیره‌نشده")
                .setMessage("نشست فعالِ این گوشی پاک می‌شود. گزارش‌هایی که قبلاً در صف همگام‌سازی ثبت شده‌اند دست‌نخورده می‌مانند.")
                .setNegativeButton("ادامه شمارش", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("پاک‌کردن", (dialog, which) -> {
                    lines = new JSONArray();
                    anbarId = "";
                    anbarName = "";
                    countDate = Jalali.todayStr();
                    clearLocalDraft();
                    a.kit.toast("نشست شمارش محلی پاک شد");
                    if (content != null) render(content);
                }).show();
    }

    @Override
    public void render(LinearLayout content) {
        restoreLocalDraft();
        this.content = content;
        content.removeAllViews();
        if (isManager()) {
            LinearLayout reviews = a.kit.v();
            content.addView(reviews, a.kit.lp(-1, -2));
            loadReviews(reviews);
        }
        LinearLayout hero = a.kit.card(Theme.VIOLET);
        hero.addView(a.kit.text("🔢 شمارش انبار", 17f, Theme.TEXT, true), a.kit.lp(-1, -2));
        hero.addView(a.kit.text(blindMode()
                        ? "شمارش کور؛ موجودی و مغایرت تا بررسی مدیر پنهان می‌ماند"
                        : "بشمارید؛ مغایرت با سیستم را برای بررسی مدیر ثبت کنید",
                12f, Theme.MUTED, false), a.kit.lp(-1, -2));
        content.addView(hero, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        LinearLayout ab = a.kit.card(Theme.STEEL);
        LinearLayout row = a.kit.h();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(a.kit.text("انبار: " + (anbarName.isEmpty() ? "— سراسری —" : anbarName),
                13f, Theme.TEXT, true), a.kit.wlp(1f));
        row.addView(a.kit.btnGhost("انتخاب", Theme.GOLD, v -> pickAnbar()), a.kit.lp(-2, -2));
        ab.addView(row, a.kit.lp(-1, -2));
        content.addView(ab, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        LinearLayout session = a.kit.card(Theme.STEEL);
        LinearLayout dateRow = a.kit.h();
        dateRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        dateRow.addView(a.kit.text("تاریخ شمارش: " + Money.fa(countDate), 13f, Theme.TEXT, true),
                a.kit.wlp(1f));
        dateRow.addView(a.kit.btnGhost("انتخاب تاریخ", Theme.GOLD, v -> chooseCountDate()), a.kit.lp(-2, -2));
        session.addView(dateRow, a.kit.lp(-1, -2));
        session.addView(a.kit.hint("تاریخ انتخاب‌شده برای همهٔ اقلام این نشست استفاده می‌شود."), a.kit.lp(-1, -2));
        if (restoredLocalDraft && lines.length() > 0) {
            String recovered = "نشست ناتمام از حافظهٔ خصوصی همین گوشی بازیابی شد؛ تغییرات جدید هم خودکار ذخیره می‌شوند.";
            if (restoredAt > 0) {
                try {
                    String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                            .format(new java.util.Date(restoredAt));
                    recovered += " آخرین ذخیره: " + Money.fa(time) + ".";
                } catch (Exception ignored) { Diag.ignored("ShomarshScreen:182", ignored); }
            }
            session.addView(a.kit.hint(recovered), a.kit.lp(-1, -2));
        } else {
            session.addView(a.kit.hint("اقلام و تعدادهای واردشده تا زمان ثبت گزارش، روی همین گوشی نگه‌داری می‌شوند."),
                    a.kit.lp(-1, -2));
        }
        content.addView(session, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        LinearLayout card = a.kit.card(Theme.SUCCESS);
        card.addView(a.kit.text("شمارش‌ها (" + Money.fa(String.valueOf(lines.length())) + ")",
                13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        if (blindMode()) card.addView(a.kit.hint("موجودی سیستم و اختلاف برای انباردار نمایش داده نمی‌شود."),
                a.kit.lp(-1, -2));
        for (int i = 0; i < lines.length(); i++) {
            final int idx = i;
            JSONObject ln = lines.optJSONObject(i);
            if (ln == null) continue;
            double counted = ln.optDouble(WarehouseWriter.L_COUNTED, 0);
            double stock = ln.optDouble(WarehouseWriter.L_STOCK, 0);
            double diff = counted - stock;
            boolean blind = blindMode();
            String detail = blind ? "شمرده " + Money.fa(fmtNum(counted))
                    : "سیستم " + Money.fa(fmtNum(stock)) + " • شمرده " + Money.fa(fmtNum(counted));
            String pill = blind ? "ثبت‌شده" : (Math.abs(diff) < 0.001 ? "✓" : Money.fa(fmtNum(diff)));
            View v = a.kit.personRow(ln.optString(WarehouseWriter.L_NAKA, "—"),
                    detail, blind ? "" : "مغایرت " + pill,
                    "✕", blind ? Theme.INFO : (Math.abs(diff) < 0.001 ? Theme.SUCCESS : Theme.DANGER),
                    v2 -> {
                        JSONArray keep = new JSONArray();
                        for (int k = 0; k < lines.length(); k++) {
                            if (k != idx) keep.put(lines.optJSONObject(k));
                        }
                        lines = keep;
                        persistLocalDraft();
                        if (content != null) render(content);
                    });
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            card.addView(v, p);
        }
        card.addView(a.kit.btn("➕ افزودن شمارش", v -> addCount()), a.kit.lp(-1, -2));
        content.addView(card, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        LinearLayout act = a.kit.card(Theme.GOLD);
        act.addView(a.kit.btn("💾 ذخیره گزارش شمارش", v -> saveCount()), a.kit.lp(-1, -2));
        act.addView(a.kit.gap(6));
        act.addView(a.kit.btnGhost("🧾 گزارش PDF", Theme.INFO, v -> countPdf()), a.kit.lp(-1, -2));
        if (lines.length() > 0 || !anbarId.isEmpty()) {
            act.addView(a.kit.gap(6));
            act.addView(a.kit.btnGhost("🗑 پاک‌کردن نشست محلی", Theme.DANGER,
                    v -> confirmClearSession()), a.kit.lp(-1, -2));
        }
        content.addView(act, a.kit.lp(-1, -2));
    }

    private static final class ReviewResult {
        List<JSONObject> rows = new ArrayList<>();
        String error = "";
    }

    private void loadReviews(final LinearLayout box) {
        box.removeAllViews();
        box.addView(a.kit.loading("در حال دریافت شمارش‌های منتظر تأیید…"), a.kit.lp(-1, -2));
        a.repo.run(c -> {
            ReviewResult result = new ReviewResult();
            WarehouseCountDb.ensure(c);
            if (!WarehouseCountDb.available(c)) {
                result.error = "صف تأیید سرور در دسترس نیست.";
                return result;
            }
            result.rows = WarehouseCountDb.pending(c);
            return result;
        }, new Repo.Cb<ReviewResult>() {
            @Override public void ok(ReviewResult result) {
                if (content == null || box.getParent() == null) return;
                box.removeAllViews();
                LinearLayout card = a.kit.card(result.error.isEmpty() && !result.rows.isEmpty()
                        ? Theme.WARNING : Theme.INFO);
                card.addView(a.kit.sectionHead("تأیید شمارش‌های کور",
                        null, null), a.kit.lp(-1, -2));
                if (!result.error.isEmpty()) {
                    card.addView(a.kit.hint(result.error), a.kit.lp(-1, -2));
                } else if (result.rows.isEmpty()) {
                    card.addView(a.kit.hint("شمارشِ منتظر بررسی ندارید."), a.kit.lp(-1, -2));
                } else {
                    card.addView(a.kit.hint("تأیید، ثبت بررسی مدیر است و موجودی آتیران را تغییر نمی‌دهد؛ جدول تعدیل تا تأیید schema عمداً دست‌نخورده می‌ماند."),
                            a.kit.lp(-1, -2));
                    for (JSONObject row : result.rows) {
                        final JSONObject doc = row;
                        JSONArray ls = parseLines(doc.optString("lines", "[]"));
                        int variance = 0;
                        for (int i = 0; i < ls.length(); i++) {
                            JSONObject ln = ls.optJSONObject(i);
                            if (ln != null && Math.abs(ln.optDouble(WarehouseWriter.L_COUNTED, 0)
                                    - ln.optDouble(WarehouseWriter.L_STOCK, 0)) > 0.001) variance++;
                        }
                        String sub = Money.fa(doc.optString("day", "")) + " • "
                                + doc.optString("anbar", "سراسری") + " • "
                                + doc.optString("keeper", "—");
                        card.addView(a.kit.personRow("شمارش " + Money.fa(String.valueOf(ls.length())) + " قلم",
                                sub, Money.fa(String.valueOf(variance)) + " مغایرت",
                                "بررسی", variance > 0 ? Theme.WARNING : Theme.SUCCESS,
                                v -> reviewDialog(doc)), a.kit.lp(-1, -2));
                        card.addView(a.kit.gap(6));
                    }
                }
                box.addView(card, a.kit.lp(-1, -2));
                box.addView(a.kit.gap(8));
            }
            @Override public void fail(String faError) {
                if (content == null || box.getParent() == null) return;
                box.removeAllViews();
                box.addView(a.kit.error(faError, () -> loadReviews(box)), a.kit.lp(-1, -2));
            }
        });
    }

    private JSONArray parseLines(String json) {
        try { return new JSONArray(json == null || json.isEmpty() ? "[]" : json); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private boolean hasVarianceWithoutReason(JSONObject doc) {
        JSONArray ls = parseLines(doc == null ? "[]" : doc.optString("lines", "[]"));
        for (int i = 0; i < ls.length(); i++) {
            JSONObject ln = ls.optJSONObject(i);
            if (ln == null) continue;
            double stock = ln.optDouble(WarehouseWriter.L_STOCK, 0);
            double counted = ln.optDouble(WarehouseWriter.L_COUNTED, 0);
            if (Math.abs(counted - stock) > 0.001 && ln.optString("reason", "").trim().isEmpty()) return true;
        }
        return false;
    }

    private void reviewDialog(final JSONObject doc) {
        if (!isManager()) return;
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(12), Theme.dp(16), Theme.dp(12));
        body.addView(a.kit.kv("ثبت‌کننده / تاریخ", doc.optString("keeper", "—") + " • "
                + Money.fa(doc.optString("day", "")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("انبار", doc.optString("anbar", "سراسری"), Theme.TEXT), a.kit.lp(-1, -2));
        JSONArray ls = parseLines(doc.optString("lines", "[]"));
        for (int i = 0; i < ls.length(); i++) {
            JSONObject ln = ls.optJSONObject(i);
            if (ln == null) continue;
            double stock = ln.optDouble(WarehouseWriter.L_STOCK, 0);
            double counted = ln.optDouble(WarehouseWriter.L_COUNTED, 0);
            double diff = counted - stock;
            String detail = "کد " + ln.optString(WarehouseWriter.L_SHKA, "")
                    + " • سیستم " + Money.fa(fmtNum(stock))
                    + " • شمرده " + Money.fa(fmtNum(counted))
                    + " • اختلاف " + Money.fa(fmtNum(diff));
            String reason = ln.optString("reason", "").trim();
            if (!reason.isEmpty()) detail += " • علت/توضیح انباردار: " + reason;
            body.addView(a.kit.kv(ln.optString(WarehouseWriter.L_NAKA, "—"), detail,
                    Math.abs(diff) > 0.001 ? Theme.WARNING : Theme.SUCCESS), a.kit.lp(-1, -2));
        }
        body.addView(a.kit.hint("تأیید فقط وضعیت بررسی را ثبت می‌کند؛ تعدیل موجودی مستقیماً ارسال نمی‌شود. اگر علت مغایرتی ثبت نشده، پیش از تأیید علت یا اقدام مدیر را یادداشت کنید."),
                a.kit.lp(-1, -2));
        final EditText note = a.kit.edit("علت مغایرتِ بی‌توضیح / اقدام مدیر (رد اجباری)", "");
        body.addView(note, a.kit.lp(-1, -2));
        final AlertDialog[] dialog = new AlertDialog[1];
        LinearLayout actions = a.kit.h();
        actions.addView(a.kit.btnGold("تأیید بررسی", v -> {
            String memo = note.getText().toString().trim();
            if (hasVarianceWithoutReason(doc) && memo.isEmpty()) {
                a.kit.toast("برای تأیید، علت یا اقدامِ مغایرتِ بی‌توضیح را ثبت کنید");
                return;
            }
            dialog[0].dismiss();
            submitReview(doc.optString("id", ""), true, memo);
        }), a.kit.wlp(1f));
        actions.addView(a.kit.space(8));
        actions.addView(a.kit.btnGhost("رد با علت", Theme.DANGER, v -> {
            String memo = note.getText().toString().trim();
            if (memo.isEmpty()) { a.kit.toast("برای رد، علت را بنویسید"); return; }
            dialog[0].dismiss();
            submitReview(doc.optString("id", ""), false, memo);
        }), a.kit.wlp(1f));
        body.addView(actions, a.kit.lp(-1, -2));
        dialog[0] = a.kit.dialog("بررسی شمارش کور", a.kit.scrollWrap(body, 520), true);
        dialog[0].show();
    }

    private void submitReview(final String id, final boolean approve, final String note) {
        String reviewer = AtiranAuth.sessionName(a);
        if (reviewer.isEmpty()) reviewer = AtiranAuth.sessionUser(a);
        if (reviewer.isEmpty()) reviewer = "مدیر";
        final String who = reviewer;
        a.repo.run(c -> WarehouseCountDb.review(c, id, approve, who, note), new Repo.Cb<Boolean>() {
            @Override public void ok(Boolean saved) {
                if (Boolean.TRUE.equals(saved)) a.kit.toast(approve
                        ? "بررسی شمارش تأیید شد؛ موجودی تغییر نکرد" : "شمارش با علت ثبت‌شده رد شد");
                else a.kit.toast("ثبت تصمیم ممکن نشد؛ شاید مورد قبلاً بررسی شده باشد");
                if (content != null) render(content);
            }
            @Override public void fail(String faError) { a.kit.toast(faError); }
        });
    }

    private String fmtNum(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) return String.valueOf(Math.round(v));
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private void pickAnbar() {
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whAnbars(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                if (rows == null || rows.isEmpty()) {
                    a.kit.toast("انباری یافت نشد");
                    return;
                }
                LinearLayout b = a.kit.v();
                b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
                final AlertDialog[] box = new AlertDialog[1];
                for (Row r : rows) {
                    final Row row = r;
                    b.addView(a.kit.btnGhost(row.s("name"), Theme.GOLD, v -> {
                        anbarId = row.s("id");
                        anbarName = row.s("name");
                        persistLocalDraft();
                        try {
                            box[0].dismiss();
                        } catch (Exception ignored) { Diag.ignored("ShomarshScreen:410", ignored); }
                        if (content != null) render(content);
                    }), a.kit.lp(-1, -2));
                }
                box[0] = a.kit.dialog("انتخاب انبار", a.kit.scrollWrap(b, 420), true);
                box[0].show();
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private void addCount() {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        final EditText e = a.kit.edit("نام یا کد کالا…", "");
        b.addView(e, a.kit.lp(-1, -2));
        final LinearLayout results = a.kit.v();
        b.addView(results, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        final Runnable search = () -> {
            final String q = e.getText().toString();
            a.repo.run(c -> {
                Meta m = new Meta(c);
                return Repo.exec(c, MasterQueries.whProducts(m, q));
            }, new Repo.Cb<List<Row>>() {
                @Override
                public void ok(List<Row> rows) {
                    results.removeAllViews();
                    if (rows == null || rows.isEmpty()) {
                        results.addView(a.kit.hint("یافت نشد"), a.kit.lp(-1, -2));
                        return;
                    }
                    for (Row r : rows) {
                        final Row row = r;
                        String label = blindMode() ? row.s("name") + " • کد " + row.s("code")
                                : row.s("name") + " • سیستم: " + Money.fa(fmtNum(row.d("stock")));
                        results.addView(a.kit.btnGhost(label, Theme.GOLD, v -> {
                            try {
                                box[0].dismiss();
                            } catch (Exception ignored) { Diag.ignored("ShomarshScreen:453", ignored); }
                            countedDialog(row.s("code"), row.s("name"),
                                    row.s("unit"), row.d("stock"));
                        }), a.kit.lp(-1, -2));
                    }
                }

                @Override
                public void fail(String faError) {
                    a.kit.toast(faError);
                }
            });
        };
        b.addView(a.kit.btn("🔍 جستجو", v -> search.run()), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("انتخاب کالا", a.kit.scrollWrap(b, 440), true);
        box[0].show();
        search.run();
    }

    private void countedDialog(final String code, final String name, final String unit,
            final double stock) {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        b.addView(a.kit.kv("کالا", name.isEmpty() ? "—" : name, Theme.TEXT), a.kit.lp(-1, -2));
        if (!blindMode()) {
            b.addView(a.kit.kv("موجودی سیستم", Money.fa(fmtNum(stock)) + " " + unit, Theme.TEXT),
                    a.kit.lp(-1, -2));
        }
        final EditText e = a.kit.editNum("تعداد شمرده‌شده *", "");
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        b.addView(e, a.kit.lp(-1, -2));
        final EditText reason = a.kit.edit("توضیح/علت احتمالی اختلاف (اختیاری در شمارش کور)", "");
        reason.setMinLines(2);
        b.addView(reason, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(a.kit.btn("ثبت شمارش", v -> {
            String raw = Money.en(e.getText().toString()).trim()
                    .replace("٬", "").replace('٫', '.').replace(',', '.');
            if (raw.isEmpty()) {
                a.kit.toast("تعداد شمرده‌شده را وارد کنید");
                return;
            }
            final double c;
            try {
                c = Double.parseDouble(raw);
            } catch (Exception invalid) {
                a.kit.toast("تعداد معتبر نیست");
                return;
            }
            if (Double.isNaN(c) || Double.isInfinite(c) || c < 0) {
                a.kit.toast("تعداد باید عددی معتبر و صفر یا بیشتر باشد");
                return;
            }
            String cause = reason.getText().toString().trim();
            if (!blindMode() && Math.abs(c - stock) > 0.001 && cause.isEmpty()) {
                a.kit.toast("برای شمارشی که مغایرت دارد علت یا توضیح را ثبت کنید");
                return;
            }
            try {
                JSONObject ln = new JSONObject();
                ln.put(WarehouseWriter.L_SHKA, code);
                ln.put(WarehouseWriter.L_NAKA, name);
                ln.put(WarehouseWriter.L_UNIT, unit == null ? "" : unit);
                ln.put(WarehouseWriter.L_STOCK, stock);
                ln.put(WarehouseWriter.L_COUNTED, c);
                ln.put("reason", cause);
                boolean replaced = false;
                if (code != null && !code.trim().isEmpty()) {
                    for (int i = 0; i < lines.length(); i++) {
                        JSONObject old = lines.optJSONObject(i);
                        if (old != null && code.equals(old.optString(WarehouseWriter.L_SHKA, ""))) {
                            old.put(WarehouseWriter.L_NAKA, name);
                            old.put(WarehouseWriter.L_UNIT, unit == null ? "" : unit);
                            old.put(WarehouseWriter.L_STOCK, stock);
                            old.put(WarehouseWriter.L_COUNTED, c);
                            old.put("reason", cause);
                            replaced = true;
                            break;
                        }
                    }
                }
                if (!replaced) lines.put(ln);
                persistLocalDraft();
                try {
                    box[0].dismiss();
                } catch (Exception ignored) { Diag.ignored("ShomarshScreen:540", ignored); }
                if (content != null) render(content);
                double diff = c - stock;
                if (!blindMode() && Math.abs(diff) > 0.001)
                    a.kit.toast("⚠ مغایرت: " + Money.fa(fmtNum(diff)));
            } catch (Exception ex) {
                a.kit.toast("ثبت ممکن نشد");
            }
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog(blindMode() ? "شمارش کور" : "شمارش", b, true);
        box[0].show();
    }

    private void saveCount() {
        if (lines.length() == 0) {
            a.kit.toast("شمارشی ثبت نشده است");
            return;
        }
        try {
            JSONObject d = new JSONObject();
            d.put(WarehouseWriter.D_TYPE, WarehouseWriter.COUNT);
            d.put(WarehouseWriter.D_DATE, countDate);
            d.put(WarehouseWriter.D_ANBAR, anbarId);
            d.put(WarehouseWriter.D_ANBARNAME, anbarName);
            String keeper = AtiranAuth.sessionName(a);
            if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
            d.put(WarehouseWriter.D_USER, keeper);
            d.put(WarehouseWriter.D_LINES, new JSONArray(lines.toString()));
            int no = WarehouseWriter.saveDraft(a, d);
            if (no <= 0) {
                a.kit.toast("ذخیرهٔ محلی انجام نشد؛ شمارش‌ها پاک نشدند");
                return;
            }
            JSONObject saved = WarehouseWriter.getDraft(a, no);
            if (saved == null) {
                a.kit.toast("تأیید ذخیرهٔ محلی انجام نشد؛ شمارش‌ها نگه داشته شدند");
                return;
            }
            saved.put("reviewId", DeviceId.code(a) + "-" + saved.optLong(WarehouseWriter.D_CREATED,
                    System.currentTimeMillis()) + "-" + no);
            saved.put("device", DeviceId.code(a));
            saved.put("approvalSync", "pending");
            int confirmedNo = WarehouseWriter.saveDraft(a, saved);
            if (confirmedNo != no || WarehouseWriter.getDraft(a, no) == null) {
                a.kit.toast("تأیید ذخیرهٔ گزارش ممکن نشد؛ شمارش‌ها پاک نشدند");
                return;
            }
            a.kit.toast("گزارش شمارش " + Money.fa(String.valueOf(no)) + " ذخیره شد؛ برای تأیید مدیر در صف قرار گرفت");
            lines = new JSONArray();
            countDate = Jalali.todayStr();
            clearLocalDraft();
            if (content != null) render(content);
            WarehouseSync.retry(a, (handovers, counts, pending, error) -> {
                if (counts > 0) a.kit.toast("شمارش برای بررسی مدیر ارسال شد");
                else if (pending > 0) a.kit.toast("شمارش محلی ماند؛ در صف همگام‌سازی منتظر است");
            });
        } catch (Exception e) {
            a.kit.toast("ذخیره ممکن نشد؛ شمارش‌های این نشست نگه داشته شدند");
        }
    }

    private void countPdf() {
        if (lines.length() == 0) {
            a.kit.toast("شمارشی ثبت نشده است");
            return;
        }
        try {
            String sub = "انبار: " + (anbarName.isEmpty() ? "سراسری" : anbarName)
                    + "  •  تاریخ: " + countDate;
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < lines.length(); i++) {
                JSONObject ln = lines.optJSONObject(i);
                if (ln == null) continue;
                double stock = ln.optDouble(WarehouseWriter.L_STOCK, 0);
                double counted = ln.optDouble(WarehouseWriter.L_COUNTED, 0);
                Row r = new Row();
                r.put("name", ln.optString(WarehouseWriter.L_NAKA, ""));
                if (!blindMode()) {
                    r.put("stock", Money.fa(fmtNum(stock)));
                    r.put("counted", Money.fa(fmtNum(counted)));
                    r.put("diff", Money.fa(fmtNum(counted - stock)));
                } else {
                    r.put("counted", Money.fa(fmtNum(counted)));
                }
                rows.add(r);
            }
            ReportCatalog.Col[] cols = blindMode() ? new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("counted", "تعداد شمرده‌شده", ReportCatalog.T_TEXT),
            } : new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("stock", "سیستم", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("counted", "شمرده", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("diff", "مغایرت", ReportCatalog.T_TEXT),
            };
            a.sharePdf("گزارش شمارش انبار", sub, cols, rows);
        } catch (Exception e) {
            a.kit.toast("ساخت گزارش ممکن نشد");
        }
    }
}
