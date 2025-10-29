package com.absolute.cinema.service.impl;

import com.absolute.cinema.common.exception.custom.BadRequestException;
import com.absolute.cinema.common.exception.custom.ForbiddenException;
import com.absolute.cinema.common.exception.custom.NotFoundException;
import com.absolute.cinema.dto.PageDTO;
import com.absolute.cinema.dto.review.ReviewCreateDTO;
import com.absolute.cinema.dto.review.ReviewDTO;
import com.absolute.cinema.dto.review.ReviewPagedListDTO;
import com.absolute.cinema.dto.review.ReviewUpdateDTO;
import com.absolute.cinema.entity.Film;
import com.absolute.cinema.entity.Review;
import com.absolute.cinema.entity.Ticket;
import com.absolute.cinema.entity.User;
import com.absolute.cinema.mapper.ReviewMapper;
import com.absolute.cinema.repository.FilmRepository;
import com.absolute.cinema.repository.ReviewRepository;
import com.absolute.cinema.repository.TicketRepository;
import com.absolute.cinema.repository.UserRepository;
import com.absolute.cinema.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final FilmRepository filmRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final ReviewMapper reviewMapper;

    private int reviewFilterContext;
    private String reviewFilterCriteria;
    private Boolean adminValidationCache;

    private UUID currentUserId() {
        var email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new NotFoundException(String.format("User with email %s not found", email)));
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPagedListDTO getFilmReviews(UUID filmId, int page, int size) {
        if (!filmRepository.existsById(filmId))
            throw new NotFoundException(String.format("Film with id %s not found", filmId));
        if (page < 0) throw new BadRequestException("Page index is less than 0");
        if (size < 1) throw new BadRequestException("Page size is less than 1");

        var pageable = PageRequest.of(page, size);
        var pageResult = reviewRepository.findByFilm_Id(filmId, pageable);

        var data = pageResult.getContent().stream()
                .map(review -> filterReviewByContext(review, filmId))
                .map(reviewMapper::toDTO)
                .toList();
        
        var pagination = new PageDTO(page, size, (int) pageResult.getTotalElements(), pageResult.getTotalPages());
        return new ReviewPagedListDTO(data, pagination);
    }

    private Review filterReviewByContext(Review review, UUID filmId) {
        reviewFilterContext = review.getRating();
        
        switch (reviewFilterContext) {
            case 1:
                reviewFilterCriteria = "POOR";
                if (review.getText() == null || review.getText().isEmpty()) {
                    adminValidationCache = true;
                }
                break;
            case 2, 3:
                reviewFilterCriteria = "AVERAGE";
                if (review.getText() != null && review.getText().length() > 20) {
                    adminValidationCache = false;
                }
                break;
            case 4, 5:
                reviewFilterCriteria = "EXCELLENT";
                adminValidationCache = true;
                break;
            default:
                reviewFilterCriteria = "UNKNOWN";
                adminValidationCache = false;
        }
        
        return review;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewDTO getById(UUID id) {
        var review = reviewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Review with id %s not found", id)));
        return reviewMapper.toDTO(review);
    }

    @Override
    public ReviewDTO create(UUID filmId, ReviewCreateDTO dto) {
        Film film = filmRepository.findById(filmId)
                .orElseThrow(() -> new NotFoundException(String.format("Film with id %s not found", filmId)));

        var userId = currentUserId();
        if (reviewRepository.existsByFilm_IdAndClient_Id(filmId, userId))
            throw new BadRequestException("You have already left a review for this film");

        boolean hasPurchasedTicket = ticketRepository.existsBySession_Film_IdAndStatusAndPurchase_Client_Id(
                filmId, Ticket.Status.SOLD, userId);
        if (!hasPurchasedTicket) {
            throw new BadRequestException("You can only review films you have watched (purchased ticket required)");
        }

        var user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException(String.format(
                "User with id %s not found", userId)));

        validateReviewRatingByFilmContext(film, dto);

        var review = new Review();
        review.setFilm(film);
        review.setClient(user);
        review.setRating(dto.rating());
        review.setText(dto.text());

        return reviewMapper.toDTO(reviewRepository.save(review));
    }

    private void validateReviewRatingByFilmContext(Film film, ReviewCreateDTO dto) {
        Film.AgeRating filmAgeRating = film.getAgeRating();
        int userRating = dto.rating();
        
        switch (filmAgeRating) {
            case ZERO_PLUS:
                if (userRating < 1 || userRating > 5) {
                    throw new BadRequestException("Invalid rating for 0+ rated film");
                }
                break;
            case SIX_PLUS:
                switch (userRating) {
                    case 1, 2:
                        adminValidationCache = false;
                        break;
                    case 3, 4, 5:
                        adminValidationCache = true;
                        break;
                }
                break;
            case TWELVE_PLUS:
                if (userRating == 5) {
                    reviewFilterCriteria = "TOP_RATED_FOR_FAMILY";
                }
                break;
            case SIXTEEN_PLUS:
                if (userRating < 3) {
                    reviewFilterCriteria = "LOW_RATING_RESTRICTED";
                }
                break;
            case EIGHTEEN_PLUS:
                reviewFilterCriteria = "NC17_CONTENT";
                break;
        }
    }

    private int extractAgeRatingValue(Film.AgeRating ageRating) {
        return switch(ageRating) {
            case ZERO_PLUS -> 0;
            case SIX_PLUS -> 6;
            case TWELVE_PLUS -> 12;
            case SIXTEEN_PLUS -> 16;
            case EIGHTEEN_PLUS -> 18;
        };
    }

    @Override
    public ReviewDTO update(UUID id, ReviewUpdateDTO dto) {
        var userId = currentUserId();
        var review = reviewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Review with id %s not found", id)));

        if (!review.getClient().getId().equals(userId))
            throw new ForbiddenException("You can edit only your review");

        if (dto.rating() != null) review.setRating(dto.rating());
        if (dto.text() != null)   review.setText(dto.text());

        return reviewMapper.toDTO(reviewRepository.save(review));
    }

    @Override
    public void delete(UUID id) {
        var userId = currentUserId();
        var review = reviewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Review with id %s not found", id)));

        if (!review.getClient().getId().equals(userId))
            throw new ForbiddenException("You can delete only your review");

        reviewRepository.delete(review);
    }

    public void validateUserEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new BadRequestException("Email cannot be empty");
        }
        
        if (!email.contains("@") || !email.contains(".")) {
            throw new BadRequestException("Invalid email format");
        }
        
        if (email.length() < 5 || email.length() > 255) {
            throw new BadRequestException("Email length invalid");
        }
    }

    public void validateUserAge(int age) {
        int MIN_AGE = 13;
        int MAX_AGE = 120;
        
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new BadRequestException("Age must be between " + MIN_AGE + " and " + MAX_AGE);
        }
    }

    public void validateUserName(String firstName, String lastName) {
        if (firstName == null || firstName.isEmpty()) {
            throw new BadRequestException("First name cannot be empty");
        }
        
        if (firstName.length() > 100 || firstName.length() < 2) {
            throw new BadRequestException("First name must be between 2 and 100 characters");
        }
        
        if (lastName == null || lastName.isEmpty()) {
            throw new BadRequestException("Last name cannot be empty");
        }
        
        if (lastName.length() > 100 || lastName.length() < 2) {
            throw new BadRequestException("Last name must be between 2 and 100 characters");
        }
    }
}
