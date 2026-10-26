package ir.meelano.manager.core;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ProductionWriterTest {
    @Test public void repeatedIngredientRowsAreSummedBeforeStockValidation() throws Exception {
        List<ProductionWriter.Ingredient> rows = new ArrayList<>();
        rows.add(new ProductionWriter.Ingredient(17, "مادهٔ خام", "گرم", 1, new BigDecimal("3.250")));
        rows.add(new ProductionWriter.Ingredient(29, "مادهٔ دیگر", "عدد", 1, new BigDecimal("2")));
        rows.add(new ProductionWriter.Ingredient(17, "مادهٔ خام", "گرم", 1, new BigDecimal("4.750")));

        Map<Long, BigDecimal> totals = ProductionWriter.sumIngredientQuantities(rows);

        assertEquals(2, totals.size());
        assertEquals(new BigDecimal("8.000"), totals.get(17L));
        assertEquals(new BigDecimal("2"), totals.get(29L));
    }

    @Test(expected = Exception.class)
    public void rejectsNonPositiveIngredientRows() throws Exception {
        List<ProductionWriter.Ingredient> rows = new ArrayList<>();
        rows.add(new ProductionWriter.Ingredient(17, "مادهٔ خام", "گرم", 1, BigDecimal.ZERO));
        ProductionWriter.sumIngredientQuantities(rows);
    }

    @Test public void recipeLabelDoesNotRepeatIdenticalNameAndProduct() {
        ProductionWriter.Recipe duplicate = new ProductionWriter.Recipe(4,"مغز کازو زرد",530,
                "مغز کازو زرد","شرح",BigDecimal.ONE,BigDecimal.ZERO);
        ProductionWriter.Recipe distinct = new ProductionWriter.Recipe(5,"بادام ممتاز",525,
                "بادام خام","شرح",BigDecimal.ONE,BigDecimal.ZERO);

        assertEquals("مغز کازو زرد",duplicate.toString());
        assertEquals("بادام ممتاز • بادام خام",distinct.toString());
    }

    @Test public void recipeScalingUsesBaseOutputRatio() throws Exception {
        assertEquals(new BigDecimal("7.500"), ProductionWriter.scale(
                new BigDecimal("5"), new BigDecimal("15"), new BigDecimal("10")));
    }

    @Test(expected = Exception.class)
    public void refusesScalingAgainstZeroBaseOutput() throws Exception {
        ProductionWriter.scale(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO);
    }
}
