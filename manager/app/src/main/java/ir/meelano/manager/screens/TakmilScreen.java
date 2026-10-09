package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.WarehouseWriter;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Office completion (v29): the back office prices the warehouse drafts
 * and posts them finally. Drafts arrive from this device or as imported
 * JSON files shared by the warehouse keeper.
 */
public class TakmilScreen extends Screen {
    public TakmilScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "takmil"; }

    @Override
    public String title() { return "تکمیل پیش‌فاکتور"; }

    @Override
    public String glyph() { return "🧾"; }

    @Override
    public int accent() { return Theme.GOLD; }

    private LinearLayout content;

    @Override
    public void render(LinearLayout content) {
        this.content = content;
        content.removeAllViews();
        LinearLayout hero = a.kit.card(Theme.GOLD);
        hero.addView(a.kit.text("🧾 تکمیل پیش‌فاکتور (دفتر)", 17f, Theme.TEXT, true),
                a.kit.lp(-1, -2));
        hero.addView(a.kit.text("قیمت‌گذاری پیش‌نویس‌های انبار و ثبت نهایی در آتیران",
                12f, Theme.MUTED, false), a.kit.lp(-1, -2));
        content.addView(hero, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.btnGhost("📥 ورود فایل پیش‌نویس", Theme.INFO, v -> importDraft()),
                a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        List<JSONObject> drafts = WarehouseWriter.listDrafts(a);
        List<JSONObject> mine = new ArrayList<>();
        for (JSONObject d : drafts) {
            if (!WarehouseWriter.COUNT.equals(d.optString(WarehouseWriter.D_TYPE, ""))) mine.add(d);
        }
        content.addView(a.kit.text("در انتظار تکمیل (" + Money.fa(String.valueOf(mine.size())) + ")",
                14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(6));
        if (mine.isEmpty()) {
            content.addView(a.kit.empty("پیش‌نویسی نیست", "انبار هنوز چیزی نفرستاده است"),
                    a.kit.lp(-1, -2));
            return;
        }
        for (JSONObject d : mine) {
            final JSONObject doc = d;
            JSONArray ls = doc.optJSONArray(WarehouseWriter.D_LINES);
            int n = ls == null ? 0 : ls.length();
            int priced = 0;
            if (ls != null) {
                for (int i = 0; i < ls.length(); i++) {
                    JSONObject ln = ls.optJSONObject(i);
                    if (ln != null && ln.optDouble(WarehouseWriter.L_PRICE, 0) > 0) priced++;
                }
            }
            View v = a.kit.personRow(
                    WarehouseWriter.faType(doc.optString(WarehouseWriter.D_TYPE, ""))
                            + " • " + Money.fa(String.valueOf(doc.optInt(WarehouseWriter.D_NO, 0))),
                    doc.optString(WarehouseWriter.D_PNAME, "—") + " • "
                            + Money.fa(doc.optString(WarehouseWriter.D_DATE, "")),
                    Money.fa(String.valueOf(n)) + " قلم",
                    priced == n && n > 0 ? "✓ قیمت‌دار" : "تکمیل",
                    priced == n && n > 0 ? Theme.SUCCESS : Theme.GOLD,
                    v2 -> openComplete(doc));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
    }

    private void importDraft() {
        try {
            a.startDocPick(text -> {
                try {
                    JSONObject d = new JSONObject(text);
                    JSONArray ls = d.optJSONArray(WarehouseWriter.D_LINES);
                    if (ls == null || ls.length() == 0) {
                        a.kit.toast("فایل معتبر نیست");
                        return;
                    }
                    d.remove(WarehouseWriter.D_NO);
                    int no = WarehouseWriter.saveDraft(a, d);
                    a.kit.toast("پیش‌نویس " + Money.fa(String.valueOf(no)) + " وارد شد");
                    if (content != null) render(content);
                } catch (Exception e) {
                    a.kit.toast("فایل معتبر نیست");
                }
            });
        } catch (Exception e) {
            a.kit.toast("انتخاب فایل ممکن نشد");
        }
    }

    private void openComplete(final JSONObject d) {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.kv("نوع", WarehouseWriter.faType(d.optString(WarehouseWriter.D_TYPE, "")),
                Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("طرف", d.optString(WarehouseWriter.D_PNAME, "—"), Theme.TEXT),
                a.kit.lp(-1, -2));
        final JSONArray ls = d.optJSONArray(WarehouseWriter.D_LINES);
        final List<EditText> priceEdits = new ArrayList<>();
        if (ls != null) {
            for (int i = 0; i < ls.length(); i++) {
                final JSONObject ln = ls.optJSONObject(i);
                if (ln == null) continue;
                String nm = ln.optBoolean(WarehouseWriter.L_NEW, false)
                        ? ln.optString(WarehouseWriter.L_NNAME, "")
                        : ln.optString(WarehouseWriter.L_NAKA, "");
                body.addView(a.kit.text((i + 1) + ". " + (nm.isEmpty() ? "—" : nm)
                        + " — " + Money.fa(ln.optString(WarehouseWriter.L_QTY, "0")) + " "
                        + ln.optString(WarehouseWriter.L_UNIT, ""), 12.5f, Theme.TEXT, true),
                        a.kit.lp(-1, -2));
                double p0 = ln.optDouble(WarehouseWriter.L_PRICE, 0);
                EditText e = a.kit.editNum("فی واحد (تومان)",
                        p0 > 0 ? String.valueOf((long) p0) : "");
                body.addView(e, a.kit.lp(-1, -2));
                priceEdits.add(e);
            }
        }
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btnGhost("💾 ذخیره قیمت‌ها", Theme.GOLD, v -> {
            if (readPrices(ls, priceEdits, false)) {
                WarehouseWriter.saveDraft(a, d);
                a.kit.toast("قیمت‌ها ذخیره شد");
                if (content != null) render(content);
            }
        }), a.kit.lp(-1, -2));
        body.addView(a.kit.btn("⬆ تکمیل و ثبت نهایی", v -> {
            boolean direct = false;
            try {
                direct = a.settings.whDirect();
            } catch (Exception ignored) { }
            if (!direct) {
                a.kit.toast("ثبت مستقیم از تنظیمات خاموش است");
                return;
            }
            if (!readPrices(ls, priceEdits, true)) return;
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            postCompleted(d);
        }), a.kit.lp(-1, -2));
        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        box[0] = dlg;
        dlg.show();
    }

    /** Read price edits back into the lines. Strict = all must be > 0. */
    private boolean readPrices(JSONArray ls, List<EditText> edits, boolean strict) {
        if (ls == null) return false;
        try {
            for (int i = 0; i < ls.length() && i < edits.size(); i++) {
                JSONObject ln = ls.optJSONObject(i);
                if (ln == null) continue;
                double p = 0;
                try {
                    p = Double.parseDouble(
                            Money.en(edits.get(i).getText().toString()).trim());
                } catch (Exception ignored) { }
                if (strict && p <= 0) {
                    a.kit.toast("فی قلم " + Money.fa(String.valueOf(i + 1)) + " وارد نشده");
                    return false;
                }
                ln.put(WarehouseWriter.L_PRICE, p);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void postCompleted(final JSONObject d) {
        a.kit.toast("در حال ثبت نهایی…");
        final int uid = AtiranAuth.sessionUid(a);
        final String user = AtiranAuth.sessionUser(a);
        a.repo.run(c -> {
            List<String> warnings = new ArrayList<>();
            List<WarehouseWriter.Stmt> stmts = WarehouseWriter.buildDoc(c, d, uid, user, warnings);
            WarehouseWriter.execute(c, stmts);
            long docNo = d.optLong("_postedNo", 0);
            String type = d.optString(WarehouseWriter.D_TYPE, WarehouseWriter.BUY);
            boolean salesSide = WarehouseWriter.CUSTRET.equals(type);
            double total = WarehouseWriter.completePrices(c,
                    salesSide ? "sailfact_pish" : "buyfact_pish",
                    salesSide ? "subsailfact_pish" : "subbuyfact_pish",
                    salesSide ? "shfacfo" : "shfackh", docNo,
                    d.optJSONArray(WarehouseWriter.D_LINES), warnings);
            warnings.add("TOTAL:" + total);
            warnings.add("DOCNO:" + docNo);
            return warnings;
        }, new Repo.Cb<List<String>>() {
            @Override
            public void ok(List<String> warnings) {
                WarehouseWriter.deleteDraft(a, d.optInt(WarehouseWriter.D_NO, 0));
                double total = 0;
                String docNo = "";
                if (warnings != null) {
                    for (String w : warnings) {
                        if (w.startsWith("TOTAL:")) {
                            try {
                                total = Double.parseDouble(w.substring(6));
                            } catch (Exception ignored) { }
                        }
                        if (w.startsWith("DOCNO:")) docNo = w.substring(6);
                    }
                }
                a.kit.toast("ثبت نهایی شد • سند " + Money.fa(docNo));
                receiptPdf(d, docNo, total);
                if (content != null) render(content);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private void receiptPdf(JSONObject d, String docNo, double total) {
        try {
            String t = d.optString(WarehouseWriter.D_TYPE, WarehouseWriter.BUY);
            String sub = WarehouseWriter.faPartyRole(t) + ": "
                    + d.optString(WarehouseWriter.D_PNAME, "")
                    + "  •  سند: " + docNo
                    + "  •  تاریخ: " + d.optString(WarehouseWriter.D_DATE, "")
                    + "  •  جمع: " + Money.compact(total) + " تومان";
            JSONArray ls = d.optJSONArray(WarehouseWriter.D_LINES);
            List<Row> rows = new ArrayList<>();
            if (ls != null) {
                for (int i = 0; i < ls.length(); i++) {
                    JSONObject ln = ls.optJSONObject(i);
                    if (ln == null) continue;
                    double q = 0, p = 0;
                    try {
                        q = Double.parseDouble(Money.en(ln.optString(WarehouseWriter.L_QTY, "0")));
                        p = ln.optDouble(WarehouseWriter.L_PRICE, 0);
                    } catch (Exception ignored) { }
                    Row r = new Row();
                    r.put("name", ln.optBoolean(WarehouseWriter.L_NEW, false)
                            ? ln.optString(WarehouseWriter.L_NNAME, "")
                            : ln.optString(WarehouseWriter.L_NAKA, ""));
                    r.put("qty", Money.fa(ln.optString(WarehouseWriter.L_QTY, "0")));
                    r.put("price", Money.fa(Money.compact(p)));
                    r.put("sum", Money.fa(Money.compact(q * p)));
                    rows.add(r);
                }
            }
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("qty", "تعداد", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("price", "فی", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("sum", "جمع", ReportCatalog.T_TEXT),
            };
            a.sharePdf(WarehouseWriter.faReceiptTitle(t) + " (تکمیل‌شده)", sub, cols, rows);
        } catch (Exception e) {
            a.kit.toast("ساخت رسید ممکن نشد");
        }
    }
}
