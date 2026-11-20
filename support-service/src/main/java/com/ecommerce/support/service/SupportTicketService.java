package com.ecommerce.support.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.support.client.OrderClient;
import com.ecommerce.support.client.OrderClient.OrderSummary;
import com.ecommerce.support.client.UserIdentityClient;
import com.ecommerce.support.dto.AddTicketMessageRequest;
import com.ecommerce.support.dto.CreateTicketRequest;
import com.ecommerce.support.dto.TicketDetailResponse;
import com.ecommerce.support.dto.TicketMessageResponse;
import com.ecommerce.support.dto.TicketSummaryResponse;
import com.ecommerce.support.entity.MessageAuthorRole;
import com.ecommerce.support.entity.SupportTicket;
import com.ecommerce.support.entity.TicketCategory;
import com.ecommerce.support.entity.TicketMessage;
import com.ecommerce.support.entity.TicketStatus;
import com.ecommerce.support.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private static final Duration SUPPORT_WINDOW = Duration.ofDays(14);
    private static final Set<TicketCategory> ORDER_REQUIRED_CATEGORIES = EnumSet.of(
            TicketCategory.ORDER,
            TicketCategory.DELIVERY,
            TicketCategory.RETURN_REFUND
    );

    private final SupportTicketRepository ticketRepository;
    private final UserIdentityClient userIdentityClient;
    private final OrderClient orderClient;
    private final Clock clock;

    @Transactional
    public TicketDetailResponse createTicket(String userSubject, CreateTicketRequest request) {
        Long userId = userIdentityClient.resolveUserIdBySubject(userSubject).orElse(null);
        TicketCategory category = request.category() != null ? request.category() : TicketCategory.OTHER;

        Long resolvedOrderId = request.orderId();
        String resolvedOrderNumber = trimToNull(request.orderNumber());

        if (ORDER_REQUIRED_CATEGORIES.contains(category) && resolvedOrderId == null && resolvedOrderNumber == null) {
            throw new BadRequestException("Une référence de commande (orderId ou orderNumber) est obligatoire pour cette catégorie.");
        }

        if (resolvedOrderId != null || resolvedOrderNumber != null) {
            OrderSummary order = validateOrderEligibility(userId, resolvedOrderId, resolvedOrderNumber);
            resolvedOrderId = order.id();
            resolvedOrderNumber = order.orderNumber();
        }

        SupportTicket ticket = SupportTicket.builder()
                .userSubject(userSubject)
                .userId(userId)
                .orderId(resolvedOrderId)
                .orderNumber(resolvedOrderNumber)
                .category(category)
                .subject(request.subject().trim())
                .status(TicketStatus.OPEN)
                .build();

        TicketMessage initialMessage = TicketMessage.builder()
                .authorSubject(userSubject)
                .authorRole(MessageAuthorRole.CUSTOMER)
                .content(request.message().trim())
                .build();

        ticket.addMessage(initialMessage);
        SupportTicket saved = ticketRepository.save(ticket);
        return TicketDetailResponse.from(saved);
    }

    private OrderSummary validateOrderEligibility(Long userId, Long orderId, String orderNumber) {
        if (userId == null) {
            throw new BadRequestException("Impossible d'identifier le compte client pour vérifier la commande.");
        }

        OrderSummary order = (orderId != null)
                ? orderClient.getCustomerOrderById(userId, orderId)
                : orderClient.getCustomerOrderByNumber(userId, orderNumber);

        if (orderNumber != null && order.orderNumber() != null && !order.orderNumber().equalsIgnoreCase(orderNumber)) {
            throw new BadRequestException("Le numéro de commande ne correspond pas à l'identifiant fourni.");
        }

        if (order.createdAt() == null) {
            throw new BadRequestException("Date de commande manquante pour vérifier l'éligibilité SAV.");
        }

        Instant deadline = order.createdAt().plus(SUPPORT_WINDOW);
        if (Instant.now(clock).isAfter(deadline)) {
            throw new ConflictException("Le délai de 14 jours pour ouvrir un ticket SAV sur cette commande est dépassé.");
        }

        return order;
    }

    @Transactional(readOnly = true)
    public List<TicketSummaryResponse> listClientTickets(String userSubject, TicketStatus status) {
        List<SupportTicket> tickets = (status != null)
                ? ticketRepository.findByUserSubjectAndStatusOrderByUpdatedAtDesc(userSubject, status)
                : ticketRepository.findByUserSubjectOrderByUpdatedAtDesc(userSubject);
        return tickets.stream()
                .map(TicketSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getClientTicket(String userSubject, Long ticketId) {
        SupportTicket ticket = ticketRepository.findWithMessagesByIdAndUserSubject(ticketId, userSubject)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket SAV introuvable : " + ticketId));
        return TicketDetailResponse.from(ticket);
    }

    @Transactional
    public TicketMessageResponse addClientMessage(String userSubject, Long ticketId, AddTicketMessageRequest request) {
        SupportTicket ticket = ticketRepository.findWithMessagesByIdAndUserSubject(ticketId, userSubject)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket SAV introuvable : " + ticketId));

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ConflictException("Impossible d'ajouter un message sur un ticket clôturé.");
        }

        if (ticket.getStatus() == TicketStatus.WAITING_CUSTOMER || ticket.getStatus() == TicketStatus.RESOLVED) {
            ticket.setStatus(TicketStatus.OPEN);
        }

        TicketMessage message = TicketMessage.builder()
                .authorSubject(userSubject)
                .authorRole(MessageAuthorRole.CUSTOMER)
                .content(request.content().trim())
                .build();

        ticket.addMessage(message);
        SupportTicket saved = ticketRepository.save(ticket);
        TicketMessage persisted = saved.getMessages().getLast();
        return TicketMessageResponse.from(persisted);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}