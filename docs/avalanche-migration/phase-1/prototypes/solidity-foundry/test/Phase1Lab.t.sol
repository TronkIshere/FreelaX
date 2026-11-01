// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {Phase1Lab} from "../src/Phase1Lab.sol";

contract Phase1LabTest is Test {
    Phase1Lab lab;
    address admin = address(0xA11CE);
    address user = address(0xB0B);
    uint256 signerPk = 0x5151; // ephemeral test key, local only, no value
    address signer;

    function setUp() public {
        signer = vm.addr(signerPk);
        lab = new Phase1Lab(admin);
        vm.startPrank(admin);
        lab.grantRole(lab.OPERATOR_ROLE(), admin);
        lab.grantRole(lab.AUTH_SIGNER_ROLE(), signer);
        vm.stopPrank();
    }

    function test_role_check_and_event() public {
        vm.prank(user);
        vm.expectRevert();
        lab.setValue(1);
        vm.prank(admin);
        vm.expectEmit(true, false, false, true);
        emit Phase1Lab.ValueSet(admin, 0, 7);
        lab.setValue(7);
        assertEq(lab.value(), 7);
    }

    function test_custom_error_revert() public {
        vm.expectRevert(abi.encodeWithSelector(Phase1Lab.Boom.selector, 42));
        lab.boom(42);
    }

    function test_deadline_boundaries() public {
        vm.warp(1000);
        lab.actUntil(1000); // equality passes
        vm.expectRevert();
        lab.actAfter(1000); // equality fails: mutually exclusive with actUntil
        lab.actAfter(999);
        vm.expectRevert();
        lab.actUntil(999);
        vm.warp(1001);
        vm.expectRevert();
        lab.actUntil(1000);
        lab.actAfter(1000);
    }

    function _sig(address acct, bytes32 action, uint256 nonce, uint256 dl) internal view returns (bytes memory) {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(signerPk, lab.authDigest(acct, action, nonce, dl));
        return abi.encodePacked(r, s, v);
    }

    function test_eip712_ok_replay_expiry_wrong_account_tamper() public {
        bytes32 a = keccak256("FUND");
        uint256 dl = block.timestamp + 100;
        bytes memory sig = _sig(user, a, 1, dl);
        vm.startPrank(user);
        lab.useAuthorization(a, 1, dl, sig);
        vm.expectRevert(Phase1Lab.NonceUsed.selector);
        lab.useAuthorization(a, 1, dl, sig); // replay
        vm.stopPrank();

        uint256 dl2 = block.timestamp + 10;
        bytes memory s2 = _sig(user, a, 2, dl2);
        vm.warp(block.timestamp + 11);
        vm.prank(user);
        vm.expectRevert(Phase1Lab.Expired.selector);
        lab.useAuthorization(a, 2, dl2, s2); // expired

        uint256 dl3 = block.timestamp + 100;
        bytes memory s3 = _sig(user, a, 3, dl3);
        vm.prank(address(0xBAD));
        vm.expectRevert(Phase1Lab.BadSigner.selector);
        lab.useAuthorization(a, 3, dl3, s3); // bound to msg.sender
        vm.prank(user);
        vm.expectRevert(Phase1Lab.BadSigner.selector);
        lab.useAuthorization(a, 99, dl3, s3); // tampered nonce
    }

    function test_eip712_wrong_chain_and_wrong_contract() public {
        bytes32 a = keccak256("FUND");
        uint256 dl = block.timestamp + 100;
        bytes memory sig = _sig(user, a, 5, dl);
        uint256 original = block.chainid;
        vm.chainId(43114);
        vm.prank(user);
        vm.expectRevert(Phase1Lab.BadSigner.selector);
        lab.useAuthorization(a, 5, dl, sig); // different chain id
        vm.chainId(original);
        Phase1Lab other = new Phase1Lab(admin);
        bytes32 role = other.AUTH_SIGNER_ROLE();
        vm.prank(admin);
        other.grantRole(role, signer);
        vm.prank(user);
        vm.expectRevert(Phase1Lab.BadSigner.selector);
        other.useAuthorization(a, 5, dl, sig); // different verifyingContract
    }

    function test_garbage_signature_reverts() public {
        vm.prank(user);
        vm.expectRevert();
        lab.useAuthorization(keccak256("X"), 1, block.timestamp + 1, hex"1234");
    }
}
