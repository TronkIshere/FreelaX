// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @notice Interface of the FreelaX milestone escrow. Escrows are addressed by `escrowId` everywhere (storage keys, events,
/// authorizations) so the same interface could later be served by per-escrow clones.
interface IFreelaXEscrow {
    enum Status {
        None,
        Funded,
        Submitted,
        Revision,
        Disputed,
        Released,
        Refunded
    }

    struct FundTerms {
        bytes32 escrowId;
        address client;
        address freelancer;
        address token;
        uint256 amount;
        uint64 fundingExpiresAt;
        uint64 deliveryDueAt;
        uint32 reviewWindowHours;
        uint8 maxRevisions;
        bool highValueGrace;
        address arbiter;
    }

    struct Escrow {
        address client;
        address freelancer;
        address arbiter;
        uint128 amount;
        Status status;
        uint8 maxRevisions;
        uint8 revisionsUsed;
        bool extensionUsed;
        uint32 reviewWindowSeconds;
        uint32 submissionCount;
        uint64 authNonce;
        uint64 deliveryDueAt;
        uint64 originalDeliveryDueAt;
        uint64 requestedDeliveryDueAt;
        uint64 reviewDueAt;
        bytes32 submissionHash;
        bytes32 resolutionHash;
        address disputedBy;
        uint64 disputedAt;
    }

    event Funded(
        bytes32 indexed escrowId,
        address indexed client,
        address indexed freelancer,
        address arbiter,
        uint256 amount,
        uint64 deliveryDueAt,
        uint32 reviewWindowSeconds,
        uint8 maxRevisions
    );
    event ExtensionRequested(bytes32 indexed escrowId, uint64 requestedDeliveryDueAt);
    event ExtensionApproved(bytes32 indexed escrowId, uint64 deliveryDueAt);
    event WorkSubmitted(bytes32 indexed escrowId, bytes32 evidenceHash, uint32 submissionCount, uint64 reviewDueAt);
    event Released(bytes32 indexed escrowId, address indexed to, uint256 amount, address indexed by);
    event DefaultArbiterChanged(address indexed previousArbiter, address indexed newArbiter);

    error InvalidState(Status current);
    error NotClient();
    error NotFreelancer();
    error AlreadyExists();
    error UnknownEscrow();
    error WrongToken();
    error BadTerms();
    error BadArbiter();
    error FundingExpired();
    error AuthorizationExpired();
    error InvalidAuthorization();
    error StaleNonce();
    error TooEarly();
    error TooLate();
    error ZeroHash();

    function fund(FundTerms calldata terms, uint64 deadline, bytes calldata signature) external;
    function requestExtension(bytes32 escrowId, uint64 newDeliveryDueAt) external;
    function approveExtension(bytes32 escrowId) external;
    function submit(bytes32 escrowId, bytes32 evidenceHash, uint64 deadline, bytes calldata signature) external;
    function release(bytes32 escrowId) external;
    function getEscrow(bytes32 escrowId) external view returns (Escrow memory);
}
