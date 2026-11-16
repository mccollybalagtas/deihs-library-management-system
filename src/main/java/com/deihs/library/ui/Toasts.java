package com.deihs.library.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Lightweight, non-blocking feedback shown in the bottom-right corner of the
 * shell.
 *
 * <p>Successful actions (issued, returned, saved) use a toast so the person can
 * keep working. Errors and destructive confirmations still use
 * {@link Dialogs}, because those need an explicit acknowledgement.</p>
 *
 * <p>Part of: the shell's feedback layer. It owns a single vertical stack and
 * the animation lifecycle for every message.</p>
 * <p>Used by: {@link MainView}, which creates one instance over its toast
 * layer and exposes it to the screens.</p>
 * <p>Key rules: at most {@code MAX_VISIBLE} toasts exist at once, and each one
 * removes itself when its animation finishes.</p>
 */
public final class Toasts {

    // The cap keeps a burst of messages from covering the page; older toasts
    // are dropped first.
    private static final int MAX_VISIBLE = 3;

    // Newest toast is added at the end, which is the bottom of the column.
    private final VBox stack = new VBox(8);

    // ====
    // PART: Setting up the toast layer
    // ====

    /**
     * @param layer an empty, transparent StackPane that sits above the page.
     *
     * <p>WHAT: stores the vertical stack and installs it in the given layer.
     * WHY: toasts must float over the content without stealing clicks, so both
     * the stack and the layer are made mouse-transparent. CALLED BY: the shell
     * (MainView) once, at start-up.</p>
     */
    public Toasts(StackPane layer) {
        // The stack itself never takes clicks and hugs the bottom-right corner.
        stack.setAlignment(Pos.BOTTOM_RIGHT);
        stack.setPickOnBounds(false);
        stack.setMouseTransparent(true);

        // The layer is only a positioning surface: same corner, a little
        // padding, and no mouse capture so the page underneath still works.
        layer.setAlignment(Pos.BOTTOM_RIGHT);
        layer.setPadding(new Insets(0, 22, 18, 0));
        layer.setPickOnBounds(false);
        layer.setMouseTransparent(true);
        layer.getChildren().add(stack);
    }

    // ====
    // PART: Public entry points
    // ====

    // The three helpers differ only in colour and icon; the real work happens
    // in show().

    public void success(String message) {
        show(message, "toast-success", "\u2713");
    }

    public void info(String message) {
        show(message, "toast-info", "i");
    }

    public void error(String message) {
        show(message, "toast-error", "!");
    }

    // ====
    // PART: Showing and expiring one toast
    // ====

    // WHAT: builds one toast, trims the stack, and animates it in and out.
    // WHY: feedback should appear, be readable for a moment, then leave on its
    // own without blocking the user. CALLED BY: success(), info() and error().
    private void show(String message, String styleClass, String iconText) {
        // An empty message would leave a blank pill; ignore it.
        if (message == null || message.isBlank()) {
            return;
        }

        // Step 1: the icon and the wrapped text. The text is width-capped so a
        // long message becomes a paragraph rather than a wide banner.
        Label icon = new Label(iconText);
        icon.getStyleClass().add("toast-icon");

        Label text = new Label(message);
        text.getStyleClass().add("toast-text");
        text.setWrapText(true);
        text.setMaxWidth(320);

        // Step 2: assemble the pill and attach the variant class (success,
        // info or error) that colours it.
        HBox toast = new HBox(10, icon, text);
        toast.getStyleClass().addAll("toast", styleClass);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setMaxWidth(370);
        toast.setMouseTransparent(true);

        // Step 3: add it and drop the oldest if the screen is already busy.
        stack.getChildren().add(toast);
        while (stack.getChildren().size() > MAX_VISIBLE) {
            stack.getChildren().remove(0);
        }

        // Step 4: fade in, hold, fade out. The hold is what makes the message
        // actually readable; the fades keep it from popping.
        FadeTransition in = new FadeTransition(Duration.millis(180), toast);
        in.setFromValue(0);
        in.setToValue(1);

        PauseTransition stay = new PauseTransition(Duration.millis(2600));

        FadeTransition out = new FadeTransition(Duration.millis(260), toast);
        out.setFromValue(1);
        out.setToValue(0);

        // Step 5: run the three phases in order and remove the node when the
        // animation finishes, so the stack does not grow forever.
        SequentialTransition sequence = new SequentialTransition(in, stay, out);
        sequence.setOnFinished(e -> stack.getChildren().remove(toast));
        sequence.play();
    }
}
