package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.data.GatewayFactory;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.text.Font;
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

        Scene scene = new Scene(view, 1450, 940);
        scene.getStylesheets().add(MainApp.class.getResource("/styles.css").toExternalForm());
        scene.getRoot().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        stage.setTitle("MEELANO · ثبت فاکتور فروش");
        try (InputStream icon = MainApp.class.getResourceAsStream("/icons/meelano-invoice.png")) {
            if (icon != null) stage.getIcons().add(new javafx.scene.image.Image(icon));
        } catch (Exception ignored) { }
        stage.setMinWidth(1160);
        stage.setMinHeight(760);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (view != null && view.isSubmitting()) {
                e.consume();
                Alert pending = new Alert(Alert.AlertType.WARNING);
                pending.setTitle("ثبت فاکتور در حال انجام است");
                pending.setHeaderText("تا دریافت نتیجه، پنجره را نبندید.");
                pending.setContentText("بستن اجباری در این لحظه می‌تواند وضعیت ثبت در آتیران را نامشخص کند.");
                pending.showAndWait();
            } else {
                closeView();
            }
        });
        stage.show();
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
