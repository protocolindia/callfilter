CallFilter v25.12.1 — hotfix for v25.12 Android build error
=============================================================

ONE-LINE: v25.12's new LoginActivity called auth.checkAndUnlockWithPin(pin)
which doesn't exist on AuthManager. Use checkPin(pin) + markLoggedIn()
instead. Renamed in this drop.

============================================================
WHAT CHANGED
============================================================

ONLY this single file:
   android/app/src/main/java/pro/onephone/callfilter/LoginActivity.java

Compile error was:
   error: cannot find symbol
       if (auth.checkAndUnlockWithPin(pin)) {

The method I invented in v25.9 didn't exist on AuthManager (the
real API is checkPin(pin) + markLoggedIn()).

I missed this in v25.9, v25.10, v25.11, and v25.12 because I was
only running node/JS syntax checks — not Android Studio's javac.
This drop has been cross-checked: all `auth.*` method calls in the
codebase reference real methods on AuthManager.

============================================================
EVERYTHING ELSE FROM v25.12 IS INTACT
============================================================

- Migration 009/010 with client_id INTEGER→TEXT (rules sync root cause fix)
- Migration runner loud-failure mode + server.js hard-fail
- Paywall paise → rupees / dollars
- "null" plan name normalized
- Subscription EXTENDS instead of REPLACES
- Profile: Manage button hidden, Buy/Extend label toggle
- Smart login flow + cross-nav links

============================================================
DEPLOY
============================================================

Same as v25.12 — overwrite + commit + push + rebuild APK:

cd D:\\callfilter
git pull origin main

robocopy F:\\app\\CallManager\\callfilter-v25.12.1-monorepo\\callfilter-monorepo\\android  android  /E
robocopy F:\\app\\CallManager\\callfilter-v25.12.1-monorepo\\callfilter-monorepo\\backend  backend  /E
robocopy F:\\app\\CallManager\\callfilter-v25.12.1-monorepo\\callfilter-monorepo\\frontend frontend /E
copy F:\\app\\CallManager\\callfilter-v25.12.1-monorepo\\callfilter-monorepo\\INTEGRATION_README.txt .

git add .
git commit -m "v25.12.1 — fix LoginActivity compile error (checkAndUnlockWithPin → checkPin + markLoggedIn)"
git push origin main

# In Android Studio: Rebuild → Generate Signed APK
