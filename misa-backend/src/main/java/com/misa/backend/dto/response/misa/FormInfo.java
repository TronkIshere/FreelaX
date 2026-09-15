package com.misa.backend.dto.response.misa;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FormInfo {
    String formNumber;
    String symbol;
    String number;
}
