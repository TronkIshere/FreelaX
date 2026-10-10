# Guide trình diễn luồng sản phẩm trên trình duyệt

Guide này đi qua luồng sản phẩm bằng giao diện: từ đăng Job, ký quỹ, bàn giao, **màn quyết định có nút lựa chọn và dòng đếm ngược tự duyệt**, **tranh chấp do Admin xử lý**, đến **rút VND và tải chứng từ thuế**. Mọi số tiền đều là mô phỏng (Mock USDC, đối tác USD/VND mô phỏng, MISA mô phỏng).

Thời gian: khoảng 15–20 phút cho nhánh chính, thêm 5 phút cho nhánh tranh chấp.

## 0. Chuẩn bị

1. Khởi chạy hệ thống theo [README gốc](../../README.md#khởi-chạy-từng-bước) (validator, Compose, bootstrap Mock USDC). Mở `http://localhost:8080`.
2. Môi trường đã chuyển đổi: `.env` có `PAYMENT_FLOW_CUTOVER_ENABLED=true`, nên Job mới tự đi luồng thống nhất.
3. Tài khoản:
   - **Client** và **Freelancer**: tự đăng ký mới ở `/register`, hoặc dùng tài khoản seed (mật khẩu trong `.env`: `DEMO_CLIENT_PASSWORD`, `DEMO_FREELANCER_PASSWORD`).
   - **Admin**: `admin.e2e@example.test`, mật khẩu `DEMO_ADMIN_PASSWORD` (chỉ có ở profile dev).
4. Ví demo local được kết nối tự động khi đăng nhập, cho cả tài khoản seed và tài khoản mới. Giao diện chỉ hiện **“Đã kết nối ví”**; không cần cài tiện ích ví, nhập khóa hoặc nạp SOL. Khóa ví mới được giữ mã hóa ở Marketplace; giao dịch demo do hệ thống ký cho đúng tài khoản đã đăng nhập.
5. Mở hai cửa sổ trình duyệt (hoặc một cửa sổ thường và một cửa sổ ẩn danh) cho Client và Freelancer. Mỗi tài khoản chỉ giữ một phiên đăng nhập; đăng nhập ở nơi khác sẽ đẩy phiên cũ ra.

> Muốn có ngay một Job đang chờ Client quyết định để trình diễn bước 4, chạy từ `solana-stablecoin-payout`:
> `ANCHOR_PROVIDER_URL=http://127.0.0.1:9123 ANCHOR_WALLET=~/.config/solana/id.json ./node_modules/.bin/ts-mocha -p tsconfig.json -t 600000 scripts/local-unified-timeout-e2e.ts`
> Lệnh in ra `jobId`. Job này dùng tài khoản seed và đã đi xong USD → USDC → vault → bàn giao.

## 1. Đăng Job và thống nhất điều khoản

1. **Client** → *Công việc* → *Đăng công việc*: nhập tiêu đề, mô tả, danh mục, ngân sách (ví dụ 12 USD), hạn bàn giao (ít nhất 24 giờ sau), một sản phẩm bàn giao và một điều kiện nghiệm thu. Bấm đăng.
2. Trang Job hiện điều khoản thanh toán bằng ngôn ngữ dễ hiểu: giá công việc, phí dịch vụ, số tiền dự kiến nhận và thời hạn duyệt.
3. **Freelancer** mở cùng Job, tick "Tôi đã đọc và đồng ý điều khoản…", bấm **Ứng tuyển**. Thao tác này dùng được hoàn toàn bằng bàn phím: Tab, Space, Enter.
4. **Client** → *Ứng tuyển* của Job → **Chọn Freelancer** → tick đồng ý điều khoản → **Xác nhận chọn**. Hai bên đã xác nhận cùng một bản điều khoản (cùng fingerprint).

## 2. Ký quỹ

1. Cả hai bên mở trang Job và thấy **Đã kết nối ví**. Nếu dịch vụ vừa khởi động lại, bấm **Thử lại** khi trạng thái chưa cập nhật. Không cần thao tác với tiện ích ví.
2. **Client** điền *Tài khoản ngân hàng Client* (nơi nhận hoàn tiền nếu hủy) → **Bắt đầu thanh toán** → **Xem và xác nhận thanh toán** → **Xác nhận thanh toán**.
3. Đợi đến khi thẻ **Tiền vào ví** chuyển xanh. Trong phần **Giữ tiền cho công việc**, bấm **Chuẩn bị giữ tiền** → tick đồng ý điều khoản → **Xác nhận giữ tiền**.
4. Job chuyển sang *Đang làm* sau khi khoản tiền được xác nhận. Freelancer thấy form bàn giao. Các thẻ thanh toán nối mũi tên từ đầu đến cuối; thẻ hoàn tất chuyển xanh.

Nếu đối tác chậm xác nhận thanh toán, trang hiển thị **Đang chờ** và chưa mở công việc.

## 3. Bàn giao

**Freelancer** → trang Job → khối *Gửi bàn giao*: nhập tóm tắt, tick sản phẩm và tiêu chí, dán URL HTTPS bằng chứng → **Gửi bàn giao**. Hệ thống tự xác nhận bằng ví đã kết nối.

## 4. Màn quyết định: nút lựa chọn và đếm ngược tự duyệt

**Client** mở trang Job. Khối **"Bản bàn giao mới nhất"** hiển thị bằng chứng, hạn review và dòng đếm ngược:

![Bản bàn giao mới nhất và dòng đếm ngược tới hạn review](images/01-ban-giao-dem-nguoc.png)

- *"Còn N phút đến hạn review"*: tính từ lúc bàn giao, đủ 72 giờ. Hết hạn mà Client chưa quyết định thì hệ thống **tự duyệt và release USDC**, không gia hạn thêm. Khi đó Freelancer cũng có nút **Nhận tiền sau hạn review**.

Ngay bên dưới là khối **"Quyết định của bạn"** với ba lựa chọn:

![Ba nút quyết định của Client](images/02-nut-lua-chon.png)

| Nút | Kết quả |
| --- | --- |
| **Duyệt bàn giao** | Xác nhận → USDC chuyển từ vault vào ví Freelancer; Job *Hoàn thành* |
| **Yêu cầu chỉnh sửa** | Nhập phản hồi và chọn tiêu chí/sản phẩm liên quan; tối đa 2 lần; đồng hồ review bắt đầu lại khi có bản mới |
| **Mở tranh chấp** | Nhập mã lý do và mô tả → **Xác nhận mở tranh chấp** → vault bị khóa, chờ Admin (xem bước 5) |

Để trình diễn nhánh chính, bấm **Duyệt bàn giao** → **Xác nhận duyệt**, rồi chuyển sang bước 6. Thời hạn 72 giờ là thật. Bằng chứng tự duyệt khi hết hạn được kiểm chứng bằng script `local-unified-timeout-e2e.ts` trên bản sao ledger, xem [VERIFICATION](VERIFICATION.md).

## 5. Nhánh tranh chấp (Admin quyết định)

1. **Client** bấm **Mở tranh chấp**, nhập lý do và mô tả:

   ![Form mở tranh chấp](images/03-mo-tranh-chap.png)

2. Sau khi ký, trang Job hiện **"Tranh chấp đang mở"**. Hai bên có thể bổ sung bằng chứng; tự duyệt và rút tiền đều bị chặn:

   ![Tranh chấp chờ Admin tiếp nhận](images/04-tranh-chap-cho-admin.png)

3. **Admin** → `/admin/disputes`: hàng đợi tranh chấp. Bấm vào mã lý do để mở hồ sơ:

   ![Hàng đợi tranh chấp của Admin](images/05-admin-hang-doi.png)

4. Bấm **Tiếp nhận hồ sơ** (bằng chứng của các bên bị khóa lại), rồi chọn **Release cho Freelancer** hoặc **Hoàn tiền cho Client**, nhập lý do và xác nhận:

   ![Admin chọn quyết định](images/06-admin-quyet-dinh.png)

   - *Release cho Freelancer*: USDC vào ví Freelancer, tiếp tục ở bước 6.
   - *Hoàn tiền cho Client*: USDC về ví Client. Client vào *Thanh toán* của Job → **Xem khoản hoàn tiền** → **Xác nhận hoàn tiền**. Đối tác hoàn đủ USD, phí 0.

## 6. Freelancer rút VND

**Freelancer** → *Thu nhập* → mở Job (hoặc `/finance?jobId=<id>`). Các thẻ thanh toán cho biết công đoạn nào đã hoàn tất và công đoạn nào đang chờ. Bấm **Xem số tiền sẽ nhận**, kiểm tra số VND thực nhận sau phí dịch vụ và thuế mô phỏng, rồi bấm **Xác nhận nhận tiền**. Khi tiền được chi, thẻ **Người làm nhận tiền** chuyển xanh và có liên kết **Xem chứng từ thuế**.

## 7. Xuất chứng từ thuế

Khoảng 30 giây sau khi đối tác xác nhận chi VND, MISA mô phỏng phát hành **chứng từ khấu trừ thuế** cho Freelancer. Cần bản code có [PR #7](https://github.com/TronkIshere/FreelaX/pull/7) (phát hành chứng từ cho luồng unified).

1. **Freelancer** → *Thu nhập* → tab **Chứng từ thuế** (`/finance/tax-records`): danh sách theo Job, kèm thu nhập chịu thuế và trạng thái:

   ![Danh sách chứng từ thuế](images/07-chung-tu-danh-sach.png)

2. Bấm **Xem chi tiết**: thu nhập chịu thuế, thuế đã khấu trừ, tỷ giá đã khóa (`LOCKED_PAYOUT_QUOTE`), ngày phát hành và ngày gửi cơ quan thuế. Bấm **Tải PDF** hoặc **Tải XML** để lấy file chứng từ:

   ![Chi tiết chứng từ và nút tải PDF/XML](images/08-chung-tu-chi-tiet.png)

   Ví dụ Job 5 USD: phí 0,15 USD; thu nhập chịu thuế 121.250 VND (4,85 × 25.000); thuế đã khấu trừ 12.125 VND; Freelancer thực nhận 109.125 VND. Job đã khóa khoản chi theo cách tính cũ (trước 2026-10-10) có cảnh báo riêng trên trang Job.

## 8. Admin xem tiền đang ở đâu

**Admin** → `/admin/unified-reconciliation`. Bốn ô đầu trang đếm flow theo nhóm **Cần xử lý / Đang chạy / Đã khớp đủ / Tất cả**; bấm vào một ô để lọc danh sách (mặc định mở nhóm Cần xử lý nếu có). Ba thẻ **"Tiền đang ở đâu"** tách riêng USD, USDC và VND (không quy đổi cộng chung). Mỗi flow là một dòng gọn với 4 chặng tiền (*Khớp / Đang chờ / Lệch / Chưa rõ*); ô tìm kiếm lọc theo mã Job hoặc flow. Bấm **Chi tiết** để xem mã đối soát, nguồn bằng chứng, mã tham chiếu, lịch sử quyết định và form ghi quyết định có audit:

![Tổng hợp theo đồng tiền](images/09-admin-tien-dang-o-dau.png)

## Chạy tự động thay vì bấm tay

| Mục đích | Lệnh (từ `frontend/`) |
| --- | --- |
| Toàn bộ nhánh chính và nhánh hoàn tiền với tài khoản mới | `node scripts/unified-new-account-e2e.mjs` |
| Từ chối ký ví, đối tác chậm, thao tác bằng bàn phím | `node scripts/unified-browser-edge-e2e.mjs` |
| Chụp lại các ảnh trong guide | `node scripts/demo-guide-screenshots.mjs <jobId chờ duyệt> <jobId để mở tranh chấp>` |

Trên WSL thiếu thư viện Chromium thì thêm `LD_LIBRARY_PATH=/tmp/freelax-browser-libs/usr/lib/x86_64-linux-gnu` (xem [VERIFICATION](VERIFICATION.md)).
