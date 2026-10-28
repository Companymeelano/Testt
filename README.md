# MEELANO-Android (پخش درخشان) — برنامهٔ نقش‌محور مدیر، ویزیتور و انباردار

برنامه مستقیماً به **SQL Server آتیران** وصل می‌شود. مبنای نسخهٔ نقش‌محور کامل، برنامهٔ حجیم `manager/` با دستیار صوتی آفلاین است؛ حساب آتیران نقش را تعیین می‌کند و هر نقش فقط تب‌ها و بخش‌های مجاز خودش را می‌بیند.

## نسخهٔ نقش‌محور این نوبت: 36.28.0

در نسخهٔ ۳۶٫۲۸٫۰ (کد ۶۰)، ورود ویزیتور در **همان برنامهٔ حدود ۷۰ مگابایتی** فعال شده است؛ مدیر و انباردار هم در همان APK و با دسترسی و صفحه‌های جداگانه باقی می‌مانند. خروجی مستقیم CI: `dist/meelano-manager-v36-debug-10.apk` با شناسهٔ بستهٔ debug `ir.meelano.manager.debug`. workflow [build-manager-v36.yml](.github/workflows/build-manager-v36.yml) تست‌ها، هویت بسته و امضای APK را بررسی و فایل را در `dist/` همین شاخه قرار می‌دهد.

> پروژهٔ ریشهٔ `app/` و APK سبک `ir.meelano.visitor` مسیر ساخت جداگانه‌ای هستند؛ آن‌ها مبنای نسخهٔ نقش‌محور ۷۰ مگابایتی نیستند.

```bash
./gradlew -p manager :app:testDebugUnitTest :app:assembleDebug
```

## چه چیزی در این نسخه تازه است

- **تولید مدیرمحور در APK یکپارچه**: ساخت/ویرایش فرمول بر مبنای بازدهٔ پایه، مصرف واقعی مواد و سربار، برآورد بهای تمام‌شده و ثبت قطعی گردش‌های رسمی تولید آتیران. فقط مدیر اجازهٔ ورود دارد؛ سری جدید حدس زده/ساخته نمی‌شود و سوابق سری‌های قبلی دست‌نخورده می‌ماند.
- **انبار هوشمند** (`warehouse`): تحلیل ABC، ماتریس ABC×گردش، نقطهٔ سفارش/EOQ/ذخیرهٔ اطمینان،
  کالای راکد، کارت گردش کالا با علت واقعی گردش، موجودی به تفکیک انبار از دفتر انبار آتیران
  (`ka_act` ⋈ `anbars`)، و چاپ برچسب A4 با QR + بارکد.
- **اتاق فرمان مالی** (`finance`): سن واقعی مطالبات هر مشتری، اولویت وصول با امتیاز، ریسک اعتباری،
  چک‌های سررسید، قیمت‌گذاری هدف‌محور و جریان نقدی چهار هفته‌ای.
- **به‌روزرسانی امن برنامهٔ یکپارچه**: بررسی خودکار هر ۱۲ ساعت و بررسی دستی از تنظیمات؛ دریافت درون‌برنامه‌ای با کنترل SHA-256، شناسه/نسخهٔ بسته و گواهی امضای شناخته‌شده، سپس تأیید نهایی نصب توسط Android.
- **موتورهای مستقل QR/بارکد** (بدون کتابخانهٔ بیرونی) و **کیت رابط کاربری** تازه
  (`MeelanoUiKit`) با پالت تم به‌صورت شیء (`MeelanoPalette`) و واسط میزبان (`MeelanoUiHost`).

جزئیات: [`docs/RESULT-fa.md`](docs/RESULT-fa.md) (چه چیزی پیاده شد) و
[`docs/TAHLIL-fa.md`](docs/TAHLIL-fa.md) (تحلیل و نقشهٔ راه).

## ساختار پروژه

```
app/
  build.gradle                  پیکربندی ماژول (۶ flavor، package/امضا، وابستگی‌ها)
  proguard-rules.pro
  signing/meelano-install.p12   فایل نصب محلی (CI آن را موقتاً از Test.zip استخراج می‌کند)
  latest.json                   آخرین نسخهٔ منتشرشده
  src/main/java/ir/meelano/android/   کد برنامه (MainActivity + ماژول‌های مشترک)
  src/main/res/                 ۱۴۱ آیکون وکتور، تم‌ها، فونت وزیرمتن
  src/{unified,manager,staff,store,tax}/res/  منابع مخصوص هر نسخه؛ unified از آیکون اصلی ارث می‌برد
  src/test/java/…               تست‌های واحد (MeelanoTaxTest، MeelanoHrTest)
tools/                          ابزارهای بازبینی و تولید جدول (پایتون، بدون وابستگی به اندروید)
docs/                           سند تحلیلی و گزارش پیاده‌سازی
manager/                        پروژهٔ مستقل مدیر v36؛ workflow و آزمون جدا از flavorهای ریشه
gradle/, gradlew, *.gradle      پیکربندی Gradle 8.13 و AGP 8.10.1
```

## ساخت

```bash
mkdir -p app/signing
unzip -p Test.zip Test/signing/meelano-install.p12 > app/signing/meelano-install.p12
cp keystore.properties.example keystore.properties   # مقادیر محلی را تنظیم کنید
./gradlew assembleUnifiedRelease     # برنامهٔ اصلیِ یکپارچه، package ir.meelano.visitor
./gradlew assembleRelease            # همهٔ نسخه‌ها
./gradlew test                       # تست‌های واحد (JUnit)
```

- **Gradle**: ۸.۱۳ (AGP 8.10.1 به ۸.۱۱.۱+ نیاز دارد). اگر `gradle` نصب نباشد، `./gradlew`
  خودش نسخهٔ درست را دانلود می‌کند.
- **JDK**: ۱۷ به بالا. **Android SDK**: `compileSdk 36`، `minSdk 24`.
- بدون `keystore.properties` هم build متوقف نمی‌شود؛ نسخهٔ release با کلید debug امضا می‌شود.

## ابزارهای بازبینی (`tools/`)

| ابزار | کار |
|---|---|
| `verify_all.py` | **بازبین جامع**: یک فرمان، ۲۰ کنترل (نحو، ارجاع‌ها، منابع، آیکون‌ها، اتصال صفحه‌ها، هویت نسخهٔ یکپارچه،
  تعریف تکراری، SQLهای دست‌ساز، QR/بارکد) + خودآزمون کنترل‌ها |
| `java_api_check.py` | بازبینی نحوی همهٔ فایل‌های جاوا + کنترل ارجاع‌های بین‌کلاسی (جانشین سبک `javac` در محیط بدون JDK) |
| `qr_reference_check.py` | مقایسهٔ ماتریس‌های `MeelanoQr` با کتابخانهٔ مرجع (۲۲۴ ماتریس، ۰ اختلاف) |
| `barcode_reference_check.py` | مقایسهٔ بارکد Code128/Code39/EAN-13 با مرجع (۶۸۵ آزمون، ۰ ناموفق) |
| `gen_java_tables.py` | تولید جدول‌های جاوا از منابع مرجع |

برای اجرای بازبینی کامل (نیازمند Python 3 و وابستگی‌های قفل‌شده):

```bash
python3 -m pip install -r requirements-audit.txt
python3 tools/verify_all.py
```
