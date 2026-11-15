package ir.meelano.windowsinvoice;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.SVGPath;

import java.util.Map;

/** Semantic, layered vector icons with a small bevel and shadow for the desktop UI. */
public final class IconFactory {
    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("invoice", "M7 3.5h7l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z M14 3.5V8h4 M9 12h6 M9 15.5h6 M9 19h3"),
            Map.entry("box", "m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z M4.5 7.5 12 12l7.5-4.5 M12 12v9"),
            Map.entry("users", "M16 20v-1.5a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4V20 M9.5 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7 M17 11a3 3 0 1 0-.4-6 M17 14.5a4 4 0 0 1 4 4V20"),
            Map.entry("search", "M10.7 18a7.3 7.3 0 1 1 0-14.6 7.3 7.3 0 0 1 0 14.6Z M16 16l5 5"),
            Map.entry("barcode", "M4 5v14 M7 5v14 M10 5v14 M13 5v14 M17 5v14 M20 5v14 M3 3h18 M3 21h18"),
            Map.entry("cart", "M3 4h2l2.2 10.3a2 2 0 0 0 2 1.6h8.5a2 2 0 0 0 1.9-1.4L21 8H6 M9 20h.01 M18 20h.01"),
            Map.entry("chart", "M4 19V5 M4 19h17 M7 15l4-4 3 2 5-6 M16 7h3v3"),
            Map.entry("print", "M7 8V3h10v5 M7 17H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-3 M7 14h10v7H7z M18 11h.01"),
            Map.entry("plus", "M12 5v14 M5 12h14"),
            Map.entry("trash", "M4 7h16 M10 11v6 M14 11v6 M6 7l1 14h10l1-14 M9 7V4h6v3"),
            Map.entry("check", "m5 12 4 4L19 6"),
            Map.entry("refresh", "M20 7v5h-5 M4 17v-5h5 M5.5 9A7 7 0 0 1 18 6l2 2 M4 16l2 2a7 7 0 0 0 12.5-3"),
            Map.entry("settings", "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-1.8 1.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-2.6V20a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1-1.8-1.8.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.6-1H6.2v-2.6h.2a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1 1.8-1.8.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6v-.2H15v.2a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1 1.8 1.8-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v2.6h-.2a1.7 1.7 0 0 0-1.6 1Z"),
            Map.entry("calendar", "M5 4h14v17H5z M8 2v4 M16 2v4 M5 9h14 M8 12h2 M14 12h2 M8 16h2 M14 16h2"),
            Map.entry("note", "M6 3h9l4 4v14H6z M15 3v5h4 M9 12h7 M9 16h7 M9 8h2"),
            Map.entry("discount", "M4 8l8-5 8 5v8l-8 5-8-5z M8 9h.01 M16 15h.01 M8 16l8-8"),
            Map.entry("truck", "M3 7h11v10H3z M14 10h4l3 3v4h-7z M7 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4 M18 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4 M14 14h7"),
            Map.entry("percent", "M5 19 19 5 M7.5 9a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3 M16.5 18a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3"),
            Map.entry("clock", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z M12 7v5l3.5 2"),
            Map.entry("warehouse", "M3 9 12 4l9 5v11H3z M7 20v-6h10v6 M7 9h10 M8 11h2 M14 11h2"),
            Map.entry("wallet", "M4 6h16v14H4z M4 9h16 M15 13h5v4h-5a2 2 0 0 1 0-4Z M7 4h11"),
            Map.entry("money", "M4 7c0-2 3-3 8-3s8 1 8 3-3 3-8 3-8-1-8-3Z M4 7v5c0 2 3 3 8 3 M20 7v5 M4 12v5c0 2 3 3 8 3 M12 20c5 0 8-1 8-3v-5 M12 15c5 0 8-1 8-3")
    );

    private static final Map<String, Color> ACCENTS = Map.ofEntries(
            Map.entry("invoice", Color.web("#d7a943")),
            Map.entry("box", Color.web("#42b79a")),
            Map.entry("users", Color.web("#589bd2")),
            Map.entry("search", Color.web("#8b79d0")),
            Map.entry("barcode", Color.web("#34aabf")),
            Map.entry("cart", Color.web("#dd8058")),
            Map.entry("chart", Color.web("#329c7a")),
            Map.entry("print", Color.web("#658fc8")),
            Map.entry("plus", Color.web("#d5a640")),
            Map.entry("trash", Color.web("#cf5e59")),
            Map.entry("check", Color.web("#45a66c")),
            Map.entry("refresh", Color.web("#51a8ae")),
            Map.entry("settings", Color.web("#8a9a91")),
            Map.entry("calendar", Color.web("#738bd0")),
            Map.entry("note", Color.web("#7d8eae")),
            Map.entry("discount", Color.web("#ca8051")),
            Map.entry("truck", Color.web("#4a9cb2")),
            Map.entry("percent", Color.web("#b186ce")),
            Map.entry("clock", Color.web("#7e91ab")),
            Map.entry("warehouse", Color.web("#4e9b7b")),
            Map.entry("wallet", Color.web("#d1a53d")),
            Map.entry("money", Color.web("#39a77e"))
    );

    private IconFactory() { }

    /** Builds a crisp vector glyph with an offset extrusion, metallic face gradient and soft cast shadow. */
    public static Node icon(String key, double size, Color requestedColor) {
        String content = PATHS.getOrDefault(key, PATHS.get("invoice"));
        Color accent = ACCENTS.getOrDefault(key, Color.web("#6d9b7c"));
        Color face = requestedColor == null ? accent : requestedColor.interpolate(accent.brighter(), 0.28);
        LinearGradient bevel = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, face.brighter()),
                new Stop(0.48, face),
                new Stop(1, accent.darker()));

        Group layers = new Group(
                layer(content, accent.deriveColor(0, 0.85, 0.52, 0.92), 3.1, 0.7, 1.1, 0.94),
                layer(content, bevel, 1.95, 0, 0, 1),
                layer(content, Color.WHITE.deriveColor(0, 0.4, 1, 0.58), 0.68, -0.25, -0.42, 0.68)
        );
        layers.setScaleX(size / 24.0);
        layers.setScaleY(size / 24.0);
        layers.setMouseTransparent(true);

        DropShadow cast = new DropShadow();
        cast.setBlurType(BlurType.GAUSSIAN);
        cast.setColor(accent.deriveColor(0, 0.85, 0.5, 0.40));
        cast.setRadius(Math.max(1.1, size * 0.09));
        cast.setSpread(0.08);
        cast.setOffsetX(0.35);
        cast.setOffsetY(0.8);
        layers.setEffect(cast);

        StackPane icon = new StackPane(layers);
        icon.setAlignment(Pos.CENTER);
        icon.setMinSize(size, size);
        icon.setPrefSize(size, size);
        icon.setMaxSize(size, size);
        icon.setPickOnBounds(false);
        icon.setMouseTransparent(true);
        return icon;
    }

    private static SVGPath layer(String content, Paint stroke, double width,
                                 double offsetX, double offsetY, double opacity) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.setFill(Color.TRANSPARENT);
        path.setStroke(stroke);
        path.setStrokeWidth(width);
        path.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        path.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        path.setTranslateX(offsetX);
        path.setTranslateY(offsetY);
        path.setOpacity(opacity);
        path.setMouseTransparent(true);
        return path;
    }
}
