-- ============================================================
-- Upgrade an existing DEIHS database to the three-role build
--
-- Run once:
--   mariadb -u root deihs_library < database/migrate_roles.sql
--
-- The application also performs these steps at launch (db/Schema.java), so
-- this script is only needed when you want to prepare the database by hand.
-- It never drops a table or a row.
-- ============================================================

USE deihs_library;

-- ---------- users ----------
ALTER TABLE users
  ADD COLUMN role            VARCHAR(20) NOT NULL DEFAULT 'STUDENT' AFTER display_name,
  ADD COLUMN failed_attempts INT         NOT NULL DEFAULT 0         AFTER is_admin,
  ADD COLUMN locked_until    DATETIME    NULL                       AFTER failed_attempts,
  ADD COLUMN active          TINYINT(1)  NOT NULL DEFAULT 1         AFTER locked_until,
  ADD COLUMN last_login      DATETIME    NULL                       AFTER active,
  ADD INDEX idx_users_role (role);

-- PBKDF2 hashes are about 100 characters; CHAR(64) would truncate them.
ALTER TABLE users MODIFY password_hash VARCHAR(255) NOT NULL;

-- The old administrator flag becomes the ADMIN role.
UPDATE users SET role = 'ADMIN' WHERE is_admin = 1;

-- Never leave the system without an administrator.
UPDATE users SET role = 'ADMIN'
WHERE id = (SELECT id FROM (SELECT MIN(id) AS id FROM users) AS first_user)
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM users WHERE role = 'ADMIN') AS admins);

UPDATE users SET is_admin = CASE WHEN role = 'ADMIN' THEN 1 ELSE 0 END;

-- ---------- books ----------
ALTER TABLE books
  ADD COLUMN borrower_id INT      NULL AFTER borrower,
  ADD COLUMN due_at      DATETIME NULL AFTER borrowed_at,
  ADD INDEX idx_books_borrower (borrower_id),
  ADD INDEX idx_books_due (due_at);

-- Link existing loans to accounts by display name and give them a due date.
UPDATE books b
  JOIN users u ON u.display_name = b.borrower
SET b.borrower_id = u.id
WHERE b.borrowed = 1 AND b.borrower_id IS NULL;

UPDATE books
SET due_at = DATE_ADD(borrowed_at, INTERVAL 7 DAY)
WHERE borrowed = 1 AND due_at IS NULL AND borrowed_at IS NOT NULL;

-- ---------- demo accounts ----------
-- admin / admin123 (existing account keeps working)
INSERT INTO users (username, password_hash, display_name, role, is_admin)
VALUES ('librarian', 'pbkdf2$sha256$120000$ZHDkP5bossPqRsYD3va3Bg==$K66XAVSdQBONGXvuZ7jpjZaa4Fip9Kb5ip1mzHUE+Tw=', 'Librarian', 'LIBRARIAN', 0)
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO users (username, password_hash, display_name, role, is_admin)
VALUES ('student', 'pbkdf2$sha256$120000$hrW4T2zOdBJMtpqKqw1peg==$ZmQ4stUUEornxHg/uoasbNkK+Z0IjsMBqcq6dgyPlv0=', 'Student', 'STUDENT', 0)
ON DUPLICATE KEY UPDATE username = username;
