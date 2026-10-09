# Solana trong kiến trúc thanh toán FreelaX

**Vai trò chính thức:** Solana là lớp **giữ và chuyển USDC có thể xác minh** ở giữa on-ramp USD và off-ramp VND. Một Job có **một luồng tiền**: `USD Client → USDC Client → vault escrow của Milestone → USDC Freelancer → VND Freelancer`. `paymentFlowId` ở Marketplace liên kết các bước; Solana không nhận USD, không tự đổi USDC thành VND và không xác nhận tiền đã đến ngân hàng.

**Trạng thái 2026-10-10:** luồng USD order → on-ramp → escrow → withdrawal → VND mock payout và nhánh hoàn USD **đã triển khai ở local mock**: browser từ tài khoản mới, tranh chấp, hết hạn review, drill sự cố và đối soát bốn ranh giới đều đạt; môi trường local đã chuyển đổi. Devnet, tiền thật và đối tác thật chưa bắt đầu.

## Vị trí của Solana

```text
Client USD ──► Đối tác on-ramp / sao kê USD
                        │ xác nhận USD, chuyển USDC
                        ▼
                 Client USDC ATA (Solana)
                        │ Client ký fund
                        ▼
              Milestone Escrow PDA + vault ATA
                        │ nghiệm thu/timeout/dispute hợp lệ
                        ▼
              Freelancer USDC ATA (Solana)
                        │ Freelancer ký request_offramp
                        ▼
              Treasury USDC ATA + WithdrawalRecord
                        │ đối tác xử lý ngoài chain
                        ▼
              VND vào tài khoản Freelancer
```

`ATA` là token account của đúng mint và chủ ví. Escrow PDA/vault là nơi giữ token trước khi làm; treasury của off-ramp chỉ nhận USDC **sau khi Freelancer đã được release**. Hai tài khoản này có vai trò khác nhau và không được coi là cùng một số dư ký quỹ.

## Ranh giới trách nhiệm

| Thành phần | Quyết định/giữ gì | Bằng chứng trả về |
| --- | --- | --- |
| Frontend | Hiển thị điều khoản và trạng thái; chuyển transaction cho Client/Freelancer ký | Wallet address, signed transaction; không tự xác nhận tiền |
| Marketplace Backend | Authority nghiệp vụ Job/Contract/Milestone, `paymentFlowId`, quyền, điều kiện chuyển bước và đối soát | Timeline, reference/amount/status theo từng bước |
| Payment Backend/đối tác | USD vào, quote, VND chi hoặc USD hoàn; mock local chỉ mô phỏng | Statement, receipt, payout/refund reference độc lập |
| Solana Gateway | Build/send/read transaction và account qua RPC theo quyền đã cấu hình | Signature, PDA/ATA, trạng thái giao dịch và account |
| Anchor program | Kiểm tra signer, mint, amount, hạn, quyền release/refund; chuyển token | Escrow/WithdrawalRecord và token balance trên chain |

Browser chỉ gọi Marketplace; Gateway/RPC và API nội bộ đối tác không trở thành authority nghiệp vụ độc lập cho Job. Marketplace không được gán trạng thái thành công chỉ từ HTTP 200 hoặc signature chưa xác minh.

## Từng bước áp dụng cho một Job

1. **USD vào:** Marketplace tạo USD order có `paymentFlowId` và quote. Đối tác xác nhận nhận USD từ nguồn độc lập. Trên local, `mock_onramp` chuyển **Mock USDC có sẵn trong treasury demo** vào Client ATA và tạo receipt; nó không mint USDC thật hoặc chứng minh USD ngân hàng.
2. **Client funding escrow:** Marketplace xác minh on-ramp receipt, Client ATA, mint và lượng token theo hợp đồng. Client ký `fund_milestone_escrow` cùng marketplace authority theo điều kiện program. PDA theo Milestone ID giữ vault USDC; chỉ khi account và vault balance khớp mới ghi `ESCROW_FUNDED` và mở công việc.
3. **Làm việc/nghiệm thu:** Freelancer submit evidence hash; Client duyệt, yêu cầu revision hoặc mở dispute. Program lưu hạn/review và terminal state; Marketplace lưu bản giao, bằng chứng và quyết định nghiệp vụ. Scheduler có thể gửi timeout release sau hạn; Solana không tự chạy transaction vì đồng hồ trôi.
4. **Release hoặc refund:** Client duyệt hợp lệ, timeout đủ điều kiện hoặc Admin resolve dispute mới cho chuyển USDC từ vault sang Freelancer ATA; mutual/Admin refund chuyển USDC về Client ATA. Marketplace kiểm tra escrow terminal, đúng ví/mint/amount, vault và ATA delta trước khi ghi `USDC_RELEASED` hoặc `USDC_REFUNDED`.
5. **Off-ramp sau release:** Freelancer ký `request_offramp`; USDC vào treasury ATA, WithdrawalRecord được tạo. Đối tác ngoài chain chi VND theo quote đã khóa và beneficiary đã xác minh. Chỉ sau đối tác xác nhận chi và đối soát mới gọi `record_offramp`/transition phù hợp và ghi `VND_PAID`. WithdrawalRecord `Completed` một mình không thay sao kê ngân hàng.
6. **Hoàn USD nếu hủy:** `USDC_REFUNDED` chỉ nói token về Client ATA. Nếu điều khoản cam kết trả đủ USD, phải có lệnh đổi/hoàn qua đối tác và xác nhận USD về Client; bước này tách khỏi escrow và chưa được nối cho flow mới.

**Phí FreelaX 3%** do Freelancer chịu chỉ được ghi sau VND payout và phần phí được đối soát. `settle_milestone_escrow` hiện chuyển toàn bộ token từ vault cho Freelancer; program chưa trừ phí 3%. Cơ chế thu phần phí tại off-ramp cần chốt trước khi bật flow thống nhất. Tỷ giá và quote phải khóa theo lệnh, không tính lại khi retry.

## Đối soát Solana với fiat

| Ranh giới | Cần so khớp | Khi chưa khớp |
| --- | --- | --- |
| USD đối tác → Client ATA | USD order/statement ↔ receipt on-ramp ↔ đúng USDC mint, ví, token delta | Không cho fund vault |
| Client ATA → vault | Contract/Milestone/amount ↔ escrow PDA/status ↔ vault balance và tx | Không cho Freelancer bắt đầu |
| Vault → Freelancer/Client ATA | Quyết định release/refund ↔ escrow terminal ↔ vault về 0 ↔ participant đã ghi. `settle`/`refund_mutual` chuyển toàn bộ amount vào ATA participant trong cùng instruction ghi terminal, nên điều kiện này chứng minh ví nhận được ghi có kể cả khi lịch sử giao dịch đã mất | Không kết luận đã nhận USDC/hoàn USDC |
| Freelancer ATA → treasury | Withdrawal ID/amount/mint ↔ tx và WithdrawalRecord ↔ treasury delta | Không gửi/không lặp payout VND |
| Treasury/đối tác → VND | Withdrawal/quote/fee ↔ provider reference, beneficiary, statement chi VND | Không ghi `VND_PAID` hoặc phí đã thu |

Mọi lệnh có idempotency key và reference ổn định. Với RPC timeout, mất callback, mất lịch sử signature hoặc restart, đọc PDA/account/balance và sao kê theo **cùng reference** trước khi gửi lại. Không phát lệnh release và refund đối nghịch; `UNKNOWN` không có nghĩa là thất bại.

## Tái dùng code và giới hạn hiện tại

- Program: [`mock_onramp.rs`](../../solana-stablecoin-payout/programs/invoice_payments/src/instructions/mock_onramp.rs), [`milestone_escrow.rs`](../../solana-stablecoin-payout/programs/invoice_payments/src/instructions/milestone_escrow.rs), [`request_offramp.rs`](../../solana-stablecoin-payout/programs/invoice_payments/src/instructions/request_offramp.rs), [`record_offramp.rs`](../../solana-stablecoin-payout/programs/invoice_payments/src/instructions/record_offramp.rs). `pay_invoice` chuyển Client ATA → Freelancer ATA trực tiếp là nhánh Invoice cũ; **không thay** vault escrow của Job.
- Gateway: [`SolanaGatewayController.java`](../../solana-integration/src/main/java/com/freelax/solanagateway/api/SolanaGatewayController.java) và [`SolanaGatewayService.java`](../../solana-integration/src/main/java/com/freelax/solanagateway/service/SolanaGatewayService.java) có endpoint cho mock on-ramp, escrow và withdrawal; chúng chưa tự ghép các endpoint thành một payment flow.
- Marketplace: [`SolanaEscrowFundingService.java`](../../marketplace-backend/src/main/java/com/marketplace/backend/service/SolanaEscrowFundingService.java) và [`SolanaEscrowTimeoutService.java`](../../marketplace-backend/src/main/java/com/marketplace/backend/service/SolanaEscrowTimeoutService.java) xác minh vault/release/refund local; [`SettlementDownstreamService.java`](../../marketplace-backend/src/main/java/com/marketplace/backend/service/SettlementDownstreamService.java) chạy on-ramp/Invoice/off-ramp **sau** primary Payment release của P06. Không dùng thứ tự P06 cho flow mới.
- Program escrow lưu review timeout theo giây. Grace 24 giờ cho amount trên 500 USDC chỉ áp khi Marketplace gửi `high_value_review_grace=true` (rail `SOLANA_ESCROW` cũ); Job unified gửi `false` để giữ đúng 72 giờ. Escrow đã fund trước thay đổi giữ giá trị đã lưu trên account. Đối tác mock dùng ngày làm việc. [Checklist phase 0](PAYMENT_FLOW_REBUILD_CHECKLIST.md#0-khóa-hợp-đồng-nghiệp-vụ-và-đường-chuyển-đổi) yêu cầu chốt một chính sách trước khi đồng bộ server và chain.
- Ví/signer demo local chưa phải cấp phát, bảo vệ và khôi phục khóa production cho tài khoản mới. User action cần chữ ký đúng quyền; secrets/backend signing không được đưa vào frontend.

## Điều kiện gọi là đã tích hợp

Một **Job mới** phải có cùng `paymentFlowId` xuyên USD order, on-ramp receipt, escrow PDA, release signature, WithdrawalRecord và VND payout reference; số dư/statement khớp ở mọi ranh giới. Job khác chứng minh hoàn USDC rồi hoàn USD. Các ca duplicate, `UNKNOWN`, RPC/provider outage và restart không tạo tiền/lệnh hai lần. Chỉ sau [gate hoàn tất](PAYMENT_FLOW_REBUILD_CHECKLIST.md#gate-hoàn-tất) và bằng chứng trong [VERIFICATION](VERIFICATION.md) mới đổi trạng thái tài liệu thành “đã triển khai”; local mock không chứng minh devnet hay tiền thật.
