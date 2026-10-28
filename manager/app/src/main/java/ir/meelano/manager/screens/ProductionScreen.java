package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Diag;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ProductionWriter;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.core.Sql;
import ir.meelano.manager.core.SmartLink;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.ui.Theme;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Manager-only production desk. Inventory posting is delegated to ProductionWriter. */
public final class ProductionScreen extends Screen {
    private static final int MAX_PICKER_ROWS = 60;

    private static final class Catalog {
        final List<ProductionWriter.Product> products;
        final List<ProductionWriter.Warehouse> warehouses;
        final List<ProductionWriter.Recipe> recipes;
        final List<ProductionWriter.CostType> costs;
        final ProductionWriter.Warehouse selectedWarehouse;
        final Map<Long, ProductionWriter.Product> selectedWarehouseProducts;
        Catalog(List<ProductionWriter.Product> p, List<ProductionWriter.Warehouse> w,
                List<ProductionWriter.Recipe> r, List<ProductionWriter.CostType> c,
                ProductionWriter.Warehouse selected, Map<Long, ProductionWriter.Product> refreshed) {
            products=p; warehouses=w; recipes=r; costs=c; selectedWarehouse=selected;
            selectedWarehouseProducts=refreshed;
        }
    }
    private static final class Line {
        ProductionWriter.Product product;
        BigDecimal baseQty;
        BigDecimal qty;
        boolean available = true;
        boolean requiredByRecipe;
        Line(ProductionWriter.Product p, BigDecimal base, BigDecimal actual) { product=p; baseQty=base; qty=actual; }
    }
    private static final class CostLine {
        final ProductionWriter.CostType type;
        BigDecimal baseAmount;
        BigDecimal amount;
        CostLine(ProductionWriter.CostType t, BigDecimal base, BigDecimal actual) { type=t; baseAmount=base; amount=actual; }
    }

    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final ExecutorService pickerFilter = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private Catalog catalog;
    private ProductionWriter.Product output;
    private ProductionWriter.Warehouse warehouse;
    private ProductionWriter.Recipe recipe;
    private final List<Line> ingredients = new ArrayList<>();
    private final List<CostLine> costs = new ArrayList<>();
    private EditText qtyEdit, weightEdit, descriptionEdit;
    // Preserve raw editor text across re-renders so edited ingredients stay in sync with the yield.
    private String outputQtyDraft;
    private String actualWeightDraft;
    private String descriptionDraft = "";
    private String idempotencyKey = "";
    private boolean busy;
    private int catalogLoadToken;

    public ProductionScreen(MainActivity a) { super(a); }
    @Override public String id() { return "production"; }
    @Override public String title() { return "تولید محصول"; }
    @Override public String glyph() { return Theme.isLight() ? "⚙" : "🏭"; }
    @Override public int accent() { return Theme.GOLD; }

    /** The approved 3D production emblem is exclusive to the dark gold skin. */
    private View productionHero(String subtitle) {
        LinearLayout card = a.kit.v();
        card.setBackground(Theme.hero(accent()));
        card.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(15));
        LinearLayout head = a.kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (!Theme.isLight() && !Theme.isNuts()) {
            android.widget.ImageView emblem = new android.widget.ImageView(a);
            emblem.setImageResource(R.drawable.icon3d_production);
            emblem.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            emblem.setContentDescription(null);
            head.addView(emblem, new LinearLayout.LayoutParams(Theme.dp(58), Theme.dp(58)));
        } else {
            head.addView(a.kit.icon(R.drawable.mi_warehouse, accent(), 30),
                    new LinearLayout.LayoutParams(Theme.dp(58), Theme.dp(58)));
        }
        head.addView(a.kit.space(10));
        LinearLayout copy = a.kit.v();
        copy.addView(a.kit.title(title()), a.kit.lp(-1, -2));
        if (subtitle != null && !subtitle.isEmpty()) copy.addView(a.kit.sub(subtitle), a.kit.lp(-1, -2));
        head.addView(copy, a.kit.wlp(1f));
        card.addView(head, a.kit.lp(-1, -2));
        return card;
    }

    /** Fail closed: the central nav gate and the screen itself both enforce manager-only access. */
    public static boolean managerAllowed(MainActivity a) {
        try {
            String session = AtiranAuth.sessionRole(a);
            return RoleStore.ADMIN.equals(session == null || session.isEmpty() ? RoleStore.current(a) : session);
        } catch (Exception e) { return false; }
    }

    @Override public void render(LinearLayout content) {
        captureFormDrafts();
        this.content=content;
        content.removeAllViews();
        if (!managerAllowed(a)) {
            content.addView(a.kit.empty("این بخش فقط برای مدیر است", "برای عملیات تولید با نقش مدیر وارد شوید."), a.kit.lp(-1,-2));
            return;
        }
        if (catalog == null) {
            content.addView(productionHero("فرمول، مواد اولیه، سربار و بهای تمام‌شده"), a.kit.lp(-1,-2));
            content.addView(a.kit.gap(10));
            content.addView(a.kit.loading("در حال بررسی ساختار تولید آتیران و دریافت اطلاعات…"), a.kit.lp(-1,-2));
            loadCatalog();
            return;
        }
        content.addView(productionHero("فقط مدیر • سری جدید ساخته نمی‌شود • ثبت با گردش رسمی انبار آتیران"), a.kit.lp(-1,-2));
        content.addView(a.kit.gap(10));
        content.addView(statusCard(), a.kit.lp(-1,-2));
        content.addView(a.kit.gap(8));
        content.addView(selectionCard(), a.kit.lp(-1,-2));
        content.addView(a.kit.gap(8));
        if (recipe != null && output != null) {
            content.addView(outputCard(), a.kit.lp(-1,-2));
            content.addView(a.kit.gap(8));
            content.addView(ingredientsCard(), a.kit.lp(-1,-2));
            content.addView(a.kit.gap(8));
            content.addView(costsCard(), a.kit.lp(-1,-2));
            content.addView(a.kit.gap(8));
            content.addView(actionCard(), a.kit.lp(-1,-2));
        } else {
            content.addView(a.kit.gap(4));
            if (recipe != null) {
                content.addView(a.kit.empty("کالای نهایی در انبار/فهرست فعال پیدا نشد",
                        "فرمول انتخاب‌شده را بررسی کنید؛ تا تأیید کالای واقعی، ثبت تولید غیرفعال است."), a.kit.lp(-1,-2));
            } else {
                content.addView(emptySelectionCard(),a.kit.lp(-1,-2));
            }
        }
        content.addView(a.kit.gap(12));
    }

    @Override public void onShown() { if (catalog == null && managerAllowed(a)) loadCatalog(); }
    @Override public void onHidden() { /* No background refresh or writer survives navigation. */ }

    private void captureFormDrafts() {
        if (qtyEdit != null && qtyEdit.getText() != null) outputQtyDraft = qtyEdit.getText().toString();
        if (weightEdit != null && weightEdit.getText() != null) actualWeightDraft = weightEdit.getText().toString();
        if (descriptionEdit != null && descriptionEdit.getText() != null) descriptionDraft = descriptionEdit.getText().toString();
    }

    private void loadCatalog() {
        if (a.repo == null || !managerAllowed(a)) return;
        final int token = ++catalogLoadToken;
        final int requestedWarehouseId = warehouse == null ? 0 : warehouse.id;
        final List<Long> refreshIds = new ArrayList<>();
        if (output != null) refreshIds.add(output.id);
        else if (recipe != null) refreshIds.add(recipe.productId);
        for (Line line : ingredients) if (!refreshIds.contains(line.product.id)) refreshIds.add(line.product.id);
        a.repo.run(c -> {
            ProductionWriter.preflight(c);
            ProductionWriter.ensureProfileTable(c);
            List<ProductionWriter.Warehouse> warehouses = ProductionWriter.warehouses(c);
            ProductionWriter.Warehouse selected = null;
            for (ProductionWriter.Warehouse candidate : warehouses)
                if (candidate.id == requestedWarehouseId) { selected = candidate; break; }
            if (selected == null && !warehouses.isEmpty()) selected = warehouses.get(0);
            List<ProductionWriter.Product> products = selected == null
                    ? new ArrayList<>() : ProductionWriter.products(c, selected.id);
            Map<Long, ProductionWriter.Product> refreshed = new HashMap<>();
            if (selected != null) for (Long id : refreshIds) {
                ProductionWriter.Product product = ProductionWriter.productAtWarehouse(c, id, selected.id);
                if (product != null) refreshed.put(id, product);
            }
            return new Catalog(products, warehouses, ProductionWriter.recipes(c), ProductionWriter.costTypes(c),
                    selected, refreshed);
        }, new Repo.Cb<Catalog>() {
            @Override public void ok(Catalog value) {
                if (token != catalogLoadToken) return;
                catalog=value;
                warehouse=value.selectedWarehouse;
                if (output != null) output=value.selectedWarehouseProducts.get(output.id);
                else if (recipe != null) output=value.selectedWarehouseProducts.get(recipe.productId);
                for (Line line : ingredients) {
                    ProductionWriter.Product current=value.selectedWarehouseProducts.get(line.product.id);
                    line.available=current!=null;
                    if (current!=null) line.product=current;
                }
                render(content);
            }
            @Override public void fail(String error) {
                if (token != catalogLoadToken) return;
                content.removeAllViews();
                content.addView(productionHero("ساختار دیتابیس باید با پایگاه آتیران بررسی شود"), a.kit.lp(-1,-2));
                content.addView(a.kit.gap(8));
                LinearLayout errorCard=a.kit.card(Theme.WARNING);
                errorCard.addView(a.kit.text("تولید فعلاً متوقف است",14f,Theme.TEXT,true),a.kit.lp(-1,-2));
                errorCard.addView(a.kit.hint(error),a.kit.lp(-1,-2));
                content.addView(errorCard,a.kit.lp(-1,-2));
                content.addView(a.kit.btnGhost("تلاش دوباره", Theme.GOLD, v -> { catalog=null; render(content); }), a.kit.lp(-1,-2));
            }
        });
    }

    private View statusCard() {
        LinearLayout card=a.kit.card(Theme.SUCCESS);
        card.addView(a.kit.text("گردش تولید آتیران • مطابق Mili.sql",14f,Theme.TEXT,true),a.kit.lp(-1,-2));
        card.addView(a.kit.hint("در این فرم، مصرف مواد با گردش ۸۵ و ورود محصول نهایی با گردش ۸۶ ثبت می‌شود؛ بازگشت مواد با گردش ۸۷ فعلاً در فرم پشتیبانی نمی‌شود. موجودی از روی ka_act و رویه‌های رسمی آتیران بازسازی می‌شود."),a.kit.lp(-1,-2));
        card.addView(a.kit.hint("برای حسابداری، پس از ثبت گردش‌ها رویهٔ رسمی سندداری آتیران فراخوانی می‌شود (در صورت فعال‌بودن تنظیم سند خودکار). سری جدید ساخته نمی‌شود؛ مواد سری‌دار و کالاهایی که دقیقاً یک تنوع ثبت‌شده ندارند، تا انتخاب قطعی متوقف می‌شوند."),a.kit.lp(-1,-2));
        return card;
    }

    private View emptySelectionCard(){
        LinearLayout card=a.kit.card(Theme.INFO);
        LinearLayout row=a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(a.kit.icon(R.drawable.mi_inventory_2,Theme.INFO,26),a.kit.lp(Theme.dp(32),Theme.dp(32)));
        row.addView(a.kit.space(9));
        LinearLayout copy=a.kit.v();
        copy.addView(a.kit.text("برای شروع، یک فرمول تولید انتخاب کنید",13f,Theme.TEXT,true),a.kit.lp(-1,-2));
        copy.addView(a.kit.text("یا «فرمول جدید» را بزنید و مواد اولیه و بازده پایه را تعریف کنید.",11f,Theme.MUTED,false),a.kit.lp(-1,-2));
        row.addView(copy,a.kit.wlp(1f));
        card.addView(row,a.kit.lp(-1,-2));
        return card;
    }

    private View selectionCard() {
        LinearLayout card=a.kit.card(Theme.VIOLET);
        card.addView(a.kit.text("فرمول و انبار",14f,Theme.TEXT,true),a.kit.lp(-1,-2));
        card.addView(a.kit.text("فرمول: "+(recipe==null?"انتخاب نشده":recipe.name+" • "+recipe.productName),12.5f,Theme.TEXT,true),a.kit.lp(-1,-2));
        card.addView(a.kit.text("انبار: "+(warehouse==null?"انتخاب نشده":warehouse.name),12.5f,Theme.MUTED,false),a.kit.lp(-1,-2));
        LinearLayout row=a.kit.h();
        row.addView(a.kit.btn("انتخاب فرمول",v->pickRecipe()),a.kit.wlp(1f));
        row.addView(a.kit.space(6));
        row.addView(a.kit.btnGhost("فرمول جدید",Theme.SUCCESS,v->editRecipe(null)),a.kit.wlp(1f));
        card.addView(row,a.kit.lp(-1,-2));
        LinearLayout row2=a.kit.h();
        row2.addView(a.kit.btnGhost("ویرایش فرمول",Theme.GOLD,v->{if(recipe==null)a.kit.toast("ابتدا فرمول را انتخاب کنید");else editRecipe(recipe);}),a.kit.wlp(1f));
        row2.addView(a.kit.space(6));
        row2.addView(a.kit.btnGhost("انتخاب انبار",Theme.INFO,v->pickWarehouse()),a.kit.wlp(1f));
        card.addView(row2,a.kit.lp(-1,-2));
        return card;
    }

    private View outputCard() {
        LinearLayout card=a.kit.card(Theme.GOLD);
        card.addView(a.kit.text("محصول و خروجی",14f,Theme.TEXT,true),a.kit.lp(-1,-2));
        card.addView(a.kit.text("کالای نهایی: "+output.name+" • واحد: "+output.unit+" • موجودی انبار «"+warehouse.name+"»: "+qty(output.stockPieces)+" قطعهٔ پایه",12f,Theme.TEXT,true),a.kit.lp(-1,-2));
        qtyEdit=a.kit.edit("مقدار خروجی در واحد بسته‌بندی",outputQtyDraft==null?qtyText(recipe.baseOutputQty):outputQtyDraft);
        qtyEdit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        weightEdit=a.kit.edit("وزن خالص تولیدشده (کیلوگرم)",actualWeightDraft==null?qtyText(defaultWeight()):actualWeightDraft);
        weightEdit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        card.addView(qtyEdit,a.kit.lp(-1,-2)); card.addView(a.kit.gap(6)); card.addView(weightEdit,a.kit.lp(-1,-2));
        card.addView(a.kit.hint("مقادیر فرمول برای خروجی پایه ذخیره می‌شوند. مقدار و وزن واقعی هر نوبت قابل اصلاح است؛ هزینهٔ هر واحد و هر کیلو از خروجی واقعی محاسبه می‌شود."),a.kit.lp(-1,-2));
        return card;
    }

    private View ingredientsCard() {
        LinearLayout card=a.kit.card(Theme.INFO);
        card.addView(a.kit.text("مواد اولیهٔ مصرفی • "+Money.fa(String.valueOf(ingredients.size())),14f,Theme.TEXT,true),a.kit.lp(-1,-2));
        card.addView(a.kit.hint("مواد اصلی فرمول حذف‌پذیر نیستند؛ مقدار مصرف واقعی قابل ویرایش است. مواد اضافه فقط با انتخاب صریح ثبت می‌شوند."),a.kit.lp(-1,-2));
        if(ingredients.isEmpty()) card.addView(a.kit.hint("برای این فرمول ماده‌ای ثبت نشده است."),a.kit.lp(-1,-2));
        for(int i=0;i<ingredients.size();i++){
            final int index=i; Line line=ingredients.get(i);
            boolean supported=line.available&&!line.product.withProductionSerial&&line.product.varietyCount==1;
            String stockLabel;
            if(!line.available) stockLabel="این ماده در فهرست فعال آتیران پیدا نشد؛ ثبت متوقف است";
            else if(line.product.withProductionSerial) stockLabel="ماده سری‌دار است؛ انتخاب سری واقعی در این فرم انجام نمی‌شود";
            else if(line.product.varietyCount!=1) stockLabel="تعداد تنوع آتیران دقیقاً یک نیست؛ ثبت متوقف است";
            else stockLabel="موجودی انبار «"+(warehouse==null?"—":warehouse.name)+"»: "+qty(line.product.stockPieces)+" قطعهٔ پایه • "+line.product.unit;
            View row=a.kit.personRow(line.product.name,stockLabel,
                    qty(line.qty)+" قطعهٔ پایه","ویرایش",supported?Theme.INFO:Theme.DANGER,v->editIngredient(index));
            LinearLayout.LayoutParams lp=a.kit.lp(-1,-2); lp.setMargins(0,0,0,Theme.dp(6)); card.addView(row,lp);
        }
        LinearLayout row=a.kit.h();
        row.addView(a.kit.btn("افزودن مادهٔ خام",v->pickIngredient()),a.kit.wlp(1f));
        row.addView(a.kit.space(6));
        row.addView(a.kit.btnGhost("مقیاس مجدد از فرمول",Theme.GOLD,v->rescale()),a.kit.wlp(1f));
        card.addView(row,a.kit.lp(-1,-2));
        return card;
    }

    private View costsCard() {
        LinearLayout card=a.kit.card(Theme.WARNING);
        card.addView(a.kit.text("هزینه‌های سربار",14f,Theme.TEXT,true),a.kit.lp(-1,-2));
        if(costs.isEmpty()) card.addView(a.kit.hint("سرباری ثبت نشده؛ در صورت نیاز نوع و مبلغ را اضافه کنید."),a.kit.lp(-1,-2));
        for(int i=0;i<costs.size();i++){
            final int index=i; CostLine cost=costs.get(i);
            View row=a.kit.personRow(cost.type.name,"هزینهٔ این نوبت",money(cost.amount),"ویرایش",Theme.WARNING,v->editCost(index));
            LinearLayout.LayoutParams lp=a.kit.lp(-1,-2); lp.setMargins(0,0,0,Theme.dp(6)); card.addView(row,lp);
        }
        card.addView(a.kit.btnGhost("افزودن سربار دستی",Theme.WARNING,v->pickCostType()),a.kit.lp(-1,-2));
        return card;
    }

    private View actionCard() {
        LinearLayout card=a.kit.card(Theme.SUCCESS);
        descriptionEdit=a.kit.edit("شرح تولید (اختیاری)",descriptionDraft==null?"":descriptionDraft);
        card.addView(descriptionEdit,a.kit.lp(-1,-2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGhost("پیش‌نمایش بهای تمام‌شده",Theme.INFO,v->preview()),a.kit.lp(-1,-2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGold(busy?"در حال ثبت…":"ثبت قطعی تولید و به‌روزرسانی موجودی",v->post()),a.kit.lp(-1,-2));
        card.addView(a.kit.hint("اگر با حساب محلی وارد شده‌اید، پیش از ثبت نام کاربری و رمز مدیر آتیران برای انتساب سند به user_id واقعی یک‌بار پرسیده می‌شود؛ رمز ذخیره نمی‌شود."),a.kit.lp(-1,-2));
        return card;
    }

    private void pickWarehouse(){
        if(catalog.warehouses.isEmpty()){a.kit.toast("انبار فعال در آتیران پیدا نشد");return;}
        showSearchPicker("انتخاب انبار مواد و محصول",catalog.warehouses,new PickerContent<ProductionWriter.Warehouse>(){
            @Override public String searchText(ProductionWriter.Warehouse w){return w.name+" "+w.id;}
            @Override public View row(ProductionWriter.Warehouse w,View.OnClickListener click){
                View row=a.kit.personRow(bidi(w.name),"انبار فعال آتیران",isolateLtr(Money.fa(String.valueOf(w.id))),"انتخاب",Theme.INFO,click);
                row.setContentDescription("انبار "+w.name+"، شناسه "+Money.fa(String.valueOf(w.id)));
                return row;
            }
        },w->{warehouse=w;catalog=null;render(content);});
    }

    private void pickRecipe(){
        if(catalog.recipes.isEmpty()){a.kit.toast("فرمول فعالی ثبت نشده است؛ ابتدا فرمول بسازید");return;}
        List<ProductionWriter.Recipe> supported = new ArrayList<>();
        for (ProductionWriter.Recipe candidate : catalog.recipes) {
            ProductionWriter.Product product = findProduct(candidate.productId);
            if (product != null && product.varietyCount == 1) supported.add(candidate);
        }
        if (supported.isEmpty()) { a.kit.toast("فرمولی با کالای نهاییِ دارای دقیقاً یک تنوع ثبت‌شده پیدا نشد."); return; }
        showSearchPicker("انتخاب فرمول تولید",supported,new PickerContent<ProductionWriter.Recipe>(){
            @Override public String searchText(ProductionWriter.Recipe r){return r.name+" "+r.productName+" "+r.id;}
            @Override public View row(ProductionWriter.Recipe r,View.OnClickListener click){
                String detail=r.name.trim().equalsIgnoreCase(r.productName.trim())
                        ? "بازده پایه: "+qty(r.baseOutputQty)+" واحد"
                        : "محصول نهایی: "+r.productName+" • بازده پایه: "+qty(r.baseOutputQty);
                View row=a.kit.personRow(bidi(r.name),bidi(detail),isolateLtr(Money.fa(String.valueOf(r.id))),"انتخاب",Theme.GOLD,click);
                row.setContentDescription("فرمول "+r.name+"، محصول نهایی "+r.productName+"، شناسه "+Money.fa(String.valueOf(r.id)));
                return row;
            }
        },this::selectRecipe);
    }

    private interface PickerContent<T>{
        String searchText(T item);
        View row(T item,View.OnClickListener onClick);
    }

    /** Searchable, theme-aware picker with RTL-safe labels; avoids the stock Android list's cramped bidi layout. */
    private <T> void showSearchPicker(String title,List<T> items,PickerContent<T> contentFactory,Consumer<T> onPick){
        showSearchPicker(title,items,contentFactory,"",onPick);
    }

    private <T> void showSearchPicker(String title,List<T> items,PickerContent<T> contentFactory,String note,Consumer<T> onPick){
        LinearLayout root=a.kit.v();
        root.setPadding(Theme.dp(14),Theme.dp(8),Theme.dp(14),Theme.dp(10));
        if(note!=null&&!note.isEmpty())root.addView(a.kit.hint(note),a.kit.lp(-1,-2));
        EditText search=a.kit.edit("جست‌وجو بر اساس نام یا شناسه…","");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextDirection(View.TEXT_DIRECTION_RTL);
        root.addView(search,a.kit.lp(-1,-2));
        TextView count=a.kit.text("",10.5f,Theme.MUTED,false);
        count.setGravity(Gravity.END);
        root.addView(count,a.kit.lp(-1,-2));
        root.addView(a.kit.gap(5));
        LinearLayout rows=a.kit.v();
        final AlertDialog[] dialogRef=new AlertDialog[1];
        final int[] generation={0};
        final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] pending={null};

        java.util.function.IntConsumer refresh=token->{
            final String key=Sql.searchKey(search.getText()==null?"":search.getText().toString());
            pickerFilter.execute(()->{
                List<T> matches=new ArrayList<>();
                int totalMatches=0;
                if(key.isEmpty()){
                    totalMatches=items.size();
                    for(T item:items){
                        if(item==null)continue;
                        if(matches.size()>=MAX_PICKER_ROWS)break;
                        matches.add(item);
                    }
                }else{
                    for(T item:items){
                        if(item==null||!Sql.searchKey(contentFactory.searchText(item)).contains(key))continue;
                        totalMatches++;
                        if(matches.size()<MAX_PICKER_ROWS)matches.add(item);
                    }
                }
                final int resultCount=totalMatches;
                a.runOnUiThread(()->{
                    if(token!=generation[0]||dialogRef[0]==null||!dialogRef[0].isShowing())return;
                    rows.removeAllViews();
                    for(T item:matches){
                        try{
                            View row=contentFactory.row(item,v->{
                                if(dialogRef[0]!=null)dialogRef[0].dismiss();
                                onPick.accept(item);
                            });
                            LinearLayout.LayoutParams lp=a.kit.lp(-1,-2);
                            lp.bottomMargin=Theme.dp(5);
                            rows.addView(row,lp);
                        }catch(RuntimeException e){Diag.ignored("ProductionScreen:pickerRow",e);}
                    }
                    if(resultCount==0)rows.addView(a.kit.empty(key.isEmpty()?"فهرست کالا خالی است":"موردی با این عبارت پیدا نشد",null),a.kit.lp(-1,-2));
                    if(resultCount>matches.size())
                        count.setText("نمایش "+Money.fa(String.valueOf(matches.size()))+" مورد از "+Money.fa(String.valueOf(resultCount))+" نتیجه؛ برای محدودکردن فهرست جست‌وجو کنید.");
                    else count.setText(Money.fa(String.valueOf(resultCount))+" مورد");
                });
            });
        };
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                if(pending[0]!=null)handler.removeCallbacks(pending[0]);
                int token=++generation[0];
                pending[0]=()->refresh.accept(token);
                handler.postDelayed(pending[0],180);
            }
            @Override public void afterTextChanged(android.text.Editable e){}
        });
        root.addView(a.kit.scrollWrap(rows,430),a.kit.lp(-1,-2));
        root.addView(a.kit.gap(4));
        root.addView(a.kit.btnGhost("بستن",Theme.MUTED,v->{if(dialogRef[0]!=null)dialogRef[0].dismiss();}),a.kit.lp(-1,-2));
        dialogRef[0]=a.kit.dialog(title,root,true);
        dialogRef[0].setOnDismissListener(d->{
            generation[0]++;
            if(pending[0]!=null)handler.removeCallbacks(pending[0]);
        });
        dialogRef[0].show();
        refresh.accept(++generation[0]);
    }

    private TextView dialogTitle(String title){
        TextView view=a.kit.text(title,16f,Theme.TEXT,true);
        view.setGravity(Gravity.CENTER);
        int pad=Theme.dp(16);
        view.setPadding(pad,pad,pad,Theme.dp(6));
        return view;
    }

    private void styleDialog(AlertDialog dialog){
        try{
            if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(Theme.dialogBg());
            if(dialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Theme.GOLD);
            if(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)!=null)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Theme.MUTED);
        }catch(Exception ignored){Diag.ignored("ProductionScreen:dialogTheme",ignored);}
    }

    /** Keep mixed Persian/Latin product names in their proper visual order in RTL rows. */
    private static String bidi(String value){
        return android.text.BidiFormatter.getInstance(true).unicodeWrap(value==null?"":value,
                android.text.TextDirectionHeuristics.RTL,true);
    }

    /** Unicode isolates keep Latin/Persian digits in their intended order inside RTL rows. */
    private static String isolateLtr(String value){return "\u2066"+(value==null?"":value)+"\u2069";}

    private void selectRecipe(ProductionWriter.Recipe chosen){
        if(warehouse==null){a.kit.toast("ابتدا انبار را انتخاب کنید");return;}
        final int selectedWarehouseId=warehouse.id;
        recipe=chosen; output=findProduct(chosen.productId); ingredients.clear(); costs.clear();
        if(output==null){recipe=null;a.kit.toast("کالای نهایی این فرمول فعال یا در فهرست کالاها موجود نیست");render(content);return;}
        if(output.varietyCount!=1){recipe=null;output=null;a.kit.toast("کالای نهایی باید دقیقاً یک تنوع واقعی در آتیران داشته باشد؛ تنوعی حدس زده نمی‌شود.");render(content);return;}
        outputQtyDraft=qtyText(chosen.baseOutputQty);
        actualWeightDraft=qtyText(defaultWeight());
        descriptionDraft="";
        a.repo.run(c->{
            List<ProductionWriter.RecipeIngredient> src=ProductionWriter.recipeIngredients(c,chosen.id,selectedWarehouseId);
            List<ProductionWriter.RecipeCost> cs=ProductionWriter.recipeCosts(c,chosen.id);
            return new Object[]{src,cs};
        },new Repo.Cb<Object[]>(){
            @SuppressWarnings("unchecked") @Override public void ok(Object[] data){
                if(recipe==null||recipe.id!=chosen.id||warehouse==null||warehouse.id!=selectedWarehouseId)return;
                List<ProductionWriter.RecipeIngredient> src=(List<ProductionWriter.RecipeIngredient>)data[0];
                for(ProductionWriter.RecipeIngredient x:src){Line line=new Line(x.product,x.baseQtyPieces,x.baseQtyPieces);line.available=x.available;line.requiredByRecipe=true;ingredients.add(line);}
                List<ProductionWriter.RecipeCost> cs=(List<ProductionWriter.RecipeCost>)data[1];
                for(ProductionWriter.RecipeCost x:cs){ProductionWriter.CostType type=findCost(x.costId);if(type!=null)costs.add(new CostLine(type,x.amount,x.amount));}
                // Rebase the existing form widgets to this recipe before calculating any scaled values.
                if(qtyEdit!=null)qtyEdit.setText(outputQtyDraft);
                if(weightEdit!=null)weightEdit.setText(actualWeightDraft);
                if(descriptionEdit!=null)descriptionEdit.setText(descriptionDraft);
                if(qtyEdit!=null)rescale();else render(content);
            }
            @Override public void fail(String err){
                if(recipe!=null&&recipe.id==chosen.id&&warehouse!=null&&warehouse.id==selectedWarehouseId){render(content);a.kit.toast("خواندن فرمول انجام نشد: "+err);}
            }
        });
    }

    private void editRecipe(ProductionWriter.Recipe existing){
        if(!managerAllowed(a))return;
        RecipeEditor ed=new RecipeEditor(existing);
        if(existing==null){pickOutputForRecipe(ed);return;}
        ed.output=findProduct(existing.productId);
        if(ed.output==null){a.kit.toast("کالای فرمول پیدا نشد");return;}
        loadRecipeEditor(ed);
    }

    private void loadRecipeEditor(RecipeEditor ed){
        if(ed.id>0){
            a.repo.run(c->{return new Object[]{ProductionWriter.recipeIngredients(c,ed.id),ProductionWriter.recipeCosts(c,ed.id)};},new Repo.Cb<Object[]>(){
                @SuppressWarnings("unchecked") @Override public void ok(Object[] v){
                    for(ProductionWriter.RecipeIngredient x:(List<ProductionWriter.RecipeIngredient>)v[0])ed.lines.add(new Line(x.product,x.baseQtyPieces,x.baseQtyPieces));
                    for(ProductionWriter.RecipeCost x:(List<ProductionWriter.RecipeCost>)v[1]){ProductionWriter.CostType t=findCost(x.costId);if(t!=null)ed.costs.add(new CostLine(t,x.amount,x.amount));}
                    showRecipeEditor(ed);
                }
                @Override public void fail(String e){a.kit.toast("خواندن فرمول انجام نشد: "+e);}
            });
        }else showRecipeEditor(ed);
    }

    private final class RecipeEditor{
        long id; String name=""; String desc=""; ProductionWriter.Product output; BigDecimal baseQty=BigDecimal.ONE,baseWeight=BigDecimal.ZERO;
        final List<Line> lines=new ArrayList<>(); final List<CostLine> costs=new ArrayList<>();
        RecipeEditor(ProductionWriter.Recipe r){if(r!=null){id=r.id;name=r.name;desc=r.description;baseQty=r.baseOutputQty;baseWeight=r.baseWeightKg;}}
    }

    private void showRecipeEditor(RecipeEditor ed){
        LinearLayout box=a.kit.v();box.setPadding(Theme.dp(14),Theme.dp(10),Theme.dp(14),Theme.dp(6));
        EditText name=a.kit.edit("نام فرمول",ed.name);EditText base=a.kit.edit("مقدار خروجی پایه",qtyText(ed.baseQty));EditText weight=a.kit.edit("وزن پایه (کیلوگرم)",qtyText(ed.baseWeight));
        EditText desc=a.kit.edit("شرح فرمول",ed.desc);
        base.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);weight.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(a.kit.text("فرمول برای «"+ed.output.name+"»",13f,Theme.TEXT,true),a.kit.lp(-1,-2));
        box.addView(name,a.kit.lp(-1,-2));box.addView(base,a.kit.lp(-1,-2));box.addView(weight,a.kit.lp(-1,-2));box.addView(desc,a.kit.lp(-1,-2));
        LinearLayout rows=a.kit.v();box.addView(rows,a.kit.lp(-1,-2));
        Runnable redraw=()->{
            rows.removeAllViews();
            for(int i=0;i<ed.lines.size();i++){final int ix=i;Line l=ed.lines.get(i);rows.addView(a.kit.personRow(l.product.name,"مقدار پایه، قطعهٔ پایه",qty(l.baseQty),"ویرایش/حذف",Theme.INFO,v->new AlertDialog.Builder(a).setTitle(l.product.name).setItems(new String[]{"ویرایش مقدار پایه","حذف ماده از فرمول"},(dialog,action)->{if(action==0)inputDecimal("مقدار پایه برای "+l.product.name,l.baseQty,n->{l.baseQty=n;showRecipeEditor(ed);});else{ed.lines.remove(ix);showRecipeEditor(ed);}}).setNegativeButton("بستن",null).show()),a.kit.lp(-1,-2));}
            for(int i=0;i<ed.costs.size();i++){final int ix=i;CostLine cost=ed.costs.get(i);rows.addView(a.kit.personRow(cost.type.name,"سربار پایه",money(cost.baseAmount),"ویرایش",Theme.WARNING,v->inputDecimal("سربار پایه",cost.baseAmount,n->{cost.baseAmount=n;showRecipeEditor(ed);})),a.kit.lp(-1,-2));}
        };
        redraw.run();
        box.addView(a.kit.btn("افزودن مادهٔ اولیه",v->pickIngredientForRecipe(ed)),a.kit.lp(-1,-2));
        box.addView(a.kit.gap(5));box.addView(a.kit.btnGhost("افزودن هزینهٔ پیش‌فرض",Theme.WARNING,v->pickCostForRecipe(ed)),a.kit.lp(-1,-2));
        AlertDialog dlg=new AlertDialog.Builder(a).setCustomTitle(dialogTitle(ed.id>0?"ویرایش فرمول":"فرمول جدید")).setView(new ScrollView(a){{addView(box);}}).setNegativeButton("انصراف",null).setPositiveButton("ذخیره فرمول",null).create();
        dlg.setOnShowListener(d->{styleDialog(dlg);dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                ed.name=name.getText().toString().trim();ed.desc=desc.getText().toString();ed.baseQty=parse(base.getText().toString());ed.baseWeight=parse(weight.getText().toString());
                if(ed.name.isEmpty()||ed.lines.isEmpty()||ed.baseQty.signum()<=0){a.kit.toast("نام، خروجی پایه و دست‌کم یک مادهٔ خام لازم است");return;}
                ProductionWriter.RecipeDraft draft=new ProductionWriter.RecipeDraft();draft.id=ed.id;draft.name=ed.name;draft.productId=ed.output.id;draft.description=ed.desc;draft.baseOutputQty=ed.baseQty;draft.baseWeightKg=ed.baseWeight;
                for(Line l:ed.lines)draft.ingredients.add(new ProductionWriter.RecipeIngredient(l.product,l.baseQty));
                for(CostLine x:ed.costs)draft.costs.add(new ProductionWriter.RecipeCost(x.type.id,x.type.name,x.baseAmount));
                saveRecipe(draft,dlg);
            }catch(Exception ex){a.kit.toast("اطلاعات فرمول معتبر نیست: "+ex.getMessage());}
        });});dlg.show();
    }

    private void pickIngredientForRecipe(RecipeEditor ed){
        pickProduct("انتخاب مادهٔ اولیه",p->{if(p.id==ed.output.id){a.kit.toast("کالای نهایی نمی‌تواند مادهٔ اولیهٔ خودش باشد");return;}inputDecimal("مقدار پایهٔ "+p.name,BigDecimal.ONE,n->{ed.lines.add(new Line(p,n,n));showRecipeEditor(ed);});});
    }
    private void pickCostForRecipe(RecipeEditor ed){pickCostType(t->inputDecimal("هزینهٔ پایهٔ "+t.name,BigDecimal.ZERO,n->{ed.costs.add(new CostLine(t,n,n));showRecipeEditor(ed);}));}

    private void saveRecipe(ProductionWriter.RecipeDraft draft,AlertDialog editorDialog){
        withAtiranManagerId(userId->{
            editorDialog.dismiss();
            runWriter("در حال ذخیرهٔ فرمول…","ذخیرهٔ فرمول انجام نشد",c->ProductionWriter.saveRecipe(c,draft,userId),id->{busy=false;catalog=null;recipe=null;ingredients.clear();costs.clear();loadCatalog();a.kit.toast("فرمول «"+draft.name+"» ذخیره شد.");});
        });
    }

    private void editIngredient(int index){
        if(index<0||index>=ingredients.size())return;Line l=ingredients.get(index);
        String[] actions=l.requiredByRecipe?new String[]{"ویرایش مقدار مصرف"}
                :new String[]{"ویرایش مقدار مصرف","حذف مادهٔ اضافه از این تولید"};
        new AlertDialog.Builder(a).setTitle(l.product.name).setItems(actions,(d,w)->{
            if(w==0)inputDecimal("مصرف واقعی (قطعهٔ پایه)",l.qty,n->{l.qty=n;render(content);});
            else {ingredients.remove(index);render(content);}
        }).setNegativeButton("بستن",null).show();
    }

    private void pickIngredient(){pickProduct("افزودن مادهٔ خام به این تولید",p->{
        if(output!=null&&p.id==output.id){a.kit.toast("کالای نهایی نمی‌تواند مادهٔ اولیهٔ خودش باشد");return;}
        inputDecimal("مقدار مصرف واقعی از "+p.name+" (قطعهٔ پایه)",BigDecimal.ONE,n->{ingredients.add(new Line(p,n,n));render(content);});
    });}
    private void pickProduct(String title,Consumer<ProductionWriter.Product> chosen){
        List<ProductionWriter.Product> candidates=new ArrayList<>();
        for(ProductionWriter.Product p:catalog.products)
            if(p.varietyCount==1&&!p.withProductionSerial)candidates.add(p);
        if(candidates.isEmpty()){a.kit.toast("مادهٔ خامِ بدون سری و با دقیقاً یک تنوع واقعی در آتیران پیدا نشد");return;}
        showSearchPicker(title,candidates,new PickerContent<ProductionWriter.Product>(){
            @Override public String searchText(ProductionWriter.Product p){return p.name+" "+p.id+" "+p.unit+" "+p.stockPieces;}
            @Override public View row(ProductionWriter.Product p,View.OnClickListener click){
                String subtitle="موجودی انبار «"+(warehouse==null?"—":warehouse.name)+"»: "+qty(p.stockPieces)+" قطعهٔ پایه • "+p.unit;
                View row=a.kit.personRow(bidi(p.name),bidi(subtitle),isolateLtr(Money.fa(String.valueOf(p.id))),"انتخاب",Theme.INFO,click);
                row.setContentDescription(p.name+"، شناسه "+Money.fa(String.valueOf(p.id))
                        +"، موجودی انبار "+(warehouse==null?"نامشخص":warehouse.name)+" "+qty(p.stockPieces)+" قطعهٔ پایه، واحد "+p.unit);
                return row;
            }
        },"مواد سری‌دار یا کالاهایی که دقیقاً یک تنوع ثبت‌شده ندارند، تا انتخاب قطعی قابل ثبت نیستند.",chosen);
    }

    private void editCost(int index){if(index<0||index>=costs.size())return;CostLine c=costs.get(index);new AlertDialog.Builder(a).setTitle(c.type.name).setItems(new String[]{"ویرایش مبلغ","حذف"},(d,w)->{if(w==0)inputDecimal("مبلغ سربار این نوبت",c.amount,n->{c.amount=n;render(content);});else{costs.remove(index);render(content);}}).setNegativeButton("بستن",null).show();}
    private void pickCostType(){pickCostType(t->inputDecimal("مبلغ سربار «"+t.name+"»",BigDecimal.ZERO,n->{if(n.signum()>0)costs.add(new CostLine(t,n,n));render(content);}));}
    private void pickCostType(Consumer<ProductionWriter.CostType> chosen){
        if(catalog.costs.isEmpty()){a.kit.toast("نوع سربار فعالی در produce.IndirectCost نیست؛ ابتدا آن را در آتیران تعریف کنید");return;}
        showSearchPicker("نوع هزینهٔ سربار",catalog.costs,new PickerContent<ProductionWriter.CostType>(){
            @Override public String searchText(ProductionWriter.CostType type){return type.name+" "+type.id;}
            @Override public View row(ProductionWriter.CostType type,View.OnClickListener click){
                View row=a.kit.personRow(bidi(type.name),"نوع فعال هزینه در آتیران",
                        isolateLtr(Money.fa(String.valueOf(type.id))),"انتخاب",Theme.WARNING,click);
                row.setContentDescription(type.name+"، شناسه "+Money.fa(String.valueOf(type.id)));
                return row;
            }
        },chosen);
    }

    private void inputDecimal(String title,BigDecimal current,java.util.function.Consumer<BigDecimal> done){
        EditText edit=a.kit.edit(title,qtyText(current));edit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        AlertDialog dlg=new AlertDialog.Builder(a).setCustomTitle(dialogTitle(title)).setView(edit).setNegativeButton("انصراف",null).setPositiveButton("تأیید",null).create();
        dlg.setOnShowListener(d->{styleDialog(dlg);dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{BigDecimal n=parse(edit.getText().toString());if(n.signum()<0)throw new Exception();dlg.dismiss();done.accept(n);}catch(Exception e){a.kit.toast("عدد معتبر وارد کنید");}});});dlg.show();
    }

    private void rescale(){
        if(recipe==null||qtyEdit==null)return;
        try{BigDecimal outputQty=parse(qtyEdit.getText().toString());if(outputQty.signum()<=0)throw new Exception();BigDecimal factor=outputQty.divide(recipe.baseOutputQty,8,RoundingMode.HALF_UP);
            for(Line l:ingredients)l.qty=l.baseQty.multiply(factor).setScale(3,RoundingMode.HALF_UP);
            for(CostLine c:costs)c.amount=c.baseAmount.multiply(factor).setScale(4,RoundingMode.HALF_UP);
            if(weightEdit!=null&&recipe.baseWeightKg.signum()>0)weightEdit.setText(qtyText(recipe.baseWeightKg.multiply(factor).setScale(3,RoundingMode.HALF_UP)));
            render(content);
        }catch(Exception e){a.kit.toast("مقدار خروجی معتبر نیست");}
    }

    private void preview(){
        ProductionWriter.Request req=buildRequest();if(req==null)return;
        a.repo.run(c->ProductionWriter.estimate(c,req),new Repo.Cb<ProductionWriter.Costing>(){
            @Override public void ok(ProductionWriter.Costing x){new AlertDialog.Builder(a).setTitle("پیش‌نمایش بهای تمام‌شده").setMessage(
                    "مواد اولیه: "+money(x.materialCost)+"\n"+
                    "سربار: "+money(x.overheadCost)+"\n"+
                    "جمع تمام‌شده: "+money(x.totalCost)+"\n"+
                    "بهای هر واحد خروجی: "+money(x.costPerUnit)+"\n"+
                    "بهای هر کیلو: "+money(x.costPerKg)+"\n"+
                    "بهای پایه برای ثبت گردش انبار: "+money(x.finishedUnitPrice))
                    .setPositiveButton("تأیید",null).show();}
            @Override public void fail(String e){a.kit.toast("محاسبهٔ بها انجام نشد: "+e);}
        });
    }

    private void post(){
        if(busy||!managerAllowed(a))return;
        ProductionWriter.Request req=buildRequest();if(req==null)return;
        withAtiranManagerId(userId->confirmPost(req,userId));
    }

    private void confirmPost(ProductionWriter.Request req,int atiranUserId){
        if(idempotencyKey.isEmpty())idempotencyKey=UUID.randomUUID().toString();
        req.idempotencyKey=idempotencyKey;
        new AlertDialog.Builder(a).setTitle("ثبت قطعی تولید؟").setMessage("مواد اولیه از موجودی همین انبار کسر می‌شود و محصول نهایی به آن افزوده خواهد شد. سری جدید ساخته نمی‌شود، سری‌های قبلی حذف نمی‌شوند و الزام تولید سری برای کالای نهایی خاموش خواهد شد. گردش‌ها و سند حسابداری (اگر تنظیم رسمی آتیران فعال باشد) در تراکنش ثبت می‌شوند.")
                .setNegativeButton("بازگشت",null).setPositiveButton("ثبت قطعی",(d,w)->{
                    busy=true;render(content);
                    runWriter("در حال ثبت تولید و محاسبهٔ موجودی…","ثبت تولید انجام نشد",c->ProductionWriter.post(c,req,atiranUserId),posted->{
                        busy=false;idempotencyKey="";
                        if(posted.duplicate)a.kit.toast("این تولید قبلاً ثبت شده است؛ شمارهٔ "+Money.fa(String.valueOf(posted.productionId)));
                        else a.kit.toast("تولید در آتیران ثبت شد؛ شمارهٔ "+Money.fa(String.valueOf(posted.productionId))+" • بهای هر واحد "+money(posted.costing.finishedUnitPrice));
                        catalog=null;recipe=null;output=null;ingredients.clear();costs.clear();loadCatalog();
                    });
                }).show();
    }

    /** Resolve an existing Atiran session or request one-operation manager reauthentication. */
    private void withAtiranManagerId(Consumer<Integer> continuation){
        if(busy)return;
        int sessionUid=AtiranAuth.sessionUid(a);
        boolean localModir = ProductionWriter.isLocalModirProductionIdentity(
                AtiranAuth.sessionUser(a), sessionUid, AtiranAuth.sessionRole(a));
        if(sessionUid>0 || localModir){
            busy=true;render(content);
            runWriter("در حال بررسی حساب آتیران…","بررسی حساب آتیران انجام نشد",c->ProductionWriter.resolveAtiranUserId(c,sessionUid,
                    AtiranAuth.sessionUser(a), AtiranAuth.sessionRole(a)),uid->{
                busy=false;render(content);continuation.accept(uid);
            });
            return;
        }

        LinearLayout fields=a.kit.v();
        fields.setPadding(Theme.dp(20),Theme.dp(8),Theme.dp(20),Theme.dp(8));
        fields.addView(a.kit.hint("برای این عملیات، مدیر فعال آتیران را تأیید کنید. رمز فقط برای همین اتصال استفاده می‌شود و ذخیره نخواهد شد."),a.kit.lp(-1,-2));
        EditText username=a.kit.edit("نام کاربری مدیر آتیران","");
        EditText password=a.kit.edit("رمز عبور مدیر آتیران","");
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSaveEnabled(false);
        fields.addView(username,a.kit.lp(-1,-2));fields.addView(password,a.kit.lp(-1,-2));
        AlertDialog dlg=new AlertDialog.Builder(a).setCustomTitle(dialogTitle("تأیید هویت مدیر آتیران"))
                .setView(fields).setNegativeButton("انصراف",null).setPositiveButton("تأیید برای این عملیات",null).create();
        dlg.setOnDismissListener(x->password.setText(""));
        dlg.setOnShowListener(x->{
            styleDialog(dlg);
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String name=username.getText().toString().trim();
                String secret=password.getText().toString();
                if(name.isEmpty()){username.setError("نام کاربری لازم است");return;}
                if(secret.isEmpty()){password.setError("رمز عبور لازم است");return;}
                password.setText("");dlg.dismiss();busy=true;render(content);
                runWriter("در حال تأیید مدیر آتیران…","احراز هویت مدیر آتیران انجام نشد",c->ProductionWriter.authenticateAtiranManager(c,name,secret),uid->{
                    busy=false;render(content);continuation.accept(uid);
                });
            });
        });
        dlg.show();
    }

    private ProductionWriter.Request buildRequest(){
        if(recipe==null||output==null){a.kit.toast("ابتدا فرمول تولید را انتخاب کنید");return null;}
        if(warehouse==null){a.kit.toast("ابتدا انبار را انتخاب کنید");return null;}
        try{
            ProductionWriter.Request r=new ProductionWriter.Request();r.recipeId=recipe.id;r.finishedProductId=output.id;r.warehouseId=warehouse.id;
            r.outputQty=parse(qtyEdit==null?"":qtyEdit.getText().toString());r.actualWeightKg=parse(weightEdit==null?"":weightEdit.getText().toString());
            r.description=descriptionEdit==null?"":descriptionEdit.getText().toString();
            if(r.outputQty.signum()<=0||r.actualWeightKg.signum()<=0)throw new Exception("مقدار و وزن باید بیشتر از صفر باشند");
            if(ingredients.isEmpty())throw new Exception("مادهٔ اولیه ثبت نشده است");
            for(Line line:ingredients){
                if(!line.available)throw new Exception("مادهٔ «"+line.product.name+"» در فهرست فعال آتیران پیدا نشد.");
                if(line.product.withProductionSerial)throw new Exception("مادهٔ «"+line.product.name+"» ردیابی سری دارد؛ سری واقعی در این فرم انتخاب نمی‌شود.");
                if(line.product.varietyCount!=1)throw new Exception("مادهٔ «"+line.product.name+"» باید دقیقاً یک تنوع ثبت‌شده داشته باشد؛ حدس‌زدن تنوع مجاز نیست.");
                if(line.qty.signum()<=0)throw new Exception("مقدار مصرف «"+line.product.name+"» باید بیشتر از صفر باشد");
                r.ingredients.add(new ProductionWriter.Ingredient(line.product.id,line.product.name,line.product.unit,line.product.packSize,line.qty));
            }
            for(CostLine line:costs){if(line.amount.signum()<0)throw new Exception("هزینهٔ سربار منفی است");r.costs.add(new ProductionWriter.ExtraCost(line.type.id,line.amount));}
            return r;
        }catch(Exception e){a.kit.toast("فرم تولید کامل نیست: "+e.getMessage());return null;}
    }

    private interface SqlRun<T>{T run(Connection c)throws Exception;}
    private interface Done<T>{void ok(T value);}
    private <T> void runWriter(String wait,SqlRun<T> work,Done<T> done){
        runWriter(wait,"ثبت عملیات انجام نشد",work,done);
    }
    private <T> void runWriter(String wait,String failurePrefix,SqlRun<T> work,Done<T> done){
        a.kit.toast(wait);
        writer.execute(()->{
            T result=null;String error=null;boolean direct=false;
            try{
                direct=a.settings.directConn();if(direct)ir.meelano.manager.data.NetRoute.bindDirect(a);
                try(Connection c=SmartLink.open(a,a.settings)){result=work.run(c);}
            }catch(Exception e){error=ir.meelano.manager.data.Atiran.diagnose(e);}
            finally{if(direct)try{ir.meelano.manager.data.NetRoute.unbind(a);}catch(Exception ignored){Diag.ignored("ProductionScreen:writer",ignored);}}
            T val=result;String err=error;
            a.runOnUiThread(()->{
                if(err!=null){busy=false;render(content);a.kit.toast(failurePrefix+": "+err);}
                else done.ok(val);
            });
        });
    }

    private void pickOutputForRecipe(RecipeEditor ed){
        List<ProductionWriter.Product> candidates=new ArrayList<>();
        for(ProductionWriter.Product p:catalog.products)if(p.varietyCount==1)candidates.add(p);
        if(candidates.isEmpty()){a.kit.toast("کالای نهایی با دقیقاً یک تنوع ثبت‌شده در آتیران پیدا نشد");return;}
        showSearchPicker("کالای نهایی فرمول",candidates,new PickerContent<ProductionWriter.Product>(){
            @Override public String searchText(ProductionWriter.Product p){return p.name+" "+p.id+" "+p.unit;}
            @Override public View row(ProductionWriter.Product p,View.OnClickListener click){
                View row=a.kit.personRow(bidi(p.name),bidi("واحد: "+p.unit),
                        isolateLtr(Money.fa(String.valueOf(p.id))),"انتخاب",Theme.GOLD,click);
                row.setContentDescription("کالای نهایی "+p.name+"، شناسه "+Money.fa(String.valueOf(p.id))+"، واحد "+p.unit);
                return row;
            }
        },"کالاهای دارای چند تنوع قابل انتخاب نیستند. کالای نهاییِ دارای سری پس از ثبت، مطابق تنظیم تولید بدون الزام سری خواهد شد.",p->{ed.output=p;loadRecipeEditor(ed);});
    }

    private ProductionWriter.Product findProduct(long id){for(ProductionWriter.Product p:catalog.products)if(p.id==id)return p;return null;}
    private ProductionWriter.CostType findCost(int id){for(ProductionWriter.CostType c:catalog.costs)if(c.id==id)return c;return null;}
    private BigDecimal defaultWeight(){if(recipe!=null&&recipe.baseWeightKg.signum()>0)return recipe.baseWeightKg;if(output!=null&&output.weightPerUnit.signum()>0)return output.weightPerUnit.multiply(recipe==null?BigDecimal.ONE:recipe.baseOutputQty);return BigDecimal.ZERO;}
    private static BigDecimal parse(String s)throws Exception{String en=Money.en(s==null?"":s).replace(",","").replace("٬","").replace('٫','.').trim();if(en.isEmpty())throw new Exception();return new BigDecimal(en);}
    private static String qty(BigDecimal n){return Money.fa(qtyText(n));}
    private static String money(BigDecimal n){return Money.rial(n==null?0d:n.doubleValue());}
    private static String qtyText(BigDecimal n){return n==null?"0":n.stripTrailingZeros().toPlainString();}
}
