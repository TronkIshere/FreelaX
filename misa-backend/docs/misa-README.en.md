# MISA Backend — Freelancer USDC Payout & Withholding Tax Certificate Module

## Context

Backend for a Unihackfest project: "A USDC payment gateway for Vietnamese
freelancers." This module implements the compliance layer — the personal
income tax (PIT) withholding certificate (Form 03/TNCN) — sitting on top of
the on-chain USDC receiving leg (Solana, not implemented in this build) and
the USDC→VND off-ramp leg (assumed to go through a duly licensed entity, also
mocked here).

The original codebase (auth, JWT, Redis, security) is reused from a pre-existing
auth project (old name "Brome Clean") — identifiers (application name, DB
defaults, JWT issuer, email subject) have been renamed to MISA. Unrelated
legacy business logic (loyalty program, QR codes, rewards) was removed from
`DataInitializer` because the source code for those entities was never
provided and is unrelated to the freelancer/tax domain.

## Business model chosen

There are two plausible models for how a freelancer's tax obligations get
handled:

- **Model A** (not used in this build): the freelancer issues their own VAT
  invoice through an e-invoicing provider (e.g. Mắt Bão), bears tax
  responsibility themselves; the platform only supports the UX.
- **Model B** (chosen, per the `MISA_Mock_API_Module_2026.docx` document): the
  platform itself acts as the **income-paying organization**, withholding PIT
  before paying the freelancer, and issuing an **electronic PIT withholding
  certificate (Form 03/TNCN)** via MISA AMIS PIT (fully mocked in-process here,
  no real MISA API call).

**Important consequence:** because this is a withholding model, the VND amount
the freelancer actually receives is the post-tax (net) amount, not the gross
figure converted from USDC. The current build stores both `taxableIncome`
(gross) and `taxWithheld` on `WithholdingCertificate`, but **the step of
actually paying out the net VND to the freelancer has not been implemented**
(since "receiving money" was explicitly deferred), so for now
`PayoutTransaction.amountVndGross` is the figure fed into the tax calculation.

## MISA module structure

- `entity`: `Taxpayer`, `PayoutTransaction`, `WithholdingCertificate`,
  `IncorrectRecordNotification`, `CertificateStatus` (status enum per the MISA
  mock document: DRAFT → SIGNED → SUBMITTING → SUBMITTED → ACCEPTED, with
  REJECTED/CORRECTION_REQUIRED branches, and ACCEPTED → REPLACED/CANCELLED).
- `service.MisaProviderClient` + `service.impl.MisaProviderClientMockImpl`: an
  adapter that simulates MISA — **makes no real network call**, only computes
  in-process (symbol/number/lookupCode, submissionId, taxAuthorityReference).
  When integrating with the real MISA API, only a second implementation of
  this interface needs to be written (e.g. using `RestClient`, available from
  Spring Framework 6.1+ inside `spring-boot-starter-web`, no extra dependency
  required).
- `service.TaxEngineService`: computes withheld tax using a configurable rate
  (`misa.tax.withholding-rate` in `application.yml`, default 10%) — this is a
  **placeholder value**, not a real tax schedule, and must be replaced with
  accurate tax logic once confirmed by an accountant/lawyer.
- `service.WithholdingCertificateService`: orchestrates the certificate's
  entire lifecycle.

## Running it

```bash
mvn spring-boot:run
```

Key environment variables (see `application.yml` for the complete list; every
variable has a default suitable for running locally): `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET`, `MISA_ORG_TAX_CODE`,
`MISA_WITHHOLDING_RATE`.

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Suggested API flow for a demo

1. `POST /api/v1/auth/register`, `POST /api/v1/auth/sign-in` — get an access
   token.
2. `POST /api/v1/taxpayers` — the freelancer creates a taxpayer profile (using
   the access token).
3. `POST /api/v1/taxpayers/{taxpayerId}/payouts` — record a payout transaction
   (manually enter `transactionHash`, `amountUsdc`, `exchangeRate` — the real
   "receiving money" step via Solana is not wired in here yet).
4. `POST /api/v1/withholding-certificates` — create a DRAFT certificate from
   the payout; the Tax Engine automatically computes `taxWithheld`.
5. `POST /api/v1/withholding-certificates/{id}/issue` — sign, transition to
   SUBMITTING.
6. `POST /api/v1/withholding-certificates/{id}/submit` — submit; the mock
   automatically jumps straight to ACCEPTED (there is no SUBMITTED state
   waiting for an async process, since this mock is synchronous).
7. `GET /api/v1/withholding-certificates/lookup/{lookupCode}` — a public
   endpoint (no login required), matching the real MISA public certificate
   lookup page.

## Known limitations — be upfront about these if asked

- `GET/{id}/pdf` and `GET/{id}/xml` currently require authentication (due to
  the default security rule `anyRequest().authenticated()`), while only the
  `lookup/{lookupCode}` endpoint is whitelisted as public. In the real MISA
  system, the income recipient looks up their certificate via a link + lookup
  code **without needing to log in** — to fully match that real behavior,
  `/api/v1/withholding-certificates/{id}/pdf` and `/xml` would need to be
  added to the whitelist, or the design changed so lookup returns the content
  directly instead of just a link.
- The **Tax Return** group (aggregate period-based filing), **replace**
  (replacing an already-issued certificate), and **webhook** groups from the
  MISA mock document are **not implemented** — deliberately deferred to
  phase 2, per the agreed MVP scope.
- `PayoutTransactionController` allows recording a payout under a `taxpayerId`
  in the path without checking whether that taxpayer belongs to the currently
  logged-in user — this is a utility endpoint for demo/testing purposes; an
  ownership check must be added before real use.
- The Tax Engine uses a single configurable flat withholding rate, not a real
  PIT tax schedule (which has thresholds, progressive brackets, etc.). This is
  an explicit placeholder and should not be presented as an accurate tax
  figure in the pitch.
