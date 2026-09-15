package com.paypal.backend.dto.request.paypal;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WithdrawPaypalPayoutRequest {

    String bankAccountNote;
}
