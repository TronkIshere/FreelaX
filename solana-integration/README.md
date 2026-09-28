# Solana Integration

Spring Boot gateway for the Anchor program `invoice_payments`.

## Runtime

- Java 17 / Spring Boot 3.5.6
- SolanaJ 1.28.0 for public keys, PDA derivation and Ed25519 primitives
- Spring `RestClient` for Solana JSON-RPC
- MySQL + Flyway for signed-transaction build sessions

The gateway exposes the 21 endpoints under `/api/v1/solana`. Read endpoints are
public at this service boundary. Mutating endpoints require `X-Internal-Api-Key`.
The intended caller is `marketplace-backend`, not an untrusted browser.

## Signing model

- Client/Freelancer operations default to `mode=build`. The gateway returns a
  transaction with zeroed signature slots for wallet signers.
- Admin/Rate/Oracle/Mock On-ramp operations default to `mode=send` and require a
  backend signer.
- `SOLANA_LOCAL_PRIVATE_KEYS` is a development-only adapter. Production must
  provide a `TransactionSigner` backed by KMS/HSM and must not store raw keys in
  environment variables.
- A configured system fee payer partially signs sponsored transactions. The
  submit endpoint verifies the build-session message hash, block-height expiry,
  signer set and every Ed25519 signature before calling `sendTransaction`.

## Local configuration

Copy `.env.example`, create the `solana_gateway` database, then start the module:

```bash
mvn -pl solana-integration -am spring-boot:run
```

Swagger UI is available at `/swagger-ui.html`.

## Postman

Import [`docs/solana-integration.postman_collection.json`](docs/solana-integration.postman_collection.json)
into Postman. The collection contains all 21 gateway endpoints, build/send
variants, read queries, signed-transaction submission helpers, and negative
validation/API-key checks. Update the collection variables for the keys and
accounts in the target Solana environment before running an end-to-end flow.

The canonical Program ID is currently
`CwuaAPrxYLK6avPUbMRBerBYt1apdNU829TDZmoAnhEf`. Do not deploy until the Anchor
deploy keypair is deliberately synchronized with this ID.
