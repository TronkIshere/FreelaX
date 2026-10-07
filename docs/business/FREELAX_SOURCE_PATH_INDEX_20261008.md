# FreelaX — Chỉ mục đường dẫn theo nghiệp vụ

Tài liệu này dùng để trả lời nhanh câu hỏi: **“Phần nghiệp vụ này nằm ở đâu trong repo?”**

## 1. Điểm bắt đầu nên đọc

1. `README.md` — mô tả sản phẩm/runtime hiện tại.
2. `docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md` — trạng thái frozen cuối cùng, known limitations và E2E proof.
3. `docs/mvp-functional-spec.md` — historical design/spec về business rules/aggregate/state machine; có đề xuất, không mặc định toàn bộ là API hiện hành.
4. `docs/business/FREELAX_SYSTEM_BUSINESS_FLOW_20261008.md` — luồng nghiệp vụ tổng thể.
5. `docs/business/FREELAX_SOLANA_BUSINESS_ROLE_20261008.md` — vai trò Solana theo nghiệp vụ.
6. `docs/business/FREELAX_IMPLEMENTATION_ISSUES_FIXES_20261008.md` — lịch sử lỗi và cách xử lý.

## 2. Job discovery và authoring

- `frontend/src/Jobs.tsx`
- `frontend/src/JobEditor.tsx`
- `frontend/src/Workflow.tsx`
- `frontend/src/jobDiscovery.ts`
- `frontend/src/ui/job-thumbnails/JobThumbnail.tsx`
- `marketplace-backend/docs/JOB_DISCOVERY_CONTRACT.md`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/JobServiceImpl.java`

## 3. Assignment → Contract → Milestone

- `frontend/src/Workflow.tsx`
- `frontend/src/ContractLifecycle.tsx`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/JobServiceImpl.java`

Business result cần kiểm tra: assignment tạo WorkContract + Milestone thật; Job không còn là legacy `contract:null`.

## 4. Funding

- `frontend/src/Funding.tsx`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/FundingService.java`

Business result: funding phải được server xác nhận trước khi Contract ACTIVE / Milestone FUNDED.

## 5. Submission / Review / Revision / Dispute

- `frontend/src/ContractLifecycle.tsx`
- `frontend/src/ContractDispute.tsx`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/ContractSubmissionService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/ContractDisputeService.java`

Business result: submission có evidence/version; Client review theo acceptance criteria; approve đưa milestone sang RELEASE_PENDING, không tự coi là paid.

## 6. Settlement / release

- `marketplace-backend/src/main/java/com/marketplace/backend/service/SettlementService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/SettlementDownstreamService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/scheduler/SettlementScheduler.java`
- `frontend/src/Finance.tsx`

Business result: primary money `SUCCEEDED` mới đưa Milestone RELEASED và Contract/Job COMPLETED; downstream vẫn độc lập.

## 7. Solana

- `solana-integration/`
- `solana-stablecoin-payout/`
- `solana-stablecoin-payout/docs/bao-cao-tich-hop-freelax-solana.md`
- `docs/business/FREELAX_SOLANA_BUSINESS_ROLE_20261008.md`

Business role: on-chain/stablecoin evidence + reconciliation, không thay Marketplace business authority.

## 8. Tax / certificate

- `frontend/src/Finance.tsx`
- `marketplace-backend/src/main/java/com/marketplace/backend/controller/TaxCertificateController.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/SettlementTaxService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/client/MisaBackendClient.java`
- `misa-backend/src/main/java/com/misa/backend/service/TaxEngineService.java`
- Tax routes nằm dưới `/finance/tax-records...`
- MISA integration nằm ở `misa-backend/` và Marketplace client/service liên quan.

Chỉ missing TaxRecord tại `GET /api/v1/marketplace/tax-records/jobs/{jobId}` với HTTP `404` hoặc frontend ApiError code `4010`, được `missingTax(...)` xử lý thành tax=null/taxError trống và “Chưa có chứng từ”, cùng bằng chứng độc lập tax chưa hoàn tất, mới là expected absence. Không miễn trừ arbitrary 404, read/fetch failure, CORS, 5xx hoặc JS errors.

## 9. Activity

- `frontend/src/Activity.tsx`
- `frontend/src/activityPresentation.ts`

Business role: notification ledger; không phải audit log đầy đủ.

## 10. Profile / Portfolio / Reputation

- `frontend/src/Account.tsx`
- `frontend/src/Profile.tsx`
- `frontend/src/Portfolio.tsx`
- `frontend/src/PublicProfile.tsx`

Business truth: `/auth/me` session identity và `/profiles/me` editable Marketplace profile là hai nguồn dữ liệu khác nhau.

## 11. Review hai chiều

- `frontend/src/ContractReviews.tsx`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/ContractReviewService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/scheduler/ContractReviewScheduler.java`

Business result: review chỉ mở sau completion/settlement đủ điều kiện; Client review Freelancer và Freelancer review Client; publication do server rule quyết định.

## 12. Final QA / handoff

- `docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md`
- `docs/ui/UI_VISUAL_POLISH_HANDOFF_20261006.md`
- `docs/ui/UI_VISUAL_POLISH_SESSION_LOG_20261006.md`
- `docs/ui/START_HERE_UI.md`
- `docs/ui/UI_DEVELOPMENT_MEMORY.md`
- `docs/ui/UI_IMPLEMENTATION_PROGRESS.md`
- `docs/ui/UI_POLISH_SPEC.md`
