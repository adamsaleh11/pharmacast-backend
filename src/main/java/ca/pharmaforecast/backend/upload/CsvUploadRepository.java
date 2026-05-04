package ca.pharmaforecast.backend.upload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CsvUploadRepository extends JpaRepository<CsvUpload, UUID> {
    List<CsvUpload> findTop10ByLocationIdOrderByUploadedAtDesc(UUID locationId);

    Optional<CsvUpload> findByIdAndLocationId(UUID id, UUID locationId);

    Optional<CsvUpload> findTopByLocationIdAndStatusOrderByUploadedAtDesc(UUID locationId, CsvUploadStatus status);

    long countByLocationIdAndStatus(UUID locationId, CsvUploadStatus status);
}
