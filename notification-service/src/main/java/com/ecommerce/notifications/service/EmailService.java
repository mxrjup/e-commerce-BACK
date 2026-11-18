package com.ecommerce.notifications.service;

import com.ecommerce.common.event.OrderPaidEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Year;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.from:noreply@ecommerce.test}")
    private String fromAddress;

    /**
     * Envoie l'e-mail de confirmation de commande à partir du gabarit 'mail/order-confirmation'.
     */
    public boolean sendOrderConfirmation(
            String toEmail,
            String customerName,
            String orderNumber,
            BigDecimal totalInclTax,
            List<OrderPaidEvent.Item> items) {
        String formattedAmount = totalInclTax != null
                ? NumberFormat.getCurrencyInstance(Locale.FRANCE).format(totalInclTax)
                : "0,00 €";

        Context context = new Context(Locale.FRANCE);
        context.setVariable("customerName", customerName != null ? customerName : "Client");
        context.setVariable("orderNumber", orderNumber);
        context.setVariable("totalAmount", formattedAmount);
        context.setVariable("items", items != null ? items : List.of());
        context.setVariable("year", Year.now().getValue());

        String subject = "Confirmation de votre commande " + orderNumber;
        return sendHtmlEmail(toEmail, subject, "mail/order-confirmation", context);
    }

    /**
     * Envoie l'e-mail d'expédition de commande à partir du gabarit 'mail/order-shipped'.
     */
    public boolean sendOrderShipped(
            String toEmail,
            String customerName,
            String orderNumber,
            String carrier,
            String trackingNumber) {
        Context context = new Context(Locale.FRANCE);
        context.setVariable("customerName", customerName != null ? customerName : "Client");
        context.setVariable("orderNumber", orderNumber);
        context.setVariable("carrier", carrier != null && !carrier.isBlank() ? carrier : "Notre transporteur partenaire");
        context.setVariable("trackingNumber", trackingNumber != null && !trackingNumber.isBlank() ? trackingNumber : null);
        context.setVariable("year", Year.now().getValue());

        String subject = "Votre commande " + orderNumber + " a été expédiée";
        return sendHtmlEmail(toEmail, subject, "mail/order-shipped", context);
    }

    /**
     * Envoie l'e-mail de réponse SAV à partir du gabarit 'mail/support-reply'.
     */
    public boolean sendSupportReply(
            String toEmail,
            String customerName,
            Long ticketId,
            String ticketSubject) {
        Context context = new Context(Locale.FRANCE);
        context.setVariable("customerName", customerName != null ? customerName : "Client");
        context.setVariable("ticketId", ticketId);
        context.setVariable("ticketSubject", ticketSubject != null && !ticketSubject.isBlank() ? ticketSubject : "Votre demande");
        context.setVariable("year", Year.now().getValue());

        String subject = "Nouvelle réponse du support - Ticket #" + ticketId;
        return sendHtmlEmail(toEmail, subject, "mail/support-reply", context);
    }

    private boolean sendHtmlEmail(String toEmail, String subject, String templateName, Context context) {
        if (!mailEnabled) {
            log.info("Envoi d'email désactivé (app.mail.enabled=false). Email ignoré pour to={}, template={}",
                    toEmail, templateName);
            return false;
        }

        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Adresse email de destination vide, envoi annulé (template={})", templateName);
            return false;
        }

        try {
            String htmlBody = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Email transactionnel envoyé avec succès à {} (template={}, subject={})",
                    toEmail, templateName, subject);
            return true;
        } catch (MessagingException e) {
            log.error("Erreur lors de la préparation du message MIME pour {} : {}", toEmail, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Erreur inattendue lors de l'envoi de l'email à {} (template={}) : {}",
                    toEmail, templateName, e.getMessage(), e);
            return false;
        }
    }
}
