package com.bookwell.admin;

import com.bookwell.booking.AppointmentStatus;
import com.bookwell.booking.BookingDtos;
import com.bookwell.booking.BookingService;
import com.bookwell.common.api.PageResponse;
import com.bookwell.common.security.AuthUser;
import com.bookwell.notification.NotificationStatus;
import com.bookwell.notification.NotificationType;
import com.bookwell.provider.ProviderStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final BookingService bookingService;

    public AdminController(AdminService adminService, BookingService bookingService) {
        this.adminService = adminService;
        this.bookingService = bookingService;
    }

    @GetMapping("/stats")
    public AdminDtos.PlatformStats stats() {
        return adminService.stats();
    }

    @GetMapping("/providers")
    public PageResponse<AdminDtos.AdminProviderDto> providers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProviderStatus status) {
        return adminService.providers(page, size, q, status);
    }

    @PatchMapping("/providers/{providerId}/status")
    public AdminDtos.AdminProviderDto updateProviderStatus(
            @PathVariable UUID providerId,
            @Valid @RequestBody AdminDtos.UpdateProviderStatusRequest request) {
        return adminService.updateProviderStatus(providerId, request);
    }

    @GetMapping("/customers")
    public PageResponse<AdminDtos.AdminCustomerDto> customers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled) {
        return adminService.customers(page, size, q, enabled);
    }

    @PatchMapping("/customers/{userId}/status")
    public AdminDtos.AdminCustomerDto updateCustomerStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminDtos.UpdateUserStatusRequest request) {
        return adminService.updateCustomerStatus(userId, request);
    }

    @GetMapping("/bookings")
    public PageResponse<BookingDtos.AppointmentDto> bookings(
            @AuthenticationPrincipal AuthUser actor,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookingService.searchAdmin(actor, providerId, customerId, status, from, to, page, size);
    }

    @GetMapping("/notifications")
    public PageResponse<AdminDtos.AdminNotificationDto> notifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationType type) {
        return adminService.notifications(page, size, status, type);
    }

    @PostMapping("/notifications/{notificationId}/retry")
    public ResponseEntity<AdminDtos.AdminNotificationDto> retryNotification(
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(adminService.retryNotification(notificationId));
    }
}
