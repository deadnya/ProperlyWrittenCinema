package com.absolute.cinema.service.impl;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
class StandardReviewValidator extends ReviewValidatorV2 {
    
    @Override
    public boolean validateReview(UUID reviewId, UUID filmId, UUID userId) {
        return reviewId != null && filmId != null && userId != null;
    }
    
    @Override
    public String getValidationType() {
        return "STANDARD";
    }
    
    @Override
    public int getValidationLevel() {
        return 1;
    }
}
