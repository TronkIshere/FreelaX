# Ký quỹ qua đối tác — luồng nghiệp vụ mục tiêu và khoảng trống hiện tại

**Ngày đối chiếu:** 2026-10-08. **Trạng thái:** quy tắc kinh doanh cho luồng đối tác đã chốt về phí FreelaX **3%**, thời điểm tính phí, tỷ giá và hoàn tiền; luồng đối tác **chưa được triển khai**. Các mốc ngày làm việc và nhiều milestone bên dưới vẫn là đề xuất.

## Ranh giới các luồng tiền

Hệ thống hiện có ledger thanh toán **mô phỏng** trong Payment Backend và rail `SOLANA_ESCROW` giữ **token** trong vault PDA. Hai rail tách biệt; không dùng một giao dịch ở cả hai rail hoặc cộng hai số dư thành một khoản ký quỹ. E2E local đã xác minh một số đường chuyển mock token, không chứng minh nhận USD thật hay chi VND vào ngân hàng. Xem [kết quả E2E](SOLANA_ESCROW_LOCAL_E2E_20261008.md).

Luồng đối tác nhận USD, giữ tiền, đổi sang VND, chi trả/hoàn tiền theo lệnh FreelaX là **thiết kế mục tiêu chưa triển khai**. MVP sẽ mock API và sao kê của đối tác; màn hình demo phải ghi rõ `Mô phỏng`, không hiển thị như giao dịch ngân hàng thật. Khi chọn đối tác thực, cần xác nhận phạm vi dịch vụ, loại tài khoản, quyền nhận/giữ ngoại tệ, đổi USD/VND, hoàn tiền và chi VND theo giấy phép và hợp đồng. Quy định về [dịch vụ trung gian thanh toán](https://vbpl.vn/TW/Pages/vbpq-toanvan.aspx?ItemID=167087) và [tài khoản đảm bảo thanh toán](https://vbpl.vn/nganhangnhanuoc/Pages/vbpq-toanvan.aspx?ItemID=168578) không tự chứng minh rằng một tài khoản USD bất kỳ của đối tác phù hợp cho luồng này.

**Phân công trách nhiệm mục tiêu:** Client nộp tiền và duyệt/đề nghị sửa; Freelancer làm và bàn giao; FreelaX chốt điều khoản, điều phối trạng thái và gửi lệnh; đối tác giữ tiền, xác nhận số dư, đổi/chi/hoàn; Admin xử lý tranh chấp theo bằng chứng và theo dõi đối soát. FreelaX không tự khẳng định giữ USD hoặc đã chuyển khoản VND khi chỉ có bản ghi nội bộ.

## Luồng mục tiêu qua đối tác (mock ở MVP)

1. Trước lúc giao Job, Client và Freelancer thấy cùng một bản điều khoản: sản phẩm bàn giao, tiêu chí đạt, hạn, tối đa 2 vòng sửa, giá Job bằng USD và phí FreelaX 3% do Freelancer chịu khi giải ngân. Điều khoản được chụp lại khi giao Job; không âm thầm sửa sau khi ký quỹ.
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
| Bắt đầu làm sau funding | Rail Solana chỉ kích hoạt Job sau khi vault được xác minh; Payment Backend có ledger mô phỏng. | Chưa có xác nhận nhận USD từ đối tác và lệnh chi/hoàn qua đối tác. |
| Đối soát tiền ký quỹ | Finance hiển thị vault và giao dịch theo Job. | Chưa có sao kê đối tác, tổng nghĩa vụ còn giữ theo USD và chênh lệch toàn hệ thống. |
| Phí FreelaX tại giải ngân | `settle_milestone_escrow` chuyển toàn bộ token cho Freelancer; rail này chưa trừ phí nền tảng. | Quy tắc 3% ở trên **chưa có trong code** của luồng đối tác mock; không áp sang rail Solana nếu chưa thiết kế lại. |
| Điều khoản nghiệm thu | Job bắt buộc có deliverables, acceptance criteria, hạn giao, review window; hợp đồng lưu snapshot; mặc định tối đa 2 vòng sửa. | Cần bảo đảm Client/Freelancer nhìn thấy và chấp nhận bản điều khoản trước khi funding; 3 vòng sửa chưa được hỗ trợ. |
| Client im lặng | Tự duyệt theo giờ liên tục: mặc định 72 giờ, thêm 24 giờ với milestone trên 500 USD. | Chưa tính ngày làm việc/ngày nghỉ và chưa nhắc duyệt 2–3 lần. |
| Tranh chấp | Có khóa giải ngân, bằng chứng, Admin quyết định release/refund 100%. | Chưa có giai đoạn tự thương lượng, hạn xử lý của điều phối, phân chia một phần; tin nhắn chưa được tự gom thành bộ chứng cứ. |
| Freelancer giao trễ | Có gia hạn một lần nếu Client duyệt; quá hạn không có submission thì khóa nộp và xử lý qua tranh chấp. | Client chưa có quyền hủy và hoàn tiền đơn phương theo điều kiện giao trễ. |
| Job lớn trả theo giai đoạn | Một Job tạo đúng một Milestone bằng toàn bộ ngân sách. | Chưa có nhiều milestone, nghiệm thu, funding, release/refund, phí và đối soát riêng theo từng giai đoạn. |

## Quy tắc sản phẩm đề xuất để thảo luận

- **Nghiệm thu:** giữ mức tối đa 2 vòng sửa cho MVP; yêu cầu đầu ra và tiêu chí đạt phải cụ thể, lưu snapshot bất biến khi giao Job. Thay đổi phạm vi sau funding phải được hai bên chấp thuận. Quy tắc 2 vòng đã có ở Job hiện tại, xác nhận điều khoản hai chiều trước funding cần hoàn thiện.
- **Tự duyệt:** đề xuất 3 ngày làm việc từ lúc bàn giao hợp lệ, nhắc Client 2 lần (sau 1 ngày và trước hạn 1 ngày làm việc). Cần chốt múi giờ, lịch nghỉ áp dụng và thời điểm bắt đầu lại đồng hồ sau mỗi lần sửa. Chỉ gửi lệnh chi khi không có tranh chấp; hiển thị `Đang giải ngân` đến khi đối tác xác nhận.
- **Tranh chấp:** đề xuất 3 ngày làm việc để hai bên thương lượng, sau đó điều phối quyết định trong 5 ngày làm việc dựa trên yêu cầu đã chốt, lịch sử bàn giao, file và tin nhắn liên quan. Đóng băng giải ngân ngay khi tranh chấp được ghi nhận ở hệ thống giữ tiền. Cần chốt quyền truy cập/chấp thuận sử dụng tin nhắn làm bằng chứng.
- **Kết quả tranh chấp MVP:** người điều phối chọn giải ngân **toàn bộ** theo công thức phí 3% hoặc hoàn **toàn bộ** USD cho Client; không chia một khoản theo tỷ lệ khi chỉ có một Milestone. Quyết định phải lưu lý do và bằng chứng, nhưng chưa có quy trình thương lượng/thời hạn tự động trong code.
- **Giao trễ:** Client có thể yêu cầu hủy và hoàn nếu quá hạn có hiệu lực mà không có bản giao hợp lệ, không có gia hạn đã duyệt và không có giao dịch bàn giao đang chờ xác nhận. Nếu có bằng chứng xung đột, chuyển tranh chấp; không tự hoàn theo đồng hồ khi trạng thái tiền hoặc bàn giao còn chưa rõ.
- **Nhiều giai đoạn:** hợp lý cho Job lớn, nhưng để sau MVP. Mỗi giai đoạn cần số tiền, đầu ra, tiêu chí, hạn, review, trạng thái tiền và phí riêng; tổng giai đoạn phải bằng giá trị hợp đồng. Job chỉ hoàn tất khi tất cả giai đoạn đã quyết toán.

Các mốc **3/3/5 ngày** và 2 lần nhắc ở trên là gợi ý để thảo luận, **chưa được chốt hoặc lập trình**. Quy tắc **phí 3%, chỉ thu lúc chi thành công, hoàn đủ USD, tỷ giá chốt lúc tạo lệnh và phí đối tác bằng 0 ở mock** đã được chốt về nghiệp vụ nhưng **chưa được lập trình**. Nếu đổi sang ngày làm việc, phải cập nhật đồng bộ backend, hiển thị và hạn lưu trên chain; hạn on-chain hiện tính theo giây/giờ liên tục.

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

Code/kiểm thử hiện có mới chứng minh một số nhánh escrow token local. Scheduler tự gửi timeout release, Admin dispute refund xuyên Marketplace, browser UI E2E và devnet vẫn chưa được xác minh đầy đủ. Luồng đối tác fiat mock, phí FreelaX 3% khi giải ngân, đối soát tổng và nhiều milestone chưa được triển khai; không dùng tài liệu này để tuyên bố các luồng đó đã chạy E2E.
