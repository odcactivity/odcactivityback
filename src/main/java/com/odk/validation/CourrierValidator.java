package com.odk.validation;

import com.odk.dto.CourrierDTO;
import org.springframework.web.multipart.MultipartFile;

public class CourrierValidator {

    /**
     * Valide les données d'un courrier avant traitement
     */
    public static ValidationResult validateCourrierData(CourrierDTO dto, MultipartFile fichier) {
        StringBuilder errors = new StringBuilder();

        // Validation du numéro
        if (dto.getNumero() == null || dto.getNumero().trim().isEmpty()) {
            errors.append("Le numéro du courrier est obligatoire. ");
        }

        // Validation de l'objet
        if (dto.getObjet() == null || dto.getObjet().trim().isEmpty()) {
            errors.append("L'objet du courrier est obligatoire. ");
        }

        // Validation de l'expéditeur
        if (dto.getExpediteur() == null || dto.getExpediteur().trim().isEmpty()) {
            errors.append("L'expéditeur est obligatoire. ");
        }

        // Validation de la direction
        if (dto.getDirectionId() == null) {
            errors.append("La direction est obligatoire. ");
        }

        // Validation du fichier
        FileValidationUtil.ValidationResult fileValidation = FileValidationUtil.validateFile(fichier);
        if (!fileValidation.isValid()) {
            errors.append(fileValidation.getErrorMessage()).append(" ");
        }

        return errors.length() == 0 ? 
            ValidationResult.success() : 
            ValidationResult.failure(errors.toString().trim());
    }

    /**
     * Résultat de validation pour les courriers
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
