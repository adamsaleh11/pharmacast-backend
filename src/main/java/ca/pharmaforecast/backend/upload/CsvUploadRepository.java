package ca.pharmaforecast.backend.upload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CsvUploadRepository extends JpaRepository<CsvUpload, UUID> {
}
