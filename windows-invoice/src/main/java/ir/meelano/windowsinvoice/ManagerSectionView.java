package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.sections.SectionCatalog;
import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionCsv;
import ir.meelano.windowsinvoice.sections.SectionMath;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionFilter;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionKpi;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionReport;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRequest;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRow;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.FileChooser;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One shared window for every Manager section on the desktop: cash box, cheques, stock ledger, sales
 * and finance reports, profit, production, personnel and users.
 *
 * The window is read-only. It only asks the gateway for a {@link SectionReport}, so all SQL, all schema
 * resolution, all KPI maths and all paging live in the {@code sections} package and are identical for a
 * live Atiran database and for the offline preview. Reports the caller's role may not open are refused
 * before any query is built.
 */
public final class ManagerSectionView extends BorderPane implements AutoCloseable {
    private static final int PAGE_SIZE = 200;

    private final AtiranGateway gateway;
    private final Stage stage;
    private final String sectionId;
    private final String role;
    private final ExecutorService io = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "meelano-section-io");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();

    private final ComboBox<VariantSpec> variantCombo = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final TextField contextField = new TextField();
    private final TextField fromField = new TextField();
    private final TextField toField = new TextField();
    private final TextField thresholdField = new TextField();
    private final TextField daysField = new TextField();
    private final Label hintLabel = new Label();
    private final Label modeLabel = new Label();
    private final FlowPane kpiBox = new FlowPane(8, 8);
    private final TableView<SectionRow> table = new TableView<>();
    private final ObservableList<SectionRow> rows = FXCollections.observableArrayList();
    private final Label messageLabel = new Label("آماده");
    private final Label countLabel = new Label("—");
    private final Label pageLabel = new Label("صفحهٔ ۱");
    private final Label noteLabel = new Label();
    private final Button prevButton = button("قبلی", false);
    private final Button nextButton = button("بعدی", false);
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(350));

    private SectionReport current;
    private long requestVersion;
    private int pageIndex;

    private ManagerSectionView(Stage stage, AtiranGateway gateway, String sectionId, String role) {
        this.stage = Objects.requireNonNull(stage);
        this.gateway = Objects.requireNonNull(gateway);
        this.sectionId = sectionId == null ? "" : sectionId;
        this.role = role == null ? "" : role;
        setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        getStyleClass().add("customer-center-root");
        setPadding(new Insets(16));
        setTop(buildHeader());
        setCenter(buildWorkspace());
        setBottom(buildStatusBar());
        configureControls();
        if (!WindowsRoleMenus.allows(this.role, this.sectionId)) {
            setMessage("این بخش برای نقش «" + SectionCatalog.desktopRoleLabel(this.role) + "» فعال نیست.", true);
        } else {
            reload(0);
        }
    }

    /** Opens (or reuses) the section window; the caller keeps one stage per section id. */
    public static Stage open(Window owner, AtiranGateway gateway, String sectionId, String role) {
        Stage stage = new Stage();
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("MEELANO · " + SectionCatalog.titleOf(sectionId));
        ManagerSectionView view = new ManagerSectionView(stage, gateway, sectionId, role);
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1620, Math.max(760, screen.getWidth() * .92));
        double height = Math.min(980, Math.max(520, screen.getHeight() * .90));
        Scene scene = new Scene(view, Math.min(width, screen.getWidth()), Math.min(height, screen.getHeight()));
        scene.getStylesheets().add(ManagerSectionView.class.getResource("/styles.css").toExternalForm());
        scene.getRoot().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        stage.setScene(scene);
        stage.setMinWidth(Math.min(900, screen.getWidth()));
        stage.setMinHeight(Math.min(600, screen.getHeight()));
        stage.setOnHidden(event -> view.close());
        stage.show();
        stage.centerOnScreen();
        return stage;
    }

    // ------------------------------------------------------------------ layout

    private Node buildHeader() {
        VBox header = new VBox(12);
        header.setPadding(new Insets(7, 6, 14, 6));
        FlowPane row = new FlowPane(8, 8);
        row.setAlignment(Pos.CENTER_RIGHT);
        VBox titleBlock = new VBox(3);
        Label eyebrow = new Label("MEELANO  /  MANAGER");
        eyebrow.getStyleClass().add("customer-eyebrow");
        Label title = new Label(SectionCatalog.titleOf(sectionId));
        title.getStyleClass().add("customer-title");
        Label caption = new Label(SectionCatalog.captionOf(sectionId) + " · نقش: "
                + SectionCatalog.desktopRoleLabel(role) + " · دسترسی فقط‌خواندنی");
        caption.getStyleClass().add("customer-caption");
        titleBlock.getChildren().addAll(eyebrow, title, caption);
        modeLabel.setText(gateway.live() ? "آتیران · فقط‌خواندنی" : "دادهٔ نمونه · نمایشی");
        modeLabel.getStyleClass().add(gateway.live() ? "customer-mode-live" : "customer-mode-demo");
        Button test = button("آزمون اتصال", false);
        test.setTooltip(new Tooltip("فقط SELECT 1 اجرا می‌شود؛ هیچ داده‌ای نوشته نمی‌شود."));
        test.setOnAction(event -> testConnection());
        Button refresh = button("تازه‌سازی", false);
        refresh.setOnAction(event -> reload(0));
        Button export = button("خروجی CSV", false);
        export.setTooltip(new Tooltip("خروجی با UTF-8 BOM برای اکسل؛ اعداد به شکل عددی و بدون فرمول ذخیره می‌شوند."));
        export.setOnAction(event -> exportCsv());
        Button close = button("بستن", false);
        close.setOnAction(event -> stage.close());
        row.getChildren().addAll(titleBlock, modeLabel, test, refresh, export, close);
        header.getChildren().addAll(row, new Separator());
        return header;
    }

    private Node buildWorkspace() {
        VBox pane = new VBox(10);
        pane.getStyleClass().add("customer-pane");
        pane.setPadding(new Insets(12));
        pane.getChildren().addAll(buildFilterBar(), buildKpiRow(), buildTable(), buildNoteRow());
        VBox.setVgrow(table, Priority.ALWAYS);
        return pane;
    }

    private Node buildFilterBar() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("section-filter-bar");
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setAlignment(Pos.CENTER_RIGHT);
        addField(grid, "گزارش", variantCombo, 0);
        addField(grid, "جست‌وجو", searchField, 1);
        addField(grid, "", contextField, 2);
        addField(grid, "از تاریخ", fromField, 3);
        addField(grid, "تا تاریخ", toField, 4);
        addField(grid, "آستانه٪", thresholdField, 5);
        addField(grid, "روز", daysField, 6);
        FlowPane actions = new FlowPane(8, 8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button apply = button("اعمال فیلتر", true);
        apply.setOnAction(event -> reload(0));
        Button reset = button("پاک‌کردن فیلترها", false);
        reset.setOnAction(event -> resetFilters());
        actions.getChildren().addAll(apply, reset);
        grid.add(actions, 7, 1);
        hintLabel.getStyleClass().add("customer-hint");
        hintLabel.setWrapText(true);
        GridPane.setColumnSpan(hintLabel, 8);
        grid.add(hintLabel, 0, 2);
        return grid;
    }

    private void addField(GridPane grid, String caption, Region control, int column) {
        VBox box = new VBox(3);
        Label label = new Label(caption);
        label.getStyleClass().add("customer-field-label");
        label.setMinHeight(caption.isEmpty() ? 0 : USE_PREF_SIZE);
        label.setVisible(!caption.isEmpty());
        label.setManaged(!caption.isEmpty());
        box.getChildren().addAll(label, control);
        if (control instanceof TextField field) field.setPrefWidth(column == 1 ? 230 : 150);
        if (control instanceof ComboBox<?> combo) combo.setPrefWidth(230);
        grid.add(box, column, 1);
    }

    private Node buildKpiRow() {
        kpiBox.setAlignment(Pos.CENTER_RIGHT);
        kpiBox.setPrefWrapLength(1200);
        kpiBox.setMinHeight(Region.USE_PREF_SIZE);
        return kpiBox;
    }

    private Node buildTable() {
        table.getStyleClass().add("customer-table");
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("برای خواندن گزارش، «اعمال فیلتر» را بزن."));
        return table;
    }

    private Node buildNoteRow() {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_RIGHT);
        noteLabel.getStyleClass().add("section-note");
        noteLabel.setWrapText(true);
        HBox.setHgrow(noteLabel, Priority.ALWAYS);
        box.getChildren().add(noteLabel);
        return box;
    }

    private Node buildStatusBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(10, 6, 2, 6));
        messageLabel.getStyleClass().add("customer-status-message");
        HBox.setHgrow(messageLabel, Priority.ALWAYS);
        messageLabel.setMaxWidth(Double.MAX_VALUE);
        countLabel.getStyleClass().add("customer-muted");
        pageLabel.getStyleClass().add("customer-muted");
        bar.getChildren().addAll(messageLabel, prevButton, pageLabel, nextButton, countLabel);
        return bar;
    }

    // ------------------------------------------------------------------ behaviour

    private void configureControls() {
        List<VariantSpec> specs = SectionCatalog.specsFor(sectionId);
        variantCombo.getItems().setAll(specs);
        variantCombo.setConverter(new StringConverter<>() {
            @Override public String toString(VariantSpec spec) { return spec == null ? "" : spec.label(); }
            @Override public VariantSpec fromString(String value) { return null; }
        });
        if (!specs.isEmpty()) variantCombo.setValue(specs.get(0));
        variantCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !Objects.equals(oldValue, newValue)) {
                applySpecUi(newValue);
                reload(0);
            }
        });

        searchDelay.setOnFinished(event -> reload(0));
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue)) searchDelay.playFromStart();
        });
        searchField.setOnAction(event -> reload(0));
        contextField.setOnAction(event -> reload(0));
        fromField.setOnAction(event -> reload(0));
        toField.setOnAction(event -> reload(0));
        thresholdField.setOnAction(event -> reload(0));
        daysField.setOnAction(event -> reload(0));
        searchField.setPromptText("جست‌وجو در این گزارش…");

        prevButton.setOnAction(event -> changePage(-1));
        nextButton.setOnAction(event -> changePage(1));

        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F5) {
                reload(0);
                event.consume();
            } else if (event.isControlDown() && !event.isAltDown() && event.getCode() == KeyCode.F) {
                searchField.requestFocus();
                searchField.selectAll();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
                event.consume();
            }
        });

        if (!specs.isEmpty()) applySpecUi(specs.get(0));
        table.setItems(rows);
    }

    /** Shows only the filters this report understands, and its catalog hint. */
    private void applySpecUi(VariantSpec spec) {
        searchField.setPromptText(spec.queryPrompt().isEmpty() ? "جست‌وجو در این گزارش…" : spec.queryPrompt());
        setVisible(contextField, !spec.contextPrompt().isEmpty());
        if (!spec.contextPrompt().isEmpty()) contextField.setPromptText(spec.contextPrompt());
        setVisible(fromField, spec.supportsDateRange());
        setVisible(toField, spec.supportsDateRange());
        setVisible(thresholdField, spec.supportsThreshold());
        if (spec.supportsThreshold() && thresholdField.getText().isBlank() && spec.defaultThreshold() != null) {
            thresholdField.setText(spec.defaultThreshold().stripTrailingZeros().toPlainString());
        }
        setVisible(daysField, spec.supportsDays());
        if (spec.supportsDays() && daysField.getText().isBlank() && spec.defaultDays() > 0) {
            daysField.setText(Integer.toString(spec.defaultDays()));
        }
        String hint = spec.hint();
        if (spec.dateKey().isEmpty() && spec.supportsDays())
            hint = (hint + " · مبنای رکود: آخرین فعالیت پیش از N روز گذشته.").trim();
        if (spec.dueWindow()) hint = (hint + " · بازهٔ روز به تاریخ سررسید پیش‌رو اعمال می‌شود.").trim();
        hintLabel.setText(hint);
    }

    private static void setVisible(Region control, boolean visible) {
        Node parent = control.getParent();
        control.setVisible(visible);
        control.setManaged(visible);
        if (parent instanceof VBox box) {
            box.setVisible(visible);
            box.setManaged(visible);
        }
    }

    private void resetFilters() {
        searchField.clear();
        contextField.clear();
        fromField.clear();
        toField.clear();
        thresholdField.clear();
        daysField.clear();
        VariantSpec spec = variantCombo.getValue();
        if (spec != null) applySpecUi(spec);
        reload(0);
    }

    private SectionFilter buildFilter(int page) {
        VariantSpec spec = variantCombo.getValue();
        if (spec == null) throw new IllegalArgumentException("ابتدا یک گزارش را انتخاب کنید.");
        BigDecimal threshold = parseThreshold(thresholdField.getText());
        int days = parseDays(daysField.getText());
        return new SectionFilter(searchField.getText(), contextField.getText(), fromField.getText(),
                toField.getText(), threshold, days, page, PAGE_SIZE);
    }

    private void reload(int page) {
        if (!WindowsRoleMenus.allows(role, sectionId)) {
            setMessage("این بخش برای نقش «" + SectionCatalog.desktopRoleLabel(role) + "» فعال نیست.", true);
            return;
        }
        VariantSpec spec = variantCombo.getValue();
        if (spec == null) return;
        SectionFilter filter;
        try {
            filter = buildFilter(page);
        } catch (IllegalArgumentException ex) {
            setMessage(safeValidationMessage(ex), true);
            return;
        }
        SectionRequest request = new SectionRequest(sectionId, spec.id(), role, filter);
        long version = ++requestVersion;
        pageIndex = filter.page();
        runIo("در حال خواندن «" + spec.label() + "»…", () -> gateway.loadSection(request), report -> {
            if (version != requestVersion || closed.get()) return;
            apply(report);
        });
    }

    private void apply(SectionReport report) {
        current = report;
        pageIndex = report.page();
        rebuildColumns(report.columns());
        rows.setAll(report.rows());
        rebuildKpis(report.kpis());
        countLabel.setText(SectionMath.count(report.totalRows()) + " ردیف · "
                + SectionMath.count(report.rows().size()) + " ردیف در این صفحه");
        pageLabel.setText("صفحهٔ " + SectionMath.count(report.page() + 1L) + " از "
                + SectionMath.count(report.pageCount()));
        prevButton.setDisable(report.page() <= 0);
        nextButton.setDisable(report.page() + 1 >= report.pageCount());
        noteLabel.setText(report.note());
        setMessage(report.demo()
                ? "پیش‌نمایش محلی با دادهٔ نمونه · " + report.variantLabel()
                : "«" + report.variantLabel() + "» از آتیران خوانده شد.", false);
    }

    private void rebuildColumns(List<SectionColumn> columns) {
        List<TableColumn<SectionRow, String>> built = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
            SectionColumn column = columns.get(index);
            int position = index;
            TableColumn<SectionRow, String> tableColumn = new TableColumn<>(column.label());
            tableColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                    data.getValue() == null ? "" : data.getValue().at(position).text()));
            // Catalog widths are relative units, scaled the same way the customer center scales them.
            tableColumn.setPrefWidth(column.width() * 112);
            tableColumn.setMinWidth(Math.max(64, column.width() * 68));
            tableColumn.setStyle(column.numeric()
                    ? "-fx-alignment: CENTER_LEFT; -fx-font-weight: 700;"
                    : "-fx-alignment: CENTER_RIGHT;");
            tableColumn.setSortable(false);
            built.add(tableColumn);
        }
        table.getColumns().clear();
        table.getColumns().addAll(built);
    }

    private void rebuildKpis(List<SectionKpi> kpis) {
        kpiBox.getChildren().clear();
        for (SectionKpi kpi : kpis) {
            VBox chip = new VBox(2);
            chip.getStyleClass().add("section-kpi-chip");
            Label label = new Label(kpi.label());
            label.getStyleClass().add("section-kpi-label");
            Label value = new Label(kpi.value());
            value.getStyleClass().add("section-kpi-value");
            chip.getChildren().addAll(label, value);
            // A chip is a layout node, not a control, so the tooltip is installed on the node.
            if (!kpi.hint().isEmpty()) Tooltip.install(chip, new Tooltip(kpi.hint()));
            kpiBox.getChildren().add(chip);
        }
    }

    private void changePage(int delta) {
        reload(Math.max(0, pageIndex + delta));
    }

    private void testConnection() {
        runIo("در حال آزمون اتصال امن…", () -> {
            gateway.testConnection();
            return Boolean.TRUE;
        }, ignored -> setMessage(gateway.live()
                ? "اتصال آتیران برقرار است؛ گزارش‌ها فقط‌خواندنی خوانده می‌شوند."
                : "درگاه نمونه فعال است؛ داده‌ها واقعی نیستند.", false));
    }

    /** CSV export with a UTF-8 BOM; cells are neutralized against formula injection. */
    private void exportCsv() {
        SectionReport report = current;
        if (report == null) {
            setMessage("ابتدا یک گزارش بخوان، سپس خروجی بگیر.", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("ذخیرهٔ خروجی CSV");
        chooser.setInitialFileName(SectionCsv.fileName(report));
        chooser.getExtensionFilters().add(new ExtensionFilter("CSV برای اکسل", "*.csv"));
        File target = chooser.showSaveDialog(stage.getScene() == null ? stage : stage.getScene().getWindow());
        if (target == null) return;
        byte[] bytes = SectionCsv.exportBytes(report);
        runIo("در حال نوشتن خروجی CSV…", () -> {
            Files.write(target.toPath(), bytes);
            return target.toPath();
        }, saved -> {
            setMessage("خروجی CSV ذخیره شد: " + saved.getFileName(), false);
            try {
                WindowsShareHandoff.reveal(saved);
            } catch (Exception ignored) {
                // Revealing the folder is a convenience only; the file is already written.
            }
        });
    }

    private <T> void runIo(String message, Callable<T> operation, Consumer<T> success) {
        setMessage(message, false);
        io.submit(() -> {
            try {
                T value = operation.call();
                Platform.runLater(() -> { if (!closed.get()) success.accept(value); });
            } catch (Throwable ex) {
                Platform.runLater(() -> { if (!closed.get()) setMessage(safeError(ex), true); });
            }
        });
    }

    private void setMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("customer-status-error", "customer-status-ok");
        messageLabel.getStyleClass().add(error ? "customer-status-error" : "customer-status-ok");
    }

    /**
     * Only messages this app wrote itself are shown: a validation message or a Persian "structure is
     * missing" explanation. Driver messages are replaced, because they can carry server or login detail.
     */
    private static String safeError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        boolean ours = cause instanceof IllegalArgumentException
                || cause instanceof UnsupportedOperationException
                || cause instanceof ir.meelano.windowsinvoice.sections.SectionModels.SectionUnavailable;
        if (ours && message != null && !message.isBlank()) return message;
        return "خواندن این گزارش از آتیران ناموفق بود؛ اتصال، دسترسی فقط‌خواندنی و ساختار پایگاه را بررسی کنید."
                + " جزئیات محرمانه نمایش داده نمی‌شود.";
    }

    private static String safeValidationMessage(IllegalArgumentException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "مقدار واردشده معتبر نیست." : message;
    }

    private static BigDecimal parseThreshold(String text) {
        String value = normalizeNumber(text);
        if (value.isEmpty()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("درصد آستانه را به شکل عددی وارد کنید (مثلاً ۱۰).");
        }
    }

    private static int parseDays(String text) {
        String value = normalizeNumber(text);
        if (value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("تعداد روز را به شکل عددی وارد کنید.");
        }
    }

    private static String normalizeNumber(String text) {
        if (text == null) return "";
        return SectionMath.latinDigits(text).replace(",", "").replace("٬", "").trim();
    }

    private static Button button(String text, boolean primary) {
        Button button = new Button(text);
        button.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        button.setFocusTraversable(true);
        return button;
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) io.shutdownNow();
    }
}
