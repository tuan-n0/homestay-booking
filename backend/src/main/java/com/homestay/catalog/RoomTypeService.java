package com.homestay.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homestay.common.ApiException;
import com.homestay.room.RoomRepository;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** S1-06: loại phòng; S1-08: gắn tiện nghi cho loại phòng. */
@Service
public class RoomTypeService {

    private final RoomTypeRepository roomTypes;
    private final AmenityService amenityService;
    private final RoomRepository rooms;

    public RoomTypeService(RoomTypeRepository roomTypes, AmenityService amenityService, RoomRepository rooms) {
        this.roomTypes = roomTypes;
        this.amenityService = amenityService;
        this.rooms = rooms;
    }

    @Transactional(readOnly = true)
    public List<RoomTypeResponse> list() {
        return roomTypes.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoomTypeResponse get(Integer id) {
        return toResponse(find(id));
    }

    @Transactional
    public RoomTypeResponse create(RoomTypeRequest req) {
        validate(req);
        String code = req.code().trim().toUpperCase();
        if (roomTypes.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Mã loại phòng " + code + " đã tồn tại");
        }
        RoomType t = new RoomType();
        t.setCode(code);
        apply(t, req);
        roomTypes.save(t);
        return toResponse(t);
    }

    @Transactional
    public RoomTypeResponse update(Integer id, RoomTypeRequest req) {
        validate(req);
        RoomType t = find(id);
        String code = req.code().trim().toUpperCase();
        if (!t.getCode().equalsIgnoreCase(code) && roomTypes.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Mã loại phòng " + code + " đã tồn tại");
        }
        t.setCode(code);
        apply(t, req);
        return toResponse(t);
    }

    @Transactional
    public RoomTypeResponse setActive(Integer id, boolean active) {
        RoomType t = find(id);
        t.setActive(active);
        return toResponse(t);
    }

    @Transactional
    public void delete(Integer id) {
        RoomType t = find(id);
        long count = rooms.countByRoomTypeId(id);
        if (count > 0) {
            throw ApiException.conflict("Loại phòng đang có " + count
                    + " phòng gắn vào nên không xoá được. Bạn có thể đánh dấu ngừng bán");
        }
        roomTypes.delete(t);
    }

    @Transactional
    public RoomTypeResponse attachAmenity(Integer roomTypeId, Integer amenityId) {
        RoomType t = find(roomTypeId);
        Amenity a = amenityService.find(amenityId);
        if (t.getAmenities().stream().anyMatch(x -> x.getId().equals(amenityId))) {
            throw ApiException.conflict("Tiện nghi \"" + a.getName() + "\" đã được gắn cho loại phòng này");
        }
        if (!a.isActive()) {
            throw ApiException.badRequest("Tiện nghi \"" + a.getName() + "\" đã ngừng dùng, không gắn thêm được");
        }
        t.getAmenities().add(a);
        return toResponse(t);
    }

    @Transactional
    public RoomTypeResponse detachAmenity(Integer roomTypeId, Integer amenityId) {
        RoomType t = find(roomTypeId);
        t.getAmenities().removeIf(x -> x.getId().equals(amenityId));
        return toResponse(t);
    }

    RoomType find(Integer id) {
        return roomTypes.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy loại phòng"));
    }

    private static void validate(RoomTypeRequest req) {
        if (req.maxCapacity() < req.standardCapacity()) {
            throw ApiException.badRequest("Sức chứa tối đa (" + req.maxCapacity()
                    + ") không được nhỏ hơn sức chứa tiêu chuẩn (" + req.standardCapacity() + ")");
        }
    }

    private static void apply(RoomType t, RoomTypeRequest req) {
        t.setName(req.name().trim());
        t.setStandardCapacity(req.standardCapacity().shortValue());
        t.setMaxCapacity(req.maxCapacity().shortValue());
        t.setBedCount(req.bedCount().shortValue());
        t.setDescription(req.description());
    }

    RoomTypeResponse toResponse(RoomType t) {
        return new RoomTypeResponse(t.getId(), t.getCode(), t.getName(), t.getStandardCapacity(), t.getMaxCapacity(),
                t.getBedCount(), t.getDescription(), t.isActive(), t.getWeekdayPrice(), t.getWeekendPrice(),
                rooms.countByRoomTypeId(t.getId()),
                t.getAmenities().stream().filter(Amenity::isActive).map(a -> AmenityService.toResponse(a, 0)).toList());
    }

    public record RoomTypeRequest(
            @NotBlank(message = "Vui lòng nhập mã loại phòng") @Size(max = 20, message = "Mã tối đa 20 ký tự")
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã chỉ gồm chữ không dấu, số, gạch dưới") String code,
            @NotBlank(message = "Vui lòng nhập tên loại phòng") @Size(max = 100) String name,
            @NotNull(message = "Vui lòng nhập sức chứa tiêu chuẩn") @Min(value = 1, message = "Sức chứa tiêu chuẩn tối thiểu 1 người")
            @Max(value = 50) Integer standardCapacity,
            @NotNull(message = "Vui lòng nhập sức chứa tối đa") @Min(value = 1, message = "Sức chứa tối đa tối thiểu 1 người")
            @Max(value = 50) Integer maxCapacity,
            @NotNull(message = "Vui lòng nhập số giường") @Min(value = 1, message = "Số giường tối thiểu là 1")
            @Max(value = 20) Integer bedCount,
            @Size(max = 4000) String description) {}

    public record RoomTypeResponse(Integer id, String code, String name, int standardCapacity, int maxCapacity,
                                   int bedCount, String description, boolean active, Long weekdayPrice,
                                   Long weekendPrice, long roomCount, List<AmenityService.AmenityResponse> amenities) {}
}
