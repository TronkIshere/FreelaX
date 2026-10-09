# FreelaX

FreelaX là marketplace hai phía cho **Client** (thuê việc) và **Freelancer** (nhận việc). Hệ thống bao trọn vòng đời của một Job: đăng việc, thống nhất điều khoản, ký quỹ, bàn giao, nghiệm thu hoặc tranh chấp, chi trả, chứng từ thuế và đánh giá. Tiền đi theo **một luồng có bằng chứng ở từng bước**:

**USD của Client → USDC trong ví Client → vault escrow của Job trên Solana → USDC trong ví Freelancer → VND vào tài khoản Freelancer**, kèm chứng từ khấu trừ thuế.

**Trạng thái (2026-10-10):** luồng `UNIFIED_USDC_PAYOUT` **đã triển khai ở phạm vi local mock**: Mock USDC trên Solana local validator, đối tác USD/VND và MISA đều mô phỏng. Môi trường local đã chuyển đổi, nên Job mới chỉ đi luồng này. **Chưa** có tiền thật, đối tác thật hay devnet/production.

## Hệ thống có gì và đã làm được gì

| Nhóm | Đã có |
| --- | --- |
| Tài khoản | Đăng ký/đăng nhập Client hoặc Freelancer; Admin cấp riêng; profile, kỹ năng, portfolio, hồ sơ công khai; liên kết ví Solana bằng chữ ký |
| Job và hợp đồng | Job có sản phẩm bàn giao, tiêu chí nghiệm thu, hạn, số lần sửa; tìm việc và ứng tuyển; hai bên xác nhận cùng một bản điều khoản (fingerprint) trước khi giao việc |
| Ký quỹ | Client nộp USD → đối tác mô phỏng xác nhận → Mock USDC vào ví Client → Client ký chuyển vào vault của Milestone; công việc chỉ mở khi vault đã có tiền |
| Làm việc và nghiệm thu | Bàn giao có bằng chứng và phiên bản; Client **Duyệt / Yêu cầu chỉnh sửa / Mở tranh chấp**, kèm **đồng hồ đếm ngược**; hết 72 giờ không quyết định thì tự duyệt; gia hạn bàn giao một lần ≤ 7 ngày |
| Tranh chấp | Mở tranh chấp sẽ khóa vault; Admin tiếp nhận rồi quyết định toàn phần: release cho Freelancer hoặc hoàn tiền cho Client |
| Chi trả và hoàn tiền | Freelancer ký rút USDC → đối tác chi VND, giữ phí 3%; hủy trước release thì hoàn đủ USD cho Client, phí 0 |
| Chứng từ thuế | Sau khi chi VND, MISA (mô phỏng) phát hành chứng từ khấu trừ thuế; Freelancer tải PDF/XML |
| Quản trị | Đối soát 4 ranh giới tiền; bảng "Tiền đang ở đâu" tách theo USD/USDC/VND; quyết định Admin có audit; hủy hợp đồng quá hạn ký quỹ |
| Độ tin cậy | Mỗi bước tiền có idempotency key; trạng thái `UNKNOWN` thì tra cứu lại, không gửi lệnh mới; hồi phục khi đối tác hoặc RPC sập mà không chi trùng; Flyway quản lý schema |
| Khác | Finance, Activity, thông báo, đánh giá hai chiều và reputation |

Đã kiểm chứng bằng unit/integration test, E2E trên local validator (release, hoàn tiền, tranh chấp, hết hạn review, sự cố) và browser test với tài khoản mới đăng ký. Chi tiết ở [VERIFICATION](docs/business/VERIFICATION.md).

## Đọc tài liệu theo thứ tự

1. [Nghiệp vụ hệ thống](docs/business/README.md): sản phẩm làm gì, ai làm gì, quy tắc đã chốt.
2. [Luồng tiền](docs/business/PAYMENT_FLOW.md): tiền nằm ở đâu, bằng chứng nào, đối soát ra sao, ví dụ 100 USD.
3. [Solana trong FreelaX](docs/business/SOLANA_ARCHITECTURE.md): ví, vault, release/refund, rút tiền, giải thích dễ hiểu.
4. [Điều khoản local](docs/business/UNIFIED_FLOW_LOCAL_TERMS.md): con số và quy tắc cụ thể.
5. [Guide trình diễn trên trình duyệt](docs/business/BROWSER_DEMO_GUIDE.md): đi qua luồng sản phẩm bằng giao diện, có ảnh.
6. [Kiểm chứng](docs/business/VERIFICATION.md), [Checklist](docs/business/PAYMENT_FLOW_REBUILD_CHECKLIST.md), [Runbook chuyển đổi](docs/business/CUTOVER_RUNBOOK.md): bằng chứng, tiêu chí, vận hành.

Thư mục `docs/business/archive/` và `docs/ui/` chỉ giữ biên bản lịch sử.

## Kiến trúc nhanh

```text
Trình duyệt (React/TypeScript, :8080)
      │  chỉ gọi /api/v1
      ▼
Marketplace Backend (:9191) ── quyền, Job/hợp đồng, PaymentFlow, đối soát, scheduler
      ├──► Payment Backend (:9190) ─ đối tác USD/VND mô phỏng, sao kê độc lập
      ├──► Solana Gateway (:9193) ── dựng/gửi/đọc giao dịch ──► Local validator (:9123)
      │                                                         └─ Anchor program: vault, on-ramp, withdrawal
      └──► MISA Backend (:9192) ──── chứng từ khấu trừ thuế PDF/XML (mô phỏng)
MySQL (:3307, mỗi service một schema) · Redis (:6380)
```

Marketplace là nơi quyết định nghiệp vụ. Solana là bằng chứng chuyển USDC; sao kê đối tác là bằng chứng tiền fiat; MISA là chứng từ thuế. Các cổng của Compose chỉ bind `127.0.0.1`.

| Thư mục | Nội dung |
| --- | --- |
| `frontend/` | Giao diện React; script browser E2E ở `frontend/scripts/` |
| `marketplace-backend/` | Spring Boot, nghiệp vụ chính, Flyway `db/migration` |
| `payment-backend/` | Đối tác USD/VND mô phỏng (và BofA mock của P06) |
| `solana-integration/` | Solana Gateway (Spring Boot) |
| `solana-stablecoin-payout/` | Anchor program, test, script E2E local |
| `misa-backend/` | Chứng từ thuế mô phỏng |

## Khởi chạy từng bước

Cần Git, Docker + Compose, Solana CLI (`solana-test-validator`), Anchor, Node.js và Yarn. Chỉ dùng khóa và mật khẩu **demo**; không đưa password, API key hay private key vào Git hoặc ảnh chụp.

**Bước 1 — Cấu hình.** Tạo file bằng `cp .env.example .env`, rồi điền:
- secret ngẫu nhiên cho MySQL, các JWT và internal API key;
- mật khẩu tài khoản seed `DEMO_CLIENT_PASSWORD`, `DEMO_FREELANCER_PASSWORD`, `DEMO_ADMIN_PASSWORD`;
- ví demo: `SOLANA_CUSTODIAL_CLIENT_PUBLIC_KEY`, `DEMO_FREELANCER_SOLANA_PUBLIC_KEY`, `SOLANA_ONRAMP_AUTHORITY_PUBLIC_KEY`, `SOLANA_LOCAL_PRIVATE_KEYS`. Định dạng khóa xem `solana-integration/README.md`.
- Bật luồng thống nhất cho Job mới: `PAYMENT_FLOW_CUTOVER_ENABLED=true`.

**Bước 2 — Build program và bật validator** (giữ terminal này chạy):

```bash
cd solana-stablecoin-payout && yarn install --frozen-lockfile && anchor build --ignore-keys && cd ..
solana-test-validator --ledger solana-stablecoin-payout/target/demo-ledger \
  --rpc-port 9123 --faucet-port 9125 --bind-address 127.0.0.1 \
  --upgradeable-program 2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb \
    solana-stablecoin-payout/target/deploy/invoice_payments.so "$(solana address -k ~/.config/solana/id.json)" \
  --quiet
```

Dùng cùng một đường dẫn ledger cho các lần chạy sau để giữ dữ liệu; không thêm `--reset`. Dừng validator bằng `Ctrl+C`, không kill nó giữa chừng.

**Bước 3 — Bật các service:**

```bash
docker compose --profile deploy up -d --build --wait
docker compose --profile deploy ps
```

Lần đầu chạy, Flyway tạo schema; với DB cũ thì tự baseline.

**Bước 4 — Khởi tạo Mock USDC và authority** (chỉ cần một lần cho ledger mới), từ `solana-stablecoin-payout`:

```bash
export ANCHOR_PROVIDER_URL=http://127.0.0.1:9123 ANCHOR_WALLET="$HOME/.config/solana/id.json"
./node_modules/.bin/ts-mocha -p tsconfig.json -t 120000 scripts/bootstrap-local-demo.ts            # mint + treasury demo
./node_modules/.bin/ts-mocha -p tsconfig.json -t 400000 scripts/local-unified-flow-e2e.ts --prepare # đồng bộ authority on-ramp/tỷ giá
```

Lệnh `--prepare` còn tạo 2 Job QA bằng tài khoản seed; cần flag cutover đang bật.

**Bước 5 — Kiểm tra:** `curl -I http://localhost:8080` phải trả 200; `curl -i http://localhost:8080/api/v1/auth/me` trả 401 khi chưa đăng nhập là bình thường; RPC phải trả `"result":"ok"`:

```bash
curl -fsS -H 'Content-Type: application/json' --data '{"jsonrpc":"2.0","id":1,"method":"getHealth"}' http://127.0.0.1:9123
```

**Bước 6 — Dùng thử:** mở `http://localhost:8080` và làm theo [guide trình diễn](docs/business/BROWSER_DEMO_GUIDE.md). Tài khoản seed: Freelancer `freelancer.seed@example.com`, Admin `admin.e2e@example.test`, Client seed khai báo trong `marketplace-backend/.../configuration/DataInitializer.java`; mật khẩu lấy trong `.env`.

**Bước 7 — Chạy kiểm chứng tự động (tùy chọn):** lệnh ở [VERIFICATION](docs/business/VERIFICATION.md#chạy-lại-kiểm-chứng).

**Dừng:** `Ctrl+C` validator, rồi `docker compose --profile deploy stop`. Tránh `down -v` nếu muốn giữ dữ liệu.

## Những gì sẽ bổ sung

| Hạng mục | Ghi chú |
| --- | --- |
| Tiền thật và đối tác thật | On-ramp USD và off-ramp VND qua provider thật; cơ chế thu phí 3% và bằng chứng phí riêng; bảo đảm hoàn đủ USD |
| Solana devnet/production | Triển khai program, quản lý authority và nâng cấp, giám sát RPC |
| Ví và khóa cho người dùng thật | Cấp phát, bảo vệ, khôi phục ví; bỏ khóa demo ở backend |
| Chứng từ thuế thật | Kết nối MISA hoặc cơ quan thuế thật; Flyway cho `misa-backend` (hiện vẫn `ddl-auto: update`) |
| Sản phẩm | Nhiều Milestone trong một Job; chat làm bằng chứng tranh chấp; lịch ngày lễ cho hạn; tối ưu mobile |
| Vận hành | Cảnh báo cho khoản `UNKNOWN`/lệch kéo dài; báo cáo đối soát định kỳ theo từng loại tiền |
