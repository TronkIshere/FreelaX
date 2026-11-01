// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Script} from "forge-std/Script.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {FreelaXEscrow} from "../src/FreelaXEscrow.sol";
import {MockUSDC} from "../src/mocks/MockUSDC.sol";

/// Deploys the escrow (and, in LOCAL_SIMULATION only, a simulated USDC) and writes a deployment manifest.
/// The script holds NO key material: the broadcaster is whatever signer forge is given on the command line (an unlocked Anvil
/// account locally; a human-operated wallet for Fuji). It refuses every chain except 31337 and 43113 and names Mainnet explicitly.
/// Required environment: DEPLOY_ENVIRONMENT (LOCAL_SIMULATION or AVALANCHE_FUJI), ARBITER, AUTH_SIGNER. Optional: PAUSER.
contract Deploy is Script {
    address internal constant FUJI_USDC = 0x5425890298aed601595a70AB815c96711a31Bc65;
    bytes32 internal constant LOCAL = keccak256("LOCAL_SIMULATION");
    bytes32 internal constant FUJI = keccak256("AVALANCHE_FUJI");

    error MainnetForbidden(uint256 chainId);
    error UnsupportedChain(uint256 chainId);
    error UnknownEnvironment(string environment);

    function guard(string memory environment, uint256 chainId) public pure {
        if (chainId == 43114 || chainId == 1) revert MainnetForbidden(chainId);
        bytes32 h = keccak256(bytes(environment));
        if (h == LOCAL) {
            // a LOCAL fork of Fuji reports 43113, so the environment is explicit and the chain id alone never decides
            if (chainId != 31337 && chainId != 43113) revert UnsupportedChain(chainId);
        } else if (h == FUJI) {
            if (chainId != 43113) revert UnsupportedChain(chainId);
        } else {
            revert UnknownEnvironment(environment);
        }
    }

    function run() external returns (FreelaXEscrow escrow, address token) {
        string memory environment = vm.envString("DEPLOY_ENVIRONMENT");
        guard(environment, block.chainid);
        address arbiter = vm.envAddress("ARBITER");
        address authSigner = vm.envAddress("AUTH_SIGNER");
        address pauser = vm.envOr("PAUSER", address(0));
        uint256 startBlock = block.number;

        vm.startBroadcast();
        (, address broadcaster,) = vm.readCallers();
        bool mock = keccak256(bytes(environment)) == LOCAL;
        token = mock ? address(new MockUSDC()) : FUJI_USDC;
        escrow = new FreelaXEscrow(broadcaster, IERC20(token), arbiter);
        escrow.grantRole(escrow.AUTH_SIGNER_ROLE(), authSigner);
        if (pauser != address(0)) {
            escrow.grantRole(escrow.PAUSER_ROLE(), pauser);
        }
        vm.stopBroadcast();

        string memory key = "manifest";
        vm.serializeUint(key, "schemaVersion", 1);
        vm.serializeString(key, "environment", environment);
        vm.serializeUint(key, "chainId", block.chainid);
        vm.serializeAddress(key, "escrow", address(escrow));
        vm.serializeAddress(key, "token", token);
        vm.serializeString(key, "tokenKind", mock ? "MOCK" : "CIRCLE_FUJI_USDC");
        vm.serializeAddress(key, "admin", broadcaster);
        vm.serializeAddress(key, "arbiter", arbiter);
        vm.serializeAddress(key, "authSigner", authSigner);
        vm.serializeString(key, "contractVersion", "1");
        string memory json = vm.serializeUint(key, "startBlock", startBlock);
        vm.writeJson(json, string.concat("./deployments/", environment, ".json"));
    }
}
