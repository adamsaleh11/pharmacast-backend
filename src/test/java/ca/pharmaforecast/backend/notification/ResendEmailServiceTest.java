package ca.pharmaforecast.backend.notification;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailServiceTest {

    @Test
    void sendEmailPostsHtmlBodyToResend() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer test-api-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailService service = new ResendEmailService(builder.build(), "alerts@pharmacy.example", 0);

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("alerts@pharmacy.example"))
                .andExpect(jsonPath("$.to[0]").value("owner@example.com"))
                .andExpect(jsonPath("$.subject").value("Critical stock alert"))
                .andExpect(jsonPath("$.html").value("<p>Order today</p>"))
                .andRespond(withSuccess());

        assertThat(service.sendEmail("owner@example.com", "Critical stock alert", "<p>Order today</p>")).isTrue();
        server.verify();
    }

    @Test
    void sendEmailWithAttachmentPostsBase64AttachmentToResend() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer test-api-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailService service = new ResendEmailService(builder.build(), "alerts@pharmacy.example", 0);
        byte[] attachment = "csv-data".getBytes();

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(jsonPath("$.attachments[0].filename").value("digest.csv"))
                .andExpect(jsonPath("$.attachments[0].content").value(Base64.getEncoder().encodeToString(attachment)))
                .andExpect(jsonPath("$.attachments[0].content_type").value("text/csv"))
                .andRespond(withSuccess());

        assertThat(service.sendEmailWithAttachment(
                "owner@example.com",
                "Digest",
                "<p>Attached</p>",
                attachment,
                "digest.csv",
                "text/csv"
        )).isTrue();
        server.verify();
    }

    @Test
    void sendEmailReturnsFalseAfterRetriesFail() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer test-api-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailService service = new ResendEmailService(builder.build(), "alerts@pharmacy.example", 0);

        server.expect(times(3), requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());

        assertThat(service.sendEmail("owner@example.com", "Critical stock alert", "<p>Order today</p>")).isFalse();
        server.verify();
    }
}
