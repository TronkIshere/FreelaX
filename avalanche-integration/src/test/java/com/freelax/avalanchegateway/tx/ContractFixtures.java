package com.freelax.avalanchegateway.tx;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deploys the real compiled MockUSDC and FreelaXEscrow to the local Anvil with its unlocked account (test infrastructure only). */
final class ContractFixtures {

    static final String ANVIL_ACCOUNT_0 = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    record Stack(String usdc, String escrow) {
    }

    private ContractFixtures() {
    }

    static String resource(String name) {
        try (var in = ContractFixtures.class.getResourceAsStream("/contracts/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sendFromAdmin(String url, String to, String data) {
        String params = "{\"from\":\"" + ANVIL_ACCOUNT_0 + "\"" + (to == null ? "" : ",\"to\":\"" + to + "\"") + ",\"data\":\"" + data + "\",\"gas\":\"0x1c9c380\"}";
        Matcher m = Pattern.compile("\"result\":\"(0x[0-9a-fA-F]{64})\"").matcher(AnvilRpc.call(url, "eth_sendTransaction", params));
        if (!m.find()) {
            throw new IllegalStateException("transaction not accepted");
        }
        return m.group(1);
    }

    static String deploy(String url, String creationCode) {
        String hash = sendFromAdmin(url, null, creationCode);
        for (int i = 0; i < 80; i++) {
            Matcher m = Pattern.compile("\"contractAddress\":\"(0x[0-9a-fA-F]{40})\"").matcher(AnvilRpc.call(url, "eth_getTransactionReceipt", "\"" + hash + "\""));
            if (m.find()) {
                return m.group(1).toLowerCase(Locale.ROOT);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("deployment not mined");
    }

    static Stack deployStack(String url, String arbiter) {
        String usdc = deploy(url, resource("MockUSDC.bin"));
        String args = FunctionEncoder.encodeConstructor(List.of(new Address(ANVIL_ACCOUNT_0), new Address(usdc), new Address(arbiter)));
        return new Stack(usdc, deploy(url, resource("FreelaXEscrow.bin") + args));
    }

    static Path writeManifest(Path file, String environment, long chainId, Stack stack, long startBlock) throws IOException {
        String json = "{\"schemaVersion\":1,\"environment\":\"" + environment + "\",\"chainId\":" + chainId + ",\"escrow\":\"" + stack.escrow()
                + "\",\"token\":\"" + stack.usdc() + "\",\"tokenKind\":\"MOCK\",\"admin\":\"" + ANVIL_ACCOUNT_0 + "\",\"arbiter\":\"" + ANVIL_ACCOUNT_0
                + "\",\"authSigner\":\"" + ANVIL_ACCOUNT_0 + "\",\"contractVersion\":\"1\",\"startBlock\":" + startBlock + "}";
        Files.writeString(file, json);
        return file;
    }
}
