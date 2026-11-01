// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {FreelaXEscrow} from "../src/FreelaXEscrow.sol";
import {IFreelaXEscrow} from "../src/interfaces/IFreelaXEscrow.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";

interface IFiatToken {
    function blacklister() external view returns (address);
    function pauser() external view returns (address);
    function blacklist(address account) external;
    function unBlacklist(address account) external;
    function isBlacklisted(address account) external view returns (bool);
    function pause() external;
    function unpause() external;
    function decimals() external view returns (uint8);
    function symbol() external view returns (string memory);
    function version() external view returns (string memory);
}

/// O-6 evaluation on a LOCAL FORK of the real Circle testnet USDC (read-only RPC; every state change stays on the fork).
/// Optional: skipped unless FUJI_RPC is set. Run through scripts/forge.sh with DOCKER_EXTRA="-e FUJI_RPC=<fuji https url>".
contract UsdcForkTest is Test {
    address internal constant USDC = 0x5425890298aed601595a70AB815c96711a31Bc65;
    uint256 internal constant AUTH_KEY = 0xA11CE;
    uint256 internal constant AMOUNT = 25e6;

    FreelaXEscrow internal escrow;
    address internal client = makeAddr("client");
    address internal freelancer = makeAddr("freelancer");
    address internal arbiter = makeAddr("arbiter");
    address internal admin = makeAddr("admin");
    address internal relayer = makeAddr("relayer");

    function setUp() public {
        string memory rpc = vm.envOr("FUJI_RPC", string(""));
        if (bytes(rpc).length == 0) {
            vm.skip(true);
            return;
        }
        vm.createSelectFork(rpc);
        require(block.chainid == 43113, "not Fuji");
        escrow = new FreelaXEscrow(admin, IERC20(USDC), arbiter);
        bytes32 role = escrow.AUTH_SIGNER_ROLE();
        vm.prank(admin);
        escrow.grantRole(role, vm.addr(AUTH_KEY));
        deal(USDC, client, 1_000e6);
        vm.prank(client);
        IERC20(USDC).approve(address(escrow), type(uint256).max);
    }

    function _terms(bytes32 id) internal view returns (IFreelaXEscrow.FundTerms memory) {
        return IFreelaXEscrow.FundTerms({
            escrowId: id,
            client: client,
            freelancer: freelancer,
            token: USDC,
            amount: AMOUNT,
            fundingExpiresAt: uint64(block.timestamp + 1 hours),
            deliveryDueAt: uint64(block.timestamp + 7 days),
            reviewWindowHours: 72,
            maxRevisions: 2,
            highValueGrace: false,
            arbiter: arbiter
        });
    }

    function _sign(bytes32 digest) internal pure returns (bytes memory) {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(AUTH_KEY, digest);
        return abi.encodePacked(r, s, v);
    }

    function _fundAndSubmit(bytes32 id) internal {
        IFreelaXEscrow.FundTerms memory t = _terms(id);
        uint64 deadline = uint64(block.timestamp + 1 hours);
        bytes memory fundSig = _sign(escrow.fundDigest(t, 0, deadline));
        vm.prank(client);
        escrow.fund(t, deadline, fundSig);
        bytes memory submitSig = _sign(escrow.submitDigest(id, keccak256("work"), 0, 1, deadline));
        vm.prank(freelancer);
        escrow.submit(id, keccak256("work"), deadline, submitSig);
    }

    function _releaseReverts(bytes32 id) internal returns (bool reverted) {
        vm.prank(client);
        try escrow.release(id) {
            return false;
        } catch {
            return true;
        }
    }

    function test_realUsdc_identityAndHappyPath() public {
        assertEq(IFiatToken(USDC).decimals(), 6);
        assertEq(IFiatToken(USDC).symbol(), "USDC");
        assertEq(IFiatToken(USDC).version(), "2");
        _fundAndSubmit(keccak256("happy"));
        assertEq(IERC20(USDC).balanceOf(address(escrow)), AMOUNT);
        vm.prank(client);
        escrow.release(keccak256("happy"));
        assertEq(IERC20(USDC).balanceOf(freelancer), AMOUNT);
        assertEq(IERC20(USDC).balanceOf(address(escrow)), 0);
    }

    function test_realUsdc_blacklistedRecipientBlocksReleaseButTheEscrowSurvives() public {
        bytes32 id = keccak256("blacklist-recipient");
        _fundAndSubmit(id);
        address issuer = IFiatToken(USDC).blacklister();
        vm.prank(issuer);
        IFiatToken(USDC).blacklist(freelancer);
        assertTrue(IFiatToken(USDC).isBlacklisted(freelancer));

        assertTrue(_releaseReverts(id), "the push payout reverts for a blacklisted recipient");
        assertEq(uint8(escrow.getEscrow(id).status), uint8(IFreelaXEscrow.Status.Submitted), "state unchanged");
        assertEq(IERC20(USDC).balanceOf(address(escrow)), AMOUNT, "funds still held");

        vm.prank(issuer);
        IFiatToken(USDC).unBlacklist(freelancer);
        vm.prank(client);
        escrow.release(id);
        assertEq(IERC20(USDC).balanceOf(freelancer), AMOUNT);
    }

    function test_realUsdc_blacklistingTheSharedContractFreezesEveryOpenEscrow() public {
        bytes32 a = keccak256("a");
        bytes32 b = keccak256("b");
        _fundAndSubmit(a);
        _fundAndSubmit(b);
        address issuer = IFiatToken(USDC).blacklister();
        vm.prank(issuer);
        IFiatToken(USDC).blacklist(address(escrow));

        assertTrue(_releaseReverts(a), "escrow A frozen");
        assertTrue(_releaseReverts(b), "escrow B frozen");
        IFreelaXEscrow.FundTerms memory t = _terms(keccak256("c"));
        uint64 deadline = uint64(block.timestamp + 1 hours);
        bytes memory sig = _sign(escrow.fundDigest(t, 0, deadline));
        vm.prank(client);
        vm.expectRevert();
        escrow.fund(t, deadline, sig);
        assertEq(uint8(escrow.getEscrow(a).status), uint8(IFreelaXEscrow.Status.Submitted));
        assertEq(uint8(escrow.getEscrow(b).status), uint8(IFreelaXEscrow.Status.Submitted));

        vm.prank(issuer);
        IFiatToken(USDC).unBlacklist(address(escrow));
        vm.prank(client);
        escrow.release(a);
        vm.prank(client);
        escrow.release(b);
        assertEq(IERC20(USDC).balanceOf(freelancer), 2 * AMOUNT);
    }

    function test_realUsdc_issuerPauseBlocksPayoutsUntilUnpaused() public {
        bytes32 id = keccak256("issuer-pause");
        _fundAndSubmit(id);
        address pauser = IFiatToken(USDC).pauser();
        vm.prank(pauser);
        IFiatToken(USDC).pause();
        assertTrue(_releaseReverts(id));
        assertEq(uint8(escrow.getEscrow(id).status), uint8(IFreelaXEscrow.Status.Submitted));
        vm.prank(pauser);
        IFiatToken(USDC).unpause();
        vm.prank(client);
        escrow.release(id);
        assertEq(IERC20(USDC).balanceOf(freelancer), AMOUNT);
    }
}
