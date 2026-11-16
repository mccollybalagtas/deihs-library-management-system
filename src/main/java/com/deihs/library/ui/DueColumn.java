package com.deihs.library.ui;

import com.deihs.library.model.Book;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

/**
 * The shared "Due" column.
 *
 * <p>The relative wording ("Due in 3 days", "2 days overdue") is the primary
 * line because it is what people actually act on; the absolute date sits
 * underneath for reference.</p>
 *
 * <p>Part of: the shared table helpers. The catalogue, circulation and loans
 * tables all show due dates, so the column is built once here.</p>
 * <p>Used by: {@link CatalogView}, {@link CirculationView} and
 * {@link MyLoansView}.</p>
 * <p>Key rules: the cell value is the absolute date string (what sorting sees),
 * while the cell itself draws two lines; copies that are not out show an em
 * dash.</p>
 */
final class DueColumn {

    private DueColumn() {
    }

    // WHAT: builds a due-date column with the given header.
    // WHY: one implementation keeps every table consistent and lets the cell
    // show both relative and absolute dates. CALLED BY: the table-building code
    // in CatalogView, CirculationView and MyLoansView.
    static TableColumn<Book, String> create(String header) {
        TableColumn<Book, String> column = new TableColumn<>(header);
        // The raw value is the absolute date string; it is what sorting and any
        // text export see.
        column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dueText()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                // The cell needs the whole Book, not just the string, so it can
                // pick the urgency colour. A row may be empty while scrolling.
                Book book = getTableRow() == null ? null : getTableRow().getItem();
                // Nothing useful to show for an empty row or an on-shelf copy:
                // an em dash keeps the column readable instead of blank.
                if (empty || book == null || !book.isBorrowed() || book.getDueAt() == null) {
                    setGraphic(null);
                    setText("\u2014");
                    return;
                }

                // Primary line: relative wording, coloured by urgency.
                Label relative = new Label(book.dueRelativeText());
                relative.getStyleClass().add(book.dueUrgencyStyle());

                // Secondary line: the exact date, muted and small so it does not
                // compete with the relative wording.
                Label absolute = new Label(book.dueText());
                absolute.getStyleClass().add("muted");
                absolute.setFont(Font.font("SansSerif", 9.5));

                // Stack the two lines and hand the box to the cell; clearing the
                // text stops JavaFX from also drawing the raw value.
                VBox box = new VBox(1, relative, absolute);
                setGraphic(box);
                setText(null);
            }
        });
        return column;
    }
}
