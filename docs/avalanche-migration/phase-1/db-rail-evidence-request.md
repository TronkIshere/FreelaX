# Database and legacy payment-rail evidence: status and operator request

Status: **BLOCKED (no safe read-only access)**. No query was run. Nothing in any database was read or changed. Scope reminder: FreelaX is a development simulation ([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md)); the rows in question are simulation data. The queries are still needed to know which payment rails hold data before any legacy path is considered for retirement. **No legacy Solana payment path is deleted or migrated by this phase.**

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
## How the answer will be used
Rail data mix for the roadmap and the open question on legacy payout rails ([07](../07-open-questions.md)). Environment label matters: a result from a developer's local database says nothing about any other environment.
