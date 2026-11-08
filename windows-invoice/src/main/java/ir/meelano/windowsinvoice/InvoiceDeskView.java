package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceDraft;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceLine;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceResult;
import ir.meelano.windowsinvoice.InvoiceModels.Product;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Primary RTL desktop invoice-entry screen. The default gateway is demo-only and cannot write. */
public final class InvoiceDeskView extends BorderPane implements AutoCloseable {
    private static final String GREEN = "#174d3c";
    private final AtiranGateway gateway;
    private final ExecutorService io = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "meelano-atiran-io");
        t.setDaemon(true);
        return t;
    });
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final TextField customerSearch = new TextField();
    private final TextField productSearch = new TextField();
    private final ListView<Customer> customerList = new ListView<>();
    private final ListView<Product> productList = new ListView<>();
    private final TableView<Row> invoiceTable = new TableView<>(rows);
    private ScrollPane bodyScroll;
    private final TextField dateField = new TextField(PersianDate.today());
    private final TextField noteField = new TextField();
    private final TextField invoiceDiscountField = new TextField("0");
    private final TextField freightField = new TextField("0");
    private final TextField taxField = new TextField("0");
    private final TextField settlementField = new TextField("0");
    private final TextField warehouseField = new TextField("0");
    private final Label connectionLabel = new Label();
    private final Label safetyLabel = new Label();
    private final StackPane connectionDot = new StackPane();
    private final Label selectionLabel = new Label("هنوز مشتری انتخاب نشده است");
    private final Label selectedBalanceLabel = new Label("مانده: —");
    private final Label messageLabel = new Label("آمادهٔ ثبت فاکتور");
    private final Label subtotalLabel = amountLabel("۰");
    private final Label discountsLabel = amountLabel("۰");
    private final Label taxTotalLabel = amountLabel("۰");
    private final Label grandTotalLabel = amountLabel("۰");
    private final Button submitButton = new Button("ثبت قطعی در آتیران");
    private final BooleanProperty submitting = new SimpleBooleanProperty(false);
    private Customer selectedCustomer;
    /** Stable across retries; a new token is minted only when the operator starts a new invoice. */
    private String draftUniqueId = newInvoiceId();
    private String startupWarning = "";
    private String currentValidationMessage = "";

    public InvoiceDeskView(AtiranGateway gateway, String startupWarning) {
        this.gateway = gateway;
        this.startupWarning = startupWarning == null ? "" : startupWarning;
        setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        getStyleClass().add("app-root");
        setRight(buildSidebar());
        setCenter(buildMainPanel());
        updateConnectionBadge();
        refreshTotals();
        if (!this.startupWarning.isBlank()) setMessage(this.startupWarning, true);
        else setMessage(gateway.live() ? "برای شروع، مشتری یا کالا را جست‌وجو کن." : "پیش‌نمایش محلی آماده است؛ برای دیدن دادهٔ نمونه جست‌وجو کن.", false);
    }

    private Node buildSidebar() {
        VBox sidebar = new VBox(9);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(224);
        sidebar.setMinWidth(224);
        sidebar.setPadding(new Insets(24, 16, 18, 16));

        HBox brand = new HBox(11);
        brand.setAlignment(Pos.CENTER_RIGHT);
        ImageView mark = new ImageView(loadIcon());
        mark.setFitWidth(48);
        mark.setFitHeight(48);
        VBox brandText = new VBox(2);
        Label wordmark = new Label("MEELANO");
        wordmark.getStyleClass().add("brand-wordmark");
        Label descriptor = new Label("میز فاکتور فروش");
        descriptor.getStyleClass().add("brand-caption");
        brandText.getChildren().addAll(wordmark, descriptor);
        brand.getChildren().addAll(mark, brandText);
        sidebar.getChildren().add(brand);

        Label section = new Label("فروشگاه");
        section.getStyleClass().add("sidebar-section-label");
        VBox.setMargin(section, new Insets(27, 9, 7, 0));
        sidebar.getChildren().add(section);
        sidebar.getChildren().addAll(
                navButton("invoice", "ثبت فاکتور", true, dateField::requestFocus),
                navButton("users", "انتخاب مشتری", false, () -> focusInBody(customerSearch, 0.0)),
                navButton("box", "جست‌وجوی کالا", false, () -> focusInBody(productSearch, 0.48)),
                navButton("cart", "ردیف‌های فاکتور", false, () -> focusInBody(invoiceTable, 1.0))
        );
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);
        sidebar.getChildren().add(new Separator());
        sidebar.getChildren().add(navButton("settings", "اتصال و تنظیمات", false, this::showConnectionHelp));
        Label version = new Label("نسخهٔ دسکتاپ · ۱.۰");
        version.getStyleClass().add("sidebar-footnote");
        VBox.setMargin(version, new Insets(10, 9, 0, 0));
        sidebar.getChildren().add(version);
        return sidebar;
    }

    private Button navButton(String iconName, String text, boolean active, Runnable action) {
        Button b = new Button(text);
        b.setGraphic(IconFactory.icon(iconName, 20, active ? Color.web("#f4d990") : Color.web("#c3d4c9")));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_RIGHT);
        b.getStyleClass().add("nav-button");
        if (active) b.getStyleClass().add("nav-button-active");
        b.disableProperty().bind(submitting);
        b.setOnAction(e -> action.run());
        return b;
    }

    private void focusInBody(Node node, double position) {
        if (bodyScroll != null) bodyScroll.setVvalue(position);
        Platform.runLater(node::requestFocus);
    }

    private Node buildMainPanel() {
        BorderPane main = new BorderPane();
        main.getStyleClass().add("main-panel");
        main.setTop(buildPageHeader());
        main.setCenter(buildInvoiceBody());
        main.setBottom(buildStatusBar());
        return main;
    }

    private Node buildPageHeader() {
        VBox header = new VBox(13);
        header.getStyleClass().add("page-header");
        header.setPadding(new Insets(22, 26, 14, 26));

        HBox top = new HBox(16);
        top.setAlignment(Pos.CENTER_RIGHT);
        VBox titleBlock = new VBox(4);
        Label overline = new Label("MEELANO  /  فروش");
        overline.getStyleClass().add("eyebrow");
        Label title = new Label("ثبت فاکتور فروش");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("ثبت سریع، کنترل دقیق و گردش امن در آتیران");
        subtitle.getStyleClass().add("page-subtitle");
        titleBlock.getChildren().addAll(overline, title, subtitle);
        HBox.setHgrow(titleBlock, Priority.ALWAYS);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button test = secondaryButton("آزمون اتصال", "refresh");
        test.disableProperty().bind(submitting);
        test.setOnAction(e -> runAsync("در حال آزمون اتصال…", () -> { gateway.testConnection(); return Boolean.TRUE; }, ok -> {
            setMessage("اتصال به SQL Server برقرار است.", false);
            updateConnectionBadge();
        }));
        Button fresh = secondaryButton("فاکتور جدید", "plus");
        fresh.disableProperty().bind(submitting);
        fresh.setOnAction(e -> requestNewDraft());
        actions.getChildren().addAll(test, fresh);
        top.getChildren().addAll(titleBlock, actions);

        HBox meta = new HBox(10);
        meta.setAlignment(Pos.CENTER_RIGHT);
        HBox invoiceId = pill("شماره را آتیران تولید می‌کند", "pill-neutral");
        HBox currency = pill("مبالغ با واحد ثبت‌شده در آتیران", "pill-gold");
        Label dateLabel = new Label("تاریخ فاکتور");
        dateLabel.getStyleClass().add("field-label");
        dateField.getStyleClass().add("field-date");
        dateField.setAlignment(Pos.CENTER_RIGHT);
        dateField.setPrefWidth(150);
        dateField.setTooltip(new Tooltip("تاریخ شمسی، مانند 1405/08/16"));
        dateField.textProperty().addListener((o, a, b) -> updateSubmitState());
        noteField.setPromptText("توضیح کوتاه فاکتور (اختیاری)");
        noteField.setAlignment(Pos.CENTER_RIGHT);
        noteField.setPrefWidth(245);
        noteField.setTooltip(new Tooltip("یادداشتی که در فاکتور آتیران ثبت می‌شود"));
        meta.getChildren().addAll(invoiceId, currency, dateLabel, dateField, noteField);
        header.getChildren().addAll(top, meta);
        return header;
    }

    private Node buildInvoiceBody() {
        VBox workArea = new VBox(14);
        workArea.getStyleClass().add("workspace");
        workArea.setPadding(new Insets(8, 26, 15, 14));
        workArea.setMinWidth(560);
        workArea.getChildren().addAll(buildCustomerCard(), buildProductCard(), buildInvoiceTableCard());
        VBox.setVgrow(workArea.getChildren().get(2), Priority.ALWAYS);

        Node summary = buildSummaryCard();
        HBox body = new HBox(16, workArea, summary);
        body.disableProperty().bind(submitting);
        body.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        HBox.setHgrow(workArea, Priority.ALWAYS);
        body.setPadding(new Insets(0, 0, 0, 0));
        bodyScroll = new ScrollPane(body);
        bodyScroll.setFitToWidth(true);
        bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bodyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        bodyScroll.getStyleClass().add("body-scroll");
        return bodyScroll;
    }

    private Node buildCustomerCard() {
        VBox card = card();
        HBox heading = sectionHeading("مشتری فاکتور", "users", "فقط مشتری فعال و مجاز انتخاب می‌شود");
        HBox search = new HBox(9);
        search.setAlignment(Pos.CENTER_RIGHT);
        customerSearch.setPromptText("نام، کد یا شمارهٔ تماس مشتری");
        customerSearch.setAlignment(Pos.CENTER_RIGHT);
        customerSearch.setOnAction(e -> searchCustomers());
        Button searchButton = primaryButton("جست‌وجو", "search");
        searchButton.setOnAction(e -> searchCustomers());
        HBox.setHgrow(customerSearch, Priority.ALWAYS);
        search.getChildren().addAll(customerSearch, searchButton);

        HBox selected = new HBox(10);
        selected.setAlignment(Pos.CENTER_RIGHT);
        selected.getStyleClass().add("selected-customer");
        StackPane avatar = iconBubble("users", "green");
        VBox selectedText = new VBox(3);
        selectionLabel.getStyleClass().add("selected-customer-name");
        selectedBalanceLabel.getStyleClass().add("selected-customer-detail");
        selectedText.getChildren().addAll(selectionLabel, selectedBalanceLabel);
        Button clear = iconButton("×", "حذف انتخاب مشتری");
        clear.setOnAction(e -> selectCustomer(null));
        Region flex = new Region();
        HBox.setHgrow(flex, Priority.ALWAYS);
        selected.getChildren().addAll(avatar, selectedText, flex, clear);

        customerList.getSelectionModel().selectedItemProperty().addListener((o, oldCustomer, customer) -> {
            if (customer != null) selectCustomer(customer);
        });
        customerList.setPlaceholder(new Label("برای حفظ حریم خصوصی، مشتری را جست‌وجو کن"));
        customerList.setPrefHeight(112);
        customerList.setMinHeight(90);
        customerList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Customer c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setText(null); setGraphic(null); return; }
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_RIGHT);
                VBox detail = new VBox(2);
                Label name = new Label(c.name()); name.getStyleClass().add("result-name");
                Label code = new Label("کد " + digits(c.code()) + (c.phone().isBlank() ? "" : "  ·  " + c.phone()));
                code.getStyleClass().add("result-detail");
                detail.getChildren().addAll(name, code);
                Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
                Label balance = new Label("مانده  " + money(c.balance())); balance.getStyleClass().add("result-balance");
                row.getChildren().addAll(detail, grow, balance);
                setGraphic(row); setText(null); getStyleClass().add("result-cell");
            }
        });
        card.getChildren().addAll(heading, search, selected, customerList);
        return card;
    }

    private Node buildProductCard() {
        VBox card = card();
        HBox heading = sectionHeading("افزودن کالا", "barcode", "نرخ فقط از گروه قیمت یکتای آتیران خوانده می‌شود");
        HBox search = new HBox(9);
        search.setAlignment(Pos.CENTER_RIGHT);
        productSearch.setPromptText("نام کالا، کد یا بارکد را وارد کنید");
        productSearch.setAlignment(Pos.CENTER_RIGHT);
        productSearch.setOnAction(e -> searchProducts());
        Button searchButton = primaryButton("جست‌وجوی کالا", "search");
        searchButton.setOnAction(e -> searchProducts());
        HBox.setHgrow(productSearch, Priority.ALWAYS);
        search.getChildren().addAll(productSearch, searchButton);

        productList.setPlaceholder(new Label("نام، کد یا بارکد کالا را جست‌وجو کن"));
        productList.setPrefHeight(162);
        productList.setMinHeight(120);
        productList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); setGraphic(null); return; }
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_RIGHT);
                StackPane icon = iconBubble("box", p.hasUnambiguousPrice() ? "green" : "muted");
                VBox text = new VBox(3);
                Label name = new Label(p.name()); name.getStyleClass().add("result-name");
                Label detail = new Label("کد " + digits(p.code()) + "  ·  " + p.unit()
                        + (p.barcode().isBlank() ? "" : "  ·  " + p.barcode()));
                detail.getStyleClass().add("result-detail");
                text.getChildren().addAll(name, detail);
                Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
                VBox priceBox = new VBox(2);
                priceBox.setAlignment(Pos.CENTER_LEFT);
                Label price = new Label(p.hasUnambiguousPrice() ? money(p.unitPrice()) : "نرخ نامعتبر");
                price.getStyleClass().add(p.hasUnambiguousPrice() ? "result-price" : "result-error");
                Label packageName = new Label(p.packageName().isBlank() ? "" : p.packageName());
                packageName.getStyleClass().add("result-detail");
                priceBox.getChildren().addAll(price, packageName);
                Button add = primaryButton("افزودن", "plus");
                add.getStyleClass().add("compact-button");
                add.setDisable(!p.hasUnambiguousPrice());
                add.setOnAction(e -> addProduct(p));
                row.getChildren().addAll(icon, text, grow, priceBox, add);
                setGraphic(row); setText(null); getStyleClass().add("result-cell");
            }
        });
        card.getChildren().addAll(heading, search, productList);
        return card;
    }

    private Node buildInvoiceTableCard() {
        VBox card = card();
        HBox heading = sectionHeading("ردیف‌های فاکتور", "invoice", "برای تغییر تعداد یا تخفیف، روی سلول بزنید");
        configureTable();
        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_RIGHT);
        Button remove = secondaryButton("حذف ردیف", "trash");
        remove.setOnAction(e -> {
            Row selected = invoiceTable.getSelectionModel().getSelectedItem();
            if (selected != null) rows.remove(selected);
            refreshTotals();
        });
        Button clear = secondaryButton("پاک‌کردن ردیف‌ها", "refresh");
        clear.setOnAction(e -> { rows.clear(); refreshTotals(); });
        Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
        Label hint = new Label("مقادیر آزمایشی فقط در حالت پیش‌نمایش‌اند");
        hint.getStyleClass().add("table-hint");
        controls.getChildren().addAll(hint, grow, remove, clear);
        VBox.setVgrow(invoiceTable, Priority.ALWAYS);
        card.getChildren().addAll(heading, invoiceTable, controls);
        return card;
    }

    private void configureTable() {
        invoiceTable.setEditable(true);
        invoiceTable.setPlaceholder(new Label("هنوز کالایی به فاکتور اضافه نشده است"));
        invoiceTable.getStyleClass().add("invoice-table");
        invoiceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Row, String> item = new TableColumn<>("شرح کالا");
        item.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().product.name()));
        item.setPrefWidth(270);
        TableColumn<Row, String> quantity = new TableColumn<>("مقدار");
        quantity.setCellValueFactory(v -> v.getValue().quantityProperty());
        quantity.setCellFactory(TextFieldTableCell.forTableColumn());
        quantity.setOnEditCommit(e -> {
            String value = normalizeDigits(e.getNewValue()).replace('٫', '.').replace(',', '.').trim();
            try {
                BigDecimal qty = new BigDecimal(value);
                if (qty.signum() <= 0 || qty.compareTo(new BigDecimal("100000")) > 0) throw new NumberFormatException();
                e.getRowValue().setQuantity(qty.stripTrailingZeros().toPlainString());
                refreshTotals();
            } catch (NumberFormatException ex) {
                e.getRowValue().setQuantity("1");
                refreshTotals();
                setMessage("مقدار معتبر وارد کن.", true);
            }
        });
        quantity.setPrefWidth(78);
        TableColumn<Row, String> unit = new TableColumn<>("واحد");
        unit.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().product.unit()));
        unit.setPrefWidth(80);
        TableColumn<Row, String> price = new TableColumn<>("قیمت واحد");
        price.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().product.unitPrice())));
        price.setPrefWidth(122);
        TableColumn<Row, String> discount = new TableColumn<>("تخفیف ٪");
        discount.setCellValueFactory(v -> v.getValue().discountProperty());
        discount.setCellFactory(TextFieldTableCell.forTableColumn());
        discount.setOnEditCommit(e -> {
            String value = normalizeDigits(e.getNewValue()).replace('٫', '.').replace(',', '.').trim();
            try {
                BigDecimal pct = value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
                if (pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0
                        || pct.stripTrailingZeros().scale() > 2) throw new NumberFormatException();
                e.getRowValue().setDiscount(pct.stripTrailingZeros().toPlainString());
                refreshTotals();
            } catch (NumberFormatException ex) {
                e.getRowValue().setDiscount("0");
                refreshTotals();
                setMessage("تخفیف باید بین صفر تا ۱۰۰ و حداکثر دو رقم اعشار باشد.", true);
            }
        });
        discount.setPrefWidth(88);
        TableColumn<Row, String> total = new TableColumn<>("جمع ردیف");
        total.setCellValueFactory(v -> new ReadOnlyStringWrapper(rowTotal(v.getValue())));
        total.setPrefWidth(140);
        invoiceTable.getColumns().setAll(item, quantity, unit, price, discount, total);
        invoiceTable.setPrefHeight(270);
    }

    private Node buildSummaryCard() {
        VBox summary = new VBox(13);
        summary.getStyleClass().add("summary-card");
        summary.setPrefWidth(315);
        summary.setMinWidth(290);
        summary.setMaxWidth(360);
        Label title = new Label("خلاصهٔ فاکتور"); title.getStyleClass().add("summary-title");
        Label desc = new Label("مبالغ بر اساس واحد آتیران"); desc.getStyleClass().add("summary-caption");
        VBox head = new VBox(4, title, desc);
        VBox values = new VBox(10);
        values.getChildren().addAll(
                summaryRow("جمع ردیف‌ها", subtotalLabel),
                summaryRow("تخفیف ردیف‌ها و کل", discountsLabel),
                summaryRow("مالیات", taxTotalLabel)
        );
        Separator separator = new Separator();
        VBox extra = new VBox(8);
        extra.getChildren().addAll(
                inputRow("تخفیف کل", invoiceDiscountField, "۰"),
                inputRow("باربری", freightField, "۰"),
                inputRow("مالیات ٪", taxField, "۰"),
                inputRow("مدت تسویه (روز)", settlementField, "۰ تا ۷۲۰"),
                inputRow("کد انبار (اختیاری)", warehouseField, "پیش‌فرض آتیران")
        );
        HBox payable = new HBox(6);
        payable.setAlignment(Pos.CENTER_RIGHT);
        VBox payableText = new VBox(3);
        Label payableLabel = new Label("مبلغ نهایی"); payableLabel.getStyleClass().add("payable-label");
        Label payableUnit = new Label("تخفیف، مالیات و باربری لحاظ‌شده"); payableUnit.getStyleClass().add("summary-caption");
        payableText.getChildren().addAll(payableLabel, payableUnit);
        Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
        grandTotalLabel.getStyleClass().add("grand-total");
        payable.getChildren().addAll(payableText, grow, grandTotalLabel);
        VBox submitArea = new VBox(9);
        submitButton.getStyleClass().add("submit-button");
        submitButton.setGraphic(IconFactory.icon("check", 20, Color.WHITE));
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setOnAction(e -> confirmSubmit());
        Button preview = secondaryButton("پیش‌نمایش چاپ", "print");
        preview.setMaxWidth(Double.MAX_VALUE);
        preview.setOnAction(e -> showPrintPreview());
        safetyLabel.setWrapText(true);
        submitArea.getChildren().addAll(submitButton, preview, safetyLabel);
        updateConnectionBadge();
        summary.getChildren().addAll(head, separator, values, extra, new Separator(), payable, submitArea);
        invoiceDiscountField.textProperty().addListener((o, a, b) -> refreshTotals());
        freightField.textProperty().addListener((o, a, b) -> refreshTotals());
        taxField.textProperty().addListener((o, a, b) -> refreshTotals());
        settlementField.textProperty().addListener((o, a, b) -> updateSubmitState());
        warehouseField.textProperty().addListener((o, a, b) -> updateSubmitState());
        return summary;
    }

    private Node buildStatusBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(10, 26, 13, 26));
        connectionDot.getStyleClass().add(gateway.connected() ? "status-dot-live" : "status-dot-demo");
        connectionLabel.setText(gateway.connectionStatus());
        connectionLabel.getStyleClass().add("connection-label");
        messageLabel.getStyleClass().add("status-message");
        Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
        Label privacy = new Label(gateway.live() ? "اطلاعات فقط به SQL Server پیکربندی‌شده فرستاده می‌شود." : "پیش‌نمایش محلی · بدون ذخیره‌سازی یا ارسال");
        privacy.getStyleClass().add("status-privacy");
        bar.getChildren().addAll(connectionDot, connectionLabel, new Separator(), messageLabel, grow, privacy);
        return bar;
    }

    private void searchCustomers() {
        String query = customerSearch.getText();
        runAsync("در حال جست‌وجوی مشتری…", () -> gateway.searchCustomers(query), results -> {
            customerList.getItems().setAll(results);
            if (results.isEmpty()) setMessage("مشتری فعالی با این عبارت پیدا نشد.", true);
            else setMessage(results.size() + " مشتری یافت شد.", false);
        });
    }

    private void searchProducts() {
        String query = productSearch.getText();
        runAsync("در حال جست‌وجوی کالا و نرخ آتیران…", () -> gateway.searchProducts(query), results -> {
            productList.getItems().setAll(results);
            if (results.isEmpty()) setMessage("کالایی با نرخ قابل‌تأیید پیدا نشد.", true);
            else setMessage(results.size() + " کالا بررسی شد؛ نرخ مبهم برای افزودن مسدود است.", false);
        });
    }

    private void selectCustomer(Customer customer) {
        selectedCustomer = customer;
        if (customer == null) {
            customerList.getSelectionModel().clearSelection();
            selectionLabel.setText("هنوز مشتری انتخاب نشده است");
            selectedBalanceLabel.setText("مانده: —");
        } else {
            selectionLabel.setText(customer.name() + "  ·  " + digits(customer.code()));
            selectedBalanceLabel.setText("ماندهٔ فعلی: " + money(customer.balance())
                    + (customer.phone().isBlank() ? "" : "  ·  " + customer.phone()));
        }
        updateSubmitState();
    }

    private void addProduct(Product product) {
        if (!product.hasUnambiguousPrice()) {
            setMessage("نرخ یکتای این کالا از آتیران تأیید نشد؛ افزودن مسدود است.", true);
            return;
        }
        for (Row existing : rows) {
            if (existing.product.code() == product.code()) {
                BigDecimal newQty = parseAmount(existing.quantity.get()).add(BigDecimal.ONE);
                existing.setQuantity(newQty.stripTrailingZeros().toPlainString());
                invoiceTable.refresh();
                refreshTotals();
                setMessage("مقدار کالای موجود یک واحد افزایش یافت.", false);
                return;
            }
        }
        rows.add(new Row(product));
        invoiceTable.getSelectionModel().selectLast();
        refreshTotals();
        setMessage("«" + product.name() + "» به فاکتور اضافه شد.", false);
    }

    private void refreshTotals() {
        try {
            List<InvoiceLine> current = invoiceLines();
            BigDecimal taxPercent = parseAmount(taxField.getText());
            if (taxPercent.signum() < 0 || taxPercent.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("درصد مالیات باید بین صفر تا ۱۰۰ باشد.");
            if (taxPercent.signum() > 0) {
                current = current.stream().map(line -> new InvoiceLine(line.product(), line.quantity(), line.discountPercent(), taxPercent)).toList();
            }
            InvoiceMath.Totals t = InvoiceMath.calculate(current, parseAmount(invoiceDiscountField.getText()), parseAmount(freightField.getText()));
            subtotalLabel.setText(money(t.linesGross()));
            discountsLabel.setText(money(t.lineDiscounts().add(t.globalDiscount())));
            taxTotalLabel.setText(money(t.tax()));
            grandTotalLabel.setText(money(t.payable()));
            setMessage("جمع به‌صورت زنده محاسبه شد.", false);
        } catch (Exception ex) {
            setMessage(ex.getMessage() == null ? "مقدار مالی نامعتبر است." : ex.getMessage(), true);
        }
        updateSubmitState();
        invoiceTable.refresh();
    }

    private List<InvoiceLine> invoiceLines() {
        BigDecimal taxPercent = parseAmount(taxField.getText());
        return rows.stream().map(r -> r.toLine(taxPercent)).toList();
    }

    private String rowTotal(Row row) {
        try { return money(InvoiceMath.linePayable(row.toLine(parseAmount(taxField.getText())))); }
        catch (Exception ex) { return "—"; }
    }

    private void updateSubmitState() {
        String validationMessage = "";
        if (!isNonNegative(invoiceDiscountField.getText())) validationMessage = "تخفیف کل باید عددی و نامنفی باشد.";
        else if (!isNonNegative(freightField.getText())) validationMessage = "هزینهٔ باربری باید عددی و نامنفی باشد.";
        else if (!isPercent(taxField.getText())) validationMessage = "درصد مالیات باید بین صفر تا ۱۰۰ و حداکثر دو رقم اعشار باشد.";
        else if (!isIntegerInRange(settlementField.getText(), 0, 720)) validationMessage = "مدت تسویه باید عددی بین صفر تا ۷۲۰ روز باشد.";
        else if (!isIntegerInRange(warehouseField.getText(), 0, Integer.MAX_VALUE)) validationMessage = "کد انبار باید عدد صحیح نامنفی باشد.";
        else if (!validJalaliDate(normalizeDigits(dateField.getText()).trim())) validationMessage = "تاریخ شمسی معتبر را به شکل 1405/08/16 وارد کن.";

        boolean validInputs = validationMessage.isEmpty();
        if (validInputs && !rows.isEmpty()) {
            try {
                for (Row row : rows) {
                    BigDecimal quantity = parseAmount(row.quantity.get());
                    if (quantity.signum() <= 0 || quantity.compareTo(new BigDecimal("100000")) > 0
                            || quantity.stripTrailingZeros().scale() > 3)
                        throw new IllegalArgumentException("مقدار هر کالا باید مثبت و حداکثر سه رقم اعشار داشته باشد.");
                    if (!isPercent(row.discount.get()))
                        throw new IllegalArgumentException("تخفیف هر ردیف باید بین صفر تا ۱۰۰ باشد.");
                    InvoiceMath.toAtiranQuantity(quantity, row.product.unitsPerPack());
                }
                InvoiceMath.Totals totals = InvoiceMath.calculate(invoiceLines(),
                        parseAmount(invoiceDiscountField.getText()), parseAmount(freightField.getText()));
                if (parseAmount(invoiceDiscountField.getText())
                        .compareTo(totals.linesGross().subtract(totals.lineDiscounts())) > 0)
                    throw new IllegalArgumentException("تخفیف کل از ماندهٔ جمع ردیف‌ها بیشتر است.");
            } catch (RuntimeException ex) {
                validInputs = false;
                validationMessage = ex.getMessage() == null ? "مقادیر فاکتور معتبر نیستند." : ex.getMessage();
            }
        }
        submitButton.setDisable(!gateway.writeEnabled() || selectedCustomer == null || rows.isEmpty() || !validInputs);
        if (!validationMessage.isEmpty()) {
            currentValidationMessage = validationMessage;
            setMessage(validationMessage, true);
        } else if (!currentValidationMessage.isEmpty()) {
            currentValidationMessage = "";
            setMessage("مقادیر فاکتور معتبرند.", false);
        }
    }

    private void confirmSubmit() {
        if (!gateway.writeEnabled()) {
            setMessage("ثبت واقعی خاموش است؛ ابتدا اتصال امن و نسخهٔ آزمایشی آتیران را آماده کن.", true);
            return;
        }
        InvoiceDraft draft;
        try {
            String invoiceDate = normalizeDigits(dateField.getText()).trim();
            draft = new InvoiceDraft(selectedCustomer, invoiceLines(), invoiceDate, noteField.getText(),
                    parseAmount(invoiceDiscountField.getText()), parseAmount(freightField.getText()),
                    parseInt(settlementField.getText(), 0), parseInt(warehouseField.getText(), 0), draftUniqueId);
            if (draft.lines().isEmpty()) throw new IllegalArgumentException("حداقل یک کالا به فاکتور اضافه کن.");
            for (InvoiceLine line : draft.lines())
                InvoiceMath.toAtiranQuantity(line.quantity(), line.product().unitsPerPack());
            InvoiceMath.Totals totals = InvoiceMath.calculate(draft.lines(), draft.globalDiscount(), draft.freight());
            if (!validJalaliDate(draft.jalaliDate())) throw new IllegalArgumentException("تاریخ شمسی معتبر را به شکل 1405/08/16 وارد کن.");
            if (draft.globalDiscount().compareTo(totals.linesGross().subtract(totals.lineDiscounts())) > 0) throw new IllegalArgumentException("تخفیف کل از ماندهٔ مبلغ ردیف‌ها بیشتر است.");
        } catch (Exception ex) {
            setMessage(ex.getMessage(), true);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأیید ثبت در آتیران");
        confirm.setHeaderText("این عملیات، فاکتور و گردش موجودی را در آتیران ثبت می‌کند.");
        confirm.setContentText("مشتری: " + selectedCustomer.name() + "\nمبلغ نهایی: " + grandTotalLabel.getText()
                + "\nپس از تأیید، نتیجه را از فهرست فاکتورهای آتیران هم بررسی کن.");
        ButtonType submit = new ButtonType("ثبت قطعی", ButtonBar.ButtonData.OK_DONE);
        confirm.getButtonTypes().setAll(submit, ButtonType.CANCEL);
        confirm.showAndWait().filter(submit::equals).ifPresent(button -> submitInvoice(draft));
    }

    private void submitInvoice(InvoiceDraft draft) {
        if (submitting.get()) return;
        submitting.set(true);
        setMessage("در حال ثبت امن فاکتور…", false);
        updateSubmitState();
        io.submit(() -> {
            try {
                InvoiceResult result = gateway.submit(draft);
                Platform.runLater(() -> {
                    submitting.set(false);
                    updateConnectionBadge();
                    showSubmitResult(result);
                });
            } catch (Exception ex) {
                String safe = safeMessage(ex);
                Platform.runLater(() -> {
                    submitting.set(false);
                    setMessage(safe, true);
                    updateConnectionBadge();
                });
            }
        });
    }

    private void showSubmitResult(InvoiceResult result) {
        String status = result.confirmed() ? "فاکتور در آتیران قطعی شد." : "فاکتور ثبت شد، اما قطعی‌سازی آن نیاز به بررسی در آتیران دارد.";
        if (result.duplicate()) status = "این فاکتور قبلاً ثبت شده بود؛ از ثبت دوباره جلوگیری شد.\n" + status;
        if (!result.note().isBlank()) status += "\nجزئیات قطعی‌سازی را در برنامهٔ آتیران بررسی کن.";
        Alert done = new Alert(result.confirmed() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        done.setTitle("نتیجهٔ فاکتور");
        done.setHeaderText("شمارهٔ فاکتور " + digits(result.invoiceNumber()));
        done.setContentText(status + "\nمبلغ: " + money(result.total()) + "\nماندهٔ مشتری: " + money(result.customerBalance()));
        done.showAndWait();
        if (result.confirmed()) resetDraft();
        setMessage(result.confirmed() ? "ثبت و قطعی‌سازی موفق بود." : "ثبت انجام شد؛ وضعیت قطعی‌سازی را در آتیران بررسی کن.", !result.confirmed());
    }

    private void showPrintPreview() {
        if (rows.isEmpty()) {
            setMessage("برای پیش‌نمایش، ابتدا ردیف کالا اضافه کن.", true);
            return;
        }
        StringBuilder text = new StringBuilder("پیش‌نمایش محلی فاکتور\n");
        text.append("مشتری: ").append(selectedCustomer == null ? "انتخاب نشده" : selectedCustomer.name()).append("\n");
        for (InvoiceLine line : invoiceLines()) {
            text.append("• ").append(line.product().name()).append("  ·  ")
                    .append(digits(line.quantity())).append(" × ").append(money(line.product().unitPrice()))
                    .append(" = ").append(money(InvoiceMath.linePayable(line))).append("\n");
        }
        text.append("\nمبلغ نهایی: ").append(grandTotalLabel.getText())
                .append("\n\nاین پیش‌نمایش در نسخهٔ فعلی چیزی چاپ یا ذخیره نمی‌کند.");
        Alert preview = new Alert(Alert.AlertType.INFORMATION);
        preview.setTitle("پیش‌نمایش چاپ");
        preview.setHeaderText("فاکتور فروش · " + dateField.getText());
        preview.setContentText(text.toString());
        preview.showAndWait();
    }

    private void showConnectionHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("اتصال امن به آتیران");
        alert.setHeaderText(gateway.connectionStatus());
        alert.setContentText(gateway.live()
                ? "این برنامه فقط از تنظیمات محلی محیط اجرا برای SQL استفاده می‌کند؛ گذرواژه در فایل پروژه ذخیره نمی‌شود. ثبت واقعی فقط با مجوز صریح فعال است."
                : "برای اتصال، مدیر سیستم باید متغیرهای محیطی محلی را طبق windows-invoice/README-fa.md تنظیم کند.\nدر حالت فعلی داده‌ها صرفاً نمایشی‌اند و هیچ فاکتوری ذخیره نمی‌شود.");
        alert.showAndWait();
    }

    private void requestNewDraft() {
        if (!hasInvoiceDraftChanges()) {
            resetDraft();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("شروع فاکتور جدید");
        confirm.setHeaderText("پیش‌نویس فعلی پاک می‌شود.");
        confirm.setContentText("مشتری، ردیف‌ها و مبالغی که وارد کرده‌ای کنار گذاشته می‌شوند. ادامه می‌دهی؟");
        ButtonType discard = new ButtonType("پاک‌کردن و شروع جدید", ButtonBar.ButtonData.OK_DONE);
        confirm.getButtonTypes().setAll(discard, ButtonType.CANCEL);
        confirm.showAndWait().filter(discard::equals).ifPresent(button -> resetDraft());
    }

    private boolean hasInvoiceDraftChanges() {
        return selectedCustomer != null || !rows.isEmpty() || !noteField.getText().isBlank()
                || !isZeroAmount(invoiceDiscountField.getText()) || !isZeroAmount(freightField.getText())
                || !isZeroAmount(taxField.getText()) || !isZeroAmount(settlementField.getText())
                || !isZeroAmount(warehouseField.getText())
                || !normalizeDigits(dateField.getText()).trim().equals(PersianDate.today());
    }

    private static boolean isZeroAmount(String value) {
        try { return parseAmount(value).signum() == 0; }
        catch (RuntimeException ex) { return false; }
    }

    private void resetDraft() {
        rows.clear();
        selectCustomer(null);
        customerSearch.clear();
        productSearch.clear();
        noteField.clear();
        invoiceDiscountField.setText("0");
        freightField.setText("0");
        taxField.setText("0");
        settlementField.setText("0");
        warehouseField.setText("0");
        dateField.setText(PersianDate.today());
        draftUniqueId = newInvoiceId();
        refreshTotals();
        setMessage("فرم فاکتور تازه آماده است.", false);
    }

    private void updateConnectionBadge() {
        connectionLabel.setText(gateway.connectionStatus());
        connectionLabel.getStyleClass().removeAll("connection-live", "connection-demo", "connection-readonly", "connection-pending");
        String stateClass = !gateway.live() ? "connection-demo"
                : !gateway.connected() ? "connection-pending"
                : gateway.writeEnabled() ? "connection-live" : "connection-readonly";
        connectionLabel.getStyleClass().add(stateClass);
        connectionDot.getStyleClass().removeAll("status-dot-live", "status-dot-demo");
        connectionDot.getStyleClass().add(gateway.connected() ? "status-dot-live" : "status-dot-demo");
        safetyLabel.setText(gateway.writeEnabled()
                ? "ثبت فعال است؛ پیش از هر ارسال، مشتری و جمع نهایی را بازبینی کن."
                : "ثبت واقعی خاموش است؛ در این حالت هیچ فاکتوری به آتیران نوشته نمی‌شود.");
        safetyLabel.getStyleClass().removeAll("safety-note", "safety-note-warning");
        safetyLabel.getStyleClass().add(gateway.writeEnabled() ? "safety-note" : "safety-note-warning");
        updateSubmitState();
    }

    private <T> void runAsync(String pending, Callable<T> work, Consumer<T> success) {
        setMessage(pending, false);
        io.submit(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> {
                    success.accept(result);
                    updateConnectionBadge();
                });
            } catch (Exception ex) {
                String safe = safeMessage(ex);
                Platform.runLater(() -> {
                    setMessage(safe, true);
                    updateConnectionBadge();
                });
            }
        });
    }

    private void setMessage(String message, boolean error) {
        messageLabel.setText(message == null || message.isBlank() ? " " : message);
        messageLabel.getStyleClass().removeAll("status-message", "status-message-error");
        messageLabel.getStyleClass().add(error ? "status-message-error" : "status-message");
    }

    private static String safeMessage(Exception ex) {
        // JDBC/SQL diagnostics can reveal server names, account names, or schema details.
        if (ex instanceof java.sql.SQLException) return "عملیات SQL انجام نشد؛ اتصال، سطح دسترسی و سازگاری نسخهٔ آتیران را بررسی کن.";
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return "عملیات انجام نشد؛ اتصال و تنظیمات را بررسی کن.";
        if (message.contains("رمز") || message.toLowerCase(Locale.ROOT).contains("password")
                || message.toLowerCase(Locale.ROOT).contains("jdbc:")) return "اتصال برقرار نشد؛ تنظیمات محلی SQL Server را بررسی کن.";
        return message.length() > 150 ? message.substring(0, 150) + "…" : message;
    }

    private static VBox card() {
        VBox box = new VBox(11);
        box.getStyleClass().add("surface-card");
        box.setPadding(new Insets(15, 17, 15, 17));
        return box;
    }

    private HBox sectionHeading(String title, String icon, String subtitle) {
        HBox row = new HBox(9);
        row.setAlignment(Pos.CENTER_RIGHT);
        StackPane badge = iconBubble(icon, "green");
        VBox text = new VBox(2);
        Label name = new Label(title); name.getStyleClass().add("section-title");
        Label sub = new Label(subtitle); sub.getStyleClass().add("section-caption");
        text.getChildren().addAll(name, sub);
        row.getChildren().addAll(badge, text);
        return row;
    }

    private static HBox summaryRow(String name, Label value) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_RIGHT);
        Label label = new Label(name); label.getStyleClass().add("summary-row-label");
        Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
        value.getStyleClass().add("summary-row-value");
        row.getChildren().addAll(label, grow, value);
        return row;
    }

    private static VBox inputRow(String labelText, TextField input, String prompt) {
        VBox row = new VBox(4);
        Label label = new Label(labelText); label.getStyleClass().add("field-label");
        input.setPromptText(prompt);
        input.getStyleClass().add("summary-input");
        input.setAlignment(Pos.CENTER_RIGHT);
        row.getChildren().addAll(label, input);
        return row;
    }

    private static HBox pill(String text, String cssClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("pill", cssClass);
        HBox box = new HBox(label); box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Label amountLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("summary-row-value");
        return label;
    }

    private static Button primaryButton(String title, String icon) {
        Button b = new Button(title);
        b.setGraphic(IconFactory.icon(icon, 17, Color.WHITE));
        b.getStyleClass().add("primary-button");
        return b;
    }

    private static Button secondaryButton(String title, String icon) {
        Button b = new Button(title);
        b.setGraphic(IconFactory.icon(icon, 17, Color.web(GREEN)));
        b.getStyleClass().add("secondary-button");
        return b;
    }

    private static Button iconButton(String title, String tooltip) {
        Button b = new Button(title);
        b.getStyleClass().add("icon-button");
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    private static StackPane iconBubble(String icon, String style) {
        StackPane box = new StackPane(IconFactory.icon(icon, 19, Color.web(style.equals("green") ? GREEN : "#87958d")));
        box.getStyleClass().addAll("icon-bubble", "icon-bubble-" + style);
        box.setMinSize(40, 40); box.setPrefSize(40, 40); box.setMaxSize(40, 40);
        return box;
    }

    private static Image loadIcon() {
        try (InputStream in = InvoiceDeskView.class.getResourceAsStream("/icons/meelano-invoice.png")) {
            return in == null ? null : new Image(in);
        } catch (Exception ignored) { return null; }
    }

    private static String money(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value.setScale(0, java.math.RoundingMode.HALF_UP);
        NumberFormat f = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR"));
        f.setGroupingUsed(true);
        return f.format(amount);
    }

    private static String digits(long value) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR")).format(value);
    }

    private static String digits(BigDecimal value) {
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("fa-IR")).format(value);
    }

    private static String normalizeDigits(String value) {
        if (value == null) return "";
        return value.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3')
                .replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7')
                .replace('۸','8').replace('۹','9').replace('٠','0').replace('١','1')
                .replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5')
                .replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
    }

    private static BigDecimal parseAmount(String value) {
        String normalized = normalizeDigits(value).replace("٬", "").replace("٫", ".")
                .replace(",", "").replace(" ", "").trim();
        if (normalized.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(normalized);
    }

    private static String newInvoiceId() {
        return "MEELANO-WIN-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean validJalaliDate(String value) {
        return PersianDate.isValidJalaliDate(value);
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(normalizeDigits(value).trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private static boolean isNonNegative(String value) {
        try { return parseAmount(value).signum() >= 0; } catch (Exception ex) { return false; }
    }

    private static boolean isPercent(String value) {
        try {
            BigDecimal n = parseAmount(value);
            return n.signum() >= 0 && n.compareTo(new BigDecimal("100")) <= 0
                    && n.stripTrailingZeros().scale() <= 2;
        } catch (Exception ex) { return false; }
    }

    private static boolean isIntegerInRange(String value, int min, int max) {
        try { int n = parseInt(value, Integer.MIN_VALUE); return n >= min && n <= max; }
        catch (Exception ex) { return false; }
    }

    public boolean isSubmitting() { return submitting.get(); }

    @Override public void close() {
        io.shutdownNow();
        gateway.close();
    }

    private final class Row {
        private final Product product;
        private final StringProperty quantity = new SimpleStringProperty("1");
        private final StringProperty discount = new SimpleStringProperty("0");
        private Row(Product product) { this.product = product; }
        private StringProperty quantityProperty() { return quantity; }
        private StringProperty discountProperty() { return discount; }
        private void setQuantity(String value) { quantity.set(value); }
        private void setDiscount(String value) { discount.set(value); }
        private InvoiceLine toLine(BigDecimal taxPercent) {
            return new InvoiceLine(product, parseAmount(quantity.get()), parseAmount(discount.get()), taxPercent);
        }
    }
}
