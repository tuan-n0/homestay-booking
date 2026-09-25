package com.homestay.room;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService service;

    public RoomController(RoomService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATALOG_VIEW', 'ROOM_STATUS_VIEW')")
    public List<RoomService.RoomResponse> list(@RequestParam(required = false) Integer roomTypeId,
                                               @RequestParam(required = false) Short floor,
                                               @RequestParam(required = false) RoomStatus status) {
        return service.list(roomTypeId, floor, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public RoomService.RoomResponse create(@Valid @RequestBody RoomService.RoomRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public RoomService.RoomResponse update(@PathVariable Integer id, @Valid @RequestBody RoomService.RoomRequest req,
                                           @RequestParam(defaultValue = "false") boolean confirm) {
        return service.update(id, req, confirm);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROOM_STATUS_UPDATE')")
    public RoomService.RoomResponse changeStatus(@PathVariable Integer id,
                                                 @Valid @RequestBody RoomService.StatusRequest req) {
        return service.changeStatus(id, req);
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAuthority('ROOM_STATUS_VIEW')")
    public List<RoomService.HistoryResponse> history(@PathVariable Integer id) {
        return service.history(id);
    }
}
