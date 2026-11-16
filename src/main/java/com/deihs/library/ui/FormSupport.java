package com.deihs.library.ui;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Shared behaviour for the data-entry dialogs.
 *
 * <p>Three things make a form feel finished: the field you are meant to fill is
 * already focused, a mistake is pointed out next to the field that caused it
 * instead of in one distant label, and closing the dialog by accident asks
 * before it throws the typing away.</p>
 */
final class FormSupport {

    private FormSupport() {
    }

    /** Paints a control red, or clears the mark. Only the border changes. */
    static void markInvalid(Node control, boolean invalid) {
        if (control == null) {
            return;
        }
        if (invalid) {
            if (!control.getStyleClass().contains("field-error")) {
                control.getStyleClass().add("field-error");
            }
        } else {
            control.getStyleClass().remove("field-error");
        }
    }

    /** Focuses a control once the dialog is on screen, selecting its text. */
    static void focus(Node node) {
        if (node == null) {
            return;
        }
        Platform.runLater(() -> {
            node.requestFocus();
            if (node instanceof TextInputControl text) {
                text.selectAll();
            }
        });
    }

    /**
     * One text field plus the small message under it.
     *
     * <p>Problems stay hidden until the field is left, so a freshly opened form
     * is not covered in red before the user has typed anything.</p>
     */
    static final class TextRule {

        private final TextInputControl field;
        private final Label hint;
        private final Supplier<String> rule;
        private final String idleText;

        private boolean touched;

        TextRule(TextInputControl field, Label hint, Supplier<String> rule) {
            this(field, hint, rule, null);
        }

        TextRule(TextInputControl field, Label hint, Supplier<String> rule, String idleText) {
            this.field = field;
            this.hint = hint;
            this.rule = rule;
            this.idleText = idleText;

            field.focusedProperty().addListener((o, was, focused) -> {
                if (!focused) {
                    touched = true;
                    refresh();
                }
            });
            field.textProperty().addListener((o, before, after) -> {
                if (touched) {
                    refresh();
                }
            });
            refresh();
        }

        /** The user-facing problem with the current value, or {@code null}. */
        String problem() {
            return rule.get();
        }

        /** Reveals this field's problem even if it was never left. */
        void touch() {
            touched = true;
            refresh();
        }

        /** Re-checks after a sibling field changed, without revealing anything new. */
        void recheck() {
            if (touched) {
                refresh();
            }
        }

        private void refresh() {
            String problem = touched ? rule.get() : null;
            markInvalid(field, problem != null);
            String text = problem != null ? problem : (idleText == null ? "" : idleText);
            hint.setText(text);
            hint.getStyleClass().setAll(problem != null ? "field-error" : "hint-label");
            boolean show = !text.isEmpty();
            hint.setVisible(show);
            hint.setManaged(show);
        }
    }

    /**
     * Watches a dialog for edits and asks before Cancel or the window close
     * button discards them.
     */
    static final class DirtyGuard {

        private final String message;
        private boolean dirty;
        private boolean discardConfirmed;

        DirtyGuard(String message) {
            this.message = message;
        }

        /** Marks the form dirty when this value changes from its starting state. */
        void watch(ObservableValue<?> value) {
            value.addListener((o, before, after) -> {
                if (!Objects.equals(before, after)) {
                    dirty = true;
                }
            });
        }

        /** For changes that have no observable property, such as a picked file. */
        void markDirty() {
            dirty = true;
        }

        void install(Dialog<?> dialog, ButtonType cancelType) {
            dialog.setOnCloseRequest(event -> {
                if (!confirmDiscard(dialog)) {
                    event.consume();
                }
            });
            Node cancel = dialog.getDialogPane().lookupButton(cancelType);
            if (cancel != null) {
                cancel.addEventFilter(ActionEvent.ACTION, event -> {
                    if (!confirmDiscard(dialog)) {
                        event.consume();
                    }
                });
            }
        }

        private boolean confirmDiscard(Dialog<?> dialog) {
            if (!dirty || discardConfirmed) {
                return true;
            }
            var pane = dialog.getDialogPane();
            var owner = pane.getScene() == null ? null : pane.getScene().getWindow();
            if (Dialogs.confirmDestructive(owner,
                    "Discard changes?", message, "Discard")) {
                discardConfirmed = true;
                return true;
            }
            return false;
        }
    }
}
