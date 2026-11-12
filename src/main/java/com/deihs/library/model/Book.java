package com.deihs.library.model;

import java.time.LocalDateTime;

/** One catalog record. */
public class Book {

    public static final String TYPE_PRINTED = "Printed";
    public static final String TYPE_EBOOK = "E-Book";
    public static final String TYPE_ARCHIVE = "Archive";

    private int id;
    private String title;
    private String author;
    private String category;
    private String bookType = TYPE_PRINTED;
    private String extraInfo;
    private String coverFile;
    private boolean borrowed;
    private String borrower;
    private LocalDateTime borrowedAt;

    public Book() {
    }

    public Book(String title, String author, String category) {
        this.title = title;
        this.author = author;
        this.category = category;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBookType() {
        return bookType;
    }

    public void setBookType(String bookType) {
        this.bookType = bookType;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
    }

    public String getCoverFile() {
        return coverFile;
    }

    public void setCoverFile(String coverFile) {
        this.coverFile = coverFile;
    }

    public boolean isBorrowed() {
        return borrowed;
    }

    public void setBorrowed(boolean borrowed) {
        this.borrowed = borrowed;
    }

    public String getBorrower() {
        return borrower;
    }

    public void setBorrower(String borrower) {
        this.borrower = borrower;
    }

    public LocalDateTime getBorrowedAt() {
        return borrowedAt;
    }

    public void setBorrowedAt(LocalDateTime borrowedAt) {
        this.borrowedAt = borrowedAt;
    }

    public boolean isEbook() {
        return TYPE_EBOOK.equalsIgnoreCase(bookType);
    }

    public String statusText() {
        return borrowed ? "Borrowed" : "Available";
    }
}
