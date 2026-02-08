
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
}
