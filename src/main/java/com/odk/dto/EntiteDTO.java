package com.odk.dto;

import com.odk.Entity.Utilisateur;
import com.odk.Enum.TypeEntite;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntiteDTO {
    private Long id;
    private String nom;
    private String logo;
    private String description;
    private Long parentId;    // ID du parent
    private List<Long> sousEntiteIds; // liste des IDs des sous-entités
    private Long responsable;              // uniquement l'ID du responsable
    private List<Long> typeActivitesIds;      // uniquement les IDs des types d'activités
    private TypeEntite type; //Recupere DIRECTION||SERVICE

    
}
