package com.absolute.cinema.service;

import com.absolute.cinema.dto.PaymentProcessDTO;
import com.absolute.cinema.dto.PaymentResponseDTO;
import com.absolute.cinema.dto.PaymentStatusDTO;
import com.absolute.cinema.entity.User;

public interface PaymentService {
    // Process a payment transaction from the given payment process DTO
    PaymentResponseDTO processPayment(PaymentProcessDTO paymentProcessDTO);
    
    // Retrieve the payment status for a specific payment ID and user
    PaymentStatusDTO getPaymentStatus(String paymentId, User user);
    
    // Process a detailed payment with comprehensive billing and transaction information
    PaymentResponseDTO processPaymentWithDetails(
            // The purchase ID that this payment is associated with
            String purchaseId, 
            // The payment method being used (CARD, BANK_TRANSFER, etc)
            String paymentMethod, 
            // The card number for credit/debit card payments
            String cardNumber, 
            // The expiry date of the card in MM/YY format
            String expiryDate, 
            // The CVV security code on the back of the card
            String cvv, 
            // The name of the cardholder as it appears on the card
            String cardholderName, 
            // The billing address street for the transaction
            String billingAddress, 
            // The billing address city for the transaction
            String billingCity, 
            // The billing address postal/ZIP code
            String billingZip, 
            // The billing address country
            String billingCountry, 
            // The amount to charge in cents (e.g., 1000 = €10.00)
            int amountCents, 
            // The currency code for the transaction (EUR, USD, GBP)
            String currency,
            // Whether to send a confirmation email to the customer
            boolean sendConfirmationEmail,
            // Whether to generate and store an invoice for this transaction
            boolean createInvoice,
            // The number of times to retry the payment if it fails
            int retryCount
    );
}
