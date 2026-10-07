# FreelaX

FreelaX là marketplace hai phía dành cho Client và Freelancer, ghi nhận phạm vi công việc, hợp đồng, bàn giao, quyết định nghiệm thu, bằng chứng tài chính và đánh giá sau hoàn thành.

> **LOCAL MVP / DEMO — P06 UI COMPLETE / FROZEN.** Financial flows include simulation/local infrastructure. Đây không phải banking production, khai thuế production, bằng chứng chuyển tiền fiat thật hay triển khai Solana production.

Điểm bắt đầu hiện tại: [Final Freeze / Handoff — 2026-10-08](docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md). Product/source authority: `7fc31555b9cd3f50a23872401968ee67c5275f30`. P06.7 docs authority: `44987c7e16dcc785845adbf8d05df6be44bf25cd`. Các docs commit sau đó không thay đổi source authority này.

## Tài liệu nghiệp vụ & bàn giao

Đọc [Business documentation](docs/business/README.md) để hiểu hệ thống, luồng Client/Freelancer, contract/funding/review, vai trò Solana, các lỗi đã gặp và cách xử lý, cùng source path traceability. Bộ tài liệu diễn giải baseline P06 frozen, không mở thêm phạm vi sản phẩm.

## 1. Sản phẩm hiện đã triển khai — CURRENTLY IMPLEMENTED

Marketplace quyết định vai trò, quyền tham gia và trạng thái. UI dùng dữ liệu thật từ API; tính năng dưới đây chỉ khả dụng khi role, ownership và server state cho phép.

| Client | Freelancer |
| --- | --- |
| Đăng nhập theo vai trò; Overview | Đăng nhập theo vai trò; Overview |
| Tạo Job với category, skills, deliverables, acceptance criteria, deadline, review window và revision limit | Explore với category/skill/keyword/budget/application filters từ server |
| Sửa metadata của Job OPEN thuộc mình; điều khoản hợp đồng không sửa qua form này | Ứng tuyển; Applications; My Work |
| Xem hồ sơ ứng viên và chọn Freelancer; tạo WorkContract + Milestone | Chờ funding được xác nhận trước khi bàn giao contract-backed |
| Bank setup được mask sau lưu; funding mô phỏng Milestone | Bàn giao bằng chứng gắn scope IDs; lịch sử phiên bản; revision loop |
| Review bàn giao: Approve / Request Revision / Dispute theo eligibility | Xem phản hồi, quota revision và hạn review do server trả |
| Cancellation/refund theo trạng thái; dispute bằng chứng và xử lý Admin được bảo vệ quyền | Participant dispute/cancellation theo policy và server state |
| Finance, Tax evidence, Activity, Account/Profile, public partner profile có xác thực | Income/Finance, Tax evidence, Activity, Account/Profile, portfolio, public profile có xác thực |
| Đánh giá Freelancer sau hoàn thành/release khi có invitation | Đánh giá Client sau hoàn thành/release khi có invitation |

Profiles/skills/portfolio có persistence và kiểm soát version theo API. Reputation dùng review đã công bố từ server, không phải điểm UI tự tính. Admin dispute claim/resolution và review moderation được backend phân quyền; đăng ký Client/Freelancer không tự cấp quyền Admin.

Revision limit và review window là điều khoản thực tế; không mặc định mọi Job có cùng số vòng hay deadline. Khi hết hạn, scheduler server áp dụng chính sách review: <=500 USD đủ điều kiện tự duyệt tại hạn; >500 USD thêm 24 giờ grace trước khi tự duyệt nếu vẫn đủ điều kiện. UI không tự approve hoặc giải ngân bằng timer.

## 2. Vòng đời contract-backed hiện tại

`Client tạo Job → Freelancer ứng tuyển → Client chọn → WorkContract + Milestone → Client funding → Contract ACTIVE → Freelancer submit evidence → Client review`

- **Approve:** work decision → RELEASE_PENDING → scheduler settlement → primary release SUCCEEDED → Milestone RELEASED → Contract/Job COMPLETED.
- **Revision:** phản hồi gắn deliverable/acceptance-criterion IDs → phiên bản tiếp theo, trong quota server.
- **Dispute:** participant mở case khi đủ điều kiện → trusted Admin xử lý bằng chứng → release/refund được backend điều phối.
- **Cancellation/refund:** phụ thuộc trạng thái/decision; REFUND_PENDING chưa phải hoàn tiền thành công.
- **Trust close:** scheduler tạo invitation → hai bên gửi review → publication theo quy tắc server → public reputation. Review là post-completion feedback, **không phải điều kiện để hoàn tất release/payment**.

P06.7 đã kiểm chứng happy path bằng Job contract-backed thật, không dùng legacy `contract:null` làm proof và không INSERT/UPDATE DB để tạo trạng thái. Chi tiết IDs/timestamps nằm trong final handoff và P06.7 authority docs. Legacy records vẫn có đường hiển thị tương thích, không bị coi là modern contract.

## 3. Sự thật tài chính và giới hạn demo

**Primary Marketplace release** độc lập với **on-chain**, **off-ramp** và **tax/MISA**. Funding SUCCEEDED không đồng nghĩa release; Job COMPLETED không chứng minh ngân hàng đã trả tiền. Mỗi lớp cần bằng chứng/trạng thái riêng.

P06.7 primary release/review đã PASS. Downstream của Job QA vẫn: on-chain `UNKNOWN / ON_RAMP_AWAITING_RECONCILIATION`, off-ramp `NOT_STARTED`, tax `NOT_STARTED`. Solana RPC local không khả dụng trong regression đó; không có tuyên bố production stablecoin settlement hoặc chuyển khoản fiat thật.

TaxRecord chưa tồn tại có thể trả HTTP 404 / frontend ApiError code 4010. Chỉ endpoint `/api/v1/marketplace/tax-records/jobs/{jobId}`, được `missingTax(...)` xử lý thành tax=null, taxError trống và “Chưa có chứng từ”, mới là expected absence khi trạng thái tài chính xác nhận tax chưa hoàn tất. Không miễn trừ arbitrary 404, asset failure, JS exception, CORS hay 5xx. Existing ACCEPTED certificate PDF/XML download là bằng chứng riêng của record đó, không chứng minh Job QA mới đã có chứng từ.

Seed/demo accounts có thể dùng wallet/signer được cấu hình ở runtime local. Account đăng ký mới chưa được bảo đảm tự provision wallet/signer production-safe. Không có UI wallet balance/send/receive/Phantom/manual withdrawal.

## 4. Kiến trúc và ranh giới API

`Browser → React/TypeScript → same-origin /api/v1 → Marketplace`

Marketplace điều phối Payment, MISA, Solana Gateway/RPC ở backend. **Browser chỉ gọi Marketplace**, không gọi trực tiếp Payment/MISA/Solana Gateway/Solana RPC. Vite local proxy hiện trỏ localhost:9191; production-style local origin dùng Caddy :8080 với SPA fallback và /api/v1 proxy.

| Thành phần | Công nghệ / local host port | Trách nhiệm |
| --- | --- | --- |
| Frontend | React 18, TypeScript, Vite; Caddy :8080 | Role UI, workflow, evidence |
| Marketplace | Java 17, Spring Boot 3; :9191 | Public business facade, auth, permissions, orchestration |
| Payment | Java 17, Spring Boot 3; :9190 | Simulated funding/release/refund records |
| MISA | Java 17, Spring Boot 3; :9192 | Local/demo tax and PDF/XML certificate provider |
| Solana Gateway | Java 17, Spring Boot 3, SolanaJ; :9193 | Backend-only signing/RPC boundary |
| Anchor program | Rust/Anchor; local RPC :9123 | Existing local on-chain program; validator outside Compose |
| Data | MySQL 8.4 :3307; Redis 7 :6380 | Persistence/session support |

Compose host ports default to 127.0.0.1. Internal API keys, signing material and auth secrets belong in ignored runtime config, never VITE_* or Git. Keep the existing Marketplace JWT secret across container recreation to preserve session compatibility. This configuration is local-demo setup, not production wallet/key management.

## 5. Chạy local

Yêu cầu: Git, Docker/Compose; ignored root .env được điền từ .env.example. On-chain end-to-end cần validator/toolchain/program/chain bootstrap riêng. Không commit .env, passwords, JWTs, internal API keys, private keys hoặc seed phrases.

```bash
docker compose --profile deploy config --quiet
docker compose --profile deploy up -d --build
docker compose --profile deploy ps
```

Mở http://localhost:8080. Development frontend: chạy `npm ci`, `npm run dev` trong frontend (localhost:3000; giữ proxy Marketplace localhost:9191). Không reset dữ liệu hay validator để trình diễn bản frozen.

```bash
curl -I http://localhost:8080
curl -i http://localhost:8080/api/v1/auth/me
```

HTTP 401 UNAUTHENTICATED trước login là đúng; sau login /auth/me phải xác nhận đúng role. Demo credentials chỉ lấy từ local runtime config được phép, không xuất hiện trong docs. `docker compose --profile deploy down` dừng stack, không dùng -v nếu muốn giữ dữ liệu.

## 6. Frozen UI và validation

KINETIC EDITORIAL BRUTALISM: hard Ink borders, zero-blur offset shadows, cut-paper editorial grammar, semantic colors, deterministic graphics, restrained/reduced motion và keyboard focus. Desktop/laptop **1440/1024** đã validate; **mobile optimization deferred**.

**Prior accepted P06.7 evidence:** real modern contract E2E/review/reputation PASS; focused 99/99; full frontend 793/793, 22 files; production build PASS. P06.8 docs-only, không rerun tests/build. P06.8 compact read-only smoke: 22 checks PASS, unexpected JS/network/runtime errors 0, overflow 0, 4 handled missing-TaxRecord 404s; no business mutations/new screenshots.

Delayed review invitations now reconcile without hard reload (source 7fc3155). Time/focus triggers a read, never grants eligibility or advances financial state. Chi tiết trong [final handoff](docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md) và [session log](docs/ui/UI_VISUAL_POLISH_SESSION_LOG_20261006.md).

## 7. KNOWN NEXT-GENERATION WORK — ngoài freeze

- Production-safe wallet/signer provisioning và quản lý secrets.
- Production payment/off-ramp/tax-provider integration; không suy từ mô phỏng thành giao dịch thật.
- Public-network Solana operationalization và downstream reconciliation.
- Operational observability sâu hơn; bundle-size optimization (warning >500kB hiện non-blocking).
- Mobile optimization.
- Skill Verification work-sample assessment, GitHub/GitLab supporting evidence, scoped results/evidenceHash, possible Solana Attestation Service, async attestation, revocation/supersession và portability: **FUTURE / CONCEPT ONLY; PROPOSED_NOT_APPROVED; IMPLEMENTATION NOT_STARTED**. SAS integration chưa triển khai.

Không mở lại P06 để subjective polish. Chỉ sửa khi có reproducible functional/accessibility/security/privacy/source-contract/responsive regression hoặc yêu cầu sản phẩm mới được duyệt. Future work dùng **NEW explicit track/branch**. Không tự merge master hoặc tạo tag.
