package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.CustomerModels.BalanceFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerDirectoryRow;
import ir.meelano.windowsinvoice.CustomerModels.CustomerLedgerReport;
import ir.meelano.windowsinvoice.CustomerModels.CustomerListFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerPage;
import ir.meelano.windowsinvoice.CustomerModels.CustomerSort;
import ir.meelano.windowsinvoice.CustomerModels.CustomerStatusFilter;
import ir.meelano.windowsinvoice.CustomerModels.DetailField;
import ir.meelano.windowsinvoice.CustomerModels.DetailLine;
import ir.meelano.windowsinvoice.CustomerModels.LedgerCategory;
import ir.meelano.windowsinvoice.CustomerModels.LedgerEntry;
import ir.meelano.windowsinvoice.CustomerModels.LedgerFilter;
import ir.meelano.windowsinvoice.CustomerModels.TransactionDetails;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Read-only customer directory, account statement and document-detail surface for the Windows client. */
public final class CustomerCenterView extends BorderPane implements AutoCloseable {
    private static final int CUSTOMER_PAGE_SIZE = 80;
    private static final int LEDGER_PAGE_SIZE = 200;
    private static final int EXPORT_LIMIT = 3000;
    private static final Locale PERSIAN = Locale.forLanguageTag("fa-IR");

    private final AtiranGateway gateway;
    private final Stage stage;
    private final ExecutorService io = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "meelano-customer-center-io");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();

    private final TextField searchField = new TextField();
    private final ComboBox<BalanceFilter> balanceFilter = new ComboBox<>();
    private final ComboBox<CustomerStatusFilter> statusFilter = new ComboBox<>();
    private final ComboBox<CustomerSort> sortFilter = new ComboBox<>();
    private final TextField groupFilter = new TextField();
    private final TextField routeFilter = new TextField();
    private final TextField visitorFilter = new TextField();
    private final TextField minBalanceField = new TextField();
    private final TextField maxBalanceField = new TextField();
    private final TableView<CustomerDirectoryRow> customerTable = new TableView<>();
    private final ObservableList<CustomerDirectoryRow> customers = FXCollections.observableArrayList();
    private final Label customerCountLabel = new Label("—");
    private final Label customerPageLabel = new Label("صفحهٔ ۱");
    private final Button customerPrev = button("قبلی", false);
    private final Button customerNext = button("بعدی", false);

    private final Label messageLabel = new Label("آماده");
    private final Label modeLabel = new Label();
    private final Label selectedCustomerLabel = new Label("برای مشاهدهٔ گردش، یک مشتری را انتخاب کنید.");
    private final Label statementStatusLabel = new Label("—");
    private final Label openingLabel = new Label("—");
    private final Label debitLabel = new Label("—");
    private final Label creditLabel = new Label("—");
    private final Label closingLabel = new Label("—");
    private final TextField dateFromField = new TextField();
    private final TextField dateToField = new TextField();
    private final ComboBox<LedgerCategory> categoryFilter = new ComboBox<>();
    private final TableView<LedgerEntry> ledgerTable = new TableView<>();
    private final ObservableList<LedgerEntry> ledgerRows = FXCollections.observableArrayList();
    private final Label ledgerCountLabel = new Label("—");
    private final Label ledgerPageLabel = new Label("صفحهٔ ۱");
    private final Button ledgerPrev = button("قبلی", false);
    private final Button ledgerNext = button("بعدی", false);
    private final VBox detailsBox = new VBox(6);
    private final Label detailsTitle = new Label("جزئیات سند");
    private final VBox detailsContent = new VBox(5);
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(300));

    private CustomerPage currentCustomerPage;
    private CustomerDirectoryRow selectedCustomer;
    private CustomerLedgerReport currentReport;
    private LedgerFilter currentLedgerFilter;
    private TransactionDetails currentTransactionDetails;
    private int customerPageIndex;
    private int ledgerPageIndex;
    private long customerRequestVersion;
    private long ledgerRequestVersion;
    private long detailRequestVersion;

    private CustomerCenterView(Stage stage, AtiranGateway gateway) {
        this.stage = Objects.requireNonNull(stage);
        this.gateway = Objects.requireNonNull(gateway);
        setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        getStyleClass().add("customer-center-root");
        setPadding(new Insets(16));
        setTop(buildHeader());
        setCenter(buildWorkspace());
        setBottom(buildStatusBar());
        configureControls();
        searchDelay.setOnFinished(event -> {
            customerPageIndex = 0;
            reloadCustomers();
        });
        reloadCustomers();
    }

    public static Stage open(Window owner, AtiranGateway gateway) {
        Stage stage = new Stage();
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("MEELANO · مرکز مشتریان");
        CustomerCenterView view = new CustomerCenterView(stage, gateway);
        RectangleBounds bounds = screenBounds();
        Scene scene = new Scene(view, bounds.width(), bounds.height());
        scene.getStylesheets().add(CustomerCenterView.class.getResource("/styles.css").toExternalForm());
        scene.getRoot().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        stage.setScene(scene);
        stage.setMinWidth(Math.min(940, bounds.width()));
        stage.setMinHeight(Math.min(640, bounds.height()));
        stage.setOnHidden(event -> view.close());
        stage.show();
        stage.centerOnScreen();
        return stage;
    }

    private static RectangleBounds screenBounds() {
        var screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1640, Math.max(600, screen.getWidth() * .93));
        double height = Math.min(1000, Math.max(480, screen.getHeight() * .90));
        return new RectangleBounds(Math.min(width, screen.getWidth()), Math.min(height, screen.getHeight()));
    }

    private Node buildHeader() {
        VBox header = new VBox(12);
        header.setPadding(new Insets(7, 6, 14, 6));
        FlowPane row = new FlowPane(8, 8);
        row.setAlignment(Pos.CENTER_RIGHT);
        VBox titleBlock = new VBox(3);
        Label eyebrow = new Label("MEELANO  /  WINDOWS");
        eyebrow.getStyleClass().add("customer-eyebrow");
        Label title = new Label("مرکز مشتریان");
        title.getStyleClass().add("customer-title");
        Label caption = new Label("جست‌وجو، مانده و گردش حساب · دسترسی فقط‌خواندنی");
        caption.getStyleClass().add("customer-caption");
        titleBlock.getChildren().addAll(eyebrow, title, caption);
        modeLabel.setText(gateway.live() ? "آتیران · فقط‌خواندنی" : "دادهٔ نمونه · نمایشی");
        modeLabel.getStyleClass().add(gateway.live() ? "customer-mode-live" : "customer-mode-demo");
        Button test = button("آزمون اتصال", false);
        test.setTooltip(new Tooltip("فقط SELECT 1 اجرا می‌شود؛ هیچ داده‌ای نوشته نمی‌شود."));
        test.setOnAction(event -> testConnection());
        Button refresh = button("تازه‌سازی", false);
        refresh.setOnAction(event -> { customerPageIndex = 0; reloadCustomers(); if (selectedCustomer != null) reloadLedger(0); });
        Button close = button("بستن", false);
        close.setOnAction(event -> stage.close());
        row.getChildren().addAll(titleBlock, modeLabel, test, refresh, close);
        header.getChildren().addAll(row, new Separator());
        return header;
    }

    private Node buildWorkspace() {
        SplitPane split = new SplitPane(buildDirectoryPane(), buildStatementPane());
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(.38);
        split.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            if (newWidth.doubleValue() < 1200 && split.getOrientation() != Orientation.VERTICAL) {
                split.setOrientation(Orientation.VERTICAL);
                split.setDividerPositions(.40);
            } else if (newWidth.doubleValue() >= 1200 && split.getOrientation() != Orientation.HORIZONTAL) {
                split.setOrientation(Orientation.HORIZONTAL);
                split.setDividerPositions(.38);
            }
        });
        SplitPane.setResizableWithParent(split.getItems().get(0), true);
        VBox.setVgrow(split, Priority.ALWAYS);
        return split;
    }

    private Node buildDirectoryPane() {
        VBox pane = new VBox(9);
        pane.getStyleClass().add("customer-pane");
        pane.setPadding(new Insets(12));
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_RIGHT);
        Label title = new Label("فهرست مشتریان");
        title.getStyleClass().add("customer-section-title");
        HBox.setHgrow(title, Priority.ALWAYS);
        customerCountLabel.getStyleClass().add("customer-muted");
        titleRow.getChildren().addAll(title, customerCountLabel);

        HBox searchRow = new HBox(8);
        searchRow.setAlignment(Pos.CENTER_RIGHT);
        searchField.setPromptText("نام، کد یا تلفن مشتری…");
        searchField.setAccessibleText("جست‌وجوی مشتری");
        searchField.setTooltip(new Tooltip("جست‌وجو میان نام، کد و شمارهٔ تماس"));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        Button search = button("جست‌وجو", true);
        search.setOnAction(event -> { searchDelay.stop(); customerPageIndex = 0; reloadCustomers(); });
        searchRow.getChildren().addAll(searchField, search);

        HBox quickFilters = new HBox(7);
        quickFilters.setAlignment(Pos.CENTER_RIGHT);
        balanceFilter.setPromptText("مانده");
        statusFilter.setPromptText("وضعیت");
        sortFilter.setPromptText("مرتب‌سازی");
        balanceFilter.setPrefWidth(140);
        statusFilter.setPrefWidth(130);
        sortFilter.setPrefWidth(150);
        quickFilters.getChildren().addAll(balanceFilter, statusFilter, sortFilter);

        GridPane advancedGrid = new GridPane();
        advancedGrid.setHgap(8);
        advancedGrid.setVgap(7);
        addFilterField(advancedGrid, "گروه مشتری", groupFilter, 0, 0);
        addFilterField(advancedGrid, "مسیر", routeFilter, 1, 0);
        addFilterField(advancedGrid, "ویزیتور", visitorFilter, 2, 0);
        addFilterField(advancedGrid, "حداقل مانده", minBalanceField, 0, 1);
        addFilterField(advancedGrid, "حداکثر مانده", maxBalanceField, 1, 1);
        Button applySmart = button("اعمال فیلترها", true);
        applySmart.setOnAction(event -> applyDirectoryFilters());
        advancedGrid.add(applySmart, 2, 1);
        TitledPane smartFilters = new TitledPane("فیلترهای هوشمند و حسابداری", advancedGrid);
        smartFilters.setExpanded(false);
        smartFilters.getStyleClass().add("customer-filter-pane");
        smartFilters.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        configureCustomerTable();
        HBox pageRow = new HBox(8, customerPrev, customerPageLabel, customerNext);
        pageRow.setAlignment(Pos.CENTER);
        customerPrev.setDisable(true);
        customerNext.setDisable(true);
        customerPrev.setOnAction(event -> changeCustomerPage(-1));
        customerNext.setOnAction(event -> changeCustomerPage(1));

        Label hint = new Label("روی مشتری کلیک کن یا ردیف را انتخاب و Enter بزن تا گردش حساب باز شود.");
        hint.getStyleClass().add("customer-hint");
        hint.setWrapText(true);
        pane.getChildren().addAll(titleRow, searchRow, quickFilters, smartFilters, customerTable, pageRow, hint);
        VBox.setVgrow(customerTable, Priority.ALWAYS);
        return pane;
    }

    private void addFilterField(GridPane grid, String caption, TextField field, int column, int row) {
        VBox box = new VBox(4);
        Label label = new Label(caption);
        label.getStyleClass().add("customer-field-label");
        field.setPromptText(caption);
        box.getChildren().addAll(label, field);
        grid.add(box, column, row);
        while (grid.getColumnConstraints().size() <= column) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(33.33);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }
    }

    private Node buildStatementPane() {
        VBox pane = new VBox(9);
        pane.getStyleClass().add("customer-pane");
        pane.setPadding(new Insets(12));
        HBox heading = new HBox(10);
        heading.setAlignment(Pos.CENTER_RIGHT);
        VBox labels = new VBox(3, new Label("گردش حساب مشتری"), selectedCustomerLabel);
        labels.getChildren().get(0).getStyleClass().add("customer-section-title");
        selectedCustomerLabel.getStyleClass().add("customer-muted");
        HBox.setHgrow(labels, Priority.ALWAYS);
        heading.getChildren().addAll(labels, statementStatusLabel);
        statementStatusLabel.getStyleClass().add("customer-mode-live");

        FlowPane summary = new FlowPane(8, 7);
        summary.setAlignment(Pos.CENTER_RIGHT);
        summary.getChildren().addAll(summaryChip("ماندهٔ ابتدا", openingLabel),
                summaryChip("بدهکار", debitLabel), summaryChip("بستانکار", creditLabel),
                summaryChip("ماندهٔ پایان", closingLabel));

        FlowPane dateFilters = new FlowPane(7, 7);
        dateFilters.setAlignment(Pos.CENTER_RIGHT);
        dateFromField.setPromptText("از تاریخ شمسی · ۱۴۰۵/۰۸/۰۱");
        dateToField.setPromptText("تا تاریخ شمسی · ۱۴۰۵/۰۸/۳۰");
        dateFromField.setPrefWidth(165);
        dateToField.setPrefWidth(165);
        dateFromField.setAccessibleText("از تاریخ شمسی");
        dateToField.setAccessibleText("تا تاریخ شمسی");
        categoryFilter.setPrefWidth(150);
        Button applyLedger = button("اعمال", true);
        applyLedger.setOnAction(event -> applyLedgerFilters());
        Button resetLedger = button("همهٔ گردش‌ها", false);
        resetLedger.setOnAction(event -> {
            dateFromField.clear(); dateToField.clear(); categoryFilter.setValue(LedgerCategory.ALL); applyLedgerFilters();
        });
        dateFilters.getChildren().addAll(dateFromField, dateToField, categoryFilter, applyLedger, resetLedger);

        FlowPane exportButtons = new FlowPane(7, 7);
        exportButtons.setAlignment(Pos.CENTER_RIGHT);
        Button pdf = button("PDF", false);
        pdf.setOnAction(event -> chooseExport(ExportType.PDF, false));
        Button excel = button("Excel", false);
        excel.setOnAction(event -> chooseExport(ExportType.XLSX, false));
        Button image = button("تصویر", false);
        image.setTooltip(new Tooltip("تصویر PNG از صفحهٔ جاری گزارش؛ تا ۱۸۰ ردیف"));
        image.setOnAction(event -> chooseExport(ExportType.PNG, false));
        Button windowsShare = button("تحویل Windows / Bluetooth", true);
        windowsShare.setTooltip(new Tooltip("فایل خروجی را در Explorer انتخاب می‌کند؛ سپس از Share ویندوز، Bluetooth یا Nearby Sharing را انتخاب کن."));
        windowsShare.setOnAction(event -> chooseShareFormat());
        exportButtons.getChildren().addAll(pdf, excel, image, windowsShare);
        ledgerCountLabel.getStyleClass().add("customer-muted");
        exportButtons.getChildren().add(ledgerCountLabel);

        configureLedgerTable();
        HBox ledgerPage = new HBox(8, ledgerPrev, ledgerPageLabel, ledgerNext);
        ledgerPage.setAlignment(Pos.CENTER);
        ledgerPrev.setDisable(true);
        ledgerNext.setDisable(true);
        ledgerPrev.setOnAction(event -> changeLedgerPage(-1));
        ledgerNext.setOnAction(event -> changeLedgerPage(1));

        detailsBox.getStyleClass().add("customer-details-card");
        detailsTitle.getStyleClass().add("customer-section-title");
        detailsContent.setPadding(new Insets(2, 3, 4, 3));
        ScrollPane detailScroll = new ScrollPane(detailsContent);
        detailScroll.setFitToWidth(true);
        detailScroll.setPrefViewportHeight(145);
        detailScroll.setMaxHeight(175);
        detailScroll.getStyleClass().add("customer-detail-scroll");
        detailsBox.getChildren().addAll(detailsTitle, detailScroll);
        detailsBox.setMinHeight(95);

        pane.getChildren().addAll(heading, summary, dateFilters, exportButtons, ledgerTable, ledgerPage, detailsBox);
        VBox.setVgrow(ledgerTable, Priority.ALWAYS);
        return pane;
    }

    private Node summaryChip(String caption, Label value) {
        VBox chip = new VBox(3);
        chip.getStyleClass().add("customer-summary-chip");
        Label title = new Label(caption);
        title.getStyleClass().add("customer-field-label");
        value.getStyleClass().add("customer-summary-value");
        chip.getChildren().addAll(title, value);
        return chip;
    }

    private Node buildStatusBar() {
        HBox bar = new HBox(10, new Separator(), messageLabel);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(8, 6, 0, 6));
        messageLabel.getStyleClass().add("customer-status-message");
        return bar;
    }

    private void configureControls() {
        balanceFilter.getItems().setAll(BalanceFilter.values());
        statusFilter.getItems().setAll(CustomerStatusFilter.values());
        sortFilter.getItems().setAll(CustomerSort.values());
        categoryFilter.getItems().setAll(LedgerCategory.values());
        balanceFilter.setValue(BalanceFilter.ALL);
        statusFilter.setValue(CustomerStatusFilter.ALL);
        sortFilter.setValue(CustomerSort.NAME);
        categoryFilter.setValue(LedgerCategory.ALL);
        balanceFilter.setOnAction(event -> { customerPageIndex = 0; reloadCustomers(); });
        statusFilter.setOnAction(event -> { customerPageIndex = 0; reloadCustomers(); });
        sortFilter.setOnAction(event -> { customerPageIndex = 0; reloadCustomers(); });
        searchField.textProperty().addListener((observable, oldValue, newValue) -> searchDelay.playFromStart());
        searchField.setOnAction(event -> { searchDelay.stop(); customerPageIndex = 0; reloadCustomers(); });
        for (TextField field : List.of(groupFilter, routeFilter, visitorFilter, minBalanceField, maxBalanceField))
            field.setOnAction(event -> applyDirectoryFilters());
        customerTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                CustomerDirectoryRow row = customerTable.getSelectionModel().getSelectedItem();
                if (row != null) openLedger(row);
                event.consume();
            }
        });
        ledgerTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) { showTransactionDialog(); event.consume(); }
        });
        ledgerTable.getSelectionModel().selectedItemProperty().addListener((observable, oldEntry, newEntry) -> loadDetails(newEntry));
    }

    private void configureCustomerTable() {
        customerTable.setItems(customers);
        customerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        customerTable.getStyleClass().add("customer-table");
        customerTable.setPlaceholder(new Label("مشتری‌ای برای این فیلتر پیدا نشد."));
        customerTable.setRowFactory(table -> {
            TableRow<CustomerDirectoryRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 1
                        && event.getButton() != javafx.scene.input.MouseButton.SECONDARY) openLedger(row.getItem());
            });
            return row;
        });
        customerTable.getColumns().addAll(
                customerColumn("مشتری", 1.65, row -> row.customer().name()),
                customerColumn("کد", .72, row -> digits(Long.toString(row.customer().code()))),
                customerColumn("تلفن", 1.0, row -> row.customer().phone()),
                customerColumn("مانده", 1.05, row -> formatMoney(row.customer().balance())),
                customerColumn("وضعیت", .72, CustomerDirectoryRow::statusLabel),
                customerColumn("فروش", 1.0, row -> formatMoney(row.salesTotal())),
                customerColumn("آخرین خرید", .86, row -> row.lastSaleDate().isBlank() ? "—" : digits(row.lastSaleDate()))
        );
    }

    private TableColumn<CustomerDirectoryRow, String> customerColumn(String title, double width,
                                                                      java.util.function.Function<CustomerDirectoryRow, String> value) {
        TableColumn<CustomerDirectoryRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width * 112);
        column.setMinWidth(Math.max(64, width * 68));
        return column;
    }

    private void configureLedgerTable() {
        ledgerTable.setItems(ledgerRows);
        ledgerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ledgerTable.getStyleClass().add("customer-table");
        ledgerTable.setPlaceholder(new Label("مشتری را از فهرست انتخاب کنید."));
        ledgerTable.setRowFactory(table -> {
            TableRow<LedgerEntry> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() >= 2) showTransactionDialog();
            });
            return row;
        });
        ledgerTable.getColumns().addAll(
                ledgerColumn("تاریخ", .82, entry -> digits(entry.date())),
                ledgerColumn("سند", .85, entry -> digits(entry.documentNo())),
                ledgerColumn("نوع عملیات", 1.10, entry -> entry.operation()),
                ledgerColumn("شرح", 1.75, entry -> entry.description()),
                ledgerColumn("بدهکار", .95, entry -> formatMoney(entry.debit())),
                ledgerColumn("بستانکار", .95, entry -> formatMoney(entry.credit())),
                ledgerColumn("مانده", .95, entry -> formatMoney(entry.balance()))
        );
    }

    private TableColumn<LedgerEntry, String> ledgerColumn(String title, double width,
                                                           java.util.function.Function<LedgerEntry, String> value) {
        TableColumn<LedgerEntry, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width * 105);
        column.setMinWidth(Math.max(66, width * 56));
        return column;
    }

    private void applyDirectoryFilters() {
        try {
            buildCustomerFilter(0);
            customerPageIndex = 0;
            reloadCustomers();
        } catch (IllegalArgumentException ex) {
            setMessage(safeValidationMessage(ex), true);
        }
    }

    private CustomerListFilter buildCustomerFilter(int page) {
        return new CustomerListFilter(searchField.getText(), balanceFilter.getValue(), statusFilter.getValue(),
                groupFilter.getText(), routeFilter.getText(), visitorFilter.getText(),
                parseOptionalAmount(minBalanceField.getText()), parseOptionalAmount(maxBalanceField.getText()),
                sortFilter.getValue(), page, CUSTOMER_PAGE_SIZE);
    }

    private void reloadCustomers() {
        final CustomerListFilter filter;
        try { filter = buildCustomerFilter(customerPageIndex); }
        catch (IllegalArgumentException ex) { setMessage(safeValidationMessage(ex), true); return; }
        long version = ++customerRequestVersion;
        runIo("در حال خواندن فهرست مشتریان…", () -> gateway.listCustomers(filter), page -> {
            if (version != customerRequestVersion) return;
            currentCustomerPage = page;
            customers.setAll(page.rows());
            customerCountLabel.setText(digits(Long.toString(page.total())) + " مشتری");
            customerPageLabel.setText("صفحهٔ " + digits(Integer.toString(page.page() + 1)) + " از " + digits(Integer.toString(page.pageCount())));
            customerPrev.setDisable(page.page() <= 0);
            customerNext.setDisable(page.page() + 1 >= page.pageCount());
            setMessage(page.rows().isEmpty() ? "موردی یافت نشد؛ فیلترهای حسابداری را بررسی کن." : "فهرست مشتریان آماده است.", false);
        });
    }

    private void changeCustomerPage(int delta) {
        if (currentCustomerPage == null) return;
        int target = currentCustomerPage.page() + delta;
        if (target < 0 || target >= currentCustomerPage.pageCount()) return;
        customerPageIndex = target;
        reloadCustomers();
    }

    private void openLedger(CustomerDirectoryRow row) {
        if (row == null) return;
        selectedCustomer = row;
        currentReport = null;
        currentLedgerFilter = null;
        ledgerPageIndex = 0;
        detailRequestVersion++;
        selectedCustomerLabel.setText(row.customer().name() + "  ·  کد " + digits(Long.toString(row.customer().code()))
                + "  ·  " + row.balanceLabel() + " " + formatMoney(row.customer().balance()));
        statementStatusLabel.setText(gateway.live() ? "گردش آتیران · فقط‌خواندنی" : "گردش نمونه · غیرواقعی");
        openingLabel.setText("—"); debitLabel.setText("—"); creditLabel.setText("—"); closingLabel.setText("—");
        ledgerPrev.setDisable(true); ledgerNext.setDisable(true);
        ledgerRows.clear();
        clearDetails("برای نمایش جزئیات، یک ردیف گردش را انتخاب کنید.");
        reloadLedger(0);
    }

    private void applyLedgerFilters() {
        if (selectedCustomer == null) { setMessage("ابتدا یک مشتری را انتخاب کنید.", true); return; }
        try {
            new LedgerFilter(dateFromField.getText(), dateToField.getText(), categoryFilter.getValue(), 0, LEDGER_PAGE_SIZE);
            ledgerPageIndex = 0;
            reloadLedger(0);
        } catch (IllegalArgumentException ex) {
            setMessage(safeValidationMessage(ex), true);
        }
    }

    private void reloadLedger(int page) {
        if (selectedCustomer == null) return;
        final LedgerFilter filter;
        try { filter = new LedgerFilter(dateFromField.getText(), dateToField.getText(), categoryFilter.getValue(), page, LEDGER_PAGE_SIZE); }
        catch (IllegalArgumentException ex) { setMessage(safeValidationMessage(ex), true); return; }
        long version = ++ledgerRequestVersion;
        CustomerDirectoryRow customer = selectedCustomer;
        runIo("در حال خواندن گردش حساب…", () -> gateway.customerLedger(customer, filter), report -> {
            if (version != ledgerRequestVersion || customer != selectedCustomer) return;
            currentReport = report;
            currentLedgerFilter = filter;
            ledgerPageIndex = report.entries().isEmpty() && report.totalEntries() > 0 ? Math.max(0, page - 1) : page;
            ledgerRows.setAll(report.entries());
            openingLabel.setText(formatMoney(report.openingBalance()));
            debitLabel.setText(formatMoney(report.debitTotal()));
            creditLabel.setText(formatMoney(report.creditTotal()));
            closingLabel.setText(formatMoney(report.closingBalance()));
            int pages = Math.max(1, (int) ((report.totalEntries() + filter.pageSize() - 1) / filter.pageSize()));
            ledgerCountLabel.setText(digits(Long.toString(report.totalEntries())) + " گردش" + (report.truncated() ? " · سقف خواندن" : ""));
            ledgerPageLabel.setText("صفحهٔ " + digits(Integer.toString(ledgerPageIndex + 1)) + " از " + digits(Integer.toString(pages)));
            ledgerPrev.setDisable(ledgerPageIndex <= 0);
            ledgerNext.setDisable(ledgerPageIndex + 1 >= pages);
            setMessage(report.entries().isEmpty() ? "در این بازه گردش ثبت‌شده‌ای وجود ندارد." : "گردش حساب به‌روزرسانی شد.", false);
            if (!report.entries().isEmpty()) ledgerTable.getSelectionModel().selectFirst();
            else clearDetails("در این بازه سندی برای نمایش وجود ندارد.");
        });
    }

    private void changeLedgerPage(int delta) {
        if (currentReport == null || currentLedgerFilter == null) return;
        int pages = Math.max(1, (int) ((currentReport.totalEntries() + currentLedgerFilter.pageSize() - 1) / currentLedgerFilter.pageSize()));
        int target = ledgerPageIndex + delta;
        if (target < 0 || target >= pages) return;
        reloadLedger(target);
    }

    private void loadDetails(LedgerEntry entry) {
        if (entry == null || selectedCustomer == null) { clearDetails("برای نمایش جزئیات، یک ردیف گردش را انتخاب کنید."); return; }
        long version = ++detailRequestVersion;
        CustomerDirectoryRow customer = selectedCustomer;
        currentTransactionDetails = null;
        detailsTitle.setText("در حال خواندن جزئیات سند…");
        detailsContent.getChildren().setAll(new Label("ارتباط فقط‌خواندنی با سند آتیران"));
        runIo("در حال خواندن جزئیات سند…", () -> gateway.customerTransactionDetails(customer, entry), details -> {
            if (version != detailRequestVersion || entry != ledgerTable.getSelectionModel().getSelectedItem()) return;
            currentTransactionDetails = details;
            detailsTitle.setText(details.title().isBlank() ? "جزئیات سند" : details.title());
            detailsContent.getChildren().setAll(buildDetailsContent(details));
            setMessage("جزئیات ردیف انتخاب‌شده آماده است.", false);
        });
    }

    private Node buildDetailsContent(TransactionDetails details) {
        VBox container = new VBox(7);
        FlowPane fields = new FlowPane(7, 5);
        fields.setAlignment(Pos.CENTER_RIGHT);
        for (DetailField field : details.fields()) {
            VBox item = new VBox(2);
            item.getStyleClass().add("customer-detail-field");
            Label label = new Label(field.label());
            label.getStyleClass().add("customer-field-label");
            Label value = new Label(field.value());
            value.setWrapText(true);
            value.setMaxWidth(260);
            value.getStyleClass().add("customer-detail-value");
            item.getChildren().addAll(label, value);
            fields.getChildren().add(item);
        }
        container.getChildren().add(fields);
        if (!details.lines().isEmpty()) {
            TableView<DetailLine> lines = new TableView<>(FXCollections.observableArrayList(details.lines()));
            lines.setPrefHeight(Math.min(150, 40 + details.lines().size() * 28));
            lines.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            lines.getColumns().addAll(
                    detailColumn("کالا", DetailLine::item),
                    detailColumn("مقدار", DetailLine::quantity),
                    detailColumn("قیمت واحد", DetailLine::unitPrice),
                    detailColumn("جمع", line -> formatMoney(line.total())));
            container.getChildren().add(lines);
        }
        return container;
    }

    private TableColumn<DetailLine, String> detailColumn(String title, java.util.function.Function<DetailLine, String> value) {
        TableColumn<DetailLine, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private void clearDetails(String text) {
        currentTransactionDetails = null;
        detailsTitle.setText("جزئیات سند");
        detailsContent.getChildren().setAll(new Label(text));
    }

    private void showTransactionDialog() {
        TransactionDetails details = currentTransactionDetails;
        if (details == null) {
            LedgerEntry selected = ledgerTable.getSelectionModel().getSelectedItem();
            if (selected != null) loadDetails(selected);
            return;
        }
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("جزئیات گردش مشتری");
        dialog.setHeaderText(details.title());
        ButtonType close = new ButtonType("بستن", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(close);
        ScrollPane content = new ScrollPane(buildDetailsContent(details));
        content.setFitToWidth(true);
        content.setPrefViewportWidth(760);
        content.setPrefViewportHeight(470);
        content.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void testConnection() {
        runIo("در حال آزمون اتصال امن…", () -> { gateway.testConnection(); return Boolean.TRUE; }, ignored -> {
            modeLabel.setText(gateway.connectionStatus());
            setMessage("اتصال برقرار شد؛ آزمون فقط خواندنی بود.", false);
            reloadCustomers();
        });
    }

    private void chooseShareFormat() {
        if (selectedCustomer == null || currentReport == null) { setMessage("ابتدا مشتری و گردش حساب را بارگذاری کنید.", true); return; }
        javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>("PDF", List.of("PDF", "Excel", "تصویر PNG"));
        dialog.initOwner(stage);
        dialog.setTitle("تحویل بومی Windows");
        dialog.setHeaderText("یک قالب برای خروجی و Explorer انتخاب کنید");
        dialog.setContentText("Bluetooth را از Share / Nearby در Windows Explorer انتخاب کنید.");
        dialog.showAndWait().ifPresent(choice -> {
            ExportType type = choice.startsWith("Excel") ? ExportType.XLSX : choice.startsWith("تصویر") ? ExportType.PNG : ExportType.PDF;
            chooseExport(type, true);
        });
    }

    private void chooseExport(ExportType type, boolean handoff) {
        if (selectedCustomer == null || currentReport == null) { setMessage("ابتدا مشتری و گردش حساب را بارگذاری کنید.", true); return; }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("ذخیرهٔ گردش حساب مشتری");
        String extension = type.extension;
        chooser.getExtensionFilters().add(new ExtensionFilter(type.label + " (*" + extension + ")", "*" + extension));
        chooser.setInitialFileName("گردش-" + selectedCustomer.customer().code() + "-" + PersianDate.today().replace('/', '-') + extension);
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        java.nio.file.Path target = file.toPath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
            target = target.resolveSibling(target.getFileName() + extension);
        final java.nio.file.Path output = target;
        CustomerDirectoryRow customer = selectedCustomer;
        LedgerFilter applied = currentLedgerFilter == null
                ? new LedgerFilter(dateFromField.getText(), dateToField.getText(), categoryFilter.getValue(), 0, LEDGER_PAGE_SIZE)
                : currentLedgerFilter;
        LedgerFilter base = new LedgerFilter(applied.fromJalali(), applied.toJalali(), applied.category(), 0, 500);
        int limit = type == ExportType.PNG ? 180 : EXPORT_LIMIT;
        CustomerLedgerReport reportSnapshot = currentReport;
        List<LedgerEntry> visiblePage = List.copyOf(currentReport.entries());
        boolean pageOnly = reportSnapshot.truncated() || reportSnapshot.totalEntries() > visiblePage.size();
        runIo("در حال آماده‌سازی خروجی…", () -> type == ExportType.PNG
                ? new ExportData(reportSnapshot, visiblePage, pageOnly)
                : collectExport(customer, base, limit), data -> {
            io.submit(() -> {
                try {
                    switch (type) {
                        case PDF -> CustomerStatementExporter.writePdf(output, data.report(), data.entries(), data.capped());
                        case XLSX -> CustomerStatementExporter.writeXlsx(output, data.report(), data.entries(), data.capped());
                        case PNG -> CustomerStatementExporter.writeImage(output, data.report(), data.entries(), data.capped());
                    }
                    Platform.runLater(() -> finishExport(output, type, handoff, data.capped()));
                } catch (Exception ex) {
                    Platform.runLater(() -> setMessage("ساخت خروجی انجام نشد؛ فضای دیسک یا مجوز پوشه را بررسی کنید.", true));
                }
            });
        });
    }

    private ExportData collectExport(CustomerDirectoryRow customer, LedgerFilter base, int limit) throws Exception {
        LedgerFilter firstFilter = new LedgerFilter(base.fromJalali(), base.toJalali(), base.category(), 0, 500);
        CustomerLedgerReport first = gateway.customerLedger(customer, firstFilter);
        List<LedgerEntry> all = new ArrayList<>(Math.min(limit, (int) Math.min(first.totalEntries(), limit)));
        all.addAll(first.entries());
        long wanted = Math.min(first.totalEntries(), limit);
        int page = 1;
        while (all.size() < wanted) {
            LedgerFilter nextFilter = new LedgerFilter(base.fromJalali(), base.toJalali(), base.category(), page++, 500);
            CustomerLedgerReport next = gateway.customerLedger(customer, nextFilter);
            if (next.entries().isEmpty()) break;
            all.addAll(next.entries());
        }
        if (all.size() > limit) all = new ArrayList<>(all.subList(0, limit));
        boolean capped = first.truncated() || first.totalEntries() > all.size();
        CustomerLedgerReport exportReport = new CustomerLedgerReport(first.customer(), all, first.totalEntries(),
                first.openingBalance(), first.debitTotal(), first.creditTotal(), first.closingBalance(),
                first.fromJalali(), first.toJalali(), first.category(), capped);
        return new ExportData(exportReport, List.copyOf(all), capped);
    }

    private void finishExport(java.nio.file.Path file, ExportType type, boolean handoff, boolean capped) {
        if (handoff) {
            try {
                WindowsShareHandoff.reveal(file);
                setMessage("خروجی در Explorer انتخاب شد. برای Bluetooth: راست‌کلیک روی فایل → Share → Bluetooth / Nearby.", false);
                Alert tip = new Alert(Alert.AlertType.INFORMATION);
                tip.initOwner(stage);
                tip.setTitle("تحویل با Windows");
                tip.setHeaderText("فایل خروجی در Explorer انتخاب شده است");
                tip.setContentText("از گزینهٔ Share ویندوز برای Nearby Sharing یا Bluetooth استفاده کنید.\n"
                        + "هیچ پیام‌رسان یا سرویس ابری به برنامه متصل نمی‌شود.\n\n" + file);
                tip.show();
            } catch (Exception ex) {
                setMessage("فایل ساخته شد؛ باز کردن Explorer ممکن نشد: " + file, true);
            }
        } else {
            setMessage(type.label + " ساخته شد: " + file + (capped ? " · خروجی محدود است" : ""), false);
        }
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

    private static String safeError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof IllegalArgumentException && cause.getMessage() != null) return cause.getMessage();
        return "خواندن اطلاعات از آتیران ناموفق بود؛ اتصال، دسترسی فقط‌خواندنی و ساختار پایگاه را بررسی کنید. جزئیات محرمانه نمایش داده نمی‌شود.";
    }

    private static String safeValidationMessage(IllegalArgumentException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "مقدار واردشده معتبر نیست." : message;
    }

    private static BigDecimal parseOptionalAmount(String text) {
        String value = normalizeNumber(text);
        if (value.isBlank()) return null;
        try { return new BigDecimal(value); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("مانده را به شکل عددی معتبر وارد کنید."); }
    }

    private static String normalizeNumber(String text) {
        if (text == null) return "";
        return text.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3')
                .replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7')
                .replace('۸','8').replace('۹','9').replace('٠','0').replace('١','1')
                .replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5')
                .replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9')
                .replace(",", "").replace("٬", "").replace("٫", ".").replace("−", "-").trim();
    }

    private static String formatMoney(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(PERSIAN);
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(0);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private static String digits(String value) {
        if (value == null) return "";
        return value.replace('0', '۰').replace('1', '۱').replace('2', '۲').replace('3', '۳')
                .replace('4', '۴').replace('5', '۵').replace('6', '۶').replace('7', '۷')
                .replace('8', '۸').replace('9', '۹');
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

    private enum ExportType {
        PDF("PDF", ".pdf"), XLSX("Excel", ".xlsx"), PNG("تصویر PNG", ".png");
        private final String label;
        private final String extension;
        ExportType(String label, String extension) { this.label = label; this.extension = extension; }
    }

    private record ExportData(CustomerLedgerReport report, List<LedgerEntry> entries, boolean capped) { }
    private record RectangleBounds(double width, double height) { }
}
