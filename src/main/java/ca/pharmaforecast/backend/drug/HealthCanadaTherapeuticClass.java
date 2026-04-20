package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HealthCanadaTherapeuticClass(
        @JsonProperty("tc_atc_number") String atcNumber,
        @JsonProperty("tc_atc") String atc
) {
}
