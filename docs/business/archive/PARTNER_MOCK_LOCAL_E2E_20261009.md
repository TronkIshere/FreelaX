# Partner escrow mock — E2E local ngày 2026-10-09

> **Lưu trữ theo mốc ngày.** Xem [tổng quan hiện tại](../README.md) và [trạng thái kiểm chứng](../VERIFICATION.md) trước khi dùng các kết luận bên dưới.

**Phạm vi:** Docker Marketplace + Payment + frontend trên working tree `feat/solana-milestone-escrow`, tài khoản Client/Freelancer seed, API công khai và sao kê độc lập của Payment Backend. Đây là tiền và tỷ giá **mô phỏng**; không có USD/VND ngân hàng thật. Job được tạo qua Marketplace API, không tạo trạng thái nghiệp vụ bằng SQL.

## Kết quả

| Ca | Job / Milestone | Kết quả quan sát |
| --- | --- | --- |
| Funding → bàn giao → Client duyệt → chi | `d2b2fee3-65e6-42cd-9508-afacb482e94c` / `d64214b7-ca96-4618-97a1-24a379ecbbc5` | PASS. Trước funding, Freelancer bị từ chối nộp bài. Sau đối tác xác nhận và đối soát, Job `IN_PROGRESS`; sau duyệt, Job/Contract `COMPLETED`, settlement `SUCCEEDED`. Giá 100 USD, phí 3 USD, phần Freelancer 97 USD, tỷ giá mock 25.000, chi mock 2.425.000 VND. Sao kê đúng một `FUND` và một `RELEASE`. |
| Funding → hai bên đồng ý hoàn | `b89be4c9-48d9-4a49-a4b3-b21dc388545b` / `28a7678d-8772-4ae5-b955-70f01645f271` | PASS. Job/Contract `CANCELLED`, refund `SUCCEEDED`, hoàn đủ 20 USD, phí 0. Lặp quyết định chấp nhận trả cùng cancellation; sao kê chỉ có một `FUND` và một `REFUND`. |
| Payment Backend ngắt trong lúc chấp nhận hoàn | `253bc920-d415-4678-b4ef-0156a16aa3c6` / `e999c9d2-8ef7-4120-bd37-df688aa37aa4` | PASS. Khi Payment dừng: cancellation `REFUND_PENDING`, refund `UNKNOWN`, Job vẫn `IN_PROGRESS`. Sau khi Payment khởi động lại: scheduler chuyển refund `SUCCEEDED`, Job `CANCELLED`; sao kê đúng một `FUND` và một `REFUND` cho 35 USD, số dư từ 0 về 0. |

Lệnh funding gửi lặp cùng `Idempotency-Key` trả lại cùng transaction. Client gọi API đối soát Admin nhận HTTP 403. Frontend image build thành công và HTTP `/` trả 200; `/auth/me` chưa đăng nhập trả 401. Hai script có thể chạy lại trên stack local: `node scripts/partner-mock-e2e.mjs` và `node scripts/partner-mock-outage-e2e.mjs` theo thứ tự `prepare` → dừng `payment-backend` → `accept` → khởi động lại `payment-backend` → `verify`. Script outage lưu ID vào `target/partner-mock-outage-state.json` (Git bỏ qua). Không chạy `prepare` nếu đang tiếp tục một ca chưa `verify`.

Trong lần chuẩn bị ca outage, script ban đầu không tạo được file trạng thái vì thư mục `target/` chưa tồn tại. Job đã được funding và yêu cầu hủy trước khi lỗi file; script được sửa, `recover` lấy lại đúng Job qua API và ca đó đã đi tiếp đến `verify`. Không có Job funding bỏ dở từ lỗi này.

## Giới hạn gate

- Các ca trong bảng trên là **HTTP E2E xuyên Marketplace ↔ Payment mock**. Sau đó, Playwright đã chạy luồng browser với tài khoản Client/Freelancer/Admin seed: Client funding, Freelancer bàn giao, Client duyệt/chi, Admin thấy đối soát **Khớp**, cảnh báo khi có khoản lệch và **Khớp** lại sau xử lý. Ảnh ở `target/browser-e2e/`; script là `frontend/scripts/partner-browser-e2e.mjs`. Browser test tạo Job/ứng tuyển/phân công bằng API trước khi thao tác UI; tạo tài khoản mới và đăng Job hoàn toàn bằng UI chưa nằm trong gate tự động này.
- Admin demo `admin.e2e@example.test` được seed trong dev profile khi `DEMO_ADMIN_PASSWORD` có giá trị. Quyền từ chối Client với API Admin đã được xác minh bằng HTTP 403.
- Ca `UNKNOWN` là mất kết nối **trước khi** đối tác mock nhận lệnh hoàn. Nó chứng minh giữ trạng thái chờ và retry an toàn, chưa chứng minh trường hợp đối tác đã chi/hoàn thành công nhưng phản hồi bị mất.
- Luồng Solana escrow và tiền fiat đối tác là hai rail riêng. Kết quả này không thay thế gate E2E Solana scheduler timeout, Admin dispute hoặc devnet.
