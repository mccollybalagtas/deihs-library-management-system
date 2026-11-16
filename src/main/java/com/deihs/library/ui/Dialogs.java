package com.deihs.library.ui;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Small helpers so every dialog looks the same.
 *
 * <p>Part of: the shared widget layer. Centralising the alert setup means the
 * stylesheet, owner window and button conventions are applied in one place.</p>
 * <p>Used by: most screens — the catalogue, circulation, loans, users, the book
 * form and the login flow — for errors, notices and confirmations.</p>
 * <p>Key rules: destructive confirmations default to Cancel and style the
 * confirm button as dangerous; styling is optional and never blocks the
 * dialog.</p>
 */
public final class Dialogs {

    private Dialogs() {
    }

    // The two simple notices differ only by alert type; both delegate to show()
    // so the owner, header and stylesheet are handled identically.

    public static void info(Window owner, String title, String message) {
        show(owner, Alert.AlertType.INFORMATION, title, message);
    }

    public static void error(Window owner, String title, String message) {
        show(owner, Alert.AlertType.ERROR, title, message);
    }

    // WHAT: asks a yes/no question and returns true only for Yes.
    // WHY: callers want a boolean, not an Optional, so the unwrapping happens
    // here. CALLED BY: ordinary confirmations such as "sign out?" or
    // "deactivate this account?".
    public static boolean confirm(Window owner, String title, String message) {
        // YES/NO are the plain confirmation buttons; the answer is read below.
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(null);
        // Tie the dialog to its window so it centres correctly and cannot be
        // lost behind the main window.
        if (owner != null) {
            alert.initOwner(owner);
        }
        // showAndWait blocks until the user answers; closing with the window
        // button yields an empty Optional, which counts as "no".
        style(alert.getDialogPane());
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.YES;
    }

    /**
     * Confirmation for an action that cannot be undone.
     *
     * <p>Cancel is the default button, so a stray Enter cannot delete
     * anything, and the confirm button is painted with the danger style.</p>
     *
     * <p>CALLED BY: delete actions in the catalogue, the collection grid, the
     * users screen and the shared form support.</p>
     */
    public static boolean confirmDestructive(Window owner, String title, String message,
                                             String confirmLabel) {
        // Step 1: build the two buttons. Cancel is declared first, which is
        // what makes it the default and keeps it left of the confirm button.
        ButtonType confirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, cancel, confirm);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        DialogPane pane = alert.getDialogPane();
        style(pane);

        // Step 2: paint the confirm button red so the destructive choice is
        // unmistakable.
        Node confirmNode = pane.lookupButton(confirm);
        confirmNode.getStyleClass().add("danger-btn");

        // Step 3: make Cancel the default, so pressing Enter does not delete.
        Node cancelNode = pane.lookupButton(cancel);
        if (cancelNode instanceof Button button) {
            button.setDefaultButton(true);
        }

        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == confirm;
    }

    // WHAT: the shared body for info() and error(): one OK button, no header,
    // and the app stylesheet. WHY: keeps every notice identical.
    // CALLED BY: info() and error().
    private static void show(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        style(alert.getDialogPane());
        alert.showAndWait();
    }

    // WHAT: applies the app stylesheet to a dialog pane and gives it a minimum
    // width. WHY: dialogs are separate windows and do not inherit the scene's
    // styles automatically. CALLED BY: every helper in this class, plus the
    // screens that build their own custom dialogs.
    public static void style(DialogPane pane) {
        try {
            String css = Dialogs.class.getResource("/css/app.css").toExternalForm();
            // Add the sheet only once; duplicate entries are harmless but noisy.
            if (!pane.getStylesheets().contains(css)) {
                pane.getStylesheets().add(css);
            }
        } catch (Exception ignored) {
            // Styling is optional; the dialog still works without it.
        }
        // A floor width stops short messages from producing a cramped dialog.
        pane.setMinWidth(420);
    }
}
