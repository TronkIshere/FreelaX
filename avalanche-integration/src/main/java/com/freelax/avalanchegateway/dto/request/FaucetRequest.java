package com.freelax.avalanchegateway.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record FaucetRequest(String address, String amount) {

}
