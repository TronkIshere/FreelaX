# Tham chiếu module MISA — căn cứ pháp lý & API contract

> File này tóm tắt lại tài liệu `MISA_Mock_API_Module_2026.docx` do team cung cấp,
> cùng các giả định pháp lý đã thống nhất trong quá trình tư vấn, để bất kỳ ai
> (người hoặc AI) đọc code sau này hiểu ngay bối cảnh mà không cần đọc lại toàn bộ
> lịch sử trao đổi.

## Căn cứ pháp lý được tài liệu nguồn dẫn chiếu

- Nghị định 254/2026/NĐ-CP
- Thông tư 91/2026/TT-BTC

**Cảnh báo quan trọng:** đây là thông tin do team tự cung cấp, phát sinh sau
knowledge cutoff của AI hỗ trợ, **chưa được xác minh độc lập**. Trước khi đưa vào
slide pitch hoặc bất kỳ tài liệu chính thức, cần tự tra cứu lại số hiệu văn bản này
trên nguồn chính thống (Cổng thông tin điện tử Chính phủ, Thư viện Pháp luật...).

## Disclaimer bắt buộc khi thuyết trình (theo checklist của tài liệu nguồn)

Khi trình bày trước giám khảo, **không nói**:
- Đây là API công khai chính thức của MISA (đây là **mock do team tự xây**, mô
  phỏng theo tài liệu API contract được cung cấp).
- "Mua chứng từ MISA" (chứng từ khấu trừ TNCN không phải hàng hóa mua bán).
- Đồng nhất hóa đơn điện tử (invoice) với chứng từ khấu trừ thuế TNCN — đây là hai
  loại văn bản khác nhau về bản chất pháp lý.
- Access token của module này là bằng chứng pháp lý cho quyền khấu trừ thuế của
  platform — token chỉ là cơ chế xác thực kỹ thuật giữa các service.

## Mô hình nghiệp vụ: B, không phải A

Bản này triển khai **Mô hình B**: platform tự đóng vai trò *tổ chức trả thu nhập*
(có `taxCode` riêng, xem `misa.organization.tax-code` trong `application.yml`), tự
khấu trừ thuế TNCN của freelancer trước khi trả tiền, và tự phát hành chứng từ khấu
trừ. Điều này khác với Mô hình A (freelancer tự xuất hóa đơn GTGT qua bên thứ ba,
platform chỉ hỗ trợ UX) đã được thảo luận và loại bỏ trước đó.

**Giả định forward-looking cần nêu rõ trong pitch:** để mô hình B hợp lệ, platform
cần có tư cách pháp nhân "tổ chức trả thu nhập" thật, đăng ký mã số thuế, và có đủ
điều kiện pháp lý để khấu trừ thuế TNCN thay freelancer. Đây là giả định cần được
luật sư/kế toán xác nhận trước khi triển khai thật, tương tự giả định về USDC/đơn vị
off-ramp được cấp phép ở lớp bên dưới (không thuộc phạm vi module này).

## State machine của chứng từ khấu trừ (`CertificateStatus`)

```
DRAFT → SUBMITTING → ACCEPTED
  ↓         ↓            ↓
CANCELLED  CANCELLED   REPLACED / CANCELLED
                       CORRECTION_REQUIRED (qua incorrect-record-notification)
```

Ghi chú so với tài liệu gốc: tài liệu gốc có thêm trạng thái trung gian `SIGNED` và
`SUBMITTED` hiển thị tường minh. Bản mock này gộp `SIGNED` vào bước issue (không lưu
làm trạng thái top-level riêng, chỉ thể hiện trong `signature.status` của response),
và bỏ qua khoảng chờ `SUBMITTED` (mock xử lý đồng bộ, `submit()` trả kết quả
`ACCEPTED` ngay) vì không có hàng đợi xử lý bất đồng bộ thật. Enum `CertificateStatus`
vẫn giữ đủ các giá trị `SIGNED`/`SUBMITTED` để tương thích khi nối vào MISA thật sau
này (lúc đó các trạng thái trung gian sẽ thực sự dừng ở đó chờ webhook).

## Ký hiệu chứng từ

Theo tài liệu gốc: `CT` + 2 số năm + 2 ký tự tự chọn, ví dụ `CT26AA`. Xem
`util.CertificateNumberGenerator.generateSymbol()`.

## Endpoint đã triển khai trong bản này

| Method | Path | Mô tả |
|---|---|---|
| POST | `/api/v1/taxpayers` | Đăng ký hồ sơ người nộp thuế (freelancer) |
| GET | `/api/v1/taxpayers/me` | Lấy hồ sơ của user hiện tại |
| GET | `/api/v1/taxpayers/{id}` | Lấy hồ sơ theo id |
| GET | `/api/v1/organization` | Thông tin tổ chức trả thu nhập (tĩnh, từ config) |
| POST | `/api/v1/taxpayers/{taxpayerId}/payouts` | Ghi nhận giao dịch payout (nhập tay) |
| GET | `/api/v1/taxpayers/{taxpayerId}/payouts/{payoutId}` | Xem payout |
| POST | `/api/v1/withholding-certificates` | Tạo chứng từ (DRAFT) từ một payout |
| POST | `/api/v1/withholding-certificates/{id}/issue` | Ký, chuyển SUBMITTING |
| POST | `/api/v1/withholding-certificates/{id}/submit` | Gửi, mock trả về ACCEPTED |
| GET | `/api/v1/withholding-certificates/{id}/status` | Xem trạng thái |
| GET | `/api/v1/withholding-certificates/{id}/pdf` | Mock PDF (yêu cầu đăng nhập) |
| GET | `/api/v1/withholding-certificates/{id}/xml` | Mock XML (yêu cầu đăng nhập) |
| GET | `/api/v1/withholding-certificates/lookup/{lookupCode}` | Tra cứu công khai |
| POST | `/api/v1/withholding-certificates/{id}/cancel` | Hủy chứng từ |
| POST | `/api/v1/incorrect-record-notifications` | Báo sai sót → CORRECTION_REQUIRED |

## Đã gác lại (phase 2, chưa triển khai)

- Nhóm **Tax Return** (kê khai tổng hợp theo kỳ thuế).
- **Replace certificate** (thay thế chứng từ đã ACCEPTED bị sai) — entity đã có sẵn
  cột `replacement_of_id` để mở đường, nhưng chưa có service/controller.
- **Webhook** (đẩy trạng thái bất đồng bộ về platform) — không cần thiết vì bản mock
  xử lý đồng bộ, chỉ cần thiết khi nối vào MISA thật.

## Dải mã lỗi module MISA

`ErrorCode` 3000–3999 dành riêng cho module này (xem `exception/ErrorCode.java`),
tách biệt với 1000s (data chung), 2000s (auth), 5000s (rate-limit), 9000s (system) đã
có từ trước.
