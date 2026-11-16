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
 *
 * <p>Part of: Presentation support, shared by every screen that shows a book.<br>
 * Used by: BookFormView (save, delete, nodeFromFile, node), CatalogView (node,
 * delete), BookCard, MyLoansView and CirculationView (node).<br>
 * Key rules: the database holds only the file name, never a full path, so the
 * project folder can be moved; deleting a cover must never throw; a missing or
 * unreadable image is replaced by the generated jacket, not by an error.</p>
 */
public final class Covers {

    // ============================================================
    // PART: Storage location and palette
    // ============================================================

    /** Folder that holds the cover files, relative to the working directory. */
    public static final Path DIR = Paths.get("covers");

    /**
     * Background colour pairs for generated jackets, each entry being
     * {darker start, lighter end} of the gradient.
     */
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

    /** WHAT: Blocks instantiation. WHY: the class is a set of static helpers over one folder. */
    private Covers() {
    }

    // ============================================================
    // PART: Saving and deleting files
    // ============================================================

    /**
     * WHAT: Copies a user-selected image into covers/ and returns the stored filename.
     * WHY: The form needs a short value that fits the database column, and the
     * original file may later be moved or deleted by the user.
     * CALLED BY: BookFormView, when a new cover is picked for a book.
     */
    public static String save(File source, String titleHint) throws IOException {
        // No file chosen: the book keeps its current cover state.
        if (source == null) {
            return null;
        }

        // Step 1: make sure the folder exists before writing into it.
        Files.createDirectories(DIR);

        // Step 2: build a readable, unique name: title slug, timestamp, suffix.
        // The timestamp stops two books with the same title from overwriting
        // each other.
        String extension = extensionOf(source.getName());
        String base = slug(titleHint);
        String fileName = base + "-" + System.currentTimeMillis() + extension;

        // Step 3: copy the bytes. The caller keeps its own copy of the file.
        Files.copy(source.toPath(), DIR.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    /**
     * WHAT: Removes a cover file from covers/ if it exists.
     * WHY: Replacing or deleting a book should not leave orphan images behind.
     * CALLED BY: BookFormView (cover replaced or cleared) and CatalogView
     * (book deleted).
     */
    public static void delete(String fileName) {
        // A blank name means there was never a cover.
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(DIR.resolve(fileName));
        } catch (IOException ignored) {
            // A missing cover must never break the application.
        }
    }

    // ============================================================
    // PART: Building the node shown on screen
    // ============================================================

    /**
     * WHAT: A fixed-size, rounded cover for a book (real image or generated jacket).
     * WHY: Cards, tables and the form preview all need the same shape, so the
     * size is passed in and the drawing rules stay here.
     * CALLED BY: BookCard, CatalogView, MyLoansView, CirculationView and
     * BookFormView.
     */
    public static StackPane node(Book book, double width, double height) {
        // Step 1: try the stored file. A null book or blank name gives null.
        Image image = image(book == null ? null : book.getCoverFile());

        // Step 2: a real image is cropped to fill; otherwise draw a jacket.
        if (image != null) {
            return framed(fill(image, width, height), width, height);
        }
        return framed(placeholder(book, width, height), width, height);
    }

    /**
     * WHAT: Preview of a file the user just picked, before it is saved.
     * WHY: The form shows the chosen picture immediately, without copying it
     * into covers/ until the book is saved.
     * CALLED BY: BookFormView, after the file chooser closes.
     */
    public static StackPane nodeFromFile(File file, double width, double height) {
        Image image = image(file);
        if (image != null) {
            return framed(fill(image, width, height), width, height);
        }
        // Nothing to show yet, so fall back to the generated jacket.
        return placeholder(null, width, height);
    }

    /**
     * WHAT: Puts the content in a fixed-size box and rounds its corners.
     * WHY: Without the clip, a cropped image would spill past the rounded
     * border and look like a rectangle.
     * CALLED BY: {@link #node} and {@link #nodeFromFile}.
     */
    private static StackPane framed(Node content, double width, double height) {
        // Step 1: fix all three sizes so a layout pass cannot stretch the cover.
        StackPane frame = new StackPane(content);
        frame.setPrefSize(width, height);
        frame.setMinSize(width, height);
        frame.setMaxSize(width, height);

        // Step 2: clip to a rounded rectangle of the same size.
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        frame.setClip(clip);
        return frame;
    }

    /**
     * WHAT: Scales the image to cover the frame and centre-crops the overflow.
     * WHY: Stretching would distort faces and lettering; cropping keeps the
     * proportions and fills the whole box.
     * CALLED BY: {@link #node} and {@link #nodeFromFile}, for real images only.
     */
    private static ImageView fill(Image image, double width, double height) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(false);
        view.setSmooth(true);
        view.setFitWidth(width);
        view.setFitHeight(height);

        // Step 1: compare the shape of the frame with the shape of the image.
        double targetRatio = width / height;
        double imageRatio = image.getWidth() / image.getHeight();

        // Step 2: a wider image loses its left and right edges; a taller image
        // loses its top and bottom. Either way the viewport is centred.
        if (imageRatio > targetRatio) {
            double viewWidth = image.getHeight() * targetRatio;
            view.setViewport(new Rectangle2D((image.getWidth() - viewWidth) / 2, 0, viewWidth, image.getHeight()));
        } else {
            double viewHeight = image.getWidth() / targetRatio;
            view.setViewport(new Rectangle2D(0, (image.getHeight() - viewHeight) / 2, image.getWidth(), viewHeight));
        }
        return view;
    }

    // ============================================================
    // PART: Drawing the generated jacket
    // ============================================================

    /**
     * WHAT: Draws a fake book jacket from the title, author and category.
     * WHY: A catalog full of empty grey boxes looks broken; the jacket keeps
     * the grid readable and still identifies the book.
     * CALLED BY: {@link #node} when there is no usable image, and
     * {@link #nodeFromFile} when the picked file cannot be read.
     */
    private static StackPane placeholder(Book book, double width, double height) {
        // Step 1: fall back to neutral text for a missing book or blank fields.
        String title = book == null || book.getTitle() == null ? "Untitled" : book.getTitle();
        String author = book == null || book.getAuthor() == null ? "DEIHS Library" : book.getAuthor();
        String category = book == null || book.getCategory() == null ? "Library" : book.getCategory();

        // Step 2: pick the gradient from the title. The same title therefore
        // always gets the same colours, which helps recognition.
        String[] colors = PALETTE[Math.abs(title.hashCode()) % PALETTE.length];

        StackPane pane = new StackPane();
        pane.setPrefSize(width, height);
        pane.setMinSize(width, height);
        pane.setMaxSize(width, height);
        pane.setStyle("-fx-background-color: linear-gradient(to bottom right, " + colors[0] + ", " + colors[1] + ");");

        // Step 3: a light strip on the left edge suggests a book spine.
        Region spine = new Region();
        spine.setPrefWidth(Math.max(5, width * 0.045));
        spine.setMaxWidth(Math.max(5, width * 0.045));
        spine.setStyle("-fx-background-color: rgba(255,255,255,0.22);");
        StackPane.setAlignment(spine, Pos.CENTER_LEFT);

        // Step 4: a soft highlight in the top-right corner adds depth.
        Region glow = new Region();
        glow.setStyle("-fx-background-color: radial-gradient(center 80% 15%, radius 65%, "
                + "rgba(255,255,255,0.20), rgba(255,255,255,0));");
        glow.setMouseTransparent(true);

        // Step 5: the three text labels. Font sizes grow with the cover width
        // so the same code works for a 34 px thumbnail and a 174 px preview.
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

        // Step 6: stack the labels, then the school mark, and lay the three
        // layers over the gradient in back-to-front order.
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

    // ============================================================
    // PART: Loading and naming helpers
    // ============================================================

    /**
     * WHAT: Loads a cover from covers/ by its stored file name.
     * WHY: The name comes from the database and may point at a file that was
     * removed by hand, so every failure is turned into null.
     * CALLED BY: {@link #node}.
     */
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

    /**
     * WHAT: Loads an image file and returns null when it cannot be decoded.
     * WHY: The UI asks one question - "is there a picture?" - and a broken file
     * should behave exactly like no file.
     * CALLED BY: {@link #image(String)} and {@link #nodeFromFile}.
     */
    private static Image image(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        // false means load now, on this thread, so the size is known below.
        Image image = new Image(file.toURI().toString(), false);
        return image.isError() || image.getWidth() <= 0 ? null : image;
    }

    /**
     * WHAT: Returns a safe file extension, including the dot.
     * WHY: The extension is taken from the user's file name, so it must be
     * normalised and capped; anything strange becomes ".jpg".
     * CALLED BY: {@link #save}.
     */
    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return ".jpg";
        }
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        // Six characters covers ".jpeg"; longer values are not real suffixes.
        return ext.length() > 6 ? ".jpg" : ext;
    }

    /**
     * WHAT: Turns a title into a short, filesystem-safe name.
     * WHY: The stored name should be recognisable in the covers/ folder and
     * must not contain spaces or characters that upset different systems.
     * CALLED BY: {@link #save}.
     */
    private static String slug(String text) {
        String source = text == null || text.isBlank() ? "cover" : text;
        // Step 1: lowercase, then replace every run of unsupported characters
        // with a single dash. Only a-z and 0-9 survive, so accented letters are
        // dropped rather than transliterated.
        String slug = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        // Step 2: a title made only of symbols leaves nothing behind.
        if (slug.isEmpty()) {
            slug = "cover";
        }

        // Step 3: keep the name short enough for any filesystem.
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }
}
