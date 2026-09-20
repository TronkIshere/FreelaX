package com.paypal.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_DATA(1000, "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    DATA_NOT_FOUND(1001, "Không tìm thấy dữ liệu: %s", HttpStatus.NOT_FOUND),
    DATA_ALREADY_EXISTS(1002, "Dữ liệu đã tồn tại: %s", HttpStatus.CONFLICT),

    UNAUTHENTICATED(2000, "Chưa đăng nhập", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(2001, "Không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    ACCESS_DENIED(2002, "Không có quyền truy cập", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS(2003, "Sai email hoặc mật khẩu", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(2004, "Phiên đăng nhập không hợp lệ", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(2005, "Phiên đăng nhập đã hết hạn", HttpStatus.UNAUTHORIZED),
    TOKEN_BLACKLISTED(2006, "Phiên đăng nhập đã bị vô hiệu hóa", HttpStatus.UNAUTHORIZED),
    USER_NOT_EXISTED(2007, "Tài khoản không tồn tại", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(2008, "Email đã được sử dụng", HttpStatus.CONFLICT),
    EMAIL_NOT_FOUND(2009, "Email không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    REFRESH_TOKEN_INVALID(2010, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", HttpStatus.UNAUTHORIZED),
    SIGN_OUT_FAILED(2011, "Đăng xuất thất bại", HttpStatus.BAD_REQUEST),
    INVALID_OTP(2012, "Mã OTP không đúng hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    PASSWORD_NOT_MATCH(2013, "Mật khẩu xác nhận không khớp", HttpStatus.BAD_REQUEST),

    PAYEE_NOT_FOUND(3000, "Không tìm thấy hồ sơ nhận tiền PayPal: %s", HttpStatus.NOT_FOUND),
    PAYOUT_NOT_FOUND(3001, "Không tìm thấy giao dịch payout: %s", HttpStatus.NOT_FOUND),
    DUPLICATE_PLATFORM_PAYOUT_ID(3002, "Mã payout đã tồn tại: %s", HttpStatus.CONFLICT),
    INVALID_TRANSACTION_STATUS(3003, "Trạng thái giao dịch không cho phép thao tác này", HttpStatus.CONFLICT),
    CHECKOUT_ORDER_NOT_FOUND(3004, "Không tìm thấy giao dịch thanh toán: %s", HttpStatus.NOT_FOUND),
    INVALID_CHECKOUT_ORDER_STATUS(3005, "Trạng thái giao dịch thanh toán không cho phép thao tác này", HttpStatus.CONFLICT),
    PAYPAL_ORDER_FAILED(3006, "Giao dịch PayPal thất bại: %s", HttpStatus.BAD_GATEWAY),

    JOB_NOT_FOUND(4000, "Không tìm thấy công việc: %s", HttpStatus.NOT_FOUND),

    RATE_LIMIT_EXCEEDED(5000, "Bạn thao tác quá nhanh, vui lòng thử lại sau", HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(9999, "Đã có lỗi xảy ra, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),
    CODE_GENERATION_FAILED(9998, "Tạo mã xác nhận thất bại, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),
    ACCOUNT_NOT_FOUND(9997, "Không tìm thấy tài khoản", HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}