
package com.odk.dto;

import lombok.Data;

import java.util.Date;

@Data
public class HistoriqueCourrierDTO {


    private String statut;
    private String commentaire;
    private Date dateAction;
    private String utilisateur;
    private String entite;
    /** Expéditeur métier du courrier (ex. KEÏTA DCIRE). */
    private String expediteurCourrier;
    private String ancienneEntiteNom;
    private String nouvelleEntiteNom;
    private String entiteDetentionNom;
    private String structureOrigineNom;
}
