package com.deihs.library.ui;

import com.deihs.library.dao.DaoException;
import com.deihs.library.dao.UserDao;
import com.deihs.library.model.Role;
import com.deihs.library.util.Validation;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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
 */
public class UsersView extends VBox {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("MMM d, yyyy \u00B7 h:mm a", Locale.ENGLISH);

    private final MainView main;
    private final UserDao dao = new UserDao();

    private final ObservableList<UserDao.Account> rows = FXCollections.observableArrayList();
    private final TableView<UserDao.Account> table = new TableView<>(rows);
    private final TextField search = new TextField();
    private final Label countLabel = new Label();
    private final HBox statsRow = new HBox(12);

    private List<UserDao.Account> all = new ArrayList<>();

    public UsersView(MainView main) {
        this.main = main;
        setSpacing(14);
        setPadding(new Insets(22, 26, 26, 26));

        Node toolbar = buildToolbar();
        Node filters = buildFilterRow();
        Node tableCard = buildTableCard();

        getChildren().addAll(toolbar, statsRow, filters, tableCard);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        statsRow.setFillHeight(true);
        refresh();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private Node buildToolbar() {
        Label subtitle = new Label("Create accounts and control what each person can do. "
                + "Only an administrator can see this page.");
        subtitle.getStyleClass().add("muted");
        subtitle.setFont(Font.font("SansSerif", 12));

        countLabel.getStyleClass().add("muted");
        countLabel.setFont(Font.font("SansSerif", 11));

        VBox left = new VBox(3, subtitle, countLabel);

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

    private Node buildFilterRow() {
        search.setPromptText("Search by name, username, role, or status\u2026");
        search.getStyleClass().add("field");
        search.setPrefHeight(40);
        search.setMaxWidth(Double.MAX_VALUE);
        search.textProperty().addListener((o, a, b) -> applyFilter());
        HBox.setHgrow(search, Priority.ALWAYS);

        Button clear = new Button("Clear");
        clear.getStyleClass().add("ghost-btn");
        clear.setPrefHeight(40);
        clear.setOnAction(e -> search.clear());

        HBox row = new HBox(10, search, clear);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildTableCard() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(placeholder("No accounts match your search."));
        table.setFixedCellSize(64);
        table.setPrefHeight(460);

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

        TableColumn<UserDao.Account, String> lastCol = new TableColumn<>("Last sign-in");
        lastCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().lastLogin() == null ? "Never" : data.getValue().lastLogin().format(STAMP)));
        lastCol.setMinWidth(145);

        TableColumn<UserDao.Account, Void> actionCol = new TableColumn<>("Manage");
        actionCol.setMinWidth(105);
        actionCol.setMaxWidth(125);
        actionCol.setSortable(false);
        actionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
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

                MenuItem unlock = new MenuItem("Unlock account");
                unlock.setDisable(!account.isLocked());
                unlock.setOnAction(e -> unlock(account));

                MenuItem toggle = new MenuItem(account.active() ? "Disable account" : "Enable account");
                toggle.setOnAction(e -> toggleActive(account));

                MenuItem delete = new MenuItem("Delete account");
                delete.setOnAction(e -> delete(account));

                menu.getItems().addAll(role, reset, unlock, toggle, new SeparatorMenuItem(), delete);
                setGraphic(menu);
            }
        });

        table.getColumns().setAll(List.of(nameCol, roleCol, statusCol, lastCol, actionCol));

        VBox card = new VBox(table);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(6));
        VBox.setVgrow(table, Priority.ALWAYS);
        return card;
    }

    private Node placeholder(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("muted");
        label.setFont(Font.font("SansSerif", 12));
        StackPane pane = new StackPane(label);
        pane.setPadding(new Insets(40));
        return pane;
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    public void refresh() {
        try {
            all = dao.listAccounts();
            UserDao.Summary summary = dao.summary();
            statsRow.getChildren().setAll(
                    statCard("Total", String.valueOf(summary.total()), "stat-blue"),
                    statCard("Admins", String.valueOf(summary.admins()), "stat-purple"),
                    statCard("Librarians", String.valueOf(summary.librarians()), "stat-green"),
                    statCard("Students", String.valueOf(summary.students()), "stat-amber"),
                    statCard("Locked / Disabled",
                            summary.locked() + " / " + summary.disabled(), "stat-pink"));
            countLabel.setText(all.size() + (all.size() == 1 ? " account" : " accounts")
                    + "  \u2022  " + summary.admins() + " administrator"
                    + (summary.admins() == 1 ? "" : "s"));
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not load accounts", ex.getMessage());
        }
        applyFilter();
    }

    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<UserDao.Account> filtered = new ArrayList<>();
        for (UserDao.Account account : all) {
            if (query.isEmpty()
                    || account.displayName().toLowerCase(Locale.ROOT).contains(query)
                    || account.username().toLowerCase(Locale.ROOT).contains(query)
                    || account.role().label().toLowerCase(Locale.ROOT).contains(query)
                    || account.statusText().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(account);
            }
        }
        rows.setAll(filtered);
    }

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

    private boolean isSelf(UserDao.Account account) {
        return account.id() == main.user().getId();
    }

    private long activeAdmins() {
        long count = 0;
        for (UserDao.Account account : all) {
            if (account.role() == Role.ADMIN && account.active()) {
                count++;
            }
        }
        return count;
    }

    private boolean isLastActiveAdmin(UserDao.Account account) {
        return account.role() == Role.ADMIN && account.active() && activeAdmins() <= 1;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void createAccount() {
        NewAccount draft = showCreateDialog();
        if (draft == null) {
            return;
        }
        try {
            dao.create(draft.username(), draft.displayName(), draft.password(), draft.role());
            main.setStatus("Created the " + draft.role().label().toLowerCase(Locale.ROOT)
                    + " account \"" + draft.username().trim().toLowerCase(Locale.ROOT) + "\".");
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not create the account", ex.getMessage());
        }
    }

    private void changeRole(UserDao.Account account) {
        if (isSelf(account)) {
            Dialogs.error(main.window(), "Not allowed",
                    "You cannot change your own role. Ask another administrator to do it.");
            return;
        }
        Role chosen = showRoleDialog(account);
        if (chosen == null || chosen == account.role()) {
            return;
        }
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
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not change the role", ex.getMessage());
        }
    }

    private void resetPassword(UserDao.Account account) {
        String password = showPasswordDialog(account);
        if (password == null) {
            return;
        }
        try {
            dao.resetPassword(account.id(), account.username(), password);
            main.setStatus("Reset the password for " + account.displayName()
                    + ". Their lockout was cleared.");
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not reset the password", ex.getMessage());
        }
    }

    private void unlock(UserDao.Account account) {
        try {
            dao.unlock(account.id());
            main.setStatus("Unlocked " + account.displayName() + "'s account.");
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not unlock the account", ex.getMessage());
        }
    }

    private void toggleActive(UserDao.Account account) {
        if (isSelf(account)) {
            Dialogs.error(main.window(), "Not allowed",
                    "You cannot disable the account you are signed in with.");
            return;
        }
        if (account.active() && isLastActiveAdmin(account)) {
            Dialogs.error(main.window(), "Keep one administrator",
                    "\"" + account.displayName() + "\" is the last active administrator.\n"
                            + "Promote another account before disabling this one.");
            return;
        }
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
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not update the account", ex.getMessage());
        }
    }

    private void delete(UserDao.Account account) {
        if (isSelf(account)) {
            Dialogs.error(main.window(), "Not allowed",
                    "You cannot delete the account you are signed in with.");
            return;
        }
        if (isLastActiveAdmin(account)) {
            Dialogs.error(main.window(), "Keep one administrator",
                    "\"" + account.displayName() + "\" is the last active administrator.\n"
                            + "Promote another account before deleting this one.");
            return;
        }
        if (!Dialogs.confirm(main.window(), "Delete account",
                "Permanently delete the account for \"" + account.displayName() + "\" ("
                        + account.username() + ")?\n\nThis cannot be undone.")) {
            return;
        }
        try {
            dao.delete(account.id());
            main.setStatus("Deleted the account for " + account.displayName() + ".");
            refresh();
        } catch (DaoException ex) {
            Dialogs.error(main.window(), "Could not delete the account", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Dialogs
    // ------------------------------------------------------------------

    private record NewAccount(String username, String displayName, String password, Role role) {
    }

    private NewAccount showCreateDialog() {
        Dialog<NewAccount> dialog = new Dialog<>();
        dialog.setTitle("New Account");
        dialog.setHeaderText("Create a library account");
        dialog.initOwner(main.window());
        Dialogs.style(dialog.getDialogPane());

        ButtonType createType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        TextField username = new TextField();
        username.setPromptText("Letters only, 3-20 characters");
        username.getStyleClass().add("field");
        username.setPrefHeight(38);

        Label usernameHint = new Label("Letters only \u2014 no numbers or symbols.");
        usernameHint.getStyleClass().add("hint-label");
        usernameHint.setWrapText(true);
        username.textProperty().addListener((o, a, b) -> {
            String problem = Validation.usernameProblem(b);
            usernameHint.setText(problem == null
                    ? "Looks good." : problem);
            usernameHint.getStyleClass().removeAll("hint-label", "field-error");
            usernameHint.getStyleClass().add(problem == null ? "hint-label" : "field-error");
        });

        TextField displayName = new TextField();
        displayName.setPromptText("Full name shown in the app");
        displayName.getStyleClass().add("field");
        displayName.setPrefHeight(38);

        ComboBox<Role> role = new ComboBox<>();
        role.getItems().setAll(Role.values());
        role.setConverter(roleConverter());
        role.setValue(Role.STUDENT);
        role.getStyleClass().add("combo-box");
        role.setMaxWidth(Double.MAX_VALUE);
        role.setPrefHeight(38);

        PasswordField password = new PasswordField();
        password.setPromptText("At least 8 characters, with a letter and a number");
        password.getStyleClass().add("field");
        password.setPrefHeight(38);

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repeat the password");
        confirm.getStyleClass().add("field");
        confirm.setPrefHeight(38);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(6, 4, 4, 4));
        form.addRow(0, fieldLabel("Username"), username);
        form.addRow(1, new Label(), usernameHint);
        form.addRow(2, fieldLabel("Display name"), displayName);
        form.addRow(3, fieldLabel("Role"), role);
        form.addRow(4, fieldLabel("Password"), password);
        form.addRow(5, fieldLabel("Confirm"), confirm);

        Label error = new Label(" ");
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMinHeight(28);
        form.add(error, 1, 6);

        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setHgrow(displayName, Priority.ALWAYS);
        GridPane.setHgrow(role, Priority.ALWAYS);
        GridPane.setHgrow(password, Priority.ALWAYS);
        GridPane.setHgrow(confirm, Priority.ALWAYS);

        dialog.getDialogPane().setContent(form);

        Node createButton = dialog.getDialogPane().lookupButton(createType);
        if (createButton != null) {
            createButton.addEventFilter(ActionEvent.ACTION, event -> {
                String problem = Validation.usernameProblem(username.getText());
                if (problem != null) {
                    error.setText(problem);
                    event.consume();
                    return;
                }
                problem = Validation.displayNameProblem(displayName.getText());
                if (problem != null) {
                    error.setText(problem);
                    event.consume();
                    return;
                }
                problem = Validation.passwordProblem(password.getText());
                if (problem != null) {
                    error.setText(problem);
                    event.consume();
                    return;
                }
                if (!password.getText().equals(confirm.getText())) {
                    error.setText("The two passwords do not match.");
                    event.consume();
                    return;
                }
                try {
                    if (dao.exists(username.getText())) {
                        error.setText("That username is already taken.");
                        event.consume();
                    }
                } catch (DaoException ex) {
                    error.setText(ex.getMessage());
                    event.consume();
                }
            });
        }

        dialog.setResultConverter(button -> button == createType
                ? new NewAccount(username.getText().trim(),
                        displayName.getText() == null || displayName.getText().isBlank()
                                ? username.getText().trim() : displayName.getText().trim(),
                        password.getText(),
                        role.getValue() == null ? Role.STUDENT : role.getValue())
                : null);
        return dialog.showAndWait().orElse(null);
    }

    private Role showRoleDialog(UserDao.Account account) {
        Dialog<Role> dialog = new Dialog<>();
        dialog.setTitle("Change role");
        dialog.setHeaderText("Choose a new role for " + account.displayName());
        dialog.initOwner(main.window());
        Dialogs.style(dialog.getDialogPane());

        ButtonType saveType = new ButtonType("Save Role", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        ComboBox<Role> role = new ComboBox<>();
        role.getItems().setAll(Role.values());
        role.setConverter(roleConverter());
        role.setValue(account.role());
        role.getStyleClass().add("combo-box");
        role.setMaxWidth(Double.MAX_VALUE);
        role.setPrefHeight(38);

        Label description = new Label(account.role().description());
        description.getStyleClass().add("hint-label");
        description.setWrapText(true);
        role.valueProperty().addListener((o, a, b) ->
                description.setText(b == null ? "" : b.description()));

        VBox box = new VBox(8, fieldLabel("Role"), role, description);
        box.setPadding(new Insets(6, 4, 4, 4));
        box.setPrefWidth(380);
        dialog.getDialogPane().setContent(box);

        dialog.setResultConverter(button -> button == saveType ? role.getValue() : null);
        return dialog.showAndWait().orElse(null);
    }

    private String showPasswordDialog(UserDao.Account account) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Reset password");
        dialog.setHeaderText("Set a new password for " + account.displayName());
        dialog.initOwner(main.window());
        Dialogs.style(dialog.getDialogPane());

        ButtonType saveType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        PasswordField password = new PasswordField();
        password.setPromptText("At least 8 characters, with a letter and a number");
        password.getStyleClass().add("field");
        password.setPrefHeight(38);

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repeat the password");
        confirm.getStyleClass().add("field");
        confirm.setPrefHeight(38);

        Label error = new Label(" ");
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMinHeight(28);

        VBox box = new VBox(8, fieldLabel("New password"), password,
                fieldLabel("Confirm password"), confirm, error);
        box.setPadding(new Insets(6, 4, 4, 4));
        box.setPrefWidth(400);
        dialog.getDialogPane().setContent(box);

        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        if (saveButton != null) {
            saveButton.addEventFilter(ActionEvent.ACTION, event -> {
                String problem = Validation.passwordProblem(password.getText());
                if (problem != null) {
                    error.setText(problem);
                    event.consume();
                    return;
                }
                if (!password.getText().equals(confirm.getText())) {
                    error.setText("The two passwords do not match.");
                    event.consume();
                }
            });
        }

        dialog.setResultConverter(button -> button == saveType ? password.getText() : null);
        return dialog.showAndWait().orElse(null);
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

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
