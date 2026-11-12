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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * Borrowing desk: every title with its live availability and a one-click
 * borrow/return action.
 */
public class CirculationView extends VBox {

    private final MainView main;
    private final BookDao dao = new BookDao();

    private final ObservableList<Book> rows = FXCollections.observableArrayList();
    private final TableView<Book> table = new TableView<>(rows);
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final Label countLabel = new Label();
    private final Label hintLabel = new Label();

    private boolean updatingFilters;

    public CirculationView(MainView main) {
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

    private Node buildToolbar() {
        Label subtitle = new Label("Borrow and return books. Availability updates the moment you act.");
        subtitle.getStyleClass().add("muted");
        subtitle.setFont(Font.font("SansSerif", 12));

        hintLabel.getStyleClass().add("muted");
        hintLabel.setFont(Font.font("SansSerif", 11));

        VBox left = new VBox(3, subtitle, hintLabel);

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

    private Node buildFilterRow() {
        search.setPromptText("Search by title, author, or category\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(40);
        search.setMaxWidth(Double.MAX_VALUE);
        search.textProperty().addListener((o, a, b) -> reloadRows());
        HBox.setHgrow(search, Priority.ALWAYS);

        category.getStyleClass().add("combo-box");
        category.setPrefWidth(190);
        category.setPrefHeight(40);
        category.setValue("All Categories");
        category.valueProperty().addListener((o, a, b) -> {
            if (!updatingFilters) {
                reloadRows();
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

    private Node buildTableCard() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(placeholder("No books match the current filters."));
        table.setFixedCellSize(64);
        table.setPrefHeight(460);

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
        categoryCol.setMinWidth(110);

        TableColumn<Book, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBookType()));
        typeCol.setMinWidth(85);

        TableColumn<Book, String> detailsCol = new TableColumn<>("Details");
        detailsCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getExtraInfo() == null ? "\u2014" : data.getValue().getExtraInfo()));
        detailsCol.setMinWidth(100);

        TableColumn<Book, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().statusText()));
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
                Label pill = new Label(book.statusText());
                pill.getStyleClass().add(book.isBorrowed() ? "pill-out" : "pill-available");

                if (book.isBorrowed() && book.getBorrower() != null && !book.getBorrower().isBlank()) {
                    Label borrower = new Label("by " + book.getBorrower());
                    borrower.getStyleClass().add("muted");
                    borrower.setFont(Font.font("SansSerif", 9.5));
                    VBox box = new VBox(3, pill, borrower);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                } else {
                    setGraphic(pill);
                }
                setText(null);
            }
        });

        TableColumn<Book, Void> actionCol = new TableColumn<>("Action");
        actionCol.setMinWidth(105);
        actionCol.setMaxWidth(130);
        actionCol.setSortable(false);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button action = new Button();

            {
                action.getStyleClass().add("row-btn");
                action.setOnAction(e -> {
                    Book book = getTableRow() == null ? null : getTableRow().getItem();
                    if (book != null) {
                        toggle(book);
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
                action.setText(book.isBorrowed() ? "Return" : "Borrow");
                action.getStyleClass().remove("row-btn-danger");
                if (book.isBorrowed()) {
                    action.getStyleClass().add("row-btn-danger");
                }
                setGraphic(action);
            }
        });

        table.getColumns().setAll(List.of(titleCol, categoryCol, typeCol, detailsCol, statusCol, actionCol));

        VBox card = new VBox(table);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(6, 6, 6, 6));
        VBox.setVgrow(table, Priority.ALWAYS);
        return card;
    }

    private Node placeholder(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("muted");
        label.setFont(Font.font("SansSerif", 12));
        StackPane pane = new StackPane(label);
        pane.setPadding(new Insets(40));
        return pane;
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

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
        reloadRows();
    }

    private void reloadRows() {
        try {
            List<Book> books = dao.search(search.getText(), category.getValue());
            rows.setAll(books);
            long borrowed = books.stream().filter(Book::isBorrowed).count();
            countLabel.setText(books.size() + (books.size() == 1 ? " title" : " titles")
                    + "  \u2022  " + borrowed + " out on loan");
            hintLabel.setText(countLabel.getText());
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load circulation", ex.getMessage());
        }
    }

    private void toggle(Book book) {
        boolean returning = book.isBorrowed();
        try {
            if (returning) {
                dao.setBorrowed(book.getId(), false, null);
                main.setStatus("Returned \"" + book.getTitle() + "\" to the shelves.");
            } else {
                dao.setBorrowed(book.getId(), true, main.user().getDisplayName());
                main.setStatus("Checked out \"" + book.getTitle() + "\" to "
                        + main.user().getDisplayName() + ".");
            }
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), returning ? "Could not return the book" : "Could not borrow the book",
                    ex.getMessage());
        }
    }
}
