package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.applicationException.EnvironmentGuardException;
import com.freelax.avalanchegateway.config.TxProperties;
import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.entity.TxState;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.env.DbEndpointGuard;
import com.freelax.avalanchegateway.service.impl.TransactionSubmissionServiceImpl;
import com.freelax.avalanchegateway.signer.DevSignerFactory;
import com.freelax.avalanchegateway.signer.Signer;
import com.freelax.avalanchegateway.signer.SignerRole;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.transaction.type.Transaction1559;
import org.web3j.crypto.transaction.type.TransactionType;

import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.security.SignatureException;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests: no Docker, no network. */
class TxUnitTest {

    private final ErrorClassifier classifier = new ErrorClassifier();

    // ---- classifier -----------------------------------------------------------------------------------------------

    @Test
    void rpcErrorsAreClassifiedAndOnlyPermanentRejectionIsDefinitive() {
        assertEquals(ErrorClass.ALREADY_KNOWN, classifier.classifyRpcError("transaction already imported"));
        assertEquals(ErrorClass.ALREADY_KNOWN, classifier.classifyRpcError("already known"));
        assertEquals(ErrorClass.NONCE_TOO_LOW, classifier.classifyRpcError("nonce too low: next nonce 5, tx nonce 3"));
        assertEquals(ErrorClass.UNDERPRICED, classifier.classifyRpcError("replacement transaction underpriced"));
        assertEquals(ErrorClass.UNDERPRICED, classifier.classifyRpcError("max fee per gas less than block base fee"));
        assertEquals(ErrorClass.INSUFFICIENT_FUNDS, classifier.classifyRpcError("insufficient funds for gas * price + value"));
        assertEquals(ErrorClass.PERMANENT_REJECTION, classifier.classifyRpcError("intrinsic gas too low"));
        assertEquals(ErrorClass.PERMANENT_REJECTION, classifier.classifyRpcError("exceeds block gas limit"));
        assertEquals(ErrorClass.PERMANENT_REJECTION, classifier.classifyRpcError("intrinsic gas too high -- tx.gas_limit > env.block.gas_limit"));
        assertEquals(ErrorClass.UNCLASSIFIED_RPC_ERROR, classifier.classifyRpcError("something unexpected"));
        assertEquals(ErrorClass.UNCLASSIFIED_RPC_ERROR, classifier.classifyRpcError(null));
    }

    @Test
    void transportFailuresAreNeverDefinitive() {
        assertEquals(ErrorClass.RATE_LIMITED, classifier.classifyTransport(new RuntimeException("Invalid response received: 429; <html>")));
        assertEquals(ErrorClass.RATE_LIMITED, classifier.classifyTransport(new RuntimeException("wrapper", new RuntimeException("Too Many Requests"))));
        assertEquals(ErrorClass.TRANSPORT, classifier.classifyTransport(new SocketTimeoutException("timeout")));
        assertEquals(ErrorClass.TRANSPORT, classifier.classifyTransport(new RuntimeException()));
    }

    // ---- state machine --------------------------------------------------------------------------------------------

    @Test
    void terminalStatesAreFinalAndConfirmedOnlyMovesToFinalized() {
        for (TxState terminal : new TxState[]{TxState.FINALIZED, TxState.REVERTED, TxState.FAILED, TxState.REPLACED}) {
            assertTrue(terminal.terminal());
            for (TxState to : TxState.values()) {
                assertFalse(TxTransitions.allowed(terminal, to), terminal + " -> " + to);
            }
        }
        assertTrue(TxTransitions.allowed(TxState.CONFIRMED, TxState.FINALIZED));
        assertFalse(TxTransitions.allowed(TxState.CONFIRMED, TxState.UNKNOWN));
        assertFalse(TxTransitions.allowed(TxState.CONFIRMED, TxState.REVERTED));
        assertFalse(TxTransitions.allowed(TxState.CONFIRMED, TxState.SUBMITTED));
    }

    @Test
    void createdMayOnlyBeSignedOrFailedAndSubmissionIsNeverSuccess() {
        assertTrue(TxTransitions.allowed(TxState.CREATED, TxState.SIGNED));
        assertTrue(TxTransitions.allowed(TxState.CREATED, TxState.FAILED));
        assertFalse(TxTransitions.allowed(TxState.CREATED, TxState.SUBMITTED));
        assertFalse(TxTransitions.allowed(TxState.CREATED, TxState.CONFIRMED));
        assertFalse(TxState.SUBMITTED.terminal());
        assertFalse(TxState.PENDING.terminal());
        assertFalse(TxState.UNKNOWN.terminal());
        assertFalse(TxState.CONFIRMED.terminal());
    }

    // ---- signing --------------------------------------------------------------------------------------------------

    private static TxRecord record(Signer signer, long chainId) {
        return new TxRecord(1, "op", "LOCAL_SIMULATION", chainId, signer.role().name(), signer.address(),
                "0x1111111111111111111111111111111111111111", "0xdeadbeef", 100_000,
                BigInteger.valueOf(3_000_000_000L), BigInteger.valueOf(1_000_000_000L), 7, null, null, null, TxState.CREATED,
                null, null, null, null, null, 0, false, 0, 0, 0, 0, null, null);
    }

    @Test
    void signedTransactionIsEip1559BoundToTheChainAndRecoversToTheSigner() {
        Signer signer = DevSignerFactory.create(Harness.LOCAL, SignerRole.RELAYER);
        TransactionSigning.Signed signed = TransactionSigning.sign(record(signer, 31337), signer);
        SignedRawTransaction decoded = (SignedRawTransaction) TransactionDecoder.decode(signed.rawHex());
        assertEquals(TransactionType.EIP1559, decoded.getType());
        assertEquals(31337L, ((Transaction1559) decoded.getTransaction()).getChainId(), "the chain id is signed into the transaction");
        assertEquals(BigInteger.valueOf(7), decoded.getNonce());
        assertEquals(BigInteger.ZERO, decoded.getValue());
        assertEquals("0x1111111111111111111111111111111111111111", decoded.getTo());
        try {
            assertEquals(signer.address().toLowerCase(), decoded.getFrom().toLowerCase());
        } catch (SignatureException e) {
            throw new AssertionError(e);
        }
        assertTrue(signed.txHash().startsWith("0x") && signed.txHash().length() == 66);
    }

    @Test
    void signingRefusesAChainOrAddressThatDiffersFromTheSignersBinding() {
        Signer signer = DevSignerFactory.create(Harness.LOCAL, SignerRole.RELAYER);
        Signer other = DevSignerFactory.create(Harness.LOCAL, SignerRole.ADMIN);
        assertThrows(IllegalStateException.class, () -> TransactionSigning.sign(record(signer, 43113), signer));
        assertThrows(IllegalStateException.class, () -> TransactionSigning.sign(record(signer, 31337), other));
    }

    // ---- guards ---------------------------------------------------------------------------------------------------

    @Test
    void submissionServiceCannotBeBuiltForFuji() {
        ValidatedEnvironment fuji = new ValidatedEnvironment(AvalancheEnvironment.AVALANCHE_FUJI, 43113, "avalanchego", "api.avax-test.network");
        TxProperties props = Harness.props(0, 2);
        EnvironmentGuardException e = assertThrows(EnvironmentGuardException.class, () ->
                new TransactionSubmissionServiceImpl(null, fuji, null, null, null, null, props, classifier, null, Clock.systemUTC(), null));
        assertEquals("SIGNER_FORBIDDEN_ENVIRONMENT", e.code());
    }

    private static String dbCode(String url) {
        try {
            DbEndpointGuard.check(url);
            return "OK";
        } catch (EnvironmentGuardException e) {
            return e.code();
        }
    }

    @Test
    void databaseUrlMustBeLocalAndNamedAvalancheGateway() {
        assertEquals("OK", dbCode("jdbc:mysql://localhost:3306/avalanche_gateway"));
        assertEquals("OK", dbCode("jdbc:mysql://127.0.0.1:3307/avalanche_gateway?useUnicode=true&characterEncoding=UTF-8"));
        assertEquals("OK", dbCode("jdbc:mysql://mysql:3306/avalanche_gateway"));
        assertEquals("DB_URL_MISSING", dbCode(null));
        assertEquals("DB_URL_MISSING", dbCode("  "));
        assertEquals("DB_URL_INVALID", dbCode("jdbc:postgresql://localhost:5432/avalanche_gateway"));
        assertEquals("DB_URL_INVALID", dbCode("jdbc:mysql://user:pw@localhost:3306/avalanche_gateway"));
        assertEquals("DB_HOST_NOT_LOCAL", dbCode("jdbc:mysql://db.example.com:3306/avalanche_gateway"));
        assertEquals("DB_NAME_NOT_ALLOWED", dbCode("jdbc:mysql://localhost:3306/solana_gateway"));
        assertEquals("DB_NAME_NOT_ALLOWED", dbCode("jdbc:mysql://localhost:3306/marketplace"));
        assertEquals("DB_NAME_NOT_ALLOWED", dbCode("jdbc:mysql://localhost:3306/"));
    }
}
