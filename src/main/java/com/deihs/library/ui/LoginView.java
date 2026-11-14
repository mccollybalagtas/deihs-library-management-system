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

/** The sign-in screen. Calls {@code onSuccess} once the credentials check out. */
public class LoginView extends StackPane {

    private final Stage owner;
    private final Consumer<User> onSuccess;
    private final UserDao userDao = new UserDao();

    private final TextField usernameField = new TextField();
    private final PasswordField passwordHidden = new PasswordField();
    private final TextField passwordShown = new TextField();
    private final SimpleStringProperty passwordText = new SimpleStringProperty("");
    private final Label errorLabel = new Label(" ");
    private final Label usernameHint = new Label(" ");
    private final Button signInButton = new Button("Sign In");

    private Timeline lockTimer;

    public LoginView(Stage owner, Consumer<User> onSuccess) {
        this.owner = owner;
        this.onSuccess = onSuccess;
        Fonts.load();

        getStyleClass().add("login-bg");
        setAlignment(Pos.CENTER);

        HBox card = new HBox(buildBrandPanel(), buildFormPanel());
        card.getStyleClass().add("login-card");
        card.setMaxSize(940, 566);
        card.setPrefSize(940, 566);
        getChildren().add(card);
        setPadding(new Insets(26));

        Platform.runLater(usernameField::requestFocus);
    }

    // ------------------------------------------------------------------
    // Left brand panel
    // ------------------------------------------------------------------

    private VBox buildBrandPanel() {
        VBox brand = new VBox(0);
        brand.getStyleClass().add("login-brand");
        brand.setPrefWidth(382);
        brand.setMinWidth(382);
        brand.setMaxWidth(382);
        brand.setPadding(new Insets(92, 38, 40, 38));
        brand.setAlignment(Pos.TOP_CENTER);

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

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label quote = new Label("\u201CRead. Learn. Grow.\u201D");
        quote.setFont(Font.font("Serif", FontPosture.ITALIC, 21));
        quote.setTextFill(Color.web("#c5f0fd"));
        quote.setWrapText(true);

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

    private Node buildLogo(double size) {
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

    private VBox buildFormPanel() {
        VBox form = new VBox(0);
        form.getStyleClass().add("login-form");
        form.setPadding(new Insets(44, 56, 34, 56));
        HBox.setHgrow(form, Priority.ALWAYS);

        Label title = new Label("Welcome back");
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 28));
        title.setTextFill(Color.web("#0D264C"));

        Label caption = new Label("Sign in to borrow, return and manage library books.");
        caption.setFont(Font.font("SansSerif", 12));
        caption.setTextFill(Color.web("#667085"));

        VBox titleBox = new VBox(6, title, caption);
        titleBox.setPadding(new Insets(0, 0, 20, 0));

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

        passwordHidden.textProperty().bindBidirectional(passwordText);
        passwordShown.textProperty().bindBidirectional(passwordText);

        StackPane passwordStack = new StackPane(passwordHidden, passwordShown);
        passwordStack.setAlignment(Pos.CENTER_LEFT);
        passwordStack.setPrefHeight(44);
        passwordStack.setMaxWidth(Double.MAX_VALUE);

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

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        errorLabel.setMinHeight(30);

        signInButton.getStyleClass().add("primary-btn");
        signInButton.setMaxWidth(Double.MAX_VALUE);
        signInButton.setPrefHeight(46);
        signInButton.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));
        signInButton.setDefaultButton(true);
        signInButton.setOnAction(e -> attemptLogin());

        usernameField.setOnAction(e -> passwordHidden.requestFocus());
        passwordHidden.setOnAction(e -> attemptLogin());
        passwordShown.setOnAction(e -> attemptLogin());

        Label newHere = new Label("New student?");
        newHere.setFont(Font.font("SansSerif", 12));
        newHere.setTextFill(Color.web("#667085"));

        Hyperlink register = new Hyperlink("Create an account");
        register.getStyleClass().add("link");
        register.setOnAction(e -> showRegisterDialog());

        HBox registerRow = new HBox(6, newHere, register);
        registerRow.setAlignment(Pos.CENTER_LEFT);

        VBox fields = new VBox(6,
                userLabel, usernameField, usernameHint,
                passLabel, passwordStack,
                showPassword, errorLabel, signInButton, registerRow);
        VBox.setMargin(passLabel, new Insets(8, 0, 0, 0));
        VBox.setMargin(showPassword, new Insets(2, 0, 4, 0));
        VBox.setMargin(errorLabel, new Insets(0, 0, 2, 0));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

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

    /** Live feedback while typing, so a number is flagged before Sign In. */
    private void checkUsername(String value) {
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

    private void attemptLogin() {
        errorLabel.setText(" ");
        String name = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordText.get() == null ? "" : passwordText.get();

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

        try {
            UserDao.LoginResult result = userDao.login(name, password);
            switch (result.status()) {
                case SUCCESS -> {
                    stopLockTimer();
                    onSuccess.accept(result.user());
                }
                case INVALID_USERNAME -> {
                    errorLabel.setText(Validation.usernameProblem(name));
                    usernameField.requestFocus();
                }
                case INACTIVE -> {
                    errorLabel.setText("This account has been disabled. Please ask the administrator.");
                }
                case LOCKED -> {
                    passwordText.set("");
                    startLockCountdown(result.lockedUntil());
                }
                case INVALID_CREDENTIALS -> {
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
            errorLabel.setText(ex.getMessage());
        }
    }

    private void startLockCountdown(LocalDateTime until) {
        stopLockTimer();
        signInButton.setDisable(true);
        signInButton.setText("Locked");

        lockTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            java.time.Duration remaining = java.time.Duration.between(LocalDateTime.now(), until);
            if (remaining.isNegative() || remaining.isZero()) {
                stopLockTimer();
                signInButton.setDisable(false);
                signInButton.setText("Sign In");
                errorLabel.setText("You can try signing in again now.");
            } else {
                long seconds = remaining.getSeconds();
                errorLabel.setText("Too many failed attempts. Account locked \u2014 try again in "
                        + String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60) + ".");
            }
        }));
        lockTimer.setCycleCount(Animation.INDEFINITE);
        lockTimer.play();
        errorLabel.setText("Too many failed attempts. Account locked for "
                + UserDao.LOCK_MINUTES + " minutes.");
    }

    private void stopLockTimer() {
        if (lockTimer != null) {
            lockTimer.stop();
            lockTimer = null;
        }
    }

    // ------------------------------------------------------------------
    // Account creation (students only)
    // ------------------------------------------------------------------

    private void showRegisterDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Student Account");
        dialog.setHeaderText("Create a student library account");
        dialog.initOwner(owner);
        Dialogs.style(dialog.getDialogPane());

        ButtonType createType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

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

        Label hint = new Label("Usernames use letters only (3\u201320). Passwords need at least "
                + Validation.PASSWORD_MIN + " characters with one letter and one number.");
        hint.setFont(Font.font("SansSerif", 10));
        hint.setTextFill(Color.web("#667085"));
        hint.setWrapText(true);

        Label error = new Label(" ");
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMinHeight(26);

        username.textProperty().addListener((o, old, value) -> {
            String problem = value == null || value.isBlank() ? null : Validation.usernameProblem(value);
            error.setText(problem == null ? " " : problem);
        });

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

        Node createButton = dialog.getDialogPane().lookupButton(createType);
        if (createButton != null) {
            createButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                String name = username.getText() == null ? "" : username.getText().trim();
                String p1 = password.getText() == null ? "" : password.getText();
                String p2 = confirm.getText() == null ? "" : confirm.getText();

                String problem = Validation.usernameProblem(name);
                if (problem == null) {
                    problem = Validation.passwordProblem(p1);
                }
                if (problem == null && !p1.equals(p2)) {
                    problem = "The two passwords do not match.";
                }
                if (problem != null) {
                    error.setText(problem);
                    event.consume();
                    return;
                }
                try {
                    userDao.createStudent(name, displayName.getText(), p1);
                    usernameField.setText(name.toLowerCase(Locale.ROOT));
                    passwordText.set("");
                } catch (DaoException ex) {
                    error.setText(ex.getMessage());
                    event.consume();
                }
            });
        }

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == createType) {
            Dialogs.info(owner, "Account created",
                    "Your student account is ready. Enter your password to sign in.");
            passwordHidden.requestFocus();
        }
    }
}
