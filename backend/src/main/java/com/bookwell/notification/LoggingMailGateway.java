package com.bookwell.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notifications", name = "mail-mode", havingValue = "log", matchIfMissing = true)
public class LoggingMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailGateway.class);

    @Override
    public DeliveryResult send(NotificationMailComposer.EmailMessage message) {
        log.info("Mail (log mode, not delivered): subject='{}' recipient='{}'", message.subject(), message.to());
        log.debug("Mail (log mode) body:\n{}", message.text());
        return DeliveryResult.success("LOG");
    }

    @Override
    public String mode() {
        return "LOG";
    }
}
