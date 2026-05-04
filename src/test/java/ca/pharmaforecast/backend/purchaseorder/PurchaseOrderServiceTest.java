package ca.pharmaforecast.backend.purchaseorder;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.currentstock.CurrentStock;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.DrugThreshold;
import ca.pharmaforecast.backend.forecast.DrugThresholdRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastConfidence;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.ReorderStatus;
import ca.pharmaforecast.backend.llm.LlmServiceClient;
import ca.pharmaforecast.backend.llm.PayloadSanitizer;
import ca.pharmaforecast.backend.llm.PurchaseOrderDrugPayload;
import ca.pharmaforecast.backend.llm.PurchaseOrderPayload;
import ca.pharmaforecast.backend.llm.PurchaseOrderTextResult;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ForecastRepository forecastRepository;

    @Mock
    private DrugRepository drugRepository;

    @Mock
    private CurrentStockRepository currentStockRepository;

    @Mock
    private DrugThresholdRepository drugThresholdRepository;

    @Mock
    private LlmServiceClient llmServiceClient;

    @Mock
    private PayloadSanitizer payloadSanitizer;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseOrderEmailService purchaseOrderEmailService;

    private PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(
                currentUserService,
                locationRepository,
                forecastRepository,
                drugRepository,
                currentStockRepository,
                drugThresholdRepository,
                llmServiceClient,
                payloadSanitizer,
                Clock.fixed(Instant.parse("2026-04-23T10:15:30Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void previewBuildsEditableDraftFromLatestForecastAndLlmOutput() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String din = "12345678";

        Location location = location(locationId, organizationId, "Downtown Pharmacy", "123 Bank St, Ottawa, ON");
        Forecast forecast = forecast(locationId, din, Instant.parse("2026-04-23T09:00:00Z"), 14, 12, ForecastConfidence.high, ReorderStatus.red, new BigDecimal("4.5"), new BigDecimal("3.0"));
        Drug drug = drug(din, "Amoxicillin", "500 mg", "capsule");
        CurrentStock stock = currentStock(locationId, din, 8);
        DrugThreshold threshold = threshold(locationId, din, 2);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId)).thenReturn(List.of(forecast));
        when(drugRepository.findByDinIn(List.of(din))).thenReturn(List.of(drug));
        when(currentStockRepository.findByLocationIdAndDin(locationId, din)).thenReturn(Optional.of(stock));
        when(drugThresholdRepository.findByLocationIdAndDin(locationId, din)).thenReturn(Optional.of(threshold));
        when(llmServiceClient.generatePurchaseOrderText(any(PurchaseOrderPayload.class))).thenReturn(
                new PurchaseOrderTextResult(
                        """
                        Summary: Purchase Order — 2026-04-23 — 1 items — Estimated 12 units
                        Drug Name | DIN | Qty to Order | Priority
                        Amoxicillin 500 mg | 12345678 | 12 | URGENT
                        """,
                        Instant.parse("2026-04-23T10:16:00Z"),
                        null
                )
        );

        PurchaseOrderPreviewResponse response = service.preview(
                locationId,
                new PurchaseOrderPreviewRequest(null, null)
        );

        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-04-23T10:16:00Z"));
        assertThat(response.orderText()).contains("Purchase Order");
        assertThat(response.lineItems()).hasSize(1);
        assertThat(response.lineItems().get(0).din()).isEqualTo(din);
        assertThat(response.lineItems().get(0).drugName()).isEqualTo("Amoxicillin");
        assertThat(response.lineItems().get(0).form()).isEqualTo("capsule");
        assertThat(response.lineItems().get(0).currentStock()).isEqualTo(8);
        assertThat(response.lineItems().get(0).predictedQuantity()).isEqualTo(12);
        assertThat(response.lineItems().get(0).recommendedQuantity()).isEqualTo(12);
        assertThat(response.lineItems().get(0).quantityToOrder()).isEqualTo(12);
        assertThat(response.lineItems().get(0).priority()).isEqualTo("URGENT");
        assertThat(response.lineItems().get(0).leadTimeDays()).isEqualTo(2);
        assertThat(response.lineItems().get(0).avgDailyDemand()).isEqualByComparingTo("3.0");

        ArgumentCaptor<PurchaseOrderPayload> payloadCaptor = ArgumentCaptor.forClass(PurchaseOrderPayload.class);
        verify(payloadSanitizer).sanitize(any());
        verify(llmServiceClient).generatePurchaseOrderText(payloadCaptor.capture());
        PurchaseOrderPayload payload = payloadCaptor.getValue();
        assertThat(payload.pharmacyName()).isEqualTo("Downtown Pharmacy");
        assertThat(payload.locationAddress()).isEqualTo("123 Bank St, Ottawa, ON");
        assertThat(payload.today()).isEqualTo("2026-04-23");
        assertThat(payload.horizonDays()).isEqualTo(14);
        assertThat(payload.maxTokens()).isEqualTo(1500);
        assertThat(payload.drugs()).containsExactly(
                new PurchaseOrderDrugPayload(
                        "Amoxicillin",
                        "500 mg",
                        din,
                        8,
                        12,
                        new BigDecimal("4.5"),
                        "RED",
                        new BigDecimal("3.0"),
                        2
                )
        );
    }

    @Test
    void previewSkipsLlmWhenNoForecastsMatchTheRequestedStatuses() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId, "Downtown Pharmacy", "123 Bank St, Ottawa, ON")));
        when(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId)).thenReturn(List.of());

        PurchaseOrderPreviewResponse response = service.preview(
                locationId,
                new PurchaseOrderPreviewRequest(null, List.of(ReorderStatus.green))
        );

        assertThat(response.lineItems()).isEmpty();
        verify(llmServiceClient, never()).generatePurchaseOrderText(any());
    }

    @Test
    void generatePersistsTheEditedDraftAndReturnsTheCommittedOrderId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID savedOrderId = UUID.randomUUID();

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId, "Downtown Pharmacy", "123 Bank St, Ottawa, ON")));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", savedOrderId);
            return order;
        });

        PurchaseOrderService persistingService = new PurchaseOrderService(
                currentUserService,
                locationRepository,
                forecastRepository,
                drugRepository,
                currentStockRepository,
                drugThresholdRepository,
                llmServiceClient,
                payloadSanitizer,
                purchaseOrderRepository,
                null,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-23T10:15:30Z"), ZoneOffset.UTC)
        );

        PurchaseOrderPreviewResponse draft = new PurchaseOrderPreviewResponse(
                Instant.parse("2026-04-23T10:16:00Z"),
                "Purchase Order — 2026-04-23 — 1 items — Estimated 12 units",
                List.of(new PurchaseOrderPreviewResponse.LineItem(
                        "12345678",
                        "Amoxicillin",
                        "500 mg",
                        "capsule",
                        8,
                        12,
                        12,
                        new BigDecimal("4.5"),
                        "RED",
                        new BigDecimal("3.0"),
                        2,
                        15,
                        "URGENT"
                ))
        );

        PurchaseOrderGenerateResponse response = persistingService.generate(locationId, draft);

        assertThat(response.orderId()).isEqualTo(savedOrderId);
        assertThat(response.orderText()).isEqualTo(draft.orderText());
        assertThat(response.lineItems()).hasSize(1);
        assertThat(response.lineItems().get(0).quantityToOrder()).isEqualTo(15);
        assertThat(response.lineItems().get(0).recommendedQuantity()).isEqualTo(12);

        ArgumentCaptor<PurchaseOrder> orderCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderRepository).save(orderCaptor.capture());
        PurchaseOrder savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getLocationId()).isEqualTo(locationId);
        assertThat(savedOrder.getGrokOutput()).isEqualTo(draft.orderText());
        assertThat(savedOrder.getStatus()).isEqualTo(ca.pharmaforecast.backend.purchaseorder.PurchaseOrderStatus.draft);
        assertThat(savedOrder.getLineItems()).contains("\"quantity_to_order\":15");
        assertThat(savedOrder.getLineItems()).contains("\"recommended_quantity\":12");
    }

    @Test
    void historyReturnsOrdersInDescendingOrderWithQuantitiesSummedFromPersistedLineItems() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        PurchaseOrder older = order(locationId, Instant.parse("2026-04-22T10:00:00Z"), PurchaseOrderStatus.draft, """
                [
                  {
                    "din": "11111111",
                    "drug_name": "Drug A",
                    "strength": "10 mg",
                    "form": "tablet",
                    "current_stock": 5,
                    "predicted_quantity": 8,
                    "recommended_quantity": 6,
                    "days_of_supply": 4.5,
                    "reorder_status": "RED",
                    "avg_daily_demand": 2.0,
                    "lead_time_days": 2,
                    "quantity_to_order": 6,
                    "priority": "URGENT"
                  }
                ]
                """);
        ReflectionTestUtils.setField(older, "id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        PurchaseOrder newer = order(locationId, Instant.parse("2026-04-23T10:00:00Z"), PurchaseOrderStatus.sent, """
                [
                  {
                    "din": "22222222",
                    "drug_name": "Drug B",
                    "strength": "20 mg",
                    "form": "capsule",
                    "current_stock": 1,
                    "predicted_quantity": 3,
                    "recommended_quantity": 4,
                    "days_of_supply": 2.5,
                    "reorder_status": "AMBER",
                    "avg_daily_demand": 1.0,
                    "lead_time_days": 2,
                    "quantity_to_order": 4,
                    "priority": "STANDARD"
                  }
                ]
                """);
        ReflectionTestUtils.setField(newer, "id", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId, "Downtown Pharmacy", "123 Bank St, Ottawa, ON")));
        when(purchaseOrderRepository.findByLocationIdOrderByGeneratedAtDesc(locationId)).thenReturn(List.of(newer, older));

        PurchaseOrderService persistingService = persistedService();

        List<PurchaseOrderHistoryResponse> history = persistingService.history(locationId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).orderId()).isEqualTo(newer.getId());
        assertThat(history.get(0).status()).isEqualTo("sent");
        assertThat(history.get(0).itemCount()).isEqualTo(1);
        assertThat(history.get(0).totalUnits()).isEqualTo(4);
        assertThat(history.get(1).orderId()).isEqualTo(older.getId());
    }

    @Test
    void getReturnsPersistedPurchaseOrderForTheRequestedLocation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PurchaseOrder order = order(locationId, Instant.parse("2026-04-23T10:00:00Z"), PurchaseOrderStatus.draft, """
                [
                  {
                    "din": "12345678",
                    "drug_name": "Amoxicillin",
                    "strength": "500 mg",
                    "form": "capsule",
                    "current_stock": 8,
                    "predicted_quantity": 12,
                    "recommended_quantity": 15,
                    "days_of_supply": 4.5,
                    "reorder_status": "RED",
                    "avg_daily_demand": 3.0,
                    "lead_time_days": 2,
                    "quantity_to_order": 15,
                    "priority": "URGENT"
                  }
                ]
                """);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId, "Downtown Pharmacy", "123 Bank St, Ottawa, ON")));
        when(purchaseOrderRepository.findByIdAndLocationId(orderId, locationId)).thenReturn(Optional.of(order));

        PurchaseOrderService persistingService = persistedService();

        var method = PurchaseOrderService.class.getMethod("get", UUID.class, UUID.class);
        Object result = method.invoke(persistingService, locationId, orderId);

        assertThat(result).isInstanceOf(PurchaseOrderDetailResponse.class);
        PurchaseOrderDetailResponse response = (PurchaseOrderDetailResponse) result;
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-04-23T10:00:00Z"));
        assertThat(response.orderText()).isEqualTo("Order text");
        assertThat(response.lineItems()).hasSize(1);
        assertThat(response.lineItems().get(0).quantityToOrder()).isEqualTo(15);
    }

    @Test
    void updateReplacesThePersistedDraftForTheSameOrderId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PurchaseOrder order = order(locationId, Instant.parse("2026-04-23T10:00:00Z"), PurchaseOrderStatus.draft, """
                [
                  {
                    "din": "12345678",
                    "drug_name": "Amoxicillin",
                    "strength": "500 mg",
                    "form": "capsule",
                    "current_stock": 8,
                    "predicted_quantity": 12,
                    "recommended_quantity": 15,
                    "days_of_supply": 4.5,
                    "reorder_status": "RED",
                    "avg_daily_demand": 3.0,
                    "lead_time_days": 2,
                    "quantity_to_order": 15,
                    "priority": "URGENT"
                  }
                ]
                """);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId, "Downtown Pharmacy", "123 Bank St, Ottawa, ON")));
        when(purchaseOrderRepository.findByIdAndLocationId(orderId, locationId)).thenReturn(Optional.of(order));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrderService persistingService = persistedService();

        var method = PurchaseOrderService.class.getMethod("update", UUID.class, UUID.class, PurchaseOrderPreviewResponse.class);
        PurchaseOrderPreviewResponse draft = new PurchaseOrderPreviewResponse(
                Instant.parse("2026-04-23T10:30:00Z"),
                "Updated purchase order text",
                List.of(new PurchaseOrderPreviewResponse.LineItem(
                        "12345678",
                        "Amoxicillin",
                        "500 mg",
                        "capsule",
                        8,
                        12,
                        18,
                        new BigDecimal("4.5"),
                        "RED",
                        new BigDecimal("3.0"),
                        2,
                        18,
                        "URGENT"
                ))
        );

        Object result = method.invoke(persistingService, locationId, orderId, draft);

        assertThat(result).isInstanceOf(PurchaseOrderGenerateResponse.class);
        PurchaseOrderGenerateResponse response = (PurchaseOrderGenerateResponse) result;
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-04-23T10:30:00Z"));
        assertThat(response.orderText()).isEqualTo("Updated purchase order text");
        assertThat(response.lineItems()).hasSize(1);
        assertThat(response.lineItems().get(0).quantityToOrder()).isEqualTo(18);

        ArgumentCaptor<PurchaseOrder> orderCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderRepository).save(orderCaptor.capture());
        PurchaseOrder saved = orderCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(orderId);
        assertThat(saved.getLocationId()).isEqualTo(locationId);
        assertThat(saved.getGrokOutput()).isEqualTo("Updated purchase order text");
        assertThat(saved.getStatus()).isEqualTo(PurchaseOrderStatus.draft);
        assertThat(saved.getLineItems()).contains("\"quantity_to_order\":18");
    }

    @Test
    void exportCsvReturnsPersistedLineItemsWithExpectedColumns() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PurchaseOrder order = order(locationId, Instant.parse("2026-04-23T10:00:00Z"), PurchaseOrderStatus.draft, """
                [
                  {
                    "din": "12345678",
                    "drug_name": "Amoxicillin",
                    "strength": "500 mg",
                    "form": "capsule",
                    "current_stock": 8,
                    "predicted_quantity": 12,
                    "recommended_quantity": 15,
                    "days_of_supply": 4.5,
                    "reorder_status": "RED",
                    "avg_daily_demand": 3.0,
                    "lead_time_days": 2,
                    "quantity_to_order": 15,
                    "priority": "URGENT"
                  }
                ]
                """);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId, "Downtown Pharmacy", "123 Bank St, Ottawa, ON")));
        when(purchaseOrderRepository.findByIdAndLocationId(orderId, locationId)).thenReturn(Optional.of(order));

        PurchaseOrderService persistingService = persistedService();

        String csv = new String(persistingService.exportCsv(locationId, orderId));

        assertThat(csv).contains("Drug Name,DIN,Strength,Form,Qty to Order,Priority,Current Stock,Days of Supply");
        assertThat(csv).contains("Amoxicillin,12345678,500 mg,capsule,15,URGENT,8,4.5");
    }

    @Test
    void sendUsesResendAndMarksTheOrderAsSent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PurchaseOrder order = order(locationId, Instant.parse("2026-04-23T10:00:00Z"), PurchaseOrderStatus.draft, """
                [
                  {
                    "din": "12345678",
                    "drug_name": "Amoxicillin",
                    "strength": "500 mg",
                    "form": "capsule",
                    "current_stock": 8,
                    "predicted_quantity": 12,
                    "recommended_quantity": 15,
                    "days_of_supply": 4.5,
                    "reorder_status": "RED",
                    "avg_daily_demand": 3.0,
                    "lead_time_days": 2,
                    "quantity_to_order": 15,
                    "priority": "URGENT"
                  }
                ]
                """);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId, "Downtown Pharmacy", "123 Bank St, Ottawa, ON")));
        when(purchaseOrderRepository.findByIdAndLocationId(orderId, locationId)).thenReturn(Optional.of(order));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrderService persistingService = persistedService();

        persistingService.send(locationId, orderId, new PurchaseOrderSendRequest("buyer@wholesaler.example", "Please rush this order"));

        ArgumentCaptor<PurchaseOrder> orderCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(PurchaseOrderStatus.sent);
        verify(purchaseOrderEmailService).sendPurchaseOrder(
                eq("buyer@wholesaler.example"),
                eq("Purchase Order — Downtown Pharmacy — 2026-04-23"),
                eq("Please rush this order\n\nPlease find our purchase order attached."),
                eq("purchase-order-2026-04-23.pdf"),
                any(byte[].class)
        );
    }

    private Location location(UUID id, UUID organizationId, String name, String address) throws Exception {
        Location location = org.springframework.util.ReflectionUtils.accessibleConstructor(Location.class).newInstance();
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "organizationId", organizationId);
        ReflectionTestUtils.setField(location, "name", name);
        ReflectionTestUtils.setField(location, "address", address);
        return location;
    }

    private Forecast forecast(
            UUID locationId,
            String din,
            Instant generatedAt,
            int horizonDays,
            int predictedQuantity,
            ForecastConfidence confidence,
            ReorderStatus reorderStatus,
            BigDecimal daysOfSupply,
            BigDecimal avgDailyDemand
    ) throws Exception {
        Forecast forecast = org.springframework.util.ReflectionUtils.accessibleConstructor(Forecast.class).newInstance();
        ReflectionTestUtils.setField(forecast, "locationId", locationId);
        ReflectionTestUtils.setField(forecast, "din", din);
        ReflectionTestUtils.setField(forecast, "generatedAt", generatedAt);
        ReflectionTestUtils.setField(forecast, "forecastHorizonDays", horizonDays);
        ReflectionTestUtils.setField(forecast, "predictedQuantity", predictedQuantity);
        ReflectionTestUtils.setField(forecast, "confidence", confidence);
        ReflectionTestUtils.setField(forecast, "daysOfSupply", daysOfSupply);
        ReflectionTestUtils.setField(forecast, "reorderStatus", reorderStatus);
        ReflectionTestUtils.setField(forecast, "avgDailyDemand", avgDailyDemand);
        ReflectionTestUtils.setField(forecast, "prophetLower", new BigDecimal("10"));
        ReflectionTestUtils.setField(forecast, "prophetUpper", new BigDecimal("14"));
        ReflectionTestUtils.setField(forecast, "reorderPoint", new BigDecimal("11"));
        ReflectionTestUtils.setField(forecast, "modelPath", "prophet");
        ReflectionTestUtils.setField(forecast, "dataPointsUsed", 28);
        return forecast;
    }

    private Drug drug(String din, String name, String strength, String form) throws Exception {
        Drug drug = org.springframework.util.ReflectionUtils.accessibleConstructor(Drug.class).newInstance();
        ReflectionTestUtils.setField(drug, "din", din);
        ReflectionTestUtils.setField(drug, "name", name);
        ReflectionTestUtils.setField(drug, "strength", strength);
        ReflectionTestUtils.setField(drug, "form", form);
        return drug;
    }

    private CurrentStock currentStock(UUID locationId, String din, int quantity) throws Exception {
        CurrentStock stock = org.springframework.util.ReflectionUtils.accessibleConstructor(CurrentStock.class).newInstance();
        ReflectionTestUtils.setField(stock, "locationId", locationId);
        ReflectionTestUtils.setField(stock, "din", din);
        ReflectionTestUtils.setField(stock, "quantity", quantity);
        return stock;
    }

    private DrugThreshold threshold(UUID locationId, String din, int leadTimeDays) throws Exception {
        DrugThreshold threshold = org.springframework.util.ReflectionUtils.accessibleConstructor(DrugThreshold.class).newInstance();
        ReflectionTestUtils.setField(threshold, "locationId", locationId);
        ReflectionTestUtils.setField(threshold, "din", din);
        ReflectionTestUtils.setField(threshold, "leadTimeDays", leadTimeDays);
        ReflectionTestUtils.setField(threshold, "redThresholdDays", 3);
        ReflectionTestUtils.setField(threshold, "amberThresholdDays", 7);
        ReflectionTestUtils.setField(threshold, "notificationsEnabled", true);
        ReflectionTestUtils.setField(threshold, "safetyMultiplier", ca.pharmaforecast.backend.forecast.SafetyMultiplier.balanced);
        return threshold;
    }

    private PurchaseOrder order(UUID locationId, Instant generatedAt, PurchaseOrderStatus status, String lineItems) throws Exception {
        PurchaseOrder order = org.springframework.util.ReflectionUtils.accessibleConstructor(PurchaseOrder.class).newInstance();
        ReflectionTestUtils.setField(order, "locationId", locationId);
        ReflectionTestUtils.setField(order, "generatedAt", generatedAt);
        ReflectionTestUtils.setField(order, "grokOutput", "Order text");
        ReflectionTestUtils.setField(order, "status", status);
        ReflectionTestUtils.setField(order, "lineItems", lineItems);
        return order;
    }

    private PurchaseOrderService persistedService() {
        return new PurchaseOrderService(
                currentUserService,
                locationRepository,
                forecastRepository,
                drugRepository,
                currentStockRepository,
                drugThresholdRepository,
                llmServiceClient,
                payloadSanitizer,
                purchaseOrderRepository,
                purchaseOrderEmailService,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-23T10:15:30Z"), ZoneOffset.UTC)
        );
    }
}
