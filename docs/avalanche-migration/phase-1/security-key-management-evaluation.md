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
