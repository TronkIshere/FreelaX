# FreelaX — nghiệp vụ hệ thống

**Tài liệu chính thức cho luồng sản phẩm.** FreelaX kết nối Client và Freelancer qua Job, hợp đồng, bàn giao, nghiệm thu và thanh toán có bằng chứng. Luồng thanh toán thống nhất cần đạt là **USD Client → USDC ví Client → escrow của Job → USDC ví Freelancer → VND tài khoản Freelancer**. Escrow, on-ramp và off-ramp là các **giai đoạn của một luồng**, không phải ba lựa chọn thanh toán cho người dùng.

**Trạng thái hiện tại (2026-10-10): luồng `UNIFIED_USDC_PAYOUT` đã triển khai ở phạm vi local mock** — Gate 0–6 và [gate hoàn tất](PAYMENT_FLOW_REBUILD_CHECKLIST.md#gate-hoàn-tất) đạt với USD/VND mô phỏng, Mock USDC và Solana local validator; môi trường local đã chuyển đổi ([nhật ký](CUTOVER_RUNBOOK.md#nhật-ký-chuyển-đổi)), Job mới chỉ đi luồng này, Job cũ giữ rail gốc. **Chưa** có tiền thật, đối tác thật hay devnet: đó là các gate riêng, chưa bắt đầu. Bằng chứng ở [VERIFICATION](VERIFICATION.md); điều khoản ở [điều khoản local](UNIFIED_FLOW_LOCAL_TERMS.md).

## Đọc lần lượt

1. Đọc [hệ thống đáp ứng những gì](#hệ-thống-đáp-ứng-những-gì) và [vai trò và luồng công việc](#vai-trò-và-luồng-công-việc) để hiểu sản phẩm.
2. Đọc [luồng tiền và đối soát](PAYMENT_FLOW.md) để biết tiền ở đâu, ai xác nhận và trạng thái nào mở bước tiếp theo.
3. Đọc [Solana trong kiến trúc](SOLANA_ARCHITECTURE.md) để hiểu on-ramp, vault, release và off-ramp liên kết với Marketplace/đối tác ra sao.
4. Đọc [hiện trạng và giới hạn](#hiện-trạng-và-giới-hạn), rồi [bằng chứng E2E](VERIFICATION.md) để phân biệt code có sẵn với luồng đã hoàn tất.
5. Khi xây lại thanh toán, làm theo [checklist theo thứ tự phụ thuộc](PAYMENT_FLOW_REBUILD_CHECKLIST.md); khi bật/tắt flag, theo [runbook chuyển đổi](CUTOVER_RUNBOOK.md).
6. Để chạy **demo cũ hiện có**, dùng [README gốc](../../README.md#chạy-demo-local-theo-từng-bước) và [guide hai phía trong archive](archive/LOCAL_TWO_SIDED_E2E_GUIDE.md). Guide này chưa chứng minh luồng thống nhất.

Chi tiết theo mốc ngày, ID giao dịch và quyết định cũ được giữ trong [archive](archive/README.md). Khi thông tin xung đột, tài liệu này xác định **nghiệp vụ đích**, source/API trên nhánh hiện tại xác định **hành vi đã có**, và biên bản E2E xác định **điều đã kiểm chứng**.

## Hệ thống đáp ứng những gì

| Nhóm chức năng | Hiện có trên local/demo | Mục tiêu sau khi làm lại thanh toán |
| --- | --- | --- |
| Tài khoản và vai trò | Đăng ký/đăng nhập Client hoặc Freelancer; profile, kỹ năng, portfolio, public profile; Admin được cấp quyền riêng. | Duy trì quyền theo role, ownership và trạng thái server; liên kết ví và tài khoản nhận tiền cho người dùng thật với quy trình cấp phát/khôi phục phù hợp. |
| Tìm việc và hợp đồng | Client tạo Job có ngân sách, đầu ra, tiêu chí, hạn, review window, revision limit; Freelancer tìm và ứng tuyển; Client chọn Freelancer, tạo WorkContract + một Milestone. | Chốt snapshot điều khoản và giá/quote trước khi Client nộp USD; mọi bước thanh toán gắn cùng Contract/Milestone. Nhiều Milestone là phạm vi sau MVP. |
| Funding | P06 ledger mô phỏng, vault token Solana local và đối tác USD mock là các nhánh tách biệt. | Đối tác xác nhận USD, USDC vào ví Client, rồi USDC vào vault được đối soát; chỉ sau đó Job mới `IN_PROGRESS`. |
| Làm việc và nghiệm thu | Freelancer bàn giao có evidence/version; Client duyệt, yêu cầu sửa hoặc mở tranh chấp; scheduler xử lý hạn theo rail hiện có. | Giữ cùng quy tắc đã công bố cho hai bên; quyết định nghiệm thu chỉ tạo quyền release, còn chuyển tiền cần bằng chứng riêng. |
| Hủy và tranh chấp | Có hủy/hoàn và Admin xử lý release/refund toàn phần theo điều kiện của từng nhánh demo. | Đóng băng release khi tranh chấp; giải quyết toàn phần; hoàn USDC từ vault và, nếu hợp đồng cam kết hoàn USD, đối soát thêm khoản USD về Client. |
| Chi trả | P06 downstream có on-ramp/off-ramp mô phỏng; Solana escrow release token; đối tác mock chi VND trực tiếp, bỏ qua USDC. | Sau release USDC sang Freelancer, off-ramp chuyển USDC theo lệnh rút và đối tác xác nhận VND đến tài khoản; ghi phí FreelaX 3% đúng một lần khi payout thành công. |
| Tài chính và hậu xử lý | Finance hiển thị bản ghi tiền; tax/MISA demo, Activity, review hai chiều và reputation có dữ liệu server. | Hiển thị riêng nghiệm thu công việc, USDC đã release, VND đã chi, phí, tỷ giá, sao kê và chứng từ; không suy bước sau thành công từ bước trước. |
| Quản trị và đối soát | Có màn hình Admin đối soát sao kê đối tác mock, dispute và các tác vụ scheduler. | Đối soát USD, USDC, vault, withdrawal và VND theo cùng payment flow; phát hiện khoản lệch, giữ trạng thái `UNKNOWN` và chặn lệnh xung đột. |

Các chức năng local/demo không chứng minh đã nhận USD ngân hàng thật, đã chuyển VND thật, đã hoạt động trên devnet/production hoặc đã chạy đầy đủ luồng thống nhất.

**Chi tiết các bề mặt đang có:** Client có Job editor, danh sách ứng viên, Work/Contract, funding, review submission, cancellation, dispute, Finance, Tax, Activity, Account/Profile và đánh giá Freelancer. Freelancer có Explore với lọc từ server, Applications, My Work, bàn giao có bằng chứng và phiên bản, revision, Finance/Income, Tax, Activity, Profile/Portfolio và đánh giá Client. Admin được cấp quyền riêng để claim/resolve dispute, moderation và xem đối soát; đăng ký thường không tự có quyền Admin. Reputation lấy review đã công bố từ server, không tính điểm giả ở UI. Chứng từ tax PDF/XML chỉ là bằng chứng của record tương ứng, không chứng minh mọi Job đã hoàn tất payout.

**Giới hạn sản phẩm hiện tại:** một Job có một Milestone; chưa có chat để tự gom tin nhắn làm bằng chứng, chưa có lịch ngày lễ cho đồng hồ ngày làm việc, chưa có nhiều giai đoạn thanh toán hoặc cấp phát ví/signer production cho tài khoản mới. UI mobile chưa được tối ưu đầy đủ. Các giới hạn này độc lập với checklist nối luồng thanh toán.

## Vai trò và luồng công việc

- **Client:** công bố Job và điều khoản; chọn Freelancer; nộp USD; xác nhận/ký funding USDC vào escrow; xem bằng chứng vault; duyệt, yêu cầu sửa, tranh chấp hoặc đồng ý hủy; nhận hoàn khi đủ điều kiện.
- **Freelancer:** đọc điều khoản và ứng tuyển; chỉ làm sau khi escrow xác nhận; bàn giao theo đầu ra/tiêu chí; xử lý revision; nhận USDC sau release; theo dõi off-ramp và VND; đánh giá Client khi đủ điều kiện.
- **FreelaX/Marketplace:** lưu hợp đồng và một `paymentFlowId` xuyên suốt; thực thi quyền, hạn, state machine, lệnh idempotent và đối soát; không tự nhận là đơn vị giữ USD/VND chỉ từ bản ghi nội bộ.
- **Đối tác on-ramp/off-ramp:** xác nhận USD vào, chuyển USDC, nhận USDC để đổi, chi VND và cung cấp sao kê/xác nhận độc lập. Local mock chỉ mô phỏng các hành động này.
- **Admin/scheduler:** xử lý timeout và tranh chấp theo bằng chứng, theo dõi khoản lệch; không tự gán `PAID_VND` khi đối tác chưa xác nhận.

Một Job MVP tạo một WorkContract và một Milestone. Luồng công việc và tiền đi cùng nhau:

```text
Đăng Job → ứng tuyển → chọn Freelancer/chốt điều khoản
→ USD confirmed → USDC Client confirmed → escrow funded/verified
→ Freelancer làm → bàn giao → duyệt/sửa/tranh chấp
→ release USDC hoặc refund USDC được xác minh
→ nếu release: off-ramp và VND payout confirmed
→ review hai chiều, chứng từ và reputation theo điều kiện riêng
```

**Mốc tách biệt:** `WORK_ACCEPTED` là quyết định về sản phẩm bàn giao; `USDC_RELEASED` là token đã vào ví Freelancer; `VND_PAID` là đối tác xác nhận chi VND. Job/Contract có thể hoàn tất phần công việc trước khi payout VND xong, nhưng Finance vẫn phải hiển thị khoản chi đang chờ. Review không phải điều kiện để release hoặc payout. Hạn và revision phải do server/điều khoản quyết định; UI không tự approve hay chuyển tiền bằng timer.

**Chính sách thời gian cần thống nhất trước cutover:** P06 từng dùng review window theo giờ và grace cho Job trên 500 USD; nhánh đối tác mock dùng 3 ngày làm việc, 2 lần nhắc, 3 ngày thương lượng và mốc 5 ngày cho Admin; nhánh Solana có hạn on-chain theo giây. Luồng mới phải chốt một bộ điều khoản hiển thị cho hai bên và ánh xạ chính xác sang đồng hồ server/chain. Không áp cả ba chính sách vào cùng Job hoặc coi một chính sách cũ là đã tự chuyển sang flow mới.

## Ranh giới hệ thống

`Browser → Frontend → /api/v1 → Marketplace → Payment/đối tác, Solana Gateway/Anchor, MISA`

Frontend chỉ gọi Marketplace. Marketplace là authority cho identity, quyền và nghiệp vụ Job; Solana/validator là bằng chứng chuyển token; đối tác và sao kê là bằng chứng fiat; MISA là chứng từ thuế demo. Mỗi bước phải có transaction/reference, đơn vị tiền, người gửi/nhận, thời điểm và nguồn xác nhận. Không cộng USD, USDC và VND hoặc các ledger demo thành một số dư. Chi tiết trạng thái và phép đối soát ở [luồng tiền](PAYMENT_FLOW.md); vai trò program/Gateway ở [kiến trúc Solana](SOLANA_ARCHITECTURE.md).

## Hiện trạng và giới hạn

- `SIMULATED` là mặc định UI/API cũ: Payment Backend chỉ ghi ledger mô phỏng. Không chứng minh funding bằng USDC hay USD thật.
- `SOLANA_ESCROW` đã có vault token local, funding/release/refund và các ca E2E riêng; chưa nối on-ramp USD trước funding hoặc off-ramp VND sau release trên cùng Job.
- `PARTNER_ESCROW_MOCK` có nhận USD, phí 3%, chi/hoàn và sao kê mock; nó đi thẳng USD → VND, không tạo/chuyển USDC.
- P06 downstream có `mock_onramp`, Invoice và off-ramp mô phỏng, nhưng chạy sau primary release mô phỏng; không phải funding escrow trước khi làm.
- Quy tắc phí và hạn ở các nhánh cũ chưa tự động thành quy tắc của luồng mới. [Checklist](PAYMENT_FLOW_REBUILD_CHECKLIST.md) yêu cầu khóa điều khoản/quote và kiểm thử trước khi đưa luồng mới vào UI.

Trong quá trình chuyển đổi, Job cũ vẫn được đọc/đối soát theo rail đã ghi; **không tự chuyển các khoản đang chờ sang flow mới**. Job mới sau cutover chỉ đi một luồng USD → USDC → escrow → USDC → VND. Không gọi luồng đó là đã hoàn tất trước khi [gate thống nhất](PAYMENT_FLOW_REBUILD_CHECKLIST.md#gate-hoàn-tất) qua.

## Mã nguồn theo nghiệp vụ

| Nghiệp vụ | Điểm bắt đầu trong repo |
| --- | --- |
| Job, ứng tuyển, giao việc | `frontend/src/Workflow.tsx`; `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/JobServiceImpl.java` |
| Funding hiện có | `frontend/src/Funding.tsx`, `EscrowFunding.tsx`, `PartnerFunding.tsx`; `marketplace-backend/src/main/java/com/marketplace/backend/service/FundingService.java`, `SolanaEscrowFundingService.java`, `PartnerEscrowFundingService.java` |
| Bàn giao, review, tranh chấp | `frontend/src/ContractLifecycle.tsx`, `ContractDispute.tsx`; `marketplace-backend/src/main/java/com/marketplace/backend/service/ContractSubmissionService.java`, `ContractDisputeService.java` |
| Settlement và downstream | `marketplace-backend/src/main/java/com/marketplace/backend/service/SettlementService.java`, `SettlementDownstreamService.java`, `ContractCancellationService.java`; `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/OnChainOffRampServiceImpl.java`, `VndPayoutServiceImpl.java` |
| Đối tác mock và đối soát | `payment-backend/src/main/java/com/payment/backend/service/PartnerEscrowMockService.java`; `marketplace-backend/src/main/java/com/marketplace/backend/service/PartnerReconciliationService.java` |
| Token vault và Gateway | `solana-stablecoin-payout/programs/invoice_payments/src/instructions/milestone_escrow.rs`; `solana-integration/src/main/java/com/freelax/solanagateway/` |
| Finance và review | `frontend/src/Finance.tsx`, `ContractReviews.tsx`; `marketplace-backend/src/main/java/com/marketplace/backend/service/ContractReviewService.java` |

Chỉ mục source P06 chi tiết ở [archive](archive/FREELAX_SOURCE_PATH_INDEX_20261008.md); mốc UI P06 ở [final handoff](../ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md). Hai tài liệu đó là bằng chứng lịch sử, không thay thế trạng thái flow thống nhất.
