package ca.pharmaforecast.backend.upload;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Convert;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Setter
@Entity
@Table(name = "csv_uploads")
@NoArgsConstructor(access = PROTECTED)
public class CsvUpload extends BaseEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Convert(converter = CsvUploadStatusConverter.class)
    @Column(name = "status", nullable = false)
    private CsvUploadStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "drug_count")
    private Integer drugCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_summary", columnDefinition = "jsonb")
    private String validationSummary;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public CsvUploadStatus getStatus() {
        return status;
    }

    public void setStatus(CsvUploadStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public Integer getDrugCount() {
        return drugCount;
    }

    public void setDrugCount(Integer drugCount) {
        this.drugCount = drugCount;
    }

    public String getValidationSummary() {
        return validationSummary;
    }

    public void setValidationSummary(String validationSummary) {
        this.validationSummary = validationSummary;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
