import Foundation
import CallKit

/// iOS calls this extension (on its own schedule, and when we request a reload)
/// to obtain the list of numbers to block / label. This is the ONLY way iOS
/// permits third-party call blocking — a static, pre-loaded, sorted list.
/// No real-time logic, overlays, reasons, or SMS are possible here.
class CallDirectoryHandler: CXCallDirectoryProvider {

    static let appGroup = "group.pro.onephone.callfilter"

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self

        // CallKit requires numbers added in ascending numeric order.
        let numbers = loadSharedBlocklist()

        for number in numbers {
            // addBlockingEntry blocks the call silently.
            context.addBlockingEntry(withNextSequentialPhoneNumber: number)
        }

        // (Optional) you could also addIdentificationEntry to label spam
        // instead of fully blocking — left out for the pure-block use case.

        context.completeRequest()
    }

    private func loadSharedBlocklist() -> [Int64] {
        let shared = UserDefaults(suiteName: Self.appGroup)
        let nums = (shared?.array(forKey: "block_numbers") as? [Int64]) ?? []
        // Ensure ascending order (CallKit will reject out-of-order entries).
        return nums.sorted()
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext,
                       withError error: Error) {
        // iOS will retry later. Log for debugging via Console.app.
        NSLog("[CallDirectory] request failed: \(error.localizedDescription)")
    }
}
