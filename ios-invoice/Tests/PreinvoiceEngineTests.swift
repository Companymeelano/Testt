import XCTest
@testable import MeelanoPreinvoice

/// ۱۰ سناریوی موتور — همان اعداد اندروید/ویندوز
final class PreinvoiceEngineTests: XCTestCase {
    func makeDraft(qty: Double, price: Double, ref: Double = 0) -> PreinvoiceEngine.Draft {
        var d = PreinvoiceEngine.Draft()
        d.lines.append(PreinvoiceEngine.Line(code: "1001", name: "کالای نمونه", unit: "عدد", qty: qty, price: price, refPrice: ref))
        return d
    }
    func testTieredQty() {
        var d = makeDraft(qty: 60, price: 100_000)
        d.lines[0].discs.append(.init(type: .tieredQty, v1: 0, v2: 0, spec: "10:5,50:10"))
        let t = PreinvoiceEngine.compute(&d)
        XCTAssertEqual(t.rowDisc, 600_000, accuracy: 0.51)
    }
    func testRowPercentAfterPerUnit() {
        var d = makeDraft(qty: 10, price: 100_000)
        d.lines[0].discs.append(contentsOf: [.init(type: .rowPercent, v1: 10, v2: 0, spec: ""), .init(type: .rowPerUnit, v1: 20_000, v2: 0, spec: "")])
        let t = PreinvoiceEngine.compute(&d)
        XCTAssertEqual(t.rowDisc, 280_000, accuracy: 0.51)
    }
    func testTaxAdd() {
        var d = makeDraft(qty: 1, price: 1_000_000); d.lines[0].dutyPct = 2
        let t = PreinvoiceEngine.compute(&d)
        XCTAssertEqual(t.tax, 100_000, accuracy: 0.51)
        XCTAssertEqual(t.grand, 1_120_000, accuracy: 0.51)
    }
    func testInclusiveTax() {
        var d = makeDraft(qty: 1, price: 1_100_000); d.taxMode = PreinvoiceEngine.taxInclusive
        let t = PreinvoiceEngine.compute(&d)
        XCTAssertEqual(t.tax, 100_000, accuracy: 0.51)
        XCTAssertEqual(t.grand, 1_100_000, accuracy: 0.51)
    }
    func testInvoicePercent() {
        var d = makeDraft(qty: 2, price: 500_000); d.lines[0].taxPct = 0
        d.lines[0].discs.append(.init(type: .invoicePercent, v1: 5, v2: 0, spec: ""))
        let t = PreinvoiceEngine.compute(&d)
        XCTAssertEqual(t.invDisc, 50_000, accuracy: 0.51)
    }
    func testRounding() {
        var d = makeDraft(qty: 3, price: 333_333.7); d.rounding = "thousand"
        let t = PreinvoiceEngine.compute(&d)
        XCTAssertEqual(t.goods, 1_000_000, accuracy: 0.01)
    }
    func testDiff() {
        var d = makeDraft(qty: 3, price: 120_000, ref: 100_000)
        _ = PreinvoiceEngine.compute(&d)
        XCTAssertEqual(d.lines[0].diffAmt, 60_000, accuracy: 0.51)
    }
    func testCashDisc() {
        var d = makeDraft(qty: 1, price: 1_000_000); d.lines[0].taxPct = 0; d.cashDiscPct = 2.5
        let t = PreinvoiceEngine.compute(&d)
        XCTAssertEqual(t.cashDisc, 25_000, accuracy: 0.51)
    }
    func testPromoCap() {
        var d = makeDraft(qty: 10, price: 1_000_000)
        d.lines[0].discs.append(.init(type: .promoPercent, v1: 50, v2: 2_000_000, spec: "نوروز"))
        let t = PreinvoiceEngine.compute(&d)
        XCTAssertEqual(t.rowDisc, 2_000_000, accuracy: 0.51)
    }
    func testPackUnpack() {
        let packed = PreinvoiceEngine.Disc(type: .tieredAmount, v1: 0, v2: 0, spec: "500000:3,1000000:6").pack()
        XCTAssertEqual(PreinvoiceEngine.Disc.unpack(packed)?.spec, "500000:3,1000000:6")
    }
    func testFractionalQty() {
        var d = makeDraft(qty: 2.5, price: 100_000); d.lines[0].taxPct = 0
        XCTAssertEqual(PreinvoiceEngine.compute(&d).goods, 250_000, accuracy: 0.01)
    }
    func testZeroQty() {
        var d = makeDraft(qty: 0, price: 0, ref: 100_000)
        XCTAssertEqual(PreinvoiceEngine.compute(&d).goods, 0, accuracy: 0.01)
    }
    func testLargeAmount() {
        var d = makeDraft(qty: 1000, price: 1_000_000_000); d.lines[0].taxPct = 0
        XCTAssertEqual(PreinvoiceEngine.compute(&d).goods, 1_000_000_000_000, accuracy: 1.0)
    }
    func testDiscountOver100Capped() {
        var d = makeDraft(qty: 1, price: 100_000)
        d.lines[0].discs.append(.init(type: .rowPercent, v1: 150, v2: 0, spec: "")); d.lines[0].taxPct = 0
        XCTAssertEqual(PreinvoiceEngine.compute(&d).rowDisc, 100_000, accuracy: 0.01)
    }
    func testMultipleLines() {
        var d = PreinvoiceEngine.Draft()
        d.lines.append(.init(code: "A", name: "کالا A", unit: "عدد", qty: 2, price: 500_000, refPrice: 0))
        d.lines.append(.init(code: "B", name: "کالا B", unit: "عدد", qty: 3, price: 200_000, refPrice: 0))
        d.lines[0].discs.append(.init(type: .rowPercent, v1: 10, v2: 0, spec: ""))
        d.lines[1].discs.append(.init(type: .rowPerUnit, v1: 10_000, v2: 0, spec: ""))
        d.lines[0].taxPct = 10; d.lines[1].taxPct = 10
        XCTAssertEqual(PreinvoiceEngine.compute(&d).grand, 1_617_000, accuracy: 1.0)
    }
    func testAllRoundings() {
        var d = makeDraft(qty: 1, price: 123_456.7); d.lines[0].taxPct = 0
        d.rounding = "rial"; XCTAssertEqual(PreinvoiceEngine.compute(&d).goods, 123457, accuracy: 0.5)
        d.rounding = "toman"; XCTAssertEqual(PreinvoiceEngine.compute(&d).goods, 123460, accuracy: 0.5)
        d.rounding = "hundred"; XCTAssertEqual(PreinvoiceEngine.compute(&d).goods, 123500, accuracy: 0.5)
        d.rounding = "thousand"; XCTAssertEqual(PreinvoiceEngine.compute(&d).goods, 123000, accuracy: 0.5)
    }
}
