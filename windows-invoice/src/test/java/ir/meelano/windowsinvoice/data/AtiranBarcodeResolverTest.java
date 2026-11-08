package ir.meelano.windowsinvoice.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtiranBarcodeResolverTest {
    @Test void prefersAndroidBarcodeAliasesAndKeepsLegacyCokaFallback() {
        assertEquals(List.of("StuffCode", "coka"),
                AtiranSqlGateway.resolveProductBarcodeColumns(List.of("coka", "Barcode", "StuffCode")));
    }

    @Test void resolvesCaseAndSeparatorVariantsLikeAndroid() {
        assertEquals(List.of("stuff code", "coka"),
                AtiranSqlGateway.resolveProductBarcodeColumns(List.of("stuff code", "coka")));
        assertEquals(List.of("کد-کالا"),
                AtiranSqlGateway.resolveProductBarcodeColumns(List.of("کد-کالا")));
    }

    @Test void fallsBackToCokaOrNoBarcodeWithoutInventingColumns() {
        assertEquals(List.of("coka"), AtiranSqlGateway.resolveProductBarcodeColumns(List.of("coka")));
        assertTrue(AtiranSqlGateway.resolveProductBarcodeColumns(List.of("shka", "naka")).isEmpty());
    }
}
