import Foundation

// MARK: - API response models (match the existing Node/Express backend)

struct SignupResponse: Codable {
    let ok: Bool
    let userId: Int?
    let otp: String?          // present only in demo mode
    let smsUrl: String?       // present in production mode — app sends the SMS itself

    enum CodingKeys: String, CodingKey {
        case ok
        case userId = "user_id"
        case otp
        case smsUrl = "sms_url"
    }
}

struct VerifyResponse: Codable {
    let ok: Bool
    let userId: Int?
    let token: String?

    enum CodingKeys: String, CodingKey {
        case ok
        case userId = "user_id"
        case token
    }
}

struct Subscription: Codable {
    let active: Bool
    let isTrial: Bool?
    let status: String?
    let secondsRemaining: Int?
    let planName: String?

    enum CodingKeys: String, CodingKey {
        case active
        case isTrial = "is_trial"
        case status
        case secondsRemaining = "seconds_remaining"
        case planName = "plan_name"
    }
}

struct SubscriptionResponse: Codable {
    let ok: Bool
    let name: String?
    let hasSubscription: Bool?
    let active: Bool?
    let secondsRemaining: Int?
    let planName: String?

    enum CodingKeys: String, CodingKey {
        case ok
        case name
        case hasSubscription = "has_subscription"
        case active
        case secondsRemaining = "seconds_remaining"
        case planName = "plan_name"
    }
}

// Global blocklist entry returned by GET /api/global-blocklist
struct GlobalBlockEntry: Codable, Identifiable {
    var id: String { number }
    let number: String
    let reason: String?
    let source: String?

    enum CodingKeys: String, CodingKey {
        case number, reason, source
    }
}

struct GlobalBlocklistResponse: Codable {
    let entries: [GlobalBlockEntry]
}

struct APIError: Codable, Error {
    let error: String
}
