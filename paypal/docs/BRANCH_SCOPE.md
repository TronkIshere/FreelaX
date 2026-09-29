# FreelaX Marketplace UI Branch Scope

## Branch

`feat/marketplace-ui-workflow`

## Original master baseline

`32fe85080ff868d0d41cc6456499fdffd82648d4`

## Product decision commit

`02dea562b5c34ea99395305c4f53b89a7496525d`

## Screen architecture commit

`4ef0a697e8acab42d6e547988f90eeff7c5e92f0`

Always record the current HEAD at session start rather than assuming it remains one of these SHAs.

---

## Purpose

This branch exists only for:

- Flutter Marketplace UI architecture
- CLIENT/FREELANCER role-aware navigation
- frontend API integration
- frontend models/repositories
- marketplace screen implementation
- relevant frontend tests
- UI-specific documentation

---

## Default allowed product areas

Potentially allowed after a workpack explicitly authorizes exact files:

- `paypal/lib/**`
- `paypal/test/**`
- `paypal/docs/**`

Control files:

- `/AGENTS.md`
- `scripts/guardrails/**`

These prefixes are not blanket permission.

The active workpack must still list exact files.

---

## Default forbidden areas

Without explicit Human authorization:

- `marketplace-backend/**`
- `payment-backend/**`
- `misa-backend/**`
- `solana-integration/**`
- `solana-stablecoin-payout/**`

Also forbidden by default:

- dependency changes
- Gradle changes
- Android/iOS native configuration
- database/schema changes
- environment files
- credentials
- generated build folders
- lockfile rewrites
- deletion of existing screens

---

## Product constraints

FreelaX remains:

`Marketplace → Work → Payment → Payout → Tax/Evidence`

It is not wallet-first.

Navigation is defined by:

`PRODUCT_UI_MEMORY.md`

Screen behavior is defined by:

`SCREEN_ARCHITECTURE.md`

Backend remains the final authorization source.

---

## Change boundaries

Do not:

- perform unrelated cleanup,
- reformat unrelated files,
- rename folders for aesthetics,
- restructure features during a targeted fix,
- replace architecture just because another architecture is preferred,
- silently introduce dependencies,
- remove legacy code until its replacement is proven.

Prefer the smallest reversible change.

---

## Push state

Current direct push to `TronkIshere/FreelaX` is known to be blocked for the current GitHub credentials.

Do not repeatedly retry push.

Local commits are allowed only when the active workpack authorizes them.
