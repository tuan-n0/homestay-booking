package com.homestay.user;

import java.util.List;
import java.util.Map;

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

import com.homestay.common.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserAdminController {

    private final UserAdminService service;
    private final PermissionService permissionService;

    public UserAdminController(UserAdminService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public PageResponse<UserAdminService.UserResponse> search(@RequestParam(required = false) String keyword,
                                                              @RequestParam(defaultValue = "0") int page) {
        return service.search(keyword, page);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public List<UserAdminService.RoleResponse> roles() {
        return service.roles();
    }

    @GetMapping("/permission-matrix")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public List<Map<String, Object>> matrix() {
        return permissionService.matrix();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserAdminService.UserResponse create(@Valid @RequestBody UserAdminService.UserRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserAdminService.UserResponse update(@PathVariable Long id, @Valid @RequestBody UserAdminService.UserRequest req) {
        return service.update(id, req);
    }
}
