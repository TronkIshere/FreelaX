# FreelaX — tài liệu hệ thống và nghiệp vụ

Bộ tài liệu này giải thích ai giữ tiền, khi nào Job được bắt đầu, cách nghiệm thu, giải ngân, hoàn tiền và đối soát. Mỗi file phân biệt **P06 đã freeze**, **escrow token Solana có code trên nhánh hiện tại**, và **luồng đối tác USD→VND đã chốt quy tắc nhưng chưa triển khai**.

## Tóm tắt trạng thái nghiệp vụ

| Luồng | Trạng thái | Điều có thể nói khi demo |
| --- | --- | --- |
| Payment P06 | Ledger **mô phỏng**, E2E P06 đã có | Job chỉ mở việc sau funding mô phỏng `SUCCEEDED`; không khẳng định đã giữ USD thật. |
| `SOLANA_ESCROW` | Có code vault token, một số nhánh E2E local đã qua | Mock token được giữ/chuyển trong vault; chưa phải tài khoản USD đối tác hoặc VND ngân hàng. |
| Đối tác giữ USD → chi VND | **Chưa có code/E2E**; quy tắc đã chốt để triển khai mock | Sau này Client nộp đủ USD, đối tác mock xác nhận, FreelaX đối soát rồi Freelancer mới làm; phí **3% do Freelancer chịu chỉ lúc chi thành công**, hoàn trước chi trả đủ USD. |

Mỗi khoản tiền chỉ thuộc **một rail**. Không cộng ledger mô phỏng P06, vault Solana và sao kê đối tác mock thành một số dư. Mọi màn hình đối tác MVP phải ghi rõ **mô phỏng**. [Luồng và quy tắc chi tiết](PARTNER_ESCROW_BUSINESS_GAP_20261008.md).

## Đọc theo câu hỏi nghiệp vụ

1. [Hệ thống và luồng công việc](FREELAX_SYSTEM_BUSINESS_FLOW_20261008.md) — vai trò, điều khoản, funding, làm việc, nghiệm thu, hoàn tất; phần 0 cập nhật nhánh hiện tại và luồng mục tiêu.
2. [Đối tác giữ USD, phí 3% và đối soát](PARTNER_ESCROW_BUSINESS_GAP_20261008.md) — quy tắc đã chốt, trạng thái mục tiêu, công thức, ví dụ và khoảng trống triển khai.
3. [Vai trò Solana](FREELAX_SOLANA_BUSINESS_ROLE_20261008.md) — tách P06 evidence, vault token escrow và đối tác fiat.
4. [Kết quả E2E escrow local](SOLANA_ESCROW_LOCAL_E2E_20261008.md) — nhánh token nào đã qua và điều gì chưa được chứng minh.
5. [Chỉ mục đường dẫn nghiệp vụ](FREELAX_SOURCE_PATH_INDEX_20261008.md) — tìm source hiện có và nhận diện chức năng chưa có source.

## Trạng thái triển khai và tài liệu lịch sử

- [Kế hoạch và trạng thái Solana escrow](SOLANA_ESCROW_IMPLEMENTATION_PLAN.md) — code đã có, full gate E2E và devnet chưa xong; không chứa triển khai phí FreelaX 3%.
- [Ghi chú nâng cấp và xác minh escrow](FREELAX_UPGRADE_AND_ESCROW_NOTE_20261008.md) — đánh giá P06, track Solana và phân biệt luồng đối tác mới.
- [Lịch sử triển khai, lỗi và bài học](FREELAX_IMPLEMENTATION_ISSUES_FIXES_20261008.md) — chứng cứ P06 và các kết luận nghiệp vụ sau P06.
- [Final UI/MVP Freeze Handoff](../ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md) — ảnh chụp lịch sử P06, không phải xác nhận các luồng mới đã chạy.

## Tài liệu tham khảo lịch sử

- **06.** [MVP Functional Specification](../mvp-functional-spec.md) — tài liệu thiết kế, có đề xuất và quyết định lịch sử; không coi toàn bộ là API hiện hành.
- **07.** [Solana integration report](../../solana-stablecoin-payout/docs/bao-cao-tich-hop-freelax-solana.md) — phân tích kỹ thuật/lịch sử, không chứng minh mọi khuyến nghị đã triển khai.

Khi có khác biệt, source/API và kết quả kiểm chứng trên nhánh hiện tại quyết định **tính năng đang chạy**. [Luồng đối tác](PARTNER_ESCROW_BUSINESS_GAP_20261008.md) quyết định **nghiệp vụ mục tiêu đã chốt** cho phí 3%, nhưng không phải bằng chứng đã có code. Các mốc 3 ngày tự duyệt, 2 lần nhắc, 3 ngày thương lượng và 5 ngày điều phối vẫn là **đề xuất**; nhiều milestone là giai đoạn sau MVP.

## Authority

- P06 source authority: `7fc31555b9cd3f50a23872401968ee67c5275f30`
- P06 docs authority: `519e77c5d689319009ddcd288de932d617fe78d0`
- Current escrow implementation branch: `feat/solana-milestone-escrow` (code committed; verification gates are listed in the plan)
- Final frozen handoff: `docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md`
