package com.deihs.library.ui;

import com.deihs.library.dao.DaoException;
import com.deihs.library.dao.UserDao;
import com.deihs.library.model.Role;
import com.deihs.library.util.Validation;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Administrator-only account manager.
 *
 * <p>Every guard here is deliberately conservative: you cannot delete your own
 * account, and you cannot demote, disable or delete the last active
 * administrator. The database is the final authority, but these checks keep the
 * mistake from ever reaching it.</p>
 *
 * <p>Part of: the account administration screen, the "Accounts" page in the
 * sidebar. Every other screen in this app can only be reached by an
 * administrator through this one.</p>
 *
 * <p>Used by: {@code MainView.showPage} (case "users") creates it and calls
 * {@link #refresh()} each time the page is shown again, and
 * {@code MainView.focusSearch()} calls {@link #focusSearch()} for Ctrl+F.</p>
 *
 * <p>Key rules: a username is 3-20 letters and nothing else; a password is at
 * least 8 characters containing one letter and one digit; the last active
 * administrator can never be demoted, disabled or deleted; and no administrator
 * can change, disable or delete the account they are signed in with.</p>
 */
public class UsersView extends VBox {

    // ============================================================
    // PART: Fields and state
    // ============================================================

    /** Date format for "last sign-in" and "locked until" cells, e.g. "Nov 5, 2026 · 3:42 PM". */
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("MMM d, yyyy \u00B7 h:mm a", Locale.ENGLISH);

    /** The parent screen. Used for the dialog owner window, toasts, and the signed-in user's id. */
    private final MainView main;
    /** All account reads and writes go through this DAO; the view never touches SQL itself. */
    private final UserDao dao = new UserDao();

    /** What the table actually displays. Replaced wholesale by {@link #applyFilter()}. */
    private final ObservableList<UserDao.Account> rows = FXCollections.observableArrayList();
    /** The account grid. Fixed row height (set later) so 64px custom cells line up. */
    private final TableView<UserDao.Account> table = new TableView<>(rows);
    /** Free-text filter box; every change restarts the debounce below. */
    private final TextField search = new TextField();
    /** Small "12 accounts • 2 administrators" caption under the page subtitle. */
    private final Label countLabel = new Label();
    /** Holds the five coloured summary tiles; rebuilt on every refresh. */
    private final HBox statsRow = new HBox(12);
    /** Waits 220 ms after the last keystroke before filtering, so typing does not refilter per letter. */
    private final Debounce searchDebounce = new Debounce(Duration.millis(220), this::applyFilter);

    /**
     * The full, unfiltered account list last loaded from the database.
     * Filtering and the "last active admin" guard both work from this snapshot,
     * so neither triggers an extra query.
     */
    private List<UserDao.Account> all = new ArrayList<>();

    /**
     * WHAT: Assembles the page from toolbar, summary tiles, filter row and table.
     * WHY: The page is built once and only refreshed afterwards, so construction
     *      order decides the vertical layout.
     * CALLED BY: MainView.showPage, first time the Accounts item is opened.
     */
    public UsersView(MainView main) {
        this.main = main;
        setSpacing(14);
        setPadding(new Insets(22, 26, 26, 26));

        // Step 1: build the four stacked regions.
        Node toolbar = buildToolbar();
        Node filters = buildFilterRow();
        Node tableCard = buildTableCard();

        // Step 2: stack them and let the table card absorb any spare height.
        getChildren().addAll(toolbar, statsRow, filters, tableCard);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        // Step 3: let the tiles fill their row, then load accounts from the database.
        statsRow.setFillHeight(true);
        refresh();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /**
     * WHAT: Page subtitle, account count caption and the "+ New Account" button.
     * WHY: The count is a label kept in a field so {@link #refresh()} can rewrite
     *      it without rebuilding the row.
     * CALLED BY: The constructor.
     */
    private Node buildToolbar() {
        Label subtitle = new Label("Create accounts and control what each person can do. "
                + "Only an administrator can see this page.");
        subtitle.getStyleClass().add("muted");
        subtitle.setFont(Font.font("SansSerif", 12));

        countLabel.getStyleClass().add("muted");
        countLabel.setFont(Font.font("SansSerif", 11));

        VBox left = new VBox(3, subtitle, countLabel);

        // An empty Region that grows, pushing the button to the right edge.
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button add = new Button("+  New Account");
        add.getStyleClass().add("primary-btn");
        add.setPrefHeight(40);
        add.setOnAction(e -> createAccount());

        HBox row = new HBox(10, left, spacer, add);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * WHAT: The search box and its Clear button.
     * WHY: Every keystroke only restarts the 220 ms debounce; the actual filtering
     *      happens once in {@link #applyFilter()}, in memory, over {@link #all}.
     * CALLED BY: The constructor.
     */
    private Node buildFilterRow() {
        search.setPromptText("Search by name, username, role, or status\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(40);
        search.setMaxWidth(Double.MAX_VALUE);
        // Listener fires per character; the debounce collapses a burst of typing into one filter pass.
        search.textProperty().addListener((o, a, b) -> searchDebounce.trigger());
        HBox.setHgrow(search, Priority.ALWAYS);

        Button clear = new Button("Clear");
        clear.getStyleClass().add("ghost-btn");
        clear.setPrefHeight(40);
        // clear() empties the box, which fires the listener above, so the table resets too.
        clear.setOnAction(e -> search.clear());

        HBox row = new HBox(10, search, clear);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * WHAT: Builds the table and wraps it in a white card.
     * WHY: The five columns are built here and installed in one call so the
     *      column order is obvious in one place.
     * CALLED BY: The constructor.
     */
    private Node buildTableCard() {
        // Step 1: table-level settings. Cells are a fixed 64px so the two-line
        // name/status cells never get clipped; the placeholder shows when no rows match.
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(EmptyState.host(accountsEmptyState()));
        table.setFixedCellSize(64);
        table.setPrefHeight(460);

        // Step 2: "Account" column - two lines, display name over @username.
        // The value factory only supplies a key for sorting; the cell factory
        // draws the real content.
        TableColumn<UserDao.Account, String> nameCol = new TableColumn<>("Account");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayName()));
        nameCol.setMinWidth(210);
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                UserDao.Account account = getTableRow().getItem();

                Label name = new Label(account.displayName());
                name.setFont(Font.font("SansSerif", FontWeight.BOLD, 12.5));

                Label username = new Label("@" + account.username());
                username.getStyleClass().add("muted");
                username.setFont(Font.font("SansSerif", 10.5));

                VBox box = new VBox(2, name, username);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
                setText(null);
            }
        });

        // Step 3: "Role" column - a coloured pill. The style class is derived from
        // the enum name, so ADMIN -> "role-admin" and the colour lives in app.css.
        TableColumn<UserDao.Account, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().role().label()));
        roleCol.setMinWidth(130);
        roleCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Role role = getTableRow().getItem().role();
                Label pill = new Label(role.label());
                pill.getStyleClass().addAll("role-pill",
                        "role-" + role.name().toLowerCase(Locale.ROOT));
                setGraphic(pill);
                setText(null);
            }
        });

        // Step 4: "Status" column - pill from the Account record, plus one extra
        // line of context: when a lock ends, or how many sign-ins have failed.
        TableColumn<UserDao.Account, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().statusText()));
        statusCol.setMinWidth(130);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                UserDao.Account account = getTableRow().getItem();
                Label pill = new Label(account.statusText());
                pill.getStyleClass().add(account.statusStyle());

                if (account.isLocked() && account.lockedUntil() != null) {
                    Label until = new Label("until " + account.lockedUntil().format(STAMP));
                    until.getStyleClass().add("muted");
                    until.setFont(Font.font("SansSerif", 9.5));
                    VBox box = new VBox(2, pill, until);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                } else if (account.failedAttempts() > 0) {
                    Label tries = new Label(account.failedAttempts() + " failed "
                            + (account.failedAttempts() == 1 ? "attempt" : "attempts"));
                    tries.getStyleClass().add("muted");
                    tries.setFont(Font.font("SansSerif", 9.5));
                    VBox box = new VBox(2, pill, tries);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                } else {
                    setGraphic(pill);
                }
                setText(null);
            }
        });

        // Step 5: "Last sign-in" column - plain formatted text, no custom cell.
        // A never-used account shows "Never" instead of a blank cell.
        TableColumn<UserDao.Account, String> lastCol = new TableColumn<>("Last sign-in");
        lastCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().lastLogin() == null ? "Never" : data.getValue().lastLogin().format(STAMP)));
        lastCol.setMinWidth(145);

        // Step 6: "Manage" column - a MenuButton per row holding every action.
        // It is not sortable because it has no underlying value (Void).
        TableColumn<UserDao.Account, Void> actionCol = new TableColumn<>("Manage");
        actionCol.setMinWidth(105);
        actionCol.setMaxWidth(125);
        actionCol.setSortable(false);
        actionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                // Cells are recycled as the table scrolls, so read the row item
                // every time rather than capturing one account in the closure.
                UserDao.Account account = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || account == null) {
                    setGraphic(null);
                    return;
                }

                MenuButton menu = new MenuButton("Manage");
                menu.getStyleClass().add("row-btn");

                MenuItem role = new MenuItem("Change role\u2026");
                role.setOnAction(e -> changeRole(account));

                MenuItem reset = new MenuItem("Reset password\u2026");
                reset.setOnAction(e -> resetPassword(account));

                // Unlock is offered but greyed out unless the lock is actually in force.
                MenuItem unlock = new MenuItem("Unlock account");
                unlock.setDisable(!account.isLocked());
                unlock.setOnAction(e -> unlock(account));

                // One item that flips between Disable and Enable, worded for the current state.
                MenuItem toggle = new MenuItem(account.active() ? "Disable account" : "Enable account");
                toggle.setOnAction(e -> toggleActive(account));

                MenuItem delete = new MenuItem("Delete account");
                delete.setOnAction(e -> delete(account));

                // Separator keeps the destructive item away from the everyday ones.
                menu.getItems().addAll(role, reset, unlock, toggle, new SeparatorMenuItem(), delete);
                setGraphic(menu);
            }
        });

        // Step 7: install the columns left to right, then place the table inside the card.
        table.getColumns().setAll(List.of(nameCol, roleCol, statusCol, lastCol, actionCol));

        VBox card = new VBox(table);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(6));
        VBox.setVgrow(table, Priority.ALWAYS);
        return card;
    }

    /**
     * WHAT: The message shown inside the table when it has no rows.
     * WHY: "No accounts yet" and "nothing matches your search" are different
     *      problems, so the text and the offered action depend on the search box.
     * CALLED BY: buildTableCard (initial placeholder) and applyFilter (after each filter).
     */
    private EmptyState accountsEmptyState() {
        String query = search.getText() == null ? "" : search.getText().trim();
        if (!query.isEmpty()) {
            return new EmptyState("\u2315", "No accounts match \u201C" + query + "\u201D",
                    "Search checks names, usernames, roles, and status. Try a shorter term.")
                    .action("Clear search", search::clear);
        }
        return new EmptyState("\u263A", "No accounts yet",
                "Create the first account so someone can sign in.")
                .action("+  New Account", this::createAccount);
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    /** Ctrl+F jumps to this page's search box. */
    /**
     * WHAT: Puts the cursor in the search box and selects what is there.
     * WHY: Ctrl+F should land on the filter of the page you are looking at.
     * CALLED BY: MainView.focusSearch(), when the user presses Ctrl+F on this page.
     */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    /**
     * WHAT: Reloads every account and the summary counts, rebuilds the tiles,
     *       rewrites the caption, then re-applies the current filter.
     * WHY: One method does all of it so no action can leave the page half-updated.
     * CALLED BY: The constructor, every successful action below, and
     *            MainView.showPage when the page is revisited.
     */
    public void refresh() {
        try {
            // Step 1: two queries - the rows, and the counts used by the tiles.
            all = dao.listAccounts();
            UserDao.Summary summary = dao.summary();
            // Step 2: rebuild the five tiles. The class name picks the colour.
            statsRow.getChildren().setAll(
                    statCard("Total", String.valueOf(summary.total()), "stat-blue"),
                    statCard("Admins", String.valueOf(summary.admins()), "stat-purple"),
                    statCard("Librarians", String.valueOf(summary.librarians()), "stat-green"),
                    statCard("Students", String.valueOf(summary.students()), "stat-amber"),
                    statCard("Locked / Disabled",
                            summary.locked() + " / " + summary.disabled(), "stat-pink"));
            // Step 3: caption, singular/plural handled inline.
            countLabel.setText(all.size() + (all.size() == 1 ? " account" : " accounts")
                    + "  \u2022  " + summary.admins() + " administrator"
                    + (summary.admins() == 1 ? "" : "s"));
        } catch (DaoException ex) {
            // A failed load leaves the old rows on screen; the dialog explains why.
            Dialogs.error(main.window(), "Could not load accounts", ex.getMessage());
        }
        // Step 4: re-run the filter so a refresh never silently widens the list.
        applyFilter();
    }

    /**
     * WHAT: Copies the accounts matching the search box into {@link #rows}.
     * WHY: Filtering happens in memory over the cached list, so typing costs no
     *      database round-trips; the match is a case-insensitive "contains" on
     *      display name, username, role label or status text.
     * CALLED BY: The debounce in buildFilterRow, and refresh().
     */
    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<UserDao.Account> filtered = new ArrayList<>();
        for (UserDao.Account account : all) {
            // An empty query keeps every account; otherwise any one field matching is enough.
            if (query.isEmpty()
                    || account.displayName().toLowerCase(Locale.ROOT).contains(query)
                    || account.username().toLowerCase(Locale.ROOT).contains(query)
                    || account.role().label().toLowerCase(Locale.ROOT).contains(query)
                    || account.statusText().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(account);
            }
        }
        // setAll on the backing ObservableList updates the table; no table.refresh() needed.
        rows.setAll(filtered);
        // The placeholder text depends on whether the search box is empty, so rebuild it.
        table.setPlaceholder(EmptyState.host(accountsEmptyState()));
    }

    /**
     * WHAT: One summary tile: small uppercase title above a large number.
     * WHY: Five of these differ only in text and colour, so the shape lives here once.
     * CALLED BY: refresh().
     */
    private VBox statCard(String title, String value, String styleClass) {
        Label titleLabel = new Label(title.toUpperCase(Locale.ROOT));
        titleLabel.getStyleClass().add("stat-title");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");

        VBox card = new VBox(5, titleLabel, valueLabel);
        card.getStyleClass().addAll("stat-card", styleClass);
        card.setPrefHeight(76);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    // ------------------------------------------------------------------
    // Guards
    // ------------------------------------------------------------------

    /**
     * WHAT: True when the row is the signed-in administrator's own account.
     * WHY: Self-service actions on your own account could lock you out of the
     *      only page that can undo them.
     * CALLED BY: changeRole, toggleActive and delete.
     */
    private boolean isSelf(UserDao.Account account) {
        return account.id() == main.user().getId();
    }

    /**
     * WHAT: How many accounts are ADMIN and still enabled.
     * WHY: Counted from the cached list, so it always agrees with what the table shows.
     * CALLED BY: isLastActiveAdmin.
     */
    private long activeAdmins() {
        long count = 0;
        for (UserDao.Account account : all) {
            if (account.role() == Role.ADMIN && account.active()) {
                count++;
            }
        }
        return count;
    }

    /**
     * WHAT: True when this row is the only enabled administrator left.
     * WHY: Demoting, disabling or deleting that row would leave nobody able to
     *      manage accounts, which is unrecoverable from inside the app.
     * CALLED BY: changeRole, toggleActive and delete.
     */
    private boolean isLastActiveAdmin(UserDao.Account account) {
        return account.role() == Role.ADMIN && account.active() && activeAdmins() <= 1;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /**
     * WHAT: Opens the New Account dialog, saves the result, then refreshes.
     * WHY: The dialog does all validation; this method only persists and reports.
     * CALLED BY: The "+ New Account" button and the empty-state action.
     */
    private void createAccount() {
        // A null result means the dialog was cancelled (or closed and discarded).
        NewAccount draft = showCreateDialog();
        if (draft == null) {
            return;
        }
        try {
            // The DAO lowercases and hashes the username/password; the view passes raw text.
            dao.create(draft.username(), draft.displayName(), draft.password(), draft.role());
            main.setStatus("Created the " + draft.role().label().toLowerCase(Locale.ROOT)
                    + " account \"" + draft.username().trim().toLowerCase(Locale.ROOT) + "\".");
            main.toastSuccess("Created the " + draft.role().label().toLowerCase(Locale.ROOT)
                    + " account \"" + draft.username().trim().toLowerCase(Locale.ROOT) + "\".");
            refresh();
        } catch (DaoException ex) {
            // A duplicate username reaches here as a database error, not a dialog hint.
            Dialogs.error(main.window(), "Could not create the account", ex.getMessage());
        }
    }

    /**
     * WHAT: Picks a new role for one account and saves it.
     * WHY: Two guards run before the dialog and before the write: no self-edit,
     *      and never remove the last enabled administrator.
     * CALLED BY: The "Change role..." item in a row's Manage menu.
     */
    private void changeRole(UserDao.Account account) {
        // Guard 1: your own role is off limits; ask a second administrator.
        if (isSelf(account)) {
            Dialogs.error(main.window(), "Not allowed",
                    "You cannot change your own role. Ask another administrator to do it.");
            return;
        }
        Role chosen = showRoleDialog(account);
        // Nothing to do when cancelled or when the same role was picked again.
        if (chosen == null || chosen == account.role()) {
            return;
        }
        // Guard 2: moving the only administrator to another role would lock the page out.
        if (chosen != Role.ADMIN && isLastActiveAdmin(account)) {
            Dialogs.error(main.window(), "Keep one administrator",
                    "\"" + account.displayName() + "\" is the last active administrator.\n"
                            + "Promote another account before changing this role.");
            return;
        }
        try {
            dao.setRole(account.id(), chosen);
            main.setStatus(account.displayName() + " is now a "
                    + chosen.label().toLowerCase(Locale.ROOT) + ".");
            main.toastSuccess(account.displayName() + " is now a "
                    + chosen.label().toLowerCase(Locale.ROOT) + ".");
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not change the role", ex.getMessage());
        }
    }

    /**
     * WHAT: Sets a new password for one account, typed twice in a dialog.
     * WHY: The DAO also clears failed_attempts and locked_until, which is why the
     *      confirmation message mentions the lockout.
     * CALLED BY: The "Reset password..." item in a row's Manage menu.
     */
    private void resetPassword(UserDao.Account account) {
        String password = showPasswordDialog(account);
        if (password == null) {
            return;
        }
        try {
            dao.resetPassword(account.id(), account.username(), password);
            main.setStatus("Reset the password for " + account.displayName()
                    + ". Their lockout was cleared.");
            main.toastSuccess("Reset the password for " + account.displayName()
                    + ". Their lockout was cleared.");
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not reset the password", ex.getMessage());
        }
    }

    /**
     * WHAT: Clears a lockout without touching the password.
     * WHY: A user who simply mistyped too often should not need a new password.
     * CALLED BY: The "Unlock account" item, enabled only while a lock is active.
     */
    private void unlock(UserDao.Account account) {
        try {
            dao.unlock(account.id());
            main.setStatus("Unlocked " + account.displayName() + "'s account.");
            main.toastSuccess("Unlocked " + account.displayName() + "'s account.");
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not unlock the account", ex.getMessage());
        }
    }

    /**
     * WHAT: Flips one account between enabled and disabled after a confirmation.
     * WHY: Disabling is the reversible alternative to deleting; the row and its
     *      history stay, the person simply cannot sign in.
     * CALLED BY: The "Disable account" / "Enable account" item in the Manage menu.
     */
    private void toggleActive(UserDao.Account account) {
        // Guard 1: you cannot disable the session you are working in.
        if (isSelf(account)) {
            Dialogs.error(main.window(), "Not allowed",
                    "You cannot disable the account you are signed in with.");
            return;
        }
        // Guard 2: only blocks disabling; re-enabling is always safe.
        if (account.active() && isLastActiveAdmin(account)) {
            Dialogs.error(main.window(), "Keep one administrator",
                    "\"" + account.displayName() + "\" is the last active administrator.\n"
                            + "Promote another account before disabling this one.");
            return;
        }
        // The target state is the opposite of the current one; the wording follows it.
        boolean enable = !account.active();
        String verb = enable ? "Enable" : "Disable";
        if (!Dialogs.confirm(main.window(), verb + " account",
                verb + " the account for \"" + account.displayName() + "\"?\n\n"
                        + (enable
                        ? "They will be able to sign in again."
                        : "They will be signed out and cannot sign in until re-enabled."))) {
            return;
        }
        try {
            dao.setActive(account.id(), enable);
            main.setStatus((enable ? "Enabled " : "Disabled ") + account.displayName() + "'s account.");
            main.toastSuccess((enable ? "Enabled " : "Disabled ") + account.displayName() + "'s account.");
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not update the account", ex.getMessage());
        }
    }

    /**
     * WHAT: Permanently removes one account row.
     * WHY: Irreversible, so it uses the destructive confirmation style and both guards.
     * CALLED BY: The "Delete account" item in the Manage menu.
     */
    private void delete(UserDao.Account account) {
        // Guard 1: deleting yourself would end the session mid-action.
        if (isSelf(account)) {
            Dialogs.error(main.window(), "Not allowed",
                    "You cannot delete the account you are signed in with.");
            return;
        }
        // Guard 2: the last enabled administrator must survive.
        if (isLastActiveAdmin(account)) {
            Dialogs.error(main.window(), "Keep one administrator",
                    "\"" + account.displayName() + "\" is the last active administrator.\n"
                            + "Promote another account before deleting this one.");
            return;
        }
        // confirmDestructive paints the confirm button red and defaults the focus to Cancel.
        if (!Dialogs.confirmDestructive(main.window(), "Delete account",
                "Permanently delete the account for \"" + account.displayName() + "\" ("
                        + account.username() + ")?\n\nThis cannot be undone.",
                "Delete account")) {
            return;
        }
        try {
            dao.delete(account.id());
            main.setStatus("Deleted the account for " + account.displayName() + ".");
            main.toastSuccess("Deleted the account for " + account.displayName() + ".");
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not delete the account", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Dialogs
    // ------------------------------------------------------------------

    // ============================================================
    // PART: Create-account dialog
    // ============================================================

    /** What the create dialog returns when Create Account is pressed. */
    private record NewAccount(String username, String displayName, String password, Role role) {
    }

    /**
     * WHAT: The New Account form; returns the typed values, or null if cancelled.
     * WHY: All validation lives in the dialog so nothing invalid can be submitted,
     *      and FormSupport keeps the messages, focus and discard prompt consistent
     *      with every other data-entry dialog in the app.
     * CALLED BY: createAccount().
     */
    private NewAccount showCreateDialog() {
        // Step 1: dialog shell, owned by the main window so it centres on the app.
        Dialog<NewAccount> dialog = new Dialog<>();
        dialog.setTitle("New Account");
        dialog.setHeaderText("Create a library account");
        dialog.initOwner(main.window());
        Dialogs.style(dialog.getDialogPane());

        // The custom OK button is looked up later to bind its enabled state.
        ButtonType createType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        // Step 2: username field. The rule delegates to Validation, which enforces
        // 3-20 letters and explains the failure in a hint under the field.
        TextField username = new TextField();
        username.setPromptText("Letters only, 3-20 characters");
        username.getStyleClass().add("field");
        username.setPrefHeight(38);

        Label usernameHint = hintLabel();
        FormSupport.TextRule usernameRule = new FormSupport.TextRule(username, usernameHint,
                () -> Validation.usernameProblem(username.getText()),
                "Letters only \u2014 no numbers or symbols.");

        // Step 3: display name. Optional in practice: the result converter below
        // falls back to the username, but the rule still asks for something typed.
        TextField displayName = new TextField();
        displayName.setPromptText("Full name shown in the app");
        displayName.getStyleClass().add("field");
        displayName.setPrefHeight(38);

        Label displayNameHint = hintLabel();
        FormSupport.TextRule displayNameRule = new FormSupport.TextRule(displayName, displayNameHint,
                () -> Validation.displayNameProblem(displayName.getText()));

        // Step 4: role picker. Defaults to the least privileged role, so a rushed
        // form cannot accidentally create another administrator.
        ComboBox<Role> role = new ComboBox<>();
        role.getItems().setAll(Role.values());
        role.setConverter(roleConverter());
        role.setValue(Role.STUDENT);
        role.getStyleClass().add("combo-box");
        role.setMaxWidth(Double.MAX_VALUE);
        role.setPrefHeight(38);

        // Step 5: password + confirmation. Validation requires 8+ characters with
        // a letter and a digit; the confirm rule only checks that the two match.
        PasswordField password = new PasswordField();
        password.setPromptText("At least 8 characters, with a letter and a number");
        password.getStyleClass().add("field");
        password.setPrefHeight(38);

        Label passwordHint = hintLabel();
        FormSupport.TextRule passwordRule = new FormSupport.TextRule(password, passwordHint,
                () -> Validation.passwordProblem(password.getText()));

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repeat the password");
        confirm.getStyleClass().add("field");
        confirm.setPrefHeight(38);

        Label confirmHint = hintLabel();
        FormSupport.TextRule confirmRule = new FormSupport.TextRule(confirm, confirmHint,
                () -> {
                    String value = confirm.getText() == null ? "" : confirm.getText();
                    if (value.isEmpty()) {
                        return "Please repeat the password.";
                    }
                    return value.equals(password.getText())
                            ? null : "The two passwords do not match.";
                });
        // Editing the first password re-checks the confirmation, so a mismatch that
        // was already visible clears as soon as the two fields agree.
        password.textProperty().addListener((o, a, b) -> confirmRule.recheck());

        // Step 6: the Create button stays disabled until all four rules pass.
        // The properties are listed so the binding re-evaluates on each keystroke.
        BooleanBinding valid = Bindings.createBooleanBinding(
                () -> usernameRule.problem() == null
                        && displayNameRule.problem() == null
                        && passwordRule.problem() == null
                        && confirmRule.problem() == null,
                username.textProperty(), displayName.textProperty(),
                password.textProperty(), confirm.textProperty());

        // Step 7: lay the fields out in a two-column grid (label, control+hint).
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(6, 4, 4, 4));
        form.addRow(0, fieldLabel("Username"), fieldBlock(username, usernameHint));
        form.addRow(1, fieldLabel("Display name"), fieldBlock(displayName, displayNameHint));
        form.addRow(2, fieldLabel("Role"), role);
        form.addRow(3, fieldLabel("Password"), fieldBlock(password, passwordHint));
        form.addRow(4, fieldLabel("Confirm"), fieldBlock(confirm, confirmHint));

        // A single line for errors that no field owns, e.g. "username already taken".
        // Its blank starting text reserves the height so the dialog does not jump.
        Label error = new Label(" ");
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMinHeight(28);
        form.add(error, 1, 5);

        GridPane.setHgrow(role, Priority.ALWAYS);

        dialog.getDialogPane().setContent(form);

        // Step 8: intercept Create before the dialog closes. touch() reveals every
        // rule's message, and a duplicate username is a database check, not a rule.
        Node createButton = dialog.getDialogPane().lookupButton(createType);
        if (createButton instanceof Button create) {
            create.disableProperty().bind(valid.not());
            create.addEventFilter(ActionEvent.ACTION, event -> {
                usernameRule.touch();
                displayNameRule.touch();
                passwordRule.touch();
                confirmRule.touch();
                if (!valid.get()) {
                    event.consume();
                    return;
                }
                try {
                    if (dao.exists(username.getText())) {
                        // Mark the field itself, not just the shared error line.
                        FormSupport.markInvalid(username, true);
                        error.setText("That username is already taken.");
                        event.consume();
                    }
                } catch (DaoException ex) {
                    // If the check cannot run, keep the dialog open rather than guess.
                    error.setText(ex.getMessage());
                    event.consume();
                }
            });
        }

        // Step 9: ask before throwing away typing when Cancel or the X is used.
        FormSupport.DirtyGuard guard = new FormSupport.DirtyGuard(
                "This account has not been created yet. Close without saving?");
        guard.watch(username.textProperty());
        guard.watch(displayName.textProperty());
        guard.watch(role.valueProperty());
        guard.watch(password.textProperty());
        guard.watch(confirm.textProperty());
        guard.install(dialog, ButtonType.CANCEL);

        // Step 10: map the button to a result. Cancel yields null, which the caller
        // treats as "do nothing". Blank display name falls back to the username.
        dialog.setResultConverter(button -> button == createType
                ? new NewAccount(username.getText().trim(),
                        displayName.getText() == null || displayName.getText().isBlank()
                                ? username.getText().trim() : displayName.getText().trim(),
                        password.getText(),
                        role.getValue() == null ? Role.STUDENT : role.getValue())
                : null);
        // Focus after the dialog is on screen, not before.
        FormSupport.focus(username);
        return dialog.showAndWait().orElse(null);
    }

    // ============================================================
    // PART: Role dialog
    // ============================================================

    /**
     * WHAT: A one-field dialog that returns the newly chosen role.
     * WHY: It shows the role's description under the picker, so an administrator
     *      sees what the role can do before saving.
     * CALLED BY: changeRole().
     */
    private Role showRoleDialog(UserDao.Account account) {
        Dialog<Role> dialog = new Dialog<>();
        dialog.setTitle("Change role");
        dialog.setHeaderText("Choose a new role for " + account.displayName());
        dialog.initOwner(main.window());
        Dialogs.style(dialog.getDialogPane());

        ButtonType saveType = new ButtonType("Save Role", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // Starts on the account's current role; saving the same value is a no-op in changeRole.
        ComboBox<Role> role = new ComboBox<>();
        role.getItems().setAll(Role.values());
        role.setConverter(roleConverter());
        role.setValue(account.role());
        role.getStyleClass().add("combo-box");
        role.setMaxWidth(Double.MAX_VALUE);
        role.setPrefHeight(38);

        // Live explanation of the highlighted role, taken from Role.description().
        Label description = new Label(account.role().description());
        description.getStyleClass().add("hint-label");
        description.setWrapText(true);
        role.valueProperty().addListener((o, a, b) ->
                description.setText(b == null ? "" : b.description()));

        VBox box = new VBox(8, fieldLabel("Role"), role, description);
        box.setPadding(new Insets(6, 4, 4, 4));
        box.setPrefWidth(380);
        dialog.getDialogPane().setContent(box);

        // Save is unavailable only if the picker is somehow empty.
        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        if (saveButton instanceof Button save) {
            save.disableProperty().bind(role.valueProperty().isNull());
        }

        // Discard prompt only if the selection actually moved away from its start value.
        FormSupport.DirtyGuard guard = new FormSupport.DirtyGuard(
                "The role has not been changed yet. Close without saving?");
        guard.watch(role.valueProperty());
        guard.install(dialog, ButtonType.CANCEL);

        dialog.setResultConverter(button -> button == saveType ? role.getValue() : null);
        FormSupport.focus(role);
        return dialog.showAndWait().orElse(null);
    }

    // ============================================================
    // PART: Password-reset dialog
    // ============================================================

    /**
     * WHAT: Asks for the new password twice and returns it, or null if cancelled.
     * WHY: There is no "old password" field because an administrator does not know
     *      the user's password; the DAO writes the new hash and clears the lockout.
     * CALLED BY: resetPassword().
     */
    private String showPasswordDialog(UserDao.Account account) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Reset password");
        dialog.setHeaderText("Set a new password for " + account.displayName());
        dialog.initOwner(main.window());
        Dialogs.style(dialog.getDialogPane());

        ButtonType saveType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // Same password rule as account creation: 8+ characters, one letter, one digit.
        PasswordField password = new PasswordField();
        password.setPromptText("At least 8 characters, with a letter and a number");
        password.getStyleClass().add("field");
        password.setPrefHeight(38);

        Label passwordHint = hintLabel();
        FormSupport.TextRule passwordRule = new FormSupport.TextRule(password, passwordHint,
                () -> Validation.passwordProblem(password.getText()));

        // Typing it a second time catches a typo before the user is locked out by it.
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repeat the password");
        confirm.getStyleClass().add("field");
        confirm.setPrefHeight(38);

        Label confirmHint = hintLabel();
        FormSupport.TextRule confirmRule = new FormSupport.TextRule(confirm, confirmHint,
                () -> {
                    String value = confirm.getText() == null ? "" : confirm.getText();
                    if (value.isEmpty()) {
                        return "Please repeat the password.";
                    }
                    return value.equals(password.getText())
                            ? null : "The two passwords do not match.";
                });
        // Keep the match check honest when the first field is edited after the second.
        password.textProperty().addListener((o, a, b) -> confirmRule.recheck());

        BooleanBinding valid = Bindings.createBooleanBinding(
                () -> passwordRule.problem() == null && confirmRule.problem() == null,
                password.textProperty(), confirm.textProperty());

        Label error = new Label(" ");
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMinHeight(28);

        // A single column: each field label sits directly above its own control and hint.
        VBox box = new VBox(6,
                fieldLabel("New password"), password, passwordHint,
                fieldLabel("Confirm password"), confirm, confirmHint,
                error);
        box.setPadding(new Insets(6, 4, 4, 4));
        box.setPrefWidth(400);
        dialog.getDialogPane().setContent(box);

        // Same gate as the create dialog: disabled until valid, and touching both
        // rules on click so the reason is visible if the button is pressed early.
        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        if (saveButton instanceof Button save) {
            save.disableProperty().bind(valid.not());
            save.addEventFilter(ActionEvent.ACTION, event -> {
                passwordRule.touch();
                confirmRule.touch();
                if (!valid.get()) {
                    event.consume();
                }
            });
        }

        // A typed password is worth a confirmation before it is discarded.
        FormSupport.DirtyGuard guard = new FormSupport.DirtyGuard(
                "The new password has not been saved. Close without saving?");
        guard.watch(password.textProperty());
        guard.watch(confirm.textProperty());
        guard.install(dialog, ButtonType.CANCEL);

        // Only the first field is returned; the confirmation was just a check.
        dialog.setResultConverter(button -> button == saveType ? password.getText() : null);
        FormSupport.focus(password);
        return dialog.showAndWait().orElse(null);
    }

    // ============================================================
    // PART: Shared form helpers
    // ============================================================

    /** Small bold caption that names a field, e.g. "Username". */
    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    /**
     * The empty message slot under a field. Hidden and unmanaged while it has no
     * text, so it takes no vertical space until FormSupport fills it in.
     */
    private static Label hintLabel() {
        Label hint = new Label();
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        hint.setVisible(false);
        hint.setManaged(false);
        return hint;
    }

    /** A control with its inline message directly underneath. */
    private static VBox fieldBlock(Node control, Label hint) {
        // Stretch the control to the column width; the hint keeps its natural height.
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        VBox box = new VBox(3, control, hint);
        box.setFillWidth(true);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /**
     * Shows a Role as its human label ("Administrator") in the combo box, and
     * parses a typed value back through Role.from, which defaults to STUDENT.
     */
    private static StringConverter<Role> roleConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Role role) {
                return role == null ? "" : role.label();
            }

            @Override
            public Role fromString(String value) {
                return Role.from(value);
            }
        };
    }
}
