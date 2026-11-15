import SwiftUI

@main
struct MeelanoApp: App {
    var body: some Scene {
        WindowGroup {
            NavigationView {
                PreinvoiceView()
            }
            .navigationViewStyle(StackNavigationViewStyle())
        }
    }
}
