package ir.meelano.manager.core;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.data.Repo;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

/**
 * Retry worker for warehouse work that must survive a lost connection. Handover marks stay
 * in a device-local outbox until the shared Meelano table confirms them; blind-count drafts
 * stay in WarehouseWriter until the manager-review side table confirms receipt.
 */
public final class WarehouseSync {
    private WarehouseSync() { }

    public interface Listener {
        void done(int syncedHandovers, int syncedCounts, int pending, String error);
    }

    private static final class Result {
        final List<String[]> handovers = new ArrayList<>();
        final List<Integer> counts = new ArrayList<>();
        int failed;
    }

    public static int pendingCount(MainActivity a) {
        int n = WarehouseWriter.pendingHandovers(a).size();
        for (JSONObject d : WarehouseWriter.listDrafts(a)) {
            if (isPendingCount(d)) n++;
        }
        return n;
    }

    /** Attempt every pending item once. A failure never deletes local work. */
    public static void retry(final MainActivity a, final Listener listener) {
        final List<JSONObject> hands = WarehouseWriter.pendingHandovers(a);
        final List<JSONObject> counts = preparePendingCounts(a);
        if (hands.isEmpty() && counts.isEmpty()) {
            if (listener != null) listener.done(0, 0, 0, "");
            return;
        }
        a.repo.run(c -> {
            Result r = new Result();
            for (JSONObject e : hands) {
                String day = e.optString("dt", "");
                String no = e.optString("no", "");
                boolean ok = HandoverDb.mark(c, day, no, e.optString("cust", ""),
                        e.optString("receiver", ""), e.optString("worker", ""),
                        e.optString("items", ""), e.optString("user", ""), DeviceId.code(a));
                if (ok) r.handovers.add(new String[]{day, no});
                else r.failed++;
            }
            for (JSONObject d : counts) {
                if (WarehouseCountDb.submit(c, d)) r.counts.add(d.optInt(WarehouseWriter.D_NO, 0));
                else r.failed++;
            }
            return r;
        }, new Repo.Cb<Result>() {
            @Override public void ok(Result r) {
                int handDone = 0;
                int countDone = 0;
                if (r != null) {
                    for (String[] h : r.handovers) {
                        WarehouseWriter.markHandoverSynced(a, h[0], h[1]);
                        handDone++;
                    }
                    for (Integer no : r.counts) {
                        if (no == null || no <= 0) continue;
                        JSONObject d = WarehouseWriter.getDraft(a, no);
                        if (d == null) continue;
                        try {
                            d.put("approvalSync", "done");
                            WarehouseWriter.saveDraft(a, d);
                            countDone++;
                        } catch (Exception ignored) { Diag.ignored("WarehouseSync:78", ignored); }
                    }
                }
                if (listener != null) listener.done(handDone, countDone, pendingCount(a),
                        r != null && r.failed > 0 ? "بعضی موارد هنوز در صف هستند" : "");
            }

            @Override public void fail(String faError) {
                if (listener != null) listener.done(0, 0, pendingCount(a), faError);
            }
        });
    }

    private static List<JSONObject> preparePendingCounts(MainActivity a) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject d : WarehouseWriter.listDrafts(a)) {
            if (!WarehouseWriter.COUNT.equals(d.optString(WarehouseWriter.D_TYPE, ""))
                    || "done".equals(d.optString("approvalSync", ""))) continue;
            if (d.optJSONArray(WarehouseWriter.D_LINES) == null
                    || d.optJSONArray(WarehouseWriter.D_LINES).length() == 0) continue;
            try {
                String id = d.optString("reviewId", "").trim();
                if (id.isEmpty()) {
                    long created = d.optLong(WarehouseWriter.D_CREATED, System.currentTimeMillis());
                    id = DeviceId.code(a) + "-" + created + "-" + d.optInt(WarehouseWriter.D_NO, 0);
                    d.put("reviewId", id);
                    d.put("device", DeviceId.code(a));
                    d.put("approvalSync", "pending");
                    WarehouseWriter.saveDraft(a, d);
                }
                out.add(d);
            } catch (Exception ignored) { Diag.ignored("WarehouseSync:109", ignored); }
        }
        return out;
    }

    private static boolean isPendingCount(JSONObject d) {
        return d != null
                && WarehouseWriter.COUNT.equals(d.optString(WarehouseWriter.D_TYPE, ""))
                && !"done".equals(d.optString("approvalSync", ""))
                && d.optJSONArray(WarehouseWriter.D_LINES) != null
                && d.optJSONArray(WarehouseWriter.D_LINES).length() > 0;
    }
}
