import Foundation

/// Persists session + shares the blocklist with the Call Directory Extension
/// via an App Group (so the extension can read the numbers to block).
final class AuthStore: ObservableObject {
    static let shared = AuthStore()

    // IMPORTANT: this App Group ID must match in BOTH targets' entitlements.
    static let appGroup = "group.pro.onephone.callfilter"

    private let defaults: UserDefaults
    private let shared: UserDefaults

    @Published var userId: String
    @Published var name: String
    @Published var fullNumber: String
    @Published var isLoggedIn: Bool

    private init() {
        defaults = .standard
        shared = UserDefaults(suiteName: AuthStore.appGroup) ?? .standard
        userId     = defaults.string(forKey: "user_id") ?? ""
        name       = defaults.string(forKey: "name") ?? ""
        fullNumber = defaults.string(forKey: "full_number") ?? ""
        isLoggedIn = defaults.bool(forKey: "logged_in")
    }

    func saveSession(userId: Int, fullNumber: String, name: String) {
        self.userId = String(userId)
        self.fullNumber = fullNumber
        self.name = name
        self.isLoggedIn = true
        defaults.set(self.userId, forKey: "user_id")
        defaults.set(fullNumber, forKey: "full_number")
        defaults.set(name, forKey: "name")
        defaults.set(true, forKey: "logged_in")
    }

    func updateName(_ n: String) {
        guard !n.isEmpty else { return }
        name = n
        defaults.set(n, forKey: "name")
    }

    func logout() {
        userId = ""; name = ""; fullNumber = ""; isLoggedIn = false
        ["user_id", "name", "full_number", "logged_in"].forEach { defaults.removeObject(forKey: $0) }
        // Clear shared blocklist so the extension blocks nothing after logout
        saveBlocklist([])
    }

    // MARK: - Shared blocklist (read by the Call Directory Extension)

    /// Numbers must be E.164 (e.g. +919812345678) and SORTED ASCENDING for CallKit.
    func saveBlocklist(_ numbers: [Int64]) {
        let sorted = numbers.sorted()
        shared.set(sorted, forKey: "block_numbers")
    }

    func loadBlocklist() -> [Int64] {
        (shared.array(forKey: "block_numbers") as? [Int64]) ?? []
    }
}
