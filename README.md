# FreelaX

FreelaX là marketplace hai phía cho Client và Freelancer: đăng việc, chốt hợp đồng, xác nhận funding, bàn giao, nghiệm thu, giải ngân/hoàn tiền và đánh giá sau hoàn thành. **Luồng tiền sản phẩm hướng tới là USD của Client → USDC trong ví Client → khóa USDC cho Job → USDC trong ví Freelancer sau nghiệm thu → đổi/chi VND cho Freelancer.** Rail `UNIFIED_USDC_PAYOUT` đã có E2E trên local validator với USD/VND mock và tài khoản seed; flag cutover vẫn tắt vì các gate về điều khoản, đối soát tự động, browser và recovery chưa hoàn tất. Không có bằng chứng chuyển tiền ngân hàng thật hoặc triển khai Solana production.

## Đọc tài liệu theo từng bước

1. **Hiểu nghiệp vụ:** đọc [hệ thống hiện đáp ứng những gì](docs/business/README.md#hệ-thống-đáp-ứng-những-gì), [vai trò và vòng đời Job](docs/business/README.md#vai-trò-và-luồng-công-việc), rồi [ranh giới hệ thống](docs/business/README.md#ranh-giới-hệ-thống).
2. **Hiểu tiền:** đọc [luồng USD → USDC → escrow → USDC → VND](docs/business/PAYMENT_FLOW.md), gồm bằng chứng và đối soát tại từng điểm chuyển tiền.
3. **Hiểu Solana:** đọc [Solana trong kiến trúc hiện tại và luồng đích](docs/business/SOLANA_ARCHITECTURE.md) để thấy on-ramp, vault, release và off-ramp nằm ở đâu.
4. **Xem tiến độ thực tế:** đọc [trạng thái kiểm chứng](docs/business/VERIFICATION.md) để thấy E2E local và các gate còn thiếu.
5. **Xây lại thanh toán:** dùng [checklist backend, Solana, frontend và E2E](docs/business/PAYMENT_FLOW_REBUILD_CHECKLIST.md) theo thứ tự phụ thuộc; chỉ đánh dấu xong khi có bằng chứng.
6. **Chạy demo hiện có:** làm lần lượt các bước ở [Chạy demo local](#chạy-demo-local-theo-từng-bước), rồi theo [guide demo cũ trong archive](docs/business/archive/LOCAL_TWO_SIDED_E2E_GUIDE.md). Guide đi qua nhánh đối tác mock cũ, chưa phải luồng thống nhất.
7. **Tra source/lịch sử:** dùng [bản đồ mã nguồn](docs/business/README.md#mã-nguồn-theo-nghiệp-vụ) và [biên bản theo ngày](docs/business/archive/README.md) khi cần ID giao dịch hoặc lỗi cũ.

Mốc P06 UI/source frozen và bằng chứng tại mốc đó ở [final handoff](docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md). Các luồng Solana escrow và đối tác mock được thêm sau P06; không dùng handoff P06 để suy chúng đã qua E2E.

## Kiến trúc nhanh

`Browser → React/TypeScript → /api/v1 → Marketplace → Payment / Solana Gateway / MISA`

Browser chỉ gọi Marketplace. Marketplace giữ auth, quyền và trạng thái nghiệp vụ; Payment giữ ledger mô phỏng và sao kê đối tác mock; Solana Gateway gọi Anchor/RPC; MISA tạo chứng từ tax demo. Compose dùng frontend `:8080`, Marketplace `:9191`, Payment `:9190`, MISA `:9192`, Gateway `:9193`, MySQL `:3307`, Redis `:6380`; local validator dùng RPC `:9123`. Host ports của Compose mặc định bind `127.0.0.1`.

## Chạy demo local theo từng bước

Các bước dưới đây chạy localnet với Mock USDC và dữ liệu đối tác mô phỏng. Cần Git, Docker/Compose; nếu chạy phần Solana, cần thêm Solana CLI/test-validator, Anchor, Node và Yarn. Dùng dữ liệu và khóa **demo**, không đưa password, API key, private key hoặc seed phrase vào Git hay ảnh chụp.

### Bước 1 — Cấu hình `.env`

Từ thư mục gốc repo, nếu chưa có `.env`, tạo bằng `cp .env.example .env`, rồi điền các giá trị bắt buộc theo chú thích trong file. `.env` được Git bỏ qua. Giữ nguyên Marketplace JWT secret giữa các lần tạo lại container để phiên đăng nhập còn hợp lệ. Cho Solana local, kiểm tra `SOLANA_NETWORK=localnet`, `SOLANA_RPC_HTTP_URL=http://host.docker.internal:9123` và `SOLANA_PROGRAM_ID=2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb`. `VITE_SOLANA_CLUSTER` có thể để trống để Compose dùng `localnet`. `SOLANA_LOCAL_PRIVATE_KEYS` chỉ dành cho demo local.

### Bước 2 — Bật validator Solana (chỉ khi thử `SOLANA_ESCROW`)

Chạy từ thư mục gốc repo. Build chỉ cần lần đầu hoặc sau khi sửa program. Ledger demo là `solana-stablecoin-payout/target/demo-ledger-20261009`; dùng cùng đường dẫn qua các lần chạy và không dùng `--reset` nếu muốn giữ giao dịch. Ledger `target/runtime-ledger` là chứng cứ ca time warp cũ, không dùng cho demo thường ngày.

```bash
cd solana-stablecoin-payout
yarn install --frozen-lockfile
anchor build --ignore-keys
cd ..
solana-test-validator \
  --ledger solana-stablecoin-payout/target/demo-ledger-20261009 \
  --rpc-port 9123 --faucet-port 9125 --bind-address 127.0.0.1 \
  --upgradeable-program 2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb \
    solana-stablecoin-payout/target/deploy/invoice_payments.so "$(solana address -k ~/.config/solana/id.json)" \
  --quiet
```

Giữ terminal này chạy. Khi ledger đã tồn tại, validator tiếp tục ledger cũ và bỏ qua tham số nạp program ở genesis. Trên host khác, cần xác nhận container Gateway truy cập được RPC qua `host.docker.internal:9123`.

### Bước 3 — Bật ứng dụng

Mở terminal khác tại thư mục gốc repo:

```bash
docker compose --profile deploy config --quiet
docker compose --profile deploy up -d --build --wait
docker compose --profile deploy ps
```

Nếu chỉ thử `PARTNER_ESCROW_MOCK`, dùng giao diện với frontend, Marketplace, Payment, MySQL và Redis; validator ở bước 2 chỉ phục vụ luồng Solana.

### Bước 4 — Bootstrap Mock USDC (chỉ với ledger Solana mới)

Chạy từ `solana-stablecoin-payout`. Script kiểm tra trước và khi chạy lại trên cùng ledger chỉ đọc số dư. Chạy thêm lần nữa ngay sau bootstrap; RPC local có thể trả `0` khi đọc quá sớm. Kết quả `mode: existing` phải cho số dư Client lớn hơn `0`.

```bash
cd solana-stablecoin-payout
ANCHOR_PROVIDER_URL=http://127.0.0.1:9123 \
ANCHOR_WALLET="$HOME/.config/solana/id.json" \
./node_modules/.bin/ts-mocha -p tsconfig.json -t 120000 scripts/bootstrap-local-demo.ts
```

### Bước 5 — Kiểm tra hệ thống và thao tác UI

Mở `http://localhost:8080`. `curl -I http://localhost:8080` phải trả HTTP 200; `curl -i http://localhost:8080/api/v1/auth/me` trả HTTP 401 trước đăng nhập là bình thường. Nếu đã bật validator, kiểm tra RPC:

```bash
curl -fsS -H 'Content-Type: application/json' \
  --data '{"jsonrpc":"2.0","id":1,"method":"getHealth"}' http://127.0.0.1:9123
```

RPC trả `"result":"ok"`. Sau đăng nhập, `/auth/me` phải trả đúng role. Xem [guide demo cũ hai phía](docs/business/archive/LOCAL_TWO_SIDED_E2E_GUIDE.md) để chạy nhánh đối tác mock với hai phiên Client/Freelancer; mật khẩu tài khoản seed nằm trong `.env` local.

### Bước 6 — Chạy Solana escrow E2E (tùy chọn)

Trên ledger đã bootstrap, từ `solana-stablecoin-payout` chạy:

```bash
ANCHOR_PROVIDER_URL=http://127.0.0.1:9123 \
ANCHOR_WALLET="$HOME/.config/solana/id.json" \
./node_modules/.bin/ts-mocha -p tsconfig.json -t 120000 scripts/local-marketplace-escrow-e2e.ts --existing
```

Script tạo hai Job QA bằng Mock USDC cho release và mutual refund; mỗi lần chạy tiêu thụ 15 Mock USDC ròng từ ví Client demo. Kết quả mong đợi: `1 passing`, Job `COMPLETED`/`CANCELLED`, hai vault bằng `0`. Đây là ca local token; [các gate khác và giới hạn](docs/business/VERIFICATION.md) được ghi riêng.

Luồng thống nhất `UNIFIED_USDC_PAYOUT` (USD → USDC → vault → VND) có hai bài kiểm tra: `scripts/local-unified-flow-e2e.ts` (API, tài khoản seed) và `frontend/scripts/unified-new-account-e2e.mjs` (browser, tài khoản mới đăng ký, ví thử nghiệm). Cả hai cần Marketplace chạy với `PAYMENT_FLOW_CUTOVER_ENABLED=true` khi tạo Job; lệnh đầy đủ, rollback và khoản đang dở ở [runbook chuyển đổi](docs/business/CUTOVER_RUNBOOK.md) và [VERIFICATION](docs/business/VERIFICATION.md). Schema Marketplace do Flyway quản lý (`marketplace-backend/src/main/resources/db/migration`); DB cũ được baseline tự động ở V1.

### Bước 7 — Dừng và giữ dữ liệu demo

Dừng validator bằng `Ctrl+C`; chạy `docker compose --profile deploy stop` ở thư mục gốc. Tránh `down -v` nếu muốn giữ MySQL/Redis và dữ liệu Caddy. Nếu đổi sang ledger mới, các Job QA gắn với ledger cũ không còn account chain tương ứng.

## Phạm vi chưa có trong demo

Chưa có tích hợp payment/off-ramp/tax provider production, đối tác fiat thật, Solana devnet/production đã kiểm chứng, cấp phát ví/signer production cho tài khoản mới hoặc tối ưu mobile. Chi tiết từng luồng và phạm vi test nằm trong [tài liệu nghiệp vụ](docs/business/README.md).
