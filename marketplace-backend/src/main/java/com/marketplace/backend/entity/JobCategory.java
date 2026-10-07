package com.marketplace.backend.entity;

import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;

public enum JobCategory {
    WEB_FRONTEND, BACKEND_API, SEO_CONTENT, MOBILE_APP, UI_UX_DESIGN,
    ECOMMERCE, DATA_ANALYTICS, BRANDING_GRAPHIC, OTHER;

    public static JobCategory require(String value) {
        try {
            return JobCategory.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
    }
}
