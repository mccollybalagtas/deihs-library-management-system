-- ============================================================
-- Balagtas DEIHS Library Management System - MySQL schema
-- Database : deihs_library
-- Server   : MariaDB / MySQL on 127.0.0.1:3307
--
-- Fresh install:  mariadb -u root < database/schema.sql
-- Existing install: run database/migrate_roles.sql instead.
--
-- What this file does
--   Creates the database, the two tables, their indexes, the three demo
--   accounts and a small sample catalog in one pass.
-- Fresh install
--   Run this file on an empty server, then start the application.
-- Upgrade
--   Do not run this file on an old database; run database/migrate_roles.sql,
--   which only adds the columns an older install is missing.
-- Safety
--   Every statement is IF NOT EXISTS or guarded, so a second run adds nothing
--   and never drops a table or a row.
-- ============================================================

-- Create the database container only. utf8mb4 holds every character the
-- catalog needs (accents, the Filipino titles further down) and
-- utf8mb4_unicode_ci makes title searches and sorting case-insensitive.
-- IF NOT EXISTS keeps a re-run harmless.
CREATE DATABASE IF NOT EXISTS deihs_library
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Every statement below applies to this database, so the script needs no
-- database name on the command line.
USE deihs_library;

-- ------------------------------------------------------------
-- Users
--
-- One row per person who can sign in. `role` decides what the UI allows, and
-- books.borrower_id points back to users.id, so a row here is the anchor for
-- every loan that account has made. That link is why the UI deactivates
-- accounts instead of deleting them: a delete would orphan loan history.
--
-- role            ADMIN | LIBRARIAN | STUDENT
-- failed_attempts wrong passwords since the last successful sign-in
-- locked_until    sign-in is refused until this moment
-- active          0 disables the account without deleting its history
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  -- Surrogate key; books.borrower_id and the session both refer to it.
  id              INT AUTO_INCREMENT PRIMARY KEY,
  -- Login name typed at the sign-in screen; UNIQUE stops two accounts
  -- sharing a name, which would make lookups ambiguous.
  username        VARCHAR(50)  NOT NULL UNIQUE,
  -- PBKDF2 string ("pbkdf2$sha256$iterations$salt$hash"), about 100
  -- characters, so 255 leaves room for a longer iteration count later.
  password_hash   VARCHAR(255) NOT NULL,
  -- Friendly name shown in the sidebar, tables and receipts.
  display_name    VARCHAR(100) NOT NULL,
  -- Authorisation level read by the UI guards; ADMIN may manage accounts,
  -- LIBRARIAN runs the desk, STUDENT may only borrow and return.
  role            VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
  -- Legacy flag from the two-role build. Kept in sync with role = 'ADMIN'
  -- so older queries still work; `role` is the value the app trusts.
  is_admin        TINYINT(1)   NOT NULL DEFAULT 0,
  -- Counts consecutive wrong passwords; reset to 0 on a good sign-in.
  failed_attempts INT          NOT NULL DEFAULT 0,
  -- Set when failed_attempts reaches the limit; NULL means not locked.
  locked_until    DATETIME     NULL,
  -- Soft enable switch: 0 blocks sign-in but keeps the row and its history.
  active          TINYINT(1)   NOT NULL DEFAULT 1,
  -- Last successful sign-in, shown on the account card.
  last_login      DATETIME     NULL,
  -- Row creation time, filled by MySQL so no code has to supply it.
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- The user list and the admin-count guard filter by role, so index it
  -- rather than scanning the table on every page load.
  INDEX idx_users_role (role)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Books
--
-- One row per physical or digital copy in the catalog. A row is either
-- available (borrowed = 0, loan columns NULL) or on loan (borrowed = 1,
-- borrower_id / borrowed_at / due_at filled).
--
-- borrower_id / due_at are filled while a title is on loan.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS books (
  -- Surrogate key used by the book cards and by borrow/return requests.
  id          INT AUTO_INCREMENT PRIMARY KEY,
  -- Title as printed on the cover; also the key the seed data matches on.
  title       VARCHAR(255) NOT NULL,
  -- Author or issuing body; shown under the title on every card.
  author      VARCHAR(255) NOT NULL,
  -- Free-text shelf group (Programming, Filipiniana, Mathematics, ...)
  -- used by the catalog filter chips.
  category    VARCHAR(100) NOT NULL,
  -- Physical format: Printed | E-Book | Archive. Picks the badge and icon.
  book_type   VARCHAR(20)  NOT NULL DEFAULT 'Printed',
  -- Optional size or page count shown in the card's detail line.
  extra_info  VARCHAR(100) NULL,
  -- Optional file name of an uploaded cover; NULL means draw a placeholder.
  cover_file  VARCHAR(255) NULL,
  -- Availability switch: 1 while a copy is out, 0 when it is on the shelf.
  borrowed    TINYINT(1)   NOT NULL DEFAULT 0,
  -- Borrower name as typed in the old build; kept for display and so the
  -- migration can match old loans to accounts by display_name.
  borrower    VARCHAR(100) NULL,
  -- users.id of the account holding the copy; NULL when available. This is
  -- the authoritative owner of the loan in the three-role build.
  borrower_id INT          NULL,
  -- Moment the copy went out; the due date is derived from it.
  borrowed_at DATETIME     NULL,
  -- Moment the copy is due back; the UI colours the row by urgency.
  due_at      DATETIME     NULL,
  -- Row creation time, filled by MySQL on insert.
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- Title search and the "already seeded?" guards scan by title.
  INDEX idx_books_title (title),
  -- The category filter chips group rows by this column.
  INDEX idx_books_category (category),
  -- The dashboard counts available vs borrowed, filtering on this flag.
  INDEX idx_books_borrowed (borrowed),
  -- "My loans" and the return action look rows up by borrower.
  INDEX idx_books_borrower (borrower_id),
  -- The due-soon and overdue lists sort and filter by due date.
  INDEX idx_books_due (due_at)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Demo accounts
--
--   admin      / admin123       (ADMIN, full control)
--   librarian  / librarian123   (LIBRARIAN, circulation desk)
--   student    / student123     (STUDENT, borrow and return)
--
-- Passwords are stored as PBKDF2-HMAC-SHA256 strings, not plain text, so the
-- stored value cannot be read back as a password.
--
-- The admin row keeps the original SHA-256 hash so an older database can be
-- imported unchanged; the login code upgrades it to PBKDF2 on first sign-in.
-- Change these passwords before using the system for real records.
-- ------------------------------------------------------------
-- ON DUPLICATE KEY UPDATE username = username is a deliberate no-op: if the
-- account already exists it is left untouched (password, role and all),
-- so re-running the script never resets a password someone has changed.
INSERT INTO users (username, password_hash, display_name, role, is_admin)
VALUES ('admin', 'bf6b5bdb74c79ece9fc0ad0ac9fb0359f9555d4f35a83b2e6ec69ae99e09603d', 'Administrator', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE username = username;

-- Librarian demo account; hash was produced by the same PBKDF2 routine the
-- app uses, so it verifies without any upgrade step.
INSERT INTO users (username, password_hash, display_name, role, is_admin)
VALUES ('librarian', 'pbkdf2$sha256$120000$ZHDkP5bossPqRsYD3va3Bg==$K66XAVSdQBONGXvuZ7jpjZaa4Fip9Kb5ip1mzHUE+Tw=', 'Librarian', 'LIBRARIAN', 0)
ON DUPLICATE KEY UPDATE username = username;

-- Student demo account; is_admin stays 0 because the role column carries
-- the authority in this build.
INSERT INTO users (username, password_hash, display_name, role, is_admin)
VALUES ('student', 'pbkdf2$sha256$120000$hrW4T2zOdBJMtpqKqw1peg==$ZmQ4stUUEornxHg/uoasbNkK+Z0IjsMBqcq6dgyPlv0=', 'Student', 'STUDENT', 0)
ON DUPLICATE KEY UPDATE username = username;

-- ------------------------------------------------------------
-- Sample catalog (covers are optional; UI draws a placeholder)
--
-- INSERT ... SELECT ... WHERE NOT EXISTS makes each row idempotent: a second
-- run skips titles that are already present instead of duplicating them.
-- The first insert only checks "is the table empty at all", so on a database
-- that already has books it never adds Effective Java; the rest match on
-- their own title. NULL cover_file means the card shows a drawn placeholder.
-- ------------------------------------------------------------
-- First row: the SELECT list is aliased so it can be wrapped in a derived
-- table, which is the portable way to add a WHERE to a constant row set.
INSERT INTO books (title, author, category, book_type, extra_info)
SELECT * FROM (SELECT 'Effective Java' AS t, 'Joshua Bloch' AS a, 'Programming' AS c, 'Printed' AS ty, '412 pages' AS ex) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM books LIMIT 1);

-- Remaining seeds are guarded by an exact title match.
INSERT INTO books (title, author, category, book_type, extra_info)
SELECT 'Clean Code', 'Robert C. Martin', 'Programming', 'E-Book', '12.4 MB'
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'Clean Code');

INSERT INTO books (title, author, category, book_type, extra_info)
SELECT 'Head First Design Patterns', 'Eric Freeman', 'Programming', 'Printed', '694 pages'
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'Head First Design Patterns');

-- The doubled '' is an escaped apostrophe inside the SQL string literal.
-- LIKE 'Salt-salti%' is used so a later title tweak still counts as present.
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
