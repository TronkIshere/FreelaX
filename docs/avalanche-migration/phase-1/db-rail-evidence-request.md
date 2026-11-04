# Database and legacy payment-rail evidence: status and operator request

Status: **BLOCKED (DB operator not yet named)**. Authorization (owner decision update, 2026-11-03, request R2): **the
development database only**, for the six read-only queries below; no other environment is authorized. No query was run. Nothing in any database was read or changed. Scope reminder: FreelaX is a development simulation ([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md)); the rows in question are simulation data. The queries are still needed to know which payment rails hold data before any legacy path is considered for retirement. **No legacy Solana payment path is deleted or migrated by this phase.**

## What was checked (and not touched)
| Item | Finding |
|---|---|
| FreelaX compose MySQL (`shared-mysql`, `127.0.0.1:3307`) | **Not running** (`docker ps` shows only `wifas-invoice-postgres` and `wifas-invoice-redis`). |
| PostgreSQL on 5437 and Redis on 6379 | Belong to another project (`wifas-invoice`): **not accessed**. |
| Something listening on 3306 | Owner unknown. **Not connected**: the only documented credential is the root password in the ignored `.env`, which this task must not read, and root is not a read-only account. |
| Credentials | None available to this session for a read-only user. |
| Schema | Checked **statically** against `marketplace-backend/.../V1__baseline_schema.sql` and `payment-backend/.../V1__baseline_schema.sql` (file reads only). |

## Column check of the ADR 0001 queries (static, against the Flyway baselines)
| Query column | Table | Present in V1 |
|---|---|---|
| `payment_flow_version` (int), `status`, `checkout_order_id` | `jobs` (marketplace) | yes |
| `payment_rail` (varchar 30), `status` | `work_contracts` | yes |
| `on_ramp_status`, `off_ramp_status`, `client_payment_status`, `on_chain_off_ramp_status`, `contract_settlement_id` | `freelancer_payout_records` | yes |
| `status` (`CAPTURED`/`CREATED`/`FAILED`) | `bofa_checkout_orders` (payment-backend) | yes (unfinished orders = `CREATED`) |
Rail literals seen in code: `UNIFIED_USDC_PAYOUT`, `PARTNER_ESCROW_MOCK`, `SOLANA_ESCROW` (plus null for the legacy job-level rail). The queries below are the ADR's, unchanged in meaning, with the unfinished-checkout query made explicit.

## Exact request for a DB operator (read-only; run against each environment that holds data, label each result with the environment)
Create or use a dedicated read-only account (`GRANT SELECT` only) and start each session with `SET SESSION TRANSACTION READ ONLY;`. Send back **counts only**; do not export personal, bank or wallet data.
```sql
-- database: marketplace
SELECT payment_flow_version, status, COUNT(*) AS n FROM jobs GROUP BY 1,2 ORDER BY 1,2;
SELECT payment_rail, status, COUNT(*) AS n FROM work_contracts GROUP BY 1,2 ORDER BY 1,2;
SELECT on_ramp_status, off_ramp_status, client_payment_status, on_chain_off_ramp_status,
       contract_settlement_id IS NOT NULL AS contract_driven, COUNT(*) AS n
FROM freelancer_payout_records GROUP BY 1,2,3,4,5 ORDER BY 1,2,3,4,5;
SELECT status, COUNT(*) AS n FROM jobs WHERE checkout_order_id IS NOT NULL GROUP BY 1;
-- database: payment
SELECT status, COUNT(*) AS n FROM bofa_checkout_orders GROUP BY 1;
SELECT COUNT(*) AS unfinished_checkout_orders FROM bofa_checkout_orders WHERE status = 'CREATED';
```
## Query validation against the current schema (LOCAL-SIM, 2026-11-04; not evidence)
The static column check above covered the V1 baselines only. On 2026-11-04 the six queries were **executed unchanged** (read
straight from the `sql` block above by [scripts/db_query_validate.sh](scripts/db_query_validate.sh)) against an **empty,
throwaway** MySQL 8.4.11 container whose schema was built from this repository's migrations (marketplace V1 to V6, payment
V1), under a `GRANT SELECT`-only account in a `READ ONLY` session. Result: all six statements ran without error (empty sets;
Q6 returned 0); `DELETE` and `CREATE TABLE` were refused (MySQL error 1142). Output:
[evidence/db-validation/LOCAL_SIMULATION-e2-query-validation-20261104.txt](evidence/db-validation/LOCAL_SIMULATION-e2-query-validation-20261104.txt).
This proves only that the queries and the read-only account setup work on the current schema. **It is not E2 evidence**: no
row of any real database was read, the counts are from an empty local schema, and E2 stays **BLOCKED (DB operator)**. The
operator may run the same script on their own machine first, as a dry run, before connecting to the development database.

## Operator checklist and required output format (added 2026-11-03, owner response section 7)
Status stays **BLOCKED (DB operator)** until results are returned. No developer or AI session runs these queries.
1. **Authorization first.** The project owner names each environment the operator may query. **Authorized on 2026-11-03:
   the development database only.** An environment not named is not queried. A development or local developer database is
   **not** evidence for any other environment and must be labelled as such (`environment: development`).
2. **Read-only session.** Dedicated account with `GRANT SELECT` only; `SET SESSION TRANSACTION READ ONLY;` at the start of
   every session. No `INSERT`, `UPDATE`, `DELETE`, DDL, export of rows, or copy of personal, bank or wallet data.
3. **Run exactly the six queries above**, unchanged, against the `marketplace` and `payment` databases of each authorized
   environment.
4. **Return one file per environment**, `rail-evidence-<environment>-<yyyymmdd>.md`, in this shape (counts only):
```
environment: <name as authorized by the owner>     operator: <role, not a personal name>     collected_at_utc: <timestamp>
database_versions: marketplace Flyway <latest version>, payment Flyway <latest version>
Q1 jobs by payment_flow_version, status:           | payment_flow_version | status | n |
Q2 work_contracts by payment_rail, status:          | payment_rail | status | n |
Q3 freelancer_payout_records by stage statuses:     | on_ramp_status | off_ramp_status | client_payment_status | on_chain_off_ramp_status | contract_driven | n |
Q4 jobs with a checkout order, by status:           | status | n |
Q5 bofa_checkout_orders by status:                  | status | n |
Q6 unfinished checkout orders:                      | unfinished_checkout_orders |
notes: <anything unusual, e.g. a query error; never row contents>
```
5. A developer attaches the files under `evidence/db/` and records the result against register item E2 / B6. Until then the
   legacy-rail decision C8 stays OPEN and no legacy Solana payment path is changed.

## How the answer will be used
Rail data mix for the roadmap and the open question on legacy payout rails ([07](../07-open-questions.md)). Environment label matters: a result from a developer's local database says nothing about any other environment.
