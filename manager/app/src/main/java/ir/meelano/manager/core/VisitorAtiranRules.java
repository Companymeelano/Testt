package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/** Server-side authorization and rate checks shared by all visitor write paths. */
final class VisitorAtiranRules {
    private VisitorAtiranRules() { }

    static void requireCustomerScope(Connection c, VisitorIdentity.Identity visitor, String customerCode) throws Exception {
        Meta m=new Meta(c);
        if(!m.table("CUSTOMERS"))throw new Exception("جدول مشتریان آتیران پیدا نشد.");
        String key=m.col("CUSTOMERS","SHMO","shmo"), vis=m.col("CUSTOMERS","vis_rdf","VisitorID","visitor_id"), active=m.col("CUSTOMERS","active","Active");
        if(key==null||vis==null||active==null)throw new Exception("پیوند مشتری به ویزیتور یا وضعیت فعال‌بودن آن در آتیران قابل بررسی نیست.");
        String sql="SELECT COUNT_BIG(1) FROM "+m.qTable("CUSTOMERS")+" WHERE TRY_CONVERT(nvarchar(100),"+q(key)+")=? AND TRY_CONVERT(int,"+q(vis)+")=?"+(active==null?"":" AND TRY_CONVERT(nvarchar(10),"+q(active)+") IN (N't',N'T',N'1',N'true',N'TRUE')");
        try(PreparedStatement ps=c.prepareStatement(sql)){
            ps.setString(1,customerCode.trim());ps.setInt(2,visitor.visitorId);
            try(ResultSet rs=ps.executeQuery()){if(!rs.next()||rs.getLong(1)!=1)throw new Exception("این مشتری به مسیر ویزیتور فعلی تخصیص ندارد یا پیوند آن یکتا نیست.");}
        }
    }

    static int defaultPriceGroup(Connection c,int visitorId)throws Exception{
        Meta m=new Meta(c);
        if(!m.table("DeviceSettings"))throw new Exception("تنظیم قیمت ویزیتور (DeviceSettings) در آتیران پیدا نشد.");
        String v=m.col("DeviceSettings","VisitorId","VisitorID","vis_rdf");
        String g=m.col("DeviceSettings","DefaultPriceGrp","DefaultPriceGroup");
        if(v==null||g==null)throw new Exception("ستون‌های VisitorId و DefaultPriceGrp در DeviceSettings پیدا نشد.");
        String sql="SELECT CASE WHEN COUNT_BIG(1)=1 AND COUNT_BIG(1)=COUNT_BIG(TRY_CONVERT(int,"+q(g)+")) AND MIN(TRY_CONVERT(int,"+q(g)+"))=MAX(TRY_CONVERT(int,"+q(g)+")) THEN MIN(TRY_CONVERT(int,"+q(g)+")) ELSE 0 END FROM "+m.qTable("DeviceSettings")+" WHERE TRY_CONVERT(int,"+q(v)+")=?";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,visitorId);try(ResultSet rs=ps.executeQuery()){int group=rs.next()?rs.getInt(1):0;if(VisitorPricePolicy.column(group)==null)throw new Exception("DefaultPriceGrp باید یک مقدار یکتا از ۱ تا ۵ باشد.");return group;}}
    }

    static void requirePrice(Connection c,int visitorId,VisitorPrefactorWriter.Line line)throws Exception{
        Meta m=new Meta(c);int group=defaultPriceGroup(c,visitorId);
        if(group!=line.priceGroup)throw new Exception("گروه قیمت ویزیتور تغییر کرده است؛ پیش‌فاکتور را با قیمت تازه بازسازی کنید.");
        if(!m.table("forosh_price"))throw new Exception("جدول قیمت فروش آتیران پیدا نشد.");
        String key=m.col("forosh_price","shka","SHKA","ProductID","KalaID");
        String price=m.col("forosh_price",VisitorPricePolicy.column(group));
        String active=m.col("forosh_price","active","Active");
        if(key==null||price==null||active==null)throw new Exception("ستون نرخ یا وضعیت فعال‌بودن آن در forosh_price پیدا نشد.");
        String sql="SELECT COUNT_BIG(1),MIN(TRY_CONVERT(decimal(19,2),"+q(price)+")) FROM "+m.qTable("forosh_price")+" WHERE TRY_CONVERT(nvarchar(100),"+q(key)+")=?"+(active==null?"":" AND TRY_CONVERT(nvarchar(10),"+q(active)+") IN (N't',N'T',N'1',N'true',N'TRUE')");
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,line.code);try(ResultSet rs=ps.executeQuery()){
            if(!rs.next()||!VisitorPricePolicy.isUnambiguous(group,rs.getLong(1),rs.getDouble(2)))throw new Exception("نرخ کالا «"+line.name+"» در آتیران مبهم، غیرفعال یا ناموجود است.");
            BigDecimal actual=rs.getBigDecimal(2);if(actual==null||actual.subtract(line.price).abs().compareTo(new BigDecimal("0.01"))>0)throw new Exception("قیمت «"+line.name+"» تغییر کرده است؛ سبد را تازه‌سازی کنید.");
        }}
        String productId=m.col("inventory","shka","SHKA","ProductID");
        String activeOnline=m.col("inventory","ActiveCapillarySales","active_capillary_sales");
        if(productId==null||activeOnline==null)throw new Exception("شناسه یا وضعیت فعال‌بودن فروش میدانی در inventory پیدا نشد.");
        String check="SELECT COUNT_BIG(1) FROM "+m.qTable("inventory")+" WHERE TRY_CONVERT(nvarchar(100),"+q(productId)+")=?"+(activeOnline==null?"":" AND ISNULL(TRY_CONVERT(int,"+q(activeOnline)+"),1)=1");
        try(PreparedStatement ps=c.prepareStatement(check)){ps.setString(1,line.code);try(ResultSet rs=ps.executeQuery()){if(!rs.next()||rs.getLong(1)!=1)throw new Exception("کالا «"+line.name+"» در فروش میدانی فعال نیست.");}}
    }

    static void requirePrices(Connection c,VisitorIdentity.Identity visitor,List<VisitorPrefactorWriter.Line> lines)throws Exception{
        int group=defaultPriceGroup(c,visitor.visitorId);
        for(VisitorPrefactorWriter.Line line:lines){
            if(line.priceGroup!=group)throw new Exception("گروه قیمت ویزیتور تغییر کرده است.");
            requirePrice(c,visitor.visitorId,line);
        }
    }

    static String q(String s){return "["+s.replace("]","]]")+ "]";}
}
