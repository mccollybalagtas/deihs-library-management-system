-- ============================================================
-- Balagtas DEIHS Library Management System - MySQL schema
-- Database : deihs_library
-- Server   : MariaDB / MySQL on 127.0.0.1:3307
--
-- Fresh install:  mariadb -u root < database/schema.sql
-- Existing install: run database/migrate_roles.sql instead.
-- ============================================================

CREATE DATABASE IF NOT EXISTS deihs_library
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE deihs_library;

-- ------------------------------------------------------------
-- Users
--
-- role            ADMIN | LIBRARIAN | STUDENT
-- failed_attempts wrong passwords since the last successful sign-in
-- locked_until    sign-in is refused until this moment
-- active          0 disables the account without deleting its history
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  username        VARCHAR(50)  NOT NULL UNIQUE,
  password_hash   VARCHAR(255) NOT NULL,
  display_name    VARCHAR(100) NOT NULL,
  role            VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
  is_admin        TINYINT(1)   NOT NULL DEFAULT 0,
  failed_attempts INT          NOT NULL DEFAULT 0,
  locked_until    DATETIME     NULL,
  active          TINYINT(1)   NOT NULL DEFAULT 1,
  last_login      DATETIME     NULL,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_users_role (role)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Books
--
-- borrower_id / due_at are filled while a title is on loan.
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
  borrower_id INT          NULL,
  borrowed_at DATETIME     NULL,
  due_at      DATETIME     NULL,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_books_title (title),
  INDEX idx_books_category (category),
  INDEX idx_books_borrowed (borrowed),
  INDEX idx_books_borrower (borrower_id),
  INDEX idx_books_due (due_at)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Demo accounts
--
--   admin      / admin123       (ADMIN, full control)
--   librarian  / librarian123   (LIBRARIAN, circulation desk)
--   student    / student123     (STUDENT, borrow and return)
--
-- The admin row keeps the original SHA-256 hash so an older database can be
-- imported unchanged; the login code upgrades it to PBKDF2 on first sign-in.
-- Change these passwords before using the system for real records.
-- ------------------------------------------------------------
INSERT INTO users (username, password_hash, display_name, role, is_admin)
VALUES ('admin', 'bf6b5bdb74c79ece9fc0ad0ac9fb0359f9555d4f35a83b2e6ec69ae99e09603d', 'Administrator', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO users (username, password_hash, display_name, role, is_admin)
VALUES ('librarian', 'pbkdf2$sha256$120000$ZHDkP5bossPqRsYD3va3Bg==$K66XAVSdQBONGXvuZ7jpjZaa4Fip9Kb5ip1mzHUE+Tw=', 'Librarian', 'LIBRARIAN', 0)
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO users (username, password_hash, display_name, role, is_admin)
VALUES ('student', 'pbkdf2$sha256$120000$hrW4T2zOdBJMtpqKqw1peg==$ZmQ4stUUEornxHg/uoasbNkK+Z0IjsMBqcq6dgyPlv0=', 'Student', 'STUDENT', 0)
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
