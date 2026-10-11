package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Warehouse write engine (v28, phase 2).
 *
 * The warehouse keeper works quantity-only; the office completes prices
 * later. This engine NEVER guesses the schema: every INSERT is built from
 * the LIVE database shape (INFORMATION_SCHEMA + identity/PK/FK catalogs),
 * identity columns are excluded, vendor defaults win over zeros, single
 * letters and status codes are sampled from live rows, and foreign keys
 * fall back to a live valid value. Anything unmappable is reported in
 * Persian instead of corrupting data. All statements of one document run
 * in a single transaction (all or nothing).
 *
 * Drafts (offline-first): every document is first saved as structured JSON
 * in the app; direct posting to Atiran happens only when the shop switch
 * «ثبت مستقیم انبار» is ON (Settings, admin) — otherwise the draft waits
 * safely on the device.
 */
public final class WarehouseWriter {
    private WarehouseWriter() { }

    // ---- document types ----
    public static final String BUY = "BUY";         // supplier -> warehouse
    public static final String CUSTRET = "CUSTRET"; // customer  -> warehouse
    public static final String BUYRET = "BUYRET";   // warehouse -> supplier
    public static final String COUNT = "COUNT";     // cycle count (draft + report only)

    public static String faType(String t) {
        if (CUSTRET.equals(t)) return "برگشت از مشتری";
        if (BUYRET.equals(t)) return "برگشت از خرید";
        if (COUNT.equals(t)) return "شمارش انبار";
        return "خرید";
    }

    public static String faReceiptTitle(String t) {
        if (CUSTRET.equals(t)) return "رسید برگشت از مشتری";
        if (BUYRET.equals(t)) return "حواله برگشت به تأمین‌کننده";
        return "رسید ورود کالا";
    }

    /** Smart deliverer/receiver wording per document type. */
    public static String faPartyRole(String t) {
        if (CUSTRET.equals(t)) return "تحویل‌دهنده (مشتری / راننده)";
        if (BUYRET.equals(t)) return "تحویل‌گیرنده (تأمین‌کننده)";
        return "تحویل‌دهنده (تأمین‌کننده)";
    }

    // ---- draft JSON keys ----
    public static final String D_TYPE = "type";
    public static final String D_DATE = "date";
    public static final String D_PSHMO = "partyShmo";
    public static final String D_PNAME = "partyName";
    public static final String D_PNEW = "partyNew";   // JSONObject or null
    public static final String D_ANBAR = "anbarId";
    public static final String D_ANBARNAME = "anbarName";
    public static final String D_USER = "user";
    public static final String D_DESC = "desc";
    public static final String D_LINES = "lines";
    public static final String D_NO = "draftNo";
    public static final String D_CREATED = "createdAt";
    public static final String L_SHKA = "shka";
    public static final String L_NAKA = "naka";
    public static final String L_QTY = "qty";
    public static final String L_UNIT = "unit";
    public static final String L_NEW = "isNew";
    public static final String L_NNAME = "newName";
    public static final String L_NUNIT = "newUnit";
    public static final String L_NGROUP = "newGroupId";
    public static final String L_NGROUPNAME = "newGroupName";
    public static final String L_PRICE = "price";       // office completion (Takmil)
    public static final String L_COUNTED = "counted";   // cycle count qty
    public static final String L_STOCK = "stock";       // system stock at count

    // ================= draft store (private prefs) =================

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("meelano_wh", Context.MODE_PRIVATE);
    }

    /** Save (insert or replace by draftNo). Returns the draftNo. */
    public static int saveDraft(Context c, JSONObject doc) {
        try {
            List<JSONObject> all = listDrafts(c);
            int no = doc.optInt(D_NO, 0);
            if (no <= 0) {
                no = prefs(c).getInt("draft_seq", 0) + 1;
                prefs(c).edit().putInt("draft_seq", no).apply();
                doc.put(D_NO, no);
                doc.put(D_CREATED, System.currentTimeMillis());
            }
            boolean replaced = false;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).optInt(D_NO, -1) == no) {
                    all.set(i, doc);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) all.add(0, doc);
            JSONArray arr = new JSONArray();
            for (JSONObject o : all) arr.put(o);
            prefs(c).edit().putString("drafts", arr.toString()).apply();
            return no;
        } catch (Exception e) {
            return 0;
        }
    }

    public static List<JSONObject> listDrafts(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            String raw = prefs(c).getString("drafts", "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(o);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static JSONObject getDraft(Context c, int draftNo) {
        for (JSONObject o : listDrafts(c)) {
            if (o.optInt(D_NO, -1) == draftNo) return o;
        }
        return null;
    }

    public static void deleteDraft(Context c, int draftNo) {
        try {
            List<JSONObject> keep = new ArrayList<>();
            for (JSONObject o : listDrafts(c)) {
                if (o.optInt(D_NO, -1) != draftNo) keep.add(o);
            }
            JSONArray arr = new JSONArray();
            for (JSONObject o : keep) arr.put(o);
            prefs(c).edit().putString("drafts", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ---- handover log (which invoices were handed over, per day) ----

    /** Back-compat form without a worker or an item breakdown. */
    public static void markHanded(Context c, String shfacfo, String cust,
            String receiver, String user) {
        markHanded(c, shfacfo, cust, receiver, user, "", "");
    }

    /**
     * Record a handover: who received it, which worker physically carried the load out and
     * exactly which items were ticked. Everything lands in the local handover log so the
     * warehouse can search past deliveries when a customer disputes one.
     */
    public static void markHanded(Context c, String shfacfo, String cust,
            String receiver, String user, String worker, String items) {
        try {
            String day = Jalali.todayStr();
            String key = "hand_" + day.replace("/", "");
            String cur = prefs(c).getString(key, ",");
            if (!cur.contains("," + shfacfo + ",")) {
                prefs(c).edit().putString(key, cur + shfacfo + ",").apply();
            }
            JSONArray log = new JSONArray(prefs(c).getString("handlog", "[]"));
            JSONObject e = new JSONObject();
            e.put("dt", day);
            e.put("no", shfacfo);
            e.put("cust", cust == null ? "" : cust);
            e.put("receiver", receiver == null ? "" : receiver);
            e.put("user", user == null ? "" : user);
            e.put("worker", worker == null ? "" : worker);
            e.put("items", items == null ? "" : items);
            e.put("ts", System.currentTimeMillis());
            log.put(e);
            while (log.length() > 300) log.remove(0);
            prefs(c).edit().putString("handlog", log.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static boolean handedToday(Context c, String shfacfo) {
        try {
            String day = Jalali.todayStr();
            String cur = prefs(c).getString("hand_" + day.replace("/", ""), ",");
            return cur.contains("," + shfacfo + ",");
        } catch (Exception e) {
            return false;
        }
    }

    // ================= live schema =================

    public static final class Col {
        public String name = "";
        public String type = "";
        public boolean nullable = true;
        public boolean hasDefault;
        public boolean identity;
        public boolean pk;
        /** "table.column" or null. */
        public String fkRef;
    }

    /** Full live shape of one table (empty when the table is missing). */
    public static List<Col> shape(Connection c, String table) {
        Map<String, Col> cols = new LinkedHashMap<>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement("SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT"
                    + " FROM INFORMATION_SCHEMA.COLUMNS"
                    + " WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=? ORDER BY ORDINAL_POSITION");
            ps.setString(1, table);
            rs = ps.executeQuery();
            while (rs.next()) {
                Col col = new Col();
                col.name = rs.getString(1);
                col.type = rs.getString(2) == null ? "" : rs.getString(2).toLowerCase(Locale.US);
                col.nullable = !"NO".equalsIgnoreCase(rs.getString(3));
                col.hasDefault = rs.getString(4) != null;
                cols.put(col.name.toLowerCase(Locale.US), col);
            }
        } catch (Exception ignored) {
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
            rs = null;
            ps = null;
        }
        if (cols.isEmpty()) return new ArrayList<>();
        try {
            ps = c.prepareStatement("SELECT c.name FROM sys.identity_columns ic"
                    + " JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id"
                    + " WHERE ic.object_id=OBJECT_ID('dbo.[" + table.replace("]", "]]") + "]')");
            rs = ps.executeQuery();
            while (rs.next()) {
                Col col = cols.get(rs.getString(1).toLowerCase(Locale.US));
                if (col != null) col.identity = true;
            }
        } catch (Exception ignored) {
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
            rs = null;
            ps = null;
        }
        try {
            ps = c.prepareStatement("SELECT c.name FROM sys.key_constraints kc"
                    + " JOIN sys.index_columns ic ON ic.object_id=kc.parent_object_id AND ic.index_id=kc.unique_index_id"
                    + " JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id"
                    + " WHERE kc.type='PK' AND kc.parent_object_id=OBJECT_ID('dbo.["
                    + table.replace("]", "]]") + "]')");
            rs = ps.executeQuery();
            while (rs.next()) {
                Col col = cols.get(rs.getString(1).toLowerCase(Locale.US));
                if (col != null) col.pk = true;
            }
        } catch (Exception ignored) {
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
            rs = null;
            ps = null;
        }
        try {
            ps = c.prepareStatement("SELECT c.name, OBJECT_NAME(f.referenced_object_id), rc.name"
                    + " FROM sys.foreign_keys f"
                    + " JOIN sys.foreign_key_columns fc ON fc.constraint_object_id=f.object_id"
                    + " JOIN sys.columns c ON c.object_id=fc.parent_object_id AND c.column_id=fc.parent_column_id"
                    + " JOIN sys.columns rc ON rc.object_id=fc.referenced_object_id AND rc.column_id=fc.referenced_column_id"
                    + " WHERE f.parent_object_id=OBJECT_ID('dbo.[" + table.replace("]", "]]") + "]')");
            rs = ps.executeQuery();
            while (rs.next()) {
                Col col = cols.get(rs.getString(1).toLowerCase(Locale.US));
                if (col != null) col.fkRef = rs.getString(2) + "." + rs.getString(3);
            }
        } catch (Exception ignored) {
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
        }
        return new ArrayList<>(cols.values());
    }

    /** Enabled trigger names on the table (for the dry-run warning). */
    public static List<String> triggers(Connection c, String table) {
        List<String> out = new ArrayList<>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement("SELECT name FROM sys.triggers WHERE parent_id=OBJECT_ID('dbo.["
                    + table.replace("]", "]]") + "]') AND is_disabled=0");
            rs = ps.executeQuery();
            while (rs.next()) out.add(rs.getString(1));
        } catch (Exception ignored) {
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
        }
        return out;
    }

    /** Next manual number for a numeric key column (MAX+1). */
    public static long nextNum(Connection c, String table, String col) throws Exception {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement("SELECT ISNULL(MAX([" + col.replace("]", "]]") + "]),0)"
                    + " FROM dbo.[" + table.replace("]", "]]") + "]");
            rs = ps.executeQuery();
            long mx = rs.next() ? rs.getLong(1) : 0;
            return mx + 1;
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
        }
    }

    /** One live non-null sample of a column, or null. */
    public static String liveSample(Connection c, String table, String col) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement("SELECT TOP 1 [" + col.replace("]", "]]")
                    + "] FROM dbo.[" + table.replace("]", "]]")
                    + "] WHERE [" + col.replace("]", "]]") + "] IS NOT NULL");
            rs = ps.executeQuery();
            if (rs.next()) {
                Object o = rs.getObject(1);
                return o == null ? null : String.valueOf(o);
            }
        } catch (Exception ignored) { }
        finally {
            closeQuiet(rs);
            closeQuiet(ps);
        }
        return null;
    }

    /** A live valid value for an FK column (MIN of the referenced key), or null. */
    public static String liveFkValue(Connection c, String fkRef) {
        if (fkRef == null || !fkRef.contains(".")) return null;
        String[] parts = fkRef.split("\\.", 2);
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement("SELECT TOP 1 [" + parts[1].replace("]", "]]")
                    + "] FROM dbo.[" + parts[0].replace("]", "]]")
                    + "] WHERE [" + parts[1].replace("]", "]]") + "] IS NOT NULL ORDER BY 1");
            rs = ps.executeQuery();
            if (rs.next()) {
                Object o = rs.getObject(1);
                return o == null ? null : String.valueOf(o);
            }
        } catch (Exception ignored) {
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
        }
        return null;
    }

    // ================= INSERT builder =================

    public static final class Stmt {
        public String sql = "";
        public List<Object> binds = new ArrayList<>();
    }

    /** Map one column to a value. SKIP = omit (identity / default wins). */
    private static final Object SKIP = new Object();

    private static boolean isNum(String type) {
        return type.contains("int") || type.contains("money") || type.contains("decimal")
                || type.contains("numeric") || type.contains("float") || type.contains("real");
    }

    private static boolean isBit(String type) {
        return type.contains("bit");
    }

    private static boolean isDate(String type) {
        return type.contains("date") || type.contains("time");
    }

    @SuppressWarnings("unchecked")
    private static Object mapCol(Connection c, String table, Col col, Map<String, Object> ctx,
            List<String> warnings) {
        if (col.identity) return SKIP;
        // Vendor defaults always win unless we carry a REAL value for this column.
        String n = col.name.toLowerCase(Locale.US);
        Object real = null;
        // --- document numbers (PK cols take OWN numbers, ref cols take HEADER refs) ---
        if (n.equals("shfackh") || n.equals("shfacfo") || n.equals("sh_f")) {
            real = col.pk ? ctx.get("ownDoc") : ctx.get("docNo");
            if (real == null) real = ctx.get("docNo");
        } else if (n.equals("rdf__") || n.equals("rdf")) {
            real = col.pk ? ctx.get("ownRdf") : ctx.get("hdrRdf");
            if (real == null) real = ctx.get("hdrRdf");
        }
        // --- party / user / warehouse ---
        if (real == null && (n.equals("shmo") || n.equals("sh_mo"))) real = ctx.get("party");
        if (real == null && (n.equals("t_tafsil") || n.equals("tafsilid")) && ctx.containsKey("party"))
            real = SKIP; // supplier-side tafsil links stay for the office
        if (real == null && (n.equals("userid") || n.equals("user_id")
                || n.equals("taeeduserid") || n.equals("user_f"))) {
            real = ctx.get("uid");
            if (real == null || "0".equals(String.valueOf(real))) real = SKIP;
        }
        if (real == null && (n.startsWith("user") || n.equals("panevis"))) {
            real = n.equals("panevis") ? SKIP : ctx.get("user");
        }
        if (real == null && n.contains("anbar")) real = ctx.get("anbar");
        // --- dates ---
        if (real == null && (isDate(col.type) || n.equals("date") || n.equals("done_date")
                || n.equals("date_f") || n.equals("stdate") || n.equals("start_date"))) {
            real = ctx.get("today");
        }
        // --- line item ---
        if (real == null && (n.equals("shka") || n.equals("kala") || n.equals("kala_rdf")))
            real = ctx.get("shka");
        if (real == null && (n.equals("naka") || n.equals("kala_name"))) real = ctx.get("naka");
        if (real == null && (n.equals("tedvah") || n.equals("tedjoz") || n.equals("tedad")
                || n.equals("meghdar") || n.equals("qty")))
            real = ctx.get("qty");
        if (real == null && (n.equals("vahsanj") || n.equals("bastebandi") || n.equals("unit")
                || n.equals("vah"))) real = ctx.get("unit");
        if (real == null && (n.equals("lineno") || n.equals("radif"))) real = ctx.get("lineNo");
        // --- product master (quick-create) ---
        if (real == null && ctx.containsKey("p_name") && (n.equals("naka") || n.equals("coka")))
            real = ctx.get("p_name");
        if (real == null && ctx.containsKey("p_unit") && n.equals("vahsanj")) real = ctx.get("p_unit");
        if (real == null && ctx.containsKey("p_group") && n.contains("group")) real = ctx.get("p_group");
        // --- customer master (quick-create) ---
        if (real == null && ctx.containsKey("c_name") && (n.equals("moname") || n.equals("name")))
            real = ctx.get("c_name");
        if (real == null && ctx.containsKey("c_cell")
                && (n.equals("cell") || n.equals("tell1"))) real = ctx.get("c_cell");
        if (real == null && ctx.containsKey("c_group") && n.contains("group")) real = ctx.get("c_group");
        if (real == null && ctx.containsKey("c_vis") && n.contains("vis")) real = ctx.get("c_vis");
        if (real == null && ctx.containsKey("c_addr") && n.equals("addre")) real = ctx.get("c_addr");
        // --- return flag on pre-invoice headers ---
        if (real == null && (n.equals("isret") || n.equals("is_ret"))) {
            real = ctx.get("isRet");
            if (real == null) real = SKIP;
        }
        if (real != null && real != SKIP) return coerce(col, real);
        if (col.hasDefault) return SKIP;
        if (col.nullable) return SKIP;
        // Required with no default: type-safe zero + live sampling for flags.
        if (isBit(col.type)) return 0;
        if (isNum(col.type)) {
            if (col.fkRef != null) {
                String fk = liveFkValue(c, col.fkRef);
                if (fk != null) {
                    warnings.add(col.name + " ← " + col.fkRef + " (" + fk + ")");
                    return coerce(col, fk);
                }
                warnings.add("مقدار معتبر برای " + col.name + " یافت نشد؛ صفر گذاشته شد");
            }
            return 0;
        }
        if (isDate(col.type)) return ctx.get("today");
        // Single-letter flags: sample the live convention.
        if (n.equals("active") || n.equals("ismodify") || n.equals("isret")
                || n.equals("tasvieh") || n.equals("special") || n.equals("is_taeed")) {
            String live = liveSample(c, table, col.name);
            if (live != null && live.length() == 1) return live;
            return n.equals("active") ? "1" : "0";
        }
        return "";
    }

    private static Object coerce(Col col, Object v) {
        if (v == null) return SKIP;
        String s = String.valueOf(v);
        try {
            if (isBit(col.type)) {
                return ("1".equals(s) || "true".equalsIgnoreCase(s)) ? 1 : 0;
            }
            if (col.type.contains("bigint") || col.type.contains("int")) {
                return Long.parseLong(s.trim());
            }
            if (isNum(col.type)) {
                return Double.parseDouble(s.trim());
            }
        } catch (Exception ignored) {
            if (isNum(col.type)) return 0;
        }
        return s;
    }

    private static Stmt buildInsert(Connection c, String table, List<Col> shape,
            Map<String, Object> ctx, List<String> warnings) throws Exception {
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        List<Object> binds = new ArrayList<>();
        for (Col col : shape) {
            Object v = mapCol(c, table, col, ctx, warnings);
            if (v == SKIP) continue;
            if (cols.length() > 0) {
                cols.append(", ");
                vals.append(", ");
            }
            cols.append("[").append(col.name.replace("]", "]]")).append("]");
            vals.append("?");
            binds.add(v);
        }
        if (binds.isEmpty()) throw new Exception("no mappable columns in " + table);
        Stmt st = new Stmt();
        st.sql = "INSERT INTO dbo.[" + table.replace("]", "]]") + "] (" + cols + ") VALUES (" + vals + ")";
        st.binds = binds;
        return st;
    }

    /**
     * Build all statements for one warehouse document (new masters first,
     * then header, then lines). Throws with a Persian message when the
     * target tables are absent.
     */
    public static List<Stmt> buildDoc(Connection c, JSONObject doc, int uid, String user,
            List<String> warnings) throws Exception {
        String type = doc.optString(D_TYPE, BUY);
        boolean salesSide = CUSTRET.equals(type);
        String hTable = salesSide ? "sailfact_pish" : "buyfact_pish";
        String lTable = salesSide ? "subsailfact_pish" : "subbuyfact_pish";
        List<Col> hShape = shape(c, hTable);
        List<Col> lShape = shape(c, lTable);
        if (hShape.isEmpty()) throw new Exception("جدول " + hTable + " در این دیتابیس نیست");
        if (lShape.isEmpty()) throw new Exception("جدول " + lTable + " در این دیتابیس نیست");
        List<String> hTrg = triggers(c, hTable);
        List<String> lTrg = triggers(c, lTable);
        if (!hTrg.isEmpty() || !lTrg.isEmpty()) {
            warnings.add("روی این جدول‌ها تریگر فعال است (" + (hTrg.isEmpty() ? "—" : hTrg.get(0))
                    + ")؛ ثبت با احتیاط انجام می‌شود");
        }
        List<Stmt> out = new ArrayList<>();
        // --- new party (customer/supplier quick-create) ---
        String party = doc.optString(D_PSHMO, "");
        JSONObject partyNew = doc.optJSONObject(D_PNEW);
        if ((party.isEmpty() || "0".equals(party)) && partyNew != null) {
            List<Col> cShape = shape(c, "CUSTOMERS");
            if (cShape.isEmpty()) throw new Exception("جدول CUSTOMERS در این دیتابیس نیست");
            String idCol = findCol(cShape, "shmo", "SHMO", "id");
            long newId = nextNum(c, "CUSTOMERS", idCol);
            Map<String, Object> ctx = baseCtx(uid, user);
            ctx.put("party", newId);
            ctx.put("c_name", partyNew.optString("name", ""));
            ctx.put("c_cell", partyNew.optString("cell", ""));
            ctx.put("c_group", partyNew.optLong("group", 0));
            ctx.put("c_vis", partyNew.optLong("vis", 0));
            ctx.put("c_addr", partyNew.optString("addr", ""));
            putId(ctx, cShape, idCol, newId);
            out.add(buildInsert(c, "CUSTOMERS", cShape, ctx, warnings));
            party = String.valueOf(newId);
        }
        if (party.isEmpty()) throw new Exception("طرف سند مشخص نیست");
        // --- new products (quick-create) ---
        JSONArray lines = doc.optJSONArray(D_LINES);
        if (lines == null || lines.length() == 0) throw new Exception("سند قلم ندارد");
        List<Col> iShape = null;
        for (int i = 0; i < lines.length(); i++) {
            JSONObject ln = lines.optJSONObject(i);
            if (ln == null || !ln.optBoolean(L_NEW, false)) continue;
            if (iShape == null) {
                iShape = shape(c, "inventory");
                if (iShape.isEmpty()) throw new Exception("جدول inventory در این دیتابیس نیست");
            }
            String idCol = findCol(iShape, "shka", "SHKA", "code");
            long newId = nextNum(c, "inventory", idCol);
            Map<String, Object> ctx = baseCtx(uid, user);
            ctx.put("p_name", ln.optString(L_NNAME, ""));
            ctx.put("p_unit", ln.optString(L_NUNIT, ""));
            ctx.put("p_group", ln.optLong(L_NGROUP, 0));
            putId(ctx, iShape, idCol, newId);
            out.add(buildInsert(c, "inventory", iShape, ctx, warnings));
            ln.put(L_SHKA, String.valueOf(newId));
            ln.put(L_NAKA, ln.optString(L_NNAME, ""));
        }
        // --- header ---
        String noCol = findCol(hShape, "shfackh", "shfacfo", "sh_f", "no");
        String rdfCol = matchCol(hShape, "rdf__", "rdf");
        long docNo = nextNum(c, hTable, noCol);
        long hdrRdf = docNo;
        if (rdfCol != null && !rdfCol.equalsIgnoreCase(noCol)) {
            hdrRdf = nextNum(c, hTable, rdfCol);
        }
        Map<String, Object> hctx = baseCtx(uid, user);
        hctx.put("party", party);
        hctx.put("docNo", docNo);
        hctx.put("hdrRdf", hdrRdf);
        hctx.put("ownDoc", docNo);
        hctx.put("ownRdf", hdrRdf);
        hctx.put("anbar", doc.optString(D_ANBAR, ""));
        if (CUSTRET.equals(type) || BUYRET.equals(type)) hctx.put("isRet", "1");
        out.add(buildInsert(c, hTable, hShape, hctx, warnings));
        // --- lines (each line gets its own fresh row number) ---
        String lineIdCol = matchCol(lShape, "rdf");
        for (int i = 0; i < lines.length(); i++) {
            JSONObject ln = lines.optJSONObject(i);
            if (ln == null) continue;
            Map<String, Object> lctx = baseCtx(uid, user);
            lctx.put("party", party);
            lctx.put("docNo", docNo);
            lctx.put("hdrRdf", hdrRdf);
            lctx.put("ownDoc", docNo);
            lctx.put("ownRdf", lineIdCol == null ? (docNo * 1000 + i + 1)
                    : nextNum(c, lTable, lineIdCol));
            lctx.put("anbar", doc.optString(D_ANBAR, ""));
            lctx.put("shka", ln.optString(L_SHKA, ""));
            lctx.put("naka", ln.optString(L_NAKA, ""));
            lctx.put("qty", ln.optString(L_QTY, "0"));
            lctx.put("unit", ln.optString(L_UNIT, ""));
            lctx.put("lineNo", i + 1);
            if (CUSTRET.equals(type) || BUYRET.equals(type)) lctx.put("isRet", "1");
            out.add(buildInsert(c, lTable, lShape, lctx, warnings));
        }
        warnings.add(0, "شماره سند: " + docNo);
        try {
            doc.put("_postedNo", docNo);
        } catch (Exception ignored) { }
        return out;
    }

    private static Map<String, Object> baseCtx(int uid, String user) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("today", Jalali.todayStr());
        ctx.put("todayG", Jalali.todayGregorian());
        ctx.put("uid", uid);
        ctx.put("user", user == null ? "" : user);
        return ctx;
    }

    private static String findCol(List<Col> shape, String... candidates) throws Exception {
        String hit = matchCol(shape, candidates);
        if (hit == null) throw new Exception("ستون " + candidates[0] + " یافت نشد");
        return hit;
    }

    private static String matchCol(List<Col> shape, String... candidates) {
        for (String want : candidates) {
            for (Col col : shape) {
                if (col.name.equalsIgnoreCase(want)) return col.name;
            }
        }
        return null;
    }

    /** Force an explicit id value for a master-table key column. */
    private static void putId(Map<String, Object> ctx, List<Col> shape, String idCol, long id) {
        String n = idCol.toLowerCase(Locale.US);
        if (n.equals("shka")) ctx.put("shka", id);
        else if (n.equals("shmo")) ctx.put("party", id);
        else {
            ctx.put("ownDoc", id);
            ctx.put("ownRdf", id);
        }
    }

    /** Execute all statements in ONE transaction (all or nothing). */
    public static void execute(Connection c, List<Stmt> stmts) throws Exception {
        boolean auto = true;
        try {
            auto = c.getAutoCommit();
        } catch (Exception ignored) { }
        try {
            c.setAutoCommit(false);
            for (Stmt st : stmts) {
                PreparedStatement ps = null;
                try {
                    ps = c.prepareStatement(st.sql);
                    for (int i = 0; i < st.binds.size(); i++) {
                        Object b = st.binds.get(i);
                        if (b instanceof Long) ps.setLong(i + 1, (Long) b);
                        else if (b instanceof Integer) ps.setInt(i + 1, (Integer) b);
                        else if (b instanceof Double) ps.setDouble(i + 1, (Double) b);
                        else ps.setString(i + 1, b == null ? null : String.valueOf(b));
                    }
                    ps.executeUpdate();
                } finally {
                    closeQuiet(ps);
                }
            }
            c.commit();
        } catch (Exception e) {
            try {
                c.rollback();
            } catch (Exception ignored) { }
            throw e;
        } finally {
            try {
                c.setAutoCommit(auto);
            } catch (Exception ignored) { }
        }
    }

    public static JSONArray handlog(Context c) {
        try {
            return new JSONArray(prefs(c).getString("handlog", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /**
     * Office completion (Takmil): write unit prices + line sums onto an
     * already-posted pre-invoice, then refresh its header totals. Column
     * names are resolved live by pattern; anything unmapped is reported in
     * warnings and skipped (never guessed). Lines carry shka/qty/price.
     *
     * @return document total posted.
     */
    public static double completePrices(Connection c, String hTable, String lTable,
            String noCol, long docNo, JSONArray lines, List<String> warnings) throws Exception {
        List<Col> lShape = shape(c, lTable);
        List<Col> hShape = shape(c, hTable);
        if (lShape.isEmpty() || hShape.isEmpty())
            throw new Exception("جدول سند در دسترس نیست");
        String shkaCol = matchCol(lShape, "shka", "SHKA", "kala", "kala_rdf");
        String priceCol = matchCol(lShape, "vahprice", "VAHPRICE", "fi", "FI", "price",
                "buyprice", "BUY_PRICE", "narkh", "gheymat", "unitprice", "vahadprice");
        String sumCol = matchCol(lShape, "linesum", "LINESUM", "sum", "mablagh", "jam",
                "total", "linetotal");
        if (shkaCol == null)
            throw new Exception("ستون کالای اقلام شناخته نشد");
        if (priceCol == null && sumCol == null)
            throw new Exception("ستون مبلغ در اقلام این دیتابیس شناخته نشد");
        if (priceCol == null) warnings.add("ستون فی واحد یافت نشد؛ فقط جمع سطرها ثبت شد");
        if (sumCol == null) warnings.add("ستون جمع سطر یافت نشد؛ فقط فی واحد ثبت شد");
        double total = 0;
        for (int i = 0; i < lines.length(); i++) {
            JSONObject ln = lines.optJSONObject(i);
            if (ln == null) continue;
            double qty = ln.optDouble(L_QTY, 0);
            try {
                qty = Double.parseDouble(Money.en(ln.optString(L_QTY, "0")).trim());
            } catch (Exception ignored) { }
            double price = ln.optDouble(L_PRICE, 0);
            try {
                price = Double.parseDouble(Money.en(ln.optString(L_PRICE, "0")).trim());
            } catch (Exception ignored) { }
            double sum = qty * price;
            total += sum;
            StringBuilder set = new StringBuilder();
            List<Object> binds = new ArrayList<>();
            if (priceCol != null) {
                set.append("[").append(priceCol.replace("]", "]]")).append("]=?");
                binds.add(price);
            }
            if (sumCol != null) {
                if (set.length() > 0) set.append(", ");
                set.append("[").append(sumCol.replace("]", "]]")).append("]=?");
                binds.add(sum);
            }
            String sql = "UPDATE dbo.[" + lTable.replace("]", "]]") + "] SET " + set
                    + " WHERE [" + noCol.replace("]", "]]") + "]=? AND ["
                    + shkaCol.replace("]", "]]") + "]=?";
            PreparedStatement ps = null;
            try {
                ps = c.prepareStatement(sql);
                int p = 1;
                for (Object b : binds) ps.setDouble(p++, ((Number) b).doubleValue());
                ps.setLong(p++, docNo);
                try {
                    ps.setLong(p, Long.parseLong(
                            Money.en(ln.optString(L_SHKA, "0")).trim()));
                } catch (Exception e) {
                    ps.setString(p, ln.optString(L_SHKA, ""));
                }
                ps.executeUpdate();
            } finally {
                closeQuiet(ps);
            }
        }
        // Header totals (best-effort over total-ish columns).
        String[] totals = {"sumlineall", "all", "jam", "mablagh", "total", "jamkol", "sumall"};
        int posted = 0;
        for (String t : totals) {
            String hc = matchCol(hShape, t);
            if (hc == null) continue;
            PreparedStatement ps = null;
            try {
                ps = c.prepareStatement("UPDATE dbo.[" + hTable.replace("]", "]]")
                        + "] SET [" + hc.replace("]", "]]") + "]=? WHERE ["
                        + noCol.replace("]", "]]") + "]=?");
                ps.setDouble(1, total);
                ps.setLong(2, docNo);
                ps.executeUpdate();
                posted++;
            } catch (Exception ignored) {
            } finally {
                closeQuiet(ps);
            }
        }
        if (posted == 0) warnings.add("ستون جمع سربرگ یافت نشد؛ جمع سند در سربرگ ثبت نشد");
        warnings.add(0, "جمع سند: " + total);
        return total;
    }

    /** Human-readable SQL with binds inlined (dry-run display). Never executed. */
    public static String describe(List<Stmt> stmts) {
        StringBuilder b = new StringBuilder();
        for (Stmt st : stmts) {
            String sql = st.sql;
            for (Object bind : st.binds) {
                String v;
                if (bind instanceof Number) v = String.valueOf(bind);
                else v = "N'" + String.valueOf(bind).replace("'", "''") + "'";
                sql = sql.replaceFirst("\\?", java.util.regex.Matcher.quoteReplacement(v));
            }
            b.append(sql).append(";\n\n");
        }
        return b.toString();
    }

    private static void closeQuiet(Object o) {
        try {
            if (o instanceof ResultSet) ((ResultSet) o).close();
            else if (o instanceof java.sql.Statement) ((java.sql.Statement) o).close();
        } catch (Exception ignored) { }
    }
}
