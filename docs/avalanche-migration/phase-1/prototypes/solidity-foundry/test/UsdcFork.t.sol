// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test, console} from "forge-std/Test.sol";
import {Phase1Lab} from "../src/Phase1Lab.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";

/// Behavioural verification of the *deployed* Fuji USDC on an anvil/forge FORK (state changes stay local).
/// Run: forge test --match-contract UsdcFork --fork-url <fuji-rpc> -vv
contract UsdcForkTest is Test {
    address constant USDC = 0x5425890298aed601595a70AB815c96711a31Bc65;
    address constant HOLDER = 0x0000000000000000000000000000000000000001; // observed non-zero balance on Fuji (see evidence)
    uint256 ownerPk = 0xA11CE5;
    address owner;
    address spender = address(0x5E11);
    address payee = address(0xFA11);

    function setUp() public {
        owner = vm.addr(ownerPk);
        uint256 h = IERC20(USDC).balanceOf(HOLDER);
        require(h >= 10e6, "holder drained on Fuji; pick another funded address");
        vm.prank(HOLDER);
        IERC20(USDC).transfer(owner, 10e6);
    }

    function test_metadata_and_asset_identity() public view {
        (, bytes memory n) = USDC.staticcall(abi.encodeWithSignature("name()"));
        (, bytes memory s) = USDC.staticcall(abi.encodeWithSignature("symbol()"));
        (, bytes memory v) = USDC.staticcall(abi.encodeWithSignature("version()"));
        assertEq(abi.decode(s, (string)), "USDC");
        assertEq(abi.decode(n, (string)), "USD Coin");
        assertEq(abi.decode(v, (string)), "2");
        (, bytes memory d) = USDC.staticcall(abi.encodeWithSignature("decimals()"));
        assertEq(abi.decode(d, (uint8)), 6);
    }

    function test_erc20_approve_and_transferFrom_via_contract() public {
        Phase1Lab lab = new Phase1Lab(address(this));
        vm.startPrank(owner);
        IERC20(USDC).approve(address(lab), 3e6);
        assertEq(IERC20(USDC).allowance(owner, address(lab)), 3e6);
        lab.pull(IERC20(USDC), 3e6);
        vm.stopPrank();
        assertEq(IERC20(USDC).balanceOf(address(lab)), 3e6);
        assertEq(IERC20(USDC).allowance(owner, address(lab)), 0);
    }

    function _domain() internal view returns (bytes32) {
        (, bytes memory r) = USDC.staticcall(abi.encodeWithSignature("DOMAIN_SEPARATOR()"));
        return abi.decode(r, (bytes32));
    }

    function test_domain_separator_matches_eip712_recomputation() public view {
        bytes32 expected = keccak256(
            abi.encode(
                keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"),
                keccak256(bytes("USD Coin")), keccak256(bytes("2")), block.chainid, USDC
            )
        );
        assertEq(_domain(), expected, "domain separator != recomputed (name/version/chainId/contract)");
        assertEq(block.chainid, 43113);
    }

    function test_eip2612_permit_behaviour() public {
        bytes32 typehash = keccak256("Permit(address owner,address spender,uint256 value,uint256 nonce,uint256 deadline)");
        uint256 dl = block.timestamp + 1 hours;
        bytes32 structHash = keccak256(abi.encode(typehash, owner, spender, 2e6, 0, dl));
        bytes32 digest = keccak256(abi.encodePacked("\x19\x01", _domain(), structHash));
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(ownerPk, digest);

        // negative: wrong value must fail
        (bool bad,) = USDC.call(abi.encodeWithSignature("permit(address,address,uint256,uint256,uint8,bytes32,bytes32)", owner, spender, 3e6, dl, v, r, s));
        assertFalse(bad, "permit with tampered value must revert");
        // positive
        (bool ok,) = USDC.call(abi.encodeWithSignature("permit(address,address,uint256,uint256,uint8,bytes32,bytes32)", owner, spender, 2e6, dl, v, r, s));
        assertTrue(ok, "permit(v,r,s) failed: EIP-2612 NOT supported behaviourally");
        assertEq(IERC20(USDC).allowance(owner, spender), 2e6);
        // replay: nonce consumed
        (bool replay,) = USDC.call(abi.encodeWithSignature("permit(address,address,uint256,uint256,uint8,bytes32,bytes32)", owner, spender, 2e6, dl, v, r, s));
        assertFalse(replay, "permit replay must revert");
        (, bytes memory nn) = USDC.staticcall(abi.encodeWithSignature("nonces(address)", owner));
        assertEq(abi.decode(nn, (uint256)), 1);
    }

    uint256 constant VB_OFFSET = 1 hours;

    function _auth(bytes32 typehash, bytes32 nonce) internal view returns (uint8 v, bytes32 r, bytes32 s) {
        bytes32 sh = keccak256(abi.encode(typehash, owner, payee, 1e6, uint256(0), block.timestamp + VB_OFFSET, nonce));
        (v, r, s) = vm.sign(ownerPk, keccak256(abi.encodePacked("\x19\x01", _domain(), sh)));
    }

    function _call3009(string memory fn, bytes32 nonce, uint8 v, bytes32 r, bytes32 s) internal returns (bool ok) {
        (ok,) = USDC.call(abi.encodeWithSignature(
            string.concat(fn, "(address,address,uint256,uint256,uint256,bytes32,uint8,bytes32,bytes32)"),
            owner, payee, 1e6, uint256(0), block.timestamp + VB_OFFSET, nonce, v, r, s));
    }

    function test_eip3009_transferWithAuthorization_behaviour() public {
        bytes32 th = keccak256("TransferWithAuthorization(address from,address to,uint256 value,uint256 validAfter,uint256 validBefore,bytes32 nonce)");
        bytes32 nonce = keccak256("phase1-nonce-1");
        (uint8 v, bytes32 r, bytes32 s) = _auth(th, nonce);
        uint256 before_ = IERC20(USDC).balanceOf(payee);
        bool ok = _call3009("transferWithAuthorization", nonce, v, r, s);
        console.log("transferWithAuthorization(v,r,s) ok:", ok);
        assertTrue(ok, "EIP-3009 transferWithAuthorization(v,r,s) NOT behaviourally supported");
        assertEq(IERC20(USDC).balanceOf(payee), before_ + 1e6);
        (, bytes memory st) = USDC.staticcall(abi.encodeWithSignature("authorizationState(address,bytes32)", owner, nonce));
        assertTrue(abi.decode(st, (bool)), "nonce not marked used");
        assertFalse(_call3009("transferWithAuthorization", nonce, v, r, s), "authorization replay must revert");
        // tampered: wrong signer's signature on different nonce
        (uint8 v3, bytes32 r3, bytes32 s3) = _auth(th, keccak256("phase1-nonce-X"));
        assertFalse(_call3009("transferWithAuthorization", keccak256("phase1-nonce-Y"), v3, r3, s3), "tampered nonce must revert");
    }

    function test_eip3009_receiveWithAuthorization_payee_only() public {
        bytes32 rt = keccak256("ReceiveWithAuthorization(address from,address to,uint256 value,uint256 validAfter,uint256 validBefore,bytes32 nonce)");
        bytes32 n2 = keccak256("phase1-nonce-2");
        (uint8 v, bytes32 r, bytes32 s) = _auth(rt, n2);
        assertFalse(_call3009("receiveWithAuthorization", n2, v, r, s), "non-payee caller must revert");
        vm.prank(payee);
        assertTrue(_call3009("receiveWithAuthorization", n2, v, r, s), "payee caller failed");
    }

    function test_proxy_and_admin_surface_reads() public view {
        // Circle FiatTokenProxy (zeppelinos) implementation slot = keccak256("org.zeppelinos.proxy.implementation")
        bytes32 slot = 0x7050c9e0f4ca769c69bd3a8ef740bc37934f8e2c036e5a723fd8ee048ed3f8c3;
        address impl = address(uint160(uint256(vm.load(USDC, slot))));
        console.log("zeppelinos implementation slot ->", impl);
        (bool ok, bytes memory bl) = USDC.staticcall(abi.encodeWithSignature("isBlacklisted(address)", HOLDER));
        console.log("isBlacklisted(holder) call ok:", ok);
        if (ok) console.log("isBlacklisted(holder):", abi.decode(bl, (bool)));
    }
}
