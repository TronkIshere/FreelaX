// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {Deploy} from "../script/Deploy.s.sol";
import {FreelaXEscrow} from "../src/FreelaXEscrow.sol";

contract DeployGuardTest is Test {
    Deploy internal script;

    function setUp() public {
        script = new Deploy();
    }

    function test_mainnetChainIdsAreRefusedInEveryEnvironment() public {
        string[2] memory envs = ["LOCAL_SIMULATION", "AVALANCHE_FUJI"];
        uint256[2] memory forbidden = [uint256(43114), uint256(1)];
        for (uint256 e = 0; e < 2; e++) {
            for (uint256 c = 0; c < 2; c++) {
                vm.expectRevert(abi.encodeWithSelector(Deploy.MainnetForbidden.selector, forbidden[c]));
                script.guard(envs[e], forbidden[c]);
            }
        }
    }

    function test_onlyTheTwoSupportedChainsAreAcceptedPerEnvironment() public {
        script.guard("LOCAL_SIMULATION", 31337);
        script.guard("LOCAL_SIMULATION", 43113);
        script.guard("AVALANCHE_FUJI", 43113);
        vm.expectRevert(abi.encodeWithSelector(Deploy.UnsupportedChain.selector, uint256(31337)));
        script.guard("AVALANCHE_FUJI", 31337);
        vm.expectRevert(abi.encodeWithSelector(Deploy.UnsupportedChain.selector, uint256(999)));
        script.guard("LOCAL_SIMULATION", 999);
        vm.expectRevert(abi.encodeWithSelector(Deploy.UnsupportedChain.selector, uint256(0)));
        script.guard("AVALANCHE_FUJI", 0);
    }

    function test_theEnvironmentMustBeNamedAndKnown() public {
        vm.expectRevert(abi.encodeWithSelector(Deploy.UnknownEnvironment.selector, "MAINNET"));
        script.guard("MAINNET", 43114 + 1);
        vm.expectRevert(abi.encodeWithSelector(Deploy.UnknownEnvironment.selector, ""));
        script.guard("", 31337);
        vm.expectRevert(abi.encodeWithSelector(Deploy.UnknownEnvironment.selector, "PRODUCTION"));
        script.guard("PRODUCTION", 31337);
    }

    function test_runOnTheWrongChainRevertsBeforeDeployingAnything() public {
        vm.setEnv("DEPLOY_ENVIRONMENT", "LOCAL_SIMULATION");
        vm.setEnv("ARBITER", vm.toString(makeAddr("arbiter")));
        vm.setEnv("AUTH_SIGNER", vm.toString(makeAddr("signer")));
        vm.chainId(43114);
        vm.expectRevert(abi.encodeWithSelector(Deploy.MainnetForbidden.selector, uint256(43114)));
        script.run();
    }

    function test_localRunDeploysTheStackGrantsTheSignerRoleAndWritesTheManifest() public {
        address arbiter = makeAddr("arbiter");
        address signer = makeAddr("signer");
        vm.setEnv("DEPLOY_ENVIRONMENT", "LOCAL_SIMULATION");
        vm.setEnv("ARBITER", vm.toString(arbiter));
        vm.setEnv("AUTH_SIGNER", vm.toString(signer));
        vm.chainId(31337);
        (FreelaXEscrow escrow, address token) = script.run();
        assertTrue(escrow.hasRole(escrow.AUTH_SIGNER_ROLE(), signer));
        assertEq(escrow.defaultArbiter(), arbiter);
        assertEq(address(escrow.token()), token);
        assertTrue(token.code.length > 0);
        string memory manifest = vm.readFile("./deployments/LOCAL_SIMULATION.json");
        assertEq(vm.parseJsonString(manifest, ".environment"), "LOCAL_SIMULATION");
        assertEq(vm.parseJsonUint(manifest, ".chainId"), 31337);
        assertEq(vm.parseJsonAddress(manifest, ".escrow"), address(escrow));
        assertEq(vm.parseJsonString(manifest, ".tokenKind"), "MOCK");
    }
}
