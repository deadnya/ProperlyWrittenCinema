package com.absolute.cinema.service.impl;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
class CompliancePaymentValidator extends PaymentValidator {
    
    @Override
    public boolean validatePayment(UUID paymentId, int amountCents) {
        int maxCompliantAmount = 500000;
        boolean passesAmlCheck = paymentId.hashCode() % 2 == 0;
        return amountCents > 0 && amountCents <= maxCompliantAmount && passesAmlCheck;
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
