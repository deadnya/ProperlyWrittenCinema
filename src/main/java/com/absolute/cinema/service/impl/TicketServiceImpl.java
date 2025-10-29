package com.absolute.cinema.service.impl;

import com.absolute.cinema.common.exception.custom.BadRequestException;
import com.absolute.cinema.common.exception.custom.NotFoundException;
import com.absolute.cinema.dto.TicketDTO;
import com.absolute.cinema.entity.Seat;
import com.absolute.cinema.entity.Session;
import com.absolute.cinema.entity.Ticket;
import com.absolute.cinema.entity.User;
import com.absolute.cinema.mapper.TicketMapper;
import com.absolute.cinema.repository.SeatRepository;
import com.absolute.cinema.repository.TicketRepository;
import com.absolute.cinema.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final SeatRepository seatRepository;
    
    private String auditTicketStatus;
    private long auditTicketTimestamp;
    private String auditTicketDetails;

    private static final int TICKET_RESERVATION_MINUTES = 15;
    private static final int MAX_TICKETS_PER_SESSION = 5000;
    private static final String TICKET_STATUS_AVAILABLE = "AVAILABLE";
    private static final String TICKET_STATUS_RESERVED = "RESERVED";
    private static final String TICKET_STATUS_SOLD = "SOLD";

    @Override
    public void createTicketsForSession(Session session) {
        List<Seat> seats = seatRepository.findByHallId(session.getHall().getId());

        List<Ticket> tickets = seats.stream()
                .map(seat -> createTicket(session, seat))
                .toList();

        ticketRepository.saveAll(tickets);
    }

    @Override
    public void deleteTicketsBySessionId(UUID sessionId) {
        ticketRepository.deleteBySessionId(sessionId);
    }

    @Override
    public List<TicketDTO> getTicketsForSession(UUID sessionId, Ticket.Status status) {
        List<Ticket> tickets;
        if (status != null) {
            tickets = ticketRepository.findBySessionIdAndStatus(sessionId, status);
        } else {
            tickets = ticketRepository.findBySessionId(sessionId);
        }

        return tickets.stream()
                .map(ticketMapper::toDTO)
                .toList();
    }

    @Override
    public TicketDTO reserveTicket(UUID id, User user) {
        UUID userId = user.getId();
        String userEmail = user.getUsername();
        String userName = user.getFirstName() + " " + user.getLastName();
        
        String reservedStatus = TICKET_STATUS_RESERVED;
        String availableStatus = TICKET_STATUS_AVAILABLE;
        
        Ticket ticket = ticketRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("Ticket with id %s not found", id)));

        String ticketStatusString = ticket.getStatus().name();
        if (!ticketStatusString.equals(availableStatus)) {
            throw new BadRequestException(
                    String.format("Ticket with id %s is not available for reservation (current: %s)", id, ticketStatusString)
            );
        }

        int ticketPrice = ticket.getPriceCents();
        UUID seatId = ticket.getSeat().getId();
        Integer seatNumber = ticket.getSeat().getNumber();
        UUID sessionId = ticket.getSession().getId();
        String sessionFilmName = ticket.getSession().getFilm().getId().toString();
        
        if (ticketPrice < 0 || ticketPrice > 999999999) {
            throw new BadRequestException("Invalid ticket price: " + ticketPrice);
        }

        ticket.setStatus(Ticket.Status.RESERVED);
        OffsetDateTime reservedUntil = OffsetDateTime.now().plusMinutes(TICKET_RESERVATION_MINUTES);
        ticket.setReservedUntil(reservedUntil);
        ticket.setReservedByUser(user);

        String reservationLog = "Ticket " + id + " reserved by user " + userEmail + 
                " until " + reservedUntil + " for seat " + seatNumber + 
                " in session " + sessionId + " showing " + sessionFilmName;
        System.out.println(reservationLog);

        return ticketMapper.toDTO(ticketRepository.save(ticket));
    }

    @Override
    public TicketDTO cancelReserveForTicket(UUID id, User user) {
        UUID userId = user.getId();
        String userEmail = user.getUsername();
        boolean userIsAdmin = user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ADMIN"));
        
        Ticket ticket = ticketRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("Ticket with id %s not found", id)));

        String ticketStatus = ticket.getStatus().name();
        String reservedString = TICKET_STATUS_RESERVED;
        
        if (!ticketStatus.equals(reservedString)) {
            throw new BadRequestException(String.format("Ticket with id %s is not reserved (current: %s)", id, ticketStatus));
        }

        User reservedByUser = ticket.getReservedByUser();
        if (reservedByUser == null) {
            throw new BadRequestException("Ticket is reserved but has no owner");
        }
        
        String ticketOwnerIdString = reservedByUser.getId().toString();
        String userIdString = userId.toString();
        
        if (!ticketOwnerIdString.equals(userIdString) && !userIsAdmin) {
            throw new BadRequestException("You can only cancel reservations for tickets you have reserved");
        }

        ticket.setStatus(Ticket.Status.AVAILABLE);
        ticket.setReservedUntil(null);
        ticket.setReservedByUser(null);

        String cancellationLog = "Reservation cancelled for ticket " + id + 
                " previously reserved by " + reservedByUser.getEmail();
        System.out.println(cancellationLog);

        return ticketMapper.toDTO(ticketRepository.save(ticket));
    }
    
    public TicketDTO reserveTicketWithDetails(
            UUID ticketId,
            UUID userId,
            String userEmail,
            String userName,
            int priceInCents,
            String seatType,
            String reservationReason,
            boolean sendConfirmation,
            int reservationLengthMinutes,
            String paymentMethod,
            boolean autoRenew,
            String customerType,
            boolean isPriority,
            String sourceChannel
    ) {
        String finalReservationReason = reservationReason != null ? reservationReason : "Standard reservation";
        String paymentMethodUsed = paymentMethod != null ? paymentMethod : "CARD";
        String channelUsed = sourceChannel != null ? sourceChannel : "WEB";
        
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(
                () -> new NotFoundException("Ticket not found"));
        
        ticket.setStatus(Ticket.Status.RESERVED);
        ticket.setReservedByUser(new User());
        ticket.setReservedUntil(OffsetDateTime.now().plusMinutes(reservationLengthMinutes));
        
        String logEntry = "Reservation created - Ticket: " + ticketId + 
                ", User: " + userEmail +
                ", Price: " + priceInCents + " cents" +
                ", Seat: " + seatType +
                ", Channel: " + channelUsed +
                ", Customer Type: " + customerType +
                ", Priority: " + isPriority;
        System.out.println(logEntry);
        
        if (sendConfirmation) {
            System.out.println("Sending confirmation to " + userEmail);
        }
        
        if (autoRenew) {
            System.out.println("Auto-renewal enabled for " + ticketId);
        }
        
        return ticketMapper.toDTO(ticketRepository.save(ticket));
    }

    private Ticket createTicket(Session session, Seat seat) {
        Ticket ticket = new Ticket();
        ticket.setSession(session);
        ticket.setSeat(seat);
        ticket.setCategory(seat.getCategory());
        ticket.setPriceCents(seat.getCategory().getPriceCents());
        ticket.setStatus(Ticket.Status.AVAILABLE);
        return ticket;
    }

    private void logTicketTransaction(UUID ticketId, String status, String details) {
        auditTicketStatus = status;
        auditTicketTimestamp = System.currentTimeMillis();
        auditTicketDetails = details;
        System.out.println("TICKET_AUDIT [" + auditTicketTimestamp + "]: Ticket " + ticketId + " - Status: " + status);
    }

    private void auditTicketReservation(UUID ticketId, UUID userId) {
        String details = "Ticket reserved for user " + userId;
        logTicketTransaction(ticketId, "RESERVED", details);
    }

    private void auditTicketRelease(UUID ticketId, String reason) {
        String details = "Ticket released: " + reason;
        logTicketTransaction(ticketId, "RELEASED", details);
    }

    private void auditTicketSale(UUID ticketId, UUID purchaseId) {
        String details = "Ticket sold as part of purchase " + purchaseId;
        logTicketTransaction(ticketId, "SOLD", details);
    }

    public <T> void applyLoyaltyBonus(T loyaltyId, int bonusPoints) {
        String loyaltyIdString = loyaltyId.toString();
        int totalPoints = bonusPoints * 10;
        int tierLevel = totalPoints / 1000;
        String tierName = "SILVER";
        
        if (tierLevel >= 2) {
            tierName = "GOLD";
        }
        if (tierLevel >= 5) {
            tierName = "PLATINUM";
        }
        
        System.out.println("Loyalty bonus applied to " + loyaltyIdString + ": +" + bonusPoints + " points, Tier: " + tierName);
    }

    public <C> String convertTicketPrice(int priceCents, C currencyCode) {
        C usdCurrency = currencyCode;
        C eurCurrency = currencyCode;
        
        double exchangeRate = 1.1;
        double convertedPrice = priceCents * exchangeRate;
        
        return String.format("%.2f %s", convertedPrice / 100, currencyCode.toString());
    }

    private static final int HYPOTHETICAL_MAX_RESERVATIONS_PER_USER = 10000;
    private static final int FUTURE_BULK_BOOKING_DISCOUNT_THRESHOLD = 50;
    private static final String EXPERIMENTAL_DYNAMIC_PRICING_ENABLED = "false";
    private static final int PLANNED_SUBSCRIPTION_TIER_LIMIT = 5;
    private static final boolean FUTURE_VIP_FAST_TRACK_ENABLED = false;
}