package com.homestay.room;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homestay.auth.CurrentUser;
import com.homestay.booking.Booking;
import com.homestay.booking.BookingRepository;
import com.homestay.booking.BookingStatus;
import com.homestay.catalog.RoomType;
import com.homestay.catalog.RoomTypeRepository;
import com.homestay.common.ApiException;
import com.homestay.common.Times;
import com.homestay.user.User;
import com.homestay.user.UserRepository;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** S1-07: phòng vật lý; S1-10: trạng thái phòng và bảo trì. */
@Service
public class RoomService {

    private final RoomRepository rooms;
    private final RoomTypeRepository roomTypes;
    private final BookingRepository bookings;
    private final RoomMaintenanceRepository maintenances;
    private final RoomStatusHistoryRepository history;
    private final UserRepository users;
    private final Clock clock;

    public RoomService(RoomRepository rooms, RoomTypeRepository roomTypes, BookingRepository bookings,
                       RoomMaintenanceRepository maintenances, RoomStatusHistoryRepository history,
                       UserRepository users, Clock clock) {
        this.rooms = rooms;
        this.roomTypes = roomTypes;
        this.bookings = bookings;
        this.maintenances = maintenances;
        this.history = history;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list(Integer roomTypeId, Short floor, RoomStatus status) {
        return rooms.filter(roomTypeId, floor, status).stream().map(this::toResponse).toList();
    }

    @Transactional
    public RoomResponse create(RoomRequest req) {
        String number = req.roomNumber().trim().toUpperCase();
        if (rooms.existsByRoomNumberIgnoreCase(number)) {
            throw ApiException.conflict("Số phòng " + number + " đã tồn tại");
        }
        Room room = new Room();
        room.setRoomNumber(number);
        apply(room, req);
        rooms.save(room);
        return toResponse(room);
    }

    /**
     * Đổi loại phòng khi phòng còn booking trong tương lai → trả 409 kèm danh sách booking bị ảnh hưởng,
     * người dùng xem lại rồi gửi lại với confirm=true mới được lưu.
     */
    @Transactional
    public RoomResponse update(Integer id, RoomRequest req, boolean confirm) {
        Room room = find(id);
        String number = req.roomNumber().trim().toUpperCase();
        if (!room.getRoomNumber().equalsIgnoreCase(number) && rooms.existsByRoomNumberIgnoreCase(number)) {
            throw ApiException.conflict("Số phòng " + number + " đã tồn tại");
        }
        if (!room.getRoomType().getId().equals(req.roomTypeId()) && !confirm) {
            List<Booking> upcoming = bookings.findUpcomingByRoom(id, Times.today(clock), BookingStatus.OCCUPYING);
            if (!upcoming.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "FUTURE_BOOKINGS",
                        "Phòng " + room.getRoomNumber() + " đang có " + upcoming.size()
                                + " booking trong tương lai. Đổi loại phòng sẽ ảnh hưởng các booking dưới đây",
                        Map.of("bookings", upcoming.stream().map(b -> Map.of(
                                "id", b.getId(), "code", b.getCode(), "customerName", b.getCustomerName(),
                                "checkInDate", b.getCheckInDate().toString(), "checkOutDate", b.getCheckOutDate().toString(),
                                "status", b.getStatus().label())).toList()));
            }
        }
        room.setRoomNumber(number);
        apply(room, req);
        return toResponse(room);
    }

    /** S1-10: đổi trạng thái thủ công. Nhận/trả phòng đổi trạng thái qua luồng booking. */
    @Transactional
    public RoomResponse changeStatus(Integer id, StatusRequest req) {
        Room room = find(id);
        RoomStatus from = room.getStatus();
        RoomStatus to = req.status();
        if (from == to) {
            throw ApiException.badRequest("Phòng đang ở trạng thái " + to.label());
        }
        if (from == RoomStatus.OCCUPIED) {
            throw ApiException.badRequest(to == RoomStatus.MAINTENANCE
                    ? "Phòng đang có khách, không chuyển thẳng sang bảo trì được. Vui lòng làm thủ tục trả phòng trước"
                    : "Phòng đang có khách, trạng thái chỉ thay đổi khi làm thủ tục trả phòng");
        }
        if (to == RoomStatus.OCCUPIED) {
            throw ApiException.badRequest("Phòng chỉ chuyển sang \"Đang ở\" qua thủ tục nhận phòng");
        }
        Long userId = CurrentUser.get().id();
        RoomStatusHistory h = new RoomStatusHistory();
        if (to == RoomStatus.MAINTENANCE) {
            if (req.reason() == null || req.reason().isBlank()) {
                throw ApiException.badRequest("Chuyển phòng sang bảo trì bắt buộc nhập lý do");
            }
            if (req.maintenanceFrom() == null || req.maintenanceTo() == null) {
                throw ApiException.badRequest("Chuyển phòng sang bảo trì bắt buộc nhập khoảng ngày dự kiến");
            }
            if (req.maintenanceTo().isBefore(req.maintenanceFrom())) {
                throw ApiException.badRequest("Ngày kết thúc bảo trì phải sau hoặc bằng ngày bắt đầu");
            }
            if (req.maintenanceFrom().isBefore(Times.today(clock))) {
                throw ApiException.badRequest("Ngày bắt đầu bảo trì không được ở quá khứ");
            }
            RoomMaintenance m = new RoomMaintenance();
            m.setRoomId(id);
            m.setStartDate(req.maintenanceFrom());
            m.setEndDate(req.maintenanceTo());
            m.setReason(req.reason().trim());
            m.setCreatedBy(userId);
            maintenances.save(m);
            h.setMaintenanceId(m.getId());
        }
        if (from == RoomStatus.MAINTENANCE) {
            maintenances.findFirstByRoomIdAndFinishedAtIsNullOrderByCreatedAtDesc(id)
                    .ifPresent(m -> m.setFinishedAt(clock.instant()));
        }
        room.setStatus(to);
        h.setRoomId(id);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setNote(req.note() != null && !req.note().isBlank() ? req.note().trim() : req.reason());
        h.setChangedBy(userId);
        h.setChangedAt(clock.instant());
        history.save(h);
        return toResponse(room);
    }

    /** Dùng chung cho nhận phòng, trả phòng, buồng phòng: đổi trạng thái và ghi lịch sử. */
    @Transactional
    public void recordStatus(Room room, RoomStatus to, String note, Long userId) {
        RoomStatusHistory h = new RoomStatusHistory();
        h.setRoomId(room.getId());
        h.setFromStatus(room.getStatus());
        h.setToStatus(to);
        h.setNote(note);
        h.setChangedBy(userId);
        h.setChangedAt(clock.instant());
        room.setStatus(to);
        history.save(h);
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> history(Integer roomId) {
        List<RoomStatusHistory> list = history.findTop50ByRoomIdOrderByChangedAtDesc(roomId);
        Map<Long, User> userMap = users.findAllById(list.stream().map(RoomStatusHistory::getChangedBy).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, RoomMaintenance> mMap = maintenances.findAllById(list.stream()
                        .map(RoomStatusHistory::getMaintenanceId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(RoomMaintenance::getId, Function.identity()));
        return list.stream().map(h -> {
            RoomMaintenance m = h.getMaintenanceId() == null ? null : mMap.get(h.getMaintenanceId());
            User u = userMap.get(h.getChangedBy());
            return new HistoryResponse(h.getId(), h.getFromStatus().label(), h.getToStatus().label(), h.getNote(),
                    m == null ? null : m.getStartDate(), m == null ? null : m.getEndDate(),
                    u == null ? null : u.getFullName(), Times.format(h.getChangedAt()));
        }).toList();
    }

    Room find(Integer id) {
        return rooms.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng"));
    }

    private void apply(Room room, RoomRequest req) {
        RoomType type = roomTypes.findById(req.roomTypeId())
                .orElseThrow(() -> ApiException.badRequest("Loại phòng không tồn tại"));
        room.setFloor(req.floor().shortValue());
        room.setRoomType(type);
        room.setNote(req.note());
        if (req.active() != null) {
            room.setActive(req.active());
        }
    }

    RoomResponse toResponse(Room r) {
        RoomMaintenance m = r.getStatus() == RoomStatus.MAINTENANCE
                ? maintenances.findFirstByRoomIdAndFinishedAtIsNullOrderByCreatedAtDesc(r.getId()).orElse(null)
                : null;
        return new RoomResponse(r.getId(), r.getRoomNumber(), r.getFloor(), r.getRoomType().getId(),
                r.getRoomType().getName(), r.getNote(), r.isActive(), r.getStatus(), r.getStatus().label(),
                m == null ? null : m.getReason(), m == null ? null : m.getStartDate(), m == null ? null : m.getEndDate());
    }

    public record RoomRequest(
            @NotBlank(message = "Vui lòng nhập số phòng") @Size(max = 10, message = "Số phòng tối đa 10 ký tự") String roomNumber,
            @NotNull(message = "Vui lòng nhập tầng") @Min(value = -5, message = "Tầng không hợp lệ") @Max(value = 200) Integer floor,
            @NotNull(message = "Vui lòng chọn loại phòng") Integer roomTypeId,
            @Size(max = 2000) String note,
            Boolean active) {}

    public record StatusRequest(@NotNull(message = "Vui lòng chọn trạng thái") RoomStatus status,
                                String reason, LocalDate maintenanceFrom, LocalDate maintenanceTo, String note) {}

    public record RoomResponse(Integer id, String roomNumber, int floor, Integer roomTypeId, String roomTypeName,
                               String note, boolean active, RoomStatus status, String statusLabel,
                               String maintenanceReason, LocalDate maintenanceFrom, LocalDate maintenanceTo) {}

    public record HistoryResponse(Long id, String fromStatus, String toStatus, String note, LocalDate maintenanceFrom,
                                  LocalDate maintenanceTo, String changedBy, String changedAt) {}
}
