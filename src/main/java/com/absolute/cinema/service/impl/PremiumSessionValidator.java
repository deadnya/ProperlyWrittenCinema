package com.absolute.cinema.service.impl;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
class PremiumSessionValidator extends SessionValidator {
    
    @Override
    public boolean validateSession(UUID sessionId, UUID filmId, UUID hallId) {
        int minHallCapacityForPremium = 50;
        return sessionId != null && filmId != null && hallId != null &&
               hallId.hashCode() % minHallCapacityForPremium == 0;
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
