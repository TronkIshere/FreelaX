# FreelaX Business Documentation

Bộ tài liệu này bổ sung góc nhìn **nghiệp vụ/hệ thống** cho final frozen P06, hạn chế tập trung vào implementation detail.

## CURRENT TRUTH — thứ tự đọc

1. [Hệ thống và luồng nghiệp vụ](FREELAX_SYSTEM_BUSINESS_FLOW_20261008.md)
2. [Solana giải quyết bài toán nghiệp vụ gì](FREELAX_SOLANA_BUSINESS_ROLE_20261008.md)
3. [Những gì đã làm, lỗi đã gặp và cách khắc phục](FREELAX_IMPLEMENTATION_ISSUES_FIXES_20261008.md)
4. [Chỉ mục đường dẫn theo nghiệp vụ](FREELAX_SOURCE_PATH_INDEX_20261008.md)
5. [Final UI/MVP Freeze Handoff](../ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md)

## CURRENT UPGRADE — đã có implementation, đang chờ E2E verification

- [Solana milestone escrow implementation plan](SOLANA_ESCROW_IMPLEMENTATION_PLAN.md) — code cho Anchor, Gateway, Marketplace và frontend đã có trên nhánh `feat/solana-milestone-escrow`; một số ca local-validator E2E đã qua, toàn bộ gate và devnet demo chưa hoàn tất.
- [Kết quả E2E escrow local 2026-10-08](SOLANA_ESCROW_LOCAL_E2E_20261008.md) — đã chứng minh release, mutual refund và permissionless release/reconcile trên local validator; scheduler tự gọi timeout và Admin dispute refund xuyên Marketplace vẫn mở.
- [Tiền thật, quyền ví và escrow](FREELAX_UPGRADE_AND_ESCROW_NOTE_20261008.md) — ghi chú đánh giá baseline P06 và trạng thái nâng cấp hiện tại. Các phần mô tả P06 là ảnh chụp lịch sử, không phải trạng thái của nhánh escrow.
- [Đối chiếu đề xuất nghiệp vụ và khoảng trống hiện tại](PARTNER_ESCROW_BUSINESS_GAP_20261008.md) — luồng đối tác giữ USD, đối soát, phí lúc giải ngân, nghiệm thu, tranh chấp, giao trễ và thanh toán theo giai đoạn; các con số đề xuất chưa phải chính sách đã chốt.

## HISTORICAL / TECHNICAL REFERENCE

- **06.** [MVP Functional Specification](../mvp-functional-spec.md) — tài liệu thiết kế, có đề xuất và quyết định lịch sử; không coi toàn bộ là API hiện hành.
- **07.** [Solana integration report](../../solana-stablecoin-payout/docs/bao-cao-tich-hop-freelax-solana.md) — phân tích kỹ thuật/lịch sử, không chứng minh mọi khuyến nghị đã triển khai.

Khi có khác biệt, source/API trên nhánh hiện tại và trạng thái kiểm chứng trong implementation plan quyết định sự thật triển khai. Final frozen handoff vẫn là bằng chứng lịch sử cho P06; không dùng nó để phủ nhận các thay đổi của track escrow sau freeze.

## Authority

- P06 source authority: `7fc31555b9cd3f50a23872401968ee67c5275f30`
- P06 docs authority: `519e77c5d689319009ddcd288de932d617fe78d0`
- Current escrow implementation branch: `feat/solana-milestone-escrow` (working tree implementation; verification gates are listed in the plan)
- Final frozen handoff: `docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md`

## Cách dùng

Nếu cần thuyết trình/giải thích dự án, đọc theo thứ tự:

1. Hệ thống và luồng nghiệp vụ.
2. Vai trò Solana.
3. Lỗi/khắc phục và E2E proof.
4. Source path index khi cần tìm file cụ thể.
