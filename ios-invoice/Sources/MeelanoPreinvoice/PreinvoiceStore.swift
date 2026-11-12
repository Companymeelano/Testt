import Foundation

/// دفترچهٔ محلی پیش‌فاکتورهای خرید — بدون نوشتن روی دیتابیس.
/// روی iOS در `UserDefaults` (و به‌اختیار `NSUbiquitousKeyValueStore` برای همگامی iCloud) نگه داشته می‌شود.
public enum PreinvoiceStore {

    private static let suite = "meelano_purchase_preinvoice"
    private static let indexKey = "__index"

    static func defaults() -> UserDefaults {
        UserDefaults(suiteName: suite) ?? .standard
    }

    public static func load() -> [PreinvoiceEngine.Draft] {
        let d = defaults()
        let index = d.string(forKey: indexKey) ?? ""
        // سازگار با فرمت بسته‌بندی متنی اندروید/ویندوز (v1|…)
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
        }
        if let data = try? JSONEncoder().encode(draft) {
            d.set(data, forKey: draft.id)
        }
        // همگامی iCloud (اختیاری، اگر کاربر iCloud فعال کرده باشد)
        NSUbiquitousKeyValueStore.default.set(index, forKey: indexKey)
    }

    public static func delete(id: String) {
        let d = defaults()
        let index = d.string(forKey: indexKey) ?? ""
        let filtered = index.split(separator: ",").filter { $0 != id }.joined(separator: ",")
        d.set(filtered, forKey: indexKey)
        d.removeObject(forKey: id)
    }

    // سازگار با بسته‌بندی متنی اندروید/ویندوز (برای مهاجرت داده)
    static func unpack(_ s: String) -> PreinvoiceEngine.Draft? {
        guard s.hasPrefix("v1|") else { return nil }
        // فعلاً از JSON استفاده می‌کنیم؛ مهاجرت متنی در نسخهٔ بعد
        return nil
    }
}
