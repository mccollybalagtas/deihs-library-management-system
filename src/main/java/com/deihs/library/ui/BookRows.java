package com.deihs.library.ui;

import com.deihs.library.model.Book;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

/** Adds a coloured left edge to book rows so urgency is visible at a glance. */
final class BookRows {

    private BookRows() {
    }

    static void markUrgency(TableView<Book> table) {
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(Book book, boolean empty) {
                super.updateItem(book, empty);
                getStyleClass().removeAll("row-overdue", "row-soon", "row-out");
                if (empty || book == null) {
                    return;
                }
                if (book.isOverdue()) {
                    getStyleClass().add("row-overdue");
                } else if (book.isDueSoon()) {
                    getStyleClass().add("row-soon");
                } else if (book.isBorrowed()) {
                    getStyleClass().add("row-out");
                }
            }
        });
    }
}
