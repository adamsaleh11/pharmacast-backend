package ca.pharmaforecast.backend.upload;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record BacktestUploadRequest(
        @JsonProperty("organization_id")
        UUID organizationId,
        @JsonProperty("location_id")
        UUID locationId,
        @JsonProperty("csv_upload_id")
        UUID csvUploadId,
        @JsonProperty("model_version")
        String modelVersion,
        List<BacktestDemandRow> rows,
        @JsonProperty("debug_artifacts")
        boolean debugArtifacts
) {
    public static BacktestUploadRequest xgboostResidualV1(UUID organizationId, UUID locationId, UUID csvUploadId, List<BacktestDemandRow> rows) {
        return new BacktestUploadRequest(organizationId, locationId, csvUploadId, "xgboost_residual_v1", rows, false);
    }

    public static BacktestUploadRequest prophetV1(UUID organizationId, UUID locationId, UUID csvUploadId, List<BacktestDemandRow> rows) {
        return xgboostResidualV1(organizationId, locationId, csvUploadId, rows);
    }
}
