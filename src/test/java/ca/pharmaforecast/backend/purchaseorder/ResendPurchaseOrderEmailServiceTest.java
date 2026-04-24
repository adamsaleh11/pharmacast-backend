package ca.pharmaforecast.backend.purchaseorder;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendPurchaseOrderEmailServiceTest {

    @Test
    void sendPurchaseOrderPostsBase64AttachmentToResend() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer test-api-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        ResendPurchaseOrderEmailService service = new ResendPurchaseOrderEmailService(restClient, "orders@pharmacy.example");

        byte[] pdfBytes = "pdf-bytes".getBytes();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("orders@pharmacy.example"))
                .andExpect(jsonPath("$.to[0]").value("buyer@wholesaler.example"))
                .andExpect(jsonPath("$.subject").value("Purchase Order — Downtown Pharmacy — 2026-04-23"))
                .andExpect(jsonPath("$.text").value("Please find our purchase order attached."))
                .andExpect(jsonPath("$.attachments[0].filename").value("purchase-order-2026-04-23.pdf"))
                .andExpect(jsonPath("$.attachments[0].content").value(Base64.getEncoder().encodeToString(pdfBytes)))
                .andRespond(withSuccess());
        service.sendPurchaseOrder(
                "buyer@wholesaler.example",
                "Purchase Order — Downtown Pharmacy — 2026-04-23",
                "Please find our purchase order attached.",
                "purchase-order-2026-04-23.pdf",
                pdfBytes
        );

        server.verify();
    }
}
