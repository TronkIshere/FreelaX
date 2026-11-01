package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.scan.EventDecoder;
import com.freelax.avalanchegateway.scan.ScannedEvent;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The escrow events: signatures, topic0 values (computed, never copied), registration for the scanner and field decoding. */
public final class EscrowEvents {

    public static final List<String> SIGNATURES = List.of(
            "Funded(bytes32,address,address,address,uint256,uint64,uint32,uint8)",
            "ExtensionRequested(bytes32,uint64)",
            "ExtensionApproved(bytes32,uint64)",
            "WorkSubmitted(bytes32,bytes32,uint32,uint64)",
            "RevisionRequested(bytes32,bytes32,uint8)",
            "DisputeOpened(bytes32,address,bytes32)",
            "DisputeResolved(bytes32,bool,bytes32)",
            "Released(bytes32,address,uint256,address)",
            "Refunded(bytes32,address,uint256,bool)");

    public record DecodedEvent(String name, String escrowId, Map<String, Object> fields) {
    }

    private EscrowEvents() {
    }

    public static String topic0(String signature) {
        return Hash.sha3String(signature);
    }

    public static String nameOf(String signature) {
        return signature.substring(0, signature.indexOf('('));
    }

    /** Registers every escrow event with the scanner's decoder so only these logs are accepted. */
    public static EventDecoder registerAll(EventDecoder decoder) {
        for (String signature : SIGNATURES) {
            decoder.register(topic0(signature), nameOf(signature));
        }
        return decoder;
    }

    public static DecodedEvent decode(ScannedEvent event) {
        String name = event.eventName();
        String data = event.data();
        List<String> topics = event.topics();
        Map<String, Object> f = new LinkedHashMap<>();
        switch (name) {
            case "Funded" -> {
                f.put("client", address(topics.get(2)));
                f.put("freelancer", address(topics.get(3)));
                f.put("arbiter", address(word(data, 0)));
                f.put("amount", number(data, 1));
                f.put("deliveryDueAt", number(data, 2));
                f.put("reviewWindowSeconds", number(data, 3));
                f.put("maxRevisions", number(data, 4));
            }
            case "ExtensionRequested" -> f.put("requestedDeliveryDueAt", number(data, 0));
            case "ExtensionApproved" -> f.put("deliveryDueAt", number(data, 0));
            case "WorkSubmitted" -> {
                f.put("evidenceHash", "0x" + word(data, 0));
                f.put("submissionCount", number(data, 1));
                f.put("reviewDueAt", number(data, 2));
            }
            case "RevisionRequested" -> {
                f.put("feedbackHash", "0x" + word(data, 0));
                f.put("revisionsUsed", number(data, 1));
            }
            case "DisputeOpened" -> {
                f.put("by", address(topics.get(2)));
                f.put("reasonHash", "0x" + word(data, 0));
            }
            case "DisputeResolved" -> {
                f.put("releasedToFreelancer", number(data, 0).signum() != 0);
                f.put("resolutionHash", "0x" + word(data, 1));
            }
            case "Released" -> {
                f.put("to", address(topics.get(2)));
                f.put("by", address(topics.get(3)));
                f.put("amount", number(data, 0));
            }
            case "Refunded" -> {
                f.put("to", address(topics.get(2)));
                f.put("amount", number(data, 0));
                f.put("mutual", number(data, 1).signum() != 0);
            }
            default -> throw new IllegalArgumentException("not an escrow event: " + name);
        }
        return new DecodedEvent(name, topics.get(1).toLowerCase(), f);
    }

    private static String word(String data, int index) {
        String hex = Numeric.cleanHexPrefix(data);
        return hex.substring(index * 64, index * 64 + 64);
    }

    private static BigInteger number(String data, int index) {
        return new BigInteger(word(data, index), 16);
    }

    private static String address(String wordOrTopic) {
        String hex = Numeric.cleanHexPrefix(wordOrTopic);
        return "0x" + hex.substring(hex.length() - 40).toLowerCase();
    }
}
