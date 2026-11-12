package com.deihs.library.ui;

import javafx.scene.text.Font;

import java.io.InputStream;

/**
 * Loads the bundled display typeface.
 *
 * <p>Playfair Display ships with the application so the branding looks the same
 * on every machine instead of depending on whatever serif happens to be
 * installed.</p>
 */
public final class Fonts {

    /** Family name of the bundled display serif. */
    public static final String DISPLAY = "Playfair Display";

    private static boolean loaded;

    private Fonts() {
    }

    /** Loads the bundled font faces once; safe to call repeatedly. */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        String[] faces = {
                "/fonts/PlayfairDisplay-Regular.ttf",
                "/fonts/PlayfairDisplay-SemiBold.ttf",
                "/fonts/PlayfairDisplay-Bold.ttf"
        };
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
        load();
        return Font.font(DISPLAY, weight, size);
    }
}
