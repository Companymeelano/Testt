import Foundation

/// پرچم نسخهٔ رایگان — آیفون رایگان: بدون لایسنس، با محدودیت ملایم + واترمارک
public enum FeatureFlags {
    public static let isFree = true
    public static let freeMonthlyLimit = 10
    public static let freeLocksTieredDiscount = true
    public static let freeWatermark = "نسخهٔ رایگان میلو — meelano.ir"

    /// کلید ماهانه — با سال و ماه تا در گذر سال صفر شود (مهاجرت: کلید قدیمیِ فقط-ماه به‌صورت خودکار منقضی می‌شود)
    private static var monthlyKey: String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM"; f.locale = Locale(identifier: "en_US_POSIX")
        return "free_invoice_count_" + f.string(from: Date())
    }

    public static func canCreateInvoice() -> Bool {
        guard isFree else { return true }
        let used = UserDefaults.standard.integer(forKey: monthlyKey)
        return used < freeMonthlyLimit
    }
    public static func recordInvoice() {
        guard isFree else { return }
        let used = UserDefaults.standard.integer(forKey: monthlyKey)
        UserDefaults.standard.set(used + 1, forKey: monthlyKey)
    }
    /// برای نمایش به کاربر: چندتا مانده
    public static var remaining: Int {
        max(0, freeMonthlyLimit - UserDefaults.standard.integer(forKey: monthlyKey))
    }
    /// ریست برای تست (فقط در DEBUG)
    public static func resetForTest() {
        UserDefaults.standard.removeObject(forKey: monthlyKey)
    }
}
