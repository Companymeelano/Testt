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
import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.ui.DesignTokens;
import ir.meelano.manager.ui.Theme;

import java.io.File;
import java.io.FileInputStream;
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

    @Test public void emptyCashPositionOffersDirectOpeningBalanceAction() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Activity activity = startPreview(target, "light", 1, false);
        try {
            View action = findTaggedView(activity.getWindow().getDecorView(),
                    "home-cash-position-action");
            assertNotNull("Empty cash position must link directly to opening-balance entry", action);
            assertTrue("Opening-balance action is not clickable and accessible",
                    action.isClickable() && action.isFocusable());
            assertTrue("Opening-balance CTA is not explicit",
                    action instanceof TextView
                            && ((TextView) action).getText().toString().contains("ماندهٔ آغاز"));
            assertTrue("Opening-balance CTA has no local-storage explanation",
                    action.getContentDescription() != null
                            && action.getContentDescription().toString().contains("فقط روی این دستگاه"));
        } finally {
            finishPreview(activity);
        }
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
        assertValidPng(output);
        finishPreview(activity);
    }

    private static void assertValidPng(File file) throws Exception {
        assertTrue("Screenshot PNG is missing or truncated: " + file,
                file.isFile() && file.length() >= 4096);
        byte[] header = new byte[24];
        int read = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (read < header.length) {
                int count = input.read(header, read, header.length - read);
                if (count < 0) break;
                read += count;
            }
        }
        assertEquals("PNG header is truncated", header.length, read);
        assertEquals("PNG signature byte 1", 0x89, header[0] & 0xff);
        assertEquals("PNG signature byte 2", 'P', header[1]);
        assertEquals("PNG signature byte 3", 'N', header[2]);
        assertEquals("PNG signature byte 4", 'G', header[3]);
        assertEquals("PNG IHDR marker", 'I', header[12]);
        assertEquals("PNG IHDR marker", 'H', header[13]);
        assertEquals("PNG IHDR marker", 'D', header[14]);
        assertEquals("PNG IHDR marker", 'R', header[15]);
        int width = ((header[16] & 0xff) << 24) | ((header[17] & 0xff) << 16)
                | ((header[18] & 0xff) << 8) | (header[19] & 0xff);
        int height = ((header[20] & 0xff) << 24) | ((header[21] & 0xff) << 16)
                | ((header[22] & 0xff) << 8) | (header[23] & 0xff);
        assertTrue("Screenshot dimensions are too small: " + width + "x" + height,
                width >= 320 && height >= 480);
    }

    /** Exercise the preserved Nuts identity with the app's largest text setting. */
    private void verifyLargeFontNuts(Context target) throws Exception {
        Activity activity = startPreview(target, "nuts", 3);
        try {
            assertTrue("Nuts theme did not remain active", Theme.isNuts());
            assertEquals("Nuts theme must use the shared flat brand mark", R.drawable.mi_diamond, Theme.logoRes());
            assertEquals("Nuts theme gold accent was not preserved", DesignTokens.GOLD, Theme.GOLD);
            assertEquals("Largest text setting was not applied", 1.3f, Theme.fontScale(), 0.001f);
            verifyShellInteractions(activity);
        } finally {
            finishPreview(activity);
        }
    }

    private Activity startPreview(Context target, String theme, int zoomIndex) throws Exception {
        return startPreview(target, theme, zoomIndex, true);
    }

    private Activity startPreview(Context target, String theme, int zoomIndex,
                                  boolean seedTillBalances) throws Exception {
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
        if (seedTillBalances) {
            settings.setCloseoutCashOpening(Jalali.todayStr(), "1000000000");
            settings.setCloseoutCashCounted(Jalali.todayStr(), "1100000000");
        }
        settings.saveConnection("127.0.0.1", "1", "HOME_PREVIEW", "preview", "preview");
        settings.saveHomeCache(sampleCache());
        verifyLocalBankStatementStore(settings, Jalali.todayStr());

        LicenseStore.Status license = LicenseStore.activate(target,
                License.generate(DeviceId.code(target), License.P_MONTHLY, 0));
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
            if (visibleText[0].contains("شاخص‌های روزانه")) break;
            SystemClock.sleep(250L);
        }
        assertTrue("Home dashboard was not visible; current UI text: " + visibleText[0],
                visibleText[0].contains("شاخص‌های روزانه"));
        assertFalse("Preview screenshot contains a live connection error",
                visibleText[0].contains("جزئیات اتصال"));
    }

    private static void verifyDashboardPolish(Activity activity) {
        assertEquals("Financial artwork should remain a flat, tinted vector",
                R.drawable.mi_account_balance_wallet, Theme.icon3d(R.drawable.mi_account_balance_wallet));
        assertEquals("Return artwork should remain a flat, tinted vector",
                R.drawable.mi_assignment_return, Theme.icon3d(R.drawable.mi_assignment_return));
        assertFalse("The new visual system should not substitute 3D artwork",
                Theme.isIcon3d(Theme.icon3d(R.drawable.mi_account_balance_wallet)));
        assertEquals("Expanded 30-day chip leaves too little title room on a narrow phone",
                192, MainActivity.headerTitleWidthDp(320, 4, true, false));
        assertEquals("Collapsed header controls are not included in the narrow-width budget",
                140, MainActivity.headerTitleWidthDp(320, 4, true, true));
        verifyDashboardColorContrast();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final View[] targets = new View[30];
        final int[] facts = new int[9];
        final int[] firstKpiRowHeights = new int[2];
        final String[] reconciliationCopy = {""};
        final String[] dailyKpiCopy = {""};
        final String[] returnKpiCopy = {""};
        final String[] salesChartCopy = {""};
        final String[] followUpCopy = {""};
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
                targets[8] = findTaggedView(decor, "home-financial-reconciliation");
                targets[9] = findTaggedView(decor, "home-bank-statement-entry");
                targets[10] = findTaggedView(decor, "home-financial-overview");
                targets[11] = findTaggedView(decor, "home-financial-exceptions");
                targets[12] = findTaggedView(decor, "home-bank-reconciliation");
                targets[13] = findTaggedView(decor, "home-bank-statement-import");
                targets[14] = findTaggedView(decor, "home-cash-control-card");
                targets[15] = findTaggedView(decor, "home-financial-returns");
                targets[16] = findTaggedView(decor, "home-financial-detail-panels");
                targets[17] = findTaggedView(decor, "home-financial-details-toggle");
                targets[18] = findTaggedView(decor, "home-group-overview");
                targets[19] = findTaggedView(decor, "home-group-management-1");
                targets[20] = findTaggedView(decor, "home-trend-status-فاکتور ابطالی");
                targets[21] = findTaggedView(decor, "home-trend-فاکتور ابطالی");
                targets[22] = findTaggedView(decor, "home-category-nav");
                targets[23] = findTaggedView(decor, "home-category-chip-overview");
                targets[24] = findTaggedView(decor, "home-category-chip-management");
                targets[25] = findTaggedView(decor, "home-returns-kpis");
                targets[26] = findTaggedView(decor, "home-sales-chart-hint");
                targets[27] = findTaggedView(decor, "home-smart-followup-overdue");
                targets[28] = findTaggedView(decor, "home-cash-position-action");
                targets[29] = findTaggedView(decor, "home-closeout-cash-entry");
                reconciliationCopy[0] = "";
                dailyKpiCopy[0] = "";
                returnKpiCopy[0] = "";
                salesChartCopy[0] = "";
                followUpCopy[0] = "";
                if (targets[8] != null) collectText(targets[8], reconciliationCopy);
                if (targets[0] != null) collectText(targets[0], dailyKpiCopy);
                if (targets[25] != null) collectText(targets[25], returnKpiCopy);
                if (targets[26] != null) collectText(targets[26], salesChartCopy);
                if (targets[27] != null) collectText(targets[27], followUpCopy);
                facts[0] = targets[0] == null ? 0 : countTaggedViews(targets[0], "home-financial-kpi");
                facts[5] = targets[25] == null ? 0
                        : countTaggedViews(targets[25], "home-financial-kpi");
                firstKpiRowHeights[0] = 0;
                firstKpiRowHeights[1] = 0;
                View kpiGrid = targets[0] == null ? null : findTaggedView(targets[0], "home-kpi-grid");
                if (kpiGrid instanceof ViewGroup && ((ViewGroup) kpiGrid).getChildCount() > 0) {
                    View firstRow = ((ViewGroup) kpiGrid).getChildAt(0);
                    if (firstRow instanceof ViewGroup) {
                        ViewGroup row = (ViewGroup) firstRow;
                        int captured = 0;
                        for (int i = 0; i < row.getChildCount() && captured < 2; i++) {
                            View child = row.getChildAt(i);
                            if ("home-financial-kpi".equals(child.getTag()))
                                firstKpiRowHeights[captured++] = child.getHeight();
                        }
                    }
                }
                facts[1] = targets[0] != null
                        && containsViewType(targets[0], HorizontalScrollView.class) ? 1 : 0;
                facts[2] = targets[1] == null ? 0 : targets[1].getWidth();
                facts[8] = targets[1] == null ? 0 : targets[1].getHeight();
                facts[3] = targets[5] == null ? 0 : targets[5].getHeight();
                Rect visibleLicense = new Rect();
                facts[4] = targets[6] != null && targets[6].getGlobalVisibleRect(visibleLicense)
                        && visibleLicense.width() == targets[6].getWidth() ? 1 : 0;
                facts[6] = targets[2] != null
                        && taggedViewsShareHeight(targets[2], "home-cash-position-metric", 2) ? 1 : 0;
                facts[7] = targets[3] != null
                        && taggedViewsShareHeight(targets[3], "home-closeout-metric", 2) ? 1 : 0;
            });
            if (facts[2] > 0 && facts[3] > 0 && facts[4] > 0 && facts[5] > 0
                    && facts[6] > 0 && facts[7] > 0 && facts[8] > 0
                    && targets[26] != null && targets[27] != null
                    && targets[28] != null && targets[29] != null) break;
            SystemClock.sleep(40L);
        } while (SystemClock.uptimeMillis() < layoutDeadline);
        assertNotNull("Financial KPI section is missing", targets[0]);
        assertEquals("Financial KPIs still use a clipping horizontal carousel", 0, facts[1]);
        assertEquals("Daily KPIs must remain separate from the 30-day returns KPI", 3, facts[0]);
        assertNotNull("The separate 30-day returns KPI section is missing", targets[25]);
        assertEquals("The returns section should contain its own single KPI", 1, facts[5]);
        assertTrue("Daily KPI period and comparison basis are not readable",
                dailyKpiCopy[0].contains("بازه:") && dailyKpiCopy[0].contains("روز قبل"));
        assertTrue("The returns KPI does not expose both labeled 30-day date ranges",
                returnKpiCopy[0].contains("اسناد برگشتی")
                        && returnKpiCopy[0].contains("بازهٔ جاری (۳۰ روز):")
                        && returnKpiCopy[0].contains("بازهٔ قبل (۳۰ روز):"));
        assertNotNull("Financial metric tiles were not rendered", targets[1]);
        assertTrue("Financial metric tile is too narrow for its value",
                facts[2] >= 120f * activityDensity(targets[1]));
        assertTrue("Financial KPI tile height is not on the common dashboard size",
                facts[8] >= 180f * activityDensity(targets[1]));
        assertTrue("First-row KPI cards are not aligned to a common height",
                firstKpiRowHeights[0] > 0
                        && firstKpiRowHeights[0] == firstKpiRowHeights[1]);
        assertNotNull("Cash-position module is missing", targets[2]);
        assertNotNull("Closeout module is missing", targets[3]);
        assertEquals("Cash-position metric cards are not equal height", 1, facts[6]);
        assertEquals("Closeout metric cards are not equal height", 1, facts[7]);
        assertNotNull("Smart-report share action is missing", targets[4]);
        assertTrue("Smart-report share action is not clickable", targets[4].isClickable());
        assertNotNull("Cash-position closeout action is missing", targets[28]);
        assertNotNull("Till registration action is missing from closeout", targets[29]);
        assertTrue("Cash-position CTA should clearly lead to closing the day",
                targets[28] instanceof TextView
                        && ((TextView) targets[28]).getText().toString().contains("بستن روز"));
        assertTrue("Closeout CTA should clearly lead to cash entry",
                targets[29] instanceof TextView
                        && ((TextView) targets[29]).getText().toString().contains("ثبت صندوق"));
        assertTrue("Closeout cash-entry CTA is not accessible or is too small",
                targets[29].isClickable() && targets[29].isFocusable()
                        && targets[29].getMinimumHeight() >= 48f * activityDensity(targets[29]));
        assertTrue("Smart-report share action is not accessible", targets[4].isFocusable());
        assertNotNull("Home has no clearance below the last card", targets[5]);
        assertTrue("Home's bottom clearance is smaller than 16dp",
                facts[3] >= 16f * activityDensity(targets[5]));
        assertNotNull("Expanded header licence chip is missing", targets[6]);
        assertTrue("Licence chip is not a text control", targets[6] instanceof TextView);
        TextView licence = (TextView) targets[6];
        assertTrue("The 30-day license label was not preserved in the home header",
                licence.getText().toString().contains("۳۰ روز"));
        assertTrue("Licence chip is clipped by the viewport/header bounds", facts[4] == 1);
        assertTrue("Licence chip is below the 48dp touch target",
                licence.getWidth() >= 48f * activityDensity(licence));
        assertTrue("Licence chip lost its management action", licence.isClickable());
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
        assertNotNull("Daily financial reconciliation module is missing", targets[8]);
        assertTrue("Financial reconciliation title was lost", reconciliationCopy[0].contains("مغایرت‌گیری مالی"));
        assertTrue("Bank statement limitation was not disclosed", reconciliationCopy[0].contains("صورت‌حساب مستقل بانکی"));
        assertNotNull("Per-bank actual-statement entry is missing", targets[9]);
        assertTrue("Per-bank statement entry is not clickable", targets[9].isClickable());
        assertTrue("Per-bank statement entry is not keyboard/accessibility focusable", targets[9].isFocusable());
        assertTrue("Per-bank statement entry is below the 48dp touch target",
                targets[9].getMinimumHeight() >= 48f * activityDensity(targets[9]));
        assertTrue("Per-bank statement entry has no accessible explanation",
                targets[9].getContentDescription() != null
                        && targets[9].getContentDescription().toString().contains("ویرایش صورت‌حساب"));
        assertNotNull("Executive financial overview is missing", targets[10]);
        assertNotNull("Separate manager exception panel is missing", targets[11]);
        assertNotNull("Separate bank reconciliation panel is missing", targets[12]);
        assertNotNull("Manual statement import action is missing", targets[13]);
        assertTrue("Statement import action is not clickable and accessible",
                targets[13].isClickable() && targets[13].isFocusable());
        assertTrue("Statement import action is below the 48dp touch target",
                targets[13].getMinimumHeight() >= 48f * activityDensity(targets[13]));
        assertNotNull("Explicit till-count control panel is missing", targets[14]);
        assertNotNull("Reported returns panel is missing", targets[15]);
        String[] tillCopy = {""};
        instrumentation.runOnMainSync(() -> collectText(targets[14], tillCopy));
        assertTrue("Till-count formula or source was lost",
                tillCopy[0].contains("ماندهٔ آغاز + دریافت نقد")
                        && (tillCopy[0].contains("فقط روی این دستگاه")
                        || tillCopy[0].contains("محلی روی همین دستگاه")));
        assertNotNull("Overview category heading is missing", targets[18]);
        assertNotNull("Management and finance category heading is missing", targets[19]);
        assertNotNull("Dashboard category navigation is missing", targets[22]);
        assertTrue("Category navigation should be horizontally scrollable",
                targets[22] instanceof HorizontalScrollView);
        assertNotNull("Overview shortcut is missing", targets[23]);
        assertNotNull("Management shortcut is missing", targets[24]);
        assertTrue("Management shortcut is not accessible and clickable",
                targets[24].isClickable() && targets[24].isFocusable()
                        && targets[24].getContentDescription() != null);
        assertTrue("Management shortcut is below the 48dp touch target",
                targets[24].getHeight() >= 48f * activityDensity(targets[24]));
        assertTrue("A voided-invoice trend with no data should be omitted", targets[20] == null);
        assertTrue("A voided-invoice trend with no data should not allocate a chart", targets[21] == null);
        assertNotNull("Sales chart peak summary is missing", targets[26]);
        assertTrue("Outlier peak does not show its actual selected-unit value",
                salesChartCopy[0].contains("قلهٔ پرت")
                        && salesChartCopy[0].contains(HomeNumberFormatter.money(34_000_000_000d)));
        assertNotNull("Overdue follow-up is not displayed in its own row", targets[27]);
        assertTrue("Follow-up priority is not attached to the specific overdue row",
                followUpCopy[0].contains("فاکتورهای معوق")
                        && followUpCopy[0].contains("نیازمند پیگیری"));
        assertNotNull("Financial detail disclosure is missing", targets[17]);
        assertNotNull("Collapsible financial detail container is missing", targets[16]);
        assertEquals("Detailed reconciliation panels should start compact", View.GONE,
                targets[16].getVisibility());
        assertTrue("Financial detail disclosure is not accessible", targets[17].isClickable()
                && targets[17].isFocusable());
        final boolean[] expanded = {false};
        instrumentation.runOnMainSync(() -> expanded[0] = targets[17].performClick());
        assertTrue("Financial detail disclosure did not respond", expanded[0]);
        assertEquals("Financial details did not expand", View.VISIBLE, targets[16].getVisibility());
        instrumentation.runOnMainSync(() -> expanded[0] = targets[17].performClick());
        assertTrue("Financial detail disclosure could not be collapsed", expanded[0]);
        assertEquals("Financial details remained open after collapsing", View.GONE,
                targets[16].getVisibility());
    }

    private static void verifyDashboardColorContrast() {
        int[] accents = {DesignTokens.SALES, DesignTokens.PURCHASE, DesignTokens.RECEIVE,
                DesignTokens.PAY, DesignTokens.RETURNS, DesignTokens.ALERT,
                DesignTokens.MARKET, DesignTokens.WAREHOUSE, DesignTokens.CLOSEOUT,
                DesignTokens.CASH_POSITION, DesignTokens.TOP_PERFORMERS,
                DesignTokens.SALES_GOAL, DesignTokens.GROSS_PROFIT,
                DesignTokens.SEASONAL_CALENDAR, DesignTokens.QUICK_ACTIONS,
                DesignTokens.SALES_FORECAST, Theme.SUCCESS, Theme.DANGER, Theme.WARNING};
        boolean light = Theme.isLight();
        float badgeTopTint = light ? 0.23f : 0.42f;
        float badgeBottomTint = light ? 0.12f : 0.27f;
        float metricTopTint = Theme.dashboardMetricTopTint();
        float metricBottomTint = Theme.dashboardMetricBottomTint();
        float cardTopTint = Theme.dashboardCardTopTint();
        float cardBottomTint = Theme.dashboardCardBottomTint();
        int welcomeRoyal = DesignTokens.mix(DesignTokens.EMERALD, Theme.PRIMARY,
                Theme.isNuts() ? 0.12f : 0.08f);
        int[] welcomeSurfaces = {welcomeRoyal, DesignTokens.EMERALD, DesignTokens.EMERALD_DEEP};
        for (int surface : welcomeSurfaces) {
            assertTrue("Welcome title contrast on emerald hero",
                    DesignTokens.contrastRatio(Theme.dashboardWelcomeText(), surface) >= 4.8);
            assertTrue("Welcome secondary-text contrast on emerald hero",
                    DesignTokens.contrastRatio(Theme.dashboardWelcomeMuted(), surface) >= 4.8);
        }
        for (int accent : accents) {
            int badgeTop = DesignTokens.mix(Theme.SURFACE, accent, badgeTopTint);
            int badgeBottom = DesignTokens.mix(Theme.SURFACE, accent, badgeBottomTint);
            int iconInk = Theme.dashboardAccentInk(accent);
            assertTrue("Dashboard icon contrast on top badge for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(iconInk, badgeTop) >= 4.8);
            assertTrue("Dashboard icon contrast on lower badge for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(iconInk, badgeBottom) >= 4.8);

            int metricTop = DesignTokens.mix(Theme.SURFACE, accent, metricTopTint);
            int metricBottom = DesignTokens.mix(Theme.SURFACE, accent, metricBottomTint);
            int mutedInk = Theme.dashboardMutedInk(accent);
            assertTrue("Dashboard metric label contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(mutedInk, metricTop) >= 4.8);
            assertTrue("Dashboard metric label contrast across gradient for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(mutedInk, metricBottom) >= 4.8);
            int categoryInk = Theme.dashboardAccentInk(accent);
            assertTrue("Dashboard category shortcut contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(categoryInk, metricTop) >= 4.8);
            assertTrue("Dashboard category shortcut contrast across gradient for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(categoryInk, metricBottom) >= 4.8);

            int cardTop = DesignTokens.mix(Theme.SURFACE, accent, cardTopTint);
            int cardBottom = DesignTokens.mix(Theme.SURFACE, accent, cardBottomTint);
            int cardMuted = Theme.dashboardCardMutedInk(accent);
            assertTrue("Dashboard card subtitle contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(cardMuted, cardTop) >= 4.8);
            assertTrue("Dashboard card subtitle contrast across gradient for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(cardMuted, cardBottom) >= 4.8);

            int actionInk = Theme.dashboardActionInk(accent);
            int safeShade = actionInk == 0xFF000000 ? 0xFFFFFFFF : 0xFF000000;
            int actionEnd = DesignTokens.mix(accent, safeShade, 0.035f);
            assertTrue("Dashboard action text contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(actionInk, accent) >= 4.8);
            assertTrue("Dashboard action text contrast across gradient for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(actionInk, actionEnd) >= 4.8);
        }
    }

    private static void verifySalesChartRangeSwitch(Activity activity) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final View[] chartAndSelector = new View[2];
        final String[] initialDescription = {""};
        instrumentation.runOnMainSync(() -> {
            View decor = activity.getWindow().getDecorView();
            chartAndSelector[0] = findTaggedView(decor, "home-sales-chart");
            chartAndSelector[1] = findTaggedView(decor, "sales-chart-range-selector");
            if (chartAndSelector[0] != null)
                initialDescription[0] = normalizedAccessibleText(chartAndSelector[0].getContentDescription());
        });
        assertNotNull("Live sales chart is missing", chartAndSelector[0]);
        assertNotNull("Sales chart range selector is missing", chartAndSelector[1]);
        assertTrue("Initial chart window was not identified: " + initialDescription[0],
                initialDescription[0].contains("۱۴ روز"));
        assertTrue("Sales chart selector must offer a focused and full-history range",
                chartAndSelector[1] instanceof ViewGroup
                        && ((ViewGroup) chartAndSelector[1]).getChildCount() >= 2);

        ViewGroup initialSelector = (ViewGroup) chartAndSelector[1];
        View weekSegment = initialSelector.getChildAt(0);
        final boolean[] weekClicked = {false};
        instrumentation.runOnMainSync(() -> weekClicked[0] = weekSegment.performClick());
        assertTrue("Seven-day sales chart option is not clickable", weekClicked[0]);
        String[] weeklyDescription = {""};
        instrumentation.runOnMainSync(() ->
                weeklyDescription[0] = normalizedAccessibleText(chartAndSelector[0].getContentDescription()));
        assertTrue("Selecting seven days did not update the chart window to real daily data",
                weeklyDescription[0].contains("۷ روز تا"));

        final View[] refreshedSelector = {null};
        final View[] fortnightSegment = {null};
        instrumentation.runOnMainSync(() -> {
            refreshedSelector[0] = findTaggedView(activity.getWindow().getDecorView(),
                    "sales-chart-range-selector");
            if (refreshedSelector[0] instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) refreshedSelector[0];
                for (int i = 0; i < group.getChildCount(); i++) {
                    CharSequence label = group.getChildAt(i).getContentDescription();
                    if (label != null && "همه".contentEquals(label)) {
                        fortnightSegment[0] = group.getChildAt(i);
                        break;
                    }
                }
            }
        });
        assertNotNull("Updated sales range selector was not rendered", refreshedSelector[0]);
        assertNotNull("Full-history sales chart option disappeared after range change", fortnightSegment[0]);
        final boolean[] fortnightClicked = {false};
        instrumentation.runOnMainSync(() -> fortnightClicked[0] = fortnightSegment[0].performClick());
        assertTrue("Full-history sales chart option is not clickable", fortnightClicked[0]);
        String[] restoredDescription = {""};
        instrumentation.runOnMainSync(() ->
                restoredDescription[0] = normalizedAccessibleText(chartAndSelector[0].getContentDescription()));
        assertTrue("Sales chart did not return to the available full-history window",
                restoredDescription[0].contains("۱۴ روز"));
    }

    /** Remove bidi isolation controls before matching human-readable accessibility text. */
    private static String normalizedAccessibleText(CharSequence text) {
        if (text == null) return "";
        StringBuilder normalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u061C' || ch == '\u200E' || ch == '\u200F'
                    || (ch >= '\u202A' && ch <= '\u202E')
                    || (ch >= '\u2066' && ch <= '\u2069')) continue;
            normalized.append(ch);
        }
        return normalized.toString();
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

    private static boolean taggedViewsShareHeight(View view, String tag, int minimumCount) {
        int[] heights = {-1, 0};
        collectTaggedHeights(view, tag, heights);
        return heights[1] >= minimumCount && heights[0] > 0;
    }

    private static void collectTaggedHeights(View view, String tag, int[] state) {
        if (view == null) return;
        if (tag.equals(view.getTag())) {
            if (state[0] == -1) state[0] = view.getHeight();
            else if (state[0] != -2 && state[0] != view.getHeight()) state[0] = -2;
            state[1]++;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                collectTaggedHeights(group.getChildAt(i), tag, state);
        }
    }

    private static void finishPreview(Activity activity) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        // Let the window and its final theme/scroll animations settle before the next seeded launch.
        SystemClock.sleep(700L);
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
        verifySalesChartRangeSwitch(activity);

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

    private static void verifyLocalBankStatementStore(Settings settings, String day) {
        assertTrue("Valid statement totals were not stored locally",
                settings.setHomeBankStatementRials(day, "preview/bank-10", "۰", "۱۲٫۵"));
        assertEquals("Explicit zero was not preserved", "0",
                settings.homeBankStatementInRials(day, "preview/bank-10"));
        assertEquals("Persian decimal digits were not normalized", "12.5",
                settings.homeBankStatementOutRials(day, "preview/bank-10"));
        assertFalse("A missing statement direction must not be saved as zero",
                settings.setHomeBankStatementRials(day, "preview/bank-10", "", "0"));
        assertFalse("Negative statement totals must not be saved",
                settings.setHomeBankStatementRials(day, "preview/bank-10", "-1", "0"));
        settings.clearHomeBankStatement(day, "preview/bank-10");
        assertEquals("Cleared statement input remains in local settings", "",
                settings.homeBankStatementInRials(day, "preview/bank-10"));
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
                .append("|retodayok=1|cashinok=1|cashoutok=1|gpok=1|gpqok=1")
                .append("|bankaccountsok=1|bankturnok=1|overpaidvok=1|unbalancedjok=1")
                .append("|bankacct0=id=10;name=بانک نمونه")
                .append("|bankacct1=id=11;name=بانک دوم")
                .append("|bankturn0=bank=بانک نمونه;in=3500000000;out=500000000;count=6;bankId=10")
                .append("|bankturn1=bank=بانک دوم;in=900000000;out=120000000;count=2;bankId=11")
                .append("|top0=label=پستهٔ نمونه;total=2400000000;qty=18;docs=7")
                .append("|overdue0=party=مشتری نمونه;amount=730000000;dueDate=").append(today)
                .append(";days=2;invoice=۴۸۲۱");

        for (int i = 0; i < 14; i++) {
            String day = Jalali.addDays(today, i - 13);
            long sales = 3_800_000_000L + (i * 410_000_000L) + ((i % 4) * 130_000_000L);
            if (i == 8) sales = 34_000_000_000L;
            if (i == 13) sales = 9_200_000_000L;
            cache.append("|trend").append(i).append("=day=").append(day)
                    .append(";total=").append(sales).append(";docs=").append(7 + i % 9).append(";invalidRows=0");
        }
        for (int i = 0; i < 7; i++) {
            String day = Jalali.addDays(today, i - 6);
            cache.append("|buytrend").append(i).append("=day=").append(day)
                    .append(";total=").append(2_000_000_000L + i * 320_000_000L).append(";docs=").append(8 + i).append(";invalidRows=0");
            cache.append("|intrend").append(i).append("=day=").append(day)
                    .append(";total=").append(4_500_000_000L + i * 520_000_000L).append(";docs=").append(12 + i).append(";invalidRows=0");
            cache.append("|outtrend").append(i).append("=day=").append(day)
                    .append(";total=").append(1_600_000_000L + i * 180_000_000L).append(";docs=").append(4 + i).append(";invalidRows=0");
        }
        cache.append("|inmix0=label=نقد;amount=3100000000")
                .append("|inmix1=label=کارت‌خوان;amount=2800000000")
                .append("|inmix2=label=حواله;amount=1800000000")
                .append("|inmix3=label=چک‌های دریافتی;amount=700000000")
                .append("|cashin=total=8400000000;cash=3100000000;pos=2800000000;havaleh=1800000000;cheque=700000000;breakdownAvailable=1")
                .append("|cashout=total=2900000000;cash=1600000000;pos=900000000;havaleh=400000000;cheque=0;breakdownAvailable=1")
                .append("|retoday0=label=فاکتور برگشتی;docs=1;total=250000000")
                .append("|gp0=day=").append(today).append(";sales=9200000000;cogs=6100000000;profit=3100000000;qualityIssues=0")
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
