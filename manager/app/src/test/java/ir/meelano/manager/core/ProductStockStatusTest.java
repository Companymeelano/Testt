package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProductStockStatusTest {
    @Test public void classifiesNegativeZeroAndPointOfSaleBoundaries() {
        assertEquals(ProductStockStatus.Band.NEGATIVE,
                ProductStockStatus.classify(true, -1, -1, 5, 5));
        assertEquals(ProductStockStatus.Band.OUT,
                ProductStockStatus.classify(true, 0, 0, 5, 5));
        assertEquals(ProductStockStatus.Band.REORDER,
                ProductStockStatus.classify(true, 2, 4, 5, 5));
        assertEquals(ProductStockStatus.Band.LOW,
                ProductStockStatus.classify(true, 5, 20, 5, 5));
        assertEquals(ProductStockStatus.Band.NORMAL,
                ProductStockStatus.classify(true, 6, 20, 5, 5));
    }

    @Test public void missingDataDoesNotPretendStockIsZero() {
        assertEquals(ProductStockStatus.Band.UNKNOWN,
                ProductStockStatus.classify(false, 0, 0, 0, 5));
        assertEquals(ProductStockStatus.Band.UNKNOWN,
                ProductStockStatus.classify(true, Double.NaN, 0, 0, 5));
    }

    @Test public void labelsArePersianAndDistinct() {
        assertEquals("نقطه سفارش", ProductStockStatus.label(ProductStockStatus.Band.REORDER));
        assertEquals("موجودی کم", ProductStockStatus.label(ProductStockStatus.Band.LOW));
        assertEquals("ناموجود", ProductStockStatus.label(ProductStockStatus.Band.OUT));
    }
}
