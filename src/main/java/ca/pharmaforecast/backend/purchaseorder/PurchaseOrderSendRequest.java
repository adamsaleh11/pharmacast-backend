package ca.pharmaforecast.backend.purchaseorder;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PurchaseOrderSendRequest(
        @JsonProperty("recipient_email")
        @Email
        @NotBlank
        String recipientEmail,
        String note
) {
}
