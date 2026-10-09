# FreelaX — nghiệp vụ hệ thống (bản final, local mock)

FreelaX là marketplace hai phía: **Client** thuê việc, **Freelancer** nhận việc. Hệ thống giữ hợp đồng, bàn giao, nghiệm thu và **tiền có bằng chứng** ở từng bước.

**Trạng thái (2026-10-10):** luồng thanh toán thống nhất `UNIFIED_USDC_PAYOUT` **đã triển khai ở phạm vi local mock**. Mọi gate trong [checklist](PAYMENT_FLOW_REBUILD_CHECKLIST.md) đều đạt, bằng chứng ở [VERIFICATION](VERIFICATION.md). Môi trường local đã chuyển đổi: Job mới chỉ đi luồng này, Job cũ giữ rail gốc. USD/VND là đối tác **mô phỏng**, USDC là **Mock USDC** trên Solana local validator. **Chưa** có tiền thật, đối tác thật hay devnet.

## Đọc tài liệu theo thứ tự

| # | Tài liệu | Đọc để biết |
| --- | --- | --- |
| 1 | File này | Sản phẩm làm gì, ai làm gì, quy tắc đã chốt |
| 2 | [Luồng tiền](PAYMENT_FLOW.md) | Tiền nằm ở đâu ở từng bước, bằng chứng nào, đối soát ra sao |
| 3 | [Solana trong FreelaX](SOLANA_ARCHITECTURE.md) | Vì sao dùng Solana, vault/ví/withdrawal hoạt động thế nào |
| 4 | [Điều khoản local](UNIFIED_FLOW_LOCAL_TERMS.md) | Con số và quy tắc cụ thể (ví dụ 100 USD) |
| 5 | [Guide trình diễn trên trình duyệt](BROWSER_DEMO_GUIDE.md) | Đi qua luồng sản phẩm bằng giao diện, có ảnh minh họa |
| 6 | [Kiểm chứng](VERIFICATION.md) | Đã test gì, lệnh nào, kết quả và ID |
| 7 | [Checklist](PAYMENT_FLOW_REBUILD_CHECKLIST.md) và [Runbook chuyển đổi](CUTOVER_RUNBOOK.md) | Tiêu chí hoàn thành; bật/tắt flag, rollback, khoản cũ đang dở |

Thư mục [archive](archive/README.md) chỉ giữ biên bản lịch sử. Khi có mâu thuẫn, các file trên là bản đúng.

## Ai làm gì

- **Client:** đăng Job có ngân sách, sản phẩm bàn giao và tiêu chí nghiệm thu; chọn Freelancer; nộp USD; ký chuyển USDC vào vault; duyệt, yêu cầu sửa hoặc mở tranh chấp; nhận hoàn tiền nếu hủy trước release.
- **Freelancer:** đọc điều khoản rồi ứng tuyển; chỉ bắt đầu làm khi vault đã có tiền; bàn giao kèm bằng chứng; nhận USDC sau khi được duyệt; ký rút USDC để nhận VND; nhận chứng từ khấu trừ thuế.
- **Admin:** xử lý tranh chấp (release toàn bộ cho Freelancer hoặc hoàn toàn bộ cho Client); theo dõi đối soát; hủy hợp đồng quá hạn ký quỹ khi Client đã nộp USD.
- **Đối tác on-ramp/off-ramp (mô phỏng):** xác nhận nhận USD, chuyển USDC, chi VND hoặc hoàn USD, phát hành sao kê độc lập.
- **MISA (mô phỏng):** phát hành chứng từ khấu trừ thuế PDF/XML cho Freelancer.

## Một Job đi từ đầu đến cuối

```text
Đăng Job ─► Hai bên xem cùng điều khoản (fingerprint) ─► Ứng tuyển ─► Giao việc
   ─► Client nộp USD ─► Đối tác xác nhận ─► USDC vào ví Client ─► Client ký vào vault
   ─► Freelancer làm và bàn giao ─► Client: Duyệt │ Yêu cầu sửa │ Mở tranh chấp
        (hết 72 giờ không quyết định ─► tự duyệt)
   ─► USDC release vào ví Freelancer ─► Freelancer ký withdrawal ─► Đối tác chi VND (trừ phí 3%)
   ─► MISA phát hành chứng từ thuế ─► Hai bên đánh giá nhau
Hủy trước release: vault ─► USDC về Client ─► treasury ─► hoàn đủ USD, phí 0
```

**Ba mốc khác nhau, không suy ra nhau:** `WORK_ACCEPTED` là quyết định về sản phẩm; `USDC_RELEASED` là token đã vào ví Freelancer; `VND_PAID` là đối tác xác nhận đã chi VND. Job hoàn tất khi USDC đã release; còn Finance chỉ ghi "đã nhận VND" khi có sao kê của đối tác.

## Quy tắc đã chốt

| Quy tắc | Giá trị |
| --- | --- |
| Hạn ký quỹ | 48 giờ từ khi giao việc. Quá hạn mà chưa có tiền thì tự hủy. Nếu Client đã nộp USD thì Admin hủy và hoàn USD |
| Thời hạn duyệt | **72 giờ** sau bàn giao hợp lệ, không gia hạn. Hết hạn mà Client im lặng thì tự duyệt và release |
| Gia hạn bàn giao | Một lần, tối đa **hạn gốc + 7 ngày**, trước bản bàn giao đầu tiên, chỉ khi Client duyệt on-chain |
| Số lần sửa | Tối đa 2 |
| Tranh chấp | Chặn tự duyệt và chặn rút tiền. Admin quyết định toàn phần cho một bên |
| Phí FreelaX | 3% giá Job, Freelancer chịu, thu khi đổi sang VND. Hủy trước release thì phí 0 |
| Tỷ giá (local) | 1 USD = 1 Mock USDC; 1 USDC = 25.000 VND, khóa khi rút |
| Chứng từ thuế | Phát hành sau khi đối tác xác nhận chi VND. Thu nhập = giá Job × tỷ giá đã khóa |

## Hệ thống đã làm được

- Tài khoản và vai trò (Client, Freelancer, Admin cấp riêng); profile, kỹ năng, portfolio, hồ sơ công khai.
- Job có sản phẩm bàn giao, tiêu chí nghiệm thu, hạn và số lần sửa. Freelancer tìm và ứng tuyển. Client chọn người. Mỗi Job có một hợp đồng và một Milestone.
- Luồng tiền thống nhất USD → USDC → vault → USDC → VND, có hoàn tiền; mỗi bước có bằng chứng riêng; đối soát 4 ranh giới; bước tiền tiếp theo bị chặn khi ranh giới trước còn lệch.
- Bàn giao có bằng chứng và phiên bản; duyệt, yêu cầu sửa, tranh chấp, tự duyệt khi hết hạn.
- Admin xử lý tranh chấp, xem đối soát, xem bảng "Tiền đang ở đâu" theo USD/USDC/VND, ghi quyết định có audit.
- Chứng từ khấu trừ thuế (PDF/XML) qua MISA mô phỏng; Finance, Activity; đánh giá hai chiều và reputation.
- Hồi phục khi đối tác hoặc RPC lỗi tạm thời, không chi trùng tiền; migration schema bằng Flyway.

## Giới hạn hiện tại

Mỗi Job chỉ có một Milestone. Chưa có chat để làm bằng chứng, chưa có lịch ngày lễ, chưa tối ưu hết cho mobile. Ví và khóa vẫn là loại demo cho local. Toàn bộ tiền là mô phỏng. Danh sách những gì sẽ bổ sung nằm ở [README gốc](../../README.md#những-gì-sẽ-bổ-sung).

## Mã nguồn theo nghiệp vụ

| Nghiệp vụ | Vị trí chính |
| --- | --- |
| Job, ứng tuyển, điều khoản | `frontend/src/Workflow.tsx`, `marketplace-backend/.../service/impl/JobServiceImpl.java` |
| Luồng tiền thống nhất | `marketplace-backend/.../service/PaymentFlowService.java`, `UnifiedUsdFundingService.java`, `UnifiedExitService.java`, `frontend/src/Funding.tsx`, `Finance.tsx` |
| Escrow, bàn giao, tranh chấp | `SolanaEscrowFundingService.java`, `SolanaEscrowTimeoutService.java`, `ContractSubmissionService.java`, `ContractDisputeService.java`, `frontend/src/ContractLifecycle.tsx`, `AdminDisputes.tsx` |
| Đối soát, tổng hợp | `UnifiedReconciliationService.java`, `UnifiedLedgerSummaryService.java`, `frontend/src/UnifiedReconciliation.tsx` |
| Chứng từ thuế | `service/impl/TaxCertificateServiceImpl.java`, `scheduler/UnifiedTaxCertificateScheduler.java`, `misa-backend/` |
| Đối tác mô phỏng | `payment-backend/.../service/UnifiedUsdOrderMockService.java`, `UnifiedFiatExitMockService.java` |
| Solana | `solana-stablecoin-payout/programs/invoice_payments/`, `solana-integration/` |
