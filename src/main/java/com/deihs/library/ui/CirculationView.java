package com.deihs.library.ui;

import com.deihs.library.dao.BookDao;
import com.deihs.library.dao.DaoException;
import com.deihs.library.dao.UserDao;
import com.deihs.library.model.Book;
import com.deihs.library.util.Covers;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The circulation desk used by administrators and librarians.
 *
 * <p>A staff member first picks the student the book is for, then issues or
 * returns titles. Students never see this screen; they use {@link MyLoansView}
 * to manage their own loans.</p>
 */
public class CirculationView extends VBox {

    private static final String FILTER_ALL = "All books";
    private static final String FILTER_AVAILABLE = "Available";
    private static final String FILTER_ON_LOAN = "On loan";
    private static final String FILTER_OVERDUE = "Overdue";

    private final MainView main;
    private final BookDao dao = new BookDao();
    private final UserDao userDao = new UserDao();

    private final ObservableList<Book> rows = FXCollections.observableArrayList();
    private final TableView<Book> table = new TableView<>(rows);
    private final TextField search = new TextField();
    private final TextField scan = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<UserDao.Account> studentPicker = new ComboBox<>();
    private final DatePicker duePicker = new DatePicker(LocalDate.now().plusDays(Book.LOAN_DAYS));
    private final Label countLabel = new Label();
    private final Label hintLabel = new Label();
    private final Label studentHint = new Label();
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::reloadRows);

    private boolean updatingFilters;
    private boolean loansAtLimit;

    public CirculationView(MainView main) {
        this.main = main;
        setSpacing(14);
        setPadding(new Insets(22, 26, 26, 26));

        Node toolbar = buildToolbar();
        Node deskRow = buildDeskRow();
        Node filters = buildFilterRow();
        Node tableCard = buildTableCard();

        getChildren().addAll(toolbar, deskRow, filters, tableCard);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        refresh();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private Node buildToolbar() {
        Label subtitle = new Label("Pick a student, then issue or return books. Availability updates immediately.");
        subtitle.getStyleClass().add("muted");
        subtitle.setFont(Font.font("SansSerif", 12));

        countLabel.getStyleClass().add("muted");
        countLabel.setFont(Font.font("SansSerif", 11));

        VBox left = new VBox(3, subtitle, countLabel);

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

    private Node buildDeskRow() {
        Label scanLabel = new Label("Scan");
        scanLabel.getStyleClass().add("field-label");

        scan.setPromptText("Book ID\u2026");
        scan.getStyleClass().addAll("field", "scan-field");
        scan.setPrefWidth(150);
        scan.setPrefHeight(38);
        scan.setTooltip(new Tooltip("Scan or type a book ID and press Enter.\n"
                + "Available books are issued to the chosen student; on-loan books are returned."));
        scan.setOnAction(e -> scanBook());

        Label studentLabel = new Label("Issue to");
        studentLabel.getStyleClass().add("field-label");

        studentPicker.getStyleClass().add("combo-box");
        studentPicker.setPrefWidth(260);
        studentPicker.setPrefHeight(38);
        studentPicker.setPromptText("Choose a student\u2026");
        studentPicker.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(UserDao.Account account) {
                return account == null ? "" : account.displayName() + "  (" + account.username() + ")";
            }

            @Override
            public UserDao.Account fromString(String value) {
                return null;
            }
        });
        studentPicker.valueProperty().addListener((o, a, b) -> updateStudentHint());

        Label dueLabel = new Label("Due date");
        dueLabel.getStyleClass().add("field-label");

        duePicker.getStyleClass().add("field");
        duePicker.setPrefWidth(160);
        duePicker.setPrefHeight(38);
        duePicker.setEditable(false);
        duePicker.setTooltip(new Tooltip("Defaults to " + Book.LOAN_DAYS + " days from today"));

        studentHint.getStyleClass().add("hint-label");
        studentHint.setFont(Font.font("SansSerif", 11));
        studentHint.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, scanLabel, scan, studentLabel, studentPicker, dueLabel, duePicker, spacer, studentHint);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("desk-bar");
        row.setPadding(new Insets(12, 14, 12, 14));
        return row;
    }

    private Node buildFilterRow() {
        search.setPromptText("Search by title, author, or borrower\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(40);
        search.setMaxWidth(Double.MAX_VALUE);
        search.textProperty().addListener((o, a, b) -> searchDebounce.trigger());
        HBox.setHgrow(search, Priority.ALWAYS);

        category.getStyleClass().add("combo-box");
        category.setPrefWidth(175);
        category.setPrefHeight(40);
        category.setValue("All Categories");
        category.valueProperty().addListener((o, a, b) -> {
            if (!updatingFilters) {
                reloadRows();
            }
        });

        statusFilter.getStyleClass().add("combo-box");
        statusFilter.setPrefWidth(140);
        statusFilter.setPrefHeight(40);
        statusFilter.getItems().addAll(FILTER_ALL, FILTER_AVAILABLE, FILTER_ON_LOAN, FILTER_OVERDUE);
        statusFilter.setValue(FILTER_ALL);
        statusFilter.valueProperty().addListener((o, a, b) -> reloadRows());

        Button clear = new Button("Clear");
        clear.getStyleClass().add("ghost-btn");
        clear.setPrefHeight(40);
        clear.setOnAction(e -> clearFilters());

        HBox row = new HBox(10, search, category, statusFilter, clear);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildTableCard() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(EmptyState.host(new EmptyState("\u2315", "No books match these filters",
                "Try a different search, category, or status \u2014 or clear the filters to see everything.")
                .action("Clear filters", this::clearFilters)));
        table.setFixedCellSize(64);
        table.setPrefHeight(460);
        BookRows.markUrgency(table);

        TableColumn<Book, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        titleCol.setMinWidth(220);
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
                Label title = new Label(book.getTitle());
                title.setFont(Font.font("SansSerif", FontWeight.BOLD, 12.5));
                title.setWrapText(false);

                Label author = new Label(book.getAuthor());
                author.getStyleClass().add("muted");
                author.setFont(Font.font("SansSerif", 10.5));

                VBox text = new VBox(2, title, author);
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

        TableColumn<Book, String> borrowerCol = new TableColumn<>("Borrower");
        borrowerCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getBorrower() == null || data.getValue().getBorrower().isBlank()
                        ? "\u2014" : data.getValue().getBorrower()));
        borrowerCol.setMinWidth(130);

        TableColumn<Book, String> dueCol = DueColumn.create("Due");
        dueCol.setMinWidth(115);

        TableColumn<Book, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isBorrowed() ? data.getValue().loanStatusText() : data.getValue().statusText()));
        statusCol.setMinWidth(125);
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
                Label pill = new Label(book.isBorrowed() ? book.loanStatusText() : book.statusText());
                pill.getStyleClass().add(book.isBorrowed() ? book.loanPillStyle() : "pill-available");
                setGraphic(pill);
                setText(null);
            }
        });

        TableColumn<Book, Void> actionCol = new TableColumn<>("Action");
        actionCol.setMinWidth(100);
        actionCol.setMaxWidth(120);
        actionCol.setSortable(false);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button action = new Button();

            {
                action.getStyleClass().add("row-btn");
                action.setOnAction(e -> {
                    Book book = getTableRow() == null ? null : getTableRow().getItem();
                    if (book != null) {
                        if (book.isBorrowed()) {
                            returnBook(book);
                        } else {
                            issueBook(book);
                        }
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
                Book book = getTableRow().getItem();
                action.getStyleClass().remove("row-btn-danger");
                if (book.isBorrowed()) {
                    action.setText("Return");
                    action.getStyleClass().add("row-btn-danger");
                    action.setDisable(false);
                    action.setTooltip(null);
                } else {
                    action.setText("Issue");
                    boolean blocked = studentPicker.getValue() == null || loansAtLimit;
                    action.setDisable(blocked);
                    action.setTooltip(blocked
                            ? new Tooltip(studentPicker.getValue() == null
                                    ? "Choose a student first"
                                    : "This student already has " + Book.MAX_LOANS + " books")
                            : null);
                }
                setGraphic(action);
            }
        });

        List<TableColumn<Book, ?>> columns = new ArrayList<>();
        columns.add(titleCol);
        columns.add(categoryCol);
        columns.add(borrowerCol);
        columns.add(dueCol);
        columns.add(statusCol);
        columns.add(actionCol);
        table.getColumns().setAll(columns);

        VBox card = new VBox(table);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(6, 6, 6, 6));
        VBox.setVgrow(table, Priority.ALWAYS);
        return card;
    }

    private void clearFilters() {
        search.clear();
        category.setValue("All Categories");
        statusFilter.setValue(FILTER_ALL);
    }

    /** Ctrl+F jumps to this page's search box. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    public void refresh() {
        loadStudents();
        try {
            updatingFilters = true;
            String selected = category.getValue();
            category.getItems().setAll("All Categories");
            category.getItems().addAll(dao.categories());
            category.setValue(selected == null || !category.getItems().contains(selected)
                    ? "All Categories" : selected);
            updatingFilters = false;
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load categories", ex.getMessage());
        }
        updateStudentHint();
        reloadRows();
    }

    private void loadStudents() {
        Integer keep = studentPicker.getValue() == null ? null : studentPicker.getValue().id();
        try {
            List<UserDao.Account> students = userDao.listStudents();
            studentPicker.getItems().setAll(students);
            if (keep != null) {
                for (UserDao.Account account : students) {
                    if (account.id() == keep) {
                        studentPicker.setValue(account);
                        break;
                    }
                }
            }
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load students", ex.getMessage());
        }
    }

    private void updateStudentHint() {
        UserDao.Account student = studentPicker.getValue();
        if (student == null) {
            loansAtLimit = false;
            studentHint.setText("No student selected");
            studentHint.getStyleClass().remove("field-error");
            studentHint.getStyleClass().add("hint-label");
        } else {
            try {
                int count = dao.countByBorrower(student.id());
                loansAtLimit = count >= Book.MAX_LOANS;
                studentHint.setText(student.displayName() + " \u2022 " + count + " of "
                        + Book.MAX_LOANS + " books on loan"
                        + (loansAtLimit ? " \u2014 limit reached" : ""));
            } catch (DaoException ex) {
                loansAtLimit = false;
                studentHint.setText(student.displayName());
            }
            studentHint.getStyleClass().remove("field-error");
        }
        table.refresh();
    }

    private void reloadRows() {
        try {
            List<Book> books = dao.search(search.getText(), category.getValue());
            String status = statusFilter.getValue();
            List<Book> filtered = new ArrayList<>();
            for (Book book : books) {
                if (FILTER_AVAILABLE.equals(status) && book.isBorrowed()) {
                    continue;
                }
                if (FILTER_ON_LOAN.equals(status) && !book.isBorrowed()) {
                    continue;
                }
                if (FILTER_OVERDUE.equals(status) && !book.isOverdue()) {
                    continue;
                }
                filtered.add(book);
            }
            filtered.sort(Comparator
                    .comparingInt(CirculationView::urgencyRank)
                    .thenComparing(book -> book.getDueAt() == null
                            ? LocalDateTime.MAX : book.getDueAt())
                    .thenComparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));
            rows.setAll(filtered);
            long borrowed = filtered.stream().filter(Book::isBorrowed).count();
            countLabel.setText(filtered.size() + (filtered.size() == 1 ? " title" : " titles")
                    + "  \u2022  " + borrowed + " out on loan");
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load circulation", ex.getMessage());
        }
    }

    /** Overdue first, then due soon, then other loans, then available titles. */
    private static int urgencyRank(Book book) {
        if (book.isOverdue()) {
            return 0;
        }
        if (book.isDueSoon()) {
            return 1;
        }
        return book.isBorrowed() ? 2 : 3;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /**
     * Handles a barcode scan or typed book ID. On-loan copies are returned;
     * available copies are issued to the student chosen above.
     */
    private void scanBook() {
        String raw = scan.getText() == null ? "" : scan.getText().trim();
        if (raw.isEmpty()) {
            return;
        }
        int id;
        try {
            id = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            main.toastError("Book IDs are numbers \u2014 scan the label or type the ID shown in the catalog.");
            scan.selectAll();
            return;
        }
        Book book;
        try {
            book = dao.findById(id);
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not look up that book", ex.getMessage());
            return;
        }
        if (book == null) {
            main.toastError("No book has ID " + id + ". Check the label and try again.");
            scan.selectAll();
            return;
        }
        boolean handled;
        if (book.isBorrowed()) {
            handled = returnBook(book);
        } else if (studentPicker.getValue() == null) {
            main.toastError("Choose a student first, then scan \"" + book.getTitle() + "\".");
            studentPicker.requestFocus();
            scan.selectAll();
            return;
        } else {
            handled = issueBook(book);
        }
        if (handled) {
            scan.clear();
        }
        javafx.application.Platform.runLater(scan::requestFocus);
    }

    private boolean issueBook(Book book) {
        UserDao.Account student = studentPicker.getValue();
        if (student == null) {
            Dialogs.error(main.window(), "Choose a student",
                    "Select the student who is borrowing the book first.");
            return false;
        }
        LocalDate due = duePicker.getValue();
        if (due == null) {
            Dialogs.error(main.window(), "Choose a due date",
                    "Pick the date the book must be returned.");
            return false;
        }
        if (due.isBefore(LocalDate.now())) {
            Dialogs.error(main.window(), "Due date is in the past",
                    "Choose today or a later date for the due date.");
            return false;
        }
        try {
            int count = dao.countByBorrower(student.id());
            if (count >= Book.MAX_LOANS) {
                Dialogs.error(main.window(), "Borrowing limit reached",
                        student.displayName() + " already has " + count + " of "
                                + Book.MAX_LOANS + " allowed books.\n"
                                + "Return one before issuing another.");
                return false;
            }
            boolean ok = dao.checkout(book.getId(), student.id(), student.displayName(),
                    due.atTime(23, 59));
            if (!ok) {
                Dialogs.error(main.window(), "Book already on loan",
                        "\"" + book.getTitle() + "\" was just issued to another reader.");
                refresh();
                return false;
            }
            main.setStatus("Issued \"" + book.getTitle() + "\" to " + student.displayName()
                    + " \u2022 due " + due);
            main.toastSuccess("Issued \"" + book.getTitle() + "\" to " + student.displayName()
                    + " \u2022 due " + due);
            refresh();
            return true;
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not issue the book", ex.getMessage());
            return false;
        }
    }

    private boolean returnBook(Book book) {
        try {
            boolean ok = dao.checkin(book.getId());
            if (!ok) {
                Dialogs.error(main.window(), "Already returned",
                        "\"" + book.getTitle() + "\" is no longer on loan.");
                refresh();
                return false;
            }
            String who = book.getBorrower() == null || book.getBorrower().isBlank()
                    ? null : book.getBorrower();
            main.setStatus(who == null
                    ? "Returned \"" + book.getTitle() + "\" to the shelves."
                    : "Returned \"" + book.getTitle() + "\" from " + who + ".");
            main.toastSuccess(who == null
                    ? "Returned \"" + book.getTitle() + "\" to the shelves."
                    : "Returned \"" + book.getTitle() + "\" from " + who + ".");
            refresh();
            return true;
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not return the book", ex.getMessage());
            return false;
        }
    }
}
