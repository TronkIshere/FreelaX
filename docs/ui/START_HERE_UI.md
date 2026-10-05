# START HERE — FREELAX P06 UI

Read in this order:

1. `UI_DEVELOPMENT_MEMORY.md`
2. `UI_POLISH_SPEC.md`
3. `UI_IMPLEMENTATION_PROGRESS.md`
4. `WORKPACK_P06_UI_POLISH.md`
5. repository `README.md`
6. repository `docs/mvp-functional-spec.md`
7. current frontend source

Active instruction:

> P06.4 integrates verified bank/funding, contract evidence submissions, decisions and server review deadlines, then polishes hierarchy. Current backend source is authoritative; read `.env.mvp-backend-checklist.md` first. Keep all changes frontend/docs only.

> Product Owner scope override: Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px. Preserve existing responsive CSS; mobile is not a P06.4 gate.

Current reviewed baseline:

`feat/p06-ui-polish-20261004` at `cf0594f90af99375570e8557cbde283b51cd50f5` (backend sync includes `50841a3` and `6c605a9`).

Important baseline change:

The current master now includes the Contract/Milestone foundation. UI may use API-backed fields such as deliverables, acceptance criteria, delivery due date, review window hours, max revisions, contract summary, milestone status and revisions used when those values are present.

Do not treat the remaining future MVP spec as already implemented.
