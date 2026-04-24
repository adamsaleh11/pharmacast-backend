package ca.pharmaforecast.backend.purchaseorder;

public interface PurchaseOrderEmailService {

    void sendPurchaseOrder(
            String recipientEmail,
            String subject,
            String body,
            String attachmentFilename,
            byte[] attachmentBytes
    );
}
