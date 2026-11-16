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
 *
 * <p>Part of: Application shell. This is the JavaFX entry point and the only
 * class that owns the {@link javafx.stage.Stage} and the {@link javafx.scene.Scene}.<br>
 * Used by: the JavaFX runtime calls {@link #start(Stage)}; the class itself
 * creates {@code LoginView} and {@code MainView} from the ui package.<br>
 * Key rules: the database must connect before any screen is built, and the
 * window is undecorated, so {@code WindowFrame} supplies the title bar and the
 * drag/resize behaviour.</p>
 */
public class App extends Application {

    // ============================================================
    // PART: Window state
    // ============================================================

    /** The one window of the application; never replaced, only re-filled. */
    private Stage stage;
    /** The scene attached to the stage. Its root is swapped on sign-in/out. */
    private Scene scene;
    /** Custom chrome that draws the title bar and hosts the current screen. */
    private WindowFrame frame;

    // ============================================================
    // PART: Startup
    // ============================================================

    /**
     * WHAT: Builds the window, connects to MySQL, then shows the sign-in screen.
     * WHY: Nothing else may run before the database is ready, because every view
     * loads its data from the DAOs as soon as it is constructed.
     * CALLED BY: the JavaFX runtime, once, on the JavaFX application thread.
     */
    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        // Load the bundled fonts before any label is created, otherwise text
        // would fall back to the platform default font.
        Fonts.load();

        // Step 1: open the database. Db.init() also runs Schema.migrate(), so a
        // database created by an older release is upgraded here.
        try {
            Db.init();
        } catch (DaoException ex) {
            // Without a database there is nothing to show, so report the reason
            // and shut the process down instead of opening a broken window.
            Dialogs.error(null, "Database unavailable", ex.getMessage());
            Platform.exit();
            return;
        }

        // Step 2: remove the OS title bar; WindowFrame draws its own.
        stage.initStyle(StageStyle.UNDECORATED);

        // Step 3: build the shell and put the sign-in screen inside it. The
        // method reference this::onSignedIn is the callback LoginView fires
        // after a successful login.
        frame = new WindowFrame(stage);
        frame.setContent(new LoginView(stage, this::onSignedIn));

        // Step 4: create the scene, let the frame hook its drag/resize
        // handlers into it, then attach the theme.
        scene = new Scene(frame, 1240, 800);
        frame.install(scene);
        applyStylesheet(scene);

        // Step 5: final window setup, then show it.
        stage.setScene(scene);
        stage.setTitle("Balagtas DEIHS Library Management System");
        stage.setMinWidth(1080);
        stage.setMinHeight(700);
        loadIcon();
        stage.centerOnScreen();
        stage.show();
    }

    // ============================================================
    // PART: Screen switching
    // ============================================================

    /**
     * WHAT: Replaces the sign-in screen with the signed-in shell for this user.
     * WHY: The stage and scene are reused; only the content inside the frame
     * changes, so the window does not flicker or lose its size and position.
     * CALLED BY: LoginView, through the callback passed in {@link #start}.
     */
    private void onSignedIn(User user) {
        // MainView receives this::showLogin so its "Sign out" button can come
        // back here.
        MainView main = new MainView(stage, user, this::showLogin);
        frame.setContent(main);
    }

    /**
     * WHAT: Throws away the signed-in shell and shows a brand-new sign-in screen.
     * WHY: Building a fresh LoginView clears the previous username, password
     * field and error text, so the next person does not see the last user's data.
     * CALLED BY: MainView, through the callback passed in {@link #onSignedIn}.
     */
    private void showLogin() {
        frame.setContent(new LoginView(stage, this::onSignedIn));
    }

    // ============================================================
    // PART: Window chrome
    // ============================================================

    /**
     * WHAT: Attaches /css/app.css to the scene.
     * WHY: All colours, fonts and pill styles come from that file; without it
     * the app still runs but looks unstyled.
     * CALLED BY: {@link #start}.
     */
    private void applyStylesheet(Scene target) {
        try {
            String css = App.class.getResource("/css/app.css").toExternalForm();
            target.getStylesheets().add(css);
        } catch (Exception ignored) {
            // The app still works without the theme.
        }
    }

    /**
     * WHAT: Adds the bundled PNG icons to the stage.
     * WHY: A window with no icon shows the generic Java icon in the task bar.
     * CALLED BY: {@link #start}.
     */
    private void loadIcon() {
        // Several sizes so the window manager can pick a crisp one for the
        // title bar, task bar and Alt-Tab switcher.
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        for (int size : sizes) {
            // Each size is its own file; one missing size must not stop the rest.
            try (InputStream in = App.class.getResourceAsStream("/images/app-icon-" + size + ".png")) {
                if (in != null) {
                    stage.getIcons().add(new Image(in));
                }
            } catch (Exception ignored) {
                // A missing icon is not fatal.
            }
        }
    }

    /** WHAT: Standard JVM entry point; hands control to JavaFX. CALLED BY: the JVM. */
    public static void main(String[] args) {
        launch(args);
    }
}
