package ir.meelano.windowsinvoice;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Hands an exported statement back to the operating system without a messaging-service dependency. */
public final class WindowsShareHandoff {
    private WindowsShareHandoff() { }

    public static void reveal(Path file) throws IOException {
        if (file == null || !Files.exists(file)) throw new IOException("فایل خروجی پیدا نشد.");
        Path absolute = file.toAbsolutePath().normalize();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            try {
                new ProcessBuilder("explorer.exe", "/select," + absolute).start();
                return;
            } catch (IOException ignored) {
                // Fall through to opening the containing folder through Desktop when Explorer isn't on PATH.
            }
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(absolute.getParent().toFile());
            return;
        }
        throw new IOException("باز کردن پوشه با فایل‌منیجر سیستم پشتیبانی نمی‌شود.");
    }
}
