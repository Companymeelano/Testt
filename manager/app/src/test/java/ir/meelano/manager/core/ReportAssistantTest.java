package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ir.meelano.manager.data.Row;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ReportAssistantTest {
    @Test
    public void plannerCatalogContainsOnlyReportMetadataAndNoSql() {
        List<ReportCatalog.Spec> reports = ReportAssistant.visibleReports(RoleStore.ADMIN);
        assertTrue(reports.size() > 20);
        String prompt = ReportAssistant.plannerUser("فروش ماهانه", reports);
        assertTrue(prompt.contains("sales_monthly"));
        assertFalse(prompt.toUpperCase(java.util.Locale.ROOT).contains("SELECT "));
        assertFalse(prompt.toUpperCase(java.util.Locale.ROOT).contains("UPDATE "));
    }

    @Test
    public void parsePlanAcceptsOnlyAnExplicitAllowlistedReportAndPeriod() {
        ReportCatalog.Spec allowed = ReportCatalog.byId("sales_daily");
        ReportAssistant.Plan plan = ReportAssistant.parsePlan(
                "```json\n{\"report_id\":\"sales_daily\",\"period\":\"last7\"}\n```",
                Collections.singletonList(allowed));
        assertNotNull(plan);
        assertEquals("sales_daily", plan.report.id);
        assertEquals("last7", plan.period);
        assertEquals(Filter.P_LAST7, ReportAssistant.filter(plan).preset);

        assertNull(ReportAssistant.parsePlan(
                "{\"report_id\":\"sf_entries\",\"period\":\"last7\"}",
                Collections.singletonList(allowed)));
        assertNull(ReportAssistant.parsePlan(
                "{\"report_id\":null,\"period\":\"last7\"}",
                Collections.singletonList(allowed)));
    }

    @Test
    public void nonRangeReportsCannotReceiveModelControlledDateFilters() {
        ReportCatalog.Spec allowed = ReportCatalog.byId("sales_monthly");
        ReportAssistant.Plan plan = ReportAssistant.parsePlan(
                "{\"report_id\":\"sales_monthly\",\"period\":\"year\"}",
                Collections.singletonList(allowed));
        assertNotNull(plan);
        assertEquals("none", plan.period);
    }

    @Test
    public void dateOnlyFollowUpReusesOnlyTheLastAllowlistedReport() {
        ReportAssistant.Plan prior = new ReportAssistant.Plan(ReportCatalog.byId("sales_daily"), "last30");
        ReportAssistant.Plan next = ReportAssistant.followUp("ماه قبل چطور؟", prior);
        assertNotNull(next);
        assertEquals("sales_daily", next.report.id);
        assertEquals("lastmonth", next.period);
        Filter f = ReportAssistant.filter(next);
        assertEquals(Filter.P_CUSTOM, f.preset);
        assertTrue(f.from.compareTo(f.to) <= 0);
        assertNull(ReportAssistant.followUp("سلام، حال شما چطور است؟", prior));
        assertNull(ReportAssistant.followUp("فروش امروز", prior));
        ReportAssistant.Plan yearFollowUp = ReportAssistant.followUp("پارسال چطور؟", prior);
        assertNotNull(yearFollowUp);
        assertEquals("lastyear", yearFollowUp.period);
        Filter lastYear = ReportAssistant.filter(yearFollowUp);
        assertEquals(Filter.P_CUSTOM, lastYear.preset);
        assertTrue(lastYear.from.endsWith("/01/01"));
        assertTrue(lastYear.from.compareTo(lastYear.to) <= 0);
    }

    @Test
    public void malformedNumericValuesAreNotPassedToTheModel() {
        ReportCatalog.Spec spec = new ReportCatalog.Spec("test", "آزمایش", "آزمایش", "", false,
                new ReportCatalog.Col[]{
                        new ReportCatalog.Col("amount", "مبلغ", ReportCatalog.T_MONEY),
                        new ReportCatalog.Col("count", "تعداد", ReportCatalog.T_NUM)
                }, ReportCatalog.C_NONE, "", "");
        Row row = new Row();
        row.put("amount", "NaN");
        row.put("count", "Infinity");
        String facts = ReportAssistant.formatFacts(new ReportAssistant.Plan(spec, "none"), Arrays.asList(row));
        assertTrue(facts.contains("مبلغ در منبع قابل‌تفسیر نیست"));
        assertTrue(facts.contains("عدد در منبع قابل‌تفسیر نیست"));
        assertFalse(facts.contains("NaN"));
        assertFalse(facts.contains("Infinity"));
    }

    @Test
    public void missingSourceFieldsAreExplicitlyNotZero() {
        ReportCatalog.Spec spec = ReportCatalog.byId("fresh_debtors");
        Row partial = new Row();
        partial.put("name", "مشتری نمونه");
        String facts = ReportAssistant.formatFacts(new ReportAssistant.Plan(spec, "last30"), Arrays.asList(partial));
        assertTrue(facts, facts.contains("برخی فیلدهای منبع موجود نیستند؛ صفر فرض نمی‌شوند"));
    }

    @Test
    public void reportFactsOmitSensitiveContactColumnsAndMarkEmptyAsNotZero() {
        ReportCatalog.Spec spec = ReportCatalog.byId("fresh_debtors");
        Row row = new Row();
        row.put("name", "مشتری نمونه");
        row.put("cell", "PHONE_SHOULD_NOT_APPEAR");
        row.put("balance", 1000L);
        ReportAssistant.Plan plan = new ReportAssistant.Plan(spec, "last30");
        String facts = ReportAssistant.formatFacts(plan, Arrays.asList(row));
        assertTrue(facts.contains("مشتری نمونه"));
        assertFalse(facts.contains("PHONE_SHOULD_NOT_APPEAR"));

        String empty = ReportAssistant.formatFacts(plan, Collections.<Row>emptyList());
        assertTrue(empty.contains("اثبات نمی‌کند"));
        String unavailable = ReportAssistant.formatFacts(plan, null);
        assertTrue(unavailable.contains("دادهٔ گزارش دریافت نشد"));
        assertFalse(unavailable.contains("تعداد ردیف‌های خوانده‌شده: ۰"));
    }
}
