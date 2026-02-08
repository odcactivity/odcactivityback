package com.odk.dto;

import com.odk.Enum.StatutSupport;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupportActiviteResponseDTO {
  
    private Long id;
    private String nom;
    private String type;
    private String url;
    private StatutSupport statut;
    private String commentaire;
    private String description; // ajoutée
    private Date dateAjout;
    private Long activiteId;
    private String activiteNom;
    private String emailutilisateurAutorise;
    private List<HistoriqueSupportActiviteDTO> historiques;
}
