package com.deihs.library.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * One catalog record.
 *
 * <p>Part of: Model layer. A plain data holder plus the read-only loan-state
 * calculations the screens use to colour due dates.<br>
 * Used by: {@code BookDao} (builds these from result-set rows) and the ui
 * classes BookCard, CatalogView, CollectionPanel, CirculationView, MyLoansView,
 * DashboardView, DueColumn, BookRows and BookFormView.<br>
 * Key rules: a loan lasts {@link #LOAN_DAYS} days and one student may hold at
 * most {@link #MAX_LOANS} books. Overdue / due-soon are computed from
 * {@code dueAt} every time they are asked for, so nothing needs to be updated
 * as time passes.</p>
 */
public class Book {

    // ============================================================
    // PART: Constants
    // ============================================================

    /** Stored in books.book_type when the copy is a physical book. */
    public static final String TYPE_PRINTED = "Printed";
    /** Stored in books.book_type when the copy is a digital file. */
    public static final String TYPE_EBOOK = "E-Book";
    /** Stored in books.book_type for archived / reference material. */
    public static final String TYPE_ARCHIVE = "Archive";

    /** How long a loan lasts before it is considered overdue. */
    public static final int LOAN_DAYS = 7;

    /** How many titles one student may hold at the same time. */
    public static final int MAX_LOANS = 3;

    /** Pattern for the absolute due date shown in tables, e.g. "Mar 4, 2026". */
    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    // ============================================================
    // PART: Catalog fields
    // ============================================================

    /** Primary key from the books table; 0 until the row is inserted. */
    private int id;
    private String title;
    private String author;
    private String category;
    /** Printed, E-Book or Archive; defaults so a new object is never blank. */
    private String bookType = TYPE_PRINTED;
    /** Free text such as shelf location or file size. */
    private String extraInfo;
    /** File name inside the covers/ folder, or null when there is no image. */
    private String coverFile;

    // ============================================================
    // PART: Loan fields
    // ============================================================

    /** True while the copy is out on loan. */
    private boolean borrowed;
    /** Display name copied at checkout, so old loans keep a readable name. */
    private String borrower;
    /** Account ID of the borrower; null when available. */
    private Integer borrowerId;
    /** When the copy was issued. */
    private LocalDateTime borrowedAt;
    /** When it must be back; null when available. */
    private LocalDateTime dueAt;

    // ============================================================
    // PART: Constructors
    // ============================================================

    /** Empty constructor for the DAO, which fills the fields one by one. */
    public Book() {
    }

    /** Convenience constructor for a new catalog entry in the add-book form. */
    public Book(String title, String author, String category) {
        this.title = title;
        this.author = author;
        this.category = category;
    }

    // ============================================================
    // PART: Catalog accessors
    // ============================================================

    /** Catalog ID. */
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /** Book title. */
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /** Author name. */
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    /** Category used by the filter drop-downs. */
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    /** Printed / E-Book / Archive. */
    public String getBookType() {
        return bookType;
    }

    public void setBookType(String bookType) {
        this.bookType = bookType;
    }

    /** Extra descriptive text shown on the book card. */
    public String getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
    }

    /** Stored cover file name, resolved against Covers.DIR when drawn. */
    public String getCoverFile() {
        return coverFile;
    }

    public void setCoverFile(String coverFile) {
        this.coverFile = coverFile;
    }

    // ============================================================
    // PART: Loan accessors
    // ============================================================

    /** True while the copy is out on loan. */
    public boolean isBorrowed() {
        return borrowed;
    }

    public void setBorrowed(boolean borrowed) {
        this.borrowed = borrowed;
    }

    /** Name of the borrower as it was at checkout. */
    public String getBorrower() {
        return borrower;
    }

    public void setBorrower(String borrower) {
        this.borrower = borrower;
    }

    /** Account ID of the borrower; null means the book is on the shelf. */
    public Integer getBorrowerId() {
        return borrowerId;
    }

    public void setBorrowerId(Integer borrowerId) {
        this.borrowerId = borrowerId;
    }

    /** Issue date and time. */
    public LocalDateTime getBorrowedAt() {
        return borrowedAt;
    }

    public void setBorrowedAt(LocalDateTime borrowedAt) {
        this.borrowedAt = borrowedAt;
    }

    /** Date and time the copy is due back. */
    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    // ============================================================
    // PART: Derived loan state
    // ============================================================

    /** WHAT: True when the book type is E-Book. WHY: the UI treats digital copies differently. */
    public boolean isEbook() {
        return TYPE_EBOOK.equalsIgnoreCase(bookType);
    }

    /** WHAT: "Borrowed" or "Available". WHY: single wording used by every table. */
    public String statusText() {
        return borrowed ? "Borrowed" : "Available";
    }

    /**
     * WHAT: True when the book is out and its due date has already passed.
     * WHY: Drives the red overdue styling and the desk's overdue filter.
     */
    public boolean isOverdue() {
        return borrowed && dueAt != null && dueAt.isBefore(LocalDateTime.now());
    }

    /**
     * WHAT: True when the book is out and due within the next three days.
     * WHY: Gives the student a warning before the book turns overdue; the
     * {@code !isOverdue()} check keeps the two states mutually exclusive.
     */
    public boolean isDueSoon() {
        return borrowed && dueAt != null && !isOverdue()
                && !dueAt.isAfter(LocalDateTime.now().plusDays(3));
    }

    /**
     * WHAT: Whole days left until the due date; negative when overdue.
     * WHY: One place does the date arithmetic so every screen agrees.
     * Note: an available book returns Long.MAX_VALUE so "days left" sorting and
     * comparisons never treat it as urgent.
     */
    public long daysUntilDue() {
        if (dueAt == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDateTime.now(), dueAt);
    }

    // ============================================================
    // PART: Display text and CSS classes
    // ============================================================

    /** WHAT: Absolute due date, e.g. "Mar 4, 2026". WHY: table cell value; em dash when on the shelf. */
    public String dueText() {
        return dueAt == null ? "\u2014" : dueAt.format(DUE_FORMAT);
    }

    /**
     * Short relative wording for a due date: "Due in 3 days",
     * "Due today" or "2 days overdue". Falls back to an em dash when the
     * book is not on loan.
     */
    public String dueRelativeText() {
        if (!borrowed || dueAt == null) {
            return "\u2014";
        }
        long days = daysUntilDue();
        if (days < 0) {
            // Math.abs() because daysUntilDue() is negative for late books and
            // the wording should read "2 days overdue", not "-2 days overdue".
            long late = Math.abs(days);
            return late + (late == 1 ? " day overdue" : " days overdue");
        }
        if (days == 0) {
            return "Due today";
        }
        return "Due in " + days + (days == 1 ? " day" : " days");
    }

    /** WHAT: CSS class that colours a relative due date by urgency. WHY: keeps styling out of the views. */
    public String dueUrgencyStyle() {
        if (isOverdue()) {
            return "due-overdue";
        }
        if (isDueSoon()) {
            return "due-soon";
        }
        return "due-normal";
    }

    /**
     * Status wording used by the circulation and loan tables:
     * Available, Overdue, Due today, Due in N days or Borrowed.
     */
    public String loanStatusText() {
        if (!borrowed) {
            return "Available";
        }
        // A loan row with no due date can only come from very old data, so it
        // falls back to the neutral "Borrowed" wording.
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

    /** WHAT: CSS pill class that matches the loan state. WHY: colours the status badge in tables. */
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
