package ca.pharmaforecast.backend.forecast;

public enum SafetyMultiplier {
    conservative(1.5),
    balanced(1.0),
    aggressive(0.75);

    private final double value;

    SafetyMultiplier(double value) {
        this.value = value;
    }

    public double value() {
        return value;
    }
}
