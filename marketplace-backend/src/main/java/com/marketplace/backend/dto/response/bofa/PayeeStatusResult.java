package com.marketplace.backend.dto.response.bofa;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PayeeStatusResult {
    UUID payeeId;
    boolean registered;
    boolean active;
}