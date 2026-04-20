package ca.pharmaforecast.backend.realtime;

import ca.pharmaforecast.backend.upload.CsvUploadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SupabaseRealtimeClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupabaseRealtimeClient.class);

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String serviceRoleKey;

    public SupabaseRealtimeClient(
            RestClient.Builder restClientBuilder,
            @Value("${pharmaforecast.supabase.url:}") String supabaseUrl,
            @Value("${pharmaforecast.supabase.service-role-key:}") String serviceRoleKey
    ) {
        this.restClient = restClientBuilder.build();
        this.supabaseUrl = supabaseUrl;
        this.serviceRoleKey = serviceRoleKey;
    }

    public void broadcastUploadComplete(UUID locationId, UUID uploadId, CsvUploadStatus status, String validationSummary) {
        if (!StringUtils.hasText(supabaseUrl) || !StringUtils.hasText(serviceRoleKey)) {
            LOGGER.debug("Supabase Realtime is not configured; skipping upload completion broadcast");
            return;
        }

        try {
            restClient.post()
                    .uri(supabaseUrl + "/realtime/v1/api/broadcast")
                    .header("apikey", serviceRoleKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("messages", List.of(Map.of(
                            "topic", "location:" + locationId,
                            "event", "upload_complete",
                            "payload", Map.of(
                                    "type", "upload_complete",
                                    "uploadId", uploadId.toString(),
                                    "status", status.name(),
                                    "summary", validationSummary == null ? "" : validationSummary
                            )
                    ))))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            LOGGER.warn("Supabase Realtime upload completion broadcast failed for upload {}", uploadId, ex);
        }
    }
}
