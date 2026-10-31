package ir.meelano.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Manager-only production desk packaged inside the licensed unified application. */
public final class ProductionActivity extends Activity {
    private static final int GOLD=Color.rgb(190,142,47), DARK=Color.rgb(25,28,34), MUTED=Color.rgb(106,113,122);
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout page, body;
    private boolean busy;
    private int sessionUid;
    private String sessionName="";
    private List<ProductionWriter.Warehouse> warehouses=new ArrayList<>();
    private List<ProductionWriter.Product> products=new ArrayList<>();
    private List<ProductionWriter.Recipe> recipes=new ArrayList<>();
    private List<ProductionWriter.CostType> costTypes=new ArrayList<>();
    private ProductionWriter.Warehouse warehouse;
    private ProductionWriter.Recipe recipe;
    private ProductionWriter.Product output;
    private final List<MaterialLine> materials=new ArrayList<>();
    private final List<CostLine> costs=new ArrayList<>();
    private EditText outputQty,weight,description;
    private String qtyText="1",weightText="0",descriptionText="",idempotencyKey="";

    private static final class MaterialLine {
        ProductionWriter.Product product; BigDecimal base,actual; boolean available;
        MaterialLine(ProductionWriter.Product p,BigDecimal b,BigDecimal a,boolean ok){product=p;base=b;actual=a;available=ok;}
    }
    private static final class CostLine {
        ProductionWriter.CostType type; BigDecimal base,actual;
        CostLine(ProductionWriter.CostType t,BigDecimal b,BigDecimal a){type=t;base=b;actual=a;}
    }
    private static final class RecipeEdit {
        long id; String name="",note=""; BigDecimal baseQty=BigDecimal.ONE,baseWeight=BigDecimal.ZERO;
        ProductionWriter.Product output;
        AlertDialog dialog;
        final List<MaterialLine> materials=new ArrayList<>();
        final List<CostLine> costs=new ArrayList<>();
    }
    private interface SqlTask<T>{T run(Connection c)throws Exception;}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        sessionUid=getIntent().getIntExtra("atiran_uid",0);
        sessionName=getIntent().getStringExtra("atiran_username");
        if(sessionName==null)sessionName="";
        buildShell();loadCatalog();
    }
    @Override protected void onDestroy(){super.onDestroy();io.shutdownNow();}

    private void buildShell(){
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.rgb(247,248,250));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(18),dp(16),dp(18),dp(14));head.setBackgroundColor(DARK);
        TextView title=text("تولید محصول",21,Color.WHITE,true);head.addView(title,lp(-1,-2));
        TextView sub=text("مدیرمحور • گردش رسمی انبار آتیران • بدون ساخت سری جدید",11,Color.rgb(225,203,157),false);head.addView(sub,lp(-1,-2));
        page.addView(head,lp(-1,-2));
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(14),dp(14),dp(14),dp(22));scroll.addView(body);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        page.addView(button("بستن",false,v->finish()),lp(-1,-2));setContentView(page);
    }

    private void loadCatalog(){
        runSql("در حال بررسی ساختار آتیران…",c->{
            ProductionWriter.preflight(c);ProductionWriter.ensureProfileTable(c);
            List<ProductionWriter.Warehouse> ws=ProductionWriter.warehouses(c);
            List<ProductionWriter.Recipe> rs=ProductionWriter.recipes(c);
            List<ProductionWriter.CostType> cs=ProductionWriter.costTypes(c);
            return new Object[]{ws,rs,cs};
        },v->{
            warehouses=castList(v[0]);recipes=castList(v[1]);costTypes=castList(v[2]);
            if(warehouse==null&&!warehouses.isEmpty())warehouse=warehouses.get(0);
            if(warehouse!=null)loadProducts();else render();
        });
    }
    @SuppressWarnings("unchecked") private <T> List<T> castList(Object value){return (List<T>)value;}
    private void loadProducts(){
        int wid=warehouse.id;
        runSql("در حال دریافت کالاهای انبار…",c->ProductionWriter.products(c,wid),v->{
            products=v;render();
        });
    }

    private void render(){ render(true); }
    private void renderFromModel(){ render(false); }
    private void render(boolean captureCurrentFields){
        if(body==null)return;if(captureCurrentFields)capture();body.removeAllViews();
        if(busy){body.addView(text("در حال انجام عملیات امن آتیران…",14,DARK,true),lp(-1,-2));return;}
        if(warehouses.isEmpty()){
            body.addView(cardText("انبار آتیران پیدا نشد. ابتدا انبار فعال و دسترسی دیتابیس را بررسی کنید."),lp(-1,-2));
            body.addView(button("تلاش مجدد",true,v->loadCatalog()),lp(-1,-2));return;
        }
        addLabel("انبار جاری");body.addView(button(warehouse.name+"  ▾",false,v->pickWarehouse()),lp(-1,-2));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button(recipe==null?"انتخاب فرمول":"فرمول: "+recipe.toString(),true,v->pickRecipe()),new LinearLayout.LayoutParams(0,dp(48),1));
        row.addView(button("فرمول‌ها",false,v->showRecipeList()),new LinearLayout.LayoutParams(0,dp(48),1));
        body.addView(row,lp(-1,-2));
        if(recipe==null){body.addView(cardText("برای برآورد یا ثبت، یک فرمول تولید را انتخاب کنید یا فرمول تازه بسازید."),lp(-1,-2));return;}
        body.addView(cardText("کالای خروجی: "+(output==null?recipe.productName:output.name)+"\nمبنای فرمول: "+qty(recipe.baseOutputQty)+" واحد • "+qty(recipe.baseWeightKg)+" کیلو"),lp(-1,-2));
        LinearLayout amounts=new LinearLayout(this);amounts.setOrientation(LinearLayout.HORIZONTAL);
        outputQty=field("مقدار خروجی",qtyText);outputQty.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weight=field("وزن واقعی (کیلو)",weightText);weight.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amounts.addView(outputQty,new LinearLayout.LayoutParams(0,dp(54),1));amounts.addView(weight,new LinearLayout.LayoutParams(0,dp(54),1));body.addView(amounts,lp(-1,-2));
        description=field("شرح تولید (اختیاری)",descriptionText);body.addView(description,lp(-1,52));
        body.addView(text("مصرف مواد اولیه (قطعهٔ پایه)",14,DARK,true),marginLp(-1,-2,0,10,0,5));
        if(materials.isEmpty())body.addView(cardText("این فرمول مادهٔ اولیه ندارد؛ ویرایش فرمول را باز کنید."),lp(-1,-2));
        for(MaterialLine line:materials){
            LinearLayout item=card();item.addView(text(line.product.name+(line.available?"":" • ناموجود/غیرفعال"),13,line.available?DARK:Color.RED,true),lp(-1,-2));
            EditText amount=field("مقدار مصرف",qty(line.actual));amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setTag(line);item.addView(amount,lp(-1,48));
            body.addView(item,marginLp(-1,-2,0,0,0,7));
        }
        if(!costs.isEmpty()){
            body.addView(text("هزینه‌های سربار",14,DARK,true),marginLp(-1,-2,0,10,0,5));
            for(CostLine line:costs){LinearLayout item=card();item.addView(text(line.type.name,13,DARK,true),lp(-1,-2));EditText amount=field("مبلغ",qty(line.actual));amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setTag(line);item.addView(amount,lp(-1,48));body.addView(item,marginLp(-1,-2,0,0,0,7));}
        }
        body.addView(button("مقیاس مجدد از مبنای فرمول",false,v->rescale()),marginLp(-1,-2,0,4,0,4));
        body.addView(button("برآورد بهای تمام‌شده",false,v->estimate()),lp(-1,48));
        body.addView(button("ثبت قطعی تولید در آتیران",true,v->confirmPost()),marginLp(-1,52,0,8,0,0));
        body.addView(text("ثبت قطعی فقط با شناسهٔ کاربر فعال و مدیر آتیران انجام می‌شود. حساب محلی برای همین عملیات احراز هویت مجدد می‌شود؛ رمز ذخیره نمی‌شود.",11,MUTED,false),marginLp(-1,-2,0,12,0,0));
    }
    private void capture(){
        if(outputQty!=null)qtyText=outputQty.getText().toString();if(weight!=null)weightText=weight.getText().toString();if(description!=null)descriptionText=description.getText().toString();
        if(body!=null)for(int i=0;i<body.getChildCount();i++)captureFields(body.getChildAt(i));
    }
    private void captureFields(View view){
        if(view instanceof EditText){Object tag=view.getTag();String value=((EditText)view).getText().toString();try{if(tag instanceof MaterialLine)((MaterialLine)tag).actual=parse(value);else if(tag instanceof CostLine)((CostLine)tag).actual=parse(value);}catch(Exception ignored){}return;}
        if(view instanceof LinearLayout)for(int i=0;i<((LinearLayout)view).getChildCount();i++)captureFields(((LinearLayout)view).getChildAt(i));
    }

    private void pickWarehouse(){pick("انتخاب انبار",warehouses.toArray(),o->{warehouse=(ProductionWriter.Warehouse)o;recipe=null;output=null;materials.clear();costs.clear();loadProducts();});}
    private void pickRecipe(){if(recipes.isEmpty()){toast("فرمولی در آتیران نیست؛ از دکمهٔ فرمول‌ها، فرمول تازه بسازید.");return;}showRecipeList();}
    private void showRecipeList(){
        List<String> labels=new ArrayList<>();for(ProductionWriter.Recipe r:recipes)labels.add(r.toString());
        List<String> actions=new ArrayList<>();actions.add("ساخت فرمول جدید");for(String label:labels)actions.add(label);
        new AlertDialog.Builder(this).setTitle("فرمول‌های تولید").setItems(actions.toArray(new String[0]),(d,which)->{
            if(which==0){editRecipe(null);return;}
            ProductionWriter.Recipe selected=recipes.get(which-1);
            new AlertDialog.Builder(this).setTitle(selected.toString()).setItems(new String[]{"انتخاب برای تولید","ویرایش فرمول"},(dialog,action)->{
                if(action==0)loadRecipe(selected);else editRecipe(selected);
            }).setNegativeButton("بازگشت",null).show();
        }).setNegativeButton("بستن",null).show();
    }
    private void loadRecipe(ProductionWriter.Recipe selected){
        long rid=selected.id;int wid=warehouse.id;
        runSql("در حال بارگذاری مواد و هزینه‌ها…",c->new Object[]{ProductionWriter.recipeIngredients(c,rid,wid),ProductionWriter.recipeCosts(c,rid)},v->{
            recipe=selected;output=findProduct(selected.productId);materials.clear();costs.clear();
            for(ProductionWriter.RecipeIngredient x:this.<ProductionWriter.RecipeIngredient>castList(v[0]))materials.add(new MaterialLine(x.product,x.baseQtyPieces,x.baseQtyPieces,x.available));
            for(ProductionWriter.RecipeCost x:this.<ProductionWriter.RecipeCost>castList(v[1])){ProductionWriter.CostType t=findCost(x.costId);if(t!=null)costs.add(new CostLine(t,x.amount,x.amount));}
            qtyText=qty(selected.baseOutputQty);weightText=qty(selected.baseWeightKg);descriptionText=selected.description;renderFromModel();
        });
    }
    private ProductionWriter.Product findProduct(long id){for(ProductionWriter.Product p:products)if(p.id==id)return p;return null;}
    private ProductionWriter.CostType findCost(int id){for(ProductionWriter.CostType c:costTypes)if(c.id==id)return c;return null;}

    private void editRecipe(ProductionWriter.Recipe existing){
        RecipeEdit ed=new RecipeEdit();
        if(existing==null){if(products.isEmpty()){toast("کالای فعال برای فرمول پیدا نشد.");return;}showRecipeEditor(ed);return;}
        ed.id=existing.id;ed.name=existing.name;ed.note=existing.description;ed.baseQty=existing.baseOutputQty;ed.baseWeight=existing.baseWeightKg;ed.output=findProduct(existing.productId);
        runSql("در حال دریافت جزئیات فرمول…",c->new Object[]{ProductionWriter.recipeIngredients(c,existing.id,warehouse==null?0:warehouse.id),ProductionWriter.recipeCosts(c,existing.id)},v->{
            for(ProductionWriter.RecipeIngredient x:this.<ProductionWriter.RecipeIngredient>castList(v[0]))ed.materials.add(new MaterialLine(x.product,x.baseQtyPieces,x.baseQtyPieces,x.available));
            for(ProductionWriter.RecipeCost x:this.<ProductionWriter.RecipeCost>castList(v[1])){ProductionWriter.CostType t=findCost(x.costId);if(t!=null)ed.costs.add(new CostLine(t,x.amount,x.amount));}
            showRecipeEditor(ed);
        });
    }
    private void showRecipeEditor(RecipeEdit ed){
        if(ed.dialog!=null&&ed.dialog.isShowing())ed.dialog.dismiss();
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(6),dp(16),dp(8));
        EditText name=field("نام فرمول",ed.name);EditText note=field("شرح",ed.note);
        EditText base=field("مقدار خروجی پایه",qty(ed.baseQty));base.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText baseWeight=field("وزن پایه (کیلوگرم)",qty(ed.baseWeight));baseWeight.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        track(name,value->ed.name=value);track(note,value->ed.note=value);
        trackDecimal(base,value->ed.baseQty=value);trackDecimal(baseWeight,value->ed.baseWeight=value);
        box.addView(name,lp(-1,52));box.addView(note,lp(-1,52));box.addView(base,lp(-1,52));box.addView(baseWeight,lp(-1,52));
        box.addView(button(ed.output==null?"انتخاب کالای خروجی":ed.output.name,v->pickProduct("کالای نهایی",false,p->{ed.output=p;showRecipeEditor(ed);})),lp(-1,48));
        box.addView(text("مواد اولیهٔ پایه",13,DARK,true),marginLp(-1,-2,0,8,0,4));
        for(int i=0;i<ed.materials.size();i++){
            final int ix=i;MaterialLine l=ed.materials.get(i);LinearLayout item=card();item.addView(text(l.product.name+" • "+qty(l.base),12,DARK,false),lp(-1,-2));
            item.addView(button("ویرایش مقدار / حذف",false,v->new AlertDialog.Builder(this).setItems(new String[]{"ویرایش مقدار پایه","حذف از فرمول"},(dialog,which)->{if(which==0)decimalDialog("مقدار پایهٔ "+l.product.name,l.base,n->{l.base=n;l.actual=n;showRecipeEditor(ed);});else{ed.materials.remove(ix);showRecipeEditor(ed);}}).show()),lp(-1,42));box.addView(item,marginLp(-1,-2,0,0,0,5));
        }
        box.addView(button("افزودن مادهٔ اولیه",false,v->pickProduct("مادهٔ اولیه",true,p->decimalDialog("مقدار پایهٔ "+p.name,BigDecimal.ONE,n->{ed.materials.add(new MaterialLine(p,n,n,true));showRecipeEditor(ed);}))),marginLp(-1,44,0,4,0,5));
        box.addView(text("سربار فرمول",13,DARK,true),marginLp(-1,-2,0,8,0,4));
        for(int i=0;i<ed.costs.size();i++){final int ix=i;CostLine l=ed.costs.get(i);box.addView(button(l.type.name+" • "+qty(l.base)+" (حذف)",false,v->{ed.costs.remove(ix);showRecipeEditor(ed);}),lp(-1,42));}
        box.addView(button("افزودن هزینهٔ پیش‌فرض",false,v->pickCost(t->decimalDialog("مبلغ پایهٔ "+t.name,BigDecimal.ZERO,n->{ed.costs.add(new CostLine(t,n,n));showRecipeEditor(ed);}))),marginLp(-1,44,0,4,0,6));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle(ed.id>0?"ویرایش فرمول":"فرمول جدید").setView(new ScrollView(this){{addView(box);}}).setNegativeButton("انصراف",null).setPositiveButton("ذخیره",null).create();
        ed.dialog=dlg;
        dlg.setOnShowListener(v->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(click->{
            try{
                ed.name=name.getText().toString().trim();ed.note=note.getText().toString().trim();ed.baseQty=parse(base.getText().toString());ed.baseWeight=parse(baseWeight.getText().toString());
                if(ed.output==null||ed.name.isEmpty()||ed.materials.isEmpty()||ed.baseQty.signum()<=0)throw new Exception("نام، خروجی، مقدار پایه و دست‌کم یک ماده لازم است.");
                ProductionWriter.RecipeDraft draft=new ProductionWriter.RecipeDraft();draft.id=ed.id;draft.name=ed.name;draft.description=ed.note;draft.productId=ed.output.id;draft.baseOutputQty=ed.baseQty;draft.baseWeightKg=ed.baseWeight;
                for(MaterialLine l:ed.materials){if(l.base.signum()<=0||l.product.id==ed.output.id||l.product.withProductionSerial||l.product.varietyCount!=1)throw new Exception("مادهٔ اولیه باید فعال، بدون الزام سری و دارای یک تنوع باشد.");draft.ingredients.add(new ProductionWriter.RecipeIngredient(l.product,l.base));}
                for(CostLine l:ed.costs){if(l.base.signum()<0)throw new Exception("هزینهٔ پایه منفی است.");draft.costs.add(new ProductionWriter.RecipeCost(l.type.id,l.type.name,l.base));}
                withManagerUserId(uid->runSql("در حال ذخیرهٔ فرمول در آتیران…",c->ProductionWriter.saveRecipe(c,draft,uid),saved->{dlg.dismiss();recipe=null;output=null;materials.clear();costs.clear();toast("فرمول ذخیره شد.");loadCatalog();}));
            }catch(Exception e){toast("فرمول کامل نیست: "+e.getMessage());}
        }));dlg.show();
    }

    private void track(EditText edit,Consumer<String> update){
        edit.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}@Override public void onTextChanged(CharSequence s,int start,int before,int count){update.accept(s.toString());}@Override public void afterTextChanged(Editable e){}});
    }
    private void trackDecimal(EditText edit,Consumer<BigDecimal> update){
        track(edit,value->{try{update.accept(parse(value));}catch(Exception ignored){}});
    }

    private void pickProduct(String title,boolean raw,Consumer<ProductionWriter.Product> done){
        List<ProductionWriter.Product> choices=new ArrayList<>();for(ProductionWriter.Product p:products){if(p.varietyCount!=1)continue;if(raw&&p.withProductionSerial)continue;choices.add(p);}
        List<String> labels=new ArrayList<>();for(ProductionWriter.Product p:choices)labels.add(p.name+" • "+p.id);
        if(labels.isEmpty()){toast("کالای فعال با دقیقاً یک تنوع پیدا نشد.");return;}
        new AlertDialog.Builder(this).setTitle(title).setItems(labels.toArray(new String[0]),(d,i)->done.accept(choices.get(i))).setNegativeButton("انصراف",null).show();
    }
    private void pickCost(Consumer<ProductionWriter.CostType> done){
        List<String> labels=new ArrayList<>();for(ProductionWriter.CostType c:costTypes)labels.add(c.name);
        if(labels.isEmpty()){toast("هزینهٔ پیش‌فرض در آتیران تعریف نشده است.");return;}
        new AlertDialog.Builder(this).setTitle("نوع هزینه").setItems(labels.toArray(new String[0]),(d,i)->done.accept(costTypes.get(i))).setNegativeButton("انصراف",null).show();
    }
    private void decimalDialog(String title,BigDecimal current,Consumer<BigDecimal> done){
        EditText input=field(title,qty(current));input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("انصراف",null).setPositiveButton("تأیید",(d,w)->{try{done.accept(parse(input.getText().toString()));}catch(Exception e){toast("مقدار معتبر نیست.");}}).show();
    }
    private void rescale(){
        try{capture();BigDecimal outQty=parse(qtyText);if(recipe==null||recipe.baseOutputQty.signum()<=0)throw new Exception("مبنای فرمول معتبر نیست.");BigDecimal factor=outQty.divide(recipe.baseOutputQty,8,RoundingMode.HALF_UP);
            for(MaterialLine l:materials)l.actual=l.base.multiply(factor).setScale(3,RoundingMode.HALF_UP);for(CostLine l:costs)l.actual=l.base.multiply(factor).setScale(2,RoundingMode.HALF_UP);qtyText=qty(outQty);renderFromModel();
        }catch(Exception e){toast("مقدار خروجی یا مبنای فرمول معتبر نیست.");}
    }

    private ProductionWriter.Request buildRequest()throws Exception{
        capture();if(recipe==null||output==null)throw new Exception("ابتدا فرمول تولید را انتخاب کنید.");if(warehouse==null)throw new Exception("انبار انتخاب نشده است.");
        ProductionWriter.Request r=new ProductionWriter.Request();r.recipeId=recipe.id;r.finishedProductId=output.id;r.warehouseId=warehouse.id;
        r.outputQty=parse(qtyText);r.actualWeightKg=parse(weightText);r.description=descriptionText;
        if(r.outputQty.signum()<=0||r.actualWeightKg.signum()<=0)throw new Exception("مقدار و وزن باید بزرگ‌تر از صفر باشند.");
        if(materials.isEmpty())throw new Exception("مادهٔ اولیه ثبت نشده است.");
        for(MaterialLine l:materials){if(!l.available)throw new Exception("مادهٔ «"+l.product.name+"» فعال نیست.");if(l.product.withProductionSerial)throw new Exception("مادهٔ سری‌دار قابل ثبت حدسی نیست.");if(l.product.varietyCount!=1)throw new Exception("ماده باید دقیقاً یک تنوع ثبت‌شده داشته باشد.");if(l.actual.signum()<=0)throw new Exception("مقدار مصرف باید بیشتر از صفر باشد.");r.ingredients.add(new ProductionWriter.Ingredient(l.product.id,l.product.name,l.product.unit,l.product.packSize,l.actual));}
        for(CostLine l:costs){if(l.actual.signum()<0)throw new Exception("هزینهٔ سربار منفی است.");r.costs.add(new ProductionWriter.ExtraCost(l.type.id,l.actual));}
        return r;
    }
    private void estimate(){
        ProductionWriter.Request req;try{req=buildRequest();}catch(Exception e){toast(e.getMessage());return;}
        runSql("در حال محاسبهٔ بهای تمام‌شده…",c->ProductionWriter.estimate(c,req),costing->new AlertDialog.Builder(this).setTitle("برآورد بهای تمام‌شده")
                .setMessage("مواد اولیه: "+money(costing.materialCost)+"\nسربار: "+money(costing.overheadCost)+"\nجمع تمام‌شده: "+money(costing.totalCost)+"\nهر واحد: "+money(costing.costPerUnit)+"\nهر کیلو: "+money(costing.costPerKg))
                .setPositiveButton("تأیید",null).show());
    }
    private void confirmPost(){
        ProductionWriter.Request req;try{req=buildRequest();}catch(Exception e){toast(e.getMessage());return;}
        new AlertDialog.Builder(this).setTitle("ثبت قطعی تولید؟").setMessage("موجودی مواد اولیه از همین انبار کسر و محصول خروجی به موجودی افزوده می‌شود. سری جدید ساخته نمی‌شود؛ سابقهٔ سری‌های قبلی دست‌نخورده می‌ماند.")
                .setNegativeButton("بازگشت",null).setPositiveButton("ثبت قطعی",(d,w)->{
                    if(idempotencyKey.isEmpty())idempotencyKey=UUID.randomUUID().toString();req.idempotencyKey=idempotencyKey;
                    withManagerUserId(uid->runSql("در حال ثبت گردش‌های رسمی آتیران…",c->ProductionWriter.post(c,req,uid),posted->{
                        idempotencyKey="";toast(posted.duplicate?"این تولید قبلاً ثبت شده است؛ شمارهٔ "+posted.productionId:"تولید ثبت شد؛ شمارهٔ "+posted.productionId+" • هر واحد "+money(posted.costing.finishedUnitPrice));
                        recipe=null;output=null;materials.clear();costs.clear();loadCatalog();
                    }));
                }).show();
    }

    private void withManagerUserId(Consumer<Integer> done){
        if(sessionUid>0){runSql("در حال بررسی هویت مدیر آتیران…",c->ProductionWriter.resolveAtiranUserId(c,sessionUid),done);return;}
        credentialDialog(done);
    }
    private void credentialDialog(Consumer<Integer> done){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(8),dp(16),0);
        box.addView(text("برای همین عملیات، مدیر فعال آتیران را تأیید کنید. رمز ذخیره نمی‌شود.",12,MUTED,false),lp(-1,-2));
        EditText user=field("نام کاربری مدیر آتیران",sessionName);EditText pass=field("رمز عبور","");pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);pass.setSaveEnabled(false);
        box.addView(user,lp(-1,52));box.addView(pass,lp(-1,52));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("احراز هویت برای این عملیات").setView(box).setNegativeButton("انصراف",null).setPositiveButton("تأیید",null).create();
        dlg.setOnDismissListener(v->pass.setText(""));dlg.setOnShowListener(v->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(click->{
            String username=user.getText().toString().trim(),password=pass.getText().toString();if(username.isEmpty()||password.isEmpty()){toast("نام کاربری و رمز لازم است.");return;}
            pass.setText("");dlg.dismiss();runSql("در حال تأیید مدیر آتیران…",c->ProductionWriter.authenticateAtiranManager(c,username,password),done);
        }));dlg.show();
    }

    private <T> void runSql(String wait,SqlTask<T> task,Consumer<T> done){
        if(busy)return;capture();busy=true;render();toast(wait);
        io.execute(()->{T value=null;Exception error=null;try(Connection c=MainActivity.backgroundConnection(this)){value=task.run(c);}catch(Exception e){error=e;}
            T result=value;Exception failure=error;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;if(failure!=null){render();showError(failure);}else{done.accept(result);}});
        });
    }
    private void showError(Exception e){String message=e.getMessage();if(message==null||message.trim().isEmpty())message=e.getClass().getSimpleName();new AlertDialog.Builder(this).setTitle("عملیات انجام نشد").setMessage(message).setPositiveButton("بستن",null).show();}
    private void pick(String title,Object[] items,Consumer<Object> done){List<String> labels=new ArrayList<>();for(Object item:items)labels.add(String.valueOf(item));new AlertDialog.Builder(this).setTitle(title).setItems(labels.toArray(new String[0]),(d,i)->done.accept(items[i])).setNegativeButton("انصراف",null).show();}
    private void addLabel(String s){body.addView(text(s,12,MUTED,true),marginLp(-1,-2,0,4,0,4));}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(12),dp(10),dp(12),dp(10));l.setBackgroundColor(Color.WHITE);return l;}
    private TextView cardText(String s){TextView t=text(s,13,DARK,false);t.setPadding(dp(12),dp(12),dp(12),dp(12));t.setBackgroundColor(Color.WHITE);return t;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(14);e.setTextColor(DARK);e.setHintTextColor(MUTED);e.setHint(hint);e.setText(value==null?"":value);return e;}
    private View button(String label,boolean primary,View.OnClickListener click){TextView b=text(label,14,primary?Color.WHITE:DARK,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setBackgroundColor(primary?GOLD:Color.rgb(232,235,239));b.setOnClickListener(v->{if(!busy)click.onClick(v);});return b;}
    private View button(String label,View.OnClickListener click){return button(label,true,click);}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h<0?h:dp(h));}
    private LinearLayout.LayoutParams marginLp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=lp(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private BigDecimal parse(String value)throws Exception{
        String raw=value==null?"":value.trim();StringBuilder normalized=new StringBuilder(raw.length());
        for(int i=0;i<raw.length();i++){
            char ch=raw.charAt(i);
            if(ch>='۰'&&ch<='۹')normalized.append((char)('0'+ch-'۰'));
            else if(ch>='٠'&&ch<='٩')normalized.append((char)('0'+ch-'٠'));
            else if(ch=='٫')normalized.append('.');
            else if(ch=='٬'||ch==',')continue;
            else normalized.append(ch);
        }
        String s=normalized.toString();if(s.isEmpty())throw new Exception("مقدار خالی است.");return new BigDecimal(s);
    }
    private String qty(BigDecimal value){return value==null?"0":value.stripTrailingZeros().toPlainString();}
    private String money(BigDecimal value){return String.format(LocaleHolder.ROOT,"%,.0f",value==null?0:value.doubleValue())+" ریال";}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private static final class LocaleHolder{static final java.util.Locale ROOT=java.util.Locale.US;}
}
