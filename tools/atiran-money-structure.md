# ساختار دریافت و پرداخت در دیتابیس آتیران (از پشتیبان پیوست‌شده)

منبع: `manager/app/src/test/resources/atiran/sample-schema.json` (از `Atiran2-1405-7-14-15-21-49.zip`).
فقط ساختار (نام جدول/ستون و کلیدهای خارجی)؛ هیچ ردیف داده‌ای اینجا نیست.

## زنجیرهٔ سند
کلید سند: `(ghno, p, Rdf_)`

| جدول | نقش | ستون‌های کلیدی | وابستگی (FK) |
|---|---|---|---|
| `dbo.dar` | سرآیند قبض دریافت/پرداخت | `ghno`, `p`, `Rdf_`, `date`, `done_date`, `shmo`, `naghd`, `ted_chk`, `mabcheck`, `mab`, `shfac`, `darDescriptionTypeID`, `UserID`, `IsFinal`, `EbtalDis`, `Active`, `TakhfifDaramad`, `TakhfifHazineh` | `darDescriptionTypeID→darDescriptionType.rowId`، `UserID→sys_users`، `shmo→CUSTOMERS.SHMO` |
| `dbo.PosDetails` | ردیف‌های POS (کارت‌خوان) | `ghno`, `p`, `Rdf_`, `MabPos`, `PosBankRdf`, `IsHavaleh`, `PosDesc`, `TerminalID` | `(ghno,p,Rdf_)→dar`، `PosBankRdf→BANK.RDF` |
| `dbo.havaleh` | ردیف‌های حواله | `ghno`, `p`, `Rdf_`, `mab`, `shhavaleh`, `shmo`, `bankname`, `shobe`, `date`, `done_date`, `babat`, `rdf_bank` | `(ghno,p,Rdf_)→dar` |
| `dbo.getchk` | چک‌های دریافتی (چک‌های ورودی) | `getchkmab`, `sardate`, `getchkshhes`, `getchbank`, `getchkshobe`, `shmo`, `chk_satus`, `ghno`, `p`, `Rdf_`, `done_date`, `back`, `kharj_date` | `UserID→sys_users`، `MoeinId` |
| `dbo.putchk` | چک‌های پرداختی | `putchkmab`, `putdate`, `sardate`, `shputchk`, `bankrdf`, `shmo`, `putchk_status`, `ghno`, `Rdf_`, `done_date`, `chkbatch_num` | `TafsilID`، `MoeinId` |
| `dbo.chkbatch` | دستهٔ چک (دسته‌چک بانک) | `rdf`, `rdf_bank`, `chkbatch_num`, `type`, `BANKRDF` | — |
| `dbo.ban_act` | حرکت حساب بانکی | `bank_rdf`, `act_date`, `act_bes`, `act_bed`, `Ghno`, `DocNumber` | `bank_rdf→BANK.RDF` |
| `dbo.BANK` / `dbo.BANK_NAME` | بانک‌ها | `RDF`, `BANKNAME`, `SHOBE`, `SHHE`, `BankRdf` | `BankRdf→BANK_NAME.RDF` |
| `dbo.darDescriptionType` | نوع/شرح قبض | `rowId`, `Description`, `p`, `moeinID` | — |

## نام‌های درست برای کد اپ
| مفهوم در اپ | کد فعلی (غلط) | ستون واقعی |
|---|---|---|
| نقدی | `dar.MabNaghdi` | `dar.naghd` |
| POS | `dar.MabPos` | `SUM(PosDetails.MabPos)` با `(ghno,p,Rdf_)` |
| حواله | `dar.MabHavaleh` | `SUM(havaleh.mab)` با `(ghno,p,Rdf_)` |
| مبلغ چک | `dar.SumMabcheck` | `dar.mabcheck` |
| تعداد چک | `dar.TedadChk` | `dar.ted_chk` |
| شرح | `dar.DarDesc` | `darDescriptionType.Description` با `dar.darDescriptionTypeID` |
| کاربر | `dar.user` | `dar.UserID` (→ `sys_users`) |
| مبلغ کل سند | `dar.mab` | `dar.mab` (درست) |

## کنترل‌های مستقل (برای تأیید بدون حدس)
- `dbo.ghabz_e_daryaft` (قبض دریافت) و `dbo.ghabz_e_pardakht` (قبض پرداخت): ستون‌های `naghd`، `pos`، `mabhavaleh`، `mabchk`، `mab`، `ted_chk`. این‌ها قبض‌های چاپی آتیران هستند و جمع آن‌ها باید با سند مطابقت کند.
- `accounting.DaryaftVaPardakht` (کلید `Ghno, Rdf_, IsDaryaft`): `Naghd`، `SumCheck`، `SumPos`.
- `dbo.Vw_DaryaftUnSubmit`: `naghd`، `mabcheck`، `mab`، `ted_chk`، `SumPos`.

## هنوز تأیید نشده (فقط با شمارش روی داده، بدون نمایش ردیف)
1. معنای `dar.p` (دریافت/پرداخت). سرنخ: `ghabz_e_daryaft` و `ghabz_e_pardakht` جدا هستند و `accounting.DaryaftVaPardakht.IsDaryaft` هم وجود دارد.
2. پیوند `getchk`/`putchk` به `dar`: کلید دقیق (`ghno` تنها یا `(ghno,p,Rdf_)`). در لیست کلیدهای خارجی پیوندی صریح نیست.
3. اینکه `dar.mab` = `naghd + SumPos + havaleh + mabcheck` است یا با تخفیف (`TakhfifDaramad`/`TakhfifHazineh`) تفاوت دارد.
