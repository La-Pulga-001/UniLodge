package com.unilodge.service.mail;

import com.unilodge.service.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Sends transactional email. In development the console transport prints
 * messages to the server log so the whole flow can be exercised without an
 * inbox or SMTP credentials.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final AppProperties properties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public EmailService(AppProperties properties, ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.properties = properties;
        this.mailSenderProvider = mailSenderProvider;
    }

    public boolean isConsoleMode() {
        return properties.getMail().isConsoleMode();
    }

    public void sendOtp(String to, String purposeLabel, String code, Duration validFor) {
        String subject = "UniLodge verification code";
        String body = """
                Hello,

                Your UniLodge %s code is: %s

                It expires in %d minutes. If you did not request this code you can
                safely ignore this email.

                — UniLodge
                """.formatted(purposeLabel, code, validFor.toMinutes());
        deliver(to, subject, body);
    }

    public void sendAdminInvitation(String to, String inviterName, String inviteLink) {
        String subject = "You have been invited to administer UniLodge";
        String body = """
                Hello,

                %s has invited you to become an administrator of UniLodge, the student
                accommodation platform for Ghana.

                Open this link to verify your email and set your password:

                %s

                This invitation expires in 7 days. If you were not expecting it you can
                ignore this email.

                — UniLodge
                """.formatted(inviterName, inviteLink);
        deliver(to, subject, body);
    }

    public void sendLandlordApproved(String to, String fullName) {
        deliver(to, "Your UniLodge landlord account is approved",
                """
                        Hello %s,

                        Your landlord account has been approved. You can now publish
                        property listings from your dashboard. Every listing is reviewed
                        by an administrator before students can see it.

                        — UniLodge
                        """.formatted(fullName));
    }

    public void sendLandlordRejected(String to, String fullName, String reason) {
        deliver(to, "Your UniLodge landlord application was not approved",
                """
                        Hello %s,

                        An administrator reviewed your landlord application and it was not
                        approved for the following reason:

                        %s

                        You can contact support if you believe this was a mistake.

                        — UniLodge
                        """.formatted(fullName, reason == null || reason.isBlank() ? "No reason provided." : reason));
    }

    public void sendPropertyDecision(String to, String fullName, String title, boolean approved, String note) {
        String outcome = approved ? "approved" : "not approved";
        deliver(to, "Your UniLodge listing was " + outcome,
                """
                        Hello %s,

                        Your listing "%s" was %s.

                        %s

                        — UniLodge
                        """.formatted(fullName, title, outcome,
                        note == null || note.isBlank() ? "" : "Administrator note: " + note));
    }

    private void deliver(String to, String subject, String body) {
        if (properties.getMail().isConsoleMode()) {
            logToConsole(to, subject, body);
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (!isConfigured(sender)) {
            log.warn("SMTP mode is enabled but no mail host is configured. "
                    + "Logging the message instead; set MAIL_HOST to deliver real email.");
            logToConsole(to, subject, body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }

    private boolean isConfigured(JavaMailSender sender) {
        if (sender == null) {
            return false;
        }
        if (sender instanceof JavaMailSenderImpl implementation) {
            return StringUtils.hasText(implementation.getHost());
        }
        return true;
    }

    private void logToConsole(String to, String subject, String body) {
        log.info("""

                ============================================================
                 UniLodge mail (console transport)
                 To      : {}
                 Subject : {}
                ------------------------------------------------------------
                {}
                ============================================================
                """, to, subject, body);
    }
}
