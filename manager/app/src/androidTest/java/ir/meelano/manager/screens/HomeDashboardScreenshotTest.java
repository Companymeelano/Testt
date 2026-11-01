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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import ir.meelano.licensing.License;
import ir.meelano.manager.MainActivity;
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
import java.util.List;

/** Seed-only instrumentation preview used by CI to capture the real Views in Light and Dark. */
@RunWith(AndroidJUnit4.class)
public final class HomeDashboardScreenshotTest {
    @Test public void captureHomeLightAndDark() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        capture(target, "light", "home-dashboard-light.png");
        capture(target, "dark", "home-dashboard-dark.png");
    }

    private void capture(Context target, String theme, String filename) throws Exception {
        target.getSharedPreferences("meelano_manager7", Context.MODE_PRIVATE).edit().clear().commit();
        target.getSharedPreferences("meelano_login", Context.MODE_PRIVATE).edit().clear().commit();
        target.getSharedPreferences("meelano_roles", Context.MODE_PRIVATE).edit().clear().commit();
        target.getSharedPreferences("meelano_license", Context.MODE_PRIVATE).edit().clear().commit();

        Settings settings = new Settings(target);
        settings.setThemeMode(theme);
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
        verifyShellInteractions(activity);
        // Do not wait for global UI idleness: the live chart may animate continuously.
        SystemClock.sleep(500L);
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
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        SystemClock.sleep(350L);
    }

    /** Exercise real pointer input, dock taps, and both transitions of the collapsible header. */
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

        ViewGroup dock = (ViewGroup) targets[1];
        assertEquals("Admin dock should have five destinations", 5, dock.getChildCount());
        View moreTab = findTaggedView(dock, "more");
        View homeTab = findTaggedView(dock, "home");
        assertNotNull("Role-filtered More tab is missing", moreTab);
        assertNotNull("Home tab is missing", homeTab);

        String moreTarget = tapWithTouchTrace(instrumentation, moreTab);
        assertCurrentScreen(instrumentation, (MainActivity) activity, "more",
                "Bottom-tab tap did not open More; " + moreTarget);
        String homeTarget = tap(instrumentation, homeTab);
        assertCurrentScreen(instrumentation, (MainActivity) activity, "home",
                "Bottom-tab tap did not return to Home; " + homeTarget);

        ScrollView scroll = (ScrollView) targets[0];
        View brand = targets[2];
        int[] expandedHeight = {0};
        instrumentation.runOnMainSync(() -> expandedHeight[0] = brand.getHeight());
        assertTrue("Header was not measured before scrolling", expandedHeight[0] > 0);

        swipe(instrumentation, scroll, true);
        int[] scrolledY = {0};
        int[] collapsedHeight = {0};
        instrumentation.runOnMainSync(() -> {
            scrolledY[0] = scroll.getScrollY();
            collapsedHeight[0] = brand.getHeight();
        });
        assertTrue("Vertical finger swipe did not scroll the dashboard", scrolledY[0] > 0);
        assertTrue("Brand header did not collapse after a clear scroll",
                collapsedHeight[0] < expandedHeight[0]);

        float density = activity.getResources().getDisplayMetrics().density;
        for (int attempt = 0; attempt < 16 && readScrollY(instrumentation, scroll) > 24f * density; attempt++)
            swipe(instrumentation, scroll, false);
        int[] returnedY = {0};
        int[] expandedAgainHeight = {0};
        instrumentation.runOnMainSync(() -> {
            returnedY[0] = scroll.getScrollY();
            expandedAgainHeight[0] = brand.getHeight();
        });
        assertTrue("Reverse finger swipes could not return to the top", returnedY[0] <= 24f * density);
        assertTrue("Brand header did not expand near the top", expandedAgainHeight[0] >= expandedHeight[0]);

        swipe(instrumentation, scroll, true);
        final View[] menuTarget = new View[1];
        instrumentation.runOnMainSync(() -> menuTarget[0] = findTaggedView(
                activity.getWindow().getDecorView(), "compact-header-menu"));
        View menuButton = menuTarget[0];
        assertNotNull("Compact header menu is missing", menuButton);
        boolean[] menuShown = {false};
        instrumentation.runOnMainSync(() -> menuShown[0] = menuButton.isShown());
        assertTrue("Header menu should be visible after scrolling", menuShown[0]);
        tap(instrumentation, menuButton);
        tapAccessibleAction(instrumentation, "بازگشت به صفحهٔ قبلی");
        assertCurrentScreen(instrumentation, (MainActivity) activity, "more",
                "Header-menu Back did not restore the prior screen");
        tap(instrumentation, homeTab);
        assertCurrentScreen(instrumentation, (MainActivity) activity, "home",
                "Home dock destination did not restore the dashboard");
    }

    private static void tapAccessibleAction(Instrumentation instrumentation, String label) {
        AccessibilityNodeInfo root = null;
        List<AccessibilityNodeInfo> matches = null;
        long deadline = SystemClock.uptimeMillis() + 2_000L;
        do {
            root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                matches = root.findAccessibilityNodeInfosByText(label);
                if (!matches.isEmpty()) break;
            }
            SystemClock.sleep(50L);
        } while (SystemClock.uptimeMillis() < deadline);
        assertNotNull("No active accessibility window for the header menu", root);
        assertFalse("Header-menu action is missing: " + label, matches == null || matches.isEmpty());
        Rect bounds = new Rect();
        matches.get(0).getBoundsInScreen(bounds);
        assertTrue("Header-menu action has no visible bounds: " + label,
                bounds.width() > 0 && bounds.height() > 0);
        tapPoint(instrumentation, bounds.centerX(), bounds.centerY());
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

    private static int readScrollY(Instrumentation instrumentation, ScrollView scroll) {
        int[] value = {0};
        instrumentation.runOnMainSync(() -> value[0] = scroll.getScrollY());
        return value[0];
    }

    private static String tapWithTouchTrace(Instrumentation instrumentation, View target) {
        StringBuilder events = new StringBuilder();
        instrumentation.runOnMainSync(() -> target.setOnTouchListener((view, event) -> {
            if (events.length() > 0) events.append(';');
            events.append(event.getActionMasked()).append('@')
                    .append(event.getRawX()).append(',').append(event.getRawY());
            return false;
        }));
        String details = tap(instrumentation, target);
        instrumentation.runOnMainSync(() -> Theme.pressable(target));
        return details + ", pointer=" + events;
    }

    private static String tap(Instrumentation instrumentation, View target) {
        int[] screenBounds = new int[4];
        boolean[] ready = {false};
        String[] details = {"not measured"};
        long deadline = SystemClock.uptimeMillis() + 3_000L;
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                int[] location = new int[2];
                Rect visible = new Rect();
                target.getLocationOnScreen(location);
                boolean hasVisibleBounds = target.getGlobalVisibleRect(visible);
                screenBounds[0] = location[0];
                screenBounds[1] = location[1];
                screenBounds[2] = target.getWidth();
                screenBounds[3] = target.getHeight();
                boolean windowFocused = target.getRootView().hasWindowFocus();
                int[] inWindow = new int[2];
                int[] rootOnScreen = new int[2];
                target.getLocationInWindow(inWindow);
                target.getRootView().getLocationOnScreen(rootOnScreen);
                ready[0] = target.isShown() && target.isAttachedToWindow()
                        && target.isEnabled() && target.isClickable() && windowFocused
                        && hasVisibleBounds && visible.width() > 0 && visible.height() > 0
                        && target.getWidth() > 0 && target.getHeight() > 0;
                details[0] = "tag=" + target.getTag() + ", screen=" + location[0] + "," + location[1]
                        + " " + target.getWidth() + "x" + target.getHeight()
                        + ", window=" + inWindow[0] + "," + inWindow[1]
                        + ", rootScreen=" + rootOnScreen[0] + "," + rootOnScreen[1]
                        + ", visible=" + visible.toShortString() + ", shown=" + target.isShown()
                        + ", attached=" + target.isAttachedToWindow() + ", enabled=" + target.isEnabled()
                        + ", clickable=" + target.isClickable() + ", windowFocused=" + windowFocused;
            });
            if (ready[0]) break;
            SystemClock.sleep(50L);
        }
        if (!ready[0]) details[0] += ", windows=" + accessibilityWindows(instrumentation);
        assertTrue("Touch target did not become visible and laid out: " + details[0], ready[0]);
        float minimumTouchSize = 48f * activityDensity(target);
        assertTrue("Touch target is narrower than 48dp: " + details[0], screenBounds[2] >= minimumTouchSize);
        assertTrue("Touch target is shorter than 48dp: " + details[0], screenBounds[3] >= minimumTouchSize);
        tapPoint(instrumentation, screenBounds[0] + screenBounds[2] / 2f,
                screenBounds[1] + screenBounds[3] / 2f);
        return details[0];
    }

    private static float activityDensity(View target) {
        return target.getResources().getDisplayMetrics().density;
    }

    private static String accessibilityWindows(Instrumentation instrumentation) {
        StringBuilder result = new StringBuilder();
        try {
            for (AccessibilityWindowInfo window : instrumentation.getUiAutomation().getWindows()) {
                Rect bounds = new Rect();
                window.getBoundsInScreen(bounds);
                AccessibilityNodeInfo root = window.getRoot();
                CharSequence packageName = root == null ? null : root.getPackageName();
                if (result.length() > 0) result.append(';');
                result.append("type=").append(window.getType())
                        .append(",active=").append(window.isActive())
                        .append(",focused=").append(window.isFocused())
                        .append(",title=").append(window.getTitle())
                        .append(",package=").append(packageName)
                        .append(",bounds=").append(bounds.toShortString());
                if (root != null) root.recycle();
            }
        } catch (RuntimeException error) {
            return "unavailable:" + error.getClass().getSimpleName();
        }
        return result.length() == 0 ? "none" : result.toString();
    }

    private static void tapPoint(Instrumentation instrumentation, float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        injectPointer(instrumentation, downTime, MotionEvent.ACTION_DOWN, x, y);
        SystemClock.sleep(100L);
        injectPointer(instrumentation, downTime, MotionEvent.ACTION_UP, x, y);
        SystemClock.sleep(160L);
    }

    private static void swipe(Instrumentation instrumentation, View target, boolean up) {
        float[] points = new float[4];
        instrumentation.runOnMainSync(() -> {
            int[] location = new int[2];
            target.getLocationOnScreen(location);
            points[0] = location[0] + target.getWidth() / 2f;
            points[1] = location[1] + target.getHeight() * (up ? 0.90f : 0.10f);
            points[2] = points[0];
            points[3] = location[1] + target.getHeight() * (up ? 0.10f : 0.90f);
        });
        long downTime = SystemClock.uptimeMillis();
        injectPointer(instrumentation, downTime, MotionEvent.ACTION_DOWN, points[0], points[1]);
        for (int step = 1; step <= 6; step++) {
            float fraction = step / 6f;
            float y = points[1] + (points[3] - points[1]) * fraction;
            injectPointer(instrumentation, downTime, MotionEvent.ACTION_MOVE, points[0], y);
            SystemClock.sleep(12L);
        }
        injectPointer(instrumentation, downTime, MotionEvent.ACTION_UP, points[2], points[3]);
        SystemClock.sleep(100L);
    }

    private static void injectPointer(Instrumentation instrumentation, long downTime,
                                      int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            assertTrue("Could not inject touchscreen event",
                    instrumentation.getUiAutomation().injectInputEvent(event, true));
        } finally {
            event.recycle();
        }
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
