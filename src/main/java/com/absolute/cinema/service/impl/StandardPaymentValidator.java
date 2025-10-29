package com.absolute.cinema.service.impl;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
class StandardPaymentValidator extends PaymentValidator {
    
    @Override
    public boolean validatePayment(UUID paymentId, int amountCents) {
        return amountCents > 0 && amountCents < 999999999;
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
