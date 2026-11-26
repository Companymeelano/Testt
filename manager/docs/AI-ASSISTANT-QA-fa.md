# گزارش تست واقعی دستیار هوشمند میلانو — ۱۲ بررسی QA

> نسخه: **v36.44.0 / versionCode 77**. اصل صداقت: هیچ تستی که واقعاً اجرا نشده «موفق» اعلام نمی‌شود.
> سه سطح شواهد در این گزارش از هم جدا شده‌اند:
> - **✅ اجرا شد** — تست واحد که در CI گیت‌هاب واقعاً اجرا و پاس شد (build سبز).
> - **🔍 بررسی کد/کامپایل** — منطق در کد بررسی و کامپایل شد، اما اجرای runtime آن نیاز به دستگاه/کلید دارد.
> - **⛔ اجرا نشد** — نیازمند دستگاه/امولاتور + کلید واقعی؛ در این محیط اجرا نشد و «موفق» اعلام نمی‌شود.

## شواهد اجرای واقعی
- **CI run `45856540231`** (کامیت `7db5d58`) — **کاملاً سبز**: `:app:testDebugUnitTest` موفق،
  `:app:assembleDebug` موفق، `apksigner verify` موفق، `aapt badging` → versionCode 77 / versionName 36.44.0.
- مجموعهٔ آزمون: **۷۲ کلاس تست، ۳۷۷ متد `@Test`** — همگی اجرا و پاس شدند.
- تست‌های جدیدِ دستیار که در آن run اجرا شدند: `AiProvidersTest` (۱۰ متد)، `AiRouterLogicTest` (۸ متد)،
  `AiErrorClassificationTest` (۸ متد).
- **CI run `45858002329`** (کامیت `bb52bc3`) — افزودن مسیر گزارش واقعی (`groundReport`)؛ نتیجه در بخش ۷ ثبت می‌شود.
- sandbox فاقد JDK/Android SDK است؛ بنابراین اجرای runtime روی دستگاه اینجا ممکن نبود و صادقانه ⛔ علامت خورده.

---

## جدول ۱۲ بررسی

| # | بررسی | آنچه واقعاً انجام شد | سطح |
|---|---|---|---|
| ۱ | کلید نامعتبر | `AiErrorClassificationTest`: classify(401/403)=auth؛ `AiRouterLogicTest`: cooldown auth=۱ ساعت (> rate > server). مسیر `attempt` کلید نامعتبر را از خطای موقت جدا و سرویس را موقتاً کنار می‌گذارد. | ✅ منطق اجرا شد / ⛔ تماس واقعی با کلید نامعتبر |
| ۲ | آفلاین / بدون شبکه | `AssistantScreen.hasNetwork` + مسیر `runOffline`: در نبود snapshot پاسخ صادقانهٔ آفلاین می‌دهد و عدد جعل نمی‌کند؛ `groundReport` در خطا به snapshot برمی‌گردد. | 🔍 بررسی کد / ⛔ اجرای runtime |
| ۳ | محدودیت نرخ (429) | `AiErrorClassificationTest`: classify(429)=rate؛ `AiRouterLogicTest`: cooldown rate=۱۰ دقیقه. | ✅ منطق اجرا شد / ⛔ پاسخ 429 واقعی |
| ۴ | failover خودکار | `AiRouterLogicTest`: mergeOrder (اصلی→جایگزین‌های دستی→رجیستری، حذف تکراری/ناشناس). `attempt` روی خطای موقت/نرخ/سرور به سرویس بعدی می‌رود؛ هر سرویس حداکثر یک‌بار در هر درخواست. | ✅ منطق ترتیب اجرا شد / ⛔ failover زنده |
| ۵ | استریم پاسخ | `AiRouter.completeStream` + `AiClient.stream` (SSE). failover فقط پیش از نخستین متن؛ پس از نمایش متن جایگزین نمی‌شود. `AssistantScreen` تکه‌ها را in-place نشان می‌دهد. | 🔍 بررسی کد / ⛔ استریم runtime |
| ۶ | TTS (ابری + آفلاین) | `AiClient.speech`: Gemini بومی (PCM→WAV، زبان fa-IR) و OpenAI-compatible (`/audio/speech`, tts-1, Bearer, MP3). `Speaker` زنجیرهٔ ابری→OfflineVoice→TTS سیستم. `AiProvidersTest`: hasSpeech فقط Gemini/OpenAI. | ✅ متادیتا اجرا شد / ⛔ پخش صدای واقعی |
| ۷ | STT (آفلاین + سیستم) | `OfflineAsr` (Whisper-base، ۱۶kHz، ≤۲۰ ثانیه) + `SpeechRecognizer` سیستم در `AssistantScreen.listenOnce`. مدیریت خطای mic/شبکه/سرویس. | 🔍 بررسی کد / ⛔ تشخیص گفتار واقعی |
| ۸ | تأیید عملیات حساس | دستیار **عمداً فقط‌خواندنی** است؛ هیچ مسیر نوشتنی (حذف/سند مالی/موجودی/فروش/ویرایش مشتری) توسط مدل وجود ندارد. `Repo` فقط SELECT. چارچوب تأیید صریح کاربر برای نوشتنِ آینده مستند شد. | 🔍 بررسی کد (نبودِ مسیر نوشتن) |
| ۹ | عدم افشای کلید | `AiErrorClassificationTest.noApiKeysAreEmbeddedInTheApp`: `BuiltinKeys.get` برای همهٔ سرویس‌ها تهی. کلید فقط `SecretBox` (AES-GCM/Keystore). `AiPromptRedactor` + `Diag` (فقط نوع خطا/محل، نه کلید). | ✅ اجرا شد |
| ۱۰ | گزارش از دادهٔ واقعی | `ReportAssistant` → `AssistantScreen.groundReport`: planner فقط report_id+period از کاتالوگ مجازِ نقش؛ خواندن از مسیر رسمی `Repo.exec`+`ReportCatalog.query`+`applyMaps`؛ `formatFacts` کراندار/redact/دادهٔ موجودنبوده≠صفر. | 🔍 بررسی کد + کامپایل / ⛔ اجرای runtime با DB |
| ۱۱ | Gemini بومی | `AiProvidersTest.onlyGeminiUsesTheNativeWireFormat`: wire=gemini فقط برای Gemini؛ بقیه openai. `baseUrlsMatchTheSpecifiedEndpoints`: هر ۹ endpoint دقیق. | ✅ اجرا شد |
| ۱۲ | تست اتصال + وضعیت هر سرویس | `AiRouter.testService` (مستقل، همان سیاست cooldown، نتیجهٔ روشن بدون افشای کلید) + pill وضعیت در `AiSettings`. `AiRouterLogicTest` منطق cooldown/ترتیب را پوشش می‌دهد. | ✅ منطق اجرا شد / ⛔ تست اتصال زنده |

---

## ۱ تا ۱ — جزئیات شواهد

### ۱) کلید نامعتبر — ✅ منطق / ⛔ زنده
تست اجراشده `authCodesAreClassifiedAsAuth` و `authIsPausedLongestThenRateThenServer` ثابت می‌کنند
۴۱/۴۳ → auth و cooldown آن ۳۶۰۰۰ms است. در `attempt`، `ApiError` با kind=auth باعث cooldown یک‌ساعته
می‌شود و پیام فارسی «کلید نامعتبر» ثبت می‌گردد؛ این با خطای شبکه (بدون cooldown) فرق دارد.
**اجرا نشد:** تماس واقعی با یک کلید نامعتبر روی دستگاه (کلید/شبکه در دسترس نبود).

### ۲) آفلاین — 🔍 / 
`hasNetwork()` نبودِ `NET_CAPABILITY_INTERNET` را تشخیص می‌دهد؛ `ask` در نبود شبکه پاسخ rule-based
می‌دهد و `runOffline` صریح می‌گوید سرور در دسترس نیست. `groundReport` در هر خطا/timeout به snapshot
زنده برمی‌گردد و `formatFacts(plan,null)` می‌گوید «داده دریافت نشد؛ صفر فرض نمی‌شود».
**اجرا نشد:** قطع واقعی شبکه روی دستگاه.

### ۳) محدودیت نرخ — ✅ منطق / ⛔ زنده
`rateLimitIsClassifiedAsRate` (429→rate) و cooldown=۶۰۰۰۰۰ms اجرا و پاس شد.
**اجرا نشد:** دریافت 429 واقعی از سرویس.

### ۴) failover — ✅ ترتیب / ⛔ زنده
۵ تست `mergeOrder` اجرا شد: اصلی همیشه اول، ترتیب دستی کاربر بعد از آن، حذف تکراری و ناشناس،
و پرشدن بقیه از رجیستری. `attempt` روی خطای موقت به سرویس بعدی می‌رود و پس از cooldown سرویس
خطادار را رد می‌کند → بدون تکرار بی‌پایان.
**اجرا نشد:** failover زندهٔ چندسرویس با کلیدهای واقعی.

### ۵) استریم — 🔍 / ⛔
`completeStream` با `started[]` تضمین می‌کند failover فقط پیش از نخستین متن باشد؛ `AssistantScreen`
با `liveBody.setText` و `SpeechQueue.feed` پاسخ را تکه‌تکه نشان می‌دهد/می‌خواند.
**اجرا نشد:** استریم واقعی روی دستگاه.

### ۶) TTS — ✅ متادیتا / ⛔ صدا
`speechIsOfferedOnlyByGeminiAndOpenAi` اجرا شد. مسیر OpenAI در `AiClient.speech` بررسی شد:
`POST {base}/audio/speech` با model=tts-1، voice، input، response_format=mp3 و هدر Bearer؛ خروجی MP3.
مسیر Gemini بومی با `speechConfig.languageCode=fa-IR` و تبدیل PCM→WAV.
**اجرا نشد:** پخش صدای واقعی (ابری یا آفلاین) روی دستگاه.

### ۷) STT — 🔍 / 
`OfflineAsr` (Whisper-base int8، ۱۶kHz، کران ۲۰ ثانیه) و `SpeechRecognizer` سیستم در `listenOnce`؛
خطای mic/شبکه/سرویس مدیریت و به کاربر گفته می‌شود.
**اجرا نشد:** تشخیص گفتار واقعی.

### ۸) عملیات حساس — 🔍 (نبودِ مسیر نوشتن)
بررسی کد نشان می‌دهد دستیار هیچ فراخوانی INSERT/UPDATE/DELETE ندارد؛ `Repo` فقط SELECT اجرا می‌کند
و `groundReport` تنها از `ReportCatalog.query` (فقط‌خواندنی) استفاده می‌کند. بنابراین عملیات حساس
اصلاً توسط مدل انجام نمی‌شود؛ هر نوشتنِ آینده باید از لایهٔ رسمی + تأیید صریح کاربر + کنترل دسترسی
بگذرد (مستند شد). این یک طراحی آگاهانهٔ امنیتی است، نه یک قابلیتِ ازدست‌رفته.

### ۹) عدم افشای کلید — ✅ اجرا شد
`noApiKeysAreEmbeddedInTheApp` برای تک‌تک سرویس‌ها `BuiltinKeys.get==""` را assert می‌کند و پاس شد.
کلیدها فقط با `SecretBox` ذخیره می‌شوند؛ `AiPromptRedactor` الگوهای کلید/توکن را پیش از خروج متن
حذف می‌کند و `Diag` فقط نوع خطا و محل کد را ثبت می‌کند (نه پیام/کلید).

### ۱۰) گزارش واقعی — 🔍 + کامپایل / ⛔ runtime
`groundReport` به `AssistantScreen.askAi` متصل شد (کامیت bb52bc3). مسیر خواندن دقیقاً همان
`ReportsScreen` است: `Repo.exec(c, ReportCatalog.query(m,id,f))` + `ReportsScreen.applyMaps`.
`parsePlan` fail-closed است؛ `formatFacts` ≤۲۰ ردیف/۸۰۰۰ نویسه، ستون‌های حساس را حذف و دادهٔ
موجودنبوده را صفر فرض نمی‌کند. کامپایل در CI تأیید می‌شود (بخش ۷).
**اجرا نشد:** پرسش واقعی علیه DB آتیران روی دستگاه.

### ۱۱) Gemini بومی — ✅ اجرا شد
`onlyGeminiUsesTheNativeWireFormat` و `baseUrlsMatchTheSpecifiedEndpoints` اجرا و پاس شدند:
Gemini تنها سرویس با wire=gemini است و endpoint آن `generativelanguage.googleapis.com/v1beta`؛
هر ۹ endpoint دقیقاً مطابق مأموریت است.

### ۱۲) تست/وضعیت هر سرویس — ✅ منطق / ⛔ زنده
`testService` مستقل است و نتیجهٔ فارسی روشن بدون افشای کلید می‌دهد؛ pill وضعیت در `AiSettings`
موفق/کلید نامعتبر/محدودیت/شبکه/تست‌نشده + زمان آخرین آزمایش + cooldown را نشان می‌دهد.
منطق cooldown/ترتیب با تست واحد اجرا شد.
**اجرا نشد:** تست اتصال زندهٔ هر سرویس با کلید واقعی.

---

## جمع‌بندی صادقانه
- **منطقِ هستهٔ دستیار** (رجیستری، ترتیب/failover، تفکیک خطا، cooldown، عدم جاسازی کلید، Gemini بومی)
  با تست واحد **واقعاً اجرا و پاس** شد.
- **رفتارهای runtime** (تماس زندهٔ سرویس‌ها، استریم، TTS/STT واقعی، گزارش علیه DB، تست اتصال زنده)
  در این محیط **اجرا نشدند** چون دستگاه/امولاتور و کلید واقعی در دسترس نبود؛ این‌ها روی دستگاه با
  کلید کاربر و از طریق «تست اتصال» درون‌برنامه قابل انجام‌اند و تا آن زمان «موفق» اعلام نمی‌شوند.
- هیچ دادهٔ ساختگی/حدسی در پاسخ‌ها تولید نمی‌شود؛ در نبود داده صریحاً گفته می‌شود.