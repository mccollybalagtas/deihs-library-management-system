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
 *
 * Part of: Circulation desk — issuing and returning books on behalf of students.
 * Used by: MainView, which builds it for ADMIN and LIBRARIAN roles only.
 * Key rules: a student may hold at most Book.MAX_LOANS (3) books, and the
 *            issue path re-checks that limit against MySQL before writing.
 */
public class CirculationView extends VBox {

    // ============================================================
    // PART: Status filter labels
    // ============================================================

    // These strings double as the ComboBox item text and the comparison keys
    // inside reloadRows(), so they are constants to avoid typos.
    private static final String FILTER_ALL = "All books";
    private static final String FILTER_AVAILABLE = "Available";
    private static final String FILTER_ON_LOAN = "On loan";
    private static final String FILTER_OVERDUE = "Overdue";

    // ============================================================
    // PART: State held for the whole screen
    // ============================================================

    private final MainView main;
    private final BookDao dao = new BookDao();
    private final UserDao userDao = new UserDao();

    private final ObservableList<Book> rows = FXCollections.observableArrayList();
    private final TableView<Book> table = new TableView<>(rows);
    private final TextField search = new TextField();

    // The scan box accepts a typed or scanned book ID and acts on Enter.
    private final TextField scan = new TextField();

    private final ComboBox<String> category = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<UserDao.Account> studentPicker = new ComboBox<>();

    // Due date defaults to LOAN_DAYS (7) days from today; staff can change it.
    private final DatePicker duePicker = new DatePicker(LocalDate.now().plusDays(Book.LOAN_DAYS));
    private final Label countLabel = new Label();
    private final Label hintLabel = new Label();
    private final Label studentHint = new Label();
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::reloadRows);

    // True while refresh() rebuilds the category list (suppresses listener reloads).
    private boolean updatingFilters;

    // True when the selected student already holds MAX_LOANS books. Kept as a
    // field so each recycled table cell can disable its Issue button.
    private boolean loansAtLimit;

    // ============================================================
    // PART: Building the screen
    // ============================================================

    /**
     * WHAT: Builds the toolbar, desk bar, filter row and table, then loads data.
     * WHY: The desk bar must exist before the table because table cells read
     *      the student picker and the limit flag.
     * CALLED BY: MainView when a librarian opens the circulation page.
     */
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

    // ============================================================
    // PART: Building the toolbar and desk bar
    // ============================================================

    /**
     * WHAT: Builds the subtitle/counter strip and a manual Refresh button.
     * WHY: Refresh is needed when another workstation issues a book, because
     *      this screen otherwise only reloads after a local action.
     * CALLED BY: The CirculationView constructor.
     */
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

    /**
     * WHAT: Builds the scan box, student picker, due-date picker and hint label.
     * WHY: These three inputs are what issueBook() and scanBook() read; the hint
     *      shows the student's current loan count before staff commit.
     * CALLED BY: The CirculationView constructor.
     */
    private Node buildDeskRow() {
        Label scanLabel = new Label("Scan");
        scanLabel.getStyleClass().add("field-label");

        scan.setPromptText("Book ID\u2026");
        scan.getStyleClass().addAll("field", "scan-field");
        scan.setPrefWidth(150);
        scan.setPrefHeight(38);
        scan.setTooltip(new Tooltip("Scan or type a book ID and press Enter.\n"
                + "Available books are issued to the chosen student; on-loan books are returned."));

        // Enter in the scan box triggers the one-field workflow.
        scan.setOnAction(e -> scanBook());

        Label studentLabel = new Label("Issue to");
        studentLabel.getStyleClass().add("field-label");

        studentPicker.getStyleClass().add("combo-box");
        studentPicker.setPrefWidth(260);
        studentPicker.setPrefHeight(38);
        studentPicker.setPromptText("Choose a student\u2026");

        // The picker holds Account objects, so a converter decides how each one
        // is printed. fromString returns null because typing a name is not
        // supported: staff must pick from the list.
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

        // Recompute the loan-count hint whenever the chosen student changes.
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

    /**
     * WHAT: Builds the search field, category filter, status filter and Clear.
     * WHY: The status filter is applied in memory after the DB query, so it can
     *      change without touching MySQL.
     * CALLED BY: The CirculationView constructor.
     */
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

        // No debounce here: a status change is one deliberate click.
        statusFilter.valueProperty().addListener((o, a, b) -> reloadRows());

        Button clear = new Button("Clear");
        clear.getStyleClass().add("ghost-btn");
        clear.setPrefHeight(40);
        clear.setOnAction(e -> clearFilters());

        HBox row = new HBox(10, search, category, statusFilter, clear);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ============================================================
    // PART: Building the circulation table
    // ============================================================

    /**
     * WHAT: Creates the book table with borrower, due and an Issue/Return button.
     * WHY: One table serves both directions of the workflow; the button label
     *      and enabled state are decided per row in updateItem.
     * CALLED BY: The CirculationView constructor.
     */
    private Node buildTableCard() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(EmptyState.host(new EmptyState("\u2315", "No books match these filters",
                "Try a different search, category, or status \u2014 or clear the filters to see everything.")
                .action("Clear filters", this::clearFilters)));
        table.setFixedCellSize(64);
        table.setPrefHeight(460);

        // Adds the coloured left edge so overdue and due-soon rows stand out.
        BookRows.markUrgency(table);

        TableColumn<Book, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        titleCol.setMinWidth(220);

        // Cover + bold title + muted author, rebuilt for every recycled row.
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

        // The status pill gets its colour from the book, so overdue/soon/out/
        // available are all styled by CSS instead of branching here.
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

            // One button serves both directions; the click reads the current
            // row and calls returnBook or issueBook accordingly.
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

                // Cells are recycled, so always reset the danger style first.
                action.getStyleClass().remove("row-btn-danger");
                if (book.isBorrowed()) {
                    // On loan: the only useful action is returning it.
                    action.setText("Return");
                    action.getStyleClass().add("row-btn-danger");
                    action.setDisable(false);
                    action.setTooltip(null);
                } else {
                    // Available: issuing needs a chosen student and spare quota.
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

    /**
     * WHAT: Resets search, category and status filters to their defaults.
     * WHY: Each reset fires a listener, so the table reloads automatically.
     * CALLED BY: The Clear button and the table's empty-state action.
     */
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

    // ============================================================
    // PART: Loading students, categories and rows
    // ============================================================

    /**
     * WHAT: Reloads students, categories and table rows in one pass.
     * WHY: After every issue or return the loan counts change, so all three
     *      views of that state must be refreshed together.
     * CALLED BY: The constructor, the Refresh button, issueBook, returnBook.
     */
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

    /**
     * WHAT: Reloads the student picker while keeping the current selection.
     * WHY: Refreshing the item list resets the combo value, which would silently
     *      change who the next scan is issued to.
     * CALLED BY: refresh().
     */
    private void loadStudents() {
        // Step 1: remember the selected student by ID, not by object identity.
        Integer keep = studentPicker.getValue() == null ? null : studentPicker.getValue().id();
        try {
            List<UserDao.Account> students = userDao.listStudents();
            studentPicker.getItems().setAll(students);

            // Step 2: re-select the same ID if it still exists in the new list.
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

    /**
     * WHAT: Recounts the selected student's loans and updates the hint text.
     * WHY: The 3-book limit is decided here so the Issue buttons can be
     *      disabled before staff click, not only after the DB rejects it.
     * CALLED BY: The student picker listener and refresh().
     */
    private void updateStudentHint() {
        UserDao.Account student = studentPicker.getValue();
        if (student == null) {
            // No student chosen: nothing can be issued yet.
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
                // If the count cannot be read, do not block issuing; the DB
                // check inside issueBook still enforces the limit.
                loansAtLimit = false;
                studentHint.setText(student.displayName());
            }
            studentHint.getStyleClass().remove("field-error");
        }

        // Re-run every row's updateItem so Issue buttons pick up the new flag.
        table.refresh();
    }

    /**
     * WHAT: Queries books, applies the status filter, sorts by urgency, and
     *       updates the rows plus the counter.
     * WHY: BookDao.search only knows text + category, so the status filter and
     *      the "overdue first" ordering live here.
     * CALLED BY: The debounce, the category and status listeners, refresh().
     */
    private void reloadRows() {
        try {
            // Step 1: fetch books matching the text search and category.
            List<Book> books = dao.search(search.getText(), category.getValue());

            // Step 2: apply the in-memory status filter.
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

            // Step 3: sort so the most urgent loans sit at the top. Ties are
            // broken by the earliest due date, then alphabetically by title.
            // Books with no due date sort last (LocalDateTime.MAX).
            filtered.sort(Comparator
                    .comparingInt(CirculationView::urgencyRank)
                    .thenComparing(book -> book.getDueAt() == null
                            ? LocalDateTime.MAX : book.getDueAt())
                    .thenComparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));
            rows.setAll(filtered);

            // Step 4: update the header counter.
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

    // ============================================================
    // PART: Scan workflow
    // ============================================================

    /**
     * Handles a barcode scan or typed book ID. On-loan copies are returned;
     * available copies are issued to the student chosen above.
     *
     * WHAT: Turns a scanned/typed ID into an issue or a return.
     * WHY: Staff work from the physical book, not the table, so this is the
     *      fastest path and must validate the ID before touching the DB.
     * CALLED BY: The Enter key on the scan field.
     */
    private void scanBook() {
        // Step 1: ignore an empty scan (Enter with nothing typed).
        String raw = scan.getText() == null ? "" : scan.getText().trim();
        if (raw.isEmpty()) {
            return;
        }

        // Step 2: the ID must be a number; anything else is a misread label.
        int id;
        try {
            id = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            main.toastError("Book IDs are numbers \u2014 scan the label or type the ID shown in the catalog.");
            scan.selectAll();
            return;
        }

        // Step 3: look the book up so the branch below works on fresh data.
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

        // Step 4: route by the book's current state.
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

        // Step 5: only clear the field on success, so a failed scan can be
        // corrected instead of retyped. Focus returns for the next book.
        if (handled) {
            scan.clear();
        }
        javafx.application.Platform.runLater(scan::requestFocus);
    }

    // ============================================================
    // PART: Issue and return
    // ============================================================

    /**
     * WHAT: Issues one book to the selected student for the chosen due date.
     * WHY: The limit and date checks must run again here because scanBook and
     *      the row button both reach this method.
     * CALLED BY: The Issue row button and scanBook().
     *
     * @return true when the loan was written, false when it was refused.
     */
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
            // Step 1: re-check the concurrent-loan limit against the database,
            // because the hint may be stale if another desk issued a book.
            int count = dao.countByBorrower(student.id());
            if (count >= Book.MAX_LOANS) {
                Dialogs.error(main.window(), "Borrowing limit reached",
                        student.displayName() + " already has " + count + " of "
                                + Book.MAX_LOANS + " allowed books.\n"
                                + "Return one before issuing another.");
                return false;
            }

            // Step 2: write the loan. due.atTime(23, 59) means the book is
            // late only after the end of the chosen day.
            boolean ok = dao.checkout(book.getId(), student.id(), student.displayName(),
                    due.atTime(23, 59));
            if (!ok) {
                // The UPDATE matched no row: someone else issued it first.
                Dialogs.error(main.window(), "Book already on loan",
                        "\"" + book.getTitle() + "\" was just issued to another reader.");
                refresh();
                return false;
            }

            // Step 3: report success and reload every dependent view.
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

    /**
     * WHAT: Marks a book as returned and clears its borrower/due date.
     * WHY: checkin returns false when the book was already returned, which can
     *      happen when two desks work the same cart.
     * CALLED BY: The Return row button and scanBook().
     *
     * @return true when the book was actually checked in.
     */
    private boolean returnBook(Book book) {
        try {
            boolean ok = dao.checkin(book.getId());
            if (!ok) {
                Dialogs.error(main.window(), "Already returned",
                        "\"" + book.getTitle() + "\" is no longer on loan.");
                refresh();
                return false;
            }

            // Read the borrower before refresh() replaces the row object.
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
