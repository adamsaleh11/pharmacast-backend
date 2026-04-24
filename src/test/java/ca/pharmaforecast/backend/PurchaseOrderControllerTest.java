package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.purchaseorder.PurchaseOrderPreviewResponse;
import ca.pharmaforecast.backend.purchaseorder.PurchaseOrderService;
import ca.pharmaforecast.backend.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ca.pharmaforecast.backend.purchaseorder.PurchaseOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class PurchaseOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PurchaseOrderService purchaseOrderService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void previewEndpointReturnsEditableDraftForOwnedLocation() throws Exception {
        UUID locationId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(purchaseOrderService.preview(eq(locationId), any()))
                .thenReturn(new PurchaseOrderPreviewResponse(
                        Instant.parse("2026-04-23T00:00:00Z"),
                        "Purchase Order — Downtown Pharmacy — 2026-04-23",
                        List.of(new PurchaseOrderPreviewResponse.LineItem(
                                "12345678",
                                "Amoxicillin",
                                "500 mg",
                                "capsule",
                                8,
                                12,
                                12,
                                new BigDecimal("4.5"),
                                "STANDARD",
                                new BigDecimal("2"),
                                2,
                                12,
                                "URGENT"
                        ))
                ));

        mockMvc.perform(post("/locations/{locationId}/purchase-orders/preview", locationId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "include_status": ["RED", "AMBER"]
                                }
                                """)
                        .with(jwt().jwt(token -> token
                                .subject("22222222-2222-2222-2222-222222222222")
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generated_at").value("2026-04-23T00:00:00Z"))
                .andExpect(jsonPath("$.order_text").value("Purchase Order — Downtown Pharmacy — 2026-04-23"))
                .andExpect(jsonPath("$.line_items[0].din").value("12345678"))
                .andExpect(jsonPath("$.line_items[0].quantity_to_order").value(12))
                .andExpect(jsonPath("$.line_items[0].recommended_quantity").value(12));
    }

    @Test
    void generateEndpointReturnsCommittedOrder() throws Exception {
        UUID locationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID orderId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(purchaseOrderService.generate(eq(locationId), any()))
                .thenReturn(new ca.pharmaforecast.backend.purchaseorder.PurchaseOrderGenerateResponse(
                        orderId,
                        Instant.parse("2026-04-23T00:00:00Z"),
                        "Purchase Order — Downtown Pharmacy — 2026-04-23",
                        List.of()
                ));

        mockMvc.perform(post("/locations/{locationId}/purchase-orders/generate", locationId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "generated_at": "2026-04-23T00:00:00Z",
                                  "order_text": "Purchase Order — Downtown Pharmacy — 2026-04-23",
                                  "line_items": []
                                }
                                """)
                        .with(jwt().jwt(token -> token
                                .subject("22222222-2222-2222-2222-222222222222")
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.generated_at").value("2026-04-23T00:00:00Z"));
    }

    @Test
    void historyEndpointReturnsRecentOrders() throws Exception {
        UUID locationId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(purchaseOrderService.history(eq(locationId))).thenReturn(List.of(
                new ca.pharmaforecast.backend.purchaseorder.PurchaseOrderHistoryResponse(
                        UUID.fromString("44444444-4444-4444-4444-444444444444"),
                        Instant.parse("2026-04-23T00:00:00Z"),
                        "draft",
                        2,
                        18
                )
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/locations/{locationId}/purchase-orders", locationId)
                        .with(jwt().jwt(token -> token
                                .subject("22222222-2222-2222-2222-222222222222")
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value("44444444-4444-4444-4444-444444444444"))
                .andExpect(jsonPath("$[0].item_count").value(2))
                .andExpect(jsonPath("$[0].total_units").value(18));
    }

    @Test
    void exportCsvEndpointReturnsAttachment() throws Exception {
        UUID locationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID orderId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(purchaseOrderService.exportCsv(eq(locationId), eq(orderId))).thenReturn("Drug Name,DIN\nAmoxicillin,12345678\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/locations/{locationId}/purchase-orders/{orderId}/export/csv", locationId, orderId)
                        .with(jwt().jwt(token -> token
                                .subject("22222222-2222-2222-2222-222222222222")
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", org.hamcrest.Matchers.containsString("purchase-order-2026-04-23.csv")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("Drug Name,DIN\nAmoxicillin,12345678\n"));
    }

    @Test
    void exportPdfEndpointReturnsAttachment() throws Exception {
        UUID locationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID orderId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(purchaseOrderService.exportPdf(eq(locationId), eq(orderId))).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/locations/{locationId}/purchase-orders/{orderId}/export/pdf", locationId, orderId)
                        .with(jwt().jwt(token -> token
                                .subject("22222222-2222-2222-2222-222222222222")
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", org.hamcrest.Matchers.containsString("purchase-order-2026-04-23.pdf")));
    }

    @Test
    void sendEndpointAcceptsRecipientAndNote() throws Exception {
        UUID locationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID orderId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        mockMvc.perform(post("/locations/{locationId}/purchase-orders/{orderId}/send", locationId, orderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "recipient_email": "buyer@wholesaler.example",
                                  "note": "Please rush this order"
                                }
                                """)
                        .with(jwt().jwt(token -> token
                                .subject("22222222-2222-2222-2222-222222222222")
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isNoContent());
    }
}
