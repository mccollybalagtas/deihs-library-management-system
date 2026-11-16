package com.deihs.library.ui;

import com.deihs.library.dao.BookDao;
import com.deihs.library.dao.DaoException;
import com.deihs.library.model.Book;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.util.List;

/**
 * Full cover grid with search and category filtering.
 *
 * Part of: Collection screen — a scrollable cover grid of every title.
 * Used by: MainView, which builds it for ADMIN and LIBRARIAN roles.
 * Key rules: an empty result swaps the whole grid for an EmptyState panel,
 *            and each tile is a BookCard that edits only for admins.
 */
public class CollectionPanel extends VBox {

    // ============================================================
    // PART: State held for the whole panel
    // ============================================================

    private final MainView main;
    private final BookDao dao = new BookDao();

    // FlowPane wraps cards onto new rows as the window widens.
    private final FlowPane grid = new FlowPane();
    private final Label countLabel = new Label();

    // Holds the EmptyState when the grid has nothing to show.
    private final StackPane emptyHost = new StackPane();
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::refreshGrid);

    // True while refresh() rebuilds the category list (suppresses listener reloads).
    private boolean updatingFilters;

    // ============================================================
    // PART: Building the panel
    // ============================================================

    /**
     * WHAT: Adds the toolbar, filter row and scrollable grid area, then loads.
     * WHY: The grid is inside index 2, which is the child given vertical growth.
     * CALLED BY: MainView when the collection page is opened.
     */
    public CollectionPanel(MainView main) {
        this.main = main;
        setSpacing(14);
        setPadding(new Insets(22, 26, 26, 26));

        getChildren().addAll(buildToolbar(), buildFilterRow(), buildGridArea());
        VBox.setVgrow(getChildren().get(2), Priority.ALWAYS);

        refresh();
    }

    // ============================================================
    // PART: Toolbar and filters
    // ============================================================

    /**
     * WHAT: Builds the subtitle, counter and (for admins) the Add Book button.
     * WHY: Adding a title is an admin-only action, so the button is not created
     *      for librarians at all rather than being hidden.
     * CALLED BY: The CollectionPanel constructor.
     */
    private Node buildToolbar() {
        Label subtitle = new Label("Every title on the shelves, with covers and live availability.");
        subtitle.setFont(Font.font("SansSerif", 12));
        subtitle.getStyleClass().add("muted");

        countLabel.getStyleClass().add("muted");
        countLabel.setFont(Font.font("SansSerif", 11));

        VBox left = new VBox(3, subtitle, countLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, left, spacer);
        row.setAlignment(Pos.CENTER_LEFT);

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
     * WHAT: Builds the search box, category dropdown and Clear button.
     * WHY: These decide which cards are fetched; typing is debounced so a
     *      keystroke does not trigger a database query.
     * CALLED BY: The CollectionPanel constructor.
     */
    private Node buildFilterRow() {
        search.setPromptText("Search by title, author, or category\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(40);
        search.setMaxWidth(Double.MAX_VALUE);

        // Each keystroke restarts the 220 ms timer instead of querying now.
        search.textProperty().addListener((o, a, b) -> searchDebounce.trigger());
        HBox.setHgrow(search, Priority.ALWAYS);

        category.getStyleClass().add("combo-box");
        category.setPrefWidth(190);
        category.setPrefHeight(40);
        category.setValue("All Categories");

        // Dropdown changes are immediate. updatingFilters blocks the reload
        // while refresh() is repopulating the item list.
        category.valueProperty().addListener((o, a, b) -> {
            if (!updatingFilters) {
                refreshGrid();
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

    /**
     * WHAT: Builds the wrapping FlowPane, the empty-state host and the ScrollPane.
     * WHY: The grid must wrap and scroll on small screens; both the grid and the
     *      empty panel live in the same column so one can replace the other.
     * CALLED BY: The CollectionPanel constructor.
     */
    private Node buildGridArea() {
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setPrefWrapLength(900);
        grid.setAlignment(Pos.TOP_LEFT);
        grid.setPadding(new Insets(4, 2, 12, 2));

        emptyHost.setAlignment(Pos.TOP_CENTER);
        emptyHost.setPadding(new Insets(30, 0, 0, 0));

        VBox area = new VBox(12, grid, emptyHost);
        area.setPadding(new Insets(2, 0, 0, 0));

        ScrollPane scroll = new ScrollPane(area);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent;");
        return scroll;
    }

    // ============================================================
    // PART: Loading data
    // ============================================================

    /**
     * WHAT: Reloads the category dropdown, then the grid.
     * WHY: Categories are derived from existing books, so they go stale after
     *      any add/edit/delete.
     * CALLED BY: The constructor and MainView.refreshData().
     */
    public void refresh() {
        try {
            // updatingFilters stops the combo listener firing a query mid-rebuild.
            updatingFilters = true;
            String selected = category.getValue();
            category.getItems().setAll("All Categories");
            category.getItems().addAll(dao.categories());

            // Keep the old choice when it still exists, else reset to All.
            category.setValue(selected == null || !category.getItems().contains(selected)
                    ? "All Categories" : selected);
            updatingFilters = false;
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load categories", ex.getMessage());
        }
        refreshGrid();
    }

    /**
     * WHAT: Rebuilds every BookCard from the current filters.
     * WHY: Cards are recreated rather than updated because the grid is small and
     *      rebuilding keeps each tile's data and styling consistent.
     * CALLED BY: The debounce timer, the category listener, refresh().
     */
    private void refreshGrid() {
        try {
            // Step 1: query books with the current search text and category.
            List<Book> books = dao.search(search.getText(), category.getValue());

            // Step 2: throw away the old tiles and build one card per book.
            // The last argument enables click-to-edit for admins only.
            grid.getChildren().clear();
            for (Book book : books) {
                grid.getChildren().add(new BookCard(book, main.window(), main::refreshData,
                        main.role().canManageBooks()));
            }
            countLabel.setText(books.size() + (books.size() == 1 ? " title" : " titles"));

            // Step 3: swap between the grid and the empty panel. Setting both
            // visible and managed keeps the layout from reserving space for the
            // hidden one.
            boolean empty = books.isEmpty();
            emptyHost.getChildren().setAll(emptyState());
            emptyHost.setVisible(empty);
            emptyHost.setManaged(empty);
            grid.setVisible(!empty);
            grid.setManaged(!empty);
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load books", ex.getMessage());
        }
    }

    /**
     * WHAT: Resets the search text and category.
     * WHY: Both listeners reload the grid, so the full collection returns.
     * CALLED BY: The Clear button and the "Clear filters" empty-state action.
     */
    private void clearFilters() {
        search.clear();
        category.setValue("All Categories");
    }

    /** Ctrl+F jumps to this page's search box. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    /**
     * WHAT: Chooses the empty message for the current filter state.
     * WHY: "No matches" needs a clear-filters action, while a truly empty
     *      collection needs an Add Book action for admins.
     * CALLED BY: refreshGrid().
     */
    private EmptyState emptyState() {
        // Step 1: is the user seeing a filtered result or the whole collection?
        String query = search.getText() == null ? "" : search.getText().trim();
        boolean filtered = !query.isEmpty()
                || (category.getValue() != null && !"All Categories".equals(category.getValue()));

        // Step 2: filtered with no hits -> offer a way back.
        if (filtered) {
            return new EmptyState("\u2315", "No titles match",
                    "Try a shorter search, a different category, or clear the filters to see every book.")
                    .action("Clear filters", this::clearFilters);
        }

        // Step 3: empty collection -> admins can add the first title.
        if (main.role().canManageBooks()) {
            return new EmptyState("\u25A4", "The shelves are empty",
                    "Add the first title and its cover to start the collection.")
                    .action("+  Add Book", main::addBook);
        }
        return new EmptyState("\u25A4", "No books yet",
                "The library has not added any titles. Check back soon.");
    }
}
