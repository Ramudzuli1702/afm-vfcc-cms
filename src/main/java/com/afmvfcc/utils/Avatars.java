package com.afmvfcc.utils;

import com.afmvfcc.db.DatabaseConnection;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Round member profile pictures. Members without a photo get a neutral grey
 * person silhouette (the familiar "no profile picture" placeholder) rather
 * than initials, so every row and profile looks consistent.
 *
 * Photos are copied into the app data folder (member_photos/) when chosen, so
 * they keep working if the original file is moved or deleted.
 */
public class Avatars {

    private static final Map<String, Image> CACHE = new HashMap<>();

    /** A circular avatar of the given diameter for the photo at {@code photoPath} (may be null). */
    public static StackPane of(String photoPath, double size) {
        StackPane holder = new StackPane();
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);

        Image img = load(photoPath, size);
        if (img != null) {
            ImageView iv = new ImageView(img);
            // Centre-crop to a square so portrait/landscape photos aren't squashed
            double w = img.getWidth(), h = img.getHeight(), side = Math.min(w, h);
            iv.setViewport(new Rectangle2D((w - side) / 2, (h - side) / 2, side, side));
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setSmooth(true);
            iv.setClip(new Circle(size / 2, size / 2, size / 2));
            holder.getChildren().add(iv);
        } else {
            Circle bg = new Circle(size / 2, Color.web("#D5D9E2"));
            FontIcon person = new FontIcon("fas-user");
            person.setIconSize((int) Math.round(size * 0.56));
            person.setIconColor(Color.web("#FFFFFF"));
            // Sit the silhouette slightly low and clip it, like a head-and-shoulders crop
            person.setTranslateY(size * 0.08);
            StackPane disc = new StackPane(bg, person);
            disc.setClip(new Circle(size / 2, size / 2, size / 2));
            holder.getChildren().add(disc);
        }
        return holder;
    }

    /**
     * Shows each row's photo beside the column's text (the member's name).
     * {@code photoOf} picks the photo path out of the row item.
     */
    public static <T> void nameColumn(javafx.scene.control.TableColumn<T, String> col,
                                      java.util.function.Function<T, String> photoOf) {
        col.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                T row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null || name == null) { setGraphic(null); setText(null); return; }
                javafx.scene.control.Label l = new javafx.scene.control.Label(name);
                javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(10, of(photoOf.apply(row), 28), l);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(box);
                setText(null);
            }
        });
    }

    private static Image load(String photoPath, double size) {
        if (photoPath == null || photoPath.isBlank()) return null;
        File f = new File(photoPath);
        if (!f.isFile()) return null;
        // Decode at 2x the display size (crisp on HiDPI) and reuse across table rows
        int px = (int) Math.ceil(size * 2);
        String key = f.getAbsolutePath() + "@" + px + "#" + f.lastModified();
        return CACHE.computeIfAbsent(key, k -> {
            Image img = new Image(f.toURI().toString(), px, px, true, true, false);
            return img.isError() ? null : img;
        });
    }

    /** Folder where member photos are kept (created on demand). */
    public static Path photoDir() throws IOException {
        Path dir = DatabaseConnection.getConfigDir().resolve("member_photos");
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Copies a chosen photo into the app's photo folder and returns the stored path.
     * {@code stem} only makes the file name recognisable (e.g. the member's name).
     */
    public static String storePhoto(File source, String stem) throws IOException {
        String name = source.getName();
        String ext  = name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase() : ".jpg";
        String safe = (stem == null || stem.isBlank() ? "member" : stem).replaceAll("[^A-Za-z0-9]+", "_");
        Path target = photoDir().resolve(safe + "_" + System.currentTimeMillis() + ext);
        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toAbsolutePath().toString();
    }
}
