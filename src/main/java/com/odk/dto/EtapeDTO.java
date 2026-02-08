package com.odk.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.odk.Entity.Activite;
import com.odk.Entity.Critere;
import com.odk.Entity.Etape;
import com.odk.Entity.Liste;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.Statut;
import lombok.Data;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EtapeDTO {

    private Long id;
    private String nom;
    private Statut statut;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateDebut;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateFin;
    private Activite activite;
    private List<Critere> critere;
    private List<Liste> listes; 
    private Utilisateur created_by;
 
    public EtapeDTO(Etape e) {
        this.activite=e.getActivite();
        this.created_by=e.getCreated_by();
        this.critere=e.getCritere();
        this.listes=e.getListes();
        this.dateDebut=e.getDateDebut();
        this.dateFin=e.getDateFin();
        this.nom=e.getNom();
        this.statut=e.getStatut();
    }

}
