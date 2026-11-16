package com.deihs.library.ui;

import com.deihs.library.dao.BookDao;
import com.deihs.library.dao.DaoException;
import com.deihs.library.model.Book;
import com.deihs.library.util.Covers;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;

/**
 * Add / edit dialog. Returns the finished book, or {@code null} when cancelled.
 *
 * Part of: Book add / edit dialog.
 * Used by: MainView.addBook(), CatalogView and BookCard, which pass the window to
 *          parent the dialog to and the book to edit (or null to add).
 * Key rules: Save stays disabled until title, author and category are filled, and
 *            the method returns null for Cancel so callers can skip the database write.
 */
public final class BookFormView {

    // Static-only helper: there is no reason to create an instance.
    private BookFormView() {
    }

    /**
     * WHAT: Shows the add/edit dialog, validates the fields and saves any chosen
     * cover image, then returns the finished Book.
     * WHY: Add and edit share the same form, so one method handles both and the
     * caller only has to pass null for a new book.
     * CALLED BY: MainView.addBook(), CatalogView (edit) and BookCard (edit).
     */
    public static Book show(Window owner, Book existing) {
        // existing == null means "add"; otherwise the same object is edited in place.
        boolean isNew = existing == null;

        // Step 1: create the dialog. initOwner keeps it above the main window.
        Dialog<Book> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "Add a Book" : "Edit Book");
        dialog.setHeaderText(isNew
                ? "Register a new title in the library catalog"
                : "Update the details of this book");
        dialog.initOwner(owner);
        dialog.setResizable(true);
        Dialogs.style(dialog.getDialogPane());

        // Step 2: the confirm button's text depends on the mode, and OK_DONE marks
        // it as the button that produces a result.
        ButtonType saveType = new ButtonType(isNew ? "Add Book" : "Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // Step 3: the guard asks before Cancel or the window close button throws
        // away typing. It is wired to the fields near the end of this method.
        FormSupport.DirtyGuard guard = new FormSupport.DirtyGuard(
                "You have not saved this book yet. Close without saving?");

        // ============================================================
        // PART: Fields and starting values
        // ============================================================
        // In edit mode every field starts from the existing book; in add mode it
        // starts empty (or from a sensible default).
        TextField title = new TextField(existing == null ? "" : existing.getTitle());
        title.setPromptText("Book title");
        title.getStyleClass().add("field");
        title.setPrefHeight(38);

        TextField author = new TextField(existing == null ? "" : existing.getAuthor());
        author.setPromptText("Author");
        author.getStyleClass().add("field");
        author.setPrefHeight(38);

        // Editable means the user can type a new category as well as pick one.
        // The typed text lives in getEditor(), so the validation rules watch that
        // control rather than the ComboBox value itself.
        ComboBox<String> category = new ComboBox<>();
        category.setEditable(true);
        category.getStyleClass().add("combo-box");
        category.setMaxWidth(Double.MAX_VALUE);
        category.setPrefHeight(38);
        // Preset choices keep the dialog usable even if the database read fails.
        category.getItems().addAll("Fiction", "Filipiniana", "Programming", "Mathematics",
                "Science", "History", "Reference", "Archive");
        // Merge in categories already used in the database, skipping duplicates.
        try {
            for (String value : new BookDao().categories()) {
                if (!category.getItems().contains(value)) {
                    category.getItems().add(value);
                }
            }
        } catch (DaoException ignored) {
            // The preset list is enough when the database cannot be read here.
        }
        // Default for a new book; the stored value when editing.
        category.setValue(existing == null || existing.getCategory() == null
                ? "Fiction" : existing.getCategory());

        // The type decides how the title is described elsewhere (printed, e-book
        // or archive copy).
        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll(Book.TYPE_PRINTED, Book.TYPE_EBOOK, Book.TYPE_ARCHIVE);
        type.getStyleClass().add("combo-box");
        type.setMaxWidth(Double.MAX_VALUE);
        type.setPrefHeight(38);
        type.setValue(existing == null || existing.getBookType() == null
                ? Book.TYPE_PRINTED : existing.getBookType());

        TextField extra = new TextField(existing == null || existing.getExtraInfo() == null
                ? "" : existing.getExtraInfo());
        extra.setPromptText("Pages, file size, or a short note");
        extra.getStyleClass().add("field");
        extra.setPrefHeight(38);

        // ---------------- Cover ----------------
        // ============================================================
        // PART: Cover preview and image picking
        // ============================================================
        // A throwaway Book is used only to render the preview. It is never saved;
        // the real Book is built from the fields when Save is pressed.
        Book previewBook = new Book();
        previewBook.setTitle(existing == null ? "New Book" : existing.getTitle());
        previewBook.setAuthor(existing == null ? "Author" : existing.getAuthor());
        previewBook.setCategory(existing == null ? "Fiction" : existing.getCategory());
        previewBook.setCoverFile(existing == null ? null : existing.getCoverFile());

        // These one-element arrays work around Java's "effectively final" rule for
        // lambdas. A lambda may read a local variable but may not assign it; it can
        // still change what is inside an array. Each slot acts as a small shared
        // variable used by the buttons and the save handler below:
        //   picked[0]        - image file chosen in this session, or null
        //   removeCover[0]   - true once the user asks to delete the stored cover
        //   existingCover[0] - cover path the book had when the dialog opened
        final File[] picked = {null};
        final boolean[] removeCover = {false};
        final String[] existingCover = {existing == null ? null : existing.getCoverFile()};

        // Fixed-size preview box. Setting min, pref and max to the same size stops
        // the layout from stretching the cover when the dialog is resized.
        StackPane preview = new StackPane(Covers.node(previewBook, 132, 174));
        preview.setPrefSize(132, 174);
        preview.setMinSize(132, 174);
        preview.setMaxSize(132, 174);

        // WHAT: Rebuilds the preview image from the current field text and cover choice.
        // WHY: The preview should match what Save will store, so it is refreshed on
        // every edit instead of being drawn once at the start.
        // CALLED BY: the three text listeners below, and the Choose / Remove buttons.
        Runnable refreshPreview = () -> {
            previewBook.setTitle(blankTo(title.getText(), "New Book"));
            previewBook.setAuthor(blankTo(author.getText(), "Author"));
            previewBook.setCategory(blankTo(category.getEditor().getText(), "Fiction"));
            // A newly picked file is drawn straight from disk. Otherwise the preview
            // falls back to the stored cover, unless the user asked to remove it.
            Node node;
            if (picked[0] != null) {
                node = Covers.nodeFromFile(picked[0], 132, 174);
            } else {
                previewBook.setCoverFile(removeCover[0] ? null : existingCover[0]);
                node = Covers.node(previewBook, 132, 174);
            }
            // setAll replaces the old image instead of stacking a new one on top.
            preview.getChildren().setAll(node);
        };

        // Opens the system file picker. The filters only narrow what the user sees;
        // the file itself is handled again when Save is pressed.
        Button choose = new Button("Choose image\u2026");
        choose.getStyleClass().add("ghost-btn");
        choose.setMaxWidth(Double.MAX_VALUE);
        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a book cover");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            // showOpenDialog blocks until the user picks a file or cancels.
            File file = chooser.showOpenDialog(owner);
            if (file != null) {
                // A picked file wins over a pending removal, so reset that flag.
                picked[0] = file;
                removeCover[0] = false;
                guard.markDirty();
                refreshPreview.run();
            }
        });

        // Marks the stored cover for deletion. The file is not deleted yet; that
        // happens only after Save succeeds, so Cancel leaves everything untouched.
        Button clear = new Button("Remove cover");
        clear.getStyleClass().add("ghost-btn");
        clear.setMaxWidth(Double.MAX_VALUE);
        clear.setOnAction(e -> {
            picked[0] = null;
            removeCover[0] = true;
            guard.markDirty();
            refreshPreview.run();
        });

        Label coverHint = new Label("Portrait images look best.\nSaved into the covers/ folder.");
        coverHint.getStyleClass().add("muted");
        coverHint.setWrapText(true);
        coverHint.setMaxWidth(132);

        VBox coverBox = new VBox(8, preview, choose, clear, coverHint);
        coverBox.setAlignment(Pos.TOP_CENTER);
        coverBox.setPadding(new Insets(4, 0, 0, 0));

        // Live preview: every field that appears on the cover refreshes it as the
        // user types. The category is read from the ComboBox editor because the
        // box is editable and the typed text is not stored as the combo value.
        title.textProperty().addListener((o, a, b) -> refreshPreview.run());
        author.textProperty().addListener((o, a, b) -> refreshPreview.run());
        category.getEditor().textProperty().addListener((o, a, b) -> refreshPreview.run());

        // ---------------- Form ----------------
        // ============================================================
        // PART: Validation rules
        // ============================================================
        // One hint label per required field. A TextRule fills its label in when the
        // field is wrong and hides it again when the field becomes valid.
        Label titleHint = hintLabel();
        Label authorHint = hintLabel();
        Label categoryHint = hintLabel();

        FormSupport.TextRule titleRule = new FormSupport.TextRule(title, titleHint,
                () -> isBlank(title.getText()) ? "Please enter the book title." : null);
        FormSupport.TextRule authorRule = new FormSupport.TextRule(author, authorHint,
                () -> isBlank(author.getText()) ? "Please enter the author." : null);
        FormSupport.TextRule categoryRule = new FormSupport.TextRule(category.getEditor(), categoryHint,
                () -> isBlank(category.getEditor().getText())
                        ? "Please choose or type a category." : null);

        BooleanBinding valid = Bindings.createBooleanBinding(
                () -> titleRule.problem() == null
                        && authorRule.problem() == null
                        && categoryRule.problem() == null,
                title.textProperty(), author.textProperty(), category.getEditor().textProperty());

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(2, 4, 4, 4));

        form.addRow(0, fieldLabel("Title"), fieldBlock(title, titleHint));
        form.addRow(1, fieldLabel("Author"), fieldBlock(author, authorHint));
        form.addRow(2, fieldLabel("Category"), fieldBlock(category, categoryHint));
        form.addRow(3, fieldLabel("Type"), type);
        form.addRow(4, fieldLabel("Details"), extra);

        Label error = new Label(" ");
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMinHeight(26);
        form.add(error, 1, 5);

        GridPane.setHgrow(type, Priority.ALWAYS);
        GridPane.setHgrow(extra, Priority.ALWAYS);

        HBox content = new HBox(22, coverBox, form);
        content.setPadding(new Insets(8, 6, 6, 6));
        content.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(form, Priority.ALWAYS);
        form.setMaxWidth(Double.MAX_VALUE);
        dialog.getDialogPane().setContent(content);

        final Book[] result = {null};

        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        if (saveButton instanceof Button save) {
            save.disableProperty().bind(valid.not());
            save.addEventFilter(ActionEvent.ACTION, event -> {
                titleRule.touch();
                authorRule.touch();
                categoryRule.touch();
                if (!valid.get()) {
                    error.setText("Please fix the highlighted fields.");
                    event.consume();
                    return;
                }

                String name = title.getText().trim();
                String writer = author.getText().trim();
                String genre = category.getEditor().getText().trim();

                Book book = existing == null ? new Book() : existing;
                book.setTitle(name);
                book.setAuthor(writer);
                book.setCategory(genre);
                book.setBookType(type.getValue() == null ? Book.TYPE_PRINTED : type.getValue());
                book.setExtraInfo(extra.getText() == null || extra.getText().isBlank()
                        ? null : extra.getText().trim());

                try {
                    if (picked[0] != null) {
                        String stored = Covers.save(picked[0], name);
                        if (existingCover[0] != null && !existingCover[0].equals(stored)) {
                            Covers.delete(existingCover[0]);
                        }
                        book.setCoverFile(stored);
                    } else if (removeCover[0]) {
                        Covers.delete(existingCover[0]);
                        book.setCoverFile(null);
                    }
                } catch (IOException ex) {
                    error.setText("Could not save the cover image: " + ex.getMessage());
                    event.consume();
                    return;
                }

                result[0] = book;
            });
        }

        guard.watch(title.textProperty());
        guard.watch(author.textProperty());
        guard.watch(category.getEditor().textProperty());
        guard.watch(type.valueProperty());
        guard.watch(extra.textProperty());
        guard.install(dialog, ButtonType.CANCEL);

        dialog.setResultConverter(button -> button == saveType ? result[0] : null);
        FormSupport.focus(title);
        return dialog.showAndWait().orElse(null);
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static Label hintLabel() {
        Label hint = new Label();
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        hint.setVisible(false);
        hint.setManaged(false);
        return hint;
    }

    /** A control with its inline message directly underneath. */
    private static VBox fieldBlock(Node control, Label hint) {
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        VBox box = new VBox(3, control, hint);
        box.setFillWidth(true);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
