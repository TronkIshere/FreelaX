package com.freelax.avalanchegateway.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Unknown properties are rejected: a caller can never smuggle in a key, an RPC URL or a chain id. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ActionRequest(String operationKey, String client, String freelancer, String amount, Long fundingExpiresAt, Long deliveryDueAt,
                            Integer reviewWindowHours, Integer maxRevisions, Boolean highValueGrace, String evidenceHash,
                            String feedbackHash, String reasonHash, String resolutionHash, Boolean releaseToFreelancer,
                            Long newDeliveryDueAt, String clientSignature, String freelancerSignature, String by,
                            Long authorizationDeadline) {

}
