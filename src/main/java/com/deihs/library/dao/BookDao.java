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

/** All catalog reads and writes, including the borrowing desk. */
public final class BookDao {

    /** Aggregate numbers for the dashboard cards. */
    public record Stats(int total, int available, int borrowed, int printed, int ebook, int overdue) {
    }

    public List<Book> findAll() throws DaoException {
        return search(null, null);
    }

    public List<Book> search(String text, String category) throws DaoException {
        StringBuilder sql = new StringBuilder("SELECT * FROM books WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            sql.append(" AND (title LIKE ? OR author LIKE ? OR category LIKE ? OR borrower LIKE ?)");
            String like = "%" + text.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (category != null && !category.isBlank() && !"All Categories".equalsIgnoreCase(category)) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        sql.append(" ORDER BY created_at DESC, id DESC");

        List<Book> books = new ArrayList<>();
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
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

    /** Every book currently on loan to one account. */
    public List<Book> findByBorrower(int userId) throws DaoException {
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

    /** Number of books currently on loan to one account. */
    public int countByBorrower(int userId) throws DaoException {
        String sql = "SELECT COUNT(*) FROM books WHERE borrowed = 1 AND borrower_id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not count your borrowed books.\n" + ex.getMessage(), ex);
        }
    }

    public void insert(Book book) throws DaoException {
        String sql = "INSERT INTO books (title, author, category, book_type, extra_info, cover_file, borrowed) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)";
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

    public void delete(int bookId) throws DaoException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM books WHERE id = ?")) {
            ps.setInt(1, bookId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not remove that book.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * Issues a book to an account.
     *
     * <p>The UPDATE only matches a book that is still on the shelf, so two
     * people clicking at the same moment cannot both get the same copy. A zero
     * row count means somebody else was faster.</p>
     */
    public boolean checkout(int bookId, int borrowerId, String borrowerName, LocalDateTime dueAt)
            throws DaoException {
        String sql = "UPDATE books SET borrowed = 1, borrower_id = ?, borrower = ?, "
                + "borrowed_at = ?, due_at = ? WHERE id = ? AND borrowed = 0";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            ps.setInt(1, borrowerId);
            ps.setString(2, borrowerName);
            ps.setTimestamp(3, Timestamp.valueOf(now));
            ps.setTimestamp(4, Timestamp.valueOf(dueAt == null ? now.plusDays(Book.LOAN_DAYS) : dueAt));
            ps.setInt(5, bookId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Could not issue that book.\n" + ex.getMessage(), ex);
        }
    }

    /** Returns a book to the shelf and clears every loan field. */
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

    public Stats stats() throws DaoException {
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
        return new Stats(0, 0, 0, 0, 0, 0);
    }

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

    private static Book map(ResultSet rs) throws SQLException {
        Book book = new Book();
        book.setId(rs.getInt("id"));
        book.setTitle(rs.getString("title"));
        book.setAuthor(rs.getString("author"));
        book.setCategory(rs.getString("category"));
        String type = rs.getString("book_type");
        book.setBookType(type == null || type.isBlank() ? Book.TYPE_PRINTED : type);
        book.setExtraInfo(rs.getString("extra_info"));
        book.setCoverFile(rs.getString("cover_file"));
        book.setBorrowed(rs.getBoolean("borrowed"));
        book.setBorrower(rs.getString("borrower"));
        int borrowerId = rs.getInt("borrower_id");
        book.setBorrowerId(rs.wasNull() ? null : borrowerId);
        Timestamp borrowedAt = rs.getTimestamp("borrowed_at");
        book.setBorrowedAt(borrowedAt == null ? null : borrowedAt.toLocalDateTime());
        Timestamp dueAt = rs.getTimestamp("due_at");
        book.setDueAt(dueAt == null ? null : dueAt.toLocalDateTime());
        return book;
    }
}
