package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.core.AiRouter;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.VisitorData;
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
    private static final int HOME = 0, CUSTOMERS = 1, PRODUCTS = 2, ROUTE = 3,
            ORDER = 4, MY_ORDERS = 5, RECEIPT = 6, REPORTS = 7;
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private int page = HOME;
    /**
     * Page the command room wants to open inside the visitor workspace
     * (for example «مشتریان من» from «همهٔ بخش‌ها»). Set before nav("visitor_work").
     */
    public static String pendingPage;
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
    private LinearLayout pageBodyRef;

    private static final class Customer {
        final long code; final String name; final String phone; final String address;
        final BigDecimal balance;
        Customer(long code, String name, String phone, String address) {
            this(code, name, phone, address, null);
        }
        Customer(long code, String name, String phone, String address, BigDecimal balance) {
            this.code=code; this.name=name==null?"":name; this.phone=phone==null?"":phone;
            this.address=address==null?"":address; this.balance=balance;
        }
    }

    /** The customers of the signed-in visitor plus the counts that explain an empty list. */
    private static final class CustomerList {
        final List<Customer> rows;
        /** Rows linked to this account, active or not. */
        final long linked;
        /** Rows linked to this account and switched on. */
        final long live;
        CustomerList(List<Customer> rows, long linked, long live) {
            this.rows=rows; this.linked=linked; this.live=live;
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

    @Override public boolean onBack(){
        if(page!=HOME){page=HOME;render(contentRef);return true;}
        return false;
    }

    /** The workspace page a command-room entry asks for. */
    static int pageFor(String key){
        if(key==null)return -1;
        if("home".equals(key))return HOME;
        if("customers".equals(key))return CUSTOMERS;
        if("products".equals(key))return PRODUCTS;
        if("route".equals(key)||"visit".equals(key))return ROUTE;
        if("order".equals(key)||"prefactor".equals(key))return ORDER;
        if("my_orders".equals(key)||"orders".equals(key))return MY_ORDERS;
        if("receipt".equals(key))return RECEIPT;
        if("reports".equals(key))return REPORTS;
        return -1;
    }

    @Override public void render(LinearLayout content) {
        contentRef=content;
        content.removeAllViews();
        a.scrollToTop();
        if(pendingPage!=null&&!pendingPage.isEmpty()){
            int wanted=pageFor(pendingPage);
            pendingPage=null;
            if(wanted>=0)page=wanted;
        }
        LinearLayout hero=a.kit.h();
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setBackground(Theme.hero(Theme.GOLD));
        hero.setPadding(Theme.dp(13),Theme.dp(9),Theme.dp(13),Theme.dp(9));
        ImageView heroArt=new ImageView(a);heroArt.setImageResource(Theme.chrome3d(R.drawable.mi_star_shine));
        heroArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.addView(heroArt,new LinearLayout.LayoutParams(Theme.dp(46),Theme.dp(46)));
        hero.addView(a.kit.space(9));
        LinearLayout heroCopy=a.kit.v();
        heroCopy.addView(a.kit.text("فروش میدانی",18,Theme.TEXT,true),a.kit.lp(-1,-2));
        heroCopy.addView(a.kit.text("کارهای روزانهٔ ویزیت و سفارش",11.5f,Theme.MUTED,false),a.kit.lp(-1,-2));
        hero.addView(heroCopy,a.kit.wlp(1f));
        content.addView(hero,a.kit.lp(-1,-2));
        content.addView(a.kit.gap(7));
        String[] labels={"خانه","مشتریان","کالاها","ویزیت"};
        // Section art follows the chosen theme: the nut look uses the emerald set, the dark
        // look its own, the ivory look the gold set - one family, never a fixed picture.
        int[] icons={Theme.chrome3d(R.drawable.mi_home),Theme.chrome3d(R.drawable.mi_group),
                Theme.chrome3d(R.drawable.mi_grid_view),Theme.chrome3d(R.drawable.mi_task_alt)};
        int selected=page<=ROUTE?page:-1;
        content.addView(a.kit.chipsWithIcons(labels,selected,icons,Theme.GOLD,i->{page=i;render(content);}),a.kit.lp(-1,-2));
        content.addView(a.kit.gap(7));
        if(page>ROUTE){
            content.addView(a.kit.btnGhost("‹ بازگشت به میزکار",Theme.GOLD,v->{page=HOME;render(contentRef);}),a.kit.lp(-1,-2));
            content.addView(a.kit.gap(4));
        }
        pageBodyRef=a.kit.v();
        content.addView(pageBodyRef,a.kit.lp(-1,-2));
        if(page==HOME) renderHome(pageBodyRef);
        else if(page==CUSTOMERS||page==ROUTE) loadCustomers(pageBodyRef,false);
        else if(page==PRODUCTS) renderProducts(pageBodyRef,"");
        else if(page==ORDER) renderOrder(pageBodyRef);
        else if(page==MY_ORDERS) loadMyOrders(pageBodyRef);
        else if(page==RECEIPT) renderReceipt(pageBodyRef);
        else loadReports(pageBodyRef);
    }

    private void renderHome(LinearLayout c) {
        c.addView(sectionTitle("شروع سریع", "کارهای پرکاربرد امروز"),a.kit.lp(-1,-2));
        addHomeRow(c,
                homeTile("مشتریان", "مانده و تماس", R.drawable.mi_group, CUSTOMERS),
                homeTile("کالا و موجودی", "جستجوی کالا و نرخ", R.drawable.mi_grid_view, PRODUCTS));
        addHomeRow(c,
                homeTile("ثبت ویزیت", "نتیجه و یادداشت", R.drawable.mi_task_alt, ROUTE),
                homeTile("پیش‌فاکتور هوشمند", "کالا با صدا یا متن", R.drawable.mi_add_shopping_cart, ORDER));
        addHomeRow(c,
                homeTile("سفارش‌های من", "پیگیری وضعیت", R.drawable.mi_point_of_sale, MY_ORDERS),
                homeTile("دریافت وجه", "پیش‌دریافت و رسید", R.drawable.mi_receipt_long, RECEIPT));
        LinearLayout reports=homeTile("گزارش روزانه", "بازدیدها و عملکرد خودتان", R.drawable.mi_bar_chart, REPORTS);
        c.addView(reports,a.kit.lp(-1,-2));
        c.addView(a.kit.gap(8));
        c.addView(a.kit.hint("مشتریان و گزارش‌ها فقط برای حساب ویزیتور واردشده نمایش داده می‌شوند. نرخ کالا بر اساس تنظیم آتیران است؛ قلمِ بدون نرخ یکتا قابل ثبت نیست."),a.kit.lp(-1,-2));
    }

    private LinearLayout sectionTitle(String title,String subtitle){
        LinearLayout head=a.kit.v();
        head.addView(a.kit.text(title,16,Theme.TEXT,true),a.kit.lp(-1,-2));
        head.addView(a.kit.text(subtitle,11.5f,Theme.MUTED,false),a.kit.lp(-1,-2));
        return head;
    }

    private LinearLayout homeTile(String title,String subtitle,int icon,int target){
        LinearLayout tile=a.kit.v();
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(Theme.cardAccent(Theme.GOLD));
        tile.setPadding(Theme.dp(8),Theme.dp(7),Theme.dp(8),Theme.dp(7));
        tile.setMinimumHeight(Theme.dp(108));
        ImageView art=new ImageView(a);
        // The artwork of a section follows the skin the user chose: emerald in the nut look,
        // its own set in the dark look, gold in the ivory look. A flat glyph is the last resort.
        int artRes=Theme.chrome3d(icon);
        art.setImageResource(artRes);
        if(!Theme.isIcon3d(artRes))art.setColorFilter(Theme.GOLD);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(Theme.dp(48),Theme.dp(48));
        ip.gravity=Gravity.CENTER;
        tile.addView(art,ip);
        TextView name=a.kit.text(title,13.5f,Theme.TEXT,true);name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(name,a.kit.lp(-1,-2));
        TextView sub=a.kit.text(subtitle,10.5f,Theme.MUTED,false);sub.setGravity(Gravity.CENTER);
        sub.setMaxLines(1);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(sub,a.kit.lp(-1,-2));
        tile.setContentDescription(title+"، "+subtitle);
        Theme.pressable(tile);
        tile.setOnClickListener(v->{page=target;render(contentRef);});
        return tile;
    }

    private void addHomeRow(LinearLayout parent,View first,View second){
        LinearLayout row=a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(first,new LinearLayout.LayoutParams(0,-2,1f));
        row.addView(a.kit.space(8));
        row.addView(second,new LinearLayout.LayoutParams(0,-2,1f));
        parent.addView(row,a.kit.lp(-1,-2));
        parent.addView(a.kit.gap(8));
    }

    private View visitorErrorCard(String title,String error,Runnable retry){
        LinearLayout card=a.kit.card(Theme.DANGER);
        LinearLayout head=a.kit.h();head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView art=new ImageView(a);art.setImageResource(R.drawable.icon3d_dark_plug);art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        head.addView(art,new LinearLayout.LayoutParams(Theme.dp(48),Theme.dp(48)));
        head.addView(a.kit.space(9));
        LinearLayout copy=a.kit.v();
        copy.addView(a.kit.text(title,14.5f,Theme.TEXT,true),a.kit.lp(-1,-2));
        String detail=friendlyError(error);
        TextView body=a.kit.text(detail,11.5f,Theme.MUTED,false);body.setMaxLines(3);body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(body,a.kit.lp(-1,-2));head.addView(copy,a.kit.wlp(1f));
        card.addView(head,a.kit.lp(-1,-2));
        LinearLayout actions=a.kit.h();actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(a.kit.btnGold("تلاش دوباره",v->{if(retry!=null)retry.run();}),new LinearLayout.LayoutParams(0,-2,1f));
        actions.addView(a.kit.space(8));
        actions.addView(a.kit.btnGhost("تنظیم اتصال",Theme.GOLD,v->a.openConnectionSetup()),new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout.LayoutParams ap=a.kit.lp(-1,-2);ap.topMargin=Theme.dp(8);card.addView(actions,ap);
        return card;
    }

    private String friendlyError(String error){
        if(error==null||error.trim().isEmpty())return "بررسی شبکه و تنظیم اتصال آتیران را انجام دهید.";
        String s=error.trim();String n=s.toLowerCase(java.util.Locale.ROOT);
        if(n.contains("network")||n.contains("socket")||n.contains("timeout")||n.contains("خارج از شبکه")||n.contains("ارتباط با سرور"))
            return "ارتباط با سرور برقرار نشد. شبکه و تنظیم اتصال آتیران را بررسی کنید.";
        return s;
    }

    private void loadCustomers(LinearLayout c, boolean forPicker) {
        if(!forPicker){c.removeAllViews();c.addView(a.kit.loading("در حال دریافت مشتریان مسیر شما…"),a.kit.lp(-1,-2));}
        a.repo.run(db->{
            VisitorData.beginRead(db);
            VisitorIdentity.Identity identity=identity(db);
            Meta m=new Meta(db);
            if(!m.table("CUSTOMERS")) throw new Exception("جدول مشتریان آتیران پیدا نشد.");
            String key=m.col("CUSTOMERS","SHMO","shmo");
            String name=m.col("CUSTOMERS","MONAME","name","CusName");
            String vis=m.col("CUSTOMERS","vis_rdf","VisitorID","visitor_id");
            String active=m.col("CUSTOMERS","active","Active");
            String phone=m.col("CUSTOMERS","cell","phone","tell","tel");
            String address=m.col("CUSTOMERS","address","addre","Address");
            String balance=m.col("CUSTOMERS","man","Balance","Mandeh");
            if(key==null||name==null||vis==null||active==null) throw new Exception("شناسهٔ مشتری، نام، پیوند ویزیتور یا وضعیت فعال‌بودن در جدول مشتریان موجود نیست.");
            // Owner scope: every visitor the signed-in account owns (sys_vis grant + login name).
            // The column is compared the way Atiran stores it, so the database can use an index
            // instead of reading the whole customer table over a slow link.
            VisitorData.Owner owner=VisitorData.owner("c",vis,m.columnType("CUSTOMERS",vis),VisitorData.ids(identity));
            String sql="SELECT TOP (300) TRY_CONVERT(bigint,c."+q(key)+"),TRY_CONVERT(nvarchar(250),c."+q(name)+")"
                    +(phone==null?",CAST(N'' AS nvarchar(80))":",TRY_CONVERT(nvarchar(80),c."+q(phone)+")")
                    +(address==null?",CAST(N'' AS nvarchar(300))":",TRY_CONVERT(nvarchar(300),c."+q(address)+")")
                    +(balance==null?",CAST(NULL AS decimal(19,2))":",TRY_CONVERT(decimal(19,2),c."+q(balance)+")")
                    +" FROM "+m.qTable("CUSTOMERS")+" c WHERE "+owner.predicate
                    +" AND "+VisitorData.activePredicate("c",active)+" ORDER BY c."+q(name);
            List<Customer> out=new ArrayList<>();
            try(java.sql.PreparedStatement ps=VisitorData.read(db,sql)){
                owner.bind(ps,1);
                try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new Customer(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5)));}
            }
            // Why is the list empty? Count the rows of this account with and without the active marker.
            String countSql="SELECT COUNT_BIG(1),SUM(CASE WHEN "+VisitorData.activePredicate("c",active)
                    +" THEN 1 ELSE 0 END) FROM "+m.qTable("CUSTOMERS")+" c WHERE "+owner.predicate;
            long linked=0,live=0;
            try(java.sql.PreparedStatement ps=VisitorData.read(db,countSql)){
                owner.bind(ps,1);
                try(java.sql.ResultSet rs=ps.executeQuery()){
                    if(rs.next()){linked=rs.getLong(1);live=rs.getLong(2);if(rs.wasNull())live=0;}
                }
            }
            return new CustomerList(out,linked,live);
        },new Repo.Cb<CustomerList>(){
            @Override public void ok(CustomerList list){
                if(forPicker){showCustomerPicker(list.rows);return;}
                List<Customer> rows=list.rows;
                c.removeAllViews();
                if(page==ROUTE){
                    c.addView(sectionTitle("ثبت ویزیت","مشتری را انتخاب کنید و نتیجه را یادداشت کنید."),a.kit.lp(-1,-2));
                    for(Customer x:rows){
                        View v=a.kit.personRow(x.name,"کد "+Money.fa(String.valueOf(x.code))+ (x.phone.isEmpty()?"":" • "+x.phone),"", "ثبت",Theme.GOLD,z->recordVisit(x));
                        c.addView(v,a.kit.lp(-1,-2));c.addView(a.kit.gap(6));
                    }
                    if(rows.isEmpty())c.addView(a.kit.empty("مشتری تخصیص‌یافته پیدا نشد",emptyReason(list)),a.kit.lp(-1,-2));
                } else if(page==CUSTOMERS){
                    c.addView(sectionTitle("مشتریان و حساب","مشتریان تخصیص‌یافته و ماندهٔ ثبت‌شده در آتیران."),a.kit.lp(-1,-2));
                    for(Customer x:rows){
                        int tint=x.balance==null?Theme.INFO:(x.balance.doubleValue()>0?Theme.DANGER:(x.balance.doubleValue()<0?Theme.SUCCESS:Theme.GOLD));
                        View v=a.kit.personRow(x.name,"کد "+Money.fa(String.valueOf(x.code))+(x.phone.isEmpty()?"":" • "+x.phone),
                                x.balance==null?"مانده نامشخص":money(x.balance),"پرونده",tint,z->showCustomerDetail(x));
                        c.addView(v,a.kit.lp(-1,-2));c.addView(a.kit.gap(6));
                    }
                    if(rows.isEmpty())c.addView(a.kit.empty("مشتری تخصیص‌یافته پیدا نشد",emptyReason(list)),a.kit.lp(-1,-2));
                } else showCustomerPicker(rows);
            }
            @Override public void fail(String error){
                if(forPicker){a.kit.toast("فهرست مشتریان در دسترس نیست؛ اتصال آتیران را بررسی کنید.");return;}
                c.removeAllViews();c.addView(visitorErrorCard("مشتریان دریافت نشدند",error,()->loadCustomers(c,false)),a.kit.lp(-1,-2));
            }
        });
    }

    /** Explains an empty customer list with the numbers Atiran actually holds. */
    private String emptyReason(CustomerList list){
        if(list==null)return null;
        if(list.linked>0&&list.live<=0)
            return "همهٔ "+Money.fa(String.valueOf(list.linked))+" مشتری تخصیص‌یافته در آتیران غیرفعال‌اند؛ مدیر آتیران باید آن‌ها را فعال کند.";
        if(list.linked<=0)
            return "هیچ مشتری‌ای به این حساب ویزیتور تخصیص نیافته است؛ تخصیص مشتری باید در آتیران انجام شود.";
        return null;
    }

    /** The visitor ids of the signed-in account, as text for the app's own tables. */
    private static List<String> ownerIds(VisitorIdentity.Identity identity){
        List<String> out=new ArrayList<>();
        for(int v:VisitorData.ids(identity))out.add(String.valueOf(v));
        return out;
    }

    /** The placeholders of an IN clause. */
    private static String placeholders(int n){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<n;i++){if(i>0)b.append(',');b.append('?');}
        return b.toString();
    }

    /** Binds the owner ids of the signed-in account into an IN clause. */
    private static void bindOwner(java.sql.PreparedStatement ps,List<String> ids,int first)throws Exception{
        for(int i=0;i<ids.size();i++)ps.setString(first+i,ids.get(i));
    }

    private void showCustomerDetail(Customer customer){
        String balance=customer.balance==null?"مانده حساب: در دادهٔ آتیران موجود نیست":
                (customer.balance.signum()>0?"مانده بدهی: ":customer.balance.signum()<0?"مانده بستانکاری: ":"مانده حساب: ")+money(customer.balance);
        String message="کد مشتری: "+Money.fa(String.valueOf(customer.code))
                +(customer.phone.isEmpty()?"":"\nتماس: "+customer.phone)
                +(customer.address.isEmpty()?"":"\nنشانی: "+customer.address)
                +"\n"+balance;
        new AlertDialog.Builder(a).setTitle(customer.name).setMessage(message)
                .setPositiveButton("پیش‌فاکتور",(d,w)->{selectedCustomer=customer;page=ORDER;persistDraft();render(contentRef);})
                .setNeutralButton("ثبت ویزیت",(d,w)->recordVisit(customer))
                .setNegativeButton("دریافت وجه",(d,w)->{receiptCustomer=customer;page=RECEIPT;render(contentRef);})
                .show();
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
                    ps.setString(1,id.username);ps.setString(2,String.valueOf(id.visitorId));ps.setString(3,String.valueOf(customer.code));ps.setString(4,customer.name);ps.setString(5,r);ps.setString(6,n);ps.executeUpdate();
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
                try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT COUNT_BIG(1),ISNULL(SUM(ISNULL(grand_total,total_amount)),0) FROM dbo.meelano_prefactors WHERE CONVERT(date,created_at)=CONVERT(date,SYSDATETIME()) AND visitor_id IN ("+placeholders(ownerIds(id).size())+")")){bindOwner(ps,ownerIds(id),1);try(java.sql.ResultSet rs=ps.executeQuery()){if(rs.next()){count=rs.getLong(1);amount=rs.getDouble(2);}}}
                try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT COUNT_BIG(1) FROM dbo.meelano_visit_results WHERE CONVERT(date,created_at)=CONVERT(date,SYSDATETIME()) AND visitor_id IN ("+placeholders(ownerIds(id).size())+")")){bindOwner(ps,ownerIds(id),1);try(java.sql.ResultSet rs=ps.executeQuery()){if(rs.next())visits=rs.getLong(1);}}
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
            c.addView(a.kit.btnGold("انتخاب مشتری",v->{customerPickerReturnPage=ORDER;loadCustomers(pageBodyRef,true);}),a.kit.lp(-1,-2));
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

    private void renderProducts(LinearLayout c,String initialQuery){
        c.addView(sectionTitle("کالا و موجودی","فهرست فروش میدانی با نرخ معتبر و موجودی آتیران."),a.kit.lp(-1,-2));
        EditText search=a.kit.edit("نام، کد یا بارکد کالا",initialQuery);
        LinearLayout actions=a.kit.h();
        actions.addView(a.kit.btnGold("جستجو",v->{String term=search.getText().toString().trim();if(term.length()<2){a.kit.toast("نام یا دست‌کم دو رقم از کد کالا را وارد کنید.");return;}loadProductList(c,term);}),new LinearLayout.LayoutParams(0,-2,1f));
        actions.addView(a.kit.space(8));
        actions.addView(a.kit.btnGhost("اسکن بارکد",Theme.INFO,v->a.startScan(code->{search.setText(code);loadProductList(c,code); })),new LinearLayout.LayoutParams(0,-2,1f));
        c.addView(search,a.kit.lp(-1,-2));
        c.addView(actions,a.kit.lp(-1,-2));
        c.addView(a.kit.gap(8));
        // The shelf loads on its own: an empty list must never look like a broken section.
        loadProductList(c,initialQuery);
    }

    private void loadProductList(LinearLayout c,String query){
        final String term=query==null?"":query.trim();
        c.removeViews(3,Math.max(0,c.getChildCount()-3));
        c.addView(a.kit.loading(term.isEmpty()?"در حال خواندن کالاها و موجودی…":"در حال جستجوی کالا…"),a.kit.lp(-1,-2));
        a.repo.run(db->loadCatalog(db,term),new Repo.Cb<List<Product>>(){
            @Override public void ok(List<Product> rows){
                c.removeViews(3,Math.max(0,c.getChildCount()-3));
                if(rows.isEmpty()){
                    String title=term.isEmpty()?"فهرست کالای قابل‌نمایش خالی است":"کالایی با این عبارت پیدا نشد";
                    c.addView(a.kit.empty(title,term.isEmpty()?"فعال‌بودن کالاهای فروش میدانی در آتیران را بررسی کنید.":"نام یا کد دیگری را جستجو کنید یا بارکد را اسکن کنید."),a.kit.lp(-1,-2));
                    if(!term.isEmpty())c.addView(a.kit.btnGhost("نمایش همهٔ کالاها",Theme.GOLD,v->loadProductList(c,"")),a.kit.lp(-1,-2));
                    return;
                }
                c.addView(a.kit.hint("قیمت‌ها از گروه پیش‌فرض آتیران خوانده شده‌اند؛ نرخ مبهم نمایش داده نمی‌شود."),a.kit.lp(-1,-2));
                if(rows.size()>=250)c.addView(a.kit.hint("۲۵۰ قلم نخست نمایش داده شده؛ برای یافتن کالای بیشتر جستجو کنید."),a.kit.lp(-1,-2));
                for(Product p:rows){
                    boolean safe=VisitorPricePolicy.isUnambiguous(p.group,p.activePriceRows,p.price.doubleValue());
                    String detail="کد "+p.code+" • "+p.unit+" • "+(p.stock==null?"موجودی نامشخص":"موجودی "+qty(p.stock));
                    c.addView(a.kit.personRow(p.name,detail,safe?money(p.price):"—",safe?"افزودن":"نرخ نامشخص",
                            safe?Theme.SUCCESS:Theme.DANGER,v->{if(safe){cart.add(new CartLine(p,BigDecimal.ONE));persistDraft();page=ORDER;render(contentRef);}}),a.kit.lp(-1,-2));
                    c.addView(a.kit.gap(6));
                }
            }
            @Override public void fail(String error){
                c.removeViews(3,Math.max(0,c.getChildCount()-3));
                c.addView(visitorErrorCard("فهرست کالا دریافت نشد",error,()->loadProductList(c,term)),a.kit.lp(-1,-2));
            }
        });
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
        VisitorData.beginRead(db);
        VisitorIdentity.Identity id=identity(db);Meta m=new Meta(db);
        String invName=m.col("inventory","naka","Name","ProductName");
        String invBarcode=m.col("inventory","coka","Barcode","bar_code","ProductCode");
        String invId=m.col("inventory","shka","SHKA","ProductID");
        String unit=m.col("inventory","vahsanj","unit","UnitName");
        String stock=m.col("inventory","mojkavah","stock","Stock");
        String active=m.col("inventory","active","Active");
        if(invId==null||invName==null||active==null)throw new Exception("شناسه/نام کالا یا وضعیت فعال‌بودن کالا در inventory آتیران پیدا نشد.");
        int group=VisitorData.defaultPriceGroup(db,VisitorData.ids(id));
        String priceCol=VisitorPricePolicy.column(group);
        if(priceCol==null)throw new Exception("DefaultPriceGrp ویزیتور معتبر نیست؛ قیمت انتخاب نشد.");
        if(!m.table("forosh_price"))throw new Exception("جدول forosh_price آتیران پیدا نشد.");
        String pKey=m.col("forosh_price","shka","SHKA","ProductID","KalaID");
        String pVal=m.col("forosh_price",priceCol);
        String pActive=m.col("forosh_price","active","Active");
        if(pKey==null||pVal==null||pActive==null)throw new Exception("ستون نرخ یا وضعیت فعال‌بودن آن در forosh_price پیدا نشد.");
        // The price row of a product is found by its key. When both keys are numbers the
        // comparison stays index-friendly, so a big price table is not read row by row.
        String keyMatch=VisitorData.idEquals("p."+q(pKey),m.columnType("forosh_price",pKey),
                "i."+q(invId),m.columnType("inventory",invId));
        String activeP=" AND "+VisitorData.activePredicate("p",pActive);
        // A product may be switched on for field (capillary) sales, for online sales, or both.
        String[] flags=VisitorData.fieldSalesColumns(m);
        List<Object> binds=new ArrayList<>();String where="1=1";
        where+=" AND "+VisitorData.activePredicate("i",active);
        if(flags.length>0)where+=" AND "+VisitorData.fieldSalesPredicate("i",flags);
        if(search!=null&&!search.trim().isEmpty()){where+=" AND ("+VisitorData.textExpr("i",invName,m.columnType("inventory",invName))+" LIKE N'%'+?+N'%'";binds.add(search.trim());if(invBarcode!=null){where+=" OR TRY_CONVERT(nvarchar(100),i."+q(invBarcode)+") LIKE N'%'+?+N'%'";binds.add(search.trim());}where+=")";}
        String sql="SELECT TOP (250) TRY_CONVERT(nvarchar(100),i."+q(invId)+"),TRY_CONVERT(nvarchar(500),i."+q(invName)+")"
                +(unit==null?",CAST(N'عدد' AS nvarchar(80))":",TRY_CONVERT(nvarchar(80),i."+q(unit)+")")
                +",ISNULL(TRY_CONVERT(decimal(19,2),px.price),0),"+(stock==null?"CAST(NULL AS decimal(19,3))":"TRY_CONVERT(decimal(19,3),i."+q(stock)+")")+",ISNULL(px.price_rows,0)"
                +" FROM "+m.qTable("inventory")+" i OUTER APPLY (SELECT CASE WHEN COUNT_BIG(1)=1 THEN MAX(TRY_CONVERT(decimal(19,2),p."+q(pVal)+")) ELSE 0 END price,COUNT_BIG(1) price_rows FROM "+m.qTable("forosh_price")+" p WHERE "+keyMatch+activeP+") px WHERE "+where+" ORDER BY "+VisitorData.orderText("i",invName,m.columnType("inventory",invName));
        List<Product> out=new ArrayList<>();
        try(java.sql.PreparedStatement ps=VisitorData.read(db,sql)){int n=1;for(Object b:binds)ps.setObject(n++,b);try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next()){
            BigDecimal p=rs.getBigDecimal(4), st=rs.getBigDecimal(5);long rows=rs.getLong(6);
            out.add(new Product(rs.getString(1),rs.getString(2),rs.getString(3),p==null?BigDecimal.ZERO:p,st,group,rows));
        }}}
        return out;
    }

    private void showProductPicker(List<Product> rows){
        LinearLayout list=a.kit.v();
        for(Product p:rows){
            boolean safe=VisitorPricePolicy.isUnambiguous(p.group,p.activePriceRows,p.price.doubleValue());
            String detail="کد "+p.code+" • "+p.unit+" • "+(p.stock==null?"موجودی نامشخص":"موجودی "+qty(p.stock))+(safe?" • "+money(p.price):" • نرخ امن موجود نیست");
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

    private void loadReports(LinearLayout c){
        c.removeAllViews();
        c.addView(sectionTitle("گزارش‌های من","فقط بازدیدها و گزارش‌های ثبت‌شده با همین حساب."),a.kit.lp(-1,-2));
        c.addView(a.kit.btnGold("ثبت گزارش پایان روز",v->composeDailyReport()),a.kit.lp(-1,-2));
        c.addView(a.kit.gap(8));
        c.addView(a.kit.text("گزارش‌های روزانه",14,Theme.TEXT,true),a.kit.lp(-1,-2));
        LinearLayout daily=a.kit.v();c.addView(daily,a.kit.lp(-1,-2));
        daily.addView(a.kit.loading("در حال دریافت گزارش‌ها…"),a.kit.lp(-1,-2));
        c.addView(a.kit.gap(8));
        c.addView(a.kit.text("نتیجه‌های ویزیت",14,Theme.TEXT,true),a.kit.lp(-1,-2));
        LinearLayout visits=a.kit.v();c.addView(visits,a.kit.lp(-1,-2));
        visits.addView(a.kit.loading("در حال دریافت بازدیدها…"),a.kit.lp(-1,-2));
        a.repo.run(db->{
            VisitorIdentity.Identity id=identity(db);
            try(java.sql.Statement st=db.createStatement()){st.execute("IF OBJECT_ID(N'dbo.meelano_day_reports',N'U') IS NULL CREATE TABLE dbo.meelano_day_reports(id bigint IDENTITY(1,1) PRIMARY KEY,visitor_username nvarchar(160) NULL,visitor_id nvarchar(80) NULL,report_text nvarchar(max) NULL,created_at datetime2 NOT NULL DEFAULT SYSDATETIME())");}
            List<String[]> out=new ArrayList<>();
            try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT TOP (50) id,ISNULL(report_text,N''),CONVERT(nvarchar(19),created_at,120) FROM dbo.meelano_day_reports WHERE visitor_id IN ("+placeholders(ownerIds(id).size())+") ORDER BY id DESC")){
                bindOwner(ps,ownerIds(id),1);try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new String[]{rs.getString(1),rs.getString(2),rs.getString(3)});}
            }return out;
        },new Repo.Cb<List<String[]>>(){
            @Override public void ok(List<String[]> rows){daily.removeAllViews();if(rows.isEmpty()){daily.addView(a.kit.empty("گزارشی ثبت نشده است","برای ساخت گزارش، دکمهٔ بالا را بزنید."),a.kit.lp(-1,-2));return;}
                for(String[] r:rows)daily.addView(a.kit.personRow("گزارش روزانهٔ "+r[0],r[2],"","نمایش",Theme.INFO,v->new AlertDialog.Builder(a).setTitle("گزارش روزانهٔ "+r[0]).setMessage(r[1]).setPositiveButton("بستن",null).show()),a.kit.lp(-1,-2));}
            @Override public void fail(String e){daily.removeAllViews();daily.addView(visitorErrorCard("گزارش روزانه دریافت نشد",e,()->loadReports(c)),a.kit.lp(-1,-2));}
        });
        a.repo.run(db->{VisitorData.beginRead(db);VisitorIdentity.Identity id=identity(db);ensureVisitTable(db);List<String[]> out=new ArrayList<>();
            try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT TOP (100) customer_name,result,ISNULL(notes,N''),CONVERT(nvarchar(19),created_at,120) FROM dbo.meelano_visit_results WHERE visitor_id IN ("+placeholders(ownerIds(id).size())+") ORDER BY id DESC")){
                bindOwner(ps,ownerIds(id),1);try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new String[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)});}
            }return out;
        },new Repo.Cb<List<String[]>>(){
            @Override public void ok(List<String[]> rows){visits.removeAllViews();if(rows.isEmpty()){visits.addView(a.kit.empty("هنوز ویزیتی ثبت نشده است",null),a.kit.lp(-1,-2));return;}
                for(String[] r:rows){String detail=r[3]+(r[2].isEmpty()?"":" • "+r[2]);visits.addView(a.kit.personRow(r[0],detail,"",r[1],Theme.GOLD,v->new AlertDialog.Builder(a).setTitle(r[0]).setMessage("نتیجه: "+r[1]+(r[2].isEmpty()?"":"\nتوضیحات: "+r[2])+"\nزمان: "+r[3]).setPositiveButton("بستن",null).show()),a.kit.lp(-1,-2));}}
            @Override public void fail(String e){visits.removeAllViews();visits.addView(visitorErrorCard("بازدیدها دریافت نشدند",e,()->loadReports(c)),a.kit.lp(-1,-2));}
        });
    }

    private void loadMyOrders(LinearLayout c){
        c.removeAllViews();
        c.addView(a.kit.loading("در حال دریافت پیش‌فاکتورهای همین حساب…"),a.kit.lp(-1,-2));
        a.repo.run(db->{VisitorData.beginRead(db);VisitorIdentity.Identity id=identity(db);VisitorPrefactorWriter.ensureTables(db);List<Row> rows=new ArrayList<>();
            try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT TOP (100) id,ISNULL(customer_name,N''),ISNULL(grand_total,0),status,CONVERT(nvarchar(19),created_at,120) FROM dbo.meelano_prefactors WHERE visitor_id IN ("+placeholders(ownerIds(id).size())+") ORDER BY id DESC")){
                bindOwner(ps,ownerIds(id),1);try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next()){Row r=new Row();r.put("id",rs.getString(1));r.put("customer",rs.getString(2));r.put("total",rs.getString(3));r.put("status",rs.getString(4));r.put("created",rs.getString(5));rows.add(r);}}
            }return rows;
        },new Repo.Cb<List<Row>>(){@Override public void ok(List<Row> rows){c.removeAllViews();c.addView(a.kit.text("پیش‌فاکتورهای من",16,Theme.TEXT,true),a.kit.lp(-1,-2));for(Row r:rows)c.addView(a.kit.personRow("شماره "+r.s("id")+" • "+r.s("customer"),r.s("created"),Money.rial(r.d("total")),statusFa(r.s("status")),Theme.INFO,v->showOrderDetails(r)),a.kit.lp(-1,-2));if(rows.isEmpty())c.addView(a.kit.empty("هنوز پیش‌فاکتوری ثبت نشده است",null),a.kit.lp(-1,-2));}
            @Override public void fail(String e){c.removeAllViews();c.addView(visitorErrorCard("سفارش‌ها دریافت نشدند",e,()->loadMyOrders(c)),a.kit.lp(-1,-2));}});
    }

    private void showOrderDetails(Row row){
        a.repo.run(db->{VisitorIdentity.Identity id=identity(db);List<String> items=new ArrayList<>();try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT i.product_name,i.qty,i.unit,i.amount FROM dbo.meelano_prefactor_items i JOIN dbo.meelano_prefactors p ON p.id=i.prefactor_id WHERE p.id=? AND p.visitor_id IN ("+placeholders(ownerIds(id).size())+") ORDER BY i.id")){ps.setLong(1,Long.parseLong(row.s("id")));bindOwner(ps,ownerIds(id),2);try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next())items.add(rs.getString(1)+" • "+qty(rs.getBigDecimal(2))+" "+rs.getString(3)+" • "+money(rs.getBigDecimal(4)));}}return items;},new Repo.Cb<List<String>>(){@Override public void ok(List<String> v){StringBuilder b=new StringBuilder("وضعیت: ").append(statusFa(row.s("status"))).append("\nجمع: ").append(Money.rial(row.d("total")));for(String x:v)b.append("\n\n").append(x);new AlertDialog.Builder(a).setTitle("پیش‌فاکتور "+row.s("id")).setMessage(b.toString()).setPositiveButton("بستن",null).show();}@Override public void fail(String e){a.kit.toast(e);}});
    }

    private void chooseCustomerForOrder(){customerPickerReturnPage=ORDER;loadCustomers(pageBodyRef,true);}
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
        if(receiptCustomer==null)c.addView(a.kit.btnGold("انتخاب مشتری از مسیر من",v->{customerPickerReturnPage=RECEIPT;loadCustomers(pageBodyRef,true);}),a.kit.lp(-1,-2));
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
        try{org.json.JSONObject root=new org.json.JSONObject();root.put("uuid",draftUuid);if(selectedCustomer!=null)root.put("customerCode",selectedCustomer.code).put("customerName",selectedCustomer.name).put("phone",selectedCustomer.phone).put("address",selectedCustomer.address);org.json.JSONArray items=new org.json.JSONArray();for(CartLine l:cart)items.put(new org.json.JSONObject().put("code",l.product.code).put("name",l.product.name).put("unit",l.product.unit).put("qty",l.qty.toPlainString()).put("price",l.product.price.toPlainString()).put("group",l.product.group).put("stock",l.product.stock==null?"0":l.product.stock.toPlainString()));root.put("items",items);a.getSharedPreferences("meelano_visitor_draft",0).edit().putString("draft_"+AtiranAuth.sessionUid(a),root.toString()).apply();}catch(Exception ignored){}
    }

    private void restoreDraft(){
        try{String raw=a.getSharedPreferences("meelano_visitor_draft",0).getString("draft_"+AtiranAuth.sessionUid(a),"");if(raw.isEmpty())return;org.json.JSONObject root=new org.json.JSONObject(raw);draftUuid=root.optString("uuid",draftUuid);if(root.has("customerCode"))selectedCustomer=new Customer(root.optLong("customerCode"),root.optString("customerName"),root.optString("phone"),root.optString("address"));org.json.JSONArray arr=root.optJSONArray("items");if(arr!=null)for(int i=0;i<arr.length();i++){org.json.JSONObject o=arr.optJSONObject(i);if(o!=null)cart.add(new CartLine(new Product(o.optString("code"),o.optString("name"),o.optString("unit"),new BigDecimal(o.optString("price","0")),new BigDecimal(o.optString("stock","0")),o.optInt("group"),1),new BigDecimal(o.optString("qty","1"))));}}catch(Exception ignored){}
    }

    private void clearDraft(){a.getSharedPreferences("meelano_visitor_draft",0).edit().remove("draft_"+AtiranAuth.sessionUid(a)).apply();}
    private void ensureVisitTable(java.sql.Connection db)throws Exception{try(java.sql.Statement st=db.createStatement()){st.execute("IF OBJECT_ID(N'dbo.meelano_visit_results',N'U') IS NULL CREATE TABLE dbo.meelano_visit_results(id bigint IDENTITY(1,1) PRIMARY KEY,visitor_username nvarchar(160) NULL,visitor_id nvarchar(80) NULL,customer_code nvarchar(100) NULL,customer_name nvarchar(250) NULL,result nvarchar(120) NULL,notes nvarchar(700) NULL,created_at datetime2 NOT NULL DEFAULT SYSDATETIME())");}}
    private static String q(String s){return "["+s.replace("]","]]")+ "]";}
    private static String money(BigDecimal value){return Money.rial(value==null?0:value.doubleValue());}
    private static String qty(BigDecimal value){return Money.qty(value==null?0:value.doubleValue());}
    private String statusFa(String s){if("pending_approval".equals(s))return "در انتظار بررسی";if("approved".equals(s))return "تأییدشده";if("rejected".equals(s))return "ردشده";if("invoiced".equals(s))return "فاکتور شده";if("needs_revision".equals(s))return "نیازمند اصلاح";return s==null||s.isEmpty()?"نامشخص":s;}

    { restoreDraft(); }
}
