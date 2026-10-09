# Solana milestone escrow — kết quả E2E local ngày 2026-10-08

> **Lưu trữ theo mốc ngày.** Xem [tổng quan hiện tại](../README.md) và [trạng thái kiểm chứng](../VERIFICATION.md) trước khi dùng các kết luận bên dưới.

**Phạm vi:** Mock USDC trên `solana-test-validator`; Marketplace, Solana Gateway và Anchor chạy bằng code của working tree `feat/solana-milestone-escrow`. Đây là kiểm thử local, không phải devnet hay tiền thật. Các Job bên dưới được tạo qua API Marketplace, không tạo trạng thái nghiệp vụ bằng SQL.

**Ý nghĩa nghiệp vụ:** các ca dưới đây chỉ chứng minh mock token vào/ra vault và Job đổi trạng thái sau đối soát chain. Chúng **không** chứng minh Client đã nộp USD thật cho đối tác hoặc Freelancer đã nhận VND ngân hàng. Rail đối tác mock và phí 3% được mô tả riêng trong [tài liệu luồng đối tác](PARTNER_ESCROW_BUSINESS_GAP_20261008.md); các ca Solana không kiểm thử rail đó.

## Kết quả đã xác minh

| Luồng | Bằng chứng | Kết quả |
| --- | --- | --- |
| Client funding → Freelancer submit → Client approve → release | Job `f519435a-0cbe-413f-bafb-cd33dffbc817`, Milestone `b4c4603a-da61-46ba-a2ca-2a3eb3724748`; funding `3EKmabvAp7FUPdRuM9neorGxkPbmFYAFC2Zgp4ux3VfGpR2NHxMtxRFyfp28J29zDvUBH5y5ZheMq5YzwuCUnPwW`; submission `2zaKZE1oZ2ZTMswaV8yYhaC2hk7bQSd81mK6JLu3BreYfnRd1HvZp5L8hfyHMELX7xo5jz6XCEzhJzNeU8vEzLB2`; release `dBhxeGDqWNYSSmRHizMfXzUJt68detfgnBwm81xCi9gH1VN6uAC14vnU4uo7VmLX1jUdW8PKuGdn2dkEJ2wU4WM` | Job/Contract `COMPLETED`; vault `0`; Freelancer ATA tăng `75,000,000` base units. |
| Client funding → hai bên ký mutual refund | Job `633d75c2-3614-4238-836f-26b133fcea4f`, Milestone `2ca4c342-a9b1-4552-acb3-42e6b51041f7`; funding `4x2VJ4bQbRGjAuWobLiev8DEH3RxrxGC5NNMTu4ZHp2ZN1bDcJvDcZMLy3Qc2ZG6MVe1VNp7S3LqxvVbJoDDTw65`; refund `4ZLwxZd3XnPMbYqQU9VoYVeCyKh6yiQ3rd8WqrQZLrCfxSpZ4H5fqcaeg3m8ErM8pvtGiJCJCaVcSTi2Hfg4pgEy` | Job/Contract `CANCELLED`; vault `0`; Client ATA được hoàn đủ `20,000,000` base units. |
| Funding → submission → hết hạn review on-chain → permissionless release → Marketplace reconcile | Job `7d21c8cd-00a1-4749-a231-acd183aa5ef7`, Milestone `92454a78-6dd3-4738-b8e5-2049befe42dc`; funding `4w8g6UBHiw4P98X4xBmALYV2cAPnAQAVKpocQbYcohf3zaWdKwEV3ESEjCH2V8PzKHas1TTUTciw4JQiy6JGXE8Z`; submission `4hQAtc5CxgGJd1zsi65F88CFe5mq2qhtF7nmtNom9ZUUexzMSYzR9ehzN9k9mzyJqnPqJB8EpuMVeMW5tEa34RCn`; release `5RvxfDG6Ada4uLXWuCtb92qPkXwm22KKy73Ux5SLdRJwn5dmAirifZ8devoiFUnkue2WHmqQXYaNi3SFxN43oC7k` | Job/Contract `COMPLETED`; vault `0`; Freelancer ATA tăng `20,000,000` base units. Lệnh release do Gateway gửi sau khi warp clock chain, **không phải scheduler Marketplace tự khởi tạo**. |

Anchor local-validator suite: **74/74 pass**, trong đó 7 ca test escrow. Docker build của Gateway và Marketplace: **pass**. Frontend: production build **pass**, **793/793** test pass. Hai Job đầu chạy bằng `solana-stablecoin-payout/scripts/local-marketplace-escrow-e2e.ts`; ca permissionless release dùng `scripts/local-timeout-escrow-e2e.ts`.

Một ca Gateway → Anchor riêng đã đi qua submission → Client dispute → Admin refund; vault về `0`, Client ATA được hoàn `20,000,000` base units. Ca này chạy trên ledger test tạm **đã reset** để tạo lại fixture cho các Job Marketplace ở trên; các signature của ca đó không còn truy vấn được trên ledger cuối. Nó không chứng minh luồng Admin dispute của Marketplace.

## Khoảng trống và lỗi phát hiện

1. **Scheduler tự gửi timeout release chưa được chứng minh E2E.** Clock của validator được warp qua review deadline, nhưng đồng hồ backend không đổi. Ca timeout ở trên xác minh instruction permissionless và bước Marketplace reconcile sau giao dịch; không xác minh scheduler chủ động gọi instruction đúng hạn thực. Ca này vẫn cần chạy bằng đồng hồ kiểm thử đồng bộ hoặc một review window test được cấu hình có kiểm soát.
2. **Admin dispute refund chưa chạy xuyên Marketplace.** Cơ sở dữ liệu demo local hiện có `0` tài khoản `ROLE_ADMIN` (kiểm tra chỉ đọc). Không tạo quyền Admin bằng SQL để làm đẹp bằng chứng E2E. Cần fixture Admin hợp lệ qua cơ chế seed/test rồi chạy Job riêng từ dispute đến quyết định refund.
3. **Thiếu signature khi release được gửi ngoài Marketplace.** Sau permissionless release, Gateway trả signature `5Rvxf...`, chain đã `Released` và Job đã `COMPLETED`, nhưng `releaseSignature` trong response escrow của Marketplace vẫn `null`. Finance vì vậy chưa có reference của giao dịch release này. Cần đường đối soát signature của giao dịch được gửi trực tiếp ngoài Marketplace.
4. **Khóa deploy lệch Program ID.** `target/deploy/invoice_payments-keypair.json` có địa chỉ `2Tx2fa...`, còn `Anchor.toml`, `declare_id!` và `.env` dùng `4Wd6um...`. `anchor deploy` local đã tạo program ở ID khác rồi lỗi khởi tạo IDL. E2E này dùng validator genesis nạp `.so` tại đúng `4Wd6um...` với upgrade authority local. Cần đồng bộ keypair/deploy trước khi chứng minh quy trình deploy chuẩn hoặc devnet.

   **Cập nhật 2026-10-09:** mục 4 là lỗi của lần chạy lịch sử. Working tree hiện tại đã đồng bộ `Anchor.toml`, `declare_id!`, khóa deploy và `.env` về `2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb`. Validator local mới đã nạp program tại ID này; điều đó chưa chứng minh quy trình deploy hoặc E2E devnet.
5. Lần warp đầu khôi phục snapshot cũ và làm mất giao dịch mới của Job test. Ca timeout được tạo lại, chờ archive snapshot chứa funding/submission rồi mới warp; các số liệu thành công trong bảng thuộc lần chạy sau. Đây là giới hạn của cách test time warp bằng restart validator.

Các lần thử bị gián đoạn để lại dữ liệu QA trong MySQL local: Job `fd6b9f0a-6177-44a7-a5d5-79f2ea391edb` còn `SUBMITTED_FOR_REVIEW` dù escrow của lần chạy đó đã mất sau khi validator khôi phục snapshot cũ; một lần funding khác lỗi tại Contract `561052d2-57eb-475a-9722-2adad7715d29`. Không dùng các record này làm bằng chứng thành công. Validator đã được dừng sau kiểm thử để không giữ clock đã warp cho các luồng demo khác.

**Trạng thái gate:** local E2E của Client approve release, mutual refund và permissionless release + Marketplace reconcile **PASS**. Gate trong [implementation plan](SOLANA_ESCROW_IMPLEMENTATION_PLAN.md) yêu cầu scheduler timeout release và Admin dispute refund cho hai Job riêng vẫn **OPEN**. Browser UI E2E và devnet demo cũng chưa chạy.

**Cập nhật 2026-10-09:** Hai ca scheduler timeout release và Admin dispute refund qua Marketplace đã PASS trong [báo cáo E2E mới](SOLANA_ESCROW_GATE_E2E_20261009.md). Đoạn trạng thái ngay trên phản ánh mốc lịch sử 2026-10-08; browser UI E2E Solana và devnet vẫn mở.
