import SwiftUI

@main
struct AICallFilterApp: App {
    @StateObject private var auth = AuthStore.shared

    var body: some Scene {
        WindowGroup {
            if auth.isLoggedIn {
                HomeView()
                    .environmentObject(auth)
            } else {
                LoginView()
                    .environmentObject(auth)
            }
        }
    }
}

// MARK: - Shared style tokens (dark theme matching the Android app)
enum Theme {
    static let bg      = Color(red: 0.059, green: 0.059, blue: 0.071)   // #0F0F12
    static let card    = Color(red: 0.102, green: 0.102, blue: 0.125)   // #1A1A20
    static let surface = Color(red: 0.133, green: 0.137, blue: 0.165)   // #22232A
    static let accent  = Color(red: 0.310, green: 0.557, blue: 0.969)   // #4F8EF7
    static let reject  = Color(red: 0.937, green: 0.267, blue: 0.267)   // #EF4444
    static let subtext = Color(red: 0.631, green: 0.631, blue: 0.667)   // #A1A1AA
}
