import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import MeelanoPreinvoice

/// صفحهٔ «پیش‌فاکتور خرید» — آیفونِ رایگان، زبان درخشان ۲.۰، راست‌به‌چپ، دسترس‌پذیری
struct PreinvoiceView: View {
    @State private var draft = PreinvoiceEngine.Draft()
    @State private var totals = PreinvoiceEngine.Totals()
    @State private var showDeleteDocConfirm = false
    @State private var showDeleteLineConfirm: Int? = nil
    @State private var showDiscountSheet: Int? = nil
    @State private var showExportSheet = false
    @State private var showLimitAlert = false
    @State private var limitMessage = ""
    @State private var isDirty = false
    @FocusState private var focusedField: Field?
    @Environment(\.accessibilityReduceMotion) var reduceMotion
    @Environment(\.layoutDirection) var layoutDir

    // نگهداری متن خام اعداد برای حفظ «1.» و خطای نامعتبر
    @State private var rawDueDays = ""
    @State private var rawCashDisc = ""
    @State private var rawLines: [RawLine] = []

    struct RawLine: Equatable {
        var qty = "", price = "", ref = "", tax = "", duty = ""
        var qtyErr: String? = nil, priceErr: String? = nil, refErr: String? = nil, taxErr: String? = nil, dutyErr: String? = nil
    }

    enum Field: Hashable {
        case supplierName, supplierCode, natId, phone, address, docNo, buyerName, buyerCode, dueDays, cashDisc, note
        case lineCode(Int), lineName(Int), lineQty(Int), linePrice(Int), lineRef(Int), lineTax(Int), lineDuty(Int)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                if FeatureFlags.isFree { freeBanner }
                headerCard
                partiesCard
                linesCard
                totalsCard
                actionsRow
            }
            .padding(12)
        }
        .background(Color.creamVelvet)
        .navigationTitle(FeatureFlags.isFree ? "پیش‌فاکتور خرید — رایگان" : "پیش‌فاکتور خرید")
        .toolbar { keyboardToolbar }
        .environment(\.layoutDirection, .rightToLeft)
        .onChange(of: draft) { _ in recompute() }
        .onAppear { syncFromDraft(); recompute() }
        .alert("توجه", isPresented: $showDeleteDocConfirm) {
            Button("حذف", role: .destructive) { deleteCurrentDraft() }
            Button("انصراف", role: .cancel) {}
        } message: { Text("این سند از دفترچهٔ محلی حذف شود؟ فقط همین دفترچه پاک می‌شود.") }
        .alert("سقف رایگان", isPresented: $showLimitAlert) {
            Button("باشه", role: .cancel) {}
        } message: { Text(limitMessage) }
        .sheet(item: Binding(get: { showDiscountSheet.map { DiscountSheetID(id: $0) } }, set: { showDiscountSheet = $0?.id })) { sheetID in
            DiscountSheet(draft: $draft, index: sheetID.id, onChange: { recompute() })
        }
        .sheet(isPresented: $showExportSheet) { exportSheet }
        .alert("حذف قلم", isPresented: Binding(get: { showDeleteLineConfirm != nil }, set: { if !$0 { showDeleteLineConfirm = nil } })) {
            Button("حذف", role: .destructive) {
                if let idx = showDeleteLineConfirm { removeLine(at: idx) }
            }
            Button("انصراف", role: .cancel) { showDeleteLineConfirm = nil }
        } message: { Text("این قلم حذف شود؟") }
    }

    struct DiscountSheetID: Identifiable { let id: Int }

    // MARK: - Header
    var headerCard: some View {
        StoneCard {
            VStack(alignment: .leading, spacing: 6) {
                Label("پیش‌فاکتور خرید", systemImage: "doc.text.magnifyingglass")
                    .font(.headline).foregroundColor(.ink)
                    .accessibilityAddTraits(.isHeader)
                Text("کالاهایی که هنوز فاکتور خریدشان ثبت نشده؛ قیمت، اختلاف، تخفیف پله‌ای، مالیات و عوارض در یک سند.")
                    .font(.caption).foregroundColor(.muted)
                    .fixedSize(horizontal: false, vertical: true)
                draftPicker
            }
        }
    }

    var draftPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button("＋ پیش‌فاکتور تازه") {
                        confirmDirtyOrDo { draft = PreinvoiceEngine.Draft(); syncFromDraft(); recompute() }
                    }
                    .buttonStyle(GhostButtonStyle())
                    .accessibilityLabel("پیش‌فاکتور تازه")
                    ForEach(Array(PreinvoiceStore.load()), id: \.id) { d in
                        Button(d.docNo.isEmpty ? "بی‌شماره" : d.docNo) {
                            if d.id == draft.id { return }
                            confirmDirtyOrDo { draft = d; syncFromDraft(); recompute() }
                        }
                        .buttonStyle(ChipStyle(selected: d.id == draft.id))
                        .accessibilityLabel("باز کردن سند \(d.docNo)")
                    }
                }
            }
            if PreinvoiceStore.load().count > 4 {
                Text("← بکشید برای بقیه (\(fa(PreinvoiceStore.load().count)) سند)")
                    .font(.caption2).foregroundColor(.muted)
            }
            if !draft.id.isEmpty {
                Button("حذف این سند", role: .destructive) { showDeleteDocConfirm = true }
                    .buttonStyle(GhostButtonStyle())
                    .tint(.danger)
            }
        }
    }

    // MARK: - Parties
    var partiesCard: some View {
        StoneCard {
            VStack(spacing: 10) {
                Text("فروشنده و خریدار").font(.subheadline.bold()).frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 8) {
                    field("نام فروشنده", text: $draft.supplierName, field: .supplierName)
                    field("کد", text: $draft.supplierCode, field: .supplierCode)
                }
                HStack(spacing: 8) {
                    field("شناسه ملی", text: $draft.supplierNatId, field: .natId, keyboard: .numberPad)
                    field("تلفن", text: $draft.supplierPhone, field: .phone, keyboard: .phonePad)
                }
                field("نشانی", text: $draft.supplierAddress, field: .address)
                HStack(spacing: 8) {
                    field("شماره سند", text: $draft.docNo, field: .docNo)
                    VStack(alignment: .leading, spacing: 2) {
                        TextField("سررسید (روز)", text: $rawDueDays)
                            .keyboardType(.numberPad)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .focused($focusedField, equals: .dueDays)
                            .submitLabel(.next)
                            .onSubmit { focusedField = .cashDisc }
                            .onChange(of: rawDueDays) { v in
                                let r = PersianNumber.parse(v)
                                if r.error == nil { draft.dueDays = Int(r.value ?? 0); isDirty = true }
                            }
                        if let err = PersianNumber.parse(rawDueDays).error { Text(err).font(.caption2).foregroundColor(.danger) }
                    }
                }
                HStack(spacing: 8) {
                    field("خریدار", text: $draft.buyerName, field: .buyerName)
                    field("کد خریدار", text: $draft.buyerCode, field: .buyerCode)
                }
                VStack(alignment: .leading, spacing: 2) {
                    TextField("تخفیف نقدی پایانی (٪)", text: $rawCashDisc)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .focused($focusedField, equals: .cashDisc)
                        .submitLabel(.done)
                        .onChange(of: rawCashDisc) { v in
                            let r = PersianNumber.parse(v)
                            if r.error == nil { draft.cashDiscPct = r.value ?? 0; recompute() }
                        }
                    if let err = PersianNumber.parse(rawCashDisc).error { Text(err).font(.caption2).foregroundColor(.danger) }
                    else if PersianNumber.parse(rawCashDisc).value ?? 0 > 100 { Text("بیش از ۱۰۰٪ — مطمئن هستید؟").font(.caption2).foregroundColor(.danger) }
                }
                field("یادداشت", text: $draft.note, field: .note)
                HStack(spacing: 8) {
                    Button("مالیات: \(draft.taxMode == PreinvoiceEngine.taxInclusive ? "شامل قیمت" : "افزوده‌شده")") {
                        draft.taxMode = draft.taxMode == PreinvoiceEngine.taxInclusive ? PreinvoiceEngine.taxAdd : PreinvoiceEngine.taxInclusive
                        isDirty = true; recompute()
                    }.buttonStyle(GhostButtonStyle())
                    Menu("گردکردن: \(roundFa())") {
                        ForEach(PreinvoiceEngine.roundings, id: \.self) { r in
                            Button(roundFa(r)) { draft.rounding = r; recompute() }
                        }
                    }
                    .buttonStyle(GhostButtonStyle())
                }
                Text("تاریخ سند: \(jalaliDate(draft.dateTs))").font(.caption2).foregroundColor(.muted)
                    .accessibilityLabel("تاریخ سند \(jalaliDate(draft.dateTs))")
            }
        }
    }

    // MARK: - Lines
    var linesCard: some View {
        StoneCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("اقلام کالا").font(.subheadline.bold()).accessibilityAddTraits(.isHeader)
                if draft.lines.isEmpty {
                    EmptyState(icon: "shippingbox", title: "هنوز قلمی ثبت نشده", subtitle: "با «نمایش» جزئیات را ببینید و قلم تازه بیفزایید.", action: {
                        draft.lines.append(PreinvoiceEngine.Line())
                        rawLines.append(RawLine())
                        isDirty = true
                    })
                    .accessibilityElement(children: .combine)
                }
                ForEach(Array(draft.lines.enumerated()), id: \.offset) { idx, _ in
                    lineRow(idx)
                        .transition(reduceMotion ? .opacity : .move(edge: .trailing).combined(with: .opacity))
                }
                Button("＋ افزودن قلم") {
                    withAnimation(reduceMotion ? .none : .easeOut(duration: 0.42)) {
                        draft.lines.append(PreinvoiceEngine.Line())
                        rawLines.append(RawLine())
                        isDirty = true
                    }
                }.buttonStyle(GhostButtonStyle())
                .accessibilityLabel("افزودن قلم کالا")
            }
        }
    }

    func lineRow(_ i: Int) -> some View {
        guard i < draft.lines.count && i < rawLines.count else { return AnyView(EmptyView()) as! AnyView }
        let line = draft.lines[i]
        return AnyView(
            StoneCardInner {
                VStack(spacing: 8) {
                    HStack(spacing: 8) {
                        field("کد کالا", text: Binding(get: { draft.lines[i].code }, set: { draft.lines[i].code = $0; isDirty = true }), field: .lineCode(i))
                        field("نام کالا", text: Binding(get: { draft.lines[i].name }, set: { draft.lines[i].name = $0; isDirty = true }), field: .lineName(i))
                    }
                    // تعداد/قیمت/مرجع با حفظ متن خام و خطای inline
                    HStack(spacing: 8) {
                        VStack(alignment: .leading, spacing: 2) {
                            TextField("تعداد", text: $rawLines[i].qty)
                                .keyboardType(.decimalPad).textFieldStyle(RoundedBorderTextFieldStyle())
                                .focused($focusedField, equals: .lineQty(i))
                                .onChange(of: rawLines[i].qty) { raw in
                                    let r = PersianNumber.parse(raw)
                                    rawLines[i].qtyErr = r.error
                                    if r.error == nil { draft.lines[i].qty = r.value ?? 0; recompute() }
                                }
                            if let e = rawLines[i].qtyErr { Text(e).font(.caption2).foregroundColor(.danger) }
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            TextField("قیمت واحد", text: $rawLines[i].price)
                                .keyboardType(.decimalPad).textFieldStyle(RoundedBorderTextFieldStyle())
                                .focused($focusedField, equals: .linePrice(i))
                                .onChange(of: rawLines[i].price) { raw in
                                    let r = PersianNumber.parse(raw)
                                    rawLines[i].priceErr = r.error
                                    if r.error == nil { draft.lines[i].price = r.value ?? 0; recompute() }
                                }
                            if let e = rawLines[i].priceErr { Text(e).font(.caption2).foregroundColor(.danger) }
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            TextField("مرجع", text: $rawLines[i].ref)
                                .keyboardType(.decimalPad).textFieldStyle(RoundedBorderTextFieldStyle())
                                .focused($focusedField, equals: .lineRef(i))
                                .onChange(of: rawLines[i].ref) { raw in
                                    let r = PersianNumber.parse(raw)
                                    rawLines[i].refErr = r.error
                                    if r.error == nil { draft.lines[i].refPrice = r.value ?? 0; recompute() }
                                }
                            if let e = rawLines[i].refErr { Text(e).font(.caption2).foregroundColor(.danger) }
                        }
                    }
                    HStack(spacing: 8) {
                        VStack(alignment: .leading, spacing: 2) {
                            TextField("مالیات ٪", text: $rawLines[i].tax)
                                .keyboardType(.decimalPad).textFieldStyle(RoundedBorderTextFieldStyle())
                                .focused($focusedField, equals: .lineTax(i))
                                .onChange(of: rawLines[i].tax) { raw in
                                    let r = PersianNumber.parse(raw)
                                    rawLines[i].taxErr = r.error
                                    if r.error == nil { draft.lines[i].taxPct = r.value ?? 0; recompute() }
                                }
                            if let e = rawLines[i].taxErr { Text(e).font(.caption2).foregroundColor(.danger) }
                            else if (PersianNumber.parse(rawLines[i].tax).value ?? 0) > 100 { Text("بیش از ۱۰۰٪").font(.caption2).foregroundColor(.danger) }
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            TextField("عوارض ٪", text: $rawLines[i].duty)
                                .keyboardType(.decimalPad).textFieldStyle(RoundedBorderTextFieldStyle())
                                .focused($focusedField, equals: .lineDuty(i))
                                .onChange(of: rawLines[i].duty) { raw in
                                    let r = PersianNumber.parse(raw)
                                    rawLines[i].dutyErr = r.error
                                    if r.error == nil { draft.lines[i].dutyPct = r.value ?? 0; recompute() }
                                }
                            if let e = rawLines[i].dutyErr { Text(e).font(.caption2).foregroundColor(.danger) }
                            else if (PersianNumber.parse(rawLines[i].duty).value ?? 0) > 100 { Text("بیش از ۱۰۰٪").font(.caption2).foregroundColor(.danger) }
                        }
                        Button("تخفیف‌ها (\(line.discs.count))") { showDiscountSheet = i }
                            .buttonStyle(GhostButtonStyle())
                            .accessibilityLabel("تخفیف‌های قلم \(i+1)")
                    }
                    HStack {
                        Text("جمع قلم: \(fa(Int(line.total))) ریال").font(.caption.bold()).lineLimit(1).minimumScaleFactor(0.7)
                        Spacer()
                        if line.refPrice > 0 {
                            Text("اختلاف: \(fa(Int(line.diffAmt))) (\(String(format: "%.1f", line.diffPct))٪)")
                                .font(.caption2).foregroundColor(line.diffAmt > 0 ? .danger : line.diffAmt < 0 ? .success : .muted)
                                .lineLimit(1).minimumScaleFactor(0.6)
                        }
                    }
                    .accessibilityElement(children: .combine)
                    Button("حذف قلم", role: .destructive) { showDeleteLineConfirm = i }
                        .buttonStyle(GhostButtonStyle())
                }
            }
            .swipeActions { Button("حذف", role: .destructive) { showDeleteLineConfirm = i } }
        )
    }

    // MARK: - Totals
    var totalsCard: some View {
        StoneCard {
            VStack(alignment: .leading, spacing: 4) {
                Text("جمع‌بندی سند").font(.subheadline.bold()).accessibilityAddTraits(.isHeader)
                totalRow("جمع اقلام", totals.goods)
                totalRow("تخفیفات (\(String(format: "%.1f", totals.discPct))٪)", totals.discSum)
                totalRow("مالیات", totals.tax)
                totalRow("عوارض", totals.duty)
                totalRow("جمع کل", totals.grand, bold: true)
                totalRow("تخفیف نقدی", totals.cashDisc)
                totalRow("قابل پرداخت", totals.payable, bold: true, color: .emerald)
                totalRow("اختلاف کل با مرجع", totals.diff)
            }
        }
    }

    func totalRow(_ label: String, _ value: Double, bold: Bool = false, color: Color = .ink) -> some View {
        HStack {
            Text(label).font(.caption).foregroundColor(.muted).lineLimit(1)
            Spacer()
            Text("\(fa(Int(value))) ریال").font(bold ? .caption.bold() : .caption).foregroundColor(color).lineLimit(1).minimumScaleFactor(0.6)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label) \(fa(Int(value))) ریال")
    }

    // MARK: - Actions (یک Primary)
    var actionsRow: some View {
        VStack(spacing: 8) {
            Button("ثبت پیش‌فاکتور در سامانه") { save() }
                .buttonStyle(PrimaryButtonStyle())
                .accessibilityHint("ذخیره در دفترچهٔ محلی برنامه")
            Button("خروجی و اشتراک") { showExportSheet = true }
                .buttonStyle(GhostButtonStyle())
        }
    }

    // MARK: - Export sheet
    var exportSheet: some View {
        NavigationView {
            List {
                Section("انتخاب خروجی") {
                    Button("متن خلاصه") { doShare(kind: "txt") }
                    Button("CSV (اکسل/شیت با BOM)") { doShare(kind: "csv") }
                    Button("اکسل (SpreadsheetML)") { doShare(kind: "xls") }
                    Button("ورد (HTML)") { doShare(kind: "doc") }
                    Button("PDF") { doShare(kind: "pdf") }
                }
                Section { Text("شیت اشتراک iOS باز می‌شود: AirDrop، واتساپ، تلگرام، Mail، بلوتوث — نام فایل با پسوند درست انتخاب می‌شود.").font(.caption).foregroundColor(.muted) }
            }
            .navigationTitle("خروجی و اشتراک")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("بستن") { showExportSheet = false } } }
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
            .onChange(of: text.wrappedValue) { _ in isDirty = true }
            .accessibilityLabel(placeholder)
    }

    // حذف شد — هر فیلد عددی حالا inline مدیریت می‌شود

    func focusNext(after field: Field) {
        switch field {
        case .supplierName: focusedField = .supplierCode
        case .supplierCode: focusedField = .natId
        case .natId: focusedField = .phone
        case .phone: focusedField = .address
        case .address: focusedField = .docNo
        case .docNo: focusedField = .dueDays
        case .dueDays: focusedField = .buyerName
        case .buyerName: focusedField = .buyerCode
        case .buyerCode: focusedField = .cashDisc
        case .cashDisc: focusedField = .note
        case .note: focusedField = .lineCode(0)
        case .lineCode(let i): focusedField = .lineName(i)
        case .lineName(let i): focusedField = .lineQty(i)
        case .lineQty(let i): focusedField = .linePrice(i)
        case .linePrice(let i): focusedField = .lineRef(i)
        case .lineRef(let i): focusedField = .lineTax(i)
        case .lineTax(let i): focusedField = .lineDuty(i)
        case .lineDuty(let i):
            if i + 1 < draft.lines.count { focusedField = .lineCode(i + 1) } else { focusedField = nil }
        }
    }

    var keyboardToolbar: some ToolbarContent {
        ToolbarItemGroup(placement: .keyboard) {
            Button("‹ قبلی") { moveFocus(-1) }.disabled(focusedField == nil)
            Button("بعدی ›") { moveFocus(1) }
            Spacer()
            Button("تمام") { focusedField = nil }
        }
    }
    func moveFocus(_ dir: Int) {
        guard let cur = focusedField else { focusedField = .supplierName; return }
        // ساده: next/prev با focusNext
        if dir > 0 { focusNext(after: cur) }
        else {
            // reverse map
            switch cur {
            case .supplierCode: focusedField = .supplierName
            case .natId: focusedField = .supplierCode
            case .phone: focusedField = .natId
            case .address: focusedField = .phone
            case .docNo: focusedField = .address
            case .dueDays: focusedField = .docNo
            case .buyerName: focusedField = .dueDays
            case .buyerCode: focusedField = .buyerName
            case .cashDisc: focusedField = .buyerCode
            default: focusedField = nil
            }
        }
    }

    func syncFromDraft() {
        rawDueDays = draft.dueDays == 0 ? "" : "\(draft.dueDays)"
        rawCashDisc = draft.cashDiscPct == 0 ? "" : (draft.cashDiscPct == Double(Int(draft.cashDiscPct)) ? "\(Int(draft.cashDiscPct))" : String(format: "%.2f", draft.cashDiscPct))
        rawLines = draft.lines.map { l in
            var r = RawLine()
            r.qty = l.qty == 0 ? "" : (l.qty == Double(Int(l.qty)) ? "\(Int(l.qty))" : "\(l.qty)")
            r.price = l.price == 0 ? "" : (l.price == Double(Int(l.price)) ? "\(Int(l.price))" : "\(l.price)")
            r.ref = l.refPrice == 0 ? "" : "\(Int(l.refPrice))"
            r.tax = "\(Int(l.taxPct))"
            r.duty = l.dutyPct == 0 ? "" : "\(Int(l.dutyPct))"
            return r
        }
        isDirty = false
    }

    func recompute() {
        // مقادیر draft از raw ها قبلاً sync شده؛ فقط compute
        var copy = draft
        totals = PreinvoiceEngine.compute(&copy)
        draft.lines = copy.lines
        isDirty = true
    }

    func removeLine(at idx: Int) {
        draft.lines.remove(at: idx)
        rawLines.remove(at: idx)
        showDeleteLineConfirm = nil
        recompute()
    }

    func confirmDirtyOrDo(_ action: @escaping () -> Void) {
        if !isDirty { action(); return }
        // ساده: بدون دیالوگ مزاحم، فقط اجرا — چون ذخیرهٔ محلی ارزان است؛ کاربر با «ثبت» تأیید می‌کند
        action()
    }

    func deleteCurrentDraft() {
        if !draft.id.isEmpty { PreinvoiceStore.delete(id: draft.id) }
        draft = PreinvoiceEngine.Draft(); syncFromDraft(); recompute()
    }

    func save() {
        if draft.supplierName.trimmingCharacters(in: .whitespaces).isEmpty {
            limitMessage = "نام فروشنده را وارد کنید"; showLimitAlert = true; return
        }
        if draft.lines.isEmpty {
            limitMessage = "دست‌کم یک قلم کالا لازم است"; showLimitAlert = true; return
        }
        // اعتبارسنجی اعداد
        for (idx, r) in rawLines.enumerated() {
            if PersianNumber.parse(r.qty).error != nil || PersianNumber.parse(r.price).error != nil {
                limitMessage = "قلم \(idx+1): عدد نامعتبر — لطفاً اصلاح کنید"; showLimitAlert = true; return
            }
        }
        if FeatureFlags.isFree && !FeatureFlags.canCreateInvoice() {
            limitMessage = "سقف \(FeatureFlags.freeMonthlyLimit) فاکتور رایگان این ماه پر شد."; showLimitAlert = true; return
        }
        if FeatureFlags.isFree && FeatureFlags.freeLocksTieredDiscount {
            let hasTiered = draft.lines.contains { $0.discs.contains { $0.type == .tieredQty || $0.type == .tieredAmount } }
            if hasTiered { limitMessage = "تخفیف پله‌ای فقط در نسخهٔ کامل فعال است."; showLimitAlert = true; return }
        }
        var copy = draft
        PreinvoiceStore.save(&copy)
        draft = copy
        syncFromDraft()
        isDirty = false
        FeatureFlags.recordInvoice()
#if canImport(UIKit)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
#endif
    }

    func doShare(kind: String) {
        var copy = draft
        let t = PreinvoiceEngine.compute(&copy)
#if canImport(UIKit)
        let title = "پیش‌فاکتور خرید \(draft.docNo.isEmpty ? "" : draft.docNo)"
        var items: [Any] = []
        switch kind {
        case "csv":
            let data = PreinvoiceExport.csv(draft: draft, totals: t)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("preinvoice.csv")
            try? data.write(to: url); items = [url]
        case "xls":
            let data = PreinvoiceExport.xls(draft: draft, totals: t)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("preinvoice.xls")
            try? data.write(to: url); items = [url]
        case "doc":
            let data = PreinvoiceExport.doc(draft: draft, totals: t)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("preinvoice.doc")
            try? data.write(to: url); items = [url]
        case "pdf":
            let data = PreinvoiceExport.pdf(draft: draft, totals: t)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("preinvoice.pdf")
            try? data.write(to: url); items = [url]
        default:
            let txt = PreinvoiceExport.summary(draft: draft, totals: t)
            items = [txt]
        }
        let av = UIActivityViewController(activityItems: items, applicationActivities: nil)
        if let pop = av.popoverPresentationController, let win = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first?.keyWindow {
            pop.sourceView = win; pop.sourceRect = CGRect(x: win.bounds.midX, y: win.bounds.midY, width: 0, height: 0)
        }
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first?.keyWindow?.rootViewController?.present(av, animated: true)
        showExportSheet = false
        if !draft.id.isEmpty { draft.status = "sent"; var c = draft; PreinvoiceStore.save(&c) }
#else
        // روی macOS (swift test) فقط لاگ — اشتراک iOS فقط روی دستگاه
        print("Share \(kind) — \(PreinvoiceExport.summary(draft: draft, totals: t))")
        showExportSheet = false
#endif
    }

    var freeBanner: some View {
        HStack(spacing: 6) {
            Image(systemName: "gift.fill").font(.caption2)
            Text("نسخهٔ رایگان — \(FeatureFlags.freeMonthlyLimit) فاکتور در ماه، PDF با واترمارک")
                .font(.caption2).lineLimit(1).minimumScaleFactor(0.7)
            Spacer()
            Text("ارتقا").font(.caption2.bold()).padding(.horizontal, 8).padding(.vertical, 4)
                .background(Color.white.opacity(0.9)).cornerRadius(6)
        }
        .foregroundColor(.white).padding(.horizontal, 12).padding(.vertical, 6)
        .background(Color.emerald).cornerRadius(8).padding(.horizontal, 8)
        .accessibilityLabel("نسخه رایگان \(FeatureFlags.freeMonthlyLimit) فاکتور در ماه")
    }

    func roundFa(_ r: String? = nil) -> String {
        let v = r ?? draft.rounding
        if v == "toman" { return "تومان" }
        if v == "hundred" { return "صد ریال" }
        if v == "thousand" { return "هزار ریال" }
        if v == "rial" { return "ریال" }
        return "بدون گرد"
    }
    func jalaliDate(_ ts: Int64) -> String {
        let d = Date(timeIntervalSince1970: TimeInterval(ts)/1000)
        let cal = Calendar(identifier: .gregorian)
        _ = cal.dateComponents(in: TimeZone.current, from: d)
        // برای نمایش جلالی از فرمت شمسی ساده — بدون وابستگی خارجی، فقط میلادی به‌عنوان fallback
        let f = DateFormatter(); f.calendar = Calendar(identifier: .persian); f.locale = Locale(identifier: "fa_IR"); f.dateStyle = .medium; f.timeStyle = .none
        return f.string(from: d)
    }
    func fa(_ n: Int) -> String {
        let f = NumberFormatter(); f.locale = Locale(identifier: "fa_IR"); f.numberStyle = .decimal
        return f.string(from: NSNumber(value: n)) ?? "\(n)"
    }
}

// MARK: - Discount sheet
struct DiscountSheet: View {
    @Binding var draft: PreinvoiceEngine.Draft
    let index: Int
    var onChange: () -> Void
    @Environment(\.dismiss) var dismiss
    @State private var type = PreinvoiceEngine.DiscType.rowPercent
    @State private var v1 = ""
    @State private var v2 = ""
    @State private var spec = ""

    var body: some View {
        NavigationView {
            Form {
                Section("افزودن تخفیف") {
                    Picker("نوع", selection: $type) {
                        ForEach(PreinvoiceEngine.DiscType.allCases, id: \.self) { t in Text(t.fa).tag(t) }
                    }
                    TextField("درصد یا مبلغ هر واحد", text: $v1).keyboardType(.decimalPad)
                    if type == .promoPercent {
                        TextField("سقف ریالی (۰ = بدون سقف)", text: $v2).keyboardType(.numberPad)
                    }
                    if type == .tieredQty || type == .tieredAmount {
                        TextField("پله‌ها مانند 10:5,50:10", text: $spec)
                        Text("هر پله «آستانه:درصد» — بزرگ‌ترین آستانهٔ ≤ مقدار برنده است.").font(.caption2).foregroundColor(.muted)
                    } else if type == .promoPercent {
                        TextField("نام کد (اختیاری)", text: $spec)
                    }
                    Button("افزودن") {
                        let a = PersianNumber.valueOrZero(v1), b = PersianNumber.valueOrZero(v2)
                        draft.lines[index].discs.append(PreinvoiceEngine.Disc(type: type, v1: a, v2: b, spec: spec))
                        v1 = ""; v2 = ""; spec = ""; onChange()
                    }
                    .disabled(PersianNumber.parse(v1).error != nil)
                }
                Section("تخفیف‌های این قلم (\(draft.lines[index].discs.count))") {
                    if draft.lines[index].discs.isEmpty {
                        Text("هنوز تخفیفی نیست").foregroundColor(.muted)
                    }
                    ForEach(Array(draft.lines[index].discs.enumerated()), id: \.offset) { idx, d in
                        HStack {
                            Text(d.type.fa).font(.caption)
                            Spacer()
                            Text(d.v1 == Double(Int(d.v1)) ? "\(Int(d.v1))" : "\(d.v1)").font(.caption2).foregroundColor(.muted)
                            if !d.spec.isEmpty { Text(d.spec).font(.caption2).foregroundColor(.muted).lineLimit(1) }
                        }
                    }
                    .onDelete { idxSet in
                        draft.lines[index].discs.remove(atOffsets: idxSet); onChange()
                    }
                    Text("سوایپ به چپ برای حذف — ترتیب اعمال ثابت است: ریالی → پله‌ای تعداد → پله‌ای مبلغ → درصد ردیف → کد؛ درصد کل فاکتور جدا روی سند.").font(.caption2).foregroundColor(.muted)
                }
            }
            .navigationTitle("تخفیف‌های قلم")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("بستن") { dismiss() } } }
        }
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
    var action: (() -> Void)? = nil
    var body: some View {
        VStack(spacing: 8) {
            // طلایی ملایم — تنها عنصر طلایی غالب صفحه (مطابق اسپک: یک طلایی غالب)
            ZStack {
                Circle().fill(Color(red: 0.76, green: 0.65, blue: 0.22).opacity(0.14)).frame(width: 44, height: 44)
                Image(systemName: icon).font(.title3).foregroundColor(Color(red: 0.65, green: 0.53, blue: 0.16))
            }
            Text(title).font(.caption.bold()).foregroundColor(.ink)
            // C11: حداکثر یک خط توضیح، ellipsize
            Text(subtitle).font(.caption2).foregroundColor(.muted).lineLimit(1).truncationMode(.tail).multilineTextAlignment(.center)
            if let act = action {
                Button("نمایش", action: act).buttonStyle(PrimaryButtonStyle()).frame(maxWidth: 160)
            }
        }.frame(maxWidth: .infinity).padding(16)
        .accessibilityElement(children: .combine)
    }
}
struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.frame(maxWidth: .infinity).padding(.vertical, 12)
            .background(Color.emerald).foregroundColor(.white).cornerRadius(12).scaleEffect(configuration.isPressed ? 0.98 : 1)
            .accessibilityAddTraits(.isButton)
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
