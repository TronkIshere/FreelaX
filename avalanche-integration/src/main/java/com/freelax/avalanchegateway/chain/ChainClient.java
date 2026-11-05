package com.freelax.avalanchegateway.chain;

import com.freelax.avalanchegateway.exception.ChainClientException;
import com.freelax.avalanchegateway.exception.LogRangeExceededException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

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

    /**
     * The finalized block under the tag policy (Fuji). Not used by LOCAL_SIMULATION, where Anvil's tag does not advance.
     */
    public long finalizedBlockNumber() {
        return blockNumber(DefaultBlockParameterName.FINALIZED);
    }

    /**
     * Timestamp of the latest block, the clock that contract deadlines are checked against.
     */
    public long latestBlockTimestamp() {
        return block(DefaultBlockParameterName.LATEST).getTimestamp().longValueExact();
    }

    public BigInteger latestBaseFee() {
        EthBlock.Block block = block(DefaultBlockParameterName.LATEST);
        if (block.getBaseFeePerGas() == null) {
            throw new ChainClientException("the latest block has no baseFeePerGas");
        }
        return block.getBaseFeePerGas();
    }

    /**
     * Raw submission. An RPC error is returned as a message; only transport failures throw.
     */
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

    /**
     * The logs carried by a transaction's receipt (empty Optional when there is no receipt yet). Used to verify a token
     * transfer the gateway did not send (payout rail: the freelancer's own wallet transfer), never to infer absence.
     */
    public Optional<List<LogEntry>> receiptLogs(String txHash) {
        return call("eth_getTransactionReceipt", () -> web3j.ethGetTransactionReceipt(txHash).send(),
                r -> r.getTransactionReceipt().map(t -> t.getLogs().stream()
                        .map(log -> new LogEntry(log.getTransactionHash(), log.getLogIndex().intValueExact(),
                                log.getBlockNumber().longValueExact(), log.getBlockHash(), log.getAddress(),
                                List.copyOf(log.getTopics()), log.getData(), log.isRemoved()))
                        .toList()));
    }

    /**
     * Logs of one contract over an inclusive block range. A provider refusal of the range or result size throws
     * {@link LogRangeExceededException}; callers must never read an empty answer near the head as proof of absence.
     */
    public List<LogEntry> logs(String contractAddress, long fromBlock, long toBlock) {
        pacer.await();

        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(toBlock)),
                contractAddress);

        EthLog response;
        try {
            response = web3j.ethGetLogs(filter).send();
        } catch (IOException | RuntimeException e) {
            throw new ChainClientException("eth_getLogs failed: " + e.getClass().getSimpleName(), e);
        }

        if (response.hasError()) {
            Response.Error error = response.getError();
            String message = Optional.ofNullable(error.getMessage()).orElse("").toLowerCase(Locale.ROOT);
            boolean rangeRefused = error.getCode() == -32005
                    || Stream.of("range", "too many", "exceed", "limit", "more than").anyMatch(message::contains);

            String detail = "(code " + error.getCode() + ")";
            if (rangeRefused) {
                throw new LogRangeExceededException("eth_getLogs range or result size refused " + detail);
            }
            throw new ChainClientException("eth_getLogs returned an RPC error " + detail);
        }

        List<LogEntry> entries = new ArrayList<>();
        for (EthLog.LogResult<?> result : response.getLogs()) {
            if (!(result instanceof EthLog.LogObject log)) {
                throw new ChainClientException("eth_getLogs returned a hash-only entry");
            }
            entries.add(new LogEntry(
                    log.getTransactionHash(),
                    log.getLogIndex().intValueExact(),
                    log.getBlockNumber().longValueExact(),
                    log.getBlockHash(),
                    log.getAddress(),
                    List.copyOf(log.getTopics()),
                    log.getData(),
                    log.isRemoved()));
        }
        return entries;
    }

    /**
     * Result of an eth_call: either the returned data, or the revert data of the failed call (never an exception for a revert).
     */
    public record CallResult(boolean reverted, String data) {
    }

    /**
     * Read-only contract call at the latest block. A revert is reported with its raw revert data for error mapping.
     */
    public CallResult call(String to, String data) {
        return call(null, to, data);
    }

    /**
     * Same, simulated as coming from {@code from}: needed to reproduce the revert reason of a sender-dependent failure.
     */
    public CallResult call(String from, String to, String data) {
        pacer.await();

        EthCall response;
        try {
            response = web3j.ethCall(
                    Transaction.createEthCallTransaction(from, to, data),
                    DefaultBlockParameterName.LATEST).send();
        } catch (IOException | RuntimeException e) {
            throw new ChainClientException("eth_call failed: " + e.getClass().getSimpleName(), e);
        }

        if (!response.hasError()) {
            return new CallResult(false, response.getValue());
        }

        Response.Error error = response.getError();
        String revertData = error.getData() == null ? null : error.getData().replace("\"", "");
        if (revertData == null || !revertData.startsWith("0x")) {
            throw new ChainClientException("eth_call returned an RPC error (code " + error.getCode() + ")");
        }
        return new CallResult(true, revertData);
    }

    /**
     * Deployed bytecode at an address (empty means no contract).
     */
    public String code(String address) {
        return call("eth_getCode", () -> web3j.ethGetCode(address, DefaultBlockParameterName.LATEST).send(), r -> r.getCode());
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
