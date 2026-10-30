package com.bookwell.notification;

import com.bookwell.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@ConditionalOnProperty(prefix = "app.notifications", name = "mail-mode", havingValue = "smtp")
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(AppProperties properties) {
        return SmtpMailGateway.createSender(properties);
    }
}
