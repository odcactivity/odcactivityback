package com.odk.Enum;

public enum StatutValidation {
    Valider("Valider"),
    En_Attente("En_Attente"),
    Rejeter("Rejeter")
    ;

    private final String value;

    StatutValidation(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
