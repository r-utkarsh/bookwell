package com.bookwell.notification;

import com.bookwell.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

@Component
@ConditionalOnProperty(prefix = "app.notifications", name = "mail-mode", havingValue = "smtp")
public class SmtpMailGateway implements MailGateway {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public SmtpMailGateway(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public DeliveryResult send(NotificationMailComposer.EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(properties.notifications().fromAddress(), properties.notifications().fromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);
            return DeliveryResult.success("SMTP");
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            return DeliveryResult.failure("SMTP", ex.getMessage());
        }
    }

    @Override
    public String mode() {
        return "SMTP";
    }

    public static JavaMailSender createSender(AppProperties properties) {
        var mail = properties.notifications();
        if (mail.smtpHost() == null || mail.smtpHost().isBlank()) {
            throw new IllegalStateException("MAIL_HOST must be configured when MAIL_MODE=smtp.");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.smtpHost());
        sender.setPort(mail.smtpPort());
        sender.setUsername(mail.smtpUsername());
        sender.setPassword(mail.smtpPassword());
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", Boolean.toString(mail.smtpAuth()));
        props.put("mail.smtp.starttls.enable", Boolean.toString(mail.smtpStartTls()));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
