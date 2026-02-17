package com.odk.Controller;

import com.odk.dto.CourrierDTO;
import com.odk.exception.CourrierValidationException;
import com.odk.validation.FileValidationUtil;
import com.odk.validation.CourrierValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Contrôleur de test pour valider les fonctionnalités de validation de fichiers
 */
@RestController
@RequestMapping("/api/validation-test")
public class FileValidationTestController {

    /**
     * Endpoint de test pour valider un fichier
     */
    @PostMapping("/validate-file")
    public ResponseEntity<Map<String, Object>> validateFile(@RequestParam MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            FileValidationUtil.ValidationResult result = FileValidationUtil.validateFile(file);
            
            response.put("success", result.isValid());
            response.put("message", result.isValid() ? "Fichier valide" : result.getErrorMessage());
            
            if (file != null) {
                response.put("fileInfo", Map.of(
                    "originalName", file.getOriginalFilename(),
                    "size", file.getSize(),
                    "contentType", file.getContentType()
                ));
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Erreur lors de la validation : " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Endpoint de test pour valider les données complètes d'un courrier
     */
    @PostMapping("/validate-courrier")
    public ResponseEntity<Map<String, Object>> validateCourrier(
            @RequestParam String numero,
            @RequestParam String objet,
            @RequestParam String expediteur,
            @RequestParam Long directionId,
            @RequestParam(required = false) MultipartFile fichier) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            CourrierDTO dto = new CourrierDTO();
            dto.setNumero(numero);
            dto.setObjet(objet);
            dto.setExpediteur(expediteur);
            dto.setDirectionId(directionId);
            dto.setFichier(fichier);
            
            CourrierValidator.ValidationResult result = CourrierValidator.validateCourrierData(dto, fichier);
            
            response.put("success", result.isValid());
            response.put("message", result.isValid() ? "Courrier valide" : result.getErrorMessage());
            
            return ResponseEntity.ok(response);
            
        } catch (CourrierValidationException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Erreur inattendue : " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Endpoint pour afficher les règles de validation
     */
    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> getValidationRules() {
        Map<String, Object> rules = new HashMap<>();
        
        rules.put("maxFileSize", "10 Mo");
        rules.put("allowedExtensions", java.util.List.of(
            "pdf", "doc", "docx", "xls", "xlsx", "png", "jpg", "jpeg"
        ));
        rules.put("allowedMimeTypes", java.util.List.of(
            "application/pdf",
            "application/msword", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/png",
            "image/jpeg",
            "image/jpg"
        ));
        rules.put("security", "Validation stricte du type MIME, de la taille et du nom de fichier");
        rules.put("storage", "Chemin contrôlé et nom normalisé pour prévenir les injections de chemin");
        
        return ResponseEntity.ok(rules);
    }
}
