import Foundation

/// دفترچهٔ محلی پیش‌فاکتورهای خرید — بدون نوشتن روی دیتابیس.
/// روی iOS در `UserDefaults` (suite جدا) نگه داشته می‌شود؛ iCloud اختیاری است.
/// فرمت ذخیره: JSON؛ برای مهاجرت از اندروید/ویندوز بسته‌بندی متنی v1| هم خوانده می‌شود.
public enum PreinvoiceStore {

    private static let suite = "meelano_purchase_preinvoice"
    private static let indexKey = "__index"

    static func defaults() -> UserDefaults {
        UserDefaults(suiteName: suite) ?? .standard
    }

    public static func load() -> [PreinvoiceEngine.Draft] {
        let d = defaults()
        // اگر iCloud فعال بود، ابتدا index را از آن به‌روزرسانی کن (بدون بازنویسی دادهٔ محلی جدیدتر)
        // فعلاً فقط local را می‌خوانیم تا مهاجرت خودکار باعث حذف نشود
        let index = d.string(forKey: indexKey) ?? ""
        var out: [PreinvoiceEngine.Draft] = []
        for key in index.split(separator: ",").map(String.init) where !key.isEmpty {
            if let data = d.data(forKey: key),
               let draft = try? JSONDecoder().decode(PreinvoiceEngine.Draft.self, from: data) {
                out.append(draft)
            } else if let packed = d.string(forKey: key),
                      let draft = unpack(packed) {
                out.append(draft)
            }
        }
        return out.sorted { $0.savedAt > $1.savedAt }
    }

    public static func save(_ draft: inout PreinvoiceEngine.Draft) {
        if draft.id.isEmpty { draft.id = "pi\(Int64(Date().timeIntervalSince1970 * 1000))" }
        draft.savedAt = Int64(Date().timeIntervalSince1970 * 1000)
        let d = defaults()
        var index = d.string(forKey: indexKey) ?? ""
        if !index.split(separator: ",").contains(where: { $0 == draft.id }) {
            index = index.isEmpty ? draft.id : index + "," + draft.id
            d.set(index, forKey: indexKey)
            // iCloud: فقط index را همگام کن، داده‌ها محلی می‌مانند
            NSUbiquitousKeyValueStore.default.set(index, forKey: indexKey)
            NSUbiquitousKeyValueStore.default.synchronize()
        }
        if let data = try? JSONEncoder().encode(draft) {
            d.set(data, forKey: draft.id)
        }
    }

    public static func delete(id: String) {
        let d = defaults()
        let index = d.string(forKey: indexKey) ?? ""
        let filtered = index.split(separator: ",").filter { $0 != id }.joined(separator: ",")
        d.set(filtered, forKey: indexKey)
        d.removeObject(forKey: id)
        NSUbiquitousKeyValueStore.default.set(filtered, forKey: indexKey)
    }

    /// مهاجرت از بسته‌بندی متنی اندروید/ویندوز (v1|...;lines)
    static func unpack(_ s: String) -> PreinvoiceEngine.Draft? {
        guard s.hasPrefix("v1|") else { return nil }
        let parts = s.split(separator: ";", omittingEmptySubsequences: false).map(String.init)
        guard let head = parts.first else { return nil }
        let h = head.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        if h.count < 19 { return nil }
        var draft = PreinvoiceEngine.Draft()
        draft.id = h[1]; draft.docNo = h[2]
        draft.dateTs = Int64(h[3]) ?? draft.dateTs
        draft.dueDays = Int(h[4]) ?? 0
        draft.taxMode = h[5]; draft.rounding = h[6]
        draft.cashDiscPct = Double(h[7]) ?? 0
        draft.supplierName = h[8]; draft.supplierCode = h[9]; draft.supplierNatId = h[10]
        draft.supplierPhone = h[11]; draft.supplierAddress = h[12]
        draft.buyerName = h[13]; draft.buyerCode = h[14]
        draft.note = h[15]; draft.status = h[16]
        draft.savedAt = Int64(h[17]) ?? 0; draft.savedBy = h[18]
        for lineStr in parts.dropFirst() where !lineStr.isEmpty {
            let p = lineStr.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
            if p.count < 9 { continue }
            var line = PreinvoiceEngine.Line()
            line.code = p[0]; line.name = p[1]; line.unit = p[2].isEmpty ? "عدد" : p[2]
            line.qty = Double(p[3]) ?? 0; line.price = Double(p[4]) ?? 0; line.refPrice = Double(p[5]) ?? 0
            line.taxPct = Double(p[6]) ?? 10; line.dutyPct = Double(p[7]) ?? 0
            let discStr = p[8]
            if !discStr.isEmpty {
                for ds in discStr.split(separator: ",").map(String.init) where !ds.isEmpty {
                    if let disc = PreinvoiceEngine.Disc.unpack(ds) { line.discs.append(disc) }
                }
            }
            draft.lines.append(line)
        }
        return draft
    }
}
