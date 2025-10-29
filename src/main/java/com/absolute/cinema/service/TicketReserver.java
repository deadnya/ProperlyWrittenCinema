package com.absolute.cinema.service;

import com.absolute.cinema.dto.TicketDTO;
import com.absolute.cinema.entity.Session;
import com.absolute.cinema.entity.Ticket;
import com.absolute.cinema.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TicketReserver {

    private final TicketService ticketService;

    public void createForSession(Session session) {
        ticketService.createTicketsForSession(session);
    }

    public void deleteForSession(UUID sessionId) {
        ticketService.deleteTicketsBySessionId(sessionId);
    }

    public List<TicketDTO> getForSession(UUID sessionId, Ticket.Status status) {
        return ticketService.getTicketsForSession(sessionId, status);
    }

    public TicketDTO reserve(UUID id, User user) {
        return ticketService.reserveTicket(id, user);
    }

    public TicketDTO cancelReservation(UUID id, User user) {
        return ticketService.cancelReserveForTicket(id, user);
    }
}
