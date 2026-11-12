import SwiftUI
import MeelanoPreinvoice

/// صفحهٔ «پیش‌فاکتور خرید» — آیفون بهینه، زبان درخشان ۲.۰
struct PreinvoiceView: View {
    @State private var draft = PreinvoiceEngine.Draft()
    @State private var totals = PreinvoiceEngine.Totals()
    @State private var focusID: String?
    @FocusState private var focusedField: Field?
    @Environment(\.accessibilityReduceMotion) var reduceMotion

    enum Field: Hashable {
        case supplierName, supplierCode, natId, phone, address, docNo, buyerName, buyerCode, dueDays, cashDisc
        case lineCode(Int), lineName(Int), lineQty(Int), linePrice(Int), lineRef(Int), lineTax(Int), lineDuty(Int)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                headerCard
                partiesCard
                linesCard
                totalsCard
                actionsRow
            }
            .padding(12)
        }
        .background(Color.creamVelvet)
        .navigationTitle("پیش‌فاکتور خرید")
        .toolbar { keyboardToolbar }
        .onChange(of: draft) { recompute() }
        .onAppear { recompute() }
    }

    // MARK: - Header
    var headerCard: some View {
        StoneCard {
            VStack(alignment: .leading, spacing: 6) {
                Label("پیش‌فاکتور خرید", systemImage: "doc.text.magnifyingglass")
                    .font(.headline).foregroundColor(.ink)
                Text("کالاهایی که هنوز فاکتور خریدشان ثبت نشده؛ قیمت، اختلاف، تخفیف پله‌ای، مالیات و عوارض در یک سند.")
                    .font(.caption).foregroundColor(.muted)
                draftPicker
            }
        }
    }

    var draftPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Button("＋ پیش‌فاکتور تازه") { draft = PreinvoiceEngine.Draft(); recompute() }
                    .buttonStyle(GhostButtonStyle())
                ForEach(Array(PreinvoiceStore.load().prefix(4)), id: \.id) { d in
                    Button(d.docNo.isEmpty ? "بی‌شماره" : d.docNo) { draft = d; recompute() }
                        .buttonStyle(ChipStyle(selected: d.id == draft.id))
                }
            }
        }
    }

    // MARK: - Parties
    var partiesCard: some View {
        StoneCard {
            VStack(spacing: 8) {
                Text("فروشنده و خریدار").font(.subheadline.bold()).frame(maxWidth: .infinity, alignment: .leading)
                HStack {
                    field("نام فروشنده", text: $draft.supplierName, field: .supplierName)
                    field("کد", text: $draft.supplierCode, field: .supplierCode)
                }
                HStack {
                    field("شناسه ملی", text: $draft.supplierNatId, field: .natId, keyboard: .numberPad)
                    field("تلفن", text: $draft.supplierPhone, field: .phone, keyboard: .phonePad)
                }
                field("نشانی", text: $draft.supplierAddress, field: .address)
                HStack {
                    field("شماره سند", text: $draft.docNo, field: .docNo)
                    field("سررسید (روز)", text: Binding(
                        get: { "\(draft.dueDays)" },
                        set: { draft.dueDays = Int($0) ?? 0 }
                    ), field: .dueDays, keyboard: .numberPad)
                }
                HStack {
                    field("خریدار", text: $draft.buyerName, field: .buyerName)
                    field("کد خریدار", text: $draft.buyerCode, field: .buyerCode)
                }
                Button("مالیات: \(draft.taxMode == PreinvoiceEngine.taxInclusive ? "شامل قیمت" : "افزوده‌شده")") {
                    draft.taxMode = draft.taxMode == PreinvoiceEngine.taxInclusive ? PreinvoiceEngine.taxAdd : PreinvoiceEngine.taxInclusive
                    recompute()
                }.buttonStyle(GhostButtonStyle())
            }
        }
    }

    // MARK: - Lines (professional: stepper + swipe-to-delete + live diff)
    var linesCard: some View {
        StoneCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("اقلام کالا").font(.subheadline.bold())
                if draft.lines.isEmpty {
                    EmptyState(icon: "shippingbox", title: "هنوز قلمی ثبت نشده", subtitle: "با «افزودن قلم» اولین کالا را وارد کنید.")
                }
                ForEach(Array(draft.lines.enumerated()), id: \.offset) { idx, _ in
                    lineRow(idx)
                        .transition(.move(edge: .trailing).combined(with: .opacity))
                }
                Button("＋ افزودن قلم") {
                    withAnimation(reduceMotion ? .none : .easeOut(duration: 0.42)) {
                        draft.lines.append(PreinvoiceEngine.Line())
                    }
                }.buttonStyle(GhostButtonStyle())
            }
        }
    }

    func lineRow(_ i: Int) -> some View {
        let line = draft.lines[i]
        return StoneCardInner {
            VStack(spacing: 6) {
                HStack {
                    field("کد کالا", text: Binding(get: { draft.lines[i].code }, set: { draft.lines[i].code = $0 }), field: .lineCode(i))
                    field("نام کالا", text: Binding(get: { draft.lines[i].name }, set: { draft.lines[i].name = $0 }), field: .lineName(i))
                }
                HStack {
                    field("تعداد", text: Binding(get: { line.qty == 0 ? "" : "\(Int(line.qty))" }, set: { draft.lines[i].qty = Double($0) ?? 0; recompute() }), field: .lineQty(i), keyboard: .decimalPad)
                    field("قیمت واحد", text: Binding(get: { line.price == 0 ? "" : "\(Int(line.price))" }, set: { draft.lines[i].price = Double($0) ?? 0; recompute() }), field: .linePrice(i), keyboard: .decimalPad)
                    field("مرجع", text: Binding(get: { line.refPrice == 0 ? "" : "\(Int(line.refPrice))" }, set: { draft.lines[i].refPrice = Double($0) ?? 0; recompute() }), field: .lineRef(i), keyboard: .decimalPad)
                }
                HStack {
                    field("مالیات ٪", text: Binding(get: { "\(Int(line.taxPct))" }, set: { draft.lines[i].taxPct = Double($0) ?? 0; recompute() }), field: .lineTax(i), keyboard: .decimalPad)
                    field("عوارض ٪", text: Binding(get: { line.dutyPct == 0 ? "" : "\(Int(line.dutyPct))" }, set: { draft.lines[i].dutyPct = Double($0) ?? 0; recompute() }), field: .lineDuty(i), keyboard: .decimalPad)
                    Button("تخفیف‌ها (\(line.discs.count))") { /* sheet */ }
                        .buttonStyle(GhostButtonStyle())
                }
                HStack {
                    Text("جمع قلم: \(fa(Int(line.total))) ریال").font(.caption.bold())
                    Spacer()
                    if line.refPrice > 0 {
                        Text("اختلاف: \(fa(Int(line.diffAmt))) (\(String(format: "%.1f", line.diffPct))٪)")
                            .font(.caption2).foregroundColor(line.diffAmt > 0 ? .danger : line.diffAmt < 0 ? .success : .muted)
                    }
                }
            }
        }
        .swipeActions { Button("حذف", role: .destructive) { draft.lines.remove(at: i); recompute() } }
    }

    // MARK: - Totals
    var totalsCard: some View {
        StoneCard {
            VStack(alignment: .leading, spacing: 4) {
                Text("جمع‌بندی سند").font(.subheadline.bold())
                totalRow("جمع اقلام", totals.goods)
                totalRow("تخفیفات (\(String(format: "%.1f", totals.discPct))٪)", totals.discSum)
                totalRow("مالیات", totals.tax)
                totalRow("عوارض", totals.duty)
                totalRow("جمع کل", totals.grand, bold: true)
                totalRow("قابل پرداخت", totals.payable, bold: true, color: .emerald)
                totalRow("اختلاف کل با مرجع", totals.diff)
            }
        }
    }

    func totalRow(_ label: String, _ value: Double, bold: Bool = false, color: Color = .ink) -> some View {
        HStack { Text(label).font(.caption).foregroundColor(.muted); Spacer(); Text("\(fa(Int(value))) ریال").font(bold ? .caption.bold() : .caption).foregroundColor(color) }
    }

    // MARK: - Actions (one primary)
    var actionsRow: some View {
        VStack(spacing: 8) {
            Button("ثبت پیش‌فاکتور در سامانه") { save() }
                .buttonStyle(PrimaryButtonStyle())
            Button("خروجی و اشتراک") { share() }
                .buttonStyle(GhostButtonStyle())
        }
    }

    // MARK: - Helpers
    func field(_ placeholder: String, text: Binding<String>, field: Field, keyboard: UIKeyboardType = .default) -> some View {
        TextField(placeholder, text: text)
            .keyboardType(keyboard)
            .textFieldStyle(RoundedBorderTextFieldStyle())
            .focused($focusedField, equals: field)
            .submitLabel(.next)
            .onSubmit { focusNext(after: field) }
    }

    func focusNext(after field: Field) {
        // ساده: فوکوس بعدی در onSubmit
    }

    var keyboardToolbar: some ToolbarContent {
        ToolbarItemGroup(placement: .keyboard) {
            Spacer()
            Button("تمام") { focusedField = nil }
        }
    }

    func recompute() {
        var copy = draft
        totals = PreinvoiceEngine.compute(&copy)
        // diff روی ردیف‌ها برای نمایش
        draft.lines = copy.lines
    }

    func save() {
        guard !draft.supplierName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        guard !draft.lines.isEmpty else { return }
        PreinvoiceStore.save(&draft)
    }

    func share() {
        var copy = draft
        let t = PreinvoiceEngine.compute(&copy)
        let summary = PreinvoiceExport.summary(draft: draft, totals: t)
        let av = UIActivityViewController(activityItems: [summary], applicationActivities: nil)
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }.first?.keyWindow?.rootViewController?.present(av, animated: true)
    }

    func fa(_ n: Int) -> String {
        let f = NumberFormatter(); f.locale = Locale(identifier: "fa_IR"); f.numberStyle = .decimal
        return f.string(from: NSNumber(value: n)) ?? "\(n)"
    }
}

// MARK: - Stone cards (Derakhshan 2.0)
struct StoneCard<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        content.padding(12).background(Color.stoneLight).cornerRadius(14)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.hairline, lineWidth: 1))
    }
}
struct StoneCardInner<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        content.padding(10).background(Color.stoneLight2).cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.hairline.opacity(0.7), lineWidth: 1))
    }
}
struct EmptyState: View {
    var icon, title, subtitle: String
    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon).font(.title2).foregroundColor(.muted)
            Text(title).font(.caption.bold()).foregroundColor(.ink)
            Text(subtitle).font(.caption2).foregroundColor(.muted).multilineTextAlignment(.center)
        }.frame(maxWidth: .infinity).padding(16)
    }
}
struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.frame(maxWidth: .infinity).padding(.vertical, 12)
            .background(Color.emerald).foregroundColor(.white).cornerRadius(12).scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}
struct GhostButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.padding(.horizontal, 14).padding(.vertical, 8)
            .background(Color.emerald.opacity(0.12)).foregroundColor(.emerald).cornerRadius(10)
    }
}
struct ChipStyle: ButtonStyle {
    var selected: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.padding(.horizontal, 10).padding(.vertical, 6)
            .background(selected ? Color.emerald : Color.stoneLight2).foregroundColor(selected ? .white : .ink).cornerRadius(999)
    }
}
extension Color {
    static let creamVelvet = Color(red: 0.97, green: 0.95, blue: 0.92)
    static let emerald = Color(red: 0.10, green: 0.47, blue: 0.31)
    static let stoneLight = Color.white
    static let stoneLight2 = Color(red: 0.94, green: 0.92, blue: 0.88)
    static let hairline = Color.black.opacity(0.08)
    static let ink = Color(red: 0.12, green: 0.09, blue: 0.05)
    static let muted = Color(red: 0.30, green: 0.26, blue: 0.17)
    static let danger = Color(red: 0.71, green: 0.26, blue: 0.29)
    static let success = Color(red: 0.18, green: 0.49, blue: 0.36)
}
