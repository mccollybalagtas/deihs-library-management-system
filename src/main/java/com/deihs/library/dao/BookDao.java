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

/** All catalog reads and writes. */
public final class BookDao {

    /** Aggregate numbers for the dashboard cards. */
    public record Stats(int total, int available, int borrowed, int printed, int ebook) {
    }

    public List<Book> findAll() throws DaoException {
        return search(null, null);
    }

    public List<Book> search(String text, String category) throws DaoException {
        StringBuilder sql = new StringBuilder("SELECT * FROM books WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            sql.append(" AND (title LIKE ? OR author LIKE ? OR category LIKE ?)");
            String like = "%" + text.trim() + "%";
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

    public void setBorrowed(int bookId, boolean borrowed, String borrower) throws DaoException {
        String sql = "UPDATE books SET borrowed = ?, borrower = ?, borrowed_at = ? WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, borrowed);
            ps.setString(2, borrowed ? borrower : null);
            ps.setTimestamp(3, borrowed ? Timestamp.valueOf(LocalDateTime.now()) : null);
            ps.setInt(4, bookId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not update the borrowing status.\n" + ex.getMessage(), ex);
        }
    }

    public Stats stats() throws DaoException {
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(borrowed = 0), 0) AS available, "
                + "COALESCE(SUM(borrowed = 1), 0) AS borrowed, "
                + "COALESCE(SUM(book_type = 'Printed'), 0) AS printed, "
                + "COALESCE(SUM(book_type = 'E-Book'), 0) AS ebook "
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
                        rs.getInt("ebook"));
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not compute library statistics.\n" + ex.getMessage(), ex);
        }
        return new Stats(0, 0, 0, 0, 0);
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
        Timestamp borrowedAt = rs.getTimestamp("borrowed_at");
        book.setBorrowedAt(borrowedAt == null ? null : borrowedAt.toLocalDateTime());
        return book;
    }
}
