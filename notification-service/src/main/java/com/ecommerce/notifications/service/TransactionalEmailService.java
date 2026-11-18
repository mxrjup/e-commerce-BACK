package com.ecommerce.notifications.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionalEmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@ecommerce.local}")
    private String fromAddress;

    public void sendOrderConfirmation(String toEmail, String firstName, String orderNumber, BigDecimal totalInclTax) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", defaultName(firstName));
        variables.put("orderNumber", orderNumber);
        variables.put("formattedAmount", totalInclTax != null
                ? NumberFormat.getCurrencyInstance(Locale.FRANCE).format(totalInclTax)
                : null);

        sendTemplatedEmail(
                toEmail,
                "Confirmation de votre commande " + orderNumber,
                "email/order-confirmation",
                variables
        );
    }

    public void sendOrderShipped(String toEmail, String firstName, String orderNumber) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", defaultName(firstName));
        variables.put("orderNumber", orderNumber);

        sendTemplatedEmail(
                toEmail,
                "Votre commande " + orderNumber + " a été expédiée",
                "email/order-shipped",
                variables
        );
    }

    public void sendSupportAdminReply(String toEmail, String firstName, Long ticketId, String ticketSubject) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", defaultName(firstName));
        variables.put("ticketId", ticketId);
        variables.put("ticketSubject", ticketSubject);

        sendTemplatedEmail(
                toEmail,
                "Nouvelle réponse concernant votre demande SAV #" + ticketId,
                "email/support-reply",
                variables
        );
    }

    public void sendTemplatedEmail(
            String toEmail,
            String subject,
            String templateName,
            Map<String, Object> variables) {
        if (!mailEnabled) {
            log.debug("Envoi d'email désactivé (app.mail.enabled=false) : template={}, to={}", templateName, toEmail);
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Impossible d'envoyer l'email '{}' : adresse destinataire manquante.", subject);
            return;
        }

        try {
            Context context = new Context(Locale.FRANCE);
            context.setVariables(variables);
            String htmlContent = templateEngine.process(templateName, context);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Email transactionnel envoyé : template={}, to={}, subject={}", templateName, toEmail, subject);
        } catch (MessagingException | RuntimeException e) {
            log.error("Échec de l'envoi de l'email transactionnel (template={}, to={}) : {}",
                    templateName, toEmail, e.getMessage());
        }
    }

    private String defaultName(String firstName) {
        return (firstName != null && !firstName.isBlank()) ? firstName : "Client";
    }
}