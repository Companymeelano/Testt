#!/usr/bin/env python3
"""Guardrails for the corrected, signed, all-in-one MEELANO application."""
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
errors = []

def need(condition, description):
    if not condition:
        errors.append(description)

build = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
main = (ROOT / 'app/src/main/java/ir/meelano/android/MainActivity.java').read_text(encoding='utf-8')
base_edition = (ROOT / 'app/src/main/res/values/edition.xml').read_text(encoding='utf-8')
unified_edition_path = ROOT / 'app/src/unified/res/values/edition.xml'
unified_strings_path = ROOT / 'app/src/unified/res/values/strings.xml'
workflow = (ROOT / '.github/workflows/build-apk.yml').read_text(encoding='utf-8')

need("unified {" in build and "applicationId 'ir.meelano.visitor'" in build,
     'Gradle flavor unified باید شناسهٔ اصلی ir.meelano.visitor را نگه دارد')
need("versionCode 202" in build and "versionName '7.0.2'" in build,
     'کد/نام نسخهٔ انتشار باید پس از نسخهٔ نصب‌شده افزایش یابد')
need('meelano_unified_edition">false' in base_edition,
     'مقدار پیش‌فرض edition یکپارچه باید برای flavorهای دیگر خاموش بماند')
need(unified_edition_path.exists(), 'منابع اختصاصی flavor یکپارچه موجود نیست')
if unified_edition_path.exists():
    text = unified_edition_path.read_text(encoding='utf-8')
    need('meelano_unified_edition">true' in text and 'meelano_manager_edition">true' in text,
         'flavor یکپارچه باید هم پرچم دسترسی جامع و هم صفحهٔ مدیریت را روشن کند')
need(unified_strings_path.exists(), 'برچسب اصلی برنامهٔ یکپارچه موجود نیست')
if unified_strings_path.exists():
    text = unified_strings_path.read_text(encoding='utf-8')
    need('پخش درخشان ویزیتور' in text, 'نام نمایشی برنامهٔ اصلی حفظ نشده است')
need((ROOT / 'app/src/main/res/mipmap-mdpi/ic_launcher.png').exists(),
     'آیکون اصلی حرف طلایی D در منابع پایه پیدا نشد')
if (ROOT / 'app/src/unified/res').exists():
    overridden_icons = list((ROOT / 'app/src/unified/res').glob('**/ic_launcher*'))
    need(not overridden_icons, 'flavor یکپارچه آیکون متفاوتی را جایگزین آیکون اصلی کرده است')

need('private boolean UNIFIED_EDITION' in main and 'R.bool.meelano_unified_edition' in main,
     'پرچم برنامهٔ یکپارچه در MainActivity خوانده نمی‌شود')
production = ROOT / 'app/src/main/java/ir/meelano/android/ProductionActivity.java'
production_manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
need(production.exists(), 'صفحهٔ تولید در سورس اصلی APK یکپارچه موجود نیست')
need('android:name=".ProductionActivity" android:exported="false"' in production_manifest,
     'صفحهٔ تولید باید در APK یکپارچه باشد اما از برنامه‌های دیگر قابل فراخوانی نباشد')
need('private void openManagerProduction()' in main and 'ProductionActivity.class' in main and 'isFullAccessUser()' in main,
     'ورود به تولید باید فقط از مسیر مدیرِ برنامهٔ یکپارچه انجام شود')
need('private boolean unifiedEditionPermissionAllowed(String key)' in main,
     'فهرست مسیرهای مجاز برنامهٔ یکپارچه وجود ندارد')
need('if (UNIFIED_EDITION)' in main and 'currentPermissionSet().contains(k)' in main,
     'دسترسی‌های حساب در نسخهٔ یکپارچه اعمال نمی‌شود')
need('if (visitorEditionPermissionAllowed(k)) return true;' in main,
     'سطح دسترسی فروش/ویزیتِ برنامهٔ اصلی به‌اشتباه حذف شده است')
need('if ("dashboard".equals(k) && !isFullAccessUser()) return false;' in main,
     'داشبورد مدیریتی در نسخهٔ یکپارچه برای حساب‌های غیرمدیر محدود نشده است')
need('MANAGER_EDITION && !UNIFIED_EDITION && !profile.explicit' in main,
     'قاعدهٔ provisional مدیر از flavor جداگانه به نسخهٔ لایسنس‌دار نشت کرده است')
need('MANAGER_EDITION && !UNIFIED_EDITION && ("visitor_dashboard"' in main,
     'مسیرهای فروش/ویزیت در برنامهٔ یکپارچه به خانهٔ مدیر بازنویسی می‌شوند')
need('"فروش و ویزیت"' in main and '"امور سازمانی و ابزارها"' in main,
     'خانهٔ «بیشتر» نسخهٔ یکپارچه لینک‌های فروش و ماژول‌های سازمانی را ندارد')
updater = (ROOT / 'app/src/main/java/ir/meelano/android/MeelanoAppUpdater.java').read_text(encoding='utf-8')
manifest = (ROOT / 'app/src/main/java/ir/meelano/android/MeelanoUpdateManifest.java').read_text(encoding='utf-8')
need('MeelanoAppUpdater.check(this, manual' in main and 'MeelanoAppUpdater.pending(this)' in main,
     'برنامهٔ یکپارچه به بررسی و وضعیت به‌روزرسانی متصل نیست')
need('raw.githubusercontent.com/Companymeelano/Testt/arena/29e01a09-testt/dist/latest-unified.json' in manifest,
     'فید به‌روزرسانی باید از همان شاخهٔ انتشار نسخهٔ یکپارچه خوانده شود')
need('MessageDigest.getInstance("SHA-256")' in updater and 'getPackageArchiveInfo' in updater,
     'تمامیت فایل APK و مشخصات بسته پیش از نصب بررسی نمی‌شود')
need('signerDigests' in updater and 'RELEASE_SIGNER_SHA256' in updater,
     'گواهی امضای APK با گواهی نصب فعلی/مورد انتظار تطبیق داده نمی‌شود')
need('ACTION_MANAGE_UNKNOWN_APP_SOURCES' in updater and 'ACTION_VIEW' in updater,
     'جریان مجوز نصب و تحویل امن APK به Android وجود ندارد')
need('REQUEST_INSTALL_PACKAGES' in (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8'),
     'مجوز درخواست نصب APK در Manifest تعریف نشده است')
need((ROOT / 'app/src/test/java/ir/meelano/android/MeelanoUpdateManifestTest.java').exists(),
     'تست‌های واحد اعتبارسنجی فید به‌روزرسانی موجود نیست')

need('assembleUnifiedRelease' in workflow, 'CI نسخهٔ Release برنامهٔ اصلی را نمی‌سازد')
need('unzip -p Test.zip Test/signing/meelano-install.p12' in workflow,
     'CI گواهی کلید نصب کاربر را از بستهٔ منبع نمی‌خواند')
need('MEELANO_INSTALL_PASSWORD' in workflow and 'secrets.MEELANO_INSTALL_PASSWORD' in workflow,
     'گذرواژهٔ کلید فقط از Secret امن Actions خوانده نمی‌شود')
need("ir.meelano.visitor' versionCode='202' versionName='7.0.2'" in workflow,
     'CI هویت package/version فایل نهایی را کنترل نمی‌کند')
need('apksigner" verify --verbose --print-certs' in workflow,
     'CI امضای APK تحویلی را راستی‌آزمایی نمی‌کند')
need('1a2ed0dd70759d6e10c77a4641eeeb8f15972b76d99af351470216955b46d616' in workflow,
     'CI اثرانگشت گواهی نسخهٔ نصب‌شده را با خروجی جدید تطبیق نمی‌دهد')
need('MEELANO-v7.0.2-release.apk' in workflow and 'signerSha256' in workflow and 'latest-unified.json' in workflow,
     'CI فایل APK و فیدِ دارای digest و گواهی امضای آن را منتشر نمی‌کند')

# The uploaded project archive is the authority for the user's original installation key.
archive = ROOT / 'Test.zip'
need(archive.exists(), 'بستهٔ منبع کاربر (Test.zip) موجود نیست')
if archive.exists():
    try:
        with zipfile.ZipFile(archive) as z:
            names = set(z.namelist())
        need('Test/signing/meelano-install.p12' in names,
             'کلید نصب در مسیر مورد انتظار داخل Test.zip پیدا نشد')
        need('Test/src/main/res/drawable-nodpi/ic_launcher_art.png' in names,
             'منابع آیکون اصلی در بستهٔ کاربر موجود نیست')
    except Exception as e:
        errors.append('Test.zip خوانده نشد: %s' % e)

if errors:
    for item in errors:
        print('🔴 ' + item)
    print('unified app check: %d ایراد' % len(errors))
    sys.exit(1)
print('unified app check: 0 ایراد — شناسه/آیکون/مجوزها/امضای نسخهٔ یکپارچه محافظت می‌شوند')
