package ir.meelano.manager.screens;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
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

import java.io.File;
import java.io.FileOutputStream;

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
        settings.saveConnection("127.0.0.1", "1", "HOME_PREVIEW", "preview", "preview");
        settings.saveHomeCache(sampleCache());

        LicenseStore.Status license = LicenseStore.activate(target,
                License.generate(DeviceId.code(target), License.P_PERM, 0));
        assertTrue("Could not seed debug preview license: " + license.fa, license.ok);
        LicenseStore.setEnteredOnce(target);
        LicenseStore.setTourPending(target, false);
        AtiranAuth.saveSession(target, 1, "arena-preview", "مدیر نمونه", RoleStore.ADMIN, true, true);

        Intent launch = new Intent(target, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(launch);
        assertNotNull("Manager shell did not start", activity);

        String[] visibleText = {""};
        long deadline = SystemClock.uptimeMillis() + 20_000L;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    collectText(activity.getWindow().getDecorView(), visibleText));
            if (visibleText[0].contains("شاخص‌های مالی")) break;
            SystemClock.sleep(250L);
        }
        assertTrue("Home dashboard was not visible; current UI text: " + visibleText[0],
                visibleText[0].contains("شاخص‌های مالی"));
        // Let the intro transition finish before recording the first viewport.
        SystemClock.sleep(500L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        final Bitmap[] captured = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
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
                .append("|stockok=1|reorderok=1|priceok=1|dueinok=1|dueoutok=1");

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
