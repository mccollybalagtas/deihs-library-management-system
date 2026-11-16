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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
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

/**
 * The signed-in shell: sidebar, top bar, footer and the page area.
 *
 * Part of: Application shell, shown after a successful sign-in.
 * Used by: App.onSignedIn() creates it; CollectionPanel, CirculationView,
 *          CatalogView, MyLoansView, UsersView and DashboardView call back into it.
 * Key rules: every page is created lazily and cached, and the sidebar entries are
 *            chosen from Role permissions, never from role names.
 */
public class MainView extends BorderPane {

    // ============================================================
    // PART: Shell state
    // ============================================================
    private final Stage stage;
    private final User user;
    private final Runnable onLogout;

    // Two stacked layers: the page fills "content", toasts float above it.
    private final StackPane content = new StackPane();
    private final StackPane toastLayer = new StackPane();
    private final StackPane contentHost = new StackPane(content, toastLayer);
    private final Toasts toasts = new Toasts(toastLayer);
    private final Label pageTitle = new Label();
    private final Label statusLabel = new Label();
    // Insertion order matters: it is also the sidebar order and the navigation whitelist.
    private final Map<String, Button> navButtons = new LinkedHashMap<>();

    // Which page is open now, and one cached instance per page.
    private String currentKey = "dashboard";
    private DashboardView dashboard;
    private CollectionPanel collection;
    private CirculationView circulation;
    private CatalogView catalog;
    private MyLoansView myLoans;
    private UsersView users;

    /**
     * WHAT: Builds the sidebar, top bar, page area and footer, then opens the dashboard.
     * WHY: This is the frame every signed-in screen lives inside, so it also owns
     * navigation state and the keyboard shortcut.
     * CALLED BY: App.onSignedIn(), with the user returned by UserDao.login().
     */
    public MainView(Stage stage, User user, Runnable onLogout) {
        this.stage = stage;
        this.user = user;
        this.onLogout = onLogout;

        // Step 1: the sidebar is fixed on the left and does not scroll.
        setLeft(buildSidebar());

        // Step 2: the page area sits under the top bar and above the footer.
        content.setStyle("-fx-background-color: #F3F7FD;");
        content.setAlignment(Pos.TOP_LEFT);

        VBox center = new VBox(buildTopBar(), contentHost, buildFooter());
        VBox.setVgrow(contentHost, Priority.ALWAYS);
        setCenter(center);

        // Step 3: show the dashboard and put the signed-in user in the status bar.
        navigate("dashboard");
        setStatus("Signed in as " + user.getDisplayName() + " \u2022 " + user.getRole().label());

        // Ctrl+F (Cmd+F on macOS) jumps to the search box on the open page.
        // The listener waits for the scene because accelerators only exist once
        // the view has been attached to one.
        sceneProperty().addListener((o, old, scene) -> {
            if (scene != null) {
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                        this::focusSearch);
            }
        });
    }

    /** The window that dialogs should be parented to. */
    public Window window() {
        return stage;
    }

    /** The signed-in account. */
    public User user() {
        return user;
    }

    /** The signed-in account's role, used for permission checks. */
    public Role role() {
        return user.getRole();
    }

    /** Writes a message into the footer status line. */
    public void setStatus(String message) {
        statusLabel.setText("\u25CF  " + message);
    }

    /** Non-blocking success feedback shown above the page. */
    public void toastSuccess(String message) {
        toasts.success(message);
    }

    /** Non-blocking error feedback shown above the page. */
    public void toastError(String message) {
        toasts.error(message);
    }

    /** Non-blocking neutral feedback shown above the page. */
    public void toastInfo(String message) {
        toasts.info(message);
    }

    /**
     * WHAT: Focuses the search box on whichever page is open.
     * WHY: Ctrl+F must do something sensible on every page, but each page owns
     * its own search field, so this method dispatches by the current page key.
     * CALLED BY: The Ctrl+F accelerator registered in the constructor.
     */
    public void focusSearch() {
        switch (currentKey) {
            case "collection" -> {
                if (collection != null) {
                    collection.focusSearch();
                }
            }
            case "circulation" -> {
                if (circulation != null) {
                    circulation.focusSearch();
                }
            }
            case "myloans" -> {
                if (myLoans != null) {
                    myLoans.focusSearch();
                }
            }
            case "catalog" -> {
                if (catalog != null) {
                    catalog.focusSearch();
                }
            }
            case "users" -> {
                if (users != null) {
                    users.focusSearch();
                }
            }
            default -> {
                if (dashboard != null) {
                    dashboard.focusSearch();
                }
            }
        }
    }

    /**
     * WHAT: Asks for confirmation, then returns to the sign-in screen.
     * WHY: Signing out throws away the current page state, so it should not
     * happen on a stray click.
     * CALLED BY: The sidebar Sign out button and the top-bar Sign out button.
     */
    private void confirmLogout() {
        if (Dialogs.confirm(stage, "Sign out", "Sign out of the library system?")) {
            // onLogout is App.showLogin(), which swaps the frame content back to LoginView.
            onLogout.run();
        }
    }

    /**
     * WHAT: Shows the page for a key, falling back to the dashboard for unknown keys.
     * WHY: Buttons can only navigate to pages their role may see, but a stale or
     * wrong key must never leave the content area empty.
     * CALLED BY: The constructor, navButton handlers, DashboardView's "View all",
     * and refreshData().
     */
    public void navigate(String requestedKey) {
        // Step 1: only keys that have a sidebar button are valid for this role.
        final String key = navButtons.containsKey(requestedKey) ? requestedKey : "dashboard";
        currentKey = key;

        // Step 2: update the heading in the top bar.
        pageTitle.setText(switch (key) {
            case "collection" -> "Book Collection";
            case "circulation" -> "Circulation";
            case "myloans" -> "My Loans";
            case "catalog" -> "Catalog";
            case "users" -> "Accounts";
            default -> "Library Dashboard";
        });

        // Step 3: move the "active" style class to the matching sidebar button.
        navButtons.forEach((name, button) -> {
            button.getStyleClass().remove("active");
            if (name.equals(key)) {
                button.getStyleClass().add("active");
            }
        });

        // Step 4: create the page the first time it is opened, otherwise refresh
        // the cached instance so its data is current. Only one page is in the
        // content area at a time.
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

    /**
     * WHAT: Throws away every cached page and reopens the current one.
     * WHY: After a book is added or edited, the other pages still hold old lists
     * and counters, so they must be rebuilt from the database.
     * CALLED BY: addBook() here, and BookCard / CollectionPanel / CatalogView via
     * a MainView::refreshData callback.
     */
    public void refreshData() {
        // Clearing the references is enough: navigate() creates a fresh instance
        // for any page that is null.
        dashboard = null;
        collection = null;
        circulation = null;
        catalog = null;
        myLoans = null;
        users = null;
        navigate(currentKey);
    }

    /**
     * WHAT: Opens the add-book dialog, saves the result and refreshes the shell.
     * WHY: Several pages offer an "Add Book" action, so the permission check and
     * the save live here instead of being repeated in each page.
     * CALLED BY: DashboardView's Add Book button and CollectionPanel's Add Book button.
     */
    public void addBook() {
        // Step 1: the Role decides, so a hidden button cannot be bypassed by
        // calling this method directly.
        if (!user.getRole().canManageBooks()) {
            Dialogs.error(stage, "Not allowed",
                    "Only an administrator can add or change books in the catalog.");
            return;
        }
        // Step 2: the dialog returns null when it was cancelled.
        Book book = BookFormView.show(stage, null);
        if (book == null) {
            return;
        }
        // Step 3: save, then tell the user through both the status line and a toast.
        try {
            new BookDao().insert(book);
            setStatus("Added \"" + book.getTitle() + "\" to the catalog.");
            toastSuccess("Added \"" + book.getTitle() + "\" to the catalog.");
            // Step 4: rebuild the pages so the new title appears everywhere.
            refreshData();
        } catch (DaoException ex) {
            Dialogs.error(stage, "Could not add the book", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Chrome
    // ------------------------------------------------------------------
    // ============================================================
    // PART: Building the sidebar
    // ============================================================

    /**
     * WHAT: Builds the fixed-width sidebar: brand, menu label, role-filtered
     * navigation buttons, sign-out and a small footer.
     * WHY: This is the only navigation the user has, and filtering it here means
     * a role never sees a link to a page it cannot open.
     * CALLED BY: The constructor, through setLeft(buildSidebar()).
     */
    private VBox buildSidebar() {
        // Step 1: fixed width, so the page area always starts at the same place.
        VBox sidebar = new VBox(6);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(238);
        sidebar.setMinWidth(238);
        sidebar.setMaxWidth(238);
        sidebar.setPadding(new Insets(20, 14, 16, 14));

        // Step 2: brand block: logo plus the short school name.
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

        // Step 3: small section label above the navigation buttons.
        Label menu = new Label("MAIN MENU");
        menu.setFont(Font.font("SansSerif", FontWeight.BOLD, 8.5));
        menu.setTextFill(Color.web("#7E9CC0"));
        menu.setPadding(new Insets(0, 0, 4, 8));

        // Step 4: Dashboard is visible to everyone; the rest are added only when
        // the Role grants the matching permission. Role is asked, not compared by name.
        sidebar.getChildren().addAll(brand, menu, navButton("dashboard", "\u25A6", "Dashboard"));

        if (user.getRole().canManageBooks()) {
            // Book management is administrator-only.
            sidebar.getChildren().add(navButton("collection", "\u25A4", "Book Collection"));
        }
        if (user.getRole().canRunDesk()) {
            // Librarians and administrators issue and return books at the desk.
            sidebar.getChildren().add(navButton("circulation", "\u21C4", "Circulation"));
        }
        if (user.getRole().canBorrowForSelf()) {
            // Students get their own loans page instead of the desk page.
            sidebar.getChildren().add(navButton("myloans", "\u21C4", "My Loans"));
        }
        // Every role may browse the catalog.
        sidebar.getChildren().add(navButton("catalog", "\u2630", "Catalog"));
        if (user.getRole().canManageUsers()) {
            // Account management is administrator-only.
            sidebar.getChildren().add(navButton("users", "\u263A", "Accounts"));
        }

        // Step 5: the spacer pushes the sign-out button and footer to the bottom.
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

    /**
     * WHAT: Creates one sidebar button and registers it in navButtons.
     * WHY: navigate() needs the map to validate keys and to move the "active"
     * style, so every button must be registered at creation time.
     * CALLED BY: buildSidebar() for each visible page.
     */
    private Button navButton(String key, String icon, String text) {
        Button button = new Button(icon + "    " + text);
        button.getStyleClass().add("side-btn");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> navigate(key));
        navButtons.put(key, button);
        return button;
    }

    // ============================================================
    // PART: Building the top bar and footer
    // ============================================================

    /**
     * WHAT: Builds the bar above the page: title on the left, then date, user
     * name, avatar and a second sign-out button on the right.
     * WHY: The page title is a shared label that navigate() updates, so it cannot
     * be created inside each page.
     * CALLED BY: The constructor, when assembling the centre column.
     */
    private HBox buildTopBar() {
        pageTitle.getStyleClass().add("page-title");

        // The spacer takes all free width, which pushes everything after it right.
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label date = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)));
        date.getStyleClass().add("muted");
        date.setFont(Font.font("SansSerif", 11));

        Label name = new Label(user.getDisplayName());
        name.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        name.setTextFill(Color.web("#1F2933"));

        // The avatar is just the first letter; the tooltip supplies the full
        // name and role so the circle stays small.
        Label avatar = new Label(user.initial());
        avatar.getStyleClass().add("avatar");
        avatar.setTooltip(new Tooltip(user.getRole().label()
                + " \u2022 " + user.getDisplayName()));

        // A second sign-out entry in the top bar; excluded from tab order so Tab
        // stays inside the page.
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

    /**
     * WHAT: Builds the thin footer with the status message on the left and the
     * version label on the right.
     * WHY: setStatus() writes into statusLabel, so the label must exist once and
     * be reused for every message.
     * CALLED BY: The constructor, when assembling the centre column.
     */
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
