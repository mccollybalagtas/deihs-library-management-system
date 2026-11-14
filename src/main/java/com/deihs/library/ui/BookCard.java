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
    private final boolean editable;

    /**
     * @param editable true only for administrators; everyone else gets a
     *                 read-only tile that cannot open the edit dialog.
     */
    public BookCard(Book book, Window owner, Runnable onChanged, boolean editable) {
        this.book = book;
        this.owner = owner;
        this.onChanged = onChanged;
        this.editable = editable;

        getStyleClass().add("book-card");
        if (!editable) {
            getStyleClass().add("book-card-readonly");
        }
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

        if (book.isBorrowed() && book.getDueAt() != null) {
            Label due = new Label(book.isOverdue()
                    ? book.loanStatusText()
                    : "Due " + book.dueText());
            due.getStyleClass().add(book.isOverdue() ? "due-overdue" : "due-soon");
            due.setMaxWidth(COVER_WIDTH);
            getChildren().add(due);
        }

        if (editable) {
            setOnMouseClicked(e -> edit());
            Tooltip.install(this, new Tooltip("Click to edit this book"));
        } else {
            Tooltip.install(this, new Tooltip(book.isBorrowed()
                    ? book.loanStatusText()
                    : "Available on the shelf"));
        }
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
        Label status = new Label(book.isBorrowed() ? book.loanStatusText() : book.statusText());
        status.getStyleClass().add(book.isBorrowed() ? book.loanPillStyle() : "pill-available");

        Label type = new Label(book.getBookType());
        type.getStyleClass().add("type-badge");

        HBox row = new HBox(6, status, type);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void edit() {
        if (!editable) {
            return;
        }
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
