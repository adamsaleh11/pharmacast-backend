package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HealthCanadaForm(
        @JsonProperty("pharmaceutical_form_name") String pharmaceuticalFormName
) {
}
