package ir.meelano.windowsinvoice.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * دفترچهٔ محلی پیش‌فاکتورهای خرید روی ویندوز — یک سند بسته‌بندی‌شده در هر خط پروندهٔ
 * {@code Meelano/preinvoice-ledger.txt} زیر پوشهٔ کاربر؛ بدون هیچ نوشتنی به SQL Server.
 */
public final class PurchasePreinvoiceStoreWin {

    private PurchasePreinvoiceStoreWin() { }

    public static Path ledgerPath() {
        return Paths.get(System.getProperty("user.home", "."), "Meelano", "preinvoice-ledger.txt");
    }

    public static List<PurchasePreinvoiceEngine.Draft> load() {
        List<PurchasePreinvoiceEngine.Draft> out = new ArrayList<>();
        Path p = ledgerPath();
        if (!Files.exists(p)) return out;
        try {
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) continue;
                PurchasePreinvoiceEngine.Draft d = PurchasePreinvoiceEngine.unpack(line.trim());
                if (d != null) out.add(d);
            }
        } catch (IOException e) {
            return out;
        }
        out.sort((a, b) -> Long.compare(b.savedAt, a.savedAt));
        return out;
    }

    public static void save(PurchasePreinvoiceEngine.Draft draft) throws IOException {
        if (draft.id == null || draft.id.isEmpty()) draft.id = "pi" + System.currentTimeMillis();
        draft.savedAt = System.currentTimeMillis();
        Path p = ledgerPath();
        Files.createDirectories(p.getParent());
        List<String> lines = new ArrayList<>();
        if (Files.exists(p)) lines.addAll(Files.readAllLines(p, StandardCharsets.UTF_8));
        String packed = PurchasePreinvoiceEngine.pack(draft);
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("v1|" + draft.id + "|")) { lines.set(i, packed); replaced = true; }
        }
        if (!replaced) lines.add(packed);
        Files.write(p, lines, StandardCharsets.UTF_8);
    }

    public static void delete(String id) throws IOException {
        Path p = ledgerPath();
        if (!Files.exists(p)) return;
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            if (!line.startsWith("v1|" + id + "|")) lines.add(line);
        }
        Files.write(p, lines, StandardCharsets.UTF_8);
    }
}
