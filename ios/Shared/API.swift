import Foundation

/// Talks to the same backend as the Android app.
/// Base URL points at the production API.
enum API {
    static let baseURL = "https://api.app.onephone.pro/api"

    enum APIClientError: Error, LocalizedError {
        case badURL
        case noData
        case server(String)
        case decoding

        var errorDescription: String? {
            switch self {
            case .badURL:        return "Invalid URL"
            case .noData:        return "No response from server"
            case .server(let m): return m
            case .decoding:      return "Could not read server response"
            }
        }
    }

    // Generic POST
    static func post<T: Decodable>(_ path: String,
                                   body: [String: Any],
                                   as type: T.Type) async throws -> T {
        guard let url = URL(string: baseURL + path) else { throw APIClientError.badURL }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        req.timeoutInterval = 20

        let (data, resp) = try await URLSession.shared.data(for: req)
        try Self.checkStatus(resp, data)
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw APIClientError.decoding
        }
    }

    // Generic GET
    static func get<T: Decodable>(_ path: String, as type: T.Type) async throws -> T {
        guard let url = URL(string: baseURL + path) else { throw APIClientError.badURL }
        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.timeoutInterval = 20

        let (data, resp) = try await URLSession.shared.data(for: req)
        try Self.checkStatus(resp, data)
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw APIClientError.decoding
        }
    }

    private static func checkStatus(_ resp: URLResponse, _ data: Data) throws {
        guard let http = resp as? HTTPURLResponse else { return }
        if !(200...299).contains(http.statusCode) {
            if let apiErr = try? JSONDecoder().decode(APIError.self, from: data) {
                throw APIClientError.server(apiErr.error)
            }
            throw APIClientError.server("Server error \(http.statusCode)")
        }
    }

    /// Fire the SMS gateway URL from the device (production OTP mode),
    /// mirroring the Android `sendSmsFromDevice` approach.
    static func fireSmsUrl(_ urlString: String) {
        guard let url = URL(string: urlString) else { return }
        var req = URLRequest(url: url)
        req.timeoutInterval = 15
        URLSession.shared.dataTask(with: req).resume()
    }
}
