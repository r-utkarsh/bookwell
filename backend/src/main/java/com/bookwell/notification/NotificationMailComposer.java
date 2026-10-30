package com.bookwell.notification;

import com.bookwell.booking.Appointment;
import com.bookwell.config.AppProperties;
import com.bookwell.provider.ProviderProfile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class NotificationMailComposer {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' h:mm a z", Locale.ENGLISH);

    private final AppProperties properties;

    public NotificationMailComposer(AppProperties properties) {
        this.properties = properties;
    }

    public EmailMessage compose(NotificationType type, Appointment appointment) {
        ProviderProfile provider = appointment.getProvider();
        ZoneId zone = ZoneId.of(provider.getTimeZone());
        String when = DATE_TIME.format(appointment.getStartAt().atZone(zone));
        String price = formatMoney(appointment.getServicePrice(), appointment.getCurrency());
        String manageUrl = properties.notifications().appPublicUrl()
                + "/account/appointments/" + appointment.getId();
        String greeting = "Hi " + appointment.getCustomerName() + ",";

        String subject;
        String lead;
        switch (type) {
            case BOOKING_CONFIRMATION -> {
                subject = "Your appointment with " + provider.getDisplayName() + " is confirmed";
                lead = "Your appointment is confirmed. Here are the details:";
            }
            case CANCELLATION -> {
                subject = "Your appointment with " + provider.getDisplayName() + " was cancelled";
                lead = "Your appointment has been cancelled. If this was unexpected, please contact the provider.";
            }
            case RESCHEDULE -> {
                subject = "Your appointment with " + provider.getDisplayName() + " was rescheduled";
                lead = "Your appointment has been rescheduled. Your updated details are below.";
            }
            case REMINDER -> {
                subject = "Reminder: your appointment with " + provider.getDisplayName() + " is coming up";
                lead = "This is a friendly reminder about your upcoming appointment.";
            }
            default -> throw new IllegalArgumentException("Unsupported notification type");
        }

        String text = greeting + "\n\n" + lead + "\n\n"
                + "Service: " + appointment.getServiceName() + "\n"
                + "When: " + when + " (" + provider.getTimeZone() + ")\n"
                + "Duration: " + appointment.getServiceDurationMinutes() + " minutes\n"
                + "Price: " + price + "\n"
                + "Provider: " + provider.getBusinessName() + "\n"
                + (provider.getAddress().isBlank() ? "" : "Address: " + provider.getAddress() + ", " + provider.getCity() + "\n")
                + "\nManage your booking: " + manageUrl + "\n\n"
                + "Thank you,\nBookwell";

        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#1d2b24;max-width:560px;margin:0 auto;padding:24px">
                  <p style="font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:#6b7a70">Bookwell</p>
                  <h1 style="font-size:24px;line-height:1.3;margin:8px 0 16px">%s</h1>
                  <p>%s</p>
                  <table style="border-collapse:collapse;width:100%%;margin:20px 0;background:#f6f3ec;border-radius:12px">
                    <tr><td style="padding:12px 16px;color:#6b7a70">Service</td><td style="padding:12px 16px"><strong>%s</strong></td></tr>
                    <tr><td style="padding:12px 16px;color:#6b7a70">When</td><td style="padding:12px 16px"><strong>%s</strong></td></tr>
                    <tr><td style="padding:12px 16px;color:#6b7a70">Duration</td><td style="padding:12px 16px">%d minutes</td></tr>
                    <tr><td style="padding:12px 16px;color:#6b7a70">Price</td><td style="padding:12px 16px">%s</td></tr>
                    <tr><td style="padding:12px 16px;color:#6b7a70">Provider</td><td style="padding:12px 16px">%s</td></tr>
                  </table>
                  <p><a href="%s" style="display:inline-block;background:#1d2b24;color:#fff;text-decoration:none;padding:12px 20px;border-radius:999px">View or manage booking</a></p>
                  <p style="font-size:12px;color:#6b7a70">You are receiving this email because you made a booking on Bookwell.</p>
                </div>
                """.formatted(
                escape(subject),
                escape(lead),
                escape(appointment.getServiceName()),
                escape(when),
                appointment.getServiceDurationMinutes(),
                escape(price),
                escape(provider.getBusinessName()),
                manageUrl
        );
        return new EmailMessage(appointment.getCustomerEmail(), subject, text, html);
    }

    private String formatMoney(BigDecimal amount, String currency) {
        return currency + " " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    public record EmailMessage(String to, String subject, String text, String html) {
    }
}
