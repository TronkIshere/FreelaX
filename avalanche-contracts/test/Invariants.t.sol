// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test, console} from "forge-std/Test.sol";
import {StdInvariant} from "forge-std/StdInvariant.sol";
import {FreelaXEscrow} from "../src/FreelaXEscrow.sol";
import {IFreelaXEscrow} from "../src/interfaces/IFreelaXEscrow.sol";
import {MockUSDC} from "../src/mocks/MockUSDC.sol";

/// Random sequences of every operation (including time travel and failed attempts). Reverts are expected and ignored; the
/// invariants below must hold after every sequence.
contract EscrowHandler is Test {
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
    mapping(bytes32 id => uint8 status) public terminalSeen;
    bool public terminalChanged;

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

    function _sign(uint256 key, bytes32 digest) internal pure returns (bytes memory) {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(key, digest);
        return abi.encodePacked(r, s, v);
    }

    function _pick(uint256 seed) internal view returns (bytes32) {
        return ids[seed % ids.length];
    }

    function _observe() internal {
        for (uint256 i = 0; i < ids.length; i++) {
            uint8 s = uint8(escrow.getEscrow(ids[i]).status);
            if (s == uint8(IFreelaXEscrow.Status.Released) || s == uint8(IFreelaXEscrow.Status.Refunded)) {
                if (terminalSeen[ids[i]] == 0) {
                    terminalSeen[ids[i]] = s;
                } else if (terminalSeen[ids[i]] != s) {
                    terminalChanged = true;
                }
            }
        }
    }

    function fund(uint256 amountSeed, uint256 hoursSeed) external {
        bytes32 id = keccak256(abi.encode("e", ids.length));
        IFreelaXEscrow.FundTerms memory t = IFreelaXEscrow.FundTerms({
            escrowId: id,
            client: client,
            freelancer: freelancer,
            token: address(usdc),
            amount: bound(amountSeed, 1, 2_000e6),
            fundingExpiresAt: uint64(block.timestamp + 1 hours),
            deliveryDueAt: uint64(block.timestamp + 7 days),
            reviewWindowHours: uint32(bound(hoursSeed, 24, 168)),
            maxRevisions: 2,
            highValueGrace: false,
            arbiter: arbiter
        });
        uint64 deadline = uint64(block.timestamp + 1 hours);
        bytes memory sig = _sign(authKey, escrow.fundDigest(t, 0, deadline));
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
        uint64 deadline = uint64(block.timestamp + 1 hours);
        bytes memory sig = _sign(authKey, escrow.submitDigest(id, keccak256(abi.encode(seed)), e.submissionCount, e.authNonce, deadline));
        vm.prank(freelancer);
        try escrow.submit(id, keccak256(abi.encode(seed)), deadline, sig) {} catch {}
        _observe();
    }

    function revise(uint256 seed) external {
        if (ids.length == 0) return;
        bytes32 id = _pick(seed);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(id);
        uint64 deadline = uint64(block.timestamp + 1 hours);
        bytes memory sig = _sign(authKey, escrow.revisionDigest(id, keccak256("fb"), e.revisionsUsed, e.authNonce, deadline));
        vm.prank(client);
        try escrow.requestRevision(id, keccak256("fb"), deadline, sig) {} catch {}
        _observe();
    }

    function dispute(uint256 seed, bool byClient) external {
        if (ids.length == 0) return;
        vm.prank(byClient ? client : freelancer);
        try escrow.openDispute(_pick(seed), keccak256("reason")) {} catch {}
        _observe();
    }

    function release(uint256 seed, bool byClient) external {
        if (ids.length == 0) return;
        vm.prank(byClient ? client : address(0xBEEF));
        try escrow.release(_pick(seed)) {} catch {}
        _observe();
    }

    function resolve(uint256 seed, bool toFreelancer, bool byArbiter) external {
        if (ids.length == 0) return;
        vm.prank(byArbiter ? arbiter : client);
        try escrow.resolveDispute(_pick(seed), toFreelancer, keccak256("decision")) {} catch {}
        _observe();
    }

    function mutualRefund(uint256 seed) external {
        if (ids.length == 0) return;
        bytes32 id = _pick(seed);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(id);
        uint64 deadline = uint64(block.timestamp + 1 hours);
        bytes32 digest = escrow.mutualRefundDigest(id, e.authNonce, deadline);
        bytes memory cs = _sign(clientKey, digest);
        bytes memory fs = _sign(freelancerKey, digest);
        try escrow.mutualRefund(id, deadline, cs, fs) {} catch {}
        _observe();
    }

    function extend(uint256 seed, bool approve) external {
        if (ids.length == 0) return;
        bytes32 id = _pick(seed);
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(id);
        if (approve) {
            vm.prank(client);
            try escrow.approveExtension(id) {} catch {}
        } else {
            vm.prank(freelancer);
            try escrow.requestExtension(id, e.originalDeliveryDueAt + uint64(bound(seed, 1, 7 days))) {} catch {}
        }
        _observe();
    }

    function fundAndSubmit(uint256 amountSeed, uint256 hoursSeed) external {
        uint256 before = ids.length;
        this.fund(amountSeed, hoursSeed);
        if (ids.length > before) {
            this.submit(ids.length - 1);
        }
    }

    function warpPastReview(uint256 seed) external {
        if (ids.length == 0) return;
        uint64 due = escrow.getEscrow(_pick(seed)).reviewDueAt;
        if (due > block.timestamp) vm.warp(due);
        _observe();
    }

    function warpPastDelivery(uint256 seed) external {
        if (ids.length == 0) return;
        uint64 due = escrow.getEscrow(_pick(seed)).deliveryDueAt;
        if (due >= block.timestamp) vm.warp(uint256(due) + 1);
        _observe();
    }

    function warp(uint256 secs) external {
        vm.warp(block.timestamp + bound(secs, 0, 12 hours));
        _observe();
    }

    function openSum() external view returns (uint256 sum) {
        for (uint256 i = 0; i < ids.length; i++) {
            IFreelaXEscrow.Escrow memory e = escrow.getEscrow(ids[i]);
            if (e.status != IFreelaXEscrow.Status.Released && e.status != IFreelaXEscrow.Status.Refunded) {
                sum += e.amount;
            }
        }
    }

    function depositedSum() external view returns (uint256 sum) {
        for (uint256 i = 0; i < ids.length; i++) {
            sum += escrow.getEscrow(ids[i]).amount;
        }
    }
}

contract EscrowInvariantTest is StdInvariant, Test {
    uint256 internal constant AUTH_KEY = 0xA11CE;
    MockUSDC internal usdc;
    FreelaXEscrow internal escrow;
    EscrowHandler internal handler;

    function setUp() public {
        vm.warp(1_700_000_000);
        usdc = new MockUSDC();
        address admin = makeAddr("admin");
        address arbiter = makeAddr("arbiter");
        escrow = new FreelaXEscrow(admin, usdc, arbiter);
        bytes32 role = escrow.AUTH_SIGNER_ROLE();
        vm.prank(admin);
        escrow.grantRole(role, vm.addr(AUTH_KEY));
        handler = new EscrowHandler(escrow, usdc, AUTH_KEY, arbiter);
        targetContract(address(handler));
    }

    function afterInvariant() public view {
        uint256 n = handler.idsLength();
        uint256 released; uint256 refunded; uint256 disputed; uint256 revisions;
        for (uint256 i = 0; i < n; i++) {
            IFreelaXEscrow.Escrow memory e = escrow.getEscrow(handler.ids(i));
            if (e.status == IFreelaXEscrow.Status.Released) released++;
            if (e.status == IFreelaXEscrow.Status.Refunded) refunded++;
            if (e.status == IFreelaXEscrow.Status.Disputed) disputed++;
            revisions += e.revisionsUsed;
        }
        console.log("coverage: escrows", n, "released", released);
        console.log("coverage: refunded", refunded, "disputed", disputed);
        console.log("coverage: revisions", revisions);
    }

    /// The contract holds exactly the sum of its open escrows (no donations occur in this handler): never more owed than held.
    function invariant_balanceCoversEveryOpenEscrow() public view {
        assertEq(usdc.balanceOf(address(escrow)), escrow.totalOpenAmount());
        assertEq(handler.openSum(), escrow.totalOpenAmount());
    }

    /// Value conservation: nothing is created or destroyed, so no escrow can ever pay out more than its deposit.
    function invariant_valueIsConserved() public view {
        assertEq(usdc.balanceOf(handler.client()) + usdc.balanceOf(handler.freelancer()) + usdc.balanceOf(address(escrow)), handler.minted());
        assertLe(usdc.balanceOf(handler.freelancer()), handler.depositedSum());
    }

    /// A settled escrow (Released or Refunded) never changes status again, so it can never settle twice.
    function invariant_terminalStatesAreFinal() public view {
        assertFalse(handler.terminalChanged());
    }
}
