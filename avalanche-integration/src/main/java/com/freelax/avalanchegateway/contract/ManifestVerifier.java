package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.env.EnvironmentGuardException;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fail-closed check of a deployment manifest before the gateway uses it: environment and chain id equal the verified endpoint,
 * the asset is on the allow-list for its environment and not on the deny-list (the Mainnet USDC and the bridged USDC.e), the
 * escrow and token have code, the escrow's pinned token equals the manifest's, and the token reports 6 decimals and the USDC symbol.
 */
public final class ManifestVerifier {

    public static final String FUJI_USDC = "0x5425890298aed601595a70ab815c96711a31bc65";
    static final Set<String> DENY_LISTED = Set.of("0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e", "0xa7d7079b0fead91f3e65f86e8915cb59c1a4c664");

    private ManifestVerifier() {
    }

    public static VerifiedDeployment verify(DeploymentManifest m, ValidatedEnvironment validated, ChainClient client) {
        checkStatic(m, validated);
        if (isEmpty(client.code(m.escrow()))) {
            throw new EnvironmentGuardException("MANIFEST_CODE_MISSING", "no code at the escrow address");
        }
        if (isEmpty(client.code(m.token()))) {
            throw new EnvironmentGuardException("MANIFEST_CODE_MISSING", "no code at the token address");
        }
        String pinned = addressOf(call(client, m.escrow(), "token()"));
        if (!pinned.equals(m.token())) {
            throw new EnvironmentGuardException("MANIFEST_TOKEN_MISMATCH", "the escrow pins a different token than the manifest");
        }
        BigInteger decimals = new BigInteger(Numeric.cleanHexPrefix(call(client, m.token(), "decimals()")), 16);
        if (decimals.intValue() != 6) {
            throw new EnvironmentGuardException("ASSET_DECIMALS", "the token must have 6 decimals");
        }
        String symbol = stringOf(call(client, m.token(), "symbol()"));
        if (!"USDC".equals(symbol)) {
            throw new EnvironmentGuardException("ASSET_SYMBOL", "the token symbol must be USDC (the bridged USDC.e is refused)");
        }
        return new VerifiedDeployment(m);
    }

    /** The checks that need no network. */
    static void checkStatic(DeploymentManifest m, ValidatedEnvironment validated) {
        if (!m.environment().equals(validated.environment().name())) {
            throw new EnvironmentGuardException("MANIFEST_ENVIRONMENT_MISMATCH", "manifest " + m.environment() + " versus " + validated.environment());
        }
        if (m.chainId() != validated.chainId()) {
            throw new EnvironmentGuardException("MANIFEST_CHAIN_MISMATCH", "manifest chain " + m.chainId() + " versus " + validated.chainId());
        }
        String token = m.token().toLowerCase(Locale.ROOT);
        if (DENY_LISTED.contains(token) || DENY_LISTED.contains(m.escrow().toLowerCase(Locale.ROOT))) {
            throw new EnvironmentGuardException("ASSET_DENY_LISTED", "a deny-listed address (Mainnet USDC or USDC.e)");
        }
        boolean mock = "MOCK".equals(m.tokenKind());
        boolean circle = "CIRCLE_FUJI_USDC".equals(m.tokenKind());
        if (!mock && !circle) {
            throw new EnvironmentGuardException("ASSET_NOT_ALLOWED", "unknown token kind");
        }
        if (circle && (!token.equals(FUJI_USDC) || m.chainId() != 43113)) {
            throw new EnvironmentGuardException("ASSET_NOT_ALLOWED", "the Circle token must be the Fuji USDC on chain 43113");
        }
        if (mock && validated.environment() != AvalancheEnvironment.LOCAL_SIMULATION) {
            throw new EnvironmentGuardException("MOCK_TOKEN_FORBIDDEN", "the simulated token exists only in LOCAL_SIMULATION");
        }
        if (validated.environment() == AvalancheEnvironment.AVALANCHE_FUJI && !circle) {
            throw new EnvironmentGuardException("ASSET_NOT_ALLOWED", "AVALANCHE_FUJI accepts only the Circle Fuji USDC");
        }
    }

    private static boolean isEmpty(String code) {
        return code == null || Numeric.cleanHexPrefix(code).isEmpty();
    }

    private static String call(ChainClient client, String to, String signature) {
        String selector = Numeric.toHexString(Hash.sha3(signature.getBytes(StandardCharsets.UTF_8)), 0, 4, true);
        ChainClient.CallResult result = client.call(to, selector);
        if (result.reverted() || result.data() == null || Numeric.cleanHexPrefix(result.data()).isEmpty()) {
            throw new EnvironmentGuardException("MANIFEST_CALL_FAILED", signature + " did not answer at " + to);
        }
        return result.data();
    }

    private static String addressOf(String word) {
        String hex = Numeric.cleanHexPrefix(word);
        return "0x" + hex.substring(hex.length() - 40).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String stringOf(String data) {
        List<TypeReference<Type>> outputs = new java.util.ArrayList<>();
        outputs.add((TypeReference) new TypeReference<Utf8String>() { });
        List<Type> decoded = FunctionReturnDecoder.decode(data, outputs);
        return decoded.isEmpty() ? "" : ((Utf8String) decoded.get(0)).getValue();
    }
}
