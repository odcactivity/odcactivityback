package com.odk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourrierDashboardDetailRowDTO {
    /** Identifiant de catégorie : emis, repondu, enAttente, recu, valide */
    private String categorie;
    /** Libellé affichable (Émis, Répondu, …) */
    private String libelle;
    private String structure;
    /** Date du courrier (réception), format ISO yyyy-MM-dd */
    private String date;
}
