package com.homestay.settings;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE', 'PRICING_VIEW')")
    public SettingsService.SettingsResponse get() {
        return service.get();
    }

    @PutMapping("/homestay")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public SettingsService.HomestayDto updateHomestay(@Valid @RequestBody SettingsService.HomestayDto dto) {
        return service.updateHomestay(dto);
    }

    @PostMapping("/policies")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public SettingsService.PolicyDto createPolicy(@Valid @RequestBody SettingsService.PolicyRequest req) {
        return service.createPolicy(req);
    }
}
