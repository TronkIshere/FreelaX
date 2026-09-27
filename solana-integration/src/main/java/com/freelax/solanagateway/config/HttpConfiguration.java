package com.freelax.solanagateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpConfiguration {

    @Bean
    RestClient solanaRestClient(SolanaProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return RestClient.builder()
                .baseUrl(properties.rpcUrl())
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }
}
