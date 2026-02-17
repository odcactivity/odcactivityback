package com.odk.validation;

import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

public class FileValidationUtil {

    // Taille maximale : 10 Mo
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB en bytes

    // Extensions autorisées pour les documents bureautiques
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
        "pdf", "doc", "docx", "xls", "xlsx", "png", "jpg", "jpeg"
    );

    // Types MIME autorisés
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
        "application/pdf",
        "application/msword", 
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "image/png",
        "image/jpeg",
        "image/jpg"
    );

    /**
     * Valide un fichier uploadé selon les contraintes définies
     */
    public static ValidationResult validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ValidationResult.success();
        }

        // 1. Vérification de la taille
        if (file.getSize() > MAX_FILE_SIZE) {
            return ValidationResult.failure(
                "La taille du fichier dépasse la limite autorisée de 10 Mo. Taille actuelle : " + 
                (file.getSize() / (1024 * 1024)) + " Mo"
            );
        }

        // 2. Vérification du type MIME réel
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            return ValidationResult.failure(
                "Type de fichier non autorisé : " + mimeType + 
                ". Types autorisés : " + String.join(", ", ALLOWED_MIME_TYPES)
            );
        }

        // 3. Vérification de l'extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return ValidationResult.failure("Le nom du fichier ne peut pas être vide");
        }

        String extension = getFileExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            return ValidationResult.failure(
                "Extension de fichier non autorisée : " + extension + 
                ". Extensions autorisées : " + String.join(", ", ALLOWED_EXTENSIONS)
            );
        }

        // 4. Vérification du nom du fichier (sécurité)
        String normalizedFilename = normalizeFilename(originalFilename);
        if (!originalFilename.equals(normalizedFilename)) {
            return ValidationResult.failure(
                "Le nom du fichier contient des caractères non valides. Nom suggéré : " + normalizedFilename
            );
        }

        return ValidationResult.success();
    }

    /**
     * Extrait l'extension d'un nom de fichier
     */
    private static String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Normalise le nom du fichier en supprimant les caractères dangereux
     */
    public static String normalizeFilename(String filename) {
        if (filename == null) return null;

        // Supprimer les caractères spéciaux dangereux
        String normalized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Remplacer les espaces par des underscores
        normalized = normalized.replaceAll("\\s+", "_");
        
        // Supprimer les points consécutifs sauf le dernier
        normalized = normalized.replaceAll("\\.+(?=\\.)", ".");
        
        // Limiter la longueur du nom
        if (normalized.length() > 100) {
            String extension = getFileExtension(normalized);
            String nameWithoutExt = normalized.substring(0, normalized.lastIndexOf('.'));
            normalized = nameWithoutExt.substring(0, 50) + "." + extension;
        }

        return normalized;
    }

    /**
     * Résultat de validation
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
