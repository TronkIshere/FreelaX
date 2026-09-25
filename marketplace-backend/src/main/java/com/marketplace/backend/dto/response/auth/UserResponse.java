package com.marketplace.backend.dto.response.auth;

import com.marketplace.backend.entity.UserType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    UUID id;
    String email;
    String displayName;
    UserType userType;
    UUID misaTaxpayerId;
}
