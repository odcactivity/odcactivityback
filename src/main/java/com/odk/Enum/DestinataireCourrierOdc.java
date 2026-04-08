package com.odk.Enum;

import com.odk.exception.CourrierValidationException;

/**
 * Destinataire logique lorsqu'un courrier est créé depuis l'ODC (brouillon admin).
 * {@link #EXTERNE} : hors Fondation / RSE / DCI → passage par la DCIRE après validation directeur ODC.
 */
public enum DestinataireCourrierOdc {
    FONDATION,
    RSE,
    DCI,
    EXTERNE;

    public static DestinataireCourrierOdc fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return EXTERNE;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CourrierValidationException(
                    "destinataireOdc invalide : utilisez FONDATION, RSE, DCI ou EXTERNE (ou laissez vide pour externe).");
        }
    }
}
