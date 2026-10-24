/* ==========================================================================
   Atiran read-only discovery script (SQL Server 2014 compatible)
   Purpose : collect everything needed to build/verify reports accurately.
   Safety  : SELECT only. No DDL/DML. Aggregated counts only; no personal data
             (names, phones, account numbers) is selected by these queries.
   Run     : sqlcmd -S 37.143.147.19,1433 -U <user> -d Atiran -i atiran_discovery.sql
             -o atiran_discovery_out.txt -y 0 -s "|" -W
   ========================================================================== */
SET NOCOUNT ON;

PRINT '== 0. Environment';
SELECT @@VERSION AS version_info, DB_NAME() AS database_name, DATABASEPROPERTYEX(DB_NAME(),'Collation') AS collation;
SELECT * FROM fn_my_permissions(NULL, 'DATABASE');

PRINT '== 1. Every module (views, functions, procedures, triggers) with its text length';
SELECT s.name AS schema_name, o.name AS object_name, o.type_desc, LEN(m.definition) AS text_len
FROM sys.sql_modules m
JOIN sys.objects o ON o.object_id = m.object_id
JOIN sys.schemas s ON s.schema_id = o.schema_id
ORDER BY s.name, o.name;

PRINT '== 2. Modules that touch the money/stock/customer keywords (full text in output)';
SELECT s.name AS schema_name, o.name AS object_name, o.type_desc, m.definition
FROM sys.sql_modules m
JOIN sys.objects o ON o.object_id = m.object_id
JOIN sys.schemas s ON s.schema_id = o.schema_id
WHERE m.definition LIKE N'%tamam_joz%'
   OR m.definition LIKE N'%TamamJoz%'
   OR m.definition LIKE N'%JOZPRICE%'
   OR m.definition LIKE N'%litakhma%'
   OR m.definition LIKE N'%IsHavaleh%'
   OR m.definition LIKE N'%Karmozd%'
   OR m.definition LIKE N'%Rdf_%'
   OR m.definition LIKE N'%Mandeh%'
   OR m.definition LIKE N'%MaxSarCheck%'
   OR m.definition LIKE N'%CreditCheckType%'
   OR m.definition LIKE N'%Etebar%'
   OR m.definition LIKE N'%ShowInReport%'
   OR m.definition LIKE N'%sanadno%'
   OR m.definition LIKE N'%TakhfifDaramad%'
   OR m.definition LIKE N'%chk_satus%'
   OR m.definition LIKE N'%putchk_status%'
ORDER BY s.name, o.name;

PRINT '== 3. Triggers (they can fill computed columns on insert/update)';
SELECT OBJECT_SCHEMA_NAME(t.parent_id) + N'.' + OBJECT_NAME(t.parent_id) AS table_name,
       t.name AS trigger_name, t.is_disabled, OBJECT_DEFINITION(t.object_id) AS definition
FROM sys.triggers t
WHERE t.parent_class = 1;

PRINT '== 4. Computed columns and check/default constraints';
SELECT OBJECT_SCHEMA_NAME(object_id) + N'.' + OBJECT_NAME(object_id) AS table_name, name AS column_name, definition
FROM sys.computed_columns;
SELECT OBJECT_NAME(parent_object_id) AS table_name, name, definition FROM sys.check_constraints;
SELECT OBJECT_NAME(parent_object_id) AS table_name, name, definition FROM sys.default_constraints;

PRINT '== 5. Column and table descriptions (extended properties, may be in Persian)';
SELECT OBJECT_SCHEMA_NAME(ep.major_id) AS schema_name, OBJECT_NAME(ep.major_id) AS table_name,
       CASE WHEN ep.minor_id = 0 THEN N'(table)' ELSE COL_NAME(ep.major_id, ep.minor_id) END AS column_name,
       CAST(ep.value AS nvarchar(4000)) AS description
FROM sys.extended_properties ep
WHERE ep.class = 1 AND ep.name IN (N'MS_Description', N'Description', N'desc', N'Caption');

PRINT '== 6. Table sizes (row counts)';
SELECT s.name AS schema_name, t.name AS table_name, SUM(p.rows) AS row_count
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
GROUP BY s.name, t.name
ORDER BY row_count DESC;

PRINT '== 7. Code-column value domains (counts only). Map each value to its meaning.';
SELECT 'dar.p' AS col, CAST(p AS nvarchar(50)) AS val, COUNT(*) AS n FROM dbo.dar GROUP BY p
UNION ALL SELECT 'dar.Active', CAST(Active AS nvarchar(50)), COUNT(*) FROM dbo.dar GROUP BY Active
UNION ALL SELECT 'dar.IsFinal', CAST(IsFinal AS nvarchar(50)), COUNT(*) FROM dbo.dar GROUP BY IsFinal
UNION ALL SELECT 'PosDetails.IsHavaleh', CAST(IsHavaleh AS nvarchar(50)), COUNT(*) FROM dbo.PosDetails GROUP BY IsHavaleh
UNION ALL SELECT 'BANK.IsPos', CAST(IsPos AS nvarchar(50)), COUNT(*) FROM dbo.BANK GROUP BY IsPos
UNION ALL SELECT 'getchk.chk_satus', CAST(chk_satus AS nvarchar(50)), COUNT(*) FROM dbo.getchk GROUP BY chk_satus
UNION ALL SELECT 'getchk.back', CAST(back AS nvarchar(50)), COUNT(*) FROM dbo.getchk GROUP BY back
UNION ALL SELECT 'putchk.putchk_status', CAST(putchk_status AS nvarchar(50)), COUNT(*) FROM dbo.putchk GROUP BY putchk_status
UNION ALL SELECT 'sailfact.active', CAST(active AS nvarchar(50)), COUNT(*) FROM dbo.sailfact GROUP BY active
UNION ALL SELECT 'sailfact.Status', CAST(Status AS nvarchar(50)), COUNT(*) FROM dbo.sailfact GROUP BY Status
UNION ALL SELECT 'sailfact.Deleted', CAST(Deleted AS nvarchar(50)), COUNT(*) FROM dbo.sailfact GROUP BY Deleted
UNION ALL SELECT 'buyfact.active', CAST(active AS nvarchar(50)), COUNT(*) FROM dbo.buyfact GROUP BY active
UNION ALL SELECT 'buyfact.Tasviyeh', CAST(Tasviyeh AS nvarchar(50)), COUNT(*) FROM dbo.buyfact GROUP BY Tasviyeh
UNION ALL SELECT 'act_zirsarfasls.kind', CAST(kind AS nvarchar(50)), COUNT(*) FROM dbo.act_zirsarfasls GROUP BY kind
UNION ALL SELECT 'act_zirsarfasls.ShowInReport', CAST(ShowInReport AS nvarchar(50)), COUNT(*) FROM dbo.act_zirsarfasls GROUP BY ShowInReport
UNION ALL SELECT 'zirsarfasls.Active', CAST(Active AS nvarchar(50)), COUNT(*) FROM dbo.zirsarfasls GROUP BY Active
UNION ALL SELECT 'Sys_Mandeh_Customer.CreditCheckType', CAST(CreditCheckType AS nvarchar(50)), COUNT(*) FROM dbo.Sys_Mandeh_Customer GROUP BY CreditCheckType
UNION ALL SELECT 'CUSTOMERS.black_list', CAST(black_list AS nvarchar(50)), COUNT(*) FROM dbo.CUSTOMERS GROUP BY black_list
UNION ALL SELECT 'CUSTOMERS.hesab_status', CAST(hesab_status AS nvarchar(50)), COUNT(*) FROM dbo.CUSTOMERS GROUP BY hesab_status;

PRINT '== 8. Purchase-line cost check: tamam_joz vs JOZPRICE x TEDJOZ on 5 invoices (aggregated)';
SELECT TOP 5 b.shfackh, b.[all] AS invoice_all,
       SUM(s.tamam_joz) AS sum_tamam_joz,
       SUM(s.tamam_joz_tax) AS sum_tamam_joz_tax,
       SUM(s.JOZPRICE * s.TEDJOZ) AS sum_jozprice_x_tedjoz,
       SUM(s.TEDVAH) AS sum_tedvah
FROM dbo.buyfact b
JOIN dbo.subbuyfact s ON s.shfackh = b.shfackh
GROUP BY b.shfackh, b.[all]
ORDER BY b.shfackh DESC;

PRINT '== 9. PosDetails: revision and havaleh behaviour on documents with several POS rows';
SELECT TOP 30 pd.ghno, pd.p, pd.Rdf_, pd.MabPos, pd.IsHavaleh, pd.Karmozd, pd.PosBankRdf,
       b.IsPos, pd.isEdited
FROM dbo.PosDetails pd
LEFT JOIN dbo.BANK b ON b.RDF = pd.PosBankRdf
WHERE pd.ghno IN (SELECT TOP 10 ghno FROM dbo.PosDetails GROUP BY ghno HAVING COUNT(*) > 1 ORDER BY ghno DESC)
ORDER BY pd.ghno, pd.p, pd.Rdf_;

PRINT '== 10. Credit fields: CUSTOMERS.cred vs Sys_Mandeh_Customer (no names selected)';
SELECT TOP 10 c.SHMO, c.cred, c.man, m.Mandeh, m.Etebar, m.CreditCheckType, m.MaxOpenTime, m.BlockResult
FROM dbo.CUSTOMERS c
JOIN dbo.Sys_Mandeh_Customer m ON m.Shmo = c.SHMO
WHERE c.cred > 0 OR m.Etebar > 0
ORDER BY c.SHMO DESC;

PRINT '== 11. zirsarfasls.man vs real movement balance (3 sub-accounts with movements)';
SELECT TOP 10 z.rdf, z.man AS zir_man,
       SUM(a.bed) AS movement_bed, SUM(a.bes) AS movement_bes,
       SUM(a.bed) - SUM(a.bes) AS movement_balance
FROM dbo.zirsarfasls z
JOIN dbo.act_zirsarfasls a ON a.rdf_zirsarfasls = z.rdf
GROUP BY z.rdf, z.man
ORDER BY COUNT(*) DESC;

PRINT '== 12. Atiran built-in report definitions (the vendor''s own SQL, if stored)';
SELECT TOP 50 * FROM Reports.DefaultReports;
SELECT TOP 50 * FROM Reports.EditedReports;

PRINT '== 13. Foreign-key and index metadata for the core tables (to confirm join paths)';
SELECT OBJECT_NAME(fk.parent_object_id) AS from_table, COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS from_column,
       OBJECT_NAME(fk.referenced_object_id) AS to_table, COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) AS to_column
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
WHERE OBJECT_NAME(fk.parent_object_id) IN ('dar','PosDetails','getchk','putchk','sailfact','subsailfact','buyfact','subbuyfact',
      'act_zirsarfasls','zirsarfasls','sarfasls','GroupSarfasl','CUSTOMERS','Sys_Mandeh_Customer','cust_act','ka_act','BANK')
ORDER BY 1, 2;
