package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.data.WindowsServerDiscovery;
import ir.meelano.windowsinvoice.data.WindowsSqlSettings;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One-time Windows SQL setup: type a server name/IP (or scan the local network), choose the
 * port (1433 by default, or a forwarded port such as 5050), pick a database, then save.
 * The SQL password is stored only in the current Windows account's DPAPI-protected store.
 */
public final class ConnectionSetupDialog {
    private ConnectionSetupDialog() { }

    public static Optional<WindowsSqlSettings.Endpoint> show(Window owner) {
        Stage stage = new Stage();
        stage.initModality(owner == null ? Modality.APPLICATION_MODAL : Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("تنظیم اتصال به SQL Server");

        WindowsSqlSettings.Draft draft = WindowsSqlSettings.draft();
        String initialHost = !draft.lanHost.isBlank() ? draft.lanHost : draft.wanHost;
        int initialPort = !draft.lanHost.isBlank() ? draft.lanPort : draft.wanPort;

        TextField host = new TextField(initialHost);
        host.setPromptText("نام سرور یا IP (مثلاً 192.168.1.10 یا 5.120.30.40)");
        TextField port = new TextField(String.valueOf(initialPort > 0 ? initialPort : WindowsSqlSettings.DEFAULT_PORT));
        port.setPromptText("پورت SQL — پیش‌فرض ۱۴۳۳، یا پورت فورواردشده مثل ۵۰۵۰");
        port.setPrefColumnCount(8);
        TextField user = new TextField(draft.username);
        user.setPromptText("نام کاربری SQL");
        PasswordField password = new PasswordField();
        password.setPromptText("رمز SQL — فقط یک‌بار روی این کامپیوتر ذخیره می‌شود");
        if (!draft.password.isEmpty()) password.setText(draft.password);
        ChoiceBox<String> route = new ChoiceBox<>(
                FXCollections.observableArrayList("تشخیص خودکار", "سرور داخل شبکه (LAN)", "IP عمومی / خارج شبکه"));
        route.getSelectionModel().select(0);

        ObservableList<String> servers = FXCollections.observableArrayList();
        ObservableList<String> databases = FXCollections.observableArrayList();
        ListView<String> serverList = new ListView<>(servers);
        ListView<String> databaseList = new ListView<>(databases);
        serverList.setPrefHeight(132);
        databaseList.setPrefHeight(132);
        serverList.setPlaceholder(new Label("برای یافتن سرورهای شبکهٔ محلی، «جست‌وجوی سرور» را بزن."));
        databaseList.setPlaceholder(new Label("پس از واردکردن نام کاربری و رمز SQL، فهرست دیتابیس‌ها را دریافت کن."));

        Label status = new Label("آماده");
        status.getStyleClass().add("status-label");
        status.setWrapText(true);

        List<WindowsServerDiscovery.Candidate> foundServers = new ArrayList<>();
        List<String> foundDatabases = new ArrayList<>();
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "meelano-setup");
            t.setDaemon(true);
            return t;
        });

        Button scan = new Button("جست‌وجوی سرور در شبکهٔ محلی");
        Button list = new Button("اتصال و دریافت دیتابیس‌ها");
        Button save = new Button("آزمون و ذخیرهٔ اتصال");
        Button cancel = new Button("انصراف");

        serverList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            int index = serverList.getSelectionModel().getSelectedIndex();
            if (index < 0 || index >= foundServers.size()) return;
            WindowsServerDiscovery.Candidate candidate = foundServers.get(index);
            host.setText(candidate.host);
            port.setText(String.valueOf(candidate.port));
            route.getSelectionModel().select(WindowsSqlSettings.isPrivateAddress(candidate.host) ? 1 : 2);
            status.setText("سرور انتخاب شد؛ حالا فهرست دیتابیس‌ها را دریافت کن.");
        });
        databaseList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            String selected = databaseList.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.isBlank()) status.setText("دیتابیس «" + selected + "» انتخاب شد.");
        });

        scan.setOnAction(e -> {
            scan.setDisable(true);
            status.setText("در حال جست‌وجوی پورت‌های ۱۴۳۳ و ۵۰۵۰ در شبکهٔ محلی…");
            worker.execute(() -> {
                List<WindowsServerDiscovery.Candidate> result = WindowsServerDiscovery.discover(host.getText());
                Platform.runLater(() -> {
                    scan.setDisable(false);
                    foundServers.clear();
                    servers.clear();
                    foundServers.addAll(result);
                    for (WindowsServerDiscovery.Candidate candidate : result)
                        servers.add(candidate.label + ":" + candidate.port);
                    status.setText(result.isEmpty()
                            ? "سروری روی ۱۴۳۳ یا ۵۰۵۰ پیدا نشد؛ نام یا IP سرور را دستی وارد کن."
                            : result.size() + " سرور پاسخ‌دهنده پیدا شد؛ یکی را انتخاب کن.");
                });
            });
        });

        list.setOnAction(e -> {
            String typedHost = WindowsServerDiscovery.cleanHost(host.getText());
            int typedPort = WindowsServerDiscovery.resolvePort(port.getText(), host.getText());
            String typedUser = user.getText().trim();
            String typedPass = password.getText();
            if (!WindowsSqlSettings.validHost(typedHost) || typedUser.isEmpty() || typedPass.isEmpty()) {
                status.setText("نام یا IP سرور، نام کاربری SQL و رمز را وارد کن.");
                return;
            }
            list.setDisable(true);
            status.setText("در حال آزمون TCP و دریافت فهرست دیتابیس‌ها…");
            worker.execute(() -> {
                List<String> names = WindowsServerDiscovery.listDatabases(typedHost, typedPort, typedUser, typedPass);
                Platform.runLater(() -> {
                    list.setDisable(false);
                    foundDatabases.clear();
                    databases.clear();
                    foundDatabases.addAll(names);
                    databases.addAll(names);
                    if (!draft.database.isBlank() && names.contains(draft.database))
                        databaseList.getSelectionModel().select(draft.database);
                    status.setText(names.isEmpty()
                            ? "دیتابیسی خوانده نشد. اگر پورت ۱۴۳۳ بسته است، پورت فورواردشده (مثل ۵۰۵۰) را وارد کن و دوباره تلاش کن."
                            : names.size() + " دیتابیس پیدا شد؛ یکی را انتخاب کن.");
                });
            });
        });

        final WindowsSqlSettings.Endpoint[] saved = new WindowsSqlSettings.Endpoint[1];
        save.setOnAction(e -> {
            String typedHost = WindowsServerDiscovery.cleanHost(host.getText());
            int typedPort = WindowsServerDiscovery.resolvePort(port.getText(), host.getText());
            String typedUser = user.getText().trim();
            String typedPass = password.getText();
            String picked = databaseList.getSelectionModel().getSelectedItem();
            // Effectively final, so the worker and the UI callback can both capture it.
            final String database = picked == null || picked.isBlank() ? draft.database : picked;
            String chosenRoute = route.getSelectionModel().getSelectedIndex() == 1 ? "lan"
                    : route.getSelectionModel().getSelectedIndex() == 2 ? "wan" : "auto";
            if (!WindowsSqlSettings.validHost(typedHost) || !WindowsSqlSettings.validDatabase(database)
                    || typedUser.isEmpty() || typedPass.isEmpty()) {
                status.setText("سرور، پورت، دیتابیس، نام کاربری و رمز SQL را کامل وارد کن.");
                return;
            }
            if (!WindowsSqlSettings.isWindows()) {
                status.setText("ذخیرهٔ امن رمز فقط روی ویندوز انجام می‌شود؛ این برنامه را روی ویندوز اجرا کن.");
                return;
            }
            save.setDisable(true);
            status.setText("در حال آزمون دیتابیس انتخاب‌شده و ذخیرهٔ محلی…");
            worker.execute(() -> {
                boolean ok = false;
                try {
                    try (java.sql.Connection ignored = WindowsServerDiscovery.open(
                            typedHost, typedPort, database, typedUser, typedPass)) {
                        ok = true;
                    }
                    if (ok) ok = WindowsSqlSettings.saveVerified(typedHost, typedPort, database,
                            typedUser, typedPass, chosenRoute);
                } catch (Exception ignored) {
                    ok = false;
                }
                final boolean stored = ok;
                Platform.runLater(() -> {
                    save.setDisable(false);
                    if (!stored) {
                        status.setText("اتصال یا ذخیره انجام نشد. پورت را بررسی کن؛ اگر ۱۴۳۳ بسته است،"
                                + " پورت فورواردشده را وارد کن و فهرست دیتابیس‌ها را دوباره دریافت کن.");
                        return;
                    }
                    saved[0] = new WindowsSqlSettings.Endpoint(
                            "wan".equals(chosenRoute) ? "wan"
                                    : WindowsSqlSettings.isPrivateAddress(typedHost) ? "lan" : "wan",
                            typedHost, typedPort, database, typedUser, typedPass);
                    stage.close();
                });
            });
        });

        cancel.setOnAction(e -> stage.close());
        stage.setOnHidden(e -> worker.shutdownNow());

        VBox form = new VBox(10);
        form.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        form.setPadding(new Insets(20));
        form.getChildren().addAll(
                titled("سرور", "نام یا IP سرور SQL را وارد کن؛ پورت پیش‌فرض ۱۴۳۳ است و پورت فورواردشده (مثل ۵۰۵۰) قابل تنظیم است."),
                row(host, port),
                titled("نوع آدرس", "برای ذخیره در پروفایل درست (داخل شبکه / IP عمومی)."),
                route,
                titled("ورود SQL", "نام کاربری و رمز فقط یک‌بار وارد و در حساب همین کاربر ویندوز ذخیره می‌شود."),
                row(user, password),
                titled("یافتن سرور", "جست‌وجوی محلی روی پورت‌های ۱۴۳۳ و ۵۰۵۰."),
                scan,
                serverList,
                titled("دیتابیس", "فهرست دیتابیس‌های سرور را دریافت و یکی را انتخاب کن."),
                list,
                databaseList,
                status);

        HBox buttons = new HBox(10, save, cancel);
        buttons.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(save, Priority.NEVER);
        form.getChildren().add(buttons);

        Scene scene = new Scene(form, 620, 760);
        try {
            scene.getStylesheets().add(ConnectionSetupDialog.class.getResource("/styles.css").toExternalForm());
        } catch (Exception ignored) { }
        stage.setScene(scene);
        stage.showAndWait();
        worker.shutdownNow();
        return Optional.ofNullable(saved[0]);
    }

    private static Label titled(String title, String caption) {
        Label label = new Label(title + " — " + caption);
        label.setWrapText(true);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static HBox row(javafx.scene.Node first, javafx.scene.Node second) {
        HBox box = new HBox(10, first, second);
        box.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(first, Priority.ALWAYS);
        return box;
    }
}
