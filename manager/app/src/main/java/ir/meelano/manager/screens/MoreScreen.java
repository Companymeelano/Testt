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

    private static final String[][] ITEMS = {
            {"home", "⌂", "خانه", "داشبورد مدیریتی و هشدارها"},
            {"sales", "🧾", "فروش", "فاکتورها، معوق‌ها و تحلیل فروش"},
            {"buy", "🧺", "خرید", "فاکتورهای خرید و طرف‌حساب‌ها"},
            {"dar_in", "↓", "دریافت‌ها", "قبوض دریافت و ترکیب آن‌ها"},
            {"dar_out", "↑", "پرداخت‌ها", "قبوض پرداخت و گردش کارت"},
            {"cheques", "◉", "چک‌ها", "دریافتی، پرداختی و سررسیدها"},
            {"products", "▦", "کالاها", "موجودی، کم‌موجودی و گردش کالا"},
            {"customers", "♙", "مشتریان", "پرونده کامل و گردش حساب"},
            {"visitors", "♟", "ویزیتورها", "عملکرد، وصول و اهداف"},
            {"users", "⛉", "کاربران", "کاربران سیستم و ورودها"},
            {"profit", "↗", "سود و زیان", "حاشیه سود و هزینه‌ها"},
            {"reports", "▤", "گزارشات", "مرکز گزارش‌های مدیریتی"},
            {"settings", "⚙", "تنظیمات", "اتصال، قفل و درباره"},
    };

    private static final int[] ACCENTS = {
            Theme.GOLD, Theme.GOLD, Theme.INFO, Theme.SUCCESS, Theme.WARNING, Theme.INFO,
            Theme.VIOLET, Theme.SUCCESS, Theme.WARNING, Theme.MUTED, Theme.GOLD, Theme.GOLD, Theme.MUTED,
    };

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        for (int i = 0; i < ITEMS.length; i++) {
            final String id = ITEMS[i][0];
            View r = a.kit.navRow(ITEMS[i][1], ITEMS[i][2], ITEMS[i][3], ACCENTS[i], v -> a.nav(id));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(r, p);
        }
    }
}
