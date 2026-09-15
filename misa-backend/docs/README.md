# MISA Backend — Freelancer USDC Payout & Withholding Tax Certificate Module

## Bối cảnh

Backend cho dự án Unihackfest: "Cổng nhận thanh toán USDC cho freelancer Việt Nam".
Module này triển khai lớp compliance — chứng từ khấu trừ thuế TNCN (Mẫu 03/TNCN) —
nằm phía trên phần nhận USDC on-chain (Solana, chưa triển khai trong bản này) và
phần off-ramp USDC→VND (giả định qua một tổ chức được cấp phép hợp lệ, cũng mock).

Codebase gốc (auth, JWT, Redis, security) được tái sử dụng từ một project auth có sẵn
(tên cũ "Brome Clean") — các tên định danh (application name, DB default, JWT issuer,
email subject) đã được đổi sang MISA. Business logic cũ không liên quan (loyalty,
QR code, reward) đã được loại bỏ khỏi `DataInitializer` vì source code của các entity
đó không thuộc phạm vi được cung cấp và không liên quan tới domain freelancer/thuế.

## Mô hình nghiệp vụ đã chọn

Có hai mô hình khả dĩ cho việc kê khai thuế của freelancer:

- **Mô hình A** (không dùng trong bản này): freelancer tự xuất hóa đơn GTGT qua một
  nhà cung cấp hóa đơn điện tử (ví dụ Mắt Bão), tự chịu trách nhiệm thuế, platform chỉ
  hỗ trợ UX.
- **Mô hình B** (đã chọn, theo tài liệu `MISA_Mock_API_Module_2026.docx`): platform
  đóng vai trò **tổ chức trả thu nhập**, tự khấu trừ thuế TNCN trước khi trả tiền
  cho freelancer, và phát hành **chứng từ khấu trừ TNCN điện tử (Mẫu 03/TNCN)** qua
  MISA AMIS Thuế TNCN (ở đây được mock hoàn toàn in-process, không gọi API MISA thật).

**Hệ quả quan trọng:** vì đây là mô hình khấu trừ, số tiền VND freelancer thực nhận
là số đã trừ thuế (net), không phải số gross quy đổi từ USDC. Bản hiện tại lưu cả
`taxableIncome` (gross) và `taxWithheld` trên `WithholdingCertificate`, nhưng **chưa
triển khai bước thực trả net VND cho freelancer** (vì "nhận tiền" bị gác lại theo yêu
cầu), nên hiện tại `PayoutTransaction.amountVndGross` mới là số đưa vào tính thuế.

## Cấu trúc module MISA

- `entity`: `Taxpayer`, `PayoutTransaction`, `WithholdingCertificate`,
  `IncorrectRecordNotification`, `CertificateStatus` (enum trạng thái theo tài liệu
  MISA mock: DRAFT → SIGNED → SUBMITTING → SUBMITTED → ACCEPTED, nhánh
  REJECTED/CORRECTION_REQUIRED, ACCEPTED → REPLACED/CANCELLED).
- `service.MisaProviderClient` + `service.impl.MisaProviderClientMockImpl`: adapter
  giả lập MISA — **không gọi network thật**, chỉ tính toán trong tiến trình
  (symbol/number/lookupCode, submissionId, taxAuthorityReference). Khi tích hợp MISA
  thật, chỉ cần viết thêm một implementation khác của interface này (ví dụ dùng
  `RestClient`, có sẵn từ Spring Framework 6.1+ trong `spring-boot-starter-web`,
  không cần thêm dependency).
- `service.TaxEngineService`: tính thuế khấu trừ theo tỷ lệ cấu hình được
  (`misa.tax.withholding-rate` trong `application.yml`, mặc định 10%) — đây là **giá
  trị placeholder**, chưa phải biểu thuế thật, cần thay bằng logic thuế chính xác khi
  có xác nhận từ kế toán/luật sư.
- `service.WithholdingCertificateService`: điều phối toàn bộ vòng đời chứng từ.

## Chạy thử

```bash
mvn spring-boot:run
```

Biến môi trường quan trọng (xem `application.yml` để biết đầy đủ, mỗi biến có giá trị
mặc định để chạy local): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`,
`REDIS_PORT`, `JWT_SECRET`, `MISA_ORG_TAX_CODE`, `MISA_WITHHOLDING_RATE`.

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Luồng API gợi ý để demo

1. `POST /api/v1/auth/register`, `POST /api/v1/auth/sign-in` — lấy access token.
2. `POST /api/v1/taxpayers` — freelancer tạo hồ sơ người nộp thuế (dùng access token).
3. `POST /api/v1/taxpayers/{taxpayerId}/payouts` — ghi nhận một giao dịch payout
   (nhập tay `transactionHash`, `amountUsdc`, `exchangeRate` — bước "nhận tiền" thật
   qua Solana chưa nối vào đây).
4. `POST /api/v1/withholding-certificates` — tạo chứng từ DRAFT từ payout, Tax Engine
   tự tính `taxWithheld`.
5. `POST /api/v1/withholding-certificates/{id}/issue` — ký, chuyển SUBMITTING.
6. `POST /api/v1/withholding-certificates/{id}/submit` — gửi, mock tự động chuyển
   thẳng sang ACCEPTED (không có bước SUBMITTED chờ async, vì đây là mock đồng bộ).
7. `GET /api/v1/withholding-certificates/lookup/{lookupCode}` — endpoint công khai
   (không cần đăng nhập), giống trang tra cứu chứng từ thật của MISA.

## Giới hạn đã biết (known limitations) — cần nói rõ nếu bị hỏi

- `GET/{id}/pdf` và `GET/{id}/xml` hiện yêu cầu đăng nhập (do rule bảo mật mặc định
  "anyRequest().authenticated()"), nhưng chỉ endpoint `lookup/{lookupCode}` được
  whitelist công khai. Ở MISA thật, người nhận thu nhập tra cứu bằng link + mã tra
  cứu **không cần đăng nhập** — nếu muốn khớp hoàn toàn hành vi thật, cần thêm
  `/api/v1/withholding-certificates/{id}/pdf` và `/xml` vào whitelist, hoặc đổi
  thiết kế để lookup trả trực tiếp nội dung thay vì chỉ trả link.
- Nhóm `tax-returns` (kê khai tổng hợp theo kỳ), `replace` (thay thế chứng từ), và
  `webhook` trong tài liệu MISA mock **chưa triển khai** — được để lại cho phase 2
  theo đúng phạm vi MVP đã thống nhất.
- `PayoutTransactionController` cho phép ghi nhận payout theo `taxpayerId` trong path
  mà không kiểm tra taxpayer đó có thuộc về user đang đăng nhập hay không — đây là
  utility endpoint cho demo/testing, cần bổ sung kiểm tra ownership trước khi dùng
  thật.
- Tax Engine dùng một tỷ lệ khấu trừ cố định cấu hình được, không phải biểu thuế
  TNCN thật (có ngưỡng, biểu tiến lũy...). Đây là placeholder rõ ràng, không nên
  trình bày như số liệu thuế chính xác trong pitch.
