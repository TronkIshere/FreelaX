// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {EIP712} from "@openzeppelin/contracts/utils/cryptography/EIP712.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {IFreelaXEscrow} from "./interfaces/IFreelaXEscrow.sol";

/// @notice Shared, immutable milestone escrow. One record per escrowId; custody is logical (a shared token balance).
/// The Solana rules are preserved as bounded parameters bound by a backend EIP-712 authorization: the backend signer can
/// only authorize terms, it can never move funds, resolve disputes, pause or change roles.
contract FreelaXEscrow is IFreelaXEscrow, AccessControl, Pausable, ReentrancyGuard, EIP712 {
    using SafeERC20 for IERC20;

    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");
    bytes32 public constant AUTH_SIGNER_ROLE = keccak256("AUTH_SIGNER_ROLE");

    uint32 public constant MIN_REVIEW_HOURS = 24;
    uint32 public constant MAX_REVIEW_HOURS = 168;
    uint64 public constant MAX_EXTENSION = 7 days;
    uint8 public constant MAX_REVISIONS = 2;
    uint256 public constant HIGH_VALUE_THRESHOLD = 500_000_000;
    uint32 public constant HIGH_VALUE_GRACE = 24 hours;

    bytes32 public constant FUND_TYPEHASH = keccak256(
        "FundAuthorization(bytes32 escrowId,address client,address freelancer,address token,uint256 amount,uint64 fundingExpiresAt,uint64 deliveryDueAt,uint32 reviewWindowHours,uint8 maxRevisions,bool highValueGrace,address arbiter,uint64 nonce,uint64 deadline)"
    );
    bytes32 public constant SUBMIT_TYPEHASH = keccak256(
        "SubmitAuthorization(bytes32 escrowId,bytes32 evidenceHash,uint32 submissionCount,uint64 nonce,uint64 deadline)"
    );

    bytes32 public constant REVISION_TYPEHASH = keccak256(
        "RevisionAuthorization(bytes32 escrowId,bytes32 feedbackHash,uint8 revisionsUsed,uint64 nonce,uint64 deadline)"
    );
    bytes32 public constant MUTUAL_REFUND_TYPEHASH = keccak256("MutualRefund(bytes32 escrowId,uint64 nonce,uint64 deadline)");
    // Owner decision R12 (2026-11-05). Binds the escrow, the binary outcome, the full amount, a settlement record hash, the
    // dispute being settled (disputedAt), the per-escrow nonce and a deadline. Chain id and this contract come from the
    // EIP-712 domain. A distinct type hash, so a MutualRefund signature pair is never a settlement pair.
    bytes32 public constant MUTUAL_SETTLEMENT_TYPEHASH = keccak256(
        "MutualSettlement(bytes32 escrowId,bool releaseToFreelancer,uint256 amount,bytes32 settlementHash,uint64 disputedAt,uint64 nonce,uint64 deadline)"
    );

    IERC20 public immutable token;
    address public defaultArbiter;
    uint256 public totalOpenAmount;

    mapping(bytes32 escrowId => Escrow) private escrows;

    constructor(address admin, IERC20 token_, address defaultArbiter_) EIP712("FreelaXEscrow", "1") {
        if (admin == address(0) || address(token_) == address(0) || defaultArbiter_ == address(0)) {
            revert BadTerms();
        }
        token = token_;
        defaultArbiter = defaultArbiter_;
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        emit DefaultArbiterChanged(address(0), defaultArbiter_);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Funding
    // ---------------------------------------------------------------------------------------------------------------

    /// @notice Funds a new escrow. Needs the client as msg.sender, a prior ERC-20 approval and a backend authorization
    /// that binds every term, this chain, this contract and a deadline. Blocked while paused (the only paused function).
    function fund(FundTerms calldata t, uint64 deadline, bytes calldata signature) external whenNotPaused nonReentrant {
        if (msg.sender != t.client) revert NotClient();
        if (t.escrowId == bytes32(0)) revert BadTerms();
        if (escrows[t.escrowId].status != Status.None) revert AlreadyExists();
        _validateTerms(t);
        _checkAuthorization(fundDigest(t, 0, deadline), deadline, signature);

        uint32 windowSeconds = t.reviewWindowHours * 1 hours;
        if (t.highValueGrace && t.amount > HIGH_VALUE_THRESHOLD) {
            windowSeconds += HIGH_VALUE_GRACE;
        }

        Escrow storage e = escrows[t.escrowId];
        e.client = t.client;
        e.freelancer = t.freelancer;
        e.arbiter = t.arbiter;
        e.amount = uint128(t.amount);
        e.status = Status.Funded;
        e.maxRevisions = t.maxRevisions;
        e.reviewWindowSeconds = windowSeconds;
        e.authNonce = 1;
        e.deliveryDueAt = t.deliveryDueAt;
        e.originalDeliveryDueAt = t.deliveryDueAt;
        totalOpenAmount += t.amount;

        token.safeTransferFrom(t.client, address(this), t.amount);
        emit Funded(t.escrowId, t.client, t.freelancer, t.arbiter, t.amount, t.deliveryDueAt, windowSeconds, t.maxRevisions);
    }

    function _validateTerms(FundTerms calldata t) private view {
        if (t.token != address(token)) revert WrongToken();
        if (t.amount == 0 || t.amount > type(uint128).max) revert BadTerms();
        if (t.fundingExpiresAt < block.timestamp) revert FundingExpired();
        if (t.deliveryDueAt <= block.timestamp) revert BadTerms();
        if (t.reviewWindowHours < MIN_REVIEW_HOURS || t.reviewWindowHours > MAX_REVIEW_HOURS) revert BadTerms();
        if (t.maxRevisions > MAX_REVISIONS) revert BadTerms();
        if (t.client == address(0) || t.freelancer == address(0) || t.freelancer == t.client) revert BadTerms();
        // The arbiter is a per-escrow snapshot of the default arbiter at funding time; it is never reassigned afterwards.
        if (t.arbiter == address(0) || t.arbiter != defaultArbiter) revert BadArbiter();
        if (t.arbiter == t.client || t.arbiter == t.freelancer) revert BadArbiter();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Extension (freelancer requests, client approves; at most once, at most 7 days, only before the original date)
    // ---------------------------------------------------------------------------------------------------------------

    function requestExtension(bytes32 escrowId, uint64 newDeliveryDueAt) external {
        Escrow storage e = _existing(escrowId);
        if (msg.sender != e.freelancer) revert NotFreelancer();
        if (e.status != Status.Funded) revert InvalidState(e.status);
        if (e.extensionUsed || e.requestedDeliveryDueAt != 0) revert BadTerms();
        if (block.timestamp >= e.originalDeliveryDueAt) revert TooLate();
        if (newDeliveryDueAt <= e.originalDeliveryDueAt || newDeliveryDueAt > e.originalDeliveryDueAt + MAX_EXTENSION) {
            revert BadTerms();
        }
        e.requestedDeliveryDueAt = newDeliveryDueAt;
        emit ExtensionRequested(escrowId, newDeliveryDueAt);
    }

    function approveExtension(bytes32 escrowId) external {
        Escrow storage e = _existing(escrowId);
        if (msg.sender != e.client) revert NotClient();
        if (e.status != Status.Funded) revert InvalidState(e.status);
        if (e.extensionUsed || e.requestedDeliveryDueAt == 0) revert BadTerms();
        if (block.timestamp >= e.originalDeliveryDueAt) revert TooLate();
        e.deliveryDueAt = e.requestedDeliveryDueAt;
        e.requestedDeliveryDueAt = 0;
        e.extensionUsed = true;
        emit ExtensionApproved(escrowId, e.deliveryDueAt);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Submission
    // ---------------------------------------------------------------------------------------------------------------

    /// @notice The freelancer submits evidence. The backend authorization binds the evidence hash and the submission
    /// counter, so the review clock (and with it permissionless release) can only start for evidence the marketplace recorded.
    function submit(bytes32 escrowId, bytes32 evidenceHash, uint64 deadline, bytes calldata signature) external {
        Escrow storage e = _existing(escrowId);
        if (msg.sender != e.freelancer) revert NotFreelancer();
        if (evidenceHash == bytes32(0)) revert ZeroHash();
        if (e.status != Status.Funded && e.status != Status.Revision) revert InvalidState(e.status);
        if (e.status == Status.Funded && block.timestamp > e.deliveryDueAt) revert TooLate();
        _checkAuthorization(submitDigest(escrowId, evidenceHash, e.submissionCount, e.authNonce, deadline), deadline, signature);

        e.authNonce += 1;
        e.submissionHash = evidenceHash;
        e.submissionCount += 1;
        e.reviewDueAt = uint64(block.timestamp) + e.reviewWindowSeconds;
        e.status = Status.Submitted;
        emit WorkSubmitted(escrowId, evidenceHash, e.submissionCount, e.reviewDueAt);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Revision: the client, before the review deadline, at most maxRevisions times. The backend authorization is kept
    // for parity with the Solana co-sign (it binds the feedback record); it grants no other power (owner decision O-4).
    // ---------------------------------------------------------------------------------------------------------------

    function requestRevision(bytes32 escrowId, bytes32 feedbackHash, uint64 deadline, bytes calldata signature) external {
        Escrow storage e = _existing(escrowId);
        if (msg.sender != e.client) revert NotClient();
        if (feedbackHash == bytes32(0)) revert ZeroHash();
        if (e.status != Status.Submitted) revert InvalidState(e.status);
        if (block.timestamp >= e.reviewDueAt) revert TooLate();
        if (e.revisionsUsed >= e.maxRevisions) revert RevisionsExhausted();
        _checkAuthorization(revisionDigest(escrowId, feedbackHash, e.revisionsUsed, e.authNonce, deadline), deadline, signature);

        e.authNonce += 1;
        e.revisionsUsed += 1;
        e.reviewDueAt = 0;
        e.status = Status.Revision;
        emit RevisionRequested(escrowId, feedbackHash, e.revisionsUsed);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dispute: client or freelancer. A dispute clears the review deadline, so permissionless release cannot fire on it.
    // ---------------------------------------------------------------------------------------------------------------

    function openDispute(bytes32 escrowId, bytes32 reasonHash) external {
        Escrow storage e = _existing(escrowId);
        if (msg.sender != e.client && msg.sender != e.freelancer) revert NotParticipant();
        if (reasonHash == bytes32(0)) revert ZeroHash();
        if (e.status == Status.Submitted) {
            if (block.timestamp >= e.reviewDueAt) revert TooLate();
        } else if (e.status == Status.Funded) {
            if (block.timestamp <= e.deliveryDueAt) revert TooEarly();
        } else if (e.status != Status.Revision) {
            revert InvalidState(e.status);
        }
        e.status = Status.Disputed;
        e.disputedBy = msg.sender;
        e.disputedAt = uint64(block.timestamp);
        e.reviewDueAt = 0;
        emit DisputeOpened(escrowId, msg.sender, reasonHash);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Release: the client at any time, anyone once the review window has passed. Pause never blocks it.
    // ---------------------------------------------------------------------------------------------------------------

    function release(bytes32 escrowId) external nonReentrant {
        Escrow storage e = _existing(escrowId);
        if (e.status != Status.Submitted) revert InvalidState(e.status);
        if (msg.sender != e.client && block.timestamp < e.reviewDueAt) revert TooEarly();
        uint256 amount = e.amount;
        address to = e.freelancer;
        e.status = Status.Released;
        totalOpenAmount -= amount;
        emit Released(escrowId, to, amount, msg.sender);
        token.safeTransfer(to, amount);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dispute resolution: only the escrow's own (snapshotted) arbiter, a binary outcome with a non-zero resolution hash.
    // ---------------------------------------------------------------------------------------------------------------

    function resolveDispute(bytes32 escrowId, bool releaseToFreelancer, bytes32 resolutionHash) external nonReentrant {
        Escrow storage e = _existing(escrowId);
        if (msg.sender != e.arbiter) revert NotArbiter();
        if (e.status != Status.Disputed) revert InvalidState(e.status);
        if (resolutionHash == bytes32(0)) revert ZeroHash();
        uint256 amount = e.amount;
        e.resolutionHash = resolutionHash;
        totalOpenAmount -= amount;
        emit DisputeResolved(escrowId, releaseToFreelancer, resolutionHash);
        if (releaseToFreelancer) {
            e.status = Status.Released;
            emit Released(escrowId, e.freelancer, amount, msg.sender);
            token.safeTransfer(e.freelancer, amount);
        } else {
            e.status = Status.Refunded;
            emit Refunded(escrowId, e.client, amount, false);
            token.safeTransfer(e.client, amount);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Mutual refund: both parties sign the same EIP-712 message; anyone may submit it. Not available once Disputed (a
    // Disputed escrow is settled by the arbiter or by mutualSettleDispute below, owner decision R12).
    // The two-signature mechanism preserves the Solana rule that both parties must agree (owner decision on question 10).
    // ---------------------------------------------------------------------------------------------------------------

    function mutualRefund(bytes32 escrowId, uint64 deadline, bytes calldata clientSignature, bytes calldata freelancerSignature)
        external
        nonReentrant
    {
        Escrow storage e = _existing(escrowId);
        if (e.status != Status.Funded && e.status != Status.Submitted && e.status != Status.Revision) {
            revert InvalidState(e.status);
        }
        if (block.timestamp > deadline) revert AuthorizationExpired();
        bytes32 digest = mutualRefundDigest(escrowId, e.authNonce, deadline);
        _requireSignedBy(digest, clientSignature, e.client);
        _requireSignedBy(digest, freelancerSignature, e.freelancer);

        uint256 amount = e.amount;
        e.authNonce += 1;
        e.status = Status.Refunded;
        totalOpenAmount -= amount;
        emit Refunded(escrowId, e.client, amount, true);
        token.safeTransfer(e.client, amount);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Mutual settlement of a Disputed escrow (owner decision R12, 2026-11-05; amends O-5 question 8 for this path only): the client AND the freelancer sign one EIP-712
    // MutualSettlement message choosing the same binary outcome (the arbiter's outcome space, O-5 question 6: no partial
    // split). Anyone may submit it. The arbiter keeps full authority while the escrow is Disputed: whichever valid action
    // lands first settles it, the other then reverts on the terminal state. No role (admin, pauser, backend signer,
    // arbiter) can use it: only the two snapshotted parties' signatures count. Pause does not block it (as release/refund).
    // ---------------------------------------------------------------------------------------------------------------

    function mutualSettleDispute(
        bytes32 escrowId,
        bool releaseToFreelancer,
        bytes32 settlementHash,
        uint64 deadline,
        bytes calldata clientSignature,
        bytes calldata freelancerSignature
    ) external nonReentrant {
        Escrow storage e = _existing(escrowId);
        if (e.status != Status.Disputed) revert InvalidState(e.status);
        if (settlementHash == bytes32(0)) revert ZeroHash();
        if (block.timestamp > deadline) revert AuthorizationExpired();
        bytes32 digest =
            mutualSettlementDigest(escrowId, releaseToFreelancer, e.amount, settlementHash, e.disputedAt, e.authNonce, deadline);
        _requireSignedBy(digest, clientSignature, e.client);
        _requireSignedBy(digest, freelancerSignature, e.freelancer);

        uint256 amount = e.amount;
        e.authNonce += 1;
        e.resolutionHash = settlementHash;
        totalOpenAmount -= amount;
        emit DisputeSettledByParties(escrowId, releaseToFreelancer, settlementHash);
        if (releaseToFreelancer) {
            e.status = Status.Released;
            emit Released(escrowId, e.freelancer, amount, msg.sender);
            token.safeTransfer(e.freelancer, amount);
        } else {
            e.status = Status.Refunded;
            emit Refunded(escrowId, e.client, amount, true);
            token.safeTransfer(e.client, amount);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Administration: pause blocks only new funding; nobody can reach escrowed funds or change an existing arbiter.
    // ---------------------------------------------------------------------------------------------------------------

    function pause() external onlyRole(PAUSER_ROLE) {
        _pause();
    }

    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
    }

    /// @notice Changes the arbiter used for escrows funded AFTER this call. Existing escrows keep their snapshot.
    function setDefaultArbiter(address newArbiter) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (newArbiter == address(0)) revert BadArbiter();
        emit DefaultArbiterChanged(defaultArbiter, newArbiter);
        defaultArbiter = newArbiter;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Views and EIP-712 digests (public so the backend and the tests can check digest parity with this contract)
    // ---------------------------------------------------------------------------------------------------------------

    function getEscrow(bytes32 escrowId) external view returns (Escrow memory) {
        return escrows[escrowId];
    }

    function domainSeparator() external view returns (bytes32) {
        return _domainSeparatorV4();
    }

    function fundDigest(FundTerms calldata t, uint64 nonce, uint64 deadline) public view returns (bytes32) {
        // All members are static 32-byte words, so two concatenated abi.encode halves equal one abi.encode (avoids stack depth).
        bytes memory head = abi.encode(FUND_TYPEHASH, t.escrowId, t.client, t.freelancer, t.token, t.amount, t.fundingExpiresAt);
        bytes memory tail = abi.encode(t.deliveryDueAt, t.reviewWindowHours, t.maxRevisions, t.highValueGrace, t.arbiter, nonce, deadline);
        return _hashTypedDataV4(keccak256(bytes.concat(head, tail)));
    }

    function submitDigest(bytes32 escrowId, bytes32 evidenceHash, uint32 submissionCount, uint64 nonce, uint64 deadline)
        public
        view
        returns (bytes32)
    {
        return _hashTypedDataV4(keccak256(abi.encode(SUBMIT_TYPEHASH, escrowId, evidenceHash, submissionCount, nonce, deadline)));
    }

    function revisionDigest(bytes32 escrowId, bytes32 feedbackHash, uint8 revisionsUsed, uint64 nonce, uint64 deadline)
        public
        view
        returns (bytes32)
    {
        return _hashTypedDataV4(keccak256(abi.encode(REVISION_TYPEHASH, escrowId, feedbackHash, revisionsUsed, nonce, deadline)));
    }

    function mutualRefundDigest(bytes32 escrowId, uint64 nonce, uint64 deadline) public view returns (bytes32) {
        return _hashTypedDataV4(keccak256(abi.encode(MUTUAL_REFUND_TYPEHASH, escrowId, nonce, deadline)));
    }

    function mutualSettlementDigest(
        bytes32 escrowId,
        bool releaseToFreelancer,
        uint256 amount,
        bytes32 settlementHash,
        uint64 disputedAt,
        uint64 nonce,
        uint64 deadline
    ) public view returns (bytes32) {
        return _hashTypedDataV4(
            keccak256(
                abi.encode(
                    MUTUAL_SETTLEMENT_TYPEHASH, escrowId, releaseToFreelancer, amount, settlementHash, disputedAt, nonce, deadline
                )
            )
        );
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------------------------

    function _requireSignedBy(bytes32 digest, bytes calldata signature, address expected) private pure {
        (address signer, ECDSA.RecoverError err,) = ECDSA.tryRecover(digest, signature);
        if (err != ECDSA.RecoverError.NoError || signer != expected) revert InvalidAuthorization();
    }

    function _existing(bytes32 escrowId) private view returns (Escrow storage e) {
        e = escrows[escrowId];
        if (e.status == Status.None) revert UnknownEscrow();
    }

    /// @dev The signer must hold AUTH_SIGNER_ROLE at execution time (rotation affects new authorizations; outstanding ones
    /// also lapse by their deadline). ECDSA rejects malleable high-s signatures.
    function _checkAuthorization(bytes32 digest, uint64 deadline, bytes calldata signature) private view {
        if (block.timestamp > deadline) revert AuthorizationExpired();
        (address signer, ECDSA.RecoverError err,) = ECDSA.tryRecover(digest, signature);
        if (err != ECDSA.RecoverError.NoError || !hasRole(AUTH_SIGNER_ROLE, signer)) revert InvalidAuthorization();
    }
}
