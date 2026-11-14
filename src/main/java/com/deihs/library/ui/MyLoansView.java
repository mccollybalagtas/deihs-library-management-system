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
 */
public class MyLoansView extends VBox {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final MainView main;
    private final BookDao dao = new BookDao();

    private final ObservableList<Book> myRows = FXCollections.observableArrayList();
    private final ObservableList<Book> availableRows = FXCollections.observableArrayList();
    private final TableView<Book> myTable = new TableView<>(myRows);
    private final TableView<Book> availableTable = new TableView<>(availableRows);
    private final TextField search = new TextField();
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::reloadAvailable);
    private final Label myCount = new Label();
    private final Label availableCount = new Label();
    private final Label limitNote = new Label();
    private final Label loansValue = new Label("0 / " + Book.MAX_LOANS);
    private final Label dueSoonValue = new Label("0");
    private final Label overdueValue = new Label("0");

    private boolean atLimit;

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
        BookRows.markUrgency(myTable);

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

        TableColumn<Book, String> borrowedCol = new TableColumn<>("Borrowed");
        borrowedCol.setCellValueFactory(data -> new SimpleStringProperty(
                format(data.getValue().getBorrowedAt())));
        borrowedCol.setMinWidth(100);

        TableColumn<Book, String> dueCol = DueColumn.create("Due");
        dueCol.setMinWidth(120);

        TableColumn<Book, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().loanStatusText()));
        statusCol.setMinWidth(130);
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

    private Node buildAvailableCard() {
        Label title = new Label("Available to borrow");
        title.getStyleClass().add("h2");

        availableCount.getStyleClass().add("muted");
        availableCount.setFont(Font.font("SansSerif", 11));

        search.setPromptText("Search the shelves\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(34);
        search.setPrefWidth(240);
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

    private EmptyState myEmptyState() {
        return new EmptyState("\u21C4", "No books on your shelf yet",
                "Borrow a title from the list below and it will appear here with its due date.")
                .action("Browse available books", this::focusAvailable);
    }

    private EmptyState availableEmptyState() {
        String query = search.getText() == null ? "" : search.getText().trim();
        if (!query.isEmpty()) {
            return new EmptyState("\u2315", "No matches for \u201C" + query + "\u201D",
                    "Try a shorter title, another author, or a different spelling.")
                    .action("Clear search", search::clear);
        }
        if (atLimit) {
            return new EmptyState("\u2713", "Your shelf is full",
                    "You already hold " + Book.MAX_LOANS + " books. Return one to borrow another.")
                    .action("Go to my books", this::focusMine);
        }
        return new EmptyState("\u25A4", "Every title is out on loan",
                "All copies are currently with other readers. Check back after a return.");
    }

    private void focusAvailable() {
        availableTable.requestFocus();
        availableTable.getSelectionModel().clearSelection();
        availableTable.scrollTo(0);
    }

    private void focusMine() {
        myTable.requestFocus();
        myTable.getSelectionModel().clearSelection();
        myTable.scrollTo(0);
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    /** Ctrl+F jumps to this page's search box. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    public void refresh() {
        try {
            List<Book> mine = dao.findByBorrower(main.user().getId());
            myRows.setAll(mine);

            long dueSoon = mine.stream().filter(Book::isDueSoon).count();
            long overdue = mine.stream().filter(Book::isOverdue).count();
            loansValue.setText(mine.size() + " / " + Book.MAX_LOANS);
            dueSoonValue.setText(String.valueOf(dueSoon));
            overdueValue.setText(String.valueOf(overdue));

            myCount.setText(mine.size() + (mine.size() == 1 ? " book" : " books") + " on loan");
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
        reloadAvailable();
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
            availableTable.refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load the shelves", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void borrow(Book book) {
        try {
            int count = dao.countByBorrower(main.user().getId());
            if (count >= Book.MAX_LOANS) {
                Dialogs.error(main.window(), "Borrowing limit reached",
                        "You already have " + count + " of " + Book.MAX_LOANS
                                + " allowed books.\nReturn one before borrowing another.");
                refresh();
                return;
            }
            LocalDate due = LocalDate.now().plusDays(Book.LOAN_DAYS);
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

    private static String format(LocalDateTime value) {
        return value == null ? "\u2014" : value.format(DATE);
    }
}
