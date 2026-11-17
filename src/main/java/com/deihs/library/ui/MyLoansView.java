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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * A student's own shelf: the books they currently hold, with return buttons and
 * due-date warnings.
 *
 * <p>Part of: Student screens. Browsing and borrowing live on the separate
 * {@link AvailableBooksView} page, so this page stays focused on the books the
 * student already has.</p>
 *
 * <p>Used by: MainView, which builds it only for the STUDENT role.<br>
 * Key rules: a student may hold at most Book.MAX_LOANS (3) books, and every
 * self-service loan is due Book.LOAN_DAYS (7) days from today.</p>
 */
public class MyLoansView extends VBox {

    // All dates on this screen use the same readable pattern, e.g. "Mar 4, 2026".
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final MainView main;
    private final BookDao dao = new BookDao();

    // The only table on this page: what I currently hold.
    private final ObservableList<Book> myRows = FXCollections.observableArrayList();
    private final TableView<Book> myTable = new TableView<>(myRows);

    private final Label myCount = new Label();
    private final Label limitNote = new Label();

    // Stat-card values; these are Labels so refresh() can update the text
    // without rebuilding the cards.
    private final Label loansValue = new Label("0 / " + Book.MAX_LOANS);
    private final Label dueSoonValue = new Label("0");
    private final Label overdueValue = new Label("0");

    public MyLoansView(MainView main) {
        this.main = main;
        setSpacing(14);
        setPadding(new Insets(22, 26, 26, 26));

        Node mine = buildMyLoansCard();
        getChildren().addAll(buildToolbar(), buildStats(), mine);
        VBox.setVgrow(mine, Priority.ALWAYS);

        refresh();
    }

    // ============================================================
    // PART: Toolbar and stat cards
    // ============================================================

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

        // The shelf lives on its own page, so this button navigates there
        // instead of just scrolling to a section below.
        Button browse = new Button("\u2795   Browse available books");
        browse.getStyleClass().add("ghost-btn");
        browse.setPrefHeight(40);
        browse.setOnAction(e -> main.navigate("available"));

        Button refresh = new Button("\u21BB   Refresh");
        refresh.getStyleClass().add("ghost-btn");
        refresh.setPrefHeight(40);
        refresh.setOnAction(e -> refresh());

        HBox row = new HBox(10, left, spacer, browse, refresh);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildStats() {
        HBox row = new HBox(14,
                statCard("My Loans", loansValue, "\u21C4", "stat-blue"),
                statCard("Due Soon", dueSoonValue, "\u23F0", "stat-amber"),
                statCard("Overdue", overdueValue, "!", "stat-red"));
        row.setFillHeight(true);
        return row;
    }

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
    // PART: The "Books I have" table
    // ============================================================

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
        myTable.setPrefHeight(420);

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

    private EmptyState myEmptyState() {
        return new EmptyState("\u21C4", "No books on your shelf yet",
                "Browse the available shelves and borrow a title; it will appear here with its due date.")
                .action("Browse available books", () -> main.navigate("available"));
    }

    // ============================================================
    // PART: Loading the student's loans
    // ============================================================

    /**
     * Reloads the student's own loans and recomputes the three stat values and
     * the limit note. Called by the constructor and the Refresh button.
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

            // Step 3: update the limit note; the error colour appears at the limit.
            if (mine.size() >= Book.MAX_LOANS) {
                limitNote.setText("You have reached the " + Book.MAX_LOANS
                        + "-book limit. Return one to borrow another.");
                limitNote.getStyleClass().remove("hint-label");
                limitNote.getStyleClass().add("field-error");
            } else {
                int left = Book.MAX_LOANS - mine.size();
                limitNote.setText(left + (left == 1 ? " more book" : " more books")
                        + " may be borrowed.");
                limitNote.getStyleClass().remove("field-error");
                limitNote.getStyleClass().add("hint-label");
            }
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load your loans", ex.getMessage());
        }
    }

    // ============================================================
    // PART: Self-service return
    // ============================================================

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
