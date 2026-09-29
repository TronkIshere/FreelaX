# D1.2 C — Dense Marketplace List

Uses [the locked D1 fixture](D1_FIXTURE.md) without changing its four jobs, role context, actions, or temporary palette. This is one composition study, not a selected design.

1. **Design hypothesis:** The FreelaX graphic language should survive a long, actionable job list. Density and distinctive type can coexist without a card grid.
2. **Composition grammar:** A ruled table-like list holds all four jobs in one vertical system. A narrow active stripe gives the focus job priority; no oversized hero displaces the remaining results. Rows share column logic and strong dividers.
3. **Information hierarchy:** Screen identity and supported controls → result count → focus row title, client, USD budget, `OPEN`, **Chưa ứng tuyển**, description, action → secondary rows in the same fields and order. The focus job has a stronger title weight, not different data.
4. **Typography behavior:** System sans with compact line height, distinct title weight, and tabular budget figures. Column headers are small uppercase labels. The title remains the first line on mobile. Final font is not locked.
5. **Shape language:** Horizontal ink rules, a narrow vermilion stripe on the focus row, and square action buttons. Minimal decoration; no bordered cards or rounded status pills.
6. **Color-block behavior:** Shared temporary Bone / Ink / Vermilion / Acid Yellow / Sage palette. Bone carries the full list; Acid Yellow marks the current row, while Vermilion is a small action accent. No color-only state meaning or final token claim.
7. **CTA treatment:** Every row has the same **Ứng tuyển** action wording. Hover and keyboard focus strengthen the row boundary and action outline. Client proof uses the same aligned-field grammar for job facts and owner actions.
8. **State treatment at D1.2 scope:** Written `OPEN` and **Chưa ứng tuyển** remain in every row. No future application-state system is implied by the stripe. D1.3 owns those states.
9. **Desktop behavior:** A stable column grid allows quick comparison among jobs. The four-row fixture tests field alignment; a later D1.3 pass should stress 20–30 rows and pagination.
10. **Mobile behavior:** Column headers disappear and each row becomes a compact stacked record with its own field labels, keeping all data and action visible at 390px and 320px without horizontal scrolling.
11. **Intentionally avoids:** Card grid, faux analytics, popularity metrics, ratings, applicant counts, wallet widgets, and budget edit controls.
12. **Strengths:** Efficient scanning, predictable action placement, easy extension to long lists, and a naturally compact client detail proof.
13. **Risks:** Repeated labels can become monotonous on mobile; dense rules may feel severe if whitespace is reduced too far. The primary job may need more distinction than a stripe when results are long.
14. **D1.3 tests:** 20–30 results, applied states, keyboard row focus, empty/loading/error rows, long titles and descriptions, sorting/reordering, pagination, and client applicant data with IDs only.
15. **Prototype path:** `paypal/docs/ui/d1/prototypes/study_c_dense_marketplace.html`.
