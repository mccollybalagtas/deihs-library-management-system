-- ============================================================
-- Upgrade an existing DEIHS database to the three-role build
--
-- Run once:
--   mariadb -u root deihs_library < database/migrate_roles.sql
--
-- The application also performs these steps at launch (db/Schema.java), so
-- this script is only needed when you want to prepare the database by hand.
-- It never drops a table or a row.
--
-- Use it when: the database was created by the older two-role build and has
-- no `role`, `locked_until`, `borrower_id` or `due_at` columns.
-- Do not use it on a fresh install: database/schema.sql already creates the
-- final shape, and this script assumes the older shape is present.
-- Order matters: users are upgraded first because the books step links loans
-- to user ids.
-- ============================================================

-- Target the existing database by name; this script does not create it.
USE deihs_library;

-- ---------- users ----------
-- Add the columns the three-role build needs, in one table rebuild.
-- AFTER is only cosmetic: it keeps the column order readable in DESCRIBE.
ALTER TABLE users
  -- Authorisation level; existing rows default to STUDENT until repaired below.
  ADD COLUMN role            VARCHAR(20) NOT NULL DEFAULT 'STUDENT' AFTER display_name,
  -- Wrong-password counter used by the lockout policy.
  ADD COLUMN failed_attempts INT         NOT NULL DEFAULT 0         AFTER is_admin,
  -- End of the current lockout; NULL means not locked.
  ADD COLUMN locked_until    DATETIME    NULL                       AFTER failed_attempts,
  -- Soft enable switch so an account can be disabled without deletion.
  ADD COLUMN active          TINYINT(1)  NOT NULL DEFAULT 1         AFTER locked_until,
  -- Last successful sign-in, shown on the account card.
  ADD COLUMN last_login      DATETIME    NULL                       AFTER active,
  -- The user list filters by role, so index it now that the column exists.
  ADD INDEX idx_users_role (role);

-- PBKDF2 hashes are about 100 characters; CHAR(64) would truncate them.
-- MODIFY keeps the NOT NULL rule and only widens the type.
ALTER TABLE users MODIFY password_hash VARCHAR(255) NOT NULL;

-- The old administrator flag becomes the ADMIN role.
-- This is the one-time data conversion: every flagged admin keeps its rights.
UPDATE users SET role = 'ADMIN' WHERE is_admin = 1;

-- Never leave the system without an administrator.
-- If the flag conversion produced no admin, promote the lowest id. The two
-- derived tables are needed because MySQL cannot read the target table of an
-- UPDATE inside a plain subquery.
UPDATE users SET role = 'ADMIN'
WHERE id = (SELECT id FROM (SELECT MIN(id) AS id FROM users) AS first_user)
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM users WHERE role = 'ADMIN') AS admins);

-- Reverse sync so the legacy flag agrees with the authoritative role column
-- for every row, including accounts that were students or librarians.
UPDATE users SET is_admin = CASE WHEN role = 'ADMIN' THEN 1 ELSE 0 END;

-- ---------- books ----------
-- Add the loan-ownership columns the three-role build uses. The two new
-- indexes match the queries the catalog and dashboard run: "my loans" and
-- "due soon / overdue".
ALTER TABLE books
  -- users.id of the borrower; NULL while the copy is available.
  ADD COLUMN borrower_id INT      NULL AFTER borrower,
  -- Due moment, from which the UI derives the overdue colouring.
  ADD COLUMN due_at      DATETIME NULL AFTER borrowed_at,
  ADD INDEX idx_books_borrower (borrower_id),
  ADD INDEX idx_books_due (due_at);

-- Link existing loans to accounts by display name and give them a due date.
-- The old build stored only the borrower's typed name, so display_name is the
-- only join key available. Rows whose name matches no account keep a NULL
-- borrower_id rather than being dropped; only active loans are touched.
UPDATE books b
  JOIN users u ON u.display_name = b.borrower
SET b.borrower_id = u.id
WHERE b.borrowed = 1 AND b.borrower_id IS NULL;

-- Backfill a due date for loans that predate the column: the loan period is
-- 7 days, the same default the borrow action uses. Guarded so a real due date
-- is never overwritten.
UPDATE books
SET due_at = DATE_ADD(borrowed_at, INTERVAL 7 DAY)
WHERE borrowed = 1 AND due_at IS NULL AND borrowed_at IS NOT NULL;

-- ---------- demo accounts ----------
-- admin / admin123 (existing account keeps working)
-- The admin row is not touched here: it already exists with its hash and was
-- given the ADMIN role by the UPDATE above.
-- Librarian and student are added only when missing; the no-op ON DUPLICATE
-- clause leaves an existing row (and any changed password) alone.
-- The hashes are PBKDF2-HMAC-SHA256 strings produced by the app's own
-- routine, so they verify without a first-sign-in upgrade.
INSERT INTO users (username, password_hash, display_name, role, is_admin)
VALUES ('librarian', 'pbkdf2$sha256$120000$ZHDkP5bossPqRsYD3va3Bg==$K66XAVSdQBONGXvuZ7jpjZaa4Fip9Kb5ip1mzHUE+Tw=', 'Librarian', 'LIBRARIAN', 0)
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO users (username, password_hash, display_name, role, is_admin)
VALUES ('student', 'pbkdf2$sha256$120000$hrW4T2zOdBJMtpqKqw1peg==$ZmQ4stUUEornxHg/uoasbNkK+Z0IjsMBqcq6dgyPlv0=', 'Student', 'STUDENT', 0)
ON DUPLICATE KEY UPDATE username = username;
