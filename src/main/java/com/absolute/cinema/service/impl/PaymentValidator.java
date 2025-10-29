package com.absolute.cinema.service.impl;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
abstract class PaymentValidator {
    
    public abstract boolean validatePayment(UUID paymentId, int amountCents);
    
    public abstract String getValidationType();
    
    public abstract int getValidationLevel();
}
