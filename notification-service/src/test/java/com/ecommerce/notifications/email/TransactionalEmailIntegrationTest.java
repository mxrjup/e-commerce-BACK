package com.ecommerce.notifications.email;

import com.ecommerce.notifications.service.TransactionalEmailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "app.mail.enabled=true",
        "app.mail.from=noreply@shop.test"
})
@ActiveProfiles("local-h2")
class TransactionalEmailIntegrationTest {

    @Autowired
    private TransactionalEmailService transactionalEmailService;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation ->
                new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void shouldRenderAndSendOrderConfirmationEmail() throws Exception {
        transactionalEmailService.sendOrderConfirmation(
                "alice@example.com",
                "Alice",
                "CMD-2026-000042",
                new BigDecimal("129.90")
        );

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Confirmation de votre commande CMD-2026-000042");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldRenderAndSendOrderShippedEmail() throws Exception {
        transactionalEmailService.sendOrderShipped(
                "alice@example.com",
                "Alice",
                "CMD-2026-000042"
        );

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Votre commande CMD-2026-000042 a été expédiée");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldRenderAndSendSupportReplyEmail() throws Exception {
        transactionalEmailService.sendSupportAdminReply(
                "alice@example.com",
                "Alice",
                77L,
                "Produit manquant"
        );

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Nouvelle réponse concernant votre demande SAV #77");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldSkipSendingWhenRecipientEmailIsBlank() {
        transactionalEmailService.sendOrderConfirmation(
                "  ",
                "Alice",
                "CMD-2026-000042",
                new BigDecimal("50.00")
        );

        verifyNoInteractions(mailSender);
    }
}