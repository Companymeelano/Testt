# MEELANO-Android (پخش درخشان) — نسخهٔ ۷.۰.۰

برنامهٔ اندروید مدیریت، انبار و حسابداری که مستقیم به **SQL Server آتیران** وصل می‌شود.
یک ماژول، پنج نسخه (flavor): ویزیتور (`main`)، مدیر، پرسنل، فروشگاه، مودیان.

## چه چیزی در این نسخه تازه است

- **انبار هوشمند** (`warehouse`): تحلیل ABC، ماتریس ABC×گردش، نقطهٔ سفارش/EOQ/ذخیرهٔ اطمینان،
  کالای راکد، کارت گردش کالا با علت واقعی گردش، موجودی به تفکیک انبار از دفتر انبار آتیران
  (`ka_act` ⋈ `anbars`)، و چاپ برچسب A4 با QR + بارکد.
- **اتاق فرمان مالی** (`finance`): سن واقعی مطالبات هر مشتری، اولویت وصول با امتیاز، ریسک اعتباری،
  چک‌های سررسید، قیمت‌گذاری هدف‌محور و جریان نقدی چهار هفته‌ای.
- **موتورهای مستقل QR/بارکد** (بدون کتابخانهٔ بیرونی) و **کیت رابط کاربری** تازه
  (`MeelanoUiKit`) با پالت تم به‌صورت شیء (`MeelanoPalette`) و واسط میزبان (`MeelanoUiHost`).

جزئیات: [`docs/RESULT-fa.md`](docs/RESULT-fa.md) (چه چیزی پیاده شد) و
[`docs/TAHLIL-fa.md`](docs/TAHLIL-fa.md) (تحلیل و نقشهٔ راه).

## ساختار پروژه

```
app/
  build.gradle                  پیکربندی ماژول (۵ flavor، امضا، وابستگی‌ها)
  proguard-rules.pro
  signing/meelano-install.p12   کلید امضا (رمز در keystore.properties)
  latest.json                   آخرین نسخهٔ منتشرشده
  src/main/java/ir/meelano/android/   کد برنامه (MainActivity + ۳۹ کلاس)
  src/main/res/                 ۱۴۱ آیکون وکتور، تم‌ها، فونت وزیرمتن
  src/{manager,staff,store,tax}/res/  منابع مخصوص هر نسخه
  src/test/java/…               تست‌های واحد (MeelanoTaxTest، MeelanoHrTest)
tools/                          ابزارهای بازبینی و تولید جدول (پایتون، بدون وابستگی به اندروید)
docs/                           سند تحلیلی و گزارش پیاده‌سازی
gradle/, gradlew, *.gradle      پیکربندی Gradle 8.13 و AGP 8.10.1
```

## ساخت

```bash
cp keystore.properties.example keystore.properties   # رمزهای p12 را پر کنید
./gradlew assembleManagerRelease     # نسخهٔ مدیر
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
| `java_api_check.py` | بازبینی نحوی همهٔ فایل‌های جاوا + کنترل ارجاع‌های بین‌کلاسی (جانشین سبک `javac` در محیط بدون JDK) |
| `qr_reference_check.py` | مقایسهٔ ماتریس‌های `MeelanoQr` با کتابخانهٔ مرجع (۲۲۴ ماتریس، ۰ اختلاف) |
| `barcode_reference_check.py` | مقایسهٔ بارکد Code128/Code39/EAN-13 با مرجع (۶۸۵ آزمون، ۰ ناموفق) |
| `gen_java_tables.py` | تولید جدول‌های جاوا از منابع مرجع |
