package com.deihs.library.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** One catalog record. */
public class Book {

    public static final String TYPE_PRINTED = "Printed";
    public static final String TYPE_EBOOK = "E-Book";
    public static final String TYPE_ARCHIVE = "Archive";

    /** How long a loan lasts before it is considered overdue. */
    public static final int LOAN_DAYS = 7;

    /** How many titles one student may hold at the same time. */
    public static final int MAX_LOANS = 3;

    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private int id;
    private String title;
    private String author;
    private String category;
    private String bookType = TYPE_PRINTED;
    private String extraInfo;
    private String coverFile;
    private boolean borrowed;
    private String borrower;
    private Integer borrowerId;
    private LocalDateTime borrowedAt;
    private LocalDateTime dueAt;

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

    public Integer getBorrowerId() {
        return borrowerId;
    }

    public void setBorrowerId(Integer borrowerId) {
        this.borrowerId = borrowerId;
    }

    public LocalDateTime getBorrowedAt() {
        return borrowedAt;
    }

    public void setBorrowedAt(LocalDateTime borrowedAt) {
        this.borrowedAt = borrowedAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public boolean isEbook() {
        return TYPE_EBOOK.equalsIgnoreCase(bookType);
    }

    public String statusText() {
        return borrowed ? "Borrowed" : "Available";
    }

    /** True when the book is out and its due date has already passed. */
    public boolean isOverdue() {
        return borrowed && dueAt != null && dueAt.isBefore(LocalDateTime.now());
    }

    /** True when the book is out and due within the next three days. */
    public boolean isDueSoon() {
        return borrowed && dueAt != null && !isOverdue()
                && !dueAt.isAfter(LocalDateTime.now().plusDays(3));
    }

    /** Whole days left; negative when overdue. */
    public long daysUntilDue() {
        if (dueAt == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDateTime.now(), dueAt);
    }

    /** "Mar 4, 2026", or an em dash when the book is on the shelf. */
    public String dueText() {
        return dueAt == null ? "\u2014" : dueAt.format(DUE_FORMAT);
    }

    /**
     * Status wording used by the circulation and loan tables:
     * Available, Overdue, Due today, Due in N days or Borrowed.
     */
    public String loanStatusText() {
        if (!borrowed) {
            return "Available";
        }
        if (dueAt == null) {
            return "Borrowed";
        }
        long days = daysUntilDue();
        if (days < 0) {
            long late = Math.abs(days);
            return "Overdue by " + late + (late == 1 ? " day" : " days");
        }
        if (days == 0) {
            return "Due today";
        }
        return "Due in " + days + (days == 1 ? " day" : " days");
    }

    /** CSS pill class that matches the loan state. */
    public String loanPillStyle() {
        if (!borrowed) {
            return "pill-available";
        }
        if (isOverdue()) {
            return "pill-overdue";
        }
        if (isDueSoon()) {
            return "pill-soon";
        }
        return "pill-out";
    }
}
