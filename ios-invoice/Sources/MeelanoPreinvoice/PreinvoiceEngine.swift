import Foundation

/// موتور خالص «پیش‌فاکتور خرید» — SwiftUI ندارد، شبکه ندارد.
/// قرارداد محاسباتی عین `PurchasePreinvoice.java` است: ۶ نوع تخفیف، مالیات دوحالتی، عوارض، گردکردن.
public enum PreinvoiceEngine {

    // MARK: - انواع تخفیف
    public enum DiscType: String, CaseIterable, Codable {
        case rowPercent = "ROW_PERCENT"        // درصد ردیف
        case rowPerUnit = "ROW_PER_UNIT"       // ریالی هر واحد
        case tieredQty = "TIERED_QTY"          // پله‌ای تعداد  "10:5,50:10"
        case tieredAmount = "TIERED_AMOUNT"    // پله‌ای مبلغ
        case invoicePercent = "INVOICE_PERCENT" // درصد کل فاکتور
        case promoPercent = "PROMO_PERCENT"     // کد تخفیف با سقف

        var fa: String {
            switch self {
            case .rowPercent: return "درصد ردیف"
            case .rowPerUnit: return "ریالی هر واحد"
            case .tieredQty: return "پله‌ای تعداد"
            case .tieredAmount: return "پله‌ای مبلغ"
            case .invoicePercent: return "درصد کل فاکتور"
            case .promoPercent: return "کد تخفیف"
            }
        }
    }

    public struct Disc: Codable, Equatable {
        var type: DiscType
        var v1: Double  // درصد یا مبلغ هر واحد
        var v2: Double  // سقف ریالی (PROMO)
        var spec: String // پله‌ها یا نام کد

        func pack() -> String {
            "\(type.rawValue):\(String(format: "%.2f", v1)):\(String(format: "%.2f", v2)):\(spec.replacingOccurrences(of: ",", with: "~"))"
        }
        static func unpack(_ s: String) -> Disc? {
            let p = s.split(separator: ":", maxSplits: 3, omittingEmptySubsequences: false).map(String.init)
            guard p.count >= 3, let t = DiscType(rawValue: p[0]), let a = Double(p[1]), let b = Double(p[2]) else { return nil }
            let spec = p.count > 3 ? p[3].replacingOccurrences(of: "~", with: ",") : ""
            return Disc(type: t, v1: a, v2: b, spec: spec)
        }
    }

    public struct Line: Codable, Equatable {
        var code: String = ""
        var name: String = ""
        var unit: String = "عدد"
        var qty: Double = 0
        var price: Double = 0        // قیمت واحد خرید (ریال)
        var refPrice: Double = 0     // قیمت مرجع برای اختلاف
        var taxPct: Double = 10
        var dutyPct: Double = 0
        var discs: [Disc] = []
        // محاسبات (پس از compute):
        var gross: Double = 0, rowDisc: Double = 0, invDisc: Double = 0
        var net: Double = 0, taxAmt: Double = 0, dutyAmt: Double = 0
        var total: Double = 0, diffAmt: Double = 0, diffPct: Double = 0
    }

    public static let taxAdd = "add"
    public static let taxInclusive = "incl"
    public static let roundings = ["none", "rial", "toman", "hundred", "thousand"]

    public struct Draft: Codable, Equatable {
        var id: String = ""
        var docNo: String = ""
        var dateTs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
        var dueDays: Int = 0
        var taxMode: String = taxAdd
        var rounding: String = "none"
        var cashDiscPct: Double = 0
        var supplierName: String = "", supplierCode: String = "", supplierNatId: String = ""
        var supplierPhone: String = "", supplierAddress: String = ""
        var buyerName: String = "", buyerCode: String = ""
        var note: String = "", status: String = "draft", savedBy: String = ""
        var savedAt: Int64 = 0
        var lines: [Line] = []
    }

    public struct Totals {
        var goods: Double = 0, rowDisc: Double = 0, invDisc: Double = 0
        var discSum: Double = 0, discPct: Double = 0
        var tax: Double = 0, duty: Double = 0, grand: Double = 0
        var cashDisc: Double = 0, payable: Double = 0, diff: Double = 0
    }

    // MARK: - گردکردن
    public static func round(_ v: Double, mode: String) -> Double {
        switch mode {
        case "toman": return (v / 10).rounded() * 10
        case "hundred": return (v / 100).rounded() * 100
        case "thousand": return (v / 1000).rounded() * 1000
        case "rial": return v.rounded()
        default: return v
        }
    }

    static func tierPercent(spec: String, value: Double) -> Double {
        var best = 0.0, bestAt = -1.0
        for step in spec.split(separator: ",") {
            let kv = step.split(separator: ":")
            guard kv.count == 2, let at = Double(kv[0]), let pct = Double(kv[1]) else { continue }
            if value >= at && at >= bestAt { bestAt = at; best = pct }
        }
        return best
    }

    static func rank(_ d: Disc) -> Int {
        switch d.type {
        case .rowPerUnit: return 0
        case .tieredQty: return 1
        case .tieredAmount: return 2
        case .rowPercent: return 3
        default: return 4
        }
    }

    public static func rowDiscount(_ l: Line) -> Double {
        let gross = l.qty * l.price
        var disc = 0.0
        let order = l.discs.sorted { rank($0) < rank($1) }
        for d in order {
            if d.type == .invoicePercent { continue }
            if d.type == .rowPerUnit { disc += d.v1 * l.qty }
            else if d.type == .tieredQty { disc += (l.qty * l.price - disc) * tierPercent(spec: d.spec, value: l.qty) / 100 }
            else if d.type == .tieredAmount { disc += (gross - disc) * tierPercent(spec: d.spec, value: gross) / 100 }
            else if d.type == .rowPercent { disc += (gross - disc) * d.v1 / 100 }
            else if d.type == .promoPercent {
                var x = (gross - disc) * d.v1 / 100
                if d.v2 > 0 { x = min(x, d.v2) }
                disc += x
            }
        }
        return min(disc, gross)
    }

    public static func invoiceDiscPct(_ d: Draft) -> Double {
        d.lines.flatMap(\.discs).filter { $0.type == .invoicePercent }.reduce(0) { $0 + $1.v1 }
    }

    /// محاسبهٔ کل سند — همهٔ ردیف‌ها + جمع‌ها + تخفیف نقدی پایانی
    @discardableResult
    public static func compute(_ draft: inout Draft) -> Totals {
        var totals = Totals()
        let ip = invoiceDiscPct(draft)
        for i in draft.lines.indices {
            draft.lines[i].gross = round(draft.lines[i].qty * draft.lines[i].price, mode: draft.rounding)
            draft.lines[i].rowDisc = round(rowDiscount(draft.lines[i]), mode: draft.rounding)
            draft.lines[i].net = round(draft.lines[i].gross - draft.lines[i].rowDisc, mode: draft.rounding)
            draft.lines[i].invDisc = round(draft.lines[i].net * ip / 100, mode: draft.rounding)
            let ref = draft.lines[i].refPrice * draft.lines[i].qty
            draft.lines[i].diffAmt = round(draft.lines[i].gross - ref, mode: draft.rounding)
            draft.lines[i].diffPct = ref > 0 ? (draft.lines[i].gross - ref) / ref * 100 : 0
            totals.goods += draft.lines[i].gross
            totals.rowDisc += draft.lines[i].rowDisc
            totals.invDisc += draft.lines[i].invDisc
            totals.diff += draft.lines[i].diffAmt
        }
        var taxSum = 0.0, dutySum = 0.0, grand = 0.0
        for i in draft.lines.indices {
            let netAfterInv = draft.lines[i].net - draft.lines[i].invDisc
            let tPct = max(0, draft.lines[i].taxPct) / 100, dPct = max(0, draft.lines[i].dutyPct) / 100
            let tax, duty, total: Double
            if draft.taxMode == taxInclusive {
                let base = draft.lines[i].net / (1 + tPct)
                let baseAfter = draft.lines[i].net == 0 ? 0 : base * (netAfterInv / draft.lines[i].net)
                tax = netAfterInv - baseAfter
                duty = baseAfter * dPct
                total = netAfterInv + duty
            } else {
                tax = netAfterInv * tPct
                duty = netAfterInv * dPct
                total = netAfterInv + tax + duty
            }
            draft.lines[i].taxAmt = round(tax, mode: draft.rounding)
            draft.lines[i].dutyAmt = round(duty, mode: draft.rounding)
            draft.lines[i].total = round(total, mode: draft.rounding)
            taxSum += draft.lines[i].taxAmt
            dutySum += draft.lines[i].dutyAmt
            grand += draft.lines[i].total
        }
        totals.tax = round(taxSum, mode: draft.rounding)
        totals.duty = round(dutySum, mode: draft.rounding)
        totals.grand = round(grand, mode: draft.rounding)
        totals.discSum = round(totals.rowDisc + totals.invDisc, mode: draft.rounding)
        totals.discPct = totals.goods > 0 ? totals.discSum / totals.goods * 100 : 0
        totals.cashDisc = round(totals.grand * max(0, draft.cashDiscPct) / 100, mode: draft.rounding)
        totals.payable = round(totals.grand - totals.cashDisc, mode: draft.rounding)
        totals.diff = round(totals.diff, mode: draft.rounding)
        return totals
    }

    // MARK: - بسته‌بندی متنی (بدون JSON، سازگار با اندروید/ویندوز)
    static func clean(_ s: String) -> String {
        s.replacingOccurrences(of: "|", with: "/").replacingOccurrences(of: ";", with: ",")
            .replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: "\r", with: " ")
    }
    static func num(_ v: Double) -> String { String(format: "%.2f", v) }
}
