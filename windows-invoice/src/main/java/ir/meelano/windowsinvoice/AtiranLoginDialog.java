package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.data.AtiranLogin;
import ir.meelano.windowsinvoice.data.WindowsSqlSettings;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Atiran account login for the desktop client. The signed-in role decides which Manager
 * menus are visible, exactly as on the phone. Read-only against Atiran.
 */
public final class AtiranLoginDialog {
    private AtiranLoginDialog() { }

    public static Optional<AtiranLogin.Session> show(Window owner, List<WindowsSqlSettings.Endpoint> endpoints) {
        Stage stage = new Stage();
        stage.initModality(owner == null ? Modality.APPLICATION_MODAL : Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("ورود با حساب آتیران");

        TextField user = new TextField(WindowsSqlSettings.atiranUser());
        user.setPromptText("نام کاربری آتیران");
        PasswordField password = new PasswordField();
        password.setPromptText("رمز آتیران");
        Label status = new Label("با همان حسابی وارد شو که در آتیران تعریف شده است؛ منوها بر اساس نقشت نمایش داده می‌شوند.");
        status.setWrapText(true);
        Button login = new Button("ورود");
        Button cancel = new Button("بعداً");
        login.setDefaultButton(true);

        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "meelano-atiran-login");
            t.setDaemon(true);
            return t;
        });

        final AtiranLogin.Session[] result = new AtiranLogin.Session[1];
        Runnable attempt = () -> {
            String typedUser = user.getText().trim();
            String typedPass = password.getText();
            if (typedUser.isEmpty() || typedPass.isEmpty()) {
                status.setText("نام کاربری و رمز آتیران را وارد کن.");
                return;
            }
            if (endpoints == null || endpoints.isEmpty()) {
                status.setText("اتصال SQL ذخیره‌شده کامل نیست؛ ابتدا سرور و دیتابیس را تنظیم کن.");
                return;
            }
            login.setDisable(true);
            status.setText("در حال ورود و تعیین نقش…");
            worker.execute(() -> {
                AtiranLogin.Session session = null;
                String route = "";
                String failure = "";
                for (WindowsSqlSettings.Endpoint endpoint : endpoints) {
                    try {
                        session = AtiranLogin.login(endpoint, typedUser, typedPass);
                        route = endpoint.route;
                        break;
                    } catch (Exception ex) {
                        failure = ex.getMessage() == null ? "" : ex.getMessage();
                    }
                }
                final AtiranLogin.Session signed = session;
                final String signedRoute = route;
                final String message = failure;
                Platform.runLater(() -> {
                    login.setDisable(false);
                    if (signed == null) {
                        status.setText(message.isBlank()
                                ? "ورود انجام نشد؛ نام کاربری، رمز و دسترسی این حساب به دیتابیس انتخاب‌شده را بررسی کن."
                                : message);
                        return;
                    }
                    WindowsSqlSettings.rememberAtiranUser(signed.username);
                    WindowsSqlSettings.rememberSuccessfulRoute(signedRoute);
                    result[0] = signed;
                    stage.close();
                });
            });
        };
        login.setOnAction(e -> attempt.run());
        cancel.setOnAction(e -> stage.close());
        stage.setOnHidden(e -> worker.shutdownNow());

        VBox form = new VBox(12);
        form.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        form.setPadding(new Insets(22));
        form.getChildren().addAll(new Label("ورود آتیران"), user, password, status);
        HBox buttons = new HBox(10, login, cancel);
        buttons.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(login, Priority.NEVER);
        form.getChildren().add(buttons);

        Scene scene = new Scene(form, 460, 300);
        try {
            scene.getStylesheets().add(AtiranLoginDialog.class.getResource("/styles.css").toExternalForm());
        } catch (Exception ignored) { }
        stage.setScene(scene);
        stage.showAndWait();
        worker.shutdownNow();
        return Optional.ofNullable(result[0]);
    }
}
