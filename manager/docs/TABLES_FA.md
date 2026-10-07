# نگاشت بخش‌ها به جدول‌های آتیران

این اپ فقط **خواندن** انجام می‌دهد (`SELECT` — گارد `Repo.guard`) و مستقیم با
درایور jTDS به SQL Server آتیران وصل می‌شود. هر بخش با الگوی `soft()` کار می‌کند:
اگر جدول یا ستونی در نسخه آتیران شما نباشد، فقط همان کارت با پیام فارسی
«در دسترس نیست» نمایش داده می‌شود و بقیه اپ سالم می‌ماند.

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
| کاربران | `sys_users` ،`LoginDetails` (+ `Log`/`TableChanges` در برخی گزارش‌ها) | کاربران سیستم و ورودها |
| سود و زیان | `VW_GainDetails` ،`VW_CustomersGain` ،`sailfact` ،`subsailfact` ،`inventory` | اگر ویو نباشد، محاسبه جایگزین از بهای خرید انجام می‌شود |
| گزارش‌ها | همه بالا + `ActNames` ،`COW` ،`dif_date_alan` ،`BANK` ،`ban_act` | ۳۴ گزارش آماده؛ هر گزارش نبود جدول را جداگانه گزارش می‌دهد |
| تنظیمات | — | فقط اتصال و قفل؛ بدون کوئری داده |

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
- `sys_users` / `LoginDetails`: `user_id`
- `custgroup` / `kagroup` / `masir` / `anbars` / `BANK` / `ban_act`: کلیدهای `*_rdf`

> نام ستون‌ها به حروف کوچک/بزرگ حساس نیست (`Meta` تطبیق منعطف انجام می‌دهد)
> و برای بیشتر فیلدها چند نام جایگزین امتحان می‌شود (`colFlex`).
> برای بررسی سازگاری دیتابیس خودتان، اسکریپت `tools/probe_atiran.py` را اجرا کنید.
