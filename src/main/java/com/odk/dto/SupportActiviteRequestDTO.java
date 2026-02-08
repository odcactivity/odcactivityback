package com.odk.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class SupportActiviteRequestDTO {
    private MultipartFile file;
    private Long activiteId;
    private Long utilisateurId; // utilisateur affecté
    private String type;
    private String description;
}