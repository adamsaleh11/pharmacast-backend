package ca.pharmaforecast.backend.forecast;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum ReorderStatus {
    green,
    amber,
    red;

    @JsonCreator
    public static ReorderStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        return ReorderStatus.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }
}
