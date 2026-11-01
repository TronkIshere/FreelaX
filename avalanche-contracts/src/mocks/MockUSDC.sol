// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";

/// @notice Simulated USDC for LOCAL_SIMULATION only. It has no value and must never be deployed to a public network.
/// Mirrors the issuer controls that matter to the escrow: 6 decimals, a blacklist (a blacklisted address can neither
/// send nor receive) and an issuer pause. Anyone can mint: this is a faucet-style test token.
contract MockUSDC is ERC20 {
    error Blacklisted(address account);
    error TokenPaused();

    mapping(address account => bool) public blacklisted;
    bool public paused;

    constructor() ERC20("USD Coin (simulated)", "USDC") {}

    function decimals() public pure override returns (uint8) {
        return 6;
    }

    function mint(address to, uint256 amount) external {
        _mint(to, amount);
    }

    function setBlacklisted(address account, bool value) external {
        blacklisted[account] = value;
    }

    function setPaused(bool value) external {
        paused = value;
    }

    function _update(address from, address to, uint256 value) internal override {
        if (paused) {
            revert TokenPaused();
        }
        if (blacklisted[from]) {
            revert Blacklisted(from);
        }
        if (blacklisted[to]) {
            revert Blacklisted(to);
        }
        super._update(from, to, value);
    }
}
