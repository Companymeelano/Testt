package ir.meelano.manager.screens;

import android.view.View;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.ui.Theme;

/** Full section index (the 5th tab). */
public class MoreScreen extends Screen {
    public MoreScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "more"; }

    @Override
    public String title() { return "همه بخش‌ها"; }

    @Override
    public String glyph() { return "▦"; }

    @Override
    public int accent() { return Theme.GOLD; }

    /** Section id → description. Glyph/title/accent always mirror the destination screen. */
    private static final String[][] ITEMS = {
            {"home", "داشبورد مدیریتی و هشدارها"},
            {"sales", "فاکتورها، معوق‌ها و تحلیل فروش"},
            {"buy", "فاکتورهای خرید و طرف‌حساب‌ها"},
            {"dar_in", "قبوض دریافت و ترکیب آن‌ها"},
            {"dar_out", "قبوض پرداخت و گردش کارت"},
            {"cheques", "دریافتی، پرداختی و سررسیدها"},
            {"products", "موجودی، کم‌موجودی و گردش کالا"},
            {"customers", "پرونده کامل و گردش حساب"},
            {"visitors", "عملکرد، وصول و اهداف"},
            {"users", "کاربران سیستم و ورودها"},
            {"profit", "حاشیه سود و هزینه‌ها"},
            {"reports", "مرکز گزارش‌های مدیریتی"},
            {"settings", "اتصال، قفل و درباره"},
    };

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        for (int i = 0; i < ITEMS.length; i++) {
            final String id = ITEMS[i][0];
            Screen s = a.screen(id);
            if (s == null) continue;
            View r = a.kit.navRow(s.glyph(), s.title(), ITEMS[i][1], s.accent(), v -> a.nav(id));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(r, p);
        }
    }
}
