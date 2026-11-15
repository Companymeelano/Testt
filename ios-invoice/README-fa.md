# میلو — پیش‌فاکتور خرید (iOS / آیفون)

**وضعیت:** اسکلت حرفه‌ای آمادهٔ بیلد — موتور حسابداری ۱۰۰٪ یکسان با Manager اندروید و کلاینت ویندوز.

## چرا iOS جدا نیست؟
منطق حسابداری (۶ نوع تخفیف پله‌ای، مالیات افزوده/شامل، عوارض، گردکردن، اختلاف لحظه‌ای) در `PreinvoiceEngine.swift` **خط‌به‌خط** برابر با `PurchasePreinvoice.java` است. تست‌های `PreinvoiceEngineTests.swift` همان ۱۰ سناریو را با tolerance یکسان پاس می‌کنند — هر سه کلاینت یک عدد نشان می‌دهند.

| کلاینت | دفترچهٔ محلی | خروجی |
|---|---|---|
| اندروید Manager | `SharedPreferences` | PDF/PNG/CSV/XLS/DOC + شیت اندروید |
| ویندوز | `~/Meelano/preinvoice-ledger.txt` | CSV/XLS/DOC + چاپ |
| **آیفون (این پوشه)** | `UserDefaults` + iCloud `NSUbiquitousKeyValueStore` (اختیاری) | PDF (UIGraphicsPDFRenderer) + CSV/XLS/DOC + **شیت iOS** (AirDrop, واتساپ، تلگرام، بلوتوث، Mail) |

## طراحی — درخشان ۲.۰ روی iOS
- `Color.creamVelvet` / `Color.emeraldNight` — همان «مخمل کرم» و «شب زمرد» اندروید
- کارت سنگ دوپله + hairline، یک Primary طلایی در هر صفحه، ۴۴pt hit-target
- Shimmer یک‌بار در نشست (900ms)، ورود کارت‌ها 420ms با سقف 700ms
- `prefers-reduced-motion` → همهٔ انیمیشن‌ها fade

## بیلد IPA
```bash
# روی مک با Xcode 15+
xcodebuild -project Meelano.xcodeproj -scheme Meelano -destination 'generic/platform=iOS' archive \
  -archivePath build/Meelano.xcarchive CODE_SIGNING_ALLOWED=NO
xcodebuild -exportArchive -archivePath build/Meelano.xcarchive -exportPath build/ipa -exportOptionsPlist ExportOptions.plist
```
یا: `Product → Archive` در Xcode → Distribute → TestFlight / Ad Hoc.

Workflow گیت‌هاب `ios-build.yml` روی `macos-14` همین را به‌صورت unsigned IPA به‌عنوان Artifact می‌سازد (برای TestFlight باید `APPLE_TEAM_ID` و `APPLE_CERT` را در Secrets بگذارید — راهنمای داخل workflow).

## کیبورد حرفه‌ای آیفون
- نوار ابزار بالای کیبورد: `‹ قبلی | بعدی › | تمام` — بین فیلدها با 44pt دکمه
- `Return` → فیلد بعدی (ترتیب طبیعی فرم)، `Done` → بستن
- ورودی عددی با `keyboardType: .decimalPad` + دکمهٔ «٫» فارسی
- اختلاف با مرجع با هر کلید زنده، با رنگ سبز/قرمز
