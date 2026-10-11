package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.WarehouseWriter;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Pdf;
import ir.meelano.manager.ui.SignView;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Invoice handover (v28): sales invoices to walk-in customers and to the
 * distributor, with a gorgeous PDF receipt. The warehouse only DELIVERS
 * here — nothing is typed except who received it.
 */
public class TahvilScreen extends Screen {
    public TahvilScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "tahvil"; }

    @Override
    public String title() { return "تحویل فاکتور"; }

    @Override
    public String glyph() { return "🛻"; }

    @Override
    public int accent() { return Theme.SUCCESS; }

    private List<Row> factors = new ArrayList<>();
    private LinearLayout content;

    @Override
    public void render(LinearLayout content) {
        this.content = content;
        content.removeAllViews();
        content.addView(topCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        final LinearLayout box = a.kit.v();
        content.addView(box, a.kit.lp(-1, -2));
        box.addView(a.kit.hint("در حال دریافت فاکتورها…"), a.kit.lp(-1, -2));
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whDeliveries(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                factors = rows == null ? new ArrayList<Row>() : rows;
                box.removeAllViews();
                if (factors.isEmpty()) {
                    box.addView(a.kit.empty("فاکتوری یافت نشد", null), a.kit.lp(-1, -2));
                    return;
                }
                List<Kit.Kpi> kpis = new ArrayList<>();
                kpis.add(new Kit.Kpi("فاکتورها", Money.fa(String.valueOf(factors.size())), "فقره",
                        Theme.SUCCESS));
                box.addView(a.kit.kpiGrid(kpis, 1), a.kit.lp(-1, -2));
                box.addView(a.kit.gap(10));
                for (Row r : factors) {
                    final Row row = r;
                    String no = row.s("id");
                    boolean handed = WarehouseWriter.handedToday(a, no);
                    String sub = row.s("cust");
                    if (!row.s("visitor").isEmpty()) sub += " • " + row.s("visitor");
                    sub += " • " + Money.fa(Jalali.disp(row.s("dt")));
                    View v = a.kit.personRow("فاکتور " + Money.fa(no),
                            sub.isEmpty() ? "—" : sub,
                            Money.fa(Money.compact(row.d("amount"))) + " تومان",
                            handed ? "✓ تحویل شد" : "منتظر تحویل",
                            handed ? Theme.SUCCESS : Theme.GOLD,
                            v2 -> openHandover(row));
                    LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                    p.setMargins(0, 0, 0, Theme.dp(10));
                    box.addView(v, p);
                }
            }

            @Override
            public void fail(String faError) {
                box.removeAllViews();
                box.addView(a.kit.empty("دریافت فاکتورها ممکن نشد", faError), a.kit.lp(-1, -2));
            }
        });
    }

    private View topCard() {
        LinearLayout card = a.kit.card(Theme.SUCCESS);
        card.addView(a.kit.text("🛻 تحویل فاکتور", 17f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.text("فاکتور را به مشتری حضوری یا موزع تحویل بدهید و رسید بگیرید",
                12f, Theme.MUTED, false), a.kit.lp(-1, -2));
        return card;
    }

    // ================= handover =================

    private void openHandover(final Row f) {
        a.kit.toast("در حال دریافت اقلام…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whDeliveryItems(m, f.s("id")));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> items) {
                handoverDialog(f, items == null ? new ArrayList<Row>() : items);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private void handoverDialog(final Row f, final List<Row> items) {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.kv("فاکتور", Money.fa(f.s("id")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مشتری", f.s("cust").isEmpty() ? "—" : f.s("cust"), Theme.TEXT),
                a.kit.lp(-1, -2));
        if (!f.s("phone").isEmpty())
            body.addView(a.kit.kv("تلفن", Money.fa(f.s("phone")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مبلغ", Money.fa(Money.compact(f.d("amount"))) + " تومان", Theme.TEXT),
                a.kit.lp(-1, -2));
                final boolean[] marked = new boolean[items.size()];
        for (int x = 0; x < marked.length; x++) marked[x] = true;
if (!items.isEmpty()) {
            LinearLayout head = a.kit.h();
            head.addView(a.kit.text("اقلام فاکتور (علامت بزنید)", 13f, Theme.TEXT, true),
                    a.kit.wlp(1f));
            final TextView tally = a.kit.text("", 12f, Theme.MUTED, false);
            head.addView(tally, a.kit.lp(-2, -2));
            body.addView(head, a.kit.lp(-1, -2));
            final LinearLayout itemBox = a.kit.v();
            final Runnable repaint = () -> {
                itemBox.removeAllViews();
                for (int x = 0; x < items.size(); x++) {
                    final int idx = x;
                    final Row it = items.get(x);
                    String qty = it.s("qty");
                    String unit = it.s("unit");
                    String label = it.s("name")
                            + (qty.isEmpty() ? "" : "  × " + Money.fa(qty))
                            + (unit.isEmpty() ? "" : " " + unit);
                    android.widget.CheckBox cb = new android.widget.CheckBox(a);
                    cb.setChecked(marked[idx]);
                    cb.setText(label);
                    cb.setTextColor(Theme.TEXT);
                    cb.setTextSize(13f * Theme.fontScale() * Theme.typeScale());
                    cb.setTypeface(Theme.face(false));
                    try { cb.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); } catch (Exception ignored) { }
                    cb.setOnCheckedChangeListener((btn, on) -> marked[idx] = on);
                    itemBox.addView(cb, a.kit.lp(-1, -2));
                }
                int n = 0;
                for (boolean bb : marked) if (bb) n++;
                tally.setText(Money.fa(String.valueOf(n)) + " از "
                        + Money.fa(String.valueOf(items.size())));
            };
            repaint.run();
            body.addView(itemBox, a.kit.lp(-1, -2));
            LinearLayout allRow = a.kit.h();
            allRow.addView(a.kit.btnGhost("✓ انتخاب همه", Theme.SUCCESS, v3 -> {
                for (int x = 0; x < marked.length; x++) marked[x] = true;
                repaint.run();
            }), a.kit.wlp(1f));
            allRow.addView(a.kit.space(8));
            allRow.addView(a.kit.btnGhost("○ هیچ‌کدام", Theme.MUTED, v4 -> {
                for (int x = 0; x < marked.length; x++) marked[x] = false;
                repaint.run();
            }), a.kit.wlp(1f));
            body.addView(allRow, a.kit.lp(-1, -2));
} else {
            body.addView(a.kit.hint("قلمی برای این فاکتور یافت نشد"), a.kit.lp(-1, -2));
        }
        body.addView(a.kit.gap(8));
        body.addView(a.kit.text("تحویل به", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final String[] mode = {"in"}; // in = walk-in customer, out = distributor
        final String[] distName = {""};
        final String[] distCell = {""};
        LinearLayout row = a.kit.h();
        final android.widget.Button[] bIn = new android.widget.Button[1];
        final android.widget.Button[] bOut = new android.widget.Button[1];
        final Runnable paint = () -> {
            boolean isIn = "in".equals(mode[0]);
            ir.meelano.manager.ui.MeelanoIcons.set(bIn[0], "🧍 مشتری حضوری" + (isIn ? " ✓" : ""));
            ir.meelano.manager.ui.MeelanoIcons.set(bOut[0], "🛻 موزع" + (!isIn ? " ✓" : ""));
            bIn[0].setAlpha(isIn ? 1f : 0.55f);
            bOut[0].setAlpha(!isIn ? 1f : 0.55f);
        };
        bIn[0] = a.kit.btn("🧍 مشتری حضوری ✓", v -> {
            mode[0] = "in";
            paint.run();
        });
        bOut[0] = a.kit.btnGhost("🛻 موزع", Theme.GOLD, v -> {
            mode[0] = "out";
            paint.run();
            pickDistributor(distName, distCell, () -> {
            });
        });
        row.addView(bIn[0], a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(bOut[0], a.kit.wlp(1f));
        body.addView(row, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.gap(6));
        final String[] worker = {""};
        body.addView(a.kit.text("تحویل‌دهنده (کارگر)", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final android.widget.TextView workerLabel = a.kit.text("انتخاب نشده", 12.5f, Theme.MUTED, false);
        body.addView(a.kit.btnGhost("کارگر را انتخاب کن", Theme.SUCCESS, v2 -> {
            a.kit.toast("در حال دریافت کارکنان…");
            a.repo.run(c -> {
                Meta m = new Meta(c);
                return Repo.exec(c, MasterQueries.lookupUsers(m));
            }, new Repo.Cb<List<Row>>() {
                @Override public void ok(List<Row> rows) {
                    if (rows == null || rows.isEmpty()) {
                        a.kit.toast("کاربری در آتیران یافت نشد");
                        return;
                    }
                    List<Row> all = new ArrayList<>();
                    Row none = new Row();
                    none.put("id", "");
                    none.put("name", "بدون کارگر");
                    all.add(none);
                    all.addAll(rows);
                    a.kit.searchPicker("انتخاب کارگر", all, new String[]{"name"},
                            new Kit.PickerListener() {
                                @Override public void onPick(Row r) {
                                    worker[0] = r.s("name");
                                    if ("بدون کارگر".equals(worker[0])) worker[0] = "";
                                    workerLabel.setText(worker[0].isEmpty() ? "انتخاب نشده" : worker[0]);
                                    workerLabel.setTextColor(worker[0].isEmpty() ? Theme.MUTED : Theme.TEXT);
                                }
                            }).show();
                }
                @Override public void fail(String faError) { a.kit.toast(faError); }
            });
        }), a.kit.lp(-1, -2));
        body.addView(workerLabel, a.kit.lp(-1, -2));
        body.addView(a.kit.text("مدرک تحویل (اختیاری)", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final android.graphics.Bitmap[] signBmp = {null};
        final android.graphics.Bitmap[] photoBmp = {null};
        final android.widget.TextView proofStatus = a.kit.text("امضا: — • عکس: —",
                12f, Theme.MUTED, false);
        final Runnable paintProof = () -> proofStatus.setText(
                "امضا: " + (signBmp[0] == null ? "—" : "✓")
                        + " • عکس: " + (photoBmp[0] == null ? "—" : "✓"));
        LinearLayout proofRow = a.kit.h();
        proofRow.addView(a.kit.btnGhost("🖊 امضای گیرنده", Theme.GOLD, v -> signDialog(signBmp,
                paintProof)), a.kit.wlp(1f));
        proofRow.addView(a.kit.space(8));
        proofRow.addView(a.kit.btnGhost("📷 عکس تحویل", Theme.GOLD, v -> {
            try {
                a.startPhoto(uri -> {
                    try {
                        photoBmp[0] = loadScaled(uri, 1200);
                        paintProof.run();
                        a.kit.toast(photoBmp[0] == null ? "خواندن عکس ممکن نشد" : "عکس ثبت شد");
                    } catch (Exception e) {
                        a.kit.toast("خواندن عکس ممکن نشد");
                    }
                });
            } catch (Exception e) {
                a.kit.toast("دوربین باز نشد");
            }
        }), a.kit.wlp(1f));
        body.addView(proofRow, a.kit.lp(-1, -2));
        body.addView(proofStatus, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(a.kit.gap(8));
        body.addView(a.kit.btn("ثبت تحویل + رسید PDF", v -> {
            String receiver;
            if ("out".equals(mode[0])) {
                if (distName[0].isEmpty()) {
                    a.kit.toast("موزع را انتخاب کنید");
                    return;
                }
                receiver = "موزع: " + distName[0]
                        + (distCell[0].isEmpty() ? "" : " (" + distCell[0] + ")");
            } else {
                receiver = "مشتری حضوری: " + (f.s("cust").isEmpty() ? "—" : f.s("cust"));
            }
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            String keeper = AtiranAuth.sessionName(a);
            if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
            // Only the ticked items go on the receipt and into the handover record, so a
            // partly delivered invoice is exactly that - partly delivered.
            List<Row> taken = new ArrayList<>();
            StringBuilder takenText = new StringBuilder();
            for (int x = 0; x < items.size(); x++) {
                if (!marked[x]) continue;
                Row it = items.get(x);
                taken.add(it);
                if (takenText.length() > 0) takenText.append("، ");
                takenText.append(it.s("name"))
                        .append(it.s("qty").isEmpty() ? "" : " × " + it.s("qty"));
            }
            if (taken.isEmpty()) {
                a.kit.toast("هیچ کالایی علامت نخورده است");
                return;
            }
            String workerName = worker[0] == null ? "" : worker[0];
            WarehouseWriter.markHanded(a, f.s("id"), f.s("cust"), receiver, keeper,
                    workerName, takenText.toString());
            String sub = "مشتری: " + f.s("cust") + "  •  " + receiver
                    + (workerName.isEmpty() ? "" : "  •  کارگر: " + workerName)
                    + "  •  تحویل‌دهنده: " + keeper + "  •  تاریخ: " + Jalali.todayStr()
                    + "  •  مبلغ: " + Money.compact(f.d("amount")) + " تومان";
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("qty", "تعداد", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("unit", "واحد", ReportCatalog.T_TEXT),
            };
            List<Pdf.Img> images = new ArrayList<>();
            if (signBmp[0] != null) {
                savePng("tahvil-" + f.s("id") + "-sign.png", signBmp[0]);
                images.add(new Pdf.Img(signBmp[0], "امضای گیرنده"));
            }
            if (photoBmp[0] != null) {
                savePng("tahvil-" + f.s("id") + "-photo.png", photoBmp[0]);
                images.add(new Pdf.Img(photoBmp[0], "عکس تحویل"));
            }
            if (images.isEmpty()) a.sharePdf("رسید تحویل فاکتور " + f.s("id"), sub, cols, taken);
            else a.sharePdfImages("رسید تحویل فاکتور " + f.s("id"), sub, cols, taken, images);
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

    private void signDialog(final android.graphics.Bitmap[] out, final Runnable done) {
        LinearLayout b = a.kit.v();
        b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        b.addView(a.kit.text("گیرنده اینجا امضا کند", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final SignView sign = new SignView(a);
        b.addView(sign, new LinearLayout.LayoutParams(-1, Theme.dp(220)));
        final AlertDialog[] box = new AlertDialog[1];
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btnGhost("پاک", Theme.DANGER, v -> sign.clear()), a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(a.kit.btn("ثبت امضا", v -> {
            if (sign.isEmpty()) {
                a.kit.toast("امضایی کشیده نشده است");
                return;
            }
            out[0] = sign.bitmap();
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            done.run();
        }), a.kit.wlp(1f));
        b.addView(row, a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("امضای گیرنده", b, true);
        box[0].show();
    }

    private android.graphics.Bitmap loadScaled(android.net.Uri uri, int maxDim) {
        java.io.InputStream in = null;
        try {
            in = a.getContentResolver().openInputStream(uri);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeStream(in, null, o);
            try {
                in.close();
            } catch (Exception ignored) { }
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / s > maxDim) s *= 2;
            android.graphics.BitmapFactory.Options o2 =
                    new android.graphics.BitmapFactory.Options();
            o2.inSampleSize = Math.max(1, s);
            in = a.getContentResolver().openInputStream(uri);
            return android.graphics.BitmapFactory.decodeStream(in, null, o2);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) { }
        }
    }

    private void savePng(String name, android.graphics.Bitmap bmp) {
        try {
            java.io.File f = new java.io.File(ShareProvider.shareDir(a), name);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (Exception ignored) { }
    }

    private void pickDistributor(final String[] name, final String[] cell, final Runnable done) {
        a.kit.toast("در حال دریافت موزع‌ها…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whVisitors(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                if (rows == null || rows.isEmpty()) {
                    a.kit.toast("موزعی یافت نشد");
                    return;
                }
                LinearLayout b = a.kit.v();
                b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
                final AlertDialog[] box = new AlertDialog[1];
                for (Row r : rows) {
                    final Row row = r;
                    String label = row.s("name")
                            + (row.s("cell").isEmpty() ? "" : " • " + row.s("cell"));
                    b.addView(a.kit.btnGhost(label, Theme.GOLD, v -> {
                        name[0] = row.s("name");
                        cell[0] = row.s("cell");
                        try {
                            box[0].dismiss();
                        } catch (Exception ignored) { }
                        a.kit.toast("موزع: " + name[0]);
                        done.run();
                    }), a.kit.lp(-1, -2));
                }
                box[0] = a.kit.dialog("انتخاب موزع", a.kit.scrollWrap(b, 420), true);
                box[0].show();
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }
}
