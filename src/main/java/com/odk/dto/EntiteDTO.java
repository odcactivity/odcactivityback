package com.odk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.TypeEntite;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntiteDTO {
    private Long id;
    private String nom;
    private String logo;
    private String description;
    private Long parentId;    // ID du parent (null pour les directions)
    private List<Long> sousEntiteIds; // liste des IDs des sous-entités
    private Long responsable;              // uniquement l'ID du responsable
    @JsonProperty("typeActiviteIds")
    private List<Long> typeActivitesIds;      // uniquement les IDs des types d'activités
    private TypeEntite type; //Recupere DIRECTION||SERVICE

    
    // Constructeurs partiels pour la création
    public EntiteDTO(String nom, String logo, String description, Long responsable, List<Long> typeActivitesIds, TypeEntite type) {
        this.nom = nom;
        this.logo = logo;
        this.description = description;
        this.responsable = responsable;
        this.typeActivitesIds = typeActivitesIds;
        this.type = type;
    }
    
    // Constructeur avec parent pour les services
    public EntiteDTO(String nom, String logo, String description, Long parentId, Long responsable, List<Long> typeActivitesIds, TypeEntite type) {
        this.nom = nom;
        this.logo = logo;
        this.description = description;
        this.parentId = parentId;
        this.responsable = responsable;
        this.typeActivitesIds = typeActivitesIds;
        this.type = type;
    }
}
