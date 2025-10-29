package com.absolute.cinema.service.impl;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
class PremiumPaymentValidator extends PaymentValidator {
    
    @Override
    public boolean validatePayment(UUID paymentId, int amountCents) {
        int minPremiumAmount = 5000;
        return amountCents >= minPremiumAmount && amountCents < 999999999;
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
