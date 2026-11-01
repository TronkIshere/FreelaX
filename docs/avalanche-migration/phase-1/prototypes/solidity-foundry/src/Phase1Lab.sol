// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {EIP712} from "@openzeppelin/contracts/utils/cryptography/EIP712.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";

/// @notice PHASE 1 DISPOSABLE LAB CONTRACT. NOT the FreelaX escrow. Holds no business logic.
/// Demonstrates: roles, storage, ERC-20 pull, events, reverts, timestamp deadlines, EIP-712 + nonce replay protection.
contract Phase1Lab is AccessControl, EIP712 {
    using SafeERC20 for IERC20;

    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 public constant AUTH_SIGNER_ROLE = keccak256("AUTH_SIGNER_ROLE");
    bytes32 public constant AUTH_TYPEHASH =
        keccak256("Authorization(address account,bytes32 action,uint256 nonce,uint256 deadline)");

    uint256 public value;
    mapping(bytes32 => bool) public usedNonce; // keyed by hash(signer, nonce)

    event ValueSet(address indexed by, uint256 oldValue, uint256 newValue);
    event Pulled(address indexed token, address indexed from, uint256 amount);
    event Acted(address indexed account, uint256 blockTimestamp, uint256 deadline);
    event Authorized(address indexed account, bytes32 indexed action, uint256 nonce, address signer);

    error DeadlinePassed(uint256 nowTs, uint256 deadline);
    error DeadlineNotReached(uint256 nowTs, uint256 deadline);
    error BadSigner();
    error NonceUsed();
    error Expired();
    error Boom(uint256 code);

    constructor(address admin) EIP712("FreelaXPhase1Lab", "1") {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
    }

    function setValue(uint256 v) external onlyRole(OPERATOR_ROLE) {
        emit ValueSet(msg.sender, value, v);
        value = v;
    }

    function pull(IERC20 token, uint256 amount) external {
        token.safeTransferFrom(msg.sender, address(this), amount);
        emit Pulled(address(token), msg.sender, amount);
    }

    /// Non-strict: allowed while block.timestamp <= deadline (equality passes).
    function actUntil(uint256 deadline) external {
        if (block.timestamp > deadline) revert DeadlinePassed(block.timestamp, deadline);
        emit Acted(msg.sender, block.timestamp, deadline);
    }

    /// Strict opposite: allowed only once block.timestamp > deadline (equality fails). Mutually exclusive with actUntil.
    function actAfter(uint256 deadline) external {
        if (block.timestamp <= deadline) revert DeadlineNotReached(block.timestamp, deadline);
        emit Acted(msg.sender, block.timestamp, deadline);
    }

    function boom(uint256 code) external pure {
        revert Boom(code);
    }

    function authDigest(address account, bytes32 action, uint256 nonce, uint256 deadline) public view returns (bytes32) {
        return _hashTypedDataV4(keccak256(abi.encode(AUTH_TYPEHASH, account, action, nonce, deadline)));
    }

    /// Consumes an EIP-712 authorization for msg.sender. Signer must hold AUTH_SIGNER_ROLE.
    function useAuthorization(bytes32 action, uint256 nonce, uint256 deadline, bytes calldata sig) external {
        if (block.timestamp > deadline) revert Expired();
        address signer = ECDSA.recover(authDigest(msg.sender, action, nonce, deadline), sig);
        if (!hasRole(AUTH_SIGNER_ROLE, signer)) revert BadSigner();
        bytes32 k = keccak256(abi.encode(signer, nonce));
        if (usedNonce[k]) revert NonceUsed();
        usedNonce[k] = true;
        emit Authorized(msg.sender, action, nonce, signer);
    }
}
