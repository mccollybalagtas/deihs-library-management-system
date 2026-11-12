package com.deihs.library.util;

import com.deihs.library.model.Book;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Book-cover storage and rendering.
 *
 * <p>Cover images live in the project's {@code covers/} folder; the database
 * only stores the generated filename. When a book has no cover the UI draws an
 * attractive generated jacket instead, so the catalog never shows a blank box.</p>
 */
public final class Covers {

    public static final Path DIR = Paths.get("covers");

    private static final String[][] PALETTE = {
            {"#1F4E78", "#2D7DD2"},
            {"#5B2C6F", "#9B59B6"},
            {"#922B21", "#E67E22"},
            {"#0F5132", "#27AE60"},
            {"#14324F", "#1F6F8B"},
            {"#6C3483", "#C0392B"},
            {"#1A5276", "#148F77"},
            {"#4A235A", "#8E44AD"},
            {"#7E5109", "#CA6F1E"},
            {"#1B2631", "#34495E"}
    };

    private Covers() {
    }

    /** Copies a user-selected image into covers/ and returns the stored filename. */
    public static String save(File source, String titleHint) throws IOException {
        if (source == null) {
            return null;
        }
        Files.createDirectories(DIR);
        String extension = extensionOf(source.getName());
        String base = slug(titleHint);
        String fileName = base + "-" + System.currentTimeMillis() + extension;
        Files.copy(source.toPath(), DIR.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    public static void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(DIR.resolve(fileName));
        } catch (IOException ignored) {
            // A missing cover must never break the application.
        }
    }

    /** A fixed-size, rounded cover for a book (real image or generated jacket). */
    public static StackPane node(Book book, double width, double height) {
        Image image = image(book == null ? null : book.getCoverFile());
        if (image != null) {
            return framed(fill(image, width, height), width, height);
        }
        return framed(placeholder(book, width, height), width, height);
    }

    /** Preview of a file the user just picked, before it is saved. */
    public static StackPane nodeFromFile(File file, double width, double height) {
        Image image = image(file);
        if (image != null) {
            return framed(fill(image, width, height), width, height);
        }
        return placeholder(null, width, height);
    }

    private static StackPane framed(Node content, double width, double height) {
        StackPane frame = new StackPane(content);
        frame.setPrefSize(width, height);
        frame.setMinSize(width, height);
        frame.setMaxSize(width, height);
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        frame.setClip(clip);
        return frame;
    }

    /** Scales the image to cover the frame and centre-crops the overflow. */
    private static ImageView fill(Image image, double width, double height) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(false);
        view.setSmooth(true);
        view.setFitWidth(width);
        view.setFitHeight(height);

        double targetRatio = width / height;
        double imageRatio = image.getWidth() / image.getHeight();
        if (imageRatio > targetRatio) {
            double viewWidth = image.getHeight() * targetRatio;
            view.setViewport(new Rectangle2D((image.getWidth() - viewWidth) / 2, 0, viewWidth, image.getHeight()));
        } else {
            double viewHeight = image.getWidth() / targetRatio;
            view.setViewport(new Rectangle2D(0, (image.getHeight() - viewHeight) / 2, image.getWidth(), viewHeight));
        }
        return view;
    }

    private static StackPane placeholder(Book book, double width, double height) {
        String title = book == null || book.getTitle() == null ? "Untitled" : book.getTitle();
        String author = book == null || book.getAuthor() == null ? "DEIHS Library" : book.getAuthor();
        String category = book == null || book.getCategory() == null ? "Library" : book.getCategory();

        String[] colors = PALETTE[Math.abs(title.hashCode()) % PALETTE.length];

        StackPane pane = new StackPane();
        pane.setPrefSize(width, height);
        pane.setMinSize(width, height);
        pane.setMaxSize(width, height);
        pane.setStyle("-fx-background-color: linear-gradient(to bottom right, " + colors[0] + ", " + colors[1] + ");");

        Region spine = new Region();
        spine.setPrefWidth(Math.max(5, width * 0.045));
        spine.setMaxWidth(Math.max(5, width * 0.045));
        spine.setStyle("-fx-background-color: rgba(255,255,255,0.22);");
        StackPane.setAlignment(spine, Pos.CENTER_LEFT);

        Region glow = new Region();
        glow.setStyle("-fx-background-color: radial-gradient(center 80% 15%, radius 65%, "
                + "rgba(255,255,255,0.20), rgba(255,255,255,0));");
        glow.setMouseTransparent(true);

        Label categoryLabel = new Label(category.toUpperCase(Locale.ROOT));
        categoryLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.max(6.5, width * 0.045)));
        categoryLabel.setTextFill(Color.web("#FFFFFF", 0.72));

        Label titleLabel = new Label(title);
        titleLabel.setWrapText(true);
        titleLabel.setTextAlignment(TextAlignment.CENTER);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(width - Math.max(20, width * 0.14));
        titleLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.max(9, width * 0.085)));
        titleLabel.setTextFill(Color.WHITE);

        Label authorLabel = new Label(author);
        authorLabel.setWrapText(true);
        authorLabel.setTextAlignment(TextAlignment.CENTER);
        authorLabel.setMaxWidth(width - Math.max(20, width * 0.14));
        authorLabel.setFont(Font.font("SansSerif", Math.max(7.5, width * 0.05)));
        authorLabel.setTextFill(Color.web("#FFFFFF", 0.82));

        Label mark = new Label("DEIHS");
        mark.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.max(6, width * 0.038)));
        mark.setTextFill(Color.web("#FFFFFF", 0.55));

        VBox text = new VBox(6, categoryLabel, titleLabel, authorLabel);
        text.setAlignment(Pos.CENTER);
        text.setPadding(new Insets(10, 12, 10, 14));
        text.setMaxWidth(width);

        VBox stack = new VBox(text, mark);
        stack.setAlignment(Pos.CENTER);
        stack.setSpacing(10);
        stack.setPadding(new Insets(8, 6, 10, 8));

        pane.getChildren().addAll(glow, spine, stack);
        return pane;
    }

    private static Image image(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path path = DIR.resolve(fileName);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return image(path.toFile());
    }

    private static Image image(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        Image image = new Image(file.toURI().toString(), false);
        return image.isError() || image.getWidth() <= 0 ? null : image;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return ".jpg";
        }
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        return ext.length() > 6 ? ".jpg" : ext;
    }

    private static String slug(String text) {
        String source = text == null || text.isBlank() ? "cover" : text;
        String slug = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) {
            slug = "cover";
        }
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }
}
