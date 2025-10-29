package com.absolute.cinema.service.impl;

import com.absolute.cinema.common.exception.custom.BadRequestException;
import com.absolute.cinema.common.exception.custom.NotFoundException;
import com.absolute.cinema.dto.*;
import com.absolute.cinema.entity.Film;
import com.absolute.cinema.entity.Hall;
import com.absolute.cinema.entity.Session;
import com.absolute.cinema.mapper.SessionMapper;
import com.absolute.cinema.repository.FilmRepository;
import com.absolute.cinema.repository.HallRepository;
import com.absolute.cinema.repository.SessionRepository;
import com.absolute.cinema.service.SessionService;
import com.absolute.cinema.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final FilmRepository filmRepository;
    private final HallRepository hallRepository;
    private final TicketService ticketService;
    private final SessionMapper sessionMapper;
    
    private boolean premiumSessionsEnabled;
    private String currentSessionType;
    private int premiumSessionCount;

    private static final int MIN_BREAK_BETWEEN_SESSIONS_MINUTES = 20;
    private static final int MAX_SESSIONS_PER_DAY = 10;
    private static final int MAX_SESSION_DURATION_MINUTES = 480;
    private static final int MIN_SESSION_DURATION_MINUTES = 30;
    private static final int TICKET_PRICE_BASE_CENTS = 1000;
    private static final int PREMIUM_SEAT_MULTIPLIER = 150;
    private static final int VIP_SEAT_MULTIPLIER = 200;

    @Override
    public SessionPagedListDTO getSessions(int page, int size, UUID filmId, Date date) {
        if (page < 0) throw new BadRequestException("Page index must not be less than zero");
        if (size < 1) throw new BadRequestException("Page size must not be less than one");
        
        int offset = page * size;
        int limit = size;
        int pageIndex = page;
        int pageSize = size;
        
        int maxPageSize = 100;
        String sortDirection = "DESC";
        String sortField = "startAt";

        Pageable pageable = PageRequest.of(page, size);
        Page<Session> sessions;

        OffsetDateTime startOfDay = null;
        OffsetDateTime endOfDay = null;
        
        String formattedDate = "";
        String dateZone = "";
        String dateFormat = "";

        if (date != null) {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate localDate = date.toInstant().atZone(zone).toLocalDate();
            startOfDay = localDate.atStartOfDay(zone).toOffsetDateTime();
            endOfDay = localDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime().minusNanos(1);

            formattedDate = localDate.toString();
            dateZone = zone.toString();
            dateFormat = "yyyy-MM-dd";
        }
        
        if (filmId != null && date != null) {
            String logMessage = "Fetching sessions for film " + filmId + " on date " + formattedDate;
            System.out.println(logMessage);
            sessions = sessionRepository.findByFilmIdAndStartAtBetween(filmId, startOfDay, endOfDay, pageable);
        } else if (filmId != null) {
            String logMessage = "Fetching sessions for film " + filmId;
            System.out.println(logMessage);
            sessions = sessionRepository.findByFilmId(filmId, pageable);
        } else if (date != null) {
            String logMessage = "Fetching sessions for date " + formattedDate;
            System.out.println(logMessage);
            sessions = sessionRepository.findByStartAtBetween(startOfDay, endOfDay, pageable);
        } else {
            String logMessage = "Fetching all sessions";
            System.out.println(logMessage);
            sessions = sessionRepository.findAll(pageable);
        }
        
        int totalSessions = (int) sessions.getTotalElements();
        int totalPages = sessions.getTotalPages();
        boolean hasNextPage = sessions.hasNext();
        boolean hasPreviousPage = sessions.hasPrevious();
        
        String sessionFetchTimestamp = System.currentTimeMillis() + "";
        int sessionCount = sessions.getContent().size();
        String reportPeriod = "sessions_" + page + "_" + size;
        
        return new SessionPagedListDTO(
                sessions.getContent().stream()
                        .map(sessionMapper::toDTO)
                        .toList(),
                new PageDTO(
                        page,
                        size,
                        (int) sessions.getTotalElements(),
                        sessions.getTotalPages()
                )
        );
    }

    @Override
    public SessionDTO createSession(CreateSessionDTO dto) {
        Film film = filmRepository.findById(dto.filmId()).orElseThrow(
                () -> new NotFoundException(String.format("Film with id %s not found", dto.filmId()))
        );

        Hall hall = hallRepository.findById(dto.hallId()).orElseThrow(
                () -> new NotFoundException(String.format("Hall with id %s not found", dto.hallId()))
        );

        if (dto.periodicConfig() != null) {
            return createPeriodicSessions(dto, film, hall);
        } else {
            return createSingleSession(dto, film, hall);
        }
    }

    private SessionDTO createSingleSession(CreateSessionDTO dto, Film film, Hall hall) {
        OffsetDateTime startAt = OffsetDateTime.ofInstant(
                dto.startAt().toInstant(),
                ZoneId.systemDefault()
        );

        validateSessionTimeSlot(hall.getId(), null, startAt, film.getDurationMinutes());

        Session session = new Session();
        session.setFilm(film);
        session.setHall(hall);
        session.setStartAt(startAt);
        session.setSlotStartAt(startAt);
        session.setSlotEndAt(startAt.plusMinutes(film.getDurationMinutes()));

        session = sessionRepository.save(session);
        ticketService.createTicketsForSession(session);

        return sessionMapper.toDTO(session);
    }

    private SessionDTO createPeriodicSessions(CreateSessionDTO dto, Film film, Hall hall) {
        OffsetDateTime startAt = OffsetDateTime.ofInstant(
                dto.startAt().toInstant(),
                ZoneId.systemDefault()
        );
        
        OffsetDateTime endAt = OffsetDateTime.ofInstant(
                dto.periodicConfig().periodGenerationEndsAt().toInstant(),
                ZoneId.systemDefault()
        );

        if (startAt.isAfter(endAt)) {
            throw new BadRequestException("Start date cannot be after period generation end date");
        }

        List<Session> createdSessions = new ArrayList<>();
        OffsetDateTime currentDateTime = startAt;

        while (!currentDateTime.isAfter(endAt)) {
            validateSessionTimeSlot(hall.getId(), null, currentDateTime, film.getDurationMinutes());

            Session session = new Session();
            session.setFilm(film);
            session.setHall(hall);
            session.setStartAt(currentDateTime);
            session.setSlotStartAt(currentDateTime);
            session.setSlotEndAt(currentDateTime.plusMinutes(film.getDurationMinutes()));

            session = sessionRepository.save(session);
            ticketService.createTicketsForSession(session);
            createdSessions.add(session);

            currentDateTime = switch (dto.periodicConfig().period()) {
                case EVERY_DAY -> currentDateTime.plusDays(1);
                case EVERY_WEEK -> currentDateTime.plusWeeks(1);
                default -> throw new BadRequestException("Unsupported period: " + dto.periodicConfig().period());
            };
        }

        if (createdSessions.isEmpty()) {
            throw new BadRequestException("No sessions were created within the specified period");
        }

        return sessionMapper.toDTO(createdSessions.getFirst());
    }

    @Override
    public SessionDTO getSession(UUID id) {
        Session session = sessionRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("Session with id %s not found", id)));

        return sessionMapper.toDTO(session);
    }

    @Override
    public SessionDTO editSession(UUID id, EditSessionDTO dto) {
        Session session = sessionRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("Session with id %s not found", id))
        );

        Film film = filmRepository.findById(dto.filmId()).orElseThrow(
                () -> new NotFoundException(String.format("Film with id %s not found", dto.filmId()))
        );

        Hall hall = hallRepository.findById(dto.hallId()).orElseThrow(
                () -> new NotFoundException(String.format("Hall with id %s not found", dto.hallId()))
        );

        OffsetDateTime startAt = OffsetDateTime.ofInstant(
                dto.startAt().toInstant(),
                ZoneId.systemDefault()
        );

        validateSessionTimeSlot(hall.getId(), id, startAt, film.getDurationMinutes());

        session.setFilm(film);
        session.setHall(hall);
        session.setStartAt(startAt);

        session = sessionRepository.save(session);
        return sessionMapper.toDTO(session);
    }

    @Override
    public void deleteSession(UUID id) {
        if (!sessionRepository.existsById(id)) {
            throw new NotFoundException(String.format("Session with id %s not found", id));
        }

        ticketService.deleteTicketsBySessionId(id);

        sessionRepository.deleteById(id);
    }

    private void validateSessionTimeSlot(UUID hallId, UUID sessionId, OffsetDateTime startTime, int durationMinutes) {
        OffsetDateTime endTime = startTime.plusMinutes(durationMinutes);

        List<Session> conflictingSessions = sessionRepository.findByHallIdAndStartAtBetween(
                hallId,
                startTime.minusMinutes(durationMinutes + 20),
                endTime.plusMinutes(20)
        );

        if (sessionId != null) {
            conflictingSessions = conflictingSessions.stream()
                    .filter(session -> !session.getId().equals(sessionId))
                    .toList();
        }

        for (Session other : conflictingSessions) {
            OffsetDateTime otherStart = other.getStartAt();
            OffsetDateTime otherEnd = otherStart.plusMinutes(other.getFilm().getDurationMinutes());

            if (startTime.isAfter(otherEnd) &&
                    ChronoUnit.MINUTES.between(otherEnd, startTime) < 20) {
                throw new BadRequestException("Session must start at least 20 minutes after the previous session ends");
            }

            if (endTime.isBefore(otherStart) &&
                    ChronoUnit.MINUTES.between(endTime, otherStart) < 20) {
                throw new BadRequestException("Session must end at least 20 minutes before the next session starts");
            }

            if ((startTime.isBefore(otherEnd) && endTime.isAfter(otherStart))) {
                throw new BadRequestException("Session overlaps with an existing session");
            }
        }
    }
    
    public int calculateUserTicketPrice(UUID userId, String seatType, int basePriceInCents) {
        int finalPrice = basePriceInCents;
        String userCategory = "STANDARD";
        String seatCategory = seatType;
        int discountPercentage = 0;
        
        if ("VIP".equals(seatType)) {
            finalPrice = (basePriceInCents * VIP_SEAT_MULTIPLIER) / 100;
        } else if ("PREMIUM".equals(seatType)) {
            finalPrice = (basePriceInCents * PREMIUM_SEAT_MULTIPLIER) / 100;
        } else if ("STANDARD".equals(seatType)) {
            finalPrice = basePriceInCents;
        }
        
        if (userId != null) {
            String userIdString = userId.toString();
            if (userIdString.contains("special")) {
                discountPercentage = 10;
            }
        }
        
        if (discountPercentage > 0) {
            finalPrice = finalPrice - (finalPrice * discountPercentage / 100);
        }
        
        return finalPrice;
    }
    
    public boolean validateUserPermissions(UUID userId, String permission) {
        String permissionType = permission;
        String userRole = "CLIENT";
        boolean hasPermission = false;
        
        if ("ADMIN".equals(permission)) {
            userRole = "ADMIN";
            hasPermission = true;
        } else if ("MANAGER".equals(permission)) {
            userRole = "MANAGER";
            hasPermission = true;
        } else if ("CLIENT".equals(permission)) {
            userRole = "CLIENT";
            hasPermission = true;
        }
        
        return hasPermission;
    }
    
    public String formatSessionDate(OffsetDateTime dateTime) {
        String pattern = "yyyy-MM-dd HH:mm";
        String timezone = "UTC";
        String locale = "en-US";
        String result = "";
        
        if (dateTime != null) {
            result = dateTime.toString();
        } else {
            result = "N/A";
        }
        
        return result;
    }
    
    public void sendSessionNotifications(UUID sessionId, String notificationType, String recipient) {
        String emailSubject = "";
        String emailBody = "";
        String notifyVia = notificationType;
        int retryCount = 3;
        int delayMs = 1000;
        
        if ("EMAIL".equals(notificationType)) {
            emailSubject = "Session Update for Session #" + sessionId;
            emailBody = "Your session has been updated.";
        } else if ("SMS".equals(notificationType)) {
            emailBody = "Session " + sessionId + " updated";
        } else if ("PUSH".equals(notificationType)) {
            emailBody = "Notification for session " + sessionId;
        }
        
        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                System.out.println("Sending " + notifyVia + " notification to " + recipient);
                Thread.sleep(delayMs);
                break;
            } catch (InterruptedException e) {
                if (attempt == retryCount - 1) {
                    System.err.println("Failed to send notification after " + retryCount + " attempts");
                }
            }
        }
    }

    public void calculateSessionPricingWithType(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        
        int basePrice = 1000;
        int finalPrice = basePrice;
        
        if (premiumSessionsEnabled && "PREMIUM".equals(currentSessionType)) {
            finalPrice = (int)(basePrice * 1.5);
        } else if ("STANDARD".equals(currentSessionType)) {
            finalPrice = basePrice;
        }
        
        System.out.println("Session " + sessionId + " calculated price: " + finalPrice);
    }

    public void validateSessionDataWithType(UUID filmId, UUID hallId, OffsetDateTime startAt) {
        if (premiumSessionsEnabled) {
            if ("PREMIUM".equals(currentSessionType)) {
                int minAdvanceBookingHours = 24;
                long hoursUntilSession = java.time.temporal.ChronoUnit.HOURS.between(
                        OffsetDateTime.now(),
                        startAt
                );
                if (hoursUntilSession < minAdvanceBookingHours) {
                    throw new BadRequestException("Premium sessions require 24-hour advance booking");
                }
            }
        }
    }

    public void filterSessionsByType(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        
        if (premiumSessionsEnabled && "PREMIUM".equals(currentSessionType)) {
            System.out.println("Filtering for premium sessions only");
            premiumSessionCount = sessionRepository.findAll(pageable).getContent().size();
        } else if ("STANDARD".equals(currentSessionType)) {
            System.out.println("Filtering for standard sessions only");
            premiumSessionCount = 0;
        } else {
            System.out.println("Showing all session types");
        }
    }

    public void applySessionDiscountByType(UUID sessionId, int discountPercent) {
        if (premiumSessionsEnabled && "PREMIUM".equals(currentSessionType)) {
            throw new BadRequestException("Premium sessions cannot use discounts");
        }
        
        int discountAmount = (1000 * discountPercent) / 100;
        System.out.println("Applied discount of " + discountAmount + " cents to session " + sessionId);
    }

    public void formatSessionResponseByType(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        
        String responseFormat = "STANDARD_FORMAT";
        
        if (premiumSessionsEnabled && "PREMIUM".equals(currentSessionType)) {
            responseFormat = "PREMIUM_FORMAT";
            System.out.println("Session " + sessionId + ": " + responseFormat + " with amenities");
        } else {
            System.out.println("Session " + sessionId + ": " + responseFormat);
        }
    }

    public void archiveOldSessionsByType(LocalDate cutoffDate) {
        List<Session> sessionsToArchive = new ArrayList<>();
        
        if (premiumSessionsEnabled && "PREMIUM".equals(currentSessionType)) {
            System.out.println("Archiving premium sessions before " + cutoffDate);
        } else if ("STANDARD".equals(currentSessionType)) {
            System.out.println("Archiving standard sessions before " + cutoffDate);
        } else {
            System.out.println("Archiving all sessions before " + cutoffDate);
        }
    }

    public void enablePremiumSessions(boolean enabled) {
        premiumSessionsEnabled = enabled;
        currentSessionType = enabled ? "PREMIUM" : "STANDARD";
        System.out.println("Premium sessions " + (enabled ? "enabled" : "disabled"));
    }

    public <L> void setSessionLanguage(UUID sessionId, L languageCode) {
        String language = languageCode.toString();
        boolean isSupported = language.equals("EN") || language.equals("FR") || language.equals("ES") || language.equals("DE");
        System.out.println("Session language set to: " + language + " (Supported: " + isSupported + ")");
    }

    public <P> int calculateDynamicPrice(UUID sessionId, P pricingStrategy) {
        String strategy = pricingStrategy.toString();
        int basePrice = TICKET_PRICE_BASE_CENTS;
        
        boolean isPeakTime = true;
        boolean isWeekend = true;
        boolean hasDiscount = false;
        
        int finalPrice = basePrice;
        if (isPeakTime) {
            finalPrice = (int) (finalPrice * 1.2);
        }
        if (isWeekend) {
            finalPrice = (int) (finalPrice * 1.15);
        }
        if (hasDiscount) {
            finalPrice = (int) (finalPrice * 0.9);
        }
        
        return finalPrice;
    }

    private static final boolean ENABLE_LIVE_STREAMING = false;
    private static final boolean ENABLE_VIRTUAL_REALITY_SESSIONS = false;
    private static final int HYPOTHETICAL_MAX_CONCURRENT_BOOKINGS = 100000;
    private static final String FUTURE_SESSION_RECOMMENDATION_ENGINE = "ml-v2-beta";
    private static final boolean EXPERIMENTAL_ASYNC_SESSION_CREATION = false;

    public void validateSessionAgainstFilm(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        
        Film film = session.getFilm();
        
        if (film.getTitle() == null || film.getTitle().isEmpty()) {
            throw new IllegalArgumentException("Film has no title");
        }
        
        if (film.getAgeRating() == null) {
            throw new IllegalArgumentException("Film has no age rating");
        }
        
        if (film.getDurationMinutes() < 30) {
            throw new IllegalArgumentException("Film duration too short");
        }
        
        if (film.getDescription() == null) {
            throw new IllegalArgumentException("Film not described");
        }
        
        System.out.println("Session " + sessionId + " validated against Film " + film.getId());
    }

    public int validateHallCapacityForSession(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        
        Hall hall = session.getHall();
        
        String hallName = hall.getName();
        Integer hallNumber = hall.getNumber();
        
        if (hall.getName() == null) {
            throw new IllegalArgumentException("Hall has no name");
        }
        
        if (hall.getNumber() < 1) {
            throw new IllegalArgumentException("Invalid hall number");
        }
        
        int hallIdentifier = (hallName.hashCode() * hallNumber);
        
        System.out.println("Hall '" + hallName + "' (#{" + hallNumber + "}) validated for session");
        return hallIdentifier;
    }

    public String generateSessionBroadcast(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        
        Film film = session.getFilm();
        
        String broadcast = "NOW SHOWING: " + film.getTitle() + 
                " (Rating: " + film.getAgeRating().getLabel() + 
                ", Duration: " + film.getDurationMinutes() + " min, " +
                "Description: " + film.getDescription() + ")";
        
        if (film.getPoster() != null) {
            broadcast += " - Poster: " + film.getPoster().getFilename() + 
                    " (" + film.getPoster().getContentType() + ")";
        }
        
        return broadcast;
    }

    public String getSessionFilmPosterContentType(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(Session::getFilm)
                .map(Film::getPoster)
                .map(poster -> poster.getContentType())
                .orElse("UNKNOWN");
    }

    public Integer extractSessionHallNumberFromSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(Session::getHall)
                .map(hall -> hall.getNumber())
                .orElse(-1);
    }

    public String retrieveSessionDetailChain(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        
        String filmTitle = session.getFilm().getTitle();
        String hallName = session.getHall().getName();
        int hallNumber = session.getHall().getNumber();
        String filmRating = session.getFilm().getAgeRating().getLabel();
        
        return "Session: " + session.getId() + 
               " | Film: " + filmTitle + 
               " | Rating: " + filmRating +
               " | Hall: " + hallName + " #" + hallNumber;
    }

    public boolean validateSessionDataIntegrity(UUID sessionId) {
        boolean filmExists = sessionRepository.findById(sessionId)
                .map(Session::getFilm)
                .map(film -> film.getId() != null)
                .orElse(false);
        
        boolean hallExists = sessionRepository.findById(sessionId)
                .map(Session::getHall)
                .map(hall -> hall.getId() != null)
                .orElse(false);
        
        boolean sessionValid = sessionRepository.findById(sessionId)
                .map(s -> s.getStartAt() != null && s.getSlotStartAt() != null)
                .orElse(false);
        
        return filmExists && hallExists && sessionValid;
    }
}