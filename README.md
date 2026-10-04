# FreelaX

FreelaX là marketplace hai phía dành cho Client và Freelancer, kết nối toàn bộ vòng đời công việc từ đăng việc, ứng tuyển, giao việc, bàn giao sản phẩm, phản hồi, phê duyệt đến thanh toán và chứng từ thuế.

Mục tiêu của FreelaX không phải thay thế mọi hình thức làm việc trực tiếp. Nền tảng đóng vai trò là **lớp tin cậy cho giao dịch giữa những người chưa biết nhau**: yêu cầu công việc có trạng thái rõ ràng, quá trình bàn giao có bằng chứng, thanh toán có thể truy vết và dữ liệu tài chính được nối với chứng từ.

> Trạng thái hiện tại: bản local demo đã hoàn thiện luồng công việc cốt lõi cho hai vai trò. Thanh toán, stablecoin, off-ramp và chứng từ trong MVP phục vụ mô phỏng/đối chứng trên môi trường local, không đại diện cho giao dịch tiền thật.

## 1. Bài toán sản phẩm

Khi Client và Freelancer làm việc trực tiếp qua email, tin nhắn hoặc mạng xã hội, họ thường gặp những vấn đề sau:

- Yêu cầu ban đầu và tiêu chí nghiệm thu không được ghi nhận rõ ràng.
- Client có thể nhận sản phẩm nhưng trì hoãn thanh toán.
- Freelancer có thể trễ hạn, bỏ việc hoặc bàn giao không đạt yêu cầu.
- Việc chỉnh sửa dễ kéo dài vì không có giới hạn và cơ chế xử lý bất đồng.
- Không có lịch sử uy tín đủ đáng tin cậy để đánh giá đối tác.
- Thanh toán xuyên biên giới, payout và chứng từ thuế nằm ở nhiều hệ thống rời rạc.

FreelaX hướng tới việc biến một thỏa thuận làm việc thành quy trình có thể kiểm chứng:

```text
Thỏa thuận công việc
        ↓
Phạm vi, ngân sách và người thực hiện được xác định
        ↓
Sản phẩm được bàn giao theo từng trạng thái
        ↓
Client phê duyệt hoặc phản hồi có căn cứ
        ↓
Thanh toán, payout và chứng từ được ghi nhận
```

Nền tảng không thể đảm bảo mọi dự án đều thành công. Điều FreelaX cần đảm bảo là hai bên có quy tắc công bằng, bằng chứng đầy đủ và một đường xử lý rõ ràng khi công việc không diễn ra như mong đợi.

## 2. Phạm vi hệ thống hiện tại

### Client

Client có thể đăng nhập, theo dõi tổng quan, tạo và quản lý công việc, xem ứng viên, chọn Freelancer, review sản phẩm, yêu cầu chỉnh sửa, phê duyệt hoàn thành và theo dõi bằng chứng thanh toán.

### Freelancer

Freelancer có thể đăng nhập, khám phá công việc, ứng tuyển, theo dõi công việc được giao, nộp sản phẩm, nhận phản hồi, nộp lại phiên bản chỉnh sửa và xem thu nhập cùng chứng từ thuế liên quan.

### Tài khoản demo và wallet

Local demo sử dụng một Client và một Freelancer được seed sẵn để trình bày đầy đủ hành trình. Marketplace gắn Solana public key đã cấu hình cho hai tài khoản này; private signer chỉ tồn tại trong cấu hình runtime local và không được lưu vào source code.

Người dùng vẫn có thể đăng ký tài khoản mới, nhưng tài khoản mới hiện chưa được tự động cấp wallet/signer để chạy trọn luồng tài chính. Đây là giới hạn cần giải quyết trước khi mở rộng ngoài phạm vi demo.

## 3. Luồng nghiệp vụ

### 3.1 Đăng ký và xác thực

Người dùng đăng ký dưới vai trò Client hoặc Freelancer. Marketplace là nguồn xác thực vai trò duy nhất; giao diện không cho phép tự chuyển vai trò sau khi đăng nhập.

Freelancer cung cấp thêm thông tin thuế và tài khoản nhận tiền để phục vụ payout và chứng từ. Sau khi đăng nhập, phiên làm việc được khôi phục bằng refresh cookie và thông tin tài khoản được lấy lại từ Marketplace.

### 3.2 Tạo việc, ứng tuyển và giao việc

```text
Client tạo công việc
        ↓
Công việc được mở trên marketplace
        ↓
Freelancer khám phá và ứng tuyển
        ↓
Client xem danh sách ứng viên
        ↓
Client chọn một Freelancer
        ↓
Công việc chuyển sang trạng thái đang thực hiện
```

Client chỉ quản lý các công việc do mình tạo. Freelancer chỉ có thể thao tác trên ứng tuyển và công việc thuộc về mình. Các ràng buộc vai trò được kiểm tra ở Marketplace, không phụ thuộc vào giao diện trình duyệt.

### 3.3 Bàn giao và review

```text
Freelancer nộp phiên bản đầu tiên
        ↓
Client review
        ├── Phê duyệt → Công việc hoàn thành
        └── Yêu cầu chỉnh sửa
                    ↓
             Freelancer đọc phản hồi
                    ↓
             Nộp phiên bản tiếp theo
                    ↓
                 Client review lại
```

Mỗi lần nộp và phản hồi đều tạo ra dấu vết nghiệp vụ để hai phía theo dõi. Tuy nhiên, phiên bản hiện tại chưa có giới hạn số vòng chỉnh sửa, thời hạn bắt buộc Client phải review, quyền hủy đầy đủ hoặc quy trình tranh chấp.

### 3.4 Thanh toán, payout và chứng từ

Sau khi Client phê duyệt công việc, Marketplace điều phối các lớp Payment, Solana và MISA để hình thành chuỗi bằng chứng tài chính:

```text
Job được phê duyệt
        ↓
Payment xác nhận capture/thanh toán mô phỏng
        ↓
Solana Gateway ghi nhận invoice, payout và off-ramp trên local runtime
        ↓
MISA ghi nhận dữ liệu thuế và tạo chứng từ
        ↓
Client/Freelancer xem lại trạng thái và bằng chứng từ Marketplace
```

Các trạng thái tài chính được giữ độc lập. Giao diện không gom chúng thành một trạng thái thành công giả định và không được mô tả luồng mô phỏng như chuyển tiền thật.

## 4. Kiến trúc hệ thống

Trình duyệt chỉ giao tiếp với Marketplace. Payment, MISA, Solana Gateway, cơ sở dữ liệu, signer và Solana RPC nằm phía sau lớp backend.

```text
Browser
  │
  ▼
React + TypeScript
Caddy :8080
  │  same-origin /api/v1
  ▼
Marketplace Backend :9191
  ├── Payment Backend :9190
  ├── MISA Backend :9192
  ├── Solana Gateway :9193
  │       └── Solana local RPC :9123
  │               └── Anchor Program
  ├── MySQL :3307
  └── Redis :6380
```

### Các thành phần chính

| Thành phần | Công nghệ | Trách nhiệm kiến trúc |
| --- | --- | --- |
| Frontend | React 18, TypeScript, Vite, Caddy | Giao diện theo vai trò, hiển thị workflow và bằng chứng; chỉ gọi Marketplace qua cùng origin. |
| Marketplace | Java 17, Spring Boot 3 | Cổng nghiệp vụ công khai, xác thực, phân quyền và điều phối toàn bộ vòng đời công việc. |
| Payment | Java 17, Spring Boot 3 | Mô phỏng checkout/capture và lưu trạng thái thanh toán của MVP. |
| MISA | Java 17, Spring Boot 3 | Mô phỏng hồ sơ thuế, payout record và chứng từ PDF/XML. |
| Solana Gateway | Java 17, Spring Boot 3, SolanaJ | Cách ly RPC, xây dựng/ký/gửi giao dịch và quản lý phiên giao dịch với Anchor Program. |
| On-chain program | Rust, Anchor | Ghi nhận invoice, rate, payment, withdrawal và off-ramp trên Solana localnet. |
| Dữ liệu | MySQL 8.4, Redis 7 | Lưu dữ liệu nghiệp vụ, phiên hỗ trợ xác thực, OTP và trạng thái runtime. |

### Nguyên tắc kiến trúc

- Marketplace là facade duy nhất dành cho frontend.
- Trình duyệt không gọi trực tiếp Payment, MISA, Solana Gateway hoặc Solana RPC.
- Giao tiếp nội bộ dùng API key riêng và không được đưa vào biến `VITE_*`.
- Private key và signer material chỉ được cung cấp qua runtime configuration ở local demo.
- Môi trường production cần KMS/HSM hoặc secret manager thay cho raw private key trong `.env`.
- Các cổng local trong Docker Compose được bind vào `127.0.0.1`, không mặc định công khai ra mạng ngoài.

## 5. Công nghệ và cấu trúc repository

```text
FreelaX/
├── frontend/                  React/TypeScript web application
├── marketplace-backend/      Nghiệp vụ marketplace và xác thực
├── payment-backend/          Thanh toán mô phỏng
├── misa-backend/             Thuế và chứng từ mô phỏng
├── solana-integration/        Gateway kết nối Solana RPC
├── solana-stablecoin-payout/ Anchor program và kiểm thử on-chain
├── docker-compose.yml         Local application stack
├── init-db.sql               Khởi tạo các database MySQL
└── .env.example              Mẫu cấu hình runtime
```

Backend được tổ chức dưới một Maven multi-module project. Frontend là ứng dụng Vite độc lập. Anchor program được build và triển khai riêng lên local validator trước khi chạy đầy đủ luồng Solana.

## 6. Chạy local demo

### Yêu cầu

- Git.
- Docker Desktop hoặc Docker Engine có Docker Compose.
- Một file `.env` ở root được tạo từ `.env.example` và điền các giá trị runtime cần thiết.
- Nếu chạy đầy đủ luồng on-chain: Solana/Agave local validator, Anchor toolchain, program đã deploy và chain state đã bootstrap.

Không commit `.env`, private key, seed phrase, JWT secret, internal API key hoặc mật khẩu tài khoản demo.

### Khởi động web stack

```bash
docker compose --profile deploy config --quiet
docker compose --profile deploy up -d --build
docker compose --profile deploy ps
```

Mở ứng dụng tại:

```text
http://localhost:8080
```

### Kiểm tra nhanh

```bash
curl -I http://localhost:8080
curl -i http://localhost:8080/api/v1/auth/me
```

Kết quả mong đợi:

- Frontend trả HTTP `200`.
- `/api/v1/auth/me` trả `401` khi chưa đăng nhập; đây là hành vi đúng.
- Sau khi đăng nhập, Client và Freelancer nhận đúng vai trò từ Marketplace.
- Các khu vực Tổng quan, Công việc, Thanh toán/Thu nhập, Hoạt động và Tài khoản tải được.

### Dừng hệ thống

```bash
docker compose --profile deploy down
```

Lệnh trên dừng container nhưng giữ lại volume dữ liệu. Chỉ xóa volume khi chủ động muốn dựng lại toàn bộ dữ liệu local.

## 7. Giới hạn hiện tại và hướng phát triển

Bản hiện tại chứng minh được sự kết nối giữa workflow marketplace, thanh toán, Solana và chứng từ thuế. Tuy nhiên, để thuyết phục người dùng làm việc với người lạ thông qua FreelaX, sản phẩm cần bổ sung lớp tin cậy ở cấp marketplace:

- Chưa có profile nghề nghiệp đầy đủ cho Client và Freelancer.
- Chưa có portfolio, xác minh kỹ năng hoặc chỉ số uy tín dựa trên giao dịch thật.
- Job chưa định nghĩa acceptance criteria và milestone đủ chặt chẽ.
- Client chưa phải funding milestone trước khi Freelancer bắt đầu.
- Client im lặng sau khi nhận submission có thể làm chậm payout.
- Chưa giới hạn số vòng revision nên có nguy cơ yêu cầu sửa vô hạn.
- Hai phía chưa có luồng cancel và dispute hoàn chỉnh.
- Chưa có vai trò Admin để phân xử dựa trên bằng chứng.
- Chưa có rating hai chiều sau giao dịch.
- Account đăng ký mới chưa được tự động provision wallet an toàn.

Định hướng của FreelaX là **không hứa rằng mọi công việc đều thành công**, mà bảo đảm rằng phạm vi, tiền, thời hạn, bằng chứng và quyền lợi của hai bên được xử lý theo quy tắc minh bạch.

## 8. Phạm vi MVP nên làm trước

Để trả lời thuyết phục trước giám khảo, FreelaX chưa cần xây cả hệ thống trọng tài phức tạp. Nên ưu tiên:

1. **Profile Client và Freelancer** — hiển thị danh tính, giới thiệu, lịch sử hoạt động và mức độ xác minh phù hợp với từng vai trò.
2. **Portfolio, kỹ năng và các chỉ số uy tín** — hỗ trợ đánh giá năng lực Freelancer, đồng thời công khai độ tin cậy, thời gian phản hồi và lịch sử thanh toán của Client.
3. **Acceptance criteria khi tạo job** — Client phải chốt deliverable, điều kiện nghiệm thu, deadline và phạm vi chỉnh sửa trước khi giao việc.
4. **Milestone được funding trước khi làm** — Freelancer chỉ bắt đầu khi hệ thống xác nhận ngân sách của milestone đã được giữ, tránh phụ thuộc hoàn toàn vào thiện chí thanh toán của Client.
5. **Review deadline, ví dụ 72 giờ** — submission phải được approve, yêu cầu revision hoặc đưa vào dispute trong thời gian quy định.
6. **Tối đa hai lần revision** — hết giới hạn thì Client phải approve, mở dispute hoặc tạo change request mới có thêm chi phí và thời gian.
7. **Nút cancel và dispute cho cả hai phía** — chính sách hoàn tiền hoặc thanh toán một phần phụ thuộc vào trạng thái và phần công việc đã được chấp nhận.
8. **Admin xử lý dispute thủ công** — MVP dùng người quản trị xem acceptance criteria, submission, feedback và lịch sử bằng chứng để đưa ra quyết định.
9. **Rating hai chiều sau giao dịch** — chỉ những hợp đồng thật mới tạo review; Client và Freelancer đều được đánh giá để hạn chế hành vi xấu từ cả hai phía.
10. **Tự động giải ngân hoặc mở dispute khi Client im lặng** — sau review deadline, milestone giá trị nhỏ có thể auto-release; trường hợp rủi ro cao chuyển sang thời gian khiếu nại hoặc xử lý tranh chấp.

Luồng mục tiêu sau khi hoàn thành phạm vi MVP:

```text
Job + acceptance criteria
        ↓
Milestone được funding
        ↓
Freelancer thực hiện và submit
        ↓
Client review trong thời hạn
        ├── Approve → Giải ngân
        ├── Revision → Giới hạn số vòng
        ├── Dispute → Admin phân xử
        └── Im lặng → Auto-release hoặc dispute grace period
```

Đây là lớp sản phẩm cần thiết để FreelaX chuyển từ một workflow demo thành marketplace có khả năng bảo vệ Client và Freelancer trong giao dịch thực tế.
