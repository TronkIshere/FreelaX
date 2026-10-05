# START HERE — FREELAX UI STEPS 1–5

Read in this order:

1. `UI_DEVELOPMENT_MEMORY.md`
2. `UI_POLISH_SPEC.md`
3. `UI_IMPLEMENTATION_PROGRESS.md`
4. `WORKPACK_P06_UI_POLISH.md`
5. repository `README.md`
6. repository `docs/mvp-functional-spec.md`
7. current frontend source

Active instruction:

> UI Steps 1–5 P2 polishes the accepted P0/P1 contract workflow, settlement/cancellation/refund, Finance, Overview and Activity integration. Preserve server authority and existing APIs. Keep all changes frontend/docs only; Steps 6/8/9 remain outside this pass.

> Product Owner scope override: Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px. Preserve existing responsive CSS; mobile is not a P06.4 gate.

Current reviewed baseline:

`feat/mvp-ui-step1-5-20261005` at `7099bfa` (accepted P0 `0e790a8` and P1 `7099bfa`). P1 implementation/test/build passed; authenticated runtime smoke remains ENVIRONMENT BLOCKED.

Important baseline change:

The current master now includes the Contract/Milestone foundation. UI may use API-backed fields such as deliverables, acceptance criteria, delivery due date, review window hours, max revisions, contract summary, milestone status and revisions used when those values are present.

Do not treat the remaining future MVP spec as already implemented.
