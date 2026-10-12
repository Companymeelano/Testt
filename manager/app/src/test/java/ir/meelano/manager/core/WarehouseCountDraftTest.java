package ir.meelano.manager.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WarehouseCountDraftTest {
    @Test public void roundTripPreservesSessionAndCountLines() throws Exception {
        JSONArray lines = new JSONArray();
        lines.put(new JSONObject()
                .put(WarehouseWriter.L_SHKA, "K-17")
                .put(WarehouseWriter.L_NAKA, "کالای آزمایشی")
                .put(WarehouseWriter.L_STOCK, 12.0)
                .put(WarehouseWriter.L_COUNTED, 10.5)
                .put("reason", "قفسهٔ دوم"));

        String raw = WarehouseCountDraft.encode("۱۴۰۵/۰۷/۰۶", "4", "انبار مرکزی", lines, 123456L);
        WarehouseCountDraft.State state = WarehouseCountDraft.decode(raw, "1405/07/07");

        assertEquals("1405/07/06", state.day);
        assertEquals("4", state.anbarId);
        assertEquals("انبار مرکزی", state.anbarName);
        assertEquals(123456L, state.updatedAt);
        assertEquals(1, state.lines.length());
        assertEquals("K-17", state.lines.getJSONObject(0).getString(WarehouseWriter.L_SHKA));
        assertEquals(10.5, state.lines.getJSONObject(0).getDouble(WarehouseWriter.L_COUNTED), 0.0001);
        assertTrue(state.hasWork());
    }

    @Test public void corruptAndUnsupportedDraftsFallBackSafely() {
        WarehouseCountDraft.State corrupt = WarehouseCountDraft.decode("{broken", "1405/07/06");
        assertEquals("1405/07/06", corrupt.day);
        assertEquals(0, corrupt.lines.length());
        assertFalse(corrupt.hasWork());

        WarehouseCountDraft.State future = WarehouseCountDraft.decode(
                "{\"schemaVersion\":99,\"lines\":[{}]}", "1405/07/06");
        assertEquals(0, future.lines.length());
        assertFalse(future.hasWork());
    }

    @Test public void encodingDefensivelyCopiesLines() throws Exception {
        JSONArray lines = new JSONArray();
        lines.put(new JSONObject().put(WarehouseWriter.L_SHKA, "before"));
        String raw = WarehouseCountDraft.encode("1405/07/06", "", "", lines, 1);
        lines.getJSONObject(0).put(WarehouseWriter.L_SHKA, "after");

        WarehouseCountDraft.State state = WarehouseCountDraft.decode(raw, "1405/07/07");
        assertEquals("before", state.lines.getJSONObject(0).getString(WarehouseWriter.L_SHKA));
    }
}
