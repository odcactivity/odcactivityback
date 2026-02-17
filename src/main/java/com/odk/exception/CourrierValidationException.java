package com.odk.exception;

/**
 * Exception personnalisée pour les erreurs de validation de courrier
 */
public class CourrierValidationException extends RuntimeException {
    
    public CourrierValidationException(String message) {
        super(message);
    }
    
    public CourrierValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
