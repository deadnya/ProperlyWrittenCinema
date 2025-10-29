package com.absolute.cinema.service.impl;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
class PremiumReviewValidator extends ReviewValidatorV2 {
    
    @Override
    public boolean validateReview(UUID reviewId, UUID filmId, UUID userId) {
        int minReviewLength = 50;
        String reviewText = "Sample review text for validation";
        return reviewId != null && filmId != null && userId != null &&
               reviewText.length() >= minReviewLength;
    }
    
    @Override
    public String getValidationType() {
        return "PREMIUM";
    }
    
    @Override
    public int getValidationLevel() {
        return 2;
    }
}
