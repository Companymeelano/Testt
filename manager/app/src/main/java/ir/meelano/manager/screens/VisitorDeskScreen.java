package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AiRouter;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.VisitorIdentity;
import ir.meelano.manager.core.VisitorPrefactorWriter;
import ir.meelano.manager.core.VisitorPricePolicy;
import ir.meelano.manager.core.VisitorReceiptWriter;
import ir.meelano.manager.core.VisitorVoiceOrderParser;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Theme;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Field-sales workspace for the authenticated Atiran visitor, with strict owner scoping. */
public final class VisitorDeskScreen extends Screen {
    private static final int HOME = 0, ROUTE = 1, ORDER = 2, MY_ORDERS = 3, RECEIPT = 4;
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private int page = HOME;
    private Customer selectedCustomer;
    private Customer receiptCustomer;
    private final List<CartLine> cart = new ArrayList<>();
    private String draftUuid = UUID.randomUUID().toString();
    private String receiptUuid = UUID.randomUUID().toString();
    private String lastAiStatus = "";
    private String receiptMethod = "cash";
    private String receiptAmount = "";
    private String receiptNote = "پیش‌دریافت مشتری";
    private String receiptFingerprint = "";
    private String chequeNumber="", chequeDueDate="", chequeBankName="", chequeBranch="", chequeAccount="", chequeSayad="", chequeNote="";
    private boolean chequeInquired;
    private int selectedBankId;
    private String selectedBankName = "";
    private int customerPickerReturnPage = ORDER;
    private LinearLayout contentRef;

    private static final class Customer {
        final long code; final String name; final String phone; final String address;
        Customer(long code, String name, String phone, String address) {
            this.code=code; this.name=name==null?"":name; this.phone=phone==null?"":phone; this.address=address==null?"":address;
        }
    }

    private static final class Product {
        final String code, name, unit;
        final BigDecimal price, stock;
        final int group;
        final long activePriceRows;
        Product(String code,String name,String unit,BigDecimal price,BigDecimal stock,int group,long rows) {
            this.code=code;this.name=name;this.unit=unit;this.price=price;this.stock=stock;this.group=group;this.activePriceRows=rows;
        }
    }

    private static final class CartLine {
        final Product product; BigDecimal qty;
        CartLine(Product product,BigDecimal qty){this.product=product;this.qty=qty;}
        BigDecimal total(){return product.price.multiply(qty).setScale(2,java.math.RoundingMode.HALF_UP);}
    }

    public VisitorDeskScreen(MainActivity a) {
        super(a);
        android.content.SharedPreferences p=a.getSharedPreferences("meelano_visitor_draft",0);
        receiptUuid=p.getString("receipt_uuid_"+AtiranAuth.sessionUid(a),receiptUuid);
        receiptFingerprint=p.getString("receipt_fp_"+AtiranAuth.sessionUid(a),"");
    }
    @Override public String id(){return "visitor_work";}
    @Override public String title(){return "فروش میدانی";}
    @Override public String glyph(){return "🧾";}
    @Override public int accent(){return Theme.GOLD;}

    @Override public void render(LinearLayout content) {
        contentRef=content;
        content.removeAllViews();
        content.addView(a.kit.hero("🧭","فروش میدانی میلانو","ثبت و پیگیری کارهای حساب ویزیتور آتیران",Theme.GOLD),a.kit.lp(-1,-2));
        content.addView(a.kit.gap(10));
        String[] labels={"خانه","مسیر ویزیت","پیش‌فاکتور","سفارش‌های من","دریافت وجه"};
        content.addView(a.kit.chips(labels,page,i->{page=i;render(content);}),a.kit.lp(-1,-2));
        content.addView(a.kit.gap(10));
        if(page==HOME) renderHome(content);
        else if(page==ROUTE) loadCustomers(content,false);
        else if(page==ORDER) renderOrder(content);
        else if(page==MY_ORDERS) loadMyOrders(content);
        else renderReceipt(content);
    }

    private void renderHome(LinearLayout c) {
        c.addView(a.kit.hint("فروش میدانی به حساب کاربری و ردیف ویزیتور فعال آتیران متصل است. مشتری‌ها فقط از مسیر خود همین ویزیتور خوانده می‌شوند."),a.kit.lp(-1,-2));
        c.addView(a.kit.gap(8));
        action(c,"📍 مسیر و ثبت نتیجهٔ ویزیت","مشتریان تخصیص‌یافته به حساب آتیران شما",ROUTE);
        action(c,"🧾 ساخت پیش‌فاکتور","سبد خرید، انتخاب مشتری و ثبت پیش‌نویس برای بررسی",ORDER);
        action(c,"▤ پیش‌فاکتورهای من","وضعیت سفارش‌هایی که با همین حساب ثبت کرده‌اید",MY_ORDERS);
        action(c,"☷ دریافت وجه / پیش‌دریافت","قبض رسمی آتیران برای مشتری مجاز",RECEIPT);
        c.addView(a.kit.btnGhost("▤ ثبت گزارش پایان روز",Theme.INFO,v->composeDailyReport()),a.kit.lp(-1,-2));
        c.addView(a.kit.hint("قیمت سفارش از DefaultPriceGrp و نرخ forosh1 تا forosh5 آتیران می‌آید؛ اگر نرخ مبهم یا ناموجود باشد ثبت آن قلم مسدود می‌شود."),a.kit.lp(-1,-2));
    }

    private void action(LinearLayout c,String title,int target){
        c.addView(a.kit.btnGold(title,v->{page=target;render(contentRef);}),a.kit.lp(-1,-2));
        c.addView(a.kit.gap(7));
    }

    private void loadCustomers(LinearLayout c, boolean forPicker) {
        c.addView(a.kit.loading("در حال دریافت مشتریان مسیر شما…"),a.kit.lp(-1,-2));
        a.repo.run(db->{
            VisitorIdentity.Identity identity=identity(db);
            Meta m=new Meta(db);
            if(!m.table("CUSTOMERS")) throw new Exception("جدول مشتریان آتیران پیدا نشد.");
            String key=m.col("CUSTOMERS","SHMO","shmo");
            String name=m.col("CUSTOMERS","MONAME","name","CusName");
            String vis=m.col("CUSTOMERS","vis_rdf","VisitorID","visitor_id");
            String active=m.col("CUSTOMERS","active","Active");
            String phone=m.col("CUSTOMERS","cell","phone","tell","tel");
            String address=m.col("CUSTOMERS","address","addre","Address");
            if(key==null||name==null||vis==null||active==null) throw new Exception("شناسهٔ مشتری، نام، پیوند ویزیتور یا وضعیت فعال‌بودن در جدول مشتریان موجود نیست.");
            String sql="SELECT TOP (300) TRY_CONVERT(bigint,"+q(key)+"),TRY_CONVERT(nvarchar(250),"+q(name)+")"
                    +(phone==null?",CAST(N'' AS nvarchar(80))":",TRY_CONVERT(nvarchar(80),"+q(phone)+")")
                    +(address==null?",CAST(N'' AS nvarchar(300))":",TRY_CONVERT(nvarchar(300),"+q(address)+")")
                    +" FROM "+m.qTable("CUSTOMERS")+" WHERE TRY_CONVERT(int,"+q(vis)+")=? AND TRY_CONVERT(nvarchar(10),"+q(active)+") IN (N't',N'T',N'1',N'true',N'TRUE) ORDER BY "+q(name);
            List<Customer> out=new ArrayList<>();
            try(java.sql.PreparedStatement ps=db.prepareStatement(sql)){ps.setInt(1,identity.visitorId);try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new Customer(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)));}}
            return out;
        },new Repo.Cb<List<Customer>>(){
            @Override public void ok(List<Customer> rows){
                if(forPicker){showCustomerPicker(rows);return;}
                c.removeAllViews();
                if(page==ROUTE){
                    c.addView(a.kit.text("مشتریان مسیر شما",16,Theme.TEXT,true),a.kit.lp(-1,-2));
                    c.addView(a.kit.hint("برای ثبت نتیجهٔ بازدید روی مشتری بزنید."),a.kit.lp(-1,-2));
                    for(Customer x:rows){
                        View v=a.kit.personRow(x.name,"کد "+Money.fa(String.valueOf(x.code))+ (x.phone.isEmpty()?"":" • "+x.phone),x.address,"ثبت ویزیت",Theme.GOLD,z->recordVisit(x));
                        c.addView(v,a.kit.lp(-1,-2));c.addView(a.kit.gap(6));
                    }
                    if(rows.isEmpty())c.addView(a.kit.empty("مشتری تخصیص‌یافته پیدا نشد","پیوند visitors.UserID یا Username آتیران را بررسی کنید."),a.kit.lp(-1,-2));
                } else showCustomerPicker(rows);
            }
            @Override public void fail(String error){showError(c,error,()->render(c));}
        });
    }

    private void recordVisit(Customer customer){
        EditText result=a.kit.edit("نتیجهٔ بازدید","خرید کرد / نیازمند پیگیری");
        EditText note=a.kit.edit("توضیحات"," ");
        LinearLayout box=a.kit.v();box.addView(result,a.kit.lp(-1,-2));box.addView(note,a.kit.lp(-1,-2));
        new AlertDialog.Builder(a).setTitle("ثبت نتیجهٔ ویزیت").setView(box).setNegativeButton("لغو",null).setPositiveButton("ثبت",(d,w)->{
            String r=result.getText().toString().trim(), n=note.getText().toString().trim();
            if(r.isEmpty()){a.kit.toast("نتیجهٔ ویزیت را وارد کنید");return;}
            a.repo.run(db->{
                VisitorIdentity.Identity id=identity(db);ensureVisitTable(db);
                try(java.sql.PreparedStatement ps=db.prepareStatement("INSERT INTO dbo.meelano_visit_results(visitor_username,visitor_id,customer_code,customer_name,result,notes,created_at) VALUES(?,?,?,?,?,?,SYSDATETIME())")){
                    ps.setString(1,String.valueOf(id.visitorId));ps.setString(3,String.valueOf(customer.code));ps.setString(4,customer.name);ps.setString(5,r);ps.setString(6,n);ps.executeUpdate();
                }
                return true;
            },new Repo.Cb<Boolean>(){@Override public void ok(Boolean v){a.kit.toast("نتیجهٔ بازدید ثبت شد");render(contentRef);}@Override public void fail(String e){a.kit.toast("ثبت بازدید انجام نشد: "+e);}});
        }).show();
    }

    private void composeDailyReport(){
        EditText note=a.kit.edit("یادداشت پایان روز (اختیاری)","");
        new AlertDialog.Builder(a).setTitle("گزارش پایان روز").setMessage("گزارش تعداد و مبلغ پیش‌فاکتورهای امروز و بازدیدهای ثبت‌شده را می‌سازد.").setView(note).setNegativeButton("لغو",null).setPositiveButton("ثبت گزارش",(d,w)->{
            final String extra=note.getText().toString().trim();
            a.repo.run(db->{VisitorIdentity.Identity id=identity(db);VisitorPrefactorWriter.ensureTables(db);ensureVisitTable(db);long count=0,visits=0;double amount=0;
                try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT COUNT_BIG(1),ISNULL(SUM(ISNULL(grand_total,total_amount)),0) FROM dbo.meelano_prefactors WHERE CONVERT(date,created_at)=CONVERT(date,SYSDATETIME()) AND visitor_id=?")){ps.setString(1,String.valueOf(id.visitorId));try(java.sql.ResultSet rs=ps.executeQuery()){if(rs.next()){count=rs.getLong(1);amount=rs.getDouble(2);}}}
                try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT COUNT_BIG(1) FROM dbo.meelano_visit_results WHERE CONVERT(date,created_at)=CONVERT(date,SYSDATETIME()) AND visitor_id=?")){ps.setString(1,String.valueOf(id.visitorId));try(java.sql.ResultSet rs=ps.executeQuery()){if(rs.next())visits=rs.getLong(1);}}
                String report="گزارش پایان روز ویزیتور «"+id.displayName+"» • "+Jalali.todayStr()+"\nبازدید ثبت‌شده: "+visits+"\nپیش‌فاکتور امروز: "+count+"\nجمع پیش‌فاکتورها: "+Money.rial(amount)+(extra.isEmpty()?"":"\nیادداشت: "+extra);
                try(java.sql.Statement st=db.createStatement()){st.execute("IF OBJECT_ID(N'dbo.meelano_day_reports',N'U') IS NULL CREATE TABLE dbo.meelano_day_reports(id bigint IDENTITY(1,1) PRIMARY KEY,visitor_username nvarchar(160) NULL,visitor_id nvarchar(80) NULL,report_text nvarchar(max) NULL,created_at datetime2 NOT NULL DEFAULT SYSDATETIME())");}
                try(java.sql.PreparedStatement ps=db.prepareStatement("INSERT INTO dbo.meelano_day_reports(visitor_username,visitor_id,report_text,created_at) VALUES(?,?,?,SYSDATETIME())")){ps.setString(1,id.username);ps.setString(2,String.valueOf(id.visitorId));ps.setString(3,report);ps.executeUpdate();}
                return report;
            },new Repo.Cb<String>(){@Override public void ok(String report){new AlertDialog.Builder(a).setTitle("گزارش پایان روز ثبت شد").setMessage(report).setPositiveButton("بستن",null).show();}@Override public void fail(String e){a.kit.toast("گزارش پایان روز ثبت نشد: "+e);}});
        }).show();
    }

    private void renderOrder(LinearLayout c){

        c.addView(a.kit.text("پیش‌فاکتور جدید",16,Theme.TEXT,true),a.kit.lp(-1,-2));
        if(selectedCustomer==null){
            c.addView(a.kit.hint("ابتدا مشتری را از مسیر ویزیت خود انتخاب کنید."),a.kit.lp(-1,-2));
            c.addView(a.kit.btnGold("انتخاب مشتری",v->{customerPickerReturnPage=ORDER;loadCustomers(contentRef,true);}),a.kit.lp(-1,-2));
        }else{
            c.addView(a.kit.personRow(selectedCustomer.name,"کد "+Money.fa(String.valueOf(selectedCustomer.code)),"مشتری پیش‌فاکتور","تغییر",Theme.INFO,v->chooseCustomerForOrder()),a.kit.lp(-1,-2));
            c.addView(a.kit.gap(8));
            c.addView(a.kit.btnGhost("＋ انتخاب کالا از فهرست",Theme.GOLD,v->loadProductsForPicker()),a.kit.lp(-1,-2));
            c.addView(a.kit.btnGhost("▦ اسکن بارکد کالا",Theme.VIOLET,v->a.startScan(code->findProducts(code,1))),a.kit.lp(-1,-2));
            c.addView(a.kit.btnGhost("🎙 گفتن کالا و تعداد",Theme.VIOLET,v->a.startVoiceSearch(text->parseOrderPhrase(text,false))),a.kit.lp(-1,-2));
            c.addView(a.kit.btnGhost("✦ گفت‌وگوی هوشمند برای افزودن کالا",Theme.INFO,v->showAiInput()),a.kit.lp(-1,-2));
            if(!lastAiStatus.isEmpty())c.addView(a.kit.hint(lastAiStatus),a.kit.lp(-1,-2));
        }
        if(cart.isEmpty())c.addView(a.kit.empty("سبد خالی است","قلم‌ها را با فهرست یا گفتگو اضافه کنید؛ هر افزودن را پیش از ثبت تأیید می‌کنید."),a.kit.lp(-1,-2));
        BigDecimal total=BigDecimal.ZERO;
        for(int i=0;i<cart.size();i++){
            final int at=i;CartLine line=cart.get(i);total=total.add(line.total());
            c.addView(a.kit.personRow(line.product.name,"کد "+line.product.code+" • "+qty(line.qty)+" "+line.product.unit,
                    money(line.total()),"حذف",Theme.GOLD,v->{cart.remove(at);render(contentRef);}),a.kit.lp(-1,-2));
        }
        if(!cart.isEmpty()){
            c.addView(a.kit.gap(8));c.addView(a.kit.kv("جمع پیش‌فاکتور",money(total),Theme.GOLD),a.kit.lp(-1,-2));
            EditText note=a.kit.edit("یادداشت سفارش (اختیاری)","");c.addView(note,a.kit.lp(-1,-2));
            c.addView(a.kit.btnGold("ثبت برای بررسی مدیر",v->submitPrefactor(note.getText().toString())),a.kit.lp(-1,-2));
            c.addView(a.kit.hint("ثبت با شناسهٔ یکتا و تراکنش انجام می‌شود؛ تکرار ارسال همان پیش‌فاکتور دوم نمی‌سازد. نرخ‌ها از قیمت پیش‌فرض آتیران آمده‌اند."),a.kit.lp(-1,-2));
        }
    }

    private void loadProductsForPicker(){
        EditText query=a.kit.edit("نام یا کد کالا","");
        new AlertDialog.Builder(a).setTitle("جستجوی کالا").setView(query).setNegativeButton("لغو",null)
                .setPositiveButton("جستجو",(d,w)->{
                    String term=query.getText().toString().trim();
                    if(term.length()<2){a.kit.toast("برای جستجوی دقیق، دست‌کم دو نویسه وارد کنید");return;}
                    a.repo.run(db->loadCatalog(db,term),new Repo.Cb<List<Product>>(){
                        @Override public void ok(List<Product> rows){showProductPicker(rows);}
                        @Override public void fail(String e){a.kit.toast("فهرست کالا/قیمت امن در دسترس نیست: "+e);}
                    });
                }).show();
    }

    private List<Product> loadCatalog(java.sql.Connection db,String search)throws Exception{
        VisitorIdentity.Identity id=identity(db);Meta m=new Meta(db);
        String invName=m.col("inventory","naka","Name","ProductName");
        String invBarcode=m.col("inventory","coka","Barcode","bar_code","ProductCode");
        String invId=m.col("inventory","shka","SHKA","ProductID");
        String unit=m.col("inventory","vahsanj","unit","UnitName");
        String stock=m.col("inventory","mojkavah","stock","Stock");
        String active=m.col("inventory","active","Active");
        String online=m.col("inventory","ActiveCapillarySales","active_capillary_sales");
        if(invId==null||invName==null||active==null||online==null)throw new Exception("شناسه/نام کالا یا وضعیت فعال‌بودن فروش میدانی در inventory آتیران پیدا نشد.");
        int group=defaultPriceGroup(db,m,id.visitorId);
        String priceCol=VisitorPricePolicy.column(group);
        if(priceCol==null)throw new Exception("DefaultPriceGrp ویزیتور معتبر نیست؛ قیمت انتخاب نشد.");
        if(!m.table("forosh_price"))throw new Exception("جدول forosh_price آتیران پیدا نشد.");
        String pKey=m.col("forosh_price","shka","SHKA","ProductID","KalaID");
        String pVal=m.col("forosh_price",priceCol);
        String pActive=m.col("forosh_price","active","Active");
        if(pKey==null||pVal==null||pActive==null)throw new Exception("ستون نرخ یا وضعیت فعال‌بودن آن در forosh_price پیدا نشد.");
        String activeP=pActive==null?"":" AND (TRY_CONVERT(nvarchar(10),p."+q(pActive)+") IN (N't',N'T',N'1',N'true',N'TRUE'))";
        List<Object> binds=new ArrayList<>();String where="1=1";
        if(active!=null)where+=" AND (TRY_CONVERT(nvarchar(10),i."+q(active)+") IN (N't',N'T',N'1',N'true',N'TRUE'))";
        if(online!=null)where+=" AND ISNULL(TRY_CONVERT(int,i."+q(online)+"),1)=1";
        if(search!=null&&!search.trim().isEmpty()){where+=" AND (TRY_CONVERT(nvarchar(500),i."+q(invName)+") LIKE N'%'+?+N'%'";binds.add(search.trim());if(invBarcode!=null){where+=" OR TRY_CONVERT(nvarchar(100),i."+q(invBarcode)+") LIKE N'%'+?+N'%'";binds.add(search.trim());}where+=")";}
        String sql="SELECT TOP (250) TRY_CONVERT(nvarchar(100),i."+q(invId)+"),TRY_CONVERT(nvarchar(500),i."+q(invName)+")"
                +(unit==null?",CAST(N'عدد' AS nvarchar(80))":",TRY_CONVERT(nvarchar(80),i."+q(unit)+")")
                +",ISNULL(TRY_CONVERT(decimal(19,2),px.price),0),"+(stock==null?"CAST(0 AS decimal(19,3))":"ISNULL(TRY_CONVERT(decimal(19,3),i."+q(stock)+"),0)")+",ISNULL(px.price_rows,0)"
                +" FROM "+m.qTable("inventory")+" i OUTER APPLY (SELECT CASE WHEN COUNT_BIG(1)=1 THEN MAX(TRY_CONVERT(decimal(19,2),p."+q(pVal)+")) ELSE 0 END price,COUNT_BIG(1) price_rows FROM "+m.qTable("forosh_price")+" p WHERE TRY_CONVERT(nvarchar(100),p."+q(pKey)+")=TRY_CONVERT(nvarchar(100),i."+q(invId)+")"+activeP+") px WHERE "+where+" ORDER BY TRY_CONVERT(nvarchar(500),i."+q(invName)+")";
        List<Product> out=new ArrayList<>();
        try(java.sql.PreparedStatement ps=db.prepareStatement(sql)){int n=1;for(Object b:binds)ps.setObject(n++,b);try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next()){
            BigDecimal p=rs.getBigDecimal(4), st=rs.getBigDecimal(5);long rows=rs.getLong(6);
            out.add(new Product(rs.getString(1),rs.getString(2),rs.getString(3),p==null?BigDecimal.ZERO:p,st==null?BigDecimal.ZERO:st,group,rows));
        }}}
        return out;
    }

    private int defaultPriceGroup(java.sql.Connection db,Meta m,int visitorId)throws Exception{
        if(!m.table("DeviceSettings"))throw new Exception("DeviceSettings آتیران برای دریافت قیمت پیش‌فرض پیدا نشد.");
        String v=m.col("DeviceSettings","VisitorId","VisitorID","vis_rdf");String g=m.col("DeviceSettings","DefaultPriceGrp","DefaultPriceGroup");
        if(v==null||g==null)throw new Exception("ستون‌های VisitorId/DefaultPriceGrp در DeviceSettings پیدا نشد.");
        String sql="SELECT CASE WHEN COUNT_BIG(1)=1 AND COUNT_BIG(1)=COUNT_BIG(TRY_CONVERT(int,"+q(g)+")) AND MIN(TRY_CONVERT(int,"+q(g)+"))=MAX(TRY_CONVERT(int,"+q(g)+")) THEN MIN(TRY_CONVERT(int,"+q(g)+")) ELSE 0 END FROM "+m.qTable("DeviceSettings")+" WHERE TRY_CONVERT(int,"+q(v)+")=?";
        try(java.sql.PreparedStatement ps=db.prepareStatement(sql)){ps.setInt(1,visitorId);try(java.sql.ResultSet rs=ps.executeQuery()){return rs.next()?rs.getInt(1):0;}}
    }

    private void showProductPicker(List<Product> rows){
        LinearLayout list=a.kit.v();
        for(Product p:rows){
            boolean safe=VisitorPricePolicy.isUnambiguous(p.group,p.activePriceRows,p.price.doubleValue());
            String detail="کد "+p.code+" • "+p.unit+" • موجودی ثبت‌شده "+qty(p.stock)+ (safe?" • "+money(p.price):" • نرخ امن موجود نیست");
            list.addView(a.kit.personRow(p.name,detail,safe?money(p.price):"—",safe?"افزودن":"مسدود",safe?Theme.SUCCESS:Theme.DANGER,v->{if(safe)askQuantity(p);}),a.kit.lp(-1,-2));
        }
        if(rows.isEmpty())list.addView(a.kit.empty("کالایی پیدا نشد",null),a.kit.lp(-1,-2));
        new AlertDialog.Builder(a).setTitle("انتخاب کالای دارای نرخ یکتا").setView(a.kit.scrollWrap(list,560)).setNegativeButton("بستن",null).show();
    }

    private void askQuantity(Product product){
        EditText q=a.kit.edit("تعداد "+product.unit,"1");q.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(a).setTitle(product.name).setView(q).setNegativeButton("لغو",null).setPositiveButton("تأیید افزودن",(d,w)->{
            try{BigDecimal qty=new BigDecimal(Money.en(q.getText().toString()).replace(',','.'));if(qty.signum()<=0||qty.compareTo(new BigDecimal("100000"))>0)throw new Exception();cart.add(new CartLine(product,qty));persistDraft();render(contentRef);}
            catch(Exception e){a.kit.toast("تعداد معتبر وارد کنید");}
        }).show();
    }

    private void showAiInput(){
        if(!AiSettings.ready(a)){a.kit.toast("دستیار آنلاین هنوز با تأیید مدیر و کلید سرویس فعال نشده است؛ از ورود صوتی آفلاین یا جستجوی کالا استفاده کنید.");return;}
        android.content.SharedPreferences privacy=a.getSharedPreferences("meelano_visitor_ai",0);
        String consentKey="order_phrase_consent_"+AtiranAuth.sessionUid(a);
        if(!privacy.getBoolean(consentKey,false)){
            new AlertDialog.Builder(a).setTitle("اجازهٔ پردازش گفتار سفارش").setMessage("فقط جملهٔ نام کالا و تعداد برای پیشنهاد نام/مقدار به سرویس هوش مصنوعی پیکربندی‌شده ارسال می‌شود؛ نام مشتری، قیمت و اطلاعات حساب ارسال نمی‌شوند. هیچ قلمی بدون انتخاب کالا و تأیید شما به سبد افزوده نمی‌شود.").setNegativeButton("فعلاً نه",null).setPositiveButton("می‌پذیرم",(d,w)->{privacy.edit().putBoolean(consentKey,true).apply();showAiInput();}).show();return;
        }
        EditText phrase=a.kit.edit("مثلاً: دو بسته پسته اکبری و یک کیلو بادام"," ");
        phrase.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(a).setTitle("گفت‌وگوی هوشمند سفارش").setMessage("هوش مصنوعی فقط نام کالا و مقدار پیشنهادی را استخراج می‌کند؛ انتخاب کالا و تأیید افزودن با شماست.").setView(phrase).setNegativeButton("لغو",null).setPositiveButton("تجزیه و یافتن کالا",(d,w)->parseOrderPhrase(phrase.getText().toString(),true)).show();
    }

    private void parseOrderPhrase(String text,boolean useAi){
        String input=text==null?"":text.trim();if(input.isEmpty())return;
        if(!useAi){VisitorVoiceOrderParser.Request parsed=VisitorVoiceOrderParser.parse(input);if(!parsed.isValid()){a.kit.toast("نام کالا/تعداد از گفتار تشخیص داده نشد؛ دوباره بگویید یا دستی انتخاب کنید.");return;}findProducts(parsed.productQuery,parsed.quantity);return;}
        lastAiStatus="در حال تبدیل گفتار/متن به پیشنهاد کالا…";render(contentRef);
        aiExecutor.execute(()->{
            try{
                String system="فقط یک JSON معتبر برگردان: {\"items\":[{\"product\":\"نام کالا\",\"quantity\":1,\"unit\":\"واحد\"}]}. هیچ قیمت یا شناسه‌ای نساز. اگر نام کالا مشخص نیست items را خالی کن.";
                AiRouter.Result r=AiRouter.complete(a,"fast",system,input);
                org.json.JSONObject root=new org.json.JSONObject(r.text);org.json.JSONArray items=root.optJSONArray("items");
                if(items==null||items.length()==0)throw new Exception("نام/تعداد کالا به شکل قابل‌تأیید تشخیص داده نشد.");
                if(items.length()!=1)throw new Exception("برای جلوگیری از حذف یا اشتباه در اقلام، هر بار فقط یک کالا را بگویید.");
                org.json.JSONObject first=items.optJSONObject(0);if(first==null)throw new Exception("پاسخ هوش مصنوعی ساختار معتبر نداشت.");
                String query=first.optString("product","").trim();double qty=first.optDouble("quantity",0);
                if(query.length()<2||!Double.isFinite(qty)||qty<=0||qty>10000)throw new Exception("پیشنهاد کالا یا مقدار معتبر نیست.");
                a.runOnUiThread(()->{lastAiStatus="پیشنهاد هوش مصنوعی: "+query+"، مقدار "+Money.qty(qty)+" — انتخاب کالا و تأیید نهایی لازم است.";render(contentRef);findProducts(query,qty);});
            }catch(Exception e){a.runOnUiThread(()->{lastAiStatus="هوش مصنوعی در دسترس نبود؛ ورودی را با روش گفتار ساده یا فهرست کالا امتحان کنید.";render(contentRef);a.kit.toast("تجزیهٔ سفارش انجام نشد: "+e.getMessage());});}
        });
    }

    private void findProducts(String query,double quantity){
        a.repo.run(db->loadCatalog(db,query),new Repo.Cb<List<Product>>(){
            @Override public void ok(List<Product> rows){
                List<Product> safe=new ArrayList<>();for(Product p:rows)if(VisitorPricePolicy.isUnambiguous(p.group,p.activePriceRows,p.price.doubleValue()))safe.add(p);
                if(safe.isEmpty()){a.kit.toast("کالای هم‌نام با نرخ یکتای آتیران پیدا نشد؛ قلمی افزوده نشد.");return;}
                String[] choices=new String[safe.size()];for(int i=0;i<safe.size();i++)choices[i]=safe.get(i).name+" • "+safe.get(i).unit+" • "+money(safe.get(i).price);
                new AlertDialog.Builder(a).setTitle("انتخاب دقیق کالا و نرخ").setItems(choices,(d,which)->{
                    Product p=safe.get(which);if(quantity>100000)return;
                    new AlertDialog.Builder(a).setTitle("تأیید افزودن به پیش‌نویس").setMessage(p.name+"\nتعداد: "+Money.qty(quantity)+" "+p.unit+"\nقیمت واحد آتیران: "+money(p.price)+"\nمبلغ: "+money(p.price.multiply(BigDecimal.valueOf(quantity)))+"\n\nاین اقدام فقط پیش‌نویس محلی را تغییر می‌دهد.").setNegativeButton("برگشت",null).setPositiveButton("افزودن",(x,y)->{cart.add(new CartLine(p,BigDecimal.valueOf(quantity).setScale(3,java.math.RoundingMode.HALF_UP)));persistDraft();render(contentRef);}).show();
                }).setNegativeButton("لغو",null).show();
            }
            @Override public void fail(String e){a.kit.toast("جستجوی کالا انجام نشد: "+e);}
        });
    }

    private void submitPrefactor(String note){
        if(selectedCustomer==null||cart.isEmpty()){a.kit.toast("مشتری و دست‌کم یک قلم لازم است");return;}
        List<VisitorPrefactorWriter.Line> lines=new ArrayList<>();
        for(CartLine l:cart)lines.add(new VisitorPrefactorWriter.Line(l.product.code,l.product.name,l.product.unit,l.qty,l.product.price,l.product.group,l.product.stock));
        a.repo.run(db->{VisitorIdentity.Identity id=identity(db);VisitorPrefactorWriter.ensureTables(db);return VisitorPrefactorWriter.submit(db,id,draftUuid,String.valueOf(selectedCustomer.code),selectedCustomer.name,lines,note);},new Repo.Cb<VisitorPrefactorWriter.Receipt>(){
            @Override public void ok(VisitorPrefactorWriter.Receipt r){a.kit.toast((r.duplicate?"همان پیش‌فاکتور قبلی":"پیش‌فاکتور ثبت شد")+" • شماره "+Money.fa(String.valueOf(r.id))+" • "+money(r.total));cart.clear();selectedCustomer=null;draftUuid=UUID.randomUUID().toString();persistDraft();page=MY_ORDERS;render(contentRef);}
            @Override public void fail(String e){a.kit.toast("پیش‌فاکتور ثبت نشد: "+e);}
        });
    }

    private void loadMyOrders(LinearLayout c){
        c.addView(a.kit.loading("در حال دریافت پیش‌فاکتورهای همین حساب…"),a.kit.lp(-1,-2));
        a.repo.run(db->{VisitorIdentity.Identity id=identity(db);VisitorPrefactorWriter.ensureTables(db);List<Row> rows=new ArrayList<>();
            try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT TOP (100) id,ISNULL(customer_name,N''),ISNULL(grand_total,0),status,CONVERT(nvarchar(19),created_at,120) FROM dbo.meelano_prefactors WHERE visitor_id=? ORDER BY id DESC")){
                ps.setString(1,String.valueOf(id.visitorId));try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next()){Row r=new Row();r.put("id",rs.getString(1));r.put("customer",rs.getString(2));r.put("total",rs.getString(3));r.put("status",rs.getString(4));r.put("created",rs.getString(5));rows.add(r);}}
            }return rows;
        },new Repo.Cb<List<Row>>(){@Override public void ok(List<Row> rows){c.removeAllViews();c.addView(a.kit.text("پیش‌فاکتورهای من",16,Theme.TEXT,true),a.kit.lp(-1,-2));for(Row r:rows)c.addView(a.kit.personRow("شماره "+r.s("id")+" • "+r.s("customer"),r.s("created"),Money.rial(r.d("total")),statusFa(r.s("status")),Theme.INFO,v->showOrderDetails(r)),a.kit.lp(-1,-2));if(rows.isEmpty())c.addView(a.kit.empty("هنوز پیش‌فاکتوری ثبت نشده است",null),a.kit.lp(-1,-2));}
            @Override public void fail(String e){showError(c,e,()->loadMyOrders(c));}});
    }

    private void showOrderDetails(Row row){
        a.repo.run(db->{VisitorIdentity.Identity id=identity(db);List<String> items=new ArrayList<>();try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT i.product_name,i.qty,i.unit,i.amount FROM dbo.meelano_prefactor_items i JOIN dbo.meelano_prefactors p ON p.id=i.prefactor_id WHERE p.id=? AND p.visitor_id=? ORDER BY i.id")){ps.setLong(1,Long.parseLong(row.s("id")));ps.setString(2,String.valueOf(id.visitorId));try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next())items.add(rs.getString(1)+" • "+qty(rs.getBigDecimal(2))+" "+rs.getString(3)+" • "+money(rs.getBigDecimal(4)));}}return items;},new Repo.Cb<List<String>>(){@Override public void ok(List<String> v){StringBuilder b=new StringBuilder("وضعیت: ").append(statusFa(row.s("status"))).append("\nجمع: ").append(Money.rial(row.d("total")));for(String x:v)b.append("\n\n").append(x);new AlertDialog.Builder(a).setTitle("پیش‌فاکتور "+row.s("id")).setMessage(b.toString()).setPositiveButton("بستن",null).show();}@Override public void fail(String e){a.kit.toast(e);}});
    }

    private void chooseCustomerForOrder(){customerPickerReturnPage=ORDER;loadCustomers(contentRef,true);}
    private void showCustomerPicker(List<Customer> rows){
        if(rows.isEmpty()){a.kit.toast("مشتری تخصیص‌یافته‌ای پیدا نشد");return;}
        String[] opts=new String[rows.size()];for(int i=0;i<rows.size();i++)opts[i]=rows.get(i).name+" • "+Money.fa(String.valueOf(rows.get(i).code));
        new AlertDialog.Builder(a).setTitle(customerPickerReturnPage==RECEIPT?"مشتری دریافت را انتخاب کنید":"مشتری پیش‌فاکتور را انتخاب کنید").setItems(opts,(d,i)->{if(customerPickerReturnPage==RECEIPT)receiptCustomer=rows.get(i);else selectedCustomer=rows.get(i);page=customerPickerReturnPage;persistDraft();render(contentRef);}).setNegativeButton("لغو",null).show();
    }

    private EditText persistedField(String hint,String value,java.util.function.Consumer<String> save){
        EditText edit=a.kit.edit(hint,value);edit.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){save.accept(s.toString());}public void afterTextChanged(android.text.Editable e){}});return edit;
    }

    private void renderReceipt(LinearLayout c){
        c.addView(a.kit.text("ثبت دریافت وجه / پیش‌دریافت",16,Theme.TEXT,true),a.kit.lp(-1,-2));
        c.addView(a.kit.hint("رسید در دفتر رسمی آتیران با Daryaft و شناسهٔ کاربر واقعی ویزیتور ثبت می‌شود؛ ارسال تکراری با شناسهٔ یکتا قبض دوم نمی‌سازد."),a.kit.lp(-1,-2));
        if(receiptCustomer==null)c.addView(a.kit.btnGold("انتخاب مشتری از مسیر من",v->{customerPickerReturnPage=RECEIPT;loadCustomers(contentRef,true);}),a.kit.lp(-1,-2));
        else c.addView(a.kit.personRow(receiptCustomer.name,"کد "+Money.fa(String.valueOf(receiptCustomer.code)),receiptCustomer.phone,"تغییر مشتری",Theme.INFO,v->{receiptCustomer=null;render(contentRef);}),a.kit.lp(-1,-2));
        EditText amount=a.kit.edit("مبلغ دریافت به ریال",receiptAmount);amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){receiptAmount=s.toString();}public void afterTextChanged(android.text.Editable e){}});c.addView(amount,a.kit.lp(-1,-2));
        c.addView(a.kit.chips(new String[]{"نقدی","کارت‌خوان","حواله","چک"},"cash".equals(receiptMethod)?0:"pos".equals(receiptMethod)?1:"transfer".equals(receiptMethod)?2:3,i->{receiptMethod=i==0?"cash":i==1?"pos":i==2?"transfer":"cheque";render(contentRef);}),a.kit.lp(-1,-2));
        if("pos".equals(receiptMethod)||"transfer".equals(receiptMethod))c.addView(a.kit.btnGhost(selectedBankName.isEmpty()?"انتخاب بانک آتیران":"بانک: "+selectedBankName,Theme.INFO,v->loadBanks()),a.kit.lp(-1,-2));
        if("cheque".equals(receiptMethod)){
            c.addView(persistedField("شمارهٔ چک",chequeNumber,v->chequeNumber=v),a.kit.lp(-1,-2));
            c.addView(persistedField("تاریخ سررسید شمسی (مثلاً ۱۴۰۵/۰۸/۰۶)",chequeDueDate,v->chequeDueDate=v),a.kit.lp(-1,-2));
            c.addView(persistedField("نام بانک صادرکننده",chequeBankName,v->chequeBankName=v),a.kit.lp(-1,-2));
            c.addView(persistedField("شعبه",chequeBranch,v->chequeBranch=v),a.kit.lp(-1,-2));
            c.addView(persistedField("شماره حساب",chequeAccount,v->chequeAccount=v),a.kit.lp(-1,-2));
            c.addView(persistedField("شناسه صیاد (در صورت وجود)",chequeSayad,v->chequeSayad=v),a.kit.lp(-1,-2));
            c.addView(a.kit.chips(new String[]{"استعلام نشده","استعلام شده"},chequeInquired?1:0,i->{chequeInquired=i==1;render(contentRef);}),a.kit.lp(-1,-2));
        }
        EditText note=a.kit.edit("شرح دریافت (اختیاری)",receiptNote);note.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){receiptNote=s.toString();}public void afterTextChanged(android.text.Editable e){}});c.addView(note,a.kit.lp(-1,-2));
        c.addView(a.kit.btnGold("ثبت قبض رسمی آتیران",v->{
            if(receiptCustomer==null){a.kit.toast("مشتری را انتخاب کنید");return;}
            BigDecimal val;try{val=new BigDecimal(Money.en(amount.getText().toString()).replace(",",""));}catch(Exception ex){a.kit.toast("مبلغ معتبر وارد کنید");return;}
            if(val.signum()<=0){a.kit.toast("مبلغ باید بزرگ‌تر از صفر باشد");return;}
            if(("pos".equals(receiptMethod)||"transfer".equals(receiptMethod))&&selectedBankId<=0){a.kit.toast("حساب بانکی را انتخاب کنید");return;}
            submitReceipt(receiptCustomer,val,note.getText().toString());
        }),a.kit.lp(-1,-2));
    }

    private void loadBanks(){
        a.repo.run(db->{Meta m=new Meta(db);if(!m.table("BANK"))throw new Exception("جدول بانک آتیران پیدا نشد.");String id=m.col("BANK","RDF","ID","bank_id");String name=m.col("BANK","Name","BankName","name");String active=m.col("BANK","Active","active");if(id==null||name==null)throw new Exception("شناسه/نام بانک پیدا نشد.");String sql="SELECT TRY_CONVERT(int,"+q(id)+"),TRY_CONVERT(nvarchar(150),"+q(name)+") FROM "+m.qTable("BANK")+(active==null?"":" WHERE TRY_CONVERT(nvarchar(10),"+q(active)+") IN (N't',N'T',N'1',N'true',N'TRUE')")+" ORDER BY "+q(name);List<String[]> out=new ArrayList<>();try(java.sql.PreparedStatement ps=db.prepareStatement(sql);java.sql.ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new String[]{String.valueOf(rs.getInt(1)),rs.getString(2)});}return out;},new Repo.Cb<List<String[]>>(){@Override public void ok(List<String[]> rows){String[] labels=new String[rows.size()];for(int i=0;i<rows.size();i++)labels[i]=rows.get(i)[1];new AlertDialog.Builder(a).setTitle("حساب بانکی آتیران").setItems(labels,(d,i)->{selectedBankId=Integer.parseInt(rows.get(i)[0]);selectedBankName=rows.get(i)[1];render(contentRef);}).setNegativeButton("لغو",null).show();}@Override public void fail(String e){a.kit.toast(e);}});
    }

    private void submitReceipt(Customer customer,BigDecimal amount,String note){
        VisitorReceiptWriter.Cheque cheque="cheque".equals(receiptMethod)?new VisitorReceiptWriter.Cheque(chequeNumber,Money.en(chequeDueDate).replace('-', '/'),chequeBankName,chequeBranch,chequeAccount,chequeSayad,chequeInquired,chequeNote):null;
        String fingerprint=customer.code+"|"+receiptMethod+"|"+selectedBankId+"|"+amount.stripTrailingZeros().toPlainString()+"|"+(note==null?"":note.trim())
                +(cheque==null?"":"|"+cheque.number+"|"+cheque.dueDate+"|"+cheque.bankName+"|"+cheque.branch+"|"+cheque.account+"|"+cheque.sayadId+"|"+cheque.inquired+"|"+cheque.note);
        if(!fingerprint.equals(receiptFingerprint)){receiptUuid=UUID.randomUUID().toString();receiptFingerprint=fingerprint;saveReceiptKey();}
        final String requestKey=receiptUuid;
        a.repo.run(db->{VisitorIdentity.Identity id=identity(db);return VisitorReceiptWriter.submit(a,db,id,customer.code,note,receiptMethod,selectedBankId,amount,cheque,requestKey);},new Repo.Cb<VisitorReceiptWriter.Result>(){
            @Override public void ok(VisitorReceiptWriter.Result r){new AlertDialog.Builder(a).setTitle("قبض ثبت شد").setMessage("شماره قبض: "+Money.fa(String.valueOf(r.receiptNo))+"\nمبلغ: "+money(r.amount)+(r.duplicate?"\nاین همان ارسال قبلی است.":"" )).setPositiveButton("پایان",(d,w)->{receiptUuid=UUID.randomUUID().toString();receiptFingerprint="";saveReceiptKey();receiptAmount="";receiptNote="پیش‌دریافت مشتری";receiptCustomer=null;selectedBankId=0;selectedBankName="";render(contentRef);}).show();}
            @Override public void fail(String e){a.kit.toast("ثبت قبض انجام نشد: "+e);}
        });
    }

    private VisitorIdentity.Identity identity(java.sql.Connection db)throws Exception{
        int uid=AtiranAuth.sessionUid(a);String user=AtiranAuth.sessionUser(a);
        return VisitorIdentity.resolve(db,uid,user);
    }

    private void saveReceiptKey(){a.getSharedPreferences("meelano_visitor_draft",0).edit().putString("receipt_uuid_"+AtiranAuth.sessionUid(a),receiptUuid).putString("receipt_fp_"+AtiranAuth.sessionUid(a),receiptFingerprint).apply();}

    private void persistDraft(){
        try{org.json.JSONObject root=new org.json.JSONObject();root.put("uuid",draftUuid);if(selectedCustomer!=null)root.put("customerCode",selectedCustomer.code).put("customerName",selectedCustomer.name).put("phone",selectedCustomer.phone).put("address",selectedCustomer.address);org.json.JSONArray items=new org.json.JSONArray();for(CartLine l:cart)items.put(new org.json.JSONObject().put("code",l.product.code).put("name",l.product.name).put("unit",l.product.unit).put("qty",l.qty.toPlainString()).put("price",l.product.price.toPlainString()).put("group",l.product.group).put("stock",l.product.stock.toPlainString()));root.put("items",items);a.getSharedPreferences("meelano_visitor_draft",0).edit().putString("draft_"+AtiranAuth.sessionUid(a),root.toString()).apply();}catch(Exception ignored){}
    }

    private void restoreDraft(){
        try{String raw=a.getSharedPreferences("meelano_visitor_draft",0).getString("draft_"+AtiranAuth.sessionUid(a),"");if(raw.isEmpty())return;org.json.JSONObject root=new org.json.JSONObject(raw);draftUuid=root.optString("uuid",draftUuid);if(root.has("customerCode"))selectedCustomer=new Customer(root.optLong("customerCode"),root.optString("customerName"),root.optString("phone"),root.optString("address"));org.json.JSONArray arr=root.optJSONArray("items");if(arr!=null)for(int i=0;i<arr.length();i++){org.json.JSONObject o=arr.optJSONObject(i);if(o!=null)cart.add(new CartLine(new Product(o.optString("code"),o.optString("name"),o.optString("unit"),new BigDecimal(o.optString("price","0")),new BigDecimal(o.optString("stock","0")),o.optInt("group"),1),new BigDecimal(o.optString("qty","1"))));}}catch(Exception ignored){}
    }

    private void clearDraft(){a.getSharedPreferences("meelano_visitor_draft",0).edit().remove("draft_"+AtiranAuth.sessionUid(a)).apply();}
    private void ensureVisitTable(java.sql.Connection db)throws Exception{try(java.sql.Statement st=db.createStatement()){st.execute("IF OBJECT_ID(N'dbo.meelano_visit_results',N'U') IS NULL CREATE TABLE dbo.meelano_visit_results(id bigint IDENTITY(1,1) PRIMARY KEY,visitor_username nvarchar(160) NULL,visitor_id nvarchar(80) NULL,customer_code nvarchar(100) NULL,customer_name nvarchar(250) NULL,result nvarchar(120) NULL,notes nvarchar(700) NULL,created_at datetime2 NOT NULL DEFAULT SYSDATETIME())");}}
    private static String q(String s){return "["+s.replace("]","]]")+ "]";}
    private static String money(BigDecimal value){return Money.rial(value==null?0:value.doubleValue());}
    private static String qty(BigDecimal value){return Money.qty(value==null?0:value.doubleValue());}
    private void showError(LinearLayout c,String message,Runnable retry){c.removeAllViews();c.addView(a.kit.error(message,retry),a.kit.lp(-1,-2));}
    private String statusFa(String s){if("pending_approval".equals(s))return "در انتظار بررسی";if("approved".equals(s))return "تأییدشده";if("rejected".equals(s))return "ردشده";if("invoiced".equals(s))return "فاکتور شده";if("needs_revision".equals(s))return "نیازمند اصلاح";return s==null||s.isEmpty()?"نامشخص":s;}

    { restoreDraft(); }
}
