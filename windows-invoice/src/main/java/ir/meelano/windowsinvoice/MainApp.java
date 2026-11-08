package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.data.GatewayFactory;
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

/** JavaFX entry point for the separate Windows invoice client. */
public final class MainApp extends Application {
    private InvoiceDeskView view;

    public static void main(String[] args) {
        launch(args);
    }

    @Override public void start(Stage stage) {
        String startupWarning = "";
        try {
            loadFonts();
            view = new InvoiceDeskView(GatewayFactory.create(), "");
        } catch (Exception ex) {
            // Do not echo connection details or secrets. A partial SQL config is never treated as a live connection.
            startupWarning = ex.getMessage() == null ? "تنظیم اتصال محلی کامل نیست؛ برنامه در حالت امن متوقف شد." : ex.getMessage();
            view = new InvoiceDeskView(new DemoAtiranGateway(), startupWarning);
        }

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double sceneWidth = Math.min(1450, Math.max(760, screen.getWidth() - 48));
        double sceneHeight = Math.min(940, Math.max(560, screen.getHeight() - 64));
        Scene scene = new Scene(view, sceneWidth, sceneHeight);
        scene.getStylesheets().add(MainApp.class.getResource("/styles.css").toExternalForm());
        scene.getRoot().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        view.installKeyboardShortcuts(scene);
        stage.setTitle("MEELANO · ثبت فاکتور فروش");
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
        stage.show();
        Platform.runLater(view::focusInitialField);
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
