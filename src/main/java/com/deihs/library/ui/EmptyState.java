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
 *
 * <p>Part of: the shared widget layer. Every screen that can run out of rows
 * reuses this instead of inventing its own blank message.</p>
 * <p>Used by: the catalogue, circulation, loans, users, collection and
 * dashboard screens, either directly or through {@link #host(EmptyState)}.</p>
 * <p>Key rules: the optional action is added last, and {@code host()} is the
 * form to use when the state is a table placeholder.</p>
 */
public final class EmptyState extends VBox {

    // WHAT: builds the icon + title + message stack.
    // WHY: every empty table needs the same reassuring layout, so the pieces
    // are assembled once here. CALLED BY: the views when their data comes back
    // empty; action() adds the optional button afterwards.
    public EmptyState(String icon, String title, String message) {
        // Centre the whole block inside whatever space the caller gives it.
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);
        setSpacing(6);
        setPadding(new Insets(26));

        // Step 1: the large decorative icon.
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("empty-icon");

        // Step 2: a bold one-line headline naming the situation.
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-title");
        titleLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));

        // Step 3: the explanation, wrapped and width-capped so it stays a
        // readable paragraph rather than one long line.
        Label textLabel = new Label(message);
        textLabel.getStyleClass().add("empty-text");
        textLabel.setWrapText(true);
        textLabel.setMaxWidth(430);
        textLabel.setAlignment(Pos.CENTER);
        textLabel.setFont(Font.font("SansSerif", 11.5));

        getChildren().addAll(iconLabel, titleLabel, textLabel);
    }

    /**
     * Adds the single action that resolves the empty state.
     *
     * <p>WHAT: appends one button below the message. WHY: a way out matters
     * more than the message itself. CALLED BY: views that can offer a fix, such
     * as "Clear filters" or "Add a book".</p>
     */
    public EmptyState action(String text, Runnable runnable) {
        // A ghost button keeps the empty state visually quiet; the margin
        // separates it from the text above.
        Button button = new Button(text);
        button.getStyleClass().add("ghost-btn");
        button.setPrefHeight(34);
        button.setOnAction(e -> runnable.run());
        VBox.setMargin(button, new Insets(8, 0, 0, 0));
        getChildren().add(button);
        return this;
    }

    /**
     * Wraps the state so it can be used directly as a table placeholder.
     *
     * <p>WHAT: centres the state inside a padded pane. WHY: tables and grids
     * expect a Node placeholder, and a StackPane is what actually fills the
     * available space. CALLED BY: the views when they set a placeholder.</p>
     */
    public static StackPane host(EmptyState state) {
        StackPane pane = new StackPane(state);
        pane.setPadding(new Insets(18));
        return pane;
    }
}
