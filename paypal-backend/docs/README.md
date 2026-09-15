# PayPal Backend — Base Skeleton

Skeleton khởi tạo: auth (register/sign-in/refresh-token/sign-out/forgot-password),
JWT, Redis, Security config, User/Role. **Chưa có business logic PayPal** — đây là
nền để build tiếp các module thật lên trên, tương tự cách `misa-backend` đã được
build từ skeleton này.

## Chạy thử

```bash
docker compose up -d       # khởi tạo MySQL + Redis
mvn spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

`docker-compose.yml` tạo sẵn MySQL (database `paypal`, user/pass `paypal`/`paypal`,
cổng `3307`) và Redis (cổng `6379`) khớp đúng default trong `application.yml` —
không cần cấu hình gì thêm để chạy local.

`security.jwt.secret` ở `application-dev.yml` có sẵn giá trị mặc định **chỉ dùng
để chạy local/dev**. Khi deploy thật, phải set biến môi trường `JWT_SECRET` bằng
giá trị riêng, không dùng giá trị mặc định này.

## Ghi chú

- Package gốc: `com.paypal.backend`.
- `DataInitializer` chỉ seed 2 role: `ROLE_USER`, `ROLE_ADMIN`.
- Không kèm module nghiệp vụ nào (không phải bản mock so sánh phí — bản đó nằm ở
  `paypal-backend.zip` đã gửi trước, đây là bản base sạch để bắt đầu từ đầu).
