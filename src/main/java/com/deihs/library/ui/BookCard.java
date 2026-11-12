package com.deihs.library.ui;

import com.deihs.library.dao.BookDao;
import com.deihs.library.dao.DaoException;
import com.deihs.library.model.Book;
import com.deihs.library.util.Covers;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** A single book tile used by the dashboard and the collection grid. */
public class BookCard extends VBox {

    private static final double CARD_WIDTH = 204;
    private static final double COVER_WIDTH = 180;
    private static final double COVER_HEIGHT = 234;

    private final Book book;
    private final Window owner;
    private final Runnable onChanged;

    public BookCard(Book book, Window owner, Runnable onChanged) {
        this.book = book;
        this.owner = owner;
        this.onChanged = onChanged;

        getStyleClass().add("book-card");
        setSpacing(8);
        setPadding(new Insets(12));
        setPrefWidth(CARD_WIDTH);
        setMinWidth(CARD_WIDTH);
        setMaxWidth(CARD_WIDTH);

        getChildren().addAll(
                Covers.node(book, COVER_WIDTH, COVER_HEIGHT),
                titleLabel(),
                authorLabel(),
                badgeRow());

        setOnMouseClicked(e -> edit());
        Tooltip.install(this, new Tooltip("Click to edit this book"));
    }

    private Label titleLabel() {
        Label title = new Label(book.getTitle());
        title.getStyleClass().add("book-title");
        title.setWrapText(true);
        title.setMaxWidth(COVER_WIDTH);
        title.setMinHeight(36);
        title.setAlignment(Pos.TOP_LEFT);
        return title;
    }

    private Label authorLabel() {
        Label author = new Label(book.getAuthor());
        author.getStyleClass().add("book-author");
        author.setMaxWidth(COVER_WIDTH);
        author.setWrapText(false);
        return author;
    }

    private HBox badgeRow() {
        Label status = new Label(book.statusText());
        status.getStyleClass().add(book.isBorrowed() ? "pill-out" : "pill-available");

        Label type = new Label(book.getBookType());
        type.getStyleClass().add("type-badge");

        HBox row = new HBox(6, status, type);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void edit() {
        Book edited = BookFormView.show(owner, book);
        if (edited == null) {
            return;
        }
        try {
            new BookDao().update(edited);
            if (onChanged != null) {
                onChanged.run();
            }
        } catch (DaoException ex) {
            Dialogs.error(owner, "Update failed", ex.getMessage());
        }
    }
}
