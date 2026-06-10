# Firebase Cloud Messaging (FCM) setup — Global blocklist push

The app and backend are fully wired for push-based blocklist refresh. To turn it
on you need your own Firebase project. Until you complete these steps, the app
still works — it just falls back to syncing when opened or when a call arrives.

## 1. Create a Firebase project
- Go to https://console.firebase.google.com and create a project.
- Add an **Android app** with package name: `pro.onephone.callfilter`
  (and `pro.onephone.callfilter.debug` if you want pushes in debug builds).

## 2. Add google-services.json to the app
- Download `google-services.json` from the Firebase console.
- Place it at: `android/app/google-services.json`
- The build auto-detects this file and enables Firebase. (Without it, the build
  still succeeds and FCM stays off.)

## 3. Get the server key for the backend
- Firebase console → Project settings → **Cloud Messaging**.
- Copy the **Server key** (enable the legacy Cloud Messaging API if prompted).
- In the admin panel: **Settings** → set **`fcm_server_key`** to that value
  (or set the `FCM_SERVER_KEY` environment variable on the backend).

## 4. Deploy & rebuild
- Redeploy the backend (so migrations 023 + the push trigger are live).
- Clean-rebuild the app: `./gradlew clean assembleSideloadRelease`

## How it works
- Devices subscribe to the `global_blocklist` FCM topic on launch.
- When an admin adds/edits/removes a global-blocklist number, the backend sends
  a silent data push to that topic.
- Each device wakes and calls `GET /api/global-blocklist/delta?since_id=<cursor>`
  to pull only the changes into its local SQLite store.

## Notes
- The local list is stored in SQLite (indexed), so it scales to hundreds of
  thousands of numbers with fast lookups and low memory.
- Sync is incremental (delta) using a server-side change-log, so only changes
  are transferred — not the whole list each time.
- Per your choice, a call is never delayed waiting on the network: blocking
  decisions use the fast local list.
