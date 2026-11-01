# Phase 1 - Security and key-management evaluation (design only)

No signer, KMS client or key store was implemented. Everything below is analysis supported by the experiments cited; **KMS signing was not tested** (no KMS account), which is the largest gap.

## K1. Facts established in Phase 1
| Fact | Evidence |
|---|---|
| Public RPC nodes hold **no accounts**: `eth_sign`/`eth_sendTransaction` are unusable; the application must sign locally and submit `eth_sendRawTransaction` | DOC (C-Chain RPC page); our raw submits through web3j worked on anvil/fork; Fuji rejected an unfunded raw tx at admission |
| Chain binding: EIP-155 signature for another chain id is rejected (`invalid sender`) | OBS (Fuji, J13) |
| The tx hash is computable from the signed bytes before sending | OBS (J2) |
| Backend EIP-712 signatures can be created in Java and verified on-chain and by a second implementation | OBS ([eip712-experiments.md](eip712-experiments.md)) |
| web3j `Credentials`/`ECKeyPair` hold raw private keys in memory | web3j API; prototype used ephemeral keys only |
| USDC is an upgradeable, blacklist/pausable proxy (admin: Circle) | OBS (proxy slot, `isBlacklisted` readable); roles not exercisable |

## K2. Why a public RPC does not manage keys
The endpoint is an untrusted relay: it only sees signed bytes. Anything that "unlocks" an account on a node would put the key on a machine we do not control and is disabled on Avalanche. Signing must live in our trust boundary; the RPC provider (and a malicious or faulty one: wrong `eth_chainId`, fake receipts) is outside it. Mitigations already recommended: verify `eth_chainId` on every client, cross-check two providers, validate receipt `to`/log emitter.

## K3. Client-side vs backend signing
| | Client-side (user wallet) | Backend-side |
|---|---|---|
| What it signs | the user's own `approve`, `fund`, dispute and refund txs; EIP-712 acceptance of terms | EIP-712 **authorizations** consumed by the contract; at most operational txs (relayer `release`) |
| Key custody | user's wallet | **KMS/HSM**, one key per role |
| Risk | phishing, wrong network (chain detection required) | key compromise = ability to authorize; must **not** equal ability to move funds (D3) |
The backend never needs the user's key and should not hold a key that can withdraw escrow.

## K4. Role separation and least privilege (maps to D3)
| Role | May do | Must not be able to |
|---|---|---|
| Authorization signer (EIP-712) | sign terms for `fund`/`submit`/`requestRevision` bound to chain, contract, escrow, parameters, nonce, deadline | move funds, resolve disputes, change roles |
| Arbiter | resolve a Disputed escrow | sign authorizations, change roles |
| Relayer (gas payer, optional) | submit permissionless `release`/claim | anything privileged (convenience only) |
| Admin (multisig, optional timelock) | pause funding, rotate roles | withdraw escrowed funds or override disputes |
The contract-level negative tests for these exact statements are Phase 3 acceptance items. Do **not** template on the Solana single admin key (config admin = arbiter = co-signer = auto-claimer = resolver).

## K5. KMS/HSM options (analysis; UNVERIFIED by test)
- AWS KMS `ECC_SECG_P256K1` signs digests, non-exportable (Phase 0, secondary source). It returns **DER**; recovering Ethereum `(r,s,v)` requires: parse DER, normalise to **low-s**, try both recovery ids against the expected address. **web3j already gives us the verification half** (`Sign.signedMessageHashToKey`, used in [J9](evidence/java-local-experiments.json)), so the conversion can be unit-tested offline with ephemeral keys; the KMS call itself is not tested.
- EIP-712: KMS signs the 32-byte digest produced by `StructuredDataEncoder` (digest equality with the contract was proven); EIP-155 transactions: sign the 32-byte tx hash, set `v` from the recovery id.
- Alternatives not evaluated: HashiCorp Vault (secp256k1 support unchecked), Cloud HSM, GCP/Azure equivalents. Choice of vendor stays a business decision (backlog 4).
- Phase 2 must therefore prove: KMS signature -> valid Fuji tx and valid EIP-712 recovery (planned acceptance item).

## K6. Rotation, recovery, multisig, emergency
- **Rotation:** roles are on-chain (`AccessControl`): grant new key, revoke old; the backend config references a key *alias* per role, never a raw key. The authorization signer should be rotatable without touching funds (benefit of the D3 design). Nonces are per signer so a new signer starts clean.
- **Arbiter loss:** if the arbiter were snapshotted per escrow (Solana style) a lost key strands disputed escrows; role-based arbiter allows admin rotation (open question 1, recommended role-based).
- **Multisig/timelock:** admin actions (pause, role rotation) through a multisig; pause only blocks new funding (D4 clarification). Membership is a business decision.
- **Emergency:** pause funding; revoke compromised authorization signer; permissionless release/refund paths keep working with the backend offline (to be proven in Phase 6).
- **USDC admin risk** (blacklist, pause, upgrade) cannot be mitigated by key management; needs the pull-payout/reroute design in Phase 3.

## K7. Secrets discipline observed in this phase
No private key, API key or token was written to the repo, evidence or logs. Experiments used ephemeral in-memory keys; anvil's funds exist only locally; the one test vector key used for the `cast` interop was written to the OS temp directory (not the repo) and deleted, and the committed vector file contains the digest and signature only. No `.env` files were created. RPC URLs used are public and key-less; the probe redacts URLs containing `key`.

## Status
Analysis: **complete**. KMS signing, role-separation negative tests, rotation drill: **NOT TESTED (Phase 2/3/6)**.

---
# Part B update (2026-11-01): KMS necessity, signature conversion, role separation, rotation

## S-B1. Is KMS necessary under the simulation scope?
**Recommendation: no, defer production KMS integration.** FreelaX holds no real funds ([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md)); every key controls testnet or local tokens only. A KMS would add vendor accounts, IAM and cost for protection of nothing of monetary value. The security value that remains is **hygiene and architecture**, which do not need KMS: role separation, no keys in Git or logs, least privilege, environment isolation. (Owner confirmation requested; D3's role-separation principles are unaffected.)

**Security boundary required for local/Fuji development signing (specification):**
1. Keys are generated per developer/environment, ephemeral, testnet-only; stored outside the repository (e.g. `~/.freelax-phase1/ephemeral-testnet.key` in the Phase 1 script) with filesystem permissions; never printed, logged or committed; secret scanning in CI.
2. The signer refuses to start on a Mainnet chain id/endpoint and on a Fuji balance above a testnet-scale cap (guards prototyped in `EnvGuard`, runbook).
3. Distinct keys per role (authorization signer, arbiter, relayer); the authorization signer cannot move funds or change roles (Foundry tests below).
4. A signer interface (`sign(digest) -> r,s,v`) so a KMS can be added later without touching business code.
5. Fuji faucet wallets are single-purpose and deleted after use.

## S-B2. DER to `r,s,v` conversion: local cryptographic validation (NOT provider compatibility)
`KmsConversionTest` uses a **fake KMS**: non-exportable key held in an object that only exposes the public key and `signDigest(digest32) -> DER`, with a **random-k** ECDSA like many HSM/KMS implementations (evidence [kms-conversion-local.json](evidence/kms-conversion-local.json)):
| Check | Result |
|---|---|
| 300 random digests: DER parsed, `s` normalised to low-s, recovery id found by trying both | **PASS**; **142 to 155 of 300 signatures were high-s before normalisation** (runs vary), recovery ids split about evenly, every recovered address equals the key's address |
| EIP-712 digest (web3j `StructuredDataEncoder`) signed by the fake KMS | **PASS**: low-s signature accepted by `useAuthorization` on anvil; the **un-normalised high-s signature is rejected** by OpenZeppelin ECDSA with `ECDSAInvalidSignatureS` (selector `0xd78bce0c`): normalisation is mandatory, not cosmetic |
| EIP-1559 transaction hash signed by the fake KMS, signed envelope built with web3j | **PASS**: accepted by anvil; receipt `from` equals the KMS key's address (recovery parity correct); hash equals the node's hash |
Not tested: any real KMS (AWS, GCP, Azure, Vault): API shape, latency, quotas, key specs, IAM, DER details of a particular vendor. **Status: conversion algorithm PASS (local); provider compatibility NOT APPLICABLE (deferred by scope).**

## S-B3. Role separation and rotation (Foundry, lab contract only, not the escrow role model)
`forge test` (9 unit tests, all pass; [forge-unit-tests-partb.txt](evidence/forge-unit-tests-partb.txt)): the authorization signer cannot call operator functions or grant itself roles; an operator cannot grant roles (admin only); rotating the signer (grant new, revoke old) makes the **old key's signature revert** (`BadSigner`) and the new key's signature work with its own nonce space. These tests are specifications for Phase 3 negative tests.

## S-B4. External audit and key ceremony
Under the simulation scope an external audit and a key ceremony are **not required by evidence from this phase**; whether the owner still wants them is OPEN ([07](../07-open-questions.md)). A lightweight alternative supported by Phase 1 tooling: Slither + fuzz/invariant tests in CI.

---
# Ephemeral development signers (owner decision: KMS DEFERRED, 2026-11-01)

**Decision (OWNER-DECIDED):** production KMS integration is deferred. LOCAL_SIMULATION and AVALANCHE_FUJI use ephemeral development signers. Mainnet signing must be impossible under the simulation scope. No production signing infrastructure is implemented in Phase 1 or planned before a separate owner decision.

## Requirements for development signers
1. **Isolation by role and environment:** distinct keys for each role (authorization signer, arbiter, relayer, pauser, administrator, test buyer/freelancer wallets) and distinct keys per environment; a key accepted for LOCAL_SIMULATION is never loaded in AVALANCHE_FUJI and vice versa.
2. **Ephemeral:** generated for the environment/run; Fuji wallets are single-purpose and discarded after the experiment. Never reuse production or personal wallet keys; never import an existing wallet.
3. **No silent persistence:** a key is written to disk only by an explicit, announced action (the Phase 1 `generate` mode prints the file location and only the address, keeps the file outside the repository, and tells the operator to delete it). In-memory keys (LOCAL_SIMULATION default) are never written. No key, seed phrase or mnemonic is committed, logged or printed.
4. **Mainnet impossible:** the environment guard rejects chain id 43114 and Mainnet endpoints before any request; no configuration path enables them; a signer cannot be constructed for a chain id outside the allow-list.
5. **Funds cap:** refuse a Fuji signer whose balance exceeds a testnet-scale threshold (safety net against a mistakenly imported real-funds key; not proof).
6. **Signer interface:** `sign(digest) -> (r, s, v)` with low-s normalisation so a different backend can replace the development signer later without touching business code.

## Security limitations of ephemeral development signers (be explicit)
- Keys sit in process memory and, if persisted, in a plain file: any process or user with file access can use them. This is acceptable only because the tokens have no monetary value.
- No hardware protection, no audit trail of signing requests, no rate or policy limits, no separation of duties enforced outside the application, no backup or recovery: losing a key strands testnet assets (acceptable) and disputed demo escrows if the arbiter key is lost (design for a role-based arbiter in the contract, open question 1).
- Rotation is manual (grant new, revoke old on-chain; validated in the lab contract) and there is no emergency revocation procedure beyond the administrator role.
- A compromised development signer could authorize on the testnet within the limits of its role; this must not be mistaken for a production-grade guarantee, and demonstrations must not claim production-grade custody.

## Requirements for a future production-grade signing architecture (not planned, for completeness)
Non-exportable keys in a KMS/HSM with per-role keys and IAM least privilege; documented key ceremony and rotation drills; multi-party control (multisig and timelock) for administrative actions; signing policy and rate limits; immutable audit logs and alerting; DER to `r,s,v` conversion with low-s normalisation and recovery-id search (validated locally in Part B with a fake KMS, but **not** against any real provider); emergency pause and revocation runbooks; separate environments with no shared keys; external review. A separate owner decision is required to start any of this.
