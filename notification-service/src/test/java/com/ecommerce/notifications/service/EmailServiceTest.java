package com.ecommerce.notifications.service;

import com.ecommerce.common.event.OrderPaidEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@ecommerce.test");
    }

    @Test
    @DisplayName("Should render template and send order confirmation email")
    void sendOrderConfirmation_ShouldSendEmail() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/order-confirmation"), any(Context.class)))
                .thenReturn("<html><body>Confirmation</body></html>");

        List<OrderPaidEvent.Item> items = List.of(new OrderPaidEvent.Item(10L, 2));
        boolean result = emailService.sendOrderConfirmation(
                "client@example.com",
                "Alice",
                "CMD-2026-001",
                new BigDecimal("99.90"),
                items
        );

        assertThat(result).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/order-confirmation"), contextCaptor.capture());
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("customerName")).isEqualTo("Alice");
        assertThat(capturedContext.getVariable("orderNumber")).isEqualTo("CMD-2026-001");
        assertThat(capturedContext.getVariable("totalAmount")).asString().contains("99,90");
        assertThat(capturedContext.getVariable("items")).isEqualTo(items);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should render template and send order shipped email")
    void sendOrderShipped_ShouldSendEmail() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/order-shipped"), any(Context.class)))
                .thenReturn("<html><body>Expédié</body></html>");

        boolean result = emailService.sendOrderShipped(
                "client@example.com",
                "Bob",
                "CMD-2026-002",
                "Chronopost",
                "TRK-987654"
        );

        assertThat(result).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/order-shipped"), contextCaptor.capture());
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("customerName")).isEqualTo("Bob");
        assertThat(capturedContext.getVariable("orderNumber")).isEqualTo("CMD-2026-002");
        assertThat(capturedContext.getVariable("carrier")).isEqualTo("Chronopost");
        assertThat(capturedContext.getVariable("trackingNumber")).isEqualTo("TRK-987654");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should render template and send support reply email")
    void sendSupportReply_ShouldSendEmail() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/support-reply"), any(Context.class)))
                .thenReturn("<html><body>Réponse SAV</body></html>");

        boolean result = emailService.sendSupportReply(
                "client@example.com",
                "Charlie",
                42L,
                "Colis endommagé"
        );

        assertThat(result).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/support-reply"), contextCaptor.capture());
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("customerName")).isEqualTo("Charlie");
        assertThat(capturedContext.getVariable("ticketId")).isEqualTo(42L);
        assertThat(capturedContext.getVariable("ticketSubject")).isEqualTo("Colis endommagé");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should not send email when mail is disabled")
    void sendHtmlEmail_WhenDisabled_ShouldReturnFalse() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        boolean result = emailService.sendOrderShipped(
                "client@example.com",
                "Bob",
                "CMD-2026-002",
                "Chronopost",
                "TRK-987654"
        );

        assertThat(result).isFalse();
        verifyNoInteractions(templateEngine);
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("Should not send email when recipient email is empty")
    void sendHtmlEmail_WhenRecipientEmpty_ShouldReturnFalse() {
        boolean result = emailService.sendSupportReply(
                "",
                "Charlie",
                42L,
                "Colis"
        );

        assertThat(result).isFalse();
        verifyNoInteractions(templateEngine);
        verifyNoInteractions(mailSender);
    }
}
