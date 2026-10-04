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
    NOT_A_CLIENT(2020, "Tài khoản này không phải Client", HttpStatus.FORBIDDEN),

    JOB_NOT_FOUND(4000, "Không tìm thấy công việc: %s", HttpStatus.NOT_FOUND),
    INVALID_JOB_STATUS(4001, "Trạng thái công việc không cho phép thao tác này", HttpStatus.CONFLICT),
    JOB_NOT_PAID(4003, "Công việc chưa được thanh toán", HttpStatus.CONFLICT),
    PAYMENT_BACKEND_CALL_FAILED(4004, "Gọi payment-backend thất bại: %s", HttpStatus.BAD_GATEWAY),
    FREELANCER_NOT_FOUND(4005, "Không tìm thấy freelancer: %s", HttpStatus.NOT_FOUND),
    USER_IS_NOT_FREELANCER(4006, "Tài khoản được chọn không phải freelancer: %s", HttpStatus.BAD_REQUEST),
    JOB_FREELANCER_NOT_ASSIGNED(4007, "Công việc chưa được gán cho freelancer nào, không thể thanh toán: %s", HttpStatus.CONFLICT),
    ALREADY_APPLIED(4008, "Bạn đã ứng tuyển công việc này rồi: %s", HttpStatus.CONFLICT),
    FREELANCER_NOT_APPLIED(4009, "Freelancer chưa ứng tuyển công việc này: %s", HttpStatus.CONFLICT),
    TAX_RECORD_NOT_FOUND(4010, "Không tìm thấy bản ghi thuế: %s", HttpStatus.NOT_FOUND),
    TAX_RECORD_INVALID_STATUS(4011, "Trạng thái bản ghi thuế không cho phép thao tác này: %s", HttpStatus.CONFLICT),
    JOB_SUBMISSION_NOT_FOUND(4012, "Không tìm thấy bản bàn giao công việc: %s", HttpStatus.NOT_FOUND),
    JOB_BUDGET_IMMUTABLE(4013, "Không thể đổi budgetUsd sau khi đã tạo checkout cho công việc", HttpStatus.CONFLICT),
    JOB_DEADLINE_TOO_SOON(4014, "Hạn bàn giao phải cách thời điểm tạo ít nhất 24 giờ", HttpStatus.UNPROCESSABLE_ENTITY),
    JOB_REQUIREMENTS_MISSING(4015, "Công việc phải có sản phẩm bàn giao và điều kiện nghiệm thu trước khi chọn Freelancer", HttpStatus.UNPROCESSABLE_ENTITY),
    FUNDING_NOT_FOUND(4016, "Không tìm thấy hợp đồng hoặc milestone", HttpStatus.NOT_FOUND),
    FUNDING_AMOUNT_CHANGED(4017, "Số tiền hoặc đơn vị tiền tệ đã thay đổi", HttpStatus.CONFLICT),
    FUNDING_INVALID_STATE(4018, "Milestone không còn chờ funding", HttpStatus.CONFLICT),
    FUNDING_KEY_CONFLICT(4019, "Idempotency-Key đã được dùng với nội dung khác", HttpStatus.CONFLICT),
    FUNDING_IN_PROGRESS(4020, "Funding đang được xử lý hoặc đối soát", HttpStatus.CONFLICT),
    FUNDING_PAYMENT_METHOD_INVALID(4021, "Chưa có tài khoản ngân hàng Client hợp lệ", HttpStatus.UNPROCESSABLE_ENTITY),
    CONTRACT_SUBMISSION_NOT_FOUND(4022, "Không tìm thấy bản bàn giao của hợp đồng", HttpStatus.NOT_FOUND),
    SUBMISSION_NOT_FUNDED(4023, "Milestone chưa được funding", HttpStatus.CONFLICT),
    SUBMISSION_INVALID_STATE(4024, "Trạng thái hợp đồng hoặc milestone không cho phép thao tác này", HttpStatus.CONFLICT),
    SUBMISSION_EVIDENCE_INVALID(4025, "Bằng chứng bàn giao không hợp lệ: %s", HttpStatus.UNPROCESSABLE_ENTITY),
    SUBMISSION_KEY_CONFLICT(4026, "Idempotency-Key đã được dùng với nội dung bàn giao khác", HttpStatus.CONFLICT),
    SUBMISSION_STALE(4027, "Bản bàn giao này không còn là bản mới nhất đang chờ duyệt", HttpStatus.CONFLICT),
    SUBMISSION_REVISION_LIMIT(4028, "Đã đạt giới hạn số lần yêu cầu chỉnh sửa", HttpStatus.CONFLICT),
    CONTRACT_API_REQUIRED(4029, "Hợp đồng này phải dùng API bàn giao theo contract", HttpStatus.CONFLICT),
    CONCURRENT_WORKFLOW_CHANGE(4030, "Trạng thái đã thay đổi; vui lòng tải lại", HttpStatus.CONFLICT),
    DISPUTE_ALREADY_OPEN(4031, "Hợp đồng đã có dispute đang mở", HttpStatus.CONFLICT),
    DISPUTE_REASON_REQUIRED(4032, "Dispute cần reasonCode và description", HttpStatus.UNPROCESSABLE_ENTITY),

    MISA_BACKEND_CALL_FAILED(5000, "Gọi misa-backend thất bại: %s", HttpStatus.BAD_GATEWAY),

    SOLANA_CPR_NOT_CONFIGURED(5102, "Chưa cấu hình solana-cpr: %s", HttpStatus.INTERNAL_SERVER_ERROR),

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
