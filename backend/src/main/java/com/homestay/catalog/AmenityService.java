package com.homestay.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homestay.common.ApiException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** S1-08: danh mục tiện nghi. */
@Service
public class AmenityService {

    private final AmenityRepository amenities;

    public AmenityService(AmenityRepository amenities) {
        this.amenities = amenities;
    }

    @Transactional(readOnly = true)
    public List<AmenityResponse> list() {
        return amenities.findAllByOrderByNameAsc().stream()
                .map(a -> toResponse(a, amenities.countUsage(a.getId()))).toList();
    }

    @Transactional
    public AmenityResponse create(AmenityRequest req) {
        String code = req.code().trim().toUpperCase();
        if (amenities.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Mã tiện nghi " + code + " đã tồn tại");
        }
        Amenity a = new Amenity();
        a.setCode(code);
        apply(a, req);
        amenities.save(a);
        return toResponse(a, 0);
    }

    @Transactional
    public AmenityResponse update(Integer id, AmenityRequest req) {
        Amenity a = find(id);
        String code = req.code().trim().toUpperCase();
        if (!a.getCode().equalsIgnoreCase(code) && amenities.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Mã tiện nghi " + code + " đã tồn tại");
        }
        a.setCode(code);
        apply(a, req);
        return toResponse(a, amenities.countUsage(id));
    }

    @Transactional
    public AmenityResponse setActive(Integer id, boolean active) {
        Amenity a = find(id);
        a.setActive(active);
        return toResponse(a, amenities.countUsage(id));
    }

    @Transactional
    public void delete(Integer id) {
        Amenity a = find(id);
        long usage = amenities.countUsage(id);
        if (usage > 0) {
            throw ApiException.conflict("Tiện nghi đang được gắn cho " + usage
                    + " loại phòng nên không xoá được. Bạn có thể chuyển sang ngừng dùng");
        }
        amenities.delete(a);
    }

    Amenity find(Integer id) {
        return amenities.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tiện nghi"));
    }

    private static void apply(Amenity a, AmenityRequest req) {
        a.setName(req.name().trim());
        a.setIcon(req.icon() == null || req.icon().isBlank() ? null : req.icon().trim());
        if (req.active() != null) {
            a.setActive(req.active());
        }
    }

    static AmenityResponse toResponse(Amenity a, long usage) {
        return new AmenityResponse(a.getId(), a.getCode(), a.getName(), a.getIcon(), a.isActive(), usage);
    }

    public record AmenityRequest(
            @NotBlank(message = "Vui lòng nhập mã tiện nghi") @Size(max = 30, message = "Mã tối đa 30 ký tự")
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã chỉ gồm chữ không dấu, số, gạch dưới") String code,
            @NotBlank(message = "Vui lòng nhập tên tiện nghi") @Size(max = 100) String name,
            @Size(max = 100) String icon,
            Boolean active) {}

    public record AmenityResponse(Integer id, String code, String name, String icon, boolean active, long usageCount) {}
}
