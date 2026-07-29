package com.magzinemedia.contactapi.web;

import com.magzinemedia.contactapi.model.ContactSubmission;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

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
            log.info("Email notifications not configured; skipping admin email for submission {}", submission.getId());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(notifyTo);
            message.setSubject("New contact inquiry from " + submission.getName());
            message.setText(buildAdminBody(submission));
            mailSender.send(message);
            log.info("Sent admin notification email for submission {}", submission.getId());
        } catch (Exception e) {
            log.error("Failed to send admin notification email for submission {}", submission.getId(), e);
        }
    }

    @Async
    public void sendCustomerAcknowledgement(ContactSubmission submission) {
        if (!enabled || fromAddress.isBlank() || submission.getEmail() == null || submission.getEmail().isBlank()) {
            log.info("Email notifications not configured; skipping acknowledgement email for submission {}", submission.getId());
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromAddress, "Magzine Media");
            helper.setTo(submission.getEmail());
            helper.setSubject("We've received your inquiry - Magzine Media");
            helper.setText(buildAcknowledgementHtml(submission), true);
            mailSender.send(mimeMessage);
            log.info("Sent acknowledgement email to {} for submission {}", submission.getEmail(), submission.getId());
        } catch (Exception e) {
            log.error("Failed to send acknowledgement email for submission {}", submission.getId(), e);
        }
    }

    private String buildAdminBody(ContactSubmission submission) {
        return "New inquiry received on Magzine Media\n\n"
            + "Name: " + submission.getName() + "\n"
            + "Email: " + submission.getEmail() + "\n"
            + "Phone: " + orDash(submission.getFullPhone()) + "\n"
            + "Company: " + orDash(submission.getCompany()) + "\n"
            + "Notes: " + orDash(submission.getNotes()) + "\n";
    }

    private String buildAcknowledgementHtml(ContactSubmission submission) {
        String name = HtmlUtils.htmlEscape(orDash(submission.getName()));
        String notes = HtmlUtils.htmlEscape(submission.getNotes() == null || submission.getNotes().isBlank()
            ? "No additional details provided."
            : submission.getNotes());

        return """
            <!DOCTYPE html>
            <html>
              <body style="margin:0;padding:0;background:#f5f6fb;font-family:Arial,Helvetica,sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f6fb;padding:32px 0;">
                  <tr>
                    <td align="center">
                      <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:16px;overflow:hidden;">
                        <tr>
                          <td style="background:#0a081f;padding:28px 32px;">
                            <span style="color:#ffffff;font-size:20px;font-weight:800;letter-spacing:2px;text-transform:uppercase;">Magzine Media</span>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 6px;color:#b8860b;font-size:12px;letter-spacing:2px;text-transform:uppercase;font-weight:bold;">Thank you</p>
                            <h1 style="margin:0 0 16px;color:#0a081f;font-size:22px;line-height:1.3;">Hi %s, we've received your inquiry</h1>
                            <p style="margin:0 0 16px;color:#5b5f7a;font-size:15px;line-height:1.6;">
                              Thanks for reaching out to Magzine Media. Our team has received your message and <strong style="color:#0a081f;">will contact you shortly</strong>.
                            </p>
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f9fafb;border-radius:12px;">
                              <tr>
                                <td style="padding:16px 18px;color:#0a081f;font-size:14px;line-height:1.7;">
                                  <strong>Your message:</strong><br>%s
                                </td>
                              </tr>
                            </table>
                            <p style="margin:20px 0 0;color:#5b5f7a;font-size:14px;line-height:1.6;">
                              If anything is urgent, just reply directly to this email and we'll pick it up.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#0a081f;padding:20px 32px;">
                            <p style="margin:0;color:rgba(255,255,255,.7);font-size:12px;">© 2026 Magzine Media. Built for founders, public figures, and modern brands worldwide.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(name, notes);
    }

    private String orDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
