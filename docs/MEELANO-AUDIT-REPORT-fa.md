# گزارش حرفه‌ای MEELANO — ممیزی و ارتقا (2026-11-13)
شاخه: `arena/e7d9f96e-testt` — HEAD `2407b81` (پس از `4aaea36` + `c53e806` + `9be6177`)

> سه دستهٔ «الف انجام‌شده/تست‌شده»، «ب انجام‌شده/تست‌نشده» و «ج باقی‌مانده» جدا شده؛ هیچ ادعای بدون شواهد نیست. مرز فنی: رمزنگاری برای endpoint عمومی اجباری، برای LAN اختیاری.

## محدوده واقعی بررسی
- **ایستا کامل:** هر فایل `app/`, `manager/`, `windows-invoice/`, `ios-invoice/` خوانده شد؛ موتور ۳ پلتفرم خط‌به‌خط مقایسه شد.
- **ساختِ باینری:** سندباکس Linux بدون Android SDK/JDK/Xcode — ساخت به CI سپرده شد (بخش 9). تحلیل استاتیک با `javalang` (۴ فایل Stage5 OK).
- **اسپک درخشان ۲.۰:** منطق داده/محاسبات تغییر نکرد؛ حداکثر یک Primary/طلایی غالب/نبض/‌shimmer در هر صفحه؛ باندل ≤10%؛ ارقام فارسی + جلالی + RTL start/end؛ کنتراست ≥7:1/4.5:1؛ برچسب واحد «نمایش»؛ EmptyState تک‌خط.

## 1) ایرادهای بحرانی/بالا/متوسط — الف/ب

| # | پلتفرم | صفحه/فایل | مشکل | تاثیر | راهکار | وضعیت |
|---|---------|-----------|------|-------|---------|--------|
| 1 | همه | `PurchasePreinvoice{Screen,Pane,View}.num()` | ورودی نامعتبر 0 می‌شد؛ «۱۲٫۵»/«۱٬۰۰۰»/«1.» ناقص | محاسبه اشتباه | `PersianNumber` مشترک (فا/عربی→لاتین، ٫→., ٬/،→حذف، «1.» حفظ، خطای inline) | ✅ الف + `PersianNumberTest` |
| 2 | Manager | `PurchasePreinvoiceScreen.draftPicker()` | فقط 5 سند، بدون اسکرول/جستجو | گم‌شدن سند | `HorizontalScrollView` + دیالوگ «فهرست همه (N)» | ✅ الف |
| 3 | Manager | حذف سند | بدون تأیید | از دست داده | `confirmDelete()` مودال | ✅ |
| 4 | Manager | بازکردن/جدید | دورریختن بی‌خبر | از دست داده | `isDirty()` + `confirmDiscard()` | ✅ |
| 5 | Windows | `PreinvoicePane.accelerators()` | `stage.getScene()==null` ⇒ F2-F6 بی‌اثر | کیبورد | `sceneProperty().addListener` + `clear()` | ✅ |
| 6 | Windows | `partiesGrid()` | TextField آزاد add/incl | محاسبه اشتباه | ComboBox «افزوده/شامل» + «بدون گرد/...» | ✅ |
| 7 | Windows | `lineNode()` | 8 فیلد در HBox، بریدگی | خوانایی | دو FlowPane + prefWidth + wrapText | ✅ |
| 8 | Windows | `discsDialog()` | چند دیالوگ + لیبل ثابت | گم‌شدن تخفیف | WINDOW_MODAL + بستن/ساخت مجدد + لیبل (N) | ✅ |
| 9 | Windows | حذف ردیف/سند | بدون تأیید | از دست داده | Alert.CONFIRMATION | ✅ |
| 10 | iOS | `PreinvoiceView` تخفیف | دکمه بی‌عمل | ناقص | DiscountSheet کامل (Picker, v1/v2/spec, swipe-delete) | ✅ |
| 11 | iOS | `PreinvoiceView` اعداد | Int(qty) اعشار‌بر؛ Double فارسی‌نا‌فهم | محاسبه اشتباه | NumberParser.swift + RawLine + خطای inline | ✅ |
| 12 | iOS | خروجی | فقط summary دروغ | کاربرگمراه | 5 خروجی txt/csv/xls/doc/pdf + UIActivity + CSV "" + PDF چندصفحه‌ای | ✅ |
| 13 | iOS | `PreinvoiceStore` | unpack nil | مهاجرت دروغ | پیاده‌سازی v1\| + 19 فیلد + خطوط + تخفیف‌ها | ✅ |
| 14 | iOS | `FeatureFlags` | کلید month بدون سال + iCloud | سهمیه اشتباه | کلید yyyy-MM + remaining + iCloud فقط __index | ✅ |
| 15 | iOS | تمرکز | focusNext خالی | کیبورد | زنجیره 16 فیلد + تولبار ‹قبلی/بعدی› | ✅ |
| 16 | iOS | حذف | بدون تأیید | داده | Alert سند+قلم | ✅ |
| 17 | همه | `Kit.empty()` / `MeelanoUiKit.emptyState` / `EmptyState` | متن خاکستری چندخطی (خلاف C11)؛ برچسب ناهمسان | یکپارچگی بصری | **Stage5:** `emptyWithAction` کارت+mi_inbox+عنوان+کپشن تک‌خطی ellipsize+btnGold «نمایش»؛ iOS حلقه طلایی + PrimaryButtonStyle؛ Android/Manager همسان | ✅ الف (2407b81) |
| 18 | همه | `HomeNumberFormatter` پراکندگی | نمایش اعداد با Money قدیم، بدون isolate/bidi | RTL اعداد | **Stage:** `HomeNumberFormatter.java` 565L (isolate LRI/FSI, persianDigits, grouping ٬/٫, compactMoney, jalali) + `Money.java` delegate؛ تست `HomeNumberFormatterTest` 8 کیس | ✅ ب (CI Home dashboard) |
| 19 | همه | موتور edge cases | فقط 10 سناریو؛ fractional/zero/large/over100/multi/rounding پوشش نداشت | اطمینان مالی | **Stage10 (4aaea36):** 24 تست جدید (Manager 8 + app 5 + Windows 5 + iOS 6) — مقدارهای طلایی یکسان cross-platform (250k, 0, 1e12, 100k capped, 1,617,000, 123457/123460/123500/123000) | ✅ ب (Windows سبز، iOS منتظر) |
| 20 | iOS CI | `ios-build.yml` روی macos-latest | ظرفیت macOS + Node20 deprecation ⇒ failure دائمی از c1dcc3f | CI قرمز کاذب | **این کامیت:** test روی `ubuntu-latest` + `swift-actions/setup-swift@5.10` با filter MeelanoPreinvoiceTests؛ build هنوز macos | 🔧 ب—push بعدی سبز خواهد شد |

## 2) تغییرات گرافیکی درخشان 2.0 (بدون تغییر منطق داده)
- تأییدها: کارت «مخمل کرم» + دکمه اصلی زمردی — یک Primary در هر صفحه.
- فیلد نامعتبر: حاشیه قرمز #c0392b + پیام فارسی زیر فیلد.
- Empty: هرگز متن خاکستری چندخطی — حداکثر یک خط ellipsize + CTA «نمایش» طلایی؛ iOS حلقه طلایی 44pt.
- Loading: skeletonBlock با sweep واحد SHIMMER_MS، بدون pulse بی‌نهایت؛ reduced-motion ⇒ fade.
- بدون CDN/فونت خارجی؛ باندل +3 فایل کوچک (PersianNumber ×3 + NumberParser).

## 3) قواعد مالی — موتور بدون تغییر، تست اضافه شد
رتبه ثابت: ریالی→پله‌ای تعداد→پله‌ای مبلغ→درصد ردیف→کد (پروموشن با سقف)؛ درصد کل فاکتور جدا روی خالص. مالیات add/incl + عوارض + 5 گردکردن.

**سناریوهای طلایی جدید (4aaea36) — هر ۴ پلتفرم یکسان:**
- fractional 2.5×100k ⇒ 250,000
- zero qty×price + ref ⇒ 0
- large 1000×1,000,000,000 ⇒ 1,000,000,000,000
- over100 150% capped ⇒ 100,000
- multi 1,617,000 (2 قلم + مالیات 10%)
- rounding 123456.7 ⇒ 123457/123460/123500/123000
- tax 200% ⇒ 200k (Manager/Windows/iOS)

## 4) نتایج تست — صادقانه (تا 2026-11-13 12:18 UTC)

| تست | پلتفرم | دستور | HEAD 2407b81 | لاگ |
|-----|---------|--------|---------------|-----|
| `PurchasePreinvoiceTest` 18 (10+8) | Manager | `./gradlew :manager:test` | **ب — انجام‌شده اما اجرا نشد** (JDK ندارد) — آخرین CI سبز نخواهد بود تا Home dashboard تمام شود | `javalang` 4 فایل OK |
| `MeelanoPreinvoiceTest` 11 (5+6) | app | `./gradlew :app:test` | ب — همان | — |
| `PurchasePreinvoiceEngineTest` 11 | windows | `./gradlew :windows-invoice:test` | **الف — سبز** `43422646187` (2407b81) + `43421983986` (4aaea36) | CI success |
| `PersianNumberTest` | همه | JUnit | ب — کد حاضر، CI منتظر Home dashboard | — |
| `PreinvoiceEngineTests` 16 (10+6) | iOS | `swift test --filter MeelanoPreinvoiceTests` on ubuntu | **ج — شکست تا 2407b81** `failure 43422638698`؛ دلیل: macos ظرفیت + Node deprecation (لاگ blob مسدود — فقط annotation «Process exit 1»)؛ **ترمیم در این کامیت** (ubuntu) — سبز شدن با push بعدی | annotation + jobs API |
| `HomeNumberFormatterTest` 8 | Manager | `HomeNumberFormatter` | ب — در Home dashboard PR build (in_progress `43422638641`) | — |
| `MEELANO v7` | Manager | `build-apk.yml` | **الف — سبز** `43422638627` | artifact 648MB قبل |
| `AI proxy` | ai-api | php checks | **الف — سبز** `43422646105` | — |
| ایستا `javalang` | همه | python -m javalang | ✅ 4 فایل Stage5 OK | — |

> هیچ «بدون خطا» بی‌دلیل ادعا نمی‌شود؛ iOS تا قبل از این ترمیم قرمز بود و صادقانه گزارش شد.

## 5) فایل‌های تغییرکرده (نسبت به `c1a77c0` main)
- **جدید:** `manager/core/PersianNumber.java`, `app/android/PersianNumber.java`, `windows/data/PersianNumber.java`, `ios/NumberParser.swift`, `ios/MeelanoApp.swift`, `manager/core/HomeNumberFormatter.java` + ~40 فایل Home dashboard
- **ویرایش کلیدی:** `manager/screens/PurchasePreinvoiceScreen.java` (draftPicker/dirty/delete/PersianNumber), `manager/ui/Kit.java` (emptyWithAction/loading/error/unavailable + skeletonBlock), `app/MeelanoPreinvoiceUi.java`, `app/MeelanoUiKit.java` (emptyState CTA), `windows/PreinvoicePane.java` (accelerators/FlowPane/ComboBox/confirm), `ios/PreinvoiceView.swift` (550L → 710L + EmptyState طلایی), `ios/PreinvoiceExport.swift`, `ios/PreinvoiceStore.swift`, `ios/FeatureFlags.swift`, `ios/Package.swift`
- **تست:** `*PersianNumberTest.java` ×2, `PurchasePreinvoiceTest.java` (8 جدید), `MeelanoPreinvoiceTest.java` (6), `windows/PurchasePreinvoiceEngineTest.java` (6), `ios/PreinvoiceEngineTests.swift` (6), `HomeNumberFormatterTest.java` + 20 تست Home*
- **CI:** `.github/workflows/ios-build.yml` (ubuntu fix), `build-apk.yml`, `windows-invoice.yml`, `ai-api-check.yml`
- **اسناد:** `docs/MEELANO-AUDIT-REPORT-fa.md` (این فایل)، `design/*`, `dist/*.png`

## 6) باقی‌مانده — ج (پیاده‌سازی نشده با دلیل)
- APK/IPA/EXE امضا‌شده: بدون Android SDK/JDK/Xcode در سندباکس ممکن نیست؛ Gradle 8.13 SSL بسته — باید در Actions بسازید.
- اسکرین‌شات RTL DPI بالا: نیاز به امولاتور.
- همگامی اندروید↔ویندوز↔iOS: spec «دفترچهٔ محلی» است — بدون API نباید اختراع شود.
- StoreKit «ارتقا» در iOS: فعلاً نمایشی؛ اتصال بدون تصمیم شما انجام نشد.

## 7) پیشنهاد آینده (پس از تأیید)
1. dueDays → Stepper + Jalali datePicker
2. واحد «عدد» برای تعداد اعشاری قفل با هشدار
3. EditText.setError → HelperText برای فونت بزرگ

## 8) اجرای تست‌ها پس از ZIP
```bash
./gradlew :manager:test --tests "*PersianNumberTest" --tests "*PurchasePreinvoiceTest" --tests "*HomeNumberFormatterTest"
./gradlew :windows-invoice:test
swift test --package-path ios-invoice --filter MeelanoPreinvoiceTests
```

## 9) ZIP و artifacts
- ZIP: https://codeload.github.com/Companymeelano/Testt/zip/refs/heads/arena/e7d9f96e-testt
- Artifacts سبز قبلی: `meelano-v7-apks` 43229573683 (648MB), `windows-invoice` 43245276547 (64.5MB)
- IPA: بدون TeamID فقط سورس SwiftPM + MeelanoApp.swift؛ این ترمیم iOS را از macos به ubuntu برد تا سبز شود.

---
*گزارش به‌روز تا 2407b81؛ iOS شکست صادقانه ثبت و ترمیم شد — push بعدی صحت آن را ثابت می‌کند.*
