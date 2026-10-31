# بازبینی خط‌به‌خط کد پروژه — دور پنجم (۱۴۰۵)

> مرجع رسمی گزارش بازبینی امنیتی/کیفی پروژهٔ MEELANO.  
> پیشینه و فهرست نسخه‌ها: `docs/ACCESS_VISITOR_FIX_FA.md`  
> پیشنهادهای تکمیل و نقشهٔ راه (اندروید + ویندوز): `docs/ROADMAP-PRO-fa.md`

---

## ۱. دامنه و روش کار

بازبینی روی کل مخزن انجام شد:

| بخش | حجم | روش |
| --- | --- | --- |
| `manager/app` (اپ اصلی) | ۲۳۷ فایل جاوا + منابع/manifest | اسکن خودکار چندقاعده‌ای + بازبینی دستی فایل‌های حیاتی |
| `manager/admin` + `manager/licensing` | اپ لایسنس/SMS + کدک لایسنس | اسکن + بازبینی دستی |
| `manager/tools` + `tools/` | ۲۲ اسکریپت پایتون (پروب آتیران، ابزار release، کنترل‌های CI) | اسکن + بازبینی دستی ابزارهای اصلی |
| منابع/Manifestها | `network_security_config`، مجوزها، اجزای exported | بازبینی دستی |
| SQL/Schema | `Meta.must` ها در برابر `sample-schema.json` | `tools/audit_manager_schema.py` (۲۸۵ نگاشت) |

اسکن خودکار (اسکریپت اختصاصی این دور، ۱۲ خانواده قاعده) روی **۲۳۷ جاوا + ۲۲ پایتون** اجرا شد و **۸۷۵ یافتهٔ خام** تولید کرد که همگی دستی تریاژ شدند. اسکن چندخطی جداگانه برای `PendingIntent`/`registerReceiver` و الگوهای حلقهٔ خواندن نیز اجرا شد.

**فایل‌هایی که در این دور خط‌به‌خط خوانده شدند:**  
`Settings.java`، `SecretBox.java`، `PinHash` (جدید)، بلوک PIN و احراز هویت `AtiranAuth.java`، `CacheStore.java`، `Backup.java` (بخش آرشیو/پاک‌سازی/trim)، `UpdateCenter.java` (دانلود/sha256/copy)، `Jalali.java` (parseها)، `SmartLink.java`، `Guard.java`، `LanDiscover.java`، `ManagerAlertsScreen.java` (بخش parse)، `Assistant.java` (suggestions)، `PersianNumbers.java` (words)، بخش‌های حیاتی `LicenseActivity.java` (لایسنس/پین/trim)، `Kit.java` (کارخانه‌های متن/دکمه و iconize)، `MeelanoIcons.java` (نقشهٔ گلیف‌ها)، `probe_atiran.py`، Manifest هر دو اپ، `network_security_config.xml`، `manager/tools/make_version.py`.

---

## ۲. یافته‌های واقعی و رفع‌شده (در همین دور)

### ۲.۱ هش کردن PIN — ضعف واقعی ⇒ رفع شد (`32c00f5`)

دو جا PIN ذخیره می‌شد: قفل برنامه (`Settings.checkPin/setPin`) و PIN کاربران (`AtiranAuth.checkPin/setPin`). هر دو از الگوی زیر استفاده می‌کردند:

- **SHA-256 تک‌مرحله‌ای با salt ثابت** (`meelano7:` / `meelano_loginpin:`) ⇒ ساختن rainbow table یک‌بار برای همیشه ممکن بود؛ PIN چهاررقمی عملاً آنلاین/آفلاین شکسته می‌شد.
- **مقایسه با `String.equals`** ⇒ مقایسهٔ non-constant-time.
- **fallback خطرناک**: در نبود digest، متد `"x" + s` برمی‌گرداند؛ یعنی **خود PIN** با پیشوند ذخیره می‌شد!
- **بدون مهاجرت**: تغییر الگوی هش، PINهای موجود را از کار می‌انداخت.

**رفع:** کلاس جدید `core/PinHash.java`:

- `PBKDF2WithHmacSHA256` با **۱۲٬۰۰۰ تکرار** و **salt تصادفی ۱۶ بایتی** per-PIN (فرمت `v2:b64salt:b64key`).
- مقایسهٔ **`MessageDigest.isEqual`** (constant-time).
- **Fail-closed**: خطای هش ⇒ چیزی مشتق‌شده از PIN ذخیره/بازگردانده نمی‌شود.
- **مهاجرت شفاف**: هش‌های قدیمی همچنان verify می‌شوند و در اولین ورود موفق به‌صورت در‌جا به v2 ارتقا می‌یابند (رفتار کاربر بدون تغییر).
- `AtiranAuth` فقط در بلوک PIN تغییر کرد؛ **احراز هویت آتیران (`user_password` با MD5/SHA-1 سازگار) دست‌نخورده ماند**.

### ۲.۲ `android:allowBackup="true"` ⇒ رفع شد (`32c00f5`)

در هر دو manifest (`manager/app` و `manager/admin`) فعال بود. با `adb backup` می‌شد SharedPreferences را بیرون کشید: هش PIN (که تا پیش از ۲.۱ با salt ثابت آفلاین شکسته می‌شد)، نشانی/نام کاربری SQL و متادیتای اتصال. به `false` تغییر کرد. نکته: کلیدهای Keystore خودشان قابل بکاپ نیستند (سیب‌زمینی‌محور دستگاه)، پس SecretBox از قبل محافظت‌شده بود؛ این تغییر لایهٔ نهایی است. برای دفتر لایسنس اپ admin، «خروجی صریح دفتر» در نقشهٔ راه پیشنهاد شده (بند ۳.۲ سند نقشهٔ راه) تا تکیه بر بکاپ سیستم‌عامل نباشد.

### ۲.۳ نسخهٔ انتشار

`36.35.0 (67)` ⇒ **`36.35.1 (68)`** (`ec19b72`) — انتشار «رفع امنیتی».

---

## ۳. یافته‌های واقعی پذیرفته‌شده/مستند (عمداً تغییر داده نشدند)

| # | یافته | چرا تغییر نکرد | پیشنهاد |
| --- | --- | --- | --- |
| ۱ | رمزهای hardcode شدهٔ `S1/S2/S3` در `licensing/License.java` (codec لایسنس) | بخشی از **سیستم لایسنس فروشنده** است؛ حذف/تغییر آن لایسنس‌های صادرشده را باطل می‌کند. | انتقال به سرور صدور لایسنس + فاز ۲ (نقشهٔ راه) |
| ۲ | `network_security_config`: `cleartextTrafficPermitted="true"` سراسری | فروشگاه‌ها SQL Server محلی **بدون TLS** دارند؛ بستن cleartext اپ را در LAN از کار می‌اندازد. | نگه‌داشتن برای LAN + محدودسازی WAN به TLS در نسخهٔ v37 (بند ۳.۱ نقشهٔ راه)؛ updater همین حالا با `sha256` + HMAC-MAC (`License.updateMac`) محافظت می‌شود |
| ۳ | MD5/SHA-1 در سازگاری `AtiranAuth`/`AtiranProductionAuth` | هش‌های موجود در `sys_users` با همین الگوریتم‌ها ذخیره شده‌اند؛ تغییر = قفل شدن همهٔ کاربران. | فقط با تغییر هم‌زمان سمت دیتابیس آتیران (خارج از اختیار اپ) |

---

## ۴. دروغ‌مثبت‌های قطعی اسکن (تریاژ‌شده)

این الگوها در اسکن خودکار پرچم می‌خورند اما **ایراد نیستند** — برای بازبینی‌های آتی مبنا هستند:

1. **`rs.next()` بدون `if`** در حلقه‌های `Repo.one`/`rowConsuming`: قرارداد مصرف‌کنندهٔ تک‌سطری است؛ سطر اول *خودش* جواب است («هیچ سطری» یعنی مقدار پیش‌فرض). صدها مورد از ۵۱۵ یافتهٔ NPE از همین نوع‌اند.
2. **`PendingIntent`/`registerReceiver` بدون پرچم**: همه با `SDK_INT >= 33` محافظت شده‌اند (`FLAG_IMMUTABLE`، `RECEIVER_EXPORTED/NOT_EXPORTED`) — بررسی چندخطی تأیید کرد.
3. **حلقهٔ `while ((n = in.read(buf)) > 0)`**: قرارداد `InputStream.read` فقط برای بافر صفر بایتی 0 برمی‌گرداند؛ الگو برای استریم‌های blocking درست است (`>= 0` / `!= -1` فقط برای `ReadableByteChannel` لازم است).
4. **`http://` در کد**: همه مربوط به namespaceهای XML در `Docx`/`Xlsx`/`RTF` هستند (بدون شبکه).
5. **`split(...)[0]` در جاوا**: همیشه ≥۱ عنصر برمی‌گرداند؛ شرط `parts.length != 2` برای split با limit=2 در `SecretBox`/`PinHash` نیز صحیح است.
6. **`charAt` با `catch(Exception)`**: در `LanDiscover.fileKey` و `LicenseActivity` گارد `isEmpty`/`size==1` دارد؛ `catch(Exception)` شامل `AIOOBE` می‌شود.
7. **`sscanf` بدون بررسی خروجی در C/پایتون ابزارها**: همهٔ فراخوانی‌ها گارد دارند (بررسی دستی).
8. **هش `SHA-256` ساده در `SecretBox`/`MeelanoKeys`**: برای fingerprint کلید/امضای APK استفاده می‌شود نه رمز عبور — مناسب کاربرد.

---

## ۵. نتیجهٔ کنترل‌های ایستا و تست‌ها

| کنترل | نتیجه |
| --- | --- |
| `tools/verify_all.py` (۲۴ کنترل) | **۲۲ از ۲۴** — دو هشدار ماندگار «موتور QR/بارکد در برابر مرجع» (به‌دلیل نبود ماژول `qrcode` در محیط اجرا؛ در CI سبزند) |
| `tools/audit_manager_schema.py` | **۰ خطا** — ۲۸۵ نگاشت `Meta.must`، ۲۳۸ ارجاع `dbo`، ۴ تابع اسکالر |
| parse جاوا (javalang) روی فایل‌های تغییریافته | **سالم** (`PinHash`، `Settings`، `AtiranAuth`) |
| تست‌های واحد (`CommandRoomTest`، `VisitorWorkflowTest`، `IconArtTest`) | در CI سبز |
| CI (`MEELANO Manager v36.27.0 debug APK`) | برای commitهای `32c00f5` + `ec19b72` اجرا شد |

---

## ۶. وضعیت release

- نسخه: **`36.35.1`** (versionCode **68**) — «رفع امنیتی: هش PIN + allowBackup».
- تغییرات کاربردپذیر: **هیچ** — رفتار ورود، PIN، صفحه‌ها و قیمت‌ها بدون تغییر؛ فقط ذخیرهٔ PIN امن شد و بکاپ ADB غیرفعال شد.
- APK خروجی CI جایگزین `dist/meelano-manager-v36-debug-18.apk` می‌شود؛ hash معتبر در `dist/manager-v36-build-info.txt`.
- ریسک انتشار: **پایین**. تنها نکتهٔ عملیاتی: با `allowBackup=false`، انتقال دستگاه‌به‌دستگاه باید با ورود مجدد/تنظیم دوبارهٔ PIN انجام شود (پیش از این هم رمز SQL به‌خاطر Keystore منتقل نمی‌شد).
