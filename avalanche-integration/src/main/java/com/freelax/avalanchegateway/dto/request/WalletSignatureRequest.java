package com.freelax.avalanchegateway.dto.request;

/** EIP-191 personal_sign verification input for wallet linking (register A8). */
public record WalletSignatureRequest(String address, String message, String signature) {

}
