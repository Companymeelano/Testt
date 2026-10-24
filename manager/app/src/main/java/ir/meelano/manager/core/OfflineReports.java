package ir.meelano.manager.core;

import android.content.Context;

import ir.meelano.manager.data.Atiran;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.screens.ReportsScreen;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline copy of the report catalog. After the nightly backup, every report is run once on the
 * server with the same default range the reports screen opens with, and the rows are saved on the
 * phone (files dir). In offline mode the reports screen reads these copies instead of the server.
 */
public final class OfflineReports {
    private OfflineReports() { }

    /** Blocking; call from a background thread. Returns how many reports were saved. */
    public static int refresh(Context app) throws Exception {
        Settings s = new Settings(app);
        int saved = 0;
        try (Connection c = Atiran.open(s.effHost(), s.effPort(), s.effDb(), Backup.BACKUP_USER, Backup.BACKUP_PASS)) {
            Meta m = new Meta(c);
            String label = "بکاپ شبانه " + Jalali.faDate(Jalali.todayStr());
            for (ReportCatalog.Spec sp : ReportCatalog.ALL) {
                try {
                    boolean daily = "returns_by_kind".equals(sp.id) || "returns_list".equals(sp.id);
                    Filter f = daily ? Filter.daily() : new Filter();
                    List<Row> rows = ReportsScreen.applyMaps(m, sp.id,
                            Repo.exec(c, ReportCatalog.query(m, sp.id, f)));
                    Map<String, Object> parts = new LinkedHashMap<>();
                    parts.put("rows", rows);
                    CacheStore.saveData(app, ReportsScreen.cacheKey(sp.id, f), label, parts);
                    saved++;
                } catch (Exception e) {
                    Diag.ignored("OfflineReports:" + sp.id, e);
                }
            }
        }
        return saved;
    }
}
