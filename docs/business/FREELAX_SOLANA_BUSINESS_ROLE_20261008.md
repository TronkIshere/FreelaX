# FreelaX — Solana đã được dùng để giải quyết bài toán nghiệp vụ gì?

**Mục tiêu tài liệu:** giải thích vai trò của Solana theo góc nhìn sản phẩm/nghiệp vụ, hạn chế đi sâu vào code.

**Trạng thái:** phần 1–9 dưới đây chủ yếu mô tả kiến trúc và giới hạn **baseline P06.7/P06.8**. Track escrow sau P06 có implementation riêng trên `feat/solana-milestone-escrow`; một số ca local-validator E2E đã qua, toàn bộ gate và devnet chưa xong. Xem [kế hoạch](SOLANA_ESCROW_IMPLEMENTATION_PLAN.md) và [kết quả E2E](SOLANA_ESCROW_LOCAL_E2E_20261008.md).

## Bản đồ vai trò Solana và luồng đối tác USD→VND

| Luồng | Vai trò Solana | Quyết định tiền cuối cùng |
| --- | --- | --- |
| P06 simulation | Bằng chứng/downstream stablecoin được theo dõi riêng sau primary settlement mô phỏng; không có vault Job ở baseline | Payment Backend xác nhận ledger mô phỏng. |
| `SOLANA_ESCROW` trên nhánh hiện tại | Vault PDA giữ mock token của **một Milestone/Job**, ghi submission, review, revision, dispute và chuyển token release/refund | Trạng thái vault và giao dịch chuyển token đã xác minh. Đây không phải USD ngân hàng hoặc payout VND. |
| Đối tác giữ USD → chi VND, mục tiêu MVP | **Không tham gia khoản tiền của luồng này**; không được tính cùng một khoản ở vault Solana và sổ đối tác | Sao kê/xác nhận của đối tác mock; khi có đối tác thật phải tích hợp và đối soát riêng. |

Quy tắc phí **FreelaX 3% chỉ khi giải ngân thành công** thuộc luồng đối tác USD→VND đã chốt về nghiệp vụ, **chưa có code**. Instruction release của rail Solana hiện chuyển toàn bộ token cho Freelancer và không thực hiện phép trừ 3%. Xem [luồng đối tác](PARTNER_ESCROW_BUSINESS_GAP_20261008.md).

## 1. Vấn đề nghiệp vụ trước khi có lớp Solana

Một marketplace xuyên biên giới không chỉ cần biết “Client đã bấm thanh toán”. Hai phía cần trả lời được các câu hỏi:

- Khoản tiền nào thuộc đúng Job/Contract/Milestone nào?
- Một trạng thái payout là bằng chứng nội bộ hay có dấu vết giao dịch độc lập?
- Nếu hệ thống downstream lỗi, có thể đối soát lại mà không làm mất trạng thái business hay không?
- Làm sao tách rõ “Client đã approve”, “Marketplace đã release”, “đã có on-chain evidence”, “đã off-ramp” và “đã có chứng từ thuế”?

Trong baseline P06, FreelaX dùng Solana như **một lớp bằng chứng/reconciliation cho rail stablecoin**. Trên nhánh `SOLANA_ESCROW`, vault Solana còn giữ và chuyển mock token theo quy tắc escrow; Marketplace vẫn quản lý Job, điều khoản và bằng chứng nghiệm thu.

## 2. Vai trò nghiệp vụ của Solana trong FreelaX

### 2.1 Tạo dấu vết thanh toán có thể đối soát

Solana giúp hệ thống có một lớp bằng chứng bên ngoài database Marketplace cho các bước liên quan stablecoin/on-chain.

Giá trị nghiệp vụ:

- giảm phụ thuộc vào một dòng trạng thái nội bộ duy nhất;
- có transaction/reference để phục vụ đối soát;
- hỗ trợ điều tra khi một service nói đã xử lý nhưng service khác chưa đồng bộ;
- tạo nền tảng cho việc kiểm tra amount/network/reference thay vì chỉ tin một cờ boolean.

Khi giao dịch đã được xác nhận trên mạng, transaction reference cho phép kiểm tra độc lập với sổ cái Marketplace; bằng chứng đó khó bị âm thầm sửa lại chỉ bằng một thay đổi database nội bộ. Đây là giá trị của lớp bằng chứng, không phải tuyên bố rằng mọi record MVP hiện đã có giao dịch được xác nhận.

### 2.2 Phân tách rõ các giai đoạn tài chính

FreelaX không coi “payment” là một trạng thái duy nhất.

```text
Funding
   ↓
Primary release / settlement
   ↓
On-chain evidence (Solana)
   ↓
Off-ramp
   ↓
Tax record / certificate
```

Solana nằm ở **một giai đoạn riêng**. Vì vậy nếu on-chain chưa reconcile, hệ thống vẫn có thể nói chính xác:

- primary settlement đã thành công hay chưa;
- on-chain đang pending/unknown;
- off-ramp đã bắt đầu hay chưa;
- tax record đã tồn tại hay chưa.

Điều này tránh tình trạng UI báo “đã thanh toán xong” trong khi thực tế chỉ mới hoàn thành một bước.

### 2.3 Hỗ trợ stablecoin payout xuyên biên giới

Trong kiến trúc FreelaX, Solana/USDC là hướng rail giúp chuyển giá trị số trước khi đi tới off-ramp fiat.

Business value hướng tới:

- dùng stablecoin làm đơn vị chuyển giá trị có thể lập trình/đối soát;
- tách transfer on-chain khỏi bước đổi ra tiền ngân hàng;
- giữ bằng chứng riêng cho on-chain và off-ramp;
- dễ retry/reconcile từng chặng thay vì rollback cả hợp đồng.

### 2.4 Không để lỗi blockchain phá hủy business truth ở baseline P06

Một quyết định quan trọng của FreelaX là:

> **Ở rail P06 simulation**, Payment/primary settlement success là mốc tiền mô phỏng chính; lỗi Solana/MISA downstream không được biến một release đã xác nhận thành thất bại nghiệp vụ.

Với `SOLANA_ESCROW`, kết luận tiền đảo chiều: Marketplace **chưa** được đánh dấu release/refund thành công cho đến khi chuyển token từ vault được xác minh. Với luồng đối tác USD→VND mục tiêu, cần xác nhận chi/hoàn từ đối tác trước khi đánh dấu `PAID`/`REFUNDED`.

Điều này giải quyết bài toán reliability:

- contract/job không bị lật ngược chỉ vì RPC tạm thời lỗi;
- worker có thể reconcile/retry riêng;
- Finance hiển thị trạng thái thật của từng chặng;
- người dùng biết chính xác cái gì đã xong và cái gì chưa.

## 3. Solana KHÔNG giải quyết những gì

Với **baseline P06**, Solana không phải business authority cho các việc sau:

- không quyết định Client/Freelancer nào được assign;
- không quyết định submission đạt hay không;
- không quyết định revision/dispute;
- không đánh giá chất lượng Freelancer;
- không tạo review/reputation;
- không tự động chứng minh tiền đã vào tài khoản ngân hàng;
- không phải escrow pháp lý;
- không thay thế Marketplace authorization;
- không làm Job completed chỉ vì có một transaction on-chain.

Các quyết định về chất lượng bàn giao, ai được giao việc và review vẫn thuộc Marketplace business rules. Rail escrow hiện tại có thêm điều kiện on-chain cho submission, revision, dispute và chuyển token; câu “Solana không quyết định revision/dispute” ở danh sách trên chỉ mô tả P06, không áp dụng nguyên xi cho track escrow. Solana cũng không chứng minh đối tác đã nhận USD hoặc Freelancer đã nhận VND ngân hàng.

## 4. Luồng business có Solana trong baseline P06

```text
Client funding Milestone
        |
        v
Marketplace xác nhận Funding
        |
        v
Freelancer làm việc và submit
        |
        v
Client approve
        |
        v
Primary Settlement / Release
        |
        +--> moneyStatus SUCCEEDED
        |        |
        |        +--> Milestone RELEASED
        |        +--> Contract/Job COMPLETED
        |
        v
Solana / on-chain reconciliation
        |
        +--> SUCCEEDED: lưu bằng chứng on-chain
        |        |
        |        v
        |     Off-ramp
        |        |
        |        v
        |   Tax / certificate
        |
        `--> PROCESSING/UNKNOWN: giữ trạng thái thật, retry/reconcile;
                                off-ramp chưa chạy khi on-chain chưa SUCCEEDED
```

Đây là sơ đồ business của contract-backed downstream: off-ramp chỉ chạy sau on-chain `SUCCEEDED`; tax chỉ chạy sau off-ramp `SUCCEEDED`. Các nhãn trạng thái có bằng chứng từ source/API, không tự chuyển bước theo thời gian trên UI.

Điểm quan trọng: **review/reputation có thể mở sau primary completion theo business contract hiện tại; không cần giả vờ Solana/off-ramp/tax đã xong.**

## 5. Trạng thái đã chứng minh ở P06.7

P06.7 đã chứng minh thật:

- Funding: `SUCCEEDED`
- Submission: `APPROVED`
- Primary settlement: `SUCCEEDED`
- Milestone: `RELEASED`
- Contract/Job: `COMPLETED`
- Review hai chiều: được scheduler tạo và publish
- Reputation: cập nhật từ server truth

Nhưng final downstream state vẫn được ghi đúng là:

- Solana/on-chain: **NOT FULLY RECONCILED**
- `UNKNOWN / ON_RAMP_AWAITING_RECONCILIATION`
- off-ramp: **NOT_STARTED**
- tax: **NOT_STARTED**

Do local Solana RPC không sẵn sàng trong final regression, FreelaX **không tuyên bố** full on-chain E2E đã hoàn tất.

## 6. Giá trị nghiệp vụ thực tế của cách thiết kế này

### Minh bạch

Client/Freelancer nhìn thấy từng giai đoạn riêng thay vì một nhãn “Paid”.

### Đối soát được

Mỗi layer có reference/status riêng, giúp điều tra sai lệch giữa Marketplace, Payment, Solana, off-ramp và Tax.

### Không double-process

Các flow quan trọng dùng idempotency/reconciliation để hạn chế gửi lại một nghiệp vụ tài chính khi kết quả trước chưa chắc chắn.

### Chịu lỗi tốt hơn

Một dependency lỗi không bắt buộc rollback toàn bộ Job lifecycle.

### Phù hợp mở rộng xuyên biên giới

Stablecoin/on-chain có thể trở thành rail chuyển giá trị; off-ramp và tax tiếp tục là adapter downstream theo từng thị trường.

## 7. Cách nói ngắn khi thuyết trình

> FreelaX có rail escrow Solana giữ mock token cho từng Milestone, với bằng chứng funding, release và refund on-chain. Đây là luồng token riêng, chưa phải tài khoản USD của đối tác hay chuyển khoản VND. Luồng đối tác USD→VND cho MVP sẽ được mô phỏng và đối soát bằng sao kê riêng; phí FreelaX 3% chỉ phát sinh khi giải ngân thành công. Các màn hình phải nói đúng tiền đang nằm ở rail nào và bước nào đã được xác nhận.

## 8. Source paths liên quan

- `solana-stablecoin-payout/`
- `solana-integration/`
- `solana-stablecoin-payout/docs/bao-cao-tich-hop-freelax-solana.md`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/SettlementService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/scheduler/SettlementScheduler.java`
- `frontend/src/Finance.tsx`
- `docs/mvp-functional-spec.md`
- `docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md`

## 9. Lưu ý khi đọc tài liệu Solana cũ

`solana-stablecoin-payout/docs/bao-cao-tich-hop-freelax-solana.md` chứa cả đánh giá lịch sử và kiến trúc đích ở thời điểm trước. Khi nội dung cũ mâu thuẫn với hiện trạng hoặc quy tắc đã chốt, đọc theo phạm vi:

1. Source trên nhánh hiện tại cùng [báo cáo E2E local](SOLANA_ESCROW_LOCAL_E2E_20261008.md) quyết định điều **đã triển khai/kiểm chứng**.
2. [Luồng đối tác](PARTNER_ESCROW_BUSINESS_GAP_20261008.md) quyết định nghiệp vụ **đã chốt nhưng chưa triển khai** cho phí 3% và USD→VND.
3. `docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md` và `docs/mvp-functional-spec.md` là bằng chứng/thiết kế **lịch sử P06**, không ghi đè track sau P06.

## 10. Skill Verification / Solana Attestation — tách khỏi hiện tại

Skill Verification / Solana Attestation là một hướng riêng trong tương lai: **CONCEPT_ONLY**, **PROPOSED_NOT_APPROVED**, **IMPLEMENTATION NOT_STARTED**. Chưa có SAS integration hay bằng chứng xác minh kỹ năng được triển khai trong freeze này. Solana hiện phục vụ các sự kiện tài chính/stablecoin và đối soát; không dùng giao dịch thanh toán để suy ra kỹ năng hoặc reputation.
