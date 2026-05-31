package com.odk.Enum;

public enum Statut {
    En_Cours("En_Cours"),
    En_Attente("En_Attente"),
    Termine("Termine"),
    /** Création personnel : vérification salle / logistique par le responsable ODK */
    En_Validation_Responsable_ODK("En_Validation_Responsable_ODK"),
    En_Validation_Directeur_ODC("En_Validation_Directeur_ODC"),
    Rejetee("Rejetee")
    ;

    private final String value;

    Statut(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
