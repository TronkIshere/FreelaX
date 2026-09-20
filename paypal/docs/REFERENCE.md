# PaySim (`paypal`) — Technical Reference

Purpose of this file: give a future AI assistant (or a new developer) full
context on this Flutter codebase in one read, without needing to re-explore
every file. Pair it with `README.md` (quick-start / handoff notes) and the
backend design docs `PAYPAL_MODULE_REFERENCE.md` / this project's earlier
`PAYPAL_MOCK_REFERENCE.md` / `PAYPAL_SIMULATION_MODULE.md` (describe the
Java/Spring backend this app calls — note the backend has since moved past
the stateless-calculator design those last two describe; see §8).

## 1. What this project is

A Flutter app that visually mimics PayPal's UI (balance card, Send/Receive
buttons, bottom nav) to demo one real feature: recording a PayPal payout
transaction against the persisted `paypal-backend` module, for freelancers
comparing it against a USDC + a licensed off-ramp + MISA flow. Built for a
pitch/hackathon context, not a production payment app. Package name is
`paypal` (`pubspec.yaml` `name:`); the in-app display name is `PaySim`
(`AppStrings.appName`) — the two are deliberately different, see §7.

Only two things call a real backend:
1. Auth (`/api/v1/auth/*`) — login, register, forgot password, OTP, refresh.
2. **PayPal payee/transaction flow** (`/api/v1/paypal/payees/*`) — register a
   payee profile once, then record a payout transaction and read back its
   persisted fee breakdown. See §8 for the exact contract.

The USDC + off-ramp + MISA side and the savings comparison
(`savingsVnd`/`savingsPercent`) are **not wired up yet** — deliberately
deferred, not a bug. `CompareFeeScreen` currently shows only the PayPal side.
See §9.

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
  `HttpJsonException(message, statusCode)` (message taken from `error`) on
  failure or non-2xx status. Repositories catch `HttpJsonException`/
  `ApiException` and rethrow as their own domain exception (`AuthException`,
  `ApiException`, `PaypalPayeeException`, `PaypalTransactionException`) so
  screens only ever catch one exception type per call site.
- **`ApiException` carries `statusCode` now** (added alongside the payee/
  transaction rewrite): `ApiClient._attempt()`/`_parse()` pass through
  `HttpJsonException.statusCode` instead of discarding it. This exists
  specifically so `PaypalPayeeRepository.getMine()` can tell "no payee yet"
  (expected to be a 404) apart from a network/server failure — see §8 for
  why that distinction still needs confirming against the real backend.
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
│   │   └── api_client.dart            generic /api/v1 client, 401 auto-refresh,
│   │                                  ApiException now carries statusCode
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
    ├── simulation/                     the one real feature end-to-end (name is
    │       │                           now a slight misnomer — see §10 — kept to
    │       │                           avoid touching unrelated import paths)
    │       data/paypal_payee_models.dart        PaypalPayee (+fromJson)
    │       data/paypal_payee_repository.dart    GET .../payees/me, POST .../payees
    │       data/paypal_transaction_models.dart  PaypalFeeBreakdown,
    │       │                                    PaypalPayoutTransaction (+fromJson)
    │       data/paypal_transaction_repository.dart  POST .../payees/{id}/transactions
    │       presentation/screens/compare_fee_screen.dart
    │                           registration form → transaction form → result card
    ├── activity/presentation/screens/activity_screen.dart   static empty state
    └── settings/presentation/
            screens/settings_screen.dart        profile row, server config, about, logout
            widgets/server_settings_section.dart backend base-URL editor (persisted)
```

**Removed in the payee/transaction rewrite**: `data/simulation_models.dart`
(`FeeLineItem`, `RailSimulationResult`, `PaymentComparisonResult`) and
`data/simulation_repository.dart` (`SimulationRepository`,
`SimulationException`) — the endpoint they called
(`POST /api/v1/simulations/compare`) no longer exists on the backend.

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
| `success` | `#1E8E3E` | now used for the "payee profile" confirmation icon on `CompareFeeScreen` (was: the USDC+MISA rail's accent, before that rail was removed pending §9 item 1) |
| `warning` | `#B25E00` | not currently used in any screen |
| `danger` | `#D64550` | errors, and the PayPal rail's accent on `CompareFeeScreen`'s result card |

`AppSpacing`: `xs=4, sm=8, md=16, lg=24, xl=32, xl2=40, xxl=48`.

`AppTypography`: static `heading1/heading2/body/bodyMuted/caption/score`
methods, each taking an `AppColors` instance and returning a themed
`TextStyle`. `score()` (28px/w800, colored `primaryDark`) was used for the
savings amount on the old compare screen — **currently unused** now that the
savings summary has been removed pending §9 item 1; kept in
`AppTypography` for when that comes back.

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
1. `CompareFeeScreen` — the real feature (payee registration + transaction recording)
2. `ActivityScreen` — placeholder
3. `SettingsScreen`

`WalletTab` takes an `onOpenCompareFee` callback from `HomeShellScreen` that
just does `setState(() => _index = 1)` — switching tabs, not pushing a route
— so the "Compare fees" highlight card on the home tab and the bottom-nav
tab both land on the exact same `CompareFeeScreen` instance pattern. (The
card's copy on `wallet_tab.dart` still says "see how much you'd save" —
slightly ahead of what the screen currently does; see §9 item 1.)

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
relevant button, replace it with a real `onPressed`/`onTap` handler,
following the same repository shape as `PaypalPayeeRepository` /
`PaypalTransactionRepository` (see §8) — a small class holding an `ApiClient`,
one domain exception type, and methods that call `_client.get/post` and parse
the response into a typed model. Delete the now-unused `coming_soon.dart`
import if that file is no longer referenced from the screen you're editing.

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

### PayPal payee — `/api/v1/paypal/payees/*` (see `PaypalPayeeRepository`)
Requires a Bearer token (attached automatically by `ApiClient` for the
logged-in user; the backend's `PaypalPayeeController` resolves the caller via
`@AuthenticationPrincipal` and now enforces ownership on `GET /{id}` — not
used by this app, only `/me` is).

- `GET /me` → `PaypalPayee` on success. **On no-payee-yet**: the repository
  treats an `ApiException` with `statusCode == 404` as "not registered" and
  returns `null` instead of throwing — this assumption about the backend's
  HTTP status for `ErrorCode.PAYEE_NOT_FOUND` has **not been confirmed**
  against a running instance or its `@ControllerAdvice`. If the real status
  differs, a genuine network/server error would be misread as "not
  registered" and the UI would show the registration form instead of an
  error — worth testing end-to-end before trusting this in a demo.
- `POST /` `{fullName, paypalEmail, phone?, address?, nationality?}` →
  `PaypalPayee`. Field names inferred from `PaypalPayeeServiceImpl.register()`
  reading `request.getXxx()` — the actual `CreatePaypalPayeeRequest` DTO
  (validation annotations, required vs. optional) was never shared, so this
  is a best-effort match, not a confirmed contract.

`PaypalPayee` shape: `{id, fullName, paypalEmail, phone, address,
nationality, active}` — matches `PaypalPayeeServiceImpl.toResponse()`.

### PayPal payout transaction — `/api/v1/paypal/payees/{payeeId}/transactions` (see `PaypalTransactionRepository`)
Same auth requirement; the backend now enforces that `payeeId` belongs to the
caller (see `PAYPAL_MODULE_REFERENCE.md`'s "Ownership / access control"
section) — a `payeeId` that isn't the caller's own returns a not-found-style
error rather than someone else's data.

- `POST /` `{platformPayoutId, grossAmountUsd, midMarketRate,
  senderReference?, description?, paymentDate?}` → `PaypalPayoutTransaction`.
  Only `record()` is wired up; the backend's `getById`/`withdraw` exist but
  aren't called from this app yet. Field names/requiredness inferred the same
  way as `CreatePaypalPayeeRequest` above — same caveat applies. Three
  specific uncertainties baked into the repository, flagged in code comments:
    - `platformPayoutId` must be globally unique (`existsByPlatformPayoutId`
      check on the backend) — the repository generates one client-side with
      `const Uuid().v4()` (package `uuid`, already a dependency) rather than
      letting the backend assign it.
    - `paymentDate` is sent as `DateTime.now().toIso8601String()` (a full
      ISO-8601 datetime). If the Java field is `LocalDate` rather than
      `LocalDateTime`, this may fail to deserialize — not confirmed either way.
    - Whether `senderReference`/`description` are validated as required on the
      backend is unknown; the repository only includes them in the request
      body when non-empty.

`PaypalPayoutTransaction` shape: `{id, platformPayoutId, payeeId, status,
grossAmountUsd, midMarketRate, feeBreakdown, netVnd, paymentDate,
withdrawnAt}`, where `feeBreakdown` is `{commercialFeeUsd, fxSpreadCostUsd,
netUsdAfterFees, effectiveFeeRatePercent}` — matches
`PaypalPayoutTransactionServiceImpl.toResponse()`. **This replaces** the old
`feeItems` array shape entirely; there is no array of labeled line items
anymore, just four fixed numeric fields.

`CompareFeeScreen` renders: a payee-profile confirmation line, the amount/
rate input form, and — after recording — a result card showing
`feeBreakdown`'s two cost lines, the transaction status, and `netVnd`.
Amounts are formatted with `intl`'s `NumberFormat.currency` (`en_US`/`$` for
USD, `vi_VN`/`đ` for VND, 0 decimals). No USDC/MISA rail, no savings figure —
see §9 item 1.

## 9. Known gaps / next steps

1. **USDC + off-ramp + MISA comparison and `savingsVnd`/`savingsPercent` are
   not implemented on either side.** The old `/api/v1/simulations/compare`
   endpoint used to return both rails plus a savings figure in one call; the
   new persisted `paypal-backend` module only records the PayPal side, and no
   equivalent call into `misa-backend` exists yet from this app (per the
   project owner, this is deliberately deferred, not an oversight). Wiring it
   back means: (a) a `misa-backend` equivalent of `PaypalPayoutTransaction`
   for the USDC/MISA side, and (b) either a combined `/compare` endpoint on
   one of the backends, or two separate calls from this app with the savings
   math done client-side — an architectural decision the project owner
   hadn't settled as of this note.
2. **Freelancer-marketplace money-transfer flow is not built.** The intended
   workflow was documented in `Tong_quan_du_an_USDC_Freelancer.docx`, which
   arrived corrupted (0-byte `word/document.xml`, confirmed via `extract-text`,
   `pandoc`, and manual raw-deflate decompression — not a reading mistake, the
   file's compressed bytes are genuinely damaged). Only the title survived:
   *"Tổng quan dự án Cổng thanh toán USDC cho Freelancer"*. The doc's metadata
   shows it was exported from Claude Docs (`Claude Docs node/de3e2a89-459a@15`),
   so re-sharing via a claude.ai link (if it still exists there) avoids the
   export/corruption step entirely. Until this content is available, "Send
   money" stays a `showComingSoon()` placeholder.
3. **Several backend DTO/behavior assumptions are unconfirmed** (listed in
   detail in §8): the exact `CreatePaypalPayeeRequest`/`RecordPaypalPayoutRequest`
   field requiredness and types, the `paymentDate` Java type
   (`LocalDate` vs `LocalDateTime`), and the HTTP status the backend actually
   returns for "no payee yet." None of these have been exercised against a
   running backend from this Flutter app — only checked for internal
   consistency (imports resolve, braces balance) in the generating sandbox.
4. **No live PayPal UI reference was available** when this was generated (no
   browser/web-search tool in that session) — the visual match is "best
   general knowledge," not verified against current screenshots.
5. Transaction history (`ActivityScreen`) still has no backing UI, even
   though the backend now *does* persist `PaypalPayoutTransaction` rows (a
   change from the original stateless design, which persisted nothing). The
   blocker now is that `PaypalPayoutTransactionController` has no "list
   transactions for a payee" endpoint yet (`PAYPAL_MODULE_REFERENCE.md`'s
   "Known limitations" — only get-by-id exists). Once that endpoint exists,
   `ActivityScreen` is the natural place to call it.
6. Not verified against a real Flutter/Dart toolchain (sandbox that generated
   this had neither installed, nor network access to pub.dev). Only static
   checks were done: relative-import resolution and brace/paren balance
   across every file. Run `flutter pub get && flutter analyze` before trusting
   this builds clean.
7. `android/app/src/main/kotlin/.../MainActivity.kt`, the Gradle wrapper,
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
`permission_handler`, `mobile_scanner`, `url_launcher`).

**Second pass — the `simulation` feature's own history.** It was originally
written against a stateless calculator backend
(`POST /api/v1/simulations/compare`, described in `PAYPAL_MOCK_REFERENCE.md`
/ `PAYPAL_SIMULATION_MODULE.md`): one call, no auth, no persistence, both
rails plus a savings figure in a single response. The backend was then
rebuilt as a persisted module (`paypal-backend`, described in
`PAYPAL_MODULE_REFERENCE.md`) with a payee-registration step, per-transaction
persistence, and auth-gated, ownership-checked endpoints — but only for the
PayPal side; no equivalent for USDC/MISA yet. This Flutter feature was
rewritten to match: `simulation_models.dart`/`simulation_repository.dart`
were deleted outright (the endpoint they called no longer exists) and
replaced with `paypal_payee_models.dart`/`paypal_payee_repository.dart` and
`paypal_transaction_models.dart`/`paypal_transaction_repository.dart`, and
`compare_fee_screen.dart` was rewritten from a single-form-and-result screen
into a three-state flow (loading → payee registration if needed → amount
form and result). The feature folder is still named `simulation/` — kept
as-is to avoid touching import paths in `home_shell_screen.dart` and
elsewhere for a rename with no functional benefit; a future rename to
something like `paypal_payout/` would be purely cosmetic.