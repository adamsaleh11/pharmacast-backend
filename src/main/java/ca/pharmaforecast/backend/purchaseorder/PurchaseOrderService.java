package ca.pharmaforecast.backend.purchaseorder;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.DrugThresholdRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.ReorderStatus;
import ca.pharmaforecast.backend.llm.LlmServiceClient;
import ca.pharmaforecast.backend.llm.LlmUnavailableException;
import ca.pharmaforecast.backend.llm.PayloadSanitizer;
import ca.pharmaforecast.backend.llm.PurchaseOrderDrugPayload;
import ca.pharmaforecast.backend.llm.PurchaseOrderPayload;
import ca.pharmaforecast.backend.llm.PurchaseOrderTextResult;
import ca.pharmaforecast.backend.location.LocationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService {

    private final CurrentUserService currentUserService;
    private final LocationRepository locationRepository;
    private final ForecastRepository forecastRepository;
    private final DrugRepository drugRepository;
    private final CurrentStockRepository currentStockRepository;
    private final DrugThresholdRepository drugThresholdRepository;
    private final LlmServiceClient llmServiceClient;
    private final PayloadSanitizer payloadSanitizer;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderEmailService purchaseOrderEmailService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PurchaseOrderService(
            CurrentUserService currentUserService,
            LocationRepository locationRepository,
            ForecastRepository forecastRepository,
            DrugRepository drugRepository,
            CurrentStockRepository currentStockRepository,
            DrugThresholdRepository drugThresholdRepository,
            LlmServiceClient llmServiceClient,
            PayloadSanitizer payloadSanitizer,
            Clock clock
    ) {
        this(
                currentUserService,
                locationRepository,
                forecastRepository,
                drugRepository,
                currentStockRepository,
                drugThresholdRepository,
                llmServiceClient,
                payloadSanitizer,
                null,
                null,
                null,
                clock
        );
    }

    @Autowired
    public PurchaseOrderService(
            CurrentUserService currentUserService,
            LocationRepository locationRepository,
            ForecastRepository forecastRepository,
            DrugRepository drugRepository,
            CurrentStockRepository currentStockRepository,
            DrugThresholdRepository drugThresholdRepository,
            LlmServiceClient llmServiceClient,
            PayloadSanitizer payloadSanitizer,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderEmailService purchaseOrderEmailService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
        this.forecastRepository = forecastRepository;
        this.drugRepository = drugRepository;
        this.currentStockRepository = currentStockRepository;
        this.drugThresholdRepository = drugThresholdRepository;
        this.llmServiceClient = llmServiceClient;
        this.payloadSanitizer = payloadSanitizer;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderEmailService = purchaseOrderEmailService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderPreviewResponse preview(UUID locationId, PurchaseOrderPreviewRequest request) {
        validateLocationOwnership(locationId);
        List<String> requestedDins = request == null || request.dins() == null ? List.of() : request.dins();
        Set<String> allowedStatuses = allowedStatuses(request);
        List<Forecast> latestForecasts = latestEligibleForecasts(locationId, requestedDins, allowedStatuses);
        if (latestForecasts.isEmpty()) {
            return new PurchaseOrderPreviewResponse(now(), "", List.of());
        }

        Map<String, Drug> drugsByDin = drugRepository.findByDinIn(latestForecasts.stream().map(Forecast::getDin).toList())
                .stream()
                .collect(Collectors.toMap(Drug::getDin, Function.identity(), (existing, replacement) -> existing, LinkedHashMap::new));

        List<ForecastBundle> bundles = new ArrayList<>();
        List<PurchaseOrderDrugPayload> payloadDrugs = new ArrayList<>();
        for (Forecast forecast : latestForecasts) {
            Drug drug = drugsByDin.get(forecast.getDin());
            if (drug == null) {
                continue;
            }
            int currentStock = currentStockRepository.findByLocationIdAndDin(locationId, forecast.getDin())
                    .map(stock -> stock.getQuantity() == null ? 0 : stock.getQuantity())
                    .orElse(0);
            int leadTimeDays = drugThresholdRepository.findByLocationIdAndDin(locationId, forecast.getDin())
                    .map(threshold -> threshold.getLeadTimeDays() == null ? 2 : threshold.getLeadTimeDays())
                    .orElse(2);
            bundles.add(new ForecastBundle(forecast, drug, currentStock, leadTimeDays));
            payloadDrugs.add(new PurchaseOrderDrugPayload(
                    drug.getName(),
                    drug.getStrength(),
                    forecast.getDin(),
                    currentStock,
                    forecast.getPredictedQuantity() == null ? 0 : forecast.getPredictedQuantity(),
                    safeBigDecimal(forecast.getDaysOfSupply()),
                    reorderStatus(forecast),
                    safeBigDecimal(forecast.getAvgDailyDemand()),
                    leadTimeDays
            ));
        }

        if (payloadDrugs.isEmpty()) {
            return new PurchaseOrderPreviewResponse(now(), "", List.of());
        }

        PurchaseOrderPayload payload = new PurchaseOrderPayload(
                locationRepository.findById(locationId).map(location -> location.getName()).orElse(""),
                locationRepository.findById(locationId).map(location -> location.getAddress()).orElse(""),
                LocalDate.now(clock).toString(),
                latestForecasts.get(0).getForecastHorizonDays() == null ? 14 : latestForecasts.get(0).getForecastHorizonDays(),
                payloadDrugs,
                1500
        );
        payloadSanitizer.sanitize(payload);
        PurchaseOrderTextResult result = llmServiceClient.generatePurchaseOrderText(payload);
        if (result.error() != null) {
            throw new LlmUnavailableException();
        }

        Map<String, ParsedLineItem> parsedLineItems = parseLineItems(result.orderText());
        List<PurchaseOrderPreviewResponse.LineItem> lineItems = bundles.stream()
                .map(bundle -> toPreviewLineItem(bundle, parsedLineItems.get(bundle.forecast().getDin())))
                .toList();

        return new PurchaseOrderPreviewResponse(
                result.generatedAt() == null ? now() : result.generatedAt(),
                result.orderText(),
                lineItems
        );
    }

    @Transactional
    public PurchaseOrderGenerateResponse generate(UUID locationId, PurchaseOrderPreviewResponse draft) {
        validateLocationOwnership(locationId);
        PurchaseOrder order = new PurchaseOrder();
        order.setLocationId(locationId);
        order.setStatus(ca.pharmaforecast.backend.purchaseorder.PurchaseOrderStatus.draft);
        PurchaseOrder saved = savePurchaseOrder(order, draft);
        return new PurchaseOrderGenerateResponse(
                saved.getId(),
                saved.getGeneratedAt(),
                saved.getGrokOutput(),
                draft.lineItems() == null ? List.of() : draft.lineItems()
        );
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDetailResponse get(UUID locationId, UUID orderId) {
        PurchaseOrder order = loadOrder(locationId, orderId);
        return new PurchaseOrderDetailResponse(
                order.getId(),
                order.getGeneratedAt(),
                order.getGrokOutput(),
                lineItems(order)
        );
    }

    @Transactional
    public PurchaseOrderGenerateResponse update(UUID locationId, UUID orderId, PurchaseOrderPreviewResponse draft) {
        PurchaseOrder order = loadOrder(locationId, orderId);
        PurchaseOrderStatus status = order.getStatus() == null ? PurchaseOrderStatus.draft : order.getStatus();
        order.setStatus(status);
        PurchaseOrder saved = savePurchaseOrder(order, draft);
        return new PurchaseOrderGenerateResponse(
                saved.getId(),
                saved.getGeneratedAt(),
                saved.getGrokOutput(),
                draft.lineItems() == null ? List.of() : draft.lineItems()
        );
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderHistoryResponse> history(UUID locationId) {
        validateLocationOwnership(locationId);
        if (purchaseOrderRepository == null) {
            return List.of();
        }
        return purchaseOrderRepository.findByLocationIdOrderByGeneratedAtDesc(locationId).stream()
                .limit(20)
                .map(order -> new PurchaseOrderHistoryResponse(
                        order.getId(),
                        order.getGeneratedAt(),
                        order.getStatus() == null ? null : order.getStatus().name(),
                        lineItems(order).size(),
                        lineItems(order).stream().mapToInt(PurchaseOrderPreviewResponse.LineItem::quantityToOrder).sum()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID locationId, UUID orderId) {
        PurchaseOrder order = loadOrder(locationId, orderId);
        List<PurchaseOrderPreviewResponse.LineItem> lineItems = lineItems(order);
        StringBuilder csv = new StringBuilder();
        csv.append("Drug Name,DIN,Strength,Form,Qty to Order,Priority,Current Stock,Days of Supply\n");
        for (PurchaseOrderPreviewResponse.LineItem lineItem : lineItems) {
            csv.append(csv(lineItem.drugName())).append(',')
                    .append(csv(lineItem.din())).append(',')
                    .append(csv(lineItem.strength())).append(',')
                    .append(csv(lineItem.form())).append(',')
                    .append(lineItem.quantityToOrder()).append(',')
                    .append(csv(lineItem.priority())).append(',')
                    .append(lineItem.currentStock()).append(',')
                    .append(csv(lineItem.daysOfSupply().toPlainString()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(UUID locationId, UUID orderId) {
        PurchaseOrder order = loadOrder(locationId, orderId);
        var location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found"));
        List<PurchaseOrderPreviewResponse.LineItem> lineItems = lineItems(order);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDocument = new PdfDocument(writer);
            try (Document document = new Document(pdfDocument, PageSize.LETTER)) {
                document.add(new Paragraph("Purchase Order").setFontSize(18));
                document.add(new Paragraph(location.getName()));
                document.add(new Paragraph(location.getAddress()));
                document.add(new Paragraph("Generated: " + order.getGeneratedAt()));

                Table table = new Table(new float[]{3, 2, 1, 1});
                table.setWidth(UnitValue.createPercentValue(100));
                table.addHeaderCell(header("Drug Name"));
                table.addHeaderCell(header("DIN"));
                table.addHeaderCell(header("Qty"));
                table.addHeaderCell(header("Priority"));
                for (PurchaseOrderPreviewResponse.LineItem lineItem : lineItems) {
                    table.addCell(cell(lineItem.drugName()));
                    table.addCell(cell(lineItem.din()));
                    table.addCell(cell(String.valueOf(lineItem.quantityToOrder())));
                    table.addCell(cell(lineItem.priority()));
                }
                document.add(table);
                document.add(new Paragraph("Total Units: " + totalUnits(lineItems)));
            }
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not generate purchase order PDF", ex);
        }
    }

    @Transactional
    public void send(UUID locationId, UUID orderId, PurchaseOrderSendRequest request) {
        PurchaseOrder order = loadOrder(locationId, orderId);
        String date = order.getGeneratedAt() == null ? LocalDate.now(clock).toString() : order.getGeneratedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
        String subject = "Purchase Order — %s — %s".formatted(
                locationRepository.findById(locationId).map(location -> location.getName()).orElse("Pharmacy"),
                date
        );
        String note = request == null || request.note() == null || request.note().isBlank()
                ? ""
                : request.note().trim() + "\n\n";
        String body = note + "Please find our purchase order attached.";
        byte[] pdf = exportPdf(locationId, orderId);
        String filename = "purchase-order-%s.pdf".formatted(date);
        if (purchaseOrderEmailService == null) {
            throw new UnsupportedOperationException("Purchase order email service is not configured");
        }
        purchaseOrderEmailService.sendPurchaseOrder(
                request == null ? null : request.recipientEmail(),
                subject,
                body,
                filename,
                pdf
        );
        order.setStatus(PurchaseOrderStatus.sent);
        purchaseOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Instant generatedAt(UUID locationId, UUID orderId) {
        return loadOrder(locationId, orderId).getGeneratedAt();
    }

    private void validateLocationOwnership(UUID locationId) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        var location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AccessDeniedException("Location is not accessible"));
        if (!location.getOrganizationId().equals(currentUser.organizationId())) {
            throw new AccessDeniedException("Location is not accessible");
        }
    }

    private Set<String> allowedStatuses(PurchaseOrderPreviewRequest request) {
        List<ReorderStatus> statuses = request == null || request.includeStatus() == null || request.includeStatus().isEmpty()
                ? List.of(ReorderStatus.red, ReorderStatus.amber)
                : request.includeStatus();
        return statuses.stream()
                .filter(value -> value != null)
                .map(value -> value.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private List<Forecast> latestEligibleForecasts(UUID locationId, List<String> requestedDins, Set<String> allowedStatuses) {
        Map<String, Forecast> latestByDin = new LinkedHashMap<>();
        for (Forecast forecast : forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId)) {
            String reorderStatus = forecast.getReorderStatus() == null ? "" : forecast.getReorderStatus().name().toLowerCase(Locale.ROOT);
            if (!allowedStatuses.contains(reorderStatus)) {
                continue;
            }
            if (!requestedDins.isEmpty() && !requestedDins.contains(forecast.getDin())) {
                continue;
            }
            latestByDin.putIfAbsent(forecast.getDin(), forecast);
        }
        return latestByDin.values().stream()
                .sorted(Comparator.comparing(Forecast::getGeneratedAt).reversed())
                .toList();
    }

    private PurchaseOrderPreviewResponse.LineItem toPreviewLineItem(ForecastBundle bundle, ParsedLineItem parsedLineItem) {
        int recommendedQuantity = parsedLineItem == null || parsedLineItem.quantityToOrder() == null
                ? defaultRecommendedQuantity(bundle.forecast(), bundle.currentStock(), bundle.leadTimeDays())
                : parsedLineItem.quantityToOrder();
        String priority = parsedLineItem == null || parsedLineItem.priority() == null || parsedLineItem.priority().isBlank()
                ? priorityFor(bundle.forecast())
                : parsedLineItem.priority().toUpperCase(Locale.ROOT);
        return new PurchaseOrderPreviewResponse.LineItem(
                bundle.forecast().getDin(),
                bundle.drug().getName(),
                bundle.drug().getStrength(),
                bundle.drug().getForm(),
                bundle.currentStock(),
                bundle.forecast().getPredictedQuantity() == null ? 0 : bundle.forecast().getPredictedQuantity(),
                recommendedQuantity,
                safeBigDecimal(bundle.forecast().getDaysOfSupply()),
                reorderStatus(bundle.forecast()),
                safeBigDecimal(bundle.forecast().getAvgDailyDemand()),
                bundle.leadTimeDays(),
                recommendedQuantity,
                priority
        );
    }

    private int defaultRecommendedQuantity(Forecast forecast, int currentStock, int leadTimeDays) {
        int predictedQuantity = forecast.getPredictedQuantity() == null ? 0 : forecast.getPredictedQuantity();
        BigDecimal avgDailyDemand = safeBigDecimal(forecast.getAvgDailyDemand());
        BigDecimal safetyBuffer = avgDailyDemand.multiply(BigDecimal.valueOf(leadTimeDays));
        BigDecimal recommended = BigDecimal.valueOf(predictedQuantity)
                .subtract(BigDecimal.valueOf(currentStock))
                .add(safetyBuffer);
        return recommended.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String reorderStatus(Forecast forecast) {
        return forecast.getReorderStatus() == null ? "GREEN" : forecast.getReorderStatus().name().toUpperCase(Locale.ROOT);
    }

    private String priorityFor(Forecast forecast) {
        if (forecast.getReorderStatus() == ReorderStatus.red) {
            return "URGENT";
        }
        if (forecast.getReorderStatus() == ReorderStatus.amber) {
            return "STANDARD";
        }
        return "OPTIONAL";
    }

    private Map<String, ParsedLineItem> parseLineItems(String orderText) {
        Map<String, ParsedLineItem> parsed = new LinkedHashMap<>();
        if (orderText == null || orderText.isBlank()) {
            return parsed;
        }
        for (String rawLine : orderText.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank() || !line.contains("|")) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("drug name") || lower.contains("qty to order") || line.matches("[|\\s:-]+")) {
                continue;
            }
            String[] pieces = line.split("\\|");
            List<String> columns = new ArrayList<>();
            for (String piece : pieces) {
                String value = piece.trim();
                if (!value.isBlank()) {
                    columns.add(value);
                }
            }
            if (columns.size() < 4) {
                continue;
            }
            String din = columns.get(1);
            parsed.putIfAbsent(din, new ParsedLineItem(
                    columns.get(0),
                    din,
                    parseQuantity(columns.get(2)),
                    columns.get(3).toUpperCase(Locale.ROOT)
            ));
        }
        return parsed;
    }

    private Integer parseQuantity(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9-]", "");
        if (digits.isBlank()) {
            return null;
        }
        return Integer.parseInt(digits);
    }

    private PurchaseOrder loadOrder(UUID locationId, UUID orderId) {
        validateLocationOwnership(locationId);
        if (purchaseOrderRepository == null) {
            throw new UnsupportedOperationException("Purchase order persistence is not configured");
        }
        return purchaseOrderRepository.findByIdAndLocationId(orderId, locationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order not found"));
    }

    private PurchaseOrder savePurchaseOrder(PurchaseOrder order, PurchaseOrderPreviewResponse draft) {
        if (purchaseOrderRepository == null || objectMapper == null) {
            throw new UnsupportedOperationException("Purchase order persistence is not configured");
        }
        order.setGeneratedAt(draft.generatedAt() == null ? now() : draft.generatedAt());
        order.setGrokOutput(draft.orderText() == null ? "" : draft.orderText());
        try {
            order.setLineItems(objectMapper.writeValueAsString(draft.lineItems() == null ? List.of() : draft.lineItems()));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize purchase order line items", ex);
        }
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return saved;
    }

    private List<PurchaseOrderPreviewResponse.LineItem> lineItems(PurchaseOrder order) {
        if (order == null || order.getLineItems() == null || order.getLineItems().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(order.getLineItems(), new TypeReference<List<PurchaseOrderPreviewResponse.LineItem>>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("Could not parse purchase order line items", ex);
        }
    }

    private int totalUnits(List<PurchaseOrderPreviewResponse.LineItem> lineItems) {
        return lineItems.stream().mapToInt(PurchaseOrderPreviewResponse.LineItem::quantityToOrder).sum();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private Cell header(String value) {
        return new Cell().add(new Paragraph(value)).setBackgroundColor(ColorConstants.LIGHT_GRAY);
    }

    private Cell cell(String value) {
        return new Cell().add(new Paragraph(value == null ? "" : value)).setBorder(Border.NO_BORDER);
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private record ForecastBundle(Forecast forecast, Drug drug, int currentStock, int leadTimeDays) {
    }

    private record ParsedLineItem(String drugName, String din, Integer quantityToOrder, String priority) {
    }
}
