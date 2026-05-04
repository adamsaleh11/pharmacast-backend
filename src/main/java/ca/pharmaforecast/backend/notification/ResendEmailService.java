package ca.pharmaforecast.backend.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;

@Service
public class ResendEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendEmailService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;
    private final String fromEmail;
    private final long initialBackoffMillis;

    @Autowired
    public ResendEmailService(
            @Value("${pharmaforecast.resend.api-key:}") String apiKey,
            @Value("${pharmaforecast.resend.from-email:}") String fromEmail
    ) {
        this(
                RestClient.builder()
                        .baseUrl("https://api.resend.com")
                        .defaultHeader("Authorization", "Bearer " + (apiKey == null ? "" : apiKey.trim()))
                        .build(),
                fromEmail,
                250
        );
    }

    ResendEmailService(RestClient restClient, String fromEmail, long initialBackoffMillis) {
        this.restClient = restClient;
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.initialBackoffMillis = Math.max(0, initialBackoffMillis);
    }

    public boolean sendEmail(String to, String subject, String htmlBody) {
        return send(new ResendEmailRequest(
                fromEmail,
                List.of(to),
                subject,
                htmlBody,
                null
        ));
    }

    public boolean sendEmailWithAttachment(
            String to,
            String subject,
            String htmlBody,
            byte[] attachment,
            String attachmentFilename,
            String mimeType
    ) {
        return send(new ResendEmailRequest(
                fromEmail,
                List.of(to),
                subject,
                htmlBody,
                List.of(new Attachment(
                        attachmentFilename,
                        Base64.getEncoder().encodeToString(attachment == null ? new byte[0] : attachment),
                        mimeType
                ))
        ));
    }

    private boolean send(ResendEmailRequest request) {
        if (!StringUtils.hasText(fromEmail)) {
            LOGGER.error("Resend sender email is not configured");
            return false;
        }
        if (request.to() == null || request.to().isEmpty() || !StringUtils.hasText(request.to().getFirst())) {
            LOGGER.error("Resend recipient email is not configured");
            return false;
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                restClient.post()
                        .uri("/emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (RuntimeException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    LOGGER.error("Resend email failed after {} attempts to recipient {}", attempt, request.to().getFirst(), ex);
                    return false;
                }
                sleepBeforeRetry(attempt);
            }
        }
        return false;
    }

    private void sleepBeforeRetry(int attempt) {
        long sleepMillis = initialBackoffMillis * (1L << Math.max(0, attempt - 1));
        if (sleepMillis == 0) {
            return;
        }
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String html,
            List<Attachment> attachments
    ) {
    }

    private record Attachment(
            String filename,
            String content,
            @JsonProperty("content_type") String contentType
    ) {
    }
}
