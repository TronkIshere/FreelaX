# Deployment manifests

`Deploy.s.sol` writes `deployments/<ENVIRONMENT>.json` (ignored by git for LOCAL_SIMULATION: an Anvil chain is disposable). The
gateway (`avalanche.deployment-manifest`) refuses a manifest whose environment or chain id differs from the live endpoint.

## LOCAL_SIMULATION (Anvil, simulated USDC)
`scripts/deploy-local.sh <anvil-rpc-url>` with `ARBITER` and `AUTH_SIGNER` set to the addresses of the gateway's development
signers (they are logged at startup; the keys are ephemeral, so a new gateway process needs a new deployment or a role grant).

## AVALANCHE_FUJI (testnet) - BLOCKED until a human-operated signing procedure exists
No backend signer, key or credential exists for Fuji, by owner decision. A human with a wallet that holds Fuji AVAX runs the same
script with a hardware or keystore signer chosen on the forge command line, for example:

    DEPLOY_ENVIRONMENT=AVALANCHE_FUJI ARBITER=<addr> AUTH_SIGNER=<addr> \
      forge script script/Deploy.s.sol --rpc-url <fuji https url from the gateway allow-list> --ledger --broadcast

Never pass a private key on the command line or commit one. After the deployment, commit the produced
`deployments/AVALANCHE_FUJI.json` deliberately (it contains only addresses) and start the gateway with it; the gateway then
verifies code, token, decimals and the asset allow-list before accepting it. The token is the Circle Fuji USDC
`0x5425890298aed601595a70AB815c96711a31Bc65`; there is no mock mint on a real testnet token (test USDC comes from the Circle faucet).

## Explorer verification on Fuji (Phase 1 E7; optional per backlog amendment row 19) - BLOCKED
Prerequisites: the Fuji deployment above exists (register B2) and the owner provides a Snowtrace API key through the
operator's own environment (an external provider account; never committed). Official Avalanche documentation (contract
verification pages on build.avax.network, checked 2026-11-03) points Fuji verification at the Snowtrace testnet API
`https://api-testnet.snowtrace.io/api` and uses Foundry's `forge verify-contract` (or `--verify` at deployment time). The
human operator would run, for the escrow:

    forge verify-contract --chain 43113 --watch \
      --constructor-args $(cast abi-encode "constructor(address,address,address)" <admin> <token> <arbiter>) \
      <escrow address> src/FreelaXEscrow.sol:FreelaXEscrow
    # with the API key supplied by the operator's environment, e.g. ETHERSCAN_API_KEY

Record the explorer URL of the verified source in the Phase 1 evidence. The compiler settings must match `foundry.toml`
(solc 0.8.38, optimizer 200, `via_ir`, `evm_version = "cancun"`). A Routescan-specific flow was not found in official docs.
