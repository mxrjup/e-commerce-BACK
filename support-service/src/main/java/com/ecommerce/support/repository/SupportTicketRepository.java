package com.ecommerce.support.repository;

import com.ecommerce.support.entity.SupportTicket;
import com.ecommerce.support.entity.TicketStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByUserSubjectOrderByUpdatedAtDesc(String userSubject);

    List<SupportTicket> findByUserSubjectAndStatusOrderByUpdatedAtDesc(String userSubject, TicketStatus status);

    @EntityGraph(attributePaths = "messages")
    Optional<SupportTicket> findWithMessagesByIdAndUserSubject(Long id, String userSubject);
}