# Lưu trữ nghiệp vụ theo mốc ngày

Các file ở đây giữ nguyên chi tiết điều tra, quyết định, guide demo cũ và E2E tại từng thời điểm. Chúng có thể ghi một gate là **OPEN** trước khi một biên bản mới hơn xác minh **PASS**. Đọc [nghiệp vụ chính thức](../README.md), [luồng thanh toán thống nhất](../PAYMENT_FLOW.md), [kiến trúc Solana](../SOLANA_ARCHITECTURE.md), [checklist xây lại](../PAYMENT_FLOW_REBUILD_CHECKLIST.md) và [trạng thái kiểm chứng](../VERIFICATION.md) trước.

| Cần tra cứu | Tài liệu |
| --- | --- |
| P06: luồng nghiệp vụ, vai trò Solana, source path, lỗi và bài học | [Business flow](FREELAX_SYSTEM_BUSINESS_FLOW_20261008.md), [Solana role](FREELAX_SOLANA_BUSINESS_ROLE_20261008.md), [Source path index](FREELAX_SOURCE_PATH_INDEX_20261008.md), [Issues/fixes](FREELAX_IMPLEMENTATION_ISSUES_FIXES_20261008.md) |
| Đánh giá nâng cấp escrow từ P06 | [Upgrade note](FREELAX_UPGRADE_AND_ESCROW_NOTE_20261008.md), [Solana implementation plan](SOLANA_ESCROW_IMPLEMENTATION_PLAN.md) |
| Quy tắc chi tiết và khoảng trống đối tác mock | [Partner business gap](PARTNER_ESCROW_BUSINESS_GAP_20261008.md) |
| Bằng chứng Solana local theo ngày | [E2E 2026-10-08](SOLANA_ESCROW_LOCAL_E2E_20261008.md), [scheduler/Admin gate 2026-10-09](SOLANA_ESCROW_GATE_E2E_20261009.md) |
| Bằng chứng đối tác mock local | [Partner E2E 2026-10-09](PARTNER_MOCK_LOCAL_E2E_20261009.md) |
| Thao tác hai phía trên demo USD → VND đi tắt | [Guide E2E local cũ](LOCAL_TWO_SIDED_E2E_GUIDE.md) |

Tài liệu thiết kế MVP cũ nằm ở [functional spec](../../mvp-functional-spec.md); báo cáo tích hợp Solana cũ nằm ở [Solana report](../../../solana-stablecoin-payout/docs/bao-cao-tich-hop-freelax-solana.md). Các tài liệu này là tham chiếu lịch sử, không tự chứng minh API hiện hành.
