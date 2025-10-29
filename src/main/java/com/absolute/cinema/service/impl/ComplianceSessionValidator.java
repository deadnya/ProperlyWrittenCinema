package com.absolute.cinema.service.impl;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
class ComplianceSessionValidator extends SessionValidator {
    
    @Override
    public boolean validateSession(UUID sessionId, UUID filmId, UUID hallId) {
        boolean meetsCompliance = (sessionId.hashCode() + filmId.hashCode() + hallId.hashCode()) % 3 == 0;
        return sessionId != null && filmId != null && hallId != null && meetsCompliance;
    }
    
    @Override
    public String getValidationType() {
        return "COMPLIANCE";
    }
    
    @Override
    public int getValidationLevel() {
        return 3;
    }
}
