package com.bookwell.notification;

public interface MailGateway {

    DeliveryResult send(NotificationMailComposer.EmailMessage message);

    String mode();

    record DeliveryResult(boolean success, String mode, String error) {
        public static DeliveryResult success(String mode) {
            return new DeliveryResult(true, mode, null);
        }

        public static DeliveryResult failure(String mode, String error) {
            return new DeliveryResult(false, mode, error);
        }
    }
}
