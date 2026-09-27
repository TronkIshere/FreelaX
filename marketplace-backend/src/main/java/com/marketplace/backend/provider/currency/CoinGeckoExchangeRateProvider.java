package com.marketplace.backend.provider.currency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class CoinGeckoExchangeRateProvider implements ExchangeRateProvider {

    private static final String COINGECKO_URL =
            "https://api.coingecko.com/api/v3/simple/price?ids=usd-coin&vs_currencies=vnd";

    private final RestTemplate restTemplate;

    @Value("${exchange-rate.fallback-usdc-vnd-rate:25000}")
    private BigDecimal fallbackRate;

    @Value("${exchange-rate.cache-ttl-seconds:60}")
    private long cacheTtlSeconds;

    private final AtomicReference<ExchangeRateResult> liveCache = new AtomicReference<>();

    public CoinGeckoExchangeRateProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ExchangeRateResult getUsdcToVndRate() {
        ExchangeRateResult cached = liveCache.get();
        if (cached != null && Instant.now().isBefore(cached.fetchedAt().plusSeconds(cacheTtlSeconds))) {
            return cached;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> body = restTemplate.getForObject(COINGECKO_URL, Map.class);

            Object vndValue = (body != null && body.get("usd-coin") != null)
                    ? body.get("usd-coin").get("vnd")
                    : null;

            if (vndValue == null) {
                throw new IllegalStateException("CoinGecko tra ve du lieu rong hoac sai dinh dang");
            }

            BigDecimal rate = new BigDecimal(vndValue.toString());
            ExchangeRateResult result = new ExchangeRateResult(
                    rate, ExchangeRateResult.RateSource.LIVE_COINGECKO, Instant.now());
            liveCache.set(result);
            return result;

        } catch (RestClientException | IllegalStateException | NumberFormatException e) {
            log.warn("Khong lay duoc ty gia USDC->VND song tu CoinGecko, dung fallback placeholder ({}): {}",
                    fallbackRate, e.getMessage());
            return new ExchangeRateResult(
                    fallbackRate, ExchangeRateResult.RateSource.FALLBACK_PLACEHOLDER, Instant.now());
        }
    }
}