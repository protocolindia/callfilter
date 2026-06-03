import SwiftUI

struct ProfileView: View {
    @EnvironmentObject var auth: AuthStore
    @State private var sub: SubscriptionResponse?
    @State private var loading = true

    var body: some View {
        ZStack {
            Theme.bg.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 16) {

                    // Signed-in card
                    HStack(spacing: 14) {
                        Image(systemName: "person.crop.circle.fill")
                            .resizable().frame(width: 52, height: 52)
                            .foregroundColor(Theme.accent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("SIGNED IN").font(.caption).foregroundColor(Theme.subtext)
                            if !auth.name.isEmpty {
                                Text(auth.name).font(.title3.bold()).foregroundColor(.white)
                            }
                            Text(auth.fullNumber).font(.subheadline).foregroundColor(Theme.subtext)
                        }
                        Spacer()
                    }
                    .padding().background(Theme.card).cornerRadius(12)

                    // Subscription card
                    VStack(alignment: .leading, spacing: 8) {
                        Text("✨ SUBSCRIPTION").font(.caption.bold()).foregroundColor(Theme.subtext)
                        if loading {
                            ProgressView().tint(.white)
                        } else if let sub {
                            let active = sub.active ?? false
                            Text(active ? "Active" : "Inactive")
                                .font(.title2.bold())
                                .foregroundColor(active ? .green : Theme.reject)
                            if let plan = sub.planName {
                                Text(plan).foregroundColor(Theme.subtext)
                            }
                            if let secs = sub.secondsRemaining, secs > 0 {
                                Text(timeLeft(secs)).font(.footnote).foregroundColor(Theme.subtext)
                            }
                        } else {
                            Text("No subscription info").foregroundColor(Theme.subtext)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding().background(Theme.card).cornerRadius(12)

                    Spacer(minLength: 30)

                    // Sign out at the bottom
                    Button(role: .destructive) {
                        auth.logout()
                    } label: {
                        Text("Sign out")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Theme.card)
                            .foregroundColor(Theme.reject)
                            .cornerRadius(12)
                    }
                }
                .padding()
            }
        }
        .navigationTitle("Profile")
        .task { await loadSubscription() }
    }

    private func timeLeft(_ secs: Int) -> String {
        let days = secs / 86400
        if days > 0 { return "\(days) day\(days == 1 ? "" : "s") left" }
        let hours = secs / 3600
        return "\(hours)h left"
    }

    private func loadSubscription() async {
        guard !auth.userId.isEmpty else { loading = false; return }
        do {
            let resp = try await API.get("/subscription/\(auth.userId)", as: SubscriptionResponse.self)
            await MainActor.run {
                self.sub = resp
                if let n = resp.name, !n.isEmpty { auth.updateName(n) }
                loading = false
            }
        } catch {
            await MainActor.run { loading = false }
        }
    }
}
