package com.odk.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.mail.Multipart;
import lombok.Data;

@Data
public class CourrierDTO {

    private String numero;
    private String objet;
    private String expediteur;
    private Long directionId; //Entite direction ...
    private MultipartFile fichier; //Piece jointe ...
}
