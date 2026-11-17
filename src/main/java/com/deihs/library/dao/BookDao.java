package com.deihs.library.dao;

import com.deihs.library.db.Db;
import com.deihs.library.model.Book;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All catalog reads and writes, including the borrowing desk.
 *
 * <p>Part of: Data-access layer. The only class that runs SQL against the
 * {@code books} table.<br>
 * Used by: CatalogView, CollectionPanel, DashboardView, CirculationView,
 * MyLoansView, BookCard, BookFormView and MainView (add book).<br>
 * Key rules: every statement is a {@link PreparedStatement} with bound
 * parameters, so user text can never change the SQL. Issue, return and delete
 * carry a {@code borrowed = 0 / 1} guard inside the WHERE clause, so two people
 * acting at the same moment cannot both change the same copy.</p>
 */
public final class BookDao {

    // ============================================================
    // PART: Result types
    // ============================================================

    /** Aggregate numbers for the dashboard cards. */
    public record Stats(int total, int available, int borrowed, int printed, int ebook, int overdue) {
    }

    // ============================================================
    // PART: Catalog queries
    // ============================================================

    /**
     * WHAT: Loads the whole catalog.
     * WHY: Used when no filter is active.
     * CALLED BY: kept as the simple entry point; callers normally use search().
     */
    public List<Book> findAll() throws DaoException {
        return search(null, null);
    }

    /**
     * WHAT: Loads books filtered by free text and category.
     * WHY: This is what every search box and category drop-down calls; it builds
     * the WHERE clause only from the filters the user actually set.
     * CALLED BY: CatalogView, CollectionPanel, DashboardView, CirculationView,
     * MyLoansView, and findAll() above.
     */
    public List<Book> search(String text, String category) throws DaoException {
        // Step 1: start with "WHERE 1 = 1" so every extra condition can be
        // appended with a plain " AND ..." without checking for a first one.
        StringBuilder sql = new StringBuilder("SELECT * FROM books WHERE 1 = 1");
        // Parameters are collected in the same order as their placeholders, then
        // bound by index below.
        List<Object> params = new ArrayList<>();

        // Step 2: add the free-text condition only when the box is not empty.
        if (text != null && !text.isBlank()) {
            // The same %text% pattern is used four times, one per searched column.
            sql.append(" AND (title LIKE ? OR author LIKE ? OR category LIKE ? OR borrower LIKE ?)");
            String like = "%" + text.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        // Step 3: the category drop-down sends its own label for "no filter",
        // which must not become a real WHERE condition.
        if (category != null && !category.isBlank() && !"All Categories".equalsIgnoreCase(category)) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        // Step 4: newest entries first; id breaks ties when two rows share a
        // timestamp (possible on fast inserts).
        sql.append(" ORDER BY created_at DESC, id DESC");

        List<Book> books = new ArrayList<>();
        // try-with-resources closes the connection, statement and result set
        // even when an SQLException is thrown.
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            // JDBC placeholders are 1-based, the params list is 0-based.
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not load the catalog.\n" + ex.getMessage(), ex);
        }
        return books;
    }

    /**
     * WHAT: Loads one book by its primary key.
     * WHY: The circulation scan field resolves a scanned ID before acting on it.
     * CALLED BY: CirculationView.scanBook().
     */
    public Book findById(int bookId) throws DaoException {
        String sql = "SELECT * FROM books WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                // A missing row is a normal outcome: the caller shows "no book".
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not look up that book.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * WHAT: Every book currently on loan to one account.
     * WHY: A student must see only their own loans, matched on borrower_id rather
     * than the display name.
     * CALLED BY: MyLoansView and DashboardView.
     */
    public List<Book> findByBorrower(int userId) throws DaoException {
        // Ordering by due_at puts the most urgent loan first.
        String sql = "SELECT * FROM books WHERE borrowed = 1 AND borrower_id = ? ORDER BY due_at, id";
        List<Book> books = new ArrayList<>();
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not load your borrowed books.\n" + ex.getMessage(), ex);
        }
        return books;
    }

    /**
     * WHAT: Number of books currently on loan to one account.
     * WHY: The 3-book limit is checked before a checkout, and counting in SQL
     * avoids loading every row just to call size().
     * CALLED BY: MyLoansView and CirculationView.
     */
    public int countByBorrower(int userId) throws DaoException {
        String sql = "SELECT COUNT(*) FROM books WHERE borrowed = 1 AND borrower_id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                // COUNT(*) always returns one row, but guard anyway.
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not count your borrowed books.\n" + ex.getMessage(), ex);
        }
    }

    // ============================================================
    // PART: Catalog writes
    // ============================================================

    /**
     * WHAT: Inserts a new catalog row and copies the generated ID back into the
     * passed Book.
     * WHY: The UI needs the ID immediately after adding a book, for example to
     * save its cover.
     * CALLED BY: MainView.addBook().
     */
    public void insert(Book book) throws DaoException {
        // borrowed is fixed at 0: a newly added copy is always on the shelf.
        String sql = "INSERT INTO books (title, author, category, book_type, extra_info, cover_file, borrowed) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)";
        // RETURN_GENERATED_KEYS makes MySQL report the new AUTO_INCREMENT id.
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, book.getTitle());
            ps.setString(2, book.getAuthor());
            ps.setString(3, book.getCategory());
            ps.setString(4, book.getBookType());
            ps.setString(5, book.getExtraInfo());
            ps.setString(6, book.getCoverFile());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    book.setId(keys.getInt(1));
                }
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not add that book.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * WHAT: Saves edited catalog fields.
     * WHY: Only descriptive columns are updated; loan columns are owned by
     * checkout/checkin so an edit can never clear an active loan.
     * CALLED BY: BookCard (quick edit) and the book form save path.
     */
    public void update(Book book) throws DaoException {
        String sql = "UPDATE books SET title = ?, author = ?, category = ?, book_type = ?, "
                + "extra_info = ?, cover_file = ? WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, book.getTitle());
            ps.setString(2, book.getAuthor());
            ps.setString(3, book.getCategory());
            ps.setString(4, book.getBookType());
            ps.setString(5, book.getExtraInfo());
            ps.setString(6, book.getCoverFile());
            ps.setInt(7, book.getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not save the changes.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * Deletes a book that is on the shelf.
     *
     * <p>The {@code borrowed = 0} guard means a copy that is out on loan can
     * never be deleted by accident, even if two people act at once. A false
     * result tells the caller the book was issued in the meantime.</p>
     */
    public boolean delete(int bookId) throws DaoException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM books WHERE id = ? AND borrowed = 0")) {
            ps.setInt(1, bookId);
            // Exactly one affected row means the delete really happened.
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Could not remove that book.\n" + ex.getMessage(), ex);
        }
    }

    // ============================================================
    // PART: Circulation desk
    // ============================================================

    /**
     * Issues a book to an account.
     *
     * <p>The UPDATE only matches a book that is still on the shelf, so two
     * people clicking at the same moment cannot both get the same copy. A zero
     * row count means somebody else was faster.</p>
     */
    public boolean checkout(int bookId, int borrowerId, String borrowerName, LocalDateTime dueAt)
            throws DaoException {
        // The "AND borrowed = 0" at the end is the concurrency guard: the
        // database itself decides who wins the race.
        String sql = "UPDATE books SET borrowed = 1, borrower_id = ?, borrower = ?, "
                + "borrowed_at = ?, due_at = ? WHERE id = ? AND borrowed = 0";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            // The issue time is taken here so the UI cannot pass a stale value.
            LocalDateTime now = LocalDateTime.now();
            ps.setInt(1, borrowerId);
            ps.setString(2, borrowerName);
            // java.time is converted to java.sql.Timestamp for the JDBC driver.
            ps.setTimestamp(3, Timestamp.valueOf(now));
            // Callers may pick a due date; when they do not, the 7-day default
            // from Book.LOAN_DAYS applies.
            ps.setTimestamp(4, Timestamp.valueOf(dueAt == null ? now.plusDays(Book.LOAN_DAYS) : dueAt));
            ps.setInt(5, bookId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Could not issue that book.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * WHAT: Returns a book to the shelf and clears every loan field.
     * WHY: Leaving a stale due date behind would keep the copy looking overdue.
     * The {@code borrowed = 1} guard makes a double return a harmless no-op.
     * CALLED BY: CirculationView and MyLoansView.
     */
    public boolean checkin(int bookId) throws DaoException {
        String sql = "UPDATE books SET borrowed = 0, borrower_id = NULL, borrower = NULL, "
                + "borrowed_at = NULL, due_at = NULL WHERE id = ? AND borrowed = 1";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Could not return that book.\n" + ex.getMessage(), ex);
        }
    }

    // ============================================================
    // PART: Reporting
    // ============================================================

    /**
     * WHAT: One row of dashboard counters.
     * WHY: Six separate queries would be slower and could disagree with each
     * other; this reads every count in a single pass over the table.
     * CALLED BY: DashboardView.
     */
    public Stats stats() throws DaoException {
        // In MySQL a comparison yields 1 or 0, so SUM(condition) counts the rows
        // that match. COALESCE turns the NULL of an empty table into 0.
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(borrowed = 0), 0) AS available, "
                + "COALESCE(SUM(borrowed = 1), 0) AS borrowed, "
                + "COALESCE(SUM(book_type = 'Printed'), 0) AS printed, "
                + "COALESCE(SUM(book_type = 'E-Book'), 0) AS ebook, "
                + "COALESCE(SUM(borrowed = 1 AND due_at IS NOT NULL AND due_at < NOW()), 0) AS overdue "
                + "FROM books";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new Stats(
                        rs.getInt("total"),
                        rs.getInt("available"),
                        rs.getInt("borrowed"),
                        rs.getInt("printed"),
                        rs.getInt("ebook"),
                        rs.getInt("overdue"));
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not compute library statistics.\n" + ex.getMessage(), ex);
        }
        // Defensive fallback: an all-zero summary instead of null.
        return new Stats(0, 0, 0, 0, 0, 0);
    }

    /**
     * WHAT: The distinct category names used to fill the filter drop-downs.
     * WHY: The list must come from real rows, so empty and null categories are
     * filtered out in SQL rather than in each view.
     * CALLED BY: CatalogView, CollectionPanel, DashboardView, CirculationView,
     * BookFormView.
     */
    public List<String> categories() throws DaoException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT category FROM books WHERE category IS NOT NULL AND category <> '' ORDER BY category";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString(1));
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not load the category list.\n" + ex.getMessage(), ex);
        }
        return list;
    }

    // ============================================================
    // PART: Row mapping
    // ============================================================

    /**
     * WHAT: Converts the current result-set row into a Book.
     * WHY: Every query in this class ends here, so the null handling is written
     * once.
     * CALLED BY: search, findByBorrower and findById.
     */
    private static Book map(ResultSet rs) throws SQLException {
        Book book = new Book();
        book.setId(rs.getInt("id"));
        book.setTitle(rs.getString("title"));
        book.setAuthor(rs.getString("author"));
        book.setCategory(rs.getString("category"));
        // Rows written by older releases may have a null or blank type; treat
        // them as a normal printed copy.
        String type = rs.getString("book_type");
        book.setBookType(type == null || type.isBlank() ? Book.TYPE_PRINTED : type);
        book.setExtraInfo(rs.getString("extra_info"));
        book.setCoverFile(rs.getString("cover_file"));
        book.setBorrowed(rs.getBoolean("borrowed"));
        book.setBorrower(rs.getString("borrower"));
        // getInt() returns 0 for SQL NULL, which is a valid-looking ID, so
        // wasNull() is the only reliable way to detect "not on loan".
        int borrowerId = rs.getInt("borrower_id");
        book.setBorrowerId(rs.wasNull() ? null : borrowerId);
        // Timestamps convert to LocalDateTime; NULL stays NULL.
        Timestamp borrowedAt = rs.getTimestamp("borrowed_at");
        book.setBorrowedAt(borrowedAt == null ? null : borrowedAt.toLocalDateTime());
        Timestamp dueAt = rs.getTimestamp("due_at");
        book.setDueAt(dueAt == null ? null : dueAt.toLocalDateTime());
        return book;
    }
}
