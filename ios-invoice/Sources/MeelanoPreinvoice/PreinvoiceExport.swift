import Foundation
import UIKit

/// خروجی‌های حرفه‌ای پیش‌فاکتور — CSV (BOM)، Excel SpreadsheetML، Word HTML، PDF چندصفحه‌ای
public enum PreinvoiceExport {

    public static func summary(draft: PreinvoiceEngine.Draft, totals: PreinvoiceEngine.Totals) -> String {
        var s = "پیش‌فاکتور خرید \(draft.docNo)\nفروشنده: \(draft.supplierName) کد \(draft.supplierCode)\n"
        s += "خریدار: \(draft.buyerName) کد \(draft.buyerCode)\n"
        if !draft.note.isEmpty { s += "یادداشت: \(draft.note)\n" }
        for l in draft.lines {
            // اعداد اعشاری بدون حذف — تا 2 رقم اعشار
            let qtyStr = l.qty == Double(Int(l.qty)) ? "\(Int(l.qty))" : String(format: "%.2f", l.qty)
            let priceStr = l.price == Double(Int(l.price)) ? "\(Int(l.price))" : String(format: "%.2f", l.price)
            let totalStr = l.total == Double(Int(l.total)) ? "\(Int(l.total))" : String(format: "%.2f", l.total)
            s += "• \(l.name) — \(qtyStr) \(l.unit) × \(priceStr) = \(totalStr) ریال"
            if l.refPrice > 0 { s += " (اختلاف \(Int(l.diffAmt)) ریال \(String(format: "%.1f", l.diffPct))٪)" }
            s += "\n"
        }
        s += "جمع اقلام: \(Int(totals.goods)) ریال\nتخفیفات: \(Int(totals.discSum)) ریال (\(String(format: "%.1f", totals.discPct))٪)\n"
        s += "مالیات: \(Int(totals.tax)) • عوارض: \(Int(totals.duty)) ریال\n"
        s += "جمع کل: \(Int(totals.grand)) ریال\nقابل پرداخت: \(Int(totals.payable)) ریال"
        return s
    }

    // CSV واقعی: اگر فیلد شامل ; یا " یا \n باشد، در " محصور و " داخلی دوبرابر می‌شود
    private static func csvCell(_ s: String) -> String {
        let needsQuote = s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")
        if !needsQuote { return s }
        return "\"" + s.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }

    public static func csv(draft: PreinvoiceEngine.Draft, totals: PreinvoiceEngine.Totals) -> Data {
        var s = "کد کالا;نام کالا;واحد;تعداد;قیمت واحد;تخفیف ردیف;تخفیف کل;مالیات;عوارض;جمع قلم;اختلاف;یادداشت سند\n"
        for l in draft.lines {
            let qtyStr = l.qty == Double(Int(l.qty)) ? "\(Int(l.qty))" : String(format: "%.2f", l.qty)
            s += "\(csvCell(l.code));\(csvCell(l.name));\(csvCell(l.unit));\(qtyStr);\(Int(l.price));\(Int(l.rowDisc));\(Int(l.invDisc));\(Int(l.taxAmt));\(Int(l.dutyAmt));\(Int(l.total));\(Int(l.diffAmt));\(csvCell(draft.note))\n"
        }
        s += ";;;;;;;;جمع کل;\(Int(totals.grand))\n;;;;;;;;قابل پرداخت;\(Int(totals.payable))\n"
        var d = Data([0xEF, 0xBB, 0xBF])
        d.append(s.data(using: .utf8)!)
        return d
    }

    public static func xls(draft: PreinvoiceEngine.Draft, totals: PreinvoiceEngine.Totals) -> Data {
        // SpreadsheetML ساده (Excel آن را می‌خواند)
        var s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><?mso-application progid=\"Excel.Sheet\"?><Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"><Worksheet ss:Name=\"پیش‌فاکتور\"><Table>"
        s += "<Row><Cell><Data ss:Type=\"String\">کد کالا</Data></Cell><Cell><Data ss:Type=\"String\">نام کالا</Data></Cell><Cell><Data ss:Type=\"String\">واحد</Data></Cell><Cell><Data ss:Type=\"String\">تعداد</Data></Cell><Cell><Data ss:Type=\"String\">قیمت واحد</Data></Cell><Cell><Data ss:Type=\"String\">جمع قلم</Data></Cell><Cell><Data ss:Type=\"String\">اختلاف</Data></Cell></Row>"
        for l in draft.lines {
            s += "<Row><Cell><Data ss:Type=\"String\">\(esc(l.code))</Data></Cell><Cell><Data ss:Type=\"String\">\(esc(l.name))</Data></Cell><Cell><Data ss:Type=\"String\">\(esc(l.unit))</Data></Cell><Cell><Data ss:Type=\"Number\">\(l.qty)</Data></Cell><Cell><Data ss:Type=\"Number\">\(l.price)</Data></Cell><Cell><Data ss:Type=\"Number\">\(l.total)</Data></Cell><Cell><Data ss:Type=\"Number\">\(l.diffAmt)</Data></Cell></Row>"
        }
        s += "<Row><Cell><Data ss:Type=\"String\">جمع کل</Data></Cell><Cell><Data ss:Type=\"Number\">\(totals.grand)</Data></Cell></Row>"
        s += "<Row><Cell><Data ss:Type=\"String\">قابل پرداخت</Data></Cell><Cell><Data ss:Type=\"Number\">\(totals.payable)</Data></Cell></Row>"
        s += "</Table></Worksheet></Workbook>"
        return s.data(using: .utf8)!
    }

    public static func doc(draft: PreinvoiceEngine.Draft, totals: PreinvoiceEngine.Totals) -> Data {
        var s = "<html dir='rtl'><head><meta charset='utf-8'></head><body><h2>پیش‌فاکتور خرید \(esc(draft.docNo))</h2>"
        s += "<p>فروشنده: \(esc(draft.supplierName)) کد \(esc(draft.supplierCode))<br>خریدار: \(esc(draft.buyerName))<br>یادداشت: \(esc(draft.note))</p>"
        s += "<table border='1' cellpadding='4' cellspacing='0'><tr><th>کد</th><th>نام کالا</th><th>تعداد</th><th>قیمت واحد</th><th>جمع</th><th>اختلاف</th></tr>"
        for l in draft.lines {
            s += "<tr><td>\(esc(l.code))</td><td>\(esc(l.name))</td><td>\(l.qty)</td><td>\(Int(l.price))</td><td>\(Int(l.total))</td><td>\(Int(l.diffAmt))</td></tr>"
        }
        s += "<tr><td colspan='4'>جمع کل</td><td>\(Int(totals.grand))</td><td></td></tr>"
        s += "<tr><td colspan='4'>قابل پرداخت</td><td>\(Int(totals.payable))</td><td></td></tr></table>"
        if FeatureFlags.isFree { s += "<p style='color:gray;font-size:10px'>\(FeatureFlags.freeWatermark)</p>" }
        s += "</body></html>"
        return s.data(using: .utf8)!
    }

    private static func esc(_ s: String) -> String { s.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;") }

    /// PDF چندصفحه‌ای با جدول، سربرگ، جمع‌بندی و واترمارک
    public static func pdf(draft: PreinvoiceEngine.Draft, totals: PreinvoiceEngine.Totals) -> Data {
        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = [kCGPDFContextTitle as String: "پیش‌فاکتور خرید \(draft.docNo)"]
        let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842) // A4
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect, format: format)
        return renderer.pdfData { ctx in
            var y: CGFloat = 20
            func newPageIfNeeded(need: CGFloat) {
                if y + need > 810 {
                    ctx.beginPage()
                    y = 20
                }
            }
            ctx.beginPage()
            let titleAttrs: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 16)]
            let headAttrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 9), .foregroundColor: UIColor.darkGray]
            let bodyAttrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 9)]
            let boldAttrs: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 10)]
            ("پیش‌فاکتور خرید " + (draft.docNo.isEmpty ? "—" : draft.docNo)).draw(at: CGPoint(x: 20, y: y), withAttributes: titleAttrs); y += 22
            "فروشنده: \(draft.supplierName)  کد: \(draft.supplierCode)  شناسه: \(draft.supplierNatId)  تلفن: \(draft.supplierPhone)".draw(at: CGPoint(x: 20, y: y), withAttributes: headAttrs); y += 14
            "خریدار: \(draft.buyerName)  کد: \(draft.buyerCode)  سررسید: \(draft.dueDays) روز".draw(at: CGPoint(x: 20, y: y), withAttributes: headAttrs); y += 14
            if !draft.note.isEmpty { "یادداشت: \(draft.note)".draw(at: CGPoint(x: 20, y: y), withAttributes: headAttrs); y += 14 }
            if !draft.supplierAddress.isEmpty { "نشانی: \(draft.supplierAddress)".draw(at: CGPoint(x: 20, y: y), withAttributes: headAttrs); y += 14 }
            y += 6
            // هدر جدول
            let cols = ["کد کالا", "نام کالا", "تعداد", "واحد", "قیمت واحد", "تخفیف", "مالیات", "جمع قلم", "اختلاف"]
            let widths: [CGFloat] = [60, 120, 45, 40, 70, 60, 60, 70, 60]
            var x: CGFloat = 20
            for (idx, col) in cols.enumerated() {
                col.draw(at: CGPoint(x: x + 2, y: y), withAttributes: bodyAttrs)
                x += widths[idx]
            }
            y += 12
            // خط
            let linePath = UIBezierPath(); linePath.move(to: CGPoint(x: 20, y: y)); linePath.addLine(to: CGPoint(x: 575, y: y)); UIColor.lightGray.setStroke(); linePath.stroke(); y += 6
            for l in draft.lines {
                newPageIfNeeded(need: 18)
                let vals = [l.code, l.name, l.qty == Double(Int(l.qty)) ? "\(Int(l.qty))" : String(format: "%.2f", l.qty), l.unit, "\(Int(l.price))", "\(Int(l.rowDisc+l.invDisc))", "\(Int(l.taxAmt+l.dutyAmt))", "\(Int(l.total))", "\(Int(l.diffAmt))"]
                x = 20
                for (idx, v) in vals.enumerated() {
                    let truncated = v.count > 18 ? String(v.prefix(18)) + "…" : v
                    truncated.draw(at: CGPoint(x: x + 2, y: y), withAttributes: bodyAttrs)
                    x += widths[idx]
                }
                y += 14
            }
            y += 8
            newPageIfNeeded(need: 40)
            "جمع اقلام: \(Int(totals.goods))  تخفیفات: \(Int(totals.discSum)) (\(String(format: "%.1f", totals.discPct))٪)  مالیات: \(Int(totals.tax))  عوارض: \(Int(totals.duty))".draw(at: CGPoint(x: 20, y: y), withAttributes: bodyAttrs); y += 14
            "جمع کل: \(Int(totals.grand)) ریال — تخفیف نقدی: \(Int(totals.cashDisc)) — قابل پرداخت: \(Int(totals.payable)) ریال".draw(at: CGPoint(x: 20, y: y), withAttributes: boldAttrs); y += 16
            "اختلاف کل با مرجع: \(Int(totals.diff)) ریال".draw(at: CGPoint(x: 20, y: y), withAttributes: bodyAttrs); y += 16
            if FeatureFlags.isFree {
                let wmAttrs: [NSAttributedString.Key: Any] = [.font: UIFont.italicSystemFont(ofSize: 7), .foregroundColor: UIColor.systemGray]
                FeatureFlags.freeWatermark.draw(at: CGPoint(x: 20, y: y), withAttributes: wmAttrs)
            }
        }
    }
}
