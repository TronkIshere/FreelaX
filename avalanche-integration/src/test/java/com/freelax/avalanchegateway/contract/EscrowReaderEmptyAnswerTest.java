package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.api.ApiError;
import com.freelax.avalanchegateway.api.ApiExceptionHandler;
import com.freelax.avalanchegateway.chain.ChainClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** An address without contract code answers eth_call with 0x: reported as unreadable chain state, never a NumberFormatException. */
class EscrowReaderEmptyAnswerTest {
    private static final String ESCROW = "0x" + "e".repeat(40);

    private EscrowReader readerAnswering(String data) {
        ChainClient client = mock(ChainClient.class);
        when(client.call(any(), any())).thenReturn(new ChainClient.CallResult(false, data));
        return new EscrowReader(client, ESCROW);
    }

    @Test
    void anEmptyAnswerIsAnUnreadableChainStateForEveryRead() {
        EscrowReader reader = readerAnswering("0x");
        assertThrows(UnreadableChainStateException.class, reader::paused);
        assertThrows(UnreadableChainStateException.class, reader::defaultArbiter);
        assertThrows(UnreadableChainStateException.class, () -> reader.tokenBalance("0x" + "a".repeat(40), "0x" + "b".repeat(40)));
        assertThrows(UnreadableChainStateException.class, () -> reader.read("0x" + "1".repeat(64)));
    }

    @Test
    void aWellFormedNumericAnswerStillParses() {
        assertEquals(false, readerAnswering("0x" + "0".repeat(64)).paused());
        assertEquals(true, readerAnswering("0x" + "0".repeat(63) + "1").paused());
    }

    @Test
    void theApiReportsItAsBadGatewayWithAStableCode() {
        ResponseEntity<ApiError> response = new ApiExceptionHandlerAccess().unreadable(new UnreadableChainStateException("paused() returned no data"));
        assertEquals(502, response.getStatusCode().value());
        assertEquals("CHAIN_STATE_UNREADABLE", response.getBody().code());
    }

    /** The handler method is package-private in another package; go through the public Spring entry point by reflection. */
    private static final class ApiExceptionHandlerAccess {
        ResponseEntity<ApiError> unreadable(UnreadableChainStateException e) {
            try {
                var method = ApiExceptionHandler.class.getDeclaredMethod("unreadable", UnreadableChainStateException.class, jakarta.servlet.http.HttpServletRequest.class);
                method.setAccessible(true);
                @SuppressWarnings("unchecked")
                ResponseEntity<ApiError> r = (ResponseEntity<ApiError>) method.invoke(new ApiExceptionHandler(), e, new MockHttpServletRequest("GET", "/api/v1/avalanche/escrows/x"));
                return r;
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
