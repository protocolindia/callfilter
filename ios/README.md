# AI CallFilter — iOS Companion

A native Swift + SwiftUI iOS app that connects to the **same backend** as your Android app
(`https://api.app.onephone.pro`) and blocks spam numbers using Apple's **CallKit Call
Directory Extension**.

## ⚠️ Read this first — iOS platform limits

iOS does **not** allow what Android does. These are Apple OS restrictions, not code gaps:

| Android feature | iOS equivalent |
|---|---|
| Real-time call screening / reject | ❌ Not possible — only a pre-loaded static list |
| "Block this number?" post-call popup | ❌ No overlays allowed |
| Block reason shown | ❌ iOS blocks silently |
| Auto-reply SMS | ❌ Forbidden by iOS |
| "Block all except contacts" live | ❌ Not possible |
| Pattern/prefix blocking at call time | ❌ Only exact numbers, loaded in advance |

What this app **does**: signs in with OTP, fetches the global blocklist from your server,
and feeds those exact numbers into iOS so the OS blocks them silently. Plus profile &
subscription screens.

## Project layout

```
AICallFilter/
├── AICallFilter/                 ← main app target
│   ├── AICallFilterApp.swift     ← entry point + theme
│   ├── LoginView.swift           ← phone + name → /signup
│   ├── OTPView.swift             ← /verify-otp
│   ├── HomeView.swift            ← blocked count, Sync now, enable banner
│   ├── ProfileView.swift         ← subscription status, Sign out
│   ├── BlocklistManager.swift    ← syncs list + reloads the extension
│   └── AICallFilter.entitlements ← App Group
├── CallDirectoryExtension/       ← the actual iOS blocking mechanism
│   ├── CallDirectoryHandler.swift
│   ├── Info.plist
│   └── CallDirectoryExtension.entitlements ← same App Group
└── Shared/                       ← compiled into BOTH targets
    ├── Models.swift
    ├── API.swift
    └── AuthStore.swift
```

## Setup in Xcode (one-time)

1. **Create the project**: Xcode → New Project → iOS App → name `AICallFilter`,
   interface **SwiftUI**, language **Swift**. Bundle ID `pro.onephone.callfilter`.

2. **Add the extension target**: File → New → Target → **Call Directory Extension**.
   Name it `CallDirectoryExtension`. Bundle ID becomes
   `pro.onephone.callfilter.CallDirectoryExtension`.

3. **Drop in the files**: replace/add the Swift files from each folder into the matching
   target. The three files in `Shared/` must have **both** targets checked in the
   File Inspector → Target Membership.

4. **App Group** (lets the app and extension share the blocklist):
   - Select the app target → Signing & Capabilities → + Capability → **App Groups** →
     add `group.pro.onephone.callfilter`.
   - Repeat for the extension target with the **same** group ID.
   - Confirm both `.entitlements` files list that group (already done in this scaffold).

5. **Bundle IDs must match the constants in code**:
   - `BlocklistManager.extensionIdentifier` = `pro.onephone.callfilter.CallDirectoryExtension`
   - `AuthStore.appGroup` = `group.pro.onephone.callfilter`
   Adjust both if you use a different prefix.

6. **Run on a real device** (CallKit extensions don't work in the simulator for blocking).

## How the user enables blocking

After install, iOS requires the user to manually turn the extension on:
**Settings → Phone → Call Blocking & Identification → enable "AI CallFilter".**
The app shows a banner + "Open Settings" button when it detects the extension is off.

## Backend — no changes needed

The iOS app reuses these existing endpoints:
- `POST /api/signup` — sends OTP (fires `sms_url` from the device, like Android)
- `POST /api/verify-otp`
- `GET /api/subscription/:user_id` — also returns the user's `name`
- `GET /api/global-blocklist` — the numbers iOS will block

## Number format

CallKit needs ascending `Int64` numbers with country code, e.g. `+91 98123 45678` →
`919812345678`. `BlocklistManager.toCallKitNumber` handles this (defaults to +91 if a bare
10-digit number is given — change the default country code there if needed).

## Submitting to the App Store

- You'll need an Apple Developer account ($99/yr).
- Call Directory Extensions are allowed on the App Store (apps like Truecaller use them).
- In App Store Connect, describe it honestly as a "call blocking & identification" app.
