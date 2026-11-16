package com.deihs.library.dao;

/**
 * Wraps any database failure in a message the UI can show to the user.
 *
 * <p>Part of: Data-access layer (checked exception).<br>
 * Used by: thrown by Db, Schema, BookDao and UserDao; caught by App.start(),
 * LoginView, UsersView, CirculationView, CatalogView, CollectionPanel,
 * DashboardView, MyLoansView, BookFormView and MainView.<br>
 * Key rules: it is a checked exception, so every caller must decide whether to
 * show the message or let it bubble up; the message is written for the person
 * reading the dialog, not for a developer.</p>
 */
public class DaoException extends Exception {

    /** WHAT: Carries only a user-facing message. WHY: for errors with no useful cause, such as validation. */
    public DaoException(String message) {
        super(message);
    }

    /**
     * WHAT: Carries a message plus the original SQLException.
     * WHY: Keeping the cause preserves the stack trace for debugging while the
     * UI still shows the short message.
     */
    public DaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
