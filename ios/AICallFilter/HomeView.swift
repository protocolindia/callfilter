import SwiftUI

struct HomeView: View {
    @EnvironmentObject var auth: AuthStore
    @StateObject private var blocklist = BlocklistManager.shared
    @State private var syncing = false
    @State private var showProfile = false

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.bg.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {

                        // Enable banner if extension is off
                        if !blocklist.isEnabled {
                            enableCard
                        }

                        // Blocked count card
                        card {
                            HStack {
                                Text("🚫").font(.system(size: 36))
                                VStack(alignment: .leading) {
                                    Text("\(blocklist.blockedCount)")
                                        .font(.system(size: 32, weight: .bold))
                                        .foregroundColor(Theme.reject)
                                    Text("Numbers blocked")
                                        .foregroundColor(Theme.subtext)
                                }
                                Spacer()
                            }
                        }

                        // Sync card
                        card {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Global Blocklist")
                                    .font(.headline).foregroundColor(.white)
                                Text("Sync the latest spam numbers from the server into iOS call blocking.")
                                    .font(.footnote).foregroundColor(Theme.subtext)
                                if let err = blocklist.lastSyncError {
                                    Text(err).font(.footnote).foregroundColor(Theme.reject)
                                }
                                Button(action: sync) {
                                    Text(syncing ? "Syncing..." : "Sync now")
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 10)
                                        .background(Theme.accent)
                                        .foregroundColor(.white)
                                        .cornerRadius(8)
                                }
                                .disabled(syncing)
                            }
                        }

                        // iOS limitations note
                        card {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("ℹ️ How blocking works on iOS")
                                    .font(.subheadline.bold()).foregroundColor(.white)
                                Text("iOS silently blocks numbers on your synced list. Unlike Android, it can't show a reason, ask per-call, or auto-reply by SMS — these are Apple restrictions.")
                                    .font(.footnote).foregroundColor(Theme.subtext)
                            }
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle("AI CallFilter")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Text("Signed in: \(auth.name.isEmpty ? auth.fullNumber : auth.name)")
                        .font(.footnote).foregroundColor(Theme.subtext)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showProfile = true } label: {
                        Image(systemName: "person.crop.circle.fill")
                            .foregroundColor(Theme.accent)
                    }
                }
            }
            .navigationDestination(isPresented: $showProfile) {
                ProfileView().environmentObject(auth)
            }
        }
        .onAppear {
            blocklist.refreshEnabledStatus()
        }
    }

    private var enableCard: some View {
        card {
            VStack(alignment: .leading, spacing: 6) {
                Text("⚠️ Blocking is OFF")
                    .font(.subheadline.bold()).foregroundColor(Theme.reject)
                Text("Enable call blocking in Settings → Phone → Call Blocking & Identification → turn on AI CallFilter.")
                    .font(.footnote).foregroundColor(Theme.subtext)
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }
                .foregroundColor(Theme.accent)
            }
        }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content()
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card)
            .cornerRadius(12)
    }

    private func sync() {
        syncing = true
        Task {
            await blocklist.syncFromBackend()
            await MainActor.run {
                syncing = false
                blocklist.refreshEnabledStatus()
            }
        }
    }
}
