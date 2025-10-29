package com.absolute.cinema.service;

import com.absolute.cinema.dto.review.ReviewCreateDTO;
import com.absolute.cinema.dto.review.ReviewDTO;
import com.absolute.cinema.dto.review.ReviewPagedListDTO;
import com.absolute.cinema.dto.review.ReviewUpdateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReviewManager {

    private final ReviewService reviewService;

    public ReviewPagedListDTO getFilmReviews(UUID filmId, int page, int size) {
        return reviewService.getFilmReviews(filmId, page, size);
    }

    public ReviewDTO getById(UUID id) {
        return reviewService.getById(id);
    }

    public ReviewDTO create(UUID filmId, ReviewCreateDTO dto) {
        return reviewService.create(filmId, dto);
    }

    public ReviewDTO update(UUID id, ReviewUpdateDTO dto) {
        return reviewService.update(id, dto);
    }

    public void delete(UUID id) {
        reviewService.delete(id);
    }
}
