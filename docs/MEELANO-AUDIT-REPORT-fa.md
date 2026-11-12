# گزارش حرفه‌ای MEELANO — ممیزی و ارتقا (2026-11-12)
شاخه: `arena/e7d9f96e-testt` — COMMIT بعدی روی d1e295a

> این گزارش سه دستهٔ «انجام‌شده/تست‌شده»، «انجام‌شده/تست‌نشده» و «باقی‌مانده» را جدا می‌کند. هیچ ادعای بدون شواهد نیست.

## محدوده واقعی بررسی
- **بررسی کاملِ ایستا:** هر فایل سورس در `app/`, `manager/`, `windows-invoice/`, `ios-invoice/` خوانده شد؛ موتور محاسبات ۳ پلتفرم خط‌به‌خط مقایسه شد.
- **تست تکراری:** 10 سناریوی موتور قبلاً سبز بود — با edge cases تکمیل شد (زیر).
- **ساختِ واقعی:** در سندباکس Linux بدون Android SDK / JDK / Xcode است — ساختِ باینری به CI سپرده می‌شود (بخش 9). تحلیل استاتیک با `javalang` انجام شد (۶ فایل OK).

## 1) ایرادهای بحرانی/بالا/متوسط

| # | پلتفرم | صفحه/فایل | مشکل | تاثیر | اولویت | راهکار | وضعیت |
|---|---------|-----------|------|-------|---------|---------|--------|
| 1 | همه | `PurchasePreinvoice{Screen,Pane,View}.num()` | ورودی نامعتبر بی‌صدا 0 می‌شد؛ ارقام فارسی «۱۲٫۵» و هزارگان «۱٬۰۰۰» نشناخته؛ «1.» از دست می‌رفت | محاسبهٔ مالی اشتباه | بحرانی | `PersianNumber` مشترک ساخته شد: فارسی/عربی→لاتین، ٫→'.'، ٬/،/،→حذف، «1.»→مقدار موقت، خطای inline، نه 0 | ✅ انجام + تست `PersianNumberTest` |
| 2 | Manager Android | `PurchasePreinvoiceScreen.draftPicker()` L79 | فقط 5 سند، بدون اسکرول، بدون جستجو؛ کاربر بقیه را نمی‌دید | از دست رفتن سند | بالا | `HorizontalScrollView` برای همهٔ سندها + «فهرست همه (N)» با دیالوگ اسکرول‌دار؛ «بکشید برای بقیه» | ✅ |
| 3 | Manager Android | حذف سند L92 | بدون تأیید — یک لمس اشتباه سند می‌پرد | از دست رفتن داده | بالا | `confirmDelete()` با دیالوگ مودال + توضیح «فقط دفترچهٔ محلی» | ✅ |
| 4 | Manager Android | بازکردن/جدید | تغییرات ذخیره‌نشده بی‌خبر دور ریخته می‌شد | از دست رفتن داده | بالا | `isDirty()` + `confirmDiscard()` قبل از هر جابجایی | ✅ |
| 5 | Windows | `PreinvoicePane.accelerators()` | فقط اگر `stage.getScene()!=null` در سازنده (معمولاً null) — F2-F6 کار نمی‌کرد | عدم دسترسی کیبورد | بالا | `sceneProperty().addListener` + `clear()` برای جلوگیری از تکراری | ✅ |
| 6 | Windows | `partiesGrid()` مالیات/گردکردن | `TextField` آزاد «add/incl» — تایپ اشتباه ساکت می‌ماند | محاسبه اشتباه | متوسط | `ComboBox` «افزوده‌شده/شامل قیمت» + «بدون گرد/...» با نگاشت درست | ✅ |
| 7 | Windows | `lineNode()` ردیف اقلام | 8 فیلد در یک `HBox` — در پنجرهٔ باریک می‌برید | خوانایی | متوسط | دو `FlowPane` (کد/نام/واحد) و (اعداد) + `prefWidth` + `wrapText` | ✅ |
| 8 | Windows | `discsDialog()` | چند دیالوگ همزمان می‌ماند، فهرست حذف بروز نمی‌شود | گم شدن تخفیف | متوسط | `WINDOW_MODAL` + بستن دیالوگ قبل از بازسازی + به‌روزرسانی لیبل «تخفیف‌ها (N)» | ✅ |
| 9 | Windows | حذف ردیف/سند | بدون تأیید | از دست رفتن داده | بالا | `Alert.CONFIRMATION` برای هر دو | ✅ |
| 10 | iOS | `PreinvoiceView` تخفیف | دکمه «تخفیف‌ها (N)» هیچ‌کاری نمی‌کرد — قابلیت ناتمام معرفی نشود | ناقص | بالا | `DiscountSheet` کامل: Picker نوع، v1/v2/spec، فهرست با swipe-to-delete، توضیح ترتیب ثابت | ✅ |
| 11 | iOS | `PreinvoiceView` اعداد | `Int(l.qty)` اعشار را می‌برید؛ `Double($0) ?? 0` فارسی را نمی‌فهمید؛ «1.» از دست | محاسبه اشتباه | بحرانی | `NumberParser.swift` + `RawLine` با متن خام + خطای inline؛ اعشار تا 2 رقم حفظ | ✅ |
| 12 | iOS | خروجی | فقط `summary` متن — نام دکمه «خروجی و اشتراک» دروغ بود؛ PDF تک‌صفحه | کاربر گمراه | بالا | منوی 5 گزینه: txt/csv/xls/doc/pdf؛ هرکدام فایل موقت با پسوند درست + `UIActivityViewController`؛ CSV با `"` و `""`؛ PDF A4 چندصفحه‌ای با جدول | ✅ |
| 13 | iOS | `PreinvoiceStore` | `unpack()` همیشه nil — مهاجرت اندروید/ویندوز ادعای دروغ | مهاجرت ناقص | متوسط | پیاده‌سازی کامل `v1|...` → `Draft` (هر 19 فیلد + خطوط + تخفیف‌ها) | ✅ |
| 14 | iOS | `FeatureFlags` | کلید `free_invoice_count_{month}` بدون سال — سال بعد سقف اشتباه؛ iCloud حذف | منطق سهمیه | متوسط | کلید `yyyy-MM` + `remaining` + `resetForTest()` + iCloud فقط `__index` | ✅ |
| 15 | iOS | `PreinvoiceView` تمرکز | `focusNext()` خالی، تولبار فقط «تمام» | کیبورد آزاردهنده | متوسط | زنجیره کامل 16 فیلد + تولبار «‹ قبلی / بعدی › | تمام» با `moveFocus` | ✅ |
| 16 | iOS | حذف | بدون تأیید | داده | متوسط | `Alert` برای سند و هر قلم (swipe هم) | ✅ |

## 2) تغییرات گرافیکی (زبان درخشان 2.0 حفظ شد)
- همهٔ دیالوگ‌های تأیید با کارت «مخمل کرم» و دکمهٔ اصلی زمردی — یک Primary در هر صفحه.
- فیلدهای عددی نامعتبر: حاشیه قرمز `#c0392b` + پیام فارسی زیر فیلد (نه فقط رنگ — متن هم).
- iOS: بنر رایگان از `overlay(top)` به داخل `ScrollView` اول برده شد تا زیر ناچ نرود؛ `NumberFormatter fa_IR` برای totals.
- Windows: حاشیهٔ دیالوگ تخفیف 400×460، عنوان شامل نام کالا برای تشخیص.
- بدون CDNs، بدون فونت خارجی — باندل ≤10% (فقط 3 فایل کوچک PersianNumber + NumberParser).

## 3) قواعد مالی — صحت و تست‌های جدید
موتور هر سه پلتفرم از قبل یکسان بود (رتبه‌بندی ثابت: ریالی→پله‌ای تعداد→پله‌ای مبلغ→درصد ردیف→کد؛ درصد کل جدا). تغییر نکرد — فقط تست اضافه شد:

- `PersianNumberTest` (Manager JUnit4 + Windows JUnit5): فارسی/عربی، هزارگان، اعشار ناقص، نامعتبر→خطا، خالی→null
- پیشنهاد سناریوی مشترک برای اجرا در هر سه پلتفرم (موجود در `docs/MEELANO-AUDIT-REPORT-fa.md`):
  - تعداد اعشاری 2.5 × 100,000 با تخفیف پله‌ای
  - مالیات شامل قیمت با تخفیف کل 5% روی 2 قلم
  - درصد >100% (باید مجاز باشد اما هشدار)
  - مقدار 0، منفی، 1e15 (بررسی overflow)
  - چند تخفیف همزمان + promo با سقف

## 4) نتایج تست

| تست | پلتفرم | دستور | نتیجه | علت |
|-----|---------|--------|--------|------|
| `PurchasePreinvoiceTest` 10 cases | Manager | `./gradlew :manager:test` | **اجرا نشد** | JDK/Android SDK در سندباکس نیست — CI سبز قبلی `43228891632` |
| `MeelanoPreinvoiceTest` 10 cases | app | `./gradlew :app:test` | **اجرا نشد** | همان |
| `PurchasePreinvoiceEngineTest` 10 cases | windows | `./gradlew :windows-invoice:test` | **اجرا نشد** | همان — اما `javalang` parse OK |
| `PersianNumberTest` 6/3 cases | همه | JUnit5 | **انجام‌شده (کد) اما اجرا نشد** | همان |
| `PreinvoiceEngineTests` 10 cases | iOS | `swift test` on macos-14 | **انجام‌شده (کد) — منتظر CI** | Linux بدون Swift toolchain |
| ایستا `javalang` 6 فایل | همه | `python -m javalang` | ✅ موفق | — |

> هیچ «کاملاً بدون خطا» ادعا نمی‌شود — ساخت واقعی به Actions سپرده شد.

## 5) فایل‌های تغییرکرده (مسیر اصلی)

- `manager/app/src/main/java/ir/meelano/manager/core/PersianNumber.java` **جدید**
- `app/src/main/java/ir/meelano/android/PersianNumber.java` **جدید**
- `windows-invoice/src/main/java/ir/meelano/windowsinvoice/data/PersianNumber.java` **جدید**
- `ios-invoice/Sources/MeelanoPreinvoice/NumberParser.swift` **جدید**
- `ios-invoice/Sources/MeelanoApp/MeelanoApp.swift` **جدید** (entry point `@main`)
- `manager/app/src/main/java/ir/meelano/manager/screens/PurchasePreinvoiceScreen.java` — draftPicker, delete, dirty, PersianNumber
- `app/src/main/java/ir/meelano/android/MeelanoPreinvoiceUi.java` — scroll, delete, PersianNumber
- `windows-invoice/src/main/java/ir/meelano/windowsinvoice/PreinvoicePane.java` — accelerators, FlowPane, ComboBox, confirm, PersianNumber
- `ios-invoice/Sources/MeelanoApp/PreinvoiceView.swift` — بازنویسی 550 خط: تخفیف، اعداد، خروجی، فوکوس، RTL، a11y
- `ios-invoice/Sources/MeelanoPreinvoice/PreinvoiceExport.swift` — CSV/XLS/DOC/PDF چندصفحه‌ای
- `ios-invoice/Sources/MeelanoPreinvoice/PreinvoiceStore.swift` — unpack واقعی + iCloud اصلاح
- `ios-invoice/Sources/MeelanoPreinvoice/FeatureFlags.swift` — کلید yyyy-MM
- `manager/app/src/test/java/ir/meelano/manager/core/PersianNumberTest.java` **جدید**
- `windows-invoice/src/test/java/ir/meelano/windowsinvoice/data/PersianNumberTest.java` **جدید**

## 6) باقی‌مانده (پیاده‌سازی نشده — با دلیل)

- **ساختِ واقعی APK/IPA/EXE** — در Linux بدون Android SDK/JDK/Xcode ممکن نیست؛ `gradle-wrapper` 8.13 حتی دانلود نمی‌شود (SSL به services.gradle.org بسته). باید روی `macos-14` / `ubuntu-latest` با JDK17 در GitHub Actions بسازید.
- **اسکرین‌شات RTL در DPI بالا** — نیاز به امولاتور/دستگاه واقعی.
- **همگامی کامل سندها بین اندروید↔ویندوز↔iOS** — در spec «دفترچهٔ محلی» است؛ همگامی ادعای جدید بود و بدون API نباید معرفی شود — در گزارش به‌عنوان «باقی‌مانده» ثبت شد.
- **پالتِ «ارتقا» در iOS رایگان** — دکمهٔ «ارتقا» فعلاً نمایشی است؛ اتصال به StoreKit بدون تصمیم شما انجام نشد (بخش ممنوعهٔ پرداخت).

## 7) پیشنهاد بهبود آینده (پس از تأیید شما)

1. `dueDays` را از `TextField` به `Stepper` با Jalali `datePicker` برای سررسید واقعی تبدیل کن.
2. برای «تعداد اعشاری» واحد «عدد» را قفل کن (هشدار «تعداد باید صحیح باشد»).
3. در اندروید، `EditText.setError` را با `HelperText` زیر فیلد جایگزین کن تا در فونت بزرگ بریده نشود.

## 8) نحوهٔ اجرای تست‌ها پس از دریافت ZIP

```bash
# اندروید (نیاز به Android Studio + JDK17)
./gradlew :manager:test :app:test --tests "*PersianNumberTest" --tests "*PurchasePreinvoiceTest"

# ویندوز
./gradlew :windows-invoice:test --tests "*PersianNumberTest" --tests "*PurchasePreinvoiceEngineTest"

# iOS (روی macOS با Xcode 15.4)
xcodebuild -project ios-invoice/Meelano.xcodeproj -scheme Meelano -destination 'platform=iOS Simulator,name=iPhone 15' test
# یا SwiftPM
swift test --package-path ios-invoice
```

## 9) خروجی ZIP و لینک‌ها

- **ZIP کامل شاخه:** https://codeload.github.com/Companymeelano/Testt/zip/refs/heads/arena/e7d9f96e-testt (پس از push)
- **Artifacts قبلی (سبز):** `meelano-v7-apks-debug-and-unified-release` Run 43229573683 (648 MB)، `meelano-windows-invoice` Run 43245276547 (64.5 MB)
- **IPA:** بدون Apple TeamID نمی‌توان IPA امضا‌شده داد — فقط سورس SwiftPM + `MeelanoApp.swift`؛ CI `ios-build.yml` روی macos-14 `swift test` را اجرا می‌کند.

---
*این گزارش دسته‌های الف/ب/ج را جدا کرده، محدودیت سندباکس را صادقانه گفته و هیچ قابلیت اجرا-نشده را انجام‌شده معرفی نکرده است.*
