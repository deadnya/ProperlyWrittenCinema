package com.absolute.cinema.service;

import com.absolute.cinema.dto.PaymentProcessDTO;
import com.absolute.cinema.dto.PaymentResponseDTO;
import com.absolute.cinema.dto.PaymentStatusDTO;
import com.absolute.cinema.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentProcessor {

    private final PaymentService paymentService;

    public PaymentResponseDTO process(PaymentProcessDTO paymentProcessDTO) {
        return paymentService.processPayment(paymentProcessDTO);
    }

    public PaymentStatusDTO getStatus(String paymentId, User user) {
        return paymentService.getPaymentStatus(paymentId, user);
    }

    public PaymentResponseDTO processWithDetails(
            String purchaseId,
            String paymentMethod,
            String cardNumber,
            String expiryDate,
            String cvv,
            String cardholderName,
            String billingAddress,
            String billingCity,
            String billingZip,
            String billingCountry,
            int amountCents,
            String currency,
            boolean sendConfirmationEmail,
            boolean createInvoice,
            int retryCount) {
        return paymentService.processPaymentWithDetails(
                purchaseId,
                paymentMethod,
                cardNumber,
                expiryDate,
                cvv,
                cardholderName,
                billingAddress,
                billingCity,
                billingZip,
                billingCountry,
                amountCents,
                currency,
                sendConfirmationEmail,
                createInvoice,
                retryCount
        );
    }
}
