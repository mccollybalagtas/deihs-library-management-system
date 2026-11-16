package com.deihs.library.ui;

import com.deihs.library.model.Book;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

/**
 * Adds a coloured left edge to book rows so urgency is visible at a glance.
 *
 * <p>Part of: the shared table helpers used by the catalogue and circulation
 * tables.</p>
 * <p>Used by: {@link CirculationView} and {@link MyLoansView} when they build
 * their tables.</p>
 * <p>Key rules: the urgency classes are cleared before each update, because
 * JavaFX recycles row nodes; the priority order is overdue, then due soon, then
 * merely out.</p>
 */
final class BookRows {

    private BookRows() {
    }

    // WHAT: installs a row factory that tags each row with an urgency class.
    // WHY: a coloured edge lets a librarian scan a long table and spot the
    // problems without reading every date. CALLED BY: the table-building code
    // in CirculationView and MyLoansView.
    static void markUrgency(TableView<Book> table) {
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(Book book, boolean empty) {
                super.updateItem(book, empty);
                // JavaFX reuses row nodes while scrolling, so drop the previous
                // row's classes before deciding this row's state.
                getStyleClass().removeAll("row-overdue", "row-soon", "row-out");
                if (empty || book == null) {
                    return;
                }
                // Only one class applies, in priority order: overdue wins over
                // due-soon, which wins over merely being checked out.
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
