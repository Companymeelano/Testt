package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.FaNum;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.WarehouseWriter;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Warehouse receiving (v28): quantity-ONLY documents in three flavors —
 * purchase, customer return, purchase return. No prices, no discounts.
 * Drafts save offline; direct posting to Atiran pre-invoices needs the
 * shop switch + connection. New products and new customers are created
 * inline with the minimum Atiran-legal fields.
 */
public class ResidScreen extends Screen {
    public ResidScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "resid"; }

    @Override
    public String title() { return "رسید کالا"; }

    @Override
    public String glyph() { return "📥"; }

    @Override
    public int accent() { return Theme.INFO; }

    private LinearLayout content;
    private String type = WarehouseWriter.BUY;
    private String partyId = "";
    private String partyName = "";
    private JSONObject partyNew;
    private String anbarId = "";
    private String anbarName = "";
    private JSONArray lines = new JSONArray();
    private String desc = "";
    private int editingDraft;

    @Override
    public void render(LinearLayout content) {
        this.content = content;
        content.removeAllViews();
        content.addView(topCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(typeCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(partyCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(anbarCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(linesCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(actionCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(draftsCard(), a.kit.lp(-1, -2));
    }

    private String partyLabel() {
        return WarehouseWriter.CUSTRET.equals(type) ? "مشتری" : "تأمین‌کننده";
    }

    private View topCard() {
        LinearLayout card = a.kit.card(Theme.INFO);
        card.addView(a.kit.text("📥 رسید کالا (فقط تعداد)", 17f, Theme.TEXT, true),
                a.kit.lp(-1, -2));
        String keeper = AtiranAuth.sessionName(a);
        if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
        String sub = "امروز " + Money.fa(Jalali.todayStr());
        if (!keeper.isEmpty()) sub += " • انباردار: " + keeper;
        if (editingDraft > 0) sub += " • پیش‌نویس " + Money.fa(String.valueOf(editingDraft));
        card.addView(a.kit.text(sub, 12f, Theme.MUTED, false), a.kit.lp(-1, -2));
        return card;
    }

    private View typeCard() {
        LinearLayout card = a.kit.card(Theme.STEEL);
        card.addView(a.kit.text("نوع سند", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        LinearLayout row = a.kit.h();
        String[][] ts = {
                {WarehouseWriter.BUY, "🛒 خرید"},
                {WarehouseWriter.CUSTRET, "↩ برگشت از مشتری"},
                {WarehouseWriter.BUYRET, "↪ برگشت از خرید"},
        };
        for (int i = 0; i < ts.length; i++) {
            final String t = ts[i][0];
            android.widget.Button b = t.equals(type)
                    ? a.kit.btn(ts[i][1], v -> {
                type = t;
                partyId = "";
                partyName = "";
                partyNew = null;
                if (content != null) render(content);
            })
                    : a.kit.btnGhost(ts[i][1], Theme.GOLD, v -> {
                type = t;
                partyId = "";
                partyName = "";
                partyNew = null;
                if (content != null) render(content);
            });
            row.addView(b, a.kit.wlp(1f));
            if (i < ts.length - 1) row.addView(a.kit.space(6));
        }
        card.addView(row, a.kit.lp(-1, -2));
        if (WarehouseWriter.BUYRET.equals(type))
            card.addView(a.kit.hint("برگشت از خرید: کالا از انبار به تأمین‌کننده برمی‌گردد"),
                    a.kit.lp(-1, -2));
        return card;
    }

    private View partyCard() {
        LinearLayout card = a.kit.card(Theme.GOLD);
        card.addView(a.kit.text(partyLabel(), 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String show = partyName.isEmpty() ? "— انتخاب نشده —" : partyName;
        if (partyNew != null) show += " (جدید)";
        else if (!partyId.isEmpty()) show += " • کد " + Money.fa(partyId);
        card.addView(a.kit.text(show, 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btn("🔍 انتخاب " + partyLabel(), v -> pickParty()), a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(a.kit.btnGhost("+ جدید", Theme.SUCCESS, v -> newPartyDialog()), a.kit.lp(-2, -2));
        card.addView(row, a.kit.lp(-1, -2));
        return card;
    }

    private View anbarCard() {
        LinearLayout card = a.kit.card(Theme.VIOLET);
        LinearLayout row = a.kit.h();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(a.kit.text("انبار: " + (anbarName.isEmpty() ? "— انتخاب نشده —" : anbarName),
                13f, Theme.TEXT, true), a.kit.wlp(1f));
        row.addView(a.kit.btnGhost("انتخاب", Theme.GOLD, v -> pickAnbar()), a.kit.lp(-2, -2));
        card.addView(row, a.kit.lp(-1, -2));
        return card;
    }

    private View linesCard() {
        LinearLayout card = a.kit.card(Theme.SUCCESS);
        card.addView(a.kit.text("اقلام (" + Money.fa(String.valueOf(lines.length())) + ")",
                13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        if (lines.length() == 0) {
            card.addView(a.kit.hint("هنوز قلمی ثبت نشده است"), a.kit.lp(-1, -2));
        }
        for (int i = 0; i < lines.length(); i++) {
            final int idx = i;
            JSONObject ln = lines.optJSONObject(i);
            if (ln == null) continue;
            String nm = ln.optBoolean(WarehouseWriter.L_NEW, false)
                    ? ln.optString(WarehouseWriter.L_NNAME, "")
                    : ln.optString(WarehouseWriter.L_NAKA, "");
            String qty = Money.fa(ln.optString(WarehouseWriter.L_QTY, "0"));
            String un = ln.optString(WarehouseWriter.L_UNIT, "");
            View v = a.kit.personRow(nm.isEmpty() ? "—" : nm,
                    (ln.optBoolean(WarehouseWriter.L_NEW, false) ? "کالای جدید • " : "")
                            + "کد " + Money.fa(ln.optString(WarehouseWriter.L_SHKA, "—")),
                    qty + (un.isEmpty() ? "" : " " + un), "⋯", Theme.GOLD,
                    v2 -> lineMenu(idx));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            card.addView(v, p);
        }
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btn("➕ افزودن کالا", v -> pickProduct()), a.kit.wlp(1f));
        row.addView(a.kit.space(6));
        row.addView(a.kit.btnGhost("⌁ اسکن", Theme.GOLD, v -> scanProduct()), a.kit.lp(-2, -2));
        card.addView(row, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGhost("+ کالای جدید (ساخت نام)", Theme.SUCCESS, v -> newProductDialog()),
                a.kit.lp(-1, -2));
        return card;
    }

    private View actionCard() {
        LinearLayout card = a.kit.card(Theme.GOLD);
        final EditText eDesc = a.kit.edit("شرح سند (اختیاری)", desc);
        card.addView(eDesc, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));
        card.addView(a.kit.btn("💾 ذخیره پیش‌نویس", v -> {
            desc = eDesc.getText().toString();
            saveCurrent();
        }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        boolean direct = false;
        try {
            direct = a.settings.whDirect();
        } catch (Exception ignored) { }
        card.addView(a.kit.btnGhost(direct ? "⬆ ثبت مستقیم در آتیران" : "⬆ ثبت مستقیم (خاموش است)",
                direct ? Theme.SUCCESS : Theme.MUTED, v -> {
                    desc = eDesc.getText().toString();
                    postCurrent();
                }), a.kit.lp(-1, -2));
        card.addView(a.kit.btnGhost("👁 پیش‌نمایش SQL", Theme.INFO, v -> {
            desc = eDesc.getText().toString();
            previewSql();
        }), a.kit.lp(-1, -2));
        if (!direct)
            card.addView(a.kit.hint("ثبت مستقیم از تنظیمات (مدیر) خاموش است؛ اسناد فقط پیش‌نویس محلی می‌مانند تا دفتر تکمیلشان کند."),
                    a.kit.lp(-1, -2));
        return card;
    }

    private View draftsCard() {
        LinearLayout card = a.kit.card(Theme.STEEL);
        List<JSONObject> drafts = WarehouseWriter.listDrafts(a);
        card.addView(a.kit.text("پیش‌نویس‌های من (" + Money.fa(String.valueOf(drafts.size())) + ")",
                13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        if (drafts.isEmpty()) {
            card.addView(a.kit.hint("پیش‌نویسی ذخیره نشده است"), a.kit.lp(-1, -2));
            return card;
        }
        for (JSONObject d : drafts) {
            final JSONObject doc = d;
            JSONArray ls = doc.optJSONArray(WarehouseWriter.D_LINES);
            int n = ls == null ? 0 : ls.length();
            View v = a.kit.personRow(
                    WarehouseWriter.faType(doc.optString(WarehouseWriter.D_TYPE, ""))
                            + " • " + Money.fa(String.valueOf(doc.optInt(WarehouseWriter.D_NO, 0))),
                    doc.optString(WarehouseWriter.D_PNAME, "—") + " • "
                            + Money.fa(doc.optString(WarehouseWriter.D_DATE, "")),
                    Money.fa(String.valueOf(n)) + " قلم", "باز کردن", Theme.GOLD,
                    v2 -> openDraft(doc));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            card.addView(v, p);
        }
        return card;
    }

    // ================= document build / validate =================

    private String validate() {
        if (partyId.isEmpty() && partyNew == null) return partyLabel() + " انتخاب نشده است";
        if (anbarId.isEmpty()) return "انبار انتخاب نشده است";
        if (lines.length() == 0) return "سند قلم ندارد";
        for (int i = 0; i < lines.length(); i++) {
            JSONObject ln = lines.optJSONObject(i);
            if (ln == null) continue;
            double q = 0;
            try {
                q = Double.parseDouble(Money.en(ln.optString(WarehouseWriter.L_QTY, "0")).trim());
            } catch (Exception ignored) { }
            if (q <= 0) return "تعداد قلم " + Money.fa(String.valueOf(i + 1)) + " معتبر نیست";
        }
        return null;
    }

    private JSONObject buildDoc() {
        try {
            JSONObject d = new JSONObject();
            d.put(WarehouseWriter.D_TYPE, type);
            d.put(WarehouseWriter.D_DATE, Jalali.todayStr());
            d.put(WarehouseWriter.D_PSHMO, partyId);
            d.put(WarehouseWriter.D_PNAME, partyName + (partyNew != null ? " (جدید)" : ""));
            if (partyNew != null) d.put(WarehouseWriter.D_PNEW, partyNew);
            d.put(WarehouseWriter.D_ANBAR, anbarId);
            d.put(WarehouseWriter.D_ANBARNAME, anbarName);
            String keeper = AtiranAuth.sessionName(a);
            if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
            d.put(WarehouseWriter.D_USER, keeper);
            d.put(WarehouseWriter.D_DESC, desc == null ? "" : desc);
            d.put(WarehouseWriter.D_LINES, lines);
            if (editingDraft > 0) d.put(WarehouseWriter.D_NO, editingDraft);
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private void resetForm() {
        partyId = "";
        partyName = "";
        partyNew = null;
        anbarId = "";
        anbarName = "";
        lines = new JSONArray();
        desc = "";
        editingDraft = 0;
        if (content != null) render(content);
    }

    private void saveCurrent() {
        String err = validate();
        if (err != null) {
            a.kit.toast(err);
            return;
        }
        JSONObject d = buildDoc();
        if (d == null) {
            a.kit.toast("ساخت سند ممکن نشد");
            return;
        }
        int no = WarehouseWriter.saveDraft(a, d);
        a.kit.toast("پیش‌نویس " + Money.fa(String.valueOf(no)) + " ذخیره شد");
        resetForm();
    }

    // ================= direct posting =================

    private void postCurrent() {
        boolean direct = false;
        try {
            direct = a.settings.whDirect();
        } catch (Exception ignored) { }
        if (!direct) {
            a.kit.toast("ثبت مستقیم خاموش است؛ پیش‌نویس ذخیره شد");
            saveCurrent();
            return;
        }
        String err = validate();
        if (err != null) {
            a.kit.toast(err);
            return;
        }
        final JSONObject d = buildDoc();
        if (d == null) {
            a.kit.toast("ساخت سند ممکن نشد");
            return;
        }
        postDoc(d, true);
    }

    private void postDoc(final JSONObject d, final boolean fromForm) {
        a.kit.toast("در حال ثبت در آتیران…");
        final int uid = AtiranAuth.sessionUid(a);
        final String user = AtiranAuth.sessionUser(a);
        a.repo.run(c -> {
            List<String> warnings = new ArrayList<>();
            List<WarehouseWriter.Stmt> stmts = WarehouseWriter.buildDoc(c, d, uid, user, warnings);
            WarehouseWriter.execute(c, stmts);
            return warnings;
        }, new Repo.Cb<List<String>>() {
            @Override
            public void ok(List<String> warnings) {
                String no = (warnings != null && !warnings.isEmpty()) ? warnings.get(0) : "";
                if (!fromForm) WarehouseWriter.deleteDraft(a, d.optInt(WarehouseWriter.D_NO, 0));
                else if (editingDraft > 0)
                    WarehouseWriter.deleteDraft(a, editingDraft);
                a.kit.toast("در آتیران ثبت شد • " + no);
                receiptPdf(d, no);
                resetForm();
            }

            @Override
            public void fail(String faError) {
                a.kit.dialog("ثبت نشد", msgView("ثبت مستقیم ممکن نشد و سند پیش‌نویس ماند:\n\n"
                        + faError + "\n\nمتن خطا را برای پشتیبانی بفرستید تا نگاشت ستون اصلاح شود."),
                        true).show();
            }
        });
    }

    private void previewSql() {
        String err = validate();
        if (err != null) {
            a.kit.toast(err);
            return;
        }
        final JSONObject d = buildDoc();
        if (d == null) return;
        a.kit.toast("در حال ساخت پیش‌نمایش…");
        final int uid = AtiranAuth.sessionUid(a);
        final String user = AtiranAuth.sessionUser(a);
        a.repo.run(c -> {
            List<String> warnings = new ArrayList<>();
            List<WarehouseWriter.Stmt> stmts = WarehouseWriter.buildDoc(c, d, uid, user, warnings);
            StringBuilder b = new StringBuilder();
            for (String w : warnings) b.append("• ").append(w).append("\n");
            b.append("\n").append(WarehouseWriter.describe(stmts));
            return b.toString();
        }, new Repo.Cb<String>() {
            @Override
            public void ok(String sql) {
                TextView tv = a.kit.text(sql == null ? "" : sql, 11f, Theme.TEXT, false);
                try {
                    tv.setTextIsSelectable(true);
                } catch (Exception ignored) { }
                a.kit.dialog("پیش‌نمایش SQL (اجرا نمی‌شود)", a.kit.scrollWrap(tv, 420), true).show();
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private View msgView(String msg) {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        b.addView(a.kit.text(msg, 12.5f, Theme.TEXT, false), a.kit.lp(-1, -2));
        return b;
    }

    private void receiptPdf(JSONObject d, String docNoLine) {
        try {
            String t = d.optString(WarehouseWriter.D_TYPE, WarehouseWriter.BUY);
            String sub = WarehouseWriter.faPartyRole(t) + ": " + d.optString(WarehouseWriter.D_PNAME, "")
                    + "  •  انبار: " + d.optString(WarehouseWriter.D_ANBARNAME, "")
                    + "  •  انباردار: " + d.optString(WarehouseWriter.D_USER, "")
                    + "  •  تاریخ: " + d.optString(WarehouseWriter.D_DATE, "")
                    + "  •  " + docNoLine;
            if (!d.optString(WarehouseWriter.D_DESC, "").isEmpty())
                sub += "  •  شرح: " + d.optString(WarehouseWriter.D_DESC, "");
            JSONArray ls = d.optJSONArray(WarehouseWriter.D_LINES);
            List<Row> rows = new ArrayList<>();
            if (ls != null) {
                for (int i = 0; i < ls.length(); i++) {
                    JSONObject ln = ls.optJSONObject(i);
                    if (ln == null) continue;
                    Row r = new Row();
                    r.put("name", ln.optBoolean(WarehouseWriter.L_NEW, false)
                            ? ln.optString(WarehouseWriter.L_NNAME, "")
                            : ln.optString(WarehouseWriter.L_NAKA, ""));
                    r.put("qty", ln.optString(WarehouseWriter.L_QTY, "0"));
                    r.put("unit", ln.optString(WarehouseWriter.L_UNIT, ""));
                    rows.add(r);
                }
            }
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("qty", "تعداد", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("unit", "واحد", ReportCatalog.T_TEXT),
            };
            a.sharePdf(WarehouseWriter.faReceiptTitle(t), sub, cols, rows);
        } catch (Exception e) {
            a.kit.toast("ساخت رسید ممکن نشد");
        }
    }

    // ================= drafts =================

    private void openDraft(final JSONObject d) {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.kv("نوع", WarehouseWriter.faType(d.optString(WarehouseWriter.D_TYPE, "")),
                Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv(partyLabelFor(d), d.optString(WarehouseWriter.D_PNAME, "—"),
                Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("انبار", d.optString(WarehouseWriter.D_ANBARNAME, "—"),
                Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("تاریخ", Money.fa(d.optString(WarehouseWriter.D_DATE, "")),
                Theme.TEXT), a.kit.lp(-1, -2));
        JSONArray ls = d.optJSONArray(WarehouseWriter.D_LINES);
        int n = ls == null ? 0 : ls.length();
        body.addView(a.kit.text("اقلام (" + Money.fa(String.valueOf(n)) + ")",
                13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        if (ls != null) {
            for (int i = 0; i < ls.length(); i++) {
                JSONObject ln = ls.optJSONObject(i);
                if (ln == null) continue;
                String nm = ln.optBoolean(WarehouseWriter.L_NEW, false)
                        ? ln.optString(WarehouseWriter.L_NNAME, "")
                        : ln.optString(WarehouseWriter.L_NAKA, "");
                body.addView(a.kit.kv(nm.isEmpty() ? "—" : nm,
                        Money.fa(ln.optString(WarehouseWriter.L_QTY, "0")) + " "
                                + ln.optString(WarehouseWriter.L_UNIT, ""), Theme.TEXT),
                        a.kit.lp(-1, -2));
            }
        }
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(a.kit.gap(8));
        body.addView(a.kit.btn("⬆ ثبت نهایی در آتیران", v -> {
            boolean direct = false;
            try {
                direct = a.settings.whDirect();
            } catch (Exception ignored) { }
            if (!direct) {
                a.kit.toast("ثبت مستقیم از تنظیمات خاموش است");
                return;
            }
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            postDoc(d, false);
        }), a.kit.lp(-1, -2));
        body.addView(a.kit.btnGhost("✏ ویرایش پیش‌نویس", Theme.GOLD, v -> {
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            loadDraft(d);
        }), a.kit.lp(-1, -2));
        body.addView(a.kit.btnGhost("📤 ارسال پیش‌نویس (فایل)", Theme.INFO, v -> shareDraft(d)),
                a.kit.lp(-1, -2));
        body.addView(a.kit.btnGhost("🗑 حذف پیش‌نویس", Theme.DANGER, v -> {
            WarehouseWriter.deleteDraft(a, d.optInt(WarehouseWriter.D_NO, 0));
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            if (content != null) render(content);
        }), a.kit.lp(-1, -2));
        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        box[0] = dlg;
        dlg.show();
    }

    private String partyLabelFor(JSONObject d) {
        return WarehouseWriter.CUSTRET.equals(d.optString(WarehouseWriter.D_TYPE, ""))
                ? "مشتری" : "تأمین‌کننده";
    }

    private void shareDraft(JSONObject d) {
        try {
            File dir = ShareProvider.shareDir(a);
            File f = new File(dir, "pish-" + d.optInt(WarehouseWriter.D_NO, 0) + ".json");
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            w.write(d.toString(2));
            w.close();
            ShareProvider.share(a, f, "application/json", "پیش‌نویس انبار");
        } catch (Exception e) {
            a.kit.toast("ارسال ممکن نشد");
        }
    }

    private void lineMenu(final int idx) {
        JSONObject ln = lines.optJSONObject(idx);
        if (ln == null) return;
        String nm = ln.optBoolean(WarehouseWriter.L_NEW, false)
                ? ln.optString(WarehouseWriter.L_NNAME, "")
                : ln.optString(WarehouseWriter.L_NAKA, "");
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        b.addView(a.kit.kv("کالا", nm.isEmpty() ? "—" : nm, Theme.TEXT), a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(a.kit.btn("✏ ویرایش تعداد", v -> {
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            editQtyDialog(idx);
        }), a.kit.lp(-1, -2));
        b.addView(a.kit.btnGhost("🏷 لیبل قفسه", Theme.GOLD, v -> {
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            printLabel(idx);
        }), a.kit.lp(-1, -2));
        b.addView(a.kit.btnGhost("🗑 حذف قلم", Theme.DANGER, v -> {
            JSONArray keep = new JSONArray();
            for (int k = 0; k < lines.length(); k++) {
                if (k != idx) keep.put(lines.optJSONObject(k));
            }
            lines = keep;
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            if (content != null) render(content);
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("قلم سند", b, true);
        box[0].show();
    }

    private void editQtyDialog(final int idx) {
        JSONObject ln = lines.optJSONObject(idx);
        if (ln == null) return;
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        final EditText e = a.kit.editNum("تعداد *", ln.optString(WarehouseWriter.L_QTY, ""));
        b.addView(e, a.kit.lp(-1, -2));
        b.addView(a.kit.btnGhost("🎙 گفتن تعداد", Theme.VIOLET, v -> voiceQty(e)), a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(a.kit.btn("ذخیره", v -> {
            double q = 0;
            try {
                q = Double.parseDouble(Money.en(e.getText().toString()).trim());
            } catch (Exception ignored) { }
            if (q <= 0) {
                a.kit.toast("تعداد معتبر وارد کنید");
                return;
            }
            try {
                lines.optJSONObject(idx).put(WarehouseWriter.L_QTY, String.valueOf(q));
                try {
                    box[0].dismiss();
                } catch (Exception ignored) { }
                if (content != null) render(content);
            } catch (Exception ex) {
                a.kit.toast("ذخیره ممکن نشد");
            }
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("ویرایش تعداد", b, true);
        box[0].show();
    }

    private void voiceQty(final EditText e) {
        try {
            a.startVoiceSearch(text -> {
                Double v = FaNum.parse(text);
                if (v == null) {
                    a.kit.toast("عدد فهمیده نشد: " + text);
                    return;
                }
                String out = (Math.abs(v - Math.round(v)) < 0.001)
                        ? String.valueOf(Math.round(v)) : String.valueOf(v);
                e.setText(out);
                a.kit.toast("شنیده شد: " + Money.fa(out));
            });
        } catch (Exception ex) {
            a.kit.toast("ورودی صوتی ممکن نشد");
        }
    }

    private void printLabel(int idx) {
        JSONObject ln = lines.optJSONObject(idx);
        if (ln == null) return;
        String code = ln.optString(WarehouseWriter.L_SHKA, "");
        String nm = ln.optBoolean(WarehouseWriter.L_NEW, false)
                ? ln.optString(WarehouseWriter.L_NNAME, "")
                : ln.optString(WarehouseWriter.L_NAKA, "");
        String un = ln.optString(WarehouseWriter.L_UNIT, "");
        if (code.isEmpty()) {
            a.kit.toast("این قلم هنوز کد ندارد (بعد از ثبت)");
            return;
        }
        ir.meelano.manager.ui.LabelPrint.share(a, code, nm, un);
    }

    /** Load a draft back into the form for editing. */
    private void loadDraft(JSONObject d) {
        try {
            type = d.optString(WarehouseWriter.D_TYPE, WarehouseWriter.BUY);
            partyId = d.optString(WarehouseWriter.D_PSHMO, "");
            partyName = d.optString(WarehouseWriter.D_PNAME, "").replace(" (جدید)", "");
            partyNew = d.optJSONObject(WarehouseWriter.D_PNEW);
            anbarId = d.optString(WarehouseWriter.D_ANBAR, "");
            anbarName = d.optString(WarehouseWriter.D_ANBARNAME, "");
            JSONArray ls = d.optJSONArray(WarehouseWriter.D_LINES);
            lines = ls == null ? new JSONArray() : new JSONArray(ls.toString());
            desc = d.optString(WarehouseWriter.D_DESC, "");
            editingDraft = d.optInt(WarehouseWriter.D_NO, 0);
            if (content != null) render(content);
            a.kit.toast("پیش‌نویس " + Money.fa(String.valueOf(editingDraft)) + " باز شد");
        } catch (Exception e) {
            a.kit.toast("باز کردن ممکن نشد");
        }
    }

    // ================= pickers =================

    private void pickParty() {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        final EditText e = a.kit.edit("نام، کد یا موبایل…", "");
        b.addView(e, a.kit.lp(-1, -2));
        final LinearLayout results = a.kit.v();
        b.addView(results, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        final Runnable search = () -> {
            final String q = e.getText().toString();
            a.kit.toast("در حال جستجو…");
            a.repo.run(c -> {
                Meta m = new Meta(c);
                return Repo.exec(c, MasterQueries.whCustomers(m, q));
            }, new Repo.Cb<List<Row>>() {
                @Override
                public void ok(List<Row> rows) {
                    results.removeAllViews();
                    if (rows == null || rows.isEmpty()) {
                        results.addView(a.kit.hint("یافت نشد؛ از «+ جدید» بسازید"), a.kit.lp(-1, -2));
                        return;
                    }
                    for (Row r : rows) {
                        final Row row = r;
                        String label = row.s("name") + " • کد " + Money.fa(row.s("id"))
                                + (row.s("cell").isEmpty() ? "" : " • " + Money.fa(row.s("cell")));
                        results.addView(a.kit.btnGhost(label, Theme.GOLD, v -> {
                            partyId = row.s("id");
                            partyName = row.s("name");
                            partyNew = null;
                            try {
                                box[0].dismiss();
                            } catch (Exception ignored) { }
                            if (content != null) render(content);
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
        box[0] = a.kit.dialog("انتخاب " + partyLabel(), a.kit.scrollWrap(b, 440), true);
        box[0].show();
        search.run();
    }

    private void newPartyDialog() {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        final EditText eName = a.kit.edit("نام " + partyLabel() + " *", "");
        final EditText eCell = a.kit.editNum("موبایل", "");
        final EditText eAddr = a.kit.edit("آدرس (اختیاری)", "");
        b.addView(eName, a.kit.lp(-1, -2));
        b.addView(eCell, a.kit.lp(-1, -2));
        b.addView(eAddr, a.kit.lp(-1, -2));
        final long[] groupId = {0};
        final String[] groupName = {""};
        final long[] visId = {0};
        final String[] visName = {""};
        final android.widget.Button[] bGroup = new android.widget.Button[1];
        final android.widget.Button[] bVis = new android.widget.Button[1];
        bGroup[0] = a.kit.btnGhost("گروه: — انتخاب —", Theme.GOLD, v -> pickCustGroup(groupId,
                groupName, () -> bGroup[0].setText("گروه: " + groupName[0])));
        bVis[0] = a.kit.btnGhost("ویزیتور: — انتخاب —", Theme.GOLD, v -> pickVisitor(visId,
                visName, () -> bVis[0].setText("ویزیتور: " + visName[0])));
        b.addView(bGroup[0], a.kit.lp(-1, -2));
        b.addView(bVis[0], a.kit.lp(-1, -2));
        b.addView(a.kit.hint("بقیه اطلاعات را بعداً کاربران دفتر تکمیل می‌کنند"), a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(a.kit.btn("ثبت در سند", v -> {
            String nm = eName.getText().toString().trim();
            if (nm.isEmpty()) {
                a.kit.toast("نام را وارد کنید");
                return;
            }
            if (groupId[0] == 0) {
                a.kit.toast("گروه را انتخاب کنید");
                return;
            }
            try {
                partyNew = new JSONObject();
                partyNew.put("name", nm);
                partyNew.put("cell", Money.en(eCell.getText().toString()).trim());
                partyNew.put("group", groupId[0]);
                partyNew.put("vis", visId[0]);
                partyNew.put("addr", eAddr.getText().toString().trim());
                partyId = "";
                partyName = nm;
                try {
                    box[0].dismiss();
                } catch (Exception ignored) { }
                if (content != null) render(content);
            } catch (Exception ex) {
                a.kit.toast("ثبت ممکن نشد");
            }
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog(partyLabel() + " جدید", a.kit.scrollWrap(b, 440), true);
        box[0].show();
    }

    private void pickCustGroup(final long[] id, final String[] name, final Runnable done) {
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whCustGroups(m));
        }, listPicker("گروه " + partyLabel(), id, name, done));
    }

    private void pickVisitor(final long[] id, final String[] name, final Runnable done) {
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whVisitors(m));
        }, listPicker("ویزیتور", id, name, done));
    }

    private void pickProdGroup(final long[] id, final String[] name, final Runnable done) {
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whProductGroups(m));
        }, listPicker("گروه کالا", id, name, done));
    }

    private Repo.Cb<List<Row>> listPicker(final String title, final long[] id,
            final String[] name, final Runnable done) {
        return new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                if (rows == null || rows.isEmpty()) {
                    a.kit.toast("موردی یافت نشد");
                    return;
                }
                LinearLayout b = a.kit.v();
                b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
                final AlertDialog[] box = new AlertDialog[1];
                for (Row r : rows) {
                    final Row row = r;
                    b.addView(a.kit.btnGhost(row.s("name"), Theme.GOLD, v -> {
                        try {
                            id[0] = Long.parseLong(Money.en(row.s("id")).trim());
                        } catch (Exception e) {
                            id[0] = 0;
                        }
                        name[0] = row.s("name");
                        try {
                            box[0].dismiss();
                        } catch (Exception ignored) { }
                        done.run();
                    }), a.kit.lp(-1, -2));
                }
                box[0] = a.kit.dialog(title, a.kit.scrollWrap(b, 420), true);
                box[0].show();
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        };
    }

    private void pickAnbar() {
        a.kit.toast("در حال دریافت انبارها…");
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
                    String label = row.s("name")
                            + (row.s("keeper").isEmpty() ? "" : " • " + row.s("keeper"));
                    b.addView(a.kit.btnGhost(label, Theme.GOLD, v -> {
                        anbarId = row.s("id");
                        anbarName = row.s("name");
                        try {
                            box[0].dismiss();
                        } catch (Exception ignored) { }
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

    private void pickProduct() {
        productDialog("");
    }

    private void scanProduct() {
        try {
            a.startScan(code -> {
                if (code != null && !code.trim().isEmpty()) productDialog(code.trim());
            });
        } catch (Exception e) {
            a.kit.toast("اسکنر باز نشد");
        }
    }

    private void productDialog(String initial) {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        final EditText e = a.kit.edit("نام یا کد کالا…", initial == null ? "" : initial);
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
                        results.addView(a.kit.hint("یافت نشد؛ از «+ کالای جدید» بسازید"),
                                a.kit.lp(-1, -2));
                        return;
                    }
                    for (Row r : rows) {
                        final Row row = r;
                        String label = row.s("name") + " • کد " + Money.fa(row.s("code"));
                        if (!row.s("unit").isEmpty()) label += " • " + row.s("unit");
                        results.addView(a.kit.btnGhost(label, Theme.GOLD, v -> {
                            try {
                                box[0].dismiss();
                            } catch (Exception ignored) { }
                            qtyDialog(row.s("code"), row.s("name"), row.s("unit"));
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

    private void qtyDialog(final String code, final String name, final String unit) {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        b.addView(a.kit.kv("کالا", name.isEmpty() ? "—" : name, Theme.TEXT), a.kit.lp(-1, -2));
        if (!unit.isEmpty()) b.addView(a.kit.kv("واحد", unit, Theme.TEXT), a.kit.lp(-1, -2));
        final EditText e = a.kit.editNum("تعداد *", "");
        b.addView(e, a.kit.lp(-1, -2));
        b.addView(a.kit.btnGhost("🎙 گفتن تعداد", Theme.VIOLET, v -> voiceQty(e)), a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(a.kit.btn("افزودن به سند", v -> {
            double q = 0;
            try {
                q = Double.parseDouble(Money.en(e.getText().toString()).trim());
            } catch (Exception ignored) { }
            if (q <= 0) {
                a.kit.toast("تعداد معتبر وارد کنید");
                return;
            }
            try {
                JSONObject ln = new JSONObject();
                ln.put(WarehouseWriter.L_SHKA, code);
                ln.put(WarehouseWriter.L_NAKA, name);
                ln.put(WarehouseWriter.L_QTY, String.valueOf(q));
                ln.put(WarehouseWriter.L_UNIT, unit == null ? "" : unit);
                lines.put(ln);
                try {
                    box[0].dismiss();
                } catch (Exception ignored) { }
                if (content != null) render(content);
            } catch (Exception ex) {
                a.kit.toast("افزودن ممکن نشد");
            }
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("تعداد", b, true);
        box[0].show();
    }

    private void newProductDialog() {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        final EditText eName = a.kit.edit("نام کالا *", "");
        b.addView(eName, a.kit.lp(-1, -2));
        final String[] unit = {""};
        final long[] groupId = {0};
        final String[] groupName = {""};
        final android.widget.Button[] bUnit = new android.widget.Button[1];
        final android.widget.Button[] bGroup = new android.widget.Button[1];
        bUnit[0] = a.kit.btnGhost("واحد: — انتخاب —", Theme.GOLD, v -> pickUnit(unit,
                () -> bUnit[0].setText("واحد: " + unit[0])));
        bGroup[0] = a.kit.btnGhost("گروه: — انتخاب —", Theme.GOLD, v -> pickProdGroup(groupId,
                groupName, () -> bGroup[0].setText("گروه: " + groupName[0])));
        b.addView(bUnit[0], a.kit.lp(-1, -2));
        b.addView(bGroup[0], a.kit.lp(-1, -2));
        final EditText eQty = a.kit.editNum("تعداد در این سند *", "");
        b.addView(eQty, a.kit.lp(-1, -2));
        b.addView(a.kit.hint("کد کالا به‌صورت خودکار گرفته می‌شود؛ قیمت‌ها صفر می‌مانند تا دفتر تکمیل کند"),
                a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        b.addView(a.kit.btn("افزودن به سند", v -> {
            String nm = eName.getText().toString().trim();
            if (nm.isEmpty()) {
                a.kit.toast("نام کالا را وارد کنید");
                return;
            }
            if (unit[0].isEmpty() || groupId[0] == 0) {
                a.kit.toast("واحد و گروه را انتخاب کنید");
                return;
            }
            double q = 0;
            try {
                q = Double.parseDouble(Money.en(eQty.getText().toString()).trim());
            } catch (Exception ignored) { }
            if (q <= 0) {
                a.kit.toast("تعداد معتبر وارد کنید");
                return;
            }
            try {
                JSONObject ln = new JSONObject();
                ln.put(WarehouseWriter.L_NEW, true);
                ln.put(WarehouseWriter.L_NNAME, nm);
                ln.put(WarehouseWriter.L_NUNIT, unit[0]);
                ln.put(WarehouseWriter.L_NGROUP, groupId[0]);
                ln.put(WarehouseWriter.L_NGROUPNAME, groupName[0]);
                ln.put(WarehouseWriter.L_SHKA, "");
                ln.put(WarehouseWriter.L_NAKA, nm);
                ln.put(WarehouseWriter.L_QTY, String.valueOf(q));
                ln.put(WarehouseWriter.L_UNIT, unit[0]);
                lines.put(ln);
                try {
                    box[0].dismiss();
                } catch (Exception ignored) { }
                if (content != null) render(content);
            } catch (Exception ex) {
                a.kit.toast("افزودن ممکن نشد");
            }
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("کالای جدید", a.kit.scrollWrap(b, 440), true);
        box[0].show();
    }

    private void pickUnit(final String[] unit, final Runnable done) {
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whUnits(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                if (rows == null || rows.isEmpty()) {
                    a.kit.toast("واحدی یافت نشد");
                    return;
                }
                LinearLayout b = a.kit.v();
                b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
                final AlertDialog[] box = new AlertDialog[1];
                for (Row r : rows) {
                    final Row row = r;
                    b.addView(a.kit.btnGhost(row.s("name"), Theme.GOLD, v -> {
                        unit[0] = row.s("name");
                        try {
                            box[0].dismiss();
                        } catch (Exception ignored) { }
                        done.run();
                    }), a.kit.lp(-1, -2));
                }
                box[0] = a.kit.dialog("واحد شمارش", a.kit.scrollWrap(b, 420), true);
                box[0].show();
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }
}
