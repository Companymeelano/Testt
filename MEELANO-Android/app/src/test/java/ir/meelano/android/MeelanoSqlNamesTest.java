package ir.meelano.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The «all tables» browser splices a runtime table name into {@code dbo.[name]}, so the
 * identifier guard is the only thing between the catalogue and a statement. These cases are the
 * names that really occur in Atiran2 plus everything that must never be allowed through.
 */
public class MeelanoSqlNamesTest {

    @Test public void realAtiranNamesAreAccepted() {
        String[] real = {
                "sailfact", "subsailfact", "sailfact_pish", "CUSTOMERS", "cust_act", "ka_act",
                "getchk", "putchk", "sys_users", "visitors", "Visit", "vis_goals", "masir",
                "anbars", "inventory", "SaleFactTasvieh", "meelano_prefactors",
                "meelano_prefactor_items", "VW_FORUSH_RizAghlam_Nakhales", "overal_setting"
        };
        for (String n : real) assertTrue(n, MeelanoSqlNames.isSafeIdentifier(n));
    }

    @Test public void injectionAttemptsAreRefused() {
        String[] bad = {
                null, "", " ", "sailfact; DROP TABLE dbo.CUSTOMERS", "sailfact--", "a b",
                "sailfact'", "sailfact]", "[sailfact", "dbo.sailfact", "sailfact\n", "1sailfact",
                "سailfact", "sailfact/*x*/", "sailfact) OR 1=1--"
        };
        for (String n : bad) assertFalse(String.valueOf(n), MeelanoSqlNames.isSafeIdentifier(n));
    }

    @Test public void overlongNameIsRefused() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 128; i++) sb.append('a');
        assertTrue(MeelanoSqlNames.isSafeIdentifier(sb.toString()));   // exactly 128 chars: allowed
        sb.append('a');
        assertFalse(MeelanoSqlNames.isSafeIdentifier(sb.toString()));  // 129: refused
    }

    @Test public void quotingWrapsOnlySafeNames() {
        assertEquals("[sailfact]", MeelanoSqlNames.quote("sailfact"));
        try {
            MeelanoSqlNames.quote("sailfact; DROP TABLE x");
            org.junit.Assert.fail("unsafe name must never be quoted");
        } catch (IllegalArgumentException expected) {
            // the statement is never built
        }
    }
}
