package ca.pharmaforecast.backend.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadSanitizerTest {

    private final PayloadSanitizer sanitizer = new PayloadSanitizer();

    @Test
    void sanitizeRejectsForbiddenFieldsAtAnyNestingDepth() {
        assertThatThrownBy(() -> sanitizer.sanitize(Map.of(
                "payload",
                Map.of(
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content", "hello",
                                        "patient_id", "abc123"
                                )
                        )
                )
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("patient_id");
    }

    @Test
    void sanitizeRejectsForbiddenFieldsInsideNestedObjects() {
        assertThatThrownBy(() -> sanitizer.sanitize(new NestedPayload(
                "hello",
                new NestedInner("John Doe", "ABC")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("patientName");
    }

    record NestedPayload(String note, NestedInner inner) {
    }

    record NestedInner(String patientName, String other) {
    }
}
