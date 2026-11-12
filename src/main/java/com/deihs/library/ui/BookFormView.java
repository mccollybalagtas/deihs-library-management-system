package com.deihs.library.ui;

import com.deihs.library.dao.BookDao;
import com.deihs.library.dao.DaoException;
import com.deihs.library.model.Book;
import com.deihs.library.util.Covers;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;

/** Add / edit dialog. Returns the finished book, or {@code null} when cancelled. */
public final class BookFormView {

    private BookFormView() {
    }

    public static Book show(Window owner, Book existing) {
        boolean isNew = existing == null;

        Dialog<Book> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "Add a Book" : "Edit Book");
        dialog.setHeaderText(isNew
                ? "Register a new title in the library catalog"
                : "Update the details of this book");
        dialog.initOwner(owner);
        dialog.setResizable(true);
        Dialogs.style(dialog.getDialogPane());

        ButtonType saveType = new ButtonType(isNew ? "Add Book" : "Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField title = new TextField(existing == null ? "" : existing.getTitle());
        title.setPromptText("Book title");
        title.getStyleClass().add("field");
        title.setPrefHeight(38);

        TextField author = new TextField(existing == null ? "" : existing.getAuthor());
        author.setPromptText("Author");
        author.getStyleClass().add("field");
        author.setPrefHeight(38);

        ComboBox<String> category = new ComboBox<>();
        category.setEditable(true);
        category.getStyleClass().add("combo-box");
        category.setMaxWidth(Double.MAX_VALUE);
        category.setPrefHeight(38);
        category.getItems().addAll("Fiction", "Filipiniana", "Programming", "Mathematics",
                "Science", "History", "Reference", "Archive");
        try {
            for (String value : new BookDao().categories()) {
                if (!category.getItems().contains(value)) {
                    category.getItems().add(value);
                }
            }
        } catch (DaoException ignored) {
            // The preset list is enough when the database cannot be read here.
        }
        category.setValue(existing == null || existing.getCategory() == null
                ? "Fiction" : existing.getCategory());

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
        Book previewBook = new Book();
        previewBook.setTitle(existing == null ? "New Book" : existing.getTitle());
        previewBook.setAuthor(existing == null ? "Author" : existing.getAuthor());
        previewBook.setCategory(existing == null ? "Fiction" : existing.getCategory());
        previewBook.setCoverFile(existing == null ? null : existing.getCoverFile());

        final File[] picked = {null};
        final boolean[] removeCover = {false};
        final String[] existingCover = {existing == null ? null : existing.getCoverFile()};

        StackPane preview = new StackPane(Covers.node(previewBook, 132, 174));
        preview.setPrefSize(132, 174);
        preview.setMinSize(132, 174);
        preview.setMaxSize(132, 174);

        Runnable refreshPreview = () -> {
            previewBook.setTitle(blankTo(title.getText(), "New Book"));
            previewBook.setAuthor(blankTo(author.getText(), "Author"));
            previewBook.setCategory(blankTo(category.getEditor().getText(), "Fiction"));
            Node node;
            if (picked[0] != null) {
                node = Covers.nodeFromFile(picked[0], 132, 174);
            } else {
                previewBook.setCoverFile(removeCover[0] ? null : existingCover[0]);
                node = Covers.node(previewBook, 132, 174);
            }
            preview.getChildren().setAll(node);
        };

        Button choose = new Button("Choose image\u2026");
        choose.getStyleClass().add("ghost-btn");
        choose.setMaxWidth(Double.MAX_VALUE);
        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a book cover");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            File file = chooser.showOpenDialog(owner);
            if (file != null) {
                picked[0] = file;
                removeCover[0] = false;
                refreshPreview.run();
            }
        });

        Button clear = new Button("Remove cover");
        clear.getStyleClass().add("ghost-btn");
        clear.setMaxWidth(Double.MAX_VALUE);
        clear.setOnAction(e -> {
            picked[0] = null;
            removeCover[0] = true;
            refreshPreview.run();
        });

        Label coverHint = new Label("Portrait images look best.\nSaved into the covers/ folder.");
        coverHint.getStyleClass().add("muted");
        coverHint.setWrapText(true);
        coverHint.setMaxWidth(132);

        VBox coverBox = new VBox(8, preview, choose, clear, coverHint);
        coverBox.setAlignment(Pos.TOP_CENTER);
        coverBox.setPadding(new Insets(4, 0, 0, 0));

        title.textProperty().addListener((o, a, b) -> refreshPreview.run());
        author.textProperty().addListener((o, a, b) -> refreshPreview.run());
        category.getEditor().textProperty().addListener((o, a, b) -> refreshPreview.run());

        // ---------------- Form ----------------
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(2, 4, 4, 4));

        form.addRow(0, fieldLabel("Title"), title);
        form.addRow(1, fieldLabel("Author"), author);
        form.addRow(2, fieldLabel("Category"), category);
        form.addRow(3, fieldLabel("Type"), type);
        form.addRow(4, fieldLabel("Details"), extra);

        Label error = new Label(" ");
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMinHeight(26);
        form.add(error, 1, 5);

        GridPane.setHgrow(title, Priority.ALWAYS);
        GridPane.setHgrow(author, Priority.ALWAYS);
        GridPane.setHgrow(category, Priority.ALWAYS);
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
        if (saveButton != null) {
            saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                String name = title.getText() == null ? "" : title.getText().trim();
                String writer = author.getText() == null ? "" : author.getText().trim();
                String genre = category.getEditor().getText() == null ? "" : category.getEditor().getText().trim();

                if (name.isEmpty()) {
                    error.setText("Please enter the book title.");
                    event.consume();
                    return;
                }
                if (writer.isEmpty()) {
                    error.setText("Please enter the author.");
                    event.consume();
                    return;
                }
                if (genre.isEmpty()) {
                    error.setText("Please choose or type a category.");
                    event.consume();
                    return;
                }

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

        dialog.setResultConverter(button -> button == saveType ? result[0] : null);
        return dialog.showAndWait().orElse(null);
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
