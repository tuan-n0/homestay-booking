package com.homestay.room;

/** S1-10: bốn trạng thái của phòng. */
public enum RoomStatus {
    VACANT_CLEAN("Trống sạch"),
    VACANT_DIRTY("Trống bẩn"),
    OCCUPIED("Đang ở"),
    MAINTENANCE("Bảo trì");

    private final String label;

    RoomStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
