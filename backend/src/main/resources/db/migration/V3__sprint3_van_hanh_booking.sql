-- =====================================================================
-- V3 — SPRINT 3: Xác nhận + đặt cọc, gán/đổi phòng, đổi ngày, huỷ, nhận phòng
-- (Chống đặt trùng S3-02 đã nằm sẵn trong bảng bookings ở V1: ex_bookings_no_overlap)
-- =====================================================================

-- S3-01, S3-05, S4-01: mọi khoản tiền của booking — chỉ ghi thêm, không sửa
CREATE TABLE payments (
  id             BIGSERIAL PRIMARY KEY,
  booking_id     BIGINT NOT NULL REFERENCES bookings(id),
  kind           VARCHAR(15) NOT NULL
                 CHECK (kind IN ('DEPOSIT','ADJUSTMENT','REFUND','FINAL')),
                 -- DEPOSIT: cọc | ADJUSTMENT: bút toán điều chỉnh | REFUND: hoàn cọc | FINAL: thu khi trả phòng
  amount         NUMERIC(12,0) NOT NULL CHECK (amount <> 0),   -- số âm cho hoàn tiền / điều chỉnh giảm
  method         VARCHAR(10) NOT NULL CHECK (method IN ('CASH','TRANSFER')),
  received_date  DATE NOT NULL,
  reference_code VARCHAR(100),                         -- mã giao dịch chuyển khoản
  note           TEXT,
  created_by     BIGINT NOT NULL REFERENCES users(id),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_payments_booking ON payments (booking_id);
CREATE TRIGGER trg_payments_append_only BEFORE UPDATE OR DELETE ON payments
  FOR EACH ROW EXECUTE FUNCTION forbid_update_delete();

-- S3-07: khách lưu trú; người đứng tên bắt buộc có số giấy tờ
CREATE TABLE booking_guests (
  id         BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  full_name  VARCHAR(100) NOT NULL,
  id_number  VARCHAR(20),                              -- chỉ hiện 4 số cuối với vai trò khác; xem đầy đủ → ghi audit_logs
  is_primary BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (NOT is_primary OR id_number IS NOT NULL)
);
CREATE UNIQUE INDEX uq_booking_guests_primary ON booking_guests (booking_id) WHERE is_primary;

-- S3-03, S3-04, S3-05, S3-07, S4-01: lịch sử thao tác trên booking (giá trị cũ → mới, ai, khi nào)
CREATE TABLE booking_events (
  id         BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  event_type VARCHAR(30) NOT NULL CHECK (event_type IN (
               'CREATED','CONFIRMED','ROOM_CHANGED','DATES_CHANGED','ROOM_TYPE_CHANGED',
               'CANCELLED','EXPIRED','CHECKED_IN','CHECKED_OUT','NOTE')),
  old_value  JSONB,                                    -- vd: {"room_id": 5}
  new_value  JSONB,                                    -- vd: {"room_id": 7}
  reason     TEXT,
  actor_id   BIGINT REFERENCES users(id),              -- NULL = khách hoặc hệ thống
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_booking_events_booking ON booking_events (booking_id, created_at);
