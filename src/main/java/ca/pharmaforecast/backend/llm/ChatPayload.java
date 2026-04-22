package ca.pharmaforecast.backend.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ChatPayload(
        @JsonProperty("system_prompt")
        String systemPrompt,
        List<Map<String, String>> messages
) {
}
