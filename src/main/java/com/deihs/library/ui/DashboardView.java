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

import java.util.List;
import java.util.Locale;

/** Landing page: greeting, statistics, search and the newest book covers. */
public class DashboardView extends ScrollPane {

    private static final int RECENT_LIMIT = 8;

    private final MainView main;
    private final BookDao dao = new BookDao();

    private final HBox statsRow = new HBox(14);
    private final FlowPane grid = new FlowPane();
    private final Label countLabel = new Label();
    private final StackPane emptyHost = new StackPane();
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();

    private boolean updatingFilters;

    public DashboardView(MainView main) {
        this.main = main;
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setStyle("-fx-background-color: transparent;");

        VBox root = new VBox(18);
        root.setPadding(new Insets(22, 26, 30, 26));
        root.getChildren().addAll(buildBanner(), statsRow, buildFilters(), buildSectionHeader(), buildGridArea());
        setContent(root);

        statsRow.setFillHeight(true);
        refresh();
    }

    private Role role() {
        return main.user().getRole();
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private Node buildBanner() {
        Label welcome = new Label("Welcome back, " + main.user().getDisplayName() + "!");
        welcome.setFont(Font.font("SansSerif", FontWeight.BOLD, 23));
        welcome.setTextFill(Color.web("#0D264C"));

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

    private Node buildFilters() {
        search.setPromptText(role() == Role.STUDENT
                ? "Search the shelves by title, author, or category\u2026"
                : "Search by title, author, or category\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(40);
        search.textProperty().addListener((o, a, b) -> refreshGrid());
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        category.getStyleClass().add("combo-box");
        category.setPrefWidth(190);
        category.setPrefHeight(40);
        category.setValue("All Categories");
        category.valueProperty().addListener((o, a, b) -> {
            if (!updatingFilters) {
                refreshGrid();
            }
        });

        HBox row = new HBox(10, search, category);
        row.setAlignment(Pos.CENTER_LEFT);

        if (role().canManageBooks()) {
            Button add = new Button("+  Add Book");
            add.getStyleClass().add("primary-btn");
            add.setPrefHeight(40);
            add.setOnAction(e -> main.addBook());
            row.getChildren().add(add);
        }
        return row;
    }

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
        viewAll.setOnAction(e -> main.navigate(role().canManageBooks() ? "collection" : "catalog"));

        HBox header = new HBox(10, title, spacer, countLabel, viewAll);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 0, 0, 2));
        return header;
    }

    private Node buildGridArea() {
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setPrefWrapLength(900);
        grid.setAlignment(Pos.TOP_LEFT);

        emptyHost.setAlignment(Pos.TOP_CENTER);
        emptyHost.setPadding(new Insets(24, 0, 0, 0));

        VBox area = new VBox(10, grid, emptyHost);
        area.setPadding(new Insets(0, 0, 10, 0));
        return area;
    }

    private void clearFilters() {
        search.clear();
        category.setValue("All Categories");
    }

    private EmptyState emptyState() {
        String query = search.getText() == null ? "" : search.getText().trim();
        boolean filtered = !query.isEmpty()
                || (category.getValue() != null && !"All Categories".equals(category.getValue()));
        if (filtered) {
            return new EmptyState("\u2315", "No titles match",
                    "Try a shorter search or a different category, or clear the filters to see the whole shelf.")
                    .action("Clear filters", this::clearFilters);
        }
        if (role().canManageBooks()) {
            return new EmptyState("\u25A4", "The shelves are empty",
                    "Add the first title and its cover to start the collection.")
                    .action("+  Add Book", main::addBook);
        }
        return new EmptyState("\u25A4", "No books yet",
                "The library has not added any titles. Check back soon.");
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    public void refresh() {
        try {
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

            updatingFilters = true;
            String selected = category.getValue();
            category.getItems().setAll("All Categories");
            category.getItems().addAll(dao.categories());
            category.setValue(selected == null || !category.getItems().contains(selected)
                    ? "All Categories" : selected);
            updatingFilters = false;
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Dashboard unavailable", ex.getMessage());
        }
        refreshGrid();
    }

    private void refreshGrid() {
        try {
            List<Book> books = dao.search(search.getText(), category.getValue());
            int shown = Math.min(books.size(), RECENT_LIMIT);
            grid.getChildren().clear();
            for (int i = 0; i < shown; i++) {
                grid.getChildren().add(new BookCard(books.get(i), main.window(), main::refreshData,
                        role().canManageBooks()));
            }
            countLabel.setText(books.isEmpty()
                    ? "No titles"
                    : "Showing " + shown + " of " + books.size() + " titles");

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
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }
}
