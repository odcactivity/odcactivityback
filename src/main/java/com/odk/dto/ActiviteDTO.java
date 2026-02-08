package com.odk.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.odk.Entity.Entite;
import com.odk.Entity.Etape;
import com.odk.Entity.Salle;
import com.odk.Entity.TypeActivite;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.Statut;
import java.util.ArrayList;
import lombok.Data;

import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiviteDTO {
    private Long id;
    private String nom;
    private String titre;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateDebut;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateFin;
    private Statut statut;
    private String lieu;
    private String description;
    private int objectifParticipation;
    private List<EtapeDTOSansActivite> etapes = new ArrayList<>() ;
    private Entite entite;
    private Salle salleId;
    private Utilisateur createdBy;
    private TypeActivite typeActivite;
    private Integer candidatureRecu;
    private Integer candidatureFemme;
    private Integer cible;

    //    private List<ParticipantDTO> listeDebut; // Liste de participants pour l'étape de début
//    private List<ParticipantDTO> listeResultat; // Liste de participants pour l'étape de résultat
   private List<ActiviteValidationDTO> activitevalidation;
    // Constructeur
    public ActiviteDTO(Long id, String nom, String titre, Date dateDebut, Date dateFin, Statut statut, String lieu, String description, int objectifParticipation,  Entite entite, Salle salleId, Utilisateur createdBy, TypeActivite typeActivite) {
        this.id = id;
        this.nom = nom;
        this.titre = titre;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.statut = statut;
        this.lieu = lieu;
        this.description = description;
        this.objectifParticipation = objectifParticipation;
        this.entite = entite;        
        this.salleId = salleId;
        this.createdBy=createdBy;
        this.typeActivite = typeActivite;
//       
    }
     public ActiviteDTO(Long id, List<ActiviteValidationDTO> activitevalidation, String nom, Date dateDebut, Date dateFin, Statut statut, String lieu, String description, int objectifParticipation, Entite entite, Salle salleId,Utilisateur createdBy, TypeActivite typeActivite) {
        this.id = id;
        this.activitevalidation = activitevalidation;
        this.nom = nom;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.statut = statut;
        this.lieu = lieu;
        this.description = description;
        this.objectifParticipation = objectifParticipation;
        this.entite = entite;
        this.salleId = salleId;
        this.createdBy=createdBy;
        this.typeActivite = typeActivite;
       
       
    }
}
