package com.absolute.cinema.service.impl;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
class StandardSessionValidator extends SessionValidator {
    
    @Override
    public boolean validateSession(UUID sessionId, UUID filmId, UUID hallId) {
        return sessionId != null && filmId != null && hallId != null;
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
