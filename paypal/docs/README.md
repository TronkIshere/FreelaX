# PaySim (`paypal`) — Flutter client

A Flutter mobile app styled after PayPal's UI/UX, built to demo a fee
comparison tool: **PayPal (mock fee model) vs USDC + legal off-ramp + MISA**,
for freelancers receiving international payments. This is a hackathon/pitch
demo, not a real payment integration.

See `REFERENCE.md` for the full technical reference (architecture, folder
map, design tokens, API contracts, feature status). This file is a shorter
quick-start / handoff note.

## Blocking issue — not resolved in this drop

`Tong_quan_du_an_USDC_Freelancer.docx` (the doc describing the intended
"send money to freelancer marketplace" workflow) arrived **corrupted at the
byte level**: `word/document.xml` extracts to 0 bytes (`invalid compressed
data to inflate` / corrupt deflate stream). Confirmed via `extract-text`,
`pandoc`, and manual raw-deflate decompression — all three lose the content;
only the title survived: *"Tổng quan dự án Cổng thanh toán USDC cho
Freelancer"*. Metadata shows it was exported from **Claude Docs**
(`Claude Docs node/de3e2a89-459a@15`).

**Result: the freelancer-marketplace money-transfer screen is not built.**
To unblock, provide one of:
1. A claude.ai link to the live Claude Doc (if it still exists) — can be read
   directly, no export step involved.
2. A fresh `.docx` export.
3. The text pasted directly into chat.

## No live browser access in the generating session

The PayPal-like visual language here (navy/blue palette, balance card,
Send/Receive button pair, bottom nav) comes from general prior knowledge of
PayPal's design patterns — **not a live screenshot of the current app**.
PayPal periodically refreshes its brand; if pixel-accuracy matters for the
pitch, share current screenshots for a follow-up pass.

## One naming decision worth knowing

The app's display name is **"PaySim"**, not literally "PayPal"
(`lib/core/constants/app_strings.dart`). The project's own
`PAYPAL_MOCK_REFERENCE.md` warns against implying a real PayPal integration;
keeping the display name distinct while the *visual language* mimics PayPal
avoids any brand-impersonation ambiguity in front of judges. Change one line
in `app_strings.dart` to override this.

## What's real vs. placeholder

| Feature | Status | Why |
|---|---|---|
| Login / Register / Forgot password / OTP | **Real** — calls `/api/v1/auth/*` | `RemoteAuthRepository` already wired |
| **Fee comparison (PayPal vs USDC+MISA)** | **Real** — calls `POST /api/v1/simulations/compare` | Matches `PAYPAL_SIMULATION_MODULE.md` |
| Send money / Receive money | Placeholder → "Coming soon" sheet | No backing API yet |
| Top up / Withdraw / Scan QR / My cards | Placeholder → "Coming soon" sheet | No backing API yet |
| Notifications bell | Placeholder | No backing API yet |
| Activity tab (transaction history) | Static empty state | Backend simulation module doesn't persist history (by design, per docs) |
| **Send money to freelancer marketplace** | ❌ Not built | Blocked on the corrupted docx (see above) |

Every placeholder calls the same helper:
`showComingSoon(context, featureName: '...')` in
`lib/shared/widgets/coming_soon.dart`. Wiring a real feature later means
replacing that call at the relevant call site with real logic — no other
plumbing changes needed.

## Dropped from the original `brome_clean` template

`points_config.dart`, `reminder_presets.dart`, and every
loyalty/reminder/rewards/notification/scan-box screen or service, plus the
`flutter_local_notifications`, `timezone`, `flutter_timezone`,
`permission_handler`, `mobile_scanner`, `url_launcher` dependencies — all
specific to the original brush-cleaning reminder app this codebase was
reused from. Dropped so the app doesn't request camera/notification
permissions it never uses (looks unprofessional mid-demo).

Also replaced a real-looking personal Gmail address hardcoded in the
original `mock_auth_repository.dart` / `login_screen.dart` with
`demo@paysim.local` — a real personal email shouldn't sit in reusable
sample code regardless of where it came from.

## Server configuration (required before login works)

`defaultBaseUrl` in `app_config_service.dart` is **empty** on purpose (the
original template pointed at a fixed production domain; this project's
backend runs on a dev machine with no fixed domain yet). On first launch, go
to the **Settings** tab and enter `http://<backend machine's LAN IP>:<port>`,
e.g. `http://192.168.1.23:8080`. Get the IP via `ipconfig` (Windows) or
`ifconfig` / `ip addr` (macOS/Linux) on the machine running the backend —
phone and laptop must be on the same WiFi network.

## Not verified against a real Flutter toolchain

The generating sandbox has no Flutter/Dart SDK and no network path to
pub.dev, so `flutter pub get` / `flutter analyze` could not be run here.
Static checks performed instead: every relative import resolves to a file
that exists, and brace/paren counts balance in every file. Still run
`flutter pub get && flutter analyze` right after unzipping, before building
an APK.

## What's in the zip

- `lib/` — all Dart source (package name `paypal`, unchanged from your setup)
- `pubspec.yaml` — trimmed to drop unused dependencies
- `assets/logo/` — empty folder; **drop your `logo.png` in here** (if
  missing, `AppLogo` falls back to a generic icon instead of crashing)
- `android/app/src/main/AndroidManifest.xml` — camera/notification
  permissions removed
- `android/settings.gradle.kts`, `android/build.gradle.kts`,
  `android/app/build.gradle.kts` — unchanged, included for reference only

**Not included** (kept from your existing project untouched — never seen by
the generator): `android/app/src/main/kotlin/.../MainActivity.kt`, the
Gradle wrapper, `local.properties`, the `ios/` folder, existing mipmap icons.
