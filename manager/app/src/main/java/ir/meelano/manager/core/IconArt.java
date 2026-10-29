package ir.meelano.manager.core;

/**
 * The naming rule of the 3D artwork.
 *
 * Every 3D picture has a name that starts with {@code icon3d_}. The nut look uses the emerald
 * set, the dark look its own set and the ivory look the gold set, so the artwork of a section
 * is chosen by the theme. The emerald twin of a picture is the same subject in the emerald set:
 * {@code icon3d_medal_sales} becomes {@code icon3d_emerald_sales} and
 * {@code icon3d_customers} becomes {@code icon3d_emerald_customers}.
 *
 * The rule lives here, free of Android types, so the unit tests can prove that every section
 * of the workspace really has an artwork in the skin the user picked.
 */
public final class IconArt {
    private IconArt() { }

    /** The name of the emerald twin of a 3D picture, or "" when the picture has none. */
    public static String emeraldName(String base) {
        if (base == null) return "";
        if (base.startsWith("icon3d_medal_"))
            return "icon3d_emerald_" + base.substring("icon3d_medal_".length());
        if (base.startsWith("icon3d_"))
            return "icon3d_emerald_" + base.substring("icon3d_".length());
        return "";
    }

    /** True when the name belongs to the emerald set of the nut look. */
    public static boolean isEmerald(String name) {
        return name != null && name.startsWith("icon3d_emerald_");
    }

    /** True when the name belongs to the 3D artwork (not a flat vector). */
    public static boolean isArtwork(String name) {
        return name != null && name.startsWith("icon3d_");
    }
}
