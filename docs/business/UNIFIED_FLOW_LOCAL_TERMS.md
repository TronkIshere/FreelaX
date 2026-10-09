# Điều khoản mục tiêu cho local mock `UNIFIED_USDC_PAYOUT`

**Trạng thái: chủ sản phẩm đã chốt mẫu điều khoản local ngày 2026-10-09.** Server cung cấp cùng bản xem trước cho Client và Freelancer trước ứng tuyển/phân công, yêu cầu hai bên xác nhận cùng fingerprint và lưu dấu chấp nhận. Chưa có bằng chứng luồng này với tài khoản mới trên browser; Gate 0 vẫn chờ kiểm chứng đầy đủ. Không dùng các con số hoặc lời hứa dưới đây cho tiền thật. [Bằng chứng local](VERIFICATION.md#e2e-thống-nhất-trên-local-validator-2026-10-09) ghi phạm vi đã chạy.

## Snapshot phải hiện cho cả Client và Freelancer trước USD order

- Một Job có một Contract, một Milestone và một `paymentFlowId` bất biến. Rail của Job mới sau cutover là `UNIFIED_USDC_PAYOUT`; Job cũ giữ nguyên rail đã ghi.
- Network và mint Mock USDC được chụp khi đăng Job và nằm trong fingerprint hai bên xác nhận; nếu cấu hình mint đổi trước USD order thì order bị từ chối và phải đăng Job mới. Trước USD order, Client phải liên kết ví Solana (USDC on-ramp chỉ chuyển vào ví đã xác minh) và tài khoản ngân hàng (nhận hoàn USD); Freelancer liên kết ví trước khi Client ký escrow.
- `grossUsd` là giá Job. Quote local dùng 1 USD = 1 Mock USDC; đúng `grossUsd` USDC phải vào vault. Quote phải có mã, nguồn, thời điểm, hạn 15 phút, mint/network local validator và quy tắc làm tròn (USD 2 chữ số, USDC 6 chữ số). Quote hết hạn hoặc USDC vào vault thiếu một đơn vị nhỏ nhất thì không mở việc.
- Phí FreelaX do Freelancer chịu: `roundHalfUp(grossUsd × 3%, 0.01 USD)`. Escrow release toàn bộ USDC sang ví Freelancer. Sau withdrawal, mock partner giữ lượng USDC tương ứng phí vào treasury FreelaX và đổi phần còn lại ra VND. Chỉ ghi doanh thu khi **cả** payout VND và phần USDC phí về treasury đều có receipt độc lập; nếu payout đã chi mà phí chưa xác nhận, giữ trạng thái đối soát mở và không thu lần hai bằng một lệnh mới.
- Quote VND local dùng 1 USDC = 1 USD và 1 USD = 25.000 VND, khóa cùng payout ID khi withdrawal được xác nhận. VND làm tròn `HALF_UP` tới đồng. Retry giữ nguyên beneficiary, quote, phí, amount và idempotency key. Production cần quote/provider và bảo đảm giá thực tế riêng.
- Funding hết hạn sau 48 giờ từ lúc hai bên chốt Contract; mốc này lưu một lần trên `PaymentFlow.fundingExpiresAt` và được dùng cho cả kiểm tra server lẫn tham số escrow on-chain. Hạn giao hàng là snapshot Job; review window **cố định 72 giờ** sau bàn giao hợp lệ, **không có gia hạn 24 giờ cho khoản trên 500 USD** như rail cũ; tối đa 2 revision. Hết hạn funding không tự hủy Contract khi USD order đã mở và có thể đã nhận tiền (`USD_ORDER` khác `NOT_STARTED`/`FAILED`, hoặc quote chưa hết hạn); trường hợp đó phải được đối soát và xử lý hoàn tiền riêng. Gia hạn bàn giao theo program hiện có: Freelancer xin một lần, tối đa 7 ngày, Client duyệt on-chain (**chờ chủ sản phẩm xác nhận là điều khoản chính thức**). Dispute hợp lệ trước release chặn auto release. Sau hết review window và không có dispute, scheduler chỉ có thể gửi release on-chain rồi đối soát; quyết định không đồng nghĩa token đã chuyển. Admin chỉ resolve toàn phần về Client hoặc Freelancer ở MVP.
- Hủy trước release: vault hoàn đủ USDC về Client; mock partner hoàn đúng `grossUsd` về Client bằng sao kê USD riêng. Phí FreelaX = 0. Mock partner/FreelaX chịu toàn bộ phí mạng, đổi tiền và sai lệch tỷ giá trong local; nếu provider thật không bảo đảm đủ USD, phải thay đổi điều khoản và được hai bên chấp nhận **trước** funding. `USDC_REFUNDED` và `USD_REFUNDED` là hai mốc khác nhau.
- Job `COMPLETED` khi công việc được chấp nhận và USDC release đã xác minh. Finance `VND_PAID` chỉ sau provider payout receipt/sao kê đúng beneficiary, amount và reference. Review/tax không xác nhận payout.

### Ví dụ 100 USD trên local mock

| Mục | Giá trị |
| --- | ---: |
| Client nộp / số USD phải được xác nhận | 100,00 USD |
| On-ramp vào ví Client / escrow vault | 100,000000 Mock USDC |
| Freelancer nhận từ vault khi release | 100,000000 Mock USDC |
| Phí FreelaX giữ tại off-ramp | 3,000000 Mock USDC (giá trị quote 3,00 USD) |
| USDC được đổi cho Freelancer | 97,000000 Mock USDC |
| VND dự kiến đến Freelancer | 2.425.000 VND |
| Hủy trước release | 100,000000 Mock USDC về Client, tiếp theo 100,00 USD hoàn Client; phí FreelaX 0 |

## Điều kiện trước khi bật flag

Hiện `PAYMENT_FLOW_CUTOVER_ENABLED=false`. Bản ghi `PaymentFlow` được thiết kế ở trạng thái `DRAFT`, chưa có mint/quote đã khóa và không được phát lệnh tiền. Chỉ bật khi quote, on-ramp, Solana vault, withdrawal, payout/refund và đối soát có test và bằng chứng theo checklist. Mọi Job `PENDING`/`UNKNOWN` của P06, Solana-only và partner mock phải được xử lý trên rail gốc; không đổi `paymentRail` hoặc tạo withdrawal/quote mới bằng migration metadata.

**Quyết định cutover ngày 2026-10-09:** trong thời gian xây và kiểm thử luồng mới, giữ các rail cũ để xử lý Job hiện có và demo; chưa tắt funding của Job mới khi `UNIFIED_USDC_PAYOUT` còn ở trạng thái `DRAFT`. Sau khi luồng thống nhất qua các gate local mock, bật flag cho Job được tạo từ thời điểm cutover. Giao diện của những Job này chỉ hiển thị một hành trình thanh toán và không có bộ chọn ba rail. Job tạo trước thời điểm đó giữ rail và quyền xử lý theo phiên bản đã ghi, kể cả khi được phân công sau cutover.
