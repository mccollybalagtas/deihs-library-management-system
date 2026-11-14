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
 */
public class CatalogView extends VBox {

    private final MainView main;
    private final BookDao dao = new BookDao();

    private final ObservableList<Book> rows = FXCollections.observableArrayList();
    private final TableView<Book> table = new TableView<>(rows);
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final Label countLabel = new Label();
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::reloadRows);

    private boolean updatingFilters;

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

    private Node buildToolbar() {
        Label subtitle = new Label(main.role().canManageBooks()
                ? "Maintain the library catalog. Every change is saved to MySQL immediately."
                : "Browse every title on the shelves. Only an administrator can change catalog records.");
        subtitle.getStyleClass().add("muted");
        subtitle.setFont(Font.font("SansSerif", 12));

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

    private Node buildTableCard() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(EmptyState.host(catalogEmptyState()));
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

        TableColumn<Book, String> dueCol = DueColumn.create("Due");
        dueCol.setMinWidth(115);

        TableColumn<Book, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isBorrowed() ? data.getValue().loanStatusText() : data.getValue().statusText()));
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

        if (main.role().canManageBooks()) {
            TableColumn<Book, Void> actionCol = new TableColumn<>("Actions");
            actionCol.setMinWidth(150);
            actionCol.setMaxWidth(170);
            actionCol.setSortable(false);
            actionCol.setCellFactory(col -> new TableCell<>() {
                private final Button edit = new Button("Edit");
                private final Button delete = new Button("Delete");
                private final HBox box = new HBox(6, edit, delete);

                {
                    box.setAlignment(Pos.CENTER_LEFT);
                    edit.getStyleClass().add("row-btn");
                    delete.getStyleClass().addAll("row-btn", "row-btn-danger");
                    edit.setOnAction(e -> editBook(rowBook()));
                    delete.setOnAction(e -> deleteBook(rowBook()));
                }

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

    private void clearFilters() {
        search.clear();
        category.setValue("All Categories");
    }

    private EmptyState catalogEmptyState() {
        String query = search.getText() == null ? "" : search.getText().trim();
        boolean filtered = !query.isEmpty()
                || (category.getValue() != null && !"All Categories".equals(category.getValue()));
        if (filtered) {
            return new EmptyState("\u2315", "No titles match",
                    "Try a broader search term or a different category.")
                    .action("Clear filters", this::clearFilters);
        }
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

    /** Ctrl+F jumps to this page's search box. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    private void reloadRows() {
        try {
            List<Book> books = dao.search(search.getText(), category.getValue());
            rows.setAll(books);
            table.setPlaceholder(EmptyState.host(catalogEmptyState()));
            countLabel.setText(books.size() + (books.size() == 1 ? " title in the catalog" : " titles in the catalog"));
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load the catalog", ex.getMessage());
        }
    }

    private void editBook(Book book) {
        if (book == null || !main.role().canManageBooks()) {
            return;
        }
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

    private void deleteBook(Book book) {
        if (book == null || !main.role().canManageBooks()) {
            return;
        }
        if (book.isBorrowed()) {
            String who = book.getBorrower() == null || book.getBorrower().isBlank()
                    ? "a reader" : book.getBorrower();
            Dialogs.error(main.window(), "Book is on loan",
                    "\"" + book.getTitle() + "\" is currently with " + who + ".\n\n"
                            + "Return it from the circulation desk before removing it.");
            return;
        }
        boolean confirmed = Dialogs.confirmDestructive(main.window(), "Remove book",
                "Remove \"" + book.getTitle() + "\" from the catalog?\n\n"
                        + "Its cover image is deleted too. This cannot be undone.",
                "Remove book");
        if (!confirmed) {
            return;
        }
        try {
            boolean removed = dao.delete(book.getId());
            if (!removed) {
                Dialogs.error(main.window(), "Could not remove the book",
                        "\"" + book.getTitle() + "\" was just issued to a reader.\n"
                                + "Return it first, then try again.");
                main.refreshData();
                return;
            }
            Covers.delete(book.getCoverFile());
            main.setStatus("Removed \"" + book.getTitle() + "\" from the catalog.");
            main.toastSuccess("Removed \"" + book.getTitle() + "\" from the catalog.");
            main.refreshData();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not remove the book", ex.getMessage());
        }
    }
}
