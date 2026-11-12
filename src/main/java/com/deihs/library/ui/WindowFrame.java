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
 */
public class WindowFrame extends BorderPane {

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

    public WindowFrame(Stage stage) {
        this.stage = stage;
        getStyleClass().add("window-frame");
        setTop(buildTitleBar());
        setCenter(contentHost);
    }

    /** Replaces the window contents, keeping the branded title bar in place. */
    public void setContent(Node content) {
        contentHost.getChildren().setAll(content);
    }

    /** Wires the drag / resize / maximise behaviour once the scene exists. */
    public void install(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, this::onMouseMoved);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> zone = Zone.NONE);
    }

    // ------------------------------------------------------------------
    // Title bar
    // ------------------------------------------------------------------

    private HBox buildTitleBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 0, 14));
        bar.setMinHeight(42);
        bar.setPrefHeight(42);

        Node logo = buildLogo(24);

        Label title = new Label("Balagtas DEIHS Library Management System");
        title.getStyleClass().add("title-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

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
        bar.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                toggleMaximise();
            }
        });

        stage.maximizedProperty().addListener((obs, was, is) -> updateMaximiseIcon());
        return bar;
    }

    private Node buildLogo(double size) {
        StackPane holder = new StackPane();
        holder.setPrefSize(size, size);
        holder.setMinSize(size, size);
        holder.setMaxSize(size, size);

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

    private Button windowButton(SVGPath icon, String tooltip) {
        icon.getStyleClass().add("win-icon");
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("win-btn");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        button.setMinWidth(46);
        button.setPrefWidth(46);
        button.setMinHeight(42);
        button.setPrefHeight(42);
        return button;
    }

    private static SVGPath icon(String content) {
        SVGPath shape = new SVGPath();
        shape.setContent(content);
        shape.setFill(javafx.scene.paint.Color.TRANSPARENT);
        return shape;
    }

    private void toggleMaximise() {
        stage.setMaximized(!stage.isMaximized());
    }

    private void updateMaximiseIcon() {
        maximiseIcon.setContent(stage.isMaximized()
                ? "M4 6 H12 V14 H4 Z M6 6 V2 H14 V10 H12"
                : "M2.5 2.5 H13.5 V13.5 H2.5 Z");
    }

    // ------------------------------------------------------------------
    // Edge resizing
    // ------------------------------------------------------------------

    private void onMouseMoved(MouseEvent e) {
        if (stage.isMaximized()) {
            applyCursor(Cursor.DEFAULT);
            return;
        }
        applyCursor(cursorFor(zoneAt(e)));
    }

    private void onMousePressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || stage.isMaximized()) {
            return;
        }
        Zone hit = zoneAt(e);
        if (hit == Zone.NONE) {
            return;
        }
        zone = hit;
        pressScreenX = e.getScreenX();
        pressScreenY = e.getScreenY();
        startX = stage.getX();
        startY = stage.getY();
        startWidth = stage.getWidth();
        startHeight = stage.getHeight();
        e.consume();
    }

    private void onMouseDragged(MouseEvent e) {
        if (zone == Zone.NONE || stage.isMaximized()) {
            return;
        }
        double dx = e.getScreenX() - pressScreenX;
        double dy = e.getScreenY() - pressScreenY;

        double x = startX;
        double y = startY;
        double w = startWidth;
        double h = startHeight;

        if (zone.left) {
            w = Math.max(MIN_WIDTH, startWidth - dx);
            x = startX + (startWidth - w);
        } else if (zone.right) {
            w = Math.max(MIN_WIDTH, startWidth + dx);
        }

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

    private Zone zoneAt(MouseEvent e) {
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

    private void applyCursor(Cursor cursor) {
        if (getScene() != null && getScene().getCursor() != cursor) {
            getScene().setCursor(cursor);
        }
    }

    private static Cursor cursorFor(Zone zone) {
        return switch (zone) {
            case LEFT, RIGHT -> Cursor.H_RESIZE;
            case TOP, BOTTOM -> Cursor.V_RESIZE;
            case TOP_LEFT, BOTTOM_RIGHT -> Cursor.NW_RESIZE;
            case TOP_RIGHT, BOTTOM_LEFT -> Cursor.NE_RESIZE;
            case NONE -> Cursor.DEFAULT;
        };
    }

    /** Edge or corner currently under the pointer. */
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
