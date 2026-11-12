package com.deihs.library.ui;

import com.deihs.library.dao.DaoException;
import com.deihs.library.dao.UserDao;
import com.deihs.library.model.User;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
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

import java.io.InputStream;
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
        quote.setTextFill(Color.web("#DCEBFF"));
        quote.setWrapText(true);

        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setMaxWidth(64);
        divider.setStyle("-fx-background-color: #2E5C8F;");

        Region gap = new Region();
        gap.setPrefHeight(18);

        Region gap2 = new Region();
        gap2.setPrefHeight(18);

        Label school = new Label("BALAGTAS DEIHS\nSCHOOL LIBRARY");
        school.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
        school.setTextFill(Color.web("#9FB6D4"));

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
        form.setPadding(new Insets(48, 56, 38, 56));
        HBox.setHgrow(form, Priority.ALWAYS);

        Label title = new Label("Welcome back");
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 28));
        title.setTextFill(Color.web("#0D264C"));

        Label caption = new Label("Sign in to manage the library catalog.");
        caption.setFont(Font.font("SansSerif", 12));
        caption.setTextFill(Color.web("#667085"));

        VBox titleBox = new VBox(6, title, caption);
        titleBox.setPadding(new Insets(0, 0, 24, 0));

        Label userLabel = new Label("USERNAME");
        userLabel.getStyleClass().add("field-label");

        usernameField.setPromptText("Enter your username");
        usernameField.getStyleClass().add("field");
        usernameField.setPrefHeight(44);
        usernameField.setMaxWidth(Double.MAX_VALUE);

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

        Button signIn = new Button("Sign In");
        signIn.getStyleClass().add("primary-btn");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setPrefHeight(46);
        signIn.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));
        signIn.setDefaultButton(true);
        signIn.setOnAction(e -> attemptLogin());

        usernameField.setOnAction(e -> passwordHidden.requestFocus());
        passwordHidden.setOnAction(e -> attemptLogin());
        passwordShown.setOnAction(e -> attemptLogin());

        Label newHere = new Label("New to the system?");
        newHere.setFont(Font.font("SansSerif", 12));
        newHere.setTextFill(Color.web("#667085"));

        Hyperlink register = new Hyperlink("Create an account");
        register.getStyleClass().add("link");
        register.setOnAction(e -> showRegisterDialog());

        HBox registerRow = new HBox(6, newHere, register);
        registerRow.setAlignment(Pos.CENTER_LEFT);

        VBox fields = new VBox(7,
                userLabel, usernameField,
                passLabel, passwordStack,
                showPassword, errorLabel, signIn, registerRow);
        VBox.setMargin(passLabel, new Insets(12, 0, 0, 0));
        VBox.setMargin(showPassword, new Insets(2, 0, 6, 0));
        VBox.setMargin(errorLabel, new Insets(0, 0, 2, 0));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("\u00A9 " + java.time.Year.now() + " Balagtas DEIHS School Library");
        footer.setFont(Font.font("SansSerif", 10));
        footer.setTextFill(Color.web("#98A2B3"));

        VBox footBox = new VBox(4, footer);
        footBox.setPadding(new Insets(14, 0, 0, 0));

        form.getChildren().addAll(titleBox, fields, spacer, footBox);
        return form;
    }

    // ------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------

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
            User user = userDao.authenticate(name, password);
            if (user == null) {
                errorLabel.setText("Invalid username or password. Please try again.");
                passwordText.set("");
                passwordHidden.requestFocus();
                return;
            }
            onSuccess.accept(user);
        } catch (DaoException ex) {
            errorLabel.setText(ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Account creation
    // ------------------------------------------------------------------

    private void showRegisterDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Account");
        dialog.setHeaderText("Create a new library account");
        dialog.initOwner(owner);
        Dialogs.style(dialog.getDialogPane());

        ButtonType createType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        TextField username = new TextField();
        username.setPromptText("Username");
        username.getStyleClass().add("field");
        username.setPrefHeight(38);

        TextField displayName = new TextField();
        displayName.setPromptText("Display name (optional)");
        displayName.getStyleClass().add("field");
        displayName.setPrefHeight(38);

        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.getStyleClass().add("field");
        password.setPrefHeight(38);

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Re-enter password");
        confirm.getStyleClass().add("field");
        confirm.setPrefHeight(38);

        Label hint = new Label("Usernames are not case-sensitive. Minimum 3 characters for the username, 4 for the password.");
        hint.setFont(Font.font("SansSerif", 10));
        hint.setTextFill(Color.web("#667085"));
        hint.setWrapText(true);

        Label error = new Label(" ");
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMinHeight(26);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12);
        grid.setVgap(9);
        grid.setPadding(new Insets(6, 4, 4, 4));
        grid.addRow(0, new Label("Username"), username);
        grid.addRow(1, new Label("Display name"), displayName);
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

                if (name.length() < 3) {
                    error.setText("Username must be at least 3 characters.");
                    event.consume();
                } else if (p1.length() < 4) {
                    error.setText("Password must be at least 4 characters.");
                    event.consume();
                } else if (!p1.equals(p2)) {
                    error.setText("Passwords do not match.");
                    event.consume();
                } else {
                    try {
                        userDao.create(name, displayName.getText(), p1);
                        usernameField.setText(name.toLowerCase(java.util.Locale.ROOT));
                        passwordText.set("");
                    } catch (DaoException ex) {
                        error.setText(ex.getMessage());
                        event.consume();
                    }
                }
            });
        }

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == createType) {
            Dialogs.info(owner, "Account created",
                    "Your account is ready. Enter your password to sign in.");
            passwordHidden.requestFocus();
        }
    }
}
