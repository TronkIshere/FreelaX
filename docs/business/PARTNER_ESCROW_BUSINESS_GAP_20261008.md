# Đối chiếu đề xuất ký quỹ qua đối tác và nghiệp vụ FreelaX

**Ngày đối chiếu:** 2026-10-08. **Trạng thái:** phân tích và đề xuất, không mô tả tính năng đã triển khai hay chính sách đã được phê duyệt.

## Ranh giới các luồng tiền

Hệ thống hiện có ledger thanh toán **mô phỏng** trong Payment Backend và rail `SOLANA_ESCROW` giữ **token** trong vault PDA. Hai rail tách biệt; không dùng một giao dịch ở cả hai rail hoặc cộng hai số dư thành một khoản ký quỹ. E2E local đã xác minh một số đường chuyển mock token, không chứng minh nhận USD thật hay chi VND vào ngân hàng. Xem [kết quả E2E](SOLANA_ESCROW_LOCAL_E2E_20261008.md).

Luồng đối tác nhận USD, giữ tiền, đổi sang VND, chi trả/hoàn tiền theo lệnh FreelaX là **thiết kế mục tiêu chưa triển khai**. MVP có thể mock API và sao kê của đối tác; màn hình demo phải ghi rõ `Mô phỏng`, không hiển thị như giao dịch ngân hàng thật. Khi chọn đối tác thực, cần xác nhận phạm vi dịch vụ, loại tài khoản, quyền nhận/giữ ngoại tệ, đổi USD/VND, hoàn tiền và chi VND theo giấy phép và hợp đồng. Quy định về [dịch vụ trung gian thanh toán](https://vbpl.vn/TW/Pages/vbpq-toanvan.aspx?ItemID=167087) và [tài khoản đảm bảo thanh toán](https://vbpl.vn/nganhangnhanuoc/Pages/vbpq-toanvan.aspx?ItemID=168578) không tự chứng minh rằng một tài khoản USD bất kỳ của đối tác phù hợp cho luồng này.

## Luồng mục tiêu qua đối tác (mock ở MVP)

1. Client thanh toán USD cho đối tác. FreelaX tạo `FUNDING_PENDING`; chỉ sau xác nhận/sao kê của đối tác và đối soát đúng số tiền mới ghi `FUNDED/Đã ký quỹ` và cho Freelancer bắt đầu. Không coi lệnh gửi hoặc callback chưa xác minh là tiền đã nhận.
2. Freelancer bàn giao theo yêu cầu đã chốt. Client duyệt hoặc hệ thống tự duyệt theo thời hạn công bố, nếu không có tranh chấp hợp lệ.
3. FreelaX tạo lệnh giải ngân có mã idempotency. Đối tác trừ phí FreelaX theo chính sách đã công bố, đổi phần còn lại sang VND và chi trực tiếp cho tài khoản ngân hàng Freelancer. FreelaX chỉ ghi `PAID` sau khi đối tác xác nhận và đối soát; phí FreelaX chỉ được ghi nhận khi giải ngân thành công.
4. Với khoản được hoàn, đối tác hoàn từ số dư ký quỹ của khoản đó về Client. Không trừ phí FreelaX khi hoàn. Phí dịch vụ đối tác, tỷ giá, khoản không hoàn lại và bên chịu phí cần được thỏa thuận, hiển thị riêng trước khi nhận tiền.

## Đối chiếu với code

| Nhu cầu | Hiện trạng | Khoảng trống |
| --- | --- | --- |
| Bắt đầu làm sau funding | Rail Solana chỉ kích hoạt Job sau khi vault được xác minh; Payment Backend có ledger mô phỏng. | Chưa có xác nhận nhận USD từ đối tác và lệnh chi/hoàn qua đối tác. |
| Đối soát tiền ký quỹ | Finance hiển thị vault và giao dịch theo Job. | Chưa có sao kê đối tác, tổng nghĩa vụ còn giữ theo USD và chênh lệch toàn hệ thống. |
| Phí FreelaX tại giải ngân | `settle_milestone_escrow` chuyển toàn bộ token cho Freelancer. | Chưa có biểu phí nền tảng, phép tính net, bút toán phí lúc chi thành công và chứng từ liên quan. |
| Điều khoản nghiệm thu | Job bắt buộc có deliverables, acceptance criteria, hạn giao, review window; hợp đồng lưu snapshot; mặc định tối đa 2 vòng sửa. | Cần bảo đảm Client/Freelancer nhìn thấy và chấp nhận bản điều khoản trước khi funding; 3 vòng sửa chưa được hỗ trợ. |
| Client im lặng | Tự duyệt theo giờ liên tục: mặc định 72 giờ, thêm 24 giờ với milestone trên 500 USD. | Chưa tính ngày làm việc/ngày nghỉ và chưa nhắc duyệt 2–3 lần. |
| Tranh chấp | Có khóa giải ngân, bằng chứng, Admin quyết định release/refund 100%. | Chưa có giai đoạn tự thương lượng, hạn xử lý của điều phối, phân chia một phần; tin nhắn chưa được tự gom thành bộ chứng cứ. |
| Freelancer giao trễ | Có gia hạn một lần nếu Client duyệt; quá hạn không có submission thì khóa nộp và xử lý qua tranh chấp. | Client chưa có quyền hủy và hoàn tiền đơn phương theo điều kiện giao trễ. |
| Job lớn trả theo giai đoạn | Một Job tạo đúng một Milestone bằng toàn bộ ngân sách. | Chưa có nhiều milestone, nghiệm thu, funding, release/refund, phí và đối soát riêng theo từng giai đoạn. |

## Quy tắc sản phẩm đề xuất để thảo luận

- **Nghiệm thu:** giữ mức tối đa 2 vòng sửa cho MVP; yêu cầu đầu ra và tiêu chí đạt phải cụ thể, lưu snapshot bất biến khi giao Job. Thay đổi phạm vi sau funding phải được hai bên chấp thuận.
- **Tự duyệt:** đề xuất 3 ngày làm việc từ lúc bàn giao hợp lệ, nhắc Client 2 lần (sau 1 ngày và trước hạn 1 ngày làm việc). Cần chốt múi giờ, lịch nghỉ áp dụng và thời điểm bắt đầu lại đồng hồ sau mỗi lần sửa. Chỉ gửi lệnh chi khi không có tranh chấp; hiển thị `Đang giải ngân` đến khi đối tác xác nhận.
- **Tranh chấp:** đề xuất 3 ngày làm việc để hai bên thương lượng, sau đó điều phối quyết định trong 5 ngày làm việc dựa trên yêu cầu đã chốt, lịch sử bàn giao, file và tin nhắn liên quan. Đóng băng giải ngân ngay khi tranh chấp được ghi nhận ở hệ thống giữ tiền. Cần chốt quyền truy cập/chấp thuận sử dụng tin nhắn làm bằng chứng.
- **Giao trễ:** Client có thể yêu cầu hủy và hoàn nếu quá hạn có hiệu lực mà không có bản giao hợp lệ, không có gia hạn đã duyệt và không có giao dịch bàn giao đang chờ xác nhận. Nếu có bằng chứng xung đột, chuyển tranh chấp; không tự hoàn theo đồng hồ khi trạng thái tiền hoặc bàn giao còn chưa rõ.
- **Nhiều giai đoạn:** hợp lý cho Job lớn, nhưng để sau MVP. Mỗi giai đoạn cần số tiền, đầu ra, tiêu chí, hạn, review, trạng thái tiền và phí riêng; tổng giai đoạn phải bằng giá trị hợp đồng. Job chỉ hoàn tất khi tất cả giai đoạn đã quyết toán.

Các mốc **3/3/5 ngày** và 2 lần nhắc ở trên là gợi ý để thảo luận, **chưa được chốt hoặc lập trình**. Nếu đổi sang ngày làm việc, phải cập nhật đồng bộ backend, hiển thị và hạn lưu trên chain; hạn on-chain hiện tính theo giây/giờ liên tục.

## Đối soát cần demo

Màn hình quản trị mock nên hiển thị theo **từng loại tiền**: số dư đầu kỳ + tiền đã nhận - tổng tiền rời tài khoản cho giải ngân (gồm phần Freelancer và phí FreelaX) - hoàn tiền thành công = số dư cuối kỳ theo sao kê đối tác. So sánh số dư cuối kỳ với tổng nghĩa vụ chưa quyết toán trên ledger; chênh lệch phải bằng 0 hoặc có khoản chờ đối soát được giải thích bằng mã giao dịch. Hiển thị số lượng giao dịch `PENDING/UNKNOWN`, thời điểm sao kê, lần đối soát gần nhất và nguồn `MOCK`. Không cộng USD với VND theo một tỷ giá tùy ý để tạo cảm giác khớp giả.

## Trạng thái kiểm chứng

Code/kiểm thử hiện có mới chứng minh một số nhánh escrow token local. Scheduler tự gửi timeout release, Admin dispute refund xuyên Marketplace, browser UI E2E và devnet vẫn chưa được xác minh đầy đủ. Luồng đối tác fiat, phí FreelaX khi giải ngân, đối soát tổng và nhiều milestone chưa được triển khai; không dùng tài liệu này để tuyên bố các luồng đó đã chạy E2E.
