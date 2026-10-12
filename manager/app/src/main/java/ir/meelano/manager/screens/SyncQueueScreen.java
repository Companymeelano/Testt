package ir.meelano.manager.screens;

import android.view.View;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.WarehouseSync;
import ir.meelano.manager.core.DeviceId;
import ir.meelano.manager.core.WarehouseCountDb;
import ir.meelano.manager.core.WarehouseWriter;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Theme;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** Durable retry queue for deliveries and manager-review count submissions. */
public class SyncQueueScreen extends Screen {
    private LinearLayout content;
    private boolean running;

    public SyncQueueScreen(MainActivity a) { super(a); }
    @Override public String id() { return "sync_queue"; }
    @Override public String title() { return "صف همگام‌سازی"; }
    @Override public String glyph() { return "⟳"; }
    @Override public int accent() { return Theme.INFO; }

    @Override public void render(LinearLayout content) {
        this.content = content;
        paint();
        if (WarehouseSync.pendingCount(a) > 0) retry();
    }

    private void paint() {
        if (content == null) return;
        content.removeAllViews();
        int pending = WarehouseSync.pendingCount(a);
        content.addView(a.kit.hero(glyph(), title(),
                pending == 0 ? "همهٔ تغییرهای صف‌شده تأیید شده‌اند"
                        : "" + ir.meelano.manager.core.Money.fa(String.valueOf(pending))
                                + " مورد منتظر تأیید سرور", accent()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        LinearLayout status = a.kit.card(pending == 0 ? Theme.SUCCESS : Theme.WARNING);
        status.addView(a.kit.text(pending == 0 ? "صف خالی است" : "در انتظار همگام‌سازی: "
                + ir.meelano.manager.core.Money.fa(String.valueOf(pending)), 14f,
                pending == 0 ? Theme.SUCCESS : Theme.WARNING, true), a.kit.lp(-1, -2));
        status.addView(a.kit.hint("تحویل‌های آفلاین و گزارش شمارش کور تا تأیید سرور روی دستگاه می‌مانند؛ خطای شبکه هیچ‌کدام را حذف نمی‌کند."),
                a.kit.lp(-1, -2));
        status.addView(a.kit.btnGold(running ? "در حال تلاش…" : "⟳ تلاش دوباره برای همه", v -> retry()),
                a.kit.lp(-1, -2));
        content.addView(status, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));

        List<JSONObject> handovers = WarehouseWriter.pendingHandovers(a);
        content.addView(a.kit.text("تحویل‌های منتظر", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        if (handovers.isEmpty()) content.addView(a.kit.hint("تحویلِ منتظر ندارید."), a.kit.lp(-1, -2));
        else {
            for (JSONObject e : handovers) {
                String day = e.optString("dt", "—");
                String no = e.optString("no", "—");
                String receiver = e.optString("receiver", "گیرنده ثبت نشده");
                String detail = e.optString("cust", "") + " • " + receiver
                        + (e.optString("worker", "").isEmpty() ? "" : " • کارگر: " + e.optString("worker"));
                View row = a.kit.personRow("فاکتور " + ir.meelano.manager.core.Money.fa(no),
                        ir.meelano.manager.core.Money.fa(day) + " • " + detail,
                        e.optString("items", ""), "در صف", Theme.WARNING, null);
                content.addView(row, a.kit.lp(-1, -2));
                content.addView(a.kit.gap(4));
            }
        }

        int countN = 0;
        content.addView(a.kit.gap(8));
        content.addView(a.kit.text("شمارش‌های منتظر ارسال برای تأیید مدیر", 13.5f, Theme.TEXT, true),
                a.kit.lp(-1, -2));
        for (JSONObject d : WarehouseWriter.listDrafts(a)) {
            if (!WarehouseWriter.COUNT.equals(d.optString(WarehouseWriter.D_TYPE, ""))
                    || "done".equals(d.optString("approvalSync", ""))) continue;
            JSONArray lines = d.optJSONArray(WarehouseWriter.D_LINES);
            if (lines == null || lines.length() == 0) continue;
            countN++;
            content.addView(a.kit.personRow("شمارش انبار • "
                            + ir.meelano.manager.core.Money.fa(String.valueOf(d.optInt(WarehouseWriter.D_NO, 0))),
                    ir.meelano.manager.core.Money.fa(d.optString(WarehouseWriter.D_DATE, ""))
                            + " • " + d.optString(WarehouseWriter.D_ANBARNAME, "سراسری")
                            + " • ثبت‌کننده: " + d.optString(WarehouseWriter.D_USER, "—"),
                    ir.meelano.manager.core.Money.fa(String.valueOf(lines.length())) + " قلم",
                    "در انتظار", Theme.WARNING, null), a.kit.lp(-1, -2));
        }
        if (countN == 0) content.addView(a.kit.hint("گزارش شمارشِ منتظر ارسال ندارید."), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        final LinearLayout decisions = a.kit.v();
        content.addView(decisions, a.kit.lp(-1, -2));
        loadDecisions(decisions);
        content.addView(a.kit.gap(8));
        content.addView(a.kit.hint("وضعیت «در صف» فقط پس از ثبت و خواندن مجدد از جدول جانبی سرور پاک می‌شود؛ در صورت نداشتن مجوز ساخت/نوشتن جدول، مورد محلی باقی می‌ماند."),
                a.kit.lp(-1, -2));
    }

    private void loadDecisions(final LinearLayout box) {
        box.removeAllViews();
        a.repo.run(c -> {
            WarehouseCountDb.ensure(c);
            if (!WarehouseCountDb.available(c)) return new java.util.ArrayList<JSONObject>();
            return WarehouseCountDb.mine(c, DeviceId.code(a));
        }, new ir.meelano.manager.data.Repo.Cb<List<JSONObject>>() {
            @Override public void ok(List<JSONObject> rows) {
                if (content == null || box.getParent() == null) return;
                box.removeAllViews();
                if (rows == null || rows.isEmpty()) return;
                LinearLayout card = a.kit.card(Theme.INFO);
                card.addView(a.kit.sectionHead("تصمیم مدیر دربارهٔ شمارش‌های من", null, null), a.kit.lp(-1, -2));
                for (JSONObject row : rows) {
                    String status = row.optString("status", "pending");
                    boolean approved = "approved".equalsIgnoreCase(status);
                    boolean rejected = "rejected".equalsIgnoreCase(status);
                    String state = approved ? "تأیید مدیر • تعدیل موجودی ثبت نشده"
                            : (rejected ? "رد شده" : "در انتظار بررسی مدیر");
                    String note = row.optString("reviewNote", "");
                    String sub = ir.meelano.manager.core.Money.fa(row.optString("day", ""))
                            + " • " + row.optString("anbar", "سراسری")
                            + (note.isEmpty() ? "" : " • علت/یادداشت: " + note);
                    card.addView(a.kit.kv(state, sub, approved ? Theme.SUCCESS
                            : (rejected ? Theme.DANGER : Theme.INFO)), a.kit.lp(-1, -2));
                }
                box.addView(card, a.kit.lp(-1, -2));
            }
            @Override public void fail(String faError) {
                if (content == null || box.getParent() == null) return;
                box.removeAllViews();
                box.addView(a.kit.hint("وضعیت بررسی مدیر در دسترس نیست؛ صف محلی دست‌نخورده است."),
                        a.kit.lp(-1, -2));
            }
        });
    }

    private void retry() {
        if (running) return;
        running = true;
        paint();
        WarehouseSync.retry(a, (handovers, counts, pending, error) -> {
            running = false;
            if (error != null && !error.isEmpty()) a.kit.toast("همگام‌سازی انجام نشد؛ موارد در صف ماندند");
            else if (handovers + counts > 0) a.kit.toast("همگام شد: "
                    + ir.meelano.manager.core.Money.fa(String.valueOf(handovers + counts)) + " مورد");
            paint();
        });
    }
}
