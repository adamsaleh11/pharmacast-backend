package ca.pharmaforecast.backend.upload;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/locations/{locationId}/uploads")
public class CsvUploadController {

    private final CurrentUserService currentUserService;
    private final CsvUploadService csvUploadService;

    public CsvUploadController(CurrentUserService currentUserService, CsvUploadService csvUploadService) {
        this.currentUserService = currentUserService;
        this.csvUploadService = csvUploadService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public CreateUploadResponse createUpload(
            @PathVariable UUID locationId,
            @RequestPart("file") MultipartFile file
    ) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        CsvUpload upload = csvUploadService.createUpload(locationId, file, currentUser);
        return new CreateUploadResponse(upload.getId(), upload.getStatus());
    }

    @GetMapping
    public List<UploadResponse> recentUploads(@PathVariable UUID locationId) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        return csvUploadService.recentUploads(locationId, currentUser)
                .stream()
                .map(UploadResponse::from)
                .toList();
    }

    @GetMapping("/{uploadId}")
    public UploadResponse getUpload(@PathVariable UUID locationId, @PathVariable UUID uploadId) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        return UploadResponse.from(csvUploadService.getUpload(locationId, uploadId, currentUser));
    }

    public record CreateUploadResponse(UUID uploadId, CsvUploadStatus status) {
    }

    public record UploadResponse(
            UUID uploadId,
            String filename,
            CsvUploadStatus status,
            Integer rowCount,
            Integer drugCount,
            String validationSummary,
            Instant uploadedAt
    ) {
        static UploadResponse from(CsvUpload upload) {
            return new UploadResponse(
                    upload.getId(),
                    upload.getFilename(),
                    upload.getStatus(),
                    upload.getRowCount(),
                    upload.getDrugCount(),
                    upload.getValidationSummary(),
                    upload.getUploadedAt()
            );
        }
    }
}
