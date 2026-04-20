package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HealthCanadaActiveIngredient(
        @JsonProperty("ingredient_name") String ingredientName,
        String strength,
        @JsonProperty("strength_unit") String strengthUnit,
        @JsonProperty("dosage_value") String dosageValue,
        @JsonProperty("dosage_unit") String dosageUnit
) {
}
