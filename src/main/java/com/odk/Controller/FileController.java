package com.odk.Controller;

import com.odk.Service.Interface.Service.UploadFileService;
import com.odk.Auth.Login;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600, allowedHeaders = "*", exposedHeaders = {"Authorization", "Content-Type"})
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final UploadFileService uploadFileService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private final String BUCKET_NAME = "odc-activite-assets";
    private final String REGION = "us-east-1";

    /**
     * POST /api/files/upload
     * Retourne l'URL S3 encapsulée dans un objet CResponse (JSON)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderName", required = false, defaultValue = "documents") String folderName) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                        "data", null,
                        "message", "Le fichier est vide",
                        "success", false
                    ));
        }

        try {
            String savedFileName = uploadFileService.uploadFile(file, folderName);

            // Génération de l'URL finale S3
            String fileUrl = String.format("https://%s.s3.%s.amazonaws.com/%s/%s",
                    BUCKET_NAME, REGION, folderName, savedFileName);

            log.info("Fichier uploadé avec succès sur S3 : {}", fileUrl);

            // IMPORTANT : On retourne l'objet CResponse pour que le Frontend reçoive du JSON
            return ResponseEntity.ok(Map.of(
                "data", fileUrl,
                "message", "Upload réussi",
                "success", true
            ));
        } catch (Exception e) {
            log.error("Erreur S3 : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "data", null,
                "message", "Erreur d'upload : " + e.getMessage(),
                "success", false
            ));
        }
    }

    /**
     * GET /api/files/url
     * Retourne l'URL d'un fichier existant au format JSON
     */
    @GetMapping("/url")
    public ResponseEntity<Map<String, Object>> getFileUrl(
            @RequestParam String fileName,
            @RequestParam(value = "folder", defaultValue = "documents") String folder) {

        String fileUrl = String.format("https://%s.s3.%s.amazonaws.com/%s/%s",
                BUCKET_NAME, REGION, folder, fileName);

        return ResponseEntity.ok(Map.of(
            "data", fileUrl,
            "message", "URL récupérée avec succès",
            "success", true
        ));
    }
    
}