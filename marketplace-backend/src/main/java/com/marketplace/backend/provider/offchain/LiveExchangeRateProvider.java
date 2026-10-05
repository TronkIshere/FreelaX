package com.marketplace.backend.provider.offchain;

import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.provider.currency.ExchangeRateProvider;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
@Component
public class LiveExchangeRateProvider implements ExchangeRateProvider {

    private static final String USD_VND_URL = "https://open.er-api.com/v6/latest/USD";
    private static final String USDC_VND_URL =
            "https://api.coingecko.com/api/v3/simple/price?ids=usd-coin&vs_currencies=vnd";

    private final RestTemplate restTemplate;
    private final AtomicReference<ExchangeRateResult> usdVndCache = new AtomicReference<>();
    private final AtomicReference<ExchangeRateResult> usdcVndCache = new AtomicReference<>();

    @Value("${exchange-rate.fallback-usd-vnd-rate:25000}")
    private BigDecimal fallbackUsdVndRate;

    @Value("${exchange-rate.fallback-usdc-vnd-rate:25000}")
    private BigDecimal fallbackUsdcVndRate;

    @Value("${exchange-rate.cache-ttl-seconds:60}")
    private long cacheTtlSeconds;

    public LiveExchangeRateProvider(RestTemplateBuilder restTemplateBuilder,
                                    @Value("${http-client.connect-timeout-ms:3000}") int connectTimeoutMs,
                                    @Value("${http-client.read-timeout-ms:10000}") int readTimeoutMs) {
        this.restTemplate = restTemplateBuilder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(connectTimeoutMs);
                    factory.setReadTimeout(readTimeoutMs);
                    return factory;
                })
                .build();
    }

    @Override
    public ExchangeRateResult getUsdToVndRate() {
        return resolve(usdVndCache, ExchangeRateSource.LIVE_OPEN_ER_API, fallbackUsdVndRate, "USD->VND", this::fetchUsdVnd);
    }

    @Override
    public ExchangeRateResult getUsdcToVndRate() {
        return resolve(usdcVndCache, ExchangeRateSource.LIVE_COINGECKO, fallbackUsdcVndRate, "USDC->VND", this::fetchUsdcVnd);
    }

    private ExchangeRateResult resolve(AtomicReference<ExchangeRateResult> cache, ExchangeRateSource liveSource,
                                       BigDecimal fallbackRate, String pair, Supplier<BigDecimal> fetcher) {
        ExchangeRateResult cached = cache.get();
        if (cached != null && Instant.now().isBefore(cached.fetchedAt().plusSeconds(cacheTtlSeconds))) {
            return cached;
        }

        try {
            BigDecimal rate = fetcher.get();
            if (rate == null || rate.signum() <= 0) {
                throw new IllegalStateException("ty gia khong hop le: " + rate);
            }
            ExchangeRateResult result = new ExchangeRateResult(rate, liveSource, Instant.now());
            cache.set(result);
            return result;
        } catch (RuntimeException e) {
            log.warn("Live exchange rate unavailable for {}; using configured fallback", pair);
            return new ExchangeRateResult(fallbackRate, ExchangeRateSource.FALLBACK_PLACEHOLDER, Instant.now());
        }
    }

    private BigDecimal fetchUsdVnd() {
        Map<?, ?> body = restTemplate.getForObject(USD_VND_URL, Map.class);
        if (body == null || !"success".equals(body.get("result"))) {
            throw new IllegalStateException("open.er-api tra ve ket qua khong thanh cong");
        }
        if (!(body.get("rates") instanceof Map<?, ?> rates) || rates.get("VND") == null) {
            throw new IllegalStateException("open.er-api thieu ty gia VND");
        }
        return new BigDecimal(rates.get("VND").toString());
    }

    private BigDecimal fetchUsdcVnd() {
        Map<?, ?> body = restTemplate.getForObject(USDC_VND_URL, Map.class);
        if (body == null || !(body.get("usd-coin") instanceof Map<?, ?> coin) || coin.get("vnd") == null) {
            throw new IllegalStateException("CoinGecko tra ve du lieu rong hoac sai dinh dang");
        }
        return new BigDecimal(coin.get("vnd").toString());
    }
}
