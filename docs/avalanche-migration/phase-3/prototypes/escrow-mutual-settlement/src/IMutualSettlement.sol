// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @notice R12 / F14 LOCAL_SIMULATION-only addition to IFreelaXEscrow. Kept in a separate interface so the baseline
/// interface (and every ABI built from it) stays unchanged.
interface IMutualSettlement {
    /// Emitted before the existing Released / Refunded event of the same settlement. Distinct from DisputeResolved so an
    /// indexer never mistakes a parties' settlement for an arbiter decision.
    event DisputeSettledByParties(bytes32 indexed escrowId, bool releasedToFreelancer, bytes32 settlementHash);

    function mutualSettleDispute(
        bytes32 escrowId,
        bool releaseToFreelancer,
        bytes32 settlementHash,
        uint64 deadline,
        bytes calldata clientSignature,
        bytes calldata freelancerSignature
    ) external;

    function mutualSettlementDigest(
        bytes32 escrowId,
        bool releaseToFreelancer,
        uint256 amount,
        bytes32 settlementHash,
        uint64 disputedAt,
        uint64 nonce,
        uint64 deadline
    ) external view returns (bytes32);
}
