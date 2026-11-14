# Balagtas DEIHS Library Management System

A modern desktop library management system for **Balagtas DEIHS**, built with
**JavaFX** and **MySQL/MariaDB**. It replaces the old Swing prototype with a
clean dashboard, a visual book-cover collection, a borrowing desk, and a
maintainable catalog — with separate **administrator**, **librarian** and
**student** accounts.

![Dashboard](docs/screenshot-dashboard.png)

---

## Accounts and permissions

Every account has exactly one role, and the role decides which pages and actions
are available. Permission checks live in `model/Role.java`, so a screen cannot
offer an action the role may not perform.

| Capability | Administrator | Librarian | Student |
| --- | :---: | :---: | :---: |
| Dashboard with library-wide statistics | ✅ | ✅ | — |
| Dashboard with their own loans | — | — | ✅ |
| Browse the catalog | ✅ | ✅ | ✅ |
| Add / edit / delete books and covers | ✅ | — | — |
| Issue and return books for any student | ✅ | ✅ | — |
| Monitor every loan, due date and overdue title | ✅ | ✅ | — |
| Borrow and return their **own** books | — | — | ✅ |
| Manage accounts (roles, passwords, lockout) | ✅ | — | — |

Students are limited to **3 books at a time** and a **7-day loan**. They never
see the circulation desk or other students' loans; the **My Loans** page is
their whole workflow.

Librarians see the **Circulation** desk, which lets them pick a student, issue a
book with a due date, and return it — plus a filter for available, on-loan and
overdue titles.

Administrators get everything: the full collection editor, the circulation
desk, and the **Accounts** page for creating users and changing roles.

---

## Features

| Area | What it does |
| --- | --- |
| **Sign in / out** | Three account types with role-filtered navigation. Use **Sign out** in the top bar (or at the bottom of the sidebar) to return to the login screen. |
| **Dashboard** | Staff see live counters (total titles, available, on loan, overdue, categories, users); students see their own loan count, due-soon and overdue totals. |
| **Collection** | Full grid of book covers with title, author, category and availability. Administrators click any cover to edit the title; other roles see a read-only grid. |
| **Circulation** | Staff pick a student, then issue or return books. Availability, borrower, due date and loan state are saved immediately. |
| **My Loans** | Students borrow from the available shelves and return their own books, with the 3-book limit and due dates enforced. |
| **Catalog** | Searchable table of every title. Administrators can add, edit and delete titles, including cover images. |
| **Accounts** | Administrators create accounts, change roles, reset passwords, unlock locked accounts, disable accounts, and delete accounts. |
| **Cover photos** | Upload a JPG/PNG for any book. When a title has no cover, the app draws a gradient placeholder jacket so the grid never looks broken. |

The interface uses a navy full-height sidebar, pastel statistic cards and a
consistent light-blue theme defined in a single stylesheet.

---

## Requirements

- **JDK 21 or newer** (the build targets Java 21; it runs on later JDKs)
- **Maven 3.8+**
- **MySQL 8** or **MariaDB 10.6+** running locally or on the network
- A graphical desktop session (JavaFX needs a display)

Check your toolchain:

```bash
java -version
mvn -version
```

---

## 1. Create the database

The schema, the three demo accounts and eight sample titles are all in
`database/schema.sql`.

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p < database/schema.sql
```

For MariaDB the command is the same (`mariadb` also works):

```bash
mariadb -h 127.0.0.1 -P 3307 -u root < database/schema.sql
```

The script creates the `deihs_library` database, the `users` and `books`
tables, and seeds the catalog. It is safe to re-run: existing rows are kept.

### Upgrading a database from the previous release

`database/migrate_roles.sql` adds the role, lockout and loan columns without
dropping any table or row. It also promotes the old administrator flag to the
`ADMIN` role and links existing loans to accounts.

```bash
mariadb -h 127.0.0.1 -P 3307 -u root deihs_library < database/migrate_roles.sql
```

The application also runs the same migration automatically at launch
(`db/Schema.java`), so the script is only needed when you want to prepare the
database by hand.

Verify the import:

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p -e "SELECT username, role FROM deihs_library.users;"
```

---

## 2. Point the app at your database

Connection settings live in
`src/main/resources/db.properties`:

```properties
db.host=127.0.0.1
db.port=3307
db.name=deihs_library
db.user=root
db.password=
```

Edit that file to match your server. Every value can also be overridden at
launch time without touching the file — handy when the database is on another
PC:

```bash
mvn javafx:run -Ddb.host=192.168.1.10 -Ddb.port=3306 -Ddb.user=library -Ddb.password=secret
```

> **Note:** the bundled `db.properties` targets MariaDB on port **3307**. Change
> `db.port` to `3306` for a default MySQL installation.

---

## 3. Run the application

From the project folder:

```bash
./run.sh
```

or directly with Maven:

```bash
mvn javafx:run
```

The first launch compiles the sources automatically. If the database is
unreachable the app shows a clear error dialog naming the host, port and
database it tried.

### Build a runnable jar (optional)

```bash
mvn package
java --module-path "$JAVA_HOME/javafx/lib" \
     --add-modules javafx.controls \
     -jar target/deihs-library-hub-1.0.0.jar
```

A JavaFX runtime (SDK) must be on the module path when launching the packaged
jar.

---

## Demo accounts

| Username | Password | Role | What they see |
| --- | --- | --- | --- |
| `admin` | `admin123` | Administrator | Everything, including the Accounts page |
| `librarian` | `librarian123` | Librarian | Dashboard, Circulation, Catalog |
| `student` | `student123` | Student | Dashboard, My Loans, Catalog |

> Change these passwords before the system is used by real staff. An
> administrator can reset any password from **Accounts → Manage**.

New accounts created from the sign-in screen are always **students**. Only an
administrator can create librarian or administrator accounts.

---

## Sign-in security

- **Password storage** — new passwords are hashed with
  **PBKDF2WithHmacSHA256** (120,000 iterations, 16-byte random salt). The old
  SHA-256 hashes are upgraded automatically the next time that user signs in
  successfully.
- **Lockout** — four failed attempts lock the account for **5 minutes**. While
  locked, even the correct password is refused. An administrator can unlock it
  immediately from **Accounts**.
- **Username rules** — usernames are **letters only**, 3–20 characters. Typing a
  digit or symbol shows a live *“Username may contain letters only”* warning
  before the form is submitted, and the server rejects it as well.
- **No account enumeration** — a wrong username and a wrong password return the
  same message.
- **Disabled accounts** — an administrator can disable an account, which blocks
  sign-in until it is re-enabled.
- **Last administrator guard** — the app refuses to delete, demote or disable the
  last active administrator, and you cannot delete your own account.

---

## Where cover photos live

Uploaded covers are written to the **`covers/`** folder next to the project
root (created automatically). The database stores only the generated filename
in `books.cover_file`, for example:

```
covers/8f3c1d2e-....jpg
```

- Supported upload formats: JPG, JPEG, PNG, GIF, BMP, WEBP.
- Images are center-cropped to the card aspect ratio when displayed.
- Deleting a book from the Catalog removes its cover file as well.
- If a cover file is missing, a generated gradient jacket is drawn instead.

Because covers are stored on disk, keep the `covers/` folder with the
application and include it in backups alongside the database.

---

## Project layout

```
Balagtas DEIHS Library Management System/
├── database/
│   ├── schema.sql               Schema, three demo accounts, sample books
│   └── migrate_roles.sql        In-place upgrade from the single-admin build
├── covers/                      Uploaded cover images (created at runtime)
├── pom.xml                      Maven build (JavaFX 21, MySQL Connector/J)
├── run.sh                       Convenience launcher
└── src/main/
    ├── java/com/deihs/library/
    │   ├── App.java             Entry point, scene switching
    │   ├── db/
    │   │   ├── Db.java          Connection pool/config
    │   │   └── Schema.java      Automatic column migration at launch
    │   ├── dao/                 UserDao, BookDao, exceptions
    │   ├── model/               User, Book, Role (permission matrix)
    │   ├── ui/                  LoginView, MainView, DashboardView,
    │   │                        CollectionPanel, CirculationView,
    │   │                        MyLoansView, UsersView, CatalogView,
    │   │                        BookFormView, BookCard
    │   └── util/                Covers, Passwords, Validation
    └── resources/
        ├── css/app.css          Complete visual theme
        ├── db.properties        Connection settings
        └── images/deihs-logo.png
```

---

## How it works

- **Authentication** — `UserDao.login` looks the account up by username, checks
  the lockout and disabled flags, then verifies the password with
  `Passwords.verify`. Passwords are stored as PBKDF2WithHmacSHA256 hashes; a
  legacy SHA-256 hash is re-hashed with PBKDF2 on the next successful sign-in.
- **Permissions** — `model/Role.java` is the single source of truth. Each page
  asks the role before showing an action, and `MainView` only builds the nav
  entries the role is allowed to use.
- **Persistence** — every action calls a DAO method, so the database is always
  the source of truth. Refreshing a page re-queries MySQL.
- **Loans** — `books.borrowed`, `borrower_id`, `borrower`, `borrowed_at` and
  `due_at` track the loan. Staff flip them from the Circulation desk; students
  flip them from My Loans. A borrower may hold at most `Book.MAX_LOANS` (3)
  titles at once, and a loan is due `Book.LOAN_DAYS` (7) days after issue.
- **Editing** — clicking a cover or using **Edit** in the Catalog opens the same
  form; it returns a `Book` and the caller saves it, so add and edit behave
  identically.

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| “Cannot connect to the library database” | Confirm MySQL/MariaDB is running, the port matches `db.properties`, and the schema was imported. |
| “Access denied for user” | Update `db.user` / `db.password`, or grant the user access to `deihs_library`. |
| `Unknown database 'deihs_library'` | Run `database/schema.sql`. |
| Window does not open on a server | JavaFX needs a display. Use a desktop session, or run with a virtual display (e.g. `xvfb-run mvn javafx:run`). |
| Covers show placeholders | The `covers/` folder or the file named in `books.cover_file` is missing. Re-upload the cover. |

---

## Notes

- The legacy `Book1.java` file in the project root is the original prototype and
  is **not** part of the Maven build.
- The app targets Java 21 bytecode but is tested on newer JDKs.
