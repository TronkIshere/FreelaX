# FreelaX

FreelaX là marketplace hai phía cho **Client** (thuê việc) và **Freelancer** (nhận việc). Hệ thống bao trọn vòng đời của một Job: đăng việc, thống nhất điều khoản, ký quỹ, bàn giao, nghiệm thu hoặc tranh chấp, chi trả, chứng từ thuế và đánh giá. Tiền đi theo **một luồng có bằng chứng ở từng bước**:

**USD của Client → USDC trong ví Client → escrow của Job trên Avalanche C-Chain → USDC trong ví Freelancer → VND vào tài khoản Freelancer**, kèm chứng từ khấu trừ thuế.

**Trạng thái:** Avalanche là blockchain chính của repo (nhánh `master`). Chạy ở hai môi trường mô phỏng: **LOCAL_SIMULATION** (Anvil, MockUSDC) và **AVALANCHE_FUJI** (testnet). Đối tác USD/VND và MISA đều mô phỏng. **Chưa** có tiền thật, đối tác thật hay Mainnet. Mã Solana cũ được giữ ở nhánh `solana` và không còn là module của `master`.

## Phạm vi dự án: mô phỏng phát triển trên Avalanche (ràng buộc cố định)

> FreelaX is an Avalanche-powered development simulation of a USDC-based freelance payment and escrow platform. It demonstrates blockchain-based payment workflows, milestone escrow, transaction verification, and AI-assisted financial monitoring without handling real-world funds or providing actual financial settlement.

- **Không xử lý tiền thật** và không quyết toán tài chính thật. Công nghệ blockchain là thật (EVM, chữ ký, giao dịch trên testnet công khai), nhưng mọi nghiệp vụ tài chính đều là **mô phỏng**.
- Chỉ hỗ trợ hai môi trường: **LOCAL_SIMULATION** (Anvil/EVM cục bộ, USDC mô phỏng) và **AVALANCHE_FUJI** (testnet AVAX và testnet USDC, giao dịch thật trên testnet công khai). **Mainnet nằm ngoài phạm vi**: không triển khai, không cấu hình, không có đường dự phòng.
- Ngôn ngữ giao diện: "Simulation Balance", "Testnet USDC", "Simulated Escrow", "Testnet Transaction", "Demonstration Only"; tránh "số dư thật", "rút tiền", "đã nhận tiền thật".
- Chi tiết, lý do dùng Avalanche, khác biệt local và testnet: [docs/SIMULATION_SCOPE.md](docs/SIMULATION_SCOPE.md). Các mục tiền thật, đối tác thật, ví người dùng thật, Mainnet/production **không thuộc phạm vi**.

## Hệ thống có gì và đã làm được gì

| Nhóm | Đã có |
| --- | --- |
| Tài khoản | Đăng ký/đăng nhập Client hoặc Freelancer; Admin cấp riêng; profile, kỹ năng, portfolio, hồ sơ công khai; liên kết ví EVM (EIP-1193, ví trình duyệt) với tài khoản |
| Job và hợp đồng | Job có sản phẩm bàn giao, tiêu chí nghiệm thu, hạn, số lần sửa; tìm việc và ứng tuyển; hai bên xác nhận cùng một bản điều khoản (fingerprint) trước khi giao việc |
| Ký quỹ | Client nộp USD → đối tác mô phỏng xác nhận → USDC mô phỏng vào ví Client → Client ký nạp escrow của Job trên Avalanche; công việc chỉ mở khi escrow đã có tiền |
| Làm việc và nghiệm thu | Bàn giao có bằng chứng và phiên bản; Client **Duyệt / Yêu cầu chỉnh sửa / Mở tranh chấp**, kèm **đồng hồ đếm ngược**; hết 72 giờ không quyết định thì tự duyệt; gia hạn bàn giao một lần ≤ 7 ngày |
| Tranh chấp | Mở tranh chấp sẽ khóa escrow; hai bên có thể thỏa thuận chia (`mutualSettleDispute`), hoặc Admin/arbiter quyết định: release cho Freelancer hoặc hoàn tiền cho Client |
| Chi trả và hoàn tiền | Freelancer ký rút USDC → đối tác chi VND sau khi trừ phí 3% và khấu trừ thuế 10% trên phần còn lại (100 USD → 2.182.500 VND); hủy trước release thì hoàn đủ USD cho Client, phí 0 |
| Chứng từ thuế | Sau khi chi VND, MISA (mô phỏng) phát hành chứng từ khấu trừ thuế; Freelancer tải PDF/XML |
| Quản trị | Đối soát các ranh giới tiền; bảng "Tiền đang ở đâu" tách theo USD/USDC/VND; quyết định Admin có audit; hủy hợp đồng quá hạn ký quỹ |
| Độ tin cậy | Mỗi bước tiền có idempotency key; trạng thái `UNKNOWN` thì tra cứu lại, không gửi lệnh mới; hồi phục khi đối tác, RPC hoặc node chain sập mà không chi trùng; Flyway quản lý schema |
| Khác | Finance, Activity, thông báo, đánh giá hai chiều và reputation |

Đã kiểm chứng bằng unit/integration test, E2E trên Anvil và Docker Compose (drive, browser, outage) (release, hoàn tiền, tranh chấp, hết hạn review, sự cố) và browser test với tài khoản mới đăng ký. Kết quả Avalanche: [PROJECT_STATUS](docs/avalanche-migration/PROJECT_STATUS.md); chi tiết nghiệp vụ cũ ở [VERIFICATION](docs/business/VERIFICATION.md).

## Đọc tài liệu theo thứ tự

1. [Nghiệp vụ hệ thống](docs/business/README.md): sản phẩm làm gì, ai làm gì, quy tắc đã chốt.
2. [Luồng tiền](docs/business/PAYMENT_FLOW.md): tiền nằm ở đâu, bằng chứng nào, đối soát ra sao, ví dụ 100 USD.
3. [Phạm vi mô phỏng](docs/SIMULATION_SCOPE.md) và [ADR 0001: chuyển Solana sang Avalanche](docs/adr/0001-solana-to-avalanche-migration.md): quyết định và ràng buộc.
4. [Avalanche migration](docs/avalanche-migration/README.md), trạng thái trong [PROJECT_STATUS](docs/avalanche-migration/PROJECT_STATUS.md), cách chạy local trong [local-runbook](docs/avalanche-migration/phase-2/local-runbook.md).
5. [Điều khoản local](docs/business/UNIFIED_FLOW_LOCAL_TERMS.md) và [Guide trình diễn trên trình duyệt](docs/business/BROWSER_DEMO_GUIDE.md).
6. [Kiểm chứng](docs/business/VERIFICATION.md), [Checklist](docs/business/PAYMENT_FLOW_REBUILD_CHECKLIST.md), [Runbook chuyển đổi](docs/business/CUTOVER_RUNBOOK.md).

Một số tài liệu trong `docs/business/` (ví dụ `SOLANA_ARCHITECTURE.md`) mô tả luồng Solana cũ và chỉ còn giá trị lịch sử; mã tương ứng nằm ở nhánh `solana`. Thư mục `docs/business/archive/` và `docs/ui/` chỉ giữ biên bản lịch sử.

## Kiến trúc nhanh

```text
Trình duyệt (React/TypeScript, :8080), ví EIP-1193
      │  chỉ gọi /api/v1
      ▼
Marketplace Backend (:9191) ── quyền, Job/hợp đồng, PaymentFlow, đối soát, scheduler
      ├──► Payment Backend (:9190) ─ đối tác USD/VND mô phỏng, sao kê độc lập
      ├──► Avalanche Gateway (:9194) ─ dựng/gửi/đọc giao dịch EVM ──► Anvil (:8545) hoặc Fuji
      │                                                              └─ FreelaXEscrow.sol (EIP-712)
      └──► MISA Backend (:9192) ──── chứng từ khấu trừ thuế PDF/XML (mô phỏng)
MySQL (:3307, mỗi service một schema) · Redis (:6380)
```

Marketplace là nơi quyết định nghiệp vụ. Avalanche là bằng chứng chuyển USDC (escrow on-chain); sao kê đối tác là bằng chứng tiền fiat; MISA là chứng từ thuế. Các cổng của Compose chỉ bind `127.0.0.1`.

| Thư mục | Nội dung |
| --- | --- |
| `frontend/` | Giao diện React; script browser E2E ở `frontend/scripts/` |
| `marketplace-backend/` | Spring Boot, nghiệp vụ chính, Flyway `db/migration` |
| `payment-backend/` | Đối tác USD/VND mô phỏng cùng module checkout/payout (PayPal sandbox ở `dev`) cho luồng Job cũ |
| `avalanche-integration/` | Avalanche Gateway (Java 21, web3j), module blockchain chính |
| `avalanche-contracts/` | Hợp đồng escrow Solidity (Foundry), script deploy, manifest |
| `misa-backend/` | Chứng từ thuế mô phỏng |
| `docs/avalanche-migration/` | Tài liệu, harness và bằng chứng của việc chuyển sang Avalanche |

Mã Solana (`solana-integration/`, `solana-stablecoin-payout/`) chỉ còn ở nhánh `solana`.

## Khởi chạy local (LOCAL_SIMULATION)

Cần Git, Docker + Compose, Node.js 24 và Foundry (hoặc image `ghcr.io/foundry-rs/foundry:v1.8.7`). Chỉ dùng khóa và mật khẩu **demo**; không đưa password, API key hay private key vào Git hoặc ảnh chụp. Không dùng Mainnet; backend không được giữ khóa Fuji.

**Bước 1 - Cấu hình.** `cp .env.example .env`, rồi điền:
- secret ngẫu nhiên cho MySQL, các JWT (ít nhất 64 ký tự) và internal API key (`PAYMENT_INTERNAL_API_KEY`, `AVALANCHE_INTERNAL_API_KEY`);
- mật khẩu tài khoản seed `DEMO_CLIENT_PASSWORD`, `DEMO_FREELANCER_PASSWORD`, `DEMO_ADMIN_PASSWORD` (tối đa 72 byte);
- Avalanche: `AVALANCHE_ENVIRONMENT=LOCAL_SIMULATION`, `AVALANCHE_RPC_PRIMARY_URL`, `AVALANCHE_SIGNERS_ENABLED`, `AVALANCHE_TX_ENABLED`, `AVALANCHE_DB_*`, `AVALANCHE_DEPLOYMENT_MANIFEST`, `AVALANCHE_GATEWAY_ENABLED=true`, `VITE_AVALANCHE_ENABLED=true`. Ý nghĩa từng biến có trong `.env.example`;
- bật luồng thống nhất cho Job mới: `PAYMENT_FLOW_CUTOVER_ENABLED=true`.

**Bước 2 - Chain và hợp đồng.** Bật Anvil (profile `avalanche`, file `docs/avalanche-migration/phase-2/compose-avalanche/docker-compose.anvil.yml`), triển khai `avalanche-contracts/script/Deploy.s.sol` với `DEPLOY_ENVIRONMENT=LOCAL_SIMULATION` để sinh manifest `avalanche-contracts/deployments/LOCAL_SIMULATION.json`. Gateway dùng signer tạm thời, nên sau mỗi lần gateway khởi động lại phải cấp lại vai trò `AUTH_SIGNER_ROLE` và arbiter cho địa chỉ signer mới. Các lệnh cụ thể nằm trong `docs/avalanche-migration/phase-2/compose-avalanche/run.sh`.

**Bước 3 - Bật các service:**

```bash
docker compose -f docker-compose.yml -f docs/avalanche-migration/phase-2/compose-avalanche/docker-compose.anvil.yml --profile avalanche --profile deploy up -d --build
docker compose --profile avalanche --profile deploy ps
```

Lần đầu chạy, Flyway tạo schema.

**Chạy nhanh toàn bộ stack bằng harness** (secret ngẫu nhiên dùng một lần, không đọc `.env`):

```bash
bash docs/avalanche-migration/phase-2/compose-avalanche/run.sh <thu-muc-ket-qua> drive.mjs browser.mjs outage.mjs
```

**Bước 4 - Kiểm tra:** `curl -I http://localhost:8080` phải trả 200; `curl -i http://localhost:8080/api/v1/auth/me` trả 401 khi chưa đăng nhập là bình thường; Anvil trả block number:

```bash
curl -fsS -H 'Content-Type: application/json' --data '{"jsonrpc":"2.0","id":1,"method":"eth_blockNumber"}' http://127.0.0.1:8545
```

**Bước 5 - Dùng thử:** mở `http://localhost:8080`, đăng ký tài khoản Client và Freelancer, liên kết ví EVM và làm theo [guide trình diễn](docs/business/BROWSER_DEMO_GUIDE.md). Tài khoản seed: Freelancer `freelancer.seed@example.com`, Admin `admin.e2e@example.test`, Client seed khai báo trong `marketplace-backend/.../configuration/DataInitializer.java`; mật khẩu lấy trong `.env`.

**Dừng:** `docker compose --profile avalanche --profile deploy stop`. Tránh `down -v` nếu muốn giữ dữ liệu.

## Những gì sẽ bổ sung

> Ghi chú phạm vi: dự án là mô phỏng phát triển ([SIMULATION_SCOPE](docs/SIMULATION_SCOPE.md)). Các mục tiền thật, đối tác thật, ví người dùng thật, chứng từ thuế thật và Mainnet **nằm ngoài phạm vi**.

| Hạng mục | Ghi chú |
| --- | --- |
| Avalanche Fuji | Chạy có chữ ký người và bằng chứng trên Fuji còn chờ chủ dự án; backend không giữ khóa Fuji. Xem [PROJECT_STATUS](docs/avalanche-migration/PROJECT_STATUS.md) |
| Ví người dùng | Kiểm chứng với tiện ích ví thật (MetaMask); hiện mới kiểm chứng bằng ví giả lập EIP-1193 |
| Chứng từ thuế | Flyway cho `misa-backend` (hiện vẫn `ddl-auto: update`) |
| Sản phẩm | Nhiều Milestone trong một Job; chat làm bằng chứng tranh chấp; lịch ngày lễ cho hạn; tối ưu mobile |
| Vận hành | Cảnh báo cho khoản `UNKNOWN`/lệch kéo dài; báo cáo đối soát định kỳ |
