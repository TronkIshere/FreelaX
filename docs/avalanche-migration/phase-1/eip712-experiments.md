# Phase 1 - EIP-712 and wallet-authorization experiments

Evidence: [java-local-experiments.json](evidence/java-local-experiments.json) (J9), [eip712-java-vector.json](evidence/eip712-java-vector.json), [eip712-cast-interop.txt](evidence/eip712-cast-interop.txt), [forge-unit-tests.txt](evidence/forge-unit-tests.txt). Code: `Phase1Lab.sol` (`useAuthorization`, `authDigest`), `LocalEvmExperimentsTest.eip712...`, `Phase1Lab.t.sol`, `scripts/eip712_interop.sh`. Ephemeral keys only; **no key is stored in the repo** (the interop key goes to the OS temp dir and was deleted after use).

## E1. Setup
Typed data: `Authorization(address account, bytes32 action, uint256 nonce, uint256 deadline)`, domain `{name:"FreelaXPhase1Lab", version:"1", chainId, verifyingContract}`. Contract side: OZ 5.7.0 `EIP712` + `ECDSA`; the signer must hold `AUTH_SIGNER_ROLE`; nonce consumed per `(signer, nonce)`; `block.timestamp > deadline` reverts. Java side: web3j `StructuredDataEncoder` (digest) + `Sign.signMessage` (secp256k1). This is a **lab shape only**, not the FreelaX authorization policy (open item D3/[07](../07-open-questions.md)).

## E2. Results
| Check | Result | Where | Status |
|---|---|---|---|
| Struct hash / final digest | **Java digest == contract `_hashTypedDataV4` digest** (`authDigest` via `eth_call`) | anvil | PASS |
| Domain name / version / chainId / verifyingContract bound | Changing chainId (43114 vs 31337) -> `BadSigner`; different verifying contract -> `BadSigner` (Java-signed on anvil, Solidity-signed in forge) | both | PASS |
| Signature generation + recovery | Java recovers the signer address from the digest and signature | Java | PASS |
| Invalid signature rejected | Tampered signature recovers a different address / throws `IllegalArgumentException`; garbage bytes revert in `ECDSA.recover`; random (role-less) signer -> `BadSigner` | both | PASS |
| Tampered parameter / wrong caller | Different nonce or `msg.sender` than signed -> recovers another address -> `BadSigner` | forge | PASS |
| Replay protection | Second use of the same `(signer, nonce)` -> `NonceUsed`; first use succeeded and emitted `Authorized` | both | PASS |
| Expiry | Past `deadline` -> `Expired`; deadline boundary is `block.timestamp > deadline` (equality still valid) | both | PASS |
| **Java <-> standard EVM implementation interop** | Foundry `cast wallet sign --data` over the **same typed JSON** produced a **byte-identical 65-byte signature** to web3j (both RFC 6979 deterministic, low-s); `cast wallet verify` confirmed the Java signature for the Java signer address | cast 1.8.7 vs web3j 6.1.0 | PASS |
Caveat: `cast hash-typed-data` invocation errored in my script (syntax), so the digest comparison with cast is **indirect** (identical signature over the same typed data, plus Java digest == on-chain digest). Not a failure of the stack.
`v` handling: web3j returns `v` as 27/28; OZ `ECDSA.recover` accepts 27/28 and rejects high-s. web3j produced low-s signatures in all runs.

## E3. Not tested
- Wallet-side signing (`eth_signTypedData_v4` in MetaMask/Core): needs a browser wallet; the frontend is out of scope. The Java/Foundry equivalence makes the digest standard, but **wallet UX/typed-data rendering is UNVERIFIED**.
- KMS-produced signatures (DER -> r,s,v conversion): UNVERIFIED ([security-key-management-evaluation.md](security-key-management-evaluation.md)).
- ERC-1271 smart-wallet signers, EIP-2098 compact signatures: not tested.

## E4. Three things that are not interchangeable
| | EIP-712 typed-data signing | EIP-2612 `permit` | EIP-3009 `transferWithAuthorization` |
|---|---|---|---|
| What it is | A **hashing and domain-separation standard** for signing structured data off-chain. Nothing happens on-chain until some contract verifies it. | A **token-defined** function: the owner signs an EIP-712 `Permit(owner, spender, value, nonce, deadline)`; anyone submits `permit`, which sets an **allowance** | A **token-defined** function: the owner signs `TransferWithAuthorization(from, to, value, validAfter, validBefore, nonce)`; anyone submits it and the token **moves funds directly** (no allowance) |
| Who verifies | The contract you write (FreelaX: for backend authorizations/arbiter decisions) | The USDC contract | The USDC contract |
| Replay protection | You design it (here: `(signer, nonce)` map, deadline) | Sequential `nonces(owner)` | Random 32-byte nonce, `authorizationState` |
| Verified on Fuji USDC | - (Fuji USDC uses it for its own domain; `DOMAIN_SEPARATOR` recomputed) | **Executed, behaviourally supported** (fork) | **Executed, behaviourally supported** (fork); `receiveWithAuthorization` enforces `caller == payee` |
| FreelaX relevance | Backend/arbiter authorizations (D3); mutual-refund signatures | Optional UX optimisation for `approve` | Alternative funding path; payee-bound variant resists front-running |
A signature for one cannot be used for another: the typehash differs, and each is domain-bound to a different verifying contract (the escrow vs the USDC token). Choosing among them for funding is a Phase 3 design decision.

## E5. Implication for FreelaX
The chosen Java stack (web3j 6.1.0) **can create and verify EIP-712 signatures that match the on-chain verifier and a second independent implementation**. Phase 3 should keep: domain = (name, version, **chainId**, **verifyingContract**), explicit `action`, `nonce`, `deadline`, and bind every authorized parameter (escrow id, amounts, windows) as typed fields; negative tests exactly like those in `Phase1Lab.t.sol`. Deadlines are `block.timestamp` comparisons, subject to [timestamp-deadline-experiments.md](timestamp-deadline-experiments.md).

## Status
**PASS** (library correctness, domain separation, replay, expiry, cross-implementation). Wallet-side and KMS signing: **UNVERIFIED**.
