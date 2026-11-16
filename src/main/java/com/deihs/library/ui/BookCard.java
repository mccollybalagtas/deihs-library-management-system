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

/**
 * A single book tile used by the dashboard and the collection grid.
 *
 * <p>Part of: the reusable widget layer of the UI. One card renders one
 * {@link Book}: cover art, title, author, status pills and, for a copy that is
 * out, a due-date line.</p>
 * <p>Used by: {@link CollectionPanel} for the browsable grid and
 * {@link DashboardView} for its highlighted shelves.</p>
 * <p>Key rules: the tile is read-only unless {@code editable} is true, and only
 * then does clicking it open {@link BookFormView}. The due line is added only
 * when the copy is borrowed and has a due timestamp.</p>
 */
public class BookCard extends VBox {

    // Fixed sizing keeps every tile identical, so a grid of cards lines up
    // instead of reflowing as titles and authors differ in length.
    private static final double CARD_WIDTH = 204;
    private static final double COVER_WIDTH = 180;
    private static final double COVER_HEIGHT = 234;

    // The model row this tile renders, plus the three things it needs to act:
    // the window that owns any dialog, a callback to refresh the parent list
    // after an edit, and whether this viewer is allowed to edit at all.
    private final Book book;
    private final Window owner;
    private final Runnable onChanged;
    private final boolean editable;

    // ====
    // PART: Building the tile
    // ====

    /**
     * @param editable true only for administrators; everyone else gets a
     *                 read-only tile that cannot open the edit dialog.
     */
    public BookCard(Book book, Window owner, Runnable onChanged, boolean editable) {
        // Keep the inputs; the label builders below read them later.
        this.book = book;
        this.owner = owner;
        this.onChanged = onChanged;
        this.editable = editable;

        // Step 1: apply the base card look, plus a muted variant when the
        // viewer may not edit, so read-only tiles are visibly not clickable.
        getStyleClass().add("book-card");
        if (!editable) {
            getStyleClass().add("book-card-readonly");
        }
        // Step 2: fix the width so tiles form an even grid.
        setSpacing(8);
        setPadding(new Insets(12));
        setPrefWidth(CARD_WIDTH);
        setMinWidth(CARD_WIDTH);
        setMaxWidth(CARD_WIDTH);

        // Step 3: cover first, then the text stack. Each label builder below
        // owns one piece so this constructor stays readable.
        getChildren().addAll(
                Covers.node(book, COVER_WIDTH, COVER_HEIGHT),
                titleLabel(),
                authorLabel(),
                badgeRow());

        // Step 4: a due line only makes sense for a copy that is actually out
        // and has a timestamp. The wording and colour come from the model, so
        // every screen agrees on what "due soon" looks like.
        if (book.isBorrowed() && book.getDueAt() != null) {
            Label due = new Label(book.dueRelativeText());
            due.getStyleClass().add(book.dueUrgencyStyle());
            due.setMaxWidth(COVER_WIDTH);
            // Tooltip carries the exact date; the label itself is relative.
            Tooltip.install(due, new Tooltip("Due " + book.dueText()));
            getChildren().add(due);
        }

        // Step 5: wire the click behaviour. Only admins get an action; everyone
        // else gets an explanatory tooltip instead of a dead click.
        if (editable) {
            setOnMouseClicked(e -> edit());
            Tooltip.install(this, new Tooltip("Click to edit this book"));
        } else {
            Tooltip.install(this, new Tooltip(book.isBorrowed()
                    ? book.loanStatusText() + (book.getDueAt() == null
                    ? "" : " \u2022 due " + book.dueText())
                    : "Available on the shelf"));
        }
    }

    // ====
    // PART: Small text and pill builders
    // ====

    // WHAT: builds the wrapped title label.
    // WHY: titles are long and variable, so they wrap to two lines and reserve
    // that height to keep cards aligned. CALLED BY: the constructor.
    private Label titleLabel() {
        Label title = new Label(book.getTitle());
        title.getStyleClass().add("book-title");
        title.setWrapText(true);
        title.setMaxWidth(COVER_WIDTH);
        title.setMinHeight(36);
        title.setAlignment(Pos.TOP_LEFT);
        return title;
    }

    // WHAT: builds the single-line author label.
    // WHY: authors are shorter than titles, so this one clips instead of
    // wrapping and the card height stays stable. CALLED BY: the constructor.
    private Label authorLabel() {
        Label author = new Label(book.getAuthor());
        author.getStyleClass().add("book-author");
        author.setMaxWidth(COVER_WIDTH);
        author.setWrapText(false);
        return author;
    }

    // WHAT: builds the row of pills under the text.
    // WHY: the same card has to read as "on loan", "overdue" or "available" at
    // a glance. CALLED BY: the constructor.
    private HBox badgeRow() {
        // The status pill borrows the model's wording and colour when the copy
        // is out; an on-shelf copy uses the neutral "available" pill.
        Label status = new Label(book.isBorrowed() ? book.loanStatusText() : book.statusText());
        status.getStyleClass().add(book.isBorrowed() ? book.loanPillStyle() : "pill-available");

        // The type pill is informational only (fiction, reference, ...).
        Label type = new Label(book.getBookType());
        type.getStyleClass().add("type-badge");

        HBox row = new HBox(6, status, type);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ====
    // PART: Admin-only edit flow
    // ====

    // WHAT: opens the book form for this row and saves the result.
    // WHY: the card is the fastest way for a librarian to fix a typo, so the
    // edit path lives here as well as in the table view.
    // CALLED BY: the click handler installed in the constructor.
    private void edit() {
        // Defensive re-check: a read-only tile should never reach the form even
        // if a handler were attached by mistake.
        if (!editable) {
            return;
        }
        // Step 1: show the shared form; null means the user cancelled.
        Book edited = BookFormView.show(owner, book);
        if (edited == null) {
            return;
        }
        // Step 2: persist, then tell the parent list to reload so this tile and
        // any other view showing the book stay in sync.
        try {
            new BookDao().update(edited);
            if (onChanged != null) {
                onChanged.run();
            }
        } catch (DaoException ex) {
            // Step 3: surface a failed write without losing the user's place.
            Dialogs.error(owner, "Update failed", ex.getMessage());
        }
    }
}
