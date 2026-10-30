package com.bookwell.booking;

import com.bookwell.common.api.PageResponse;
import com.bookwell.common.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final BookingService bookingService;

    public AppointmentController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BookingDtos.AppointmentDto> create(
            @AuthenticationPrincipal AuthUser actor,
            @Valid @RequestBody BookingDtos.CreateAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(actor, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public PageResponse<BookingDtos.AppointmentDto> mine(
            @AuthenticationPrincipal AuthUser actor,
            @RequestParam(defaultValue = "upcoming") String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return bookingService.listForCustomer(actor, scope, page, size);
    }

    @GetMapping("/{appointmentId}")
    public BookingDtos.AppointmentDto get(@AuthenticationPrincipal AuthUser actor,
                                          @PathVariable UUID appointmentId) {
        return bookingService.get(actor, appointmentId);
    }

    @PostMapping("/{appointmentId}/cancel")
    public BookingDtos.AppointmentDto cancel(@AuthenticationPrincipal AuthUser actor,
                                             @PathVariable UUID appointmentId,
                                             @RequestBody(required = false) @Valid BookingDtos.CancelRequest request) {
        return bookingService.cancel(actor, appointmentId, request == null ? null : request.reason());
    }

    @PostMapping("/{appointmentId}/reschedule")
    public BookingDtos.AppointmentDto reschedule(@AuthenticationPrincipal AuthUser actor,
                                                 @PathVariable UUID appointmentId,
                                                 @Valid @RequestBody BookingDtos.RescheduleRequest request) {
        return bookingService.reschedule(actor, appointmentId, request);
    }

    @PostMapping("/{appointmentId}/complete")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public BookingDtos.AppointmentDto complete(@AuthenticationPrincipal AuthUser actor,
                                               @PathVariable UUID appointmentId) {
        return bookingService.markCompleted(actor, appointmentId);
    }

    @PostMapping("/{appointmentId}/no-show")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public BookingDtos.AppointmentDto noShow(@AuthenticationPrincipal AuthUser actor,
                                             @PathVariable UUID appointmentId) {
        return bookingService.markNoShow(actor, appointmentId);
    }
}
