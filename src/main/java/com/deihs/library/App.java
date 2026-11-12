package com.deihs.library;

import com.deihs.library.dao.DaoException;
import com.deihs.library.db.Db;
import com.deihs.library.model.User;
import com.deihs.library.ui.Dialogs;
import com.deihs.library.ui.Fonts;
import com.deihs.library.ui.LoginView;
import com.deihs.library.ui.MainView;
import com.deihs.library.ui.WindowFrame;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.InputStream;

/**
 * Entry point for the Balagtas DEIHS Library Management System.
 *
 * <p>The window starts on the sign-in screen. Once the credentials are
 * verified the scene root is swapped for the signed-in shell, and logging out
 * swaps it back.</p>
 */
public class App extends Application {

    private Stage stage;
    private Scene scene;
    private WindowFrame frame;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        Fonts.load();

        try {
            Db.init();
        } catch (DaoException ex) {
            Dialogs.error(null, "Database unavailable", ex.getMessage());
            Platform.exit();
            return;
        }

        stage.initStyle(StageStyle.UNDECORATED);

        frame = new WindowFrame(stage);
        frame.setContent(new LoginView(stage, this::onSignedIn));

        scene = new Scene(frame, 1240, 800);
        frame.install(scene);
        applyStylesheet(scene);

        stage.setScene(scene);
        stage.setTitle("Balagtas DEIHS Library Management System");
        stage.setMinWidth(1080);
        stage.setMinHeight(700);
        loadIcon();
        stage.centerOnScreen();
        stage.show();
    }

    private void onSignedIn(User user) {
        MainView main = new MainView(stage, user, this::showLogin);
        frame.setContent(main);
    }

    private void showLogin() {
        frame.setContent(new LoginView(stage, this::onSignedIn));
    }

    private void applyStylesheet(Scene target) {
        try {
            String css = App.class.getResource("/css/app.css").toExternalForm();
            target.getStylesheets().add(css);
        } catch (Exception ignored) {
            // The app still works without the theme.
        }
    }

    private void loadIcon() {
        // Several sizes so the window manager can pick a crisp one for the
        // title bar, task bar and Alt-Tab switcher.
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        for (int size : sizes) {
            try (InputStream in = App.class.getResourceAsStream("/images/app-icon-" + size + ".png")) {
                if (in != null) {
                    stage.getIcons().add(new Image(in));
                }
            } catch (Exception ignored) {
                // A missing icon is not fatal.
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
