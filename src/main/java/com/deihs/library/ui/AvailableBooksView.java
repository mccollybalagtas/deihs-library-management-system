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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The student's borrowable shelf: every title that is currently free, with its
 * own search box and Borrow button.
 *
 * <p>Part of: Student screens. This is deliberately a separate page from My
 * Loans so browsing the shelves never competes with the table of books the
 * student already holds.</p>
 *
 * <p>Used by: MainView, which builds it only for the STUDENT role.<br>
 * Key rules: a student may hold at most Book.MAX_LOANS (3) books, and every
 * self-service loan is due Book.LOAN_DAYS (7) days from today.</p>
 */
public class AvailableBooksView extends VBox {

    private final MainView main;
    private final BookDao dao = new BookDao();

    private final ObservableList<Book> availableRows = FXCollections.observableArrayList();
    private final TableView<Book> availableTable = new TableView<>(availableRows);

    private final TextField search = new TextField();
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::reloadAvailable);
    private final Label availableCount = new Label();
    private final Label limitNote = new Label();

    // True when the student already holds MAX_LOANS books. Used to disable
    // every Borrow button in the table.
    private boolean atLimit;

    public AvailableBooksView(MainView main) {
        this.main = main;
        setSpacing(14);
        setPadding(new Insets(22, 26, 26, 26));

        Node shelves = buildAvailableCard();
        getChildren().addAll(buildToolbar(), shelves);
        VBox.setVgrow(shelves, Priority.ALWAYS);

        refresh();
    }

    // ============================================================
    // PART: Toolbar
    // ============================================================

    private Node buildToolbar() {
        Label subtitle = new Label("Borrow up to " + Book.MAX_LOANS
                + " books at a time. Pick a title below, then return it from My Loans.");
        subtitle.getStyleClass().add("muted");
        subtitle.setFont(Font.font("SansSerif", 12));

        limitNote.getStyleClass().add("hint-label");
        limitNote.setFont(Font.font("SansSerif", 11));

        VBox left = new VBox(3, subtitle, limitNote);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button mine = new Button("\u21C4   My Loans");
        mine.getStyleClass().add("ghost-btn");
        mine.setPrefHeight(40);
        mine.setOnAction(e -> main.navigate("myloans"));

        Button refresh = new Button("\u21BB   Refresh");
        refresh.getStyleClass().add("ghost-btn");
        refresh.setPrefHeight(40);
        refresh.setOnAction(e -> refresh());

        HBox row = new HBox(10, left, spacer, mine, refresh);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ============================================================
    // PART: The available table
    // ============================================================

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

                // Every Borrow button disables together when the shelf is full.
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
    // PART: Empty states and data
    // ============================================================

    private EmptyState availableEmptyState() {
        String query = search.getText() == null ? "" : search.getText().trim();
        if (!query.isEmpty()) {
            return new EmptyState("\u2315", "No matches for \u201C" + query + "\u201D",
                    "Try a shorter title, another author, or a different spelling.")
                    .action("Clear search", search::clear);
        }
        if (atLimit) {
            return new EmptyState("\u2713", "Your shelf is full",
                    "You already hold " + Book.MAX_LOANS + " books. Return one from My Loans to borrow another.")
                    .action("Go to my books", () -> main.navigate("myloans"));
        }
        return new EmptyState("\u25A4", "Every title is out on loan",
                "All copies are currently with other readers. Check back after a return.");
    }

    /** Ctrl+F jumps to this page's search box. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    /**
     * Reloads the limit note and the borrowable shelf. Called by the
     * constructor, the Refresh button, and after every borrow.
     */
    public void refresh() {
        try {
            int count = dao.countByBorrower(main.user().getId());
            atLimit = count >= Book.MAX_LOANS;
            updateLimitNote(count);
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load your loans", ex.getMessage());
        }
        reloadAvailable();
    }

    private void updateLimitNote(int count) {
        if (atLimit) {
            limitNote.setText("You have reached the " + Book.MAX_LOANS
                    + "-book limit. Return one to borrow another.");
            limitNote.getStyleClass().remove("hint-label");
            limitNote.getStyleClass().add("field-error");
        } else {
            int left = Book.MAX_LOANS - count;
            limitNote.setText(left + (left == 1 ? " more book" : " more books")
                    + " may be borrowed.");
            limitNote.getStyleClass().remove("field-error");
            limitNote.getStyleClass().add("hint-label");
        }
    }

    private void reloadAvailable() {
        try {
            List<Book> books = dao.search(search.getText(), null);

            List<Book> available = new ArrayList<>();
            for (Book book : books) {
                if (!book.isBorrowed()) {
                    available.add(book);
                }
            }
            availableRows.setAll(available);

            availableCount.setText(available.size()
                    + (available.size() == 1 ? " title available" : " titles available"));
            availableTable.setPlaceholder(EmptyState.host(availableEmptyState()));

            // Repaint so each Borrow button re-reads atLimit.
            availableTable.refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load the shelves", ex.getMessage());
        }
    }

    // ============================================================
    // PART: Self-service borrow
    // ============================================================

    private void borrow(Book book) {
        try {
            // Authoritative limit check, straight from the database.
            int count = dao.countByBorrower(main.user().getId());
            if (count >= Book.MAX_LOANS) {
                Dialogs.error(main.window(), "Borrowing limit reached",
                        "You already have " + count + " of " + Book.MAX_LOANS
                                + " allowed books.\nReturn one before borrowing another.");
                refresh();
                return;
            }

            // Due date is always LOAN_DAYS from today, at end of day.
            java.time.LocalDate due = java.time.LocalDate.now().plusDays(Book.LOAN_DAYS);
            boolean ok = dao.checkout(book.getId(), main.user().getId(),
                    main.user().getDisplayName(), due.atTime(23, 59));
            if (!ok) {
                Dialogs.error(main.window(), "Book already on loan",
                        "\"" + book.getTitle() + "\" was just borrowed by someone else.");
                refresh();
                return;
            }

            main.setStatus("Borrowed \"" + book.getTitle() + "\" \u2022 due " + due);
            main.toastSuccess("Borrowed \"" + book.getTitle() + "\" \u2022 due " + due);
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not borrow the book", ex.getMessage());
        }
    }
}
