package com.ecommerce.support.controller;

import com.ecommerce.support.dto.AddTicketMessageRequest;
import com.ecommerce.support.dto.CreateTicketRequest;
import com.ecommerce.support.dto.TicketDetailResponse;
import com.ecommerce.support.dto.TicketMessageResponse;
import com.ecommerce.support.dto.TicketSummaryResponse;
import com.ecommerce.support.entity.TicketStatus;
import com.ecommerce.support.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support/tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    @PostMapping
    public ResponseEntity<TicketDetailResponse> createTicket(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTicketRequest request) {
        TicketDetailResponse created = supportTicketService.createTicket(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<TicketSummaryResponse>> listMyTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) TicketStatus status) {
        return ResponseEntity.ok(supportTicketService.listClientTickets(jwt.getSubject(), status));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketDetailResponse> getMyTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long ticketId) {
        return ResponseEntity.ok(supportTicketService.getClientTicket(jwt.getSubject(), ticketId));
    }

    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<TicketMessageResponse> addMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long ticketId,
            @Valid @RequestBody AddTicketMessageRequest request) {
        TicketMessageResponse message = supportTicketService.addClientMessage(jwt.getSubject(), ticketId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }
}