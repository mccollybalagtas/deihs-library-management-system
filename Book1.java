import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;

// ============================================================
// DEIHS LIBRARY HUB (JavaFX Version)
// Complete JavaFX Library Management System
// Save as Book1.java
// ============================================================

abstract class Book implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final String title;
    private final String author;
    private final String category;
    private boolean borrowed;

    public Book(int id, String title, String author, String category, boolean borrowed) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.category = category;
        this.borrowed = borrowed;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getCategory() { return category; }
    public boolean isBorrowed() { return borrowed; }

    public void setBorrowed(boolean borrowed) {
        this.borrowed = borrowed;
    }

    public abstract String getType();
    public abstract String getExtraInfo();
}

class PrintedBook extends Book {
    private static final long serialVersionUID = 1L;
    private final int pageCount;

    public PrintedBook(int id, String title, String author, String category, boolean borrowed, int pageCount) {
        super(id, title, author, category, borrowed);
        this.pageCount = pageCount;
    }

    public int getPageCount() { return pageCount; }

    @Override
    public String getType() { return "Printed"; }

    @Override
    public String getExtraInfo() {
        return pageCount + " pages";
    }
}

class Ebook extends Book {
    private static final long serialVersionUID = 1L;
    private final double fileSizeMB;

    public Ebook(int id, String title, String author, String category, boolean borrowed, double fileSizeMB) {
        super(id, title, author, category, borrowed);
        this.fileSizeMB = fileSizeMB;
    }

    public double getFileSizeMB() { return fileSizeMB; }

    @Override
    public String getType() { return "E-Book"; }

    @Override
    public String getExtraInfo() {
        return String.format(Locale.US, "%.2f MB", fileSizeMB);
    }
}

public class Book1 extends Application {

    private static final String DATA_FILE = "library_data.ser";

    private final ObservableList<Book> catalog = FXCollections.observableArrayList();
    private FilteredList<Book> filteredCatalog;

    private BorderPane root;
    private BorderPane mainContent;
    private Label pageTitle;
    private Label statusLabel;

    private Label totalValue;
    private Label availableValue;
    private Label borrowedValue;
    private Label printedValue;
    private Label ebookValue;

    private TableView<Book> bookTable;
    private TextField searchField;
    private ComboBox<String> categoryFilter;

    private Stage primaryStage;

    // ---- Login / user accounts ----
    private static final String USERS_FILE = "library_users.dat";
    private final Map<String, String> users = new LinkedHashMap<>();
    private String currentUser;
    private Scene mainScene;
    private Label userBadge;
    private Label userNameLabel;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("DEIHS Library Hub");

        loadUsers();
        loadCatalog();
        filteredCatalog = new FilteredList<>(catalog, p -> true);

        buildMainScene();
        showLogin();

        stage.setOnCloseRequest(e -> {
            e.consume();
            exitApplication();
        });

        stage.show();
    }

    // =========================================================
    // SCENE SWITCHING (LOGIN <-> APPLICATION)
    // =========================================================

    private void buildMainScene() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #F3F7FD;");

        root.setLeft(createSidebar());

        VBox centerLayout = new VBox();
        centerLayout.getChildren().addAll(createTopBar(), createMainArea(), createFooter());
        VBox.setVgrow(centerLayout.getChildren().get(1), Priority.ALWAYS);

        root.setCenter(centerLayout);

        showDashboard();

        mainScene = new Scene(root, 1380, 820);
    }

    private void showLogin() {
        currentUser = null;
        primaryStage.setTitle("DEIHS Library Hub — Sign In");
        primaryStage.setScene(createLoginScene());
        primaryStage.setMinWidth(860);
        primaryStage.setMinHeight(600);
        primaryStage.setWidth(1000);
        primaryStage.setHeight(660);
        primaryStage.centerOnScreen();
    }

    private void enterApplication() {
        primaryStage.setTitle("DEIHS Library Hub");
        primaryStage.setMinWidth(1050);
        primaryStage.setMinHeight(680);
        primaryStage.setScene(mainScene);
        primaryStage.setWidth(1380);
        primaryStage.setHeight(820);
        primaryStage.centerOnScreen();
        updateUserBadge();
        setStatus("Signed in as " + currentUser);
    }

    private void updateUserBadge() {
        if (userBadge != null) {
            userBadge.setText(currentUser == null || currentUser.isEmpty()
                    ? " ? "
                    : " " + Character.toUpperCase(currentUser.charAt(0)) + " ");
        }
        if (userNameLabel != null) {
            userNameLabel.setText(currentUser == null ? "" : currentUser);
        }
    }

    private void logout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Log out of DEIHS Library Hub?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Log Out");
        alert.setHeaderText(null);
        alert.initOwner(primaryStage);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                showLogin();
            }
        });
    }

    // =========================================================
    // LOGIN SCREEN
    // =========================================================

    private Scene createLoginScene() {
        StackPane backdrop = new StackPane();
        backdrop.setStyle("-fx-background-color: linear-gradient(to bottom right, #07162C, #0D264C 48%, #16406F);");

        // ---------- Left brand panel ----------
        VBox brand = new VBox(0);
        brand.setPrefWidth(360);
        brand.setMinWidth(360);
        brand.setPadding(new Insets(48, 38, 42, 38));
        brand.setStyle("-fx-background-color: linear-gradient(to bottom, #0D264C, #123A6B); -fx-background-radius: 18 0 0 18;");

        Label mark = new Label("✦");
        mark.setFont(Font.font("SansSerif", FontWeight.BOLD, 44));
        mark.setTextFill(Color.web("#64DCEB"));

        Label brandName = new Label("DEIHS");
        brandName.setFont(Font.font("SansSerif", FontWeight.BOLD, 40));
        brandName.setTextFill(Color.WHITE);

        Label brandSub = new Label("L I B R A R Y   H U B");
        brandSub.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
        brandSub.setTextFill(Color.web("#64DCEB"));

        Region brandSpacer = new Region();
        VBox.setVgrow(brandSpacer, Priority.ALWAYS);

        Label quote = new Label("\u201CRead. Learn. Grow.\u201D");
        quote.setFont(Font.font("Serif", javafx.scene.text.FontPosture.ITALIC, 20));
        quote.setTextFill(Color.web("#DCEBFF"));
        quote.setWrapText(true);

        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setMaxWidth(60);
        divider.setStyle("-fx-background-color: #2E5C8F;");

        Region brandGap = new Region();
        brandGap.setPrefHeight(18);

        Label school = new Label("BALAGTAS DEIHS\nSCHOOL LIBRARY");
        school.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
        school.setTextFill(Color.web("#9FB6D4"));

        brand.getChildren().addAll(mark, brandName, brandSub, brandSpacer, quote, brandGap, divider, brandGap, school);

        // ---------- Right form panel ----------
        VBox form = new VBox(0);
        form.setPrefWidth(540);
        form.setPadding(new Insets(52, 58, 46, 58));
        form.setStyle("-fx-background-color: white; -fx-background-radius: 0 18 18 0;");

        Label title = new Label("Welcome back");
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 27));
        title.setTextFill(Color.web("#0D264C"));

        Label caption = new Label("Sign in to manage the library catalog.");
        caption.setFont(Font.font("SansSerif", 12));
        caption.setTextFill(Color.web("#667085"));

        VBox titleBox = new VBox(6, title, caption);
        titleBox.setPadding(new Insets(0, 0, 26, 0));

        Label userLabel = new Label("USERNAME");
        userLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
        userLabel.setTextFill(Color.web("#475467"));

        TextField userField = new TextField();
        userField.setPromptText("Enter your username");
        userField.setPrefHeight(44);
        userField.setStyle(FIELD_STYLE);

        Label passLabel = new Label("PASSWORD");
        passLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
        passLabel.setTextFill(Color.web("#475467"));

        PasswordField passHidden = new PasswordField();
        passHidden.setPromptText("Enter your password");
        passHidden.setPrefHeight(44);
        passHidden.setStyle(FIELD_STYLE);

        TextField passShown = new TextField();
        passShown.setPromptText("Enter your password");
        passShown.setPrefHeight(44);
        passShown.setStyle(FIELD_STYLE);
        passShown.setVisible(false);
        passShown.setManaged(false);

        SimpleStringProperty passwordText = new SimpleStringProperty("");
        passHidden.textProperty().bindBidirectional(passwordText);
        passShown.textProperty().bindBidirectional(passwordText);

        StackPane passStack = new StackPane(passHidden, passShown);
        passStack.setAlignment(Pos.CENTER_LEFT);
        passStack.setPrefHeight(44);
        passStack.setMaxWidth(Double.MAX_VALUE);

        CheckBox showPass = new CheckBox("Show password");
        showPass.setFont(Font.font("SansSerif", 11));
        showPass.setTextFill(Color.web("#475467"));
        showPass.setOnAction(e -> {
            boolean show = showPass.isSelected();
            passHidden.setVisible(!show);
            passHidden.setManaged(!show);
            passShown.setVisible(show);
            passShown.setManaged(show);
            (show ? passShown : passHidden).requestFocus();
        });

        Label error = new Label(" ");
        error.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        error.setTextFill(Color.web("#C0392B"));
        error.setWrapText(true);
        error.setMinHeight(30);

        Button signIn = new Button("Sign In");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setPrefHeight(46);
        signIn.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));
        signIn.setStyle(PRIMARY_BUTTON_STYLE);
        signIn.setOnMouseEntered(e -> signIn.setStyle(PRIMARY_BUTTON_HOVER_STYLE));
        signIn.setOnMouseExited(e -> signIn.setStyle(PRIMARY_BUTTON_STYLE));
        signIn.setOnAction(e -> attemptLogin(userField.getText(), passwordText.get(), error));

        passHidden.setOnAction(e -> attemptLogin(userField.getText(), passwordText.get(), error));
        passShown.setOnAction(e -> attemptLogin(userField.getText(), passwordText.get(), error));
        userField.setOnAction(e -> passHidden.requestFocus());

        Hyperlink register = new Hyperlink("Create an account");
        register.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        register.setTextFill(Color.web("#17457F"));
        register.setOnAction(e -> showRegisterDialog(userField));

        Label newHere = new Label("New to the system?");
        newHere.setFont(Font.font("SansSerif", 12));
        newHere.setTextFill(Color.web("#667085"));

        HBox registerRow = new HBox(6, newHere, register);
        registerRow.setAlignment(Pos.CENTER_LEFT);

        Region formSpacer = new Region();
        VBox.setVgrow(formSpacer, Priority.ALWAYS);

        Label foot = new Label("© " + java.time.Year.now() + " Balagtas DEIHS School Library");
        foot.setFont(Font.font("SansSerif", 10));
        foot.setTextFill(Color.web("#98A2B3"));

        VBox fields = new VBox(7, userLabel, userField, passLabel, passStack, showPass, error, signIn, registerRow);
        VBox.setMargin(passLabel, new Insets(10, 0, 0, 0));
        VBox.setMargin(showPass, new Insets(2, 0, 8, 0));
        VBox.setMargin(error, new Insets(0, 0, 2, 0));

        form.getChildren().addAll(titleBox, fields, formSpacer, foot);

        HBox card = new HBox(brand, form);
        card.setMaxWidth(900);
        card.setMaxHeight(540);
        card.setPrefSize(900, 540);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 18; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 46, 0.18, 0, 16);");

        backdrop.getChildren().add(card);
        StackPane.setMargin(card, new Insets(30));

        return new Scene(backdrop, 1000, 660);
    }

    private static final String FIELD_STYLE =
            "-fx-background-color: #F8FAFF; -fx-border-color: #C9D6E6; -fx-border-radius: 8; "
            + "-fx-background-radius: 8; -fx-padding: 0 14 0 14; -fx-font-size: 13px;";

    private static final String PRIMARY_BUTTON_STYLE =
            "-fx-background-color: #0D264C; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;";

    private static final String PRIMARY_BUTTON_HOVER_STYLE =
            "-fx-background-color: #17457F; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;";

    private void attemptLogin(String username, String password, Label error) {
        String name = username == null ? "" : username.trim().toLowerCase();
        if (name.isEmpty()) {
            error.setText("Please enter your username.");
            return;
        }
        if (password == null || password.isEmpty()) {
            error.setText("Please enter your password.");
            return;
        }
        String stored = users.get(name);
        if (stored == null || !stored.equals(hashPassword(name, password))) {
            error.setText("Invalid username or password. Please try again.");
            return;
        }
        currentUser = name;
        enterApplication();
    }

    private void showRegisterDialog(TextField loginUserField) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Account");
        dialog.setHeaderText("Create a new library account");
        dialog.initOwner(primaryStage);

        ButtonType createType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        TextField newUser = new TextField();
        newUser.setPromptText("Username");
        PasswordField newPass = new PasswordField();
        newPass.setPromptText("Password");
        PasswordField confirmPass = new PasswordField();
        confirmPass.setPromptText("Re-enter password");

        Label hint = new Label("Usernames are not case-sensitive. Username minimum 3 characters, password minimum 4.");
        hint.setFont(Font.font("SansSerif", 10));
        hint.setTextFill(Color.web("#667085"));
        hint.setWrapText(true);

        Label error = new Label(" ");
        error.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        error.setTextFill(Color.web("#C0392B"));
        error.setWrapText(true);
        error.setMinHeight(28);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        grid.addRow(0, new Label("Username"), newUser);
        grid.addRow(1, new Label("Password"), newPass);
        grid.addRow(2, new Label("Confirm"), confirmPass);
        grid.add(hint, 1, 3);
        grid.add(error, 1, 4);
        GridPane.setHgrow(newUser, Priority.ALWAYS);
        GridPane.setHgrow(newPass, Priority.ALWAYS);
        GridPane.setHgrow(confirmPass, Priority.ALWAYS);
        dialog.getDialogPane().setContent(grid);

        Node createButton = dialog.getDialogPane().lookupButton(createType);
        if (createButton != null) {
            createButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                String name = newUser.getText() == null ? "" : newUser.getText().trim();
                String p1 = newPass.getText();
                String p2 = confirmPass.getText();
                if (name.length() < 3) {
                    error.setText("Username must be at least 3 characters.");
                    event.consume();
                } else if (users.containsKey(name.toLowerCase())) {
                    error.setText("That username is already taken.");
                    event.consume();
                } else if (p1 == null || p1.length() < 4) {
                    error.setText("Password must be at least 4 characters.");
                    event.consume();
                } else if (!p1.equals(p2)) {
                    error.setText("Passwords do not match.");
                    event.consume();
                } else {
                    users.put(name.toLowerCase(), hashPassword(name.toLowerCase(), p1));
                    saveUsers();
                    loginUserField.setText(name.toLowerCase());
                }
            });
        }

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == createType) {
            showInfo("Account created. You can now sign in with your new username.");
        }
    }

    // =========================================================
    // USER ACCOUNT STORAGE (SHA-256 HASHED)
    // =========================================================

    private void loadUsers() {
        users.clear();
        File file = new File(USERS_FILE);
        if (file.exists()) {
            Properties props = new Properties();
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException ex) {
                showError("Unable to load user accounts:\n" + ex.getMessage());
            }
            for (String key : props.stringPropertyNames()) {
                users.put(key.toLowerCase(), props.getProperty(key));
            }
        }
        // Seed the built-in administrator account on first run.
        if (!users.containsKey("admin")) {
            users.put("admin", hashPassword("admin", "admin123"));
            saveUsers();
        }
    }

    private void saveUsers() {
        Properties props = new Properties();
        for (Map.Entry<String, String> entry : users.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        try (OutputStream out = new FileOutputStream(USERS_FILE)) {
            props.store(out, "DEIHS Library Hub - user accounts (SHA-256 hashed passwords)");
        } catch (IOException ex) {
            showError("Unable to save user accounts:\n" + ex.getMessage());
        }
    }

    private static String hashPassword(String username, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    // =========================================================
    // MAIN LAYOUT SETUP
    // =========================================================

    private VBox createMainArea() {
        mainContent = new BorderPane();
        mainContent.setStyle("-fx-background-color: #F3F7FD; -fx-padding: 12 18 8 18;");
        VBox container = new VBox(mainContent);
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        return container;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(220);
        sidebar.setStyle("-fx-background-color: #0D264C; -fx-padding: 0;");

        // Header / Brand
        VBox brand = new VBox(2);
        brand.setPadding(new Insets(20, 16, 18, 16));

        Label logo = new Label("✦  DEIHS");
        logo.setFont(Font.font("SansSerif", FontWeight.BOLD, 23));
        logo.setTextFill(Color.WHITE);

        Label subtitle = new Label("      LIBRARY HUB");
        subtitle.setFont(Font.font("SansSerif", FontWeight.BOLD, 9));
        subtitle.setTextFill(Color.web("#64DCEB"));

        brand.getChildren().addAll(logo, subtitle);

        // Menu Buttons
        VBox menu = new VBox(7);
        menu.setPadding(new Insets(8, 12, 8, 12));

        addSidebarButton(menu, "⌂", "Dashboard", this::showDashboard);
        addSidebarButton(menu, "+", "Add a Book", this::showAddBookPage);
        addSidebarButton(menu, "↗", "Borrow Book", this::showBorrowPage);
        addSidebarButton(menu, "↶", "Return Book", this::showReturnPage);
        addSidebarButton(menu, "✕", "Remove Book", this::showRemoveBookPage);
        addSidebarButton(menu, "⇧", "Upload Book File", this::uploadBookFile);
        addSidebarButton(menu, "▤", "View Full Catalog", this::showCatalogPage);
        addSidebarButton(menu, "▣", "Save Catalog", this::saveCatalog);

        VBox.setVgrow(menu, Priority.ALWAYS);

        // Bottom Text
        VBox bottom = new VBox(8);
        bottom.setPadding(new Insets(10, 16, 20, 16));

        Button logoutBtn = new Button("↩   Log out");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setPrefHeight(36);
        logoutBtn.setAlignment(Pos.CENTER_LEFT);
        logoutBtn.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        String logoutBase = "-fx-background-color: transparent; -fx-text-fill: #F2C7C7; "
                + "-fx-border-color: #3B5A82; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;";
        String logoutHover = "-fx-background-color: #1B3E6E; -fx-text-fill: white; "
                + "-fx-border-color: #3B5A82; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;";
        logoutBtn.setStyle(logoutBase);
        logoutBtn.setOnMouseEntered(e -> logoutBtn.setStyle(logoutHover));
        logoutBtn.setOnMouseExited(e -> logoutBtn.setStyle(logoutBase));
        logoutBtn.setOnAction(e -> logout());

        Label quote = new Label("\"Read. Learn. Grow.\"");
        quote.setFont(Font.font("Serif", javafx.scene.text.FontPosture.ITALIC, 14));
        quote.setTextFill(Color.web("#64DCEB"));

        Label school = new Label("DEIHS SCHOOL LIBRARY");
        school.setFont(Font.font("SansSerif", FontWeight.BOLD, 9));
        school.setTextFill(Color.WHITE);

        bottom.getChildren().addAll(logoutBtn, quote, school);

        sidebar.getChildren().addAll(brand, menu, bottom);
        return sidebar;
    }

    private void addSidebarButton(VBox parent, String icon, String text, Runnable action) {
        Button btn = new Button(icon + "   " + text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(40);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        btn.setStyle("-fx-background-color: #F8FAFF; -fx-text-fill: black; -fx-border-color: #BECDE1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");

        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #D7E7FA; -fx-text-fill: black; -fx-border-color: #BECDE1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #F8FAFF; -fx-text-fill: black; -fx-border-color: #BECDE1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;"));
        btn.setOnAction(e -> action.run());

        parent.getChildren().add(btn);
    }

    private HBox createTopBar() {
        HBox top = new HBox();
        top.setStyle("-fx-background-color: white; -fx-padding: 12 20 12 20; -fx-border-color: #DCE5F0; -fx-border-width: 0 0 1 0;");
        top.setAlignment(Pos.CENTER_LEFT);

        pageTitle = new Label("Library Dashboard");
        pageTitle.setFont(Font.font("SansSerif", FontWeight.BOLD, 19));
        pageTitle.setTextFill(Color.BLACK);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label date = new Label(new SimpleDateFormat("EEE, dd MMM yyyy").format(new Date()));
        date.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        date.setTextFill(Color.BLACK);

        userNameLabel = new Label("");
        userNameLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        userNameLabel.setTextFill(Color.BLACK);

        userBadge = new Label(" M ");
        userBadge.setStyle("-fx-background-color: #DCEBFF; -fx-padding: 5; -fx-font-weight: bold; -fx-background-radius: 4;");
        userBadge.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));

        HBox right = new HBox(10, date, userNameLabel, userBadge);
        right.setAlignment(Pos.CENTER_RIGHT);

        top.getChildren().addAll(pageTitle, spacer, right);
        return top;
    }

    private HBox createFooter() {
        HBox footer = new HBox();
        footer.setStyle("-fx-background-color: white; -fx-padding: 7 18 7 18; -fx-border-color: #DCE5F0; -fx-border-width: 1 0 0 0;");
        footer.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("●  System ready");
        statusLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
        statusLabel.setTextFill(Color.web("#0F7D50"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label right = new Label("DEIHS Library Hub | JavaFX");
        right.setFont(Font.font("SansSerif", 10));
        right.setTextFill(Color.web("#55606E"));

        footer.getChildren().addAll(statusLabel, spacer, right);
        return footer;
    }

    // =========================================================
    // DASHBOARD VIEW
    // =========================================================

    private void showDashboard() {
        pageTitle.setText("Library Dashboard");
        mainContent.setCenter(null);

        VBox page = new VBox(12);
        page.setStyle("-fx-background-color: transparent;");

        VBox topSection = new VBox(12);
        topSection.getChildren().addAll(createWelcomePanel(), createStatisticsPanel());

        VBox catalogPanel = createCatalogPanel();
        VBox.setVgrow(catalogPanel, Priority.ALWAYS);

        page.getChildren().addAll(topSection, catalogPanel);
        mainContent.setCenter(page);

        refreshCatalogTable();
    }

    private HBox createWelcomePanel() {
        HBox panel = new HBox();
        panel.setStyle("-fx-background-color: #DCEBFF; -fx-border-color: #96BEF0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 12 18 12 18;");
        panel.setAlignment(Pos.CENTER_LEFT);

        VBox labels = new VBox(5);
        Label heading = new Label("Welcome to DEIHS Library!");
        heading.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
        heading.setTextFill(Color.BLACK);

        Label description = new Label("Your learning journey begins here. Explore and manage your collection.");
        description.setFont(Font.font("SansSerif", 12));
        description.setTextFill(Color.web("#2D3746"));

        labels.getChildren().addAll(heading, description);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label bookIcon = new Label("▤");
        bookIcon.setFont(Font.font("SansSerif", FontWeight.BOLD, 32));
        bookIcon.setTextFill(Color.web("#1955A0"));

        panel.getChildren().addAll(labels, spacer, bookIcon);
        return panel;
    }

    private GridPane createStatisticsPanel() {
        GridPane grid = new GridPane();
        grid.setHgap(10);

        ColumnConstraints col1 = new ColumnConstraints(); col1.setPercentWidth(20);
        ColumnConstraints col2 = new ColumnConstraints(); col2.setPercentWidth(20);
        ColumnConstraints col3 = new ColumnConstraints(); col3.setPercentWidth(20);
        ColumnConstraints col4 = new ColumnConstraints(); col4.setPercentWidth(20);
        ColumnConstraints col5 = new ColumnConstraints(); col5.setPercentWidth(20);
        grid.getColumnConstraints().addAll(col1, col2, col3, col4, col5);

        grid.add(createStatCard("TOTAL BOOKS", "0", label -> totalValue = label), 0, 0);
        grid.add(createStatCard("AVAILABLE", "0", label -> availableValue = label), 1, 0);
        grid.add(createStatCard("BORROWED", "0", label -> borrowedValue = label), 2, 0);
        grid.add(createStatCard("PRINTED-BOOKS", "0", label -> printedValue = label), 3, 0);
        grid.add(createStatCard("E-BOOKS", "0", label -> ebookValue = label), 4, 0);

        return grid;
    }

    private VBox createStatCard(String title, String initialValue, java.util.function.Consumer<Label> labelConsumer) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: white; -fx-border-color: #DCE5F0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12 8 12;");

        Label heading = new Label(title);
        heading.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
        heading.setTextFill(Color.web("#55606E"));

        Label value = new Label(initialValue);
        value.setFont(Font.font("SansSerif", FontWeight.BOLD, 22));
        value.setTextFill(Color.BLACK);

        card.getChildren().addAll(heading, value);
        labelConsumer.accept(value);
        return card;
    }

    // =========================================================
    // CATALOG PANEL & TABLE
    // =========================================================

    private VBox createCatalogPanel() {
        VBox panel = new VBox(10);
        panel.setStyle("-fx-background-color: white; -fx-border-color: #DCE5F0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 12 12 10 12;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox headingPanel = new VBox(2);
        Label heading = new Label("Book Catalog");
        heading.setFont(Font.font("SansSerif", FontWeight.BOLD, 16));
        heading.setTextFill(Color.BLACK);

        Label subtitle = new Label("Browse and search your library collection.");
        subtitle.setFont(Font.font("SansSerif", 11));
        subtitle.setTextFill(Color.web("#55606E"));
        headingPanel.getChildren().addAll(heading, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("Search title, author...");
        searchField.setPrefWidth(175);
        searchField.setPrefHeight(32);
        searchField.setStyle("-fx-background-color: white; -fx-border-color: #DCE5F0; -fx-border-radius: 4; -fx-background-radius: 4;");

        categoryFilter = new ComboBox<>();
        categoryFilter.setPrefWidth(140);
        categoryFilter.setPrefHeight(32);
        categoryFilter.setStyle("-fx-background-color: white; -fx-border-color: #DCE5F0; -fx-border-radius: 4; -fx-background-radius: 4;");

        Button refreshBtn = createStyledButton("Refresh", "#DCEBFF");
        refreshBtn.setPrefHeight(32);
        refreshBtn.setOnAction(e -> refreshCatalogTable());

        HBox filters = new HBox(7, searchField, categoryFilter, refreshBtn);
        filters.setAlignment(Pos.CENTER_RIGHT);

        header.getChildren().addAll(headingPanel, spacer, filters);

        bookTable = new TableView<>();
        bookTable.setStyle("-fx-border-color: #DCE5F0; -fx-border-radius: 4;");

        TableColumn<Book, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(50);
        idCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Book, String> typeCol = new TableColumn<>("TYPE");
        typeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getType()));
        typeCol.setPrefWidth(80);
        typeCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Book, String> titleCol = new TableColumn<>("BOOK TITLE");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(220);

        TableColumn<Book, String> authorCol = new TableColumn<>("AUTHOR");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("author"));
        authorCol.setPrefWidth(160);

        TableColumn<Book, String> categoryCol = new TableColumn<>("CATEGORY");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(120);

        TableColumn<Book, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().isBorrowed() ? "Borrowed" : "Available"));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(col -> new TableCell<Book, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("Borrowed".equals(item)) {
                        setStyle("-fx-background-color: #FFE1E5; -fx-text-fill: black; -fx-alignment: CENTER; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-background-color: #DAF7E6; -fx-text-fill: black; -fx-alignment: CENTER; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<Book, String> detailsCol = new TableColumn<>("DETAILS");
        detailsCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getExtraInfo()));
        detailsCol.setPrefWidth(110);

        bookTable.getColumns().addAll(idCol, typeCol, titleCol, authorCol, categoryCol, statusCol, detailsCol);
        bookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEXIBLE);
        bookTable.setItems(filteredCatalog);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        categoryFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());

        panel.getChildren().addAll(header, bookTable);
        VBox.setVgrow(bookTable, Priority.ALWAYS);

        return panel;
    }

    // =========================================================
    // ADD BOOK PAGE
    // =========================================================

    private void showAddBookPage() {
        pageTitle.setText("Add a Book");
        mainContent.setCenter(null);

        VBox page = createFormPage("Add a New Book", "Enter the book details below to add it to the catalog.");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(18, 20, 18, 20));

        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("Printed Book", "E-Book"));
        typeBox.getSelectionModel().select(0);
        typeBox.setPrefWidth(330);
        typeBox.setPrefHeight(36);

        TextField idField = createTextField();
        TextField titleField = createTextField();
        TextField authorField = createTextField();
        TextField categoryField = createTextField();
        TextField detailsField = createTextField();

        Label detailsLabel = new Label("Number of pages:");
        detailsLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));

        typeBox.setOnAction(e -> {
            if (typeBox.getSelectionModel().getSelectedIndex() == 0) {
                detailsLabel.setText("Number of pages:");
            } else {
                detailsLabel.setText("File size (MB):");
            }
        });
        addFormRow(form, 0, "Book Type", typeBox);
        addFormRow(form, 1, "Book ID", idField);
        addFormRow(form, 2, "Book Title", titleField);
        addFormRow(form, 3, "Author", authorField);
        addFormRow(form, 4, "Category", categoryField);
        addFormRow(form, 5, detailsLabel, detailsField);

        Button saveBtn = createStyledButton("Save Book", "#DAF7E6");
        Button clearBtn = createStyledButton("Clear Fields", "#EBEFF3");
        Button backBtn = createStyledButton("Back to Dashboard", "#DCEBFF");

        saveBtn.setOnAction(e -> {
            try {
                int id = Integer.parseInt(idField.getText().trim());
                String title = titleField.getText().trim();
                String author = authorField.getText().trim();
                String category = categoryField.getText().trim();
                String details = detailsField.getText().trim();

                if (id <= 0 || title.isEmpty() || author.isEmpty() || category.isEmpty() || details.isEmpty()) {
                    showWarning("Please complete all fields.");
                    return;
                }

                if (findBook(id) != null) {
                    showWarning("This Book ID already exists.");
                    return;
                }

                Book book;
                if (typeBox.getSelectionModel().getSelectedIndex() == 0) {
                    int pages = Integer.parseInt(details);
                    if (pages <= 0) { showWarning("Page count must be greater than zero."); return; }
                    book = new PrintedBook(id, title, author, category, false, pages);
                } else {
                    double size = Double.parseDouble(details);
                    if (!Double.isFinite(size) || size <= 0) { showWarning("File size must be greater than zero."); return; }
                    book = new Ebook(id, title, author, category, false, size);
                }

                catalog.add(book);
                persistCatalog();
                updateStatistics();
                setStatus("Added book: " + title);
                showInfo("Book added successfully!");
                showDashboard();

            } catch (NumberFormatException ex) {
                showWarning("Enter a valid Book ID and valid numeric details.");
            }
        });

        clearBtn.setOnAction(e -> {
            idField.clear(); titleField.clear(); authorField.clear(); categoryField.clear(); detailsField.clear();
            typeBox.getSelectionModel().select(0);
        });

        backBtn.setOnAction(e -> showDashboard());

        HBox buttons = new HBox(10, saveBtn, clearBtn, backBtn);
        addFormRow(form, 6, "", buttons);

        page.getChildren().add(form);
        mainContent.setCenter(page);
    }

    // =========================================================
    // BORROW / RETURN PAGE
    // =========================================================

    private void showBorrowPage() { showTransactionPage(true); }
    private void showReturnPage() { showTransactionPage(false); }

    private void showTransactionPage(boolean borrowing) {
        pageTitle.setText(borrowing ? "Borrow a Book" : "Return a Book");
        mainContent.setCenter(null);

        VBox page = createFormPage(
                borrowing ? "Borrow a Book" : "Return a Book",
                borrowing ? "Enter a Book ID to record a book as borrowed." : "Enter a Book ID to mark a borrowed book as available."
        );

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(20, 20, 20, 20));

        TextField idField = createTextField();
        Label preview = new Label("Book information will appear here.");
        preview.setFont(Font.font("SansSerif", 12));
        preview.setTextFill(Color.web("#55606E"));

        Button findBtn = createStyledButton("Find Book", "#DCEBFF");
        findBtn.setOnAction(e -> {
            try {
                int id = Integer.parseInt(idField.getText().trim());
                Book book = findBook(id);
                if (book == null) {
                    preview.setText("Book not found.");
                    showWarning("No book was found with that ID.");
                    return;
                }
                preview.setText(book.getTitle() + " — " + book.getAuthor() + " | Type: " + book.getType() + " | Status: " + (book.isBorrowed() ? "Borrowed" : "Available"));
            } catch (NumberFormatException ex) {
                showWarning("Enter a valid numeric Book ID.");
            }
        });

        Button submitBtn = createStyledButton(borrowing ? "Confirm Borrow" : "Confirm Return", borrowing ? "#DCEBFF" : "#DAF7E6");
        submitBtn.setOnAction(e -> {
            try {
                int id = Integer.parseInt(idField.getText().trim());
                Book book = findBook(id);
                if (book == null) { showWarning("Book ID not found."); return; }
                if (borrowing && book.isBorrowed()) { showWarning("This book has already been borrowed."); return; }
                if (!borrowing && !book.isBorrowed()) { showWarning("This book is already available."); return; }

                book.setBorrowed(borrowing);
                persistCatalog();
                updateStatistics();
                showInfo(borrowing ? "Book borrowed successfully:\n" + book.getTitle() : "Book returned successfully:\n" + book.getTitle());
                setStatus((borrowing ? "Borrowed: " : "Returned: ") + book.getTitle());
                showDashboard();
            } catch (NumberFormatException ex) {
                showWarning("Enter a valid numeric Book ID.");
            }
        });

        Button backBtn = createStyledButton("Back to Dashboard", "#EBEFF3");
        backBtn.setOnAction(e -> showDashboard());

        addFormRow(form, 0, "Book ID", idField);
        addFormRow(form, 1, "", findBtn);
        addFormRow(form, 2, "Book Details", preview);

        HBox buttons = new HBox(8, submitBtn, backBtn);
        addFormRow(form, 3, "", buttons);

        page.getChildren().add(form);
        mainContent.setCenter(page);
    }

    // =========================================================
    // REMOVE BOOK PAGE
    // =========================================================

    private void showRemoveBookPage() {
        pageTitle.setText("Remove a Book");
        mainContent.setCenter(null);

        VBox page = createFormPage(
                "Remove a Book",
                "Enter a Book ID to permanently remove a book, or remove all books from the catalog."
        );

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(20, 20, 20, 20));

        TextField idField = createTextField();
        Label preview = new Label("Book information will appear here.");
        preview.setFont(Font.font("SansSerif", 12));
        preview.setTextFill(Color.web("#55606E"));

        Button findBtn = createStyledButton("Find Book", "#DCEBFF");
        findBtn.setOnAction(e -> {
            try {
                int id = Integer.parseInt(idField.getText().trim());
                Book book = findBook(id);
                if (book == null) {
                    preview.setText("Book not found.");
                    showWarning("No book was found with that ID.");
                    return;
                }
                preview.setText(book.getTitle() + " — " + book.getAuthor() + " | Type: " + book.getType() + " | Status: " + (book.isBorrowed() ? "Borrowed" : "Available"));
            } catch (NumberFormatException ex) {
                showWarning("Enter a valid numeric Book ID.");
            }
        });

        Button removeBtn = createStyledButton("Confirm Removal", "#FFE1E5");
        removeBtn.setOnAction(e -> {
            try {
                int id = Integer.parseInt(idField.getText().trim());
                Book book = findBook(id);
                if (book == null) { showWarning("Book ID not found."); return; }

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to remove:\n" + book.getTitle() + "?", ButtonType.YES, ButtonType.NO);
                alert.setTitle("Confirm Removal");
                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.YES) {
                        catalog.remove(book);
                        persistCatalog();
                        updateStatistics();
                        showInfo("Book removed successfully:\n" + book.getTitle());
                        setStatus("Removed book: " + book.getTitle());
                        showDashboard();
                    }
                });
            } catch (NumberFormatException ex) {
                showWarning("Enter a valid numeric Book ID.");
            }
        });

        Button removeAllBtn = createStyledButton("Remove All Books", "#FFC8C8");
        removeAllBtn.setOnAction(e -> {
            if (catalog.isEmpty()) { showWarning("The catalog is already empty."); return; }

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "WARNING: Are you sure you want to delete ALL books?\nThis action cannot be undone!", ButtonType.YES, ButtonType.NO);
            alert.setTitle("Confirm Remove All Books");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    catalog.clear();
                    persistCatalog();
                    updateStatistics();
                    showInfo("All books have been removed from the catalog.");
                    setStatus("All books removed from catalog.");
                    showDashboard();
                }
            });
        });

        Button backBtn = createStyledButton("Back to Dashboard", "#EBEFF3");
        backBtn.setOnAction(e -> showDashboard());

        addFormRow(form, 0, "Book ID", idField);
        addFormRow(form, 1, "", findBtn);
        addFormRow(form, 2, "Book Details", preview);

        HBox buttons = new HBox(8, removeBtn, removeAllBtn, backBtn);
        addFormRow(form, 3, "", buttons);

        page.getChildren().add(form);
        mainContent.setCenter(page);
    }

    // =========================================================
    // FULL CATALOG PAGE
    // =========================================================

    private void showCatalogPage() {
        pageTitle.setText("Full Book Catalog");
        mainContent.setCenter(null);

        VBox page = new VBox(10);
        Label description = new Label("Complete collection of printed books and electronic books.");
        description.setFont(Font.font("SansSerif", 12));
        description.setTextFill(Color.web("#55606E"));

        VBox catalogPanel = createCatalogPanel();
        VBox.setVgrow(catalogPanel, Priority.ALWAYS);

        page.getChildren().addAll(description, catalogPanel);
        mainContent.setCenter(page);

        refreshCatalogTable();
    }

    // =========================================================
    // FILE UPLOAD
    // =========================================================

    private void uploadBookFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Upload Book Data File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text or CSV Files", "*.txt", "*.csv"));

        File selectedFile = chooser.showOpenDialog(primaryStage);
        if (selectedFile == null) return;

        int added = 0; int skipped = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(selectedFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;

                String[] p = line.split(",", -1);
                if (p.length < 7) { skipped++; continue; }

                try {
                    String type = p[0].trim();
                    int id = Integer.parseInt(p[1].trim());
                    String title = p[2].trim();
                    String author = p[3].trim();
                    String category = p[4].trim();
                    boolean borrowed = Boolean.parseBoolean(p[5].trim());

                    if (id <= 0 || title.isEmpty() || author.isEmpty() || findBook(id) != null) {
                        skipped++; continue;
                    }

                    Book book;
                    if (type.equalsIgnoreCase("Printed")) {
                        int pages = Integer.parseInt(p[6].trim());
                        if (pages <= 0) { skipped++; continue; }
                        book = new PrintedBook(id, title, author, category, borrowed, pages);
                    } else if (type.equalsIgnoreCase("Ebook") || type.equalsIgnoreCase("E-Book")) {
                        double size = Double.parseDouble(p[6].trim());
                        if (!Double.isFinite(size) || size <= 0) { skipped++; continue; }
                        book = new Ebook(id, title, author, category, borrowed, size);
                    } else {
                        skipped++; continue;
                    }

                    catalog.add(book);
                    added++;
                } catch (RuntimeException ex) {
                    skipped++;
                }
            }

            persistCatalog();
            updateStatistics();
            setStatus("Import complete. Added: " + added + ", skipped: " + skipped);
            showInfo("Import completed!\n\nBooks added: " + added + "\nRows skipped: " + skipped);
            showDashboard();

        } catch (IOException ex) {
            showError("Unable to read file:\n" + ex.getMessage());
        }
    }

    // =========================================================
    // HELPERS, FILTERS & PERSISTENCE
    // =========================================================

    private void refreshCatalogTable() {
        updateStatistics();
        updateCategoryOptions();
        applyFilters();
    }

    private void applyFilters() {
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String selectedCategory = categoryFilter.getValue();

        filteredCatalog.setPredicate(book -> {
            boolean matchesSearch = query.isEmpty() || 
                String.valueOf(book.getId()).contains(query) ||
                book.getTitle().toLowerCase().contains(query) ||
                book.getAuthor().toLowerCase().contains(query) ||
                book.getCategory().toLowerCase().contains(query);

            boolean matchesCategory = selectedCategory == null || "All Categories".equals(selectedCategory) || book.getCategory().equalsIgnoreCase(selectedCategory);

            return matchesSearch && matchesCategory;
        });

        if (statusLabel != null) {
            statusLabel.setText("●  Showing " + bookTable.getItems().size() + " of " + catalog.size() + " books.");
        }
    }

    private void updateCategoryOptions() {
        String previous = categoryFilter.getValue();
        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Book book : catalog) {
            if (!book.getCategory().trim().isEmpty()) {
                categories.add(book.getCategory());
            }
        }

        ObservableList<String> items = FXCollections.observableArrayList();
        items.add("All Categories");
        items.addAll(categories);
        categoryFilter.setItems(items);

        if (previous != null && items.contains(previous)) {
            categoryFilter.setValue(previous);
        } else {
            categoryFilter.getSelectionModel().select(0);
        }
    }

    private void updateStatistics() {
        if (totalValue == null) return;

        int borrowed = 0;
        int printed = 0;
        int ebooks = 0;

        for (Book book : catalog) {
            if (book.isBorrowed()) borrowed++;
            if (book instanceof PrintedBook) printed++;
            if (book instanceof Ebook) ebooks++;
        }

        totalValue.setText(String.valueOf(catalog.size()));
        availableValue.setText(String.valueOf(catalog.size() - borrowed));
        borrowedValue.setText(String.valueOf(borrowed));
        printedValue.setText(String.valueOf(printed));
        ebookValue.setText(String.valueOf(ebooks));
    }

    private VBox createFormPage(String title, String subtitle) {
        VBox page = new VBox(10);
        page.setStyle("-fx-background-color: white; -fx-border-color: #DCE5F0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 16 18 16 18;");

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.BLACK);

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setFont(Font.font("SansSerif", 12));
        subtitleLabel.setTextFill(Color.web("#55606E"));

        page.getChildren().addAll(titleLabel, subtitleLabel);
        return page;
    }

    private TextField createTextField() {
        TextField field = new TextField();
        field.setPrefWidth(330);
        field.setPrefHeight(36);
        field.setStyle("-fx-background-color: white; -fx-border-color: #DCE5F0; -fx-border-radius: 4; -fx-background-radius: 4;");
        return field;
    }

    private void addFormRow(GridPane panel, int row, String labelText, javafx.scene.Node control) {
        Label name = new Label(labelText);
        name.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        name.setTextFill(Color.BLACK);

        panel.add(name, 0, row);
        panel.add(control, 1, row);
    }

    private Button createStyledButton(String text, String hexBg) {
        Button btn = new Button(text);
        btn.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        btn.setTextFill(Color.BLACK);
        btn.setStyle("-fx-background-color: " + hexBg + "; -fx-border-color: derive(" + hexBg + ", -20%); -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 7 12 7 12;");
        return btn;
    }

    private void loadCatalog() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                Object data = in.readObject();
                if (data instanceof List<?>) {
                    for (Object item : (List<?>) data) {
                        if (item instanceof Book) {
                            Book book = (Book) item;
                            if (findBook(book.getId()) == null) {
                                catalog.add(book);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Could not load catalog: " + ex.getMessage());
            }
        }
        

        if (catalog.isEmpty()) {
            catalog.add(new PrintedBook(101, "To Kill a Mockingbird", "Harper Lee", "Fiction", false, 281));
            catalog.add(new Ebook(102, "Java Programming", "John Doe", "Education", false, 15.0));
            catalog.add(new PrintedBook(103, "Introduction to Science", "Maria Santos", "Science", false, 320));
            catalog.add(new Ebook(104, "Digital Learning Guide", "Alex Reyes", "Education", false, 8.5));
            persistCatalog();
        }
    }

    private void persistCatalog() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            out.writeObject(new ArrayList<>(catalog));
        } catch (IOException ex) {
            showError("Unable to save catalog:\n" + ex.getMessage());
        }
    }

    private void saveCatalog() {
        persistCatalog();
        setStatus("Catalog saved successfully.");
        showInfo("Your catalog has been saved.");
    }

    private Book findBook(int id) {
        for (Book book : catalog) {
            if (book.getId() == id) return book;
        }
        return null;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText("●  " + message);
        }
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle("Please Check");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle("DEIHS Library");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void exitApplication() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Would you like to save the catalog before exiting?", ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        alert.setTitle("Exit DEIHS Library Hub");
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                persistCatalog();
                primaryStage.close();
                System.exit(0);
            } else if (response == ButtonType.NO) {
                primaryStage.close();
                System.exit(0);
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}