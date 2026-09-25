-- =====================================================================
-- V2 — SPRINT 2: Bảng giá, ảnh phòng, tra phòng trống, đặt phòng công khai
-- =====================================================================

-- S2-01: giá ngày thường và giá cuối tuần theo loại phòng (NULL = chưa khai giá, chưa bán được)
ALTER TABLE room_types
  ADD COLUMN weekday_price NUMERIC(12,0) CHECK (weekday_price > 0),
  ADD COLUMN weekend_price NUMERIC(12,0) CHECK (weekend_price > 0);

-- S2-01: đêm nào được tính là cuối tuần — khai báo trong tham số (ISODOW: 5 = thứ Sáu, 6 = thứ Bảy)
ALTER TABLE operating_policies
  ADD COLUMN weekend_days SMALLINT[] NOT NULL DEFAULT '{5,6}';

-- S2-02: giá đè theo mùa / ngày lễ
CREATE TABLE seasonal_rates (
  id              SERIAL PRIMARY KEY,
  name            VARCHAR(100) NOT NULL,               -- vd: "Lễ 30/4 - 1/5"
  room_type_id    INT NOT NULL REFERENCES room_types(id),
  start_date      DATE NOT NULL,
  end_date        DATE NOT NULL,                       -- tính cả đêm của ngày này
  price_per_night NUMERIC(12,0) NOT NULL CHECK (price_per_night > 0),
  created_by      BIGINT NOT NULL REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (end_date >= start_date),
  -- 2 đợt giá đè trùng ngày cho cùng 1 loại phòng bị chặn
  CONSTRAINT ex_seasonal_rates_no_overlap EXCLUDE USING gist
    (room_type_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
);

-- S2-09: ảnh loại phòng (file lưu ở MinIO, DB chỉ lưu đường dẫn)
CREATE TABLE room_type_images (
  id            BIGSERIAL PRIMARY KEY,
  room_type_id  INT NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
  object_key    VARCHAR(255) NOT NULL,                 -- ảnh đã nén, rộng tối đa 1600px
  thumbnail_key VARCHAR(255) NOT NULL,                 -- bản thu nhỏ cho danh sách
  alt_text      VARCHAR(255) NOT NULL,                 -- S2-03: văn bản thay thế
  sort_order    SMALLINT NOT NULL DEFAULT 0,           -- 0 = ảnh đại diện
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_rti_type_order ON room_type_images (room_type_id, sort_order);

-- S2-06, S2-01: giá chốt từng đêm của booking (đổi bảng giá sau này không ảnh hưởng)
-- Dùng lại cho S4-01 (tính tiền), S4-06 (công suất), S4-07 (doanh thu theo ngày)
CREATE TABLE booking_nights (
  booking_id       BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  stay_date        DATE NOT NULL,                      -- đêm của ngày này
  price            NUMERIC(12,0) NOT NULL CHECK (price >= 0),
  rate_type        VARCHAR(10) NOT NULL CHECK (rate_type IN ('WEEKDAY','WEEKEND','SEASON')),
  seasonal_rate_id INT REFERENCES seasonal_rates(id),
  label            VARCHAR(100),                       -- "Ngày thường", "Cuối tuần", tên đợt lễ
  PRIMARY KEY (booking_id, stay_date)
);
CREATE INDEX ix_booking_nights_date ON booking_nights (stay_date);

-- S2-07 (5 lượt đặt/giờ/địa chỉ), S2-08 (10 lần tra sai/15 phút/địa chỉ)
CREATE TABLE rate_limit_events (
  id         BIGSERIAL PRIMARY KEY,
  action     VARCHAR(30) NOT NULL,                     -- BOOKING_CREATE, BOOKING_LOOKUP_FAIL
  client_key VARCHAR(255) NOT NULL,                    -- địa chỉ IP
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_rle_lookup ON rate_limit_events (action, client_key, created_at);
