package com.marketplace.backend.exception;

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
    NOT_A_FREELANCER(2016, "Tài khoản này không phải freelancer", HttpStatus.FORBIDDEN),
    TAX_INFO_REQUIRED(2017, "Freelancer phải cung cấp đầy đủ thông tin thuế (taxCode, identityNumber, nationality, taxAddress) khi đăng ký", HttpStatus.BAD_REQUEST),
    BANK_INFO_REQUIRED(2018, "Freelancer phải chọn ngân hàng và cung cấp số tài khoản nhận tiền khi đăng ký", HttpStatus.BAD_REQUEST),
    NOTIFICATION_NOT_FOUND(2019, "Không tìm thấy thông báo: %s", HttpStatus.NOT_FOUND),

    JOB_NOT_FOUND(4000, "Không tìm thấy công việc: %s", HttpStatus.NOT_FOUND),
    INVALID_JOB_STATUS(4001, "Trạng thái công việc không cho phép thao tác này", HttpStatus.CONFLICT),
    JOB_NOT_PAID(4003, "Công việc chưa được thanh toán", HttpStatus.CONFLICT),
    PAYPAL_BACKEND_CALL_FAILED(4004, "Gọi paypal-backend thất bại: %s", HttpStatus.BAD_GATEWAY),
    FREELANCER_NOT_FOUND(4005, "Không tìm thấy freelancer: %s", HttpStatus.NOT_FOUND),
    USER_IS_NOT_FREELANCER(4006, "Tài khoản được chọn không phải freelancer: %s", HttpStatus.BAD_REQUEST),
    JOB_FREELANCER_NOT_ASSIGNED(4007, "Công việc chưa được gán cho freelancer nào, không thể thanh toán: %s", HttpStatus.CONFLICT),

    MISA_BACKEND_CALL_FAILED(5000, "Gọi misa-backend thất bại: %s", HttpStatus.BAD_GATEWAY),

    INTERNAL_ERROR(9999, "Đã có lỗi xảy ra, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),
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