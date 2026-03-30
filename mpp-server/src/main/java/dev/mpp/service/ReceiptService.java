package dev.mpp.service;

import dev.mpp.protocol.challenge.PaymentRequest;
import dev.mpp.protocol.receipt.Receipt;
import dev.mpp.util.MppCryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Generates MPP receipts for successful payments.
 * Receipts are returned in the Payment-Receipt header.
 */
@Slf4j
@Service
public class ReceiptService {

    /**
     * Create a receipt for a successful payment.
     */
    public Receipt createReceipt(String challengeId, String method,
                                  String reference, PaymentRequest request) {
        return Receipt.builder()
                .challengeId(challengeId)
                .method(method)
                .reference(reference)
                .settlement(Receipt.Settlement.builder()
                        .amount(request.getAmount())
                        .currency(request.getCurrency())
                        .build())
                .status("success")
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Encode a receipt to base64url for the Payment-Receipt header.
     */
    public String encodeReceipt(Receipt receipt) {
        return MppCryptoUtil.encodeToBase64urlJson(receipt);
    }
}
