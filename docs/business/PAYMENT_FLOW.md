# Luồng tiền của một Job

Mỗi Job có **một `paymentFlowId`**. Mọi bước tiền (nộp USD, USDC, vault, release, rút, chi VND, phí, hoàn USD) đều gắn với mã này và có bằng chứng riêng. Hệ thống chỉ ghi một bước là "đã xác nhận" khi có bằng chứng từ nguồn độc lập: sao kê đối tác, hoặc dữ liệu trên Solana. Một response HTTP thành công không được tính là bằng chứng.

Phạm vi: local mock. Số liệu và đối tác đều mô phỏng.

## Tiền đi qua những đâu

```text
 USD (tài khoản Client)
   │ 1. Client nộp USD theo quote ─────────────── bằng chứng: sao kê USD của đối tác
   ▼
 USDC trong ví Client (Solana)
   │ 2. On-ramp chuyển Mock USDC ──────────────── bằng chứng: receipt on-chain theo purchase ID
   ▼
 Vault escrow của Milestone (Solana PDA)
   │ 3. Client ký chuyển vào vault ────────────── bằng chứng: tài khoản escrow + số dư vault
   │    → công việc mới được bắt đầu
   │ 4. Duyệt / hết 72 giờ / Admin quyết định
   ▼
 USDC trong ví Freelancer
   │ 5. Freelancer ký withdrawal ─────────────── bằng chứng: WithdrawalRecord + treasury tăng
   ▼
 Treasury FreelaX (Solana)
   │ 6. Đối tác trừ phí 3%, khấu trừ thuế 10%, chi VND
   │    ────────────────────────────────────────── bằng chứng: VND_PAYOUT + PLATFORM_FEE + TAX_WITHHELD
   ▼
 VND trong tài khoản Freelancer ─► 7. MISA phát hành chứng từ khấu trừ thuế
```

**Hủy trước release:** vault → USDC về ví Client → Client ký gửi USDC về treasury → đối tác hoàn **đủ** USD (sao kê `USD_REFUND`), phí 0. `USDC_REFUNDED` và `USD_REFUNDED` là hai mốc khác nhau.

**Quá hạn ký quỹ khi đã nộp USD:** timer không tự hủy, vì tiền có thể đã nằm ở đối tác hoặc trong ví Client. Admin bấm "Hủy hợp đồng quá hạn" (sau hạn ít nhất 15 phút), rồi tiền đi theo đường hoàn tiền ở trên.

## Ví dụ 100 USD

| Bước | Số tiền |
| --- | ---: |
| Client nộp | 100,00 USD |
| USDC vào ví Client, rồi vào vault | 100,000000 Mock USDC |
| Freelancer nhận khi release | 100,000000 Mock USDC |
| Phí FreelaX giữ ở off-ramp | 3,000000 USDC |
| Thu nhập tính thuế sau phí (97 × 25.000) | 2.425.000 VND |
| Thuế khấu trừ mô phỏng (10% × 2.425.000) | 242.500 VND |
| VND thực trả Freelancer | 2.182.500 VND |
| Nếu hủy trước release | 100,00 USD hoàn Client, phí 0 |

## Trạng thái của một bước

`NOT_STARTED` → `PENDING` / `PROCESSING` → `CONFIRMED`; nếu chưa rõ kết quả thì `UNKNOWN`; lỗi chắc chắn thì `FAILED`.

- `UNKNOWN` **không** có nghĩa là thất bại. Hệ thống tra cứu lại theo **cùng reference/idempotency key**, không gửi lệnh tiền mới.
- Release và refund loại trừ nhau. Một flow chỉ có thể có một trong hai.

## Đối soát 4 ranh giới (màn Admin "Đối soát luồng USDC")

| Ranh giới | So khớp | Mã khi khớp |
| --- | --- | --- |
| USD → USDC Client | Sao kê USD ↔ receipt on-ramp ↔ đúng ví, mint, số lượng | `USD_USDC_MATCH` |
| Ví Client → vault | Escrow PDA ↔ Milestone, hai bên, mint, số tiền, số dư vault | `VAULT_MATCH` |
| Vault → người nhận | Escrow đã chốt + vault bằng 0 + đúng người nhận | `RECIPIENT_BY_ESCROW_INVARIANT` |
| Withdrawal → fiat | WithdrawalRecord ↔ lệnh đối tác ↔ sao kê VND + phí, hoặc hoàn USD | `FIAT_AND_FEE_MATCH` |

- **Trên màn Admin:** bốn ranh giới hiện là bốn chặng *Nộp USD → ví Client*, *Ví Client → vault*, *Vault → người nhận*, *Rút → VND / hoàn USD*, với nhãn *Khớp* (`MATCHED`), *Đang chờ* (`PENDING`), *Lệch* (`MISMATCH`), *Chưa rõ* (`UNKNOWN`). Flow có chặng Lệch hoặc Chưa rõ nằm trong nhóm **Cần xử lý**; mã đối soát và nguồn bằng chứng xem trong **Chi tiết**.
- **Chặn bước tiếp theo:** ký escrow cần ranh giới 1 `MATCHED`; rút USDC và gửi lệnh fiat cần ranh giới 1–3 `MATCHED`. Trạng thái `MISMATCH` hoặc `UNKNOWN` sẽ chặn.
- **Quyết định Admin** (`ACKNOWLEDGED`, `ESCALATED`, `CLEARED_BY_EVIDENCE`) chỉ ghi audit, không chuyển tiền, không ghi đè bằng chứng. Hệ thống từ chối "Đã khớp theo bằng chứng mới" khi bằng chứng hiện tại chưa khớp.
- **Bảng "Tiền đang ở đâu":** tổng theo từng loại tiền, không bao giờ quy đổi cộng chung. Gồm: USD (đã nhận, chờ on-ramp, phải hoàn, đã hoàn); USDC (ví Client, vault, ví người nhận, treasury chờ chi, phí đã thu); VND (phải chi, đã chi); số bước `UNKNOWN`.

## Phí và chứng từ thuế

- Phí 3% = `round(giá Job × 3%, 0,01)`, do Freelancer chịu. Toàn bộ USDC được release cho Freelancer. Tại bước đổi tiền, thu nhập tính thuế = `(USDC đã release − phí USDC) × tỷ giá USDC/VND đã khóa`; thuế mô phỏng = `round(thu nhập tính thuế × 10%)`; VND thực trả = `thu nhập tính thuế − thuế`. Phí và thuế chỉ được ghi nhận khi WithdrawalRecord trên chuỗi cùng ba sao kê `VND_PAYOUT`, `PLATFORM_FEE`, `TAX_WITHHELD` khớp.
- Local mock khóa `1 USDC = 1 USD` và `1 USD = 25.000 VND`, nên tỷ giá hiệu dụng `1 USDC = 25.000 VND`. Đây là hai cặp tỷ giá khác nhau, dù số 25.000 trùng nhau trong mô phỏng.
- Sau khi `VND_PAYOUT` được xác nhận, hệ thống phát hành chứng từ khấu trừ thuế qua MISA. Chứng từ ghi thu nhập **sau phí dịch vụ, trước thuế**, và thuế đã giữ từ khoản VND phải chi; nguồn tỷ giá ghi `LOCKED_PAYOUT_QUOTE`. Freelancer xem và tải PDF/XML ở *Thu nhập → Chứng từ thuế*. Nếu phát hành lỗi, hệ thống dừng lại và chờ người dùng bấm "Lập lại chứng từ".
- Chứng từ đã phát hành theo công thức cũ không tự bị sửa khi cập nhật phần mềm. Trang chứng từ cảnh báo khi số thuế trên chứng từ không khớp với khoản chi cũ; muốn thay thế chứng từ cần một quy trình điều chỉnh riêng.

## Job cũ

Các rail cũ (`SIMULATED`, `SOLANA_ESCROW`, `PARTNER_ESCROW_MOCK`) chỉ còn để đọc và xử lý Job đã tạo trước khi chuyển đổi. Không chuyển tiền đang dở của Job cũ sang luồng mới. Xem [runbook](CUTOVER_RUNBOOK.md).
