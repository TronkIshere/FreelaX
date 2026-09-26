package com.payment.backend.dto.response.common;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageResponse<T> implements Serializable {
    int currentPage;
    int pageSize;
    int totalPages;
    Long totalElements;

    @Builder.Default
    List<T> data = Collections.emptyList();
}
