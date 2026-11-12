import Foundation

/// پرچم نسخهٔ رایگان — آیفون رایگان: بدون لایسنس، با محدودیت ملایم + واترمارک
public enum FeatureFlags {
    /// نسخهٔ رایگان فعال است (برای iPhone Free). پولی = false
    public static let isFree = true
    /// سقف ماهانه برای نسخهٔ رایگان (۱۰ فاکتور). پولی = نامحدود
    public static let freeMonthlyLimit = 10
    /// آیا تخفیف پله‌ای در رایگان قفل است؟ (پیشنهاد: بله — فقط درصد ساده)
    public static let freeLocksTieredDiscount = true
    /// متن واترمارک PDF/XLS رایگان
    public static let freeWatermark = "نسخهٔ رایگان میلو — meelano.ir"

    public static func canCreateInvoice() -> Bool {
        guard isFree else { return true }
        let key = "free_invoice_count_\(Calendar.current.component(.month, from: Date()))"
        let used = UserDefaults.standard.integer(forKey: key)
        return used < freeMonthlyLimit
    }
    public static func recordInvoice() {
        guard isFree else { return }
        let key = "free_invoice_count_\(Calendar.current.component(.month, from: Date()))"
        let used = UserDefaults.standard.integer(forKey: key)
        UserDefaults.standard.set(used + 1, forKey: key)
    }
}
