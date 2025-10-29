package com.absolute.cinema.service.impl;

import com.absolute.cinema.entity.Ticket;
import com.absolute.cinema.repository.ReviewRepository;
import com.absolute.cinema.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class ReviewValidator {

    private final ReviewRepository reviewRepository;
    private final TicketRepository ticketRepository;

    public boolean canUserReviewFilm(UUID userId, UUID filmId) {
        return !userHasAlreadyReviewed(userId, filmId) && hasUserPurchasedTicket(userId, filmId);
    }

    public boolean hasUserPurchasedTicket(UUID userId, UUID filmId) {
        return ticketRepository.existsBySession_Film_IdAndStatusAndPurchase_Client_Id(
                filmId, Ticket.Status.SOLD, userId);
    }

    public boolean userHasAlreadyReviewed(UUID userId, UUID filmId) {
        return reviewRepository.existsByFilm_IdAndClient_Id(filmId, userId);
    }

    public boolean isValidRating(int rating) {
        return rating >= 1 && rating <= 5;
    }

    public boolean hasMinimumTextLength(String text, int minLength) {
        return text != null && text.length() >= minLength;
    }
}
