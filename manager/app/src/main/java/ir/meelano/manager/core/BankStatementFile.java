package ir.meelano.manager.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Safe, offline importer for bank-export CSV/TSV and Excel .xlsx files.
 * It normalizes rows in memory only. Callers decide what aggregate to save locally.
 */
public final class BankStatementFile {
    public static final int MAX_FILE_BYTES = 12 * 1024 * 1024;
    public static final int MAX_UNCOMPRESSED_BYTES = 24 * 1024 * 1024;
    public static final int MAX_ROWS = 100_000;
    public static final int MAX_COLUMNS = 64;
    public static final int MAX_PERIOD_DAYS = 366;
    public static final double AMOUNT_TOLERANCE_RIALS = 0.5d;
    /** Conservative Rial bound shared by the parser, period comparison, and local persistence. */
    public static final BigDecimal MAX_AMOUNT_RIALS = new BigDecimal("999999999999999999");
    private static final BigDecimal TOMAN_TO_RIAL = BigDecimal.TEN;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public static final int DATE_AUTO = 0;
    public static final int DATE_JALALI_YMD = 1;
    public static final int DATE_JALALI_DMY = 2;
    public static final int DATE_GREGORIAN_YMD = 3;
    public static final int DATE_GREGORIAN_DMY = 4;
    public static final int DATE_GREGORIAN_MDY = 5;
    public static final int DATE_EXCEL_SERIAL = 6;
    public static final int DATE_JALALI_MDY = 7;

    public static final int AMOUNT_AUTO = 0;
    public static final int AMOUNT_DECIMAL_DOT = 1;
    public static final int AMOUNT_DECIMAL_COMMA = 2;
    public static final int AMOUNT_GROUPING_ONLY = 3;

    public static final class DateRange {
        public final String fromDay;
        public final String toDay;

        private DateRange(String fromDay, String toDay) {
            this.fromDay = fromDay == null ? "" : fromDay;
            this.toDay = toDay == null ? "" : toDay;
        }

        public boolean available() { return !fromDay.isEmpty() && !toDay.isEmpty(); }
    }

    public static final class Table {
        public final List<List<String>> rows;
        public final List<String> headers;
        public final int headerRow;
        public final boolean xlsx;
        public final String delimiter;

        private Table(List<List<String>> rows, boolean xlsx, String delimiter) throws IOException {
            if (rows == null || rows.isEmpty()) throw new IOException("فایل ردیفی برای خواندن ندارد");
            if (rows.size() > MAX_ROWS) throw new IOException("فایل بیش از حد بزرگ است؛ حداکثر ۱۰۰٬۰۰۰ ردیف پذیرفته می‌شود");
            this.rows = Collections.unmodifiableList(rows);
            this.xlsx = xlsx;
            this.delimiter = delimiter == null ? "" : delimiter;
            int found = detectHeaderRow(rows);
            if (found < 0) throw new IOException("سربرگ ستون‌ها پیدا نشد؛ از قالب ورود بانکی برنامه استفاده کنید");
            this.headerRow = found;
            List<String> h = new ArrayList<>();
            List<String> source = rows.get(found);
            for (int i = 0; i < source.size(); i++) {
                String value = source.get(i) == null ? "" : source.get(i).trim();
                h.add(value.isEmpty() ? "ستون " + (i + 1) : value);
            }
            this.headers = Collections.unmodifiableList(h);
        }
    }

    /** Column indexes are zero-based; -1 means not mapped. Amounts are always normalized to Rials. */
    public static final class Mapping {
        public int date = -1;
        public int credit = -1;
        public int debit = -1;
        public int amount = -1;
        public int direction = -1;
        public int balance = -1;
        public int description = -1;
        public int reference = -1;
        public int account = -1;
        public String accountValue = "";
        public int dateFormat = DATE_AUTO;
        public int amountFormat = AMOUNT_AUTO;
        public boolean signedAmountDefinesDirection;
        public boolean amountsInToman;
        public boolean amountUnitConfirmed;

        public boolean valid() {
            if (date < 0 || !amountUnitConfirmed
                    || dateFormat < DATE_AUTO || dateFormat > DATE_JALALI_MDY
                    || amountFormat < AMOUNT_AUTO || amountFormat > AMOUNT_GROUPING_ONLY) return false;
            if (accountValue != null && !accountValue.trim().isEmpty() && account < 0) return false;
            boolean split = credit >= 0 && debit >= 0 && credit != debit
                    && credit != date && debit != date;
            boolean single = amount >= 0 && amount != date && amount != direction
                    && (direction >= 0 && direction != date || signedAmountDefinesDirection);
            return split || single;
        }

        public static Mapping suggested(List<String> headers) {
            Mapping m = new Mapping();
            m.date = find(headers, new String[]{"تاریخ تراکنش", "تاریخ عملیات", "تاریخ", "تاريخ", "date", "transactiondate", "operationdate"});
            m.credit = findMoneyColumn(headers, new String[]{"واریز", "بستانکار", "deposit", "credit", "inflow", "دریافت"});
            m.debit = findMoneyColumn(headers, new String[]{"برداشت", "بدهکار", "withdraw", "debit", "outflow", "پرداخت"});
            m.amount = find(headers, new String[]{"مبلغتراکنش", "مبلغ", "amount", "transactionamount", "value"});
            m.direction = find(headers, new String[]{"جهت", "نوعتراکنش", "نوععملیات", "نوع", "direction", "transactiontype", "operationtype", "type", "debitcredit", "crdr"});
            m.balance = find(headers, new String[]{"مانده", "balance", "availablebalance"});
            m.description = find(headers, new String[]{"شرح", "توضیحات", "شرحعملیات", "description", "details", "narrative"});
            m.reference = find(headers, new String[]{"شمارهپیگیری", "پیگیری", "شمارهمرجع", "شناسهتراکنش", "reference", "trace", "rrn", "refno"});
            m.account = find(headers, new String[]{"شمارهحساب", "حساب", "شبا", "iban", "accountnumber", "account", "accountno"});
            return m;
        }
    }

    public static final class Transaction {
        public final int rowNumber;
        public final String day;
        public final BigDecimal creditRialsExact;
        public final BigDecimal debitRialsExact;
        /** Double views remain for existing read-only UI consumers; all arithmetic uses exact values. */
        public final double creditRials;
        public final double debitRials;
        public final String description;
        public final String reference;
        public final BigDecimal balanceRialsExact;
        public final Double balanceRials;

        private Transaction(int rowNumber, String day, BigDecimal creditRials, BigDecimal debitRials,
                            String description, String reference, BigDecimal balanceRials) {
            this.rowNumber = rowNumber;
            this.day = day;
            this.creditRialsExact = creditRials;
            this.debitRialsExact = debitRials;
            this.creditRials = creditRials.doubleValue();
            this.debitRials = debitRials.doubleValue();
            this.description = description;
            this.reference = reference;
            this.balanceRialsExact = balanceRials;
            this.balanceRials = balanceRials == null ? null : balanceRials.doubleValue();
        }
    }

    public static final class DailyTotal {
        public final String day;
        public final BigDecimal creditRialsExact;
        public final BigDecimal debitRialsExact;
        /** Convenience/display doubles; never use these for persistence or reconciliation. */
        public final double creditRials;
        public final double debitRials;
        public final int transactions;

        private DailyTotal(String day, BigDecimal creditRials, BigDecimal debitRials, int transactions) {
            this.day = day;
            this.creditRialsExact = creditRials;
            this.debitRialsExact = debitRials;
            this.creditRials = creditRials.doubleValue();
            this.debitRials = debitRials.doubleValue();
            this.transactions = transactions;
        }
    }

    public static final class Parsed {
        public final List<Transaction> transactions;
        public final Map<String, DailyTotal> daily;
        public final List<String> warnings;
        public final int skippedRows;
        public final int invalidRows;
        public final int outsideRangeRows;
        public final int filteredAccountRows;
        public final BigDecimal totalCreditRialsExact;
        public final BigDecimal totalDebitRialsExact;
        public final double totalCreditRials;
        public final double totalDebitRials;
        public final String minDay;
        public final String maxDay;
        public final String errorSummary;
        public final int transactionCount;

        private Parsed(List<Transaction> transactions, Map<String, DailyTotal> daily,
                       List<String> warnings, int skippedRows, int invalidRows,
                       int outsideRangeRows, int filteredAccountRows,
                       BigDecimal totalCreditRials, BigDecimal totalDebitRials,
                       String minDay, String maxDay, String errorSummary, int transactionCount) {
            this.transactions = Collections.unmodifiableList(transactions);
            this.daily = Collections.unmodifiableMap(daily);
            this.warnings = Collections.unmodifiableList(warnings);
            this.skippedRows = skippedRows;
            this.invalidRows = invalidRows;
            this.outsideRangeRows = outsideRangeRows;
            this.filteredAccountRows = filteredAccountRows;
            this.totalCreditRialsExact = totalCreditRials;
            this.totalDebitRialsExact = totalDebitRials;
            this.totalCreditRials = totalCreditRials.doubleValue();
            this.totalDebitRials = totalDebitRials.doubleValue();
            this.minDay = minDay;
            this.maxDay = maxDay;
            this.errorSummary = errorSummary;
            this.transactionCount = Math.max(0, transactionCount);
        }
    }

    private static final class MutableDaily {
        BigDecimal credit = ZERO;
        BigDecimal debit = ZERO;
        int count;
    }

    private BankStatementFile() { }

    /** Drops private per-transaction details while preserving only the daily movement summary. */
    public static Parsed aggregateOnly(Parsed parsed) {
        if (parsed == null) return null;
        return new Parsed(Collections.emptyList(), parsed.daily, parsed.warnings,
                parsed.skippedRows, parsed.invalidRows, parsed.outsideRangeRows, parsed.filteredAccountRows,
                parsed.totalCreditRialsExact, parsed.totalDebitRialsExact, parsed.minDay, parsed.maxDay,
                parsed.errorSummary, parsed.transactionCount);
    }

    public static Table read(InputStream input, String fileName) throws IOException {
        byte[] bytes = readBounded(input, MAX_FILE_BYTES);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.US);
        if (name.endsWith(".xls") && !name.endsWith(".xlsx"))
            throw new IOException("فایل Excel قدیمی .xls پشتیبانی نمی‌شود؛ از بانک «ذخیره به CSV» یا «ذخیره به .xlsx» را انتخاب کنید");
        boolean zip = bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K';
        if (zip || name.endsWith(".xlsx")) return readXlsx(bytes);
        String text = decodeText(bytes);
        String delimiter = detectDelimiter(text);
        return new Table(parseDelimited(text, delimiter), false, delimiter);
    }

    /** Suggests the earliest and latest parseable dates; the manager still confirms the full period. */
    public static DateRange suggestedRange(Table table, Mapping mapping) throws IOException {
        if (table == null || mapping == null || mapping.date < 0 || mapping.date >= table.headers.size())
            throw new IOException("ستون تاریخ انتخاب نشده است");
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        String minDay = "";
        String maxDay = "";
        for (int ri = table.headerRow + 1; ri < table.rows.size(); ri++) {
            String raw = cell(table.rows.get(ri), mapping.date);
            if (raw.trim().isEmpty()) continue;
            String day = normalizeDate(raw, mapping.dateFormat);
            int number = Jalali.parse(day);
            if (number < 0) continue;
            if (number < min) { min = number; minDay = day; }
            if (number > max) { max = number; maxDay = day; }
        }
        return new DateRange(minDay, maxDay);
    }

    public static Parsed analyze(Table table, Mapping mapping, String fromDay, String toDay) throws IOException {
        if (table == null || mapping == null)
            throw new IOException("فایل یا نگاشت ستون‌ها در دسترس نیست");
        if (!mapping.amountUnitConfirmed)
            throw new IOException("واحد مبالغ را صریحاً به ریال یا تومان تأیید کنید");
        if (!mapping.valid()) throw new IOException("ستون تاریخ و ستون‌های مبلغ/جهت را انتخاب کنید");
        String from = Jalali.disp(fromDay);
        String to = Jalali.disp(toDay);
        int fromNumber = Jalali.parse(from);
        int toNumber = Jalali.parse(to);
        if (fromNumber < 0 || toNumber < 0 || toNumber < fromNumber)
            throw new IOException("بازهٔ صورت‌حساب معتبر نیست؛ تاریخ شروع و پایان را بررسی کنید");
        if (toNumber - fromNumber + 1 > MAX_PERIOD_DAYS)
            throw new IOException("برای امنیت و دقت، هر بار حداکثر ۳۶۶ روز را وارد کنید");
        validateMapping(table, mapping);

        List<Transaction> transactions = new ArrayList<>();
        Map<String, MutableDaily> totals = new TreeMap<>();
        List<String> warnings = new ArrayList<>();
        java.util.Set<String> accountValues = new java.util.LinkedHashSet<>();
        String requestedAccount = mapping.accountValue == null ? "" : accountToken(mapping.accountValue);
        String minDay = "";
        String maxDay = "";
        int skipped = 0;
        int invalid = 0;
        int outside = 0;
        int filteredAccount = 0;
        int matchedAccountRows = 0;
        StringBuilder errors = new StringBuilder();
        BigDecimal totalCredit = ZERO;
        BigDecimal totalDebit = ZERO;

        for (int ri = table.headerRow + 1; ri < table.rows.size(); ri++) {
            List<String> row = table.rows.get(ri);
            if (blankRow(row)) { skipped++; continue; }
            String rawDate = cell(row, mapping.date);
            if (rawDate.trim().isEmpty() && blankAmounts(row, mapping)) { skipped++; continue; }

            if (mapping.account >= 0) {
                String rowAccount = accountToken(cell(row, mapping.account));
                if (rowAccount.isEmpty()) {
                    invalid++;
                    appendError(errors, ri + 1, "شناسهٔ حساب در ردیف خالی است؛ ستون حساب یا فایل را بررسی کنید");
                    continue;
                }
                if (!requestedAccount.isEmpty()) {
                    if (!requestedAccount.equals(rowAccount)) { filteredAccount++; continue; }
                    matchedAccountRows++;
                } else {
                    accountValues.add(rowAccount);
                }
            }

            String day = normalizeDate(rawDate, mapping.dateFormat);
            if (day.isEmpty()) {
                invalid++;
                appendError(errors, ri + 1, mapping.dateFormat == DATE_AUTO
                        ? "تاریخ نامعتبر یا مبهم است؛ قالب تاریخ را صریح انتخاب کنید"
                        : "تاریخ با قالب انتخاب‌شده سازگار نیست");
                continue;
            }
            int dateNumber = Jalali.parse(day);
            if (dateNumber < 0) {
                invalid++;
                appendError(errors, ri + 1, "تاریخ پس از نرمال‌سازی معتبر نیست");
                continue;
            }
            if (dateNumber < fromNumber || dateNumber > toNumber) { outside++; continue; }

            BigDecimal credit;
            BigDecimal debit;
            try {
                if (mapping.credit >= 0 && mapping.debit >= 0) {
                    String rawCredit = cell(row, mapping.credit).trim();
                    String rawDebit = cell(row, mapping.debit).trim();
                    requireSelectedCurrency(rawCredit, mapping);
                    requireSelectedCurrency(rawDebit, mapping);
                    if (missingAmount(rawCredit) && missingAmount(rawDebit))
                        throw new IllegalArgumentException("هر دو مبلغ واریز و برداشت خالی‌اند؛ مبلغ ناموجود به صفر تبدیل نشد");
                    credit = absoluteOptionalExact(rawCredit, mapping.amountFormat);
                    debit = absoluteOptionalExact(rawDebit, mapping.amountFormat);
                    if (credit.signum() < 0 || debit.signum() < 0)
                        throw new IllegalArgumentException("مبلغ منفی در ستون جداگانهٔ واریز/برداشت مجاز نیست؛ جهت ردیف را بررسی کنید");
                    if (credit.signum() > 0 && debit.signum() > 0)
                        throw new IllegalArgumentException("در یک ردیف هم واریز و هم برداشت ثبت شده؛ ستون‌ها را بررسی کنید");
                } else {
                    String rawAmount = cell(row, mapping.amount).trim();
                    requireSelectedCurrency(rawAmount, mapping);
                    if (missingAmount(rawAmount))
                        throw new IllegalArgumentException("مبلغ این ردیف خالی است؛ مبلغ ناموجود به صفر تبدیل نشد");
                    BigDecimal amount = parseAmountExact(rawAmount, mapping.amountFormat);
                    if (amount == null) throw new IllegalArgumentException("مبلغ نامعتبر یا قالب جداکننده مبهم است؛ قالب را صریح انتخاب کنید");
                    int amountSign = amount.signum();
                    boolean positive = amountSign >= 0;
                    if (mapping.direction >= 0) {
                        int movement = direction(cell(row, mapping.direction));
                        if (movement == 0) throw new IllegalArgumentException("جهت واریز/برداشت نامشخص یا متناقض است");
                        if (amountSign < 0 && movement > 0)
                            throw new IllegalArgumentException("علامت منفی مبلغ با جهت واریز ناسازگار است");
                        if (mapping.signedAmountDefinesDirection && (amountSign < 0) != (movement < 0))
                            throw new IllegalArgumentException("علامت مبلغ با ستون جهت ناسازگار است؛ یکی از آن‌ها را مبنا قرار دهید");
                        positive = movement > 0;
                    } else if (!mapping.signedAmountDefinesDirection) {
                        throw new IllegalArgumentException("جهت مبلغ تأیید نشده است");
                    } else if (amountSign == 0) {
                        throw new IllegalArgumentException("علامت مبلغ صفر جهت واریز/برداشت را مشخص نمی‌کند");
                    }
                    BigDecimal value = amount.abs();
                    credit = positive ? value : ZERO;
                    debit = positive ? ZERO : value;
                }
                if (mapping.amountsInToman) {
                    credit = credit.multiply(TOMAN_TO_RIAL);
                    debit = debit.multiply(TOMAN_TO_RIAL);
                }
                if (!finiteAmount(credit) || !finiteAmount(debit))
                    throw new IllegalArgumentException("مبلغ خارج از محدودهٔ مجاز است");
            } catch (IllegalArgumentException bad) {
                invalid++;
                appendError(errors, ri + 1, bad.getMessage());
                continue;
            }
            BigDecimal balance = null;
            if (mapping.balance >= 0 && !cell(row, mapping.balance).trim().isEmpty()) {
                String rawBalance = cell(row, mapping.balance).trim();
                try { requireSelectedCurrency(rawBalance, mapping); }
                catch (IllegalArgumentException mismatch) {
                    if (warnings.size() < 5) warnings.add("ردیف " + (ri + 1) + ": " + mismatch.getMessage());
                    rawBalance = "";
                }
                BigDecimal parsedBalance = rawBalance.isEmpty() ? null
                        : parseAmountExact(rawBalance, mapping.amountFormat);
                if (parsedBalance == null || !finiteAmount(parsedBalance.abs().multiply(
                        mapping.amountsInToman ? TOMAN_TO_RIAL : BigDecimal.ONE))) {
                    if (warnings.size() < 5) warnings.add("ردیف " + (ri + 1) + ": مانده قابل‌خواندن نیست؛ در جمع واریز/برداشت دخالت داده نشد.");
                } else {
                    balance = parsedBalance.multiply(mapping.amountsInToman ? TOMAN_TO_RIAL : BigDecimal.ONE);
                }
            }
            MutableDaily daily = totals.get(day);
            BigDecimal nextDailyCredit = (daily == null ? ZERO : daily.credit).add(credit);
            BigDecimal nextDailyDebit = (daily == null ? ZERO : daily.debit).add(debit);
            BigDecimal nextTotalCredit = totalCredit.add(credit);
            BigDecimal nextTotalDebit = totalDebit.add(debit);
            if (!finiteAmount(nextDailyCredit) || !finiteAmount(nextDailyDebit)
                    || !finiteAmount(nextTotalCredit) || !finiteAmount(nextTotalDebit)) {
                invalid++;
                appendError(errors, ri + 1, "جمع دقیق مبلغ‌ها از محدودهٔ مجاز عبور می‌کند");
                continue;
            }
            if (daily == null) { daily = new MutableDaily(); totals.put(day, daily); }
            Transaction tx = new Transaction(ri + 1, day, credit, debit,
                    cell(row, mapping.description).trim(), cell(row, mapping.reference).trim(), balance);
            transactions.add(tx);
            daily.credit = nextDailyCredit;
            daily.debit = nextDailyDebit;
            daily.count++;
            totalCredit = nextTotalCredit;
            totalDebit = nextTotalDebit;
            if (minDay.isEmpty() || dateNumber < Jalali.parse(minDay)) minDay = day;
            if (maxDay.isEmpty() || dateNumber > Jalali.parse(maxDay)) maxDay = day;
        }
        if (!requestedAccount.isEmpty() && matchedAccountRows == 0)
            throw new IOException("فیلتر حساب در هیچ ردیفی از فایل پیدا نشد؛ هیچ جمعی قابل‌مقایسه نیست");
        if (requestedAccount.isEmpty() && accountValues.size() > 1)
            throw new IOException("فایل شامل چند حساب است؛ برای جلوگیری از جمع‌کردن ناخواسته، ستون حساب و فیلتر دقیق را انتخاب کنید");
        if (transactions.isEmpty() && invalid > 0)
            throw new IOException("در بازهٔ انتخاب‌شده تراکنش معتبر پیدا نشد؛ " + invalid + " ردیف خطادار است. " + errors);
        if (invalid > 0 && warnings.size() < 5)
            warnings.add(invalid + " ردیف به‌علت تاریخ، مبلغ یا جهت نامعتبر کنار گذاشته شد؛ تا بررسی خطاها ذخیره نکنید.");

        Map<String, DailyTotal> immutableDaily = new LinkedHashMap<>();
        for (Map.Entry<String, MutableDaily> entry : totals.entrySet()) {
            MutableDaily v = entry.getValue();
            immutableDaily.put(entry.getKey(), new DailyTotal(entry.getKey(), v.credit, v.debit, v.count));
        }
        return new Parsed(transactions, immutableDaily, warnings, skipped, invalid, outside, filteredAccount,
                totalCredit, totalDebit, minDay, maxDay, errors.toString(), transactions.size());
    }

    private static void validateMapping(Table table, Mapping m) throws IOException {
        int columns = table.headers.size();
        int[] indexes = {m.date, m.credit, m.debit, m.amount, m.direction, m.balance, m.description, m.reference, m.account};
        for (int index : indexes) if (index >= columns) throw new IOException("انتخاب ستون از فایل خارج است");
        if (m.date < 0) throw new IOException("ستون تاریخ انتخاب نشده است");
        if (m.dateFormat < DATE_AUTO || m.dateFormat > DATE_JALALI_MDY
                || m.amountFormat < AMOUNT_AUTO || m.amountFormat > AMOUNT_GROUPING_ONLY)
            throw new IOException("قالب تاریخ یا عدد انتخاب‌شده پشتیبانی نمی‌شود");
        if (!m.amountUnitConfirmed)
            throw new IOException("واحد مبالغ را صریحاً به ریال یا تومان تأیید کنید");
        boolean split = m.credit >= 0 && m.debit >= 0 && m.credit != m.debit
                && m.credit != m.date && m.debit != m.date;
        boolean single = m.amount >= 0 && m.amount != m.date && m.amount != m.direction
                && (m.direction >= 0 && m.direction != m.date || m.signedAmountDefinesDirection);
        if (!split && !single) throw new IOException("دو ستون واریز/برداشت یا مبلغ همراه ستون جهت لازم است");
        if (m.account >= 0 && (m.account == m.date || m.account == m.credit || m.account == m.debit
                || m.account == m.amount || m.account == m.direction || m.account == m.balance
                || m.account == m.description || m.account == m.reference))
            throw new IOException("ستون حساب باید از ستون تاریخ و مبلغ جدا باشد");
        if (m.accountValue != null && !m.accountValue.trim().isEmpty() && m.account < 0)
            throw new IOException("برای فیلتر حساب، ستون شناسهٔ حساب را انتخاب کنید");
    }

    private static boolean blankAmounts(List<String> row, Mapping m) {
        return missingAmount(cell(row, m.credit)) && missingAmount(cell(row, m.debit))
                && missingAmount(cell(row, m.amount));
    }

    private static boolean missingAmount(String raw) {
        if (raw == null) return true;
        String value = raw.trim();
        return value.isEmpty() || "-".equals(value) || "−".equals(value)
                || "—".equals(value) || "–".equals(value);
    }

    private static String cell(List<String> row, int index) {
        return row == null || index < 0 || index >= row.size() || row.get(index) == null ? "" : row.get(index);
    }

    private static boolean blankRow(List<String> row) {
        if (row == null) return true;
        for (String cell : row) if (cell != null && !cell.trim().isEmpty()) return false;
        return true;
    }

    private static String accountToken(String raw) {
        if (raw == null) return "";
        String value = Money.en(raw).toUpperCase(Locale.US);
        StringBuilder token = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) token.append(c);
        }
        return token.toString();
    }

    private static BigDecimal absoluteOptionalExact(String raw, int amountFormat) {
        if (raw == null || raw.trim().isEmpty() || "-".equals(raw.trim()) || "—".equals(raw.trim())) return ZERO;
        BigDecimal amount = parseAmountExact(raw, amountFormat);
        if (amount == null) throw new IllegalArgumentException("مبلغ نامعتبر یا قالب جداکننده مبهم است؛ قالب را بررسی کنید");
        if (amount.signum() < 0)
            throw new IllegalArgumentException("مبلغ منفی در ستون جداگانهٔ واریز/برداشت مجاز نیست؛ علامت را بررسی کنید");
        return amount;
    }

    /** Parse Persian/Arabic digits without using floating point for any monetary arithmetic. */
    public static Double parseAmount(String raw) { return parseAmount(raw, AMOUNT_AUTO); }

    /** Compatibility/display wrapper. New calculations should use parseAmountExact. */
    public static Double parseAmount(String raw, int amountFormat) {
        BigDecimal value = parseAmountExact(raw, amountFormat);
        return value == null ? null : value.doubleValue();
    }

    /**
     * Exact parser. Auto mode deliberately rejects a single separator followed by three digits
     * when that token is also a valid thousands-grouped integer (for example «1,234»).
     */
    public static BigDecimal parseAmountExact(String raw, int amountFormat) {
        if (raw == null || amountFormat < AMOUNT_AUTO || amountFormat > AMOUNT_GROUPING_ONLY) return null;
        String value = Money.en(raw).trim().replace('−', '-').replace('٬', ',').replace('٫', '.')
                .replace('ي', 'ی').replace('ك', 'ک');
        if (value.isEmpty() || "-".equals(value) || "—".equals(value)) return null;
        boolean parenthesized = value.startsWith("(") && value.endsWith(")");
        if (value.contains("(") || value.contains(")")) {
            if (!parenthesized) return null;
            value = value.substring(1, value.length() - 1).trim();
        }
        value = stripCurrency(value);
        if (value == null || value.isEmpty()) return null;
        int sign = parenthesized ? -1 : 1;
        if (value.startsWith("-") || value.startsWith("+")) {
            if (parenthesized) return null;
            if (value.charAt(0) == '-') sign = -1;
            value = value.substring(1).trim();
        }
        if (value.isEmpty()) return null;

        final char groupingSpace = '\u0001';
        StringBuilder bodyBuilder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || c == '\u00a0' || c == '\u202f' || c == '\'')
                bodyBuilder.append(groupingSpace);
            else if ((c >= '0' && c <= '9') || c == '.' || c == ',') bodyBuilder.append(c);
            else return null;
        }
        String body = bodyBuilder.toString();
        if (body.isEmpty()) return null;

        int dotCount = count(body, '.');
        int commaCount = count(body, ',');
        int decimalPosition = -1;
        char decimalSeparator = 0;
        if (amountFormat == AMOUNT_DECIMAL_DOT) decimalSeparator = '.';
        else if (amountFormat == AMOUNT_DECIMAL_COMMA) decimalSeparator = ',';
        else if (amountFormat == AMOUNT_AUTO) {
            if (dotCount > 0 && commaCount > 0) {
                decimalPosition = Math.max(body.lastIndexOf('.'), body.lastIndexOf(','));
                decimalSeparator = body.charAt(decimalPosition);
                int fractionalDigits = body.length() - decimalPosition - 1;
                if (fractionalDigits < 1 || fractionalDigits > 4) return null;
                if (fractionalDigits == 3 && groupedInteger(body, groupingSpace) != null) return null;
            } else {
                char separator = dotCount > 0 ? '.' : commaCount > 0 ? ',' : 0;
                int occurrences = separator == 0 ? 0 : count(body, separator);
                if (occurrences == 1) {
                    int position = body.lastIndexOf(separator);
                    int fractionalDigits = body.length() - position - 1;
                    if (fractionalDigits < 1 || fractionalDigits > 4) return null;
                    if (fractionalDigits == 3 && groupedInteger(body, groupingSpace) != null) return null;
                    decimalPosition = position;
                    decimalSeparator = separator;
                } else if (occurrences > 1) {
                    String grouped = groupedInteger(body, groupingSpace);
                    if (grouped == null) return null;
                    body = grouped;
                }
            }
        }

        if (amountFormat == AMOUNT_GROUPING_ONLY || (amountFormat == AMOUNT_AUTO && decimalSeparator == 0)) {
            String integer = groupedInteger(body, groupingSpace);
            if (integer == null) return null;
            body = integer;
        } else if (decimalSeparator != 0) {
            char other = amountFormat == AMOUNT_DECIMAL_DOT ? ','
                    : amountFormat == AMOUNT_DECIMAL_COMMA ? '.'
                    : decimalSeparator == '.' ? ',' : decimalSeparator == ',' ? '.' : 0;
            int decimalCount = count(body, decimalSeparator);
            if (decimalCount > 1) return null;
            int position = body.lastIndexOf(decimalSeparator);
            if (position >= 0) {
                String fraction = body.substring(position + 1);
                if (fraction.isEmpty() || fraction.length() > 4 || !digitsOnly(fraction)) return null;
                String integer = other == 0 ? groupedInteger(body.substring(0, position), groupingSpace)
                        : groupedInteger(body.substring(0, position), groupingSpace, other);
                if (integer == null) return null;
                body = integer + "." + fraction;
            } else {
                body = other == 0 ? groupedInteger(body, groupingSpace)
                        : groupedInteger(body, groupingSpace, other);
                if (body == null) return null;
            }
        }
        try {
            BigDecimal amount = new BigDecimal(body).multiply(BigDecimal.valueOf(sign));
            if (amount.precision() > 24 || amount.scale() > 4
                    || amount.abs().compareTo(MAX_AMOUNT_RIALS) > 0) return null;
            return amount;
        } catch (Exception invalid) {
            return null;
        }
    }

    private static void requireSelectedCurrency(String raw, Mapping mapping) {
        if (raw == null || raw.trim().isEmpty()) return;
        int marker = explicitCurrency(raw);
        if (marker < 0) throw new IllegalArgumentException("در یک مبلغ هم‌زمان ریال و تومان درج شده است؛ واحد مبهم است");
        if (marker == 1 && mapping.amountsInToman)
            throw new IllegalArgumentException("ردیف صریحاً ریال دارد اما واحد فایل تومان انتخاب شده است");
        if (marker == 2 && !mapping.amountsInToman)
            throw new IllegalArgumentException("ردیف صریحاً تومان دارد اما واحد فایل ریال انتخاب شده است");
    }

    /** 0 = no explicit marker, 1 = Rial, 2 = Toman, -1 = conflicting markers. */
    private static int explicitCurrency(String raw) {
        String lower = Money.en(raw).toLowerCase(Locale.US).replace('ي', 'ی').replace('ك', 'ک');
        boolean toman = lower.contains("تومان")
                || lower.matches(".*(?<![a-z])(?:irt|toman|tomans)(?![a-z]).*");
        boolean rial = lower.contains("ریال")
                || lower.matches(".*(?<![a-z])(?:irr|rial|rials)(?![a-z]).*");
        if (toman && rial) return -1;
        if (toman) return 2;
        return rial ? 1 : 0;
    }

    private static String stripCurrency(String value) {
        String result = value.trim();
        String[] units = {"ریال", "تومان", "tomans", "toman", "rials", "rial", "irr", "irt"};
        int removed = 0;
        for (String unit : units) {
            String lower = result.toLowerCase(Locale.US);
            if (lower.startsWith(unit)) { result = result.substring(unit.length()).trim(); removed++; }
            lower = result.toLowerCase(Locale.US);
            if (lower.endsWith(unit)) { result = result.substring(0, result.length() - unit.length()).trim(); removed++; }
        }
        String lower = result.toLowerCase(Locale.US);
        for (String unit : units) if (lower.contains(unit)) return null;
        return removed > 1 ? null : result;
    }

    private static String groupedInteger(String value, char groupingSpace) {
        return groupedInteger(value, groupingSpace, '\0', '\0');
    }

    private static String groupedInteger(String value, char groupingSpace, char allowedGrouping) {
        return groupedInteger(value, groupingSpace, allowedGrouping, '\0');
    }

    private static String groupedInteger(String value, char groupingSpace, char allowedGrouping, char secondGrouping) {
        if (value == null || value.isEmpty()) return "0";
        StringBuilder digits = new StringBuilder(value.length());
        int groupLength = 0;
        int groups = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
                groupLength++;
                continue;
            }
            boolean grouping = c == groupingSpace || c == allowedGrouping || c == secondGrouping
                    || (allowedGrouping == '\0' && secondGrouping == '\0' && (c == '.' || c == ','));
            if (!grouping || groupLength == 0 || (groups == 0 ? groupLength > 3 : groupLength != 3)) return null;
            groups++;
            groupLength = 0;
        }
        if (groupLength == 0 || (groups > 0 && groupLength != 3)) return null;
        return digits.toString();
    }

    private static int count(String value, char needle) {
        int result = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == needle) result++;
        return result;
    }

    private static boolean digitsOnly(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        return true;
    }

    public static String normalizeDate(String raw, int dateFormat) {
        if (raw == null || dateFormat < DATE_AUTO || dateFormat > DATE_JALALI_MDY) return "";
        String s = Money.en(raw).trim().replace('\uFEFF', ' ');
        if (s.isEmpty()) return "";
        if (dateFormat == DATE_EXCEL_SERIAL) return excelSerialDate(s);
        if (dateFormat == DATE_AUTO && s.matches("[0-9]{5}")) {
            int serial = Integer.parseInt(s);
            if (serial >= 20000 && serial <= 80000) {
                String converted = excelSerialDate(s);
                if (!converted.isEmpty()) return converted;
            }
        }
        String token = s.split("[\\sTt]", 2)[0].replace('.', '/');
        String[] parts = token.split("[/\\-]");
        try {
            if (token.matches("[0-9]{8}") && dateFormat == DATE_JALALI_DMY) {
                int day = Integer.parseInt(token.substring(0, 2));
                int month = Integer.parseInt(token.substring(2, 4));
                int year = Integer.parseInt(token.substring(4, 8));
                return year >= 1300 && year <= 1600 ? safeJalali(year, month, day) : "";
            }
            if (token.matches("[0-9]{8}") && dateFormat == DATE_JALALI_MDY) {
                int month = Integer.parseInt(token.substring(0, 2));
                int day = Integer.parseInt(token.substring(2, 4));
                int year = Integer.parseInt(token.substring(4, 8));
                return year >= 1300 && year <= 1600 ? safeJalali(year, month, day) : "";
            }
            if (token.matches("[0-9]{8}") && dateFormat == DATE_GREGORIAN_DMY) {
                int day = Integer.parseInt(token.substring(0, 2));
                int month = Integer.parseInt(token.substring(2, 4));
                int year = Integer.parseInt(token.substring(4, 8));
                return year >= 1700 && year <= 2300 ? gregorianToJalali(year, month, day) : "";
            }
            if (token.matches("[0-9]{8}") && dateFormat == DATE_GREGORIAN_MDY) {
                int month = Integer.parseInt(token.substring(0, 2));
                int day = Integer.parseInt(token.substring(2, 4));
                int year = Integer.parseInt(token.substring(4, 8));
                return year >= 1700 && year <= 2300 ? gregorianToJalali(year, month, day) : "";
            }
            if (token.matches("[0-9]{8}")) {
                int year = Integer.parseInt(token.substring(0, 4));
                int first = Integer.parseInt(token.substring(4, 6));
                int second = Integer.parseInt(token.substring(6, 8));
                if ((dateFormat == DATE_JALALI_YMD || dateFormat == DATE_AUTO) && year >= 1300 && year <= 1600)
                    return safeJalali(year, first, second);
                if ((dateFormat == DATE_GREGORIAN_YMD || dateFormat == DATE_AUTO) && year >= 1700 && year <= 2300)
                    return gregorianToJalali(year, first, second);
                return "";
            }
            if (dateFormat == DATE_JALALI_YMD || dateFormat == DATE_GREGORIAN_YMD
                    || dateFormat == DATE_AUTO) {
                if (parts.length == 3 && parts[0].length() == 4) {
                    int year = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]);
                    int day = Integer.parseInt(parts[2]);
                    if ((dateFormat == DATE_JALALI_YMD || dateFormat == DATE_AUTO) && year >= 1300 && year <= 1600)
                        return safeJalali(year, month, day);
                    if ((dateFormat == DATE_GREGORIAN_YMD || dateFormat == DATE_AUTO) && year >= 1700 && year <= 2300)
                        return gregorianToJalali(year, month, day);
                    if (dateFormat != DATE_AUTO) return "";
                } else if (dateFormat == DATE_JALALI_YMD || dateFormat == DATE_GREGORIAN_YMD) return "";
            }
            if (parts.length == 3 && parts[2].length() == 4) {
                int first = Integer.parseInt(parts[0]);
                int second = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                if (dateFormat == DATE_JALALI_DMY)
                    return year >= 1300 && year <= 1600 ? safeJalali(year, second, first) : "";
                if (dateFormat == DATE_JALALI_MDY)
                    return year >= 1300 && year <= 1600 ? safeJalali(year, first, second) : "";
                if (dateFormat == DATE_GREGORIAN_DMY)
                    return year >= 1700 && year <= 2300 ? gregorianToJalali(year, second, first) : "";
                if (dateFormat == DATE_GREGORIAN_MDY)
                    return year >= 1700 && year <= 2300 ? gregorianToJalali(year, first, second) : "";
                if (dateFormat == DATE_AUTO) {
                    if (year >= 1300 && year <= 1600) {
                        // The Jalali month is 1..12; auto is safe only when the day cannot be a month.
                        if (first > 12 && second >= 1 && second <= 12)
                            return safeJalali(year, second, first);
                        return "";
                    }
                    if (year >= 1700 && year <= 2300) {
                        // DMY and MDY are indistinguishable when both components are <= 12.
                        if (first > 12 && second <= 12)
                            return gregorianToJalali(year, second, first);
                        if (first <= 12 && second > 12)
                            return gregorianToJalali(year, first, second);
                    }
                }
            }
        } catch (Exception invalid) {
            return "";
        }
        // AUTO never falls back to a permissive normalizer; ambiguous/unsupported dates stay unknown.
        return "";
    }

    private static String excelSerialDate(String raw) {
        String s = raw.trim();
        if (!s.matches("[0-9]{4,6}(?:\\.[0-9]+)?")) return "";
        try {
            double serial = Double.parseDouble(s);
            if (serial < 1 || serial > 100000) return "";
            LocalDate gregorian = LocalDate.of(1899, 12, 30).plusDays((long) Math.floor(serial));
            return Jalali.disp(gregorian.toString());
        } catch (Exception invalid) {
            return "";
        }
    }

    private static String gregorianToJalali(int year, int month, int day) {
        try {
            LocalDate date = LocalDate.of(year, month, day);
            return Jalali.disp(date.toString());
        } catch (Exception invalid) {
            return "";
        }
    }

    private static String safeJalali(int year, int month, int day) {
        String candidate = String.format(Locale.US, "%04d/%02d/%02d", year, month, day);
        return Jalali.disp(candidate);
    }

    private static int direction(String raw) {
        String literal = raw == null ? "" : Money.en(raw).trim().toLowerCase(Locale.US);
        if ("+".equals(literal) || "c".equals(literal) || "cr".equals(literal) || "1".equals(literal)) return 1;
        if ("-".equals(literal) || "d".equals(literal) || "dr".equals(literal) || "2".equals(literal)) return -1;
        String s = normalizeHeader(raw);
        if (s.isEmpty()) return 0;
        boolean credit = containsAny(s, "واریز", "بستانکار", "دریافت", "deposit", "credit", "inflow", "افزایش", "cr", "plus");
        boolean debit = containsAny(s, "برداشت", "بدهکار", "پرداخت", "withdraw", "debit", "outflow", "کاهش", "dr", "minus");
        if (credit == debit) return 0;
        return credit ? 1 : -1;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(normalizeHeader(needle))) return true;
        return false;
    }

    private static int find(List<String> headers, String[] aliases) {
        if (headers == null) return -1;
        for (int i = 0; i < headers.size(); i++) {
            String header = normalizeHeader(headers.get(i));
            if (header.isEmpty()) continue;
            for (String alias : aliases) {
                String norm = normalizeHeader(alias);
                if (!norm.isEmpty() && header.contains(norm)) return i;
            }
        }
        return -1;
    }

    private static int findMoneyColumn(List<String> headers, String[] aliases) {
        if (headers == null) return -1;
        for (int i = 0; i < headers.size(); i++) {
            String header = normalizeHeader(headers.get(i));
            if (header.contains("تاریخ") || header.contains("date")) continue;
            for (String alias : aliases) {
                String norm = normalizeHeader(alias);
                if (!norm.isEmpty() && header.contains(norm)) return i;
            }
        }
        return -1;
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) return "";
        String s = Money.en(raw).toLowerCase(Locale.US).replace('ي', 'ی').replace('ك', 'ک');
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }

    private static boolean finiteAmount(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.precision() <= 24
                && value.scale() <= 4 && value.compareTo(MAX_AMOUNT_RIALS) <= 0;
    }

    private static void appendError(StringBuilder errors, int row, String message) {
        if (errors.length() >= 1200) return;
        if (errors.length() > 0) errors.append('\n');
        errors.append("ردیف ").append(row).append(" • ")
                .append(message == null ? "مقدار نامعتبر" : message);
    }

    private static boolean hasStatementHeader(List<List<String>> rows) {
        if (rows == null) return false;
        for (int i = 0; i < Math.min(30, rows.size()); i++)
            if (isStatementHeader(rows.get(i))) return true;
        return false;
    }

    private static int detectHeaderRow(List<List<String>> rows) {
        for (int i = 0; i < Math.min(30, rows.size()); i++) {
            // Pick the first plausible header; transaction values later in the file may
            // contain words such as "not-a-date" and outscore the real header by substring.
            if (isStatementHeader(rows.get(i))) return i;
        }
        for (int i = 0; i < rows.size(); i++) if (!blankRow(rows.get(i))) return i;
        return -1;
    }

    private static boolean isStatementHeader(List<String> row) {
        if (blankRow(row)) return false;
        boolean date = find(row, new String[]{"تاریخ", "تاريخ", "date", "transactiondate"}) >= 0;
        boolean amount = find(row, new String[]{"مبلغ", "amount", "value"}) >= 0;
        boolean credit = find(row, new String[]{"واریز", "بستانکار", "deposit", "credit", "inflow", "دریافت"}) >= 0;
        boolean debit = find(row, new String[]{"برداشت", "بدهکار", "withdraw", "debit", "outflow", "پرداخت"}) >= 0;
        return date && (amount || credit && debit);
    }

    private static List<List<String>> parseDelimited(String text, String delimiter) throws IOException {
        if (text == null || text.trim().isEmpty()) throw new IOException("فایل خالی است");
        char sep = delimiter == null || delimiter.isEmpty() ? ',' : delimiter.charAt(0);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"'); i++;
                } else if (c == '"') quoted = false;
                else field.append(c);
            } else if (c == '"' && field.length() == 0) quoted = true;
            else if (c == sep) {
                row.add(field.toString()); field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                if (!blankRow(row)) rows.add(row);
                row = new ArrayList<>();
                if (rows.size() > MAX_ROWS) throw new IOException("تعداد ردیف‌ها از حد مجاز بیشتر است");
            } else field.append(c);
        }
        if (quoted) throw new IOException("یکی از فیلدهای متنی CSV با کوتیشن باز مانده است؛ فایل را دوباره ذخیره کنید");
        row.add(field.toString());
        if (!blankRow(row)) rows.add(row);
        if (rows.size() > MAX_ROWS) throw new IOException("تعداد ردیف‌ها از حد مجاز بیشتر است");
        int maxCols = 0;
        for (List<String> r : rows) maxCols = Math.max(maxCols, r.size());
        if (maxCols > MAX_COLUMNS) throw new IOException("فایل بیش از ۶۴ ستون دارد؛ ستون‌های لازم را در قالب برنامه نگه دارید");
        return rows;
    }

    private static String detectDelimiter(String text) {
        char[] candidates = {',', ';', '\t', '|'};
        char best = ',';
        int bestScore = 0;
        for (char candidate : candidates) {
            int score = 0;
            int consistent = 0;
            String[] lines = text.split("\\r?\\n", 8);
            for (int i = 0; i < Math.min(6, lines.length); i++) {
                int n = countOutsideQuotes(lines[i], candidate);
                score += n;
                if (n > 0) consistent++;
            }
            int adjusted = score + consistent * 3;
            if (adjusted > bestScore) { bestScore = adjusted; best = candidate; }
        }
        return String.valueOf(best);
    }

    private static int countOutsideQuotes(String line, char delimiter) {
        boolean quoted = false;
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') i++;
                else quoted = !quoted;
            } else if (!quoted && c == delimiter) count++;
        }
        return count;
    }

    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        if (input == null) throw new IOException("فایل قابل‌خواندن نیست");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int n;
        try {
            while ((n = input.read(buffer)) != -1) {
                total += n;
                if (total > limit) throw new IOException("حجم فایل از ۱۲ مگابایت بیشتر است");
                out.write(buffer, 0, n);
            }
        } finally {
            try { input.close(); } catch (Exception ignored) { }
        }
        return out.toByteArray();
    }

    private static String decodeText(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IOException("فایل خالی است");
        try {
            if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe)
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
            if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff)
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
            int offset = bytes.length >= 3 && (bytes[0] & 0xff) == 0xef
                    && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf ? 3 : 0;
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
            } catch (CharacterCodingException invalidUtf8) {
                return new String(bytes, Charset.forName("windows-1256"));
            }
        } catch (Exception invalid) {
            throw new IOException("کدگذاری فایل پشتیبانی نمی‌شود؛ CSV را با UTF-8 یا Excel ذخیره کنید", invalid);
        }
    }

    private static Table readXlsx(byte[] bytes) throws IOException {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                boolean wanted = "xl/sharedStrings.xml".equals(name)
                        || (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"));
                if (wanted) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zip.read(buf)) != -1) {
                        total += n;
                        if (total > MAX_UNCOMPRESSED_BYTES) throw new IOException("محتوای فشردهٔ Excel بیش از حد مجاز است");
                        out.write(buf, 0, n);
                    }
                    parts.put(name, out.toByteArray());
                } else {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = zip.read(buf)) != -1) {
                        total += n;
                        if (total > MAX_UNCOMPRESSED_BYTES) throw new IOException("محتوای فشردهٔ Excel بیش از حد مجاز است");
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new IOException("فایل Excel معتبر نیست", invalid);
        }
        List<String> sheetNames = new ArrayList<>();
        for (String part : parts.keySet()) if (part.startsWith("xl/worksheets/sheet")) sheetNames.add(part);
        Collections.sort(sheetNames, (left, right) -> Integer.compare(sheetIndex(left), sheetIndex(right)));
        if (sheetNames.isEmpty()) throw new IOException("برگهٔ داده در فایل Excel پیدا نشد");
        List<String> shared = parts.containsKey("xl/sharedStrings.xml")
                ? parseSharedStrings(parts.get("xl/sharedStrings.xml")) : Collections.emptyList();
        IOException last = null;
        Table fallback = null;
        for (String sheetName : sheetNames) {
            try {
                Table candidate = new Table(parseSheet(parts.get(sheetName), shared), true, "");
                if (fallback == null) fallback = candidate;
                if (hasStatementHeader(candidate.rows)) return candidate;
            } catch (IOException invalidSheet) { last = invalidSheet; }
        }
        if (fallback != null) return fallback;
        throw last == null ? new IOException("سربرگ تراکنش در هیچ‌یک از برگه‌های Excel پیدا نشد")
                : new IOException("سربرگ تراکنش در برگه‌های Excel پیدا نشد؛ فایل را بررسی یا به CSV ذخیره کنید", last);
    }

    private static int sheetIndex(String path) {
        if (path == null) return Integer.MAX_VALUE;
        int start = path.lastIndexOf("sheet") + 5;
        int end = path.lastIndexOf(".xml");
        if (start < 5 || end <= start) return Integer.MAX_VALUE;
        try { return Integer.parseInt(path.substring(start, end)); }
        catch (Exception invalid) { return Integer.MAX_VALUE; }
    }

    private static List<String> parseSharedStrings(byte[] xml) throws IOException {
        final List<String> strings = new ArrayList<>();
        DefaultHandler handler = new DefaultHandler() {
            StringBuilder current;
            boolean inString;
            boolean inText;
            @Override public void startElement(String uri, String local, String qName, Attributes atts) {
                String tag = local.isEmpty() ? qName : local;
                if ("si".equals(tag)) { current = new StringBuilder(); inString = true; }
                else if ("t".equals(tag) && inString) inText = true;
            }
            @Override public void characters(char[] ch, int start, int length) {
                if (inString && inText) current.append(ch, start, length);
            }
            @Override public void endElement(String uri, String local, String qName) {
                String tag = local.isEmpty() ? qName : local;
                if ("t".equals(tag)) inText = false;
                else if ("si".equals(tag) && current != null) { strings.add(current.toString()); inString = false; current = null; }
            }
        };
        parseXml(xml, handler);
        return strings;
    }

    private static List<List<String>> parseSheet(byte[] xml, final List<String> shared) throws IOException {
        final List<List<String>> rows = new ArrayList<>();
        DefaultHandler handler = new DefaultHandler() {
            List<String> row;
            String cellType;
            int cellColumn;
            StringBuilder value;
            boolean capture;
            int expectedRow = 1;
            @Override public void startElement(String uri, String local, String qName, Attributes atts) {
                String tag = local.isEmpty() ? qName : local;
                if ("row".equals(tag)) {
                    row = new ArrayList<>();
                    String r = atts.getValue("r");
                    try {
                        int rowNo = r == null ? expectedRow : Integer.parseInt(r);
                        while (expectedRow < rowNo && rows.size() < MAX_ROWS) { rows.add(Collections.emptyList()); expectedRow++; }
                    } catch (Exception ignored) { }
                } else if ("c".equals(tag)) {
                    cellType = atts.getValue("t");
                    cellColumn = columnIndex(atts.getValue("r"));
                    value = new StringBuilder();
                } else if ("v".equals(tag) || ("t".equals(tag) && "inlineStr".equals(cellType))) {
                    capture = true;
                }
            }
            @Override public void characters(char[] ch, int start, int length) {
                if (capture && value != null) value.append(ch, start, length);
            }
            @Override public void endElement(String uri, String local, String qName) {
                String tag = local.isEmpty() ? qName : local;
                if ("v".equals(tag) || "t".equals(tag)) capture = false;
                else if ("c".equals(tag) && row != null && value != null && cellColumn >= 0
                        && cellColumn < MAX_COLUMNS) {
                    String text = value.toString();
                    if ("s".equals(cellType)) {
                        try { int index = Integer.parseInt(text); text = index >= 0 && index < shared.size() ? shared.get(index) : ""; }
                        catch (Exception ignored) { text = ""; }
                    }
                    while (row.size() <= cellColumn) row.add("");
                    row.set(cellColumn, text);
                    value = null;
                } else if ("row".equals(tag) && row != null) {
                    rows.add(row);
                    expectedRow++;
                    row = null;
                }
            }
        };
        parseXml(xml, handler);
        if (rows.size() > MAX_ROWS) throw new IOException("تعداد ردیف‌های Excel از حد مجاز بیشتر است");
        for (List<String> row : rows) if (row.size() > MAX_COLUMNS)
            throw new IOException("فایل Excel بیش از ۶۴ ستون دارد");
        return rows;
    }

    private static int columnIndex(String cellRef) {
        if (cellRef == null || cellRef.isEmpty()) return -1;
        int value = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (c >= 'A' && c <= 'Z') value = value * 26 + (c - 'A' + 1);
            else if (c >= 'a' && c <= 'z') value = value * 26 + (c - 'a' + 1);
            else break;
        }
        return value <= 0 ? -1 : value - 1;
    }

    private static void parseXml(byte[] xml, DefaultHandler handler) throws IOException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
            catch (Exception ignored) { }
            try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); }
            catch (Exception ignored) { }
            try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); }
            catch (Exception ignored) { }
            org.xml.sax.XMLReader reader = factory.newSAXParser().getXMLReader();
            try { reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
            catch (Exception ignored) { }
            try { reader.setFeature("http://xml.org/sax/features/external-general-entities", false); }
            catch (Exception ignored) { }
            try { reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false); }
            catch (Exception ignored) { }
            reader.setContentHandler(handler);
            reader.parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (Exception invalid) {
            throw new IOException("ساختار داخلی فایل Excel قابل‌خواندن نیست", invalid);
        }
    }
}
