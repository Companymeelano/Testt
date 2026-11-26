# معماری دستیار هوشمند میلانو — مستند فنی (پیاده‌سازی‌شده)

> این سند ابتدا **پیش از هر تغییر** نوشته شد تا معماری موجود ثبت و طراحی افزوده‌ها مشخص گردد.
> بخش‌های ۱ تا ۵ همان تحلیل/طراحی اولیه‌اند؛ **بخش ۶ وضعیت پیاده‌سازی‌شدهٔ نهایی** را ثبت می‌کند.
> نسخهٔ تحویل: **v36.44.0 / versionCode 77**. گزارش تست واقعی: `AI-ASSISTANT-QA-fa.md`؛
> گزارش تحویل و محدودیت‌ها: `AI-ASSISTANT-DELIVERY-fa.md`.
> مبنای بررسی: سورس کامل پروژهٔ `manager/` (نه فقط APK). نسخهٔ پایه: **v36.43.0 / versionCode 76**.
> خوشبختانه پروژهٔ اصلی Android Studio در دسترس است؛ بنابراین بازسازی ناقص از APK لازم نیست و
> همهٔ تغییرها روی سورس واقعی و قابل‌ساخت انجام می‌شود.

---

## ۱. ساختار کلی برنامه

- پروژهٔ مستقل Gradle در `manager/`؛ بستهٔ `ir.meelano.manager` (نسخهٔ debug با پسوند `.debug`).
- **زبان:** جاوای خالص. **رابط کاربری:** Viewهای دست‌ساز (بدون Compose / viewBinding / کتابخانهٔ UI).
- `minSdk 26`، `targetSdk 34`، `compileSdk 35`. Gradle 8.11.1 + AGP 8.10.1، JDK 17.
- وابستگی‌های بیرونی (فقط همین‌ها):
  - `net.sourceforge.jtds:jtds:1.3.1` — اتصال مستقیم به **SQL Server آتیران**.
  - `com.google.zxing:core:3.5.3` — QR.
  - `sherpa-onnx-1.13.8.aar` (محلی در `app/libs`) — موتور **آفلاین** گفتار/شنیدن فارسی.
  - `kotlin-stdlib`، `commons-compress` (برای بازکردن مدل‌های tar.bz2).
  - ماژول `:licensing` (کدک مشترک لایسنس).
- مسیر ساخت در CI: `.github/workflows/build-manager-v36.yml` → `dist/meelano-manager-v36-debug-*.apk`.

### لایهٔ داده
- `data/Repo.java`: اجرای کوئری در نخ پس‌زمینه (ThreadPool با صف کراندار). **فقط SELECT** — برنامه
  در لایهٔ گزارش/دستیار فقط‌خواندنی است. اتصال هوشمند داخل/خارج شبکه از طریق `SmartLink`.
- `data/Settings.java`: تنظیمات اتصال (host/lan/wan/port/db/user/pass) در SharedPreferences؛
  گذرواژهٔ دیتابیس از مسیر امن خوانده می‌شود.
- `core/Queries`, `MasterQueries`, `MoneyQueries`, `ReportCatalog`: کوئری‌های تایپ‌شدهٔ آتیران.
  `ReportCatalog.query(m,id,f)` تنها سازندهٔ SQL گزارش‌هاست (شناسهٔ گزارش + فیلتر تایپ‌شده).

### نقش‌ها و دسترسی
- `core/RoleStore.java`: ۸ نقش (مدیر، مدیر فروش، فروشنده، حسابدار، ویزیتور، موزع، انباردار، مودیان).
  gating مرکزی در `MainActivity.nav()`. نشست آتیران (`AtiranAuth`) بر نقش محلی اولویت دارد.
- `RoleStore.allowed(c, screenId)` / `allowedForRole(role, screenId)` مرجع کنترل دسترسی هر بخش.

### صفحه‌ها
- ۳۴+ صفحه در `MainActivity` ثبت می‌شوند (`reg(new …Screen(this))`)، از جمله
  `AssistantScreen` (دستیار)، `VoiceScreen` (سفارش صوتی ویزیتور)، `SettingsScreen`.
- بخش «هوش مصنوعی» در تنظیمات: `SettingsScreen` → `AiSettings.build(a, content, rerender)`.

---

## ۲. پشتهٔ فعلی هوش مصنوعی (وضعیت موجود)

| کلاس | نقش | نکتهٔ امنیتی/معماری |
|---|---|---|
| `core/AiClient` | کلاینت HTTP. دو قالب سیمی: **Gemini بومی** و **OpenAI-compatible**. استریم SSE، پاسخ کامل، TTS. | کلید در هر فراخوانی پاس می‌شود و اینجا ذخیره نمی‌شود. `ApiError` کد HTTP را حمل می‌کند. |
| `core/AiRouter` | انتخاب سرویس + failover. ترتیب per-task (analysis/fast/writing/general). | cooldown:‏ 429→۱۰ دقیقه، 401/403→۱ ساعت. فقط سرویس‌های دارای کلید. |
| `screens/AiSettings` | UI تنظیمات: کلید per-provider (رمزنگاری‌شده)، رضایت، دانلود ASR/TTS آفلاین، تست اتصال. | کلیدها با `SecretBox` (AES-GCM/Keystore) ذخیره می‌شوند. |
| `core/BuiltinKeys` | ⚠️ کلیدهایجاسازی‌شده Gemini/Groq در سورس. | **نقض امنیتی:** کلید در کد ثابت و APK جاسازی شده. |
| `core/SecretBox` | رمزنگاری AES-GCM با کلید Android Keystore. | متن خام هرگز به SharedPreferences نمی‌رسد. |
| `core/AiPromptRedactor` | حذف الگوهای کلید/توکن از متن قبل از ارسال به مدل. | |
| `core/AiEndpoint` | اعتبارسنجی URL پروکسی (فقط HTTPS، بدون credential/query). | |
| `core/AiBackendClient` | کلاینت اختیاری پروکسی PHP (AvalAI) با نشست bearer. | رمز فقط برای یک درخواست استفاده و ذخیره نمی‌شود. |
| `core/AiTask` | طبقه‌بندی پرسش به writing/analysis/fast/general. | |
| `core/Assistant` | Snapshot زندهٔ فروشگاه (`Snap`) + پاسخ rule-based فارسی + `contextBlock` برای مدل. | فقط‌خواندنی؛ هیچ داده‌ای به بیرون نمی‌فرستد مگر با رضایت. |
| `core/AssistantEngine` | مسیریاب intent آفلاین فارسی؛ ابزارهای whitelist ثابت؛ role-check. | مدل هرگز SQL نمی‌سازد؛ در نبود داده صریح می‌گوید. |
| `core/AssistantPrompt` | ساخت prompt کراندار با redaction و تاریخچهٔ محدود. | |
| `core/ReportAssistant` | پل امن NL→کاتالوگ گزارش تایپ‌شده. مدل فقط `report_id`+`period` انتخاب می‌کند. | fail-closed؛ شناسهٔ ناشناس/غیرمجاز رد می‌شود. |
| `screens/AssistantScreen` | UI چت، استریم، «گفتگوی زنده» صوتی، پرسش‌های آماده. | |
| `core/Speaker` | ارکستر TTS فارسی: آفلاین sherpa → TTS سیستم → ابری Gemini/OpenAI. | |
| `core/OfflineVoice` | TTS آفلاین فارسی (sherpa-onnx، مدل Matcha زن). | مدل یک‌بار دانلود (~۱۳۰MB) سپس آفلاین. |
| `core/OfflineAsr` | ASR آفلاین فارسی (Whisper base sherpa-onnx). | مدل یک‌بار دانلود (~۲۰MB) سپس آفلاین. |
| `core/SpeechQueue` | خواندن جمله‌به‌جملهٔ پاسخ استریم‌شده. | |

### سرویس‌های شناسایی‌شده در کد
`AiClient.baseUrl()` هر ۹ سرویس + custom را می‌شناسد:
GapGPT, Groq, DeepSeek, Gemini, xAI/Grok, OpenAI, OpenRouter, Cerebras, Hugging Face.
**اما** `AiClient.NAMES` فقط ۴ سرویس (Gemini, DeepSeek, Groq, OpenAI) را در UI نشان می‌دهد؛
بنابراین GapGPT و بقیه در تنظیمات قابل انتخاب نیستند.

### مدیریت کلید فعلی
- کلید هر سرویس جداگانه و رمزنگاری‌شده: `SecretBox` تحت نام `ai_key_<provider>`.
- مهاجرت کلید legacy (`ai_key`) یک‌بار انجام می‌شود.
- در نبود کلید کاربر، `BuiltinKeys.get(provider)` کلیدجاسازی‌شده را برمی‌گرداند (⚠️ باید حذف شود).
- رضایت (`consent`) پیش‌شرط ارسال هر داده به سرویس بیرونی است.

---

## ۳. شکاف‌ها نسبت به مأموریت (چه چیزی باید اضافه/اصلاح شود)

1. **انتخاب سرویس اصلی و همهٔ سرویس‌ها:** فقط ۴ سرویس در UI؛ GapGPT باید سرویس اصلی پیش‌فرض باشد
   (در صورت تأیید اتصال/دسترسی). ← رجیستری مرکزی `AiProviders` + پیش‌فرض GapGPT.
2. **انتخاب مدل per-service:** در UI وجود ندارد (مدل‌ها در کد hardcode شده‌اند). ← فیلد مدل + override.
3. **سرویس جایگزین دستی:** ترتیب fallback hardcode است. ← ترتیب قابل‌پیکربندی توسط کاربر.
4. **وضعیت/تست مستقل هر سرویس:** فقط تست سرویس فعال. ← تست + pill وضعیت برای هر سرویس.
5. **امنیت کلید:** `BuiltinKeys` کلید واقعی را در سورس/APK دارد. ← حذف کلیدهایجاسازی‌شده؛ چرخش کلیدهای
   افشاشده توسط مالک الزامی است (در گزارش محدودیت‌ها ذکر می‌شود).
6. **تنظیمات صدا/زبان/رفتار:** UI انتخاب صدا/زبان/رفتار دستیار ناقص است. ← افزودن به تنظیمات.
7. **عملیات حساس:** دستیار عمداً فقط‌خواندنی است (امن). چارچوب تأیید کاربر برای هر عملیات نوشتنی
   آینده مستند می‌شود؛ هیچ نوشتن مستقیم توسط مدل اضافه نمی‌شود.

---

## ۴. طراحی افزوده‌ها (مرحلهٔ ۲)

### ۴.۱ رجیستری سرویس `AiProviders` (خالص، قابل‌تست)
- یک آرایهٔ مرتب از `Spec{id, name, baseUrl, wire, defaultModel, analysisModel, ttsModel, speech}`.
- ترتیب پیش‌فرض: **GapGPT اول** (سرویس اصلی)، سپس Groq، DeepSeek، Gemini، OpenAI، Grok،
  OpenRouter، Cerebras، HuggingFace و در پایان custom.
- `wire` دو مقدار دارد: `openai` (سازگار با OpenAI) و `gemini` (بومی گوگل). Gemini هرگز از
  مسیر OpenAI نمی‌رود.
- `AiClient` متادیتا (baseUrl/defaultModel/modelFor/hasSpeech/nameOf/NAMES) را از این رجیستری
  می‌خواند تا یک منبع حقیقت واحد باشد و افزودن سرویس جدید = یک ورودی تازه در آرایه.

### ۴.۲ مسیریاب `AiRouter` (منطق خالصِ قابل‌تست + پوستهٔ Android)
- `buildOrder(primary, configuredOrder, hasKey, cooled)` یک تابع **خالص**: سرویس اصلی اول،
  سپس ترتیب دستی کاربر، حذف تکراری‌ها، حذف بدون‌کلیدها و حذف سرویس‌های در cooldown.
- `classify(code)` خطا را به `auth` (401/403)، `rate` (429)، `server` (5xx)، `net` (سایر) نگاشت
  می‌کند؛ auth هرگز با خطای موقت شبکه اشتباه نمی‌شود.
- override مدل per-provider از prefs (`model_<provider>`)؛ در نبود آن `AiClient.modelFor`.
- ثبت وضعیت هر سرویس (`status_<provider>`, `status_time_<provider>`) برای UI.
- جلوگیری از تکرار بی‌پایان: cooldown همان منطق قبلی + سقف تلاش در هر درخواست.

### ۴.۳ تنظیمات `AiSettings`
- انتخاب سرویس اصلی از **همهٔ** سرویس‌ها.
- برای هر سرویس: فیلد مدل (با پیش‌فرض رجیستری)، کلید API (رمزنگاری‌شده)، دکمهٔ تست مستقل،
  pill وضعیت (موفق/کلید نامعتبر/محدودیت/شبکه/تست‌نشده).
- ویرایشگر ترتیب جایگزین‌ها (بالا/پایین بردن سرویس‌ها).
- تنظیمات صدا (روشن/خاموش، ابری/آفلاین)، زبان پاسخ، و رفتار دستیار (کوتاه/کامل).
- رضایت داده همچنان پیش‌شرط است.

### ۴.۴ امنیت
- `BuiltinKeys` دیگر هیچ کلیدی را جاسازی نمی‌کند (تهی). کلید فقط از ورود کاربر + `SecretBox`.
- redaction و عدم چاپ کلید در لاگ حفظ می‌شود. `Diag` فقط نوع خطا و محل کد را ثبت می‌کند.

### ۴.۵ صوت
- موتورهای موجود (OfflineAsr/OfflineVoice/Speaker/SpeechQueue) حفظ می‌شوند.
- UI انتخاب صدا/زبان/رفتار و توقف/ادامهٔ پخش تکمیل می‌شود.
- «گفتگوی زنده» موجود است؛ محدودیت‌های فنی/هزینه‌ای در گزارش ذکر می‌شود.

### ۴.۶ اتصال به عملیات واقعی
- دستیار فقط‌خواندنی می‌ماند؛ داده از `Assistant.Snap` و `ReportCatalog` (لایهٔ رسمی) می‌آید.
- مدل هرگز مستقیم به DB دسترسی ندارد؛ فقط ابزارهای whitelist و گزارش‌های تایپ‌شده.
- هر عملیات نوشتنی آینده باید از لایهٔ رسمی + تأیید صریح کاربر بگذرد (مستند شد).

---

## ۵. راهبرد تست و ساخت
- sandbox فاقد JDK/Android SDK است (بدون root؛ CDN دارایی‌های GitHub مسدود). بنابراین
  **کامپایل واقعی، تست واحد و ساخت APK در CI گیت‌هاب** انجام می‌شود (همان مسیر موجود پروژه).
- locally: `tools/java_api_check.py` (javalang) برای نحو/ارجاع بین‌کلاسی + تست‌های واحد جدید
  که در CI اجرا می‌شوند.
- هیچ تستی که واقعاً اجرا نشده «موفق» اعلام نمی‌شود؛ گزارش تست، خروجی واقعی CI را نقل می‌کند.


---

## ۶. وضعیت پیاده‌سازی‌شدهٔ نهایی (مرحلهٔ ۳ تا ۶)

همهٔ موارد بخش ۳ (شکاف‌ها) پیاده شدند. خلاصهٔ وضعیت واقعی:

| قابلیت | وضعیت | محل |
|---|---|---|
| رجیستری ۹ سرویس + custom، GapGPT اصلی پیش‌فرض | ✅ پیاده | `core/AiProviders` |
| Gemini بومی (generateContent/streamGenerateContent، هدر x-goog-api-key) | ✅ پیاده | `AiClient` (wire=gemini) |
| انتخاب سرویس اصلی از همهٔ سرویس‌ها | ✅ پیاده | `screens/AiSettings` |
| مدل per-service + override | ✅ پیاده | `AiSettings` + `AiRouter.modelFor` |
| ترتیب دستی جایگزین‌ها + failover | ✅ پیاده | `AiRouter.mergeOrder/attempt` |
| تفکیک خطای auth/rate/server/net + cooldown بدون تکرار بی‌پایان | ✅ پیاده | `AiClient.classify` + `AiRouter.cooldownMs` |
| تست اتصال مستقل + pill وضعیت هر سرویس | ✅ پیاده | `AiRouter.testService` + `AiSettings` |
| حذف کلیدهای جاسازی‌شده؛ کلید فقط SecretBox | ✅ پیاده | `core/BuiltinKeys` (تهی) |
| تنظیمات صدا/زبان/سبک پاسخ | ✅ پیاده | `AiSettings` + `AssistantScreen.systemPrompt` |
| گزارش‌گیری از دادهٔ واقعی (روزانه/هفتگی/ماهانه/سالانه) | ✅ پیاده | `ReportAssistant` → `AssistantScreen.groundReport` |
| دستیار فقط‌خواندنی؛ مدل هرگز SQL/DB مستقیم | ✅ حفظ | `Repo.exec` + `ReportCatalog.query` |
| صوت STT→دستیار→TTS + گفتگوی زنده | ✅ حفظ/تکمیل | `OfflineAsr/OfflineVoice/Speaker/SpeechQueue` |

### ۶.۱ مسیر گزارش واقعی (`groundReport`)
برای سؤال‌های کسب‌وکار (یا follow-up کوتاه مثل «دیروز») پیش از پاسخ مدل:
1. `ReportAssistant.followUp` یا `couldBeBusinessQuestion` تشخیص می‌دهد سؤال گزارش می‌خواهد.
2. `visibleReports(role)` فقط گزارش‌های مجازِ نقش فعلی را می‌دهد (همان فیلتر `ReportsScreen`).
3. planner (یک تماس کوچک مدل) فقط `report_id`+`period` را از کاتالوگ مجاز انتخاب می‌کند؛
   `parsePlan` fail-closed است (شناسهٔ ناشناس/غیرمجاز/JSON خراب = بدون گزارش).
4. ردیف‌ها از **مسیر رسمی** خوانده می‌شوند: `Repo.exec(c, ReportCatalog.query(m,id,f))` سپس
   `ReportsScreen.applyMaps` — دقیقاً همان مسیر صفحهٔ گزارش‌ها، فقط SELECT.
5. `formatFacts` حداکثر ۲۰ ردیف / ۸۰۰ نویسه، ستون‌های حساس (تلفن/آدرس/کلید/…) را حذف می‌کند،
   دادهٔ موجودنبوده را «صفر» فرض نمی‌کند و صریح می‌گوید داده دریافت نشد.
6. هر خطا (planner/خواندن/timeout ۲۰ ثانیه) به snapshot زنده برمی‌گردد و در `Diag` ثبت می‌شود؛
   هیچ عددی جعل نمی‌شود. این کار در نخ پس‌زمینه اجرا می‌شود (بدون بلاک UI).

### ۶.۲ تغییرات جنبیِ لازم برای ساخت
- `data/Row`: افزودن `bd()/exactLong()` (اعشار دقیق، fail-closed) — افزودنی، بدون تغییر رفتار موجود.
- `core/AutoBackup.NOTIF_ID` از ۷۰۸ به **۷۰۱۴** تغییر کرد تا با `Notify.EOD_NOTIF_ID=7008` برخورد
  نکند (رفع باگ واقعی: اعلان بکاپ جای خلاصهٔ پایان روز را می‌گرفت). هر دو ثابت package-private شدند.
- تست‌های orphaned/ناهمگون با سورس این شاخه حذف/اصلاح شدند (فهرست کامل در `AI-ASSISTANT-DELIVERY-fa.md`).
  هیچ قابلیت/سورس برنامه حذف نشد.

### ۶.۳ آنچه تغییر نکرد (طبق مأموریت)
- امنیت/احراز هویت/ارتباط سرور: بدون تغییر غیرضروری. credentialهای SQL جاسازی‌شدهٔ آتیران دست‌نخورده
  ماندند (حذفشان اتصال را می‌شکند)؛ به‌عنوان یافتهٔ امنیتی برای مالک مستند شد.
- هیچ عملیات نوشتنی توسط مدل اضافه نشد؛ چارچوب تأیید کاربر برای عملیات حساس آینده مستند است.
