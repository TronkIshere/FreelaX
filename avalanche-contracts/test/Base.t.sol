// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {FreelaXEscrow} from "../src/FreelaXEscrow.sol";
import {IFreelaXEscrow} from "../src/interfaces/IFreelaXEscrow.sol";
import {MockUSDC} from "../src/mocks/MockUSDC.sol";

abstract contract Base is Test {
    uint256 internal constant T0 = 1_700_000_000;
    uint256 internal constant AUTH_KEY = 0xA11CE;

    MockUSDC internal usdc;
    FreelaXEscrow internal escrow;

    address internal authSigner = vm.addr(AUTH_KEY);
    address internal admin = makeAddr("admin");
    address internal arbiter = makeAddr("arbiter");
    address internal pauser = makeAddr("pauser");
    address internal client = makeAddr("client");
    address internal freelancer = makeAddr("freelancer");
    address internal relayer = makeAddr("relayer");
    address internal stranger = makeAddr("stranger");

    bytes32 internal constant ID = keccak256("escrow-1");
    uint256 internal constant AMOUNT = 100e6;

    function setUp() public virtual {
        vm.warp(T0);
        usdc = new MockUSDC();
        escrow = new FreelaXEscrow(admin, usdc, arbiter);
        vm.startPrank(admin);
        escrow.grantRole(escrow.AUTH_SIGNER_ROLE(), authSigner);
        escrow.grantRole(escrow.PAUSER_ROLE(), pauser);
        vm.stopPrank();
        usdc.mint(client, 100_000e6);
        vm.prank(client);
        usdc.approve(address(escrow), type(uint256).max);
    }

    function terms(bytes32 id, uint256 amount) internal view returns (IFreelaXEscrow.FundTerms memory t) {
        t = IFreelaXEscrow.FundTerms({
            escrowId: id,
            client: client,
            freelancer: freelancer,
            token: address(usdc),
            amount: amount,
            fundingExpiresAt: uint64(T0 + 1 hours),
            deliveryDueAt: uint64(T0 + 7 days),
            reviewWindowHours: 72,
            maxRevisions: 2,
            highValueGrace: false,
            arbiter: arbiter
        });
    }

    function sign(bytes32 digest) internal pure returns (bytes memory) {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(AUTH_KEY, digest);
        return abi.encodePacked(r, s, v);
    }

    function signFund(IFreelaXEscrow.FundTerms memory t, uint64 deadline) internal view returns (bytes memory) {
        return sign(escrow.fundDigest(t, 0, deadline));
    }

    function fundDefault() internal returns (IFreelaXEscrow.FundTerms memory t) {
        t = terms(ID, AMOUNT);
        uint64 deadline = uint64(T0 + 1 hours);
        bytes memory sig = signFund(t, deadline);
        vm.prank(client);
        escrow.fund(t, deadline, sig);
    }

    function signSubmit(bytes32 id, bytes32 evidence, uint64 deadline) internal view returns (bytes memory) {
        IFreelaXEscrow.Escrow memory e = escrow.getEscrow(id);
        return sign(escrow.submitDigest(id, evidence, e.submissionCount, e.authNonce, deadline));
    }

    function submitDefault(bytes32 id, bytes32 evidence) internal {
        uint64 deadline = uint64(vm.getBlockTimestamp() + 1 hours);
        bytes memory sig = signSubmit(id, evidence, deadline);
        vm.prank(freelancer);
        escrow.submit(id, evidence, deadline, sig);
    }
}
