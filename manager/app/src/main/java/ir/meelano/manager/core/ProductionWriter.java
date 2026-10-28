package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production posting for the Atiran schema in Mili.sql.
 *
 * Mili.sql contains no production-entry procedure or Production trigger; its
 * stock contract is the produce.Production / ka_act tables plus the inventory
 * rebuild procedures. It does contain the official accounting wrapper
 * SabtSanadHesabdariFromAtiran, which must be called after the ledger rows so
 * act 85/86 are reflected in accounting when Atiran's setting enables it.
 * UpdateMojodiInventory* rebuilds balances from ka_act. The
 * ka_Ghno_HactID trigger is scalar over inserted, so each movement is inserted
 * as a separate row.
 *
 * All writes are one SQL transaction and are guarded by strict live-shape
 * checks. This class never creates or deletes a ProductionSeries row. The
 * output product's WithProductionSerial flag is switched off in the same
 * transaction as explicitly requested; ingredient flags are never changed.
 */
public final class ProductionWriter {
    private ProductionWriter() { }

    public static final int ACT_RAW_CONSUMED = 85;
    public static final int ACT_FINISHED_OUTPUT = 86;

    public static final class Product {
        public final long id;
        public final String name;
        public final String unit;
        public final long packSize;
        public final BigDecimal stockPieces;
        public final BigDecimal weightPerUnit;
        public final boolean withProductionSerial;
        public final int varietyId;
        public final int varietyCount;

        public Product(long id, String name, String unit, long packSize,
                       BigDecimal stockPieces, BigDecimal weightPerUnit,
                       boolean withProductionSerial, int varietyId, int varietyCount) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.unit = unit == null ? "" : unit;
            this.packSize = Math.max(1L, packSize);
            this.stockPieces = stockPieces == null ? BigDecimal.ZERO : stockPieces;
            this.weightPerUnit = weightPerUnit == null ? BigDecimal.ZERO : weightPerUnit;
            this.withProductionSerial = withProductionSerial;
            this.varietyId = varietyId;
            this.varietyCount = varietyCount;
        }

        @Override public String toString() { return name + " • " + id; }
    }

    public static final class Warehouse {
        public final int id;
        public final String name;
        public Warehouse(int id, String name) { this.id = id; this.name = name == null ? "" : name; }
        @Override public String toString() { return name + " • " + id; }
    }

    public static final class Recipe {
        public final long id;
        public final String name;
        public final long productId;
        public final String productName;
        public final String description;
        public final BigDecimal baseOutputQty;
        public final BigDecimal baseWeightKg;
        public Recipe(long id, String name, long productId, String productName, String description,
                      BigDecimal baseOutputQty, BigDecimal baseWeightKg) {
            this.id = id; this.name = name == null ? "" : name; this.productId = productId;
            this.productName = productName == null ? "" : productName;
            this.description = description == null ? "" : description;
            this.baseOutputQty = nz(baseOutputQty); this.baseWeightKg = nz(baseWeightKg);
        }
        @Override public String toString() {
            String n=name.trim(), p=productName.trim();
            if(n.isEmpty())return p;
            return n.equalsIgnoreCase(p)?n:n+" • "+p;
        }
    }

    public static final class Ingredient {
        public final long productId;
        public final String name;
        public final String unit;
        public final long packSize;
        /** Actual base pieces consumed for this production, not recipe-scaled quantity. */
        public final BigDecimal qtyPieces;
        public Ingredient(long productId, String name, String unit, long packSize, BigDecimal qtyPieces) {
            this.productId = productId; this.name = name == null ? "" : name;
            this.unit = unit == null ? "" : unit; this.packSize = Math.max(1L, packSize);
            this.qtyPieces = nz(qtyPieces);
        }
    }

    public static final class CostType {
        public final int id;
        public final String name;
        public CostType(int id, String name) { this.id = id; this.name = name == null ? "" : name; }
        @Override public String toString() { return name; }
    }

    public static final class ExtraCost {
        public final int costId;
        public final BigDecimal amount;
        public ExtraCost(int costId, BigDecimal amount) { this.costId = costId; this.amount = nz(amount); }
    }

    public static final class RecipeIngredient {
        public final Product product;
        /** Formula quantity in base pieces for the recipe's base output. */
        public final BigDecimal baseQtyPieces;
        public final boolean available;
        public RecipeIngredient(Product product, BigDecimal baseQtyPieces) {
            this(product, baseQtyPieces, true);
        }
        public RecipeIngredient(Product product, BigDecimal baseQtyPieces, boolean available) {
            this.product = product; this.baseQtyPieces = nz(baseQtyPieces); this.available = available;
        }
    }

    public static final class RecipeCost {
        public final int costId;
        public final String name;
        public final BigDecimal amount;
        public RecipeCost(int costId, String name, BigDecimal amount) {
            this.costId = costId; this.name = name == null ? "" : name; this.amount = nz(amount);
        }
    }

    public static final class RecipeDraft {
        public long id;
        public String name = "";
        public long productId;
        public String description = "";
        public BigDecimal baseOutputQty = BigDecimal.ONE;
        public BigDecimal baseWeightKg = BigDecimal.ZERO;
        public final List<RecipeIngredient> ingredients = new ArrayList<>();
        public final List<RecipeCost> costs = new ArrayList<>();
    }

    public static final class Request {
        public long recipeId;
        public long finishedProductId;
        public int warehouseId;
        public BigDecimal outputQty;
        public BigDecimal actualWeightKg;
        public String description = "";
        public String idempotencyKey = "";
        public final List<Ingredient> ingredients = new ArrayList<>();
        public final List<ExtraCost> costs = new ArrayList<>();
    }

    public static final class Costing {
        public BigDecimal materialCost = BigDecimal.ZERO;
        public BigDecimal overheadCost = BigDecimal.ZERO;
        public BigDecimal totalCost = BigDecimal.ZERO;
        public BigDecimal costPerUnit = BigDecimal.ZERO;
        public BigDecimal costPerKg = BigDecimal.ZERO;
        public BigDecimal finishedUnitPrice = BigDecimal.ZERO;
    }

    public static final class Posted {
        public final long productionId;
        public final int revision;
        public final Costing costing;
        public final boolean duplicate;
        private Posted(long productionId, int revision, Costing costing, boolean duplicate) {
            this.productionId = productionId; this.revision = revision;
            this.costing = costing; this.duplicate = duplicate;
        }
    }

    /**
     * Creates the app-only recipe yield extension if absent. Atiran's native
     * Formulation tables have no base output quantity/weight columns.
     */
    public static void ensureProfileTable(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("IF OBJECT_ID(N'dbo.meelano_production_profile',N'U') IS NULL " +
                    "CREATE TABLE dbo.meelano_production_profile(" +
                    "FormulID bigint NOT NULL PRIMARY KEY," +
                    "BaseOutputQty decimal(18,3) NOT NULL," +
                    "BaseWeightKg decimal(18,3) NOT NULL," +
                    "UpdatedAt datetime2 NOT NULL CONSTRAINT DF_meelano_prod_profile_UpdatedAt DEFAULT(SYSDATETIME())," +
                    "UpdatedBy int NOT NULL)");
        }
    }

    /** Keeps the app-owned yield basis alongside Atiran's native recipe revision snapshot. */
    private static void ensureProfileHistoryTable(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("IF OBJECT_ID(N'dbo.meelano_production_profile_history',N'U') IS NULL " +
                    "CREATE TABLE dbo.meelano_production_profile_history(" +
                    "FormulID bigint NOT NULL,Rdf_ int NOT NULL," +
                    "BaseOutputQty decimal(18,3) NOT NULL,BaseWeightKg decimal(18,3) NOT NULL," +
                    "UpdatedAt datetime2 NOT NULL,UpdatedBy int NOT NULL," +
                    "CONSTRAINT PK_meelano_prod_profile_history PRIMARY KEY(FormulID,Rdf_))");
        }
    }

    /** Resolve only an active, unlocked Atiran manager; synthetic local IDs are never posted. */
    public static int resolveAtiranUserId(Connection c, int sessionUid) throws Exception {
        return resolveAtiranUserId(c, sessionUid, "", "");
    }

    /** Resolve the signed-in identity to a real Atiran audit id. */
    public static int resolveAtiranUserId(Connection c, int sessionUid, String sessionUser,
            String sessionRole) throws Exception {
        // Never map a synthetic local session to a real audit identity. The caller
        // must have authenticated the Atiran account and supply its positive user_id.
        if (sessionUid <= 0)
            throw new Exception("نشست معتبر آتیران لازم است؛ با حساب مدیر آتیران وارد شوید.");
        Meta meta=new Meta(c);
        if(!meta.table("sys_users"))throw new Exception("جدول کاربران آتیران (sys_users) پیدا نشد.");
        String idCol=meta.col("sys_users","user_id"),nameCol=meta.col("sys_users","user_name");
        if(idCol==null||nameCol==null)throw new Exception("شناسه یا نام کاربری در sys_users آتیران پیدا نشد.");
        String username=null;
        try(PreparedStatement ps=c.prepareStatement("SELECT TOP 1 "+Sql.q(nameCol)+" FROM "+meta.qTable("sys_users")+" WHERE "+Sql.q(idCol)+"=?")){
            ps.setInt(1,sessionUid);try(ResultSet rs=ps.executeQuery()){if(rs.next())username=rs.getString(1);}
        }
        if(username==null||username.trim().isEmpty())throw new Exception("کاربر نشست دیگر در sys_users آتیران پیدا نشد.");
        AtiranAuth.AuthUser user=AtiranAuth.fetchUser(c,username);
        if(user==null||user.uid!=sessionUid||!user.active||user.locked)
            throw new Exception("کاربر نشست دیگر فعال و قابل استفاده در آتیران نیست؛ دوباره وارد شوید.");
        if(!RoleStore.ADMIN.equals(user.appRole))
            throw new Exception("ثبت تولید فقط با حساب دارای نقش مدیر آتیران مجاز است.");
        return user.uid;
    }


    /** Refuse to write unless the actual DB exposes the exact Mili production ledger contract. */
    public static void preflight(Connection c) throws Exception {
        requireColumns(c, "produce", "Production", new String[]{"ID", "Rdf_", "FormulID", "shka", "TedadVahed", "TedBastebandi", "DoneDate", "DoneTime", "Description", "UserId", "Active", "rdf_anbar", "dateProduction", "IsManual", "IsConfirm", "IsConfirmAcc"});
        requireColumns(c, "produce", "ProductionCost", new String[]{"ID", "Rdf_", "ProductionID", "CostID", "Price", "Active"});
        requireColumns(c, "produce", "Formulation", new String[]{"ID", "Name", "shka", "Description", "UserId", "Active", "DoneDate", "DoneTime"});
        requireColumns(c, "produce", "FormulationItems", new String[]{"ID", "FormulID", "shka", "TedadVahed", "TedadJoz", "Active"});
        requireColumns(c, "produce", "FormulationCost", new String[]{"ID", "FormulID", "CostID", "Price", "Active"});
        requireColumns(c, "produce", "IndirectCost", new String[]{"RowId", "CostName", "Active", "MoeinID", "TafsilID"});
        requireColumns(c, "dbo", "inventory", new String[]{"shka", "naka", "vahsanj", "mohvah", "mojkavah", "mojkajoz", "vahwe", "active", "WithProductionSerial", "ProductionPrice"});
        requireColumns(c, "dbo", "inventory_anbars", new String[]{"rdf_anbars", "shka", "mojkavah", "mojkajoz", "name", "tedbastebandi"});
        requireColumns(c, "dbo", "anbars", new String[]{"rdf_anbar", "name", "Active", "Base", "MoeinId"});
        requireColumns(c, "dbo", "Variety", new String[]{"VarietyID", "Shka", "Show"});
        requireColumns(c, "dbo", "Inventory_Anbars_Variety", new String[]{"Shka", "AnbarID", "VarietyID", "Mojkavah", "Mojkajoz"});
        requireColumns(c, "dbo", "ka_act", new String[]{"shka", "act_id", "shfac", "rdf__", "act_date", "done_date", "UserID", "tedvah", "tedjoz", "tedbastebandi", "active", "price", "invepgh", "invep", "gain", "rdf_kh", "ptax", "tax", "vis_rdf", "pertaf", "pervis", "litakhma", "HAct_id", "ghno", "sysid", "pavarez", "avarez", "Gift", "RdfAnbar", "shmo", "PerPromotion", "TafifHajmi1", "ProductionSeriesID", "PriceFinished", "TamamJoz", "ShSanadAnbar", "VarietyID"});
        requireProcedure(c, "UpdateMojodiInventory");
        requireProcedure(c, "UpdateMojodiInventoryAnbars");
        requireProcedure(c, "UpdateMojodiInventoryAnbarVariety");
        requireColumns(c, "dbo", "overal_setting", new String[]{"id", "value"});
        // The accounting document table is required only while Atiran's official posting switch
        // is enabled. Validate that dependency before the production transaction begins.
        long accountingEnabled;
        try (PreparedStatement ps = c.prepareStatement("SELECT [value] FROM dbo.overal_setting WHERE id=67");
             ResultSet r = ps.executeQuery()) {
            if (!r.next()) throw new Exception("تنظیم حسابداری تولید آتیران (overal_setting.id=67) پیدا نشد؛ ثبت تولید متوقف شد.");
            accountingEnabled = r.getLong(1);
        }
        if (accountingEnabled == 1) {
            requireProcedure(c, "SabtSanadHesabdariFromAtiran");
            requireColumns(c, "accounting", "Document", new String[]{"Kind", "Shfac", "Active"});
        }
        requireColumns(c, "dbo", "subbuyfact", new String[]{"shfackh", "rdf", "shka", "TEDVAH", "TEDJOZ", "rdf__", "active", "tamam_joz", "tamam_joz_tax", "VarietyID"});
        requireColumns(c, "dbo", "buyfact", new String[]{"shfackh", "rdf__", "DATE", "all", "active"});
        // The source DB has no production write trigger/SP, but the movement trigger is essential.
        if (!triggerExists(c, "dbo", "ka_Ghno_HactID", "ka_act"))
            throw new Exception("تریگر ثبت گردش انبار ka_Ghno_HactID در دیتابیس پیدا نشد؛ ثبت تولید متوقف شد.");
    }

    /** Active products with stock from the selected warehouse, never the company-wide balance. */
    public static List<Product> products(Connection c, int warehouseId) throws Exception {
        List<Product> out = new ArrayList<>();
        String q = "SELECT i.shka,i.naka,i.vahsanj,ISNULL(NULLIF(i.mohvah,0),1)," +
                "CAST(ISNULL(ia.mojkavah,0)*ISNULL(NULLIF(i.mohvah,0),1)+ISNULL(ia.mojkajoz,0) AS decimal(18,3))," +
                "ISNULL(i.vahwe,0),ISNULL(i.WithProductionSerial,0),ISNULL(v.varietyId,0),ISNULL(v.varietyCount,0) " +
                "FROM dbo.inventory i LEFT JOIN dbo.inventory_anbars ia ON ia.shka=i.shka AND ia.rdf_anbars=? " +
                "OUTER APPLY (SELECT MIN(VarietyID) AS varietyId,COUNT(*) AS varietyCount FROM dbo.Variety WHERE Shka=i.shka) v " +
                "WHERE i.active='t' ORDER BY i.naka";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, warehouseId);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) out.add(new Product(r.getLong(1), r.getString(2), r.getString(3), r.getLong(4),
                        r.getBigDecimal(5), r.getBigDecimal(6), r.getBoolean(7), r.getInt(8), r.getInt(9)));
            }
        }
        return out;
    }

    public static List<Warehouse> warehouses(Connection c) throws Exception {
        List<Warehouse> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT rdf_anbar,name FROM dbo.anbars WHERE ISNULL(Active,1)=1 ORDER BY CASE WHEN ISNULL(Base,0)=1 THEN 0 ELSE 1 END,rdf_anbar"); ResultSet r = ps.executeQuery()) {
            while (r.next()) out.add(new Warehouse(r.getInt(1), r.getString(2)));
        }
        return out;
    }

    public static List<CostType> costTypes(Connection c) throws Exception {
        List<CostType> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT RowId,CostName FROM produce.IndirectCost WHERE Active=1 ORDER BY CostName"); ResultSet r = ps.executeQuery()) {
            while (r.next()) out.add(new CostType(r.getInt(1), r.getString(2)));
        }
        return out;
    }

    public static List<Recipe> recipes(Connection c) throws Exception {
        ensureProfileTable(c);
        List<Recipe> out = new ArrayList<>();
        String q = "SELECT f.ID,f.Name,f.shka,i.naka,f.Description,ISNULL(p.BaseOutputQty,1),ISNULL(p.BaseWeightKg,0) " +
                "FROM produce.Formulation f JOIN dbo.inventory i ON i.shka=f.shka " +
                "LEFT JOIN dbo.meelano_production_profile p ON p.FormulID=f.ID " +
                "WHERE f.Active=1 AND i.active='t' ORDER BY f.Name,f.ID";
        try (PreparedStatement ps = c.prepareStatement(q); ResultSet r = ps.executeQuery()) {
            while (r.next()) out.add(new Recipe(r.getLong(1), r.getString(2), r.getLong(3), r.getString(4), r.getString(5), r.getBigDecimal(6), r.getBigDecimal(7)));
        }
        return out;
    }

    public static List<RecipeIngredient> recipeIngredients(Connection c, long recipeId) throws Exception {
        return recipeIngredients(c, recipeId, 0);
    }

    /** Recipe rows carry real serial/variety metadata and stock for the chosen warehouse. */
    public static List<RecipeIngredient> recipeIngredients(Connection c, long recipeId, int warehouseId) throws Exception {
        List<RecipeIngredient> out = new ArrayList<>();
        String q = "SELECT fi.shka,ISNULL(i.naka,N'[کالای غیرفعال یا ناموجود]'),ISNULL(i.vahsanj,N'—'),ISNULL(NULLIF(i.mohvah,0),1)," +
                "CAST(ISNULL(ia.mojkavah,0)*ISNULL(NULLIF(i.mohvah,0),1)+ISNULL(ia.mojkajoz,0) AS decimal(18,3))," +
                "ISNULL(i.vahwe,0),ISNULL(i.WithProductionSerial,0),ISNULL(v.varietyId,0),ISNULL(v.varietyCount,0)," +
                "CAST(fi.TedadVahed*ISNULL(NULLIF(i.mohvah,0),1)+fi.TedadJoz AS decimal(18,3))," +
                "CASE WHEN i.shka IS NOT NULL AND i.active='t' THEN 1 ELSE 0 END " +
                "FROM produce.FormulationItems fi LEFT JOIN dbo.inventory i ON i.shka=fi.shka " +
                "LEFT JOIN dbo.inventory_anbars ia ON ia.shka=i.shka AND ia.rdf_anbars=? " +
                "OUTER APPLY (SELECT MIN(VarietyID) AS varietyId,COUNT(*) AS varietyCount FROM dbo.Variety WHERE Shka=fi.shka) v " +
                "JOIN produce.Formulation f ON f.ID=fi.FormulID " +
                "WHERE fi.FormulID=? AND fi.Active=1 AND f.Active=1 ORDER BY fi.ID";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, warehouseId);
            ps.setLong(2, recipeId);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    Product p = new Product(r.getLong(1), r.getString(2), r.getString(3), r.getLong(4),
                            r.getBigDecimal(5), r.getBigDecimal(6), r.getBoolean(7), r.getInt(8), r.getInt(9));
                    out.add(new RecipeIngredient(p, r.getBigDecimal(10), r.getBoolean(11)));
                }
            }
        }
        return out;
    }

    public static List<RecipeCost> recipeCosts(Connection c, long recipeId) throws Exception {
        List<RecipeCost> out = new ArrayList<>();
        String q = "SELECT fc.CostID,ic.CostName,fc.Price FROM produce.FormulationCost fc " +
                "JOIN produce.IndirectCost ic ON ic.RowId=fc.CostID " +
                "WHERE fc.FormulID=? AND fc.Active=1 AND ic.Active=1 ORDER BY fc.ID";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setLong(1, recipeId);
            try (ResultSet r = ps.executeQuery()) { while (r.next()) out.add(new RecipeCost(r.getInt(1), r.getString(2), r.getBigDecimal(3))); }
        }
        return out;
    }

    /** New recipe or an edit with a complete snapshot in Atiran's History* tables. */
    public static long saveRecipe(Connection c, RecipeDraft d, int userId) throws Exception {
        if (d == null || d.productId <= 0 || d.name == null || d.name.trim().isEmpty()) throw new Exception("نام فرمول و کالای نهایی الزامی است.");
        if (userId <= 0) throw new Exception("برای ثبت فرمول، شناسهٔ واقعی کاربر آتیران لازم است.");
        if (d.ingredients.isEmpty()) throw new Exception("فرمول دست‌کم باید یک مادهٔ اولیه داشته باشد.");
        if (nz(d.baseOutputQty).signum() <= 0) throw new Exception("مقدار خروجی پایه باید بیشتر از صفر باشد.");
        if (nz(d.baseWeightKg).signum() < 0) throw new Exception("وزن پایه نمی‌تواند منفی باشد.");
        requirePrecision(d.baseOutputQty,3,"مقدار خروجی پایه");
        requirePrecision(d.baseWeightKg,3,"وزن پایه");
        for (RecipeIngredient line : d.ingredients) {
            if (line == null || line.product == null || line.product.id <= 0 || nz(line.baseQtyPieces).signum() <= 0)
                throw new Exception("هر مادهٔ فرمول باید کالا و مقدار پایهٔ مثبت داشته باشد.");
            requirePrecision(line.baseQtyPieces,3,"مقدار پایهٔ مادهٔ فرمول");
        }
        for (RecipeCost cost : d.costs) {
            if (cost == null || cost.costId <= 0 || nz(cost.amount).signum() < 0)
                throw new Exception("هر سربار فرمول باید نوع معتبر و مبلغ غیرمنفی داشته باشد.");
            requirePrecision(cost.amount,4,"مبلغ سربار");
        }
        if (!c.getAutoCommit()) throw new Exception("ذخیرهٔ فرمول باید روی اتصال مستقل با autoCommit فعال آغاز شود.");
        ensureProfileTable(c);
        if (d.id > 0) {
            ensureProfileHistoryTable(c);
            requireColumns(c, "produce", "HistoryFormulation", new String[]{"FormulID", "Rdf_", "Name", "shka", "Description", "UserId", "DoneDate", "DoneTime"});
            requireColumns(c, "produce", "HistoryFormulationItems", new String[]{"FormulID", "Rdf_", "shka", "TedadVahed", "TedadJoz"});
            requireColumns(c, "produce", "HistoryFormulationCost", new String[]{"FormulID", "Rdf_", "Price", "CostID"});
        }
        boolean auto = true;
        c.setAutoCommit(false);
        try {
            lock(c, "meelano-production-recipes");
            Product output = product(c,d.productId,0,true);
            if (output == null) throw new Exception("کالای نهایی فرمول در آتیران فعال نیست.");
            requireSingleVariety(output);
            for (RecipeIngredient line : d.ingredients) {
                if (line.product.id == d.productId) throw new Exception("کالای نهایی نمی‌تواند مادهٔ اولیهٔ فرمول خودش باشد.");
                Product raw = product(c,line.product.id,0,true);
                if (raw == null) throw new Exception("مادهٔ فرمول در فهرست فعال آتیران نیست.");
                requireSingleVariety(raw);
                if (raw.withProductionSerial) throw new Exception("مادهٔ «"+raw.name+"» سری‌دار است و تا انتخاب سری واقعی نمی‌تواند وارد فرمول تولید شود.");
            }
            String day = Jalali.todayStr();
            String time = nowTime();
            long id = d.id;
            if (id <= 0) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO produce.Formulation(Name,shka,Description,UserId,Active,DoneDate,DoneTime) VALUES(?,?,?,?,1,?,?); SELECT CAST(SCOPE_IDENTITY() AS bigint)")) {
                    ps.setString(1, d.name.trim()); ps.setLong(2, d.productId); ps.setString(3, safe(d.description, 1000));
                    ps.setInt(4, userId); ps.setString(5, day); ps.setString(6, time);
                    try (ResultSet r = ps.executeQuery()) { if (!r.next()) throw new Exception("شمارهٔ فرمول آتیران برنگشت."); id = r.getLong(1); }
                }
            } else {
                int rev = nextRevision(c, id);
                snapshotRecipe(c, id, rev);
                try (PreparedStatement ps = c.prepareStatement("UPDATE produce.Formulation SET Name=?,shka=?,Description=?,UserId=?,DoneDate=?,DoneTime=?,Active=1 WHERE ID=?")) {
                    ps.setString(1, d.name.trim()); ps.setLong(2, d.productId); ps.setString(3, safe(d.description, 1000));
                    ps.setInt(4, userId); ps.setString(5, day); ps.setString(6, time); ps.setLong(7, id);
                    if (ps.executeUpdate() != 1) throw new Exception("فرمول برای ویرایش پیدا نشد.");
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE produce.FormulationItems SET Active=0 WHERE FormulID=? AND Active=1")) { ps.setLong(1, id); ps.executeUpdate(); }
                try (PreparedStatement ps = c.prepareStatement("UPDATE produce.FormulationCost SET Active=0 WHERE FormulID=? AND Active=1")) { ps.setLong(1, id); ps.executeUpdate(); }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO produce.FormulationItems(FormulID,shka,TedadVahed,TedadJoz,Active) VALUES(?,?,?,?,1)")) {
                for (RecipeIngredient line : d.ingredients) {
                    long pack = Math.max(1, line.product.packSize);
                    BigDecimal[] split = splitQty(line.baseQtyPieces, pack);
                    ps.setLong(1, id); ps.setLong(2, line.product.id); ps.setBigDecimal(3, split[0]);
                    ps.setInt(4, split[1].setScale(0, RoundingMode.HALF_UP).intValueExact()); ps.addBatch();
                }
                int[] wrote = ps.executeBatch();
                if (wrote.length == 0) throw new Exception("هیچ مادهٔ اولیهٔ معتبری در فرمول ثبت نشد.");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO produce.FormulationCost(FormulID,CostID,Price,Active) VALUES(?,?,?,1)")) {
                for (RecipeCost cost : d.costs) {
                    if (nz(cost.amount).signum() == 0) continue;
                    requireActiveCost(c, cost.costId);
                    ps.setLong(1, id); ps.setInt(2, cost.costId); ps.setBigDecimal(3, money4(cost.amount)); ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE dbo.meelano_production_profile SET BaseOutputQty=?,BaseWeightKg=?,UpdatedAt=SYSDATETIME(),UpdatedBy=? WHERE FormulID=?")) {
                ps.setBigDecimal(1, qty3(d.baseOutputQty)); ps.setBigDecimal(2, qty3(d.baseWeightKg)); ps.setInt(3, userId); ps.setLong(4, id);
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement ins = c.prepareStatement("INSERT INTO dbo.meelano_production_profile(FormulID,BaseOutputQty,BaseWeightKg,UpdatedBy) VALUES(?,?,?,?)")) {
                        ins.setLong(1, id); ins.setBigDecimal(2, qty3(d.baseOutputQty)); ins.setBigDecimal(3, qty3(d.baseWeightKg)); ins.setInt(4, userId); ins.executeUpdate();
                    }
                }
            }
            if (auto) c.commit();
            return id;
        } catch (Exception e) {
            try { if (auto) c.rollback(); } catch (Exception ignored) { }
            throw e;
        } finally { if (auto) try { c.setAutoCommit(true); } catch (Exception ignored) { } }
    }

    /**
     * Post one confirmed manufacturing document. The current user must be a
     * positive Atiran sys_users id; local synthetic role ids are rejected.
     */
    public static Posted post(Connection c, Request req, int userId) throws Exception {
        if (req == null) throw new Exception("سند تولید خالی است.");
        if (userId <= 0) throw new Exception("ثبت قطعی تولید فقط با شناسهٔ واقعی مدیر آتیران مجاز است.");
        if (req.finishedProductId <= 0 || req.warehouseId <= 0) throw new Exception("کالای نهایی و انبار را انتخاب کنید.");
        if (nz(req.outputQty).signum() <= 0 || nz(req.actualWeightKg).signum() <= 0) throw new Exception("مقدار و وزن خالص خروجی باید بیشتر از صفر باشند.");
        requirePrecision(req.outputQty,3,"مقدار خروجی");
        requirePrecision(req.actualWeightKg,3,"وزن خالص خروجی");
        if (req.ingredients.isEmpty()) throw new Exception("مواد اولیهٔ مصرفی ثبت نشده است.");
        if (req.idempotencyKey == null || req.idempotencyKey.trim().length() < 12) throw new Exception("شناسهٔ یکتای عملیات تولید معتبر نیست.");
        if (!c.getAutoCommit()) throw new Exception("ثبت قطعی تولید باید روی اتصال مستقل با autoCommit فعال آغاز شود.");
        preflight(c);
        boolean auto = true;
        c.setAutoCommit(false);
        try {
            lock(c, "meelano-production-post");
            String stamp = "[MEELANO-PROD:" + safe(req.idempotencyKey, 64) + "]";
            Long duplicate = existingByStamp(c, stamp);
            if (duplicate != null) {
                c.rollback();
                return new Posted(duplicate, 1, new Costing(), true);
            }
            Product output = product(c, req.finishedProductId, req.warehouseId, true);
            if (output == null) throw new Exception("کالای نهایی در آتیران پیدا نشد یا غیرفعال است.");
            requireSingleVariety(output);
            if (req.ingredients.isEmpty()) throw new Exception("فرمول مواد اولیه خالی است.");

            if (req.recipeId <= 0) throw new Exception("فرمول تولید معتبر انتخاب نشده است.");
            requireActiveRecipe(c, req.recipeId, req.finishedProductId);
            requireRecipeIngredientsPresent(c, req.recipeId, req.ingredients);
            BigDecimal outputPieces = outputQtyPieces(output, req.outputQty);
            if (outputPieces.signum() <= 0) throw new Exception("مقدار خروجی با واحد کالای آتیران سازگار نیست.");
            Costing costing = new Costing();
            List<Move> moves = new ArrayList<>();
            Map<Long, BigDecimal> requiredByProduct = sumIngredientQuantities(req.ingredients);
            for (Map.Entry<Long, BigDecimal> required : requiredByProduct.entrySet()) {
                if (required.getKey() == req.finishedProductId) throw new Exception("کالای نهایی نمی‌تواند مادهٔ اولیهٔ خودش باشد.");
                Product raw = product(c, required.getKey(), req.warehouseId, true);
                if (raw == null) throw new Exception("یکی از مواد اولیه غیرفعال یا در آتیران ناموجود است.");
                requireSingleVariety(raw);
                if (raw.withProductionSerial) throw new Exception("مادهٔ اولیهٔ «" + raw.name + "» ردیابی سری دارد؛ برای جلوگیری از مغایرت، تا انتخاب سری واقعی مصرف آن را ثبت نمی‌کنم.");
                BigDecimal totalQty = required.getValue();
                if (raw.stockPieces.compareTo(totalQty) < 0) throw new Exception("موجودی «" + raw.name + "» در انبار انتخاب‌شده برای مجموع مصرف " + totalQty.toPlainString() + " کافی نیست.");
                BigDecimal unitCost = latestAllInPurchaseUnitCost(c, raw.id, raw.varietyId);
                if (unitCost.signum() <= 0) throw new Exception("بهای خرید معتبر برای «" + raw.name + "» پیدا نشد؛ تولید ثبت نشد.");
                BigDecimal lineCost = unitCost.multiply(totalQty).setScale(4, RoundingMode.HALF_UP);
                costing.materialCost = costing.materialCost.add(lineCost);
                moves.add(new Move(raw, ACT_RAW_CONSUMED, totalQty, unitCost));
            }
            for (ExtraCost extra : req.costs) {
                if (extra == null || extra.costId <= 0 || nz(extra.amount).signum() < 0) throw new Exception("یکی از هزینه‌های سربار معتبر نیست.");
                requirePrecision(extra.amount,4,"مبلغ سربار");
                if (extra.amount.signum() == 0) continue;
                requireActiveCost(c, extra.costId);
                costing.overheadCost = costing.overheadCost.add(extra.amount);
            }
            costing.totalCost = costing.materialCost.add(costing.overheadCost).setScale(4, RoundingMode.HALF_UP);
            costing.costPerUnit = costing.totalCost.divide(req.outputQty, 4, RoundingMode.HALF_UP);
            costing.costPerKg = costing.totalCost.divide(req.actualWeightKg, 4, RoundingMode.HALF_UP);
            costing.finishedUnitPrice = costing.totalCost.divide(outputPieces, 4, RoundingMode.HALF_UP);
            if (costing.totalCost.signum() <= 0) throw new Exception("بهای تمام‌شدهٔ محصول صفر است؛ ثبت متوقف شد.");
            long id = nextProductionId(c);
            int revision = 1;
            String day = Jalali.todayStr(); String time = nowTime();
            String description = safe(req.description, 420) + (req.description == null || req.description.trim().isEmpty() ? "" : " ") + stamp;
            insertProduction(c, id, revision, req, userId, output, day, time, description);
            insertCosts(c, id, revision, req.costs);

            // The manager explicitly requested no serial records for manufactured output.
            // Do not delete old series rows; only disable the item-level requirement.
            try (PreparedStatement ps = c.prepareStatement("UPDATE dbo.inventory SET WithProductionSerial=0,ProductionPrice=? WHERE shka=?")) {
                ps.setBigDecimal(1, money4(costing.finishedUnitPrice)); ps.setLong(2, output.id);
                if (ps.executeUpdate() != 1) throw new Exception("تنظیم کالای نهایی در آتیران به‌روزرسانی نشد.");
            }
            for (Move m : moves) insertMovement(c, m.product, req.warehouseId, id, revision, userId, day, time, m.actId, m.qtyPieces, m.unitCost);
            insertMovement(c, output, req.warehouseId, id, revision, userId, day, time, ACT_FINISHED_OUTPUT, outputPieces, costing.finishedUnitPrice);

            // Use Atiran's own accounting entry point; it posts only when setting 67 enables it.
            postAtiranAccounting(c, id, userId, req.warehouseId, req.costs);

            // These native procedures are the source of truth for aggregate inventory balances.
            for (Move m : moves) refreshStock(c, m.product);
            refreshStock(c, output);
            c.commit();
            return new Posted(id, revision, costing, false);
        } catch (Exception e) {
            try { if (!c.getAutoCommit()) c.rollback(); } catch (Exception ignored) { }
            throw e;
        } finally { if (auto) try { c.setAutoCommit(true); } catch (Exception ignored) { } }
    }

    /** Read-only cost preview; uses the same purchase valuation as the posting transaction. */
    public static Costing estimate(Connection c, Request req) throws Exception {
        if (req == null || req.finishedProductId <= 0 || req.warehouseId <= 0 || req.ingredients.isEmpty())
            throw new Exception("اطلاعات پیش‌نمایش تولید کامل نیست.");
        if (nz(req.outputQty).signum() <= 0 || nz(req.actualWeightKg).signum() <= 0)
            throw new Exception("مقدار و وزن خالص خروجی باید بیشتر از صفر باشند.");
        requirePrecision(req.outputQty,3,"مقدار خروجی");
        requirePrecision(req.actualWeightKg,3,"وزن خالص خروجی");
        if (req.recipeId <= 0) throw new Exception("فرمول تولید معتبر انتخاب نشده است.");
        requireActiveRecipe(c, req.recipeId, req.finishedProductId);
        requireRecipeIngredientsPresent(c, req.recipeId, req.ingredients);
        Product output = product(c, req.finishedProductId, req.warehouseId, false);
        if (output == null) throw new Exception("کالای نهایی در آتیران پیدا نشد.");
        requireSingleVariety(output);
        Costing result = new Costing();
        for (Map.Entry<Long, BigDecimal> required : sumIngredientQuantities(req.ingredients).entrySet()) {
            if (required.getKey() == req.finishedProductId) throw new Exception("کالای نهایی نمی‌تواند مادهٔ اولیهٔ خودش باشد.");
            Product raw = product(c, required.getKey(), req.warehouseId, false);
            if (raw == null) throw new Exception("مادهٔ اولیه در آتیران پیدا نشد.");
            requireSingleVariety(raw);
            if (raw.withProductionSerial) throw new Exception("مادهٔ اولیهٔ «" + raw.name + "» ردیابی سری دارد و قابل پیش‌نمایش برای ثبت نیست.");
            if (raw.stockPieces.compareTo(required.getValue()) < 0) throw new Exception("موجودی «" + raw.name + "» برای مجموع مصرف کافی نیست.");
            BigDecimal unit = latestAllInPurchaseUnitCost(c, raw.id, raw.varietyId);
            if (unit.signum() <= 0) throw new Exception("بهای خرید معتبر برای «" + raw.name + "» پیدا نشد.");
            result.materialCost = result.materialCost.add(unit.multiply(required.getValue()));
        }
        for (ExtraCost cost : req.costs) {
            if (cost == null || cost.costId <= 0 || nz(cost.amount).signum() < 0)
                throw new Exception("یکی از هزینه‌های سربار معتبر نیست.");
            requirePrecision(cost.amount,4,"مبلغ سربار");
            if (cost.amount.signum() > 0) requireActiveCost(c, cost.costId);
            result.overheadCost = result.overheadCost.add(nz(cost.amount));
        }
        result.totalCost = result.materialCost.add(result.overheadCost).setScale(4, RoundingMode.HALF_UP);
        result.costPerUnit = result.totalCost.divide(req.outputQty, 4, RoundingMode.HALF_UP);
        result.costPerKg = result.totalCost.divide(req.actualWeightKg, 4, RoundingMode.HALF_UP);
        BigDecimal pieces = outputQtyPieces(output, req.outputQty);
        if (pieces.signum() <= 0) throw new Exception("واحد کالای نهایی معتبر نیست.");
        result.finishedUnitPrice = result.totalCost.divide(pieces, 4, RoundingMode.HALF_UP);
        return result;
    }

    /** Combine repeated rows for the same raw item before stock checks and ledger posting. */
    public static Map<Long, BigDecimal> sumIngredientQuantities(List<Ingredient> ingredients) throws Exception {
        if (ingredients == null || ingredients.isEmpty()) throw new Exception("مواد اولیهٔ مصرفی ثبت نشده است.");
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.productId <= 0 || nz(ingredient.qtyPieces).signum() <= 0)
                throw new Exception("مقدار یکی از مواد اولیه معتبر نیست.");
            requirePrecision(ingredient.qtyPieces,3,"مقدار مصرف مادهٔ اولیه");
            totals.merge(ingredient.productId, ingredient.qtyPieces, BigDecimal::add);
        }
        return totals;
    }

    /** Formula line quantities are multiplied by output/base-output; costs likewise. */
    public static BigDecimal scale(BigDecimal baseQty, BigDecimal outputQty, BigDecimal baseOutputQty) throws Exception {
        if (nz(baseOutputQty).signum() <= 0) throw new Exception("خروجی پایهٔ فرمول معتبر نیست.");
        return nz(baseQty).multiply(nz(outputQty)).divide(baseOutputQty, 3, RoundingMode.HALF_UP);
    }

    private static final class Move {
        final Product product; final int actId; final BigDecimal qtyPieces; final BigDecimal unitCost;
        Move(Product product, int actId, BigDecimal qtyPieces, BigDecimal unitCost) {
            this.product = product; this.actId = actId; this.qtyPieces = qtyPieces; this.unitCost = unitCost;
        }
    }

    /** Live item + selected-warehouse balance, used to refresh an in-progress UI draft. */
    public static Product productAtWarehouse(Connection c, long id, int warehouseId) throws Exception {
        return product(c, id, warehouseId, false);
    }

    private static Product product(Connection c, long id, int warehouseId, boolean lock) throws Exception {
        String hints = lock ? " WITH (UPDLOCK,HOLDLOCK)" : "";
        String q = "SELECT i.shka,i.naka,i.vahsanj,ISNULL(NULLIF(i.mohvah,0),1)," +
                "CAST(ISNULL(ia.mojkavah,0)*ISNULL(NULLIF(i.mohvah,0),1)+ISNULL(ia.mojkajoz,0) AS decimal(18,3))," +
                "ISNULL(i.vahwe,0),ISNULL(i.WithProductionSerial,0),ISNULL(v.varietyId,0),ISNULL(v.varietyCount,0) " +
                "FROM dbo.inventory i" + hints + " LEFT JOIN dbo.inventory_anbars ia" + hints + " ON ia.shka=i.shka AND ia.rdf_anbars=? " +
                "OUTER APPLY (SELECT MIN(VarietyID) AS varietyId,COUNT(*) AS varietyCount FROM dbo.Variety WHERE Shka=i.shka) v " +
                "WHERE i.shka=? AND i.active='t'";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, warehouseId); ps.setLong(2, id);
            try (ResultSet r = ps.executeQuery()) {
                if (!r.next()) return null;
                return new Product(r.getLong(1), r.getString(2), r.getString(3), r.getLong(4), r.getBigDecimal(5), r.getBigDecimal(6), r.getBoolean(7), r.getInt(8), r.getInt(9));
            }
        }
    }

    /**
     * All-in unit cost from the latest active purchase receipt. Item-level
     * tamam_joz_tax includes line discount/tax; the invoice header delta
     * (buyfact.all minus extended line costs) is allocated proportionally by
     * line value, so recorded freight/fees/discounts reconcile to header all.
     */
    private static BigDecimal latestAllInPurchaseUnitCost(Connection c, long shka, int varietyId) throws Exception {
        String q = "SELECT TOP (1) b.shfackh,b.rdf__,b.[all],s.rdf, " +
                "CAST((s.TEDVAH*ISNULL(NULLIF(i.mohvah,0),1)+ISNULL(s.TEDJOZ,0)) AS decimal(18,3)) AS pieces, " +
                "ISNULL(s.tamam_joz_tax,s.tamam_joz) AS unitLineCost " +
                "FROM dbo.subbuyfact s JOIN dbo.buyfact b ON b.shfackh=s.shfackh AND b.rdf__=s.rdf__ " +
                "JOIN dbo.inventory i ON i.shka=s.shka " +
                "WHERE s.shka=? AND s.VarietyID=? AND s.active='t' AND b.active='t' " +
                "ORDER BY b.[DATE] DESC,b.shfackh DESC,b.rdf__ DESC,s.rdf DESC";
        long bill = 0; int revision = 0; BigDecimal headerAll = BigDecimal.ZERO, linePieces = BigDecimal.ZERO, lineUnit = BigDecimal.ZERO;
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setLong(1, shka); ps.setInt(2, varietyId);
            try (ResultSet r = ps.executeQuery()) {
                if (!r.next()) throw new Exception("آخرین خرید فعال برای مادهٔ اولیه در آتیران پیدا نشد.");
                bill = r.getLong(1); revision = r.getInt(2); headerAll = nz(r.getBigDecimal(3));
                linePieces = nz(r.getBigDecimal(5)); lineUnit = nz(r.getBigDecimal(6));
            }
        }
        if (linePieces.signum() <= 0 || lineUnit.signum() <= 0) throw new Exception("مقدار/بهای آخرین خرید مادهٔ اولیه معتبر نیست.");
        BigDecimal lineTotal = BigDecimal.ZERO;
        String sumQ = "SELECT SUM(CAST((s.TEDVAH*ISNULL(NULLIF(i.mohvah,0),1)+ISNULL(s.TEDJOZ,0))*ISNULL(s.tamam_joz_tax,s.tamam_joz) AS decimal(28,4))) " +
                "FROM dbo.subbuyfact s JOIN dbo.inventory i ON i.shka=s.shka " +
                "WHERE s.shfackh=? AND s.rdf__=? AND s.active='t'";
        try (PreparedStatement ps = c.prepareStatement(sumQ)) {
            ps.setLong(1, bill); ps.setInt(2, revision);
            try (ResultSet r = ps.executeQuery()) { if (r.next()) lineTotal = nz(r.getBigDecimal(1)); }
        }
        if (lineTotal.signum() <= 0) throw new Exception("جمع اقلام آخرین فاکتور خرید صفر است؛ بهای تمام‌شده قابل محاسبه نیست.");
        BigDecimal allocation = headerAll.divide(lineTotal, 8, RoundingMode.HALF_UP);
        return lineUnit.multiply(allocation).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal outputQtyPieces(Product p, BigDecimal qty) {
        return nz(qty).multiply(BigDecimal.valueOf(Math.max(1, p.packSize))).setScale(3, RoundingMode.HALF_UP);
    }

    private static void insertProduction(Connection c, long id, int revision, Request req, int uid,
                                         Product output, String day, String time, String description) throws Exception {
        String q = "INSERT INTO produce.Production(ID,Rdf_,FormulID,shka,TedadVahed,TedBastebandi,DoneDate,DoneTime,Description,UserId,Active,rdf_anbar,dateProduction,IsManual,IsConfirm,DateConfirm,TimeConfirm,UserIdConfirm,IsConfirmAcc) " +
                "VALUES(?,?,?,?,?,0,?,?,?,?,1,?,?,1,1,?,?,?,0)";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            int i=1; ps.setLong(i++,id); ps.setInt(i++,revision); ps.setLong(i++,req.recipeId);
            ps.setLong(i++,output.id); ps.setBigDecimal(i++,qty3(req.outputQty));
            ps.setString(i++,day); ps.setString(i++,time); ps.setString(i++,description);
            ps.setInt(i++,uid); ps.setInt(i++,req.warehouseId); ps.setString(i++,day);
            ps.setString(i++,day); ps.setString(i++,time); ps.setInt(i,uid);
            ps.executeUpdate();
        }
    }

    private static void insertCosts(Connection c, long id, int revision, List<ExtraCost> costs) throws Exception {
        if (costs == null || costs.isEmpty()) return;
        String q = "INSERT INTO produce.ProductionCost(Rdf_,ProductionID,CostID,Price,Active) VALUES(?,?,?,?,1)";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            for (ExtraCost cost : costs) {
                if (cost == null || cost.costId <= 0 || nz(cost.amount).signum() <= 0) continue;
                ps.setInt(1,revision); ps.setLong(2,id); ps.setInt(3,cost.costId); ps.setBigDecimal(4,money4(cost.amount)); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Keep this column/value contract centralized; ka_act has non-null location, user, price and variety fields. */
    static String movementInsertSql() {
        return "INSERT INTO dbo.ka_act(shka,act_id,shfac,rdf__,act_dis,act_date,done_date,UserID,tedvah,tedjoz,"
                + "tedbastebandi,active,price,invepgh,invep,gain,RdfAnbar,ProductionSeriesID,PriceFinished,TamamJoz,VarietyID) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    }

    private static void insertMovement(Connection c, Product p, int warehouse, long productionId, int revision,
                                       int uid, String day, String time, int actId, BigDecimal pieces,
                                       BigDecimal unitCost) throws Exception {
        BigDecimal[] split = splitQty(pieces, p.packSize);
        String q = movementInsertSql();
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setLong(1, p.id);
            ps.setInt(2, actId);
            ps.setLong(3, productionId);
            ps.setInt(4, revision);
            ps.setString(5, "تولید میلانو");
            ps.setString(6, day);
            ps.setString(7, day);
            ps.setInt(8, uid);
            ps.setBigDecimal(9, split[0]);
            ps.setInt(10, split[1].setScale(0, RoundingMode.HALF_UP).intValueExact());
            ps.setBigDecimal(11, BigDecimal.ZERO);
            ps.setString(12, "t");
            ps.setBigDecimal(13, money4(unitCost));
            ps.setBigDecimal(14, money4(unitCost));
            ps.setBigDecimal(15, money4(unitCost));
            ps.setBigDecimal(16, BigDecimal.ZERO);
            ps.setInt(17, warehouse);
            ps.setNull(18, java.sql.Types.BIGINT); // ProductionSeriesID is deliberately not generated.
            ps.setBigDecimal(19, money4(unitCost));
            ps.setBigDecimal(20, money4(unitCost));
            ps.setInt(21, p.varietyId);
            ps.executeUpdate();
        }
    }

    /**
     * Invoke Atiran's actual accounting posting path after all production ledger rows exist.
     * Setting 67 is the same switch used by SabtSanadHesabdariFromAtiran; when disabled,
     * Atiran intentionally leaves the document unposted. The whole call remains in our
     * outer transaction so a failed accounting entry cannot leave half a production.
     */
    private static void postAtiranAccounting(Connection c, long productionId, int userId,
                                                int warehouseId, List<ExtraCost> costs) throws Exception {
        long enabled;
        try (PreparedStatement ps = c.prepareStatement("SELECT [value] FROM dbo.overal_setting WHERE id=67");
             ResultSet r = ps.executeQuery()) {
            if (!r.next()) throw new Exception("تنظیم حسابداری تولید آتیران (overal_setting.id=67) پیدا نشد؛ وضعیت سندداری نامشخص است.");
            enabled = r.getLong(1);
        }
        if (enabled != 1) return; // Match the official wrapper: no automatic document when disabled.
        try (PreparedStatement ps = c.prepareStatement("SELECT MoeinId FROM dbo.anbars WHERE rdf_anbar=? AND ISNULL(Active,1)=1")) {
            ps.setInt(1, warehouseId);
            try (ResultSet r = ps.executeQuery()) {
                if (!r.next() || r.getObject(1) == null)
                    throw new Exception("برای انبار انتخاب‌شده حساب معین آتیران تعریف نشده؛ سند تولید ساخته نشد.");
            }
        }
        if (costs != null) for (ExtraCost cost : costs) {
            if (cost == null || nz(cost.amount).signum() <= 0) continue;
            try (PreparedStatement ps = c.prepareStatement("SELECT MoeinID FROM produce.IndirectCost WHERE RowId=? AND Active=1")) {
                ps.setInt(1, cost.costId);
                try (ResultSet r = ps.executeQuery()) {
                    if (!r.next() || r.getObject(1) == null)
                        throw new Exception("برای سربار «"+cost.costId+"» معین حسابداری آتیران تعریف نشده؛ سند تولید ساخته نشد.");
                }
            }
        }
        if (productionId > Integer.MAX_VALUE) throw new Exception("شمارهٔ تولید برای سندداری آتیران خارج از محدوده است.");
        try (PreparedStatement ps = c.prepareStatement("EXEC dbo.SabtSanadHesabdariFromAtiran ?,?,?,?")) {
            ps.setInt(1, 54);
            ps.setInt(2, (int) productionId);
            ps.setInt(3, userId);
            ps.setInt(4, 1);
            ps.execute();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT TOP (1) 1 FROM accounting.Document WHERE Kind=54 AND Shfac=? AND Active=1")) {
            ps.setLong(1, productionId);
            try (ResultSet r = ps.executeQuery()) {
                if (!r.next()) throw new Exception("رویهٔ رسمی آتیران اجرا شد اما سند حسابداری تولید ایجاد نشد؛ تراکنش برگشت خورد.");
            }
        }
    }

    private static void refreshStock(Connection c, Product product) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("EXEC dbo.UpdateMojodiInventory ?")) { ps.setLong(1,product.id); ps.execute(); }
        try (PreparedStatement ps = c.prepareStatement("EXEC dbo.UpdateMojodiInventoryAnbars ?")) { ps.setLong(1,product.id); ps.execute(); }
        try (PreparedStatement ps = c.prepareStatement("EXEC dbo.UpdateMojodiInventoryAnbarVariety ?,?")) {
            ps.setLong(1,product.id); ps.setInt(2,product.varietyId); ps.execute();
        }
    }

    static void requireSingleVariety(Product p) throws Exception {
        if (p.varietyCount != 1 || p.varietyId <= 0)
            throw new Exception("کالای «" + p.name + "» در آتیران " + p.varietyCount + " تنوع ثبت‌شده دارد؛ برای جلوگیری از مغایرت باید تنوع دقیق آن انتخاب شود.");
    }

    private static void requireActiveRecipe(Connection c, long recipeId, long outputProductId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM produce.Formulation WHERE ID=? AND shka=? AND Active=1")) {
            ps.setLong(1,recipeId); ps.setLong(2,outputProductId);
            try (ResultSet r=ps.executeQuery()) { if (!r.next()) throw new Exception("فرمول فعال با کالای نهایی انتخاب‌شده هم‌خوان نیست."); }
        }
    }

    /** An inactive/missing formula item must not silently disappear from the ledger request. */
    private static void requireRecipeIngredientsPresent(Connection c, long recipeId, List<Ingredient> requested) throws Exception {
        Map<Long, BigDecimal> included = sumIngredientQuantities(requested);
        int found = 0;
        String q = "SELECT fi.shka,i.active,i.naka FROM produce.FormulationItems fi WITH (UPDLOCK,HOLDLOCK) " +
                "LEFT JOIN dbo.inventory i ON i.shka=fi.shka " +
                "WHERE fi.FormulID=? AND fi.Active=1";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setLong(1, recipeId);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    found++;
                    long productId = r.getLong(1);
                    String active = r.getString(2);
                    String name = r.getString(3);
                    if (active == null || !"t".equalsIgnoreCase(active.trim()))
                        throw new Exception("مادهٔ فرمول «"+(name==null?productId:name)+"» در فهرست فعال آتیران نیست؛ فرمول را اصلاح کنید.");
                    if (!included.containsKey(productId))
                        throw new Exception("مادهٔ فرمول «"+(name==null?productId:name)+"» در درخواست مصرف جا افتاده است.");
                }
            }
        }
        if (found == 0) throw new Exception("فرمول فعال هیچ مادهٔ اولیه‌ای ندارد؛ ثبت تولید متوقف شد.");
    }

    private static void requireActiveCost(Connection c, int id) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM produce.IndirectCost WHERE RowId=? AND Active=1")) {
            ps.setInt(1,id); try (ResultSet r=ps.executeQuery()) { if (!r.next()) throw new Exception("نوع هزینهٔ سربار در آتیران فعال نیست."); }
        }
    }

    private static long nextProductionId(Connection c) throws Exception {
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT ISNULL(MAX(ID),0)+1 FROM produce.Production WITH (UPDLOCK,HOLDLOCK)")) {
            if (!r.next()) throw new Exception("شمارهٔ تولید آتیران خوانده نشد.");
            long id=r.getLong(1); if (id<=0 || id>Integer.MAX_VALUE) throw new Exception("شمارهٔ تولید خارج از محدودهٔ سندداری آتیران است."); return id;
        }
    }

    private static Long existingByStamp(Connection c, String stamp) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT TOP (1) ID FROM produce.Production WHERE [Description] LIKE ? AND Active=1 ORDER BY ID DESC")) {
            ps.setString(1,"%"+stamp+"%"); try(ResultSet r=ps.executeQuery()){return r.next()?r.getLong(1):null;}
        }
    }

    private static void lock(Connection c, String resource) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("DECLARE @r int; EXEC @r=sp_getapplock @Resource=?,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=30000; SELECT @r")) {
            ps.setString(1,resource); try(ResultSet r=ps.executeQuery()){if(!r.next()||r.getInt(1)<0)throw new Exception("قفل ثبت تولید گرفته نشد؛ چند لحظه بعد دوباره تلاش کنید.");}
        }
    }

    private static void snapshotRecipe(Connection c, long id, int rev) throws Exception {
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO produce.HistoryFormulation(FormulID,Rdf_,Name,shka,Description,UserId,DoneDate,DoneTime) SELECT ID,?,Name,shka,Description,UserId,DoneDate,DoneTime FROM produce.Formulation WHERE ID=?")) {ps.setInt(1,rev);ps.setLong(2,id);ps.executeUpdate();}
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO produce.HistoryFormulationItems(FormulID,Rdf_,shka,TedadVahed,TedadJoz) SELECT FormulID,?,shka,TedadVahed,TedadJoz FROM produce.FormulationItems WHERE FormulID=? AND Active=1")) {ps.setInt(1,rev);ps.setLong(2,id);ps.executeUpdate();}
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO produce.HistoryFormulationCost(FormulID,Rdf_,Price,CostID) SELECT FormulID,?,Price,CostID FROM produce.FormulationCost WHERE FormulID=? AND Active=1")) {ps.setInt(1,rev);ps.setLong(2,id);ps.executeUpdate();}
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO dbo.meelano_production_profile_history(FormulID,Rdf_,BaseOutputQty,BaseWeightKg,UpdatedAt,UpdatedBy) " +
                "SELECT f.ID,?,ISNULL(p.BaseOutputQty,1),ISNULL(p.BaseWeightKg,0),ISNULL(p.UpdatedAt,SYSDATETIME()),ISNULL(p.UpdatedBy,f.UserId) " +
                "FROM produce.Formulation f LEFT JOIN dbo.meelano_production_profile p ON p.FormulID=f.ID WHERE f.ID=?")) {
            ps.setInt(1,rev);ps.setLong(2,id);
            if(ps.executeUpdate()!=1)throw new Exception("نسخهٔ پایهٔ بازدهٔ فرمول ذخیره نشد.");
        }
    }

    private static int nextRevision(Connection c, long id) throws Exception {
        try (PreparedStatement ps=c.prepareStatement("SELECT ISNULL(MAX(Rdf_),0)+1 FROM produce.HistoryFormulation WITH (UPDLOCK,HOLDLOCK) WHERE FormulID=?")) {ps.setLong(1,id);try(ResultSet r=ps.executeQuery()){return r.next()?r.getInt(1):1;}}
    }

    private static void requireColumns(Connection c,String schema,String table,String[] required)throws Exception{
        if(!tableExists(c,schema,table))throw new Exception("جدول «"+schema+"."+table+"» در دیتابیس نیست.");
        StringBuilder q=new StringBuilder("SELECT c.name FROM sys.columns c JOIN sys.objects o ON o.object_id=c.object_id JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE s.name=? AND o.name=?");
        java.util.Set<String> have=new java.util.HashSet<>();try(PreparedStatement ps=c.prepareStatement(q.toString())){ps.setString(1,schema);ps.setString(2,table);try(ResultSet r=ps.executeQuery()){while(r.next())have.add(r.getString(1).toLowerCase(java.util.Locale.US));}}
        for(String col:required)if(!have.contains(col.toLowerCase(java.util.Locale.US)))throw new Exception("ستون «"+col+"» در "+schema+"."+table+" نیست؛ ثبت تولید متوقف شد.");
    }
    private static void requireTable(Connection c,String schema,String table)throws Exception{if(!tableExists(c,schema,table))throw new Exception("جدول "+schema+"."+table+" در دیتابیس نیست.");}
    private static boolean tableExists(Connection c,String schema,String table)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE s.name=? AND o.name=? AND o.type='U'")){ps.setString(1,schema);ps.setString(2,table);try(ResultSet r=ps.executeQuery()){return r.next();}}}
    private static void requireProcedure(Connection c,String name)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM sys.procedures p JOIN sys.schemas s ON s.schema_id=p.schema_id WHERE s.name=N'dbo' AND p.name=?")){ps.setString(1,name);try(ResultSet r=ps.executeQuery()){if(!r.next())throw new Exception("رویهٔ رسمی "+name+" در آتیران موجود نیست.");}}}
    private static boolean triggerExists(Connection c,String schema,String trigger,String table)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM sys.triggers t JOIN sys.objects o ON o.object_id=t.parent_id JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE t.name=? AND s.name=? AND o.name=? AND t.is_disabled=0")){ps.setString(1,trigger);ps.setString(2,schema);ps.setString(3,table);try(ResultSet r=ps.executeQuery()){return r.next();}}}
    private static BigDecimal[] splitQty(BigDecimal pieces,long pack){BigDecimal p=nz(pieces);long m=Math.max(1,pack);if(m==1)return new BigDecimal[]{p.setScale(3,RoundingMode.HALF_UP),BigDecimal.ZERO};BigDecimal[] dr=p.divideAndRemainder(BigDecimal.valueOf(m));if(dr[1].scale()>0&&dr[1].stripTrailingZeros().scale()>0)return new BigDecimal[]{p.divide(BigDecimal.valueOf(m),3,RoundingMode.HALF_UP),BigDecimal.ZERO};return new BigDecimal[]{dr[0].setScale(3,RoundingMode.HALF_UP),dr[1].setScale(0,RoundingMode.HALF_UP)};}
    private static BigDecimal nz(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private static void requirePrecision(BigDecimal value,int scale,String label)throws Exception{
        if(nz(value).stripTrailingZeros().scale()>scale)throw new Exception(label+" بیش از "+scale+" رقم اعشار دارد؛ ثبت بدون گردکردن انجام می‌شود.");
    }
    private static BigDecimal qty3(BigDecimal v){return nz(v).setScale(3,RoundingMode.HALF_UP);}
    private static BigDecimal money4(BigDecimal v){return nz(v).setScale(4,RoundingMode.HALF_UP);}
    private static String safe(String s,int max){String v=s==null?"":s.replace('\u0000',' ').trim();return v.length()>max?v.substring(0,max):v;}
    private static String nowTime(){return new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.US).format(new java.util.Date());}
}
