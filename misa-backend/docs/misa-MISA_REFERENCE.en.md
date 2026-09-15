# MISA Module Reference — legal basis & API contract

> This file summarizes the `MISA_Mock_API_Module_2026.docx` document provided
> by the team, along with the legal assumptions agreed on during the
> consulting process, so that anyone (human or AI) reading the code later can
> immediately understand the context without re-reading the entire
> conversation history.

## Legal basis cited by the source document

- Decree 254/2026/NĐ-CP
- Circular 91/2026/TT-BTC

**Important warning:** this information was supplied by the team, dates after
the assisting AI's knowledge cutoff, and **has not been independently
verified**. Before including it in a pitch deck or any official document,
look up these document numbers yourself on an authoritative source (the
Government's e-portal, Thư Viện Pháp Luật, etc.).

## Mandatory disclaimers when presenting (per the source document's checklist)

When presenting to judges, **do not say**:
- That this is MISA's official public API (this is a **mock the team built
  itself**, modeled after the supplied API contract document).
- "Buying a MISA certificate" (a PIT withholding certificate is not a good
  bought and sold).
- Treating an e-invoice as the same thing as a PIT withholding certificate —
  these are two legally distinct types of documents.
- That this module's access token is legal proof of the platform's right to
  withhold tax — the token is purely a technical authentication mechanism
  between services.

## Business model: B, not A

This build implements **Model B**: the platform itself acts as the *income-
paying organization* (with its own `taxCode`, see `misa.organization.tax-code`
in `application.yml`), withholding the freelancer's PIT before paying them,
and issuing the withholding certificate itself. This differs from Model A
(the freelancer issues their own VAT invoice through a third party, and the
platform only supports the UX), which was discussed and ruled out earlier.

**Forward-looking assumption to state clearly in the pitch:** for Model B to
be legally valid, the platform needs a genuine "income-paying organization"
legal status, a registered tax code, and to meet the legal conditions to
withhold PIT on the freelancer's behalf. This is an assumption that needs
confirmation from a lawyer/accountant before real deployment — similar to the
assumption about USDC and the licensed off-ramp entity at the layer below
(outside the scope of this module).

## Withholding certificate state machine (`CertificateStatus`)

```
DRAFT → SUBMITTING → ACCEPTED
  ↓         ↓            ↓
CANCELLED  CANCELLED   REPLACED / CANCELLED
                       CORRECTION_REQUIRED (via incorrect-record-notification)
```

Note versus the source document: the source document has additional
intermediate states, `SIGNED` and `SUBMITTED`, shown explicitly. This mock
folds `SIGNED` into the issue step (not persisted as a separate top-level
state, only reflected in the response's `signature.status`), and skips the
`SUBMITTED` waiting period (the mock is processed synchronously — `submit()`
returns `ACCEPTED` immediately) since there is no real asynchronous processing
queue. The `CertificateStatus` enum still retains the `SIGNED`/`SUBMITTED`
values for forward compatibility when wiring up the real MISA API later (at
which point those intermediate states will genuinely pause there waiting for a
webhook).

## Certificate symbol

Per the source document: `CT` + 2-digit year + 2 freely chosen letters, e.g.
`CT26AA`. See `util.CertificateNumberGenerator.generateSymbol()`.

## Endpoints implemented in this build

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/taxpayers` | Register a taxpayer profile (freelancer) |
| GET | `/api/v1/taxpayers/me` | Get the current user's profile |
| GET | `/api/v1/taxpayers/{id}` | Get a profile by id |
| GET | `/api/v1/organization` | Income-paying organization info (static, from config) |
| POST | `/api/v1/taxpayers/{taxpayerId}/payouts` | Record a payout transaction (manual entry) |
| GET | `/api/v1/taxpayers/{taxpayerId}/payouts/{payoutId}` | View a payout |
| POST | `/api/v1/withholding-certificates` | Create a certificate (DRAFT) from a payout |
| POST | `/api/v1/withholding-certificates/{id}/issue` | Sign, transition to SUBMITTING |
| POST | `/api/v1/withholding-certificates/{id}/submit` | Submit; mock returns ACCEPTED |
| GET | `/api/v1/withholding-certificates/{id}/status` | View status |
| GET | `/api/v1/withholding-certificates/{id}/pdf` | Mock PDF (requires login) |
| GET | `/api/v1/withholding-certificates/{id}/xml` | Mock XML (requires login) |
| GET | `/api/v1/withholding-certificates/lookup/{lookupCode}` | Public lookup |
| POST | `/api/v1/withholding-certificates/{id}/cancel` | Cancel a certificate |
| POST | `/api/v1/incorrect-record-notifications` | Report an error → CORRECTION_REQUIRED |

## Deferred (phase 2, not implemented)

- The **Tax Return** group (aggregate filing by tax period).
- **Replace certificate** (replacing an already-ACCEPTED certificate that
  contains an error) — the entity already has a `replacement_of_id` column to
  pave the way, but there is no service/controller for it yet.
- **Webhook** (pushing asynchronous status updates back to the platform) —
  unnecessary since the mock is processed synchronously; only needed once
  wired up to the real MISA API.

## MISA module error code range

`ErrorCode` 3000–3999 is reserved for this module (see
`exception/ErrorCode.java`), separate from the pre-existing 1000s (general
data), 2000s (auth), 5000s (rate limiting), and 9000s (system) ranges.
