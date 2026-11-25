package com.etoile.shared;

import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DishImageCache {

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private static final Map<String, String> PHOTO_URLS = Map.ofEntries(
            Map.entry("ribeye",         "https://images.unsplash.com/photo-1600891964092-4316c288032e?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("salmon",         "https://images.unsplash.com/photo-1519708227418-c8fd9a32b7a2?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("lamb",           "https://images.unsplash.com/photo-1603360946369-dc9bb6258143?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("duck",           "https://images.unsplash.com/photo-1580476262798-bddd9f4b7369?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("mushroom_pasta", "https://images.unsplash.com/photo-1555949258-eb67b1ef0ceb?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("lobster_pasta",  "https://images.unsplash.com/photo-1563379926898-05f4575a45d8?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("scallops",       "https://images.unsplash.com/photo-1559737558-2f5a35f4523b?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("fries",          "https://images.unsplash.com/photo-1573080496219-bb080dd4f877?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("onion_soup",     "https://images.unsplash.com/photo-1547592166-23ac45744acd?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("burrata",        "https://images.unsplash.com/photo-1540420773420-3366772f4999?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("caesar",         "https://images.unsplash.com/photo-1550304943-4f24f54ddde9?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("fondant",        "https://images.unsplash.com/photo-1624353365286-3f8d62daad51?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("brulee",         "https://images.unsplash.com/photo-1488477181946-6428a0291777?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("red_wine",       "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=800&h=500&q=80"),
            Map.entry("mocktail",       "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?auto=format&fit=crop&w=800&h=500&q=80")
    );

    private static final Map<String, String> CATEGORY_IMAGE_KEYS = Map.of(
            "All",      "ribeye",
            "Starters", "onion_soup",
            "Salads",   "burrata",
            "Mains",    "salmon",
            "Pasta",    "mushroom_pasta",
            "Desserts", "fondant",
            "Wine",     "red_wine"
    );

    private static Image getImage(String imageKey) {
        String key = (imageKey != null && PHOTO_URLS.containsKey(imageKey)) ? imageKey : "ribeye";
        String url = PHOTO_URLS.get(key);
        return CACHE.computeIfAbsent(key, k -> new Image(url, 800, 500, false, true, true));
    }

    /** Full-rounded photo (used in detail drawer & list rows). */
    public static StackPane createRoundedPhoto(String imageKey, String fallbackTitle, double width, double height, double radius) {
        StackPane container = buildBasePhoto(imageKey, fallbackTitle, width, height,
                "-fx-background-radius: " + radius + ";");

        ImageView view = (ImageView) container.getChildren().get(1);
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        view.setClip(clip);
        return container;
    }

    /** Edge-to-edge card header photo: rounded top corners, crisp flat bottom edge. */
    public static StackPane createTopRoundedPhoto(String imageKey, String fallbackTitle, double width, double height, double topRadius) {
        StackPane container = buildBasePhoto(imageKey, fallbackTitle, width, height,
                "-fx-background-radius: " + topRadius + " " + topRadius + " 0 0;");

        ImageView view = (ImageView) container.getChildren().get(1);
        // Extend clip height past the bottom edge so only top-left & top-right are rounded
        Rectangle clip = new Rectangle(0, 0, width, height + topRadius + 8);
        clip.setArcWidth(topRadius * 2);
        clip.setArcHeight(topRadius * 2);
        view.setClip(clip);
        return container;
    }

    /** Crisp circular food thumbnail for category squircles (replaces broken emojis). */
    public static StackPane createCategoryThumb(String category, double diameter) {
        String key = CATEGORY_IMAGE_KEYS.getOrDefault(category, "ribeye");
        Image img = getImage(key);

        ImageView view = new ImageView(img);
        view.setFitWidth(diameter);
        view.setFitHeight(diameter);
        view.setPreserveRatio(false);
        view.setSmooth(true);
        view.setClip(new Circle(diameter / 2.0, diameter / 2.0, diameter / 2.0));

        StackPane holder = new StackPane(view);
        holder.setPrefSize(diameter, diameter);
        holder.setMaxSize(diameter, diameter);
        holder.setEffect(new DropShadow(6, 0, 2, Color.rgb(24, 22, 21, 0.16)));
        return holder;
    }

    private static StackPane buildBasePhoto(String imageKey, String fallbackTitle, double width, double height, String radiusCss) {
        StackPane container = new StackPane();
        container.setPrefSize(width, height);
        container.setMinSize(width, height);
        container.setMaxSize(width, height);
        container.setStyle("-fx-background-color: linear-gradient(to bottom right, #F2EFEA, #E2DBD1); " + radiusCss);

        Label fallback = new Label(fallbackTitle);
        fallback.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #6E5847;");
        fallback.setWrapText(true);

        ImageView view = new ImageView(getImage(imageKey));
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(false);
        view.setSmooth(true);

        container.getChildren().addAll(fallback, view);
        return container;
    }
}