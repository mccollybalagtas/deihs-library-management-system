package com.deihs.library.ui;

import com.deihs.library.dao.BookDao;
import com.deihs.library.dao.DaoException;
import com.deihs.library.model.Book;
import com.deihs.library.model.Role;
import com.deihs.library.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
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
import javafx.stage.Window;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** The signed-in shell: sidebar, top bar, footer and the page area. */
public class MainView extends BorderPane {

    private final Stage stage;
    private final User user;
    private final Runnable onLogout;

    private final StackPane content = new StackPane();
    private final StackPane toastLayer = new StackPane();
    private final StackPane contentHost = new StackPane(content, toastLayer);
    private final Toasts toasts = new Toasts(toastLayer);
    private final Label pageTitle = new Label();
    private final Label statusLabel = new Label();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();

    private String currentKey = "dashboard";
    private DashboardView dashboard;
    private CollectionPanel collection;
    private CirculationView circulation;
    private CatalogView catalog;
    private MyLoansView myLoans;
    private UsersView users;

    public MainView(Stage stage, User user, Runnable onLogout) {
        this.stage = stage;
        this.user = user;
        this.onLogout = onLogout;

        setLeft(buildSidebar());

        content.setStyle("-fx-background-color: #F3F7FD;");
        content.setAlignment(Pos.TOP_LEFT);

        VBox center = new VBox(buildTopBar(), contentHost, buildFooter());
        VBox.setVgrow(contentHost, Priority.ALWAYS);
        setCenter(center);

        navigate("dashboard");
        setStatus("Signed in as " + user.getDisplayName() + " \u2022 " + user.getRole().label());
    }

    public Window window() {
        return stage;
    }

    public User user() {
        return user;
    }

    public Role role() {
        return user.getRole();
    }

    public void setStatus(String message) {
        statusLabel.setText("\u25CF  " + message);
    }

    /** Non-blocking success feedback shown above the page. */
    public void toastSuccess(String message) {
        toasts.success(message);
    }

    /** Asks for confirmation, then returns to the sign-in screen. */
    private void confirmLogout() {
        if (Dialogs.confirm(stage, "Sign out", "Sign out of the library system?")) {
            onLogout.run();
        }
    }

    public void navigate(String requestedKey) {
        final String key = navButtons.containsKey(requestedKey) ? requestedKey : "dashboard";
        currentKey = key;

        pageTitle.setText(switch (key) {
            case "collection" -> "Book Collection";
            case "circulation" -> "Circulation";
            case "myloans" -> "My Loans";
            case "catalog" -> "Catalog";
            case "users" -> "Accounts";
            default -> "Library Dashboard";
        });

        navButtons.forEach((name, button) -> {
            button.getStyleClass().remove("active");
            if (name.equals(key)) {
                button.getStyleClass().add("active");
            }
        });

        switch (key) {
            case "collection" -> {
                if (collection == null) {
                    collection = new CollectionPanel(this);
                } else {
                    collection.refresh();
                }
                content.getChildren().setAll(collection);
            }
            case "circulation" -> {
                if (circulation == null) {
                    circulation = new CirculationView(this);
                } else {
                    circulation.refresh();
                }
                content.getChildren().setAll(circulation);
            }
            case "myloans" -> {
                if (myLoans == null) {
                    myLoans = new MyLoansView(this);
                } else {
                    myLoans.refresh();
                }
                content.getChildren().setAll(myLoans);
            }
            case "catalog" -> {
                if (catalog == null) {
                    catalog = new CatalogView(this);
                } else {
                    catalog.refresh();
                }
                content.getChildren().setAll(catalog);
            }
            case "users" -> {
                if (users == null) {
                    users = new UsersView(this);
                } else {
                    users.refresh();
                }
                content.getChildren().setAll(users);
            }
            default -> {
                if (dashboard == null) {
                    dashboard = new DashboardView(this);
                } else {
                    dashboard.refresh();
                }
                content.getChildren().setAll(dashboard);
            }
        }
    }

    /** Rebuilds every cached page so counts and grids stay accurate. */
    public void refreshData() {
        dashboard = null;
        collection = null;
        circulation = null;
        catalog = null;
        myLoans = null;
        users = null;
        navigate(currentKey);
    }

    public void addBook() {
        if (!user.getRole().canManageBooks()) {
            Dialogs.error(stage, "Not allowed",
                    "Only an administrator can add or change books in the catalog.");
            return;
        }
        Book book = BookFormView.show(stage, null);
        if (book == null) {
            return;
        }
        try {
            new BookDao().insert(book);
            setStatus("Added \"" + book.getTitle() + "\" to the catalog.");
            toastSuccess("Added \"" + book.getTitle() + "\" to the catalog.");
            refreshData();
        } catch (DaoException ex) {
            Dialogs.error(stage, "Could not add the book", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Chrome
    // ------------------------------------------------------------------

    private VBox buildSidebar() {
        VBox sidebar = new VBox(6);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(238);
        sidebar.setMinWidth(238);
        sidebar.setMaxWidth(238);
        sidebar.setPadding(new Insets(20, 14, 16, 14));

        StackPane logo = new StackPane();
        logo.setPrefSize(40, 40);
        logo.setMinSize(40, 40);
        logo.setMaxSize(40, 40);

        try (InputStream in = MainView.class.getResourceAsStream("/images/deihs-logo.png")) {
            if (in != null) {
                ImageView view = new ImageView(new Image(in));
                view.setPreserveRatio(true);
                view.setSmooth(true);
                view.setFitWidth(40);
                view.setFitHeight(40);
                logo.getChildren().add(view);
            }
        } catch (Exception ignored) {
            // Logo is decorative only.
        }

        Label name = new Label("DEIHS");
        name.setFont(Fonts.display(17, FontWeight.BOLD));
        name.setTextFill(Color.WHITE);
        name.setAlignment(Pos.CENTER_LEFT);
        name.setMaxHeight(Double.MAX_VALUE);

        HBox brand = new HBox(10, logo, name);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 0, 18, 4));

        Label menu = new Label("MAIN MENU");
        menu.setFont(Font.font("SansSerif", FontWeight.BOLD, 8.5));
        menu.setTextFill(Color.web("#7E9CC0"));
        menu.setPadding(new Insets(0, 0, 4, 8));

        sidebar.getChildren().addAll(brand, menu, navButton("dashboard", "\u25A6", "Dashboard"));

        if (user.getRole().canManageBooks()) {
            sidebar.getChildren().add(navButton("collection", "\u25A4", "Book Collection"));
        }
        if (user.getRole().canRunDesk()) {
            sidebar.getChildren().add(navButton("circulation", "\u21C4", "Circulation"));
        }
        if (user.getRole().canBorrowForSelf()) {
            sidebar.getChildren().add(navButton("myloans", "\u21C4", "My Loans"));
        }
        sidebar.getChildren().add(navButton("catalog", "\u2630", "Catalog"));
        if (user.getRole().canManageUsers()) {
            sidebar.getChildren().add(navButton("users", "\u263A", "Accounts"));
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logout = new Button("\u21A9   Sign out");
        logout.getStyleClass().add("side-logout");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> confirmLogout());

        Label quote = new Label("\u201CRead. Learn. Grow.\u201D");
        quote.setFont(Font.font("Serif", FontPosture.ITALIC, 12));
        quote.setTextFill(Color.web("#64DCEB"));
        quote.setPadding(new Insets(14, 0, 2, 6));

        Label school = new Label("DEIHS SCHOOL LIBRARY");
        school.setFont(Font.font("SansSerif", FontWeight.BOLD, 8));
        school.setTextFill(Color.web("#9FB6D4"));
        school.setPadding(new Insets(0, 0, 0, 6));

        sidebar.getChildren().addAll(spacer, logout, quote, school);
        return sidebar;
    }

    private Button navButton(String key, String icon, String text) {
        Button button = new Button(icon + "    " + text);
        button.getStyleClass().add("side-btn");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> navigate(key));
        navButtons.put(key, button);
        return button;
    }

    private HBox buildTopBar() {
        pageTitle.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label date = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)));
        date.getStyleClass().add("muted");
        date.setFont(Font.font("SansSerif", 11));

        Label name = new Label(user.getDisplayName());
        name.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        name.setTextFill(Color.web("#1F2933"));

        Label avatar = new Label(user.initial());
        avatar.getStyleClass().add("avatar");
        avatar.setTooltip(new Tooltip(user.getRole().label()
                + " \u2022 " + user.getDisplayName()));

        Button signOut = new Button("\u21A9  Sign out");
        signOut.getStyleClass().add("ghost-btn");
        signOut.setPrefHeight(32);
        signOut.setFocusTraversable(false);
        signOut.setOnAction(e -> confirmLogout());
        signOut.setTooltip(new Tooltip("Sign out"));

        HBox bar = new HBox(10, pageTitle, spacer, date, name, avatar, signOut);
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildFooter() {
        statusLabel.getStyleClass().add("status-ok");
        statusLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label version = new Label("Balagtas DEIHS Library  \u2022  v1.1");
        version.getStyleClass().add("muted");
        version.setFont(Font.font("SansSerif", 9.5));

        HBox footer = new HBox(8, statusLabel, spacer, version);
        footer.getStyleClass().add("footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        return footer;
    }
}
