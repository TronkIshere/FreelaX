// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Base} from "./Base.t.sol";
import {FreelaXEscrow} from "../src/FreelaXEscrow.sol";
import {IFreelaXEscrow} from "../src/interfaces/IFreelaXEscrow.sol";
import {MockUSDC} from "../src/mocks/MockUSDC.sol";

contract FreelaXEscrowLifecycleTest is Base {
    uint256 internal clientKey;
    uint256 internal freelancerKey;

    function setUp() public override {
        super.setUp();
        (, clientKey) = makeAddrAndKey("client");
        (, freelancerKey) = makeAddrAndKey("freelancer");
    }

    function signRevision(bytes32 id, bytes32 feedback, uint64 deadline) internal view returns (bytes memory) {
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(id);
        return sign(escrow.revisionDigest(id, feedback, e.revisionsUsed, e.authNonce, deadline));
    }

    function reviseDefault(bytes32 id, bytes32 feedback) internal {
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory sig = signRevision(id, feedback, deadline);
        vm.prank(client);
        escrow.requestRevision(id, feedback, deadline, sig);
    }

    function signMutual(uint256 key, bytes32 id, uint64 deadline) internal view returns (bytes memory) {
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(id);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(key, escrow.mutualRefundDigest(id, e.authNonce, deadline));
        return abi.encodePacked(r, s, v);
    }

    function status(bytes32 id) internal view returns (IFreelaXEscrow.Status) {
        return escrow.getEscrow(id).status;
    }

    // ---- revision ------------------------------------------------------------------------------------------------------

    function test_revision_cycleClearsTheClockResubmitsAndIsCapped() public {
        fundDefault();
        submitDefault(ID, keccak256("v1"));
        reviseDefault(ID, keccak256("fb1"));
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ID);
        assertEq(uint8(e.status), uint8(IFreelaXEscrow.Status.Revision));
        assertEq(e.reviewDueAt, 0, "the review deadline is cleared");
        assertEq(e.revisionsUsed, 1);

        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Revision));
        escrow.release(ID);

        vm.warp(vm.getBlockTimestamp() + 5 days);
        submitDefault(ID, keccak256("v2"));
        e = escrow.getEscrow(ID);
        assertEq(e.submissionCount, 2);
        assertEq(e.reviewDueAt, vm.getBlockTimestamp() + 72 hours, "a resubmission restarts the review clock");
        reviseDefault(ID, keccak256("fb2"));
        submitDefault(ID, keccak256("v3"));

        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory sig = signRevision(ID, keccak256("fb3"), deadline);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.RevisionsExhausted.selector));
        escrow.requestRevision(ID, keccak256("fb3"), deadline, sig);

        vm.prank(client);
        escrow.release(ID);
        assertEq(usdc.balanceOf(freelancer), AMOUNT);
    }

    function test_revision_reviewDeadlineBoundaryIsExclusive() public {
        fundDefault();
        submitDefault(ID, keccak256("v1"));
        uint64 due = escrow.getEscrow(ID).reviewDueAt;
        bytes32 fb = keccak256("fb");
        vm.warp(due - 1);
        uint64 deadline = uint64(due + 1 hours);
        bytes memory sig = signRevision(ID, fb, deadline);
        vm.prank(client);
        escrow.requestRevision(ID, fb, deadline, sig);

        bytes32 second = keccak256("second");
        IFreelaXEscrow.FundTerms memory t = terms(second, AMOUNT);
        t.fundingExpiresAt = uint64(vm.getBlockTimestamp() + 1 hours);
        t.deliveryDueAt = uint64(vm.getBlockTimestamp() + 7 days);
        bytes memory fundSig = signFund(t, uint64(vm.getBlockTimestamp() + 1 hours));
        vm.prank(client);
        escrow.fund(t, uint64(vm.getBlockTimestamp() + 1 hours), fundSig);
        submitDefault(second, keccak256("w"));
        uint64 secondDue = escrow.getEscrow(second).reviewDueAt;
        vm.warp(secondDue);
        uint64 dl2 = uint64(secondDue + 1 hours);
        bytes memory sig2 = signRevision(second, fb, dl2);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.TooLate.selector));
        escrow.requestRevision(second, fb, dl2, sig2);
        vm.prank(relayer);
        escrow.release(second);
    }

    function test_revision_authorizationAndRoleNegatives() public {
        fundDefault();
        submitDefault(ID, keccak256("v1"));
        bytes32 fb = keccak256("fb");
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory good = signRevision(ID, fb, deadline);
        bytes memory otherFeedback = signRevision(ID, keccak256("other"), deadline);
        bytes memory zeroSig = signRevision(ID, bytes32(0), deadline);
        bytes memory staleNonce = sign(escrow.revisionDigest(ID, fb, 0, 0, deadline));
        bytes memory wrongCounter = sign(escrow.revisionDigest(ID, fb, 1, 2, deadline));

        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.NotClient.selector));
        escrow.requestRevision(ID, fb, deadline, good);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.ZeroHash.selector));
        escrow.requestRevision(ID, bytes32(0), deadline, zeroSig);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.requestRevision(ID, fb, deadline, otherFeedback);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.requestRevision(ID, fb, deadline, staleNonce);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.requestRevision(ID, fb, deadline, wrongCounter);

        vm.prank(client);
        escrow.requestRevision(ID, fb, deadline, good);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Revision));
        escrow.requestRevision(ID, fb, deadline, good);
    }

    function test_revision_zeroMaxRevisionsMeansNoRevision() public {
        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        t.maxRevisions = 0;
        bytes memory sig = signFund(t, uint64(T0 + 1 hours));
        vm.prank(client);
        escrow.fund(t, uint64(T0 + 1 hours), sig);
        submitDefault(ID, keccak256("v1"));
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory rev = signRevision(ID, keccak256("fb"), deadline);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.RevisionsExhausted.selector));
        escrow.requestRevision(ID, keccak256("fb"), deadline, rev);
    }

    // ---- dispute --------------------------------------------------------------------------------------------------------

    function test_dispute_fromSubmittedOnlyBeforeTheReviewDeadline() public {
        fundDefault();
        submitDefault(ID, keccak256("v1"));
        uint64 due = escrow.getEscrow(ID).reviewDueAt;
        vm.warp(due);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.TooLate.selector));
        escrow.openDispute(ID, keccak256("late"));
        vm.prank(relayer);
        escrow.release(ID);
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Released), "after the deadline release is the only path");
    }

    function test_dispute_fromSubmittedJustBeforeTheDeadlineClearsTheClock() public {
        fundDefault();
        submitDefault(ID, keccak256("v1"));
        uint64 due = escrow.getEscrow(ID).reviewDueAt;
        vm.warp(due - 1);
        vm.prank(freelancer);
        vm.expectEmit(true, true, false, true, address(escrow));
        emit IFreelaXEscrow.DisputeOpened(ID, freelancer, keccak256("why"));
        escrow.openDispute(ID, keccak256("why"));
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ID);
        assertEq(uint8(e.status), uint8(IFreelaXEscrow.Status.Disputed));
        assertEq(e.disputedBy, freelancer);
        assertEq(e.disputedAt, due - 1);
        assertEq(e.reviewDueAt, 0);

        vm.warp(due + 30 days);
        vm.prank(relayer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Disputed));
        escrow.release(ID);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Disputed));
        escrow.release(ID);
    }

    function test_dispute_fromFundedOnlyAfterTheDeliveryDate() public {
        fundDefault();
        uint64 due = uint64(T0 + 7 days);
        vm.warp(due);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.TooEarly.selector));
        escrow.openDispute(ID, keccak256("early"));
        vm.warp(due + 1);
        vm.prank(client);
        escrow.openDispute(ID, keccak256("no delivery"));
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Disputed));
    }

    function test_dispute_fromRevisionAtAnyTimeAndOnlyByAParticipantWithAReason() public {
        fundDefault();
        submitDefault(ID, keccak256("v1"));
        reviseDefault(ID, keccak256("fb"));
        vm.warp(vm.getBlockTimestamp() + 400 days);
        vm.prank(stranger);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.NotParticipant.selector));
        escrow.openDispute(ID, keccak256("x"));
        vm.prank(arbiter);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.NotParticipant.selector));
        escrow.openDispute(ID, keccak256("x"));
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.ZeroHash.selector));
        escrow.openDispute(ID, bytes32(0));
        vm.prank(client);
        escrow.openDispute(ID, keccak256("stalled"));
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Disputed));
        escrow.openDispute(ID, keccak256("again"));
    }

    function test_dispute_isImpossibleOnSettledEscrows() public {
        fundDefault();
        submitDefault(ID, keccak256("v1"));
        vm.prank(client);
        escrow.release(ID);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Released));
        escrow.openDispute(ID, keccak256("late"));
    }

    // ---- resolution -----------------------------------------------------------------------------------------------------

    function _disputed() internal {
        fundDefault();
        submitDefault(ID, keccak256("v1"));
        vm.prank(client);
        escrow.openDispute(ID, keccak256("why"));
    }

    function test_resolve_arbiterReleasesToTheFreelancerOrRefundsTheClient() public {
        _disputed();
        vm.expectEmit(true, false, false, true, address(escrow));
        emit IFreelaXEscrow.DisputeResolved(ID, true, keccak256("decision"));
        vm.prank(arbiter);
        escrow.resolveDispute(ID, true, keccak256("decision"));
        assertEq(usdc.balanceOf(freelancer), AMOUNT);
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Released));
        assertEq(escrow.getEscrow(ID).resolutionHash, keccak256("decision"));
        assertEq(escrow.totalOpenAmount(), 0);

        bytes32 second = keccak256("second");
        IFreelaXEscrow.FundTerms memory t = terms(second, 40e6);
        bytes memory sig = signFund(t, uint64(T0 + 1 hours));
        uint256 before = usdc.balanceOf(client);
        vm.prank(client);
        escrow.fund(t, uint64(T0 + 1 hours), sig);
        submitDefault(second, keccak256("w"));
        vm.prank(client);
        escrow.openDispute(second, keccak256("bad"));
        vm.prank(arbiter);
        escrow.resolveDispute(second, false, keccak256("refund"));
        assertEq(usdc.balanceOf(client), before, "the client got the full deposit back");
        assertEq(uint8(status(second)), uint8(IFreelaXEscrow.Status.Refunded));
    }

    function test_resolve_onlyTheSnapshottedArbiterOnADisputedEscrowWithAHash() public {
        _disputed();
        address[5] memory others = [client, freelancer, admin, authSigner, stranger];
        for (uint256 i = 0; i < others.length; i++) {
            vm.prank(others[i]);
            vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.NotArbiter.selector));
            escrow.resolveDispute(ID, true, keccak256("x"));
        }
        vm.prank(arbiter);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.ZeroHash.selector));
        escrow.resolveDispute(ID, true, bytes32(0));
        vm.prank(arbiter);
        escrow.resolveDispute(ID, true, keccak256("d"));
        vm.prank(arbiter);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Released));
        escrow.resolveDispute(ID, false, keccak256("again"));
    }

    function test_resolve_cannotResolveANonDisputedEscrow() public {
        fundDefault();
        vm.prank(arbiter);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Funded));
        escrow.resolveDispute(ID, false, keccak256("x"));
        submitDefault(ID, keccak256("v1"));
        vm.prank(arbiter);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Submitted));
        escrow.resolveDispute(ID, true, keccak256("x"));
    }

    function test_resolve_arbiterSnapshotSurvivesDefaultArbiterRotation() public {
        _disputed();
        address newArbiter = makeAddr("rotated");
        vm.prank(admin);
        escrow.setDefaultArbiter(newArbiter);
        vm.prank(newArbiter);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.NotArbiter.selector));
        escrow.resolveDispute(ID, true, keccak256("x"));
        vm.prank(arbiter);
        escrow.resolveDispute(ID, true, keccak256("x"));
        assertEq(usdc.balanceOf(freelancer), AMOUNT);
    }

    function test_resolve_pauseDoesNotBlockDisputeOrResolution() public {
        fundDefault();
        submitDefault(ID, keccak256("v1"));
        vm.prank(pauser);
        escrow.pause();
        vm.prank(client);
        escrow.openDispute(ID, keccak256("why"));
        vm.prank(arbiter);
        escrow.resolveDispute(ID, false, keccak256("d"));
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Refunded));
    }

    function test_resolve_blacklistedRecipientLeavesTheEscrowDisputedAndRecoverable() public {
        _disputed();
        usdc.setBlacklisted(freelancer, true);
        vm.prank(arbiter);
        vm.expectRevert(abi.encodeWithSelector(MockUSDC.Blacklisted.selector, freelancer));
        escrow.resolveDispute(ID, true, keccak256("d"));
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Disputed), "stuck, not lost: state and funds unchanged");
        assertEq(usdc.balanceOf(address(escrow)), AMOUNT);
        assertEq(escrow.totalOpenAmount(), AMOUNT);

        vm.prank(arbiter);
        escrow.resolveDispute(ID, false, keccak256("refund instead"));
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Refunded), "the arbiter can still refund the client");
    }

    // ---- mutual refund --------------------------------------------------------------------------------------------------

    function test_mutualRefund_fromFundedSubmittedAndRevision() public {
        bytes32[3] memory ids = [keccak256("m1"), keccak256("m2"), keccak256("m3")];
        for (uint256 i = 0; i < 3; i++) {
            IFreelaXEscrow.FundTerms memory t = terms(ids[i], AMOUNT);
            bytes memory sig = signFund(t, uint64(T0 + 1 hours));
            vm.prank(client);
            escrow.fund(t, uint64(T0 + 1 hours), sig);
        }
        submitDefault(ids[1], keccak256("w"));
        submitDefault(ids[2], keccak256("w"));
        reviseDefault(ids[2], keccak256("fb"));

        uint256 before = usdc.balanceOf(client);
        for (uint256 i = 0; i < 3; i++) {
            uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
            bytes memory cs = signMutual(clientKey, ids[i], deadline);
            bytes memory fs = signMutual(freelancerKey, ids[i], deadline);
            vm.expectEmit(true, true, false, true, address(escrow));
            emit IFreelaXEscrow.Refunded(ids[i], client, AMOUNT, true);
            vm.prank(relayer);
            escrow.mutualRefund(ids[i], deadline, cs, fs);
            assertEq(uint8(status(ids[i])), uint8(IFreelaXEscrow.Status.Refunded));
        }
        assertEq(usdc.balanceOf(client), before + 3 * AMOUNT);
        assertEq(escrow.totalOpenAmount(), 0);
    }

    function test_mutualRefund_needsBothDistinctValidSignaturesBoundToTheEscrow() public {
        fundDefault();
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory cs = signMutual(clientKey, ID, deadline);
        bytes memory fs = signMutual(freelancerKey, ID, deadline);
        bytes memory strangerSig = signMutual(0xBAD, ID, deadline);
        bytes memory otherDeadline = signMutual(freelancerKey, ID, deadline + 1);

        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualRefund(ID, deadline, cs, cs);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualRefund(ID, deadline, fs, cs);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualRefund(ID, deadline, cs, strangerSig);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.mutualRefund(ID, deadline, cs, otherDeadline);
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Funded), "nothing moved");

        escrow.mutualRefund(ID, deadline, cs, fs);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Refunded));
        escrow.mutualRefund(ID, deadline, cs, fs);
    }

    function test_mutualRefund_expiredNotWhileDisputedAndBoundToThisContract() public {
        fundDefault();
        uint64 deadline = uint64(vm.getBlockTimestamp() + 100);
        bytes memory cs = signMutual(clientKey, ID, deadline);
        bytes memory fs = signMutual(freelancerKey, ID, deadline);
        vm.warp(vm.getBlockTimestamp() + 101);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.AuthorizationExpired.selector));
        escrow.mutualRefund(ID, deadline, cs, fs);
        vm.warp(vm.getBlockTimestamp() - 101);

        FreelaXEscrow other = new FreelaXEscrow(admin, usdc, arbiter);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.UnknownEscrow.selector));
        other.mutualRefund(ID, deadline, cs, fs);

        vm.warp(T0 + 7 days + 1);
        vm.prank(client);
        escrow.openDispute(ID, keccak256("no delivery"));
        uint64 dl = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory cs2 = signMutual(clientKey, ID, dl);
        bytes memory fs2 = signMutual(freelancerKey, ID, dl);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Disputed));
        escrow.mutualRefund(ID, dl, cs2, fs2);
    }

    function test_mutualRefund_blacklistedClientKeepsTheEscrowOpen() public {
        fundDefault();
        usdc.setBlacklisted(client, true);
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory cs = signMutual(clientKey, ID, deadline);
        bytes memory fs = signMutual(freelancerKey, ID, deadline);
        vm.expectRevert(abi.encodeWithSelector(MockUSDC.Blacklisted.selector, client));
        escrow.mutualRefund(ID, deadline, cs, fs);
        assertEq(uint8(status(ID)), uint8(IFreelaXEscrow.Status.Funded));
    }
}
