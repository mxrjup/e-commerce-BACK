package com.ecommerce.support.controller;

import com.ecommerce.support.dto.AdminReplyRequest;
import com.ecommerce.support.dto.AdminTicketDetailResponse;
import com.ecommerce.support.dto.AdminTicketSummaryResponse;
import com.ecommerce.support.dto.UpdateTicketStatusRequest;
import com.ecommerce.support.entity.TicketCategory;
import com.ecommerce.support.entity.TicketStatus;
import com.ecommerce.support.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Back-office authorization is enforced by the API gateway on /api/admin/**. */
@RestController
@RequestMapping("/api/admin/support/tickets")
@RequiredArgsConstructor
public class AdminSupportTicketController {

    private final SupportTicketService supportTicketService;

    @GetMapping
    public ResponseEntity<List<AdminTicketSummaryResponse>> listTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketCategory category) {
        return ResponseEntity.ok(supportTicketService.listAdminTickets(status, category));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<AdminTicketDetailResponse> getTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(supportTicketService.getAdminTicket(ticketId));
    }

    @PostMapping("/{ticketId}/reply")
    public ResponseEntity<AdminTicketDetailResponse> replyToTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long ticketId,
            @Valid @RequestBody AdminReplyRequest request) {
        AdminTicketDetailResponse updated = supportTicketService.replyAsAdmin(jwt.getSubject(), ticketId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @PatchMapping("/{ticketId}/status")
    public ResponseEntity<AdminTicketDetailResponse> updateTicketStatus(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketStatusRequest request) {
        return ResponseEntity.ok(supportTicketService.updateTicketStatus(ticketId, request));
    }
}