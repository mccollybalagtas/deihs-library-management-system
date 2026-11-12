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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.layout.FlowPane;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private final Label emptyLabel = new Label("No books match your search yet.");
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

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private Node buildBanner() {
        Label welcome = new Label("Welcome back, " + main.user().getDisplayName() + "!");
        welcome.setFont(Font.font("SansSerif", FontWeight.BOLD, 23));
        welcome.setTextFill(Color.web("#0D264C"));

        Label subtitle = new Label("Here is what is happening in the Balagtas DEIHS library today.");
        subtitle.setFont(Font.font("SansSerif", 12));
        subtitle.setTextFill(Color.web("#5B6B80"));

        VBox text = new VBox(5, welcome, subtitle);
        text.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label todayLabel = new Label("TODAY");
        todayLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 8.5));
        todayLabel.setTextFill(Color.web("#7A8CA3"));

        Label today = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)));
        today.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        today.setTextFill(Color.web("#0D264C"));

        VBox dateBox = new VBox(2, todayLabel, today);
        dateBox.setAlignment(Pos.CENTER_RIGHT);

        StackPane badge = new StackPane();
        badge.setPrefSize(48, 48);
        badge.setMinSize(48, 48);
        badge.setMaxSize(48, 48);
        badge.setStyle("-fx-background-color: #0D264C; -fx-background-radius: 14;");
        Label icon = new Label("\u25A4");
        icon.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
        icon.setTextFill(Color.web("#64DCEB"));
        badge.getChildren().add(icon);

        HBox banner = new HBox(16, text, spacer, dateBox, badge);
        banner.getStyleClass().add("banner");
        banner.setAlignment(Pos.CENTER_LEFT);
        return banner;
    }

    private Node buildFilters() {
        search.setPromptText("Search by title, author, or category\u2026");
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

        Button add = new Button("+  Add Book");
        add.getStyleClass().add("primary-btn");
        add.setPrefHeight(40);
        add.setOnAction(e -> main.addBook());

        HBox row = new HBox(10, search, category, add);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildSectionHeader() {
        Label title = new Label("Book Collection");
        title.getStyleClass().add("h2");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        countLabel.getStyleClass().add("muted");
        countLabel.setFont(Font.font("SansSerif", 11));

        Button viewAll = new Button("View all  \u2192");
        viewAll.getStyleClass().add("link");
        viewAll.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        viewAll.setOnAction(e -> main.navigate("collection"));

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

        emptyLabel.getStyleClass().add("muted");
        emptyLabel.setFont(Font.font("SansSerif", 12));
        emptyLabel.setPadding(new Insets(20, 0, 0, 4));
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);

        VBox area = new VBox(10, grid, emptyLabel);
        area.setPadding(new Insets(0, 0, 10, 0));
        return area;
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    public void refresh() {
        try {
            BookDao.Stats stats = dao.stats();
            statsRow.getChildren().setAll(
                    statCard("Total Books", String.valueOf(stats.total()), "\u25A4", "stat-blue"),
                    statCard("Available", String.valueOf(stats.available()), "\u2713", "stat-green"),
                    statCard("Borrowed", String.valueOf(stats.borrowed()), "\u21C4", "stat-pink"),
                    statCard("Printed", String.valueOf(stats.printed()), "\u25A6", "stat-purple"),
                    statCard("E-Books", String.valueOf(stats.ebook()), "\u25C9", "stat-amber"));

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
                grid.getChildren().add(new BookCard(books.get(i), main.window(), main::refreshData));
            }
            countLabel.setText(books.isEmpty()
                    ? "No titles"
                    : "Showing " + shown + " of " + books.size() + " titles");

            boolean empty = books.isEmpty();
            emptyLabel.setVisible(empty);
            emptyLabel.setManaged(empty);
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
