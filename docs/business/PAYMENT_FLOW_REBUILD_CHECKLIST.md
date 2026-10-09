# Checklist xây lại một luồng thanh toán cho Job

**Mục tiêu:** một Job đi theo `USD Client → USDC Client → escrow Milestone → USDC Freelancer → VND Freelancer`. On-ramp, escrow, release/refund và off-ramp là **các bước của cùng `paymentFlowId`**. Bản đặc tả nghiệp vụ ở [README](README.md); tiền, trạng thái và đối soát ở [luồng thanh toán](PAYMENT_FLOW.md); vận hành chuyển đổi ở [runbook](CUTOVER_RUNBOOK.md). Dấu `[x]` bên dưới chỉ xác nhận **mục riêng lẻ ở phạm vi local mock** đã có source, test và bằng chứng; không xác nhận cả Gate hoặc tiền thật.

**Cách đánh dấu:** chỉ đổi `[ ]` thành `[x]` khi có source, test và bằng chứng đúng tiêu chí ở ô đó; ghi test output hoặc biên bản vào [VERIFICATION](VERIFICATION.md). Không đánh dấu cả phase chỉ vì một API trả HTTP 200. Hoàn thành local mock trước; tích hợp đối tác thật/devnet/production là gate riêng.

## 0. Khóa hợp đồng nghiệp vụ và đường chuyển đổi

- [x] Chốt một flow cho **Job mới** với tên `UNIFIED_USDC_PAYOUT`; khi flag bật, Job mới chỉ đi rail này và ba API funding cũ từ chối Contract unified. P06 `SIMULATED`, `SOLANA_ESCROW`, `PARTNER_ESCROW_MOCK` chỉ còn cho Job cũ/test.
- [x] Chốt snapshot điều khoản trước funding: `grossUsd`, lượng USDC phải vào vault, **mint/network**, Client/Freelancer, đầu ra, hạn, review window, revision limit và phí 3% do Freelancer chịu. Mint/network được chụp khi đăng Job, nằm trong fingerprint hai bên xác nhận; USD order bị từ chối nếu config đã đổi mint.
- [x] Chốt nguồn quote USD/USDC (local 1:1, hạn 15 phút) và USDC/USD/VND (25.000, khóa theo withdrawal), làm tròn USD 2 chữ số, USDC 6 chữ số, VND `HALF_UP`; vault thiếu một đơn vị nhỏ nhất thì không mở việc.
- [x] Chốt cơ chế thu phí 3% **cho local mock**: toàn bộ USDC release về ví Freelancer, Freelancer ký withdrawal toàn bộ vào treasury FreelaX; đối tác mock chỉ đổi phần sau phí sang VND. Phí chỉ được ghi khi WithdrawalRecord on-chain (toàn bộ USDC vào treasury) **và** hai sao kê độc lập `VND_PAYOUT` + `PLATFORM_FEE` cùng khớp; thiếu một trong hai thì giữ `UNKNOWN`, không tạo lệnh thu lần hai. Provider thật cần cơ chế riêng (gate production).
- [x] Chốt cách hoàn **đủ gross USD** trước release, gồm bên chịu phí on-ramp/off-ramp và chênh lệch tỷ giá. Nếu chưa bảo đảm được, phải sửa điều khoản trước khi bật flow.
- [ ] Chốt một chính sách hạn funding, bàn giao/gia hạn, review, tự duyệt/nhắc, tranh chấp và Admin cho flow mới; ánh xạ cùng điều khoản sang server và chain. **Đã làm:** hạn funding 48 giờ lưu một lần trên `PaymentFlow` và dùng cho server lẫn chain; review cố định 72 giờ, không grace (`high_value_review_grace=false`); timer không hủy khi USD có thể đã vào; Admin hủy quá hạn có audit. **Còn lại:** chủ sản phẩm xác nhận quy tắc gia hạn bàn giao on-chain hiện có (một lần, ≤ 7 ngày, Client duyệt) là điều khoản của flow mới.
- [x] Chốt quy tắc Job `COMPLETED` khi công việc/release USDC xong, còn Finance `VND_PAID` chỉ khi đối tác xác nhận VND; tax/review không thay thế payout evidence.
- [x] Liệt kê Job đang `PENDING/UNKNOWN` trên từng rail cũ và quy tắc xử lý riêng: [runbook](CUTOVER_RUNBOOK.md#khoản-đang-dở-trên-db-local-đọc-ngày-2026-10-09-sau-e2e). Không migrate tiền/vault/withdrawal đang dở bằng cập nhật DB.

**Gate 0: chưa đạt** chỉ vì quy tắc gia hạn bàn giao chờ chủ sản phẩm xác nhận. Tài khoản mới đã thấy cùng điều khoản (gồm mint/network, review 72 giờ) và xác nhận cùng fingerprint trên browser.

## 1. Backend — mô hình dữ liệu, API và bảo toàn trạng thái

- [x] `PaymentFlow` duy nhất theo Milestone/Contract (unique `milestone_id`, `contract_id`), có version, trạng thái từng giai đoạn, amount/currency, provider, network/mint và timestamps.
- [x] Bản ghi bất biến `payment_flow_evidence` cho từng lệnh và bằng chứng, gắn `paymentFlowId`, `jobId`, `contractId`, `milestoneId`, idempotency key; quyết định Admin lưu thêm người thực hiện và ghi chú.
- [x] Phân biệt `PENDING`, `PROCESSING`, `UNKNOWN`, `CONFIRMED`, `FAILED` cho từng bước; khóa dòng (`findWithLock…`) và kiểm tra trạng thái chặn release/refund đối nghịch hoặc ghi lặp.
- [x] API đọc một timeline thanh toán tổng hợp cho participant/Admin; response giữ riêng work status, token status, fiat status, amount, currency, nguồn chứng cứ và `retryAfter`.
- [x] API hành động có quyền rõ ràng: Client mở/nộp USD order và ký escrow; người nhận ký withdrawal; participant hủy/tranh chấp qua escrow; Admin ghi quyết định đối soát và hủy quá hạn (`ROLE_ADMIN`). Amount, ví, mint và beneficiary lấy từ server.
- [x] Migration Flyway: `V1__baseline_schema.sql` (schema hiện có) và `V2__unified_terms_and_admin_audit.sql` (chỉ thêm cột nullable). DB hiện có được baseline ở V1; Hibernate chuyển sang `validate`. Không cần backfill metadata cho Job cũ vì chúng đọc qua API của rail gốc.
- [x] Test uniqueness, optimistic/pessimistic locking, owner/role, idempotency, stale quote, amount mismatch, duplicate callback và restart sau remote success/local commit failure. Gồm `PaymentFlowMySqlConcurrencyIT` trên MySQL 8.4 + Flyway: hai flow cùng Milestone (unique chặn), release/refund đồng thời (chỉ một `CONFIRMED`), bốn Admin hủy quá hạn cùng lúc (một lần, một audit); và test phân quyền `PaymentFlowControllerSecurityTest`.

**Gate 1: đạt ở local mock.**

## 2. Payment Backend và adapter đối tác — USD vào, VND/hoàn USD ra

- [x] Adapter tách biệt: `UnifiedUsdOrderMockService` (nhận USD) và `UnifiedFiatExitMockService` (chi VND/hoàn USD). `PartnerEscrowMockService` (USD → VND trực tiếp) chỉ còn cho Job cũ và từ chối Contract unified.
- [x] USD order có mã duy nhất, payer, gross USD, quote, thời hạn và sao kê mock độc lập; chỉ sao kê được xác minh mới chuyển `USD_RECEIVED`.
- [x] Sau `USD_RECEIVED`, on-ramp chuyển đúng Mock USDC từ treasury demo vào Client ATA, ghi receipt; receipt PDA theo purchase ID chặn cấp token hai lần.
- [x] Off-ramp chỉ nhận lệnh sau `USDC_RELEASED`; khóa quote và beneficiary, đối chiếu WithdrawalRecord/treasury rồi mới mô phỏng chi VND. Cùng idempotency key giữ nguyên số tiền và tỷ giá.
- [x] Ghi riêng gross, phí FreelaX, phần đổi cho Freelancer, VND payout và phần phí; chỉ quyết toán phí khi payout và phần phí đều được xác nhận.
- [x] Với hủy trước release, hoàn USDC về Client trước; sau đó đối tác mock hoàn USD theo điều khoản đã chốt. `USDC_REFUNDED` không được hiển thị là `USD_REFUNDED`.
- [x] API tra cứu độc lập cho USD order, sao kê USD, fiat exit và sao kê VND/phí/hoàn USD; test mô phỏng sao kê lệch, mất phản hồi **trước** (lệnh chưa tới) và **sau** khi đối tác đã chi (tra cứu cùng `paymentFlowId`, không chi lần hai).
- [x] Test tính tiền, làm tròn, quote hết hạn, beneficiary khác, payout/refund trùng và retry sau khi provider đã chi.

**Gate 2: đạt ở local mock** (test đơn vị và E2E, xem VERIFICATION).

## 3. Solana program và Gateway — token đi qua escrow

Đối chiếu với [kiến trúc Solana](SOLANA_ARCHITECTURE.md).

- [x] Rà soát/reuse `mock_onramp`, `milestone_escrow`, `request_offramp`, `publish_rate`; thêm `high_value_review_grace` vào `fund_milestone_escrow` để flow unified giữ đúng review 72 giờ.
- [x] Marketplace xác minh receipt on-ramp, Client ATA, mint và amount trước khi build funding; chỉ ghi `ESCROW_FUNDED` sau khi PDA, vault balance và điều khoản on-chain khớp. (Kiểm tra nằm ở Marketplace, Gateway chỉ build/đọc.)
- [x] Escrow lưu Client, Freelancer, mint, amount, hạn funding/delivery/review, dispute và terminal; PDA `init` theo Milestone chặn fund hai lần; signer sai bị program từ chối (Anchor test).
- [x] Nghiệm thu, timeout và Admin dispute chỉ tạo release/refund hợp lệ; dispute chặn timeout release (Anchor test và E2E escrow trước đó trên cùng program).
- [x] Release/refund chuyển đúng USDC vault → ATA người nhận. Ranh giới vault → người nhận được chứng minh bằng bất biến của program: `settle`/`refund_mutual` chuyển toàn bộ `amount` tới ATA của participant đã ghi **trong cùng instruction** ghi trạng thái terminal, nên terminal + vault 0 + participant khớp ⇒ người nhận đã được ghi có, kể cả khi lịch sử giao dịch bị cắt. Admin hiển thị `RECIPIENT_BY_ESCROW_INVARIANT`.
- [x] Off-ramp lấy chính USDC vừa release của flow; người nhận ký `request_offramp` chuyển sang treasury; WithdrawalRecord và amount khớp payment flow.
- [x] Kiểm thử Anchor/Gateway: mint/amount/signer sai, tx trùng, timeout, dispute, refund; RPC mất kết nối (drill qua proxy RPC: Admin `UNKNOWN`, withdrawal không gửi, gửi lại cùng giao dịch); validator dừng/khởi động lại làm rơi giao dịch on-ramp → tự gửi lại theo cùng purchase ID khi chưa có receipt; history mất nhưng account còn → bất biến escrow. Gateway test 7/7 (gồm mã hóa `high_value_review_grace`).

**Gate 3: đạt ở local mock.**

## 4. Marketplace — điều phối và đối soát toàn chuỗi

- [x] Khi flag bật, Job mới tạo flow và USD order trước; không thể chọn `SIMULATED` hoặc partner USD → VND.
- [x] Nối USD statement → on-ramp receipt → Client ATA → vault verification theo thứ tự; chỉ `ESCROW_FUNDED` mới mở `IN_PROGRESS` và quyền submit.
- [x] Nối approval/timeout/dispute với release/refund on-chain; Contract unified bị chặn khỏi release mô phỏng. Đã sửa lỗi duyệt qua UI đánh dấu escrow "reconciled" trước khi ghi `USDC_RELEASE`, và thêm lượt quét tự hồi phục.
- [x] Sau `USDC_RELEASED`, `UnifiedExitService` tạo withdrawal, chờ provider payout và chốt `VND_PAID`; không khởi tạo lại `mock_onramp` như P06. (`SettlementDownstreamService` giữ cho P06; không tái dùng vì thứ tự của nó ngược với flow mới.)
- [x] Reconciler cho 4 ranh giới, mỗi kết quả có mã, nguồn, thời điểm; **bước tiền tiếp theo bị chặn** (`PAYMENT_RECONCILIATION_BLOCKED`) khi ranh giới trước đó chưa `MATCHED`: ký escrow cần ranh giới 1, withdrawal và lệnh fiat cần ranh giới 1–3.
- [x] Scheduler chỉ chuyển trạng thái khi điều kiện server/chain đủ; `PENDING/UNKNOWN` không tự hủy hay chi; sau restart tiếp tục theo cùng reference (kiểm chứng: flow `f07293e8-…` tự ghi `USDC_RELEASE` sau restart).
- [x] Admin nhìn được nghĩa vụ và số dư theo **từng đồng tiền** (“Tiền đang ở đâu”: USD đã nhận/chờ on-ramp/phải hoàn/đã hoàn; USDC ở ví Client/vault/ví người nhận/treasury/phí; VND phải chi/đã chi; đếm `UNKNOWN`), case lệch theo 4 ranh giới và audit quyết định.
- [x] Test unit/integration cho chuyển tiếp, timeout, dispute, double-process, provider outage, chain outage, commit failure và recovery; E2E `local-unified-dispute-e2e.ts`, `local-unified-timeout-e2e.ts`, `local-unified-outage-e2e.ts`.

**Gate 4: đạt ở local mock.**

## 5. Frontend — một trải nghiệm thanh toán

- [x] Bỏ bộ chọn ba rail cho Job mới; Job cũ hiển thị đúng lịch sử theo rail cũ.
- [x] Trước funding, Client/Freelancer thấy cùng snapshot giá USD, USDC khóa, phí 3%, tỷ giá, hạn funding, review 72 giờ, số lần sửa, mint/network và mô tả hoàn tiền; có nhãn “mô phỏng”.
- [x] Client thấy lần lượt liên kết ví, ngân hàng, USD order, USDC ví Client, bước ký escrow, vault funded; màn hình làm việc chỉ mở sau vault.
- [x] Freelancer liên kết ví, thấy vault funded, bàn giao/duyệt, release USDC và off-ramp; UI phân biệt “đã duyệt”, “đã nhận USDC” và “đã nhận VND”.
- [x] Finance hiển thị từng amount/currency, quote, phí, reference/chữ ký, nguồn, `PENDING/UNKNOWN`, nút đối soát lại; lỗi thao tác hiển thị riêng, không bị lần tải lại xóa.
- [x] Admin reconciliation hiển thị 4 ranh giới, trạng thái chặn, lịch sử quyết định, form ghi quyết định và hủy quá hạn; dữ liệu giả lập có nhãn.
- [x] Test browser với **tài khoản mới** từ đăng ký đến payout (`unified-new-account-e2e.mjs`, release + refund, 1440/390 px) và ca biên (`unified-browser-edge-e2e.mjs`): từ chối ký ví không gửi giao dịch, ký lại funding đúng một lần; đối tác xác nhận chậm không mở việc; điều khoản và ứng tuyển dùng được hoàn toàn bằng bàn phím (focus 3px).

**Gate 5: đạt ở local mock.**

## 6. Cutover và tương thích Job cũ

- [x] Feature flag/phiên bản flow theo Job; không đổi ngầm `paymentRail` của Job đang chạy.
- [x] API/Finance/Admin đọc được Job P06, Solana-only và partner mock cũ; quyền xử lý/retry theo rail gốc.
- [x] Sau cutover, Job mới không thể vào ba đường funding cũ; lịch sử và công cụ xử lý Job cũ được giữ.
- [x] Hướng dẫn vận hành khoản cũ `PENDING/UNKNOWN`, rollback flag và điều kiện không được rollback: [runbook](CUTOVER_RUNBOOK.md).
- [x] `.env.example`, README và VERIFICATION mô tả adapter mock, validator, Flyway, cách chạy E2E API và browser; không đưa secrets vào Git.

**Gate 6: đạt ở local mock** — flag đã bật/tắt nhiều lần trong E2E, Job version 1 vẫn xử lý đúng khi flag tắt; danh sách khoản cũ và runbook có sẵn. **Đã chuyển đổi local ngày 2026-10-09** theo xác nhận chủ sản phẩm ([nhật ký](CUTOVER_RUNBOOK.md#nhật-ký-chuyển-đổi)); các mục `[ ]` còn lại là rủi ro đã biết.

## Gate hoàn tất

- [x] **Happy path:** tài khoản mới đăng ký hai phía → USD mock → USDC Client → vault → bàn giao → release → withdrawal → VND mock; cùng `paymentFlowId`, bốn ranh giới `MATCHED`.
- [x] **Refund:** Job khác hủy bằng hai chữ ký trước release → USDC về Client → treasury → USD mock hoàn đủ; phí 0; không có payout VND.
- [x] **Dispute/timeout:** Admin resolve về Freelancer (→ VND + phí) và về Client (→ hoàn USD) trên flow unified; tranh chấp chặn withdrawal. Hết hạn review 72 giờ: scheduler tự gửi release trên bản sao ledger warp, chain chốt sau hạn, Freelancer nhận đủ.
- [x] **Failure/recovery:** Payment Backend sập lúc nộp USD và lúc chi fiat, RPC mất lúc đối soát và lúc gửi withdrawal, giao dịch on-ramp bị rơi khi validator dừng, restart Marketplace: hồi phục cùng reference, một sao kê, treasury tăng đúng một lần.
- [x] **Security:** rà soát endpoint mới (participant/Client/người nhận/Admin, chữ ký do server build và Gateway so hash, beneficiary không lộ ra API); ẩn tài khoản ngân hàng Client khỏi Freelancer; test 401/403/danh tính.
- [x] **Reconciliation:** trên stack chạy thật Admin thấy `MATCHED`, `MISMATCH` (flow timeout chỉ tồn tại trên bản sao ledger: `TERMINAL_CHAIN_MISMATCH`, withdrawal bị chặn, không thể “xóa” khi chưa có bằng chứng) và `UNKNOWN` (RPC mất); quyết định Admin có audit.
- [ ] **Docs/evidence:** VERIFICATION đã cập nhật; README chỉ đổi thành “đã triển khai” sau khi chủ sản phẩm xác nhận quy tắc gia hạn (Gate 0).

**Local mock PASS** không đồng nghĩa đã vận hành với USD/USDC/VND thật. Devnet, provider thật, quản lý khóa và vận hành production có gate riêng.
