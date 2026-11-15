package ir.meelano.windowsinvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import ir.meelano.windowsinvoice.InvoiceModels.InvoiceLine;

/** One source of truth for invoice totals; calculations use decimal arithmetic, never binary floating point. */
public final class InvoiceMath {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_ATIRAN_MONEY = new BigDecimal("922337203685477.5807");
    private InvoiceMath() { }

    public record Totals(BigDecimal linesGross, BigDecimal lineDiscounts,
                         BigDecimal tax, BigDecimal globalDiscount,
                         BigDecimal freight, BigDecimal payable) { }

    public record PackedQuantity(BigDecimal packs, int looseUnits) { }

    /** Converts counted units to Atiran's decimal-pack plus integer-loose-unit representation without rounding. */
    public static PackedQuantity toAtiranQuantity(BigDecimal quantity, int unitsPerPack) {
        if (quantity == null || quantity.signum() <= 0 || unitsPerPack < 1
                || quantity.stripTrailingZeros().scale() > 3)
            throw new IllegalArgumentException("مقدار کالا یا تعداد در بسته معتبر نیست.");
        BigDecimal packSize = BigDecimal.valueOf(unitsPerPack);
        if (quantity.stripTrailingZeros().scale() <= 0) {
            long pieces;
            try { pieces = quantity.longValueExact(); }
            catch (ArithmeticException ex) { throw new IllegalArgumentException("مقدار کالا خارج از محدوده است.", ex); }
            return new PackedQuantity(BigDecimal.valueOf(pieces / unitsPerPack), (int) (pieces % unitsPerPack));
        }
        try {
            BigDecimal packs = quantity.divide(packSize, 3, RoundingMode.UNNECESSARY);
            if (packs.multiply(packSize).compareTo(quantity) != 0)
                throw new ArithmeticException("Not exactly representable");
            return new PackedQuantity(packs, 0);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("این مقدار در بسته‌بندی آتیران بدون گردکردن قابل ثبت نیست.", ex);
        }
    }

    public static BigDecimal gross(InvoiceLine line) {
        return money(line.product().unitPrice().multiply(line.quantity()));
    }

    public static BigDecimal lineDiscount(InvoiceLine line) {
        BigDecimal pct = clampPercent(line.discountPercent());
        return money(gross(line).multiply(pct).divide(HUNDRED, 8, RoundingMode.HALF_UP));
    }

    public static BigDecimal lineTax(InvoiceLine line) {
        BigDecimal taxable = gross(line).subtract(lineDiscount(line)).max(BigDecimal.ZERO);
        return money(taxable.multiply(clampPercent(line.taxPercent()))
                .divide(HUNDRED, 8, RoundingMode.HALF_UP));
    }

    public static BigDecimal linePayable(InvoiceLine line) {
        return gross(line).subtract(lineDiscount(line)).add(lineTax(line)).max(BigDecimal.ZERO);
    }

    public static Totals calculate(List<InvoiceLine> lines, BigDecimal globalDiscount, BigDecimal freight) {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal taxes = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            gross = gross.add(gross(line));
            discounts = discounts.add(lineDiscount(line));
            taxes = taxes.add(lineTax(line));
        }
        BigDecimal safeGlobal = money(nonNegative(globalDiscount));
        BigDecimal safeFreight = money(nonNegative(freight));
        BigDecimal payable = gross.subtract(discounts).subtract(safeGlobal).max(BigDecimal.ZERO)
                .add(taxes).add(safeFreight);
        return new Totals(money(gross), money(discounts), money(taxes), safeGlobal,
                safeFreight, money(payable));
    }

    public static BigDecimal money(BigDecimal amount) {
        BigDecimal rounded = (amount == null ? BigDecimal.ZERO : amount).setScale(0, RoundingMode.HALF_UP);
        if (rounded.abs().compareTo(MAX_ATIRAN_MONEY) > 0)
            throw new IllegalArgumentException("مبلغ از محدودهٔ قابل ثبت در آتیران بیشتر است.");
        return rounded;
    }

    public static BigDecimal nonNegative(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
    }

    public static BigDecimal clampPercent(BigDecimal value) {
        if (value == null || value.signum() < 0) return BigDecimal.ZERO;
        return value.min(HUNDRED);
    }
}
