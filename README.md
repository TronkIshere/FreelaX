# FreelaX

**Thuê freelancer, trả bằng USDC, tiền do hợp đồng thông minh trên Avalanche giữ, không do nền tảng giữ.**

FreelaX là marketplace hai phía cho **Client** (thuê việc) và **Freelancer** (nhận việc). Hệ thống bao trọn vòng đời của một Job: đăng việc, thống nhất điều khoản, ký quỹ USDC trên **Avalanche C-Chain**, bàn giao, nghiệm thu hoặc tranh chấp, giải ngân, đổi sang VND và lập chứng từ khấu trừ thuế.

```text
USD của Client → USDC trong ví Client → Escrow trên Avalanche C-Chain → USDC trong ví Freelancer → VND + chứng từ thuế
```

## Ba điểm khác biệt

1. **Tự giải ngân, không ai chặn được.** Hết hạn duyệt 72 giờ mà Client im lặng, **bất kỳ ai** cũng gọi được `release()` và USDC đi thẳng vào ví Freelancer. Kể cả khi hợp đồng bị tạm dừng, tiền đến hạn vẫn giải ngân được. ([`release`](avalanche-contracts/src/FreelaXEscrow.sol#L209))
2. **Nền tảng không chạm được vào tiền.** Escrow là hợp đồng bất biến, không có hàm rút tiền đặc quyền. Backend chỉ ký điều khoản bằng EIP-712; admin, pauser, backend signer đều không chuyển được USDC đang ký quỹ.
3. **Mỗi bước tiền đều có bằng chứng độc lập.** Receipt và event trên chuỗi ở mức `finalized`, sao kê đối tác, chứng từ thuế. Một phản hồi HTTP thành công không bao giờ được tính là đã xong.

## Avalanche được tận dụng thế nào

| Điểm | Cách FreelaX dùng |
| --- | --- |
| **Hợp đồng giữ tiền, luật nằm trên chuỗi** | [`FreelaXEscrow.sol`](avalanche-contracts/src/FreelaXEscrow.sol) thi hành toàn bộ luật escrow: nạp, gia hạn, bàn giao, yêu cầu sửa, tranh chấp, giải ngân, phán quyết, hoàn tiền. Mọi hạn chót và bộ đếm đều kiểm bằng `block.timestamp` trên chuỗi |
| **EIP-712 thay cho đồng ký** | Backend ký điều khoản có cấu trúc (`FundAuthorization`, `SubmitAuthorization`, `RevisionAuthorization`) gắn chain id, địa chỉ hợp đồng, escrowId, nonce và deadline, nên **không replay được** sang escrow, chain hay hợp đồng khác. Freelancer không thể tự khởi động đồng hồ duyệt bằng bằng chứng mà marketplace chưa ghi nhận |
| **Hai bên tự thỏa thuận, không cần nền tảng** | Hoàn tiền (`mutualRefund`) và giải quyết tranh chấp (`mutualSettleDispute`) chỉ cần chữ ký EIP-712 của **cả Client và Freelancer**; ai cũng nộp được giao dịch |
| **Tách vai trò khóa** | Authorization signer, arbiter, relayer, pauser, admin là các khóa riêng; không vai trò nào vừa ký điều khoản vừa chuyển được tiền. Arbiter được snapshot lúc nạp, không ai gán lại được |
| **Finality nhanh, xác nhận theo `finalized`** | Một bước tiền chỉ được ghi nhận khi block đã `finalized`. Giao dịch tự ký trên Fuji: có receipt sau khoảng **3,6 giây**, `finalized` sau **8,5–12,2 giây** ([đo đạc](docs/avalanche-migration/phase-1/evidence/live/FUJI-OBS-e1-human-signed-run-20261105.md)) |
| **Xử lý đúng Continuous Execution (Helicon, ACP-194)** | Giao dịch được chấp nhận chưa chắc đã chạy: chỉ receipt `status = 1` từ đúng hợp đồng mới tính. `eth_getLogs` rỗng gần đầu chuỗi không bị coi là "không có sự kiện"; luôn đối chiếu receipt và đọc state `getEscrow()` |
| **USDC native của Circle** | Tài sản nhận diện theo `(chainId, địa chỉ hợp đồng)`, không theo tên. Token cố định lúc triển khai; USDC.e (bridged, cùng tên "USDC Coin") bị từ chối |
| **Event log và kiểm chứng công khai** | Mỗi chuyển trạng thái phát event. Gateway quét log theo cursor, chống trùng theo `(txHash, logIndex)`, đối chiếu với provider thứ hai cùng mạng |
| **Hệ sinh thái EVM** | Solidity + OpenZeppelin v5.7 (`AccessControl`, `ReentrancyGuard`, `EIP712`, `SafeERC20`), Foundry 1.8.7 (fuzz, invariant), Slither, web3j 6.1.0, MetaMask qua EIP-1193 |
| **An toàn mạng** | Chỉ chấp nhận chain **31337** (Anvil) và **43113** (Fuji); Mainnet và chain lạ bị từ chối ngay khi khởi động |

**Chuyển từ Solana sang Avalanche.** FreelaX ban đầu chạy trên Solana (Anchor/Rust) và đã chuyển hoàn toàn sang C-Chain: bỏ codec Borsh/Anchor tự viết, thay admin key "làm mọi thứ" bằng 5 vai trò tách khóa, thay việc backend ký auto-claim bằng `release()` permissionless, thay polling account state bằng event log. Chi tiết: [so sánh Solana và Avalanche](docs/avalanche-migration/02-solana-vs-avalanche.md).

**Sẵn sàng cho Fuji theo thiết kế bảo mật.** Script triển khai có chain guard, ghi manifest, và hướng dẫn xác minh trên Snowtrace ([deployments](avalanche-contracts/deployments/README.md)). Việc triển khai lên Fuji được thiết kế để **con người ký** bằng keystore hoặc ví phần cứng: backend không bao giờ giữ khóa Fuji, và gateway tự kiểm code, token, decimals của manifest trước khi chấp nhận.

## Chức năng

| Nhóm | Đã có |
| --- | --- |
| Tài khoản | Đăng ký Client hoặc Freelancer; Admin cấp riêng; profile, kỹ năng, portfolio, hồ sơ công khai; **liên kết ví EVM** bằng chữ ký EIP-191 |
| Job và hợp đồng | Job có sản phẩm bàn giao, tiêu chí nghiệm thu, hạn, số lần sửa; tìm việc, ứng tuyển; hai bên xác nhận **cùng một bản điều khoản** (fingerprint) trước khi giao việc |
| Ký quỹ | Client ký `approve` + `fund` bằng MetaMask; công việc chỉ mở khi escrow đã có tiền trên chuỗi |
| Bàn giao và nghiệm thu | Bàn giao có bằng chứng và phiên bản; Client **giải ngân / yêu cầu sửa (tối đa 2) / mở tranh chấp** với đồng hồ đếm ngược; gia hạn một lần ≤ 7 ngày, Client duyệt trên chuỗi |
| Tranh chấp | Mở tranh chấp khóa escrow; arbiter quyết định, hoặc hai bên tự thỏa thuận bằng hai chữ ký |
| Chi trả VND | **Non-custodial**: Freelancer tự ký chuyển USDC tới đối tác off-ramp; gateway chứng minh giao dịch từ receipt (đúng token, đúng một log `Transfer`) rồi mới hỏi trạng thái chi VND; tiến trình tự cập nhật |
| Chứng từ thuế | Khi escrow được giải ngân, tự lập chứng từ khấu trừ **TNCN 2% + GTGT 5%** theo cơ chế của Nghị định 117/2025, tải PDF/XML |
| Quản trị | Đối soát 4 ranh giới USD → USDC → escrow → người nhận → VND; bảng "Tiền đang ở đâu"; báo cáo escrow bị kẹt; quyết định Admin có audit |
| Độ tin cậy | Idempotency key cho mỗi bước tiền; 10 trạng thái giao dịch; `UNKNOWN` thì tra cứu lại, không gửi lệnh mới; chịu được sự cố RPC, node, đối tác, DB mà không chi trùng |

## Kiến trúc

```text
Trình duyệt (React/TypeScript, :8080) ── ví MetaMask (EIP-1193) ──────────────┐
      │  /api/v1                                                              │ approve, fund, release, submit...
      ▼                                                                       ▼
Marketplace Backend (:9191) ── Job, hợp đồng, PaymentFlow, đối soát      Avalanche C-Chain
      ├──► Avalanche Gateway (:9194) ── web3j: dựng, ký EIP-712, gửi, ──► FreelaXEscrow.sol + USDC
      │                                 theo dõi receipt, quét event       (Anvil 31337 / Fuji 43113)
      ├──► Payment Backend (:9190) ──── đối tác USD/VND, sao kê độc lập
      └──► MISA Backend (:9192) ─────── chứng từ khấu trừ thuế PDF/XML
MySQL (mỗi service một schema) · Redis · mọi cổng chỉ bind 127.0.0.1
```

Hợp đồng on-chain quyết định **tiền, trạng thái escrow, hạn, bộ đếm, phán quyết**; marketplace quyết định **nghiệp vụ, sổ cái, phí, thuế, thông báo**.

| Thư mục | Công nghệ | Vai trò |
| --- | --- | --- |
| `avalanche-contracts/` | Solidity 0.8.38, OpenZeppelin v5.7.0, Foundry 1.8.7 | Escrow, script triển khai có chain guard, manifest |
| `avalanche-integration/` | Java 21, Spring, web3j 6.1.0 | Gateway: cấp nonce qua DB, signer theo vai trò, tx tracker 10 trạng thái, log scanner |
| `marketplace-backend/` | Java 17, Spring Boot, Flyway | Nghiệp vụ chính, scheduler, đối soát, chi trả, thuế |
| `frontend/` | React, TypeScript, Vite | Giao diện; panel escrow chỉ hiện hành động mà trạng thái trên chuỗi cho phép |
| `payment-backend/` | Java 17 | Đối tác USD/VND, sao kê |
| `misa-backend/` | Java 17 | Chứng từ thuế |

## Kiểm thử

| Phần | Kết quả |
| --- | --- |
| Hợp đồng (Foundry: unit, fuzz, invariant) | 77 pass |
| Avalanche Gateway (Testcontainers + Anvil thật) | 122 test, 0 lỗi |
| Marketplace / Payment / MISA | 466 / 72 / 24 |
| Frontend (Vitest) | 32 file, 899 test |
| Toàn stack Docker Compose | Kịch bản `drive` (45 bước), `browser` (15 bước), `outage` (9 bước: sập gateway, RPC, chain, backend, DB): **PASS** ([evidence](docs/avalanche-migration/phase-2/evidence/compose-avalanche-20261112/)) |
| MetaMask thật | Liên kết ví, nạp escrow, giải ngân, theo dõi tới `finalized`: **PASS** ([evidence](docs/avalanche-migration/phase-2/evidence/LOCAL_SIMULATION-b7-metamask-20261120.json)) |
| Phân tích tĩnh | Slither, kết quả đã phân loại ([output](docs/avalanche-migration/phase-2/evidence/slither-escrow-2026-11-03.txt)) |

## Dành cho ban giám khảo

**Đọc theo thứ tự:** README này → [`FreelaXEscrow.sol`](avalanche-contracts/src/FreelaXEscrow.sol) (luật escrow) → [Luồng tiền](docs/business/PAYMENT_FLOW.md) → [Kiến trúc đích](docs/avalanche-migration/03-target-architecture.md).

**Chạy toàn bộ stack bằng một lệnh** (cần Docker, Git Bash và Node; Java, Foundry và Node của frontend chạy trong image, không cần cài):

```bash
bash docs/avalanche-migration/phase-2/compose-avalanche/start-dev.sh
```

Script dựng Anvil (chain 31337), triển khai escrow và USDC bằng Foundry, cấp vai trò cho gateway, rồi bật các service. Sẵn sàng khi in ra `stack left running`; mở `http://localhost:8080`. Cổng (127.0.0.1): 8080 giao diện, 9191 API, 9194 gateway, 8545 Anvil. Nếu 8080 bận: `FREELAX_HTTP_BIND=127.0.0.1:<port>`. Dừng: `bash docs/avalanche-migration/phase-2/compose-avalanche/stop-dev.sh`.

**Tài khoản:** Freelancer `freelancer.seed@example.com`, Admin `admin.e2e@example.test`; mật khẩu demo được sinh mỗi lần chạy, nằm trong `.avalanche-dev/env` (`DEMO_FREELANCER_PASSWORD`, `DEMO_ADMIN_PASSWORD`). Client: tự đăng ký tại trang Sign up.

**Ví MetaMask:** thêm mạng RPC `http://127.0.0.1:8545`, chain ID `31337`, dùng một account mới, rồi liên kết ví trong ứng dụng. Cấp AVAX và USDC thử nghiệm cho ví đó (thay `<ví>` và `<token>`; địa chỉ token nằm trong `avalanche-contracts/deployments/LOCAL_SIMULATION.json`):

```bash
docker exec fxav-anvil-1 cast rpc anvil_setBalance <ví> 0x56BC75E2D63100000 --rpc-url http://localhost:8545
docker exec fxav-anvil-1 cast send --unlocked --from 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 <token> "mint(address,uint256)" <ví> 1000000000 --rpc-url http://localhost:8545
```

Lệnh thứ nhất cấp 100 coin gốc của chain local để trả gas, lệnh thứ hai cấp 1.000 USDC (6 decimals). Hướng dẫn chạy chi tiết từng service: [local-runbook](docs/avalanche-migration/phase-2/local-runbook.md).

## Lộ trình

| Hạng mục | Hướng phát triển |
| --- | --- |
| Fuji công khai | Triển khai escrow lên Fuji do con người ký, xác minh mã nguồn trên Snowtrace |
| Trải nghiệm ví | Nạp bằng một chữ ký với EIP-2612 `permit` (USDC của Circle đã hỗ trợ); relayer trả gas cho người dùng |
| Sản phẩm | Nhiều milestone trong một Job (hợp đồng đã tách escrow theo milestone); chat làm bằng chứng tranh chấp; tối ưu mobile |
| Vận hành | Cảnh báo cho giao dịch `UNKNOWN` kéo dài; báo cáo đối soát định kỳ |

## Tài liệu

- [Nghiệp vụ hệ thống](docs/business/README.md) và [Luồng tiền](docs/business/PAYMENT_FLOW.md)
- [Nghiên cứu kỹ thuật Avalanche](docs/avalanche-migration/01-avalanche-technical-research.md), [so sánh Solana và Avalanche](docs/avalanche-migration/02-solana-vs-avalanche.md), [kiến trúc đích](docs/avalanche-migration/03-target-architecture.md)
- [Vòng đời giao dịch và đối soát](docs/avalanche-migration/phase-2/06-transaction-lifecycle-and-reconciliation-plan.md), [rà soát bảo mật](docs/avalanche-migration/security-review-2026-11-05.md)
- [Phạm vi và môi trường](docs/SIMULATION_SCOPE.md), [ADR 0001: chuyển Solana sang Avalanche](docs/adr/0001-solana-to-avalanche-migration.md)
- Trạng thái và quyết định: [PROJECT_STATUS](docs/avalanche-migration/PROJECT_STATUS.md)
