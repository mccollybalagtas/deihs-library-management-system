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
 */
public final class Toasts {

    private static final int MAX_VISIBLE = 3;

    private final VBox stack = new VBox(8);

    /** @param layer an empty, transparent StackPane that sits above the page. */
    public Toasts(StackPane layer) {
        stack.setAlignment(Pos.BOTTOM_RIGHT);
        stack.setPickOnBounds(false);
        stack.setMouseTransparent(true);

        layer.setAlignment(Pos.BOTTOM_RIGHT);
        layer.setPadding(new Insets(0, 22, 18, 0));
        layer.setPickOnBounds(false);
        layer.setMouseTransparent(true);
        layer.getChildren().add(stack);
    }

    public void success(String message) {
        show(message, "toast-success", "\u2713");
    }

    public void info(String message) {
        show(message, "toast-info", "i");
    }

    public void error(String message) {
        show(message, "toast-error", "!");
    }

    private void show(String message, String styleClass, String iconText) {
        if (message == null || message.isBlank()) {
            return;
        }

        Label icon = new Label(iconText);
        icon.getStyleClass().add("toast-icon");

        Label text = new Label(message);
        text.getStyleClass().add("toast-text");
        text.setWrapText(true);
        text.setMaxWidth(320);

        HBox toast = new HBox(10, icon, text);
        toast.getStyleClass().addAll("toast", styleClass);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setMaxWidth(370);
        toast.setMouseTransparent(true);

        stack.getChildren().add(toast);
        while (stack.getChildren().size() > MAX_VISIBLE) {
            stack.getChildren().remove(0);
        }

        FadeTransition in = new FadeTransition(Duration.millis(180), toast);
        in.setFromValue(0);
        in.setToValue(1);

        PauseTransition stay = new PauseTransition(Duration.millis(2600));

        FadeTransition out = new FadeTransition(Duration.millis(260), toast);
        out.setFromValue(1);
        out.setToValue(0);

        SequentialTransition sequence = new SequentialTransition(in, stay, out);
        sequence.setOnFinished(e -> stack.getChildren().remove(toast));
        sequence.play();
    }
}
