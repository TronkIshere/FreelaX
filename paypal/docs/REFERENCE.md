# PaySim (`paypal`) — Technical Reference

Purpose of this file: give a future AI assistant (or a new developer) full
context on this Flutter codebase in one read, without needing to re-explore
every file. Pair it with `README.md` (quick-start / handoff notes) and the
backend design docs `PAYPAL_MOCK_REFERENCE.md` / `PAYPAL_SIMULATION_MODULE.md`
(already in the project, describe the Java/Spring backend this app calls).

## 1. What this project is

A Flutter app that visually mimics PayPal's UI (balance card, Send/Receive
buttons, bottom nav) to demo one real feature: comparing the cost of
receiving an international payment via **PayPal** vs **USDC + a licensed
off-ramp + MISA** (a Vietnamese invoicing/tax module), for freelancers. Built
for a pitch/hackathon context, not a production payment app. Package name is
`paypal` (`pubspec.yaml` `name:`); the in-app display name is `PaySim`
(`AppStrings.appName`) — the two are deliberately different, see §7.

Only two things call a real backend:
1. Auth (`/api/v1/auth/*`) — login, register, forgot password, OTP, refresh.
2. Fee comparison (`POST /api/v1/simulations/compare`) — the pitch's core
   feature.

Everything else in the UI (send/receive money, top up, withdraw, QR scan,
cards, notifications, activity history) is a **static placeholder** that
opens a "Coming soon" bottom sheet. See §6 for the exact mechanism and §9 for
what's still missing.

## 2. Architecture

- **Pattern**: feature-first folders under `lib/features/<feature>/`, each
  split into `data/` (repositories, models) and `presentation/screens/` (and
  sometimes `presentation/widgets/`), plus a shared `lib/core/` (cross-
  feature infrastructure) and `lib/shared/widgets/` (reusable UI atoms).
- **State management**: Riverpod (`flutter_riverpod`). Long-lived services
  (`AuthService`, `AppConfigService`) are plain `ChangeNotifier` singletons
  (`instance` static field) wrapped in a `ChangeNotifierProvider` so widgets
  can `ref.watch`/`ref.read` them. This is **not** a `Notifier`/`AsyncNotifier`
  Riverpod-idiomatic setup — it's a singleton-service-plus-provider bridge,
  kept from the original template. New long-lived state should probably
  follow the same pattern for consistency, unless there's a reason to
  modernize it.
- **Networking**: `package:http`, wrapped by `ApiClient`
  (`core/services/api_client.dart`) for everything under `/api/v1/*` except
  auth, which has its own `RemoteAuthRepository`
  (`core/data/remote_auth_repository.dart`) hitting `/api/v1/auth/*`
  directly. Both funnel through `core/services/http_json.dart` for URL
  building, timeouts, and response envelope parsing.
- **Response envelope**: the backend wraps responses as
  `{ "code": ..., "data": {...} }` or `{ "code": ..., "error": "..." }`.
  `parseJsonBody()` in `http_json.dart` unwraps `data` on success and throws
  `HttpJsonException(message)` (message taken from `error`) on failure or
  non-2xx status. Repositories catch `HttpJsonException` and rethrow as their
  own domain exception (`AuthException`, `ApiException`, `SimulationException`)
  so screens only ever catch one exception type per call site.
- **Auth session persistence**: `flutter_secure_storage`, storing the
  serialized `AuthUser` (including tokens) under key `paysim_auth_user`.
  `AppConfigService` (the backend base URL) persists separately via
  `shared_preferences` under key `paysim_api_base_url` — deliberately a
  different, less sensitive storage mechanism.
- **401 handling**: `ApiClient._send()` retries once after calling
  `AuthService.instance.refreshAccessToken()` on a 401. If refresh itself
  throws a 401/403 `AuthException`, it forces `AuthService.instance.logout()`
  and rethrows, so the caller's error path ends up navigating back to login
  (see `SplashScreen`'s error branch for the analogous case at startup).

## 3. Directory map

```
lib/
├── main.dart                          entry point, MaterialApp + theme wiring
├── core/
│   ├── constants/
│   │   ├── app_colors.dart            ThemeExtension<AppColors>, palette (§4)
│   │   ├── app_spacing.dart           spacing scale (§4)
│   │   ├── app_strings.dart           app name/tagline (§7)
│   │   └── app_typography.dart        TextStyle helpers keyed off AppColors
│   ├── domain/
│   │   └── auth_repository.dart       AuthRepository interface + AuthException
│   ├── models/
│   │   └── auth_user.dart             AuthUser (id/email/displayName/tokens)
│   ├── data/
│   │   ├── mock_auth_repository.dart  offline AuthRepository impl (demo creds)
│   │   └── remote_auth_repository.dart HTTP AuthRepository impl (/api/v1/auth)
│   ├── services/
│   │   ├── http_json.dart             URL building, timeout, envelope parsing
│   │   ├── app_config_service.dart    backend base URL, persisted, ChangeNotifier
│   │   ├── auth_service.dart          session state, ChangeNotifier singleton
│   │   └── api_client.dart            generic /api/v1 client, 401 auto-refresh
│   └── utils/
│       └── nav_key.dart               global navigatorKey (context-free nav)
├── shared/widgets/
│   ├── app_background.dart            soft-glow gradient page background
│   ├── app_top_bar.dart                plain AppBar wrapper (title + actions)
│   ├── app_logo.dart                  Image.asset('assets/logo/logo.png') + fallback icon
│   ├── primary_button.dart            filled CTA button (loading/disabled states)
│   ├── section_card.dart              rounded white card with soft shadow
│   └── coming_soon.dart               showComingSoon() — the placeholder mechanism (§6)
└── features/
    ├── splash/presentation/screens/splash_screen.dart
    │       loads auth session, routes to LoginScreen or HomeShellScreen
    ├── auth/presentation/screens/
    │       login_screen.dart, register_screen.dart,
    │       forgot_password_screen.dart, otp_verification_screen.dart
    ├── home/presentation/screens/
    │       home_shell_screen.dart      bottom-nav shell, 4 tabs (§5)
    │       wallet_tab.dart             "home" tab: balance card, quick actions,
    │                                   fee-comparison highlight card, empty activity
    ├── simulation/                     the one real feature end-to-end
    │       data/simulation_models.dart         FeeLineItem, RailSimulationResult,
    │                                           PaymentComparisonResult (+fromJson)
    │       data/simulation_repository.dart     POST /api/v1/simulations/compare
    │       presentation/screens/compare_fee_screen.dart  form + result UI
    ├── activity/presentation/screens/activity_screen.dart   static empty state
    └── settings/presentation/
            screens/settings_screen.dart        profile row, server config, about, logout
            widgets/server_settings_section.dart backend base-URL editor (persisted)
```

## 4. Design tokens (`lib/core/constants/`)

`AppColors` (a `ThemeExtension`, accessed via
`Theme.of(context).extension<AppColors>()!`) — PayPal-inspired, not an exact
brand palette (no live screenshot access when generated, see `README.md`):

| Token | Hex | Intent |
|---|---|---|
| `background` | `#F7F9FC` | page background |
| `surface` | `#FFFFFF` | cards |
| `primaryDark` | `#001C64` | navy, gradient end for balance card |
| `primary` | `#0070E0` | PayPal-ish blue, primary CTA / accents |
| `primaryLight` | `#D6E9FF` | light tint |
| `secondary` | `#00A971` | teal/green, used for the fee-comparison highlight icon |
| `secondaryLight` | `#B6F0D8` | light tint |
| `text` | `#15181D` | body text |
| `textMuted` | `#6B7280` | captions, secondary text |
| `success` | `#1E8E3E` | e.g. the USDC+MISA rail's accent in the comparison result |
| `warning` | `#B25E00` | not currently used in any screen |
| `danger` | `#D64550` | errors, and the PayPal rail's accent in the comparison result |

`AppSpacing`: `xs=4, sm=8, md=16, lg=24, xl=32, xl2=40, xxl=48`.

`AppTypography`: static `heading1/heading2/body/bodyMuted/caption/score`
methods, each taking an `AppColors` instance and returning a themed
`TextStyle`. `score()` (28px/w800, colored `primaryDark`) is used for the
savings amount on `CompareFeeScreen`.

To re-theme the whole app, edit only `AppColors.light` — every screen reads
colors through the theme extension, never hardcoded hex values (`wallet_tab.dart`'s
balance-card gradient and white button labels are the one intentional
exception, since that card is drawn on a dark gradient regardless of theme).

## 5. Navigation

`SplashScreen` → `LoginScreen` (not logged in) or `HomeShellScreen` (logged
in). `HomeShellScreen` is a `Scaffold` with `IndexedStack` + `NavigationBar`,
4 tabs, each already `Scaffold`-wrapped internally (nested `Scaffold` is
intentional and fine in Flutter for this bottom-nav pattern):

0. `WalletTab` — home/balance
1. `CompareFeeScreen` — the real feature
2. `ActivityScreen` — placeholder
3. `SettingsScreen`

`WalletTab` takes an `onOpenCompareFee` callback from `HomeShellScreen` that
just does `setState(() => _index = 1)` — switching tabs, not pushing a route
— so the "Compare fees" highlight card on the home tab and the bottom-nav
tab both land on the exact same `CompareFeeScreen` instance pattern.

Auth screens push/pop normally with `MaterialPageRoute` (not tab-based).
`navigatorKey` (`core/utils/nav_key.dart`) exists for context-free navigation
if ever needed (e.g. from a notification callback) but nothing currently
uses it — it's carried over from the template.

## 6. The "Coming soon" placeholder mechanism

`showComingSoon(BuildContext context, {String featureName = 'This feature'})`
in `shared/widgets/coming_soon.dart` opens a modal bottom sheet with a
construction icon, a message naming the feature, and a dismiss button.
Every UI affordance without a backing API calls this — see
`wallet_tab.dart`'s `_BalanceCard` (Send/Receive buttons) and
`_QuickActionsGrid` (top up / withdraw / scan QR / cards), and the
notification bell in `WalletTab`'s app bar row.

**To wire a real feature later**: find the `showComingSoon(...)` call at the
relevant button, replace it with a real `onPressed`/`onTap` handler (typically
navigating to a new screen backed by a new repository following the same
shape as `SimulationRepository` — see §8), and delete the now-unused import
if `coming_soon.dart` is no longer referenced from that file.

## 7. Naming/branding decision (context, not an instruction)

`AppStrings.appName = 'PaySim'`, not `'PayPal'`. This was a judgment call
made when generating the app, not a request from the project owner: the
project's own `PAYPAL_MOCK_REFERENCE.md` explicitly warns against implying a
real PayPal integration when pitching, so the display name was kept distinct
from PayPal's wordmark while the visual language (colors, card layout, button
placement) still mimics PayPal for the demo's "here's the same flow, cheaper"
narrative. This is easily reverted — it's one string in `app_strings.dart` —
if the project owner prefers the literal "PayPal" name.

## 8. API contracts consumed

### Auth — `/api/v1/auth/*` (see `RemoteAuthRepository`)
- `POST /sign-in` `{email, password}` → `AuthUser` (via `fromApiJson`, expects
  `userId`/`id`, `email`, `displayName`, `accessToken`, `refreshToken`)
- `POST /register` `{email, password, displayName}` → (no body used)
- `POST /forgot-password/send-otp` `{email}`
- `POST /forgot-password/verify-otp` `{email, otp}` → reset token (raw string
  in `data`)
- `POST /forgot-password/reset` `{resetToken, newPassword, confirmPassword}`
- `POST /sign-out` `{accessToken}` (errors swallowed — logout proceeds
  client-side regardless)
- `POST /refresh-token` `{refreshToken}` → `{accessToken}`
- `GET /me` with `Authorization: Bearer <token>` → `AuthUser`

### Fee simulation — `/api/v1/simulations/compare` (see `SimulationRepository`)
No auth required (public per `PAYPAL_SIMULATION_MODULE.md`), but `ApiClient`
attaches a bearer token anyway if the user happens to be logged in — harmless,
the endpoint just ignores it.

Request:
```json
{ "grossAmountUsd": 500, "midMarketRate": 25000 }
```

Response `data` shape (see `PaymentComparisonResult.fromJson`):
```json
{
  "paypal": {
    "railName": "PAYPAL",                 // optional; falls back to "PAYPAL" if absent
    "grossUsd": 500,
    "feeItems": [ { "label": "...", "amountUsd": 22.30, "description": null } ],
    "netVnd": 11447000,
    "effectiveFeeRatePercent": 8.42
  },
  "usdcMisa": {
    "railName": "USDC_MISA",              // optional; falls back to "USDC_MISA"
    "grossUsd": 500,
    "feeItems": [ ... ],
    "netVnd": 12437500,
    "effectiveFeeRatePercent": 0.5
  },
  "savingsVnd": 990500,
  "savingsPercent": 7.92
}
```
`RailSimulationResult.fromJson` is defensive: `railName` is optional in the
JSON (the illustrative example in `PAYPAL_MOCK_REFERENCE.md` omits it inside
the nested objects), so the parser takes a `fallbackRailName` per rail instead
of requiring the field.

`CompareFeeScreen` renders both rails as cards (colored `danger` for PayPal,
`success` for USDC+MISA — not a value judgement, just two distinct accent
colors), each fee line item, the net VND received, the effective fee rate,
and a savings summary using `AppTypography.score()`. Amounts are formatted
with `intl`'s `NumberFormat.currency` (`en_US`/`$` for USD, `vi_VN`/`đ` for
VND, 0 decimals).

## 9. Known gaps / next steps

1. **Freelancer-marketplace money-transfer flow is not built.** The intended
   workflow was documented in `Tong_quan_du_an_USDC_Freelancer.docx`, which
   arrived corrupted (0-byte `word/document.xml`, confirmed via `extract-text`,
   `pandoc`, and manual raw-deflate decompression — not a reading mistake, the
   file's compressed bytes are genuinely damaged). Only the title survived:
   *"Tổng quan dự án Cổng thanh toán USDC cho Freelancer"*. The doc's metadata
   shows it was exported from Claude Docs (`Claude Docs node/de3e2a89-459a@15`),
   so re-sharing via a claude.ai link (if it still exists there) avoids the
   export/corruption step entirely. Until this content is available, "Send
   money" stays a `showComingSoon()` placeholder — building the real flow
   without the documented workflow would mean guessing at requirements that
   were already written down once.
2. **No live PayPal UI reference was available** when this was generated (no
   browser/web-search tool in that session) — the visual match is "best
   general knowledge," not verified against current screenshots.
3. Transaction history (`ActivityScreen`) has no backing store — the backend's
   simulation module intentionally doesn't persist anything
   (`PAYPAL_SIMULATION_MODULE.md`: "Không lưu lịch sử simulation vào DB trong
   bản tối thiểu này"). If a real activity feed is wanted later, it likely
   needs its own backend entity, not an extension of the simulation module.
4. Not verified against a real Flutter/Dart toolchain (sandbox that generated
   this had neither installed, nor network access to pub.dev). Only static
   checks were done: relative-import resolution and brace/paren balance
   across every file. Run `flutter pub get && flutter analyze` before trusting
   this builds clean.
5. `android/app/src/main/kotlin/.../MainActivity.kt`, the Gradle wrapper,
   `local.properties`, the `ios/` folder, and existing mipmap icons were never
   seen by the generator and are assumed to already exist in the project as
   standard `flutter create` boilerplate — they were not touched or
   regenerated.

## 10. Provenance note

This codebase started as a direct reuse of a different, already-built app
("brome_clean" — a brush-cleaning reminder app with loyalty points and
scheduled notifications) that the project owner had on hand and repurposed
for scaffolding speed. Reused as-is: the auth stack (`AuthRepository`,
`AuthUser`, both repository impls, `AuthService`, `ApiClient`, `http_json`),
`AppSpacing`, `AppTypography`, `SectionCard`, `AppTopBar`/`AppLogo` (renamed
only). Rewritten: `AppColors` (new palette), `AppBackground` and
`PrimaryButton` (the originals had pineapple/soap-bubble illustration motifs
specific to the cleaning-product brand — replaced with a flat fintech style),
`AppStrings`, `AppConfigService` (different storage key + empty default URL),
`SplashScreen`/`LoginScreen`/`SettingsScreen` (stripped of loyalty/reminder/
rewards/notification logic that has no equivalent here). Dropped entirely:
`PointsConfig`, `reminder_presets.dart`, every loyalty/reminder/rewards/
notification/scan-box screen and service, and their now-unused dependencies
(`flutter_local_notifications`, `timezone`, `flutter_timezone`,
`permission_handler`, `mobile_scanner`, `url_launcher`). Newly written for
this project: the entire `simulation` feature, `HomeShellScreen`/`WalletTab`,
`ActivityScreen`, `coming_soon.dart`.
