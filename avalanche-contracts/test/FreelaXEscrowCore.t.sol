// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Base} from "./Base.t.sol";
import {FreelaXEscrow} from "../src/FreelaXEscrow.sol";
import {IFreelaXEscrow} from "../src/interfaces/IFreelaXEscrow.sol";
import {MockUSDC} from "../src/mocks/MockUSDC.sol";
import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract ReentrantToken is ERC20 {
    FreelaXEscrow internal target;
    bytes32 internal id;
    bool internal armed;

    constructor() ERC20("Reentrant", "RE") {}

    function mint(address to, uint256 amount) external {
        _mint(to, amount);
    }

    function arm(FreelaXEscrow t, bytes32 escrowId) external {
        target = t;
        id = escrowId;
        armed = true;
    }

    function _update(address from, address to, uint256 value) internal override {
        super._update(from, to, value);
        if (armed && from == address(target)) {
            armed = false;
            target.release(id);
        }
    }
}

contract FreelaXEscrowCoreTest is Base {
    // ---- fund: success and state ---------------------------------------------------------------------------------------

    function test_fund_recordsTermsSnapshotsArbiterAndMovesTokens() public {
        fundDefault();
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ID);
        assertEq(uint8(e.status), uint8(IFreelaXEscrow.Status.Funded));
        assertEq(e.client, client);
        assertEq(e.freelancer, freelancer);
        assertEq(e.arbiter, arbiter, "arbiter snapshot");
        assertEq(e.amount, AMOUNT);
        assertEq(e.reviewWindowSeconds, 72 hours);
        assertEq(e.maxRevisions, 2);
        assertEq(e.authNonce, 1);
        assertEq(e.deliveryDueAt, T0 + 7 days);
        assertEq(usdc.balanceOf(address(escrow)), AMOUNT);
        assertEq(escrow.totalOpenAmount(), AMOUNT);
    }

    function test_fund_emitsFunded() public {
        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        bytes memory sig = signFund(t, uint64(T0 + 1 hours));
        vm.expectEmit(true, true, true, true, address(escrow));
        emit IFreelaXEscrow.Funded(ID, client, freelancer, arbiter, AMOUNT, uint64(T0 + 7 days), 72 hours, 2);
        vm.prank(client);
        escrow.fund(t, uint64(T0 + 1 hours), sig);
    }

    function test_fund_highValueGraceOnlyWhenRequestedAndAboveThreshold() public {
        IFreelaXEscrow.FundTerms memory t = terms(keccak256("a"), 500_000_001);
        t.highValueGrace = true;
        bytes memory sig = signFund(t, uint64(T0 + 1 hours));
        vm.prank(client);
        escrow.fund(t, uint64(T0 + 1 hours), sig);
        assertEq(escrow.getEscrow(t.escrowId).reviewWindowSeconds, 72 hours + 24 hours);

        t = terms(keccak256("b"), 500_000_000);
        t.highValueGrace = true;
        sig = signFund(t, uint64(T0 + 1 hours));
        vm.prank(client);
        escrow.fund(t, uint64(T0 + 1 hours), sig);
        assertEq(escrow.getEscrow(t.escrowId).reviewWindowSeconds, 72 hours, "exactly 500 USDC gets no grace");

        t = terms(keccak256("c"), 900_000_000);
        sig = signFund(t, uint64(T0 + 1 hours));
        vm.prank(client);
        escrow.fund(t, uint64(T0 + 1 hours), sig);
        assertEq(escrow.getEscrow(t.escrowId).reviewWindowSeconds, 72 hours, "no grace unless requested");
    }

    // ---- fund: term validation (each rule, both sides of the bound) -----------------------------------------------------

    function _fundExpectRevert(IFreelaXEscrow.FundTerms memory t, bytes memory err) internal {
        bytes memory sig = signFund(t, uint64(T0 + 1 hours));
        vm.prank(client);
        vm.expectRevert(err);
        escrow.fund(t, uint64(T0 + 1 hours), sig);
    }

    function _fundOk(IFreelaXEscrow.FundTerms memory t) internal {
        bytes memory sig = signFund(t, uint64(T0 + 1 hours));
        vm.prank(client);
        escrow.fund(t, uint64(T0 + 1 hours), sig);
    }

    function test_fund_termBoundsAreEnforced() public {
        IFreelaXEscrow.FundTerms memory t = terms(keccak256("w1"), AMOUNT);
        t.token = address(0xBEEF);
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.WrongToken.selector));

        t = terms(keccak256("w2"), 0);
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        t = terms(keccak256("w3"), uint256(type(uint128).max) + 1);
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));

        t = terms(keccak256("w4"), AMOUNT);
        t.reviewWindowHours = 23;
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        t = terms(keccak256("w5"), AMOUNT);
        t.reviewWindowHours = 169;
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        t = terms(keccak256("w6"), AMOUNT);
        t.reviewWindowHours = 24;
        _fundOk(t);
        t = terms(keccak256("w7"), AMOUNT);
        t.reviewWindowHours = 168;
        _fundOk(t);

        t = terms(keccak256("w8"), AMOUNT);
        t.maxRevisions = 3;
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        t = terms(keccak256("w9"), AMOUNT);
        t.maxRevisions = 0;
        _fundOk(t);

        t = terms(keccak256("w10"), AMOUNT);
        t.deliveryDueAt = uint64(T0);
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        t = terms(keccak256("w11"), AMOUNT);
        t.deliveryDueAt = uint64(T0 + 1);
        _fundOk(t);

        t = terms(keccak256("w12"), AMOUNT);
        t.fundingExpiresAt = uint64(T0 - 1);
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.FundingExpired.selector));
        t = terms(keccak256("w13"), AMOUNT);
        t.fundingExpiresAt = uint64(T0);
        _fundOk(t);

        t = terms(keccak256("w14"), AMOUNT);
        t.freelancer = client;
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        t = terms(keccak256("w15"), AMOUNT);
        t.freelancer = address(0);
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        t = terms(bytes32(0), AMOUNT);
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
    }

    function test_fund_arbiterMustBeTheCurrentDefaultAndDistinctFromTheParties() public {
        IFreelaXEscrow.FundTerms memory t = terms(keccak256("x1"), AMOUNT);
        t.arbiter = stranger;
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadArbiter.selector));

        vm.prank(admin);
        escrow.setDefaultArbiter(client);
        t = terms(keccak256("x2"), AMOUNT);
        t.arbiter = client;
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadArbiter.selector));

        vm.prank(admin);
        escrow.setDefaultArbiter(freelancer);
        t = terms(keccak256("x3"), AMOUNT);
        t.arbiter = freelancer;
        _fundExpectRevert(t, abi.encodeWithSelector(IFreelaXEscrow.BadArbiter.selector));
    }

    function test_fund_onlyTheClientDuplicatesAndPauseAreRefused() public {
        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        bytes memory sig = signFund(t, uint64(T0 + 1 hours));
        vm.prank(stranger);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.NotClient.selector));
        escrow.fund(t, uint64(T0 + 1 hours), sig);

        vm.prank(client);
        escrow.fund(t, uint64(T0 + 1 hours), sig);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.AlreadyExists.selector));
        escrow.fund(t, uint64(T0 + 1 hours), sig);

        vm.prank(pauser);
        escrow.pause();
        IFreelaXEscrow.FundTerms memory t2 = terms(keccak256("paused"), AMOUNT);
        bytes memory sig2 = signFund(t2, uint64(T0 + 1 hours));
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(Pausable.EnforcedPause.selector));
        escrow.fund(t2, uint64(T0 + 1 hours), sig2);
    }

    // ---- EIP-712 authorization negatives ---------------------------------------------------------------------------------

    function test_authorization_expiredDeadlineIsRefusedAtTheBoundary() public {
        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        uint64 deadline = uint64(T0 + 10);
        bytes memory sig = signFund(t, deadline);
        vm.warp(T0 + 11);
        t.fundingExpiresAt = uint64(T0 + 1 hours);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.AuthorizationExpired.selector));
        escrow.fund(t, deadline, sig);
        vm.warp(T0 + 10);
        vm.prank(client);
        escrow.fund(t, deadline, sig);
    }

    function test_authorization_wrongSignerRevokedRoleAndTamperedTermsAreRefused() public {
        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        uint64 deadline = uint64(T0 + 1 hours);

        (uint8 v, bytes32 r, bytes32 s) = vm.sign(0xBAD, escrow.fundDigest(t, 0, deadline));
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.fund(t, deadline, abi.encodePacked(r, s, v));

        bytes memory good = signFund(t, deadline);
        IFreelaXEscrow.FundTerms memory tampered = terms(ID, AMOUNT + 1);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.fund(tampered, deadline, good);

        bytes32 role = escrow.AUTH_SIGNER_ROLE();
        vm.prank(admin);
        escrow.revokeRole(role, authSigner);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.fund(t, deadline, good);
    }

    function test_authorization_highSMalleableSignatureIsRefused() public {
        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        uint64 deadline = uint64(T0 + 1 hours);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(AUTH_KEY, escrow.fundDigest(t, 0, deadline));
        uint256 n = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141;
        bytes32 highS = bytes32(n - uint256(s));
        uint8 flipped = v == 27 ? 28 : 27;
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.fund(t, deadline, abi.encodePacked(r, highS, flipped));
    }

    function test_authorization_isBoundToThisContractAndThisChain() public {
        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        uint64 deadline = uint64(T0 + 1 hours);
        bytes memory sig = signFund(t, deadline);

        FreelaXEscrow other = new FreelaXEscrow(admin, usdc, arbiter);
        bytes32 signerRole = other.AUTH_SIGNER_ROLE();
        vm.prank(admin);
        other.grantRole(signerRole, authSigner);
        vm.startPrank(client);
        usdc.approve(address(other), type(uint256).max);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        other.fund(t, deadline, sig);
        vm.stopPrank();

        vm.chainId(43113);
        FreelaXEscrow onFuji = new FreelaXEscrow(admin, usdc, arbiter);
        assertTrue(onFuji.domainSeparator() != escrow.domainSeparator(), "chain id is part of the domain");
        bytes32 fujiRole = onFuji.AUTH_SIGNER_ROLE();
        vm.prank(admin);
        onFuji.grantRole(fujiRole, authSigner);
        vm.startPrank(client);
        usdc.approve(address(onFuji), type(uint256).max);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        onFuji.fund(t, deadline, sig);
        vm.stopPrank();
    }

    function test_authorization_independentTypedDataDigestMatchesTheContract() public view {
        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        uint64 deadline = uint64(T0 + 1 hours);
        string memory json = string.concat(
            '{"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],',
            '"FundAuthorization":[{"name":"escrowId","type":"bytes32"},{"name":"client","type":"address"},{"name":"freelancer","type":"address"},{"name":"token","type":"address"},{"name":"amount","type":"uint256"},{"name":"fundingExpiresAt","type":"uint64"},{"name":"deliveryDueAt","type":"uint64"},{"name":"reviewWindowHours","type":"uint32"},{"name":"maxRevisions","type":"uint8"},{"name":"highValueGrace","type":"bool"},{"name":"arbiter","type":"address"},{"name":"nonce","type":"uint64"},{"name":"deadline","type":"uint64"}]},',
            '"primaryType":"FundAuthorization","domain":{"name":"FreelaXEscrow","version":"1","chainId":',
            vm.toString(block.chainid),
            ',"verifyingContract":"',
            vm.toString(address(escrow)),
            '"},"message":{"escrowId":"',
            vm.toString(t.escrowId),
            '","client":"',
            vm.toString(t.client),
            '","freelancer":"',
            vm.toString(t.freelancer),
            '","token":"',
            vm.toString(t.token),
            '","amount":"',
            vm.toString(t.amount),
            '","fundingExpiresAt":',
            vm.toString(uint256(t.fundingExpiresAt)),
            ',"deliveryDueAt":',
            vm.toString(uint256(t.deliveryDueAt)),
            ',"reviewWindowHours":',
            vm.toString(uint256(t.reviewWindowHours)),
            ',"maxRevisions":',
            vm.toString(uint256(t.maxRevisions)),
            ',"highValueGrace":false,"arbiter":"',
            vm.toString(t.arbiter),
            '","nonce":0,"deadline":',
            vm.toString(uint256(deadline)),
            "}}"
        );
        assertEq(vm.eip712HashTypedData(json), escrow.fundDigest(t, 0, deadline));
    }

    // ---- extension ------------------------------------------------------------------------------------------------------

    function test_extension_boundsRolesAndOnlyOnce() public {
        fundDefault();
        uint64 original = uint64(T0 + 7 days);

        vm.prank(stranger);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.NotFreelancer.selector));
        escrow.requestExtension(ID, original + 1 days);
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        escrow.requestExtension(ID, original);
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        escrow.requestExtension(ID, original + 7 days + 1);

        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        escrow.approveExtension(ID);

        vm.prank(freelancer);
        escrow.requestExtension(ID, original + 7 days);
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        escrow.requestExtension(ID, original + 1 days);

        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.NotClient.selector));
        escrow.approveExtension(ID);
        vm.prank(client);
        escrow.approveExtension(ID);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ID);
        assertEq(e.deliveryDueAt, original + 7 days);
        assertEq(e.originalDeliveryDueAt, original);
        assertTrue(e.extensionUsed);

        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.BadTerms.selector));
        escrow.requestExtension(ID, original + 2 days);
    }

    function test_extension_onlyBeforeTheOriginalDeliveryDate() public {
        fundDefault();
        uint64 original = uint64(T0 + 7 days);
        vm.warp(original - 1);
        vm.prank(freelancer);
        escrow.requestExtension(ID, original + 1 days);
        vm.warp(original);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.TooLate.selector));
        escrow.approveExtension(ID);

        bytes32 other = keccak256("late");
        IFreelaXEscrow.FundTerms memory t = terms(other, AMOUNT);
        t.deliveryDueAt = uint64(original + 30 days);
        t.fundingExpiresAt = uint64(original + 1 hours);
        bytes memory sig = signFund(t, uint64(original + 1 hours));
        vm.prank(client);
        escrow.fund(t, uint64(original + 1 hours), sig);
        vm.warp(t.deliveryDueAt);
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.TooLate.selector));
        escrow.requestExtension(other, t.deliveryDueAt + 1 days);
    }

    // ---- submit ---------------------------------------------------------------------------------------------------------

    function test_submit_startsTheReviewClockAndBindsTheEvidence() public {
        fundDefault();
        vm.warp(T0 + 1 days);
        submitDefault(ID, keccak256("work"));
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ID);
        assertEq(uint8(e.status), uint8(IFreelaXEscrow.Status.Submitted));
        assertEq(e.submissionCount, 1);
        assertEq(e.submissionHash, keccak256("work"));
        assertEq(e.reviewDueAt, T0 + 1 days + 72 hours);
        assertEq(e.authNonce, 2);
    }

    function test_submit_deliveryDeadlineBoundaryIsInclusive() public {
        fundDefault();
        uint64 due = uint64(T0 + 7 days);
        bytes32 evidence = keccak256("work");
        vm.warp(due + 1);
        bytes memory late = sign(escrow.submitDigest(ID, evidence, 0, 1, uint64(due + 1 hours)));
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.TooLate.selector));
        escrow.submit(ID, evidence, uint64(due + 1 hours), late);
        vm.warp(due);
        vm.prank(freelancer);
        escrow.submit(ID, evidence, uint64(due + 1 hours), late);
    }

    function test_submit_authorizationNegativesAndReplay() public {
        fundDefault();
        bytes32 evidence = keccak256("work");
        uint64 deadline = uint64(T0 + 1 hours);

        bytes memory okSig = signSubmit(ID, evidence, deadline);
        bytes memory zeroSig = signSubmit(ID, bytes32(0), deadline);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.NotFreelancer.selector));
        escrow.submit(ID, evidence, deadline, okSig);
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.ZeroHash.selector));
        escrow.submit(ID, bytes32(0), deadline, zeroSig);

        bytes memory forOtherEvidence = signSubmit(ID, keccak256("other"), deadline);
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.submit(ID, evidence, deadline, forOtherEvidence);

        bytes memory staleNonce = sign(escrow.submitDigest(ID, evidence, 0, 0, deadline));
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.submit(ID, evidence, deadline, staleNonce);

        bytes memory forFund = signFund(terms(ID, AMOUNT), deadline);
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.submit(ID, evidence, deadline, forFund);

        bytes memory sig = signSubmit(ID, evidence, deadline);
        vm.prank(freelancer);
        escrow.submit(ID, evidence, deadline, sig);
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Submitted));
        escrow.submit(ID, evidence, deadline, sig);
    }

    function test_submit_authorizationIsBoundToTheEscrowId() public {
        fundDefault();
        bytes32 second = keccak256("escrow-2");
        IFreelaXEscrow.FundTerms memory t = terms(second, AMOUNT);
        bytes memory fundSig = signFund(t, uint64(T0 + 1 hours));
        vm.prank(client);
        escrow.fund(t, uint64(T0 + 1 hours), fundSig);

        bytes32 evidence = keccak256("work");
        uint64 deadline = uint64(T0 + 1 hours);
        bytes memory forFirst = signSubmit(ID, evidence, deadline);
        vm.prank(freelancer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidAuthorization.selector));
        escrow.submit(second, evidence, deadline, forFirst);
    }

    // ---- release --------------------------------------------------------------------------------------------------------

    function test_release_clientAnytimeAnyoneAfterTheReviewWindow() public {
        fundDefault();
        submitDefault(ID, keccak256("work"));
        uint64 reviewDue = escrow.getEscrow(ID).reviewDueAt;

        vm.prank(relayer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.TooEarly.selector));
        escrow.release(ID);
        vm.warp(reviewDue - 1);
        vm.prank(relayer);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.TooEarly.selector));
        escrow.release(ID);

        vm.warp(reviewDue);
        vm.expectEmit(true, true, true, true, address(escrow));
        emit IFreelaXEscrow.Released(ID, freelancer, AMOUNT, relayer);
        vm.prank(relayer);
        escrow.release(ID);
        assertEq(usdc.balanceOf(freelancer), AMOUNT);
        assertEq(usdc.balanceOf(address(escrow)), 0);
        assertEq(escrow.totalOpenAmount(), 0);
        assertEq(uint8(escrow.getEscrow(ID).status), uint8(IFreelaXEscrow.Status.Released));
    }

    function test_release_clientMayReleaseBeforeTheDeadlineAndNeverTwice() public {
        fundDefault();
        submitDefault(ID, keccak256("work"));
        vm.prank(client);
        escrow.release(ID);
        assertEq(usdc.balanceOf(freelancer), AMOUNT);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Released));
        escrow.release(ID);
    }

    function test_release_requiresASubmittedEscrow() public {
        fundDefault();
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.InvalidState.selector, IFreelaXEscrow.Status.Funded));
        escrow.release(ID);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.UnknownEscrow.selector));
        escrow.release(keccak256("nothing"));
    }

    function test_pauseBlocksOnlyFundingNotReleaseOrExtension() public {
        fundDefault();
        vm.prank(freelancer);
        escrow.requestExtension(ID, uint64(T0 + 8 days));
        submitDefault(ID, keccak256("work"));
        vm.prank(pauser);
        escrow.pause();
        vm.prank(client);
        escrow.release(ID);
        assertEq(usdc.balanceOf(freelancer), AMOUNT, "release works while paused");
    }

    // ---- roles ----------------------------------------------------------------------------------------------------------

    function test_roles_eachCapabilityIsSeparate() public {
        vm.prank(authSigner);
        vm.expectRevert();
        escrow.pause();
        vm.prank(stranger);
        vm.expectRevert();
        escrow.pause();
        vm.prank(pauser);
        escrow.pause();
        vm.prank(pauser);
        vm.expectRevert();
        escrow.unpause();
        vm.prank(authSigner);
        vm.expectRevert();
        escrow.unpause();
        vm.prank(admin);
        escrow.unpause();

        bytes32 signerRole = escrow.AUTH_SIGNER_ROLE();
        bytes32 pauserRole = escrow.PAUSER_ROLE();
        vm.prank(pauser);
        vm.expectRevert();
        escrow.grantRole(signerRole, stranger);
        vm.prank(authSigner);
        vm.expectRevert();
        escrow.grantRole(pauserRole, authSigner);
        vm.prank(authSigner);
        vm.expectRevert();
        escrow.setDefaultArbiter(authSigner);
        vm.prank(pauser);
        vm.expectRevert();
        escrow.setDefaultArbiter(pauser);
    }

    function test_roles_changingTheDefaultArbiterNeverTouchesAnExistingEscrow() public {
        fundDefault();
        address newArbiter = makeAddr("new-arbiter");
        vm.prank(admin);
        escrow.setDefaultArbiter(newArbiter);
        assertEq(escrow.getEscrow(ID).arbiter, arbiter, "existing escrow keeps its snapshot");

        IFreelaXEscrow.FundTerms memory stale = terms(keccak256("after"), AMOUNT);
        bytes memory sig = signFund(stale, uint64(T0 + 1 hours));
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.BadArbiter.selector));
        escrow.fund(stale, uint64(T0 + 1 hours), sig);

        IFreelaXEscrow.FundTerms memory fresh = terms(keccak256("after2"), AMOUNT);
        fresh.arbiter = newArbiter;
        _fundOk(fresh);
        assertEq(escrow.getEscrow(fresh.escrowId).arbiter, newArbiter);
        vm.prank(admin);
        vm.expectRevert(abi.encodeWithSelector(IFreelaXEscrow.BadArbiter.selector));
        escrow.setDefaultArbiter(address(0));
    }

    function test_noRoleCanReachEscrowedFundsAndDirectTransfersAreNotClaimable() public {
        fundDefault();
        vm.prank(authSigner);
        usdc.mint(authSigner, 50e6);
        vm.prank(authSigner);
        usdc.transfer(address(escrow), 50e6);
        assertEq(usdc.balanceOf(address(escrow)), AMOUNT + 50e6);
        assertEq(escrow.totalOpenAmount(), AMOUNT, "direct sends are not part of any escrow");

        submitDefault(ID, keccak256("work"));
        vm.prank(client);
        escrow.release(ID);
        assertEq(usdc.balanceOf(freelancer), AMOUNT, "payout never exceeds the deposit");
        assertEq(usdc.balanceOf(address(escrow)), 50e6);
    }

    // ---- token failure modes (O-6 evidence: blacklist and issuer pause) -------------------------------------------------

    function test_usdc_blacklistedFreelancerBlocksReleaseWithoutLosingTheEscrow() public {
        fundDefault();
        submitDefault(ID, keccak256("work"));
        usdc.setBlacklisted(freelancer, true);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(MockUSDC.Blacklisted.selector, freelancer));
        escrow.release(ID);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ID);
        assertEq(uint8(e.status), uint8(IFreelaXEscrow.Status.Submitted), "state unchanged: the escrow is stuck, not lost");
        assertEq(escrow.totalOpenAmount(), AMOUNT);
        assertEq(usdc.balanceOf(address(escrow)), AMOUNT);

        usdc.setBlacklisted(freelancer, false);
        vm.prank(client);
        escrow.release(ID);
        assertEq(usdc.balanceOf(freelancer), AMOUNT);
    }

    function test_usdc_blacklistedClientCannotFundAndIssuerPauseBlocksBoth() public {
        usdc.setBlacklisted(client, true);
        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        bytes memory sig = signFund(t, uint64(T0 + 1 hours));
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(MockUSDC.Blacklisted.selector, client));
        escrow.fund(t, uint64(T0 + 1 hours), sig);
        assertEq(uint8(escrow.getEscrow(ID).status), uint8(IFreelaXEscrow.Status.None), "a failed transfer leaves no escrow behind");

        usdc.setBlacklisted(client, false);
        fundDefault();
        submitDefault(ID, keccak256("work"));
        usdc.setPaused(true);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(MockUSDC.TokenPaused.selector));
        escrow.release(ID);
        assertEq(uint8(escrow.getEscrow(ID).status), uint8(IFreelaXEscrow.Status.Submitted));
    }

    function test_usdc_fundWithoutApprovalOrBalanceReverts() public {
        vm.prank(client);
        usdc.approve(address(escrow), 0);
        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        bytes memory sig = signFund(t, uint64(T0 + 1 hours));
        vm.prank(client);
        vm.expectRevert();
        escrow.fund(t, uint64(T0 + 1 hours), sig);
    }

    // ---- reentrancy -----------------------------------------------------------------------------------------------------

    function test_release_isProtectedAgainstReentrancyFromTheToken() public {
        ReentrantToken bad = new ReentrantToken();
        FreelaXEscrow guarded = new FreelaXEscrow(admin, bad, arbiter);
        bytes32 guardedRole = guarded.AUTH_SIGNER_ROLE();
        vm.prank(admin);
        guarded.grantRole(guardedRole, authSigner);
        bad.mint(client, 1000e6);
        vm.prank(client);
        bad.approve(address(guarded), type(uint256).max);

        IFreelaXEscrow.FundTerms memory t = terms(ID, AMOUNT);
        t.token = address(bad);
        uint64 deadline = uint64(T0 + 1 hours);
        bytes memory fundSig = sign(guarded.fundDigest(t, 0, deadline));
        vm.prank(client);
        guarded.fund(t, deadline, fundSig);
        bytes memory submitSig = sign(guarded.submitDigest(ID, keccak256("work"), 0, 1, deadline));
        vm.prank(freelancer);
        guarded.submit(ID, keccak256("work"), deadline, submitSig);

        bad.arm(guarded, ID);
        vm.prank(client);
        vm.expectRevert(abi.encodeWithSelector(ReentrancyGuard.ReentrancyGuardReentrantCall.selector));
        guarded.release(ID);
        assertEq(uint8(guarded.getEscrow(ID).status), uint8(IFreelaXEscrow.Status.Submitted));
    }

    // ---- fuzz -----------------------------------------------------------------------------------------------------------

    function testFuzz_fundSubmitReleasePaysExactlyTheDeposit(uint96 rawAmount, uint8 rawHours, uint32 rawWait) public {
        uint256 amount = bound(uint256(rawAmount), 1, 50_000e6);
        uint32 hours_ = uint32(bound(uint256(rawHours), 24, 168));
        uint256 wait = bound(uint256(rawWait), 0, 3 days);
        IFreelaXEscrow.FundTerms memory t = terms(keccak256(abi.encode(rawAmount, rawHours)), amount);
        t.reviewWindowHours = hours_;
        _fundOk(t);
        vm.warp(T0 + wait);
        uint64 deadline = uint64(block.timestamp + 1 hours);
        IFreelaXEscrow.Escrow memory e0 = escrow.getEscrow(t.escrowId);
        bytes memory sig = sign(escrow.submitDigest(t.escrowId, keccak256("w"), e0.submissionCount, e0.authNonce, deadline));
        vm.prank(freelancer);
        escrow.submit(t.escrowId, keccak256("w"), deadline, sig);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(t.escrowId);
        assertEq(e.reviewDueAt, block.timestamp + uint256(hours_) * 1 hours);
        vm.warp(e.reviewDueAt);
        vm.prank(relayer);
        escrow.release(t.escrowId);
        assertEq(usdc.balanceOf(freelancer), amount);
        assertEq(usdc.balanceOf(address(escrow)), 0);
    }
}
