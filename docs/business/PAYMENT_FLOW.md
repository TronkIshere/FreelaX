# FreelaX — luồng thanh toán thống nhất

**Quy ước:** các mục **Mục tiêu** là hợp đồng nghiệp vụ cần triển khai. Các mục **Hiện có** mô tả code local/demo ngày 2026-10-09. [Checklist xây lại](PAYMENT_FLOW_REBUILD_CHECKLIST.md) theo dõi từng bước; [verification](VERIFICATION.md) ghi điều đã chứng minh. Tài liệu này không xác nhận có USD, USDC hoặc VND thật trong hệ thống hiện nay.

[Bản điều khoản 100 USD cho local mock](UNIFIED_FLOW_LOCAL_TERMS.md) ghi rõ các giả định về quote, phí, hoàn tiền và thời hạn để hai bên xét duyệt trước khi mở funding.

## Một luồng tiền cho một Job

```text
USD Client → on-ramp xác nhận → USDC ví Client
→ escrow vault của Milestone → release USDC vào ví Freelancer
→ off-ramp nhận USDC → đối tác xác nhận chi VND Freelancer
```

Nếu Job được hoàn trước release: `escrow vault → USDC ví Client → đối tác hoàn USD`, với xác nhận riêng cho từng bước. Solana chỉ giữ/chuyển USDC; đối tác chịu các bước fiat ngoài chain. Một `paymentFlowId` gắn Job, Contract, Milestone, lệnh đối tác và các transaction on-chain; từng giai đoạn có reference riêng. Không coi chúng là ba rail để Client chọn.

## Tiền ở đâu và khi nào được chuyển tiếp

| Giai đoạn mục tiêu | Nơi giữ tiền | Bằng chứng bắt buộc | Chỉ sau khi xác minh mới được |
| --- | --- | --- | --- |
| `USD_RECEIVED` | Tài khoản của đối tác on-ramp | Lệnh nạp, sao kê/confirmation độc lập, đúng người gửi, USD và số tiền | Tạo/chuyển USDC theo quote đã chốt |
| `CLIENT_USDC_CONFIRMED` | Ví USDC của Client | Tx confirmed/finalized và balance/delta của đúng mint, ví, số lượng | Client ký funding escrow |
| `ESCROW_FUNDED` | Vault PDA riêng của Milestone | Escrow PDA, mint, Client/Freelancer, amount, status, vault balance và tx | Job `IN_PROGRESS`; Freelancer bàn giao |
| `WORK_ACCEPTED` | USDC vẫn trong vault | Bản giao hợp lệ và quyết định Client, timeout hợp lệ hoặc Admin dispute | Gửi lệnh release; chưa được ghi đã trả tiền |
| `USDC_RELEASED` | Ví USDC của Freelancer | Tx release, escrow terminal, vault giảm đúng amount, ví Freelancer tăng đúng amount | Tạo off-ramp từ số USDC đã nhận |
| `OFFRAMP_USDC_RECEIVED` | Treasury/đối tác off-ramp | Tx Freelancer → treasury và WithdrawalRecord đúng amount, mint, owner | Đối tác xử lý lệnh chi VND idempotent |
| `VND_PAID` | Tài khoản ngân hàng Freelancer | Provider payout reference, xác nhận/sao kê chi VND, beneficiary, amount và đối soát | Ghi payout hoàn tất, phí FreelaX và chứng từ liên quan |

Một lần đọc UI, callback chưa xác minh hoặc response HTTP thành công **không thay thế** bằng chứng ở cột thứ ba. Mỗi bước `PENDING`, `PROCESSING` hoặc `UNKNOWN` giữ nguyên reference để đối soát; không khởi tạo lệnh đối nghịch hoặc lệnh mới có thể trùng tiền.

## Đối soát tại từng ranh giới

1. **USD → USDC:** sao kê USD của đối tác khớp order, Client, contract, quote và lượng USDC vào ví Client. Sai số, giao dịch thiếu hoặc amount không đủ giữ Job trước funding.
2. **Ví Client → vault:** xác minh đúng mint, escrow PDA/Milestone, hai participant, token delta và vault balance. Chỉ sau đó mở việc; UI không tự chuyển `IN_PROGRESS`.
3. **Vault → ví Freelancer hoặc Client:** kết quả release/refund phải terminal đúng một lần; đối chiếu vault về 0 và ví nhận tăng đúng lượng. Quyết định nghiệp vụ và token transfer là hai trạng thái khác nhau.
4. **USDC → VND:** withdrawal khớp USDC chuyển tới treasury; quote/fee/beneficiary khớp lệnh payout; sao kê đối tác xác nhận VND thực chi. Mất phản hồi sau khi provider đã chi phải **tra cứu cùng payout ID**, không gửi lệnh chi mới.
5. **Đối soát tổng:** theo từng loại tiền, so sánh nghĩa vụ chưa tất toán với USD/USDC/VND đang được giữ hoặc đang chuyển. Không cộng các đồng tiền bằng một tỷ giá tự chọn. Màn hình Admin hiển thị nguồn, thời điểm sao kê, khoản `UNKNOWN`, chênh lệch và người xử lý.

## Điều khoản, tỷ giá và phí

- **Giá Job:** `grossUsd` và amount USDC cần vào escrow được chốt trong quote trước khi Client nộp USD. Mock local có thể dùng tỷ lệ 1 USD = 1 Mock USDC; production không được ngầm coi USD và USDC là cùng một số dư hoặc bỏ qua phí on-ramp.
- **FreelaX 3%:** `platformFeeUsd = round(grossUsd × 0.03, 2)`, do Freelancer chịu. Chỉ ghi doanh thu phí **một lần sau khi VND payout và phần phí được đối soát thành công**. Escrow release chuyển toàn bộ USDC cho Freelancer; Freelancer ký withdrawal toàn bộ vào treasury FreelaX và đối tác chỉ đổi phần sau phí sang VND. Trên local mock, phí chỉ được ghi khi WithdrawalRecord on-chain và hai sao kê `VND_PAYOUT` + `PLATFORM_FEE` cùng khớp; provider thật cần cơ chế và bằng chứng riêng.
- **Tỷ giá:** lệnh off-ramp lưu quote USDC/USD, USD/VND, nguồn, thời điểm, hạn hiệu lực và payout VND trước/sau phí. Retry cùng idempotency key dùng đúng quote đã khóa, không tính lại theo tỷ giá mới.
- **Hoàn trước release:** mục tiêu nghiệp vụ là Client nhận lại đủ `grossUsd`, phí FreelaX bằng 0. Vault trả USDC về Client trước; đối tác hoàn USD là giao dịch fiat riêng. Trước khi bật tính năng, phải chốt ai chịu phí on-ramp/off-ramp và rủi ro tỷ giá để có thể thực hiện lời hứa hoàn đủ USD. Nếu chưa bảo đảm, không hiển thị “đã hoàn USD”.
- **Sau release:** USDC đã sang Freelancer nên không dùng nhánh refund escrow thông thường. Dispute/hủy phải được xử lý trước terminal release; trường hợp payout VND lỗi sau release là khoản phải đối soát/khắc phục, không được tự phát lệnh refund từ vault đã rỗng.

## Trạng thái công việc tách khỏi trạng thái tiền

| Công việc | Tiền | Ý nghĩa hiển thị |
| --- | --- | --- |
| Chưa làm | USD/on-ramp/escrow còn chờ | Freelancer chưa được bắt đầu |
| Đang làm hoặc đang review | `ESCROW_FUNDED` | Tiền USDC được giữ trong vault, chưa thuộc ví Freelancer |
| Đã duyệt | `RELEASE_PENDING` | Được phép gửi release, chưa xác nhận token đến ví Freelancer |
| Công việc hoàn tất | `USDC_RELEASED`, off-ramp đang chờ | Freelancer đã nhận USDC; **chưa** nói đã nhận VND |
| Công việc hoàn tất | `VND_PAID` | Đối tác đã xác nhận và đối soát chi VND |
| Hủy trước release | `REFUND_PENDING` → `USDC_REFUNDED` → `USD_REFUNDED` | Chỉ dòng cuối xác nhận Client đã nhận USD |

Review hai chiều và tax/certificate là hậu xử lý với điều kiện riêng. Chúng không làm `VND_PAID` hoặc `USD_REFUNDED` trở thành sự thật. Hạn review, số lần sửa và hạn dispute phải được snapshot trên Contract; scheduler dùng đồng hồ server/chain và dừng release khi có dispute hợp lệ.

## Code hiện có và việc phải nối

| Đoạn | Hiện có trên nhánh local | Thiếu để thành một luồng |
| --- | --- | --- |
| Funding P06 | Payment ledger mô phỏng, mặc định `SIMULATED` | Không dùng làm bằng chứng USD/USDC cho Job mới sau cutover |
| Mock on-ramp + Invoice | Có `mock_onramp`, Invoice và downstream sau primary release mô phỏng | Chuyển on-ramp **trước** funding escrow; cùng payment flow; Invoice direct pay không thay escrow |
| Solana vault | `SOLANA_ESCROW` funding/release/refund token local | Nhận USDC từ on-ramp của đúng Job và kích hoạt off-ramp sau release |
| Đối tác mock | `PARTNER_ESCROW_MOCK` nhận USD và chi VND trực tiếp, đối soát sao kê; phí 3% | Tách thành adapter nhận USD và adapter payout VND; bỏ đường đi tắt USD → VND cho Job mới |
| Off-ramp P06 | `request_offramp`, WithdrawalRecord và VND payout mô phỏng có code | Dùng USDC mà vault của **cùng Job** vừa release, xác nhận provider payout và phí |

Các bản ghi và đường xử lý cũ được giữ để đọc/giải quyết Job cũ; Job mới sau cutover chỉ dùng flow thống nhất. Chưa được gọi flow mới là **đã triển khai** trước khi E2E một Job đi đủ USD → USDC → escrow → USDC → VND, cùng các nhánh refund và `UNKNOWN`, qua [gate hoàn tất](PAYMENT_FLOW_REBUILD_CHECKLIST.md#gate-hoàn-tất).
