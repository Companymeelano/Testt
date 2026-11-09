package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.data.AtiranLogin;
import ir.meelano.windowsinvoice.data.GatewayFactory;
import ir.meelano.windowsinvoice.data.WindowsSqlSettings;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * JavaFX entry point for the separate Windows invoice client.
 *
 * Startup order: show the desk immediately, then run the one-time SQL setup when no endpoint is
 * saved (server name/IP, port 1433 or a forwarded port, database picker, DPAPI-stored SQL login),
 * then the Atiran login whose role decides the visible Manager menus. The saved profile makes the
 * next launch connect automatically without asking for the SQL password again.
 */
public final class MainApp extends Application {
    private InvoiceDeskView view;
    private Stage primaryStage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override public void start(Stage stage) {
        primaryStage = stage;
        loadFonts();

        String startupWarning = "";
        AtiranGateway initialGateway;
        try {
            initialGateway = GatewayFactory.create();
        } catch (Exception ex) {
            // Do not echo connection details or secrets. A partial SQL config is never treated as a live connection.
            startupWarning = ex.getMessage() == null
                    ? "تنظیم اتصال محلی کامل نیست؛ برنامه در حالت امن متوقف شد." : ex.getMessage();
            initialGateway = new DemoAtiranGateway();
        }
        install(stage, initialGateway, null, startupWarning);
        stage.show();
        Platform.runLater(() -> {
            if (view != null) view.focusInitialField();
            runConnectionSetupAndLogin(stage);
        });
    }

    /** One-time endpoint setup followed by the Atiran login; both are skippable into demo mode. */
    private void runConnectionSetupAndLogin(Stage stage) {
        List<WindowsSqlSettings.Endpoint> endpoints = WindowsSqlSettings.candidates();
        if (endpoints.isEmpty()) {
            Optional<WindowsSqlSettings.Endpoint> saved = ConnectionSetupDialog.show(null);
            endpoints = WindowsSqlSettings.candidates();
            if (endpoints.isEmpty()) {
                String reason = saved.isPresent()
                        ? "اتصال ذخیره شد اما دوباره خوانده نشد؛ برنامه را دوباره باز کن."
                        : "اتصال SQL تنظیم نشد؛ برنامه در حالت پیش‌نمایش محلی است.";
                install(stage, new DemoAtiranGateway(), null, reason);
                return;
            }
        } else if (WindowsSqlSettings.credentialMissing()) {
            // Endpoint metadata survived but the DPAPI credential did not (new Windows account).
            ConnectionSetupDialog.show(null);
            endpoints = WindowsSqlSettings.candidates();
            if (endpoints.isEmpty()) {
                install(stage, new DemoAtiranGateway(), null,
                        "رمز SQL ذخیره‌شده در این حساب ویندوز قابل خواندن نیست؛ اتصال را دوباره تنظیم کن.");
                return;
            }
        }

        Optional<AtiranLogin.Session> signed = AtiranLoginDialog.show(null, endpoints);
        if (signed.isEmpty()) {
            install(stage, GatewayFactory.create(), null,
                    "بدون ورود آتیران، دادهٔ زنده و منوهای نقش فعال نیستند؛ از نوار کنار «تغییر کاربر آتیران» وارد شو.");
            return;
        }
        AtiranLogin.Session session = signed.get();
        try {
            install(stage, GatewayFactory.create(session), session, "");
        } catch (Exception ex) {
            install(stage, GatewayFactory.create(), session,
                    ex.getMessage() == null ? "اتصال زنده برقرار نشد." : ex.getMessage());
        }
    }

    private void install(Stage stage, AtiranGateway gateway, AtiranLogin.Session session, String warning) {
        closeView();
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double sceneWidth = Math.min(1450, Math.max(760, screen.getWidth() - 48));
        double sceneHeight = Math.min(940, Math.max(560, screen.getHeight() - 64));
        view = new InvoiceDeskView(gateway, warning, session);
        Scene scene = new Scene(view, sceneWidth, sceneHeight);
        scene.getStylesheets().add(MainApp.class.getResource("/styles.css").toExternalForm());
        scene.getRoot().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        view.installKeyboardShortcuts(scene);
        stage.setTitle(session == null ? "MEELANO · ثبت فاکتور فروش"
                : "MEELANO · " + session.title());
        try (InputStream icon = MainApp.class.getResourceAsStream("/icons/meelano-invoice.png")) {
            if (icon != null) stage.getIcons().add(new javafx.scene.image.Image(icon));
        } catch (Exception ignored) { }
        stage.setMinWidth(Math.min(1100, sceneWidth));
        stage.setMinHeight(Math.min(660, sceneHeight));
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (view != null && view.isSubmitting()) {
                e.consume();
                Alert pending = new Alert(Alert.AlertType.WARNING);
                pending.setTitle("ثبت فاکتور در حال انجام است");
                pending.setHeaderText("تا دریافت نتیجه، پنجره را نبندید.");
                pending.setContentText("بستن اجباری در این لحظه می‌تواند وضعیت ثبت در آتیران را نامشخص کند.");
                pending.showAndWait();
            } else if (view != null && view.hasInvoiceDraftChanges()) {
                e.consume();
                Alert discard = new Alert(Alert.AlertType.CONFIRMATION);
                discard.setTitle("پیش‌نویس ثبت‌نشده");
                discard.setHeaderText("این فاکتور هنوز در آتیران ثبت نشده است.");
                discard.setContentText("تغییرات این فاکتور ذخیرهٔ محلی ندارند و با بستن پنجره از بین می‌روند.");
                ButtonType stay = new ButtonType("ادامهٔ ثبت", ButtonBar.ButtonData.CANCEL_CLOSE);
                ButtonType closeAnyway = new ButtonType("بستن و دورریختن پیش‌نویس", ButtonBar.ButtonData.OK_DONE);
                discard.getButtonTypes().setAll(stay, closeAnyway);
                discard.showAndWait().filter(closeAnyway::equals).ifPresent(button -> {
                    closeView();
                    stage.hide();
                    Platform.exit();
                });
            } else {
                closeView();
            }
        });
    }

    private static void loadFonts() {
        loadFont("/fonts/Vazirmatn-Regular.ttf");
        loadFont("/fonts/Vazirmatn-Bold.ttf");
    }

    private static void loadFont(String resource) {
        try (InputStream in = MainApp.class.getResourceAsStream(resource)) {
            if (in != null) Font.loadFont(in, 13);
        } catch (Exception ignored) { }
    }

    @Override public void stop() { closeView(); }

    private void closeView() {
        if (view != null) {
            view.close();
            view = null;
        }
    }
}
