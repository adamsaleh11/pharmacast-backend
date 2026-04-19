package ca.pharmaforecast.backend.upload;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
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
}
