package lab.phase1;

import lab.phase1.EnvGuard.Env;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Part B task F. LOCAL CRYPTOGRAPHIC VALIDATION ONLY. {@code FakeKms} imitates the *shape* of a KMS Sign call
 * (non-exportable key, DER-encoded ECDSA over a 32-byte digest, NON-deterministic nonce so high-s happens about half the time).
 * It is NOT a real KMS and says nothing about any provider's API, latency, quotas or IAM. Ephemeral key, anvil only.
 */
class KmsConversionTest {
    static final String RPC = "http://localhost:8545";
    static final long CHAIN = 31337;
    static final BigInteger PRIO = BigInteger.valueOf(1_000_000_000L), MAXFEE = BigInteger.valueOf(20_000_000_000L);
    static Web3j web3j;
    static Rpc raw;
    static FakeKms kms;
    static Credentials sender;
    static String labAddr;

    static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    static final ECDomainParameters CURVE = new ECDomainParameters(CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());

    /** Holds the key privately; exposes only publicKey() and signDigest() -> DER, like a KMS. */
    static final class FakeKms {
        private final ECPrivateKeyParameters priv; final BigInteger publicKey; final String address;
        FakeKms() {
            ECKeyPair kp; try { kp = Keys.createEcKeyPair(); } catch (Exception e) { throw new RuntimeException(e); }
            priv = new ECPrivateKeyParameters(kp.getPrivateKey(), CURVE); publicKey = kp.getPublicKey(); address = "0x" + Keys.getAddress(kp.getPublicKey());
        }
        byte[] signDigest(byte[] digest32) {
            ECDSASigner s = new ECDSASigner();                                 // default: RANDOM k (not RFC 6979), like many HSM/KMS implementations
            s.init(true, new org.bouncycastle.crypto.params.ParametersWithRandom(priv, new SecureRandom()));
            BigInteger[] rs = s.generateSignature(digest32);
            try {
                ASN1EncodableVector v = new ASN1EncodableVector(); v.add(new ASN1Integer(rs[0])); v.add(new ASN1Integer(rs[1]));
                return new DERSequence(v).getEncoded();
            } catch (IOException e) { throw new RuntimeException(e); }
        }
    }

    record Rsv(BigInteger r, BigInteger s, int recId, boolean wasHighS) { }

    static final BigInteger HALF_N = CURVE_PARAMS.getN().shiftRight(1);

    /** DER -> (r, s low-normalised, recovery id) for a known expected public key. */
    static Rsv derToRsv(byte[] der, byte[] digest, BigInteger expectedPub) {
        ASN1Sequence seq = ASN1Sequence.getInstance(der);
        BigInteger r = ((ASN1Integer) seq.getObjectAt(0)).getValue(), s = ((ASN1Integer) seq.getObjectAt(1)).getValue();
        boolean high = s.compareTo(HALF_N) > 0;
        if (high) s = CURVE_PARAMS.getN().subtract(s);                  // low-s normalisation (EIP-2 / OZ ECDSA requirement)
        ECDSASignature sig = new ECDSASignature(r, s);
        for (int rec = 0; rec < 2; rec++) {
            BigInteger pub = Sign.recoverFromSignature(rec, sig, digest);
            if (expectedPub.equals(pub)) return new Rsv(r, s, rec, high);
        }
        throw new IllegalStateException("no recovery id matches the expected key");
    }

    static String enc(String fn, Type<?>... a) { return FunctionEncoder.encode(new Function(fn, Arrays.asList(a), List.of())); }

    @BeforeAll
    static void setup() throws Exception {
        EnvGuard.checkEndpoint(Env.LOCAL_SIMULATION, RPC);
        web3j = Web3j.build(new HttpService(RPC)); raw = new Rpc(RPC, java.time.Duration.ofSeconds(10));
        EnvGuard.check(Env.LOCAL_SIMULATION, RPC, web3j.ethChainId().send().getChainId().longValue());
        kms = new FakeKms();
        sender = Credentials.create(Keys.createEcKeyPair());
        raw.result("anvil_setBalance", sender.getAddress(), "0x56bc75e2d63100000");
        raw.result("anvil_setBalance", kms.address, "0x56bc75e2d63100000");
        String bin = new String(KmsConversionTest.class.getResourceAsStream("/Phase1Lab.bin").readAllBytes(), StandardCharsets.UTF_8).trim();
        String ctor = Numeric.cleanHexPrefix(FunctionEncoder.encodeConstructor(List.of(new Address(sender.getAddress()))));
        RawTransaction tx = RawTransaction.createTransaction(CHAIN, BigInteger.ZERO, BigInteger.valueOf(3_000_000), "", BigInteger.ZERO, bin + ctor, PRIO, MAXFEE);
        String h = web3j.ethSendRawTransaction(Numeric.toHexString(TransactionEncoder.signMessage(tx, CHAIN, sender))).send().getTransactionHash();
        TransactionReceipt rc = null; for (int i = 0; i < 50 && rc == null; i++) { Thread.sleep(100); rc = web3j.ethGetTransactionReceipt(h).send().getTransactionReceipt().orElse(null); }
        labAddr = rc.getContractAddress();
        // grant AUTH_SIGNER_ROLE to the KMS-held key's address
        RawTransaction g = RawTransaction.createTransaction(CHAIN, BigInteger.ONE, BigInteger.valueOf(200_000), labAddr, BigInteger.ZERO,
                enc("grantRole", new Bytes32(Hash.sha3("AUTH_SIGNER_ROLE".getBytes(StandardCharsets.UTF_8))), new Address(kms.address)), PRIO, MAXFEE);
        web3j.ethSendRawTransaction(Numeric.toHexString(TransactionEncoder.signMessage(g, CHAIN, sender))).send(); Thread.sleep(500);
    }

    @Test
    void derToRsvConversion_300Signatures_highSNormalisedAndRecoveryParityCorrect() throws Exception {
        SecureRandom rnd = new SecureRandom(); int high = 0, recId0 = 0, recId1 = 0;
        for (int i = 0; i < 300; i++) {
            byte[] digest = new byte[32]; rnd.nextBytes(digest);
            Rsv x = derToRsv(kms.signDigest(digest), digest, kms.publicKey);
            assertTrue(x.s.compareTo(HALF_N) <= 0, "s must be low after normalisation");
            assertEquals(kms.address.toLowerCase(), "0x" + Keys.getAddress(Sign.signedMessageHashToKey(digest, new Sign.SignatureData((byte) (27 + x.recId), Numeric.toBytesPadded(x.r, 32), Numeric.toBytesPadded(x.s, 32)))));
            if (x.wasHighS) high++;
            if (x.recId == 0) recId0++; else recId1++;
        }
        assertTrue(high > 60 && high < 240, "random-k ECDSA must produce a mix of low and high s: " + high);
        Evidence.rec("K1-der-to-rsv-300-signatures", "PASS", Evidence.of("signatures", 300, "highSBeforeNormalisation", high, "recId0", recId0, "recId1", recId1,
                "scope", "local cryptographic validation with a fake KMS; NOT a provider compatibility test"));
    }

    @Test
    void eip712AuthorizationSignedByFakeKms_isAcceptedOnChain_andHighSIsRejected() throws Exception {
        byte[] action = Hash.sha3("FUND".getBytes(StandardCharsets.UTF_8));
        BigInteger dl = BigInteger.valueOf(System.currentTimeMillis() / 1000 + 3600);
        String json = ("{\"types\":{\"EIP712Domain\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"version\",\"type\":\"string\"},{\"name\":\"chainId\",\"type\":\"uint256\"},{\"name\":\"verifyingContract\",\"type\":\"address\"}],"
                + "\"Authorization\":[{\"name\":\"account\",\"type\":\"address\"},{\"name\":\"action\",\"type\":\"bytes32\"},{\"name\":\"nonce\",\"type\":\"uint256\"},{\"name\":\"deadline\",\"type\":\"uint256\"}]},"
                + "\"primaryType\":\"Authorization\",\"domain\":{\"name\":\"FreelaXPhase1Lab\",\"version\":\"1\",\"chainId\":%d,\"verifyingContract\":\"%s\"},"
                + "\"message\":{\"account\":\"%s\",\"action\":\"%s\",\"nonce\":\"%s\",\"deadline\":\"%s\"}}")
                .formatted(CHAIN, labAddr, sender.getAddress(), Numeric.toHexString(action), "5001", dl);
        byte[] digest = new StructuredDataEncoder(json).hashStructuredData();
        // sign until we hold a sample that WAS high-s so both outcomes are demonstrated
        Rsv good = null; byte[] highDer = null; BigInteger highR = null, highS = null;
        for (int i = 0; i < 40 && (good == null || highDer == null); i++) {
            byte[] der = kms.signDigest(digest);
            Rsv x = derToRsv(der, digest, kms.publicKey);
            if (good == null) good = x;
            if (x.wasHighS && highDer == null) { highDer = der; highR = x.r; highS = ((ASN1Integer) ASN1Sequence.getInstance(der).getObjectAt(1)).getValue(); }
        }
        assertNotNull(highDer, "expected at least one high-s sample in 40 tries");
        byte[] sigOk = concat(Numeric.toBytesPadded(good.r, 32), Numeric.toBytesPadded(good.s, 32), (byte) (27 + good.recId));
        String call = enc("useAuthorization", new Bytes32(action), new Uint256(5001), new Uint256(dl), new DynamicBytes(sigOk));
        var ethCallOk = web3j.ethCall(org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(sender.getAddress(), labAddr, call), DefaultBlockParameterName.LATEST).send();
        assertFalse(ethCallOk.hasError(), () -> String.valueOf(ethCallOk.getError()));       // valid (low-s) signature from the KMS-held key accepted
        // the un-normalised high-s signature (valid ECDSA, but malleable) must be refused by OpenZeppelin ECDSA
        int recHigh = -1;
        for (int rec = 0; rec < 2; rec++) { BigInteger pub = Sign.recoverFromSignature(rec, new ECDSASignature(highR, CURVE_PARAMS.getN().subtract(highS)), digest); if (kms.publicKey.equals(pub)) recHigh = rec; }
        // with high s the recovery id flips; send r, highS, flipped v
        byte[] sigHigh = concat(Numeric.toBytesPadded(highR, 32), Numeric.toBytesPadded(highS, 32), (byte) (27 + (1 - recHigh)));
        String callHigh = enc("useAuthorization", new Bytes32(action), new Uint256(5001), new Uint256(dl), new DynamicBytes(sigHigh));
        var ethCallHigh = web3j.ethCall(org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(sender.getAddress(), labAddr, callHigh), DefaultBlockParameterName.LATEST).send();
        assertTrue(ethCallHigh.hasError(), "high-s signature must revert");
        String sel = Numeric.toHexString(Hash.sha3("ECDSAInvalidSignatureS(bytes32)".getBytes(StandardCharsets.UTF_8))).substring(0, 10);
        String data = String.valueOf(ethCallHigh.getError().getData()).replace("\"", "");
        Evidence.rec("K2-eip712-digest-signed-by-fake-kms", "PASS", Evidence.of("lowSAccepted", true, "highSRejected", true, "highSRevertIsECDSAInvalidSignatureS", data.startsWith(sel), "revertData", data.length() > 10 ? data.substring(0, 10) : data));
    }

    @Test
    void transactionSignedByFakeKms_isAcceptedAndSenderRecoversToKmsAddress() throws Exception {
        RawTransaction tx = RawTransaction.createTransaction(CHAIN, BigInteger.ZERO, BigInteger.valueOf(100_000), labAddr, BigInteger.ZERO,
                enc("actUntil", new Uint256(BigInteger.TWO.pow(60))), PRIO, MAXFEE);
        byte[] digest = Hash.sha3(TransactionEncoder.encode(tx));                    // signing hash of the unsigned typed transaction (EIP-1559)
        Rsv x = derToRsv(kms.signDigest(digest), digest, kms.publicKey);
        Sign.SignatureData sd = new Sign.SignatureData((byte) (27 + x.recId), Numeric.toBytesPadded(x.r, 32), Numeric.toBytesPadded(x.s, 32));   // web3j expects 27/28 here and writes yParity itself for typed tx
        byte[] signed = TransactionEncoder.encode(tx, sd);
        var resp = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        assertFalse(resp.hasError(), () -> String.valueOf(resp.getError()));
        TransactionReceipt rc = null; for (int i = 0; i < 50 && rc == null; i++) { Thread.sleep(100); rc = web3j.ethGetTransactionReceipt(resp.getTransactionHash()).send().getTransactionReceipt().orElse(null); }
        assertNotNull(rc); assertTrue(rc.isStatusOK());
        assertEquals(kms.address.toLowerCase(), rc.getFrom().toLowerCase());                // node recovered the same address from (r,s,yParity)
        assertEquals(Numeric.toHexString(Hash.sha3(signed)), resp.getTransactionHash());
        Evidence.rec("K3-eip1559-tx-signed-by-fake-kms", "PASS", Evidence.of("receiptFromEqualsKmsAddress", true, "yParity", x.recId, "wasHighS", x.wasHighS, "status", rc.getStatus()));
        Evidence.flush("kms-conversion-local.json");
    }

    static byte[] concat(byte[] a, byte[] b, byte v) { byte[] o = new byte[a.length + b.length + 1]; System.arraycopy(a, 0, o, 0, a.length); System.arraycopy(b, 0, o, a.length, b.length); o[o.length - 1] = v; return o; }
}
