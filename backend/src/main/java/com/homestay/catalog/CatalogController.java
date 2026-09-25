package com.homestay.catalog;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final RoomTypeService roomTypes;
    private final AmenityService amenities;

    public CatalogController(RoomTypeService roomTypes, AmenityService amenities) {
        this.roomTypes = roomTypes;
        this.amenities = amenities;
    }

    // ---------- Loại phòng (S1-06) ----------
    @GetMapping("/room-types")
    @PreAuthorize("hasAuthority('CATALOG_VIEW')")
    public List<RoomTypeService.RoomTypeResponse> listRoomTypes() {
        return roomTypes.list();
    }

    @GetMapping("/room-types/{id}")
    @PreAuthorize("hasAuthority('CATALOG_VIEW')")
    public RoomTypeService.RoomTypeResponse getRoomType(@PathVariable Integer id) {
        return roomTypes.get(id);
    }

    @PostMapping("/room-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public RoomTypeService.RoomTypeResponse createRoomType(@Valid @RequestBody RoomTypeService.RoomTypeRequest req) {
        return roomTypes.create(req);
    }

    @PutMapping("/room-types/{id}")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public RoomTypeService.RoomTypeResponse updateRoomType(@PathVariable Integer id,
                                                           @Valid @RequestBody RoomTypeService.RoomTypeRequest req) {
        return roomTypes.update(id, req);
    }

    @PatchMapping("/room-types/{id}/active")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public RoomTypeService.RoomTypeResponse setRoomTypeActive(@PathVariable Integer id, @RequestParam boolean value) {
        return roomTypes.setActive(id, value);
    }

    @DeleteMapping("/room-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public void deleteRoomType(@PathVariable Integer id) {
        roomTypes.delete(id);
    }

    // ---------- Gắn tiện nghi (S1-08) ----------
    @PostMapping("/room-types/{id}/amenities/{amenityId}")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public RoomTypeService.RoomTypeResponse attach(@PathVariable Integer id, @PathVariable Integer amenityId) {
        return roomTypes.attachAmenity(id, amenityId);
    }

    @DeleteMapping("/room-types/{id}/amenities/{amenityId}")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public RoomTypeService.RoomTypeResponse detach(@PathVariable Integer id, @PathVariable Integer amenityId) {
        return roomTypes.detachAmenity(id, amenityId);
    }

    // ---------- Tiện nghi (S1-08) ----------
    @GetMapping("/amenities")
    @PreAuthorize("hasAuthority('CATALOG_VIEW')")
    public List<AmenityService.AmenityResponse> listAmenities() {
        return amenities.list();
    }

    @PostMapping("/amenities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public AmenityService.AmenityResponse createAmenity(@Valid @RequestBody AmenityService.AmenityRequest req) {
        return amenities.create(req);
    }

    @PutMapping("/amenities/{id}")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public AmenityService.AmenityResponse updateAmenity(@PathVariable Integer id,
                                                        @Valid @RequestBody AmenityService.AmenityRequest req) {
        return amenities.update(id, req);
    }

    @PatchMapping("/amenities/{id}/active")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public AmenityService.AmenityResponse setAmenityActive(@PathVariable Integer id, @RequestParam boolean value) {
        return amenities.setActive(id, value);
    }

    @DeleteMapping("/amenities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public void deleteAmenity(@PathVariable Integer id) {
        amenities.delete(id);
    }
}
