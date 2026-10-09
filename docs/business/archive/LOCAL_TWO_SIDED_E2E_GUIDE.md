# Guide E2E hai phía trên máy local

**Guide demo nhánh cũ, chưa phải luồng thanh toán thống nhất.** Guide này dùng giao diện tại **http://localhost:8080** để đi từ tạo tài khoản Client/Freelancer đến ký quỹ, bàn giao và chi trả. Luồng thử nghiệm dùng **đối tác mock USD → VND trực tiếp**: số dư USD, chuyển đổi VND và sao kê đều là dữ liệu mô phỏng; **không có bước USDC Client → escrow → USDC Freelancer → VND trên cùng Job**. Không nhập thông tin ngân hàng hoặc giấy tờ thật, và không có tiền thật rời tài khoản ngân hàng. Đặc tả luồng mới ở [PAYMENT_FLOW](../PAYMENT_FLOW.md) và các bước xây lại ở [checklist](../PAYMENT_FLOW_REBUILD_CHECKLIST.md).

## 1. Chuẩn bị

Làm theo [hướng dẫn chạy local trong README](../../../README.md#chạy-demo-local-theo-từng-bước): cấu hình `.env`, bật ứng dụng, rồi kiểm tra giao diện. Với luồng đối tác mock, cần frontend, Marketplace, Payment, MySQL và Redis; validator Solana chỉ cần cho bài kiểm tra Solana riêng ở cuối guide. Kiểm tra `docker compose --profile deploy ps`, rồi mở `http://localhost:8080/login`.

Dùng **hai cửa sổ trình duyệt tách phiên** (hai profile, hoặc một cửa sổ thường và một cửa sổ ẩn danh), một cho Client và một cho Freelancer. Nếu có tài khoản Admin demo, mở phiên thứ ba để xem đối soát.

## 2. Tạo và đăng nhập hai tài khoản

1. **Phiên Client:** vào `http://localhost:8080/register`, chọn **Client**. Nhập tên hiển thị, email thử nghiệm chưa dùng và mật khẩu ít nhất 6 ký tự; bấm **Tạo tài khoản**. Trang chuyển về `/login`; đăng nhập bằng email/mật khẩu vừa tạo.
2. **Phiên Freelancer:** vào `/register`, chọn **Freelancer**. Ngoài tên, email và mật khẩu, nhập mã số thuế, số giấy tờ định danh, quốc tịch, địa chỉ thuế, ngân hàng nhận và số tài khoản nhận. Chỉ dùng dữ liệu giả cho local, ví dụ `TEST-TAX-001`, `TEST-ID-001`, `VN`, `Địa chỉ demo`, `Vietcombank`, `987654321098`. Bấm **Tạo tài khoản**, rồi đăng nhập ở `/login`.
3. Kiểm tra menu sau đăng nhập: Client có đường đăng việc; Freelancer có **Khám phá**, **Ứng tuyển**, **Công việc của tôi**. Vai trò được lưu bởi Marketplace khi đăng ký.

**Cách chạy nhanh bằng tài khoản seed:** Client `nguyenhuutrong11133@gmail.com`, Freelancer `freelancer.seed@example.com`. Mật khẩu nằm trong `.env` local, tương ứng `DEMO_CLIENT_PASSWORD` và `DEMO_FREELANCER_PASSWORD`; không sao chép mật khẩu vào tài liệu hoặc ảnh chụp. Admin demo (nếu đã bật trong dev profile): `admin.e2e@example.test`, dùng `DEMO_ADMIN_PASSWORD`.

Tạo tài khoản ứng dụng và lưu ngân hàng mock **không tạo ví Solana mới**. Ví/signer local chỉ được cấu hình cho tài khoản seed; tài khoản đăng ký mới nên dùng rail đối tác mock trong guide này.

## 3. Client đăng việc, Freelancer ứng tuyển

1. **Client:** mở `/work/new` và tạo một Job thử nghiệm, chẳng hạn:

   | Trường | Giá trị demo |
   | --- | --- |
   | Tiêu đề | `E2E demo landing page` |
   | Danh mục | Một danh mục phù hợp trong danh sách |
   | Kỹ năng | `TypeScript` |
   | Ngân sách | `100` USD |
   | Hạn bàn giao | Ít nhất **48 giờ** từ lúc tạo |
   | Thời hạn review | `72` giờ |
   | Số lần chỉnh sửa tối đa | `2` |
   | Sản phẩm bàn giao | Tên `Trang demo`, mô tả `Một URL xem được` |
   | Điều kiện nghiệm thu | `URL bàn giao mở được` |

   Bấm **Đăng công việc**. Giữ URL `/work/<jobId>` để hai bên cùng mở đúng Job. Hệ thống yêu cầu hạn bàn giao ít nhất 24 giờ tới, một sản phẩm và một điều kiện nghiệm thu.
2. **Freelancer:** ở `/work` tìm Job theo tiêu đề, mở **Chi tiết & ứng tuyển**, đọc điều khoản và bấm **Ứng tuyển**. Trạng thái đổi thành **Đã ứng tuyển**.
3. **Client:** mở lại Job, bấm **Xem ứng viên**. Ở ứng viên vừa nộp, bấm **Chọn Freelancer** → **Xác nhận chọn**. Job chuyển sang `AWAITING_PAYMENT`; hợp đồng và milestone được tạo. Freelancer chưa thể bàn giao khi funding chưa được xác nhận.

## 4. Tạo tài khoản ngân hàng mock và ký quỹ

1. **Client:** trên trang Job, xuống khu vực funding; bấm **Chọn ký quỹ đối tác mock**. Bước chọn rail chỉ hiện khi hợp đồng chưa có lần funding nào.
2. Nếu chưa lưu ngân hàng Client, chọn ngân hàng, nhập số tài khoản giả gồm **6–34 chữ số** (ví dụ `123456789012`) và tên chủ tài khoản giả từ 2 ký tự, rồi bấm **Lưu tài khoản**. Sau khi lưu, màn hình chỉ hiển thị số tài khoản đã che. Tài khoản này là dữ liệu nhận diện trong mô phỏng, **không bị trừ tiền thật**.
3. Bấm **Gửi lệnh ký quỹ mock** **một lần**. Ban đầu có thể thấy **Đang chờ đối tác xác nhận và đối soát**. Chờ vài giây hoặc bấm **Đối soát lại**; chỉ đi tiếp khi hiện **Đã ký quỹ** và Job chuyển sang `IN_PROGRESS`.
4. **Freelancer:** tải lại Job. Khi đã `IN_PROGRESS`, form **Gửi bàn giao** mới mở.

Nếu chưa rõ kết quả sau khi gửi lệnh, dùng **Đối soát lại** hoặc tải lại trang để đọc trạng thái; tránh tạo lệnh funding mới cho cùng Job.

## 5. Bàn giao, duyệt và kiểm tra chi trả

1. **Freelancer:** trong **Gửi bàn giao**, nhập **Tóm tắt bàn giao**. Đánh dấu sản phẩm và điều kiện nghiệm thu đã đáp ứng, nhập URL bằng chứng thử nghiệm cho từng mục, rồi bấm **Gửi bàn giao**. Job chuyển sang `SUBMITTED_FOR_REVIEW`.
2. **Client:** tải lại Job, kiểm tra bằng chứng, bấm **Duyệt bàn giao** → **Xác nhận duyệt**. Ngay sau duyệt có thể thấy trạng thái chờ release; chờ scheduler và bấm **Đối chiếu release** hoặc tải lại trang.
3. Kết quả thành công: Job/Contract `COMPLETED`, release `SUCCEEDED`. Trong **Release hợp đồng** và `/finance`, đối chiếu số tiền gốc, **phí FreelaX 3%**, phần Freelancer nhận, tỷ giá khóa và VND đã chi mock. Với Job **100 USD** và tỷ giá mock **25.000 VND/USD**, kết quả dự kiến là phí **3 USD**, Freelancer **97 USD**, chi mock **2.425.000 VND**. Dùng tỷ giá thực tế hiển thị trên Job nếu cấu hình đã đổi.
4. **Freelancer:** mở `/finance` → Job vừa làm để đối chiếu trạng thái thu nhập và số VND mock. **Client:** mở cùng Job ở `/finance` để đối chiếu trạng thái thanh toán. Dòng **Mô phỏng** là dấu hiệu đây chưa phải bằng chứng chuyển khoản ngân hàng.
5. **Admin demo, tùy chọn:** đăng nhập phiên riêng, mở `/admin/partner-reconciliation`, bấm **Làm mới đối soát**. Kỳ vọng **Khớp** và **Chênh lệch 0 USD** nếu không có ca thử nghiệm khác đang treo. Màn hình này hiển thị số dư/ledger mock tổng hợp, không phải số dư tài khoản ngân hàng.

## 6. Kiểm tra hoàn tiền riêng

Tạo **Job thứ hai**, lặp lại bước ứng tuyển, chọn Freelancer và funding mock, rồi dừng **trước khi Freelancer bàn giao**. Một bên mở mục hủy trên Job, bấm **Đề nghị hủy hợp đồng**, nhập mã lý do và lý do, rồi **Xác nhận gửi đề nghị**. Bên kia tải lại Job, bấm **Đồng ý hủy** → **Xác nhận đồng ý hủy**. Chờ đối soát hoặc bấm **Đối chiếu hủy / Hoàn tiền**.

Chỉ coi ca hoàn tiền thành công khi `refundStatus = SUCCEEDED` và Job/Contract `CANCELLED`. Với rail đối tác mock, Client được hoàn đủ USD đã ký quỹ; phí FreelaX bằng **0**. `REFUND_PENDING` hoặc `UNKNOWN` vẫn là trạng thái chờ, chưa phải đã hoàn.

## 7. Kiểm thử tự động và ranh giới Solana

Nếu muốn máy tự tạo Job demo và kiểm tra giao dịch mock qua Marketplace ↔ Payment, chạy từ thư mục gốc:

```bash
node scripts/partner-mock-e2e.mjs
```

Script này dùng tài khoản seed và tạo **Job mới**, độc lập với Job làm tay. Để kiểm tra mock token **on-chain localnet**, làm theo [bước 2, 4 và 6 trong README](../../../README.md#chạy-demo-local-theo-từng-bước). Đó là rail `SOLANA_ESCROW` khác với ký quỹ đối tác mock, sử dụng Mock USDC trên validator local; **devnet chưa được triển khai**. Cả hai bài kiểm tra đều không chứng minh chuyển USD/VND ngân hàng thật.
