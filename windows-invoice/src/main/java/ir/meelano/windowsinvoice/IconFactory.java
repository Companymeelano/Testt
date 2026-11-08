package ir.meelano.windowsinvoice;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.Map;

/** Consistent, lightweight line icons for the desktop UI. */
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
            Map.entry("settings", "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-1.8 1.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-2.6V20a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1-1.8-1.8.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.6-1H6.2v-2.6h.2a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1 1.8-1.8.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6v-.2H15v.2a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1 1.8 1.8-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v2.6h-.2a1.7 1.7 0 0 0-1.6 1Z")
    );

    private IconFactory() { }

    public static Node icon(String key, double size, Color color) {
        SVGPath svg = new SVGPath();
        svg.setContent(PATHS.getOrDefault(key, PATHS.get("invoice")));
        svg.setFill(Color.TRANSPARENT);
        svg.setStroke(color);
        svg.setStrokeWidth(1.75);
        svg.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        svg.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        svg.setScaleX(size / 24.0);
        svg.setScaleY(size / 24.0);
        svg.setMouseTransparent(true);
        return svg;
    }
}
