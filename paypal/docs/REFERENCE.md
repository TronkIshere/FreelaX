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

Only four things call — or have a real backend ready to call — a real backend:
1. Auth (`/api/v1/auth/*`) — login, register, forgot password, OTP, refresh.
2. **PayPal payee/transaction flow** (`/api/v1/paypal/payees/*`) — register a
   payee profile once, then record a payout transaction and read back its
   persisted fee breakdown. See §8 for the exact contract.
3. **PayPal checkout** (`/api/v1/paypal/checkout/orders`) — a real PayPal
   order create + capture, used from the marketplace "hire" flow (§5, §8).
4. **Marketplace jobs** (`/api/v1/marketplace/jobs`) — a real backend now
   exists for this (job CRUD, plus linking a job to the checkout order that
   paid for it), but the app still defaults to an in-memory mock
   (`MockJobRepository` in `main.dart`) rather than the real one — see §8.

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
│   │   ├── auth_repository.dart       AuthRepository interface + AuthException
│   │   └── job_repository.dart        JobRepository interface + JobRepositoryException
│   ├── models/
│   │   ├── auth_user.dart             AuthUser (id/email/displayName/tokens)
│   │   └── marketplace_job.dart       MarketplaceJob (id/title/description/budgetUsd/status)
│   ├── data/
│   │   ├── mock_auth_repository.dart  offline AuthRepository impl (demo creds)
│   │   ├── remote_auth_repository.dart HTTP AuthRepository impl (/api/v1/auth)
│   │   ├── mock_job_repository.dart   in-memory JobRepository impl — currently active
│   │   └── remote_job_repository.dart HTTP JobRepository impl (/api/v1/marketplace/jobs)
│   │                                  — written ahead of a real backend, unverified (§9)
│   ├── services/
│   │   ├── http_json.dart             URL building, timeout, envelope parsing
│   │   ├── app_config_service.dart    backend base URL, persisted, ChangeNotifier
│   │   ├── auth_service.dart          session state, ChangeNotifier singleton
│   │   ├── job_service.dart           job list cache, ChangeNotifier singleton,
│   │   │                              swappable repository (mirrors auth_service.dart)
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
    ├── marketplace/
    │       data/checkout_models.dart             PaypalCheckoutOrder (+fromJson)
    │       data/checkout_repository.dart         POST /paypal/checkout/orders (+capture) —
    │       │                                     real, calls the checkout endpoint built
    │       │                                     for this (see §8); job CRUD itself goes
    │       │                                     through core/services/job_service.dart,
    │       │                                     not a repository local to this feature
    │       presentation/screens/post_job_screen.dart    form → JobService.createJob()
    │       presentation/screens/job_list_screen.dart    watches JobService.jobs, FAB → post
    │       presentation/screens/hire_freelancer_screen.dart
    │                           payeeId (raw text) + amount form → real checkout
    │                           order (linked to the job via checkoutOrderId) →
    │                           real capture; no freelancer picker yet (§9 item 1)
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
| `success` | `#1E8E3E` | now used for the "payee profile" confirmation icon on `CompareFeeScreen` (was: the USDC+MISA rail's accent, before that rail was removed pending §9 item 2) |
| `warning` | `#B25E00` | not currently used in any screen |
| `danger` | `#D64550` | errors, and the PayPal rail's accent on `CompareFeeScreen`'s result card |

`AppSpacing`: `xs=4, sm=8, md=16, lg=24, xl=32, xl2=40, xxl=48`.

`AppTypography`: static `heading1/heading2/body/bodyMuted/caption/score`
methods, each taking an `AppColors` instance and returning a themed
`TextStyle`. `score()` (28px/w800, colored `primaryDark`) was used for the
savings amount on the old compare screen — **currently unused** now that the
savings summary has been removed pending §9 item 2; kept in
`AppTypography` for when that comes back.

To re-theme the whole app, edit only `AppColors.light` — every screen reads
colors through the theme extension, never hardcoded hex values (`wallet_tab.dart`'s
balance-card gradient and white button labels are the one intentional
exception, since that card is drawn on a dark gradient regardless of theme).

## 5. Navigation

`SplashScreen` → `LoginScreen` (not logged in) or `HomeShellScreen` (logged
in). `HomeShellScreen` is a `Scaffold` with `IndexedStack` + `NavigationBar`,
5 tabs, each already `Scaffold`-wrapped internally (nested `Scaffold` is
intentional and fine in Flutter for this bottom-nav pattern):

0. `WalletTab` — home/balance
1. `JobListScreen` — marketplace (post/browse jobs; hire flow calls real checkout)
2. `CompareFeeScreen` — the real feature (payee registration + transaction recording)
3. `ActivityScreen` — placeholder
4. `SettingsScreen`

`WalletTab` takes an `onOpenCompareFee` callback from `HomeShellScreen` that
just does `setState(() => _index = 2)` — switching tabs, not pushing a route
— so the "Compare fees" highlight card on the home tab and the bottom-nav
tab both land on the exact same `CompareFeeScreen` instance pattern. (The
card's copy on `wallet_tab.dart` still says "see how much you'd save" —
slightly ahead of what the screen currently does; see §9 item 2.)

`JobListScreen` calls `ref.read(jobServiceProvider).load()` once in
`initState` via `addPostFrameCallback`, then watches `JobService.jobs` for
the list. `PostJobScreen` and `HireFreelancerScreen` are pushed on top with
`MaterialPageRoute`, not tabs.

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
  HTTP status for `ErrorCode.PAYEE_NOT_FOUND` **still hasn't been confirmed**
  (the `@ControllerAdvice`/global exception handler that maps it hasn't been
  shared). If the real status differs, a genuine network/server error would
  be misread as "not registered" and the UI would show the registration form
  instead of an error — worth testing end-to-end before trusting this in a
  demo. This is now the **only** remaining unconfirmed piece of this
  endpoint.
- `POST /` `{fullName, paypalEmail, phone?, address?, nationality?}` →
  `PaypalPayee`. **Confirmed** against the real `CreatePaypalPayeeRequest.java`:
  `fullName`/`paypalEmail` are `@NotBlank` (`paypalEmail` also `@Email`);
  `phone`/`address`/`nationality` carry no validation annotation at all
  (genuinely optional). Matches what the repository already sends.

`PaypalPayee` shape: `{id, fullName, paypalEmail, phone, address,
nationality, active}` — matches `PaypalPayeeServiceImpl.toResponse()`.

### PayPal payout transaction — `/api/v1/paypal/payees/{payeeId}/transactions` (see `PaypalTransactionRepository`)
Same auth requirement; the backend now enforces that `payeeId` belongs to the
caller (see `PAYPAL_MODULE_REFERENCE.md`'s "Ownership / access control"
section) — a `payeeId` that isn't the caller's own returns a not-found-style
error rather than someone else's data.

- `POST /` `{platformPayoutId, grossAmountUsd, midMarketRate,
  senderReference?, description?, paymentDate}` → `PaypalPayoutTransaction`.
  Only `record()` is wired up; the backend's `getById`/`withdraw` exist but
  aren't called from this app yet. **Confirmed** against the real
  `RecordPaypalPayoutRequest.java`:
    - `platformPayoutId` is `@NotBlank` and must be globally unique
      (`existsByPlatformPayoutId` check on the backend) — the repository
      generates one client-side with `const Uuid().v4()` (package `uuid`,
      already a dependency) rather than letting the backend assign it.
    - `grossAmountUsd`/`midMarketRate` are `@NotNull @DecimalMin("0.01")`
      `BigDecimal` — sent as plain JSON numbers, which Jackson parses into
      `BigDecimal` without issue; no special encoding needed on the Dart side.
    - `senderReference`/`description` carry no validation annotation
      (genuinely optional) — the repository only includes them in the request
      body when non-empty, which matches.
    - **`paymentDate` is `LocalDateTime`** and `@NotNull`. This field has a
      two-step history worth knowing: it was originally `LocalDate`, and the
      repository originally sent a full ISO datetime
      (`DateTime.now().toIso8601String()`, e.g. `2026-09-19T18:24:00.123456`)
      — which Jackson's default `LocalDate` deserializer would have rejected
      with a 400 on every single call, since it only accepts a bare
      `"yyyy-MM-dd"`. That mismatch was caught and fixed (send date-only).
      The project owner then asked for time-of-day to be captured too, so the
      Java field itself was changed to `LocalDateTime`, and the Dart side was
      reverted back to sending the full `DateTime.now().toIso8601String()` —
      the current code and the current DTO agree, but only because both sides
      were changed together in the same pass; there's no independent
      confirmation this was re-tested end-to-end.

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
see §9 item 2.

### PayPal checkout — `/api/v1/paypal/checkout/orders` (see `CheckoutRepository`, `features/marketplace/data/`)
Real PayPal integration, separate from the mock payee/transaction module
above — the backend calls PayPal's actual REST API (`/v2/checkout/orders`).
Requires real sandbox/live PayPal credentials configured server-side; there
is nothing to configure on the Flutter side beyond the usual base URL.

- `POST /` `{payeeId, amountUsd, referenceId}` → `PaypalCheckoutOrder` with
  an `approvalUrl` the app opens via `url_launcher`
  (`LaunchMode.externalApplication`) so the user can approve on paypal.com.
  `payeeId` is validated against `PaypalPayeeRepository.existsById()` on the
  backend before a real order is created — an unknown id fails the call
  rather than silently creating an orphaned payment.
- `POST /{id}/capture` (no body) → `PaypalCheckoutOrder` with `status`
  updated to `CAPTURED` (or the request throws if PayPal reports anything
  else). `{id}` here is the backend's own record id, **not** the PayPal
  order id — `PaypalCheckoutOrder.id` vs `.paypalOrderId` are different
  fields; `hire_freelancer_screen.dart` calls capture with `order.id`.

`PaypalCheckoutOrder` shape: `{id, payeeId, referenceId, amountUsd,
paypalOrderId, paypalCaptureId, status, approvalUrl, createdAt, capturedAt}`.
`referenceId` is set to the job's id — the checkout module itself still just
stores it as an opaque string and never validates it against a real job; the
job ↔ order link is recorded from the *job* side instead (see below), not
enforced here.

### Marketplace jobs — `/api/v1/marketplace/jobs` (see `JobRepository`/`JobService`)
**This one now has a real backend.** Unlike when this section was first
written, `MarketplaceJobController`/`Service`/`Repository` on the backend
are real, matching `RemoteJobRepository`'s contract exactly (both were
written together in the same pass specifically to match). `main.dart` still
wires `MockJobRepository()` by default — switching to
`RemoteJobRepository()` is the one-line change described below, but hasn't
been flipped in this drop, and neither implementation has been exercised
against a *running* server from this Flutter app (see §9 item 4).

- `POST /` `{title, description, budgetUsd}` → `JobResponse`, owned by the
  caller (`clientUserId` set from the authenticated principal), status
  `OPEN`.
- `GET /` → all jobs with status `OPEN` (no ownership filter — public
  listing, by design).
- `GET /{jobId}` → a single job, also no ownership check.
- `POST /{jobId}/checkout-order` `{checkoutOrderId}` → sets
  `MarketplaceJob.checkoutOrderId` and flips status to `IN_PROGRESS`.
  Ownership-checked (`clientUserId` must match the caller) the same way as
  the PayPal payee/transaction endpoints — a job that isn't the caller's
  throws `JOB_NOT_FOUND`, not a 403. `HireFreelancerScreen` calls this right
  after successfully creating a checkout order, via
  `JobService.linkCheckoutOrder()`.

`MarketplaceJob` shape: `{id, title, description, budgetUsd, checkoutOrderId,
clientUserId, status, createdAt}`. This is the one field the backend and the
Dart model didn't originally share — `checkoutOrderId` was added to both
sides together when the job ↔ order link was built. `MockJobRepository`
never sets `clientUserId`/`checkoutOrderId`/a real `status` transition on its
own — `copyWith()` on `MarketplaceJob` is what `linkCheckoutOrder()` uses
locally to simulate the same status flip the real backend does.

## 9. Known gaps / next steps

1. **`HireFreelancerScreen` now takes a `payeeId`, but only as a raw text
   field — there is still no freelancer directory, search, or picker.**
   Whoever is hiring must already know the freelancer's `PaypalPayee` UUID
   and type it in by hand. The backend now validates that this id exists
   (`PaypalPayeeRepository.existsById()` in `PaypalCheckoutOrderServiceImpl`)
   before creating a real order, so a garbage id fails loudly rather than
   silently — that part of the original gap (payments with no recorded
   recipient at all) is closed. What's still missing is any UI for
   discovering a freelancer's id in the first place. Separately, the job
   and the resulting checkout order are now linked on the backend too:
   `MarketplaceJob.checkoutOrderId` is set via
   `POST /api/v1/marketplace/jobs/{jobId}/checkout-order`, called from
   `HireFreelancerScreen` right after a successful order creation, via
   `JobService.linkCheckoutOrder()` (mirrors `createJob()`/`load()` — same
   repository-then-reload pattern). See §8 for both contracts.
2. **USDC + off-ramp + MISA comparison and `savingsVnd`/`savingsPercent` are
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
3. **Escrow/hire/payout — the actual money-changes-hands part of the
   marketplace flow — is still not built on the backend**, even though job
   *posting* now is (see §8). The intended full workflow was documented in
   `Tong_quan_du_an_USDC_Freelancer.docx`, which arrived corrupted (0-byte
   `word/document.xml`, confirmed via `extract-text`, `pandoc`, and manual
   raw-deflate decompression — not a reading mistake, the file's compressed
   bytes are genuinely damaged). Only the title survived: *"Tổng quan dự án
   Cổng thanh toán USDC cho Freelancer"*. A first, from-scratch attempt at a
   Java marketplace module (job/contract entities, PayPal-order-backed
   escrow, crypto on/off-ramp interface stubs) was drafted once while
   waiting on this doc, then the contract/escrow/crypto parts were
   explicitly shelved by the project owner pending the real docs — only the
   job entity and its CRUD endpoints were kept and built out for real (§8).
   `checkoutOrderId` linking a job to a real PayPal order (§8) is the
   closest thing to "payment" this flow has right now; there is still no
   contract, no escrow hold, no release-on-approval step, and no USDC/VND
   conversion anywhere. Re-sharing the doc via a claude.ai link (if it still
   exists there, per its Claude Docs export metadata) avoids the
   export/corruption step entirely.
4. **Two backend behavior assumptions are still unconfirmed**: the HTTP
   status `GET /api/v1/paypal/payees/me` actually returns for "no payee yet"
   (`ErrorCode.PAYEE_NOT_FOUND`) — the repository assumes 404 (see §8); and
   whether `PaypalPayoutTransactionRepository` actually has the
   `findByPayeeId(UUID)` method `list()` needs — it was specified but that
   repository file itself was never shared, so it may still need adding by
   hand. Everything else in this space is now confirmed: the
   `CreatePaypalPayeeRequest`/`RecordPaypalPayoutRequest` field shapes
   (`paymentDate` is `LocalDateTime`, changed from `LocalDate` per the
   project owner to capture time-of-day), and all four new `ErrorCode`
   entries this feature set needed (`CHECKOUT_ORDER_NOT_FOUND`,
   `INVALID_CHECKOUT_ORDER_STATUS`, `PAYPAL_ORDER_FAILED`, `JOB_NOT_FOUND`)
   have been added to the real `ErrorCode.java` and confirmed against it.
   None of this has still been exercised against a *running* backend from
   this Flutter app, though — only checked against source files, and for
   internal consistency (imports resolve, braces balance) in the generating
   sandbox.
5. **No live PayPal UI reference was available** when this was generated (no
   browser/web-search tool in that session) — the visual match is "best
   general knowledge," not verified against current screenshots.
6. Transaction history (`ActivityScreen`) still has no backing UI — but this
   is now purely a Flutter-side gap, not a backend one.
   `GET /api/v1/paypal/payees/{payeeId}/transactions` (list, ownership-
   checked) exists on the backend now; nothing in this app calls it yet.
   `ActivityScreen` is the natural place to. The newer `PaypalCheckoutOrder`/
   `MarketplaceJob` records have the same problem from the other direction —
   `HireFreelancerScreen`'s order result is lost the moment the screen is
   left, and there is no "my payments" or "my jobs I've hired for" list
   screen calling `GET /marketplace/jobs` or any checkout-order equivalent
   (no such list endpoint exists yet for checkout orders specifically).
7. Not verified against a real Flutter/Dart toolchain (sandbox that generated
   this had neither installed, nor network access to pub.dev). Only static
   checks were done: relative-import resolution and brace/paren balance
   across every file. Run `flutter pub get && flutter analyze` before trusting
   this builds clean.
8. `android/app/src/main/kotlin/.../MainActivity.kt`, the Gradle wrapper,
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

**Third pass — how `marketplace/` and the job/domain layer came about.**
When the project owner first asked for the real marketplace flow (job
posting → hire → client funds via PayPal escrow → payout via USDC/off-ramp),
a full Java module was drafted from first principles (entities for job and
contract, a real PayPal Orders/Checkout API client, stub interfaces for
crypto on-ramp/off-ramp) since no real docs for this flow existed yet — the
same corrupted-docx problem as item 3 in §9. The project owner then paused
part of that: keep the real PayPal checkout capability (genuinely useful on
its own, and it's what `CheckoutRepository`/the
`/api/v1/paypal/checkout/orders` endpoints in §8 are), but drop the
contract/escrow/crypto-stub entities until real docs arrive for that part
specifically — the job entity itself was kept and, in the fourth pass below,
actually built out for real. On the Flutter side, job posting/listing
started as a single ad-hoc `ChangeNotifier` (`LocalJobBoard`) living inside
the `marketplace` feature folder with no interface behind it at all. The
project owner then asked for it to follow the same `core/domain` +
`core/data` + `core/services` shape as auth — specifically so a real backend
could be dropped in later without touching `PostJobScreen`/`JobListScreen`.
That produced `core/domain/job_repository.dart`,
`core/models/marketplace_job.dart`, `core/data/mock_job_repository.dart`
(replaces `LocalJobBoard`, same in-memory behavior) and
`core/data/remote_job_repository.dart`, orchestrated by
`core/services/job_service.dart` (`ChangeNotifier` singleton, mirrors
`auth_service.dart` exactly down to the `setRepository()` swap point in
`main.dart`). At the time, `RemoteJobRepository`'s contract was speculative
(no backend existed yet to check it against), and `HireFreelancerScreen`
still had no freelancer/payee field at all — both were carried forward as
known gaps rather than fixed in that pass.

**Fourth pass — the job backend got built for real, and both gaps above got
at least partially closed.** `MarketplaceJobController`/`Service`/
`Repository` were written on the backend matching `RemoteJobRepository`'s
contract exactly (both sides were written in the same pass specifically to
match, rather than one being reverse-engineered from the other after the
fact) — see §8. Separately, two real problems the project owner pointed out
got fixed together: `HireFreelancerScreen` creating a real PayPal payment
with no recorded recipient, and `MarketplaceJob` having no field remembering
which checkout order paid for it. Both `payeeId` (on
`CreateCheckoutOrderRequest`/`PaypalCheckoutOrder`) and `checkoutOrderId`
(on `MarketplaceJob`, set via a new
`POST /api/v1/marketplace/jobs/{jobId}/checkout-order` endpoint) were added
to the backend and threaded through to `HireFreelancerScreen` in the same
pass — see §8's checkout and marketplace-jobs subsections, and §9 item 1 for
what's still missing (a freelancer picker; `payeeId` is still a raw text
field). All four new `ErrorCode` entries this required
(`CHECKOUT_ORDER_NOT_FOUND`, `INVALID_CHECKOUT_ORDER_STATUS`,
`PAYPAL_ORDER_FAILED`, `JOB_NOT_FOUND`) were confirmed added to the real
`ErrorCode.java` in this same pass — see §9 item 4 for the one dependency
still outstanding (`PaypalPayoutTransactionRepository.findByPayeeId`).