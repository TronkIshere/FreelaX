package com.marketplace.backend.provider.offchain;

import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveExchangeRateProviderTest {
    private LiveExchangeRateProvider provider(boolean allowFallback, Object body) {
        LiveExchangeRateProvider provider = new LiveExchangeRateProvider(new RestTemplateBuilder(), 100, 100);
        RestTemplate rest = mock(RestTemplate.class);
        if (body == null) {
            when(rest.getForObject(anyString(), eq(Map.class))).thenThrow(new RestClientException("down"));
        } else {
            when(rest.getForObject(anyString(), eq(Map.class))).thenReturn((Map<?, ?>) body);
        }
        ReflectionTestUtils.setField(provider, "restTemplate", rest);
        ReflectionTestUtils.setField(provider, "fallbackUsdVndRate", new BigDecimal("25000"));
        ReflectionTestUtils.setField(provider, "fallbackUsdcVndRate", new BigDecimal("25000"));
        ReflectionTestUtils.setField(provider, "cacheTtlSeconds", 60L);
        ReflectionTestUtils.setField(provider, "allowFallback", allowFallback);
        return provider;
    }

    @Test
    void failsClosedWhenLiveRateIsUnavailableAndFallbackIsDisabled() {
        LiveExchangeRateProvider provider = provider(false, null);
        assertThrows(IllegalStateException.class, provider::getUsdToVndRate);
        assertThrows(IllegalStateException.class, provider::getUsdcToVndRate);
    }

    @Test
    void failsClosedWhenLiveRateIsNotPositive() {
        LiveExchangeRateProvider provider = provider(false,
                Map.of("result", "success", "rates", Map.of("VND", "0")));
        assertThrows(IllegalStateException.class, provider::getUsdToVndRate);
    }

    @Test
    void usesTaggedFallbackOnlyWhenExplicitlyAllowed() {
        ExchangeRateResult result = provider(true, null).getUsdToVndRate();
        assertEquals(ExchangeRateSource.FALLBACK_PLACEHOLDER, result.source());
        assertEquals(0, new BigDecimal("25000").compareTo(result.rate()));
    }

    @Test
    void returnsLiveRateWhenAvailable() {
        ExchangeRateResult result = provider(false,
                Map.of("result", "success", "rates", Map.of("VND", "26000"))).getUsdToVndRate();
        assertEquals(ExchangeRateSource.LIVE_OPEN_ER_API, result.source());
        assertEquals(0, new BigDecimal("26000").compareTo(result.rate()));
    }
}
