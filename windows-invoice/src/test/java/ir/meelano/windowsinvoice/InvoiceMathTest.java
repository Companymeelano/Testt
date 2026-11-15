package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.InvoiceModels.InvoiceLine;
import ir.meelano.windowsinvoice.InvoiceModels.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceMathTest {
    private Product product(String price) {
        return new Product(17, "کالای آزمون", "", "عدد", new BigDecimal(price), 1, "", 1);
    }

    @Test void computesDiscountTaxFreightAndFinalAmountWithDecimalArithmetic() {
        InvoiceLine line = new InvoiceLine(product("1200"), new BigDecimal("3"),
                new BigDecimal("10"), new BigDecimal("9"));

        InvoiceMath.Totals totals = InvoiceMath.calculate(List.of(line), new BigDecimal("100"), new BigDecimal("80"));

        assertEquals(new BigDecimal("3600"), totals.linesGross());
        assertEquals(new BigDecimal("360"), totals.lineDiscounts());
        assertEquals(new BigDecimal("292"), totals.tax());
        assertEquals(new BigDecimal("100"), totals.globalDiscount());
        assertEquals(new BigDecimal("80"), totals.freight());
        assertEquals(new BigDecimal("3512"), totals.payable());
    }

    @Test void roundsMoneyHalfUpAndPreventsNegativePayable() {
        Product p = product("10.50");
        InvoiceLine line = new InvoiceLine(p, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(new BigDecimal("11"), InvoiceMath.gross(line));
        assertEquals(new BigDecimal("0"), InvoiceMath.calculate(List.of(line), new BigDecimal("99"), BigDecimal.ZERO).payable());
    }

    @Test void capsPercentagesAtOneHundredForPreviewMath() {
        InvoiceLine line = new InvoiceLine(product("100"), BigDecimal.ONE,
                new BigDecimal("125"), new BigDecimal("125"));

        assertEquals(new BigDecimal("100"), InvoiceMath.lineDiscount(line));
        assertEquals(new BigDecimal("0"), InvoiceMath.lineTax(line));
        assertEquals(new BigDecimal("0"), InvoiceMath.linePayable(line));
    }

    @Test void rejectsMoneyBeyondTheSqlServerMoneyRange() {
        InvoiceLine line = new InvoiceLine(product("922337203685478"), BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> InvoiceMath.gross(line));
    }

    @Test void encodesAtiranPackAndLooseUnitsWithoutSilentRounding() {
        InvoiceMath.PackedQuantity integer = InvoiceMath.toAtiranQuantity(new BigDecimal("29"), 12);
        assertEquals(new BigDecimal("2"), integer.packs());
        assertEquals(5, integer.looseUnits());

        InvoiceMath.PackedQuantity fractional = InvoiceMath.toAtiranQuantity(new BigDecimal("1.5"), 2);
        assertEquals(new BigDecimal("0.750"), fractional.packs());
        assertEquals(0, fractional.looseUnits());
        assertThrows(IllegalArgumentException.class,
                () -> InvoiceMath.toAtiranQuantity(new BigDecimal("0.001"), 7));
    }
}
