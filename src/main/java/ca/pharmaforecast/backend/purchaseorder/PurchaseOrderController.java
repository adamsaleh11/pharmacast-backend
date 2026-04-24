package ca.pharmaforecast.backend.purchaseorder;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/locations/{locationId}/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @PostMapping("/preview")
    public PurchaseOrderPreviewResponse preview(
            @PathVariable UUID locationId,
            @Valid @RequestBody PurchaseOrderPreviewRequest request
    ) {
        return purchaseOrderService.preview(locationId, request);
    }

    @PostMapping("/generate")
    public PurchaseOrderGenerateResponse generate(
            @PathVariable UUID locationId,
            @Valid @RequestBody PurchaseOrderPreviewResponse draft
    ) {
        return purchaseOrderService.generate(locationId, draft);
    }

    @GetMapping
    public List<PurchaseOrderHistoryResponse> history(@PathVariable UUID locationId) {
        return purchaseOrderService.history(locationId);
    }

    @GetMapping("/{orderId}/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable UUID locationId,
            @PathVariable UUID orderId
    ) {
        byte[] csv = purchaseOrderService.exportCsv(locationId, orderId);
        String filename = attachmentFilename("csv", purchaseOrderService.generatedAt(locationId, orderId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename))
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/{orderId}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable UUID locationId,
            @PathVariable UUID orderId
    ) {
        byte[] pdf = purchaseOrderService.exportPdf(locationId, orderId);
        String filename = attachmentFilename("pdf", purchaseOrderService.generatedAt(locationId, orderId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/{orderId}/send")
    public ResponseEntity<Void> send(
            @PathVariable UUID locationId,
            @PathVariable UUID orderId,
            @Valid @RequestBody PurchaseOrderSendRequest request
    ) {
        purchaseOrderService.send(locationId, orderId, request);
        return ResponseEntity.noContent().build();
    }

    private String attachmentFilename(String extension, Instant generatedAt) {
        String date = generatedAt == null
                ? DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.now().atZone(ZoneOffset.UTC))
                : DateTimeFormatter.ISO_LOCAL_DATE.format(generatedAt.atZone(ZoneOffset.UTC));
        return "purchase-order-%s.%s".formatted(date, extension);
    }

    private String contentDisposition(String filename) {
        return "attachment; filename=\"%s\"".formatted(filename);
    }
}
