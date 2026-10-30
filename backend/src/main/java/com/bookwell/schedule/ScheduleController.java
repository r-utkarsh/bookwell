package com.bookwell.schedule;

import com.bookwell.common.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/provider/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ScheduleDtos.ScheduleOverview overview(
            @AuthenticationPrincipal AuthUser actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return scheduleService.overview(actor.id(), from);
    }

    @PutMapping("/weekly")
    public List<ScheduleDtos.WeeklyIntervalDto> replaceWeekly(
            @AuthenticationPrincipal AuthUser actor,
            @Valid @RequestBody ScheduleDtos.ReplaceWeeklyScheduleRequest request) {
        return scheduleService.replaceWeekly(actor.id(), request);
    }

    @PostMapping("/exceptions")
    public ResponseEntity<ScheduleDtos.ExceptionDto> createException(
            @AuthenticationPrincipal AuthUser actor,
            @Valid @RequestBody ScheduleDtos.CreateExceptionRequest request) {
        return ResponseEntity.status(201).body(scheduleService.createException(actor.id(), request));
    }

    @DeleteMapping("/exceptions/{exceptionId}")
    public ResponseEntity<Void> deleteException(@AuthenticationPrincipal AuthUser actor,
                                                @PathVariable UUID exceptionId) {
        scheduleService.deleteException(actor.id(), exceptionId);
        return ResponseEntity.noContent().build();
    }
}
