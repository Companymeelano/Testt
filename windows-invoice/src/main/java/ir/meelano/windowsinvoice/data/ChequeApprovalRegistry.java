package ir.meelano.windowsinvoice.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * دفترچهٔ محلی «تایید سامانهٔ چک‌ها» برای کلاینت ویندوز — ثبت مستقیم داخل برنامه.
 *
 * <p>فایلی کنار پروفایل اتصال (LocalAppData/MEELANO/WindowsInvoice) که برای هر کلید چک،
 * مهرزمان و نام کاربر ثبت‌کننده را نگه می‌دارد. فقط‌خواندنی بودن بخش‌های مدیریتی دست‌نخورده
 * می‌ماند: این دفترچه لایهٔ جداگانهٔ برنامه است و هیچ نوشتنی به SQL Server نمی‌فرستد.</p>
 *
 * <p>قالب سادهٔ خطی (کلید، مهرزمان، کاربر با جداکنندهٔ تب) است تا بدون هیچ کتابخانهٔ بیرونی
 * خوانده و نوشته شود؛ خواندن‌ها با مهرِ زمانِ فایل کش می‌شوند تا جدول‌های چند هزار ردیفی
 * برای هر سلول به دیسک نزنند.</p>
 */
public final class ChequeApprovalRegistry {

    /** یک ثبت تایید: مهرزمان (ms) و نام کاربر. */
    public record Entry(long ts, String user) { }

    private static volatile long cachedModified = -1L;
    private static volatile Map<String, Entry> cached = Map.of();

    private ChequeApprovalRegistry() { }

    /** مسیر پیش‌فرض دفترچه، کنار پروفایل اتصال ویندوز. */
    public static Path defaultPath() {
        String local = System.getenv("LOCALAPPDATA");
        Path dir = local != null && !local.isBlank()
                ? Path.of(local, "MEELANO", "WindowsInvoice")
                : Path.of(System.getProperty("user.home"), ".config", "MEELANO", "WindowsInvoice");
        return dir.resolve("cheque-approval-registry.tsv");
    }

    /** کلید یکتای چک: شناسهٔ صیادی (در صورت وجود) + شمارهٔ چک. */
    public static String keyOf(String num, String sayad) {
        String s = sayad == null ? "" : sayad.trim();
        String n = num == null ? "" : num.trim();
        return (s.isEmpty() ? n : s) + ":" + n;
    }

    public static Map<String, Entry> load(Path path) {
        Map<String, Entry> out = new LinkedHashMap<>();
        if (path == null || !Files.isRegularFile(path)) return out;
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", 3);
                if (p.length < 2 || p[0].isBlank()) continue;
                long ts;
                try {
                    ts = Long.parseLong(p[1].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                out.put(p[0], new Entry(ts, p.length > 2 ? p[2] : ""));
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    /** خواندن کش‌شده بر اساس زمان تغییر فایل. */
    public static Map<String, Entry> load() {
        Path path = defaultPath();
        long modified = Files.isRegularFile(path) ? path.toFile().lastModified() : -1L;
        Map<String, Entry> snapshot = cached;
        if (modified == cachedModified && snapshot != null) return snapshot;
        synchronized (ChequeApprovalRegistry.class) {
            if (modified != cachedModified) {
                cached = load(path);
                cachedModified = modified;
            }
            return cached;
        }
    }

    public static void confirm(Path path, String key, String user) {
        if (key == null || key.isBlank()) return;
        Map<String, Entry> all = new LinkedHashMap<>(load(path));
        all.put(key, new Entry(System.currentTimeMillis(), clean(user)));
        write(path, all);
    }

    public static void confirm(String key, String user) {
        confirm(defaultPath(), key, user);
    }

    public static void revoke(Path path, String key) {
        if (key == null || key.isBlank()) return;
        Map<String, Entry> all = new LinkedHashMap<>(load(path));
        all.remove(key);
        write(path, all);
    }

    public static void revoke(String key) {
        revoke(defaultPath(), key);
    }

    private static String clean(String v) {
        return v == null ? "" : v.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static void write(Path path, Map<String, Entry> all) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Entry> e : all.entrySet()) {
            b.append(clean(e.getKey())).append('\t').append(e.getValue().ts())
                    .append('\t').append(clean(e.getValue().user())).append('\n');
        }
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tmp, b.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            cachedModified = path.toFile().lastModified();
            cached = new HashMap<>(all);
        } catch (IOException ignored) {
            // A missing registry simply reads as «همه تیک‌نخورده»; never crash the report.
        }
    }
}
