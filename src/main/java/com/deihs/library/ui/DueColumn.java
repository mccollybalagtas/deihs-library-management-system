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
 */
final class DueColumn {

    private DueColumn() {
    }

    static TableColumn<Book, String> create(String header) {
        TableColumn<Book, String> column = new TableColumn<>(header);
        column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dueText()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                Book book = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || book == null || !book.isBorrowed() || book.getDueAt() == null) {
                    setGraphic(null);
                    setText("\u2014");
                    return;
                }

                Label relative = new Label(book.dueRelativeText());
                relative.getStyleClass().add(book.dueUrgencyStyle());

                Label absolute = new Label(book.dueText());
                absolute.getStyleClass().add("muted");
                absolute.setFont(Font.font("SansSerif", 9.5));

                VBox box = new VBox(1, relative, absolute);
                setGraphic(box);
                setText(null);
            }
        });
        return column;
    }
}
