# FREELAX — AI HANDOFF / LOCAL DEMO RUNBOOK

> Purpose: give another AI enough context to continue or run FreelaX without re-investigating the whole project.
> Updated from the verified P05.6 local-demo state on 2026-09-30.

## 1. Project summary

FreelaX is a two-sided freelancer marketplace with a stablecoin payout rail.

Main user journey:

- Freelancer: Discover jobs -> Apply -> assigned work -> submit V1 -> receive revision feedback -> submit V2.
- Client: Create/manage job -> review applicants -> assign freelancer -> review submission -> request revision or approve.
- After approval: payment/capture -> simulated on-chain payout/off-ramp -> tax/certificate evidence.

The frontend must treat blockchain/payment details as supporting evidence, not as a crypto trading UI.

## 2. Verified P05 frontend/runtime chronology

Important commits:

- `980e6411546609922ebe56a2d1dc54d5d729ebd5` — P05.1 frontend foundation
- `77b4c8f0bfce4f420b67cbe65421d7b85889e9d5` — PATCH CORS fix
- `0208adec8f84d4f136ecb2f6d3f1bbc508fa692d` — P05.2
- `c9e4f1812fe59c7e995df767c897abf9884f6889` — P05.3
- `288dc19fba83f5e85496756c932b2eb46e955c3a` — MISA PDF fix
- `a448d26c2b2f3db5e7694fe1edb8bb122a64bd23` — P05.4
- `15efeeb307714d10d76fa62d5ff8f1c3381acf4e` — P05.5
- `873a907c1158f1f43af11a8d7b30cc9d9abf3de8` — P05.6 deployment config
- `79109f7e99db7aca0e387571a2023f39f18616f2` — final local-demo hardening commit

Final P05.6 verification result:

`LOCAL DEMO READY — PASS`

Verified at that point:

- Marketplace `127.0.0.1:9191` UP
- Frontend/Caddy `http://localhost:8080` returns 200
- Caddy `/api/v1` proxy works
- unauthenticated `/api/v1/auth/me` returns 401
- Client login PASS
- Freelancer login PASS
- authenticated `/auth/me` returns 200 with correct role for both
- session refresh PASS for both
- Client five main routes PASS
- Freelancer five main routes PASS
- finance/tax/certificate read-only smoke PASS
- all exposed local service ports bind to `127.0.0.1`
- `.env` ignored and not tracked
- no current runtime credential values found committed
- working tree clean after commit `79109f7...`

## 3. IMPORTANT — verify whether the final commit was actually merged/pushed

Do **not** assume merge status from this file.

The last directly verified report before merge showed:

- push: NO
- merge: NO
- feature branch: `feat/p05-frontend-foundation`

A later merge/push command was proposed, but its terminal output was not captured in the original handoff.

On a fresh clone, run:

```powershell
git fetch origin

git remote show origin
git branch -r --contains 79109f7e99db7aca0e387571a2023f39f18616f2
```

Interpretation:

- If the repository default branch, e.g. `origin/master` or `origin/main`, appears in the output, then the final P05.6 hardening commit is already contained in the default branch.
- If only `origin/feat/p05-frontend-foundation` appears, then it was pushed as a feature branch but not merged into the default branch.
- If no remote branch appears, the final commit was not pushed to that remote.

Portable hard check after identifying the default branch:

```powershell
git merge-base --is-ancestor 79109f7e99db7aca0e387571a2023f39f18616f2 origin/master
echo $LASTEXITCODE
```

Use `origin/main` instead if `main` is the default branch.

`0` = the final P05.6 commit is included.
Non-zero = it is not included.

## 4. Architecture

Frontend talks only to Marketplace:

```text
Browser
  |
  v
Caddy / React frontend :8080
  |
  +-- same-origin /api/v1
          |
          v
Marketplace :9191
  |
  +--> Payment :9190
  +--> MISA :9192
  +--> Solana Gateway :9193
  +--> MySQL :3307
  +--> Redis :6380
```

Solana Gateway talks to local Solana RPC at `127.0.0.1:9123` / `host.docker.internal:9123`.

Canonical Solana Program ID:

`4Wd6umju26vej2ftzwR6J55pjkUqDQsxfVkt46UqDb1b`

Admin / provider / upgrade-authority public key:

`3PDJ3tg3jLKZND47Z3NaFsEEFjZU25WHyHMHWz4JhnsZ`

Mock USDC uses 6 decimals.

## 5. Local ports

- Frontend/Caddy: `127.0.0.1:8080`
- Payment: `127.0.0.1:9190`
- Marketplace: `127.0.0.1:9191`
- MISA: `127.0.0.1:9192`
- Solana Gateway: `127.0.0.1:9193`
- MySQL host port: `127.0.0.1:3307`
- Redis host port: `127.0.0.1:6380`
- Solana local RPC: `127.0.0.1:9123`

Main demo URL:

`http://localhost:8080`

## 6. Requirements on a friend's machine

Minimum for the Docker web stack:

- Git
- Docker Desktop with Docker Compose
- Windows PowerShell or equivalent shell

For the **full Solana-backed demo**, the machine also needs a working Solana/Anchor local environment or an equivalent already prepared local validator/runtime.

Previously verified toolchain on the original machine:

- WSL2 Ubuntu 24.04
- Node 20.20.2
- Yarn 1.22.22
- Rust 1.89.0
- Cargo 1.89.0
- Anchor CLI 1.2.0
- Agave / Solana CLI 4.3.0
- `solana-test-validator` 4.3.0

Do not downgrade the validator to the old Agave 3.1.x setup; the Anchor-built program uses the newer sBPF format.


## 6A. Demo accounts for the person running the project

Use the seeded Marketplace accounts for the main demo instead of registering fresh accounts.

### Client demo

- Email: `nguyenhuutrong11133@gmail.com`
- Role: `CLIENT`
- Password: the local Client demo password configured in that machine's untracked root `.env`

### Freelancer demo

- Email: `freelancer.seed@example.com`
- Role: `FREELANCER`
- Password: the local Freelancer demo password configured in that machine's untracked root `.env`

These two emails are part of the seeded demo data. The passwords were intentionally moved out of tracked source during P05.6 hardening, so this handoff file does **not** contain or invent a password.

For a friend's machine, set the password values using the password variables already documented in the current `.env.example`, then start/recreate Marketplace. Do not commit the resulting `.env`.

Important behavior:

- Marketplace startup creates/updates the seeded demo accounts.
- The Client seed owns demo jobs.
- The Freelancer seed has the demo tax identity required by the payout/tax path.
- For the full Solana-backed path, the local Client/Freelancer Wallet mappings and signer/bootstrap configuration must also be valid.
- New user registration remains available for UI/auth testing, but the seeded accounts are the preferred accounts for the complete demo workflow.

After startup, verify each account by signing in at:

`http://localhost:8080`

Then verify `/api/v1/auth/me` returns the expected role.


## 7. Fresh clone — first checks

From the clone:

```powershell
cd "PATH\TO\FreelaX"

git status --short
git log -1 --oneline
git fetch origin
git branch -r --contains 79109f7e99db7aca0e387571a2023f39f18616f2
```

If the final commit is on the default branch, checkout/pull that branch.

Example for `master`:

```powershell
git checkout master
git pull --ff-only origin master
```

Do not hardcode `master` if `git remote show origin` says the default branch is `main`.

## 8. `.env` and secrets

The real root `.env` is intentionally **not committed**.

Start from the repository template only as a reference:

```powershell
Copy-Item .env.example .env
```

Then populate the required values privately.

Important:

- Never commit `.env`.
- Never paste private keys, JWT secrets, database passwords, internal API keys, or demo account passwords into chat or source.
- Never commit Solana keypair JSON files.
- Do not invent fallback secrets.
- Demo Client/Freelancer seed passwords are runtime configuration after P05.6 hardening.

A clean clone cannot reproduce the exact original local runtime unless it receives the required local runtime configuration and, for the full Solana flow, its own local signer/bootstrap state.

## 9. Solana local identities for a full demo

Private keypairs must live outside the repository, for example in WSL:

```bash
~/.config/freelax-demo/
```

Roles required by the local MVP include:

- Client demo signer
- Freelancer demo signer
- Mock on-ramp authority
- Rate authority
- Oracle authority
- Admin/system fee payer

The gateway signer registry uses local private signer material. Never copy these private keys into Git.

The original runtime also had chain state bootstrapped for:

- Mock USDC mint, 6 decimals
- Config PDA
- treasury / treasury ATA
- rate authority
- oracle authority
- mock on-ramp authority
- funded mock treasury

If a friend's validator starts with a fresh ledger, this state must be bootstrapped again before a complete Job -> payout -> tax/certificate mutation flow can work.

## 10. Start the Docker local web/runtime stack

Once `.env` is valid and required local dependencies are ready:

```powershell
docker compose --profile deploy up -d --build
```

Then inspect:

```powershell
docker compose --profile deploy ps
```

Expected local services include:

- MySQL
- Redis
- Payment
- Marketplace
- MISA
- Solana Gateway
- frontend/Caddy

Marketplace should show a loopback mapping equivalent to:

```text
127.0.0.1:9191->9191/tcp
```

## 11. Basic runtime verification

Frontend:

```powershell
curl.exe -I http://localhost:8080
```

Expected:

```text
HTTP/1.1 200 OK
```

Unauthenticated auth check:

```powershell
curl.exe -i http://localhost:8080/api/v1/auth/me
```

Expected:

```text
HTTP/1.1 401 Unauthorized
```

A 401 here is correct when not logged in.

Bad results:

- `502` -> Caddy cannot reach Marketplace.
- `500` -> Marketplace application/auth failure.
- no `:9191` listener -> Marketplace is down/restarting.

## 12. Known fixed P05.6 blockers — do not rediscover unless they reappear

### Marketplace YAML crash

Old stale packaged YAML contained invalid control characters from damaged Vietnamese text and caused:

```text
org.yaml.snakeyaml.reader.ReaderException:
special characters are not allowed
```

Source YAML was repaired and the Marketplace image was rebuilt.

### `/auth/me` returned 500

Old behavior:

```text
NullPointerException:
Cannot invoke UserPrincipal.getId() because principal is null
```

The final fix made unauthenticated `/auth/me` return `401`, while authenticated Client/Freelancer calls still return `200`.

Do not undo this security behavior.

## 13. Frontend product constraints

Do not invent APIs or unsupported actions.

Trusted role comes from:

`/auth/me`

Frontend public API calls go only through Marketplace `/api/v1`.

Do not call Payment, MISA, Solana Gateway, or Solana RPC directly from the browser.

No real role-switch UI.

Client nav:

- Tổng quan
- Công việc
- Thanh toán
- Hoạt động
- Tài khoản

Freelancer nav:

- Tổng quan
- Công việc
- Thu nhập
- Hoạt động
- Tài khoản

Freelancer work subnav:

- Khám phá
- Ứng tuyển
- Công việc của tôi

Financial statuses are independent. Do not fake a single linear financial status if backend states differ.

USDC/on-ramp/off-ramp/bank payout behavior in this MVP is simulated. The UI must not imply real-money transfer where the API reports simulation.

## 14. Canonical workflow

```text
Freelancer
Discover
-> Job Detail
-> Apply
-> PENDING

Client
Jobs
-> Job Detail
-> Applications
-> Assign
-> ACCEPTED / IN_PROGRESS

Freelancer
Workspace
-> Submit V1
-> SUBMITTED_FOR_REVIEW

Client
Review V1
-> Request Revision

Freelancer
Read feedback
-> Submit V2

Client
Review V2
-> Approve
-> COMPLETED

Then:
Payment / payout / off-ramp evidence
-> tax record
-> certificate
```

Do not rerun the full mutation lifecycle just to prove the local web UI boots.

## 15. Read-only demo smoke after startup

Verify:

Client:

- login
- `/auth/me`
- Tổng quan
- Công việc
- Thanh toán
- Hoạt động
- Tài khoản

Freelancer:

- login
- `/auth/me`
- Tổng quan
- Công việc
- Thu nhập
- Hoạt động
- Tài khoản

Then verify finance/tax/certificate screens load.

The last verified environment had at least one existing certificate record with status:

`EXPORT_FAILED`

That is a real record state, not necessarily a deployment failure.

## 16. Security guardrails

Before calling the environment ready:

```powershell
git status --short
git diff --check
git ls-files .env
```

Expected:

- working tree clean unless intentionally editing
- `git diff --check` PASS
- `.env` not tracked
- no private Solana keypair tracked
- no runtime passwords/JWT/internal keys committed
- internal services bind only to loopback for this local-demo setup

## 17. What another AI should NOT do

Do not:

- start a repo-wide rewrite
- restore removed legacy Flutter/PayPal UI
- invent unsupported wallet/send/receive/swap/manual-payout features
- expose private keys
- commit `.env`
- use Cloudflare unless the user explicitly asks for public access again
- rerun expensive full E2E when only startup/read-only smoke is requested
- rotate demo passwords again without a proven credential problem
- overwrite existing Solana keypairs
- hardcode temporary URLs
- assume merge/push status without checking Git

## 18. Current target

The intended current operating mode is:

`LOCAL DEMO`

Main URL:

`http://localhost:8080`

The frontend/runtime itself reached:

`LOCAL DEMO READY — PASS`

The only repository-history question that must be checked on a fresh clone is whether commit:

`79109f7e99db7aca0e387571a2023f39f18616f2`

is already contained in the remote default branch.

## 19. Fast AI startup prompt

A user can give the next AI this instruction:

> Read `FREELAX_AI_HANDOFF_LOCAL_DEMO.md` first. Do not redo prior audits. First verify whether commit `79109f7e99db7aca0e387571a2023f39f18616f2` is contained in the remote default branch. Then inspect `.env.example` and the current Compose configuration without exposing secrets. Help start the local demo at `http://localhost:8080`. Prefer read-only smoke verification; do not run the full Job-to-Certificate mutation flow unless explicitly requested.

