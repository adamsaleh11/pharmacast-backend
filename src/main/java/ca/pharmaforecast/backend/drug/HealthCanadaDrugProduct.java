package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HealthCanadaDrugProduct(
        @JsonProperty("drug_code") String drugCode,
        @JsonProperty("drug_identification_number") String drugIdentificationNumber,
        @JsonProperty("brand_name") String brandName,
        @JsonProperty("company_name") String companyName,
        @JsonProperty("class_name") String className
) {
}
