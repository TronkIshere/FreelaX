# FreelaX Marketplace UI — Session Start

Every Codex/AI session must begin here.

## Required checks

Run:

```bash
git branch --show-current
git rev-parse HEAD
git status --short
git diff --stat
```

Expected branch:

`feat/marketplace-ui-workflow`

Never assume previous work was committed, pushed, or clean.

If unexplained changes exist:

`STOP → REPORT`

Do not clean them automatically.

## Required reading order

1. `/AGENTS.md`
2. `paypal/docs/BRANCH_SCOPE.md`
3. Active `paypal/docs/WORKPACK_*.md`
4. `paypal/docs/PRODUCT_UI_MEMORY.md`
5. `paypal/docs/SCREEN_ARCHITECTURE.md`
6. Actual source files involved in the workpack

## Session report before implementation

Report:

* branch
* HEAD
* working tree
* active workpack
* workpack authorization state
* intended change
* proposed exact file manifest
* blockers

If:

`IMPLEMENTATION_AUTHORIZED: NO`

then the session is reconnaissance-only.

No product source may be modified.

## Validation discipline

During implementation:

* targeted tests while iterating,
* do not repeatedly run the whole suite.

At completion:

1. targeted tests,
2. guardrail script,
3. `flutter analyze`,
4. one complete `flutter test`,
5. diff review.

Then stop for review.
