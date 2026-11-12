import Foundation

/// عدد فارسی/عربی — قرارداد یکسان با اندروید/ویندوز: ورودی نامعتبر به 0 تبدیل نمی‌شود، خطا می‌دهد؛ «1.» حفظ می‌شود.
public enum PersianNumber {
    public static func parse(_ raw: String?) -> (value: Double?, error: String?) {
        guard let raw = raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return (nil, nil) }
        var s = ""
        for ch in raw {
            let v = ch.unicodeScalars.first!.value
            if v >= 0x06F0 && v <= 0x06F9 { // ۰-۹ فارسی
                s += "\(v - 0x06F0)"
            } else if v >= 0x0660 && v <= 0x0669 { // ٠-٩ عربی
                s += "\(v - 0x0660)"
            } else if ch == "٫" { s.append(".") } // اعشار فارسی
            else if ch == "٬" || ch == "،" { s.append(",") } // هزارگان
            else { s.append(ch) }
        }
        s = s.replacingOccurrences(of: ",", with: "")
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "\u{00A0}", with: "")
            .replacingOccurrences(of: "\u{202F}", with: "")
            .trimmingCharacters(in: .whitespaces)
        if s.isEmpty || s == "." || s == "-" || s == "-." { return (nil, nil) }
        if s.hasSuffix(".") {
            let without = String(s.dropLast())
            if let v = Double(without) { return (v, nil) }
        }
        if s.hasPrefix(".") { s = "0" + s }
        if s.hasPrefix("-.") { s = "-0." + String(s.dropFirst(2)) }
        if let v = Double(s) {
            if v.isNaN || v.isInfinite { return (nil, "عدد خارج از محدوده است") }
            return (v, nil)
        }
        return (nil, "عدد نامعتبر است")
    }
    public static func valueOrZero(_ raw: String?) -> Double { parse(raw).value ?? 0 }
    public static func fa(_ v: Double) -> String {
        // نمایش فارسی ساده: با جداکننده — برای totals استفاده می‌شود
        let f = NumberFormatter(); f.locale = Locale(identifier: "fa_IR"); f.numberStyle = .decimal; f.maximumFractionDigits = 2
        return f.string(from: NSNumber(value: v)) ?? "\(v)"
    }
}
