-- ============================================================
-- DEIHS Library Hub - MySQL schema
-- Database : deihs_library
-- Server   : MariaDB / MySQL on 127.0.0.1:3307
-- ============================================================

CREATE DATABASE IF NOT EXISTS deihs_library
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE deihs_library;

-- ------------------------------------------------------------
-- Users (no roles; one built-in administrator account)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash CHAR(64)     NOT NULL,
  display_name  VARCHAR(100) NOT NULL,
  is_admin      TINYINT(1)   NOT NULL DEFAULT 0,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Books
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS books (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(255) NOT NULL,
  author      VARCHAR(255) NOT NULL,
  category    VARCHAR(100) NOT NULL,
  book_type   VARCHAR(20)  NOT NULL DEFAULT 'Printed',
  extra_info  VARCHAR(100) NULL,
  cover_file  VARCHAR(255) NULL,
  borrowed    TINYINT(1)   NOT NULL DEFAULT 0,
  borrower    VARCHAR(100) NULL,
  borrowed_at DATETIME     NULL,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_books_title (title),
  INDEX idx_books_category (category),
  INDEX idx_books_borrowed (borrowed)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Default administrator (password: admin123)
-- Hash = SHA-256("admin:admin123")
-- ------------------------------------------------------------
INSERT INTO users (username, password_hash, display_name, is_admin)
VALUES ('admin', 'bf6b5bdb74c79ece9fc0ad0ac9fb0359f9555d4f35a83b2e6ec69ae99e09603d', 'Administrator', 1)
ON DUPLICATE KEY UPDATE username = username;

-- ------------------------------------------------------------
-- Sample catalog (covers are optional; UI draws a placeholder)
-- ------------------------------------------------------------
INSERT INTO books (title, author, category, book_type, extra_info)
SELECT * FROM (SELECT 'Effective Java' AS t, 'Joshua Bloch' AS a, 'Programming' AS c, 'Printed' AS ty, '412 pages' AS ex) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM books LIMIT 1);

INSERT INTO books (title, author, category, book_type, extra_info)
SELECT 'Clean Code', 'Robert C. Martin', 'Programming', 'E-Book', '12.4 MB'
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'Clean Code');

INSERT INTO books (title, author, category, book_type, extra_info)
SELECT 'Head First Design Patterns', 'Eric Freeman', 'Programming', 'Printed', '694 pages'
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'Head First Design Patterns');

INSERT INTO books (title, author, category, book_type, extra_info)
SELECT 'Salt-salti: Sa Dugo''t Pawis', 'DEIHS Research', 'Filipiniana', 'Archive', 'School research'
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title LIKE 'Salt-salti%');

INSERT INTO books (title, author, category, book_type, extra_info)
SELECT 'Noli Me Tangere', 'Jose Rizal', 'Filipiniana', 'Printed', '352 pages'
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'Noli Me Tangere');

INSERT INTO books (title, author, category, book_type, extra_info)
SELECT 'El Filibusterismo', 'Jose Rizal', 'Filipiniana', 'E-Book', '3.1 MB'
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'El Filibusterismo');

INSERT INTO books (title, author, category, book_type, extra_info)
SELECT 'Introduction to Algorithms', 'Thomas H. Cormen', 'Mathematics', 'Printed', '1312 pages'
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'Introduction to Algorithms');

INSERT INTO books (title, author, category, book_type, extra_info)
SELECT 'The Little Prince', 'Antoine de Saint-Exupery', 'Fiction', 'Printed', '96 pages'
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'The Little Prince');
