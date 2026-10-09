# Kiểm chứng luồng thanh toán thống nhất

**Kết luận (2026-10-10): ĐÃ TRIỂN KHAI Ở LOCAL MOCK.** Gate 0–6 và Gate hoàn tất trong [checklist](PAYMENT_FLOW_REBUILD_CHECKLIST.md) đều đạt. Phạm vi: Compose local, Solana local validator, Mock USDC, đối tác USD/VND và MISA mô phỏng. Không chứng minh tiền thật, đối tác thật hay devnet.

## Bằng chứng theo nhóm

| Nhóm | Đã kiểm chứng | Lệnh / nguồn |
| --- | --- | --- |
| Điều khoản (Gate 0) | Hai bên thấy cùng điều khoản (fingerprint gồm mint/network, review 72 giờ, quy tắc gia hạn); tài khoản mới xác nhận trên browser | `frontend/scripts/unified-new-account-e2e.mjs` |
| Dữ liệu và đồng thời (Gate 1) | Một flow/Milestone; release và refund không thể cùng xác nhận; hủy quá hạn đúng một lần dưới 4 Admin song song | `PaymentFlowMySqlConcurrencyIT` trên MySQL 8.4 + Flyway |
| Đối tác mô phỏng (Gate 2) | Sao kê USD/VND/phí/hoàn USD độc lập; retry sau khi đối tác đã chi không chi lần hai; làm tròn | `payment-backend` test 71/71 |
| Solana (Gate 3) | Ký quỹ, bàn giao, gia hạn, tranh chấp, release/refund chốt một lần; cờ grace 72 giờ; mã hóa instruction | Anchor 75/75, Gateway 7/7 |
| Điều phối và đối soát (Gate 4) | 4 ranh giới `MATCHED` trên stack thật; `MISMATCH` và `UNKNOWN` chặn bước tiếp theo; bảng theo loại tiền | `scripts/local-unified-flow-e2e.ts`, `local-unified-outage-e2e.ts` |
| Giao diện (Gate 5) | Tài khoản mới từ đăng ký đến VND và hoàn USD, dùng ví demo tự động (không cần tiện ích ví); đối tác chậm; bàn phím | `unified-new-account-e2e.mjs`, `unified-browser-edge-e2e.mjs` |
| Chuyển đổi (Gate 6) | Flag theo Job; Job cũ giữ rail; runbook và rollback | [CUTOVER_RUNBOOK](CUTOVER_RUNBOOK.md) |
| Tranh chấp và hết hạn | Admin về Freelancer (→ VND) và về Client (→ hoàn USD); scheduler tự release sau 72 giờ trên bản sao ledger warp | `local-unified-dispute-e2e.ts`, `local-unified-timeout-e2e.ts` |
| Sự cố | Payment Backend sập lúc nộp USD và lúc chi; RPC mất; on-ramp bị rơi rồi gửi lại; không chi trùng | `local-unified-outage-e2e.ts` |
| Chứng từ thuế | Scheduler phát hành chứng từ sau `VND_PAYOUT`; 7 chứng từ `ACCEPTED`; PDF và XML tải được. Từ `terms-v2`: thu nhập = (giá − phí 3%) × tỷ giá, thuế 10% trừ thẳng vào khoản chi (12 USD → thu nhập 291.000, thuế 29.100, thực nhận 261.900 VND) | PR #7, `TaxCertificateContractTest`, `unified-new-account-e2e.mjs` |
| Bảo mật | Endpoint Admin trả 401/403 cho người không có quyền; danh tính lấy từ token; Freelancer không thấy ngân hàng của Client | `PaymentFlowControllerSecurityTest` |

Kết quả test gần nhất (2026-10-10): Marketplace 333/333, Payment Backend 72/72, Frontend 806/806 + tsc + build, Gateway 7/7, Anchor 75/75, MySQL IT 3/3.

## Chạy lại kiểm chứng

```bash
# Từ solana-stablecoin-payout (validator ở 127.0.0.1:9123, stack Compose đang chạy, flag cutover = true)
export ANCHOR_PROVIDER_URL=http://127.0.0.1:9123 ANCHOR_WALLET="$HOME/.config/solana/id.json"
./node_modules/.bin/ts-mocha -p tsconfig.json -t 400000 scripts/local-unified-flow-e2e.ts --prepare   # tạo Job QA
./node_modules/.bin/ts-mocha -p tsconfig.json -t 400000 scripts/local-unified-flow-e2e.ts             # release + refund
./node_modules/.bin/ts-mocha -p tsconfig.json -t 600000 scripts/local-unified-dispute-e2e.ts
./node_modules/.bin/ts-mocha -p tsconfig.json -t 1500000 scripts/local-unified-outage-e2e.ts           # cần Gateway trỏ proxy :9133

# Từ frontend
node scripts/unified-new-account-e2e.mjs
node scripts/unified-browser-edge-e2e.mjs
```

Script outage cắt kết nối RPC qua `scripts/local-rpc-proxy.sh`. Gateway phải chạy với `SOLANA_RPC_HTTP_URL=http://host.docker.internal:9133` trong lúc drill, rồi trả về `:9123` sau đó. Ca hết hạn review cần bản sao ledger được warp đồng hồ; thao tác này chỉ làm trên bản sao, xem nhật ký ngày 2026-10-10 bên dưới.

## Nhật ký kiểm chứng chi tiết

Các mục dưới đây là biên bản theo thời gian. Nhận định "chưa đạt" trong các mục cũ đã được xử lý ở các mục sau.

### Tiến độ nền dữ liệu flow (chưa qua gate)

Quyết định nghiệp vụ ngày 2026-10-09: chỉ bỏ bộ chọn rail cho Job mới **khi** `UNIFIED_USDC_PAYOUT` đã có đường funding và qua các gate local mock. Cho tới lúc đó flag vẫn tắt; Job cũ tiếp tục theo rail đã ghi. Đây là quyết định về trình tự cutover, không phải bằng chứng E2E.

Nền dữ liệu ngày 2026-10-09: thêm `PaymentFlow`, step, evidence append-only, API timeline có kiểm tra participant/Admin, và flag cutover mặc định `false`. Job mới ghi version ở lúc tạo để Job cũ không đổi rail khi flag đổi; ba API funding cũ từ chối Contract mang rail unified. UI đọc timeline của rail unified và không đưa bộ chọn ba rail cho Contract đó. [Điều khoản local đã chốt](UNIFIED_FLOW_LOCAL_TERMS.md) có ví dụ 100 USD và các ranh giới xác nhận. Chưa có migration chính thức; không đánh dấu checklist.

#### Bổ sung bản triển khai local ngày 2026-10-09

- Payment Backend có USD order mock riêng, quote 15 phút, lệnh xác nhận của Client và sao kê `USD_RECEIVED` độc lập. Marketplace chỉ xác nhận `USD_RECEIVED` khi sao kê khớp `paymentFlowId`, reference, amount và currency; sau đó đối soát receipt on-ramp trước khi mở quyền ký funding escrow. Giao diện unified hiển thị USD order, trạng thái USDC và thao tác ký escrow theo cùng timeline.
- Reconciler Solana ghi `USDC_RELEASE` hoặc `USDC_REFUND` sau trạng thái terminal on-chain khớp escrow đã xác nhận; chặn ghi hai kết quả đối nghịch. Contract unified bị chặn khỏi release/refund ledger mô phỏng cũ. Job vẫn chỉ kích hoạt khi vault ở trạng thái có đủ USDC.
- Build Java dùng JDK 21/Maven 3.9.12 tạm trong `/tmp/freelax-build` vì workspace không có JDK/Maven. `mvn -q -o -pl marketplace-backend -am -DskipTests compile` PASS; `UnifiedUsdOrderMockServiceTest` PASS; `PaymentFlowServiceTest`, `SettlementServiceTest`, `ContractCancellationServiceTest` PASS **92/92**. Build frontend và 15 ca `Funding.test.tsx` PASS sau thay đổi UI. `git diff --check` PASS.
- Tại thời điểm bổ sung này chưa có E2E unified; bằng chứng local E2E chạy sau đó được ghi ở phần dưới. Trạng thái release/refund hiện dựa trên account escrow terminal do gateway đọc; Admin chưa tự chứng minh token delta ví đích. Vì vậy **không bật** `PAYMENT_FLOW_CUTOVER_ENABLED` cho Job thường.

#### Bổ sung chặng tiền ra (code và unit test, chưa E2E)

- Gateway `request_offramp` hiện được gọi ở chế độ build; Freelancer ký withdrawal sau `USDC_RELEASE`, hoặc Client ký trả USDC vào treasury sau `USDC_REFUND`. Marketplace đối chiếu WithdrawalRecord theo ví, mint, lượng token, rate snapshot, withdrawal ID và VND gross trước khi gửi lệnh cho mock provider. Bank beneficiary, quote 25.000 VND/USDC, phí 3% và idempotency key được khóa trong flow.
- Payment Backend có `UnifiedFiatExitMock` cho payout/refund: ví dụ 100 USDC tạo phí 3 USDC và payout 2.425.000 VND; refund tạo sao kê 100 USD, phí 0. Reconcile Marketplace chỉ xác nhận `VND_PAYOUT` **và** `PLATFORM_FEE` khi cả hai sao kê khớp; xác nhận `USD_REFUND` sau sao kê USD, không suy diễn từ `USDC_REFUND`. Finance có timeline và thao tác ký withdrawal cho participant đúng quyền.
- Bộ test backend đầy đủ trên source copy `/tmp/freelax-build`: `mvn -q -o -pl marketplace-backend -am test` PASS **292/292** và `mvn -q -o -pl payment-backend -am test` PASS **69/69**. Frontend `npm run build` PASS, `npm test` PASS **794/794** (22 files); `git diff --check` PASS. JDK 21/Maven 3.9.12 dùng bản tạm trong `/tmp`, không dùng `target/` của workspace. Các con số này xác nhận compile/unit và integration H2 hiện có, **không** thay thế browser E2E/local validator.
- Payer bank được snapshot khi Client mở USD order; Payment Backend khóa cùng order, và mock USD refund dùng lại chính beneficiary này. Timeline chỉ trả mã ngân hàng và số tài khoản đã che. Hai bên đã xác nhận mẫu tiền trước nhận Job trên cặp QA mới; ngân hàng và quote/mint cuối chỉ có sau phân công, nên bước chốt snapshot cuối trước USD order vẫn cần kiểm chứng riêng.
- Admin có màn hình đọc bốn ranh giới của 50 flow gần nhất từ provider statement, on-ramp receipt, escrow PDA, WithdrawalRecord và payout/refund statement. Mã `RECIPIENT_TOKEN_DELTA_UNPROVEN` cố ý giữ `UNKNOWN`: gateway hiện chưa trả token delta ví đích, dù program escrow chuyển token cùng giao dịch trước khi ghi terminal. Đây là khoảng trống chứng cứ Gate 3/4, không được trình bày là đã đối soát khớp.
- Các giới hạn này được kiểm tra lại trong E2E bên dưới; kết quả mới không tự động xác nhận browser E2E, outage/restart và đối soát delta tự động trong Admin.

#### E2E thống nhất trên local validator (2026-10-09)

Ledger mới `solana-stablecoin-payout/target/unified-ledger-20261009` dùng program `2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb`, Mock USDC mint `DXeZia7qViiE8nsF4XLz2NH1yeZF57Wk2kbCn3JYExRZ`; script `bootstrap-local-demo.ts` cấp mint/treasury demo, `local-unified-flow-e2e.ts --prepare` kiểm tra clock và đồng bộ authority với signer local. Flag chỉ bật tạm trên container Marketplace lúc tạo Job QA, sau đó đặt lại `false` trước funding. Script E2E dùng tài khoản seed và khóa local từ `.env` bị Git bỏ qua. Không dùng đối tác ngân hàng hoặc token thật.

MySQL local vẫn giữ vài Job QA từ ledger demo trước đó. Khi chuyển validator sang ledger mới, account/receipt của các Job đó không tồn tại trên ledger đang chạy; reconciler giữ chúng ở trạng thái chờ và ghi cảnh báo. Không suy diễn cảnh báo này thành trạng thái tiền của hai flow được chứng minh ở bảng dưới, và không xóa/migrate các reference cũ bằng cập nhật DB.

```bash
# Từ solana-stablecoin-payout, sau khi validator/Compose/bootstrap đã sẵn sàng:
PAYMENT_FLOW_CUTOVER_ENABLED=true docker compose up -d --no-deps --force-recreate marketplace-backend
ANCHOR_PROVIDER_URL=http://127.0.0.1:9123 ANCHOR_WALLET="$HOME/.config/solana/id.json" ./node_modules/.bin/ts-mocha -p tsconfig.json -t 120000 scripts/local-unified-flow-e2e.ts --prepare
PAYMENT_FLOW_CUTOVER_ENABLED=false docker compose up -d --no-deps --force-recreate marketplace-backend
ANCHOR_PROVIDER_URL=http://127.0.0.1:9123 ANCHOR_WALLET="$HOME/.config/solana/id.json" ./node_modules/.bin/ts-mocha -p tsconfig.json -t 240000 scripts/local-unified-flow-e2e.ts
```

Hai lệnh `docker compose` chạy ở thư mục gốc repo; hai lệnh `ts-mocha` chạy trong `solana-stablecoin-payout`. Đợi Marketplace khởi động trước mỗi lệnh `ts-mocha`. File Job QA được lưu trong `target/unified-e2e-jobs.json` (Git bỏ qua). Script đã PASS sau khi tiếp tục cùng cặp Job qua lần đọc PDA/vault lệch slot; chạy lại sau khi hoàn tất cũng PASS mà không phát lệnh tiền mới.

| Nhánh | Job / `paymentFlowId` | Chứng cứ local cuối |
| --- | --- | --- |
| Release | `cb438cd5-006a-4240-99ab-924eaf1a3e44` / `95d7adf2-28cd-4481-bae8-65dd38a9831c` | 15 USD statement → 15 USDC on-ramp → vault 15 → Freelancer ATA tăng 15 → WithdrawalRecord `AeyLCpK2HpFJM1ZpAZfH8xKqGc5nnp2vPnChvo4TJANX` và treasury ATA tăng 15 → 363.750 VND statement + 0,45 USDC fee statement; Job `COMPLETED`, vault 0. Fund tx `2NDiCJm6v47mYDrMC4PA56MqpKyVkGzDKBtyzhsSQHxacK42dt44ubgZSEtZn7cGSYsXFZZ1PiA1T3HsfD2BM5ma`, release tx `55UCJFBMUKYy68yWP5ph3WgkQH8L8h7tKg6iXbQ4XEEB844fLKYmsmRMtBFHD96aHoWdqt5SMu9YngBE9SzsYWzz`, withdrawal tx `5BadPjsSFqE3AfdRsRPGgepCJYDgrzdRcJ5JqFEWS2mUGnRpSF55pubQvntzCyHqmjT9kDQvjESkCmBdJNaYiemk`. |
| Refund | `f9b68c1f-5262-47f4-8b8e-4b9e1ac72e3a` / `f1b7817b-e987-4050-b010-c714eb27c0a7` | 10 USD statement → 10 USDC on-ramp → vault 10 → Client ATA tăng lại 10 → WithdrawalRecord `EEb1vGhcFyTjZxqLjERr3wWxRVPr1Z31E6d3UMUEesRk` và treasury ATA tăng 10 → 10 USD refund statement, phí 0; Job `CANCELLED`, vault 0. Fund tx `3BPuubKP4CL4xmpNRGUXsYpWPt34zdhvYH7umodZjiYkHNNLcaJXr3P89QjGi7hprRMCngbyCTgkxQLHoQhEbKtJ`, refund tx `4Kh2E9Q74Zxu6BJimKXUuSAAWMhsErRZnKirRsmSktU6RE7Vo2BBNG3ntkMTaexUWmRsoqEG2KzSrg9Pi8j9fpoM`, withdrawal tx `3u2JKFF2Qnf9U97rGh8QXGvQxYNvUtxThnGzqwZbdCTLvrGLs6hqmamttWdCkZBfR8gwxpVBoSmq3EM1WcvYM53`. |

Sau khi chốt mẫu điều khoản, cặp QA tiếp theo cũng PASS với API yêu cầu fingerprint: Freelancer gửi mã khi ứng tuyển, Client gửi cùng mã khi phân công; server lưu dấu chấp nhận và từ chối mã thiếu/sai. Nhánh release Job `edb51892-757f-4b4f-b796-c31d9c9236cf`, flow `432f55a8-eb34-46c2-bda6-1720a4208619`: 15 USD → 15 USDC → 363.750 VND, phí 0,45 USDC. Nhánh refund Job `041c9d0e-f9af-46e4-b108-11f48c82ae86`, flow `d7acb36f-d621-4fef-89c1-4b5b545cc119`: hoàn 10 USD, phí 0. Cả hai đi qua vault/WithdrawalRecord và sao kê mock đúng nhánh. Flag bật tạm khi tạo Job, sau đó `docker compose exec -T marketplace-backend printenv PAYMENT_FLOW_CUTOVER_ENABLED` trả `false` trước chạy E2E.

Admin reconciliation trả `USD_USDC_MATCH`, `VAULT_MATCH`, `FIAT_AND_FEE_MATCH` cho cả hai flow và giữ `RECIPIENT_TOKEN_DELTA_UNPROVEN` ở ranh giới thứ ba. Delta đã được script E2E đo trực tiếp trên validator trong lúc chạy, nhưng chưa có chứng cứ delta được Marketplace/Gateway lưu và tái đối soát tự động sau restart; vì vậy ranh giới thứ ba **vẫn UNKNOWN trong sản phẩm**. Marketplace full unit/integration suite PASS **295/295** gồm ca điều khoản mới; Payment Backend suite PASS **69/69** ở bản code hiện tại. Frontend `npm test -- --maxWorkers=2 --testTimeout=20000` PASS **796/796**, gồm hai ca yêu cầu xác nhận điều khoản ở Client/Freelancer; `npm run build` và Docker frontend build PASS. Browser smoke read-only `frontend/scripts/unified-browser-smoke.mjs` PASS 12 ảnh Client/Freelancer/Admin ở 1440/390 px, gồm xác nhận hai bên thấy cùng mẫu tiền trước funding; không lỗi JavaScript hoặc tràn ngang; ảnh ở `target/browser-e2e/`. Browser smoke này dùng Job/tài khoản seed đã chuẩn bị qua API, **không** chứng minh hành trình đăng ký và ký ví hoàn toàn trong browser. `git diff --check` PASS. Không đánh dấu Gate 0–6 đã hoàn tất và flag Compose mặc định/đang chạy là `false`.

WSL local thiếu thư viện Chromium; ca browser chạy bằng `LD_LIBRARY_PATH=/tmp/freelax-browser-libs/usr/lib/x86_64-linux-gnu node scripts/unified-browser-smoke.mjs` từ `frontend/` sau khi tải `libnspr4`, `libnss3`, `libasound2t64` vào `/tmp`. Máy đã cài phụ thuộc Chromium có thể chạy `node scripts/unified-browser-smoke.mjs` trực tiếp. Lần `npm test` không giới hạn worker trong lúc stack/browser cùng chạy có 16 timeout/DOM chậm; lần chạy giới hạn 2 worker sau khi thêm ca điều khoản PASS đủ 796 ca.

### Bổ sung cuối ngày 2026-10-09: tài khoản mới, đối soát và migration

Source: nhánh `feat/solana-milestone-escrow`, thay đổi chưa commit tại thời điểm chạy. Môi trường: localnet `127.0.0.1:9123` trên ledger `target/unified-ledger-20261009`, program `2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb` được nâng cấp (slot 7367) để có `high_value_review_grace`; Compose local; đối tác USD/VND mock. Không dùng tiền thật.

**Thay đổi được kiểm chứng**

- Review cố định 72 giờ cho Job unified, không grace 24 giờ; hạn funding một nguồn (`PaymentFlow.fundingExpiresAt`) cho server và chain; timer không hủy khi USD có thể đã vào; Admin “Hủy hợp đồng quá hạn” (USDC ở ví Client → treasury → hoàn USD).
- Mint/network chụp khi đăng Job, nằm trong fingerprint; USD order bị từ chối khi mint đã đổi hoặc Client chưa liên kết ví.
- Ranh giới vault → người nhận: `RECIPIENT_BY_ESCROW_INVARIANT` (terminal + vault 0 + participant khớp), thay `RECIPIENT_TOKEN_DELTA_UNPROVEN`.
- Bước tiền tiếp theo bị chặn khi ranh giới trước chưa `MATCHED`; Admin ghi quyết định có audit (không ghi đè bằng chứng).
- Flyway `V1__baseline_schema.sql` + `V2__unified_terms_and_admin_audit.sql`; Hibernate `validate`.
- Lỗi tìm thấy nhờ browser E2E và đã sửa: panel unified thiếu form ngân hàng Client; liên kết ví chỉ hiện sau on-ramp (on-ramp lại cần ví trước); `ContractLifecycle` coi Contract unified là rail mô phỏng; 404 escrow trước funding hiển thị như lỗi; duyệt qua UI đánh dấu escrow reconciled trước khi ghi `USDC_RELEASE` (flow kẹt, Freelancer không rút được) — thêm lượt quét tự hồi phục; rate vừa publish chưa đọc được làm prepare withdrawal trả 409; lỗi thao tác bị lần tải lại xóa.

**Kết quả test**

- Marketplace `mvn -o -pl marketplace-backend -am test` (bản sao nguồn trong `/tmp/freelax-verify`, JDK 21): **319/319** PASS. Payment Backend: **71/71** PASS. Frontend `npm test -- --maxWorkers=2 --testTimeout=20000`: **799/799** PASS (gồm test panel ngân hàng, lifecycle unified, terms review/mint); `npm run build` PASS. Anchor `anchor test --validator legacy`: **75/75** PASS (gồm ca 501 USDC: 72 giờ khi tắt grace, 96 giờ khi bật). Gateway: compile qua `Dockerfile.maven` (máy local thiếu dependency Maven offline cho module này).
- Flyway: DB trống áp V1+V2 và app khởi động với `validate`; bản sao schema hiện có được baseline V1 rồi áp V2; DB `marketplace` local đã sao lưu trước rồi baseline V1 → V2 thành công.
- E2E API `scripts/local-unified-flow-e2e.ts` (tài khoản seed): PASS. Release flow `abfcd971-b522-41d8-9928-caa62943b63d` (15 USD → 363.750 VND, phí 0,45 USDC), refund flow `2e949af9-f573-49a8-b11d-350d883c7169` (hoàn 10 USD). Admin: `USD_USDC_MATCH`, `VAULT_MATCH`, `RECIPIENT_BY_ESCROW_INVARIANT`, `FIAT_AND_FEE_MATCH` cho cả hai.
- **Browser E2E tài khoản mới** `frontend/scripts/unified-new-account-e2e.mjs` (Playwright; ví trình duyệt được thay bằng ví thử nghiệm ký bằng khóa tạo mới mỗi lần chạy, nạp SOL local qua airdrop): đăng ký Client và Freelancer → đăng Job → hai bên thấy cùng điều khoản → ứng tuyển/giao việc → liên kết ví → ngân hàng → USD order → on-ramp → ký escrow → bàn giao → duyệt → withdrawal → VND. Release flow `69753190-18e5-4306-87a4-6784b50d224e`: 12 USD → 291.000 VND, phí 0,36 USDC. Job refund thứ hai flow `e25f5f3b-dc10-4719-afc8-fa18bb864744`: hoàn bằng hai chữ ký → USDC về Client → treasury → hoàn 8 USD, phí 0. Bốn ranh giới `MATCHED`; không lỗi JavaScript; ảnh 1440/390 px trong `target/browser-e2e/new-account/`.
- Recovery: flow `f07293e8-fb81-44f7-a829-29ef9b65e368` bị kẹt do lỗi duyệt nói trên tự ghi `USDC_RELEASE` và `WORK_ACCEPTED` sau khi Marketplace restart với bản sửa.

```bash
# Từ thư mục gốc; flag bật khi tạo Job, chỉ tạo lại Marketplace
PAYMENT_FLOW_CUTOVER_ENABLED=true docker compose up -d --no-deps --force-recreate --wait marketplace-backend
cd frontend && node scripts/unified-new-account-e2e.mjs   # cần Chromium; xem ghi chú LD_LIBRARY_PATH ở trên
```

**Chuyển đổi local (2026-10-09):** chủ sản phẩm xác nhận bật flag khi các gate dưới đây còn mở; kiểm tra trước/sau ghi ở [nhật ký chuyển đổi](CUTOVER_RUNBOOK.md#nhật-ký-chuyển-đổi). Đây là quyết định vận hành, không đổi các mục chưa đạt thành đạt.

**Giới hạn còn lại tại thời điểm đó (đã xử lý ở [bổ sung 2026-10-10](#bổ-sung-2026-10-10-tranh-chấp-timeout-sự-cố-đồng-thời-và-bảo-mật)):** chưa có test đồng thời trên MySQL thật; chưa E2E lỗi RPC timeout, restart validator/Payment giữa flow, sao kê lệch trên stack chạy thật; dispute/timeout chưa chạy lại trên flow unified tới off-ramp; Admin chưa có tổng hợp số dư theo USD/USDC/VND; browser chưa có ca từ chối ký ví, callback chậm có chủ đích và keyboard focus; quy tắc gia hạn bàn giao chờ chủ sản phẩm xác nhận. Flag Compose mặc định là `false`; môi trường local đã bật `true` qua `.env` sau khi chủ sản phẩm xác nhận.

### Bổ sung 2026-10-10: tranh chấp, timeout, sự cố, đồng thời và bảo mật

Môi trường: Compose local, localnet `127.0.0.1:9123` ledger `unified-ledger-20261009`, flag cutover `true` (đã chuyển đổi local). Thay đổi chưa commit tại thời điểm chạy, commit ngay sau.

| Ca | Lệnh | Kết quả |
| --- | --- | --- |
| Tranh chấp → Freelancer | `scripts/local-unified-dispute-e2e.ts` | Job `9e1ea5c9-883e-4844-883c-c1092959fbf0`, flow `1ef083fb-dfbf-4649-b48a-8d1f277fc21b`: dispute on-chain chặn withdrawal; Admin `RELEASE_TO_FREELANCER` → USDC release → withdrawal → 218.250 VND + 0,27 USDC phí; Job `COMPLETED`; 4 ranh giới `MATCHED`. |
| Tranh chấp → Client | như trên | Job `65c09862-4df2-4ad8-93a7-9a3dff7f8b60`, flow `b73a9677-1c73-4957-a88b-db0f65a27376`: `REFUND_TO_CLIENT` → USDC về Client → treasury → hoàn 7 USD; không VND; Job `CANCELLED`. |
| Hết hạn review 72 giờ | `scripts/local-unified-timeout-e2e.ts` (setup trên ledger gốc; finish trên bản sao `target/unified-ledger-timeout-clone` warp 3 lần tới slot 15.000.000; Marketplace `ESCROW_E2E_CLOCK_OFFSET_SECONDS=300000`, profile dev) | Job `5ca05e06-f88d-4116-8de0-835791fe7872`, flow `1ca58c5b-ddcc-4437-abbd-95eeba4572c0`: scheduler tự gửi release `4hjUqgu9…tJbk`; chain `settledAt 1791889255` ≥ `reviewDueAt 1791823891`; Freelancer +6 USDC; `USDC_RELEASE` và `WORK_ACCEPTED` `CONFIRMED`. Off-ramp không chạy trên chain warp (rate snapshot theo giờ máy bị coi là cũ); đoạn sau release dùng chung code với các ca đã PASS. |
| Lệch trên stack thật | (sau khi trả về ledger gốc) | Flow `1ca58c5b-…` chỉ release trên bản sao: Admin `TERMINAL_CHAIN_MISMATCH`, prepare withdrawal 409, `CLEARED_BY_EVIDENCE` bị từ chối (`PAYMENT_RECONCILIATION_BLOCKED`), `ESCALATED` được ghi audit. |
| Sự cố | `scripts/local-unified-outage-e2e.ts` (Gateway trỏ `SOLANA_RPC_HTTP_URL=http://host.docker.internal:9133` qua `scripts/local-rpc-proxy.sh`) | Job `0528a987-26ef-48bd-8eea-0464bed622a5`, flow `9d16f45d-acb5-4ccb-909e-1b423578a0e2`: Payment Backend dừng khi nộp USD → `USD_ORDER UNKNOWN`, hồi phục với **1** dòng `USD_RECEIVED`; RPC cắt → Admin `TERMINAL_LOOKUP_UNKNOWN`, prepare bị chặn, submit withdrawal bị từ chối, sau đó gửi lại cùng giao dịch; Payment dừng khi đến hạn chi → `VND_PAYOUT PENDING`, hồi phục với đúng `VND_PAYOUT` + `PLATFORM_FEE`; treasury +5.000.000 base units một lần; 121.250 VND, phí 0,15 USDC. |
| Giao dịch on-ramp bị rơi | (xảy ra thật khi validator ngừng sinh block) | Flow `dc965294-237c-457d-ab8b-864f54d8eb36`, `65935896-243c-41fe-bba6-30d94a88f739`: chữ ký không có trên chain, receipt chưa có → sau 300 giây tự gửi lại cùng purchase ID → `CLIENT_USDC CONFIRMED`. |
| Browser ca biên | `frontend/scripts/unified-browser-edge-e2e.mjs` | Tài khoản mới, Job `d82da6bc-0378-4589-8814-21754c4eeb79`: Tab tới ô điều khoản (outline 3px), Space/Enter ứng tuyển; đối tác dừng khi nộp USD → Job vẫn `AWAITING_PAYMENT`, chưa có bước ký quỹ; ví từ chối ký → “User rejected the request.”, không có `fundSignature`; ký lại → `IN_PROGRESS`. Ảnh trong `target/browser-e2e/edge-cases/`. |
| Đồng thời MySQL | `mvn -pl marketplace-backend test -Dtest=PaymentFlowMySqlConcurrencyIT -Dfreelax.mysql.url=… -Dfreelax.mysql.password=…` (MySQL 8.4 tạm, Flyway) | 3/3 PASS. |

**Lỗi tìm thấy và đã sửa trong đợt này:** cờ `reviewedAutomatically` dùng giờ máy thay vì thời điểm chốt trên chain; on-ramp bị rơi kẹt `FAILED` vĩnh viễn (nay gửi lại khi chain xác nhận chưa có receipt); withdrawal chuẩn bị nhưng không ký bị quét mãi (nay về `PENDING` sau khi quote hết hạn); Freelancer thấy tài khoản ngân hàng (đã che) của Client; test PDA của Gateway còn theo program ID cũ.

**Test:** Marketplace **326/326**, Payment Backend **71/71**, Frontend **800/800** + build, Gateway **7/7** (Docker Maven), Anchor 75/75 (không đổi program từ lần trước), MySQL IT 3/3. Flyway Payment Backend: DB trống áp V1, DB hiện có baseline V1, DB `payment` local baseline thành công (đã sao lưu trước).

**Ghi chú vận hành validator test:** kill validator một node khi đang vote có thể làm tower kẹt (“Waiting to switch vote”); xóa tower khiến validator hoãn leader slot ~2.000 slot. Drill RPC vì vậy cắt proxy thay vì dừng validator; `scripts/local-validator-ctl.sh` chỉ trả về khi block mới được tạo.

### Bổ sung 2026-10-10 (tối): ví demo tự động và khoản chi sau phí và thuế

Môi trường: Compose local, localnet `127.0.0.1:9123`, flag cutover `true`. Thay đổi kiểm chứng trên nhánh `feat/local-auto-wallet-net-payout`.

| Ca | Lệnh | Kết quả |
| --- | --- | --- |
| Tài khoản mới, ví tự động, release + hoàn tiền | `frontend/scripts/unified-new-account-e2e.mjs` | Run `mv1lgx1b`. Trang không có `window.solana`; ví được cấp tự động. Release Job `c486c7c0-3691-411d-b6d3-64b2b6aef93d`, flow `d6c250ee-4675-4f4a-9f72-e9efa3f0ca15`: 12 USD → phí 0,36 → `VND_PAYOUT` 261.900 VND; chứng từ `ACCEPTED`, thu nhập 291.000, thuế 29.100, `LOCKED_PAYOUT_QUOTE`. Hoàn tiền Job `1aee9033-48bb-4487-b001-d15e8c87d6e7`, flow `0623690d-02a2-4f08-a6a9-3d85f0f15336`: hoàn 8 USD, phí 0. Bốn ranh giới `MATCHED` cho cả hai flow. |
| Ca biên | `frontend/scripts/unified-browser-edge-e2e.mjs` | Run `mv1lme5c`, Job `df84c4dd-16b2-4368-8640-0e8fc751b512`: thao tác bàn phím (outline 3px); Payment Backend dừng lúc nộp USD thì bước hiện "Đang kiểm tra", công việc không mở, sau đó hồi phục; chuẩn bị giữ tiền hai lần vẫn chỉ ký quỹ một lần bằng ví tự động. Ca "từ chối ký trong ví" không còn áp dụng cho localnet. |
| Khoản cũ `terms-v1` | DB local | SmartClass vẫn chi 2.910.000 VND, chứng từ cũ 3.000.000 / 300.000 VND giữ nguyên; trang Job hiện cảnh báo cách tính cũ. |

**Giới hạn:** ví demo tự động chỉ bật ở profile `dev` + `localnet`. Backend giữ khóa (mã hóa bằng khóa dẫn xuất từ JWT secret) và ký thay mọi giao dịch có ví của tài khoản làm signer. Không dùng cho môi trường khác.

## Mẫu ghi bằng chứng khi hoàn thành gate mới

Với mỗi gate, ghi **ngày chạy, commit/source revision, môi trường (localnet/devnet/provider mock hay thật), Job/Contract/Milestone và `paymentFlowId`, lệnh test, trạng thái quan sát, transaction/provider references, số dư và sao kê trước/sau, giới hạn còn lại**. Che dữ liệu tài khoản và secrets. Chỉ khi gate E2E unified đủ bằng chứng mới đổi kết luận đầu trang và [tổng quan nghiệp vụ](README.md) thành “đã triển khai”. Local mock PASS không tự chứng minh devnet, đối tác thật hay tiền thật.
