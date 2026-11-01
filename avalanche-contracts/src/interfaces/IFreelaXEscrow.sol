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
    event RevisionRequested(bytes32 indexed escrowId, bytes32 feedbackHash, uint8 revisionsUsed);
    event DisputeOpened(bytes32 indexed escrowId, address indexed by, bytes32 reasonHash);
    event DisputeResolved(bytes32 indexed escrowId, bool releasedToFreelancer, bytes32 resolutionHash);
    event Released(bytes32 indexed escrowId, address indexed to, uint256 amount, address indexed by);
    event Refunded(bytes32 indexed escrowId, address indexed to, uint256 amount, bool mutual);
    event DefaultArbiterChanged(address indexed previousArbiter, address indexed newArbiter);

    error InvalidState(Status current);
    error NotClient();
    error NotFreelancer();
    error NotParticipant();
    error NotArbiter();
    error RevisionsExhausted();
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
    function requestRevision(bytes32 escrowId, bytes32 feedbackHash, uint64 deadline, bytes calldata signature) external;
    function openDispute(bytes32 escrowId, bytes32 reasonHash) external;
    function release(bytes32 escrowId) external;
    function resolveDispute(bytes32 escrowId, bool releaseToFreelancer, bytes32 resolutionHash) external;
    function mutualRefund(bytes32 escrowId, uint64 deadline, bytes calldata clientSignature, bytes calldata freelancerSignature) external;
    function getEscrow(bytes32 escrowId) external view returns (Escrow memory);
}
