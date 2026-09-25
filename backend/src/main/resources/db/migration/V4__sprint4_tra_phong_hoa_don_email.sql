-- =====================================================================
-- V4 — SPRINT 4: Trả phòng, phụ thu, hoá đơn, email, tác vụ định kỳ
-- (Báo cáo S4-06, S4-07 tính trực tiếp từ booking_nights, booking_charges,
--  rooms, room_maintenances — không cần bảng riêng)
-- =====================================================================

-- S4-01, S4-02: tham số bổ sung
ALTER TABLE operating_policies
  ADD COLUMN extra_bed_fee                 NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (extra_bed_fee >= 0),
  ADD COLUMN late_checkout_full_night_from TIME NOT NULL DEFAULT '18:00';   -- quá giờ này tính 1 đêm

-- S4-01, S4-02: các khoản phụ thu của booking
CREATE TABLE booking_charges (
  id              BIGSERIAL PRIMARY KEY,
  booking_id      BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  charge_type     VARCHAR(20) NOT NULL
                  CHECK (charge_type IN ('EXTRA_PERSON','EXTRA_BED','LATE_CHECKOUT','OTHER')),
  quantity        SMALLINT NOT NULL CHECK (quantity > 0),
  unit_price      NUMERIC(12,0) NOT NULL CHECK (unit_price >= 0),
  nights          SMALLINT NOT NULL DEFAULT 1 CHECK (nights > 0),   -- trả muộn theo giờ: quantity = số giờ, nights = 1
  amount          NUMERIC(14,0) GENERATED ALWAYS AS (quantity * unit_price * nights) STORED,
  note            TEXT,
  override_reason TEXT,                                -- bắt buộc khi sửa đơn giá khác tham số (kiểm ở backend)
  created_by      BIGINT NOT NULL REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_booking_charges_booking ON booking_charges (booking_id);

-- S4-03: số hoá đơn theo năm, không trùng, không có khoảng trống
-- Backend: UPDATE invoice_sequences SET last_number = last_number + 1 WHERE year = ? RETURNING last_number
--          trong cùng giao dịch với INSERT invoices (lỗi thì cả hai cùng huỷ → không thủng số)
CREATE TABLE invoice_sequences (
  year        SMALLINT PRIMARY KEY,
  last_number INT NOT NULL DEFAULT 0
);

CREATE TABLE invoices (
  id             BIGSERIAL PRIMARY KEY,
  booking_id     BIGINT NOT NULL UNIQUE REFERENCES bookings(id),
  invoice_year   SMALLINT NOT NULL,
  invoice_seq    INT NOT NULL,
  invoice_number VARCHAR(20) NOT NULL UNIQUE,          -- vd: 2026-000123
  total_amount   NUMERIC(14,0) NOT NULL,
  issued_by      BIGINT NOT NULL REFERENCES users(id),
  issued_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (invoice_year, invoice_seq)
);

-- S4-04, S4-05: nhật ký / hàng đợi gửi email (thử lại tối đa 3 lần cách 5 phút)
CREATE TABLE email_logs (
  id              BIGSERIAL PRIMARY KEY,
  booking_id      BIGINT REFERENCES bookings(id),
  recipient       VARCHAR(255) NOT NULL,
  template        VARCHAR(30) NOT NULL CHECK (template IN (
                    'BOOKING_CREATED','BOOKING_CONFIRMED','CHECKIN_REMINDER','BOOKING_CANCELLED',
                    'PASSWORD_RESET','TEMP_PASSWORD')),
  subject         VARCHAR(255) NOT NULL,
  body            TEXT NOT NULL,
  status          VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
  attempts        SMALLINT NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 4),
  next_attempt_at TIMESTAMPTZ,
  last_error      TEXT,
  sent_at         TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_email_logs_queue   ON email_logs (status, next_attempt_at);
CREATE INDEX ix_email_logs_created ON email_logs (created_at DESC);
-- S4-05: mỗi booking chỉ nhận tối đa 1 email nhắc, chạy lại tác vụ không gửi trùng
CREATE UNIQUE INDEX uq_email_logs_one_reminder ON email_logs (booking_id)
  WHERE template = 'CHECKIN_REMINDER';

-- S4-05: ghi lại mỗi lần chạy tác vụ định kỳ
CREATE TABLE job_runs (
  id              BIGSERIAL PRIMARY KEY,
  job_name        VARCHAR(50) NOT NULL,                -- vd: CHECKIN_REMINDER, EXPIRE_PENDING_BOOKINGS
  started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at     TIMESTAMPTZ,
  processed_count INT NOT NULL DEFAULT 0,              -- số email đã gửi
  error           TEXT
);
