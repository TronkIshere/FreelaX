# FreelaX Business Documentation

Bộ tài liệu này bổ sung góc nhìn **nghiệp vụ/hệ thống** cho final frozen P06, hạn chế tập trung vào implementation detail.

## CURRENT TRUTH — thứ tự đọc

1. [Hệ thống và luồng nghiệp vụ](FREELAX_SYSTEM_BUSINESS_FLOW_20261008.md)
2. [Solana giải quyết bài toán nghiệp vụ gì](FREELAX_SOLANA_BUSINESS_ROLE_20261008.md)
3. [Những gì đã làm, lỗi đã gặp và cách khắc phục](FREELAX_IMPLEMENTATION_ISSUES_FIXES_20261008.md)
4. [Chỉ mục đường dẫn theo nghiệp vụ](FREELAX_SOURCE_PATH_INDEX_20261008.md)
5. [Final UI/MVP Freeze Handoff](../ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md)

## HISTORICAL / TECHNICAL REFERENCE

- **06.** [MVP Functional Specification](../mvp-functional-spec.md) — tài liệu thiết kế, có đề xuất và quyết định lịch sử; không coi toàn bộ là API hiện hành.
- **07.** [Solana integration report](../../solana-stablecoin-payout/docs/bao-cao-tich-hop-freelax-solana.md) — phân tích kỹ thuật/lịch sử, không chứng minh mọi khuyến nghị đã triển khai.

Khi có khác biệt, source/API hiện tại và final frozen handoff quyết định sự thật triển khai. Bộ business docs diễn giải baseline đó, không thay đổi P06 freeze.

## Authority

- Final source authority: `7fc31555b9cd3f50a23872401968ee67c5275f30`
- Final docs authority trước addendum này: `519e77c5d689319009ddcd288de932d617fe78d0`
- Final frozen handoff: `docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md`

## Cách dùng

Nếu cần thuyết trình/giải thích dự án, đọc theo thứ tự:

1. Hệ thống và luồng nghiệp vụ.
2. Vai trò Solana.
3. Lỗi/khắc phục và E2E proof.
4. Source path index khi cần tìm file cụ thể.
