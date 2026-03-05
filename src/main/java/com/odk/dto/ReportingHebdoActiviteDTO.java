package com.odk.dto;

import com.odk.Entity.TypeActivite;
import com.odk.Enum.Statut;

import java.util.Date;

public class ReportingHebdoActiviteDTO {

    private Long id;
    private String nom;
    private String titre;
    private Date dateDebut;
    private Date dateFin;
    private Statut statut;
    private String lieu;
    private String description;
    private Integer objectifParticipation;

    // ✅ On envoie seulement le nom de l'entité
    private String entiteNom;

    private String createdBy;
    private TypeActivite typeActivite;
    private Integer candidatureRecu;
    private Integer candidatureFemme;
    private Integer cible;

    public ReportingHebdoActiviteDTO(
            Long id,
            String nom,
            String titre,
            Date dateDebut,
            Date dateFin,
            Statut statut,
            String lieu,
            String description,
            Integer objectifParticipation,
            String entiteNom,
            String createdBy,
            TypeActivite typeActivite,
            Integer candidatureRecu,
            Integer candidatureFemme,
            Integer cible
    ) {
        this.id = id;
        this.nom = nom;
        this.titre = titre;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.statut = statut;
        this.lieu = lieu;
        this.description = description;
        this.objectifParticipation = objectifParticipation;
        this.entiteNom = entiteNom;
        this.createdBy = createdBy;
        this.typeActivite = typeActivite;
        this.candidatureRecu = candidatureRecu;
        this.candidatureFemme = candidatureFemme;
        this.cible = cible;
    }

    public Long getId() { return id; }
    public String getNom() { return nom; }
    public String getTitre() { return titre; }
    public Date getDateDebut() { return dateDebut; }
    public Date getDateFin() { return dateFin; }
    public Statut getStatut() { return statut; }
    public String getLieu() { return lieu; }
    public String getDescription() { return description; }
    public Integer getObjectifParticipation() { return objectifParticipation; }
    public String getEntiteNom() { return entiteNom; }
    public String getCreatedBy() { return createdBy; }
    public TypeActivite getTypeActivite() { return typeActivite; }
    public Integer getCandidatureRecu() { return candidatureRecu; }
    public Integer getCandidatureFemme() { return candidatureFemme; }
    public Integer getCible() { return cible; }
}