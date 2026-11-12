package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.data.PurchasePreinvoiceEngine;
import ir.meelano.windowsinvoice.data.PurchasePreinvoiceExportWin;
import ir.meelano.windowsinvoice.data.PurchasePreinvoiceStoreWin;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * «پیش‌فاکتور خرید» روی ویندوز — فرم تمام‌کیبورد.
 *
 * <p>کلیدها: F2 سند تازه، F3 افزودن قلم، F4 ثبت در دفترچهٔ محلی، F5 خروجی فایل،
 * F6 چاپ، و Enter در هر فیلد فوکوس را به فیلد بعدی می‌برد (ترتیب طبیعی فرم).
 * اختلاف هر قلم با قیمت مرجع و همهٔ جمع‌ها با هر کلید به‌صورت لحظه‌ای بازمحاسبه می‌شوند.</p>
 */
public final class PreinvoicePane extends VBox {

    private final Stage stage;
    private PurchasePreinvoiceEngine.Draft draft = new PurchasePreinvoiceEngine.Draft();
    private final List<LineRow> rows = new ArrayList<>();
    private final List<Node> focusOrder = new ArrayList<>();
    private final VBox rowsBox = new VBox(8);
    private final Label[] totals = new Label[8];
    private final ComboBox<String> drafts = new ComboBox<>();
    private final Label status = new Label("");

    private static final class LineRow {
        TextField code, name, unit, qty, price, ref, tax, duty;
        Label total, diff;
        Button discs;
        PurchasePreinvoiceEngine.Line line = new PurchasePreinvoiceEngine.Line();
    }

    public PreinvoicePane(Stage stage) {
        this(stage, new PurchasePreinvoiceEngine.Draft());
    }

    public PreinvoicePane(Stage stage, PurchasePreinvoiceEngine.Draft initial) {
        this.stage = stage;
        this.draft = initial == null ? new PurchasePreinvoiceEngine.Draft() : initial;
        getStyleClass().add("customer-pane");
        setPadding(new Insets(12));
        setSpacing(10);

        Label title = new Label("پیش‌فاکتور خرید · کالای بدون فاکتور خرید");
        title.getStyleClass().add("customer-section-title");
        Label hint = new Label("F2 سند تازه · F3 قلم تازه · F4 ثبت · F5 خروجی · F6 چاپ · Enter = فیلد بعدی");
        hint.getStyleClass().add("customer-hint");
        getChildren().addAll(title, hint);

        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_RIGHT);
        drafts.setPromptText("دفترچهٔ محلی سندها");
        drafts.setOnAction(e -> openDraft(drafts.getSelectionModel().getSelectedIndex()));
        Button fresh = button("سند تازه (F2)", false);
        fresh.setOnAction(e -> newDraft());
        Button save = button("ثبت در سامانه (F4)", true);
        save.setOnAction(e -> save());
        Button export = button("خروجی فایل (F5)", false);
        export.setOnAction(e -> exportMenu());
        Button print = button("چاپ (F6)", false);
        print.setOnAction(e -> print());
        Button remove = button("حذف سند", false);
        remove.setOnAction(e -> deleteDraft());
        bar.getChildren().addAll(drafts, fresh, save, export, print, remove);
        getChildren().add(bar);

        getChildren().add(partiesGrid());
        Label linesTitle = new Label("اقلام کالا");
        linesTitle.getStyleClass().add("customer-section-title");
        getChildren().add(linesTitle);
        ScrollPane scroll = new ScrollPane(rowsBox);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
        Button addLine = button("＋ افزودن قلم (F3)", false);
        addLine.setOnAction(e -> addLine());
        getChildren().add(addLine);

        GridPane tot = new GridPane();
        tot.setHgap(14);
        tot.setVgap(4);
        String[] names = {"جمع اقلام", "تخفیفات", "مالیات", "عوارض", "جمع کل", "تخفیف نقدی", "قابل پرداخت", "اختلاف کل"};
        for (int i = 0; i < 8; i++) {
            Label n = new Label(names[i] + ":");
            n.getStyleClass().add("customer-field-label");
            totals[i] = new Label("—");
            totals[i].getStyleClass().add("customer-summary-value");
            tot.add(n, i % 4 * 2, i / 4);
            tot.add(totals[i], i % 4 * 2 + 1, i / 4);
        }
        getChildren().addAll(tot, status);
        status.getStyleClass().add("customer-hint");

        for (PurchasePreinvoiceEngine.Line l : draft.lines) {
            LineRow r = new LineRow();
            r.line = l;
            rows.add(r);
            rowsBox.getChildren().add(lineNode(r, false));
        }
        refreshDrafts();
        recompute();
        accelerators();
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(text);
        b.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        b.setFocusTraversable(true);
        return b;
    }

    private void accelerators() {
        if (stage == null || stage.getScene() == null) return;
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.F2), this::newDraft);
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.F3), this::addLine);
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.F4), this::save);
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.F5), this::exportMenu);
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.F6), this::print);
    }

    // --------------------------------------------------------------- فرم سربرگ
    private Node partiesGrid() {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.setPadding(new Insets(6));
        TextField supplierName = field("نام فروشنده / تامین‌کننده", draft.supplierName);
        supplierName.textProperty().addListener((o, a, b) -> { draft.supplierName = b; });
        TextField supplierCode = field("کد فروشنده", draft.supplierCode);
        supplierCode.textProperty().addListener((o, a, b) -> draft.supplierCode = b);
        TextField nat = field("شناسه ملی", draft.supplierNatId);
        nat.textProperty().addListener((o, a, b) -> draft.supplierNatId = b);
        TextField phone = field("تلفن", draft.supplierPhone);
        phone.textProperty().addListener((o, a, b) -> draft.supplierPhone = b);
        TextField addr = field("نشانی", draft.supplierAddress);
        addr.textProperty().addListener((o, a, b) -> draft.supplierAddress = b);
        TextField docNo = field("شماره سند", draft.docNo);
        docNo.textProperty().addListener((o, a, b) -> draft.docNo = b);
        TextField buyer = field("خریدار (فروشگاه)", draft.buyerName);
        buyer.textProperty().addListener((o, a, b) -> draft.buyerName = b);
        TextField buyerCode = field("کد خریدار", draft.buyerCode);
        buyerCode.textProperty().addListener((o, a, b) -> draft.buyerCode = b);
        TextField due = field("سررسید (روز)", String.valueOf(draft.dueDays));
        due.textProperty().addListener((o, a, b) -> draft.dueDays = (int) num(b));
        TextField cash = field("تخفیف نقدی پایانی ٪", str(draft.cashDiscPct));
        cash.textProperty().addListener((o, a, b) -> { draft.cashDiscPct = num(b); recompute(); });
        TextField taxMode = field("حالت مالیات (add/incl)", draft.taxMode);
        taxMode.textProperty().addListener((o, a, b) -> {
            draft.taxMode = "incl".equals(b) ? PurchasePreinvoiceEngine.TAX_INCLUSIVE : PurchasePreinvoiceEngine.TAX_ADD;
            recompute();
        });
        TextField rounding = field("گردکردن (none/rial/toman/hundred/thousand)", draft.rounding);
        rounding.textProperty().addListener((o, a, b) -> { draft.rounding = b.trim(); recompute(); });
        TextArea note = new TextArea(draft.note);
        note.setPromptText("یادداشت سند");
        note.setPrefRowCount(2);
        note.textProperty().addListener((o, a, b) -> draft.note = b);
        int r = 0;
        g.add(label("فروشنده"), 0, r); g.add(supplierName, 1, r); g.add(label("کد"), 2, r); g.add(supplierCode, 3, r);
        g.add(label("شناسه ملی"), 4, r); g.add(nat, 5, r);
        r++;
        g.add(label("تلفن"), 0, r); g.add(phone, 1, r); g.add(label("شماره سند"), 2, r); g.add(docNo, 3, r);
        g.add(label("سررسید روز"), 4, r); g.add(due, 5, r);
        r++;
        g.add(label("خریدار"), 0, r); g.add(buyer, 1, r); g.add(label("کد خریدار"), 2, r); g.add(buyerCode, 3, r);
        g.add(label("تخفیف نقدی ٪"), 4, r); g.add(cash, 5, r);
        r++;
        g.add(label("حالت مالیات"), 0, r); g.add(taxMode, 1, r); g.add(label("گردکردن"), 2, r); g.add(rounding, 3, r);
        g.add(label("نشانی"), 4, r); g.add(addr, 5, r);
        r++;
        g.add(label("یادداشت"), 0, r); g.add(note, 1, r, 5, 1);
        for (Node n : new Node[]{supplierName, supplierCode, nat, phone, docNo, due, buyer, buyerCode, cash, taxMode, rounding, addr, note}) {
            focusOrder.add(n);
        }
        return g;
    }

    private Label label(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("customer-field-label");
        return l;
    }

    private TextField field(String prompt, String value) {
        TextField t = new TextField(value == null ? "" : value);
        t.setPromptText(prompt);
        t.getStyleClass().add("customer-detail-field");
        t.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) focusNext(t);
        });
        return t;
    }

    private void focusNext(Node current) {
        int i = focusOrder.indexOf(current);
        if (i >= 0 && i + 1 < focusOrder.size()) focusOrder.get(i + 1).requestFocus();
    }

    // --------------------------------------------------------------- اقلام
    private void addLine() {
        LineRow row = new LineRow();
        rows.add(row);
        rowsBox.getChildren().add(lineNode(row, true));
        row.code.requestFocus();
        recompute();
    }

    private Node lineNode(final LineRow row, boolean attach) {
        final PurchasePreinvoiceEngine.Line l = row.line;
        VBox box = new VBox(4);
        box.setPadding(new Insets(6));
        box.getStyleClass().add("customer-details-card");
        HBox top = new HBox(6);
        row.code = field("کد کالا", l.code);
        row.code.textProperty().addListener((o, a, b) -> l.code = b);
        row.name = field("نام کالا", l.name);
        row.name.textProperty().addListener((o, a, b) -> l.name = b);
        row.unit = field("واحد", l.unit);
        row.unit.textProperty().addListener((o, a, b) -> l.unit = b);
        row.qty = field("تعداد", str(l.qty));
        row.qty.textProperty().addListener((o, a, b) -> { l.qty = num(b); recompute(); });
        row.price = field("قیمت واحد", str(l.price));
        row.price.textProperty().addListener((o, a, b) -> { l.price = num(b); recompute(); });
        row.ref = field("قیمت مرجع", str(l.refPrice));
        row.ref.textProperty().addListener((o, a, b) -> { l.refPrice = num(b); recompute(); });
        row.tax = field("مالیات ٪", str(l.taxPct));
        row.tax.textProperty().addListener((o, a, b) -> { l.taxPct = num(b); recompute(); });
        row.duty = field("عوارض ٪", str(l.dutyPct));
        row.duty.textProperty().addListener((o, a, b) -> { l.dutyPct = num(b); recompute(); });
        row.discs = button("تخفیف‌ها (0)", false);
        row.discs.setOnAction(e -> discsDialog(row));
        Button remove = button("حذف", false);
        remove.setOnAction(e -> {
            rows.remove(row);
            draft.lines.remove(l);
            rowsBox.getChildren().remove(box);
            focusOrder.removeAll(List.of(row.code, row.name, row.unit, row.qty, row.price, row.ref, row.tax, row.duty));
            recompute();
        });
        top.getChildren().addAll(row.code, row.name, row.unit, row.qty, row.price, row.ref, row.tax, row.duty, row.discs, remove);
        row.total = new Label("جمع قلم: —");
        row.total.getStyleClass().add("customer-summary-value");
        row.diff = new Label("اختلاف با مرجع: —");
        row.diff.getStyleClass().add("customer-hint");
        box.getChildren().addAll(top, row.total, row.diff);
        if (attach) draft.lines.add(l);
        for (Node n : new Node[]{row.code, row.name, row.unit, row.qty, row.price, row.ref, row.tax, row.duty}) {
            focusOrder.add(n);
        }
        return box;
    }

    private void discsDialog(final LineRow row) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(
                "درصد ردیف", "ریالی هر واحد", "پله‌ای تعداد", "پله‌ای مبلغ", "درصد کل فاکتور", "کد تخفیف"));
        type.getSelectionModel().select(0);
        TextField v1 = new TextField();
        v1.setPromptText("درصد یا مبلغ هر واحد");
        TextField v2 = new TextField();
        v2.setPromptText("سقف ریالی (کد تخفیف)");
        TextField spec = new TextField();
        spec.setPromptText("پله‌ها مانند 10:5,50:10 یا نام کد");
        Button add = button("افزودن تخفیف", true);
        add.setOnAction(e -> {
            PurchasePreinvoiceEngine.DiscType t = PurchasePreinvoiceEngine.DiscType.values()[type.getSelectionModel().getSelectedIndex()];
            row.line.discs.add(new PurchasePreinvoiceEngine.Disc(t, num(v1.getText()), num(v2.getText()), spec.getText()));
            row.discs.setText("تخفیف‌ها (" + row.line.discs.size() + ")");
            recompute();
        });
        VBox list = new VBox(4);
        for (int i = 0; i < row.line.discs.size(); i++) {
            final int at = i;
            Button del = button(row.line.discs.get(i).type.fa + " • حذف", false);
            del.setOnAction(e -> {
                row.line.discs.remove(at);
                discsDialog(row);
                recompute();
            });
            list.getChildren().add(del);
        }
        box.getChildren().addAll(type, v1, v2, spec, add, list);
        Stage dlg = new Stage();
        dlg.initOwner(stage);
        dlg.setScene(new javafx.scene.Scene(box, 380, 420));
        dlg.setTitle("تخفیف‌های قلم");
        dlg.show();
    }

    // --------------------------------------------------------------- محاسبهٔ زنده و اقدام‌ها
    private void recompute() {
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(draft);
        for (LineRow row : rows) {
            row.total.setText("جمع قلم: " + PurchasePreinvoiceExportWin.faNum(Math.round(row.line.total)) + " ریال");
            if (row.line.refPrice > 0) {
                row.diff.setText("اختلاف با مرجع: " + PurchasePreinvoiceExportWin.faNum(Math.round(row.line.diffAmt))
                        + " ریال (" + String.format(Locale.US, "%.1f", row.line.diffPct) + "٪)");
            } else {
                row.diff.setText("قیمت مرجع وارد نشده؛ اختلاف محاسبه نمی‌شود.");
            }
        }
        String[] vals = {
                PurchasePreinvoiceExportWin.faNum(Math.round(t.goods)) + " ریال",
                PurchasePreinvoiceExportWin.faNum(Math.round(t.discSum)) + " ریال ("
                        + String.format(Locale.US, "%.1f", t.discPct) + "٪)",
                PurchasePreinvoiceExportWin.faNum(Math.round(t.tax)) + " ریال",
                PurchasePreinvoiceExportWin.faNum(Math.round(t.duty)) + " ریال",
                PurchasePreinvoiceExportWin.faNum(Math.round(t.grand)) + " ریال",
                PurchasePreinvoiceExportWin.faNum(Math.round(t.cashDisc)) + " ریال",
                PurchasePreinvoiceExportWin.faNum(Math.round(t.payable)) + " ریال",
                PurchasePreinvoiceExportWin.faNum(Math.round(t.diff)) + " ریال"};
        for (int i = 0; i < 8; i++) totals[i].setText(vals[i]);
    }

    private void refreshDrafts() {
        drafts.getItems().clear();
        for (PurchasePreinvoiceEngine.Draft d : PurchasePreinvoiceStoreWin.load()) {
            drafts.getItems().add((d.docNo.isEmpty() ? "بی‌شماره" : d.docNo) + " • " + d.supplierName);
        }
    }

    private void openDraft(int index) {
        List<PurchasePreinvoiceEngine.Draft> all = PurchasePreinvoiceStoreWin.load();
        if (index < 0 || index >= all.size()) return;
        draft = all.get(index);
        rebuild();
    }

    private void newDraft() {
        draft = new PurchasePreinvoiceEngine.Draft();
        rebuild();
    }

    /** فرم را با سند انتخاب‌شده از نو می‌سازد (ریشهٔ صحنه عوض می‌شود). */
    private void rebuild() {
        if (stage == null || stage.getScene() == null) return;
        PreinvoicePane fresh = new PreinvoicePane(stage, draft);
        stage.getScene().setRoot(fresh);
    }

    private void save() {
        if (draft.supplierName.trim().isEmpty()) { status.setText("نام فروشنده را وارد کنید."); return; }
        if (draft.lines.isEmpty()) { status.setText("دست‌کم یک قلم کالا لازم است."); return; }
        try {
            PurchasePreinvoiceStoreWin.save(draft);
            status.setText("پیش‌فاکتور در دفترچهٔ محلی ویندوز ثبت شد: " + PurchasePreinvoiceStoreWin.ledgerPath());
            refreshDrafts();
        } catch (IOException e) {
            status.setText("ثبت ممکن نشد: " + e.getMessage());
        }
    }

    private void deleteDraft() {
        if (draft.id.isEmpty()) { status.setText("سندی انتخاب نشده است."); return; }
        try {
            PurchasePreinvoiceStoreWin.delete(draft.id);
            refreshDrafts();
            status.setText("سند از دفترچه حذف شد.");
        } catch (IOException e) {
            status.setText("حذف ممکن نشد: " + e.getMessage());
        }
    }

    private void exportMenu() {
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(draft);
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("اکسل xls", "*.xls"),
                new FileChooser.ExtensionFilter("ورد doc", "*.doc"),
                new FileChooser.ExtensionFilter("CSV", "*.csv"),
                new FileChooser.ExtensionFilter("متن txt", "*.txt"));
        java.io.File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            byte[] data;
            String name = f.getName().toLowerCase(Locale.US);
            if (name.endsWith(".xls")) data = PurchasePreinvoiceExportWin.xls(draft, t);
            else if (name.endsWith(".doc")) data = PurchasePreinvoiceExportWin.doc(draft, t);
            else if (name.endsWith(".csv")) data = PurchasePreinvoiceExportWin.csv(draft, t);
            else data = PurchasePreinvoiceExportWin.summary(draft, t).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(f.toPath(), data);
            status.setText("فایل ذخیره شد: " + f.getAbsolutePath());
        } catch (IOException e) {
            status.setText("ذخیره ممکن نشد: " + e.getMessage());
        }
    }

    private void print() {
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(draft);
        TextFlow flow = new TextFlow();
        flow.setPrefWidth(700);
        for (String line : PurchasePreinvoiceExportWin.summary(draft, t).split("\n")) {
            Text tx = new Text(line + "\n");
            flow.getChildren().add(tx);
        }
        javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
        if (job == null) { status.setText("چاپگری در دسترس نیست."); return; }
        if (job.showPrintDialog(stage)) {
            if (job.printPage(flow)) {
                job.endJob();
                status.setText("گزارش به چاپ سپرده شد.");
            } else {
                status.setText("چاپ ممکن نشد.");
            }
        }
    }

    private static double num(String s) {
        try { return Double.parseDouble(s == null ? "" : s.replace(",", "").trim()); }
        catch (RuntimeException e) { return 0; }
    }

    private static String str(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }
}
