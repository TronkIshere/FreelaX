# Runbook chuyển đổi sang `UNIFIED_USDC_PAYOUT`

**Phạm vi:** local mock (Mock USDC, local validator, đối tác USD/VND mô phỏng). Tài liệu này hướng dẫn bật flag, kiểm tra trước/sau, rollback và xử lý khoản cũ đang dở. Điều kiện nghiệp vụ ở [checklist](PAYMENT_FLOW_REBUILD_CHECKLIST.md); bằng chứng ở [VERIFICATION](VERIFICATION.md). Không áp dụng nguyên văn cho tiền thật hoặc devnet.

## Flag và phạm vi tác động

- `PAYMENT_FLOW_CUTOVER_ENABLED` chỉ quyết định **Job tạo mới**: lúc tạo, Job ghi `paymentFlowVersion = 1` và chụp network/mint vào điều khoản. Job đã tạo giữ version đã ghi, kể cả khi được phân công sau khi flag đổi.
- Contract của Job version 1 mang rail `UNIFIED_USDC_PAYOUT`; ba API funding cũ (`SIMULATED`, `SOLANA_ESCROW`, `PARTNER_ESCROW_MOCK`) từ chối Contract này. Job version 0 tiếp tục theo rail đã ghi và công cụ xử lý cũ.
- Flag không đổi `paymentRail`, không tạo lệnh tiền và không migrate dữ liệu.

## Trước khi bật

1. Validator chạy và program đúng bản (có tham số `high_value_review_grace`): `solana program show 2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb --url http://127.0.0.1:9123`.
2. Marketplace khởi động với Flyway ở V2: log có `now at version v2`; bảng `flyway_schema_history` có dòng baseline 1 và migration 2.
3. Gateway trả config có `acceptedMint`, `paused = false`, rate authority và mock on-ramp authority.
4. Chạy E2E unified (API) và browser E2E tài khoản mới theo [VERIFICATION](VERIFICATION.md); cả hai flow phải có bốn ranh giới `MATCHED` trong Admin.
5. Lập danh sách khoản cũ đang dở (mục dưới) và người chịu trách nhiệm xử lý.

## Bật flag

Đặt `PAYMENT_FLOW_CUTOVER_ENABLED=true` trong `.env` rồi tạo lại **chỉ** Marketplace:

```bash
docker compose up -d --no-deps --force-recreate --wait marketplace-backend
docker compose exec -T marketplace-backend printenv PAYMENT_FLOW_CUTOVER_ENABLED   # true
```

Không dùng lệnh `up` có `depends_on` mà thiếu biến này: Compose tạo lại Marketplace với giá trị mặc định `false`.

## Rollback

- **Được phép:** đặt lại flag `false` và tạo lại Marketplace. Job mới từ đó dùng rail cũ; Job version 1 đã tạo **vẫn** là unified và tiếp tục xử lý bằng flow unified (timeline, withdrawal, đối soát, Admin hủy quá hạn).
- **Không được:** đổi `paymentRail` hoặc `paymentFlowVersion` của Job/Contract đã tạo, xóa/sửa `payment_flow_steps`/`payment_flow_evidence`, tạo lệnh funding cũ cho Contract unified, hay gửi lại lệnh tiền bằng tay khi bước đang `PROCESSING/UNKNOWN`.
- Code rollback (image cũ) chỉ an toàn khi chưa có Job version 1 nào đã mở USD order. Bản Marketplace trước Flyway vẫn chạy được trên schema V2 vì V2 chỉ thêm cột nullable, nhưng không biết các đường xử lý mới (Admin hủy quá hạn, chặn theo đối soát).

## Khoản đang dở trên DB local (đọc ngày 2026-10-09, sau E2E)

Truy vấn tái lập:

```sql
SELECT COALESCE(payment_rail,'SIMULATED') rail, BIN_TO_UUID(job_id) job, status, created_at
FROM work_contracts WHERE status NOT IN ('COMPLETED','CANCELLED') ORDER BY 1, created_at;
SELECT BIN_TO_UUID(payment_flow_id) flow, kind, status FROM payment_flow_steps
WHERE status IN ('PENDING','PROCESSING','UNKNOWN','FAILED','AWAITING_CLIENT') ORDER BY 1, 2;
SELECT last_chain_status, COUNT(*) FROM escrow_contracts GROUP BY 1;
```

| Rail | Job / flow | Trạng thái | Tiền đang ở đâu | Cách xử lý (trên rail gốc) |
| --- | --- | --- | --- | --- |
| `SIMULATED` | `c860c441-…`, `234591c0-…` | Contract `PENDING_FUNDING` | Chưa có funding | Timer cũ hủy sau 48 giờ; không cần thao tác |
| `SOLANA_ESCROW` | `0fe415b3-…` | `PENDING_FUNDING`, escrow `AWAITING_SIGNATURE` | Chưa có vault | Scheduler escrow hủy sau hạn funding + 15 phút |
| `SOLANA_ESCROW` | `fd6b9f0a-…` | `UNDER_REVIEW`, escrow `Submitted` | Vault trên **ledger demo cũ** | Chỉ xử lý khi chạy lại ledger đó; không suy ra trạng thái từ ledger unified |
| `UNIFIED` | `d8cac439-…`, `8e422b07-…`, `7da3c72c-…` | `PENDING_FUNDING` | Chưa có USD order hoặc USD order chưa xác nhận | Hết hạn: nếu chưa có tiền, timer hủy; nếu USD/USDC đã vào thì Admin dùng “Hủy hợp đồng quá hạn” |
| `UNIFIED` | flow `f07293e8-…` | `WITHDRAWAL = PENDING` | 12 USDC ở ví Freelancer QA | Freelancer ký withdrawal; Job đã `COMPLETED` |
| `UNIFIED` | flow `1200b938-…`, `d6f86555-…`, `e9c6a5c5-…` | `WITHDRAWAL = PROCESSING`, chưa có chữ ký | USDC ở ví người nhận; quote đã hết hạn | Người nhận chuẩn bị lại (quote mới cùng withdrawal ID) và ký; scheduler chỉ tra cứu, không tự gửi |

Bổ sung 2026-10-10: flow `1ca58c5b-…` (ca timeout) chỉ được release trên bản sao ledger warp, nên trên ledger gốc Admin báo `TERMINAL_CHAIN_MISMATCH` và withdrawal bị chặn — giữ làm bằng chứng phát hiện lệch, đã ghi quyết định `ESCALATED`. Flow `08882bda-…` và `1200b938-…` lệch với chain hiện tại (ledger demo cũ), scheduler chỉ cảnh báo, không gửi lệnh. Các withdrawal chuẩn bị mà không ký đã tự về `PENDING` sau khi quote hết hạn.

Các Job QA tạo bằng tài khoản thử nghiệm ngẫu nhiên không có người dùng thật; giữ nguyên để đối soát, không xóa bằng cập nhật DB.

## Hết hạn funding với Job unified

- Chưa mở USD order, order thất bại, hoặc quote hết hạn mà Client chưa nộp: timer hủy Contract như cũ.
- USD order đã gửi (`PENDING/PROCESSING/UNKNOWN/CONFIRMED`): timer **không** hủy. Khi USDC đã vào ví Client và chưa có escrow on-chain, Admin vào *Quản trị / Đối soát luồng USDC*, ghi chú và bấm “Hủy hợp đồng quá hạn” (server yêu cầu quá hạn ít nhất 15 phút). Client sau đó ký gửi USDC về treasury và nhận hoàn USD theo đường hoàn tiền thường.
- USD đã nhận nhưng on-ramp chưa xác nhận: chờ on-ramp tự đối soát; nếu on-ramp kết thúc lỗi có chữ ký, Admin ghi quyết định `ESCALATED` và xử lý với đối tác.

## Quyết định của Admin

Admin chỉ ghi quyết định có audit (`ACKNOWLEDGED`, `ESCALATED`, `CLEARED_BY_EVIDENCE`) cho từng ranh giới. Quyết định không chuyển tiền và không ghi đè bằng chứng; `CLEARED_BY_EVIDENCE` bị từ chối khi bằng chứng hiện tại chưa `MATCHED`. Bước tiền tiếp theo (ký escrow, withdrawal, lệnh fiat) chỉ chạy khi các ranh giới trước đó `MATCHED`.

## Nhật ký chuyển đổi

| Ngày | Môi trường | Hành động | Kiểm tra |
| --- | --- | --- | --- |
| 2026-10-09 | Local mock (Compose + localnet) | Chủ sản phẩm xác nhận chuyển đổi khi các gate còn mở đã được ghi trong [checklist](PAYMENT_FLOW_REBUILD_CHECKLIST.md#gate-hoàn-tất). Thêm `PAYMENT_FLOW_CUTOVER_ENABLED=true` vào `.env` local (Git bỏ qua), tạo lại riêng `marketplace-backend`. | Trước: program slot 7367, Flyway V2, Gateway có mint/rate/on-ramp authority, không pause. Sau: `printenv` trả `true`; Job kiểm tra `f7a19474-8bbe-43e4-b57a-79eae13edddf` tạo với review nhập 48 giờ được ghi 72 giờ, rail `UNIFIED_USDC_PAYOUT`, mint `DXeZia7qViiE8nsF4XLz2NH1yeZF57Wk2kbCn3JYExRZ`, rồi hủy khi còn `OPEN`; Job cũ đọc được theo rail gốc (SOLANA_ESCROW 17, PARTNER_ESCROW_MOCK 22, SIMULATED 2, UNIFIED 10). |
| 2026-10-10 | Local mock | Chủ sản phẩm chốt quy tắc gia hạn bàn giao (một lần, ≤ hạn gốc + 7 ngày, Client duyệt on-chain); Gate 0 và Gate hoàn tất đạt; trạng thái tài liệu chuyển sang “đã triển khai ở local mock”. Không mở phạm vi tiền thật/devnet. | Checklist chỉ còn mục đã đánh dấu; flag local vẫn `true`; điều khoản xem trước hiển thị quy tắc gia hạn. |
| 2026-10-10 | Local mock | Bổ sung chứng từ thuế cho Job unified (PR #7): scheduler phát hành chứng từ cho các flow đã chi VND; Flyway V3 thêm nguồn tỷ giá `LOCKED_PAYOUT_QUOTE`. | 7 chứng từ `ACCEPTED` cho các flow đã chi trước đó; PDF/XML tải được. |

`docker-compose.yml` vẫn để mặc định `false`; giá trị bật chỉ nằm trong `.env` của môi trường đã chuyển đổi. Rollback theo mục [Rollback](#rollback).
