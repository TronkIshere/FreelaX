// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {Clones} from "@openzeppelin/contracts/proxy/Clones.sol";

/// @notice P3-C6 measurement only (LOCAL_SIMULATION). A per-escrow vault: holds the USDC of ONE escrow; only its controller
/// (the escrow logic) can pay out, and only to an address the controller passes. It exists to measure the cost and the
/// blast radius of physical per-escrow custody. It is NOT the FreelaX escrow and must never be deployed outside a local chain.
contract CloneVault {
    using SafeERC20 for IERC20;

    error AlreadyInitialized();
    error NotController();

    address public controller;
    IERC20 public token;

    function initialize(address controller_, IERC20 token_) external {
        if (controller != address(0)) revert AlreadyInitialized();
        controller = controller_;
        token = token_;
    }

    function payOut(address to, uint256 amount) external {
        if (msg.sender != controller) revert NotController();
        token.safeTransfer(to, amount);
    }
}

/// @notice P3-C6 measurement only: deploys one EIP-1167 clone vault per escrow id (deterministic address).
contract CloneVaultFactory {
    address public immutable implementation;

    constructor() {
        implementation = address(new CloneVault());
    }

    function vaultFor(bytes32 escrowId) external view returns (address) {
        return Clones.predictDeterministicAddress(implementation, escrowId, address(this));
    }

    function create(bytes32 escrowId, address controller, address token) external returns (CloneVault vault) {
        vault = CloneVault(Clones.cloneDeterministic(implementation, escrowId));
        vault.initialize(controller, IERC20(token));
    }
}
