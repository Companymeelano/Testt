package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * One JUnit case per report: when a report cannot build its SQL against the sample
 * Atiran schema, the name of the failing case names the report directly in the CI log
 * ({@code buildsAgainstTheSampleSchema[12: some_report]}). The shared loop test in
 * {@link ReportCatalogIntegrityTest} reports the complete list at once; this class
 * makes each failure individually visible.
 */
@RunWith(Parameterized.class)
public class ReportBuildTest {

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        List<Object[]> out = new ArrayList<>();
        for (ReportCatalog.Spec spec : ReportCatalog.ALL) out.add(new Object[]{spec.id});
        return out;
    }

    private final String id;

    public ReportBuildTest(String id) {
        this.id = id;
    }

    @Test
    public void buildsAgainstTheSampleSchema() throws Exception {
        Meta meta = new Meta(ReportCatalogIntegrityTest.connection(ReportCatalogIntegrityTest.loadManifest()));
        Filter f = new Filter();
        f.from = "1405/01/01";
        f.to = "1405/12/29";
        Queries.Q q = ReportCatalog.query(meta, id, f);
        assertTrue(id + ": empty SQL", q != null && q.sql != null && q.sql.trim().length() > 10);
    }
}
