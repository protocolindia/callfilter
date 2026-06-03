import Foundation
import CallKit

/// Manages syncing the global blocklist into the Call Directory Extension.
final class BlocklistManager: ObservableObject {
    static let shared = BlocklistManager()

    // Must match the extension's bundle identifier exactly.
    static let extensionIdentifier = "pro.onephone.callfilter.CallDirectoryExtension"

    @Published var blockedCount: Int = 0
    @Published var lastSyncError: String?
    @Published var isEnabled: Bool = false

    private init() {
        blockedCount = AuthStore.shared.loadBlocklist().count
    }

    /// Convert a phone string (e.g. "+91 98123 45678" or "9812345678") into
    /// the Int64 form CallKit needs. Defaults to India (+91) if no country code.
    static func toCallKitNumber(_ raw: String, defaultCountryCode: String = "91") -> Int64? {
        var digits = raw.filter { $0.isNumber }
        if raw.hasPrefix("+") {
            // already has country code embedded
        } else if digits.count == 10 {
            digits = defaultCountryCode + digits
        }
        return Int64(digits)
    }

    /// Pull the global blocklist from the backend and store it for the extension.
    func syncFromBackend() async {
        do {
            let resp = try await API.get("/global-blocklist", as: GlobalBlocklistResponse.self)
            let nums = resp.entries.compactMap { Self.toCallKitNumber($0.number) }
            let unique = Array(Set(nums)).sorted()
            AuthStore.shared.saveBlocklist(unique)
            await MainActor.run { self.blockedCount = unique.count }
            reloadExtension()
        } catch {
            await MainActor.run { self.lastSyncError = error.localizedDescription }
        }
    }

    /// Ask iOS to re-run the Call Directory Extension with the latest list.
    func reloadExtension() {
        CXCallDirectoryManager.sharedInstance.reloadExtension(
            withIdentifier: Self.extensionIdentifier
        ) { error in
            DispatchQueue.main.async {
                if let error = error {
                    self.lastSyncError = "Reload failed: \(error.localizedDescription)"
                } else {
                    self.lastSyncError = nil
                }
            }
        }
    }

    /// Check whether the user has enabled our extension in Settings.
    func refreshEnabledStatus() {
        CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(
            withIdentifier: Self.extensionIdentifier
        ) { status, _ in
            DispatchQueue.main.async {
                self.isEnabled = (status == .enabled)
            }
        }
    }
}
