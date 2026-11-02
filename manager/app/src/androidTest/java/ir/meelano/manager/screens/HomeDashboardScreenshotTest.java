package ir.meelano.manager.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import ir.meelano.licensing.License;
import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.DeviceId;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.ui.Theme;

import java.io.File;
import java.io.FileOutputStream;

/** Seed-only instrumentation preview for real Light/Dark screenshots and large-font Nuts accessibility checks. */
@RunWith(AndroidJUnit4.class)
public final class HomeDashboardScreenshotTest {
    @Test public void captureThemesAndVerifyLargeFontNutsAccessibility() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        capture(target, "light", "home-dashboard-light.png");
        capture(target, "dark", "home-dashboard-dark.png");
        verifyLargeFontNuts(target);
    }

    private void capture(Context target, String theme, String filename) throws Exception {
        Activity activity = startPreview(target, theme, 1);
        verifyShellInteractions(activity);
        // Do not wait for global UI idleness: the live chart may animate continuously.
        SystemClock.sleep(500L);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Bitmap[] captured = {null};
        instrumentation.runOnMainSync(() -> {
            View decor = activity.getWindow().getDecorView();
            if (decor.getWidth() <= 0 || decor.getHeight() <= 0) return;
            Bitmap bitmap = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(bitmap));
            captured[0] = bitmap;
        });
        Bitmap screenshot = captured[0];
        assertNotNull("Home activity could not render a screenshot", screenshot);
        File directory = new File(target.getFilesDir(), "home-dashboard-screenshots");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cannot create screenshot directory");
        File output = new File(directory, filename);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
                throw new IllegalStateException("PNG encoding failed: " + filename);
        } finally {
            screenshot.recycle();
        }
        finishPreview(activity);
    }

    /** Exercise the preserved Nuts identity with the app's largest text setting. */
    private void verifyLargeFontNuts(Context target) throws Exception {
        Activity activity = startPreview(target, "nuts", 3);
        try {
            assertTrue("Nuts theme did not remain active", Theme.isNuts());
            assertEquals("Nuts theme crest was not selected", R.drawable.meelano_nuts, Theme.logoRes());
            assertEquals("Largest text setting was not applied", 1.3f, Theme.fontScale(), 0.001f);
            verifyShellInteractions(activity);
        } finally {
            finishPreview(activity);
        }
    }

    private Activity startPreview(Context target, String theme, int zoomIndex) throws Exception {
        target.getSharedPreferences("meelano_manager7", Context.MODE_PRIVATE).edit().clear().commit();
        target.getSharedPreferences("meelano_login", Context.MODE_PRIVATE).edit().clear().commit();
        target.getSharedPreferences("meelano_roles", Context.MODE_PRIVATE).edit().clear().commit();
        target.getSharedPreferences("meelano_license", Context.MODE_PRIVATE).edit().clear().commit();

        Settings settings = new Settings(target);
        settings.setThemeMode(theme);
        settings.setZoomIdx(zoomIndex);
        settings.setBigFont(zoomIndex >= 3);
        settings.setMoneyUnit(Money.UNIT_TOMAN);
        settings.setHomeSimpleMode(true);
        settings.setHomeOrder(Settings.HOME_ORDER_DEFAULT);
        settings.setHomeSections(Settings.HOME_SECTIONS_DEFAULT);
        settings.setCloseoutCashOpening(Jalali.todayStr(), "1000000000");
        settings.setCloseoutCashCounted(Jalali.todayStr(), "1100000000");
        settings.saveConnection("127.0.0.1", "1", "HOME_PREVIEW", "preview", "preview");
        settings.saveHomeCache(sampleCache());

        LicenseStore.Status license = LicenseStore.activate(target,
                License.generate(DeviceId.code(target), License.P_PERM, 0));
        assertTrue("Could not seed debug preview license: " + license.fa, license.ok);
        LicenseStore.setEnteredOnce(target);
        LicenseStore.setTourPending(target, false);
        AtiranAuth.saveSession(target, 1, "arena-preview", "مدیر نمونه", RoleStore.ADMIN, true, true);

        Intent launch = new Intent(target, MainActivity.class);
        launch.putExtra("ir.meelano.manager.HOME_SCREENSHOT_PREVIEW", true);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Activity activity = instrumentation.startActivitySync(launch);
        assertNotNull("Manager shell did not start", activity);
        awaitDashboard(instrumentation, activity);
        return activity;
    }

    private static void awaitDashboard(Instrumentation instrumentation, Activity activity) {
        String[] visibleText = {""};
        long deadline = SystemClock.uptimeMillis() + 20_000L;
        while (SystemClock.uptimeMillis() < deadline) {
            visibleText[0] = "";
            instrumentation.runOnMainSync(() ->
                    collectText(activity.getWindow().getDecorView(), visibleText));
            if (visibleText[0].contains("شاخص‌های مالی")) break;
            SystemClock.sleep(250L);
        }
        assertTrue("Home dashboard was not visible; current UI text: " + visibleText[0],
                visibleText[0].contains("شاخص‌های مالی"));
        assertFalse("Preview screenshot contains a live connection error",
                visibleText[0].contains("جزئیات اتصال"));
    }

    private static void verifyDashboardPolish(Activity activity) {
        assertTrue("Selected theme did not resolve 3D financial artwork",
                Theme.isIcon3d(Theme.icon3d(R.drawable.mi_account_balance_wallet)));
        assertTrue("Selected theme did not resolve 3D return artwork",
                Theme.isIcon3d(Theme.icon3d(R.drawable.mi_assignment_return)));
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final View[] targets = new View[8];
        final int[] facts = new int[4];
        long layoutDeadline = SystemClock.uptimeMillis() + 3_000L;
        do {
            instrumentation.runOnMainSync(() -> {
                View decor = activity.getWindow().getDecorView();
                targets[0] = findTaggedView(decor, "home-financial-kpis");
                targets[1] = targets[0] == null ? null
                        : findTaggedView(targets[0], "home-financial-kpi");
                targets[2] = findTaggedView(decor, "home-cash-position");
                targets[3] = findTaggedView(decor, "home-closeout-card");
                targets[4] = findTaggedView(decor, "home-smart-report-share");
                targets[5] = findTaggedView(decor, "home-bottom-safe-space");
                targets[6] = findTaggedView(decor, "header-license-chip");
                targets[7] = targets[0] == null ? null
                        : findTaggedView(targets[0], "home-kpi-delta");
                facts[0] = targets[0] == null ? 0 : countTaggedViews(targets[0], "home-financial-kpi");
                facts[1] = targets[0] != null
                        && containsViewType(targets[0], HorizontalScrollView.class) ? 1 : 0;
                facts[2] = targets[1] == null ? 0 : targets[1].getWidth();
                facts[3] = targets[5] == null ? 0 : targets[5].getHeight();
            });
            if (facts[2] > 0 && facts[3] > 0) break;
            SystemClock.sleep(40L);
        } while (SystemClock.uptimeMillis() < layoutDeadline);
        assertNotNull("Financial KPI section is missing", targets[0]);
        assertEquals("Financial KPIs still use a clipping horizontal carousel", 0, facts[1]);
        assertEquals("Admin preview should show four complete financial metrics", 4, facts[0]);
        assertNotNull("Financial metric tiles were not rendered", targets[1]);
        assertTrue("Financial metric tile is too narrow for its value",
                facts[2] >= 120f * activityDensity(targets[1]));
        assertNotNull("Cash-position module is missing", targets[2]);
        assertNotNull("Closeout module is missing", targets[3]);
        assertNotNull("Smart-report share action is missing", targets[4]);
        assertTrue("Smart-report share action is not clickable", targets[4].isClickable());
        assertTrue("Smart-report share action is not accessible", targets[4].isFocusable());
        assertNotNull("Home has no clearance below the last card", targets[5]);
        assertTrue("Home's bottom clearance is smaller than 16dp",
                facts[3] >= 16f * activityDensity(targets[5]));
        assertNotNull("Expanded header licence chip is missing", targets[6]);
        assertTrue("Licence chip is not a text control", targets[6] instanceof TextView);
        TextView licence = (TextView) targets[6];
        assertTrue("Licence chip is below the 48dp touch target",
                licence.getWidth() >= 48f * activityDensity(licence));
        assertFalse("Licence chip text is clipped", licence.getText().toString().contains("…"));
        assertTrue("Licence chip has no accessible meaning",
                licence.getContentDescription() != null
                        && licence.getContentDescription().toString().contains("مجوز"));
        assertNotNull("Financial comparison badge is missing", targets[7]);
        TextView delta = (TextView) targets[7];
        assertFalse("Dashboard comparison badge still truncates its basis",
                delta.getText().toString().contains("…"));
        assertFalse("Visual delta badge is still overloaded with comparison copy",
                delta.getText().toString().contains("نسبت به"));
        assertTrue("Full comparison period was lost to accessibility",
                delta.getContentDescription() != null
                        && delta.getContentDescription().toString().contains("نسبت به"));
    }

    private static boolean containsViewType(View view, Class<?> type) {
        if (view == null) return false;
        if (type.isInstance(view)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (containsViewType(group.getChildAt(i), type)) return true;
        }
        return false;
    }

    private static int countTaggedViews(View view, String tag) {
        if (view == null) return 0;
        int count = tag.equals(view.getTag()) ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                count += countTaggedViews(group.getChildAt(i), tag);
        }
        return count;
    }

    private static void finishPreview(Activity activity) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        SystemClock.sleep(350L);
    }

    /** Exercise Android View touch dispatch and header scrolling without relying on headless window focus. */
    private static void verifyShellInteractions(Activity activity) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final View[] targets = new View[3];
        instrumentation.runOnMainSync(() -> {
            View decor = activity.getWindow().getDecorView();
            targets[0] = findTaggedView(decor, "main-scroll-viewport");
            targets[1] = findTaggedView(decor, "main-bottom-nav");
            targets[2] = findTaggedView(decor, "main-brand-header");
        });
        assertNotNull("Scroll viewport is missing", targets[0]);
        assertTrue("Scroll viewport is not a ScrollView", targets[0] instanceof ScrollView);
        assertNotNull("Bottom navigation is missing", targets[1]);
        assertTrue("Bottom navigation is not a ViewGroup", targets[1] instanceof ViewGroup);
        assertNotNull("Collapsible brand header is missing", targets[2]);
        assertNotNull("Brand row is not visually grouped into a themed header panel",
                targets[2].getBackground());
        verifyDashboardPolish(activity);

        ViewGroup dock = (ViewGroup) targets[1];
        assertEquals("Admin dock should have five destinations", 5, dock.getChildCount());
        boolean[] laidOut = {false};
        long dockDeadline = SystemClock.uptimeMillis() + 3_000L;
        while (SystemClock.uptimeMillis() < dockDeadline && !laidOut[0]) {
            instrumentation.runOnMainSync(() -> {
                laidOut[0] = dock.getChildCount() == 5;
                for (int i = 0; i < dock.getChildCount() && laidOut[0]; i++) {
                    View tab = dock.getChildAt(i);
                    laidOut[0] = tab.getWidth() > 0 && tab.getHeight() > 0;
                }
            });
            if (!laidOut[0]) SystemClock.sleep(40L);
        }
        assertTrue("Five bottom destinations were not measured", laidOut[0]);
        float minTouchSize = 48f * activityDensity(dock);
        for (int i = 0; i < dock.getChildCount(); i++) {
            View tab = dock.getChildAt(i);
            CharSequence spokenLabel = tab.getContentDescription();
            assertTrue("Bottom destination is not keyboard/TalkBack focusable: " + i, tab.isFocusable());
            assertTrue("Bottom destination is not clickable: " + i, tab.isClickable());
            assertNotNull("Bottom destination has no accessible label: " + i, spokenLabel);
            assertFalse("Bottom destination has a blank accessible label: " + i,
                    spokenLabel.toString().trim().isEmpty());
            assertTrue("Bottom destination is narrower than 48dp: " + i, tab.getWidth() >= minTouchSize);
            assertTrue("Bottom destination is shorter than 48dp: " + i, tab.getHeight() >= minTouchSize);
        }
        View moreTab = findTaggedView(dock, "more");
        View homeTab = findTaggedView(dock, "home");
        assertNotNull("Role-filtered More tab is missing", moreTab);
        assertNotNull("Home tab is missing", homeTab);

        String moreTarget = activate(instrumentation, moreTab);
        assertCurrentScreen(instrumentation, (MainActivity) activity, "more",
                "Bottom-tab action did not open More; " + moreTarget);
        String homeTarget = activate(instrumentation, homeTab);
        assertCurrentScreen(instrumentation, (MainActivity) activity, "home",
                "Bottom-tab action did not return to Home; " + homeTarget);

        ScrollView scroll = (ScrollView) targets[0];
        View brand = targets[2];
        int[] expandedHeight = {0};
        int[] scrollRange = {0};
        instrumentation.runOnMainSync(() -> {
            expandedHeight[0] = brand.getHeight();
            View child = scroll.getChildAt(0);
            scrollRange[0] = child == null ? 0 : child.getHeight() - scroll.getHeight();
        });
        assertTrue("Header was not measured before scrolling", expandedHeight[0] > 0);
        float density = activity.getResources().getDisplayMetrics().density;
        assertTrue("Home content does not provide enough vertical scroll range", scrollRange[0] > 72f * density);

        swipe(instrumentation, scroll, true);
        int[] scrolledY = {0};
        int[] collapsedHeight = {0};
        long collapseDeadline = SystemClock.uptimeMillis() + 2_000L;
        do {
            instrumentation.runOnMainSync(() -> {
                scrolledY[0] = scroll.getScrollY();
                collapsedHeight[0] = brand.getHeight();
            });
            if (scrolledY[0] > 0 && collapsedHeight[0] < expandedHeight[0]) break;
            SystemClock.sleep(40L);
        } while (SystemClock.uptimeMillis() < collapseDeadline);
        assertTrue("Dashboard did not scroll vertically", scrolledY[0] > 0);
        assertTrue("Brand header did not collapse after scrolling",
                collapsedHeight[0] < expandedHeight[0]);

        for (int attempt = 0; attempt < 4 && readScrollY(instrumentation, scroll) > 24f * density; attempt++)
            swipe(instrumentation, scroll, false);
        int[] returnedY = {0};
        int[] expandedAgainHeight = {0};
        long expandDeadline = SystemClock.uptimeMillis() + 2_000L;
        do {
            instrumentation.runOnMainSync(() -> {
                returnedY[0] = scroll.getScrollY();
                expandedAgainHeight[0] = brand.getHeight();
            });
            if (returnedY[0] <= 24f * density && expandedAgainHeight[0] >= expandedHeight[0]) break;
            SystemClock.sleep(40L);
        } while (SystemClock.uptimeMillis() < expandDeadline);
        assertTrue("Reverse touch swipes could not return near the top", returnedY[0] <= 24f * density);
        assertTrue("Brand header did not expand at the top", expandedAgainHeight[0] >= expandedHeight[0]);
    }

    private static void assertCurrentScreen(Instrumentation instrumentation, MainActivity activity,
                                            String expected, String message) {
        String[] current = {""};
        long deadline = SystemClock.uptimeMillis() + 2_000L;
        do {
            instrumentation.runOnMainSync(() -> current[0] = activity.currentScreenId());
            if (expected.equals(current[0])) return;
            SystemClock.sleep(40L);
        } while (SystemClock.uptimeMillis() < deadline);
        assertEquals(message, expected, current[0]);
    }

    private static View findTaggedView(View view, String tag) {
        if (view == null) return null;
        if (tag.equals(view.getTag())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTaggedView(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Check a real View target's visible hit area, then dispatch its installed click action. */
    private static String activate(Instrumentation instrumentation, View target) {
        int[] bounds = new int[4];
        boolean[] ready = {false};
        String[] details = {"not measured"};
        long deadline = SystemClock.uptimeMillis() + 3_000L;
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                int[] location = new int[2];
                Rect visible = new Rect();
                target.getLocationOnScreen(location);
                boolean hasVisibleBounds = target.getGlobalVisibleRect(visible);
                bounds[0] = location[0];
                bounds[1] = location[1];
                bounds[2] = target.getWidth();
                bounds[3] = target.getHeight();
                ready[0] = target.isShown() && target.isAttachedToWindow()
                        && target.isEnabled() && target.isClickable()
                        && hasVisibleBounds && visible.width() > 0 && visible.height() > 0
                        && bounds[2] > 0 && bounds[3] > 0;
                details[0] = "tag=" + target.getTag() + ", screen=" + location[0] + "," + location[1]
                        + " " + bounds[2] + "x" + bounds[3]
                        + ", visible=" + visible.toShortString() + ", shown=" + target.isShown()
                        + ", attached=" + target.isAttachedToWindow() + ", enabled=" + target.isEnabled()
                        + ", clickable=" + target.isClickable();
            });
            if (ready[0]) break;
            SystemClock.sleep(50L);
        }
        assertTrue("Touch target is not visible and laid out: " + details[0], ready[0]);
        float minimumTouchSize = 48f * activityDensity(target);
        assertTrue("Touch target is narrower than 48dp: " + details[0], bounds[2] >= minimumTouchSize);
        assertTrue("Touch target is shorter than 48dp: " + details[0], bounds[3] >= minimumTouchSize);
        dispatchTap(instrumentation, target, details[0]);
        return details[0] + ", touch=dispatched";
    }

    private static void dispatchTap(Instrumentation instrumentation, View target, String details) {
        float[] center = new float[2];
        instrumentation.runOnMainSync(() -> {
            center[0] = target.getWidth() / 2f;
            center[1] = target.getHeight() / 2f;
        });
        long downTime = SystemClock.uptimeMillis();
        assertTrue("Touch target rejected ACTION_DOWN: " + details,
                dispatchTouchEvent(instrumentation, target, downTime, MotionEvent.ACTION_DOWN,
                        center[0], center[1]));
        SystemClock.sleep(60L);
        assertTrue("Touch target rejected ACTION_UP: " + details,
                dispatchTouchEvent(instrumentation, target, downTime, MotionEvent.ACTION_UP,
                        center[0], center[1]));
        SystemClock.sleep(160L);
    }

    private static int readScrollY(Instrumentation instrumentation, ScrollView scroll) {
        int[] value = {0};
        instrumentation.runOnMainSync(() -> value[0] = scroll.getScrollY());
        return value[0];
    }

    private static void swipe(Instrumentation instrumentation, View target, boolean up) {
        float[] points = new float[4];
        instrumentation.runOnMainSync(() -> {
            points[0] = target.getWidth() / 2f;
            points[1] = target.getHeight() * (up ? 0.90f : 0.10f);
            points[2] = points[0];
            points[3] = target.getHeight() * (up ? 0.10f : 0.90f);
        });
        long downTime = SystemClock.uptimeMillis();
        assertTrue("Scroll viewport rejected swipe start",
                dispatchTouchEvent(instrumentation, target, downTime, MotionEvent.ACTION_DOWN,
                        points[0], points[1]));
        for (int step = 1; step <= 8; step++) {
            float fraction = step / 8f;
            float y = points[1] + (points[3] - points[1]) * fraction;
            dispatchTouchEvent(instrumentation, target, downTime, MotionEvent.ACTION_MOVE,
                    points[0], y);
            SystemClock.sleep(16L);
        }
        dispatchTouchEvent(instrumentation, target, downTime, MotionEvent.ACTION_UP,
                points[2], points[3]);
        SystemClock.sleep(100L);
    }

    private static boolean dispatchTouchEvent(Instrumentation instrumentation, View target,
                                              long downTime, int action, float x, float y) {
        boolean[] handled = {false};
        instrumentation.runOnMainSync(() -> {
            MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                    action, x, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {
                handled[0] = target.dispatchTouchEvent(event);
            } finally {
                event.recycle();
            }
        });
        return handled[0];
    }

    private static float activityDensity(View target) {
        return target.getResources().getDisplayMetrics().density;
    }

    private static String sampleCache() {
        String today = Jalali.todayStr();
        StringBuilder cache = new StringBuilder("ts=").append(today).append(" ساعت ۱۰:۳۰")
                .append("|sd=").append(today)
                .append("|sales=day=").append(today).append(";total=9200000000;docs=28")
                .append("|buy=total=4100000000;docs=17")
                .append("|in=total=8400000000;count=24")
                .append("|out=total=2900000000;count=8")
                .append("|buytrendok=1|intrendok=1|outtrendok=1|inmixok=1")
                .append("|returnsok=1|returnsprevok=1|trendok=1")
                .append("|trendfrom=").append(Jalali.addDays(today, -13))
                .append("|stockok=1|reorderok=1|priceok=1|dueinok=1|dueoutok=1|topok=1|overdueok=1")
                .append("|retodayok=1|cashinok=1|cashoutok=1|gpok=1")
                .append("|top0=label=پستهٔ نمونه;total=2400000000;qty=18;docs=7")
                .append("|overdue0=party=مشتری نمونه;amount=730000000;dueDate=").append(today)
                .append(";days=2;invoice=۴۸۲۱");

        for (int i = 0; i < 14; i++) {
            String day = Jalali.addDays(today, i - 13);
            long sales = 3_800_000_000L + (i * 410_000_000L) + ((i % 4) * 130_000_000L);
            if (i == 13) sales = 9_200_000_000L;
            cache.append("|trend").append(i).append("=day=").append(day)
                    .append(";total=").append(sales).append(";docs=").append(7 + i % 9);
        }
        for (int i = 0; i < 7; i++) {
            String day = Jalali.addDays(today, i - 6);
            cache.append("|buytrend").append(i).append("=day=").append(day)
                    .append(";total=").append(2_000_000_000L + i * 320_000_000L).append(";docs=").append(8 + i);
            cache.append("|intrend").append(i).append("=day=").append(day)
                    .append(";total=").append(4_500_000_000L + i * 520_000_000L).append(";docs=").append(12 + i);
            cache.append("|outtrend").append(i).append("=day=").append(day)
                    .append(";total=").append(1_600_000_000L + i * 180_000_000L).append(";docs=").append(4 + i);
        }
        cache.append("|inmix0=label=نقد;amount=3100000000")
                .append("|inmix1=label=کارت‌خوان;amount=2800000000")
                .append("|inmix2=label=حواله;amount=1800000000")
                .append("|inmix3=label=چک‌های دریافتی;amount=700000000")
                .append("|cashin=cash=3100000000;pos=2800000000;havaleh=1800000000;cheque=700000000;breakdownAvailable=1")
                .append("|cashout=cash=1600000000;pos=900000000;havaleh=400000000;cheque=0;breakdownAvailable=1")
                .append("|retoday0=label=فاکتور برگشتی;docs=1;total=250000000")
                .append("|gp0=day=").append(today).append(";sales=9200000000;cogs=6100000000;profit=3100000000")
                .append("|ret0=total=0;docs=0")
                .append("|retprev0=total=500000000;docs=2")
                .append("|stock0=shka=1001;name=بادام نمونه;stockPieces=0;reorderPoint=5;reorderKnown=1;isOut=1")
                .append("|price0=label=پسته نمونه;lastUnit=1200000;prevUnit=1000000;change=20")
                .append("|duein0=amount=1800000000;sardate=").append(today)
                .append("|dueout0=amount=900000000;sardate=").append(Jalali.addDays(today, 2));
        return cache.toString();
    }

    private static void collectText(View view, String[] output) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null) output[0] += text + "\n";
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), output);
        }
    }
}
