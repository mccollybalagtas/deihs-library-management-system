package com.deihs.library.ui;

import javafx.scene.text.Font;

import java.io.InputStream;

/**
 * Loads the bundled display typeface.
 *
 * <p>Playfair Display ships with the application so the branding looks the same
 * on every machine instead of depending on whatever serif happens to be
 * installed.</p>
 *
 * <p>Part of: the UI foundation, loaded once before any screen is built.</p>
 * <p>Used by: {@link App} and {@link LoginView} to register the faces, and
 * {@link MainView} / {@link LoginView} through {@link #display(double,
 * javafx.scene.text.FontWeight)} for headings.</p>
 * <p>Key rules: loading is guarded so the faces are registered at most once;
 * if a file is missing the system serif is used instead, and the app still
 * runs.</p>
 */
public final class Fonts {

    /** Family name of the bundled display serif. */
    public static final String DISPLAY = "Playfair Display";

    // Set the first time load() runs. The fonts are registered globally with
    // JavaFX, so a second pass would be wasted work.
    private static boolean loaded;

    private Fonts() {
    }

    // WHAT: registers the three bundled font faces with JavaFX.
    // WHY: Font.loadFont only makes a family available for this process, and it
    // must happen before any label asks for "Playfair Display".
    // CALLED BY: App at start-up and LoginView when it is shown directly.
    /** Loads the bundled font faces once; safe to call repeatedly. */
    public static synchronized void load() {
        // Step 1: guard against repeat work. The flag is set before loading so
        // a failure cannot cause an endless retry loop.
        if (loaded) {
            return;
        }
        loaded = true;

        // Step 2: the regular, semibold and bold weights are enough for the
        // headings in the app.
        String[] faces = {
                "/fonts/PlayfairDisplay-Regular.ttf",
                "/fonts/PlayfairDisplay-SemiBold.ttf",
                "/fonts/PlayfairDisplay-Bold.ttf"
        };
        // Step 3: each face is optional; a missing resource simply leaves that
        // weight to the fallback font.
        for (String face : faces) {
            try (InputStream in = Fonts.class.getResourceAsStream(face)) {
                if (in != null) {
                    Font.loadFont(in, 12);
                }
            } catch (Exception ignored) {
                // Fall back to the system serif if a face cannot be read.
            }
        }
    }

    /**
     * Returns the display serif at the requested size, falling back to a
     * system serif when the bundled face is unavailable.
     */
    public static Font display(double size, javafx.scene.text.FontWeight weight) {
        // Make sure the family is registered before asking for it; this is a
        // cheap no-op after the first call.
        load();
        return Font.font(DISPLAY, weight, size);
    }
}
