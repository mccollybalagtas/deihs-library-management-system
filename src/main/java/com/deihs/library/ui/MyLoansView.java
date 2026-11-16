package com.deihs.library.ui;

import com.deihs.library.dao.BookDao;
import com.deihs.library.dao.DaoException;
import com.deihs.library.model.Book;
import com.deihs.library.util.Covers;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A student's own shelf: the books they currently hold and the books they may
 * borrow. The borrow limit and due date are enforced here and again in the
 * database when the loan is written.
 *
 * Part of: Student screen — the reader's own loans and the borrowable shelf.
 * Used by: MainView, which builds it only for the STUDENT role.
 * Key rules: a student may hold at most Book.MAX_LOANS (3) books, and every
 *            self-service loan is due Book.LOAN_DAYS (7) days from today.
 */
public class MyLoansView extends VBox {

    // ============================================================
    // PART: Shared formatting and state
    // ============================================================

    // All dates on this screen use the same readable pattern, e.g. "Mar 4, 2026".
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final MainView main;
    private final BookDao dao = new BookDao();

    // Two independent tables: what I hold, and what is free to borrow.
    private final ObservableList<Book> myRows = FXCollections.observableArrayList();
    private final ObservableList<Book> availableRows = FXCollections.observableArrayList();
    private final TableView<Book> myTable = new TableView<>(myRows);
    private final TableView<Book> availableTable = new TableView<>(availableRows);

    private final TextField search = new TextField();
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::reloadAvailable);
    private final Label myCount = new Label();
    private final Label availableCount = new Label();
    private final Label limitNote = new Label();

    // Stat-card values; these are Labels so refresh() can update the text
    // without rebuilding the cards.
    private final Label loansValue = new Label("0 / " + Book.MAX_LOANS);
    private final Label dueSoonValue = new Label("0");
    private final Label overdueValue = new Label("0");

    // True when the student already holds MAX_LOANS books. Used to disable
    // every Borrow button in the available table.
    private boolean atLimit;

    // ============================================================
    // PART: Building the screen
    // ============================================================

    /**
     * WHAT: Builds the toolbar, stat cards, "my loans" card and available card.
     * WHY: Both tables must be created before refresh() can fill them.
     * CALLED BY: MainView when a student signs in.
     */
    public MyLoansView(MainView main) {
        this.main = main;
        setSpacing(14);
        setPadding(new Insets(22, 26, 26, 26));

        Node toolbar = buildToolbar();
        Node stats = buildStats();
        Node mine = buildMyLoansCard();
        Node shelves = buildAvailableCard();

        getChildren().addAll(toolbar, stats, mine, shelves);
        VBox.setVgrow(mine, Priority.ALWAYS);
        VBox.setVgrow(shelves, Priority.ALWAYS);

        refresh();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    // ============================================================
    // PART: Toolbar and stat cards
    // ============================================================

    /**
     * WHAT: Builds the subtitle, the limit note and a Refresh button.
     * WHY: limitNote is the running "you may borrow N more" message and is
     *      rewritten by refresh() each time the loan count changes.
     * CALLED BY: The MyLoansView constructor.
     */
    private Node buildToolbar() {
        Label subtitle = new Label("Borrow up to " + Book.MAX_LOANS
                + " books at a time and return them here when you are done.");
        subtitle.getStyleClass().add("muted");
        subtitle.setFont(Font.font("SansSerif", 12));

        limitNote.getStyleClass().add("hint-label");
        limitNote.setFont(Font.font("SansSerif", 11));

        VBox left = new VBox(3, subtitle, limitNote);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refresh = new Button("\u21BB   Refresh");
        refresh.getStyleClass().add("ghost-btn");
        refresh.setPrefHeight(40);
        refresh.setOnAction(e -> refresh());

        HBox row = new HBox(10, left, spacer, refresh);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * WHAT: Lays out the three summary cards in one row.
     * WHY: The counts answer "how many do I have / what needs attention" before
     *      the student reads any table.
     * CALLED BY: The MyLoansView constructor.
     */
    private Node buildStats() {
        HBox row = new HBox(14,
                statCard("My Loans", loansValue, "\u21C4", "stat-blue"),
                statCard("Due Soon", dueSoonValue, "\u23F0", "stat-amber"),
                statCard("Overdue", overdueValue, "!", "stat-red"));
        row.setFillHeight(true);
        return row;
    }

    /**
     * WHAT: Builds one stat card around a value Label.
     * WHY: The value Label is passed in so refresh() can call setText() on it
     *      instead of rebuilding the card.
     * CALLED BY: buildStats().
     */
    private VBox statCard(String title, Label value, String icon, String styleClass) {
        Label titleLabel = new Label(title.toUpperCase(Locale.ROOT));
        titleLabel.getStyleClass().add("stat-title");

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("stat-icon");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox top = new HBox(6, titleLabel, spacer, iconLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        value.getStyleClass().add("stat-value");

        VBox card = new VBox(6, top, value);
        card.getStyleClass().addAll("stat-card", styleClass);
        card.setPrefHeight(88);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    // ============================================================
    // PART: Building the "Books I have" table
    // ============================================================

    /**
     * WHAT: Builds the table of the student's current loans with a Return button.
     * WHY: This is the only place a student can give a book back, so the row
     *      action is the important part.
     * CALLED BY: The MyLoansView constructor.
     */
    private Node buildMyLoansCard() {
        Label title = new Label("Books I have");
        title.getStyleClass().add("h2");

        myCount.getStyleClass().add("muted");
        myCount.setFont(Font.font("SansSerif", 11));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, title, spacer, myCount);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 4, 8, 4));

        myTable.getStyleClass().add("table-view");
        myTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        myTable.setPlaceholder(EmptyState.host(myEmptyState()));
        myTable.setFixedCellSize(64);
        myTable.setPrefHeight(220);

        // Coloured left edge for overdue / due-soon / out-on-loan rows.
        BookRows.markUrgency(myTable);

        TableColumn<Book, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        titleCol.setMinWidth(230);

        // Cover thumbnail plus bold title and muted author, rebuilt per row.
        titleCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Book book = getTableRow().getItem();
                Label name = new Label(book.getTitle());
                name.setFont(Font.font("SansSerif", FontWeight.BOLD, 12.5));

                Label author = new Label(book.getAuthor());
                author.getStyleClass().add("muted");
                author.setFont(Font.font("SansSerif", 10.5));

                VBox text = new VBox(2, name, author);
                text.setAlignment(Pos.CENTER_LEFT);

                StackPane cover = Covers.node(book, 34, 44);
                HBox box = new HBox(10, cover, text);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
                setText(null);
            }
        });

        TableColumn<Book, String> borrowedCol = new TableColumn<>("Borrowed");
        borrowedCol.setCellValueFactory(data -> new SimpleStringProperty(
                format(data.getValue().getBorrowedAt())));
        borrowedCol.setMinWidth(100);

        TableColumn<Book, String> dueCol = DueColumn.create("Due");
        dueCol.setMinWidth(120);

        TableColumn<Book, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().loanStatusText()));
        statusCol.setMinWidth(130);

        // Every row here is on loan, so the pill always uses loanPillStyle().
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Book book = getTableRow().getItem();
                Label pill = new Label(book.loanStatusText());
                pill.getStyleClass().add(book.loanPillStyle());
                setGraphic(pill);
                setText(null);
            }
        });

        TableColumn<Book, Void> actionCol = new TableColumn<>("Action");
        actionCol.setMinWidth(100);
        actionCol.setMaxWidth(120);
        actionCol.setSortable(false);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button action = new Button("Return");

            // Wired once per cell; the click reads whichever book the recycled
            // row currently holds.
            {
                action.getStyleClass().addAll("row-btn", "row-btn-danger");
                action.setOnAction(e -> {
                    Book book = getTableRow() == null ? null : getTableRow().getItem();
                    if (book != null) {
                        returnBook(book);
                    }
                });
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null
                        ? null : action);
            }
        });

        myTable.getColumns().setAll(List.of(titleCol, borrowedCol, dueCol, statusCol, actionCol));

        VBox card = new VBox(header, myTable);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(10));
        VBox.setVgrow(myTable, Priority.ALWAYS);
        return card;
    }

    // ============================================================
    // PART: Building the "Available to borrow" table
    // ============================================================

    /**
     * WHAT: Builds the searchable list of books that are currently free, each
     *       with a Borrow button.
     * WHY: Students borrow without staff help, so this table carries the whole
     *      self-service flow.
     * CALLED BY: The MyLoansView constructor.
     */
    private Node buildAvailableCard() {
        Label title = new Label("Available to borrow");
        title.getStyleClass().add("h2");

        availableCount.getStyleClass().add("muted");
        availableCount.setFont(Font.font("SansSerif", 11));

        search.setPromptText("Search the shelves\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(34);
        search.setPrefWidth(240);

        // Debounced so the shelf is not re-queried on every keystroke.
        search.textProperty().addListener((o, a, b) -> searchDebounce.trigger());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, title, availableCount, spacer, search);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 4, 8, 4));

        availableTable.getStyleClass().add("table-view");
        availableTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        availableTable.setPlaceholder(EmptyState.host(availableEmptyState()));
        availableTable.setFixedCellSize(64);
        availableTable.setPrefHeight(220);

        TableColumn<Book, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        titleCol.setMinWidth(230);

        titleCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Book book = getTableRow().getItem();
                Label name = new Label(book.getTitle());
                name.setFont(Font.font("SansSerif", FontWeight.BOLD, 12.5));

                Label author = new Label(book.getAuthor());
                author.getStyleClass().add("muted");
                author.setFont(Font.font("SansSerif", 10.5));

                VBox text = new VBox(2, name, author);
                text.setAlignment(Pos.CENTER_LEFT);

                StackPane cover = Covers.node(book, 34, 44);
                HBox box = new HBox(10, cover, text);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
                setText(null);
            }
        });

        TableColumn<Book, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory()));
        categoryCol.setMinWidth(105);

        TableColumn<Book, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBookType()));
        typeCol.setMinWidth(80);

        TableColumn<Book, String> detailsCol = new TableColumn<>("Details");
        detailsCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getExtraInfo() == null ? "\u2014" : data.getValue().getExtraInfo()));
        detailsCol.setMinWidth(95);

        TableColumn<Book, Void> actionCol = new TableColumn<>("Action");
        actionCol.setMinWidth(100);
        actionCol.setMaxWidth(120);
        actionCol.setSortable(false);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button action = new Button("Borrow");

            {
                action.getStyleClass().add("row-btn");
                action.setOnAction(e -> {
                    Book book = getTableRow() == null ? null : getTableRow().getItem();
                    if (book != null) {
                        borrow(book);
                    }
                });
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }

                // The whole table shares one atLimit flag, so every Borrow
                // button disables together when the shelf is full.
                action.setDisable(atLimit);
                action.setTooltip(atLimit
                        ? new Tooltip("Return a book before borrowing another") : null);
                setGraphic(action);
            }
        });

        availableTable.getColumns().setAll(
                List.of(titleCol, categoryCol, typeCol, detailsCol, actionCol));

        VBox card = new VBox(header, availableTable);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(10));
        VBox.setVgrow(availableTable, Priority.ALWAYS);
        return card;
    }

    // ============================================================
    // PART: Empty-state messages
    // ============================================================

    /**
     * WHAT: Builds the message shown when the student holds no books.
     * WHY: A first-time student needs a pointer to the shelf below, not a blank box.
     * CALLED BY: buildMyLoansCard().
     */
    private EmptyState myEmptyState() {
        return new EmptyState("\u21C4", "No books on your shelf yet",
                "Borrow a title from the list below and it will appear here with its due date.")
                .action("Browse available books", this::focusAvailable);
    }

    /**
     * WHAT: Chooses the right "nothing to borrow" message.
     * WHY: "No search match", "shelf is full" and "everything is out" each need
     *      a different action, so the message is decided at display time.
     * CALLED BY: buildAvailableCard() and reloadAvailable().
     */
    private EmptyState availableEmptyState() {
        // Step 1: a search with no hits gets a Clear search shortcut.
        String query = search.getText() == null ? "" : search.getText().trim();
        if (!query.isEmpty()) {
            return new EmptyState("\u2315", "No matches for \u201C" + query + "\u201D",
                    "Try a shorter title, another author, or a different spelling.")
                    .action("Clear search", search::clear);
        }

        // Step 2: nothing is free because this student already holds the maximum.
        if (atLimit) {
            return new EmptyState("\u2713", "Your shelf is full",
                    "You already hold " + Book.MAX_LOANS + " books. Return one to borrow another.")
                    .action("Go to my books", this::focusMine);
        }

        // Step 3: the library genuinely has no free copies.
        return new EmptyState("\u25A4", "Every title is out on loan",
                "All copies are currently with other readers. Check back after a return.");
    }

    /**
     * WHAT: Moves keyboard focus to the available table and scrolls it to the top.
     * WHY: Used by the empty-state button so the student lands where the books are.
     * CALLED BY: myEmptyState().
     */
    private void focusAvailable() {
        availableTable.requestFocus();
        availableTable.getSelectionModel().clearSelection();
        availableTable.scrollTo(0);
    }

    /**
     * WHAT: Moves keyboard focus to the "my loans" table.
     * WHY: Used by the "shelf is full" message so the student can return a book.
     * CALLED BY: availableEmptyState().
     */
    private void focusMine() {
        myTable.requestFocus();
        myTable.getSelectionModel().clearSelection();
        myTable.scrollTo(0);
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    // ============================================================
    // PART: Loading the student's loans and the shelf
    // ============================================================

    /** Ctrl+F jumps to this page's search box. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    /**
     * WHAT: Reloads the student's own loans, recomputes the three stat values
     *       and the limit note, then reloads the available shelf.
     * WHY: Every borrow and return changes all of these together; splitting
     *      them would let the counters disagree with the tables.
     * CALLED BY: The constructor, the Refresh button, borrow(), returnBook().
     */
    public void refresh() {
        try {
            // Step 1: fetch only this student's loans.
            List<Book> mine = dao.findByBorrower(main.user().getId());
            myRows.setAll(mine);

            // Step 2: derive the stat-card numbers from the same list.
            long dueSoon = mine.stream().filter(Book::isDueSoon).count();
            long overdue = mine.stream().filter(Book::isOverdue).count();
            loansValue.setText(mine.size() + " / " + Book.MAX_LOANS);
            dueSoonValue.setText(String.valueOf(dueSoon));
            overdueValue.setText(String.valueOf(overdue));

            myCount.setText(mine.size() + (mine.size() == 1 ? " book" : " books") + " on loan");

            // Step 3: update the limit flag and the note above the tables.
            // The note switches to the error colour once the limit is reached.
            atLimit = mine.size() >= Book.MAX_LOANS;
            if (atLimit) {
                limitNote.setText("You have reached the " + Book.MAX_LOANS
                        + "-book limit. Return one to borrow another.");
                limitNote.getStyleClass().remove("hint-label");
                limitNote.getStyleClass().add("field-error");
            } else {
                limitNote.setText((Book.MAX_LOANS - mine.size())
                        + (Book.MAX_LOANS - mine.size() == 1 ? " more book" : " more books")
                        + " may be borrowed.");
                limitNote.getStyleClass().remove("field-error");
                limitNote.getStyleClass().add("hint-label");
            }
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load your loans", ex.getMessage());
        }

        // Step 4: the shelf depends on atLimit, so reload it after step 3.
        reloadAvailable();
    }

    /**
     * WHAT: Queries books by the search text and keeps only free copies.
     * WHY: BookDao.search has no "available only" option, so the filter runs
     *      here; the empty state is then rebuilt to match the result.
     * CALLED BY: The debounce timer and refresh().
     */
    private void reloadAvailable() {
        try {
            // Step 1: search by title/author text; no category filter here.
            List<Book> books = dao.search(search.getText(), null);

            // Step 2: drop anything already on loan.
            List<Book> available = new ArrayList<>();
            for (Book book : books) {
                if (!book.isBorrowed()) {
                    available.add(book);
                }
            }
            availableRows.setAll(available);

            // Step 3: update the counter and the placeholder for this result.
            availableCount.setText(available.size()
                    + (available.size() == 1 ? " title available" : " titles available"));
            availableTable.setPlaceholder(EmptyState.host(availableEmptyState()));

            // Step 4: repaint so each Borrow button re-reads atLimit.
            availableTable.refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load the shelves", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    // ============================================================
    // PART: Self-service borrow and return
    // ============================================================

    /**
     * WHAT: Borrows one book for the signed-in student with a 7-day due date.
     * WHY: The limit is checked again against MySQL because the cached atLimit
     *      may be stale, and checkout returns false if another student won the race.
     * CALLED BY: The Borrow button in the available table.
     */
    private void borrow(Book book) {
        try {
            // Step 1: authoritative limit check, straight from the database.
            int count = dao.countByBorrower(main.user().getId());
            if (count >= Book.MAX_LOANS) {
                Dialogs.error(main.window(), "Borrowing limit reached",
                        "You already have " + count + " of " + Book.MAX_LOANS
                                + " allowed books.\nReturn one before borrowing another.");
                refresh();
                return;
            }

            // Step 2: due date is always LOAN_DAYS from today, at end of day.
            LocalDate due = LocalDate.now().plusDays(Book.LOAN_DAYS);
            boolean ok = dao.checkout(book.getId(), main.user().getId(),
                    main.user().getDisplayName(), due.atTime(23, 59));
            if (!ok) {
                Dialogs.error(main.window(), "Book already on loan",
                        "\"" + book.getTitle() + "\" was just borrowed by someone else.");
                refresh();
                return;
            }

            // Step 3: confirm and reload both tables.
            main.setStatus("Borrowed \"" + book.getTitle() + "\" \u2022 due " + due);
            main.toastSuccess("Borrowed \"" + book.getTitle() + "\" \u2022 due " + due);
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not borrow the book", ex.getMessage());
        }
    }

    /**
     * WHAT: Returns one of the student's books.
     * WHY: checkin returns false if the book is already back, so a stale row
     *      click cannot report a false success.
     * CALLED BY: The Return button in the "Books I have" table.
     */
    private void returnBook(Book book) {
        try {
            boolean ok = dao.checkin(book.getId());
            if (!ok) {
                Dialogs.error(main.window(), "Already returned",
                        "\"" + book.getTitle() + "\" is no longer on loan to you.");
                refresh();
                return;
            }
            main.setStatus("Returned \"" + book.getTitle() + "\" to the shelves.");
            main.toastSuccess("Returned \"" + book.getTitle() + "\" to the shelves.");
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not return the book", ex.getMessage());
        }
    }

    /** Formats a timestamp for the Borrowed column; em dash when missing. */
    private static String format(LocalDateTime value) {
        return value == null ? "\u2014" : value.format(DATE);
    }
}
