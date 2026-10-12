# نگاشت بخش‌ها به جدول‌های آتیران

خواندن جدول‌های عملیاتی آتیران فقط با `SELECT` انجام می‌شود (گارد `Repo.guard`) و اتصال با jTDS مستقیم به SQL Server آتیران است. نوشتن محدود فقط در جدول‌های جانبیِ خود Meelano برای صف/تحویل/شمارش/بستن روز انجام می‌شود؛ هیچ مسیر بستن روز یا تأیید شمارش به‌تنهایی موجودی یا اسناد اصلی آتیران را تغییر نمی‌دهد. هر بخش با الگوی `soft()` کار می‌کند:
اگر جدول یا ستونی در نسخه آتیران شما نباشد، فقط همان کارت با پیام فارسی
«در دسترس نیست» نمایش داده می‌شود و بقیه اپ سالم می‌ماند.

## مبنای واقعی schema و تفاوت schemaها

فایل `app/src/test/resources/atiran/sample-schema.json` از backup نمونه در
SQL Server دورریختنی استخراج شده و **فقط metadata** دارد: ۶۵۱ جدول/ویو،
۹۰۰۱ ستون، ۶۶۵ رابطهٔ کلیدخارجی و ۲۹۰ تابع؛ هیچ ردیف عملیاتی در manifest
ذخیره نشده است. نمونه ۱۰ schema دارد. این manifest برای آزمون/بازبینی است،
نه ادعای برابری با نسخهٔ سرور اصلی مشتری.

`Meta` نام واقعی و schema را از کاتالوگ همان دیتابیس resolve می‌کند، `dbo`
را در صورت ابهام در اولویت می‌گذارد و شناسهٔ SQL را quote می‌کند. پس نباید
فرض کرد همهٔ اشیا `dbo` هستند: نمونه، `security.LoginDetails` و
`security.Log` را دارد؛ `dbo.sys_users.role_id` نیز با FK به
`dbo.Roles.id` وصل است. resolver برای functionها هم schema واقعی را می‌یابد.

در آزمون اتصال، preflight فقط یک پرس‌وجوی کاتالوگیِ read-only انجام می‌دهد:
۱۲ بخش اصلی، ستون‌های ضروری و مجوز `SELECT` را می‌سنجد؛ هیچ `COUNT(*)` یا
ردیف عملیاتی نمی‌خواند. نبود مجوز مشاهدهٔ metadata (`VIEW DEFINITION`) یا
خواندن، در نتیجهٔ آزمون به‌عنوان مشکل ساختار/مجوز نشان داده می‌شود.

## جدول‌های کلیدی هر بخش

| بخش | جدول‌های اصلی | توضیح |
|---|---|---|
| داشبورد | `sailfact` ،`buyfact` ،`dar` ،`CUSTOMERS` ،`visitors` ،`getchk` ،`putchk` | فروش/خرید/دریافت/پرداخت روز، بدهکاران، معوق‌ها، سررسید چک‌ها |
| فروش | `sailfact` ،`subsailfact` ،`CUSTOMERS` ،`visitors` | فاکتورها، اقلام (`TEDVAH`/`LINESUM`)، معوق‌ها (`t_date`/`tasvieh`) |
| خرید | `buyfact` (+ زیـرجدول اقلام) ،`CUSTOMERS` | مشابه فروش در سمت خرید |
| دریافت‌ها / پرداخت‌ها | `dar` (`p=0/1`) ،`PosDetails` ،`darDescriptionType` ،`CUSTOMERS` | قبوض (`ghno`)، کارت‌خوان (`MabPos`/`PosBankRdf`) |
| چک‌ها | `getchk` ،`putchk` ،`VW_getchk` ،`VW_Putchk` ،`CheckTypes` ،`BANK` ،`CUSTOMERS` | دسته‌بندی صندوق/بانک/وصول/خرج/برگشتی از `st` و `back` |
| کالاها | `inventory` ،`ka_act` ،`kagroup` ،`anbars` ،`inventory_anbars` / `VW_InventoryAnbars` | موجودی (`mojkavah`)، گردش کالا، ارزش گروهی |
| مشتریان | `CUSTOMERS` ،`cust_act` ،`custgroup` ،`masir` ،`visitors` ،`sailfact` ،`dar` ،`getchk` | پرونده کامل + گردش (`act_bed`/`act_bes`) |
| ویزیتورها | `visitors` ،`vis_goals` ،`sailfact` ،`CUSTOMERS` ،`dar` | عملکرد، وصول، اهداف |
| کاربران | `sys_users` ،`Roles` ،`LoginDetails` (+ `Log`/`TableChanges` در برخی گزارش‌ها) | در نمونه، نقش‌ها در `dbo.Roles` و ورود/گزارش در schema `security` هستند |
| سود و زیان | `VW_GainDetails` ،`VW_CustomersGain` ،`sailfact` ،`subsailfact` ،`inventory` | اگر ویو نباشد، محاسبه جایگزین از بهای خرید انجام می‌شود |
| گزارش‌ها | همه بالا + `ActNames` ،`COW` ،تابع `dif_date_alan` ،`BANK` ،`ban_act` | ۳۴ گزارش آماده؛ هر گزارش نبود جدول/تابع را جداگانه گزارش می‌دهد |
| تنظیمات | — | فقط اتصال و قفل؛ بدون کوئری داده |

## جدول‌های جانبی Meelano

| جدول | استفاده | اثر روی دادهٔ اصلی آتیران |
|---|---|---|
| `MeelanoDailyClose` | آخرین snapshot بستن هر روز | ندارد |
| `MeelanoDailyCloseHistory` | تاریخچهٔ افزایشی بستن/اصلاحیه‌های روز | ندارد |
| `MeelanoWhCount` | صف بررسی شمارش کور انبار | تأیید، موجودی را تعدیل نمی‌کند |
| `MeelanoHandover` | وضعیت مرکزی تحویل فاکتور | لاگ جانبی؛ فاکتور را تغییر نمی‌دهد |

ساخت جدول‌های جانبی در صورت داشتن مجوز انجام می‌شود؛ در نبود مجوز، همان
قابلیتِ مرکزی غیرفعال می‌ماند و برنامه صفر یا موفقیت ساختگی نشان نمی‌دهد.

## ستون‌های اجباری (`must` — نبودشان آن کارت را غیرفعال می‌کند)

- `sailfact`: `date` ،`shfacfo` ،`all` ،`shmo` ،`t_date` ،`tasvieh` ،`vis_rdf`
- `subsailfact`: `shfacfo` ،`SHKA` ،`TEDVAH` ،`LINESUM`
- `dar`: `ghno` ،`date` ،`shmo`
- `PosDetails`: `ghno` ،`MabPos` ،`PosBankRdf`
- `CUSTOMERS`: `SHMO` ،`man`
- `cust_act`: `shmo` ،`date` ،`act_bed` ،`act_bes`
- `inventory`: `shka` ،`naka` ،`mojkavah`
- `ka_act`: `shka`
- `visitors`: `vis_rdf` — `vis_goals`: طبق تعریف آتیران
- `sys_users` / `LoginDetails`: شناسهٔ کاربر؛ در نمونه ستون ورود `security.LoginDetails.UserID` است و `colFlex` تفاوت زیرخط را هم تطبیق می‌دهد.
- `custgroup` / `kagroup` / `masir` / `anbars` / `BANK` / `ban_act`: کلیدهای `*_rdf`

> نام ستون‌ها به حروف کوچک/بزرگ حساس نیست (`Meta` تطبیق منعطف انجام می‌دهد)
> و برای بیشتر فیلدها چند نام جایگزین امتحان می‌شود (`colFlex`).
> برای بررسی سازگاری دیتابیس خودتان، اسکریپت `tools/probe_atiran.py` را اجرا کنید.
