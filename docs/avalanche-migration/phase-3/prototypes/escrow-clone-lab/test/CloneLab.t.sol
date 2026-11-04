// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test, console2} from "forge-std/Test.sol";
import {MockUSDC} from "@freelax/mocks/MockUSDC.sol";
import {CloneVault, CloneVaultFactory} from "../src/CloneVault.sol";

/// P3-C6 (O-2, LOCAL_SIMULATION only): measured cost and blast radius of per-escrow clone custody versus the shared escrow.
/// The shared-escrow figures come from the committed gas report (phase-2/evidence/forge-gas-report-escrow.txt).
contract CloneLabTest is Test {
    MockUSDC usdc;
    CloneVaultFactory factory;
    address controller = address(this);
    address client = address(0xC1);
    address freelancer = address(0xF1);
    uint256 constant AMOUNT = 5_000_000;

    function setUp() public {
        usdc = new MockUSDC();
        factory = new CloneVaultFactory();
        usdc.mint(client, 100 * AMOUNT);
    }

    function test_measure_perEscrowCloneOverhead() public {
        bytes32 id = keccak256("escrow-1");
        uint256 g0 = gasleft();
        CloneVault vault = factory.create(id, controller, address(usdc));
        uint256 createGas = g0 - gasleft();

        vm.prank(client);
        uint256 g1 = gasleft();
        usdc.transfer(address(vault), AMOUNT);
        uint256 depositGas = g1 - gasleft();

        uint256 g2 = gasleft();
        vault.payOut(freelancer, AMOUNT);
        uint256 payoutGas = g2 - gasleft();

        console2.log("clone create + initialize gas", createGas);
        console2.log("deposit into the clone vault gas", depositGas);
        console2.log("payout from the clone vault gas", payoutGas);
        assertEq(usdc.balanceOf(freelancer), AMOUNT);
        assertEq(address(vault), factory.vaultFor(id), "deterministic address");
        // the clone adds a one-off creation cost per escrow on top of the escrow logic itself
        assertGt(createGas, 40_000);
    }

    function test_blastRadius_aBlacklistedVaultBlocksOnlyItsOwnEscrow() public {
        CloneVault a = factory.create(keccak256("a"), controller, address(usdc));
        CloneVault b = factory.create(keccak256("b"), controller, address(usdc));
        vm.startPrank(client);
        usdc.transfer(address(a), AMOUNT);
        usdc.transfer(address(b), AMOUNT);
        vm.stopPrank();

        usdc.setBlacklisted(address(a), true);
        vm.expectRevert(abi.encodeWithSelector(MockUSDC.Blacklisted.selector, address(a)));
        a.payOut(freelancer, AMOUNT);
        b.payOut(freelancer, AMOUNT); // the other escrow is unaffected
        assertEq(usdc.balanceOf(freelancer), AMOUNT);
        assertEq(usdc.balanceOf(address(a)), AMOUNT, "the blocked escrow keeps its funds: stuck and reported, never bypassed");
    }

    function test_blastRadius_aBlacklistedSharedHolderBlocksEveryEscrow() public {
        address shared = address(new SharedHolder(usdc));
        vm.prank(client);
        usdc.transfer(shared, 2 * AMOUNT); // two escrows' funds in one contract, as in the shared baseline
        usdc.setBlacklisted(shared, true);
        vm.expectRevert(abi.encodeWithSelector(MockUSDC.Blacklisted.selector, shared));
        SharedHolder(shared).payOut(freelancer, AMOUNT);
        vm.expectRevert(abi.encodeWithSelector(MockUSDC.Blacklisted.selector, shared));
        SharedHolder(shared).payOut(client, AMOUNT);
    }

    function test_onlyTheControllerCanPayOutAndInitializationIsOneShot() public {
        CloneVault v = factory.create(keccak256("c"), controller, address(usdc));
        vm.prank(client);
        vm.expectRevert(CloneVault.NotController.selector);
        v.payOut(client, 1);
        vm.expectRevert(CloneVault.AlreadyInitialized.selector);
        v.initialize(client, usdc);
    }
}

/// Stand-in for the shared baseline's custody (one address holds every escrow's funds). Measurement only.
contract SharedHolder {
    MockUSDC immutable token;

    constructor(MockUSDC token_) {
        token = token_;
    }

    function payOut(address to, uint256 amount) external {
        token.transfer(to, amount);
    }
}
