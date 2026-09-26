package com.misa.backend.exception;

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

    TAXPAYER_NOT_FOUND(3000, "Không tìm thấy hồ sơ người nộp thuế: %s", HttpStatus.NOT_FOUND),
    TAX_CODE_ALREADY_EXISTS(3001, "Mã số thuế đã được đăng ký: %s", HttpStatus.CONFLICT),
    PAYOUT_NOT_FOUND(3002, "Không tìm thấy giao dịch payout: %s", HttpStatus.NOT_FOUND),
    PAYOUT_ALREADY_HAS_CERTIFICATE(3003, "Giao dịch payout đã có chứng từ khấu trừ", HttpStatus.CONFLICT),
    CERTIFICATE_NOT_FOUND(3004, "Không tìm thấy chứng từ khấu trừ: %s", HttpStatus.NOT_FOUND),
    INVALID_CERTIFICATE_DATA(3005, "Dữ liệu chứng từ không hợp lệ", HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_CERTIFICATE_STATUS(3006, "Trạng thái chứng từ không cho phép thao tác này", HttpStatus.CONFLICT),
    CERTIFICATE_ALREADY_SUBMITTED(3007, "Chứng từ đã được gửi trước đó", HttpStatus.CONFLICT),
    LOOKUP_CODE_NOT_FOUND(3008, "Mã tra cứu không hợp lệ hoặc không tồn tại", HttpStatus.NOT_FOUND),
    EXTERNAL_ID_REQUIRED(3009, "externalId là bắt buộc khi đăng ký người nộp thuế qua đường B2B", HttpStatus.BAD_REQUEST),

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