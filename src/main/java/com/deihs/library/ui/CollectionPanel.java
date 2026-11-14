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

/** Full cover grid with search and category filtering. */
public class CollectionPanel extends VBox {

    private final MainView main;
    private final BookDao dao = new BookDao();

    private final FlowPane grid = new FlowPane();
    private final Label countLabel = new Label();
    private final StackPane emptyHost = new StackPane();
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::refreshGrid);

    private boolean updatingFilters;

    public CollectionPanel(MainView main) {
        this.main = main;
        setSpacing(14);
        setPadding(new Insets(22, 26, 26, 26));

        getChildren().addAll(buildToolbar(), buildFilterRow(), buildGridArea());
        VBox.setVgrow(getChildren().get(2), Priority.ALWAYS);

        refresh();
    }

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

    private Node buildFilterRow() {
        search.setPromptText("Search by title, author, or category\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(40);
        search.setMaxWidth(Double.MAX_VALUE);
        search.textProperty().addListener((o, a, b) -> searchDebounce.trigger());
        HBox.setHgrow(search, Priority.ALWAYS);

        category.getStyleClass().add("combo-box");
        category.setPrefWidth(190);
        category.setPrefHeight(40);
        category.setValue("All Categories");
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

    public void refresh() {
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
        refreshGrid();
    }

    private void refreshGrid() {
        try {
            List<Book> books = dao.search(search.getText(), category.getValue());
            grid.getChildren().clear();
            for (Book book : books) {
                grid.getChildren().add(new BookCard(book, main.window(), main::refreshData,
                        main.role().canManageBooks()));
            }
            countLabel.setText(books.size() + (books.size() == 1 ? " title" : " titles"));

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

    private void clearFilters() {
        search.clear();
        category.setValue("All Categories");
    }

    /** Ctrl+F jumps to this page's search box. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    private EmptyState emptyState() {
        String query = search.getText() == null ? "" : search.getText().trim();
        boolean filtered = !query.isEmpty()
                || (category.getValue() != null && !"All Categories".equals(category.getValue()));
        if (filtered) {
            return new EmptyState("\u2315", "No titles match",
                    "Try a shorter search, a different category, or clear the filters to see every book.")
                    .action("Clear filters", this::clearFilters);
        }
        if (main.role().canManageBooks()) {
            return new EmptyState("\u25A4", "The shelves are empty",
                    "Add the first title and its cover to start the collection.")
                    .action("+  Add Book", main::addBook);
        }
        return new EmptyState("\u25A4", "No books yet",
                "The library has not added any titles. Check back soon.");
    }
}
