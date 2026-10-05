# START HERE — FREELAX MVP UI

Read in this order:

1. `UI_DEVELOPMENT_MEMORY.md`
2. `UI_POLISH_SPEC.md`
3. `UI_IMPLEMENTATION_PROGRESS.md`
4. `WORKPACK_P06_UI_POLISH.md`
5. repository `README.md`
6. repository `docs/mvp-functional-spec.md`
7. current frontend source

Active instruction:

> UI Workpack B integrates existing Step 8 Profile/Portfolio and Step 9 Review APIs. Preserve the committed Step 6 participant/Admin workspace and Steps 1–5 workflow/Finance. Keep changes frontend/docs only; use trusted session authorities and server-owned reputation/visibility.

> Product Owner scope override: Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px. Preserve existing responsive CSS; mobile is not a P06.4 gate.

Current reviewed baseline:

`feat/mvp-ui-steps6-9-20261005` at `11f0611` (`feat(frontend): integrate dispute and admin workflow`). Workpack B changes remain uncommitted; consult UI_IMPLEMENTATION_PROGRESS for current tests/build and runtime gates. Authenticated runtime smoke remains ENVIRONMENT BLOCKED.

Important baseline change:

The current master now includes the Contract/Milestone foundation. UI may use API-backed fields such as deliverables, acceptance criteria, delivery due date, review window hours, max revisions, contract summary, milestone status and revisions used when those values are present.

Do not treat the remaining future MVP spec as already implemented.
