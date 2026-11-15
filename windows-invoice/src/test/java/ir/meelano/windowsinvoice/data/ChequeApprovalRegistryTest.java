package ir.meelano.windowsinvoice.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The local approval ledger must survive reloads and never lose a registration. */
class ChequeApprovalRegistryTest {

    @TempDir
    Path dir;

    @Test
    void confirmSurvivesReload() {
        Path file = dir.resolve("reg.tsv");
        ChequeApprovalRegistry.confirm(file, "S1:1001", "مدیر");
        Map<String, ChequeApprovalRegistry.Entry> loaded = ChequeApprovalRegistry.load(file);
        assertTrue(loaded.containsKey("S1:1001"));
        assertEquals("مدیر", loaded.get("S1:1001").user());
        assertTrue(loaded.get("S1:1001").ts() > 0);
    }

    @Test
    void revokeRemovesOnlyThatKey() {
        Path file = dir.resolve("reg.tsv");
        ChequeApprovalRegistry.confirm(file, "S1:1001", "الف");
        ChequeApprovalRegistry.confirm(file, "1002:1002", "ب");
        ChequeApprovalRegistry.revoke(file, "S1:1001");
        Map<String, ChequeApprovalRegistry.Entry> loaded = ChequeApprovalRegistry.load(file);
        assertFalse(loaded.containsKey("S1:1001"));
        assertTrue(loaded.containsKey("1002:1002"));
    }

    @Test
    void keyPrefersSayadAndMissingFileReadsEmpty() {
        assertEquals("S7:1001", ChequeApprovalRegistry.keyOf("1001", " S7 "));
        assertEquals("1001:1001", ChequeApprovalRegistry.keyOf("1001", "  "));
        assertTrue(ChequeApprovalRegistry.load(dir.resolve("nope.tsv")).isEmpty());
    }
}
