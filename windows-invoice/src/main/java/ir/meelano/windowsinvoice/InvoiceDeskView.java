package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.data.AtiranLogin;
import ir.meelano.windowsinvoice.data.AtiranRoles;
import ir.meelano.windowsinvoice.data.WindowsSqlSettings;
import ir.meelano.windowsinvoice.sections.SectionCatalog;
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
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Primary RTL desktop invoice-entry screen. The default gateway is demo-only and cannot write. */
public final class InvoiceDeskView extends BorderPane implements AutoCloseable {
    private static final String GREEN = "#174d3c";
    private final AtiranGateway gateway;
    /** Signed-in Atiran identity; null means environment/demo mode with the full menu set. */
    private final AtiranLogin.Session session;
    private final String role;
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
    private long customerSearchVersion;
    private long productSearchVersion;
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
    private Stage customerCenterStage;
    /** One section window per Manager section id, reused while it is open. */
    private final Map<String, Stage> sectionStages = new LinkedHashMap<>();
    /** Stable across retries; a new token is minted only when the operator starts a new invoice. */
    private String draftUniqueId = newInvoiceId();
    private String startupWarning = "";
    private String currentValidationMessage = "";

    public InvoiceDeskView(AtiranGateway gateway, String startupWarning) {
        this(gateway, startupWarning, null);
    }

    public InvoiceDeskView(AtiranGateway gateway, String startupWarning, AtiranLogin.Session session) {
        this.gateway = gateway;
        this.session = session;
        this.role = session == null ? AtiranRoles.ADMIN : session.appRole;
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

        Label section = new Label(session == null ? "فروشگاه" : "منوهای نقش · " + roleLabel());
        section.getStyleClass().add("sidebar-section-label");
        VBox.setMargin(section, new Insets(27, 9, 7, 0));
        sidebar.getChildren().add(section);
        // The Manager menu set is filtered by the signed-in Atiran role; the first entry stays active.
        List<WindowsRoleMenus.Item> menus = WindowsRoleMenus.itemsFor(role);
        boolean first = true;
        for (WindowsRoleMenus.Item item : menus) {
            sidebar.getChildren().add(navButton(iconOf(item.id), item.label, first,
                    () -> openMenu(item.id), item.shortcut));
            first = false;
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);
        sidebar.getChildren().add(new Separator());
        sidebar.getChildren().add(navButton("settings", "اتصال و تنظیمات سرور", false,
                this::openConnectionSetup, "تنظیم SQL Server و انتخاب دیتابیس"));
        sidebar.getChildren().add(navButton("users", "تغییر کاربر آتیران", false,
                this::switchAtiranUser, "ورود با حساب دیگر"));
        sidebar.getChildren().add(navButton("settings", "راهنمای اتصال", false,
                this::showConnectionHelp, "راهنمای اتصال و تنظیمات"));
        Label version = new Label("نسخهٔ دسکتاپ · ۱.۰");
        version.getStyleClass().add("sidebar-footnote");
        VBox.setMargin(version, new Insets(10, 9, 0, 0));
        sidebar.getChildren().add(version);
        return sidebar;
    }

    /** Persian label of the signed-in role for the sidebar heading and the status bar. */
    public String roleLabel() {
        if (session == null) return "دسترسی کامل محلی";
        String name = session.roleName == null ? "" : session.roleName.trim();
        if (!name.isEmpty()) return name;
        switch (role == null ? "" : role) {
            case AtiranRoles.ADMIN: return "مدیر";
            case AtiranRoles.SALES_MANAGER: return "مدیر فروش";
            case AtiranRoles.ACCOUNTANT: return "حسابدار";
            case AtiranRoles.WAREHOUSE: return "انباردار";
            case AtiranRoles.VISITOR: return "ویزیتور";
            case AtiranRoles.DISTRIBUTOR: return "نمایندهٔ پخش";
            case AtiranRoles.MOADIAN: return "کاربر مودیان";
            case AtiranRoles.SELLER: return "فروشنده";
            default: return "کاربر";
        }
    }

    private static String iconOf(String menuId) {
        switch (menuId == null ? "" : menuId) {
            case WindowsRoleMenus.INVOICE: return "invoice";
            case WindowsRoleMenus.CUSTOMERS: return "users";
            case WindowsRoleMenus.CUSTOMER_CENTER: return "users";
            case WindowsRoleMenus.PRODUCTS: return "box";
            case WindowsRoleMenus.STOCK_LEDGER: return "box";
            case WindowsRoleMenus.CASH: return "cart";
            case WindowsRoleMenus.CHEQUES: return "invoice";
            case WindowsRoleMenus.REPORTS: return "invoice";
            case WindowsRoleMenus.PROFIT: return "invoice";
            case WindowsRoleMenus.PRODUCTION: return "box";
            case WindowsRoleMenus.PERSONNEL: return "users";
            case WindowsRoleMenus.USERS: return "settings";
            case WindowsRoleMenus.MOADIAN: return "invoice";
            case WindowsRoleMenus.HANDOFF: return "print";
            case WindowsRoleMenus.CONNECTION: return "settings";
            default: return "settings";
        }
    }

    /** Runs the section this menu id stands for, or explains that it is not on the desktop build yet. */
    private void openMenu(String menuId) {
        if (!WindowsRoleMenus.allows(role, menuId)) {
            setMessage("این بخش برای نقش «" + roleLabel() + "» فعال نیست.", true);
            return;
        }
        switch (menuId == null ? "" : menuId) {
            case WindowsRoleMenus.INVOICE: focusCustomerSearch(); break;
            case WindowsRoleMenus.CUSTOMERS: focusCustomerSearch(); break;
            case WindowsRoleMenus.CUSTOMER_CENTER: openCustomerCenter(); break;
            case WindowsRoleMenus.PRODUCTS: focusProductSearch(); break;
            case WindowsRoleMenus.HANDOFF: showPrintPreview(); break;
            case WindowsRoleMenus.CONNECTION: openConnectionSetup(); break;
            default: openSection(menuId); break;
        }
    }

    private void pendingSection(String menuId) {
        setMessage("بخش «" + WindowsRoleMenus.labelOf(menuId) + "» در نسخهٔ دسکتاپ هنوز فعال نیست؛"
                + " همین بخش در برنامهٔ Manager اندروید در دسترس است.", true);
    }

    /**
     * Opens the read-only Manager section window (cash, cheques, stock, reports, profit, production,
     * personnel, users). One stage per section is kept and reused; the role check runs here and again
     * inside the gateway, so a role can never reach a section it may not open.
     */
    private void openSection(String menuId) {
        if (getScene() == null) return;
        if (WindowsRoleMenus.MOADIAN.equals(menuId)) {
            setMessage("سامانهٔ مودیان در نسخهٔ دسکتاپ فعال نیست؛ ارسال مالیاتی را از Manager اندروید انجام بده.", true);
            return;
        }
        if (!SectionCatalog.isDesktopSection(menuId)) {
            pendingSection(menuId);
            return;
        }
        if (!WindowsRoleMenus.allows(role, menuId)) {
            setMessage("بخش «" + WindowsRoleMenus.labelOf(menuId) + "» برای نقش «" + roleLabel() + "» فعال نیست.", true);
            return;
        }
        Stage existing = sectionStages.get(menuId);
        if (existing != null && existing.isShowing()) {
            existing.toFront();
            existing.requestFocus();
            return;
        }
        try {
            Stage opened = ManagerSectionView.open(getScene().getWindow(), gateway, menuId, role);
            sectionStages.put(menuId, opened);
            opened.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
                if (sectionStages.get(menuId) == opened) sectionStages.remove(menuId);
            });
            setMessage("بخش «" + WindowsRoleMenus.labelOf(menuId) + "» باز شد؛ همهٔ گزارش‌ها فقط‌خواندنی‌اند.", false);
        } catch (RuntimeException ex) {
            setMessage("باز کردن بخش «" + WindowsRoleMenus.labelOf(menuId) + "» ممکن نشد؛ دوباره تلاش کن.", true);
        }
    }

    /** Reopens the one-time SQL setup (server, port, database, SQL login) and applies the result. */
    private void openConnectionSetup() {
        if (getScene() == null) {
            showConnectionHelp();
            return;
        }
        ConnectionSetupDialog.show(getScene().getWindow()).ifPresent(endpoint -> {
            WindowsSqlSettings.rememberSuccessfulRoute(endpoint.route);
            setMessage("اتصال ذخیره شد: " + endpoint.host + ":" + endpoint.port + " / " + endpoint.database
                    + " — برای اعمال کامل، برنامه را با ورود آتیران دوباره باز کن.", false);
        });
    }

    /** Signs in as another Atiran account; menus follow the new role after a restart of the desk. */
    private void switchAtiranUser() {
        if (getScene() == null) return;
        List<WindowsSqlSettings.Endpoint> endpoints = WindowsSqlSettings.candidates();
        if (endpoints.isEmpty()) {
            openConnectionSetup();
            return;
        }
        AtiranLoginDialog.show(getScene().getWindow(), endpoints).ifPresent(signed ->
                setMessage("ورود «" + signed.title() + "» تأیید شد؛ منوها پس از بازکردن دوبارهٔ برنامه به‌روز می‌شوند.", false));
    }

    private Button navButton(String iconName, String text, boolean active, Runnable action, String shortcutHint) {
        Button b = new Button(text);
        b.setGraphic(IconFactory.icon(iconName, 20, active ? Color.web("#f4d990") : Color.web("#c3d4c9")));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_RIGHT);
        b.getStyleClass().add("nav-button");
        if (active) b.getStyleClass().add("nav-button-active");
        b.setAccessibleText(text + " · " + shortcutHint);
        b.setTooltip(new Tooltip(shortcutHint));
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
        Label overline = new Label(session == null ? "MEELANO  /  فروش"
                : "MEELANO  /  " + (session.displayName == null || session.displayName.isBlank()
                        ? session.username : session.displayName) + "  ·  " + roleLabel());
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
        test.setTooltip(new Tooltip("F5 · بررسی اتصال به SQL Server"));
        test.setOnAction(e -> testConnection());
        Button customers = secondaryButton("مرکز مشتریان · F11", "users");
        customers.setTooltip(new Tooltip("F11 · فهرست، گردش حساب و جزئیات اسناد مشتری"));
        customers.setOnAction(e -> openCustomerCenter());
        Button fresh = secondaryButton("فاکتور جدید", "plus");
        fresh.disableProperty().bind(submitting);
        fresh.setTooltip(new Tooltip("Ctrl+N · شروع یک پیش‌نویس تازه"));
        fresh.setOnAction(e -> requestNewDraft());
        Button keyboardHelp = secondaryButton("کلیدها · F1", "invoice");
        keyboardHelp.getStyleClass().add("compact-button");
        keyboardHelp.setTooltip(new Tooltip("راهنمای کامل کار با صفحه‌کلید"));
        keyboardHelp.setOnAction(e -> showKeyboardHelp());
        actions.getChildren().addAll(test, customers, fresh, keyboardHelp);
        top.getChildren().addAll(titleBlock, actions);

        HBox meta = new HBox(10);
        meta.setAlignment(Pos.CENTER_RIGHT);
        HBox invoiceId = pill("شماره با آتیران", "pill-neutral");
        HBox currency = pill("واحد پول آتیران", "pill-gold");
        Label dateLabel = new Label("تاریخ · Ctrl+4");
        dateLabel.getStyleClass().add("field-label");
        HBox dateCaption = new HBox(5, IconFactory.icon("calendar", 14, Color.web("#738bd0")), dateLabel);
        dateCaption.setAlignment(Pos.CENTER_RIGHT);
        dateField.getStyleClass().add("field-date");
        dateField.setAlignment(Pos.CENTER_RIGHT);
        dateField.setPrefWidth(132);
        dateField.setAccessibleText("تاریخ فاکتور · Ctrl+4");
        dateField.setTooltip(new Tooltip("Ctrl+4 · تاریخ شمسی، مانند 1405/08/16 · Enter برای رفتن به یادداشت"));
        dateField.setOnAction(e -> noteField.requestFocus());
        dateField.textProperty().addListener((o, a, b) -> updateSubmitState());
        noteField.setPromptText("یادداشت (اختیاری)");
        noteField.setAlignment(Pos.CENTER_RIGHT);
        noteField.setPrefWidth(205);
        noteField.setAccessibleText("یادداشت فاکتور · Ctrl+5");
        noteField.setTooltip(new Tooltip("Ctrl+5 · یادداشت فاکتور؛ Enter برای شروع انتخاب مشتری"));
        noteField.setOnAction(e -> focusCustomerSearch());
        Label noteIcon = new Label();
        noteIcon.setGraphic(IconFactory.icon("note", 14, Color.web("#7d8eae")));
        noteIcon.setAccessibleText("یادداشت فاکتور · Ctrl+5");
        noteIcon.setTooltip(new Tooltip("Ctrl+5 · رفتن به یادداشت فاکتور"));
        meta.getChildren().addAll(invoiceId, currency, dateCaption, dateField, noteIcon, noteField);
        header.widthProperty().addListener((o, oldWidth, newWidth) -> {
            boolean compactHeader = newWidth.doubleValue() < 900;
            dateField.setPrefWidth(compactHeader ? 112 : 132);
            noteField.setPrefWidth(compactHeader ? 138 : 205);
        });
        header.getChildren().addAll(top, meta);
        return header;
    }

    private void testConnection() {
        runAsync("در حال آزمون اتصال…", () -> { gateway.testConnection(); return Boolean.TRUE; }, ok -> {
            setMessage("اتصال به SQL Server برقرار است.", false);
            updateConnectionBadge();
        });
    }

    private Node buildInvoiceBody() {
        VBox workArea = new VBox(12);
        workArea.getStyleClass().add("workspace");
        workArea.setPadding(new Insets(8, 20, 12, 12));
        workArea.setMinWidth(0);

        VBox customerCard = (VBox) buildCustomerCard();
        VBox productCard = (VBox) buildProductCard();
        customerCard.setPrefWidth(300);
        customerCard.setMinWidth(248);
        customerCard.setMaxWidth(360);
        productCard.setMinWidth(300);
        HBox.setHgrow(productCard, Priority.ALWAYS);

        HBox quickEntry = new HBox(12, customerCard, productCard);
        quickEntry.setAlignment(Pos.TOP_RIGHT);
        quickEntry.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(quickEntry, Priority.NEVER);

        VBox tableCard = (VBox) buildInvoiceTableCard();
        VBox.setVgrow(tableCard, Priority.ALWAYS);
        workArea.getChildren().addAll(quickEntry, tableCard);

        VBox summary = (VBox) buildSummaryCard();
        summary.setPrefWidth(315);
        summary.setMinWidth(270);
        summary.setMaxWidth(360);

        HBox body = new HBox(16, workArea, summary);
        body.disableProperty().bind(submitting);
        body.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        HBox.setHgrow(workArea, Priority.ALWAYS);
        body.setPadding(Insets.EMPTY);
        bodyScroll = new ScrollPane(body);
        bodyScroll.setFitToWidth(true);
        bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bodyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        bodyScroll.getStyleClass().add("body-scroll");

        body.widthProperty().addListener((o, oldWidth, newWidth) -> {
            double available = newWidth.doubleValue();
            boolean compact = available < 1280;
            double summaryWidth = compact ? (available < 1080 ? 232 : 280) : 315;
            summary.setPrefWidth(summaryWidth);
            summary.setMinWidth(compact ? Math.min(230, summaryWidth) : 290);
            workArea.setPadding(compact ? new Insets(8, 8, 10, 8) : new Insets(8, 20, 12, 12));
            body.setSpacing(compact ? 8 : 16);
            quickEntry.setSpacing(compact ? 8 : 12);
            customerCard.setPrefWidth(compact ? Math.max(210, Math.min(255, available * 0.28)) : 300);
            customerCard.setMinWidth(compact ? 210 : 248);
            productCard.setMinWidth(compact ? 260 : 300);
        });
        return bodyScroll;
    }

    private Node buildCustomerCard() {
        VBox card = card();
        HBox heading = sectionHeading("مشتری فاکتور", "users", "فقط مشتری فعال و مجاز انتخاب می‌شود");
        HBox search = new HBox(9);
        search.setAlignment(Pos.CENTER_RIGHT);
        customerSearch.setPromptText("نام یا کد مشتری · Ctrl+F");
        customerSearch.setAlignment(Pos.CENTER_RIGHT);
        customerSearch.setTooltip(new Tooltip("Ctrl+F برای مشتری؛ عبارت را بنویس و Enter بزن."));
        customerSearch.setOnAction(e -> searchCustomers());
        Button searchButton = primaryButton("جست‌وجو", "search");
        searchButton.getStyleClass().add("compact-button");
        searchButton.setTooltip(new Tooltip("جست‌وجوی مشتری · Enter"));
        searchButton.setOnAction(e -> searchCustomers());
        HBox.setHgrow(customerSearch, Priority.ALWAYS);
        search.getChildren().addAll(customerSearch, searchButton);

        HBox selected = new HBox(10);
        selected.setAlignment(Pos.CENTER_RIGHT);
        selected.getStyleClass().add("selected-customer");
        StackPane avatar = iconBubble("users", "green");
        VBox selectedText = new VBox(3);
        selectedText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(selectedText, Priority.ALWAYS);
        selectionLabel.getStyleClass().add("selected-customer-name");
        selectedBalanceLabel.getStyleClass().add("selected-customer-detail");
        selectionLabel.setWrapText(true);
        selectedBalanceLabel.setWrapText(true);
        selectedText.getChildren().addAll(selectionLabel, selectedBalanceLabel);
        Button clear = iconButton("×", "حذف انتخاب مشتری");
        clear.setAccessibleText("حذف مشتری انتخاب‌شده");
        clear.setOnAction(e -> { selectCustomer(null); focusCustomerSearch(); });
        selected.getChildren().addAll(avatar, selectedText, clear);

        customerList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) acceptCustomerSelection();
        });
        customerList.setPlaceholder(new Label("برای حفظ حریم خصوصی، مشتری را جست‌وجو کن"));
        customerList.setPrefHeight(112);
        customerList.setMinHeight(52);
        customerList.setMaxHeight(120);
        customerList.setVisible(false);
        customerList.setManaged(false);
        customerList.setAccessibleText("نتایج مشتری؛ با کلیدهای جهت و Enter انتخاب کن");
        customerList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                acceptCustomerSelection();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                setCustomerResultsVisible(false);
                customerSearch.requestFocus();
                e.consume();
            }
        });
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
        productSearch.setPromptText("نام، کد یا بارکد · Ctrl+K / اسکنر");
        productSearch.setAlignment(Pos.CENTER_RIGHT);
        productSearch.setTooltip(new Tooltip("Ctrl+K؛ بارکدخوان را مثل صفحه‌کلید وصل کن و بارکد را اسکن کن."));
        productSearch.setOnAction(e -> searchProducts());
        Button searchButton = primaryButton("جست‌وجو", "search");
        searchButton.getStyleClass().add("compact-button");
        searchButton.setTooltip(new Tooltip("جست‌وجو؛ بارکد یکتا با Enter خودکار افزوده می‌شود."));
        searchButton.setOnAction(e -> searchProducts());
        HBox.setHgrow(productSearch, Priority.ALWAYS);
        search.getChildren().addAll(productSearch, searchButton);

        productList.setPlaceholder(new Label("نام، کد یا بارکد کالا را جست‌وجو کن"));
        productList.setPrefHeight(120);
        productList.setMinHeight(52);
        productList.setMaxHeight(160);
        productList.setVisible(false);
        productList.setManaged(false);
        productList.setAccessibleText("نتایج کالا؛ با کلیدهای جهت و Enter افزودن");
        productList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                addSelectedProductFromKeyboard();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                setProductResultsVisible(false);
                productSearch.requestFocus();
                e.consume();
            }
        });
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
        HBox heading = sectionHeading("ردیف‌های فاکتور", "invoice", "F2 مقدار · Shift+F2 تخفیف · Ctrl+Backspace حذف");
        configureTable();
        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_RIGHT);
        Button remove = secondaryButton("حذف ردیف", "trash");
        remove.setTooltip(new Tooltip("ردیف انتخاب‌شده · Ctrl+Backspace یا Ctrl+Delete"));
        remove.setOnAction(e -> removeSelectedRow());
        Button clear = secondaryButton("پاک‌کردن ردیف‌ها", "refresh");
        clear.setOnAction(e -> { rows.clear(); refreshTotals(); productSearch.requestFocus(); });
        Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
        Label hint = new Label("↑ ↓ جابه‌جایی · Enter تأیید مقدار · F2 ویرایش");
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
        invoiceTable.setPrefHeight(220);
        invoiceTable.setMinHeight(132);
        invoiceTable.setAccessibleText("ردیف‌های فاکتور؛ F2 ویرایش تعداد، Shift+F2 تخفیف، Ctrl+Backspace یا Ctrl+Delete حذف");
        invoiceTable.setTooltip(new Tooltip("با جهت‌ها حرکت کن؛ F2 تعداد و Shift+F2 تخفیف را ویرایش می‌کند. Ctrl+Backspace/Delete ردیف را حذف می‌کند."));
    }

    private Node buildSummaryCard() {
        VBox summary = new VBox(13);
        summary.getStyleClass().add("summary-card");
        summary.setPrefWidth(315);
        summary.setMinWidth(290);
        summary.setMaxWidth(360);
        Label title = new Label("خلاصهٔ فاکتور"); title.getStyleClass().add("summary-title");
        Label desc = new Label("مبالغ بر اساس واحد آتیران"); desc.getStyleClass().add("summary-caption");
        VBox headText = new VBox(4, title, desc);
        HBox head = new HBox(10, iconBubble("wallet", "gold"), headText);
        head.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(headText, Priority.ALWAYS);
        VBox values = new VBox(10);
        values.getChildren().addAll(
                summaryRow("جمع ردیف‌ها", subtotalLabel),
                summaryRow("تخفیف ردیف‌ها و کل", discountsLabel),
                summaryRow("مالیات", taxTotalLabel)
        );
        Separator separator = new Separator();
        VBox discountInput = inputRow("تخفیف کل", invoiceDiscountField, "۰", "discount", "Ctrl+6");
        VBox freightInput = inputRow("باربری", freightField, "۰", "truck", "Ctrl+7");
        VBox taxInput = inputRow("مالیات ٪", taxField, "۰", "percent", "Ctrl+8");
        VBox settlementInput = inputRow("تسویه (روز)", settlementField, "۰ تا ۷۲۰", "clock", "Ctrl+9");
        HBox discountAndFreight = inputPair(discountInput, freightInput);
        HBox taxAndSettlement = inputPair(taxInput, settlementInput);
        VBox extra = new VBox(7,
                discountAndFreight,
                taxAndSettlement,
                inputRow("انبار (اختیاری)", warehouseField, "پیش‌فرض آتیران", "warehouse", "Ctrl+0"));
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
        submitButton.setTooltip(new Tooltip("Ctrl+Enter · ثبت واقعی فقط با اتصال و مجوز فعال می‌شود."));
        submitButton.setOnAction(e -> confirmSubmit());
        Button preview = secondaryButton("پیش‌نمایش چاپ", "print");
        preview.setMaxWidth(Double.MAX_VALUE);
        preview.setTooltip(new Tooltip("Ctrl+P · پیش‌نمایش محلی؛ چاپ یا ذخیره نمی‌کند."));
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
        invoiceDiscountField.setOnAction(e -> freightField.requestFocus());
        freightField.setOnAction(e -> taxField.requestFocus());
        taxField.setOnAction(e -> settlementField.requestFocus());
        settlementField.setOnAction(e -> warehouseField.requestFocus());
        warehouseField.setOnAction(e -> focusProductSearch());
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
        connectionLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        connectionLabel.setMaxWidth(250);
        messageLabel.getStyleClass().add("status-message");
        messageLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        messageLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(messageLabel, Priority.SOMETIMES);
        Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
        Label privacy = new Label(gateway.live() ? "فقط به SQL Server پیکربندی‌شده ارسال می‌شود." : "پیش‌نمایش محلی · بدون ذخیره‌سازی یا ارسال");
        privacy.getStyleClass().add("status-privacy");
        privacy.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        privacy.setMaxWidth(260);
        bar.getChildren().addAll(connectionDot, connectionLabel, new Separator(), messageLabel, grow, privacy);
        return bar;
    }

    private void searchCustomers() {
        long requestVersion = ++customerSearchVersion;
        String query = customerSearch.getText() == null ? "" : customerSearch.getText().trim();
        if (normalizeDigits(query).length() < 2) {
            customerList.getItems().clear();
            setCustomerResultsVisible(false);
            setMessage("برای جست‌وجوی سریع مشتری دست‌کم دو نویسه وارد کن.", true);
            return;
        }
        customerList.getItems().clear();
        customerList.getSelectionModel().clearSelection();
        setCustomerResultsVisible(false);
        runAsync("در حال جست‌وجوی مشتری…", () -> gateway.searchCustomers(query), results -> {
            if (requestVersion != customerSearchVersion || !query.equals(customerSearch.getText().trim())) return;
            customerList.getItems().setAll(results);
            if (results.isEmpty()) {
                setCustomerResultsVisible(false);
                setMessage("مشتری فعالی با این عبارت پیدا نشد.", true);
                customerSearch.requestFocus();
                return;
            }
            customerList.setPrefHeight(Math.min(126, Math.max(56, results.size() * 48 + 8)));
            setCustomerResultsVisible(true);
            boolean exactCode = results.size() == 1
                    && FastEntryPolicy.isExactCustomerCode(query, results.get(0));
            if (exactCode) {
                customerList.getSelectionModel().selectFirst();
                acceptCustomerSelection();
                setMessage("کد دقیق مشتری تأیید شد؛ حالا کالا را جست‌وجو یا اسکن کن.", false);
            } else {
                customerList.getSelectionModel().selectFirst();
                customerList.requestFocus();
                setMessage(digits(results.size()) + " مشتری یافت شد؛ با کلیدهای ↑ و ↓ و سپس Enter انتخاب کن.", false);
            }
        });
    }

    private void searchProducts() {
        long requestVersion = ++productSearchVersion;
        String query = productSearch.getText() == null ? "" : productSearch.getText().trim();
        if (normalizeDigits(query).length() < 2) {
            productList.getItems().clear();
            setProductResultsVisible(false);
            setMessage("برای جست‌وجوی کالا دست‌کم دو نویسه یا یک بارکد اسکن کن.", true);
            return;
        }
        productList.getItems().clear();
        productList.getSelectionModel().clearSelection();
        setProductResultsVisible(false);
        runAsync("در حال جست‌وجوی کالا و نرخ آتیران…", () -> gateway.searchProducts(query), results -> {
            if (requestVersion != productSearchVersion || !query.equals(productSearch.getText().trim())) return;
            productList.getItems().setAll(results);
            if (results.isEmpty()) {
                setProductResultsVisible(false);
                setMessage("کالایی با نرخ قابل‌تأیید پیدا نشد.", true);
                productSearch.requestFocus();
                return;
            }
            productList.setPrefHeight(Math.min(156, Math.max(56, results.size() * 48 + 8)));
            setProductResultsVisible(true);
            if (results.size() == 1 && FastEntryPolicy.canAutoAddProduct(query, results.get(0))) {
                Product exact = results.get(0);
                addProduct(exact);
                clearProductSearchForNextScan();
                setMessage("شناسهٔ یکتای کالا تأیید شد؛ افزوده شد. بارکد بعدی را اسکن کن.", false);
                return;
            }
            productList.getSelectionModel().selectFirst();
            productList.requestFocus();
            setMessage(digits(results.size()) + " کالا یافت شد؛ با ↑ و ↓ انتخاب کن و Enter بزن تا افزوده شود.", false);
        });
    }

    public void installKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyboardShortcut);
    }

    public void focusInitialField() {
        focusCustomerSearch();
    }

    /** Ctrl+Shift+4..0 → Manager sections, Ctrl+Shift+U → users; "" when the key is not a section. */
    private static String sectionShortcut(KeyCode code) {
        switch (code) {
            case DIGIT4, NUMPAD4: return WindowsRoleMenus.sectionForDigit(4);
            case DIGIT5, NUMPAD5: return WindowsRoleMenus.sectionForDigit(5);
            case DIGIT6, NUMPAD6: return WindowsRoleMenus.sectionForDigit(6);
            case DIGIT7, NUMPAD7: return WindowsRoleMenus.sectionForDigit(7);
            case DIGIT8, NUMPAD8: return WindowsRoleMenus.sectionForDigit(8);
            case DIGIT9, NUMPAD9: return WindowsRoleMenus.sectionForDigit(9);
            case DIGIT0, NUMPAD0: return WindowsRoleMenus.sectionForDigit(0);
            case U: return WindowsRoleMenus.USERS;
            default: return "";
        }
    }

    private void openCustomerCenter() {
        if (getScene() == null) return;
        if (!WindowsRoleMenus.allows(role, WindowsRoleMenus.CUSTOMER_CENTER)) {
            setMessage("مرکز مشتریان برای نقش «" + roleLabel() + "» فعال نیست.", true);
            return;
        }
        if (customerCenterStage != null && customerCenterStage.isShowing()) {
            customerCenterStage.toFront();
            customerCenterStage.requestFocus();
            return;
        }
        Stage opened = CustomerCenterView.open(getScene().getWindow(), gateway);
        customerCenterStage = opened;
        opened.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
            if (customerCenterStage == opened) customerCenterStage = null;
        });
    }

    private void handleKeyboardShortcut(KeyEvent event) {
        if (event.getCode() == KeyCode.F11) {
            if (!submitting.get()) openCustomerCenter();
            event.consume();
            return;
        }
        if (submitting.get()) return;
        if (event.getCode() == KeyCode.F5) {
            testConnection();
            event.consume();
            return;
        }
        if (event.isControlDown() && !event.isAltDown() && event.isShiftDown()) {
            String section = sectionShortcut(event.getCode());
            if (!section.isEmpty()) {
                openSection(section);
                event.consume();
                return;
            }
        }
        if (event.isControlDown() && !event.isAltDown() && !event.isShiftDown()) {
            switch (event.getCode()) {
                case F -> { focusCustomerSearch(); event.consume(); }
                case K -> { focusProductSearch(); event.consume(); }
                case N -> { requestNewDraft(); event.consume(); }
                case P -> { showPrintPreview(); event.consume(); }
                case ENTER -> { submitFromKeyboard(); event.consume(); }
                case DIGIT1, NUMPAD1 -> { focusCustomerSearch(); event.consume(); }
                case DIGIT2, NUMPAD2 -> { focusProductSearch(); event.consume(); }
                case DIGIT3, NUMPAD3 -> { focusInBody(invoiceTable, 1.0); event.consume(); }
                case DIGIT4, NUMPAD4 -> { focusHeaderField(dateField); event.consume(); }
                case DIGIT5, NUMPAD5 -> { focusHeaderField(noteField); event.consume(); }
                case DIGIT6, NUMPAD6 -> { focusSummaryField(invoiceDiscountField); event.consume(); }
                case DIGIT7, NUMPAD7 -> { focusSummaryField(freightField); event.consume(); }
                case DIGIT8, NUMPAD8 -> { focusSummaryField(taxField); event.consume(); }
                case DIGIT9, NUMPAD9 -> { focusSummaryField(settlementField); event.consume(); }
                case DIGIT0, NUMPAD0 -> { focusSummaryField(warehouseField); event.consume(); }
                case BACK_SPACE, DELETE -> {
                    if (isTableFocused()) { removeSelectedRow(); event.consume(); }
                }
                default -> { }
            }
            if (event.isConsumed()) return;
        }
        if (event.getCode() == KeyCode.F1) {
            showKeyboardHelp();
            event.consume();
        } else if (event.getCode() == KeyCode.F2) {
            editSelectedTableCell(event.isShiftDown());
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            if (productList.isVisible()) {
                setProductResultsVisible(false);
                focusProductSearch();
            } else if (customerList.isVisible()) {
                setCustomerResultsVisible(false);
                focusCustomerSearch();
            }
            event.consume();
        }
    }

    private void focusHeaderField(TextField field) {
        Platform.runLater(() -> {
            field.requestFocus();
            field.selectAll();
        });
    }

    private void focusSummaryField(TextField field) {
        if (bodyScroll != null) bodyScroll.setVvalue(1.0);
        Platform.runLater(() -> {
            field.requestFocus();
            field.selectAll();
        });
    }

    private void focusCustomerSearch() {
        setCustomerResultsVisible(false);
        focusInBody(customerSearch, 0.0);
        Platform.runLater(customerSearch::selectAll);
    }

    private void focusProductSearch() {
        setProductResultsVisible(false);
        focusInBody(productSearch, 0.0);
        Platform.runLater(productSearch::selectAll);
    }

    private void submitFromKeyboard() {
        if (!WindowsRoleMenus.canSubmitInvoice(role)) {
            setMessage("ثبت فاکتور برای نقش «" + roleLabel() + "» فعال نیست.", true);
            return;
        }
        if (!gateway.writeEnabled()) {
            setMessage("ثبت واقعی خاموش است؛ Ctrl+Enter هیچ داده‌ای را در آتیران نمی‌نویسد.", true);
            return;
        }
        if (submitButton.isDisabled()) {
            setMessage(currentValidationMessage.isBlank()
                    ? "برای ثبت، مشتری و دست‌کم یک کالای معتبر انتخاب کن."
                    : currentValidationMessage, true);
            return;
        }
        submitButton.fire();
    }

    private boolean isTableFocused() {
        if (getScene() == null) return false;
        Node node = getScene().getFocusOwner();
        while (node != null) {
            if (node == invoiceTable) return true;
            node = node.getParent();
        }
        return false;
    }

    private void editSelectedTableCell(boolean discount) {
        if (rows.isEmpty()) {
            setMessage("برای ویرایش، ابتدا یک ردیف کالا اضافه کن.", true);
            focusProductSearch();
            return;
        }
        int row = invoiceTable.getSelectionModel().getSelectedIndex();
        if (row < 0) row = 0;
        int columnIndex = discount ? 4 : 1;
        TableColumn<Row, ?> column = invoiceTable.getColumns().get(columnIndex);
        invoiceTable.getSelectionModel().select(row);
        invoiceTable.getFocusModel().focus(row, column);
        invoiceTable.requestFocus();
        invoiceTable.edit(row, column);
    }

    private void removeSelectedRow() {
        Row selected = invoiceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setMessage("برای حذف، ابتدا ردیف را با کلیدهای جهت انتخاب کن.", true);
            return;
        }
        rows.remove(selected);
        refreshTotals();
        focusProductSearch();
    }

    private void addSelectedProductFromKeyboard() {
        Product product = productList.getSelectionModel().getSelectedItem();
        if (product == null && !productList.getItems().isEmpty()) {
            productList.getSelectionModel().selectFirst();
            product = productList.getSelectionModel().getSelectedItem();
        }
        if (product == null) {
            setMessage("ابتدا کالا را جست‌وجو کن.", true);
            focusProductSearch();
            return;
        }
        addProduct(product);
        if (product.hasUnambiguousPrice()) clearProductSearchForNextScan();
    }

    private void clearProductSearchForNextScan() {
        productSearch.clear();
        productList.getItems().clear();
        setProductResultsVisible(false);
        focusProductSearch();
    }

    private void acceptCustomerSelection() {
        Customer customer = customerList.getSelectionModel().getSelectedItem();
        if (customer == null && !customerList.getItems().isEmpty()) {
            customerList.getSelectionModel().selectFirst();
            customer = customerList.getSelectionModel().getSelectedItem();
        }
        if (customer == null) return;
        selectCustomer(customer);
        setCustomerResultsVisible(false);
        focusProductSearch();
    }

    private void setCustomerResultsVisible(boolean visible) {
        customerList.setVisible(visible);
        customerList.setManaged(visible);
        if (!visible) customerList.getSelectionModel().clearSelection();
    }

    private void setProductResultsVisible(boolean visible) {
        productList.setVisible(visible);
        productList.setManaged(visible);
        if (!visible) productList.getSelectionModel().clearSelection();
    }

    private void showKeyboardHelp() {
        Alert help = new Alert(Alert.AlertType.INFORMATION);
        help.setTitle("کار با صفحه‌کلید");
        help.setHeaderText("ثبت سریع فاکتور، بدون نیاز به ماوس");
        help.setContentText("F11  باز کردن مرکز مشتریان؛ کلیک روی مشتری یا انتخاب و Enter برای گردش حساب\n"
                + "Ctrl+F  رفتن به جست‌وجوی مشتری؛ Enter برای جست‌وجو\n"
                + "Ctrl+K  رفتن به جست‌وجو یا اسکن بارکد کالا\n"
                + "نتایج: ↑ و ↓ برای انتخاب · Enter برای تأیید یا افزودن · Esc برای بستن\n"
                + "بارکد یکتای کالا + Enter  افزودن خودکار؛ اسکن دوباره مقدار را یکی زیاد می‌کند\n"
                + "Ctrl+1…Ctrl+0  مشتری / کالا / جدول / تاریخ / یادداشت / تخفیف / باربری / مالیات / تسویه / انبار\n"
                + "Ctrl+Shift+4…Ctrl+Shift+0  بخش‌های مدیریتی: انبار / صندوق / چک‌ها / گزارش / سود / تولید / پرسنل\n"
                + "Ctrl+Shift+U  مدیریت کاربران و دسترسی (فقط نقش مدیر) — همهٔ گزارش‌ها فقط‌خواندنی\n"
                + "در خلاصه: Enter  تخفیف ← باربری ← مالیات ← تسویه ← انبار ← جست‌وجوی کالا\n"
                + "در بالای فرم: Enter  تاریخ ← یادداشت ← مشتری\n"
                + "Tab / Shift+Tab  کنترل بعدی / قبلی\n"
                + "F2  ویرایش مقدار ردیف · Shift+F2  ویرایش تخفیف ردیف\n"
                + "Ctrl+Backspace یا Ctrl+Delete  حذف ردیف انتخابی (با فوکوس جدول)\n"
                + "Ctrl+P  پیش‌نمایش محلی · Ctrl+Enter  بررسی و درخواست تأیید ثبت\n"
                + "Ctrl+N  فاکتور جدید · F5  آزمون اتصال · F1  راهنما · Esc  بستن نتایج");
        help.showAndWait();
        focusProductSearch();
    }

    private void selectCustomer(Customer customer) {
        selectedCustomer = customer;
        if (customer == null) {
            setCustomerResultsVisible(false);
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
        submitButton.setDisable(!gateway.writeEnabled() || !WindowsRoleMenus.canSubmitInvoice(role)
                || selectedCustomer == null || rows.isEmpty() || !validInputs);
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

    /** True when closing the window would discard user-entered invoice fields or rows. */
    public boolean hasInvoiceDraftChanges() {
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
        customerList.getItems().clear();
        productList.getItems().clear();
        setCustomerResultsVisible(false);
        setProductResultsVisible(false);
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
        focusCustomerSearch();
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
        StackPane badge = iconBubble(icon, iconBubbleStyle(icon));
        VBox text = new VBox(2);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label name = new Label(title); name.getStyleClass().add("section-title");
        Label sub = new Label(subtitle); sub.getStyleClass().add("section-caption");
        sub.setWrapText(true);
        sub.setMaxWidth(Double.MAX_VALUE);
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

    private static VBox inputRow(String labelText, TextField input, String prompt,
                                 String iconName, String shortcut) {
        VBox row = new VBox(4);
        row.setMinWidth(0);
        HBox labelLine = new HBox(4);
        labelLine.setAlignment(Pos.CENTER_RIGHT);
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        HBox.setHgrow(label, Priority.SOMETIMES);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label shortcutHint = new Label(shortcut);
        shortcutHint.getStyleClass().add("field-shortcut");
        labelLine.getChildren().addAll(
                IconFactory.icon(iconName, 13, Color.web(GREEN)), label, spacer, shortcutHint);
        input.setPromptText(prompt);
        input.getStyleClass().add("summary-input");
        input.setAlignment(Pos.CENTER_RIGHT);
        input.setPrefColumnCount(6);
        input.setMinWidth(0);
        input.setMaxWidth(Double.MAX_VALUE);
        input.setAccessibleText(labelText + " · " + shortcut);
        input.setTooltip(new Tooltip(labelText + " · میانبر صفحه‌کلید: " + shortcut));
        row.getChildren().addAll(labelLine, input);
        return row;
    }

    private static HBox inputPair(VBox first, VBox second) {
        HBox row = new HBox(10, first, second);
        row.setMinWidth(0);
        row.setAlignment(Pos.CENTER_RIGHT);
        first.setMinWidth(0);
        second.setMinWidth(0);
        first.setMaxWidth(Double.MAX_VALUE);
        second.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(first, Priority.ALWAYS);
        HBox.setHgrow(second, Priority.ALWAYS);
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
        Color tint = switch (style) {
            case "blue" -> Color.web("#477fae");
            case "teal" -> Color.web("#357f72");
            case "coral" -> Color.web("#a65f43");
            case "gold" -> Color.web("#997525");
            case "green" -> Color.web(GREEN);
            default -> Color.web("#87958d");
        };
        StackPane box = new StackPane(IconFactory.icon(icon, 19, tint));
        box.getStyleClass().addAll("icon-bubble", "icon-bubble-" + style);
        box.setMinSize(40, 40); box.setPrefSize(40, 40); box.setMaxSize(40, 40);
        return box;
    }

    private static String iconBubbleStyle(String icon) {
        return switch (icon) {
            case "users" -> "blue";
            case "barcode", "box" -> "teal";
            case "cart" -> "coral";
            case "wallet", "money", "invoice" -> "gold";
            default -> "green";
        };
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
        if (customerCenterStage != null && customerCenterStage.isShowing()) customerCenterStage.close();
        customerCenterStage = null;
        for (Stage section : new ArrayList<>(sectionStages.values())) {
            if (section != null && section.isShowing()) section.close();
        }
        sectionStages.clear();
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
