package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.CustomerModels.BalanceFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerListFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerStatusFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerSort;
import ir.meelano.windowsinvoice.CustomerModels.LedgerCategory;
import ir.meelano.windowsinvoice.CustomerModels.LedgerFilter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerModelsTest {
    @Test void jalaliDateBoundsNormalizePersianDigitsAndRejectReversedRanges() {
        LedgerFilter filter = new LedgerFilter("۱۴۰۵/۰۸/۰۱", "1405/08/30", LedgerCategory.ALL, 0, 200);
        assertEquals("1405/08/01", filter.fromJalali());
        LedgerFilter shortDigits = new LedgerFilter("۱۴۰۵/۸/۱", "1405/08/30", LedgerCategory.ALL, 0, 200);
        assertEquals("1405/08/01", shortDigits.fromJalali());
        assertThrows(IllegalArgumentException.class,
                () -> new LedgerFilter("1405/08/20", "1405/08/10", LedgerCategory.ALL, 0, 200));
    }

    @Test void accountingBalanceBoundsMustBeOrdered() {
        assertThrows(IllegalArgumentException.class, () -> new CustomerListFilter("",
                BalanceFilter.ALL, CustomerStatusFilter.ALL, "", "", "",
                new BigDecimal("20"), new BigDecimal("10"), CustomerSort.NAME, 0, 80));
    }
}
