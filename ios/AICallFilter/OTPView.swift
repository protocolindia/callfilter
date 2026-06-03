import SwiftUI

struct OTPView: View {
    @EnvironmentObject var auth: AuthStore
    let dialCode: String
    let mobile: String
    let name: String
    let userId: Int

    @State private var code = ""
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        ZStack {
            Theme.bg.ignoresSafeArea()
            VStack(spacing: 20) {
                Spacer()
                Text("🔐 Verify")
                    .font(.largeTitle.bold())
                    .foregroundColor(.white)
                Text("Sent to \(dialCode)\(mobile)")
                    .font(.subheadline)
                    .foregroundColor(Theme.subtext)

                Text("VERIFICATION CODE")
                    .font(.caption.bold())
                    .foregroundColor(Theme.subtext)

                TextField("", text: $code, prompt: Text("Enter code").foregroundColor(Theme.subtext))
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.center)
                    .font(.title.bold())
                    .padding()
                    .background(Theme.surface)
                    .cornerRadius(8)
                    .foregroundColor(.white)
                    .padding(.horizontal)

                if let error {
                    Text(error).foregroundColor(Theme.reject).font(.footnote)
                }

                Button(action: verify) {
                    Text(loading ? "Verifying..." : "Verify")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Theme.accent)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                .disabled(loading || code.isEmpty)
                .padding(.horizontal)

                Spacer()
            }
        }
        .navigationBarBackButtonHidden(false)
    }

    private func verify() {
        loading = true; error = nil
        Task {
            do {
                let resp = try await API.post("/verify-otp",
                    body: ["user_id": userId, "code": code],
                    as: VerifyResponse.self)
                if resp.ok {
                    await MainActor.run {
                        auth.saveSession(userId: resp.userId ?? userId,
                                         fullNumber: dialCode + mobile,
                                         name: name)
                    }
                    // Sync blocklist after login
                    await BlocklistManager.shared.syncFromBackend()
                } else {
                    await MainActor.run { error = "Invalid code"; loading = false }
                }
            } catch {
                await MainActor.run { self.error = error.localizedDescription; loading = false }
            }
        }
    }
}
