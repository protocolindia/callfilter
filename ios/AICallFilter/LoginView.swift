import SwiftUI

struct LoginView: View {
    @EnvironmentObject var auth: AuthStore
    @State private var dialCode = "+91"
    @State private var mobile = ""
    @State private var name = ""
    @State private var loading = false
    @State private var error: String?
    @State private var goToOTP = false
    @State private var pendingUserId = 0

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.bg.ignoresSafeArea()
                VStack(spacing: 20) {
                    Spacer()
                    Text("🛡️ AI CallFilter")
                        .font(.largeTitle.bold())
                        .foregroundColor(.white)
                    Text("Sign in to sync your spam blocklist")
                        .font(.subheadline)
                        .foregroundColor(Theme.subtext)

                    VStack(spacing: 12) {
                        TextField("", text: $name, prompt: Text("Your name").foregroundColor(Theme.subtext))
                            .textFieldStyle(.plain)
                            .padding()
                            .background(Theme.surface)
                            .cornerRadius(8)
                            .foregroundColor(.white)

                        HStack {
                            TextField("", text: $dialCode)
                                .frame(width: 60)
                                .padding()
                                .background(Theme.surface)
                                .cornerRadius(8)
                                .foregroundColor(.white)
                            TextField("", text: $mobile, prompt: Text("Mobile number").foregroundColor(Theme.subtext))
                                .keyboardType(.phonePad)
                                .padding()
                                .background(Theme.surface)
                                .cornerRadius(8)
                                .foregroundColor(.white)
                        }
                    }
                    .padding(.horizontal)

                    if let error {
                        Text(error).foregroundColor(Theme.reject).font(.footnote)
                    }

                    Button(action: signup) {
                        Text(loading ? "Sending..." : "Send OTP")
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Theme.accent)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                    }
                    .disabled(loading || mobile.isEmpty)
                    .padding(.horizontal)

                    Spacer()
                }
                .navigationDestination(isPresented: $goToOTP) {
                    OTPView(dialCode: dialCode, mobile: mobile, name: name, userId: pendingUserId)
                }
            }
        }
    }

    private func signup() {
        loading = true; error = nil
        Task {
            do {
                let resp = try await API.post("/signup",
                    body: ["dial_code": dialCode, "mobile": mobile, "name": name],
                    as: SignupResponse.self)
                // Production mode: backend returns sms_url for the device to fire
                if let smsUrl = resp.smsUrl, !smsUrl.isEmpty {
                    API.fireSmsUrl(smsUrl)
                }
                await MainActor.run {
                    pendingUserId = resp.userId ?? 0
                    loading = false
                    goToOTP = true
                }
            } catch {
                await MainActor.run {
                    self.error = error.localizedDescription
                    loading = false
                }
            }
        }
    }
}
