package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.VisitorPrefactorWriter;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/** Manager inbox for visitor pre-invoices; review and approval never posts a final Atiran invoice. */
public final class VisitorOrdersScreen extends Screen {
    private int filter;
    private LinearLayout contentRef;
    public VisitorOrdersScreen(MainActivity a){super(a);}
    @Override public String id(){return "visitor_orders";}
    @Override public String title(){return "پیش‌فاکتورهای ویزیتور";}
    @Override public String glyph(){return "🧾";}
    @Override public int accent(){return Theme.INFO;}

    @Override public void render(LinearLayout content){
        contentRef=content;content.removeAllViews();
        content.addView(a.kit.hero("🧾","پیش‌فاکتورهای ویزیتور","بازبینی، تأیید برای تبدیل یا درخواست اصلاح",accent()),a.kit.lp(-1,-2));
        content.addView(a.kit.gap(8));
        content.addView(a.kit.chips(new String[]{"در انتظار بررسی","همه"},filter,i->{filter=i;load(content);}),a.kit.lp(-1,-2));
        load(content);
    }

    private void load(LinearLayout c){
        c.addView(a.kit.loading("در حال دریافت سفارش‌ها…"),a.kit.lp(-1,-2));
        a.repo.run(db->{VisitorPrefactorWriter.ensureTables(db);List<Row> out=new ArrayList<>();
            String sql="SELECT TOP (200) p.id,ISNULL(p.visitor_username,N''),ISNULL(p.visitor_id,N''),ISNULL(p.customer_name,N''),ISNULL(p.customer_code,N''),ISNULL(p.grand_total,0),p.status,CONVERT(nvarchar(19),p.created_at,120),ISNULL(p.approval_reason,N''),COUNT(i.id) FROM dbo.meelano_prefactors p LEFT JOIN dbo.meelano_prefactor_items i ON i.prefactor_id=p.id "+(filter==0?"WHERE p.status IN (N'pending_approval',N'needs_revision') ":"")+"GROUP BY p.id,p.visitor_username,p.visitor_id,p.customer_name,p.customer_code,p.grand_total,p.status,p.created_at,p.approval_reason ORDER BY p.id DESC";
            try(java.sql.PreparedStatement ps=db.prepareStatement(sql);java.sql.ResultSet rs=ps.executeQuery()){while(rs.next()){Row r=new Row();r.put("id",rs.getLong(1));r.put("username",rs.getString(2));r.put("visitor",rs.getString(3));r.put("customer",rs.getString(4));r.put("customerCode",rs.getString(5));r.put("total",rs.getDouble(6));r.put("status",rs.getString(7));r.put("created",rs.getString(8));r.put("reason",rs.getString(9));r.put("lines",rs.getInt(10));out.add(r);}}
            return out;
        },new Repo.Cb<List<Row>>(){
            @Override public void ok(List<Row> rows){c.removeAllViews();if(rows.isEmpty()){c.addView(a.kit.empty("سفارشی برای نمایش نیست",null),a.kit.lp(-1,-2));return;}for(Row r:rows)c.addView(a.kit.personRow("شماره "+r.s("id")+" • "+r.s("customer"),"ویزیتور "+r.s("username")+" • "+r.s("created"),Money.rial(r.d("total")),statusFa(r.s("status")),statusColor(r.s("status")),v->details(r)),a.kit.lp(-1,-2));}
            @Override public void fail(String e){c.removeAllViews();c.addView(a.kit.error(e,()->load(c)),a.kit.lp(-1,-2));}
        });
    }

    private void details(Row header){
        a.repo.run(db->{List<String> items=new ArrayList<>();try(java.sql.PreparedStatement ps=db.prepareStatement("SELECT product_name,qty,unit,price,amount FROM dbo.meelano_prefactor_items WHERE prefactor_id=? ORDER BY id")){ps.setLong(1,header.l("id"));try(java.sql.ResultSet rs=ps.executeQuery()){while(rs.next())items.add(rs.getString(1)+" • "+Money.qty(rs.getDouble(2))+" "+rs.getString(3)+" • "+Money.rial(rs.getDouble(4))+" • جمع "+Money.rial(rs.getDouble(5)));}}return items;},new Repo.Cb<List<String>>(){
            @Override public void ok(List<String> items){StringBuilder text=new StringBuilder("ویزیتور: ").append(header.s("username")).append(" (").append(header.s("visitor")).append(")\nمشتری: ").append(header.s("customer")).append(" • ").append(header.s("customerCode")).append("\nوضعیت: ").append(statusFa(header.s("status"))).append("\nجمع: ").append(Money.rial(header.d("total")));for(String x:items)text.append("\n\n").append(x);if(!header.s("reason").isEmpty())text.append("\n\nیادداشت: ").append(header.s("reason"));
                boolean pending="pending_approval".equals(header.s("status"))||"needs_revision".equals(header.s("status"));
                AlertDialog.Builder b=new AlertDialog.Builder(a).setTitle("پیش‌فاکتور "+header.s("id")).setMessage(text.toString());
                if(pending){b.setNegativeButton("رد سفارش",(d,w)->decide(header,"rejected"));b.setNeutralButton("درخواست اصلاح",(d,w)->decide(header,"needs_revision"));b.setPositiveButton("تأیید",(d,w)->decide(header,"approved"));}
                else b.setPositiveButton("بستن",null);
                b.show();
            }
            @Override public void fail(String e){a.kit.toast(e);}
        });
    }

    private void decide(Row row,String status){
        EditText note=a.kit.edit(status.equals("approved")?"یادداشت تأیید (اختیاری)":"دلیل اقدام","");
        String title=status.equals("approved")?"تأیید پیش‌فاکتور؟":status.equals("rejected")?"رد پیش‌فاکتور؟":"درخواست اصلاح؟";
        new AlertDialog.Builder(a).setTitle(title).setView(note).setNegativeButton("لغو",null).setPositiveButton("ثبت وضعیت",(d,w)->{
            String actor=AtiranAuth.sessionUser(a);String prefix=status.equals("approved")?"تأیید توسط ":status.equals("rejected")?"رد توسط ":"درخواست اصلاح توسط ";String msg=prefix+actor+" — "+note.getText().toString().trim();
            a.repo.run(db->{try(java.sql.PreparedStatement ps=db.prepareStatement("UPDATE dbo.meelano_prefactors SET status=?,approval_reason=?,ready_for_invoice=?,updated_at=SYSDATETIME() WHERE id=? AND status IN (N'pending_approval',N'needs_revision')")){ps.setString(1,status);ps.setString(2,msg);ps.setBoolean(3,"approved".equals(status));ps.setLong(4,row.l("id"));int n=ps.executeUpdate();if(n!=1)throw new Exception("وضعیت سفارش هم‌زمان تغییر کرده است؛ فهرست را تازه کنید.");}return true;},new Repo.Cb<Boolean>(){@Override public void ok(Boolean v){a.kit.toast(status.equals("approved")?"پیش‌فاکتور تأیید شد؛ آمادهٔ تبدیل به فاکتور است.":"درخواست اصلاح ثبت شد.");load(contentRef);}@Override public void fail(String e){a.kit.toast("تغییر وضعیت انجام نشد: "+e);}});
        }).show();
    }

    private int statusColor(String s){return "approved".equals(s)?Theme.SUCCESS:"rejected".equals(s)?Theme.DANGER:Theme.WARNING;}
    private String statusFa(String s){if("pending_approval".equals(s))return "در انتظار بررسی";if("needs_revision".equals(s))return "نیازمند اصلاح";if("approved".equals(s))return "تأییدشده • آمادهٔ تبدیل";if("rejected".equals(s))return "ردشده";if("invoiced".equals(s))return "فاکتور شده";return s==null?"نامشخص":s;}
}
