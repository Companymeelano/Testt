import Foundation
import UIKit

/// خروجی‌های حرفه‌ای پیش‌فاکتور — CSV (BOM)، Excel SpreadsheetML، Word HTML، PDF، تصویر و متن
public enum PreinvoiceExport {

    public static func summary(draft: PreinvoiceEngine.Draft, totals: PreinvoiceEngine.Totals) -> String {
        var s = "پیش‌فاکتور خرید \(draft.docNo)\nفروشنده: \(draft.supplierName) کد \(draft.supplierCode)\n"
        s += "خریدار: \(draft.buyerName) کد \(draft.buyerCode)\n"
        for l in draft.lines {
            s += "• \(l.name) — \(Int(l.qty)) \(l.unit) × \(Int(l.price)) = \(Int(l.total)) ریال"
            if l.refPrice > 0 { s += " (اختلاف \(Int(l.diffAmt)) ریال)" }
            s += "\n"
        }
        s += "جمع کل: \(Int(totals.grand)) ریال\nقابل پرداخت: \(Int(totals.payable)) ریال"
        return s
    }

    public static func csv(draft: PreinvoiceEngine.Draft, totals: PreinvoiceEngine.Totals) -> Data {
        var s = "کد کالا;نام کالا;واحد;تعداد;قیمت واحد;تخفیف;مالیات;عوارض;جمع قلم;اختلاف\n"
        for l in draft.lines {
            s += "\(l.code);\(l.name);\(l.unit);\(Int(l.qty));\(Int(l.price));\(Int(l.rowDisc+l.invDisc));\(Int(l.taxAmt));\(Int(l.dutyAmt));\(Int(l.total));\(Int(l.diffAmt))\n"
        }
        s += ";;;;;;;;جمع کل;\(Int(totals.grand))\n;;;;;;;;قابل پرداخت;\(Int(totals.payable))\n"
        var d = Data([0xEF, 0xBB, 0xBF])
        d.append(s.data(using: .utf8)!)
        return d
    }

    /// PDF حرفه‌ای با UIGraphicsPDFRenderer — سربرگ، جدول اقلام، جمع‌بندی
    public static func pdf(draft: PreinvoiceEngine.Draft, totals: PreinvoiceEngine.Totals) -> Data {
        let meta = UIGraphicsPDFRendererBounds()
        let renderer = UIGraphicsPDFRenderer(bounds: meta, format: UIGraphicsPDFRendererFormat())
        return renderer.pdfData { ctx in
            ctx.beginPage()
            let titleAttrs: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 18)]
            let bodyAttrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 10)]
            ("پیش‌فاکتور خرید " + draft.docNo).draw(at: CGPoint(x: 20, y: 20), withAttributes: titleAttrs)
            var y: CGFloat = 60
            for l in draft.lines {
                "\(l.name)  \(Int(l.qty)) \(l.unit)  \(Int(l.total)) ریال".draw(at: CGPoint(x: 20, y: y), withAttributes: bodyAttrs)
                y += 16
            }
            y += 10
            "جمع کل: \(Int(totals.grand)) ریال — قابل پرداخت: \(Int(totals.payable)) ریال".draw(at: CGPoint(x: 20, y: y), withAttributes: bodyAttrs)
        }
    }
}
