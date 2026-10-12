# راهنمای بیلد (میلانو منیجر)

## مشخصات پروژه

- **زبان:** Java 8 — **بیلد:** Gradle (Android Gradle Plugin 8.x)
- `compileSdk 34` / `minSdk 26` (اندروید ۸ به بالا) / `targetSdk 34`
- تنها وابستگی خارجی: `net.sourceforge.jtds:jtds:1.3.1` (درایور SQL Server)
- پکیج: `ir.meelano.manager` — همه UI کدنویسی‌شده (بدون XML layout)

## بیلد با Android Studio (پیشنهادی)

۱. Android Studio (Iguana یا جدیدتر) + JDK 17 را نصب کنید.
۲. `manager/` را به‌عنوان پروژه باز کنید (Open).
۳. بگذارید Gradle Sync کامل شود (نیاز به اینترنت برای دانلود AGP/jTDS دارد).
۴. `Run ▶` روی گوشی واقعی یا شبیه‌ساز (شبیه‌ساز باید به شبکه سرور دسترسی داشته باشد).

## بیلد خط فرمان

```bash
cd manager
export ANDROID_HOME=/path/to/Android/Sdk   # یا local.properties بسازید: sdk.dir=...
./gradlew assembleDebug        # خروجی: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # نیاز به تنظیم keystore (پایین)
```

## خروجی Release (امضا)

در `app/build.gradle` بلوک `signingConfigs` را با keystore خودتان پر کنید:

```gradle
signingConfigs {
    release {
        storeFile file("../meelano.keystore")
        storePassword "..."
        keyAlias "meelano"
        keyPassword "..."
    }
}
```

سپس `assembleRelease`. برای ساخت keystore:

```bash
keytool -genkeypair -v -keystore meelano.keystore -alias meelano \
  -keyalg RSA -keysize 2048 -validity 10000
```

## ساختار کد

```
app/src/main/java/ir/meelano/manager/
├── MainActivity.java      # پوسته: تب‌ها، هدر، فیلتر، قفل PIN
├── ShareProvider.java     # اشتراک‌گذاری PDF
├── core/                  # Jalali, Money, Sql, Filter, Queries, MoneyQueries,
│                          # MasterQueries, ReportCatalog, AtiranSchema
├── data/                  # Row, Atiran (اتصال), Meta (کشف جدول/ستون),
│                          # Settings, Repo (اجرای پس‌زمینه، فقط SELECT)
├── ui/                    # Theme, Kit, Charts, FilterSheet, MeelanoIcons, Pdf
└── screens/               # ۱۳ بخش: Screen + Home/Trade/Money/Cheques/Products/
                           # Customers/Visitors/Users/Profit/Reports/Settings/More
```

## نکته امنیتی

رشته‌های اتصال پیش‌فرض در `data/Atiran.java` به‌صورت مبهم‌سازی‌شده نگه‌داری می‌شوند؛
کاربر نهایی باید حتماً مشخصات سرور خودش را در تنظیمات اپ وارد کند.
رمزها در `SharedPreferences` خصوصی اپ ذخیره می‌شوند (root‌نشده امن است)؛
برای محیط‌های حساس، رمز را در اپ ذخیره نکنید و هربار دستی وارد کنید.

## امضای نسخه انتشار (v11)

۱. یک‌بار کلید بسازید:
```
keytool -genkeypair -v -keystore meelano-release.jks -alias meelano \
  -keyalg RSA -keysize 2048 -validity 10000
```
۲. فایل `manager/keystore.properties.template` را به `manager/keystore.properties` کپی کنید
(این فایل در گیت نادیده گرفته می‌شود) و مسیر/رمزها را وارد کنید.
۳. بیلد انتشار:
```
cd manager && ./gradlew assembleRelease
```
خروجی امضاشده: `app/build/outputs/apk/release/app-release.apk`
بدون فایل keystore، بیلد release بدون امضا ساخته می‌شود (CI همچنان نسخه debug می‌سازد).
