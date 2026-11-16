package com.deihs.library.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import java.io.InputStream;

/**
 * A frameless window shell with a branded title bar: the app logo sits to the
 * left of the window title, followed by minimise / maximise / close controls.
 *
 * <p>Because the native title bar is replaced, this class also restores the
 * behaviour a window manager would normally provide: dragging the bar moves the
 * window, double-clicking it maximises, and dragging any window edge resizes.</p>
 *
 * Part of: Application shell, the frameless window that hosts every screen.
 * Used by: App, which creates it in start(), then calls setContent() and install(scene).
 * Key rules: the stage must use StageStyle.UNDECORATED, and install(scene) must run
 *            before dragging, resizing or the maximise button can work.
 */
public class WindowFrame extends BorderPane {

    // ============================================================
    // PART: Window state
    // ============================================================
    // How close to an edge, in pixels, the pointer counts as "on the border".
    private static final int RESIZE_MARGIN = 6;
    private static final double MIN_WIDTH = 1080;
    private static final double MIN_HEIGHT = 700;

    private final Stage stage;
    private final StackPane contentHost = new StackPane();
    private final SVGPath maximiseIcon = new SVGPath();

    private double dragOffsetX;
    private double dragOffsetY;

    private Zone zone = Zone.NONE;
    private double pressScreenX;
    private double pressScreenY;
    private double startX;
    private double startY;
    private double startWidth;
    private double startHeight;

    /**
     * WHAT: Stores the stage, then assembles the shell with the title bar on top
     * and an empty content host in the middle.
     * WHY: The stage reference is needed later for dragging, resizing and the
     * window buttons, so it is captured once here.
     * CALLED BY: App.start(), before the scene is created.
     */
    public WindowFrame(Stage stage) {
        this.stage = stage;
        getStyleClass().add("window-frame");
        setTop(buildTitleBar());
        setCenter(contentHost);
    }

    /**
     * WHAT: Replaces whatever is inside the content area.
     * WHY: App swaps whole screens (sign-in, signed-in shell) without rebuilding
     * the frame, so the branded title bar survives the switch.
     * CALLED BY: App.start(), App.onSignedIn() and App.showLogin().
     */
    public void setContent(Node content) {
        contentHost.getChildren().setAll(content);
    }

    /**
     * WHAT: Registers the four mouse filters that drive drag, resize and cursor changes.
     * WHY: The native window manager is gone with the decorated frame, so these
     * listeners replace the behaviour it used to provide.
     * CALLED BY: App.start(), immediately after the Scene is created.
     */
    public void install(Scene scene) {
        // Event filters run before the target node handles the event, so the
        // edge logic still works when the pointer is over a child control.
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, this::onMouseMoved);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> zone = Zone.NONE);
    }

    // ------------------------------------------------------------------
    // Title bar
    // ------------------------------------------------------------------
    // ============================================================
    // PART: Building the title bar
    // ============================================================

    /**
     * WHAT: Builds the custom title bar: logo, title text, a spacer and the
     * minimise / maximise / close buttons, then attaches drag and double-click.
     * WHY: With StageStyle.UNDECORATED there is no native title bar, so the app
     * has to draw one and re-implement moving the window.
     * CALLED BY: The constructor, through setTop(buildTitleBar()).
     */
    private HBox buildTitleBar() {
        // Step 1: create the bar and reserve the fixed 42px height the theme expects.
        HBox bar = new HBox(10);
        bar.getStyleClass().add("title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 0, 14));
        bar.setMinHeight(42);
        bar.setPrefHeight(42);

        // Step 2: brand the bar with the small logo and the application name.
        Node logo = buildLogo(24);

        Label title = new Label("DEIHS Library Management System");
        title.getStyleClass().add("title-text");

        // Step 3: a spacer with ALWAYS growth pushes the window buttons to the right.
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Step 4: create the three window buttons and wire each to the stage.
        Button minimise = windowButton(icon("M2.5 7.5 H12.5"), "Minimise");
        minimise.setOnAction(e -> stage.setIconified(true));

        Button maximise = windowButton(maximiseIcon, "Maximise");
        maximise.setOnAction(e -> toggleMaximise());
        updateMaximiseIcon();

        Button close = windowButton(icon("M3 3 L13 13 M13 3 L3 13"), "Close");
        close.getStyleClass().add("win-close");
        close.setOnAction(e -> stage.close());

        HBox controls = new HBox(minimise, maximise, close);
        controls.setAlignment(Pos.CENTER_RIGHT);

        bar.getChildren().addAll(logo, title, spacer, controls);

        // Step 5: drag-to-move. At press time we remember the gap between the
        // pointer and the window's top-left corner. Every drag event then puts
        // the window at (pointer - gap), so it follows the pointer without jumping.
        bar.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY || stage.isMaximized()) {
                return;
            }
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
        });
        bar.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY || stage.isMaximized()) {
                return;
            }
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });
        // Step 6: double-clicking the bar toggles maximise, like a native window.
        bar.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                toggleMaximise();
            }
        });

        // Step 7: keep the maximise glyph in step with the real window state,
        // including changes made by the window manager or by double-click.
        stage.maximizedProperty().addListener((obs, was, is) -> updateMaximiseIcon());
        return bar;
    }

    /**
     * WHAT: Loads the app icon into a fixed-size holder, falling back to an empty
     * box when the image is missing.
     * WHY: The title bar should still lay out correctly if the image resource is
     * absent from the build.
     * CALLED BY: buildTitleBar().
     */
    private Node buildLogo(double size) {
        // Pin all three sizes so the holder cannot grow or shrink around the image.
        StackPane holder = new StackPane();
        holder.setPrefSize(size, size);
        holder.setMinSize(size, size);
        holder.setMaxSize(size, size);

        // Read the image from the classpath, not from disk, so it also works when
        // the app is packaged into a jar.
        try (InputStream in = WindowFrame.class.getResourceAsStream("/images/app-icon-64.png")) {
            if (in != null) {
                ImageView view = new ImageView(new Image(in));
                view.setPreserveRatio(true);
                view.setSmooth(true);
                view.setFitWidth(size);
                view.setFitHeight(size);
                holder.getChildren().add(view);
            }
        } catch (Exception ignored) {
            // Decorative only.
        }
        return holder;
    }

    /**
     * WHAT: Creates one flat title-bar button around an SVG icon.
     * WHY: All three window buttons must share the same size, hover styling and
     * tooltip, and must stay out of the keyboard tab order.
     * CALLED BY: buildTitleBar(), for minimise, maximise and close.
     */
    private Button windowButton(SVGPath icon, String tooltip) {
        icon.getStyleClass().add("win-icon");
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("win-btn");
        // Excluding the buttons from focus traversal keeps Tab inside the page content.
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        button.setMinWidth(46);
        button.setPrefWidth(46);
        button.setMinHeight(42);
        button.setPrefHeight(42);
        return button;
    }

    /**
     * WHAT: Builds an SVG path with a transparent fill.
     * WHY: The glyph is drawn by the CSS stroke, so the fill must not paint over it.
     * CALLED BY: buildTitleBar() for the minimise and close icons.
     */
    private static SVGPath icon(String content) {
        SVGPath shape = new SVGPath();
        shape.setContent(content);
        shape.setFill(javafx.scene.paint.Color.TRANSPARENT);
        return shape;
    }

    // ============================================================
    // PART: Window controls
    // ============================================================

    /**
     * WHAT: Flips the stage between maximised and restored.
     * WHY: Both the maximise button and a title-bar double-click need the same
     * behaviour, and the stage owns the real state.
     * CALLED BY: The maximise button handler and the title-bar double-click handler.
     */
    private void toggleMaximise() {
        stage.setMaximized(!stage.isMaximized());
    }

    /**
     * WHAT: Swaps the maximise glyph between "maximise" and "restore".
     * WHY: Without it the button would keep showing the wrong symbol after the
     * window state changes.
     * CALLED BY: buildTitleBar() at startup, and the maximizedProperty listener.
     */
    private void updateMaximiseIcon() {
        maximiseIcon.setContent(stage.isMaximized()
                ? "M4 6 H12 V14 H4 Z M6 6 V2 H14 V10 H12"
                : "M2.5 2.5 H13.5 V13.5 H2.5 Z");
    }

    // ------------------------------------------------------------------
    // Edge resizing
    // ------------------------------------------------------------------
    // ============================================================
    // PART: Edge resizing
    // ============================================================

    /**
     * WHAT: Shows the resize cursor that matches the edge under the pointer.
     * WHY: A frameless window gives no visual hint that its border can be dragged,
     * so the cursor is the only affordance the user gets.
     * CALLED BY: The MOUSE_MOVED filter registered in install().
     */
    private void onMouseMoved(MouseEvent e) {
        if (stage.isMaximized()) {
            // A maximised window has no draggable border.
            applyCursor(Cursor.DEFAULT);
            return;
        }
        applyCursor(cursorFor(zoneAt(e)));
    }

    /**
     * WHAT: Records which edge was grabbed and the window geometry at that moment.
     * WHY: Resizing is computed from the size and position at press time, so the
     * window does not drift while the pointer moves.
     * CALLED BY: The MOUSE_PRESSED filter registered in install().
     */
    private void onMousePressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || stage.isMaximized()) {
            return;
        }
        Zone hit = zoneAt(e);
        if (hit == Zone.NONE) {
            return;
        }
        // Step 1: remember the grabbed edge and the pointer's starting point.
        zone = hit;
        pressScreenX = e.getScreenX();
        pressScreenY = e.getScreenY();
        // Step 2: remember the window geometry, which is the base for all the maths.
        startX = stage.getX();
        startY = stage.getY();
        startWidth = stage.getWidth();
        startHeight = stage.getHeight();
        // Consuming stops the press from also reaching the control underneath.
        e.consume();
    }

    /**
     * WHAT: Resizes and repositions the stage while an edge is being dragged.
     * WHY: This is the replacement for the native resize border.
     * CALLED BY: The MOUSE_DRAGGED filter registered in install().
     */
    private void onMouseDragged(MouseEvent e) {
        if (zone == Zone.NONE || stage.isMaximized()) {
            return;
        }
        // How far the pointer has travelled since the press.
        double dx = e.getScreenX() - pressScreenX;
        double dy = e.getScreenY() - pressScreenY;

        double x = startX;
        double y = startY;
        double w = startWidth;
        double h = startHeight;

        // Dragging the left edge moves the origin as well as the width; dragging
        // the right edge only changes the width. The MIN_WIDTH clamp stops the
        // window collapsing, and the origin correction keeps the opposite edge still.
        if (zone.left) {
            w = Math.max(MIN_WIDTH, startWidth - dx);
            x = startX + (startWidth - w);
        } else if (zone.right) {
            w = Math.max(MIN_WIDTH, startWidth + dx);
        }

        // The same idea vertically, with the top edge moving the origin.
        if (zone.top) {
            h = Math.max(MIN_HEIGHT, startHeight - dy);
            y = startY + (startHeight - h);
        } else if (zone.bottom) {
            h = Math.max(MIN_HEIGHT, startHeight + dy);
        }

        stage.setX(x);
        stage.setY(y);
        stage.setWidth(w);
        stage.setHeight(h);
        e.consume();
    }

    /**
     * WHAT: Works out which edge or corner the pointer is over.
     * WHY: Press, drag and hover all need the same hit test; corners are checked
     * before edges so they win when both are within the margin.
     * CALLED BY: onMouseMoved() and onMousePressed().
     */
    private Zone zoneAt(MouseEvent e) {
        // Scene coordinates are used here because only the pointer position
        // inside the window matters; the window itself is not moving yet.
        double x = e.getSceneX();
        double y = e.getSceneY();
        double w = getScene() == null ? getWidth() : getScene().getWidth();
        double h = getScene() == null ? getHeight() : getScene().getHeight();

        boolean left = x <= RESIZE_MARGIN;
        boolean right = x >= w - RESIZE_MARGIN;
        boolean top = y <= RESIZE_MARGIN;
        boolean bottom = y >= h - RESIZE_MARGIN;

        if (left && top) return Zone.TOP_LEFT;
        if (right && top) return Zone.TOP_RIGHT;
        if (left && bottom) return Zone.BOTTOM_LEFT;
        if (right && bottom) return Zone.BOTTOM_RIGHT;
        if (left) return Zone.LEFT;
        if (right) return Zone.RIGHT;
        if (top) return Zone.TOP;
        if (bottom) return Zone.BOTTOM;
        return Zone.NONE;
    }

    /**
     * WHAT: Applies a cursor to the scene, skipping the call when it is unchanged.
     * WHY: Setting the same cursor on every mouse move is wasted work and can
     * flicker on some platforms.
     * CALLED BY: onMouseMoved().
     */
    private void applyCursor(Cursor cursor) {
        if (getScene() != null && getScene().getCursor() != cursor) {
            getScene().setCursor(cursor);
        }
    }

    /**
     * WHAT: Maps a resize zone to the matching directional cursor.
     * WHY: Keeps the cursor choice in one place instead of repeating it in the
     * mouse handler.
     * CALLED BY: onMouseMoved().
     */
    private static Cursor cursorFor(Zone zone) {
        return switch (zone) {
            case LEFT, RIGHT -> Cursor.H_RESIZE;
            case TOP, BOTTOM -> Cursor.V_RESIZE;
            case TOP_LEFT, BOTTOM_RIGHT -> Cursor.NW_RESIZE;
            case TOP_RIGHT, BOTTOM_LEFT -> Cursor.NE_RESIZE;
            case NONE -> Cursor.DEFAULT;
        };
    }

    /**
     * Edge or corner currently under the pointer.
     *
     * <p>Each constant carries four flags so the resize maths can ask
     * "is this the left edge?" without a chain of equality checks.</p>
     */
    private enum Zone {
        NONE(false, false, false, false),
        LEFT(true, false, false, false),
        RIGHT(false, true, false, false),
        TOP(false, false, true, false),
        BOTTOM(false, false, false, true),
        TOP_LEFT(true, false, true, false),
        TOP_RIGHT(false, true, true, false),
        BOTTOM_LEFT(true, false, false, true),
        BOTTOM_RIGHT(false, true, false, true);

        // One flag per side; onMouseDragged() reads them directly.
        final boolean left;
        final boolean right;
        final boolean top;
        final boolean bottom;

        Zone(boolean left, boolean right, boolean top, boolean bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
    }
}
