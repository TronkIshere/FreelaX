# Ký quỹ qua đối tác — nghiệp vụ MVP mock và khoảng trống còn lại

> **Lưu trữ theo mốc ngày.** Xem [tổng quan hiện tại](../README.md) và [trạng thái kiểm chứng](../VERIFICATION.md) trước khi dùng các kết luận bên dưới.

**Ngày đối chiếu:** 2026-10-08. **Trạng thái nhánh hiện tại:** API và giao diện đối tác **mock** đã được thêm cho nhận USD, sao kê, giải ngân VND, hoàn USD và đối soát. Phí FreelaX **3%** chỉ ghi nhận sau khi đối tác mock xác nhận chi; hoàn trước chi trả đủ USD. Các mốc 3 ngày tự duyệt, 2 lần nhắc, 3 ngày thương lượng và 5 ngày điều phối đã được áp dụng cho rail mock. Kiểm thử chạy thực tế và các giới hạn còn lại được ghi cuối tài liệu; không có giao dịch ngân hàng thật.

## Ranh giới các luồng tiền

Hệ thống hiện có ledger thanh toán **mô phỏng** trong Payment Backend và rail `SOLANA_ESCROW` giữ **token** trong vault PDA. Hai rail tách biệt; không dùng một giao dịch ở cả hai rail hoặc cộng hai số dư thành một khoản ký quỹ. E2E local đã xác minh một số đường chuyển mock token, không chứng minh nhận USD thật hay chi VND vào ngân hàng. Xem [kết quả E2E](SOLANA_ESCROW_LOCAL_E2E_20261008.md).

Luồng `PARTNER_ESCROW_MOCK` chạy trên một sổ sao kê mock riêng trong Payment Backend. Màn hình demo ghi rõ `Mô phỏng`, không hiển thị như giao dịch ngân hàng thật. Khi chọn đối tác thực, cần xác nhận phạm vi dịch vụ, loại tài khoản, quyền nhận/giữ ngoại tệ, đổi USD/VND, hoàn tiền và chi VND theo giấy phép và hợp đồng. Quy định về [dịch vụ trung gian thanh toán](https://vbpl.vn/TW/Pages/vbpq-toanvan.aspx?ItemID=167087) và [tài khoản đảm bảo thanh toán](https://vbpl.vn/nganhangnhanuoc/Pages/vbpq-toanvan.aspx?ItemID=168578) không tự chứng minh rằng một tài khoản USD bất kỳ của đối tác phù hợp cho luồng này.

**Phân công trách nhiệm mục tiêu:** Client nộp tiền và duyệt/đề nghị sửa; Freelancer làm và bàn giao; FreelaX chốt điều khoản, điều phối trạng thái và gửi lệnh; đối tác giữ tiền, xác nhận số dư, đổi/chi/hoàn; Admin xử lý tranh chấp theo bằng chứng và theo dõi đối soát. FreelaX không tự khẳng định giữ USD hoặc đã chuyển khoản VND khi chỉ có bản ghi nội bộ.

## Luồng mục tiêu qua đối tác (mock ở MVP)

1. Trước lúc giao Job, Client và Freelancer thấy cùng bản điều khoản: sản phẩm bàn giao, tiêu chí đạt, hạn, tối đa 2 vòng sửa, giá Job bằng USD và phí FreelaX 3% do Freelancer chịu khi giải ngân trên rail đối tác. Freelancer ứng tuyển theo điều khoản hiển thị; sau khi đã có đơn ứng tuyển, Client không thể sửa Job. Khi giao Job, hợp đồng lưu snapshot điều khoản.
2. Client thanh toán đủ giá Job bằng USD cho đối tác. FreelaX ghi `FUNDING_PENDING`; chỉ sau xác nhận/sao kê của đối tác và đối soát đúng số tiền mới ghi `FUNDED/Đã ký quỹ` và cho Freelancer bắt đầu. Không coi lệnh gửi hoặc callback chưa xác minh là tiền đã nhận.
3. Freelancer bàn giao theo yêu cầu đã chốt. Client duyệt, yêu cầu sửa hoặc mở tranh chấp. Chỉ tự duyệt sau hạn đã công bố nếu có bàn giao hợp lệ và không có tranh chấp.
4. FreelaX tạo lệnh giải ngân có mã idempotency. Đối tác dùng tỷ giá được chốt **tại thời điểm tạo lệnh** để đổi phần USD của Freelancer sang VND, chuyển thẳng vào tài khoản ngân hàng Freelancer và chuyển/ghi nhận phần phí FreelaX. Khi gửi lại cùng lệnh, tỷ giá và số tiền không đổi. FreelaX chỉ ghi `PAID` và doanh thu phí sau xác nhận đã chi thành công và đối soát.
5. Nếu được hoàn trước giải ngân, đối tác hoàn đủ số USD Client đã ký quỹ từ chính khoản đó. Không thu phí FreelaX. Nếu lệnh chi/hoàn đang `PENDING/UNKNOWN`, giữ tiền và trạng thái chờ đối soát; không gửi lệnh đối nghịch hoặc tuyên bố đã thanh toán.

## Quy tắc tiền đã chốt cho MVP mock

| Thành phần | Quy tắc |
| --- | --- |
| Giá Job / số Client nộp | `grossUsd`, bằng đúng số USD ký quỹ; không trừ phí FreelaX lúc nạp. |
| Phí FreelaX | `platformFeeUsd = round(grossUsd × 3%, 2)`; Freelancer chịu và được thông báo trước khi nhận Job. Chỉ ghi nhận sau giải ngân thành công. |
| Phần Freelancer | `freelancerUsd = grossUsd - platformFeeUsd`; `payoutVnd = round(freelancerUsd × lockedUsdVndRate, 0)`. Làm tròn USD đến cent, VND đến đồng. |
| Tỷ giá | Lấy tỷ giá đối tác tại lúc tạo lệnh chi, lưu nguồn/giờ/tỷ giá và khóa cho cùng idempotency key. MVP dùng tỷ giá **mô phỏng cố định có gắn nhãn**, không cam kết VND tại lúc funding. |
| Phí đối tác | Bằng 0 trong MVP mock. Khi có đối tác thật, loại phí, bên chịu phí và cách hoàn phải được chốt/hiển thị riêng; không gộp vào phí FreelaX 3%. |
| Hoàn tiền | Trước giải ngân thành công: `refundUsd = grossUsd`, `platformFeeUsd = 0`; không đổi sang VND để hoàn. |

Ví dụ **chỉ để minh họa công thức:** Job 100 USD, tỷ giá mock 25.000 VND/USD → phí FreelaX 3 USD, phần Freelancer 97 USD, chi 2.425.000 VND. Nếu hoàn trước giải ngân, Client nhận 100 USD và phí FreelaX bằng 0. Tỷ giá 25.000 không phải tỷ giá thị trường hoặc cấu hình production.

Lệnh giải ngân mock gồm hai phần của cùng một khoản `grossUsd`: phần Freelancer và phí FreelaX. Chỉ khi đối tác xác nhận hai phần theo hợp đồng demo mới tất toán nghĩa vụ ký quỹ và ghi nhận phí. Nếu sau này đối tác xử lý hai phần không đồng thời, kế toán phải theo dõi phần còn phải trả riêng; không ép số dư sao kê khớp giả.

## Trạng thái nghiệp vụ mục tiêu

| Trạng thái | Điều kiện vào | Hành động tiếp theo |
| --- | --- | --- |
| `PENDING_FUNDING` | Giao Job, chưa có tiền xác nhận | Client nộp USD; Freelancer chưa làm. |
| `FUNDING_PENDING` | Có lệnh/ghi nhận thanh toán chưa đối soát | Kiểm tra sao kê đối tác, không tự coi đã ký quỹ. |
| `FUNDED` | Đủ USD được xác nhận và đối soát | Job vào `IN_PROGRESS`; Freelancer làm và bàn giao. |
| `UNDER_REVIEW` | Bàn giao hợp lệ | Client duyệt, sửa hoặc tranh chấp; có hạn phản hồi. |
| `DISPUTED` | Tranh chấp được khóa ở bên giữ tiền | Dừng tự giải ngân; xử lý theo bằng chứng. |
| `RELEASE_PENDING` / `REFUND_PENDING` | Đã có quyết định, đối tác chưa xác nhận tiền đi | Retry/đối soát theo cùng mã lệnh; không quyết toán hai lần. |
| `PAID` / `REFUNDED` | Đối tác xác nhận tiền đến đích và đối soát | Tất toán nghĩa vụ; `PAID` → Job `COMPLETED` và phát sinh phí 3%; `REFUNDED` → Job `CANCELLED`, phí FreelaX 0. |

## Đối chiếu với code

| Nhu cầu | Hiện trạng | Khoảng trống |
| --- | --- | --- |
| Bắt đầu làm sau funding | Đối tác mock xác nhận `FUNDED`, Marketplace đối chiếu đúng danh tính/số tiền rồi mới chuyển Job sang `IN_PROGRESS`. | Chưa có nhận USD thật. |
| Đối soát tiền ký quỹ | Admin xem sao kê mock độc lập, nghĩa vụ ledger USD, chênh lệch và các milestone lệch; hệ thống chặn lệnh chi/hoàn mới khi lệch. | Chưa có sao kê ngân hàng thật; có thể thấy chênh lệch tạm thời giữa hai lần đồng bộ. |
| Phí FreelaX tại giải ngân | Rail đối tác mock lưu phí 3%, phần USD của Freelancer, tỷ giá khóa và VND chi; chỉ ghi ở settlement thành công. | Rail Solana không áp phí 3%. |
| Điều khoản nghiệm thu | Job bắt buộc có sản phẩm, tiêu chí, hạn; ứng tuyển khóa sửa Job và hợp đồng lưu snapshot; tối đa 2 vòng sửa. | Chưa có quy trình thay đổi phạm vi sau funding bằng đồng thuận hai bên. |
| Client im lặng | Rail đối tác tự duyệt sau 3 ngày làm việc theo múi giờ Bangkok và gửi 2 lần nhắc; rail cũ vẫn dùng giờ liên tục. | Chưa loại trừ ngày lễ công bố. |
| Tranh chấp | Đối tác mock đóng băng tiền, hai bên có 3 ngày làm việc để cùng đồng ý chi/hoàn toàn bộ; sau đó Admin có mốc quyết định 5 ngày làm việc; bằng chứng, quyết định và audit được lưu. | Chưa tự gom tin nhắn (hệ thống chưa có module chat), chưa chia tỷ lệ, chưa tự động cưỡng chế SLA Admin. |
| Freelancer giao trễ | Client có thể hủy và hoàn đủ USD qua rail mock nếu quá hạn, chưa có submission hợp lệ, chưa có payout/dispute. | Rail mock chưa có quy trình gia hạn hai chiều; rail Solana dùng quy tắc riêng. |
| Job lớn trả theo giai đoạn | Một Job tạo đúng một Milestone bằng toàn bộ ngân sách. | Chưa có nhiều milestone, nghiệm thu, funding, release/refund, phí và đối soát riêng theo từng giai đoạn. |

## Quy tắc sản phẩm đề xuất để thảo luận

- **Nghiệm thu:** giữ mức tối đa 2 vòng sửa cho MVP; yêu cầu đầu ra và tiêu chí đạt phải cụ thể, lưu snapshot bất biến khi giao Job. Thay đổi phạm vi sau funding phải được hai bên chấp thuận. Quy tắc 2 vòng đã có ở Job hiện tại, xác nhận điều khoản hai chiều trước funding cần hoàn thiện.
- **Tự duyệt:** rail mock dùng 3 ngày làm việc từ lần bàn giao hợp lệ gần nhất; nhắc sau ngày thứ nhất và thứ hai. Múi giờ `Asia/Bangkok`, chỉ bỏ Thứ Bảy/Chủ Nhật; chưa có lịch ngày lễ. Chỉ gửi lệnh chi khi không có tranh chấp.
- **Tranh chấp:** rail mock đóng băng tiền; hai bên có 3 ngày làm việc để đề xuất và đồng ý cùng nội dung chi/hoàn toàn bộ. Sau mốc đó Admin mới được tiếp nhận, `moderationDueAt` là 5 ngày làm việc từ lúc tiếp nhận. Admin xem yêu cầu chốt, lịch sử bàn giao, file và bằng chứng do hai bên gửi. Tin nhắn chưa được tự gom vì chưa có chat trong hệ thống.
- **Kết quả tranh chấp MVP:** thỏa thuận hai bên hoặc Admin chọn giải ngân **toàn bộ** theo phí 3% hoặc hoàn **toàn bộ** USD cho Client. Quyết định lưu lý do, audit và bằng chứng; một Milestone không chia tỷ lệ.
- **Giao trễ:** Client có thể yêu cầu hủy và hoàn nếu quá hạn có hiệu lực mà không có bản giao hợp lệ, không có gia hạn đã duyệt và không có giao dịch bàn giao đang chờ xác nhận. Nếu có bằng chứng xung đột, chuyển tranh chấp; không tự hoàn theo đồng hồ khi trạng thái tiền hoặc bàn giao còn chưa rõ.
- **Nhiều giai đoạn:** hợp lý cho Job lớn, nhưng để sau MVP. Mỗi giai đoạn cần số tiền, đầu ra, tiêu chí, hạn, review, trạng thái tiền và phí riêng; tổng giai đoạn phải bằng giá trị hợp đồng. Job chỉ hoàn tất khi tất cả giai đoạn đã quyết toán.

Các mốc **3/3/5 ngày** và 2 lần nhắc đã được lập trình cho rail mock. Chúng không đổi đồng hồ của Solana escrow; hạn on-chain vẫn tính theo giây/giờ liên tục. Nhiều milestone vẫn để sau MVP theo kế hoạch hiện hành.

## Đối soát cần demo

Màn hình quản trị mock phải hiển thị theo **từng loại tiền**: số dư đầu kỳ + tiền đã nhận - tổng tiền rời tài khoản cho giải ngân (gồm phần Freelancer và phí FreelaX) - hoàn tiền thành công = số dư cuối kỳ theo sao kê đối tác. So sánh số dư cuối kỳ với tổng nghĩa vụ chưa quyết toán trên ledger; chênh lệch phải bằng 0 hoặc có khoản chờ đối soát được giải thích bằng mã giao dịch. Sao kê đối tác mock là nguồn ghi nhận **độc lập** với ledger Marketplace để ca lệch có thể bị phát hiện. Hiển thị số lượng giao dịch `PENDING/UNKNOWN`, thời điểm sao kê, lần đối soát gần nhất và nguồn `MOCK`. Không cộng USD với VND theo một tỷ giá tùy ý để tạo cảm giác khớp giả.

Đối soát mẫu: sau funding 100 USD, đối tác mock giữ 100 USD và Marketplace còn nghĩa vụ 100 USD. Trong lúc lệnh chi/hoàn chưa rõ kết quả, hai vế vẫn là 100 USD. Sau chi thành công, đối tác ghi giảm tổng 100 USD, nghĩa vụ về 0; phí 3 USD chỉ được ghi nhận ở thời điểm đó. Một chênh lệch không giải thích được phải hiện cảnh báo và tạm dừng lệnh chi mới cho khoản liên quan đến khi xử lý.

## Điều kiện chứng minh E2E khi triển khai luồng đối tác mock

1. Client tạo Job với điều khoản nghiệm thu và phí 3%; Freelancer chưa thể nộp bài trước khi xác nhận đủ funding từ đối tác mock.
2. Job được funding và đối soát khớp; Client duyệt; một lệnh chi thành công tạo đúng một khoản phí 3%, một khoản VND cho Freelancer và hoàn tất Job. Gửi lặp cùng mã lệnh không tạo thêm tiền/phí.
3. Job khác được funding rồi hoàn; Client nhận đủ USD, phí FreelaX bằng 0, Job chỉ `CANCELLED` sau xác nhận hoàn.
4. Một lệnh chi/hoàn trả `UNKNOWN` giữ tiền và trạng thái chờ; không cho lệnh đối nghịch đến khi đối soát rõ.
5. Màn hình Admin hiển thị ca số dư khớp và ca lệch có cảnh báo; ghi nguồn `MOCK`, thời điểm sao kê và mã giao dịch chưa khớp.

Chứng từ thuế, review hai chiều và các tác vụ hậu xử lý đi sau `PAID/COMPLETED` theo phạm vi tương ứng; chúng không được dùng để suy ra rằng đối tác đã chi tiền nếu chưa có xác nhận chi.

## Trạng thái kiểm chứng

Luồng đối tác mock đã có mã nguồn và kiểm thử đơn vị cho tính phí, hoàn đủ USD, khóa tranh chấp, lịch ngày làm việc và settlement. Marketplace kiểm tra bút toán `FUND` trước khi ghi `FUNDED`, và bút toán `RELEASE`/`REFUND` trước khi chốt `PAID`/`REFUNDED`; sao kê thiếu hoặc lệch giữ giao dịch ở trạng thái chờ đối soát. Ngày 2026-10-09, Java compile và các test nhắm vào payment mock, settlement, cancellation, dispute, business-day clock và xác nhận sao kê đều qua trong Docker Maven. [HTTP E2E local](PARTNER_MOCK_LOCAL_E2E_20261009.md) đã qua các nhánh chi, hoàn và hồi phục sau `UNKNOWN` do Payment Backend tạm ngắt. Browser E2E với tài khoản seed đã qua funding → bàn giao → duyệt → chi và màn hình Admin đối soát khớp/lệch; Job, ứng tuyển và phân công của ca browser được chuẩn bị qua API. Tạo tài khoản mới rồi đi hết luồng trong browser chưa được tự động kiểm chứng. Chưa có giao dịch ngân hàng thật, chưa hỗ trợ nhiều milestone trong một Job và chưa tự gom tin nhắn làm bằng chứng. Không suy từ kiểm thử mock rằng rail fiat thật đã hoạt động.
