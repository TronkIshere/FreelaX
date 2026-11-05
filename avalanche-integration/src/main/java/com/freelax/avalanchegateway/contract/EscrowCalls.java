package com.freelax.avalanchegateway.contract;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;

/** ABI encoding of escrow and ERC-20 calls (calldata only: signing and sending belong to the submission service). */
public final class EscrowCalls {

    private EscrowCalls() {
    }

    private static Bytes32 b32(String hex) {
        return new Bytes32(Numeric.hexStringToByteArray(hex));
    }

    private static DynamicBytes bytes(byte[] value) {
        return new DynamicBytes(value);
    }

    private static String encode(String name, Type<?>... inputs) {
        return FunctionEncoder.encode(new Function(name, List.of(inputs), List.of()));
    }

    static StaticStruct terms(FundTerms t) {
        return new StaticStruct(b32(t.escrowId()), new Address(t.client()), new Address(t.freelancer()), new Address(t.token()),
                new Uint256(t.amount()), new Uint64(t.fundingExpiresAt()), new Uint64(t.deliveryDueAt()),
                new Uint32(t.reviewWindowHours()), new Uint8(t.maxRevisions()), new Bool(t.highValueGrace()), new Address(t.arbiter()));
    }

    public static String fund(FundTerms terms, long deadline, byte[] signature) {
        return encode("fund", terms(terms), new Uint64(deadline), bytes(signature));
    }

    public static String requestExtension(String escrowId, long newDeliveryDueAt) {
        return encode("requestExtension", b32(escrowId), new Uint64(newDeliveryDueAt));
    }

    public static String approveExtension(String escrowId) {
        return encode("approveExtension", b32(escrowId));
    }

    public static String submit(String escrowId, String evidenceHash, long deadline, byte[] signature) {
        return encode("submit", b32(escrowId), b32(evidenceHash), new Uint64(deadline), bytes(signature));
    }

    public static String requestRevision(String escrowId, String feedbackHash, long deadline, byte[] signature) {
        return encode("requestRevision", b32(escrowId), b32(feedbackHash), new Uint64(deadline), bytes(signature));
    }

    public static String openDispute(String escrowId, String reasonHash) {
        return encode("openDispute", b32(escrowId), b32(reasonHash));
    }

    public static String release(String escrowId) {
        return encode("release", b32(escrowId));
    }

    public static String resolveDispute(String escrowId, boolean releaseToFreelancer, String resolutionHash) {
        return encode("resolveDispute", b32(escrowId), new Bool(releaseToFreelancer), b32(resolutionHash));
    }

    public static String mutualRefund(String escrowId, long deadline, byte[] clientSignature, byte[] freelancerSignature) {
        return encode("mutualRefund", b32(escrowId), new Uint64(deadline), bytes(clientSignature), bytes(freelancerSignature));
    }

    /** R12: settlement of a Disputed escrow signed by both parties (client signature first); anyone may relay it. */
    public static String mutualSettleDispute(String escrowId, boolean releaseToFreelancer, String settlementHash, long deadline,
                                             byte[] clientSignature, byte[] freelancerSignature) {
        return encode("mutualSettleDispute", b32(escrowId), new Bool(releaseToFreelancer), b32(settlementHash), new Uint64(deadline),
                bytes(clientSignature), bytes(freelancerSignature));
    }

    public static String getEscrow(String escrowId) {
        return encode("getEscrow", b32(escrowId));
    }

    public static String erc20Approve(String spender, BigInteger amount) {
        return encode("approve", new Address(spender), new Uint256(amount));
    }

    public static String erc20Mint(String to, BigInteger amount) {
        return encode("mint", new Address(to), new Uint256(amount));
    }

    /** Test helper calldata for the contract's own digest views (digest parity checks). */
    public static String fundDigest(FundTerms terms, long nonce, long deadline) {
        return encode("fundDigest", terms(terms), new Uint64(nonce), new Uint64(deadline));
    }

    public static String submitDigest(String escrowId, String evidenceHash, long submissionCount, long nonce, long deadline) {
        return encode("submitDigest", b32(escrowId), b32(evidenceHash), new Uint32(submissionCount), new Uint64(nonce), new Uint64(deadline));
    }

    public static String revisionDigest(String escrowId, String feedbackHash, int revisionsUsed, long nonce, long deadline) {
        return encode("revisionDigest", b32(escrowId), b32(feedbackHash), new Uint8(revisionsUsed), new Uint64(nonce), new Uint64(deadline));
    }

    public static String mutualRefundDigest(String escrowId, long nonce, long deadline) {
        return encode("mutualRefundDigest", b32(escrowId), new Uint64(nonce), new Uint64(deadline));
    }

    public static String mutualSettlementDigest(String escrowId, boolean releaseToFreelancer, BigInteger amount, String settlementHash,
                                                long disputedAt, long nonce, long deadline) {
        return encode("mutualSettlementDigest", b32(escrowId), new Bool(releaseToFreelancer), new Uint256(amount), b32(settlementHash),
                new Uint64(disputedAt), new Uint64(nonce), new Uint64(deadline));
    }
}
