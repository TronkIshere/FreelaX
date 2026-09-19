# PaySim (paypal) — Flutter app mô phỏng giao diện PayPal

## ⚠️ Việc chưa làm được — cần bạn xử lý trước

File `Tong_quan_du_an_USDC_Freelancer.docx` bạn gửi bị **hỏng ở tầng byte**
(không phải do tôi đọc sai): `word/document.xml` — phần chứa nội dung — giải
nén ra 0 byte, lỗi `invalid compressed data to inflate` / `corrupt deflate
stream`. Tôi đã thử `extract-text`, `pandoc`, và giải nén raw deflate bằng tay
— cả ba đều xác nhận phần nội dung đã mất, chỉ còn đọc được tiêu đề file:
*"Tổng quan dự án Cổng thanh toán USDC cho Freelancer"*. Metadata file cho
thấy nó được export từ **Claude Docs** (`Claude Docs node/de3e2a89-459a@15`).

**Vì vậy tôi CHƯA build được màn hình "chuyển tiền lên marketplace
freelancer"** theo đúng workflow bạn đã tài liệu hóa — tôi không có nội dung
đó. Để làm tiếp phần này, bạn có 3 cách, chọn 1:
1. Nếu file gốc vẫn còn là **Claude Doc** trên claude.ai (chưa xoá), gửi tôi
   link claude.ai của nó — tôi đọc trực tiếp được, không qua export.
2. Export lại file .docx (mở lại doc gốc → Export lại → upload lại).
3. Copy/paste thẳng nội dung text vào chat.

## Giới hạn cần biết: tôi không có trình duyệt/web search trong phiên này

"Tìm hiểu giao diện PayPal" ở bản này dựa trên **kiến thức chung đã biết
trước đó** về ngôn ngữ thiết kế PayPal (tông xanh navy/xanh dương, thẻ số dư,
cặp nút Gửi/Nhận, lưới quick action, thanh điều hướng đáy...) — **không phải
chụp màn hình trực tiếp từ app PayPal hiện tại**. Nếu cần khớp pixel-perfect
với bản PayPal mới nhất (PayPal có đổi bộ nhận diện thương hiệu định kỳ), bạn
nên chụp màn hình app PayPal thật gửi tôi so màu/layout ở lượt sau.

## Một quyết định tôi tự đưa ra — nói rõ để bạn cân nhắc lại nếu không đồng ý

Tôi **không đặt tên hiển thị của app là "PayPal"** (đặt là **"PaySim"**
trong `app_strings.dart`). Lý do: đây là bản demo pitch, và chính tài liệu
`PAYPAL_MOCK_REFERENCE.md` của bạn đã nhấn mạnh không được để người xem hiểu
lầm là "tích hợp PayPal thật". Phần tôi **mô phỏng giống PayPal** là NGÔN NGỮ
THIẾT KẾ (màu, bố cục thẻ số dư, luồng nút Gửi/Nhận...), còn tên/logo app vẫn
là của riêng bạn — tránh rủi ro bị hiểu là mạo danh thương hiệu khi demo
trước giám khảo. Muốn đổi lại thành "PayPal" y nguyên thì chỉ cần sửa 1 dòng
trong `lib/core/constants/app_strings.dart`.

## Cái gì THẬT, cái gì "Đang phát triển"

| Tính năng | Trạng thái | Vì sao |
|---|---|---|
| Đăng nhập / Đăng ký / Quên mật khẩu / OTP | **Thật** — gọi `/api/v1/auth/*` | Đã có `RemoteAuthRepository` sẵn |
| **So sánh phí (PayPal vs USDC+MISA)** | **Thật** — gọi `POST /api/v1/simulations/compare` | Đúng theo `PAYPAL_SIMULATION_MODULE.md` bạn cung cấp |
| Gửi tiền / Nhận tiền | Placeholder → bottom sheet "Đang phát triển" | Chưa có API đứng sau |
| Nạp tiền / Rút tiền / Quét QR / Thẻ của tôi | Placeholder → bottom sheet "Đang phát triển" | Chưa có API đứng sau |
| Thông báo (chuông) | Placeholder | Chưa có API đứng sau |
| Tab "Hoạt động" (lịch sử giao dịch) | Màn empty-state tĩnh | Backend simulation không lưu lịch sử (đúng theo docs) |
| **Chuyển tiền lên marketplace freelancer** | ❌ Chưa build | Cần nội dung từ docx bị hỏng (xem mục trên) |

Cơ chế dùng chung cho mọi nút "chưa thật": `showComingSoon(context,
featureName: '...')` trong `lib/shared/widgets/coming_soon.dart` — muốn nối
API thật cho tính năng nào, chỉ cần thay lời gọi `showComingSoon(...)` bằng
logic thật ở đúng chỗ đó.

## Đã bỏ khỏi bản gốc "brome_clean" (không liên quan tới app này)

`points_config.dart`, `reminder_presets.dart`, mọi màn hình/service về
loyalty/reminder/rewards/notification/scan-box, và các dependency
`flutter_local_notifications`, `timezone`, `flutter_timezone`,
`permission_handler`, `mobile_scanner`, `url_launcher` — toàn bộ đặc thù cho
app nhắc lịch giặt cọ, không dùng trong app mô phỏng thanh toán này. Bỏ luôn
để app không xin các quyền (camera, notification...) mà không dùng tới —
tránh nhìn thiếu chuyên nghiệp khi demo trước giám khảo.

Cũng đã đổi email demo hardcode trong `mock_auth_repository.dart` /
`login_screen.dart` từ một địa chỉ Gmail thật trong code gốc thành
`demo@paysim.local` — không nên để lẫn email cá nhân thật trong code mẫu dùng
để tái sử dụng.

## Cấu hình server (bắt buộc trước khi đăng nhập)

`defaultBaseUrl` trong `app_config_service.dart` để **rỗng** (khác bản gốc có
sẵn domain production). Mở app → tab **Cài đặt** → nhập
`http://<IP LAN của laptop>:<port>` (ví dụ `http://192.168.1.23:8080`). Lấy
IP bằng `ipconfig` (Windows) hoặc `ifconfig`/`ip addr` (macOS/Linux) trên máy
chạy backend — điện thoại và laptop phải cùng mạng WiFi.

## Chưa build/kiểm tra bằng Flutter SDK thật

Sandbox này không có Flutter/Dart SDK và không có mạng tới pub.dev, nên tôi
**không chạy được** `flutter pub get` / `flutter analyze` để build-check.
Tôi đã tự kiểm tra tĩnh: toàn bộ import tương đối trỏ đúng file tồn tại, dấu
ngoặc `{}` `()` cân bằng trong mọi file — nhưng vẫn khuyên bạn chạy
`flutter pub get && flutter analyze` ngay khi giải nén, trước khi build APK.

## File trong zip

- `lib/` — toàn bộ mã Dart (package `paypal`, giữ đúng tên bạn đã đặt)
- `pubspec.yaml` — đã bớt dependency không dùng
- `assets/logo/` — thư mục rỗng, **bạn tự thả file `logo.png` vào**
  (nếu chưa có, `AppLogo` tự fallback sang icon, không crash app)
- `android/app/src/main/AndroidManifest.xml` — đã bỏ quyền camera/notification
- `android/settings.gradle.kts`, `android/build.gradle.kts`,
  `android/app/build.gradle.kts` — giữ nguyên, không cần sửa

**Không có trong zip** (giữ nguyên từ project hiện tại của bạn, tôi không
đụng tới vì chưa từng thấy): `android/app/src/main/kotlin/.../MainActivity.kt`,
gradle wrapper, `local.properties`, thư mục `ios/`, mipmap icon có sẵn.
