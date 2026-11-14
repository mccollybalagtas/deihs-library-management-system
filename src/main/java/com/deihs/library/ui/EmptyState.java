package com.deihs.library.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * A friendly, actionable placeholder for an empty table or grid.
 *
 * <p>A bare "nothing here" line leaves people stuck. This says what happened
 * and offers the one button that fixes it.</p>
 */
public final class EmptyState extends VBox {

    public EmptyState(String icon, String title, String message) {
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);
        setSpacing(6);
        setPadding(new Insets(26));

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("empty-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-title");
        titleLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));

        Label textLabel = new Label(message);
        textLabel.getStyleClass().add("empty-text");
        textLabel.setWrapText(true);
        textLabel.setMaxWidth(430);
        textLabel.setAlignment(Pos.CENTER);
        textLabel.setFont(Font.font("SansSerif", 11.5));

        getChildren().addAll(iconLabel, titleLabel, textLabel);
    }

    /** Adds the single action that resolves the empty state. */
    public EmptyState action(String text, Runnable runnable) {
        Button button = new Button(text);
        button.getStyleClass().add("ghost-btn");
        button.setPrefHeight(34);
        button.setOnAction(e -> runnable.run());
        VBox.setMargin(button, new Insets(8, 0, 0, 0));
        getChildren().add(button);
        return this;
    }

    /** Wraps the state so it can be used directly as a table placeholder. */
    public static StackPane host(EmptyState state) {
        StackPane pane = new StackPane(state);
        pane.setPadding(new Insets(18));
        return pane;
    }
}
