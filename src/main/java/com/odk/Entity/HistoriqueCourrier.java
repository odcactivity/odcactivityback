package com.odk.Entity;

import com.odk.Enum.StatutCourrier;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HistoriqueCourrier {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; //Identifiant de la table historique: Auto-Incrémente...

    /**--------------------------------------------------------------
     *                      Courrier concerné
     * ------------------------------------------------------------**/
    @ManyToOne
    @JoinColumn(name="courrier_id")
    private Courrier courrier; //Association avec la table Courrier: courrier concerné...

    /**--------------------------------------------------------------
     *             ENTITE COURANTE: celle qui fait l'action
     * ------------------------------------------------------------**/
    @ManyToOne
    @JoinColumn(name = "entite_id")
    private Entite entite; //Entité qui recoit ou traite le courrier ...

    /**--------------------------------------------------------------
     *           UTILISATEUR CONCERNE: celui qui fait l'action
     * ------------------------------------------------------------**/
    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur; //Directeur ou responsable qui fait l'action ...


    /**--------------------------------------------------------------
     *             STATUS DU COURRIER
     * ------------------------------------------------------------**/
    @Enumerated(EnumType.STRING)
    private StatutCourrier statut;   //Statut du courrier au moment de l'action ... 

    /**--------------------------------------------------------------
     *             DATE DE L'ACTION : Avant et Apres
     *            Trace le jour+heure+minute+seconde
     * ------------------------------------------------------------**/
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateAction;

    /**--------------------------------------------------------------
     *             COMMENTAIRE LIBRE
     * ------------------------------------------------------------**/
    private String commentaire;

    /**--------------------------------------------------------------
     *        ANCIEN ENTITE: avant le changement (Imputer)...
     Garder la tracabilité lors du passage du courrier d'une entite à l'autre...
     * ------------------------------------------------------------**/
    @ManyToOne
    @JoinColumn(name = "ancienne_entite_id")
    private Entite ancienneEntite; //Garder la tracabilité lors de l'imputation

    /** --------------------------------------------------------------
     *      NOUVELLE ENTITE: Apres le changement (partage/Imputation...
     Garder la tracabilité lors du passage du courrier d'une entite à l'autre...
     * ------------------------------------------------------------ **/
    @ManyToOne
    @JoinColumn(name = "nouvelle_entite_id")
    private Entite nouvelleEntite;
}
