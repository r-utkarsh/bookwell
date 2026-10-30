package com.bookwell.common.exception;

public class BookingConflictException extends ApiException {
    public BookingConflictException() {
        super(409, "SLOT_UNAVAILABLE",
                "That time was just booked by someone else. Please choose another available time.");
    }

    public BookingConflictException(String message) {
        super(409, "SLOT_UNAVAILABLE", message);
    }
}
