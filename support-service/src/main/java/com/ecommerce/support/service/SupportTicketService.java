package com.ecommerce.support.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
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
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final UserIdentityClient userIdentityClient;

    @Transactional
    public TicketDetailResponse createTicket(String userSubject, CreateTicketRequest request) {
        Long userId = userIdentityClient.resolveUserIdBySubject(userSubject).orElse(null);

        SupportTicket ticket = SupportTicket.builder()
                .userSubject(userSubject)
                .userId(userId)
                .orderId(request.orderId())
                .orderNumber(trimToNull(request.orderNumber()))
                .category(request.category() != null ? request.category() : TicketCategory.OTHER)
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