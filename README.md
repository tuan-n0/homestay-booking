# Hệ thống đặt phòng Homestay

Ứng dụng web quản lý lịch phòng và booking cho homestay: khách tra phòng trống và đặt phòng trực tuyến,
lễ tân xác nhận, nhận phòng, trả phòng; chủ homestay khai báo phòng, bảng giá và xem báo cáo.

| Tầng | Công nghệ |
|---|---|
| Frontend | React 18 + TypeScript + Vite, Bootstrap 5 |
| Backend | Spring Boot 4 (Java 17), Spring Security + JWT, Spring Data JPA, Flyway |
| Cơ sở dữ liệu | PostgreSQL 15 (ràng buộc loại trừ chống đặt trùng phòng) |
| Hạ tầng dev | PostgreSQL 15 cài trên máy; Docker Compose chạy MinIO (ảnh phòng), Mailpit (email thử) |

## Chạy trên máy phát triển

Yêu cầu: JDK 17, Node 20+, PostgreSQL 15, Docker Desktop.

```bash
# 1. Tạo database trống trên PostgreSQL 15 (cổng 5432) — bảng sẽ do Flyway tự tạo
psql -U postgres -c "CREATE DATABASE homestay_db ENCODING 'UTF8' TEMPLATE template0;"

# 2. Khai báo mật khẩu PostgreSQL của máy bạn (file này không được đẩy lên Git)
cp backend/config/application.yml.example backend/config/application.yml
#    rồi sửa dòng password trong file vừa tạo

# 3. Dịch vụ phụ trợ (MinIO :9000/:9001, Mailpit :8025)
docker compose up -d

# 4. Backend — http://localhost:8080 (Swagger: /swagger-ui.html)
cd backend
./mvnw spring-boot:run

# 3. Frontend — http://localhost:5173
cd frontend
npm install
npm run dev
```

Lần chạy đầu, hệ thống tự tạo tài khoản quản trị `admin@homestay.local` / `Admin@123`
(bắt buộc đổi mật khẩu khi đăng nhập). Email gửi đi (mật khẩu tạm, đặt lại mật khẩu, xác nhận booking)
xem tại http://localhost:8025.

## Kiểm thử

```bash
cd backend
./mvnw test      # cần Docker đang chạy — test dùng PostgreSQL thật qua Testcontainers
```

## Cấu trúc

```
backend/   REST API (package theo nghiệp vụ: auth, user, catalog, room, booking, ...)
  src/main/resources/db/migration/   Flyway V1–V4, mỗi file ứng với một sprint
frontend/  Giao diện nhân viên (/admin) và trang công khai cho khách
docker-compose.yml   Hạ tầng phát triển
```
