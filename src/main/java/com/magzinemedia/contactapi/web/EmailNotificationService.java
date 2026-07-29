package com.magzinemedia.contactapi.web;

import com.magzinemedia.contactapi.model.ContactSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String notifyTo;
    private final String fromAddress;

    public EmailNotificationService(
        JavaMailSender mailSender,
        @Value("${app.mail.enabled:false}") boolean enabled,
        @Value("${app.mail.notify-to:}") String notifyTo,
        @Value("${spring.mail.username:}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.notifyTo = notifyTo;
        this.fromAddress = fromAddress;
    }

    @Async
    public void notifyNewContact(ContactSubmission submission) {
        if (!enabled || notifyTo.isBlank() || fromAddress.isBlank()) {
            log.info("Email notifications not configured; skipping email for submission {}", submission.getId());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(notifyTo);
            message.setSubject("New contact inquiry from " + submission.getName());
            message.setText(buildBody(submission));
            mailSender.send(message);
            log.info("Sent contact notification email for submission {}", submission.getId());
        } catch (Exception e) {
            log.error("Failed to send contact notification email for submission {}", submission.getId(), e);
        }
    }

    private String buildBody(ContactSubmission submission) {
        return "New inquiry received on Magzine Media\n\n"
            + "Name: " + submission.getName() + "\n"
            + "Email: " + submission.getEmail() + "\n"
            + "Phone: " + orDash(submission.getFullPhone()) + "\n"
            + "Company: " + orDash(submission.getCompany()) + "\n"
            + "Notes: " + orDash(submission.getNotes()) + "\n";
    }

    private String orDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
