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
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.List;

/** Full cover grid with search and category filtering. */
public class CollectionPanel extends VBox {

    private final MainView main;
    private final BookDao dao = new BookDao();

    private final FlowPane grid = new FlowPane();
    private final Label countLabel = new Label();
    private final Label emptyLabel = new Label("No books match your search.");
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();

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

        Button add = new Button("+  Add Book");
        add.getStyleClass().add("primary-btn");
        add.setPrefHeight(40);
        add.setOnAction(e -> main.addBook());

        HBox row = new HBox(10, left, spacer, add);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildFilterRow() {
        search.setPromptText("Search by title, author, or category\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(40);
        search.setMaxWidth(Double.MAX_VALUE);
        search.textProperty().addListener((o, a, b) -> refreshGrid());
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
        clear.setOnAction(e -> {
            search.clear();
            category.setValue("All Categories");
        });

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

        emptyLabel.getStyleClass().add("muted");
        emptyLabel.setFont(Font.font("SansSerif", 12));

        VBox area = new VBox(12, grid, emptyLabel);
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
                grid.getChildren().add(new BookCard(book, main.window(), main::refreshData));
            }
            countLabel.setText(books.size() + (books.size() == 1 ? " title" : " titles"));

            boolean empty = books.isEmpty();
            emptyLabel.setVisible(empty);
            emptyLabel.setManaged(empty);
            grid.setVisible(!empty);
            grid.setManaged(!empty);
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load books", ex.getMessage());
        }
    }
}
