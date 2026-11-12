package com.deihs.library.dao;

/** Wraps any database failure in a message the UI can show to the user. */
public class DaoException extends Exception {

    public DaoException(String message) {
        super(message);
    }

    public DaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
