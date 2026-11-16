package com.deihs.library.ui;

import com.deihs.library.dao.DaoException;
import com.deihs.library.dao.UserDao;
import com.deihs.library.model.User;
import com.deihs.library.util.Validation;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The sign-in screen. Calls {@code onSuccess} once the credentials check out.
 *
 * Part of: Sign-in screen, shown inside the application shell.
 * Used by: App.start() and App.showLogin(), which pass a callback for a successful sign-in.
 * Key rules: the username is checked live as letters only, and UserDao locks the
 *            account for LOCK_MINUTES (5) after MAX_ATTEMPTS (4) bad passwords.
 */
public class LoginView extends StackPane {

    // ============================================================
    // PART: Screen state
    // ============================================================
    // The owner stage is only needed to parent the "create account" dialog.
    private final Stage owner;
    private final Consumer<User> onSuccess;
    private final UserDao userDao = new UserDao();

    private final TextField usernameField = new TextField();
    // Two password controls share one value: a masked field and a plain field.
    // Only one is visible at a time, so "Show password" is just a visibility swap.
    private final PasswordField passwordHidden = new PasswordField();
    private final TextField passwordShown = new TextField();
    private final SimpleStringProperty passwordText = new SimpleStringProperty("");
    private final Label errorLabel = new Label(" ");
    private final Label usernameHint = new Label(" ");
    private final Button signInButton = new Button("Sign In");

    // Countdown that re-enables the button when a lockout expires.
    private Timeline lockTimer;

    /**
     * WHAT: Builds the two-panel sign-in card and focuses the username field.
     * WHY: The screen is the app's entry point, so it must be ready to type in
     * without an extra click.
     * CALLED BY: App.start() and App.showLogin().
     */
    public LoginView(Stage owner, Consumer<User> onSuccess) {
        this.owner = owner;
        this.onSuccess = onSuccess;
        Fonts.load();

        getStyleClass().add("login-bg");
        setAlignment(Pos.CENTER);

        // Step 1: a fixed-size card holds the brand panel and the form side by side.
        HBox card = new HBox(buildBrandPanel(), buildFormPanel());
        card.getStyleClass().add("login-card");
        card.setMaxSize(940, 566);
        card.setPrefSize(940, 566);
        getChildren().add(card);
        setPadding(new Insets(26));

        // Step 2: focus after the scene is laid out; requestFocus() before that
        // is ignored because the control is not on screen yet.
        Platform.runLater(usernameField::requestFocus);
    }

    // ------------------------------------------------------------------
    // Left brand panel
    // ------------------------------------------------------------------
    // ============================================================
    // PART: Building the brand panel
    // ============================================================

    /**
     * WHAT: Builds the dark left-hand panel: logo, school name, motto and a
     * vertical spacer that pins the footer text to the bottom.
     * WHY: It carries the school identity and gives the card a fixed 382px column.
     * CALLED BY: The constructor, when assembling the card.
     */
    private VBox buildBrandPanel() {
        // Step 1: fix the width so the form side can take all the remaining space.
        VBox brand = new VBox(0);
        brand.getStyleClass().add("login-brand");
        brand.setPrefWidth(382);
        brand.setMinWidth(382);
        brand.setMaxWidth(382);
        brand.setPadding(new Insets(92, 38, 40, 38));
        brand.setAlignment(Pos.TOP_CENTER);

        // Step 2: logo and school name form a centred heading block.
        Node logo = buildLogo(88);

        Label name = new Label("Dasmari\u00F1as East\nIntegrated High School");
        name.setFont(Fonts.display(25, FontWeight.BOLD));
        name.setTextFill(Color.WHITE);
        name.setWrapText(true);
        name.setAlignment(Pos.CENTER);
        name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setLineSpacing(2);

        VBox heading = new VBox(logo, name);
        heading.setSpacing(18);
        heading.setAlignment(Pos.CENTER);
        heading.setMaxWidth(Double.MAX_VALUE);

        // Step 3: ALWAYS growth on the spacer pushes the motto and footer down,
        // however tall the panel becomes.
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label quote = new Label("\u201CRead. Learn. Grow.\u201D");
        quote.setFont(Font.font("Serif", FontPosture.ITALIC, 21));
        quote.setTextFill(Color.web("#c5f0fd"));
        quote.setWrapText(true);

        // Step 4: a 1px divider is drawn as an empty Region with a background colour;
        // JavaFX has no dedicated horizontal-rule control.
        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setMaxWidth(64);
        divider.setStyle("-fx-background-color: #2E5C8F;");

        Region gap = new Region();
        gap.setPrefHeight(18);

        Region gap2 = new Region();
        gap2.setPrefHeight(18);

        Label school = new Label("        D E I H S\nSCHOOL LIBRARY");
        school.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
        school.setTextFill(Color.web("#c9edfc"));

        brand.getChildren().addAll(heading, spacer, quote, gap, divider, gap2, school);
        return brand;
    }

    /**
     * WHAT: Loads the school logo at a fixed size, falling back to a letter "D"
     * when the image is missing.
     * WHY: The sign-in card must still look deliberate if the image is not
     * packaged with the build.
     * CALLED BY: buildBrandPanel().
     */
    private Node buildLogo(double size) {
        // Pin the size so the surrounding heading keeps a stable height.
        StackPane holder = new StackPane();
        holder.setPrefSize(size, size);
        holder.setMinSize(size, size);
        holder.setMaxSize(size, size);

        try (InputStream in = LoginView.class.getResourceAsStream("/images/deihs-logo.png")) {
            if (in != null) {
                ImageView view = new ImageView(new Image(in));
                view.setPreserveRatio(true);
                view.setSmooth(true);
                view.setFitWidth(size);
                view.setFitHeight(size);
                holder.getChildren().add(view);
            }
        } catch (Exception ignored) {
            // Fall through to the letter mark below.
        }

        if (holder.getChildren().isEmpty()) {
            Label mark = new Label("D");
            mark.setFont(Font.font("SansSerif", FontWeight.BOLD, size * 0.5));
            mark.setTextFill(Color.web("#0D264C"));
            holder.getChildren().add(mark);
        }
        return holder;
    }

    // ------------------------------------------------------------------
    // Right form panel
    // ------------------------------------------------------------------
    // ============================================================
    // PART: Building the sign-in form
    // ============================================================

    /**
     * WHAT: Builds the white form panel: heading, username, password, error
     * line, Sign In button, register link and footer.
     * WHY: This is the only place credentials are entered, so all the input
     * wiring and feedback lives here.
     * CALLED BY: The constructor, when assembling the card.
     */
    private VBox buildFormPanel() {
        // Step 1: let the form grow to fill the card next to the fixed brand panel.
        VBox form = new VBox(0);
        form.getStyleClass().add("login-form");
        form.setPadding(new Insets(44, 56, 34, 56));
        HBox.setHgrow(form, Priority.ALWAYS);

        // Step 2: heading block.
        Label title = new Label("Welcome back");
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 28));
        title.setTextFill(Color.web("#0D264C"));

        Label caption = new Label("Sign in to borrow, return and manage library books.");
        caption.setFont(Font.font("SansSerif", 12));
        caption.setTextFill(Color.web("#667085"));

        VBox titleBox = new VBox(6, title, caption);
        titleBox.setPadding(new Insets(0, 0, 20, 0));

        // Step 3: username field. The listener runs on every keystroke so a
        // number is flagged before the user presses Sign In.
        Label userLabel = new Label("USERNAME");
        userLabel.getStyleClass().add("field-label");

        usernameField.setPromptText("Letters only, e.g. juan");
        usernameField.getStyleClass().add("field");
        usernameField.setPrefHeight(44);
        usernameField.setMaxWidth(Double.MAX_VALUE);
        usernameField.textProperty().addListener((o, old, value) -> checkUsername(value));

        usernameHint.getStyleClass().add("hint-label");
        usernameHint.setWrapText(true);
        usernameHint.setMinHeight(16);

        // Step 4: password entry uses two controls stacked in the same place:
        // a PasswordField that masks the text and a TextField that shows it.
        Label passLabel = new Label("PASSWORD");
        passLabel.getStyleClass().add("field-label");

        passwordHidden.setPromptText("Enter your password");
        passwordHidden.getStyleClass().add("field");
        passwordHidden.setPrefHeight(44);

        passwordShown.setPromptText("Enter your password");
        passwordShown.getStyleClass().add("field");
        passwordShown.setPrefHeight(44);
        passwordShown.setVisible(false);
        passwordShown.setManaged(false);

        // Both controls are bound to the same property, so typing in either one
        // keeps the other in sync and the rest of the code reads one value.
        passwordHidden.textProperty().bindBidirectional(passwordText);
        passwordShown.textProperty().bindBidirectional(passwordText);

        // StackPane overlays the two fields; the hidden one is removed from the
        // layout with setManaged(false) so it takes no space while invisible.
        StackPane passwordStack = new StackPane(passwordHidden, passwordShown);
        passwordStack.setAlignment(Pos.CENTER_LEFT);
        passwordStack.setPrefHeight(44);
        passwordStack.setMaxWidth(Double.MAX_VALUE);

        // Step 5: the checkbox only swaps which field is visible and focused;
        // the shared password value is untouched.
        CheckBox showPassword = new CheckBox("Show password");
        showPassword.setFont(Font.font("SansSerif", 11));
        showPassword.setTextFill(Color.web("#475467"));
        showPassword.setOnAction(e -> {
            boolean show = showPassword.isSelected();
            passwordHidden.setVisible(!show);
            passwordHidden.setManaged(!show);
            passwordShown.setVisible(show);
            passwordShown.setManaged(show);
            (show ? passwordShown : passwordHidden).requestFocus();
        });

        // Step 6: one shared error line under the password. It keeps a fixed
        // minimum height so the layout does not jump when a message appears.
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        errorLabel.setMinHeight(30);

        // Step 7: Sign In is the default button, so Enter anywhere on the form
        // triggers it.
        signInButton.getStyleClass().add("primary-btn");
        signInButton.setMaxWidth(Double.MAX_VALUE);
        signInButton.setPrefHeight(46);
        signInButton.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));
        signInButton.setDefaultButton(true);
        signInButton.setOnAction(e -> attemptLogin());

        // Enter in the username field moves on to the password; Enter in either
        // password control submits, so the form can be used without the mouse.
        usernameField.setOnAction(e -> passwordHidden.requestFocus());
        passwordHidden.setOnAction(e -> attemptLogin());
        passwordShown.setOnAction(e -> attemptLogin());

        // Step 8: account creation is offered to students only; staff accounts
        // are created by an administrator.
        Label newHere = new Label("New student?");
        newHere.setFont(Font.font("SansSerif", 12));
        newHere.setTextFill(Color.web("#667085"));

        Hyperlink register = new Hyperlink("Create an account");
        register.getStyleClass().add("link");
        register.setOnAction(e -> showRegisterDialog());

        HBox registerRow = new HBox(6, newHere, register);
        registerRow.setAlignment(Pos.CENTER_LEFT);

        // Step 9: stack the controls in reading order. The margins below add a
        // little extra air around the password label and error line.
        VBox fields = new VBox(6,
                userLabel, usernameField, usernameHint,
                passLabel, passwordStack,
                showPassword, errorLabel, signInButton, registerRow);
        VBox.setMargin(passLabel, new Insets(8, 0, 0, 0));
        VBox.setMargin(showPassword, new Insets(2, 0, 4, 0));
        VBox.setMargin(errorLabel, new Insets(0, 0, 2, 0));

        // Step 10: ALWAYS growth pushes the footer to the bottom of the panel.
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Year.now() is read at runtime so the copyright does not go stale.
        Label footer = new Label("\u00A9 " + java.time.Year.now() + " Balagtas DEIHS School Library");
        footer.setFont(Font.font("SansSerif", 10));
        footer.setTextFill(Color.web("#98A2B3"));

        VBox footBox = new VBox(4, footer);
        footBox.setPadding(new Insets(12, 0, 0, 0));

        form.getChildren().addAll(titleBox, fields, spacer, footBox);
        return form;
    }

    // ------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------
    // ============================================================
    // PART: Signing in
    // ============================================================

    /**
     * WHAT: Live feedback while typing, so a number is flagged before Sign In.
     * WHY: The server only accepts letters, so showing the problem immediately
     * saves a round trip and a failed attempt.
     * CALLED BY: The textProperty listener on usernameField in buildFormPanel().
     */
    private void checkUsername(String value) {
        // A blank field is not an error yet; the user may still be typing.
        boolean valid = value == null || value.isBlank() || Validation.isValidUsername(value);
        usernameField.getStyleClass().remove("field-error");
        if (valid) {
            usernameHint.setText(" ");
        } else {
            if (!usernameField.getStyleClass().contains("field-error")) {
                usernameField.getStyleClass().add("field-error");
            }
            usernameHint.setText(Validation.usernameProblem(value));
        }
    }

    /**
     * WHAT: Validates the two fields, asks UserDao to check the credentials and
     * reacts to the result.
     * WHY: All sign-in outcomes (success, disabled account, lockout, wrong
     * password) must be handled in one place so the screen never gets stuck.
     * CALLED BY: The Sign In button and the Enter key on either password control.
     */
    private void attemptLogin() {
        // Step 1: clear the old message and read the trimmed values.
        errorLabel.setText(" ");
        String name = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordText.get() == null ? "" : passwordText.get();

        // Step 2: empty fields are rejected before touching the database.
        if (name.isEmpty()) {
            errorLabel.setText("Please enter your username.");
            usernameField.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            errorLabel.setText("Please enter your password.");
            passwordHidden.requestFocus();
            return;
        }

        // Step 3: one database call returns a status instead of throwing for a
        // wrong password, so each case can show the right message.
        try {
            UserDao.LoginResult result = userDao.login(name, password);
            switch (result.status()) {
                case SUCCESS -> {
                    // Stop any countdown from an earlier lockout before leaving the screen.
                    stopLockTimer();
                    onSuccess.accept(result.user());
                }
                case INVALID_USERNAME -> {
                    // The stored username rule rejected the value; reuse the same message as the live hint.
                    errorLabel.setText(Validation.usernameProblem(name));
                    usernameField.requestFocus();
                }
                case INACTIVE -> {
                    errorLabel.setText("This account has been disabled. Please ask the administrator.");
                }
                case LOCKED -> {
                    // The account is already inside its lock window: clear the password
                    // and show the remaining time.
                    passwordText.set("");
                    startLockCountdown(result.lockedUntil());
                }
                case INVALID_CREDENTIALS -> {
                    // Wrong password: clear it so the next attempt starts fresh and
                    // tell the user how many attempts are left.
                    passwordText.set("");
                    passwordHidden.requestFocus();
                    int left = result.attemptsLeft();
                    errorLabel.setText("Invalid username or password. "
                            + left + (left == 1 ? " attempt" : " attempts")
                            + " left before the account is locked for "
                            + UserDao.LOCK_MINUTES + " minutes.");
                }
            }
        } catch (DaoException ex) {
            // A database problem is reported verbatim; the screen stays usable.
            errorLabel.setText(ex.getMessage());
        }
    }

    /**
     * WHAT: Disables Sign In and counts down until the lockout expires.
     * WHY: Without the countdown the button would stay disabled forever, because
     * nothing else would re-enable it.
     * CALLED BY: attemptLogin(), for the LOCKED result.
     */
    private void startLockCountdown(LocalDateTime until) {
        stopLockTimer();
        signInButton.setDisable(true);
        signInButton.setText("Locked");

        // Step 1: a Timeline fires once per second until it is stopped.
        lockTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            java.time.Duration remaining = java.time.Duration.between(LocalDateTime.now(), until);
            if (remaining.isNegative() || remaining.isZero()) {
                // Step 2: the lock has expired, so restore the button and say so.
                stopLockTimer();
                signInButton.setDisable(false);
                signInButton.setText("Sign In");
                errorLabel.setText("You can try signing in again now.");
            } else {
                // Step 3: show minutes:seconds, e.g. 4:59.
                long seconds = remaining.getSeconds();
                errorLabel.setText("Too many failed attempts. Account locked \u2014 try again in "
                        + String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60) + ".");
            }
        }));
        // INDEFINITE means "keep ticking until stopLockTimer() is called".
        lockTimer.setCycleCount(Animation.INDEFINITE);
        lockTimer.play();
        // Show a message immediately; otherwise the label would stay blank for one second.
        errorLabel.setText("Too many failed attempts. Account locked for "
                + UserDao.LOCK_MINUTES + " minutes.");
    }

    /**
     * WHAT: Stops and forgets the countdown.
     * WHY: Leaving a Timeline running would keep updating a screen that has moved
     * on, and would leak the timer.
     * CALLED BY: attemptLogin() on success, startLockCountdown() before restarting,
     * and the tick handler when the lock expires.
     */
    private void stopLockTimer() {
        if (lockTimer != null) {
            lockTimer.stop();
            lockTimer = null;
        }
    }

    // ------------------------------------------------------------------
    // Account creation (students only)
    // ------------------------------------------------------------------
    // ============================================================
    // PART: Creating a student account
    // ============================================================

    /**
     * WHAT: Opens a modal dialog that creates a STUDENT account, then pre-fills
     * the sign-in username.
     * WHY: Students have no administrator to create their account, so this is
     * the only self-service path into the system.
     * CALLED BY: The "Create an account" hyperlink in buildFormPanel().
     */
    private void showRegisterDialog() {
        // Step 1: a Dialog with an owner stays on top of the login window and is
        // centred relative to it.
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Student Account");
        dialog.setHeaderText("Create a student library account");
        dialog.initOwner(owner);
        Dialogs.style(dialog.getDialogPane());

        // Step 2: "Create Account" uses OK_DONE so the dialog's result converter
        // treats it as the confirming button.
        ButtonType createType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        // Step 3: the four input fields.
        TextField username = new TextField();
        username.setPromptText("Letters only");
        username.getStyleClass().add("field");
        username.setPrefHeight(38);

        TextField displayName = new TextField();
        displayName.setPromptText("Full name (optional)");
        displayName.getStyleClass().add("field");
        displayName.setPrefHeight(38);

        PasswordField password = new PasswordField();
        password.setPromptText("At least 8 characters");
        password.getStyleClass().add("field");
        password.setPrefHeight(38);

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Re-enter password");
        confirm.getStyleClass().add("field");
        confirm.setPrefHeight(38);

        // Step 4: one hint explains the rules up front; the error line is shared
        // by the live check and by the final Save validation.
        Label hint = new Label("Usernames use letters only (3\u201320). Passwords need at least "
                + Validation.PASSWORD_MIN + " characters with one letter and one number.");
        hint.setFont(Font.font("SansSerif", 10));
        hint.setTextFill(Color.web("#667085"));
        hint.setWrapText(true);

        Label error = new Label(" ");
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMinHeight(26);

        // Step 5: the same live check as the sign-in screen, so a bad username
        // is caught while it is typed.
        username.textProperty().addListener((o, old, value) -> {
            String problem = value == null || value.isBlank() ? null : Validation.usernameProblem(value);
            error.setText(problem == null ? " " : problem);
        });

        // Step 6: addRow pairs a label with its control; the Hgrow calls let the
        // fields stretch to the width of the dialog.
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12);
        grid.setVgap(9);
        grid.setPadding(new Insets(6, 4, 4, 4));
        grid.addRow(0, new Label("Username"), username);
        grid.addRow(1, new Label("Full name"), displayName);
        grid.addRow(2, new Label("Password"), password);
        grid.addRow(3, new Label("Confirm"), confirm);
        grid.add(hint, 1, 4);
        grid.add(error, 1, 5);
        javafx.scene.layout.GridPane.setHgrow(username, Priority.ALWAYS);
        javafx.scene.layout.GridPane.setHgrow(displayName, Priority.ALWAYS);
        javafx.scene.layout.GridPane.setHgrow(password, Priority.ALWAYS);
        javafx.scene.layout.GridPane.setHgrow(confirm, Priority.ALWAYS);
        dialog.getDialogPane().setContent(grid);

        // Step 7: the dialog closes as soon as Create Account is pressed, so an
        // event filter validates first and consumes the event when something is wrong.
        Node createButton = dialog.getDialogPane().lookupButton(createType);
        if (createButton != null) {
            createButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                String name = username.getText() == null ? "" : username.getText().trim();
                String p1 = password.getText() == null ? "" : password.getText();
                String p2 = confirm.getText() == null ? "" : confirm.getText();

                // Check in field order so the first problem is the one reported.
                String problem = Validation.usernameProblem(name);
                if (problem == null) {
                    problem = Validation.passwordProblem(p1);
                }
                if (problem == null && !p1.equals(p2)) {
                    problem = "The two passwords do not match.";
                }
                if (problem != null) {
                    error.setText(problem);
                    // consume() stops the dialog from closing, so the user can fix it.
                    event.consume();
                    return;
                }
                try {
                    userDao.createStudent(name, displayName.getText(), p1);
                    // Pre-fill the sign-in form so the student only has to type the password.
                    usernameField.setText(name.toLowerCase(Locale.ROOT));
                    passwordText.set("");
                } catch (DaoException ex) {
                    // Most often the username already exists; keep the dialog open.
                    error.setText(ex.getMessage());
                    event.consume();
                }
            });
        }

        // Step 8: show the confirmation only when Create was chosen, not on Cancel.
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == createType) {
            Dialogs.info(owner, "Account created",
                    "Your student account is ready. Enter your password to sign in.");
            // The username is already filled in, so the next step is the password.
            passwordHidden.requestFocus();
        }
    }
}
