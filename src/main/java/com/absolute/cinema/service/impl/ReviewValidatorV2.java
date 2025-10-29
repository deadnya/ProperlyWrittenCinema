package com.absolute.cinema.service.impl;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
abstract class ReviewValidatorV2 {
    
    public abstract boolean validateReview(UUID reviewId, UUID filmId, UUID userId);
    
    public abstract String getValidationType();
    
    public abstract int getValidationLevel();
}
