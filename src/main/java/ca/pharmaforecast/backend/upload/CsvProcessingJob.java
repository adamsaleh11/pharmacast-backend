package ca.pharmaforecast.backend.upload;

import java.util.UUID;

public interface CsvProcessingJob {
    void process(UUID uploadId, UUID locationId, byte[] csvBytes);
}
