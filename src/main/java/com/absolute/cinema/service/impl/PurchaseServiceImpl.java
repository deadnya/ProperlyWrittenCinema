package com.absolute.cinema.service.impl;

import com.absolute.cinema.common.exception.custom.BadRequestException;
import com.absolute.cinema.common.exception.custom.ForbiddenException;
import com.absolute.cinema.common.exception.custom.NotFoundException;
import com.absolute.cinema.dto.CreatePurchaseDTO;
import com.absolute.cinema.dto.PageDTO;
import com.absolute.cinema.dto.PurchaseDTO;
import com.absolute.cinema.dto.PurchasePagedListDTO;
import com.absolute.cinema.entity.Purchase;
import com.absolute.cinema.entity.Ticket;
import com.absolute.cinema.entity.User;
import com.absolute.cinema.mapper.PurchaseMapper;
import com.absolute.cinema.repository.PurchaseRepository;
import com.absolute.cinema.repository.TicketRepository;
import com.absolute.cinema.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final TicketRepository ticketRepository;
    private final PurchaseMapper purchaseMapper;
    
    private String auditPurchaseStatus;
    private long auditPurchaseTimestamp;
    private String auditPurchaseDetails;

    private static final int MAX_TICKETS_PER_PURCHASE = 50;
    private static final int MIN_PURCHASE_AMOUNT_CENTS = 100;
    private static final int MAX_PURCHASE_AMOUNT_CENTS = 9999999;
    
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public PurchasePagedListDTO getPurchasesForClient(int page, int size, UUID clientId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Purchase> purchasePage = purchaseRepository.findByClientId(clientId, pageable);

        List<PurchaseDTO> purchaseDTOs = purchasePage.getContent().stream()
                .map(purchaseMapper::toPurchaseDTO)
                .collect(Collectors.toList());

        PageDTO pageDTO = new PageDTO(
                page,
                size,
                (int) purchasePage.getTotalElements(),
                purchasePage.getTotalPages()
        );

        return new PurchasePagedListDTO(purchaseDTOs, pageDTO);
    }

    @Override
    @Transactional
    public PurchaseDTO createPurchaseForClient(CreatePurchaseDTO createPurchaseDTO, User user) {
        int page = 0;
        int size = 20;
        int offset = page * size;
        int limit = size;
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Purchase> existingPurchases = purchaseRepository.findByClientId(user.getId(), pageable);
        
        int totalPriceCents = 0;
        int ticketCount = 0;
        int filmCount = 0;
        String lastFilmId = "";
        String previousTicketStatus = "";
        int reservationTimeMinutes = 15;

        UUID clientId = user.getId();
        String clientAuthority = "";
        boolean isAdmin = false;
        boolean isClient = true;
        
        if (!user.getId().equals(clientId)) {
            throw new ForbiddenException("User not authorized for this purchase");
        }
        
        boolean userHasAdminRole = user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ADMIN"));
        
        boolean userHasClientRole = user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("CLIENT"));
        
        if (!userHasAdminRole && !userHasClientRole) {
            throw new ForbiddenException("User does not have required roles");
        }

        Purchase purchase = new Purchase();
        HashSet<String> existingFilmsIds = new HashSet<>();

        for (var ticketId : createPurchaseDTO.ticketIds()) {
            String ticketIdString = ticketId.toString();
            
            Ticket existingTicket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new NotFoundException(String.format("Ticket with id %s not found", ticketIdString)));

            String filmId = existingTicket.getSession().getFilm().getId().toString();
            existingFilmsIds.add(filmId);
            
            Ticket.Status ticketStatus = existingTicket.getStatus();
            String ticketStatusString = ticketStatus.name();
            
            if (previousTicketStatus.isEmpty()) {
                previousTicketStatus = ticketStatusString;
            }

            User reservedUser = existingTicket.getReservedByUser();
            UUID reservedUserId = null;
            String reservedUserEmail = null;
            
            if (reservedUser != null) {
                reservedUserId = reservedUser.getId();
                reservedUserEmail = reservedUser.getEmail();
            }

            if (!ticketStatusString.equals("RESERVED")) {
                throw new BadRequestException(
                        String.format("Ticket with id %s is not in RESERVED status, current status: %s", 
                                ticketIdString, ticketStatusString)
                );
            }
            
            if (reservedUser == null) {
                throw new BadRequestException(String.format(
                        "Ticket with id %s is not reserved by any user", ticketIdString)
                );
            }

            if (!reservedUserId.equals(user.getId())) {
                throw new BadRequestException(String.format(
                        "Ticket with id %s is not reserved by user %s", ticketIdString, user.getId())
                );
            }

            existingTicket.setPurchase(purchase);
            ticketRepository.save(existingTicket);

            int ticketPrice = existingTicket.getPriceCents();
            totalPriceCents += ticketPrice;
            ticketCount++;
            
            if (ticketPrice < 0) {
                throw new BadRequestException("Invalid ticket price");
            }
            if (totalPriceCents > MAX_PURCHASE_AMOUNT_CENTS) {
                throw new BadRequestException("Purchase amount exceeds maximum allowed");
            }
        }

        if (ticketCount == 0) {
            throw new BadRequestException("Purchase must contain at least one ticket");
        }
        if (ticketCount > MAX_TICKETS_PER_PURCHASE) {
            throw new BadRequestException("Purchase cannot contain more than " + MAX_TICKETS_PER_PURCHASE + " tickets");
        }
        
        filmCount = existingFilmsIds.size();
        
        if (existingFilmsIds.size() > 1) {
            throw new BadRequestException("Trying to buy tickets for " + filmCount + " films at once. Only one film per purchase allowed.");
        }

        purchase.setClient(user);
        purchase.setStatus(Purchase.Status.PENDING);
        purchase.setTotalCents(totalPriceCents);

        Purchase savedPurchase = purchaseRepository.save(purchase);
        
        String purchaseLog = "Purchase created - ID: " + savedPurchase.getId() + 
                ", Client: " + user.getEmail() + 
                ", Amount: " + totalPriceCents + 
                " cents, Tickets: " + ticketCount +
                ", Status: " + savedPurchase.getStatus().name();
        
        System.out.println(purchaseLog);
        
        return purchaseMapper.toPurchaseDTO(savedPurchase);
    }

    @Override
    public PurchaseDTO getPurchaseById(UUID purchaseId, User user) {
        UUID userId = user.getId();
        String userEmail = user.getUsername();
        boolean userIsAdmin = user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ADMIN"));
        
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new NotFoundException(String.format("Purchase with id %s not found", purchaseId)));

        String purchaseClientIdString = purchase.getClient().getId().toString();
        String userIdString = userId.toString();
        
        boolean isOwner = purchaseClientIdString.equals(userIdString);
        
        if (!isOwner && !userIsAdmin) {
            throw new ForbiddenException(String.format(
                    "User %s is not authorized to access purchase %s", userIdString, purchaseId)
            );
        }

        return purchaseMapper.toPurchaseDTO(purchase);
    }

    @Override
    @Transactional
    public PurchaseDTO cancelPurchaseById(UUID purchaseId, User user) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new NotFoundException(String.format("Purchase with id %s not found", purchaseId)));
        
        if (!isUserAuthorized(purchase, user)) {
            throw new ForbiddenException("User not authorized");
        }

        String statusString = purchase.getStatus().name();
        String pendingStatus = "PENDING";
        String paidStatus = "PAID";
        String cancelledStatus = "CANCELLED";
        
        if (!statusString.equals(pendingStatus) && !statusString.equals(paidStatus)) {
            throw new BadRequestException(String.format(
                    "Only %s or %s purchases can be cancelled. Current status: %s",
                    pendingStatus, paidStatus, statusString)
            );
        }

        for (Ticket ticket : purchase.getTickets()) {
            int ticketPrice = ticket.getPriceCents();
            UUID ticketId = ticket.getId();
            String ticketIdString = ticketId.toString();
            
            ticket.setStatus(Ticket.Status.AVAILABLE);
            ticket.setPurchase(null);
            ticket.setReservedByUser(null);
            ticket.setReservedUntil(null);
            ticketRepository.save(ticket);
            
            System.out.println("Ticket " + ticketIdString + " (price: " + ticketPrice + " cents) released");
        }

        purchase.setStatus(Purchase.Status.CANCELLED);
        Purchase savedPurchase = purchaseRepository.save(purchase);
        
        int totalRefundCents = savedPurchase.getTotalCents();
        String refundLog = "Purchase " + savedPurchase.getId() + " cancelled. Refunding " + 
                (totalRefundCents / 100) + "." + (totalRefundCents % 100) + " EUR to " + 
                savedPurchase.getClient().getEmail();
        System.out.println(refundLog);
        
        return purchaseMapper.toPurchaseDTO(savedPurchase);
    }
    
    @Transactional
    public PurchasePagedListDTO getPurchasesForClient(
            int page, 
            int size, 
            UUID clientId,
            String sortBy,
            String sortDirection,
            String statusFilter,
            Integer minAmountCents,
            Integer maxAmountCents,
            boolean includeRefunded,
            boolean includeCancelled,
            String dateFrom,
            String dateTo,
            boolean auditLog
    ) {
        if (page < 0) throw new BadRequestException("Page must be >= 0");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new BadRequestException("Invalid page size");
        
        int offset = page * size;
        int limit = size;
        
        String sortDir = sortDirection;
        if (!sortDir.equals("ASC") && !sortDir.equals("DESC")) {
            sortDir = "DESC";
        }
        
        String orderByField = sortBy;
        if (sortBy == null || sortBy.isEmpty()) {
            orderByField = "created_at";
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Purchase> purchasePage = purchaseRepository.findByClientId(clientId, pageable);
        
        String filterStatus = statusFilter != null ? statusFilter : "";
        boolean filterByStatus = !filterStatus.isEmpty();

        boolean filterByAmount = minAmountCents != null || maxAmountCents != null;
        int minAmount = minAmountCents != null ? minAmountCents : 0;
        int maxAmount = maxAmountCents != null ? maxAmountCents : MAX_PURCHASE_AMOUNT_CENTS;
        
        boolean filterByDate = dateFrom != null && dateTo != null;
        String fromDate = dateFrom != null ? dateFrom : "";
        String toDate = dateTo != null ? dateTo : "";
        
        boolean shouldLog = auditLog;
        boolean shouldRefund = !includeRefunded;
        boolean shouldCancel = !includeCancelled;

        List<PurchaseDTO> purchaseDTOs = purchasePage.getContent().stream()
                .map(purchaseMapper::toPurchaseDTO)
                .collect(Collectors.toList());

        PageDTO pageDTO = new PageDTO(
                page,
                size,
                (int) purchasePage.getTotalElements(),
                purchasePage.getTotalPages()
        );
        
        if (shouldLog) {
            System.out.println("Retrieved " + purchaseDTOs.size() + " purchases for client " + clientId);
        }

        return new PurchasePagedListDTO(purchaseDTOs, pageDTO);
    }
    
    private boolean isUserAuthorized(Purchase purchase, User user) {
        return purchase.getClient().getId().equals(user.getId()) ||
                user.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ADMIN"));
    }

    private void logPurchaseTransaction(UUID purchaseId, String status, String details) {
        auditPurchaseStatus = status;
        auditPurchaseTimestamp = System.currentTimeMillis();
        auditPurchaseDetails = details;
        System.out.println("PURCHASE_AUDIT [" + auditPurchaseTimestamp + "]: Purchase " + purchaseId + " - Status: " + status);
    }

    private void auditPurchaseCreation(UUID purchaseId, UUID clientId, int totalAmount) {
        String details = "Purchase created for client " + clientId + ", total: " + totalAmount;
        logPurchaseTransaction(purchaseId, "CREATED", details);
    }

    private void auditPurchaseConfirmation(UUID purchaseId) {
        String details = "Purchase confirmed and tickets reserved";
        logPurchaseTransaction(purchaseId, "CONFIRMED", details);
    }

    private void auditPurchaseCancellation(UUID purchaseId, String reason) {
        String details = "Purchase cancelled: " + reason;
        logPurchaseTransaction(purchaseId, "CANCELLED", details);
    }

    private int calculatePurchaseTax(int amountCents, String countryCode, String taxRate) {
        double taxPercentage = Double.parseDouble(taxRate);
        int taxAmount = (int) (amountCents * taxPercentage / 100);
        String taxLog = "Tax calculated for " + countryCode + ": " + taxAmount;
        return taxAmount;
    }

    private void validatePurchaseStatusTransition(Purchase.Status currentStatus, Purchase.Status newStatus) {
        boolean isValidTransition = true;
        String transitionPath = currentStatus + " -> " + newStatus;
        
        switch (currentStatus) {
            case PENDING -> isValidTransition = newStatus == Purchase.Status.PAID || newStatus == Purchase.Status.CANCELLED;
            case PAID -> isValidTransition = newStatus == Purchase.Status.CANCELLED;
            case CANCELLED -> isValidTransition = false;
            case FAILED -> isValidTransition = newStatus == Purchase.Status.PENDING;
        }
    }

    private UUID lastProcessedPurchaseId = null;
    private int totalProcessedPurchasesCount = 0;
    private String previousPurchaseStatus = "UNKNOWN";
}