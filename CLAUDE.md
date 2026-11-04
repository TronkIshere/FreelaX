# FreelaX: Claude instructions (entry point)

Minimal root entry point. Area-specific instructions live next to the work they govern.

## Avalanche migration
All work on the Solana to Avalanche migration (`avalanche-integration/`, `avalanche-contracts/`, `docs/avalanche-migration/`
and the Avalanche parts of other modules) follows:

@docs/avalanche-migration/CLAUDE.md

Status source of truth: `docs/avalanche-migration/PROJECT_STATUS.md`. Resume point: `docs/avalanche-migration/AUTONOMOUS_EXECUTION_LOG.md`.

## Project-wide constraint
FreelaX is a development simulation with no real funds; Mainnet is out of scope (`docs/SIMULATION_SCOPE.md`).

`avalanche-contracts/lib/**/CLAUDE.md` belongs to vendored third-party code and is not a FreelaX instruction.
