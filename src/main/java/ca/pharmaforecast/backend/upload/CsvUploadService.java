package ca.pharmaforecast.backend.upload;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CsvUploadService {

    private final CsvUploadRepository csvUploadRepository;
    private final LocationRepository locationRepository;
    private final Clock clock;
    private final CsvProcessingJob csvProcessingJob;

    public CsvUploadService(
            CsvUploadRepository csvUploadRepository,
            LocationRepository locationRepository,
            Clock clock,
            CsvProcessingJob csvProcessingJob
    ) {
        this.csvUploadRepository = csvUploadRepository;
        this.locationRepository = locationRepository;
        this.clock = clock;
        this.csvProcessingJob = csvProcessingJob;
    }

    @Transactional
    public CsvUpload createUpload(UUID locationId, MultipartFile file, AuthenticatedUserPrincipal currentUser) {
        requireOwnedLocation(locationId, currentUser);

        CsvUpload upload = new CsvUpload();
        upload.setLocationId(locationId);
        upload.setFilename(safeFilename(file));
        upload.setStatus(CsvUploadStatus.PENDING);
        upload.setUploadedAt(Instant.now(clock));
        CsvUpload savedUpload = csvUploadRepository.save(upload);
        submitProcessingJob(savedUpload.getId(), locationId, bytes(file));
        return savedUpload;
    }

    @Transactional(readOnly = true)
    public List<CsvUpload> recentUploads(UUID locationId, AuthenticatedUserPrincipal currentUser) {
        requireOwnedLocation(locationId, currentUser);
        return csvUploadRepository.findTop10ByLocationIdOrderByUploadedAtDesc(locationId);
    }

    @Transactional(readOnly = true)
    public CsvUpload getUpload(UUID locationId, UUID uploadId, AuthenticatedUserPrincipal currentUser) {
        requireOwnedLocation(locationId, currentUser);
        return csvUploadRepository.findByIdAndLocationId(uploadId, locationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found"));
    }

    private void requireOwnedLocation(UUID locationId, AuthenticatedUserPrincipal currentUser) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found"));
        if (!location.getOrganizationId().equals(currentUser.organizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Location does not belong to organization");
        }
    }

    private String safeFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload.csv";
        }
        return originalFilename;
    }

    private byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file could not be read");
        }
    }

    private void submitProcessingJob(UUID uploadId, UUID locationId, byte[] csvBytes) {
        Runnable job = () -> csvProcessingJob.process(uploadId, locationId, csvBytes);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    job.run();
                }
            });
            return;
        }
        job.run();
    }
}
