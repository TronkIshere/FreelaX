# FreelaX AI Execution Contract

## Authority

Work in this repository follows this authority order:

1. Current explicit Human/Product Owner instruction
2. Active WORKPACK
3. `paypal/docs/SCREEN_ARCHITECTURE.md`
4. `paypal/docs/PRODUCT_UI_MEMORY.md`
5. Current repository source code
6. Older documentation

If two authorities conflict, STOP and report the contradiction.

Do not silently reconcile conflicting contracts.

---

## Operating model

ChatGPT / Human:
- control
- architecture
- review
- scope approval
- final diff approval

Codex:
- bounded reconnaissance
- bounded implementation
- tests
- evidence

Codex must never expand its own scope.

One AI writer at a time.

---

## Required branch

Marketplace UI work must remain on:

`feat/marketplace-ui-workflow`

Never modify `master`.

Never switch to another teammate branch unless explicitly instructed.

---

## Default permissions

READ: ALLOW

CREATE/MODIFY:
Only paths explicitly authorized by the active WORKPACK.

DELETE:
DENY by default.

RENAME/MOVE:
DENY by default.

BACKEND MODIFICATION:
DENY by default.

DEPENDENCY CHANGE:
DENY by default.

DATABASE/SCHEMA CHANGE:
DENY by default.

ARCHITECTURE CHANGE:
DENY unless explicitly approved.

COMMIT:
Only when explicitly authorized by the current workpack.

PUSH:
DENY unless explicitly authorized.

PR CREATE/MERGE:
DENY unless explicitly authorized.

---

## Protected project areas

The Marketplace UI branch must not modify these without explicit Human authorization:

- `marketplace-backend/**`
- `payment-backend/**`
- `misa-backend/**`
- `solana-integration/**`
- `solana-stablecoin-payout/**`

Do not modify secrets, env files, credentials, keystores, signing files, or generated artifacts.

---

## Destructive commands

Forbidden unless Human explicitly authorizes the exact operation:

- `git reset --hard`
- `git clean -fd`
- broad `git restore`
- broad `git checkout --`
- force push
- history rewrite
- recursive deletion
- deleting unknown/untracked files
- repository-wide auto formatting
- repository-wide migration

A stale `.git/index.lock` may only be removed after confirming no Git process is using it.

---

## Scope control

Before implementation:

1. Read `SESSION_START_READ_FIRST.md`.
2. Read `BRANCH_SCOPE.md`.
3. Read the active WORKPACK.
4. Read `PRODUCT_UI_MEMORY.md`.
5. Read `SCREEN_ARCHITECTURE.md`.
6. Inspect actual source.
7. Produce an exact proposed file manifest.
8. Do not modify source until implementation is authorized.

No unrelated cleanup.

No opportunistic refactor.

No "while I am here" fixes.

---

## Change loop

Use:

`INSPECT → PROPOSE FILE MANIFEST → APPROVE → IMPLEMENT → TARGETED VALIDATION → DIFF REVIEW → FULL VALIDATION ONCE → COMMIT`

Do not repeatedly run the full suite during active editing.

---

## Stop conditions

STOP and report when:

- branch or HEAD is unexpected,
- working tree contains unexplained changes,
- required change touches backend,
- new dependency appears necessary,
- contract and implementation disagree,
- required file is outside approved manifest,
- deletion/rename appears necessary,
- API contract is missing,
- another AI/user changed the same files,
- implementation would weaken authorization or business rules.

Do not invent a workaround to bypass a stop condition.

---

## Completion evidence

Before requesting commit approval, report:

- exact files created,
- exact files modified,
- exact files deleted,
- `git diff --stat`,
- relevant tests,
- `flutter analyze`,
- remaining warnings/errors,
- working-tree status.

No hidden modifications.
