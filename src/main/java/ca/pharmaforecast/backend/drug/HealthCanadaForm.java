package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HealthCanadaForm(
        @JsonProperty("pharmaceutical_form_name") String pharmaceuticalFormName
) {
}
