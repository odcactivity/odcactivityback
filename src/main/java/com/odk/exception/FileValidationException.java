package com.odk.exception;

/**
 * Exception personnalisée pour les erreurs de validation de fichier
 */
public class FileValidationException extends RuntimeException {
    
    public FileValidationException(String message) {
        super(message);
    }
    
    public FileValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
