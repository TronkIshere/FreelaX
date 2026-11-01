# Solana Integration

Spring Boot gateway for the Anchor program `invoice_payments`.

## Runtime

- Java 17 / Spring Boot 3.5.16
- SolanaJ 1.28.0 for public keys, PDA derivation and Ed25519 primitives
- Spring `RestClient` for Solana JSON-RPC
- MySQL + Flyway for signed-transaction build sessions

The gateway exposes 25 endpoints under `/api/v1/solana`: 21 for config, rates, invoices, withdrawals, mock on-ramp, balances and transaction submit, plus 4 for milestone escrow (`GET /escrows/{milestoneId}`, `POST /escrows/{milestoneId}/fund`, `POST /escrows/{milestoneId}/actions/{action}`, `POST /escrows/{milestoneId}/mutual-refund`). Read endpoints are
public at this service boundary. Mutating endpoints require `X-Internal-Api-Key`.
The intended caller is `marketplace-backend`, not an untrusted browser.

## Signing model

- Client/Freelancer operations default to `mode=build`. The gateway returns a
  transaction with zeroed signature slots for wallet signers.
- Admin/Rate/Oracle/Mock On-ramp operations default to `mode=send` and require a
  backend signer.
- `SOLANA_LOCAL_PRIVATE_KEYS` is a development-only adapter; the gateway refuses to start with it unless `SOLANA_RPC_HTTP_URL` points to localhost, 127.0.0.1 or `host.docker.internal`. Production must
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
into Postman. The collection covers the 21 non-escrow gateway endpoints (the 4 escrow endpoints are not in it yet), build/send
variants, read queries, signed-transaction submission helpers, and negative
validation/API-key checks. Update the collection variables for the keys and
accounts in the target Solana environment before running an end-to-end flow.

The canonical Program ID is currently
`2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb` (`declare_id!` in `lib.rs` and `Anchor.toml`). The Anchor deploy keypair is synchronized with this ID.
