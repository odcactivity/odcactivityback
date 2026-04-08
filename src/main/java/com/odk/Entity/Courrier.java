package com.odk.Entity;

import java.util.Date;

import com.odk.Enum.DestinataireCourrierOdc;
import com.odk.Enum.StatutCourrier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name="courrier")
public class Courrier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  //Identifiant de la table courrier:Auto-increment...

    private String numero;  //Numéro d'enregistrement du courrier ...
    private String objet;    //L'objet du courrier...
    private String expediteur;  //celui qui envoie le courier...
    private String fichier;  //Chemin ou nom du fichier sur le fichier ...
    

    @Temporal(TemporalType.DATE)
    private Date dateReception; //Date arrivée du courrier...

    @Temporal(TemporalType.DATE)
    private Date dateLimite;   //Date limite de traitement (dateTraitement + 7 jours)

    @Temporal(TemporalType.DATE)
    private Date dateRelance;  //Message,notification de rappelle ...

    @Temporal(TemporalType.DATE)
    private Date dateArchivage;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", length = 64)
    private StatutCourrier statut; //Statut courant du dossier ...

    @ManyToOne
    @JoinColumn(name = "entite_id")
    private Entite entite;  //Entité responsable (Direction au départ...)

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateurAffecte;

    /** --------------------------------------------------------------------
     * Gestion des affectations : permet la trace de la provenance du courrier
     * Direction Initiale(celle qui recoit toujours le courrier o début)
     * -----------------------------------------------------------------------**/

    @ManyToOne
    @JoinColumn(name="direction_initial_id")
    private Entite directionInitial;

    /**
     * Structure métier à l'origine du dossier (ex. une direction ODC, la Fondation, le RSE…).
     * Complète {@link #directionInitial} pour le périmètre de visibilité ODC / DCIRE.
     */
    @ManyToOne
    @JoinColumn(name = "structure_origine_id")
    private Entite structureOrigine;

    /** Dernières remarques / suggestions du directeur ODC (révision contenu) */
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String suggestionDirecteur;

    /**
     * Choix du destinataire lors de la création ODC (brouillon). {@code EXTERNE} ou absent = hub DCIRE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "destinataire_odc", length = 32)
    private DestinataireCourrierOdc destinataireOdc;

    /** Précision optionnelle pour une cible externe (structure hors division), si besoin métier. */
    @Column(name = "externe_precision", length = 512)
    private String externePrecision;


    /**---------------------------------------------------
     * Champs pour le scheduler : les rappels et alertes ...
     * -----------------------------------------------------**/
    // Rappel envoyée oui ou non
     private boolean rappelEnvoye = false;

    // Alerte envoyée oui ou non
    private boolean alerteEnvoyee = false;



}
