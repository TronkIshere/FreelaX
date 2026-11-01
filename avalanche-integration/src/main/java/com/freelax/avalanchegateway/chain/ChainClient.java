package com.freelax.avalanchegateway.chain;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

/**
 * Thin, paced wrapper over web3j. Transport failures (timeouts, HTTP 429, connection errors) surface as
 * {@link ChainClientException}: they are never evidence about chain state. A null lookup result is returned as empty.
 */
public class ChainClient {

    private final Web3j web3j;
    private final RpcPacer pacer;

    public ChainClient(Web3j web3j, RpcPacer pacer) {
        this.web3j = web3j;
        this.pacer = pacer;
    }

    public long chainId() {
        return call("eth_chainId", () -> web3j.ethChainId().send(), r -> r.getChainId().longValueExact());
    }

    public String clientVersion() {
        return call("web3_clientVersion", () -> web3j.web3ClientVersion().send(), r -> r.getWeb3ClientVersion());
    }

    public long transactionCount(String address, DefaultBlockParameterName tag) {
        return call("eth_getTransactionCount", () -> web3j.ethGetTransactionCount(address, tag).send(),
                r -> r.getTransactionCount().longValueExact());
    }

    public long latestBlockNumber() {
        return call("eth_blockNumber", () -> web3j.ethBlockNumber().send(), r -> r.getBlockNumber().longValueExact());
    }

    /** The finalized block under the tag policy (Fuji). Not used by LOCAL_SIMULATION, where Anvil's tag does not advance. */
    public long finalizedBlockNumber() {
        return blockNumber(DefaultBlockParameterName.FINALIZED);
    }

    public BigInteger latestBaseFee() {
        EthBlock.Block block = block(DefaultBlockParameterName.LATEST);
        if (block.getBaseFeePerGas() == null) {
            throw new ChainClientException("the latest block has no baseFeePerGas");
        }
        return block.getBaseFeePerGas();
    }

    /** Raw submission. An RPC error is returned as a message; only transport failures throw. */
    public SendOutcome sendRawTransaction(String signedHex) {
        pacer.await();
        EthSendTransaction response;
        try {
            response = web3j.ethSendRawTransaction(signedHex).send();
        } catch (IOException | RuntimeException e) {
            throw new ChainClientException("eth_sendRawTransaction failed: " + e.getClass().getSimpleName(), e);
        }
        if (response.hasError()) {
            return SendOutcome.rejected(response.getError().getMessage());
        }
        return SendOutcome.accepted(response.getTransactionHash());
    }

    public Optional<TxReceipt> receipt(String txHash) {
        return call("eth_getTransactionReceipt", () -> web3j.ethGetTransactionReceipt(txHash).send(),
                r -> r.getTransactionReceipt().map(ChainClient::toReceipt));
    }

    public boolean transactionExists(String txHash) {
        return call("eth_getTransactionByHash", () -> web3j.ethGetTransactionByHash(txHash).send(),
                r -> r.getTransaction().isPresent());
    }

    public void shutdown() {
        web3j.shutdown();
    }

    private static TxReceipt toReceipt(TransactionReceipt r) {
        return new TxReceipt(r.getTransactionHash(), r.isStatusOK(), r.getBlockNumber().longValueExact(),
                r.getBlockHash(), r.getTo());
    }

    private long blockNumber(DefaultBlockParameter tag) {
        return block(tag).getNumber().longValueExact();
    }

    private EthBlock.Block block(DefaultBlockParameter tag) {
        return call("eth_getBlockByNumber", () -> web3j.ethGetBlockByNumber(tag, false).send(), r -> {
            if (r.getBlock() == null) {
                throw new ChainClientException("eth_getBlockByNumber returned no block");
            }
            return r.getBlock();
        });
    }

    private <R extends Response<?>, T> T call(String method, IoCall<R> request, Function<R, T> extract) {
        pacer.await();
        R response;
        try {
            response = request.get();
        } catch (IOException | RuntimeException e) {
            // web3j reports non-2xx answers (for example an HTTP 429 with an HTML body) as an unchecked
            // ClientConnectionException whose message embeds the body. Only the class name is kept.
            throw new ChainClientException(method + " failed: " + e.getClass().getSimpleName(), e);
        }
        if (response.hasError()) {
            throw new ChainClientException(method + " returned an RPC error");
        }
        return extract.apply(response);
    }

    @FunctionalInterface
    private interface IoCall<R> {
        R get() throws IOException;
    }
}
