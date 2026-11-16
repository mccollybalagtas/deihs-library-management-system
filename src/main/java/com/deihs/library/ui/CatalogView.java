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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * The catalog. Administrators can add, edit and remove titles; librarians and
 * students get the same table in read-only form.
 *
 * Part of: Catalog screen — browse, search, filter, and admin CRUD over books.
 * Used by: MainView, which builds one instance for ADMIN, LIBRARIAN and STUDENT.
 * Key rules: every search/filter change re-queries MySQL through
 *            BookDao.search, and the Actions column exists only when the
 *            signed-in role can manage books.
 */
public class CatalogView extends VBox {

    // ============================================================
    // PART: State held for the whole screen
    // ============================================================

    private final MainView main;
    private final BookDao dao = new BookDao();

    // rows is the observable backing list; the TableView redraws itself
    // whenever this list changes.
    private final ObservableList<Book> rows = FXCollections.observableArrayList();
    private final TableView<Book> table = new TableView<>(rows);
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final Label countLabel = new Label();

    // Typing is routed through a debounce so the database is not queried on
    // every keystroke (220 ms after the last key).
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::reloadRows);

    // True while refresh() rebuilds the category list. The combo listener
    // checks this so repopulating the list does not trigger extra queries.
    private boolean updatingFilters;

    // ============================================================
    // PART: Building the screen
    // ============================================================

    /**
     * WHAT: Assembles the toolbar, filter row and table, then loads data.
     * WHY: The constructor is the only entry point; without the final
     *      refresh() the table would stay empty until something else reloads.
     * CALLED BY: MainView when the catalog page is first opened.
     */
    public CatalogView(MainView main) {
        this.main = main;
        setSpacing(14);
        setPadding(new Insets(22, 26, 26, 26));

        Node toolbar = buildToolbar();
        Node filters = buildFilterRow();
        Node tableCard = buildTableCard();

        getChildren().addAll(toolbar, filters, tableCard);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        refresh();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    // ============================================================
    // PART: Building the search bar
    // ============================================================

    /**
     * WHAT: Builds the title/subtitle strip and, for admins, the "Add Book" button.
     * WHY: The subtitle explains whether this user may change records, which
     *      stops staff hunting for buttons they do not have.
     * CALLED BY: The CatalogView constructor.
     */
    private Node buildToolbar() {
        Label subtitle = new Label(main.role().canManageBooks()
                ? "Maintain the library catalog. Every change is saved to MySQL immediately."
                : "Browse every title on the shelves. Only an administrator can change catalog records.");
        subtitle.getStyleClass().add("muted");
        subtitle.setFont(Font.font("SansSerif", 12));

        countLabel.getStyleClass().add("muted");
        countLabel.setFont(Font.font("SansSerif", 11));

        VBox left = new VBox(3, subtitle, countLabel);

        // An empty Region with HGrow pushes the button to the far right.
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, left, spacer);
        row.setAlignment(Pos.CENTER_LEFT);

        // Add Book is created only for roles that may write to the catalog.
        if (main.role().canManageBooks()) {
            Button add = new Button("+  Add Book");
            add.getStyleClass().add("primary-btn");
            add.setPrefHeight(40);
            add.setOnAction(e -> main.addBook());
            row.getChildren().add(add);
        }
        return row;
    }

    /**
     * WHAT: Builds the search field, category dropdown and Clear button.
     * WHY: These are the only inputs that decide which rows are fetched.
     * CALLED BY: The CatalogView constructor.
     */
    private Node buildFilterRow() {
        search.setPromptText("Search by title, author, or category\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(40);
        search.setMaxWidth(Double.MAX_VALUE);

        // Every keystroke restarts the debounce timer instead of querying now.
        search.textProperty().addListener((o, a, b) -> searchDebounce.trigger());
        HBox.setHgrow(search, Priority.ALWAYS);

        category.getStyleClass().add("combo-box");
        category.setPrefWidth(190);
        category.setPrefHeight(40);
        category.setValue("All Categories");

        // A dropdown change is immediate (no debounce) because it is a single
        // deliberate action. updatingFilters suppresses the reload while
        // refresh() is repopulating the item list.
        category.valueProperty().addListener((o, a, b) -> {
            if (!updatingFilters) {
                reloadRows();
            }
        });

        Button clear = new Button("Clear");
        clear.getStyleClass().add("ghost-btn");
        clear.setPrefHeight(40);
        clear.setOnAction(e -> clearFilters());

        HBox row = new HBox(10, search, category, clear);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ============================================================
    // PART: Building the book table
    // ============================================================

    /**
     * WHAT: Creates every column, the placeholder and the card wrapper.
     * WHY: The table is the whole point of the page; column order and cell
     *      factories decide what a student sees per row.
     * CALLED BY: The CatalogView constructor.
     */
    private Node buildTableCard() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // The placeholder is the "nothing here" panel shown when rows is empty.
        table.setPlaceholder(EmptyState.host(catalogEmptyState()));
        table.setFixedCellSize(64);
        table.setPrefHeight(460);

        TableColumn<Book, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        titleCol.setMinWidth(230);

        // Custom cell: a small cover image next to a bold title and a muted
        // author line. updateItem runs every time a row is recycled, so all
        // state must be reset from the current row item.
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

                Label author = new Label(book.getAuthor());
                author.getStyleClass().add("muted");
                author.setFont(Font.font("SansSerif", 10.5));

                VBox text = new VBox(2, title, author);
                text.setAlignment(Pos.CENTER_LEFT);

                HBox box = new HBox(10, Covers.node(book, 34, 44), text);
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

        // The Due column is shared with the circulation and loan screens so the
        // urgency wording and colours stay identical everywhere.
        TableColumn<Book, String> dueCol = DueColumn.create("Due");
        dueCol.setMinWidth(115);

        TableColumn<Book, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isBorrowed() ? data.getValue().loanStatusText() : data.getValue().statusText()));
        statusCol.setMinWidth(130);

        // Status is rendered as a coloured "pill" rather than plain text.
        // The CSS class comes from the book so overdue/soon/available all get
        // the right colour without any if-chain here.
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

        List<TableColumn<Book, ?>> columns = new ArrayList<>();
        columns.add(titleCol);
        columns.add(categoryCol);
        columns.add(typeCol);
        columns.add(detailsCol);
        columns.add(dueCol);
        columns.add(statusCol);

        // The Actions column is appended only for administrators. It is typed
        // Void because it holds buttons, not a value from the Book.
        if (main.role().canManageBooks()) {
            TableColumn<Book, Void> actionCol = new TableColumn<>("Actions");
            actionCol.setMinWidth(150);
            actionCol.setMaxWidth(170);
            actionCol.setSortable(false);
            actionCol.setCellFactory(col -> new TableCell<>() {
                private final Button edit = new Button("Edit");
                private final Button delete = new Button("Delete");
                private final HBox box = new HBox(6, edit, delete);

                // Instance initializer: wires the buttons once per cell object.
                {
                    box.setAlignment(Pos.CENTER_LEFT);
                    edit.getStyleClass().add("row-btn");
                    delete.getStyleClass().addAll("row-btn", "row-btn-danger");
                    edit.setOnAction(e -> editBook(rowBook()));
                    delete.setOnAction(e -> deleteBook(rowBook()));
                }

                // Resolves the Book currently shown in this cell's row. The row
                // can be null while JavaFX is still laying the table out.
                private Book rowBook() {
                    return getTableRow() == null ? null : getTableRow().getItem();
                }

                @Override
                protected void updateItem(Void value, boolean empty) {
                    super.updateItem(value, empty);
                    setGraphic(empty || rowBook() == null ? null : box);
                }
            });
            columns.add(actionCol);
        }

        table.getColumns().setAll(columns);

        VBox card = new VBox(table);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(6, 6, 6, 6));
        VBox.setVgrow(table, Priority.ALWAYS);
        return card;
    }

    /**
     * WHAT: Empties the search box and resets the category to "All Categories".
     * WHY: Both listeners fire reloadRows(), so the table returns to the full list.
     * CALLED BY: The Clear button and the "Clear filters" empty-state button.
     */
    private void clearFilters() {
        search.clear();
        category.setValue("All Categories");
    }

    /**
     * WHAT: Chooses which empty-state message to show for the current filters.
     * WHY: "No matches" and "the catalog is empty" need different advice, and
     *      only an admin can be offered an Add Book button.
     * CALLED BY: buildTableCard (initial placeholder) and reloadRows (after each query).
     */
    private EmptyState catalogEmptyState() {
        // Step 1: decide whether the user is looking at a filtered result.
        String query = search.getText() == null ? "" : search.getText().trim();
        boolean filtered = !query.isEmpty()
                || (category.getValue() != null && !"All Categories".equals(category.getValue()));

        // Step 2: filtered result -> offer a way back to the full list.
        if (filtered) {
            return new EmptyState("\u2315", "No titles match",
                    "Try a broader search term or a different category.")
                    .action("Clear filters", this::clearFilters);
        }

        // Step 3: genuinely empty catalog -> admins get the Add Book shortcut.
        if (main.role().canManageBooks()) {
            return new EmptyState("\u25A4", "The catalog is empty",
                    "Add the first title to start building the collection.")
                    .action("+  Add Book", main::addBook);
        }
        return new EmptyState("\u25A4", "The catalog is empty",
                "No titles have been added yet. Check back soon.");
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    // ============================================================
    // PART: Loading data from MySQL
    // ============================================================

    /**
     * WHAT: Reloads the category dropdown and then the table rows.
     * WHY: Categories come from existing books, so the dropdown must be
     *      rebuilt after any add/edit/delete or it would show stale values.
     * CALLED BY: The constructor, MainView.refreshData(), and nothing else directly.
     */
    public void refresh() {
        try {
            // updatingFilters stops the combo listener firing a query while the
            // item list is being replaced.
            updatingFilters = true;
            String selected = category.getValue();
            category.getItems().setAll("All Categories");
            category.getItems().addAll(dao.categories());

            // Keep the old choice if it still exists, otherwise fall back to All.
            category.setValue(selected == null || !category.getItems().contains(selected)
                    ? "All Categories" : selected);
            updatingFilters = false;
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load categories", ex.getMessage());
        }
        reloadRows();
    }

    /** Ctrl+F jumps to this page's search box. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    /**
     * WHAT: Runs the current search + category against MySQL and replaces rows.
     * WHY: This is the single place the table contents are refreshed, so the
     *      count label and empty state can never disagree with the rows.
     * CALLED BY: The debounce timer, the category listener, refresh().
     */
    private void reloadRows() {
        try {
            // Step 1: ask the DAO for books matching the current filters.
            List<Book> books = dao.search(search.getText(), category.getValue());

            // Step 2: swap the observable list; the TableView repaints itself.
            rows.setAll(books);

            // Step 3: rebuild the placeholder because the message depends on
            // whether the user has active filters.
            table.setPlaceholder(EmptyState.host(catalogEmptyState()));

            // Step 4: keep the "N titles" counter in sync.
            countLabel.setText(books.size() + (books.size() == 1 ? " title in the catalog" : " titles in the catalog"));
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load the catalog", ex.getMessage());
        }
    }

    // ============================================================
    // PART: Administrator actions (edit / delete)
    // ============================================================

    /**
     * WHAT: Opens the book form pre-filled, then saves if the admin confirms.
     * WHY: The role check and null guard stop a stale button click from
     *      writing after the user's role changed or the row was recycled.
     * CALLED BY: The Edit button in the Actions column.
     */
    private void editBook(Book book) {
        if (book == null || !main.role().canManageBooks()) {
            return;
        }

        // show() returns null when the dialog is cancelled, so nothing is saved.
        Book edited = BookFormView.show(main.window(), book);
        if (edited == null) {
            return;
        }
        try {
            dao.update(edited);
            main.setStatus("Saved changes to \"" + edited.getTitle() + "\".");
            main.toastSuccess("Saved changes to \"" + edited.getTitle() + "\".");
            main.refreshData();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not save the book", ex.getMessage());
        }
    }

    /**
     * WHAT: Removes a title after two safety checks and a confirm dialog.
     * WHY: Deleting a book that is on loan would leave the borrower with no
     *      record, and deleting is irreversible, so both are blocked first.
     * CALLED BY: The Delete button in the Actions column.
     */
    private void deleteBook(Book book) {
        if (book == null || !main.role().canManageBooks()) {
            return;
        }

        // Step 1: refuse while the copy is out on loan.
        if (book.isBorrowed()) {
            String who = book.getBorrower() == null || book.getBorrower().isBlank()
                    ? "a reader" : book.getBorrower();
            Dialogs.error(main.window(), "Book is on loan",
                    "\"" + book.getTitle() + "\" is currently with " + who + ".\n\n"
                            + "Return it from the circulation desk before removing it.");
            return;
        }

        // Step 2: explicit destructive confirmation, defaulting to Cancel.
        boolean confirmed = Dialogs.confirmDestructive(main.window(), "Remove book",
                "Remove \"" + book.getTitle() + "\" from the catalog?\n\n"
                        + "Its cover image is deleted too. This cannot be undone.",
                "Remove book");
        if (!confirmed) {
            return;
        }
        try {
            // Step 3: delete the row. A false result means a race: someone
            // issued the book between the check above and this call.
            boolean removed = dao.delete(book.getId());
            if (!removed) {
                Dialogs.error(main.window(), "Could not remove the book",
                        "\"" + book.getTitle() + "\" was just issued to a reader.\n"
                                + "Return it first, then try again.");
                main.refreshData();
                return;
            }

            // Step 4: the DB row is gone, so clean up the cover file too.
            Covers.delete(book.getCoverFile());
            main.setStatus("Removed \"" + book.getTitle() + "\" from the catalog.");
            main.toastSuccess("Removed \"" + book.getTitle() + "\" from the catalog.");
            main.refreshData();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not remove the book", ex.getMessage());
        }
    }
}
