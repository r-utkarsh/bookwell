package com.bookwell.provider;

import com.bookwell.common.api.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.bookwell.common.security.AuthUser;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ProviderController {

    private final ProviderCatalogService catalogService;
    private final ProviderManagementService managementService;

    public ProviderController(ProviderCatalogService catalogService,
                              ProviderManagementService managementService) {
        this.catalogService = catalogService;
        this.managementService = managementService;
    }

    @GetMapping("/providers")
    public PageResponse<ProviderDtos.ProviderCard> providers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category) {
        return catalogService.search(page, size, q, category);
    }

    @GetMapping("/providers/categories")
    public List<String> categories() {
        return catalogService.categories();
    }

    @GetMapping("/providers/{providerId}")
    public ProviderDtos.ProviderDetail provider(@PathVariable UUID providerId) {
        return catalogService.get(providerId);
    }

    @GetMapping("/provider/profile")
    public ProviderDtos.ProviderDashboardProfile profile(@AuthenticationPrincipal AuthUser actor) {
        return managementService.profile(actor.id());
    }

    @PutMapping("/provider/profile")
    public ProviderDtos.ProviderDashboardProfile updateProfile(@AuthenticationPrincipal AuthUser actor,
                                                               @Valid @RequestBody ProviderDtos.UpdateProviderProfileRequest request) {
        return managementService.updateProfile(actor.id(), request);
    }

    @GetMapping("/provider/stats")
    public ProviderDtos.ProviderDashboardStats stats(@AuthenticationPrincipal AuthUser actor) {
        return managementService.stats(actor.id());
    }

    @GetMapping("/provider/services")
    public List<ProviderDtos.ServiceDto> services(@AuthenticationPrincipal AuthUser actor) {
        return managementService.services(actor.id());
    }

    @PostMapping("/provider/services")
    public ResponseEntity<ProviderDtos.ServiceDto> createService(
            @AuthenticationPrincipal AuthUser actor,
            @Valid @RequestBody ProviderDtos.ServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(managementService.createService(actor.id(), request));
    }

    @PutMapping("/provider/services/{serviceId}")
    public ProviderDtos.ServiceDto updateService(@AuthenticationPrincipal AuthUser actor,
                                                 @PathVariable UUID serviceId,
                                                 @Valid @RequestBody ProviderDtos.ServiceRequest request) {
        return managementService.updateService(actor.id(), serviceId, request);
    }

    @DeleteMapping("/provider/services/{serviceId}")
    public ResponseEntity<Void> deactivateService(@AuthenticationPrincipal AuthUser actor,
                                                  @PathVariable UUID serviceId) {
        managementService.deactivateService(actor.id(), serviceId);
        return ResponseEntity.noContent().build();
    }
}
