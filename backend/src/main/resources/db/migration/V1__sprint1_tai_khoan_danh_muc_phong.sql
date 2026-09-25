-- =====================================================================
-- V1 — SPRINT 1: Tài khoản, phân quyền, danh mục phòng, tham số vận hành,
--                trạng thái phòng + bảng bookings (tạo sớm vì S1-07 cần)
-- Quy ước: mốc thời gian dùng TIMESTAMPTZ (lưu UTC, hiển thị Asia/Ho_Chi_Minh);
--          khoảng ngày lưu trú là NỬA MỞ [nhận phòng, trả phòng).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- cần cho ràng buộc loại trừ (chống trùng lịch)

-- Chặn UPDATE/DELETE cho các bảng chỉ được ghi thêm (nhật ký, bút toán tiền)
CREATE OR REPLACE FUNCTION forbid_update_delete() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'Bảng % chỉ cho phép thêm mới, không được sửa hoặc xoá', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- EP-01: TÀI KHOẢN & PHÂN QUYỀN (S1-01 → S1-05)
-- ---------------------------------------------------------------------
CREATE TABLE roles (                                   -- S1-02, S1-04
  id    SMALLSERIAL PRIMARY KEY,
  code  VARCHAR(30) NOT NULL UNIQUE,
  name  VARCHAR(50) NOT NULL
);

CREATE TABLE permissions (                             -- S1-04: ma trận phân quyền ở 1 nơi
  id          SERIAL PRIMARY KEY,
  code        VARCHAR(50) NOT NULL UNIQUE,             -- vd: ROOM_TYPE_MANAGE, BOOKING_CONFIRM
  description VARCHAR(255)
);

CREATE TABLE role_permissions (
  role_id       SMALLINT NOT NULL REFERENCES roles(id),
  permission_id INT      NOT NULL REFERENCES permissions(id),
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
  id                   BIGSERIAL PRIMARY KEY,
  full_name            VARCHAR(100) NOT NULL,
  email                VARCHAR(255) NOT NULL,
  phone                VARCHAR(20),
  password_hash        VARCHAR(100) NOT NULL,          -- S1-01: BCrypt
  role_id              SMALLINT NOT NULL REFERENCES roles(id),
  is_active            BOOLEAN  NOT NULL DEFAULT TRUE,  -- S1-02
  must_change_password BOOLEAN  NOT NULL DEFAULT TRUE,  -- S1-02: mật khẩu tạm
  token_version        INT      NOT NULL DEFAULT 0,     -- S1-02, S1-03: tăng lên = huỷ mọi phiên
  failed_login_count   INT      NOT NULL DEFAULT 0,     -- S1-01: sai 5 lần / 15 phút
  first_failed_at      TIMESTAMPTZ,
  locked_until         TIMESTAMPTZ,                     -- S1-01: khoá 30 phút
  locked_at            TIMESTAMPTZ,                     -- S1-01: ghi thời điểm khoá
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));   -- A@x.com trùng a@x.com

CREATE TABLE refresh_tokens (                          -- S1-01
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_user ON refresh_tokens (user_id);

CREATE TABLE password_reset_tokens (                   -- S1-03
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,                     -- tạo lúc + 30 phút
  used_at    TIMESTAMPTZ,                              -- khác NULL = đã dùng
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_prt_user_created ON password_reset_tokens (user_id, created_at);  -- đếm 3 lần/giờ

CREATE TABLE audit_logs (                              -- S1-05, yêu cầu bảo vệ dữ liệu cá nhân
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT REFERENCES users(id),           -- NULL khi đăng nhập sai email
  email_attempt VARCHAR(255),
  action        VARCHAR(50) NOT NULL,                  -- LOGIN_SUCCESS, LOGIN_FAILED, ROLE_CHANGED,
                                                       -- ACCOUNT_DEACTIVATED, GUEST_ID_VIEWED, ...
  target_type   VARCHAR(50),                           -- USER, BOOKING, GUEST, ...
  target_id     BIGINT,
  ip_address    VARCHAR(45),
  detail        TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_created ON audit_logs (created_at);
CREATE INDEX ix_audit_user    ON audit_logs (user_id, created_at);
CREATE TRIGGER trg_audit_logs_readonly BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION forbid_update_delete();   -- S1-05: nhật ký chỉ đọc

-- ---------------------------------------------------------------------
-- EP-02: THÔNG TIN HOMESTAY & THAM SỐ VẬN HÀNH (S1-09)
-- ---------------------------------------------------------------------
CREATE TABLE homestay (                                -- chỉ 1 dòng (giả định: 1 cơ sở)
  id         SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  name       VARCHAR(150) NOT NULL,
  address    VARCHAR(255),
  phone      VARCHAR(20),
  email      VARCHAR(255),
  updated_by BIGINT REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE operating_policies (                      -- mỗi lần sửa = 1 dòng mới (phiên bản)
  id                         SERIAL PRIMARY KEY,
  check_in_time              TIME NOT NULL DEFAULT '14:00',
  check_out_time             TIME NOT NULL DEFAULT '12:00',
  late_checkout_fee_per_hour NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (late_checkout_fee_per_hour >= 0),
  extra_person_fee           NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (extra_person_fee >= 0),
  effective_from             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by                 BIGINT NOT NULL REFERENCES users(id),
  created_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Phiên bản đang áp dụng = dòng có effective_from lớn nhất và <= now()

CREATE TABLE cancellation_tiers (                      -- tối đa 3 mốc, giảm dần (kiểm ở backend)
  id             SERIAL PRIMARY KEY,
  policy_id      INT NOT NULL REFERENCES operating_policies(id) ON DELETE CASCADE,
  hours_before   INT NOT NULL CHECK (hours_before > 0),
  refund_percent SMALLINT NOT NULL CHECK (refund_percent BETWEEN 0 AND 100),
  UNIQUE (policy_id, hours_before)
);

-- ---------------------------------------------------------------------
-- EP-02: DANH MỤC PHÒNG (S1-06 → S1-08)
-- ---------------------------------------------------------------------
CREATE TABLE room_types (                              -- S1-06
  id                SERIAL PRIMARY KEY,
  code              VARCHAR(20)  NOT NULL UNIQUE,
  name              VARCHAR(100) NOT NULL,
  standard_capacity SMALLINT NOT NULL CHECK (standard_capacity > 0),
  max_capacity      SMALLINT NOT NULL,
  bed_count         SMALLINT NOT NULL CHECK (bed_count > 0),
  description       TEXT,
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,     -- FALSE = ngừng bán
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_room_types_capacity CHECK (max_capacity >= standard_capacity)
);

CREATE TABLE rooms (                                   -- S1-07, S1-10
  id           SERIAL PRIMARY KEY,
  room_number  VARCHAR(10) NOT NULL UNIQUE,
  floor        SMALLINT NOT NULL,
  room_type_id INT NOT NULL REFERENCES room_types(id) ON DELETE RESTRICT,
  note         TEXT,
  is_active    BOOLEAN NOT NULL DEFAULT TRUE,
  status       VARCHAR(20) NOT NULL DEFAULT 'VACANT_CLEAN'
               CHECK (status IN ('VACANT_CLEAN','VACANT_DIRTY','OCCUPIED','MAINTENANCE')),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_rooms_filter ON rooms (room_type_id, floor, status);

CREATE TABLE amenities (                               -- S1-08
  id        SERIAL PRIMARY KEY,
  code      VARCHAR(30)  NOT NULL UNIQUE,
  name      VARCHAR(100) NOT NULL,
  icon      VARCHAR(100),
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE room_type_amenities (                     -- S1-08
  room_type_id INT NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
  amenity_id   INT NOT NULL REFERENCES amenities(id)  ON DELETE RESTRICT,  -- đang gắn thì không xoá được
  PRIMARY KEY (room_type_id, amenity_id)                                   -- chặn gắn trùng
);

-- ---------------------------------------------------------------------
-- EP-04: TRẠNG THÁI PHÒNG & BẢO TRÌ (S1-10, dùng lại ở S2-05, S3-08, S3-09, S4-06)
-- ---------------------------------------------------------------------
CREATE TABLE room_maintenances (
  id          BIGSERIAL PRIMARY KEY,
  room_id     INT NOT NULL REFERENCES rooms(id),
  start_date  DATE NOT NULL,
  end_date    DATE NOT NULL,                           -- tính cả ngày này
  period      DATERANGE GENERATED ALWAYS AS (daterange(start_date, end_date, '[]')) STORED,
  reason      TEXT NOT NULL,
  created_by  BIGINT NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at TIMESTAMPTZ,                             -- bảo trì xong sớm/muộn
  CHECK (end_date >= start_date)
);
CREATE INDEX ix_maint_room_period ON room_maintenances USING gist (room_id, period);

CREATE TABLE room_status_history (
  id             BIGSERIAL PRIMARY KEY,
  room_id        INT NOT NULL REFERENCES rooms(id),
  from_status    VARCHAR(20) NOT NULL,
  to_status      VARCHAR(20) NOT NULL,
  maintenance_id BIGINT REFERENCES room_maintenances(id),
  note           TEXT,
  changed_by     BIGINT NOT NULL REFERENCES users(id),
  changed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (to_status <> 'MAINTENANCE' OR maintenance_id IS NOT NULL)   -- bảo trì phải có lý do + khoảng ngày
);
CREATE INDEX ix_rsh_room ON room_status_history (room_id, changed_at);

-- ---------------------------------------------------------------------
-- BOOKINGS — bảng trung tâm, dùng từ S1-07 đến S4-07
-- ---------------------------------------------------------------------
CREATE TABLE bookings (
  id                  BIGSERIAL PRIMARY KEY,
  code                CHAR(8) NOT NULL UNIQUE,         -- S2-07: mã 8 ký tự
  source              VARCHAR(10) NOT NULL DEFAULT 'ONLINE'
                      CHECK (source IN ('ONLINE','WALK_IN')),              -- S3-06
  status              VARCHAR(15) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','CONFIRMED','CHECKED_IN','CLOSED','CANCELLED','EXPIRED')),
  room_type_id        INT NOT NULL REFERENCES room_types(id),
  room_id             INT NOT NULL REFERENCES rooms(id),  -- tự gán 1 phòng trống khi đặt, S3-03 cho đổi
  check_in_date       DATE NOT NULL,
  check_out_date      DATE NOT NULL,
  stay                DATERANGE GENERATED ALWAYS AS (daterange(check_in_date, check_out_date, '[)')) STORED,
  guest_count         SMALLINT NOT NULL CHECK (guest_count > 0),
  customer_name       VARCHAR(100) NOT NULL,
  customer_phone      VARCHAR(10) NOT NULL CHECK (customer_phone ~ '^0[0-9]{9}$'),
  customer_email      VARCHAR(255),                    -- S3-06: khách vãng lai có thể bỏ trống
  customer_note       TEXT,
  internal_note       TEXT,                            -- S2-08: không hiện cho khách
  policy_id           INT NOT NULL REFERENCES operating_policies(id),   -- S1-09: chính sách lúc tạo
  room_amount         NUMERIC(12,0) NOT NULL DEFAULT 0,  -- tổng giá các đêm (chi tiết ở booking_nights)
  extra_guest_amount  NUMERIC(12,0) NOT NULL DEFAULT 0,  -- S2-06: phụ thu vượt sức chứa tiêu chuẩn
  total_amount        NUMERIC(12,0) NOT NULL DEFAULT 0,
  policy_accepted     BOOLEAN NOT NULL DEFAULT FALSE,  -- S2-07: đã đọc chính sách huỷ
  hold_expires_at     TIMESTAMPTZ,                     -- S2-07: giữ chỗ 24h; S3-01 gỡ bỏ
  created_ip          VARCHAR(45),                     -- S2-07: 5 lượt/giờ/địa chỉ
  created_by          BIGINT REFERENCES users(id),     -- NULL = khách tự đặt
  confirmed_at        TIMESTAMPTZ,
  confirmed_by        BIGINT REFERENCES users(id),
  checked_in_at       TIMESTAMPTZ,                     -- S3-07
  checked_in_by       BIGINT REFERENCES users(id),
  checked_out_at      TIMESTAMPTZ,                     -- S4-01
  checked_out_by      BIGINT REFERENCES users(id),
  early_checkout_note TEXT,                            -- S4-01: lý do trả sớm
  cancelled_at        TIMESTAMPTZ,                     -- S3-05
  cancelled_by        BIGINT REFERENCES users(id),
  cancel_reason       VARCHAR(255),
  refund_percent      SMALLINT CHECK (refund_percent BETWEEN 0 AND 100),
  refund_amount       NUMERIC(12,0),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (check_out_date > check_in_date),
  -- S3-02: chặn 2 booking còn hiệu lực cùng chiếm 1 phòng trong cùng 1 đêm, ngay ở tầng dữ liệu
  CONSTRAINT ex_bookings_no_overlap EXCLUDE USING gist (room_id WITH =, stay WITH &&)
    WHERE (status IN ('PENDING','CONFIRMED','CHECKED_IN'))
);
CREATE INDEX ix_bookings_status_created ON bookings (status, created_at DESC);   -- S2-10
CREATE INDEX ix_bookings_checkin        ON bookings (check_in_date);
CREATE INDEX ix_bookings_room_type      ON bookings (room_type_id, check_in_date);
CREATE INDEX ix_bookings_email          ON bookings (lower(customer_email));      -- S2-08
CREATE INDEX ix_bookings_phone          ON bookings (customer_phone);

-- ---------------------------------------------------------------------
-- DỮ LIỆU GỐC
-- ---------------------------------------------------------------------
INSERT INTO roles (code, name) VALUES
  ('RECEPTIONIST', 'Lễ tân'),
  ('HOUSEKEEPING', 'Buồng phòng'),
  ('OWNER',        'Chủ homestay'),
  ('ADMIN',        'Quản trị hệ thống');

-- S1-04: ma trận phân quyền — nơi duy nhất quyết định vai trò nào được làm gì
INSERT INTO permissions (code, description) VALUES
  ('USER_MANAGE',        'Tạo, sửa, vô hiệu hoá tài khoản'),
  ('USER_VIEW',          'Xem danh sách tài khoản'),
  ('AUDIT_VIEW',         'Xem nhật ký truy cập'),
  ('CATALOG_VIEW',       'Xem loại phòng, phòng, tiện nghi'),
  ('CATALOG_MANAGE',     'Khai báo loại phòng, phòng, tiện nghi, ảnh'),
  ('PRICING_VIEW',       'Xem bảng giá và chính sách huỷ'),
  ('PRICING_MANAGE',     'Khai báo bảng giá'),
  ('SETTINGS_MANAGE',    'Khai báo thông tin homestay và tham số vận hành'),
  ('BOOKING_VIEW',       'Xem booking'),
  ('BOOKING_OPERATE',    'Xác nhận, gán phòng, đổi ngày, huỷ, nhận và trả phòng'),
  ('ROOM_STATUS_VIEW',   'Xem trạng thái phòng và sơ đồ phòng'),
  ('ROOM_STATUS_UPDATE', 'Cập nhật trạng thái phòng'),
  ('HOUSEKEEPING',       'Danh sách phòng cần dọn'),
  ('PAYMENT_VIEW',       'Xem tiền cọc, thanh toán, hoá đơn'),
  ('PAYMENT_RECORD',     'Ghi nhận tiền cọc và thanh toán'),
  ('GUEST_ID_VIEW',      'Xem đầy đủ số giấy tờ tuỳ thân của khách'),
  ('REPORT_VIEW',        'Xem báo cáo công suất và doanh thu toàn kỳ'),
  ('REPORT_VIEW_TODAY',  'Xem doanh thu của ngày hiện tại'),
  ('EMAIL_LOG_VIEW',     'Xem lịch sử email đã gửi');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
  ('ADMIN', 'USER_MANAGE'), ('ADMIN', 'USER_VIEW'), ('ADMIN', 'AUDIT_VIEW'), ('ADMIN', 'CATALOG_VIEW'),
  ('ADMIN', 'CATALOG_MANAGE'), ('ADMIN', 'PRICING_VIEW'), ('ADMIN', 'SETTINGS_MANAGE'), ('ADMIN', 'BOOKING_VIEW'),
  ('ADMIN', 'ROOM_STATUS_VIEW'), ('ADMIN', 'PAYMENT_VIEW'), ('ADMIN', 'REPORT_VIEW'), ('ADMIN', 'EMAIL_LOG_VIEW'),

  ('OWNER', 'USER_VIEW'), ('OWNER', 'CATALOG_VIEW'), ('OWNER', 'CATALOG_MANAGE'), ('OWNER', 'PRICING_VIEW'),
  ('OWNER', 'PRICING_MANAGE'), ('OWNER', 'SETTINGS_MANAGE'), ('OWNER', 'BOOKING_VIEW'), ('OWNER', 'ROOM_STATUS_VIEW'),
  ('OWNER', 'PAYMENT_VIEW'), ('OWNER', 'PAYMENT_RECORD'), ('OWNER', 'GUEST_ID_VIEW'), ('OWNER', 'REPORT_VIEW'),

  ('RECEPTIONIST', 'CATALOG_VIEW'), ('RECEPTIONIST', 'PRICING_VIEW'), ('RECEPTIONIST', 'BOOKING_VIEW'),
  ('RECEPTIONIST', 'BOOKING_OPERATE'), ('RECEPTIONIST', 'ROOM_STATUS_VIEW'), ('RECEPTIONIST', 'ROOM_STATUS_UPDATE'),
  ('RECEPTIONIST', 'PAYMENT_VIEW'), ('RECEPTIONIST', 'PAYMENT_RECORD'), ('RECEPTIONIST', 'GUEST_ID_VIEW'),
  ('RECEPTIONIST', 'REPORT_VIEW_TODAY'),

  ('HOUSEKEEPING', 'HOUSEKEEPING')
) AS m(role_code, perm_code)
JOIN roles r ON r.code = m.role_code
JOIN permissions p ON p.code = m.perm_code;
