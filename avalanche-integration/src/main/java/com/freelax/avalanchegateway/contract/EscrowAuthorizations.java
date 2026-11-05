package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.signer.Eip712Digest;
import com.freelax.avalanchegateway.signer.Signature;
import com.freelax.avalanchegateway.signer.Signer;

/**
 * EIP-712 typed data for the escrow's authorizations. Every digest binds the chain id and the verifying contract through the
 * domain, plus the escrow id, the action (the struct type), the nonce and a deadline. These builders only describe what to sign:
 * the decision WHETHER to authorize something comes from the caller's recorded business state, never from here.
 */
public final class EscrowAuthorizations {

    private static final String DOMAIN_TYPE =
            "\"EIP712Domain\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"version\",\"type\":\"string\"},"
                    + "{\"name\":\"chainId\",\"type\":\"uint256\"},{\"name\":\"verifyingContract\",\"type\":\"address\"}]";

    private final long chainId;
    private final String verifyingContract;

    public EscrowAuthorizations(long chainId, String verifyingContract) {
        this.chainId = chainId;
        this.verifyingContract = verifyingContract;
    }

    public String fundTypedData(FundTerms t, long nonce, long deadline) {
        String type = "\"FundAuthorization\":[{\"name\":\"escrowId\",\"type\":\"bytes32\"},{\"name\":\"client\",\"type\":\"address\"},"
                + "{\"name\":\"freelancer\",\"type\":\"address\"},{\"name\":\"token\",\"type\":\"address\"},{\"name\":\"amount\",\"type\":\"uint256\"},"
                + "{\"name\":\"fundingExpiresAt\",\"type\":\"uint64\"},{\"name\":\"deliveryDueAt\",\"type\":\"uint64\"},"
                + "{\"name\":\"reviewWindowHours\",\"type\":\"uint32\"},{\"name\":\"maxRevisions\",\"type\":\"uint8\"},"
                + "{\"name\":\"highValueGrace\",\"type\":\"bool\"},{\"name\":\"arbiter\",\"type\":\"address\"},"
                + "{\"name\":\"nonce\",\"type\":\"uint64\"},{\"name\":\"deadline\",\"type\":\"uint64\"}]";
        String message = "{\"escrowId\":\"" + t.escrowId() + "\",\"client\":\"" + t.client() + "\",\"freelancer\":\"" + t.freelancer()
                + "\",\"token\":\"" + t.token() + "\",\"amount\":\"" + t.amount() + "\",\"fundingExpiresAt\":" + t.fundingExpiresAt()
                + ",\"deliveryDueAt\":" + t.deliveryDueAt() + ",\"reviewWindowHours\":" + t.reviewWindowHours()
                + ",\"maxRevisions\":" + t.maxRevisions() + ",\"highValueGrace\":" + t.highValueGrace() + ",\"arbiter\":\""
                + t.arbiter() + "\",\"nonce\":" + nonce + ",\"deadline\":" + deadline + "}";
        return typedData("FundAuthorization", type, message);
    }

    public String submitTypedData(String escrowId, String evidenceHash, long submissionCount, long nonce, long deadline) {
        String type = "\"SubmitAuthorization\":[{\"name\":\"escrowId\",\"type\":\"bytes32\"},{\"name\":\"evidenceHash\",\"type\":\"bytes32\"},"
                + "{\"name\":\"submissionCount\",\"type\":\"uint32\"},{\"name\":\"nonce\",\"type\":\"uint64\"},{\"name\":\"deadline\",\"type\":\"uint64\"}]";
        String message = "{\"escrowId\":\"" + escrowId + "\",\"evidenceHash\":\"" + evidenceHash + "\",\"submissionCount\":" + submissionCount
                + ",\"nonce\":" + nonce + ",\"deadline\":" + deadline + "}";
        return typedData("SubmitAuthorization", type, message);
    }

    public String revisionTypedData(String escrowId, String feedbackHash, int revisionsUsed, long nonce, long deadline) {
        String type = "\"RevisionAuthorization\":[{\"name\":\"escrowId\",\"type\":\"bytes32\"},{\"name\":\"feedbackHash\",\"type\":\"bytes32\"},"
                + "{\"name\":\"revisionsUsed\",\"type\":\"uint8\"},{\"name\":\"nonce\",\"type\":\"uint64\"},{\"name\":\"deadline\",\"type\":\"uint64\"}]";
        String message = "{\"escrowId\":\"" + escrowId + "\",\"feedbackHash\":\"" + feedbackHash + "\",\"revisionsUsed\":" + revisionsUsed
                + ",\"nonce\":" + nonce + ",\"deadline\":" + deadline + "}";
        return typedData("RevisionAuthorization", type, message);
    }

    public String mutualRefundTypedData(String escrowId, long nonce, long deadline) {
        String type = "\"MutualRefund\":[{\"name\":\"escrowId\",\"type\":\"bytes32\"},{\"name\":\"nonce\",\"type\":\"uint64\"},{\"name\":\"deadline\",\"type\":\"uint64\"}]";
        String message = "{\"escrowId\":\"" + escrowId + "\",\"nonce\":" + nonce + ",\"deadline\":" + deadline + "}";
        return typedData("MutualRefund", type, message);
    }

    /**
     * R12: typed data both parties sign to settle a Disputed escrow. It binds the outcome, the full amount, the settlement
     * record hash and the dispute being settled ({@code disputedAt}), so a pair signed for another dispute or amount is invalid.
     */
    public String mutualSettlementTypedData(String escrowId, boolean releaseToFreelancer, java.math.BigInteger amount, String settlementHash,
                                            long disputedAt, long nonce, long deadline) {
        String type = "\"MutualSettlement\":[{\"name\":\"escrowId\",\"type\":\"bytes32\"},{\"name\":\"releaseToFreelancer\",\"type\":\"bool\"},"
                + "{\"name\":\"amount\",\"type\":\"uint256\"},{\"name\":\"settlementHash\",\"type\":\"bytes32\"},"
                + "{\"name\":\"disputedAt\",\"type\":\"uint64\"},{\"name\":\"nonce\",\"type\":\"uint64\"},{\"name\":\"deadline\",\"type\":\"uint64\"}]";
        String message = "{\"escrowId\":\"" + escrowId + "\",\"releaseToFreelancer\":" + releaseToFreelancer + ",\"amount\":\"" + amount
                + "\",\"settlementHash\":\"" + settlementHash + "\",\"disputedAt\":" + disputedAt + ",\"nonce\":" + nonce
                + ",\"deadline\":" + deadline + "}";
        return typedData("MutualSettlement", type, message);
    }

    public static byte[] digest(String typedDataJson) {
        return Eip712Digest.hash(typedDataJson);
    }

    /** Signs a typed-data digest with the given signer (the AUTHORIZATION signer, or a party's own development wallet). */
    public static Signature sign(Signer signer, String typedDataJson) {
        return signer.sign(Eip712Digest.hash(typedDataJson));
    }

    private String typedData(String primaryType, String structType, String message) {
        return "{\"types\":{" + DOMAIN_TYPE + "," + structType + "},\"primaryType\":\"" + primaryType
                + "\",\"domain\":{\"name\":\"FreelaXEscrow\",\"version\":\"1\",\"chainId\":" + chainId
                + ",\"verifyingContract\":\"" + verifyingContract + "\"},\"message\":" + message + "}";
    }
}
