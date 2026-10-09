# FreelaX — Ghi chú nâng cấp và xác minh escrow

> **Lưu trữ theo mốc ngày.** Xem [tổng quan hiện tại](../README.md) và [trạng thái kiểm chứng](../VERIFICATION.md) trước khi dùng các kết luận bên dưới.

**Ngày ghi:** 2026-10-08

**Phạm vi ban đầu:** đánh giá baseline sản phẩm P06 và đề xuất cho giai đoạn sau P06.

## Cập nhật trạng thái — nhánh escrow

Sau khi chốt baseline P06, track `feat/solana-milestone-escrow` đã bổ sung implementation escrow theo Milestone trên Solana và nối vào Gateway, Marketplace, frontend. Phạm vi code gồm vault PDA, funding có chữ ký Client, submission hash, review/revision, gia hạn một lần, dispute, mutual refund và release sau hạn review; Marketplace chỉ hoàn tất Job/refund sau khi xác minh chuyển token. Rail `SOLANA_ESCROW` có persistence và trạng thái riêng, tách khỏi ledger mô phỏng.

**Quyết định nghiệp vụ mới:** rail `PARTNER_ESCROW_MOCK` mô phỏng đối tác giữ USD, đổi và chi VND cho Freelancer hoặc hoàn USD cho Client; có sao kê và đối soát riêng, không trộn tiền với vault Solana. Phí FreelaX **3% giá Job, do Freelancer chịu, chỉ ghi nhận khi giải ngân thành công**; hoàn trước giải ngân không thu phí. Tỷ giá khóa lúc tạo lệnh chi, phí đối tác bằng 0 trong mock. Toàn bộ công thức và trạng thái ở [tài liệu luồng đối tác](PARTNER_ESCROW_BUSINESS_GAP_20261008.md).

**Implementation đã có trên nhánh escrow; chưa hoàn tất kiểm chứng E2E.** Local-validator E2E đã xác minh Client duyệt rồi release, mutual refund và permissionless release kèm Marketplace reconcile. Scheduler Marketplace tự khởi tạo timeout release, Admin dispute refund xuyên Marketplace, browser UI E2E và devnet demo vẫn mở. Vì vậy chưa tuyên bố đã deploy hoặc đang nhận tiền thật. Chi tiết: [Solana escrow implementation plan](SOLANA_ESCROW_IMPLEMENTATION_PLAN.md) và [kết quả E2E local](SOLANA_ESCROW_LOCAL_E2E_20261008.md).

Các mục bên dưới ghi “hiện tại/chưa có” là mô tả **baseline P06 tại thời điểm đánh giá**, không phản ánh implementation trên nhánh escrow. Đề xuất P06 chuyển USD thành USDC ở mục 1–3 là một hướng lịch sử, **khác luồng đối tác giữ USD→chi VND hiện được chọn để mô phỏng**. Các giới hạn về đối tác fiat, quyền giữ khóa, vận hành production và nghĩa vụ pháp lý vẫn cần được xử lý trước khi dùng tiền thật.

## Đọc nhanh: tiền ở đâu, ai giữ khóa, ai quyết định trả tiền?

Hãy hình dung một Job 500 USD: Client nạp tiền, Freelancer làm việc, rồi hệ thống trả Freelancer hoặc hoàn Client. Để gọi đây là **ký quỹ bằng tiền thật**, cần chứng minh tiền/token đã được nhận, đang nằm ở đâu, ai có thể di chuyển nó và điều kiện nào cho phép chuyển đi. Một dòng `SUCCEEDED` trong cơ sở dữ liệu không đủ để chứng minh những điều đó.

| Câu hỏi | Bản demo P06 tại thời điểm đánh giá | Hướng đề xuất khi dùng tiền thật |
| --- | --- | --- |
| **1. Tiền nằm ở đâu?** | Funding, release và refund chính là **ledger mô phỏng** của Payment Backend. `mock_onramp` chuyển **Mock USDC đã có sẵn** trong ví demo sang ví Client; không nhận USD thật, không phát hành USDC thật và không chứng minh dự trữ USD. Solana Invoice chuyển token trực tiếp từ Client sang Freelancer, không khóa trước trong escrow Job. Treasury trong chương trình hiện dùng cho off-ramp sau khi Freelancer có token. | Chọn đối tác nhận/giữ USD và đổi sang **USDC thật**; ghi rõ đối tác nào chịu trách nhiệm với USD trước khi đổi. Chỉ đánh dấu Job đã funding khi đúng lượng USDC đã vào **vault escrow riêng của Milestone** và giao dịch được xác nhận/đối soát. Vault giữ token cho tới khi release hoặc refund. Nếu dùng USDC do Circle phát hành, Circle công bố cơ chế dự trữ/đổi USD của **token USDC**; điều này không thay thế trách nhiệm lưu ký, đối soát và hoàn tiền của FreelaX/đối tác nhận USD. Không “cấp USDC” chỉ bằng cách tăng số dư nội bộ. |
| **2. Ai giữ quyền điều khiển?** | Program có các địa chỉ `admin`, `treasury_authority`, `rate_authority`, `oracle_authority` và `mock_onramp_authority`; repo chỉ xác định **quyền của địa chỉ ví**, không chứng minh cá nhân/tổ chức nào đang giữ khóa. Admin on-chain có thể đổi một số cấu hình và bật `paused`. Code hiện không có instruction đổi chính địa chỉ `admin`; cũng chưa có quy trình khôi phục khóa được chứng minh. Admin Marketplace xử lý tranh chấp là một quyền ở backend, khác với khóa admin on-chain. | Công bố sơ đồ chủ khóa và người thay thế; giữ upgrade authority, quyền cấu hình, treasury, oracle và quyền xử lý tranh chấp **tách biệt**. Dùng ví nhiều chữ ký cho quyền quản trị/treasury, khóa vận hành giới hạn quyền, giám sát giao dịch và kế hoạch ứng cứu. Khi nghi lộ khóa: dừng nhận khoản mới, khóa luồng chịu ảnh hưởng, đối soát số dư/giao dịch, thay các khóa còn có thể thay bằng quyền an toàn, rồi mới mở lại. Nếu mất khóa admin hiện tại mà không còn quyền nâng cấp an toàn, không thể hứa khôi phục bằng nút “pause”; phải thiết kế đường thay admin và kiểm thử trước khi vận hành thật. |
| **3. Khi Client im lặng hoặc có tranh chấp?** | Client có thể duyệt/yêu cầu sửa/mở tranh chấp. Với bản bàn giao mới nhất đủ điều kiện và không có dispute, scheduler tự duyệt khi hết hạn review cho khoản `<= 500 USD`; khoản `> 500 USD` có thêm 24 giờ. Tự duyệt chỉ tạo `RELEASE_PENDING`, worker mới thực hiện release mô phỏng. Khi tranh chấp, Admin Marketplace đã claim case có thể quyết định **release 100%** hoặc **refund 100%** kèm lý do; tiền vẫn chờ bước settlement xác nhận. | Giữ chính sách đơn giản này cho MVP có escrow thật: Client duyệt thì release; cả hai đồng ý hủy thì refund; khi tranh chấp thì **đóng băng tự giải ngân** và người phân xử độc lập quyết định release/refund toàn phần theo bằng chứng. Khi Client im lặng, chỉ release sau hạn đã công bố nếu có bàn giao hợp lệ và không có dispute. Smart contract phải biết bằng chứng bàn giao/hạn review, hoặc nhận một quyết định có thể xác minh theo quy tắc; backend không được tự ý rút vault. |

**Ranh giới của baseline P06:** chương trình P06 có `paused`, nhưng chưa có vault escrow Job để nút này “đóng băng tiền ký quỹ”. `paused` chặn các instruction của program; nó không tự thu hồi token đã chuyển hoặc ngăn chủ khóa treasury chuyển token từ ví treasury bằng Token Program. Quy tắc tự duyệt và Admin dispute đã tồn tại ở Marketplace P06, còn thực thi các quy tắc ấy bằng tiền thật/on-chain chưa có trong baseline đó. Các con số 500 USD và 24 giờ là chính sách demo P06; trước khi dùng tiền thật phải hiển thị điều khoản/hạn cho hai bên và kiểm thử đường dispute, mất khóa, timeout và đối soát. Muốn cam kết luôn đủ tiền để release/refund, phải đối soát định kỳ tổng nghĩa vụ chưa thanh toán với USDC thực có trong các vault, và chặn funding/release nếu đối soát sai lệch.

Tham chiếu về tài sản bảo chứng của **USDC thật**: [Circle USDC](https://www.circle.com/usdc), [Circle Transparency](https://www.circle.com/transparency). Quyền nâng cấp program trên Solana là một quyền riêng: [Solana Program Deployment](https://solana.com/docs/core/programs/program-deployment).

## 1. Kết luận về escrow tại baseline P06

Tại baseline P06, FreelaX **chưa có escrow on-chain cho Job/Contract/Milestone**. Cần phân biệt ba luồng đã tồn tại khi đó:

| Luồng | Hiện trạng | Ý nghĩa chính xác |
| --- | --- | --- |
| Funding trước khi Freelancer làm | Marketplace gọi Payment Backend và chỉ mở công việc sau khi funding `SUCCEEDED`; response gắn `simulation=true`. | Có điều kiện giữ quỹ ở mức **ledger mô phỏng/off-chain**, không chứng minh token hoặc tiền thật đang được khóa trong smart contract. |
| Release/refund theo nghiệm thu, hủy hoặc dispute | Marketplace điều phối Payment Backend; release/refund trả reference `sim-release-*`/`sim-refund-*` và `simulation=true`. | Quyết định nghiệp vụ và điều chỉnh ledger mô phỏng; không phải instruction giải ngân/hoàn token từ escrow on-chain. |
| Anchor Invoice/Off-ramp | `pay_invoice` chuyển token trực tiếp Client ATA → Freelancer ATA. `request_offramp` chuyển token Freelancer ATA → Treasury ATA để rút tiền. `cancel_invoice` chỉ hủy Invoice còn `Pending`. | Treasury phục vụ **off-ramp sau khi Freelancer đã nhận token**, không phải vault escrow giữ tiền trước khi làm. Không có instruction fund/release/refund escrow. |

Từ `funding` trong giao diện và API vì vậy không đồng nghĩa với ký quỹ on-chain. Không gọi luồng hiện tại là “escrow smart contract” hoặc “refund on-chain”.

## 2. Bằng chứng nguồn

- `marketplace-backend/src/main/java/com/marketplace/backend/service/FundingService.java`: FundingResponse đặt `simulation(true)`; funding thành công mới chuyển Milestone/Contract/Job sang trạng thái làm việc.
- `marketplace-backend/src/main/java/com/marketplace/backend/service/SettlementService.java`: primary release chỉ chấp nhận response `simulation=true` và `sim-release-*`.
- `marketplace-backend/src/main/java/com/marketplace/backend/service/ContractCancellationService.java`: refund chỉ chấp nhận response `simulation=true` và `sim-refund-*`.
- `payment-backend/src/main/java/com/payment/backend/service/impl/BofaCheckoutRefundServiceImpl.java`: refund cộng lại balance trong ledger của Payment Backend.
- `solana-stablecoin-payout/programs/invoice_payments/src/lib.rs`: danh sách instruction không có escrow fund/release/refund.
- `solana-stablecoin-payout/programs/invoice_payments/src/instructions/pay_invoice.rs`: SPL `transfer_checked` trực tiếp từ Client ATA sang Freelancer ATA.
- `solana-stablecoin-payout/programs/invoice_payments/src/instructions/cancel_invoice.rs`: chỉ đổi trạng thái Invoice `Pending` sang `Cancelled`, không chuyển token.
- `solana-stablecoin-payout/programs/invoice_payments/src/instructions/request_offramp.rs`: chuyển token Freelancer ATA sang Treasury ATA cho withdrawal.
- `docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md`: local demo; Job QA có primary release thành công nhưng on-chain `UNKNOWN`, off-ramp và tax `NOT_STARTED`.

Anchor test được chạy lại khi đánh giá: `anchor test --validator legacy` → **67 passing**. Kết quả này chỉ xác minh program trên local validator, không chứng minh escrow hoặc luồng Solana end-to-end của Marketplace.

## 3. Thứ tự nâng cấp được đề xuất tại thời điểm đánh giá P06 (lịch sử)

1. **Hoàn tất bằng chứng local:** khôi phục RPC và cấu hình signer/program, đối soát các transaction bằng cùng ID/reference, chạy một Job mới qua toàn bộ downstream. Chỉ công bố trạng thái nào đã được backend/chain xác nhận.
2. **Chứng minh trên devnet:** chốt Program ID, triển khai và kiểm tra Config; chạy Marketplace → Gateway → Anchor trên devnet; lưu signature, account state và kết quả đối soát. Mock token vẫn phải ghi đúng là mock token.
3. **Đo và kiểm thử chất lượng:** ghi compute units/chi phí theo instruction, kiểm thử RPC timeout, retry, duplicate và restart; rà soát quyền ký, quản lý khóa và bảo mật program.
4. **Chốt mô hình tiền thật trước khi triển khai:** nếu cần bảo đảm vốn cho Freelancer trước khi làm và refund on-chain, thiết kế vault/escrow gắn Contract/Milestone cùng instruction fund/release/refund và quy tắc dispute, rồi thay đổi Marketplace state machine và payment authority. Nếu giữ direct transfer, phải nói rõ không có bảo đảm funding on-chain trước khi làm và không có refund tự động từ program.
5. **Cập nhật demo và giao diện theo mô hình đã chọn:** Finance hiện đã hiển thị các trạng thái và bằng chứng. Devnet demo với signer backend có thể cần ít thay đổi frontend. Ví tự quản, người dùng ký transaction hoặc thanh toán thật sẽ cần luồng frontend riêng và sửa nhãn/điều kiện funding, release, refund.

Không coi `moneyStatus=SUCCEEDED` của primary release mô phỏng tại baseline P06 là bằng chứng Freelancer đã nhận token on-chain hoặc tiền ngân hàng. Tại thời điểm đóng baseline đó, track escrow chưa được triển khai hoặc phê duyệt; implementation được bổ sung sau P06 được theo dõi riêng trong kế hoạch ở trên.

## 4. Quyết định sản phẩm cho ba khoảng trống của track Solana

Các quyết định dưới đây xác định hành vi đích của track nâng cấp. Chúng **không mô tả tính năng đã triển khai** trong baseline P06.

| Vấn đề | Quyết định | Tác động frontend |
| --- | --- | --- |
| Client không funding | Hạn funding là **48 giờ từ lúc assign**. Nhắc Client sau 24 giờ. Sau 48 giờ, chỉ tự hủy khi backend đã xác minh **không có funding thành công và không còn lần gửi tiền `PENDING/PROCESSING/UNKNOWN`**. Nếu kết quả chưa rõ, giữ trạng thái chờ đối soát; không tự hủy và không cho Freelancer bắt đầu. Funding thất bại xác định có thể thử lại trước hạn. | **Có, nhỏ:** hiển thị hạn funding/trạng thái quá hạn hoặc đang đối soát và lý do hủy; không dùng đồng hồ FE để tự quyết định trạng thái. |
| Freelancer nộp trễ | **Chốt mới:** Freelancer được xin gia hạn một lần trước hạn gốc; Client phải chấp thuận hạn mới, tối đa 7 ngày sau hạn gốc. Nếu không được chấp thuận, hạn gốc vẫn có hiệu lực. Nếu không có submission hợp lệ đến hạn có hiệu lực, Marketplace khóa thao tác tiếp tục Job và nộp bài mới, giữ tiền chưa release/refund để xử lý tranh chấp; Admin quyết định release 100% hoặc refund 100%. Không tự chuyển tiền vì hết hạn. Quy tắc này áp dụng hạn bàn giao đầu tiên; revision đã được Client yêu cầu thuộc vòng xử lý riêng. | **Có:** form xin/chấp thuận gia hạn, hạn có hiệu lực, trạng thái quá hạn bị khóa và đường vào tranh chấp. FE chỉ hiển thị; backend là nơi kiểm tra hạn và khóa thao tác. |
| Chia tiền khi tranh chấp | MVP chỉ có hai kết quả: release **100%** cho Freelancer hoặc refund **100%** cho Client, kèm lý do và audit. **Không có partial release/refund**. Nếu sau này cần chia tiền, phải thiết kế thêm kế toán, vault transfer và UI số tiền. | **Không** cho chính sách hiện tại; FE/Admin đã dùng hai lựa chọn toàn phần. Escrow on-chain riêng vẫn cần UI ký và hiển thị giao dịch. |

Lưu ý triển khai funding timeout: kiểm tra trạng thái Payment/Solana tại server trước mọi auto-cancel; một response timeout hoặc RPC lỗi không chứng minh tiền chưa chuyển. Nếu funding thành công sát hạn, serialization/locking phải bảo đảm chỉ một transition thắng. Chính sách 48 giờ là lựa chọn sản phẩm mới, không phải giá trị có sẵn trong code.

Lưu ý triển khai gia hạn: yêu cầu và quyết định phải được lưu bất biến/idempotent; không cho duyệt sau khi đã có quyết định tiền hoặc tranh chấp. Scheduler và API submit phải dùng cùng khóa/kiểm tra thời gian tại server để không nhận submission sau khi khóa. Khóa thao tác trong ứng dụng không thể ngăn Freelancer làm việc ngoài nền tảng. Khi khóa, escrow/vốn được giữ nguyên cho tới khi có quyết định tranh chấp hợp lệ; không tự hoàn hoặc giải ngân.

## 5. Thiết kế escrow on-chain trong implementation sau P06

**Payment rail riêng `SOLANA_ESCROW`:** chỉ một Milestone/Job, một mint được cấu hình, release hoặc refund 100%; không chạy capture/release mô phỏng của Payment Backend cho cùng khoản tiền. Rail mô phỏng cũ được giữ tách biệt.

1. **Program:** tạo Escrow PDA theo Milestone ID và vault token account do PDA kiểm soát. Lưu Client, Freelancer, mint, amount, hạn có hiệu lực, review window, trạng thái, arbiter và bump. `fund` do Client ký chuyển đúng amount vào vault; Marketplace authority cùng ký để chỉ chấp nhận điều khoản Job hợp lệ. Gia hạn cần yêu cầu Freelancer và chấp thuận Client để cập nhật hạn on-chain một lần; `record_submission` lưu hash bản bàn giao do Freelancer ký trước hạn có hiệu lực và bắt đầu review window trên chain. Client được ký release sớm sau submission. Sau review deadline, khi không có revision/dispute on-chain, **bất kỳ người gọi nào** có thể gửi instruction giải ngân toàn phần từ vault cho Freelancer; backend scheduler chủ động gọi, Freelancer có thể gọi dự phòng. `refund_mutual` cần hai chữ ký; một bên có thể `open_dispute` trong cửa sổ hợp lệ; Admin không tham gia hợp đồng chỉ được resolve sau khi case mở, chọn release hoặc refund toàn phần, lưu hash quyết định. Terminal state chặn xử lý lại.
2. **Marketplace/Gateway:** bổ sung build/submit/read cho các instruction và account mới, verify mint, amount, participants, vault, signature và trạng thái chain. Contract chỉ ACTIVE sau khi vault đã funded; Job chỉ COMPLETED sau release on-chain được xác minh; refund chỉ thành công sau transfer về Client. Các kết quả `UNKNOWN` phải đối soát bằng cùng Escrow PDA/reference trước khi retry. Marketplace khóa thao tác Job sau hạn; program cũng từ chối submission sau hạn để không thể vòng qua backend. Khóa không tự rút token khỏi vault.
3. **Frontend:** Client/Freelancer liên kết ví để ký hành động thuộc quyền mình; Funding hiển thị vault/amount/transaction, Work Detail hiển thị yêu cầu gia hạn, hạn có hiệu lực, khóa quá hạn, review và dispute; Finance hiển thị trạng thái và explorer theo chain. UI không tự kết luận giao dịch thành công từ lần bấm nút.
4. **Kiểm thử và demo:** test signer sai, mint/amount sai, fund/release/refund lặp, tranh chấp, giao trễ sau gia hạn, RPC timeout và restart; chạy Job mới qua UI/API trên local validator, sau đó devnet. Dùng hai Job để chứng minh riêng nhánh release và refund, cùng signature và số dư vault trước/sau.

**Quyết định cho Client im lặng:** review deadline nằm trong Escrow account, được tính từ submission đã xác nhận on-chain. Sau hạn, instruction release không cần Client ký và chỉ thành công nếu không có revision/dispute on-chain. Solana không tự chạy instruction theo đồng hồ: backend scheduler hoặc Freelancer phải gửi giao dịch và trả phí. Chỉ công bố "đã giải ngân" sau khi đối soát giao dịch và số dư vault. Một dispute mới ghi ở database nhưng chưa lên chain không thể chặn giao dịch release; giao diện phải hiển thị rủi ro này. Chính sách hiện tại là review window do Job cấu hình (mặc định 72 giờ), cộng 24 giờ nếu milestone trên 500 USD.

## 6. Nghiệp vụ qua đối tác mock và giới hạn còn lại

Luồng USD→VND qua đối tác là **rail riêng** cho một Job/Milestone: (1) chốt yêu cầu nghiệm thu và phí 3% trước khi nhận việc; (2) Client nộp đủ USD, đối tác mock xác nhận và FreelaX đối soát rồi mới cho Freelancer làm; (3) sau bàn giao hợp lệ, Client duyệt hoặc hệ thống tự duyệt theo chính sách được công bố; (4) đối tác chốt tỷ giá khi nhận lệnh, chi phần Freelancer bằng VND và ghi nhận phí FreelaX; (5) nếu hoàn trước chi, trả đủ USD cho Client và phí FreelaX bằng 0. `RELEASE_PENDING`/`REFUND_PENDING` không đồng nghĩa `PAID`/`REFUNDED`.

Màn hình Admin so sánh sao kê đối tác mock với tổng nghĩa vụ ledger theo USD và hiển thị khoản lệch, khoản chờ, thời điểm cập nhật. Bằng chứng Solana không thay sao kê đối tác. Luồng mock có lệnh nhận/chi/hoàn, phí 3%, đối soát tổng, 3 ngày làm việc tự duyệt, 2 lần nhắc, 3 ngày thương lượng và hạn 5 ngày điều phối. Chưa có đối tác fiat thật, lịch ngày lễ, tự gom chat/bằng chứng tin nhắn hoặc nhiều milestone trong một Job. Xem [đối chiếu chi tiết](PARTNER_ESCROW_BUSINESS_GAP_20261008.md).
