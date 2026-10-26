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
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Diag;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ProductionWriter;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.core.SmartLink;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.ui.Theme;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Manager-only production desk. Inventory posting is delegated to ProductionWriter. */
public final class ProductionScreen extends Screen {
    private static final class Catalog {
        final List<ProductionWriter.Product> products;
        final List<ProductionWriter.Warehouse> warehouses;
        final List<ProductionWriter.Recipe> recipes;
        final List<ProductionWriter.CostType> costs;
        Catalog(List<ProductionWriter.Product> p, List<ProductionWriter.Warehouse> w,
                List<ProductionWriter.Recipe> r, List<ProductionWriter.CostType> c) {
            products=p; warehouses=w; recipes=r; costs=c;
        }
    }
    private static final class Line {
        final ProductionWriter.Product product;
        BigDecimal baseQty;
        BigDecimal qty;
        Line(ProductionWriter.Product p, BigDecimal base, BigDecimal actual) { product=p; baseQty=base; qty=actual; }
    }
    private static final class CostLine {
        final ProductionWriter.CostType type;
        BigDecimal baseAmount;
        BigDecimal amount;
        CostLine(ProductionWriter.CostType t, BigDecimal base, BigDecimal actual) { type=t; baseAmount=base; amount=actual; }
    }

    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private Catalog catalog;
    private ProductionWriter.Product output;
    private ProductionWriter.Warehouse warehouse;
    private ProductionWriter.Recipe recipe;
    private final List<Line> ingredients = new ArrayList<>();
    private final List<CostLine> costs = new ArrayList<>();
    private EditText qtyEdit, weightEdit, descriptionEdit;
    private String idempotencyKey = "";
    private boolean busy;

    public ProductionScreen(MainActivity a) { super(a); }
    @Override public String id() { return "production"; }
    @Override public String title() { return "تولید محصول"; }
    @Override public String glyph() { return "⚙"; }
    @Override public int accent() { return Theme.GOLD; }

    /** Fail closed: the central nav gate and the screen itself both enforce manager-only access. */
    public static boolean managerAllowed(MainActivity a) {
        try {
            String session = AtiranAuth.sessionRole(a);
            return RoleStore.ADMIN.equals(session == null || session.isEmpty() ? RoleStore.current(a) : session);
        } catch (Exception e) { return false; }
    }

    @Override public void render(LinearLayout content) {
        this.content=content;
        content.removeAllViews();
        if (!managerAllowed(a)) {
            content.addView(a.kit.empty("این بخش فقط برای مدیر است", "برای عملیات تولید با نقش مدیر وارد شوید."), a.kit.lp(-1,-2));
            return;
        }
        if (catalog == null) {
            content.addView(a.kit.hero(glyph(), title(), "فرمول، مواد اولیه، سربار و بهای تمام‌شده", accent()), a.kit.lp(-1,-2));
            content.addView(a.kit.gap(10));
            content.addView(a.kit.loading("در حال بررسی ساختار تولید آتیران و دریافت اطلاعات…"), a.kit.lp(-1,-2));
            loadCatalog();
            return;
        }
        content.addView(a.kit.hero(glyph(), title(), "فقط مدیر • سری جدید ساخته نمی‌شود • ثبت با گردش رسمی انبار آتیران", accent()), a.kit.lp(-1,-2));
        content.addView(a.kit.gap(10));
        content.addView(statusCard(), a.kit.lp(-1,-2));
        content.addView(a.kit.gap(8));
        content.addView(selectionCard(), a.kit.lp(-1,-2));
        content.addView(a.kit.gap(8));
        if (recipe != null) {
            content.addView(outputCard(), a.kit.lp(-1,-2));
            content.addView(a.kit.gap(8));
            content.addView(ingredientsCard(), a.kit.lp(-1,-2));
            content.addView(a.kit.gap(8));
            content.addView(costsCard(), a.kit.lp(-1,-2));
            content.addView(a.kit.gap(8));
            content.addView(actionCard(), a.kit.lp(-1,-2));
        }
        content.addView(a.kit.gap(12));
    }

    @Override public void onShown() { if (catalog == null && managerAllowed(a)) loadCatalog(); }
    @Override public void onHidden() { /* No background refresh or writer survives navigation. */ }

    private void loadCatalog() {
        if (a.repo == null || !managerAllowed(a)) return;
        a.repo.run(c -> {
            ProductionWriter.preflight(c);
            ProductionWriter.ensureProfileTable(c);
            return new Catalog(ProductionWriter.products(c), ProductionWriter.warehouses(c),
                    ProductionWriter.recipes(c), ProductionWriter.costTypes(c));
        }, new Repo.Cb<Catalog>() {
            @Override public void ok(Catalog value) {
                catalog=value;
                if (warehouse==null && !value.warehouses.isEmpty()) warehouse=value.warehouses.get(0);
                render(content);
            }
            @Override public void fail(String error) {
                content.removeAllViews();
                content.addView(a.kit.hero(glyph(), title(), "ساختار دیتابیس باید با پایگاه آتیران بررسی شود", Theme.WARNING), a.kit.lp(-1,-2));
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
        card.addView(a.kit.text("گردش تولید از فایل Mili.sql",14f,Theme.TEXT,true),a.kit.lp(-1,-2));
        card.addView(a.kit.hint("مصرف مواد با گردش ۸۵، محصول نهایی با گردش ۸۶ و بازگشت مواد با گردش ۸۷ ثبت می‌شود. موجودی فقط از روی ka_act و رویه‌های رسمی آتیران دوباره محاسبه می‌شود."),a.kit.lp(-1,-2));
        card.addView(a.kit.hint("سری برای محصول خروجی ساخته نمی‌شود و الزام WithProductionSerial همان کالا در همین تراکنش خاموش می‌شود. مواد اولیهٔ سری‌دار یا کالاهای دارای چند تنوع، برای جلوگیری از مغایرت تا انتخاب دقیق متوقف می‌شوند."),a.kit.lp(-1,-2));
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
        card.addView(a.kit.text("کالای نهایی: "+output.name+" • واحد: "+output.unit+" • موجودی فعلی: "+qty(output.stockPieces)+" قطعه پایه",12f,Theme.TEXT,true),a.kit.lp(-1,-2));
        qtyEdit=a.kit.edit("مقدار خروجی در واحد بسته‌بندی",qtyText(recipe.baseOutputQty));
        qtyEdit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        weightEdit=a.kit.edit("وزن خالص تولیدشده (کیلوگرم)",qtyText(defaultWeight()));
        weightEdit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        card.addView(qtyEdit,a.kit.lp(-1,-2)); card.addView(a.kit.gap(6)); card.addView(weightEdit,a.kit.lp(-1,-2));
        card.addView(a.kit.hint("مقادیر فرمول برای خروجی پایه ذخیره می‌شوند. مقدار و وزن واقعی هر نوبت قابل اصلاح است؛ هزینهٔ هر واحد و هر کیلو از خروجی واقعی محاسبه می‌شود."),a.kit.lp(-1,-2));
        return card;
    }

    private View ingredientsCard() {
        LinearLayout card=a.kit.card(Theme.INFO);
        card.addView(a.kit.text("مواد اولیهٔ مصرفی • "+Money.fa(String.valueOf(ingredients.size())),14f,Theme.TEXT,true),a.kit.lp(-1,-2));
        if(ingredients.isEmpty()) card.addView(a.kit.hint("برای این فرمول ماده‌ای ثبت نشده است."),a.kit.lp(-1,-2));
        for(int i=0;i<ingredients.size();i++){
            final int index=i; Line line=ingredients.get(i);
            View row=a.kit.personRow(line.product.name,"موجودی: "+qty(line.product.stockPieces)+" قطعه پایه • "+line.product.unit,
                    qty(line.qty)+" قطعه پایه","ویرایش",Theme.INFO,v->editIngredient(index));
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
        descriptionEdit=a.kit.edit("شرح تولید (اختیاری)","");
        card.addView(descriptionEdit,a.kit.lp(-1,-2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGhost("پیش‌نمایش بهای تمام‌شده",Theme.INFO,v->preview()),a.kit.lp(-1,-2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGold(busy?"در حال ثبت…":"ثبت قطعی تولید و به‌روزرسانی موجودی",v->post()),a.kit.lp(-1,-2));
        card.addView(a.kit.hint("ثبت قطعی فقط با کاربر فعال واقعی آتیران انجام می‌شود؛ حساب محلی بدون user_id آتیران مجوز سندداری ندارد."),a.kit.lp(-1,-2));
        return card;
    }

    private void pickWarehouse(){
        if(catalog.warehouses.isEmpty()){a.kit.toast("انبار فعال در آتیران پیدا نشد");return;}
        String[] names=new String[catalog.warehouses.size()];for(int i=0;i<names.length;i++)names[i]=catalog.warehouses.get(i).toString();
        new AlertDialog.Builder(a).setTitle("انتخاب انبار مواد و محصول").setItems(names,(d,w)->{warehouse=catalog.warehouses.get(w);render(content);}).setNegativeButton("بستن",null).show();
    }

    private void pickRecipe(){
        if(catalog.recipes.isEmpty()){a.kit.toast("فرمول فعالی ثبت نشده است؛ ابتدا فرمول بسازید");return;}
        String[] names=new String[catalog.recipes.size()];for(int i=0;i<names.length;i++)names[i]=catalog.recipes.get(i).toString();
        new AlertDialog.Builder(a).setTitle("انتخاب فرمول تولید").setItems(names,(d,w)->selectRecipe(catalog.recipes.get(w))).setNegativeButton("بستن",null).show();
    }

    private void selectRecipe(ProductionWriter.Recipe chosen){
        if(warehouse==null){a.kit.toast("ابتدا انبار را انتخاب کنید");return;}
        recipe=chosen; output=findProduct(chosen.productId); ingredients.clear(); costs.clear();
        a.repo.run(c->{
            List<ProductionWriter.RecipeIngredient> src=ProductionWriter.recipeIngredients(c,chosen.id);
            List<ProductionWriter.RecipeCost> cs=ProductionWriter.recipeCosts(c,chosen.id);
            return new Object[]{src,cs};
        },new Repo.Cb<Object[]>(){
            @SuppressWarnings("unchecked") @Override public void ok(Object[] data){
                List<ProductionWriter.RecipeIngredient> src=(List<ProductionWriter.RecipeIngredient>)data[0];
                for(ProductionWriter.RecipeIngredient x:src) ingredients.add(new Line(x.product,x.baseQtyPieces,x.baseQtyPieces));
                List<ProductionWriter.RecipeCost> cs=(List<ProductionWriter.RecipeCost>)data[1];
                for(ProductionWriter.RecipeCost x:cs){ProductionWriter.CostType type=findCost(x.costId);if(type!=null)costs.add(new CostLine(type,x.amount,x.amount));}
                rescale(); render(content);
            }
            @Override public void fail(String err){a.kit.toast("خواندن فرمول انجام نشد: "+err);}
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
        RecipeEditor(ProductionWriter.Recipe r){if(r!=null){id=r.id;name=r.name;baseQty=r.baseOutputQty;baseWeight=r.baseWeightKg;}}
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
            for(int i=0;i<ed.lines.size();i++){final int ix=i;Line l=ed.lines.get(i);rows.addView(a.kit.personRow(l.product.name,"مقدار پایه، قطعهٔ پایه",qty(l.baseQty),"ویرایش",Theme.INFO,v->inputDecimal("مقدار پایه برای "+l.product.name,l.baseQty,n->{l.baseQty=n;showRecipeEditor(ed);})),a.kit.lp(-1,-2));}
            for(int i=0;i<ed.costs.size();i++){final int ix=i;CostLine cost=ed.costs.get(i);rows.addView(a.kit.personRow(cost.type.name,"سربار پایه",money(cost.baseAmount),"ویرایش",Theme.WARNING,v->inputDecimal("سربار پایه",cost.baseAmount,n->{cost.baseAmount=n;showRecipeEditor(ed);})),a.kit.lp(-1,-2));}
        };
        redraw.run();
        box.addView(a.kit.btn("افزودن مادهٔ اولیه",v->pickIngredientForRecipe(ed)),a.kit.lp(-1,-2));
        box.addView(a.kit.gap(5));box.addView(a.kit.btnGhost("افزودن هزینهٔ پیش‌فرض",Theme.WARNING,v->pickCostForRecipe(ed)),a.kit.lp(-1,-2));
        AlertDialog dlg=new AlertDialog.Builder(a).setTitle(ed.id>0?"ویرایش فرمول":"فرمول جدید").setView(new ScrollView(a){{addView(box);}}).setNegativeButton("انصراف",null).setPositiveButton("ذخیره فرمول",null).create();
        dlg.setOnShowListener(d->{dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                ed.name=name.getText().toString().trim();ed.desc=desc.getText().toString();ed.baseQty=parse(base.getText().toString());ed.baseWeight=parse(weight.getText().toString());
                if(ed.name.isEmpty()||ed.lines.isEmpty()||ed.baseQty.signum()<=0){a.kit.toast("نام، خروجی پایه و دست‌کم یک مادهٔ خام لازم است");return;}
                ProductionWriter.RecipeDraft draft=new ProductionWriter.RecipeDraft();draft.id=ed.id;draft.name=ed.name;draft.productId=ed.output.id;draft.description=ed.desc;draft.baseOutputQty=ed.baseQty;draft.baseWeightKg=ed.baseWeight;
                for(Line l:ed.lines)draft.ingredients.add(new ProductionWriter.RecipeIngredient(l.product,l.baseQty));
                for(CostLine x:ed.costs)draft.costs.add(new ProductionWriter.RecipeCost(x.type.id,x.type.name,x.baseAmount));
                dlg.dismiss();saveRecipe(draft);
            }catch(Exception ex){a.kit.toast("اطلاعات فرمول معتبر نیست: "+ex.getMessage());}
        });});dlg.show();
    }

    private void pickIngredientForRecipe(RecipeEditor ed){
        pickProduct("انتخاب مادهٔ اولیه",p->{if(p.id==ed.output.id){a.kit.toast("کالای نهایی نمی‌تواند مادهٔ اولیهٔ خودش باشد");return;}inputDecimal("مقدار پایهٔ "+p.name,BigDecimal.ONE,n->{ed.lines.add(new Line(p,n,n));showRecipeEditor(ed);});});
    }
    private void pickCostForRecipe(RecipeEditor ed){pickCostType(t->inputDecimal("هزینهٔ پایهٔ "+t.name,BigDecimal.ZERO,n->{ed.costs.add(new CostLine(t,n,n));showRecipeEditor(ed);}));}

    private void saveRecipe(ProductionWriter.RecipeDraft draft){
        runWriter("در حال ذخیرهٔ فرمول…",c->{int uid=ProductionWriter.resolveAtiranUserId(c,AtiranAuth.sessionUid(a),AtiranAuth.sessionUser(a));return ProductionWriter.saveRecipe(c,draft,uid);},id->{catalog=null;recipe=null;ingredients.clear();costs.clear();loadCatalog();a.kit.toast("فرمول «"+draft.name+"» ذخیره شد.");});
    }

    private void editIngredient(int index){
        if(index<0||index>=ingredients.size())return;Line l=ingredients.get(index);
        new AlertDialog.Builder(a).setTitle(l.product.name).setItems(new String[]{"ویرایش مقدار","حذف از این تولید"},(d,w)->{
            if(w==0)inputDecimal("مصرف واقعی (قطعهٔ پایه)",l.qty,n->{l.qty=n;render(content);});
            else {ingredients.remove(index);render(content);}
        }).setNegativeButton("بستن",null).show();
    }

    private void pickIngredient(){pickProduct("افزودن مادهٔ خام به این تولید",p->{
        if(output!=null&&p.id==output.id){a.kit.toast("کالای نهایی نمی‌تواند مادهٔ اولیهٔ خودش باشد");return;}
        inputDecimal("مقدار مصرف واقعی از "+p.name+" (قطعهٔ پایه)",BigDecimal.ONE,n->{ingredients.add(new Line(p,n,n));render(content);});
    });}
    private void pickProduct(String title,java.util.function.Consumer<ProductionWriter.Product> chosen){
        String[] names=new String[catalog.products.size()];for(int i=0;i<names.length;i++){ProductionWriter.Product p=catalog.products.get(i);names[i]=p.name+" • موجودی "+qty(p.stockPieces)+" • "+p.id;}
        new AlertDialog.Builder(a).setTitle(title).setItems(names,(d,w)->chosen.accept(catalog.products.get(w))).setNegativeButton("بستن",null).show();
    }

    private void editCost(int index){if(index<0||index>=costs.size())return;CostLine c=costs.get(index);new AlertDialog.Builder(a).setTitle(c.type.name).setItems(new String[]{"ویرایش مبلغ","حذف"},(d,w)->{if(w==0)inputDecimal("مبلغ سربار این نوبت",c.amount,n->{c.amount=n;render(content);});else{costs.remove(index);render(content);}}).setNegativeButton("بستن",null).show();}
    private void pickCostType(){pickCostType(t->inputDecimal("مبلغ سربار «"+t.name+"»",BigDecimal.ZERO,n->{if(n.signum()>0)costs.add(new CostLine(t,n,n));render(content);}));}
    private void pickCostType(java.util.function.Consumer<ProductionWriter.CostType> chosen){
        if(catalog.costs.isEmpty()){a.kit.toast("نوع سربار فعالی در produce.IndirectCost نیست؛ ابتدا آن را در آتیران تعریف کنید");return;}
        String[] names=new String[catalog.costs.size()];for(int i=0;i<names.length;i++)names[i]=catalog.costs.get(i).name;
        new AlertDialog.Builder(a).setTitle("نوع هزینهٔ سربار").setItems(names,(d,w)->chosen.accept(catalog.costs.get(w))).setNegativeButton("بستن",null).show();
    }

    private void inputDecimal(String title,BigDecimal current,java.util.function.Consumer<BigDecimal> done){
        EditText edit=a.kit.edit(title,qtyText(current));edit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        AlertDialog dlg=new AlertDialog.Builder(a).setTitle(title).setView(edit).setNegativeButton("انصراف",null).setPositiveButton("تأیید",null).create();
        dlg.setOnShowListener(d->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{BigDecimal n=parse(edit.getText().toString());if(n.signum()<0)throw new Exception();dlg.dismiss();done.accept(n);}catch(Exception e){a.kit.toast("عدد معتبر وارد کنید");}}));dlg.show();
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
        if(idempotencyKey.isEmpty())idempotencyKey=UUID.randomUUID().toString();
        req.idempotencyKey=idempotencyKey;
        new AlertDialog.Builder(a).setTitle("ثبت قطعی تولید؟").setMessage("مواد اولیه از موجودی انبار کسر می‌شود، محصول نهایی اضافه می‌شود، سری آن حذف/ساخته نمی‌شود و الزام سری همان کالا خاموش خواهد شد. این عملیات مستقیم در آتیران ثبت می‌شود.")
                .setNegativeButton("بازگشت",null).setPositiveButton("ثبت قطعی",(d,w)->{
                    busy=true;render(content);
                    runWriter("در حال ثبت تولید و محاسبهٔ موجودی…",c->{int uid=ProductionWriter.resolveAtiranUserId(c,AtiranAuth.sessionUid(a),AtiranAuth.sessionUser(a));return ProductionWriter.post(c,req,uid);},posted->{
                        busy=false;idempotencyKey="";a.kit.toast((posted.duplicate?"این تولید قبلاً ثبت شده بود؛ شماره ":"تولید در آتیران ثبت شد؛ شماره ")+Money.fa(String.valueOf(posted.productionId))+" • بهای هر واحد "+money(posted.costing.finishedUnitPrice));
                        catalog=null;recipe=null;output=null;ingredients.clear();costs.clear();loadCatalog();
                    });
                }).show();
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
            for(Line line:ingredients){if(line.qty.signum()<=0)throw new Exception("مقدار مصرف «"+line.product.name+"» باید بیشتر از صفر باشد");r.ingredients.add(new ProductionWriter.Ingredient(line.product.id,line.product.name,line.product.unit,line.product.packSize,line.qty));}
            for(CostLine line:costs){if(line.amount.signum()<0)throw new Exception("هزینهٔ سربار منفی است");r.costs.add(new ProductionWriter.ExtraCost(line.type.id,line.amount));}
            return r;
        }catch(Exception e){a.kit.toast("فرم تولید کامل نیست: "+e.getMessage());return null;}
    }

    private interface SqlRun<T>{T run(Connection c)throws Exception;}
    private interface Done<T>{void ok(T value);}
    private <T> void runWriter(String wait,SqlRun<T> work,Done<T> done){
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
                if(err!=null){busy=false;render(content);a.kit.toast("ثبت تولید انجام نشد: "+err);}
                else done.ok(val);
            });
        });
    }

    private void pickOutputForRecipe(RecipeEditor ed){
        String[] names=new String[catalog.products.size()];for(int i=0;i<names.length;i++)names[i]=catalog.products.get(i).name+" • "+catalog.products.get(i).id;
        new AlertDialog.Builder(a).setTitle("کالای نهایی فرمول").setItems(names,(d,w)->{ed.output=catalog.products.get(w);loadRecipeEditor(ed);}).setNegativeButton("بستن",null).show();
    }

    private ProductionWriter.Product findProduct(long id){for(ProductionWriter.Product p:catalog.products)if(p.id==id)return p;return null;}
    private ProductionWriter.CostType findCost(int id){for(ProductionWriter.CostType c:catalog.costs)if(c.id==id)return c;return null;}
    private BigDecimal defaultWeight(){if(recipe!=null&&recipe.baseWeightKg.signum()>0)return recipe.baseWeightKg;if(output!=null&&output.weightPerUnit.signum()>0)return output.weightPerUnit.multiply(recipe==null?BigDecimal.ONE:recipe.baseOutputQty);return BigDecimal.ZERO;}
    private static BigDecimal parse(String s)throws Exception{String en=Money.en(s==null?"":s).replace(",","").replace('٬','').replace('٫','.').trim();if(en.isEmpty())throw new Exception();return new BigDecimal(en);}
    private static String qty(BigDecimal n){return Money.fa(qtyText(n));}
    private static String money(BigDecimal n){return Money.rial(n==null?0d:n.doubleValue());}
    private static String qtyText(BigDecimal n){return n==null?"0":n.stripTrailingZeros().toPlainString();}
}
