package com.deihs.library.ui;

import com.deihs.library.dao.BookDao;
import com.deihs.library.dao.DaoException;
import com.deihs.library.model.Book;
import com.deihs.library.model.Role;
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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;

/**
 * Landing page: greeting, statistics, search and the newest book covers.
 *
 * Part of: Dashboard page, the first page shown inside MainView.
 * Used by: MainView, which creates it lazily for the "dashboard" key and calls
 *          refresh() and focusSearch() on it.
 * Key rules: the stat cards change with Role.seesLibraryStats(), and the grid
 *            shows at most RECENT_LIMIT covers even when more books match.
 */
public class DashboardView extends ScrollPane {

    // ============================================================
    // PART: Dashboard state
    // ============================================================
    // The grid is a preview, not the full collection page.
    private static final int RECENT_LIMIT = 8;

    private final MainView main;
    private final BookDao dao = new BookDao();

    private final HBox statsRow = new HBox(14);
    private final FlowPane grid = new FlowPane();
    private final Label countLabel = new Label();
    private final StackPane emptyHost = new StackPane();
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    // Typing waits 220ms before querying, so a word does not fire one query per key.
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::refreshGrid);

    // True while the category list is being rebuilt, to stop its listener from
    // triggering a second refresh in the middle of refresh().
    private boolean updatingFilters;

    /**
     * WHAT: Builds the banner, stat row, filters, section header and cover grid,
     * then loads the data.
     * WHY: The dashboard is the landing page, so it must be complete as soon as
     * MainView shows it.
     * CALLED BY: MainView.navigate(), the first time "dashboard" is opened.
     */
    public DashboardView(MainView main) {
        this.main = main;
        // Fit to width keeps the cards and grid aligned with the window edge;
        // horizontal scrolling is disabled because nothing is wider than the page.
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setStyle("-fx-background-color: transparent;");

        // Step 1: one vertical column holds every section, in reading order.
        VBox root = new VBox(18);
        root.setPadding(new Insets(22, 26, 30, 26));
        root.getChildren().addAll(buildBanner(), statsRow, buildFilters(), buildSectionHeader(), buildGridArea());
        setContent(root);

        // Step 2: make the stat cards share the row height, then load the data.
        statsRow.setFillHeight(true);
        refresh();
    }

    /** Shortcut for the signed-in user's role. */
    private Role role() {
        return main.user().getRole();
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------
    // ============================================================
    // PART: Building the dashboard sections
    // ============================================================

    /**
     * WHAT: Builds the greeting banner: welcome text, role-specific subtitle and
     * a decorative book badge on the right.
     * WHY: The first line of the page should tell the user what this page is for
     * in their role.
     * CALLED BY: The constructor.
     */
    private Node buildBanner() {
        Label welcome = new Label("Welcome back, " + main.user().getDisplayName() + "!");
        welcome.setFont(Font.font("SansSerif", FontWeight.BOLD, 23));
        welcome.setTextFill(Color.web("#0D264C"));

        // The subtitle is picked from the role, so each user is told what this
        // page is for in their own terms.
        Label subtitle = new Label(switch (role()) {
            case ADMIN -> "Here is what is happening in the Balagtas DEIHS library today.";
            case LIBRARIAN -> "Monitor borrowed and returned books and keep the circulation desk moving.";
            case STUDENT -> "Browse the shelves and keep track of the books you have borrowed.";
        });
        subtitle.setFont(Font.font("SansSerif", 12));
        subtitle.setTextFill(Color.web("#5B6B80"));

        VBox text = new VBox(5, welcome, subtitle);
        text.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane badge = new StackPane();
        badge.setPrefSize(48, 48);
        badge.setMinSize(48, 48);
        badge.setMaxSize(48, 48);
        badge.setStyle("-fx-background-color: #0D264C; -fx-background-radius: 14;");
        Label icon = new Label("\u25A4");
        icon.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
        icon.setTextFill(Color.web("#64DCEB"));
        badge.getChildren().add(icon);

        HBox banner = new HBox(16, text, spacer, badge);
        banner.getStyleClass().add("banner");
        banner.setAlignment(Pos.CENTER_LEFT);
        return banner;
    }

    /**
     * WHAT: Builds the search field, the category dropdown and, for book
     * managers, the Add Book button.
     * WHY: These are the only controls that change what the cover grid shows.
     * CALLED BY: The constructor.
     */
    private Node buildFilters() {
        // Step 1: the prompt is worded for students, who "search the shelves".
        search.setPromptText(role() == Role.STUDENT
                ? "Search the shelves by title, author, or category\u2026"
                : "Search by title, author, or category\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(40);
        // Every keystroke restarts the debounce timer instead of querying at once.
        search.textProperty().addListener((o, a, b) -> searchDebounce.trigger());
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        // Step 2: the category dropdown is filled later by refresh().
        category.getStyleClass().add("combo-box");
        category.setPrefWidth(190);
        category.setPrefHeight(40);
        category.setValue("All Categories");
        category.valueProperty().addListener((o, a, b) -> {
            // Ignore the change while refresh() is repopulating the list, otherwise
            // the grid would reload with a half-built category list.
            if (!updatingFilters) {
                refreshGrid();
            }
        });

        HBox row = new HBox(10, search, category);
        row.setAlignment(Pos.CENTER_LEFT);

        // Step 3: only book managers may add titles.
        if (role().canManageBooks()) {
            Button add = new Button("+  Add Book");
            add.getStyleClass().add("primary-btn");
            add.setPrefHeight(40);
            add.setOnAction(e -> main.addBook());
            row.getChildren().add(add);
        }
        return row;
    }

    /**
     * WHAT: Builds the "Book Collection" heading row with the match count and a
     * View all link.
     * WHY: The heading wording and the link target both depend on the role, so
     * they are decided here.
     * CALLED BY: The constructor.
     */
    private Node buildSectionHeader() {
        Label title = new Label(role().canManageBooks() ? "Book Collection" : "Browse the Collection");
        title.getStyleClass().add("h2");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        countLabel.getStyleClass().add("muted");
        countLabel.setFont(Font.font("SansSerif", 11));

        Button viewAll = new Button("View all  \u2192");
        viewAll.getStyleClass().add("link");
        viewAll.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        // Managers go to the editable collection page; everyone else to the
        // read-only catalog.
        viewAll.setOnAction(e -> main.navigate(role().canManageBooks() ? "collection" : "catalog"));

        HBox header = new HBox(10, title, spacer, countLabel, viewAll);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 0, 0, 2));
        return header;
    }

    /**
     * WHAT: Creates the FlowPane that holds the book cards, plus the empty-state
     * container that replaces it when nothing matches.
     * WHY: FlowPane wraps cards onto new rows automatically, so the grid adapts to
     * the window width without manual columns.
     * CALLED BY: The constructor.
     */
    private Node buildGridArea() {
        grid.setHgap(16);
        grid.setVgap(16);
        // Preferred wrap length; the real width still follows the parent.
        grid.setPrefWrapLength(900);
        grid.setAlignment(Pos.TOP_LEFT);

        emptyHost.setAlignment(Pos.TOP_CENTER);
        emptyHost.setPadding(new Insets(24, 0, 0, 0));

        // Both children sit in the same column; refreshGrid() shows exactly one.
        VBox area = new VBox(10, grid, emptyHost);
        area.setPadding(new Insets(0, 0, 10, 0));
        return area;
    }

    /** Clears both filters, which reloads the full shelf. */
    private void clearFilters() {
        search.clear();
        category.setValue("All Categories");
    }

    /**
     * WHAT: Chooses the empty-state message for the current situation.
     * WHY: "No books yet" and "nothing matches your search" need different advice,
     * and only a book manager should be offered the Add Book action.
     * CALLED BY: refreshGrid(), when the query returned no books.
     */
    private EmptyState emptyState() {
        String query = search.getText() == null ? "" : search.getText().trim();
        boolean filtered = !query.isEmpty()
                || (category.getValue() != null && !"All Categories".equals(category.getValue()));
        if (filtered) {
            // A filter is active, so offer a way back to the whole shelf.
            return new EmptyState("\u2315", "No titles match",
                    "Try a shorter search or a different category, or clear the filters to see the whole shelf.")
                    .action("Clear filters", this::clearFilters);
        }
        if (role().canManageBooks()) {
            // The library is genuinely empty and this user can fix that.
            return new EmptyState("\u25A4", "The shelves are empty",
                    "Add the first title and its cover to start the collection.")
                    .action("+  Add Book", main::addBook);
        }
        // A student cannot add books, so there is nothing to offer.
        return new EmptyState("\u25A4", "No books yet",
                "The library has not added any titles. Check back soon.");
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------
    // ============================================================
    // PART: Loading and refreshing data
    // ============================================================

    /**
     * WHAT: Ctrl+F jumps to this page's search box and selects what is there.
     * WHY: Typing over an existing query should be one action, not a manual clear.
     * CALLED BY: MainView.focusSearch() when the dashboard is the open page.
     */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    /**
     * WHAT: Reloads the stat cards, the category list and the cover grid.
     * WHY: MainView calls it every time this cached page is reopened, so the
     * numbers reflect books added or borrowed since the last visit.
     * CALLED BY: The constructor and MainView.navigate().
     */
    public void refresh() {
        try {
            // Step 1: staff see library-wide figures; students see their own loans.
            if (role().seesLibraryStats()) {
                BookDao.Stats stats = dao.stats();
                statsRow.getChildren().setAll(
                        statCard("Total Books", String.valueOf(stats.total()), "\u25A4", "stat-blue"),
                        statCard("Available", String.valueOf(stats.available()), "\u2713", "stat-green"),
                        statCard("On Loan", String.valueOf(stats.borrowed()), "\u21C4", "stat-pink"),
                        statCard("Overdue", String.valueOf(stats.overdue()), "!", "stat-red"),
                        statCard("E-Books", String.valueOf(stats.ebook()), "\u25C9", "stat-amber"));
            } else {
                List<Book> mine = dao.findByBorrower(main.user().getId());
                long dueSoon = mine.stream().filter(Book::isDueSoon).count();
                long overdue = mine.stream().filter(Book::isOverdue).count();
                BookDao.Stats stats = dao.stats();
                statsRow.getChildren().setAll(
                        statCard("My Loans", mine.size() + " / " + Book.MAX_LOANS, "\u21C4", "stat-blue"),
                        statCard("Due Soon", String.valueOf(dueSoon), "\u23F0", "stat-amber"),
                        statCard("Overdue", String.valueOf(overdue), "!", "stat-red"),
                        statCard("Available Now", String.valueOf(stats.available()), "\u2713", "stat-green"));
            }

            // Step 2: rebuild the category choices but keep the current selection
            // when it still exists. updatingFilters suppresses the dropdown listener
            // while the list is being replaced.
            updatingFilters = true;
            String selected = category.getValue();
            category.getItems().setAll("All Categories");
            category.getItems().addAll(dao.categories());
            category.setValue(selected == null || !category.getItems().contains(selected)
                    ? "All Categories" : selected);
            updatingFilters = false;
        } catch (DaoException ex) {
            // A database error must not leave the page half-built, so it is reported
            // and the grid still gets a chance to load below.
            Dialogs.error(main.window(), "Dashboard unavailable", ex.getMessage());
        }
        refreshGrid();
    }

    /**
     * WHAT: Queries the books that match the current filters and shows up to
     * RECENT_LIMIT covers.
     * WHY: It is called from three places (refresh, the debounced search and the
     * category dropdown), so the query and display logic lives in one method.
     * CALLED BY: refresh(), searchDebounce and the category value listener.
     */
    private void refreshGrid() {
        try {
            // Step 1: one query applies both the text and the category filter.
            List<Book> books = dao.search(search.getText(), category.getValue());
            int shown = Math.min(books.size(), RECENT_LIMIT);
            // Step 2: rebuild the cards from scratch; this page is only a preview.
            grid.getChildren().clear();
            for (int i = 0; i < shown; i++) {
                grid.getChildren().add(new BookCard(books.get(i), main.window(), main::refreshData,
                        role().canManageBooks()));
            }
            // Step 3: say how many are on screen compared with how many matched.
            countLabel.setText(books.isEmpty()
                    ? "No titles"
                    : "Showing " + shown + " of " + books.size() + " titles");

            // Step 4: show either the grid or the empty state. setManaged(false) is
            // needed as well as setVisible(false), otherwise the hidden node would
            // still take up space.
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
     * WHAT: Builds one stat card with a title, a big value and an icon.
     * WHY: All the cards must look identical, and HBox growth makes them share the
     * row width equally.
     * CALLED BY: refresh(), once per statistic.
     */
    private VBox statCard(String title, String value, String icon, String styleClass) {
        Label titleLabel = new Label(title.toUpperCase(Locale.ROOT));
        titleLabel.getStyleClass().add("stat-title");

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("stat-icon");
        iconLabel.setTextFill(Color.web("#0D264C"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox top = new HBox(6, titleLabel, spacer, iconLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");

        VBox card = new VBox(6, top, valueLabel);
        card.getStyleClass().addAll("stat-card", styleClass);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefHeight(92);
        // ALWAYS growth makes every card share the row width equally.
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }
}
