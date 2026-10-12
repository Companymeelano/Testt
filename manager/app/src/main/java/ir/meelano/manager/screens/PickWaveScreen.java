package ir.meelano.manager.screens;

import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.HandoverDb;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.WarehousePickStore;
import ir.meelano.manager.core.WarehouseWriter;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/** Multi-invoice pick wave with consolidated quantities, distributor filtering and scan tick-off. */
public class PickWaveScreen extends Screen {
    private String day = Jalali.todayStr();
    private String distributor = "";
    private String routeFilterId = "";
    private String routeFilterName = "";
    private LinearLayout content;
    private LinearLayout invoiceBox;
    private LinearLayout pickBox;
    private android.widget.TextView selectedCount;
    private EditText routeLabel;
    private String routeText = "";
    private List<Row> shownInvoices = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, PickLine> lineByCode = new LinkedHashMap<>();
    private List<PickLine> pickLines = new ArrayList<>();
    private boolean loadingPick;

    private static final class PickLine {
        String code = "";
        String name = "";
        String unit = "";
        double qty;
        boolean picked;
        final Set<String> invoices = new LinkedHashSet<>();
    }

    public PickWaveScreen(MainActivity a) { super(a); }
    @Override public String id() { return "pick_wave"; }
    @Override public String title() { return "برداشت دسته‌ای"; }
    @Override public String glyph() { return "▣"; }
    @Override public int accent() { return Theme.SUCCESS; }

    @Override public void render(LinearLayout content) {
        this.content = content;
        content.removeAllViews();
        content.addView(a.kit.hero(glyph(), title(),
                "چند فاکتور را انتخاب کنید؛ فهرست کالاها تجمیع و هنگام بارگیری اسکن می‌شود", accent()),
                a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));

        LinearLayout controls = a.kit.card(Theme.STEEL);
        LinearLayout dateRow = a.kit.h();
        dateRow.addView(a.kit.text("تاریخ فاکتور: " + Money.fa(day), 13f, Theme.TEXT, true), a.kit.wlp(1f));
        dateRow.addView(a.kit.btnGhost("انتخاب", Theme.GOLD, v -> a.kit.dateDialog(
                "تاریخ موج بارگیری", day, picked -> {
                    day = Jalali.normalizeDate(picked);
                    selected.clear();
                    pickLines.clear();
                    if (routeLabel != null) routeText = routeLabel.getText().toString();
                    render(content);
                })), a.kit.lp(-2, -2));
        controls.addView(dateRow, a.kit.lp(-1, -2));
        controls.addView(a.kit.gap(6));
        controls.addView(a.kit.btnGhost(distributor.isEmpty()
                ? "فیلتر بر اساس موزع (اختیاری)" : "موزع: " + distributor + " • تغییر",
                Theme.INFO, v -> chooseDistributor()), a.kit.lp(-1, -2));
        if (!distributor.isEmpty()) controls.addView(a.kit.btnGhost("پاک‌کردن فیلتر موزع",
                Theme.STEEL, v -> { distributor = ""; selected.clear(); pickLines.clear(); render(content); }),
                a.kit.lp(-1, -2));
        controls.addView(a.kit.btnGhost(routeFilterName.isEmpty()
                ? "فیلتر بر اساس مسیر آتیران (اختیاری)" : "مسیر: " + routeFilterName + " • تغییر",
                Theme.VIOLET, v -> chooseRoute()), a.kit.lp(-1, -2));
        if (!routeFilterId.isEmpty()) controls.addView(a.kit.btnGhost("پاک‌کردن فیلتر مسیر",
                Theme.STEEL, v -> { routeFilterId = ""; routeFilterName = ""; selected.clear(); pickLines.clear(); render(content); }),
                a.kit.lp(-1, -2));
        routeLabel = a.kit.edit("برچسب موج / نوبت (اختیاری)", routeText);
        routeLabel.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                routeText = s == null ? "" : s.toString();
            }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        controls.addView(routeLabel, a.kit.lp(-1, -2));
        controls.addView(a.kit.hint("می‌توانید فاکتورها را با مسیر آتیران یا موزع محدود کنید؛ انتخاب هم‌زمان هر دو هم ممکن است. موزع فقط از نقش‌های شناسایی‌شده نشان داده می‌شود."),
                a.kit.lp(-1, -2));
        content.addView(controls, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));

        LinearLayout invoiceCard = a.kit.card(Theme.GOLD);
        LinearLayout invoiceHead = a.kit.h();
        invoiceHead.addView(a.kit.text("فاکتورهای آمادهٔ برداشت", 13.5f, Theme.TEXT, true), a.kit.wlp(1f));
        selectedCount = a.kit.text("۰ انتخاب", 11f, Theme.GOLD, true);
        invoiceHead.addView(selectedCount, a.kit.lp(-2, -2));
        invoiceCard.addView(invoiceHead, a.kit.lp(-1, -2));
        invoiceCard.addView(a.kit.hint("فهرست روز، حداکثر شامل ۵۰۰ فاکتور بازِ فعال است."), a.kit.lp(-1, -2));
        LinearLayout allRow = a.kit.h();
        allRow.addView(a.kit.btnGhost("انتخاب همه", Theme.GOLD, v -> selectAll(true)), a.kit.wlp(1f));
        allRow.addView(a.kit.space(8));
        allRow.addView(a.kit.btnGhost("پاک‌کردن", Theme.STEEL, v -> selectAll(false)), a.kit.wlp(1f));
        invoiceCard.addView(allRow, a.kit.lp(-1, -2));
        invoiceBox = a.kit.v();
        invoiceCard.addView(invoiceBox, a.kit.lp(-1, -2));
        invoiceCard.addView(a.kit.btnGold("ساخت فهرست تجمیعی و شروع بارگیری", v -> makePickList()),
                a.kit.lp(-1, -2));
        content.addView(invoiceCard, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        pickBox = a.kit.v();
        content.addView(pickBox, a.kit.lp(-1, -2));
        List<JSONObject> savedWaves = WarehousePickStore.list(a);
        if (!savedWaves.isEmpty()) {
            JSONObject last = savedWaves.get(0);
            JSONArray savedItems = last.optJSONArray("items");
            JSONArray savedInvoices = last.optJSONArray("invoices");
            LinearLayout previous = a.kit.card(Theme.INFO);
            previous.addView(a.kit.kv("آخرین موج ذخیره‌شده", last.optString("route", "—"), Theme.INFO),
                    a.kit.lp(-1, -2));
            previous.addView(a.kit.hint(Money.fa(last.optString("day", "")) + " • "
                    + Money.fa(String.valueOf(savedInvoices == null ? 0 : savedInvoices.length()))
                    + " فاکتور • " + Money.fa(String.valueOf(savedItems == null ? 0 : savedItems.length()))
                    + " قلم • ذخیرهٔ محلی"), a.kit.lp(-1, -2));
            content.addView(previous, a.kit.lp(-1, -2));
        }
        loadInvoices();
    }

    private void loadInvoices() {
        if (invoiceBox == null || content == null) return;
        invoiceBox.removeAllViews();
        invoiceBox.addView(a.kit.loading("در حال دریافت فاکتورهای روز…"), a.kit.lp(-1, -2));
        final String selectedDay = day;
        final String selectedDistributor = distributor;
        final String selectedRouteId = routeFilterId;
        a.repo.run(c -> {
            Meta m = new Meta(c);
            List<Row> rows = Repo.exec(c, MasterQueries.whDeliveriesForPicking(m, selectedDay));
            Set<String> handed = HandoverDb.markedSet(c, selectedDay);
            if (handed == null) handed = new LinkedHashSet<>();
            handed.addAll(WarehouseWriter.handedOn(a, selectedDay));
            List<Row> out = new ArrayList<>();
            for (Row r : rows) {
                String no = r.s("id");
                if (handed.contains(no)) continue;
                if (!selectedDistributor.isEmpty()
                        && !selectedDistributor.equalsIgnoreCase(r.s("visitor").trim())) continue;
                if (!selectedRouteId.isEmpty()
                        && !selectedRouteId.equalsIgnoreCase(r.s("routeId").trim())) continue;
                out.add(r);
            }
            return out;
        }, new Repo.Cb<List<Row>>() {
            @Override public void ok(List<Row> rows) {
                if (content == null || !selectedDay.equals(day) || !selectedDistributor.equals(distributor)
                        || !selectedRouteId.equals(routeFilterId)) return;
                shownInvoices = rows == null ? new ArrayList<Row>() : rows;
                drawInvoices();
            }
            @Override public void fail(String faError) {
                if (invoiceBox == null || !selectedDay.equals(day)
                        || !selectedDistributor.equals(distributor) || !selectedRouteId.equals(routeFilterId)) return;
                invoiceBox.removeAllViews();
                invoiceBox.addView(a.kit.empty("فاکتورها دریافت نشدند", faError), a.kit.lp(-1, -2));
            }
        });
    }

    private void drawInvoices() {
        if (invoiceBox == null) return;
        invoiceBox.removeAllViews();
        if (shownInvoices.isEmpty()) {
            String filters = (routeFilterName.isEmpty() ? "" : "مسیر «" + routeFilterName + "»")
                    + (!routeFilterName.isEmpty() && !distributor.isEmpty() ? " و " : "")
                    + (distributor.isEmpty() ? "" : "موزع «" + distributor + "»");
            String msg = filters.isEmpty() ? "فاکتورِ تحویل‌نشده‌ای برای این روز پیدا نشد."
                    : "فاکتورِ بازِ این روز برای " + filters + " پیدا نشد؛ فیلترها را پاک کنید یا روز دیگری را ببینید.";
            invoiceBox.addView(a.kit.empty(msg, "فهرست فقط تا ۵۰۰ فاکتور روز بارگذاری می‌شود."), a.kit.lp(-1, -2));
            updateCount();
            return;
        }
        for (Row r : shownInvoices) {
            final Row invoice = r;
            String id = invoice.s("id");
            CheckBox cb = new CheckBox(a);
            cb.setText("فاکتور " + Money.fa(id) + " • " + invoice.s("cust")
                    + " • " + Money.compactMoney(invoice.d("amount"))
                    + (invoice.s("visitor").isEmpty() ? "" : " • " + invoice.s("visitor")));
            cb.setTextColor(Theme.TEXT);
            cb.setTypeface(Theme.face(false));
            cb.setTextSize(12f * Theme.fontScale() * Theme.typeScale());
            cb.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6));
            cb.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Theme.GOLD));
            cb.setChecked(selected.contains(id));
            cb.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(id); else selected.remove(id);
                updateCount();
            });
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, Theme.dp(3), 0, Theme.dp(3));
            invoiceBox.addView(cb, p);
        }
        updateCount();
    }

    private void updateCount() {
        if (selectedCount != null) selectedCount.setText(Money.fa(String.valueOf(selected.size())) + " انتخاب");
    }

    private void selectAll(boolean on) {
        if (on) for (Row r : shownInvoices) selected.add(r.s("id"));
        else for (Row r : shownInvoices) selected.remove(r.s("id"));
        drawInvoices();
    }

    private void chooseDistributor() {
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whVisitors(m));
        }, new Repo.Cb<List<Row>>() {
            @Override public void ok(List<Row> rows) {
                List<Row> distributors = new ArrayList<>();
                if (rows != null) for (Row r : rows)
                    if (r != null && AtiranAuth.isDistributorName(r.s("name"))) distributors.add(r);
                if (distributors.isEmpty()) {
                    a.kit.toast("موزعی با نقش مشخص یافت نشد؛ فیلتر موزع اعمال نشد");
                    return;
                }
                a.kit.searchPicker("انتخاب موزع", distributors, new String[]{"name", "cell"},
                        picked -> {
                            distributor = picked.s("name");
                            selected.clear();
                            pickLines.clear();
                            if (routeLabel != null) routeText = routeLabel.getText().toString();
                            if (content != null) render(content);
                        }).show();
            }
            @Override public void fail(String faError) { a.kit.toast(faError); }
        });
    }

    private void chooseRoute() {
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.lookupRoutes(m));
        }, new Repo.Cb<List<Row>>() {
            @Override public void ok(List<Row> rows) {
                if (rows == null || rows.isEmpty()) {
                    a.kit.toast("مسیری در آتیران یافت نشد");
                    return;
                }
                a.kit.searchPicker("انتخاب مسیر", rows, new String[]{"name"}, picked -> {
                    routeFilterId = picked.s("id").trim();
                    routeFilterName = picked.s("name");
                    selected.clear();
                    pickLines.clear();
                    if (routeLabel != null) routeText = routeLabel.getText().toString();
                    if (content != null) render(content);
                }).show();
            }
            @Override public void fail(String faError) { a.kit.toast(faError); }
        });
    }

    private void makePickList() {
        if (loadingPick) return;
        final List<Row> chosen = new ArrayList<>();
        for (Row r : shownInvoices) if (selected.contains(r.s("id"))) chosen.add(r);
        if (chosen.isEmpty()) {
            a.kit.toast("حداقل یک فاکتور را انتخاب کنید");
            return;
        }
        final String waveDay = day;
        final String waveDistributor = distributor;
        final String waveRouteId = routeFilterId;
        loadingPick = true;
        pickBox.removeAllViews();
        pickBox.addView(a.kit.loading("در حال تجمیع اقلام انتخاب‌شده…"), a.kit.lp(-1, -2));
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Map<String, PickLine> map = new LinkedHashMap<>();
            for (Row invoice : chosen) {
                String invoiceNo = invoice.s("id");
                List<Row> items = Repo.exec(c, MasterQueries.whDeliveryItems(m, invoiceNo));
                for (Row item : items) {
                    String code = item.s("code").trim();
                    String key = code.isEmpty() ? item.s("name").trim() + "|" + item.s("unit") : code;
                    PickLine line = map.get(key);
                    if (line == null) {
                        line = new PickLine();
                        line.code = code;
                        line.name = item.s("name");
                        line.unit = item.s("unit");
                        map.put(key, line);
                    }
                    line.qty += item.d("qty");
                    line.invoices.add(invoiceNo);
                }
            }
            return new ArrayList<>(map.values());
        }, new Repo.Cb<List<PickLine>>() {
            @Override public void ok(List<PickLine> rows) {
                loadingPick = false;
                if (content == null || !waveDay.equals(day) || !waveDistributor.equals(distributor)
                        || !waveRouteId.equals(routeFilterId)) return;
                pickLines = rows == null ? new ArrayList<PickLine>() : rows;
                lineByCode.clear();
                for (PickLine line : pickLines) if (!line.code.isEmpty()) lineByCode.put(line.code, line);
                drawPickList();
            }
            @Override public void fail(String faError) {
                loadingPick = false;
                if (content == null || !waveDay.equals(day) || !waveDistributor.equals(distributor)
                        || !waveRouteId.equals(routeFilterId)) return;
                pickBox.removeAllViews();
                pickBox.addView(a.kit.error(faError, () -> makePickList()), a.kit.lp(-1, -2));
            }
        });
    }

    private void drawPickList() {
        pickBox.removeAllViews();
        if (pickLines.isEmpty()) {
            pickBox.addView(a.kit.empty("فهرست کالا برای این موج خالی است", "فاکتورهای دیگری را انتخاب کنید."),
                    a.kit.lp(-1, -2));
            return;
        }
        LinearLayout card = a.kit.card(Theme.SUCCESS);
        card.addView(a.kit.sectionHead("فهرست تجمیعی بارگیری", null, null), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("هر اسکن یک قلم کالا را برای این موج علامت می‌زند؛ اگر بارکد به کد کالا نگاشت نشده باشد، می‌توانید ردیف را دستی تأیید کنید."),
                a.kit.lp(-1, -2));
        card.addView(a.kit.btnGold("▦ اسکن کالا هنگام بارگیری", v -> scan()), a.kit.lp(-1, -2));
        int done = 0;
        for (PickLine line : pickLines) if (line.picked) done++;
        card.addView(a.kit.kv("پیشرفت", Money.fa(String.valueOf(done)) + " از "
                + Money.fa(String.valueOf(pickLines.size())) + " قلم علامت خورده", done == pickLines.size()
                ? Theme.SUCCESS : Theme.INFO), a.kit.lp(-1, -2));
        for (PickLine line : pickLines) {
            final PickLine item = line;
            String invoiceNames = joinInvoices(line.invoices);
            LinearLayout row = a.kit.card(line.picked ? Theme.SUCCESS : Theme.STEEL);
            row.addView(a.kit.text(line.name, 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            row.addView(a.kit.text("کد " + (line.code.isEmpty() ? "—" : Money.fa(line.code))
                    + " • جمع " + Money.qty(line.qty) + (line.unit.isEmpty() ? "" : " " + line.unit),
                    11.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
            row.addView(a.kit.text("از فاکتورهای: " + invoiceNames, 10.5f, Theme.MUTED, false),
                    a.kit.lp(-1, -2));
            row.addView(a.kit.btnGhost(line.picked ? "✓ بارگیری شد • لغو علامت"
                    : "علامت‌گذاری دستی به‌عنوان بارگیری‌شده", line.picked ? Theme.SUCCESS : Theme.INFO,
                    v -> { item.picked = !item.picked; drawPickList(); }), a.kit.lp(-1, -2));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, Theme.dp(6), 0, 0);
            card.addView(row, p);
        }
        card.addView(a.kit.gap(8));
        card.addView(a.kit.btn("💾 ذخیره موج بارگیری روی این دستگاه", v -> saveWave()), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("ذخیرهٔ موج یک برنامهٔ برداشت محلی است و فاکتور را تحویل‌شده علامت نمی‌زند؛ ثبت تحویل همچنان از صفحهٔ تحویل انجام می‌شود."),
                a.kit.lp(-1, -2));
        pickBox.addView(card, a.kit.lp(-1, -2));
    }

    private String joinInvoices(Set<String> invoices) {
        StringBuilder b = new StringBuilder();
        for (String id : invoices) {
            if (b.length() > 0) b.append("، ");
            b.append(Money.fa(id));
        }
        return b.toString();
    }

    private void scan() {
        try {
            a.startScan(raw -> {
                final String value = raw == null ? "" : raw.trim();
                if (value.isEmpty()) return;
                a.repo.run(c -> {
                    Meta m = new Meta(c);
                    List<Row> rows = Repo.exec(c, MasterQueries.whProductScan(m, value));
                    return rows.isEmpty() ? null : rows.get(0);
                }, new Repo.Cb<Row>() {
                    @Override public void ok(Row product) {
                        if (product == null) {
                            a.kit.toast("کد یا بارکد کالا در آتیران پیدا نشد");
                            return;
                        }
                        PickLine line = lineByCode.get(product.s("code"));
                        if (line == null) {
                            a.kit.toast("این کالا در فهرست موج انتخاب‌شده نیست: " + product.s("name"));
                            return;
                        }
                        if (line.picked) a.kit.toast("این قلم قبلاً در موج علامت خورده است");
                        else {
                            line.picked = true;
                            a.kit.toast("بارگیری شد: " + line.name);
                            drawPickList();
                        }
                    }
                    @Override public void fail(String faError) { a.kit.toast(faError); }
                });
            });
        } catch (Exception e) {
            a.kit.toast("اسکنر باز نشد؛ ردیف‌ها را دستی علامت بزنید");
        }
    }

    private void saveWave() {
        if (pickLines.isEmpty()) return;
        try {
            JSONObject wave = new JSONObject();
            wave.put("day", day);
            wave.put("distributor", distributor);
            wave.put("routeId", routeFilterId);
            wave.put("routeName", routeFilterName);
            String route = routeLabel == null ? routeText.trim() : routeLabel.getText().toString().trim();
            if (route.isEmpty()) route = !routeFilterName.isEmpty() ? routeFilterName
                    : (distributor.isEmpty() ? "موج " + day : distributor);
            wave.put("route", route);
            String keeper = AtiranAuth.sessionName(a);
            if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
            wave.put("keeper", keeper);
            JSONArray invoices = new JSONArray();
            for (Row r : shownInvoices) if (selected.contains(r.s("id"))) invoices.put(r.s("id"));
            wave.put("invoices", invoices);
            JSONArray items = new JSONArray();
            for (PickLine line : pickLines) {
                JSONObject it = new JSONObject();
                it.put("code", line.code);
                it.put("name", line.name);
                it.put("unit", line.unit);
                it.put("qty", line.qty);
                it.put("picked", line.picked);
                JSONArray numbers = new JSONArray();
                for (String no : line.invoices) numbers.put(no);
                it.put("invoices", numbers);
                items.put(it);
            }
            wave.put("items", items);
            if (WarehousePickStore.save(a, wave)) a.kit.toast("موج برداشت روی این دستگاه ذخیره شد");
            else a.kit.toast("ذخیره موج ممکن نشد");
        } catch (Exception e) {
            a.kit.toast("ساخت موج ممکن نشد");
        }
    }
}
