// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test, console} from "forge-std/Test.sol";
import {StdInvariant} from "forge-std/StdInvariant.sol";
import {FreelaXEscrow} from "../src/FreelaXEscrow.sol";
import {IFreelaXEscrow} from "../src/interfaces/IFreelaXEscrow.sol";
import {MockUSDC} from "../src/mocks/MockUSDC.sol";
import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";

/// R12 / F14 (owner decision 2026-11-05; moved from the phase-3 lab): adversarial tests of the mutual-settlement path of a Disputed escrow.
abstract contract SettlementBase is Test {
    uint256 internal constant T0 = 1_700_000_000;
    uint256 internal constant AUTH_KEY = 0xA11CE;
    uint256 internal constant AMOUNT = 100e6;
    bytes32 internal constant ID = keccak256("escrow-1");
    bytes32 internal constant SETTLE = keccak256("settlement-record-1");
    // secp256k1 group order, for building a malleated (high-s) twin of a valid signature.
    uint256 internal constant N = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141;

    MockUSDC internal usdc;
    FreelaXEscrow internal escrow;
    address internal admin = makeAddr("admin");
    address internal pauser = makeAddr("pauser");
    address internal relayer = makeAddr("relayer");
    address internal client;
    address internal freelancer;
    address internal arbiter;
    uint256 internal clientKey;
    uint256 internal freelancerKey;
    uint256 internal arbiterKey;

    function setUp() public virtual {
        vm.warp(T0);
        (client, clientKey) = makeAddrAndKey("client");
        (freelancer, freelancerKey) = makeAddrAndKey("freelancer");
        (arbiter, arbiterKey) = makeAddrAndKey("arbiter");
        usdc = new MockUSDC();
        escrow = new FreelaXEscrow(admin, usdc, arbiter);
        vm.startPrank(admin);
        escrow.grantRole(escrow.AUTH_SIGNER_ROLE(), vm.addr(AUTH_KEY));
        escrow.grantRole(escrow.PAUSER_ROLE(), pauser);
        vm.stopPrank();
        usdc.mint(client, 100_000e6);
        vm.prank(client);
        usdc.approve(address(escrow), type(uint256).max);
    }

    function _sig(uint256 key, bytes32 digest) internal pure returns (bytes memory) {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(key, digest);
        return abi.encodePacked(r, s, v);
    }

    function fund(FreelaXEscrow on, bytes32 id) internal {
        IFreelaXEscrow.FundTerms memory t = IFreelaXEscrow.FundTerms({
            escrowId: id,
            client: client,
            freelancer: freelancer,
            token: address(usdc),
            amount: AMOUNT,
            fundingExpiresAt: uint64(vm.getBlockTimestamp() + 1 hours),
            deliveryDueAt: uint64(vm.getBlockTimestamp() + 7 days),
            reviewWindowHours: 72,
            maxRevisions: 2,
            highValueGrace: false,
            arbiter: arbiter
        });
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory sig = _sig(AUTH_KEY, on.fundDigest(t, 0, deadline));
        vm.prank(client);
        on.fund(t, deadline, sig);
    }

    function submit(bytes32 id) internal {
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(id);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes32 ev = keccak256("work");
        bytes memory sig = _sig(AUTH_KEY, escrow.submitDigest(id, ev, e.submissionCount, e.authNonce, deadline));
        vm.prank(freelancer);
        escrow.submit(id, ev, deadline, sig);
    }

    function revise(bytes32 id) internal {
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(id);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes32 fb = keccak256("fb");
        bytes memory sig = _sig(AUTH_KEY, escrow.revisionDigest(id, fb, e.revisionsUsed, e.authNonce, deadline));
        vm.prank(client);
        escrow.requestRevision(id, fb, deadline, sig);
    }

    /// Funded -> Submitted -> Disputed (by the client, inside the review window).
    function disputed(bytes32 id) internal {
        fund(escrow, id);
        submit(id);
        vm.prank(client);
        escrow.openDispute(id, keccak256("reason"));
    }

    function digestFor(FreelaXEscrow on, bytes32 id, bool toFreelancer, bytes32 hash, uint64 deadline)
        internal
        view
        returns (bytes32)
    {
        IFreelaXEscrow.Escrow memory e = on.getEscrow(id);
        return on.mutualSettlementDigest(id, toFreelancer, e.amount, hash, e.disputedAt, e.authNonce, deadline);
    }

    function pair(bytes32 id, bool toFreelancer, uint64 deadline) internal view returns (bytes memory cs, bytes memory fs) {
        bytes32 d = digestFor(escrow, id, toFreelancer, SETTLE, deadline);
        cs = _sig(clientKey, d);
        fs = _sig(freelancerKey, d);
    }

    function status(bytes32 id) internal view returns (IFreelaXEscrow.Status) {
        return escrow.getEscrow(id).status;
    }
}

contract MutualSettlementTest is SettlementBase {
    // ---- happy paths ----------------------------------------------------------------------------------------------

    function test_settle_refundToClient_bothSigned_anyoneSubmits() public {
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        uint256 before = usdc.balanceOf(client);

        vm.expectEmit(true, false, false, true, address(escrow));
        emit IFreelaXEscrow.DisputeSettledByParties(ID, false, SETTLE);
        vm.expectEmit(true, true, false, true, address(escrow));
        emit IFreelaXEscrow.Refunded(ID, client, AMOUNT, true);
        vm.prank(relayer);
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);

        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ID);
        assertEq(uint8(e.status), uint8(IFreelaXEscrow.Status.Refunded));
        assertEq(e.resolutionHash, SETTLE, "settlement record hash stored");
        assertEq(usdc.balanceOf(client), before + AMOUNT);
        assertEq(escrow.totalOpenAmount(), 0);
        assertEq(usdc.balanceOf(address(escrow)), 0);
    }

    function test_settle_releaseToFreelancer_bothSigned() public {
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        (bytes memory cs, bytes memory fs) = pair(ID, true, deadline);
        vm.expectEmit(true, true, true, true, address(escrow));
        emit IFreelaXEscrow.Released(ID, freelancer, AMOUNT, relayer);
        vm.prank(relayer);
        escrow.mutualSettleDispute(ID, true, SETTLE, deadline, cs, fs);
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Released));
        assertEq(usdc.balanceOf(freelancer), AMOUNT);
        assertEq(escrow.totalOpenAmount(), 0);
    }

    function test_settle_worksFromEveryDisputeEntry_fundedOverdue_revision_submitted_byEitherParty() public {
        bytes32 a = keccak256("a");
        bytes32 b = keccak256("b");
        bytes32 c = keccak256("c");
        fund(escrow, a);
        fund(escrow, b);
        fund(escrow, c);
        submit(b);
        revise(b);
        submit(c);
        vm.prank(freelancer);
        escrow.openDispute(b, keccak256("r"));
        vm.prank(freelancer);
        escrow.openDispute(c, keccak256("r"));
        vm.warp(vm.getBlockTimestamp() + 7 days + 1);
        vm.prank(client);
        escrow.openDispute(a, keccak256("late"));
        bytes32[3] memory ids = [a, b, c];
        for (uint256 i = 0; i < 3; i++) {
            uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
            (bytes memory cs, bytes memory fs) = pair(ids[i], i % 2 == 0, deadline);
            escrow.mutualSettleDispute(ids[i], i % 2 == 0, SETTLE, deadline, cs, fs);
        }
        assertEq(escrow.totalOpenAmount(), 0);
        assertEq(usdc.balanceOf(address(escrow)), 0);
    }

    /// F14 itself: the arbiter key is lost (the arbiter never acts) and the parties still reach a terminal state, while the
    /// default arbiter was even rotated in between (O-3: the escrow keeps its snapshot; rotation plays no part).
    function test_F14_lostArbiterKey_partiesSettle_noReassignmentInvolved() public {
        disputed(ID);
        vm.prank(admin);
        escrow.setDefaultArbiter(makeAddr("newArbiter"));
        assertEq(escrow.getEscrow(ID).arbiter, arbiter, "snapshot unchanged");
        vm.warp(vm.getBlockTimestamp() + 365 days);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Refunded));
        assertEq(escrow.getEscrow(ID).arbiter, arbiter, "arbiter never reassigned");
    }

    // ---- wrong state ------------------------------------------------------------------------------------------------

    function test_refused_outsideDisputed() public {
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.UnknownEscrow.selector));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, "", "");

        fund(escrow, ID);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Funded));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);

        submit(ID);
        (cs, fs) = pair(ID, false, deadline);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Submitted));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);

        revise(ID);
        (cs, fs) = pair(ID, false, deadline);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Revision));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
    }

    function test_duplicateSettlement_refused_andTerminalNeverChanges() public {
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Refunded));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
        // a fresh pair for the opposite outcome, signed after settlement, cannot flip it either
        (bytes memory cs2, bytes memory fs2) = pair(ID, true, deadline);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Refunded));
        escrow.mutualSettleDispute(ID, true, SETTLE, deadline, cs2, fs2);
        vm.prank(arbiter);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Refunded));
        escrow.resolveDispute(ID, true, keccak256("late decision"));
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Refunded));
    }

    // ---- arbiter authority preserved ------------------------------------------------------------------------------

    function test_arbiterKeepsAuthority_resolutionFirstBlocksSettlement() public {
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        vm.prank(arbiter);
        escrow.resolveDispute(ID, true, keccak256("decision"));
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Released));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
        assertEq(usdc.balanceOf(freelancer), AMOUNT, "the arbiter decision stands");
    }

    // ---- wrong parties / unilateral attempts ------------------------------------------------------------------------

    function test_wrongParties_refused() public {
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes32 d = digestFor(escrow, ID, false, SETTLE, deadline);
        bytes memory cs = _sig(clientKey, d);
        bytes memory fs = _sig(freelancerKey, d);
        bytes memory arb = _sig(arbiterKey, d);
        bytes memory auth = _sig(AUTH_KEY, d);
        bytes memory stranger = _sig(0xBAD, d);

        bytes4 bad = IFreelaXEscrow.InvalidAuthorization.selector;
        // unilateral: one party twice
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, cs);
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, fs, fs);
        // swapped positions
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, fs, cs);
        // the arbiter, the backend authorization signer or a stranger cannot stand in for a party
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, arb);
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, arb, fs);
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, auth);
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, stranger, fs);
        // empty or garbage signatures
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, "");
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, hex"0102", fs);
        // even the admin (DEFAULT_ADMIN_ROLE) submitting a valid client signature alone gets nothing
        vm.prank(admin);
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, auth);
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Disputed), "nothing moved");
        assertEq(usdc.balanceOf(address(escrow)), AMOUNT);
    }

    // ---- message binding ------------------------------------------------------------------------------------------

    function test_signaturesBoundToOutcomeHashDeadlineAndAgreement() public {
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        bytes4 bad = IFreelaXEscrow.InvalidAuthorization.selector;
        // the freelancer agreed to a refund; nobody can turn it into a release (or the reverse)
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, true, SETTLE, deadline, cs, fs);
        // different settlement record
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, keccak256("other record"), deadline, cs, fs);
        // different deadline
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline + 1, cs, fs);
        // parties disagree: client signs refund, freelancer signs release
        (, bytes memory fsRelease) = pair(ID, true, deadline);
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fsRelease);
        vm.expectRevert(abi.encodeWithSelector(bad));
        escrow.mutualSettleDispute(ID, true, SETTLE, deadline, cs, fsRelease);
        // zero settlement hash refused before any signature check
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.ZeroHash.selector));
        escrow.mutualSettleDispute(ID, false, bytes32(0), deadline, cs, fs);
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Disputed));
    }

    function test_amountIsPartOfTheSignedMessage() public {
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ID);
        bytes32 wrongAmount =
            escrow.mutualSettlementDigest(ID, false, AMOUNT - 1, SETTLE, e.disputedAt, e.authNonce, deadline);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, _sig(clientKey, wrongAmount), _sig(freelancerKey, wrongAmount));
    }

    function test_expiredDeadline_refused() public {
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 100);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        vm.warp(vm.getBlockTimestamp() + 101);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.AuthorizationExpired.selector));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
        vm.warp(vm.getBlockTimestamp() - 1); // exactly at the deadline: accepted (same `>` rule as mutualRefund)
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
    }

    // ---- replay -----------------------------------------------------------------------------------------------------

    /// A pair signed before the dispute existed (disputedAt = 0) is not valid for the dispute that follows.
    function test_replay_preDisputeSignaturesRefused() public {
        fund(escrow, ID);
        submit(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 30 days);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline); // disputedAt is 0 here
        vm.warp(vm.getBlockTimestamp() + 1);
        vm.prank(client);
        escrow.openDispute(ID, keccak256("reason"));
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
    }

    /// Same parties, same amount, two escrows: a pair for one never settles the other.
    function test_replay_acrossEscrowsRefused() public {
        bytes32 other = keccak256("escrow-2");
        disputed(ID);
        disputed(other);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualSettleDispute(other, false, SETTLE, deadline, cs, fs);
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
        assertEq(uint8(status(other)), uint8(IFreelaXEscrow.Status.Disputed), "the other escrow is untouched");
        assertEq(escrow.totalOpenAmount(), AMOUNT);
    }

    /// Same escrow id on another deployment (or chain): the EIP-712 domain binds the contract and chain id.
    function test_replay_acrossContractsAndChainsRefused() public {
        FreelaXEscrow twin = new FreelaXEscrow(admin, usdc, arbiter);
        vm.startPrank(admin);
        twin.grantRole(twin.AUTH_SIGNER_ROLE(), vm.addr(AUTH_KEY));
        vm.stopPrank();
        vm.prank(client);
        usdc.approve(address(twin), type(uint256).max);
        disputed(ID);
        fund(twin, ID);
        vm.warp(vm.getBlockTimestamp() + 7 days + 1);
        vm.prank(client);
        twin.openDispute(ID, keccak256("late"));
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes32 d = digestFor(escrow, ID, false, SETTLE, deadline);
        bytes memory cs = _sig(clientKey, d);
        bytes memory fs = _sig(freelancerKey, d);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        twin.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);

        assertEq(vm.getChainId(), 31337, "signed on the local chain id");
        vm.chainId(43113);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
        vm.chainId(31337);
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
    }

    /// Cross-type replay: a MutualRefund pair is never a MutualSettlement pair, and vice versa.
    function test_replay_crossTypeRefused() public {
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ID);
        bytes32 refundDigest = escrow.mutualRefundDigest(ID, e.authNonce, deadline);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, _sig(clientKey, refundDigest), _sig(freelancerKey, refundDigest));
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Disputed));
        escrow.mutualRefund(ID, deadline, cs, fs);
    }

    /// Malleated (high-s) twin of a valid signature is refused (OpenZeppelin ECDSA), so a signature cannot be "re-used"
    /// in a second encoding.
    function test_malleatedSignatureRefused() public {
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes32 d = digestFor(escrow, ID, false, SETTLE, deadline);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(clientKey, d);
        bytes memory highS = abi.encodePacked(r, bytes32(N - uint256(s)), v == 27 ? uint8(28) : uint8(27));
        bytes memory fs = _sig(freelancerKey, d);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, highS, fs);
    }

    // ---- pause, blacklist (O-6), accounting -----------------------------------------------------------------------

    function test_pauseDoesNotBlockSettlement() public {
        disputed(ID);
        vm.prank(pauser);
        escrow.pause();
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Refunded));
    }

    /// O-6 stuck-and-reported: a blacklisted recipient makes the whole settlement revert; nothing is rerouted and the
    /// escrow stays Disputed with its funds and its accounting intact. The parties may then choose the other outcome.
    function test_blacklistedRecipient_staysDisputed_noReroute() public {
        disputed(ID);
        usdc.setBlacklisted(client, true);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        (bytes memory cs, bytes memory fs) = pair(ID, false, deadline);
        vm.expectRevert(abi.encodeWithSelector(MockUSDC.Blacklisted.selector, client));
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ID);
        assertEq(uint8(e.status), uint8(IFreelaXEscrow.Status.Disputed));
        assertEq(e.resolutionHash, bytes32(0));
        assertEq(escrow.totalOpenAmount(), AMOUNT);
        assertEq(usdc.balanceOf(address(escrow)), AMOUNT);
        // the same signatures stay valid only for the same outcome; the nonce did not move
        usdc.setBlacklisted(client, false);
        escrow.mutualSettleDispute(ID, false, SETTLE, deadline, cs, fs);
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Refunded));
    }

    function test_settlementTouchesOnlyItsOwnEscrow() public {
        bytes32 other = keccak256("escrow-2");
        disputed(ID);
        fund(escrow, other);
        IFreelaXEscrow.Escrow memory beforeOther = escrow.getEscrow(other);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        (bytes memory cs, bytes memory fs) = pair(ID, true, deadline);
        escrow.mutualSettleDispute(ID, true, SETTLE, deadline, cs, fs);
        assertEq(keccak256(abi.encode(escrow.getEscrow(other))), keccak256(abi.encode(beforeOther)));
        assertEq(escrow.totalOpenAmount(), AMOUNT);
        assertEq(usdc.balanceOf(address(escrow)), AMOUNT);
    }

    function testFuzz_onlyTheTwoPartyKeysSettle(uint256 k1, uint256 k2, bool toFreelancer) public {
        k1 = bound(k1, 1, N - 1);
        k2 = bound(k2, 1, N - 1);
        disputed(ID);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes32 d = digestFor(escrow, ID, toFreelancer, SETTLE, deadline);
        bool valid = vm.addr(k1) == client && vm.addr(k2) == freelancer;
        if (!valid) vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualSettleDispute(ID, toFreelancer, SETTLE, deadline, _sig(k1, d), _sig(k2, d));
    }
}

/// Reentrancy: a token that calls back into the escrow during the settlement transfer is stopped by the guard.
contract ReentrantSettleToken is ERC20 {
    FreelaXEscrow internal target;
    bytes internal payload;
    bool public reentryBlocked;

    constructor() ERC20("Reentrant", "RE") {}

    function mint(address to, uint256 amount) external {
        _mint(to, amount);
    }

    function arm(FreelaXEscrow t, bytes memory p) external {
        target = t;
        payload = p;
    }

    function _update(address from, address to, uint256 value) internal override {
        super._update(from, to, value);
        if (address(target) != address(0) && from == address(target)) {
            FreelaXEscrow t = target;
            target = FreelaXEscrow(address(0));
            (bool ok,) = address(t).call(payload);
            reentryBlocked = !ok;
        }
    }
}

contract MutualSettlementReentrancyTest is Test {
    function test_reentryDuringSettlementTransferIsBlocked() public {
        vm.warp(1_700_000_000);
        uint256 authKey = 0xA11CE;
        (address client, uint256 ck) = makeAddrAndKey("client");
        (address freelancer, uint256 fk) = makeAddrAndKey("freelancer");
        address arbiter = makeAddr("arbiter");
        address admin = makeAddr("admin");
        ReentrantSettleToken tok = new ReentrantSettleToken();
        FreelaXEscrow esc = new FreelaXEscrow(admin, tok, arbiter);
        vm.startPrank(admin);
        esc.grantRole(esc.AUTH_SIGNER_ROLE(), vm.addr(authKey));
        vm.stopPrank();
        tok.mint(client, 1_000e6);
        vm.prank(client);
        tok.approve(address(esc), type(uint256).max);
        bytes32 id = keccak256("r");
        IFreelaXEscrow.FundTerms memory t = IFreelaXEscrow.FundTerms(
            id, client, freelancer, address(tok), 10e6, uint64(vm.getBlockTimestamp() + 1 hours), uint64(vm.getBlockTimestamp() + 7 days), 72, 2, false, arbiter
        );
        uint64 dl = uint64(vm.getBlockTimestamp() + 1 hours);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(authKey, esc.fundDigest(t, 0, dl));
        vm.prank(client);
        esc.fund(t, dl, abi.encodePacked(r, s, v));
        vm.warp(vm.getBlockTimestamp() + 7 days + 1);
        vm.prank(client);
        esc.openDispute(id, keccak256("late"));

        IFreelaXEscrow.Escrow memory e = esc.getEscrow(id);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes32 d = esc.mutualSettlementDigest(id, false, e.amount, keccak256("s"), e.disputedAt, e.authNonce, deadline);
        (v, r, s) = vm.sign(ck, d);
        bytes memory cs = abi.encodePacked(r, s, v);
        (v, r, s) = vm.sign(fk, d);
        bytes memory fs = abi.encodePacked(r, s, v);
        tok.arm(esc, abi.encodeCall(esc.mutualSettleDispute, (id, false, keccak256("s"), deadline, cs, fs)));
        esc.mutualSettleDispute(id, false, keccak256("s"), deadline, cs, fs);
        assertTrue(tok.reentryBlocked(), "the nested call reverted");
        assertEq(tok.balanceOf(client), 1_000e6, "paid exactly once");
        assertEq(esc.totalOpenAmount(), 0);
    }
}

/// Invariants with the settlement path in the action mix (on top of the baseline invariant suite, which run.sh also re-runs
/// unchanged against this variant).
contract SettlementHandler is Test {
    FreelaXEscrow public escrow;
    MockUSDC public usdc;
    uint256 internal authKey;
    uint256 internal clientKey;
    uint256 internal freelancerKey;
    address public client;
    address public freelancer;
    address public arbiter;
    bytes32[] public ids;
    uint256 public minted;
    mapping(bytes32 => uint8) public terminalSeen;
    bool public terminalChanged;
    uint256 public settlements;

    constructor(FreelaXEscrow e, MockUSDC u, uint256 ak, address arb) {
        escrow = e;
        usdc = u;
        authKey = ak;
        arbiter = arb;
        (client, clientKey) = makeAddrAndKey("client");
        (freelancer, freelancerKey) = makeAddrAndKey("freelancer");
        usdc.mint(client, 1_000_000e6);
        minted = 1_000_000e6;
        vm.prank(client);
        usdc.approve(address(e), type(uint256).max);
    }

    function idsLength() external view returns (uint256) {
        return ids.length;
    }

    function _sig(uint256 key, bytes32 d) internal pure returns (bytes memory) {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(key, d);
        return abi.encodePacked(r, s, v);
    }

    function _pick(uint256 seed) internal view returns (bytes32) {
        return ids[seed % ids.length];
    }

    function _observe() internal {
        for (uint256 i = 0; i < ids.length; i++) {
            uint8 s = uint8(escrow.getEscrow(ids[i]).status);
            if (s == uint8(IFreelaXEscrow.Status.Released) || s == uint8(IFreelaXEscrow.Status.Refunded)) {
                if (terminalSeen[ids[i]] == 0) terminalSeen[ids[i]] = s;
                else if (terminalSeen[ids[i]] != s) terminalChanged = true;
            }
        }
    }

    function fund(uint256 amountSeed) external {
        bytes32 id = keccak256(abi.encode("e", ids.length));
        IFreelaXEscrow.FundTerms memory t = IFreelaXEscrow.FundTerms(
            id, client, freelancer, address(usdc), bound(amountSeed, 1, 2_000e6), uint64(vm.getBlockTimestamp() + 1 hours),
            uint64(vm.getBlockTimestamp() + 7 days), 72, 2, false, arbiter
        );
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory sig = _sig(authKey, escrow.fundDigest(t, 0, deadline));
        vm.prank(client);
        try escrow.fund(t, deadline, sig) {
            ids.push(id);
        } catch {}
        _observe();
    }

    function submit(uint256 seed) external {
        if (ids.length == 0) return;
        bytes32 id = _pick(seed);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(id);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory sig = _sig(authKey, escrow.submitDigest(id, keccak256("w"), e.submissionCount, e.authNonce, deadline));
        vm.prank(freelancer);
        try escrow.submit(id, keccak256("w"), deadline, sig) {} catch {}
        _observe();
    }

    function dispute(uint256 seed, bool byClient) external {
        if (ids.length == 0) return;
        vm.prank(byClient ? client : freelancer);
        try escrow.openDispute(_pick(seed), keccak256("reason")) {} catch {}
        _observe();
    }

    function fundSubmitDispute(uint256 amountSeed, bool byClient) external {
        uint256 before = ids.length;
        this.fund(amountSeed);
        if (ids.length == before) return;
        this.submit(ids.length - 1);
        this.dispute(ids.length - 1, byClient);
    }

    function resolve(uint256 seed, bool toFreelancer) external {
        if (ids.length == 0) return;
        vm.prank(arbiter);
        try escrow.resolveDispute(_pick(seed), toFreelancer, keccak256("decision")) {} catch {}
        _observe();
    }

    function release(uint256 seed) external {
        if (ids.length == 0) return;
        vm.prank(client);
        try escrow.release(_pick(seed)) {} catch {}
        _observe();
    }

    function mutualRefund(uint256 seed) external {
        if (ids.length == 0) return;
        bytes32 id = _pick(seed);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes32 d = escrow.mutualRefundDigest(id, escrow.getEscrow(id).authNonce, deadline);
        try escrow.mutualRefund(id, deadline, _sig(clientKey, d), _sig(freelancerKey, d)) {} catch {}
        _observe();
    }

    /// Valid pair, a disagreeing pair, or a one-party pair, chosen by the seed.
    function settle(uint256 seed, bool toFreelancer, uint8 mode) external {
        if (ids.length == 0) return;
        bytes32 id = _pick(seed);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(id);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes32 d = escrow.mutualSettlementDigest(id, toFreelancer, e.amount, keccak256("s"), e.disputedAt, e.authNonce, deadline);
        bytes32 other =
            escrow.mutualSettlementDigest(id, !toFreelancer, e.amount, keccak256("s"), e.disputedAt, e.authNonce, deadline);
        bytes memory cs = _sig(clientKey, d);
        bytes memory fs = mode % 3 == 0 ? _sig(freelancerKey, d) : (mode % 3 == 1 ? _sig(freelancerKey, other) : cs);
        try escrow.mutualSettleDispute(id, toFreelancer, keccak256("s"), deadline, cs, fs) {
            settlements++;
        } catch {}
        _observe();
    }

    function warp(uint256 secs) external {
        vm.warp(vm.getBlockTimestamp() + bound(secs, 0, 4 days));
        _observe();
    }

    function openSum() external view returns (uint256 sum) {
        for (uint256 i = 0; i < ids.length; i++) {
            IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ids[i]);
            if (e.status != IFreelaXEscrow.Status.Released && e.status != IFreelaXEscrow.Status.Refunded) sum += e.amount;
        }
    }

    function depositedSum() external view returns (uint256 sum) {
        for (uint256 i = 0; i < ids.length; i++) sum += escrow.getEscrow(ids[i]).amount;
    }
}

contract MutualSettlementInvariantTest is StdInvariant, Test {
    MockUSDC internal usdc;
    FreelaXEscrow internal escrow;
    SettlementHandler internal handler;

    function setUp() public {
        vm.warp(1_700_000_000);
        usdc = new MockUSDC();
        address admin = makeAddr("admin");
        address arbiter = makeAddr("arbiter");
        escrow = new FreelaXEscrow(admin, usdc, arbiter);
        bytes32 role = escrow.AUTH_SIGNER_ROLE();
        vm.prank(admin);
        escrow.grantRole(role, vm.addr(0xA11CE));
        handler = new SettlementHandler(escrow, usdc, 0xA11CE, arbiter);
        targetContract(address(handler));
    }

    function invariant_accountingMatchesBalance() public view {
        assertEq(usdc.balanceOf(address(escrow)), escrow.totalOpenAmount());
        assertEq(handler.openSum(), escrow.totalOpenAmount());
    }

    function invariant_valueConserved() public view {
        assertEq(
            usdc.balanceOf(handler.client()) + usdc.balanceOf(handler.freelancer()) + usdc.balanceOf(address(escrow)),
            handler.minted()
        );
        assertLe(usdc.balanceOf(handler.freelancer()), handler.depositedSum());
    }

    function invariant_terminalStatesFinal() public view {
        assertFalse(handler.terminalChanged());
    }

    function afterInvariant() public view {
        console.log("coverage: escrows", handler.idsLength(), "successful settlements", handler.settlements());
    }
}
