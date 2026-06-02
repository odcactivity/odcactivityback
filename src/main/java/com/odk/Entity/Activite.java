package com.odk.Entity;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.odk.Enum.DecisionDirecteurOdc;
import com.odk.Enum.Statut;
import com.odk.jackson.JpaRefIdDeserializers;
import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Activite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String nom;
    private String titre;

    @Temporal(TemporalType.DATE)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateDebut = new Date();

    @Temporal(TemporalType.DATE)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateFin;
    private Statut statut;

    /** Renseigné lorsque le directeur ODC valide ou refuse (historique). */
    @Enumerated(EnumType.STRING)
    private DecisionDirecteurOdc directeurOdcDecision;

    @Temporal(TemporalType.TIMESTAMP)
    private Date directeurOdcTraiteLe;

    /** Date de transmission au directeur ODC par le responsable ODK (conservée pour l'historique). */
    @Temporal(TemporalType.TIMESTAMP)
    private Date transmiseDirecteurOdcLe;

    @Column(columnDefinition = "TEXT")
    private String noteResponsableOdk;

    @Column(columnDefinition = "TEXT")
    private String suggestionDirecteurOdc;

    private String lieu;
    @Column(columnDefinition = "TEXT")
    private String description;
    private Integer objectifParticipation;
    @Column(name = "candidature_recu")
    private Integer candidatureRecu;

    @Column(name = "candidature_femme")
    private Integer candidatureFemme;

    @Column(name = "cible")
    private Integer cible;
    @ManyToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "entite_id")
    @JsonDeserialize(using = JpaRefIdDeserializers.EntiteRef.class)
    private Entite entite;

    @ManyToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "typeActivite_id")
    @JsonDeserialize(using = JpaRefIdDeserializers.TypeActiviteRef.class)
    private TypeActivite typeActivite;

    @OneToMany(mappedBy = "activite", cascade = CascadeType.MERGE)
    @JsonManagedReference
    private List<Etape> etapes = new ArrayList<>();
    @ManyToOne
    @JoinColumn(name = "created_by")
    private Utilisateur createdBy;
    @ManyToOne
    @JoinColumn(name = "salleId")
    @JsonDeserialize(using = JpaRefIdDeserializers.SalleRef.class)
    private Salle salleId;


@OneToMany(mappedBy = "activite", cascade = CascadeType.ALL)
private List<ActiviteValidation> validations = new ArrayList<>();
    // Ajout d'un constructeur prenant un ID pour la désérialisation
    public Activite(Long id) {
        this.id = id;
    }

    public void mettreAJourStatut() {
        if (statut == Statut.En_Validation_Responsable_ODK
                || statut == Statut.En_Validation_Directeur_ODC
                || statut == Statut.Rejetee) {
            return;
        }
        Date maintenant = new Date();
        if (dateDebut != null && dateFin != null) {
            if (maintenant.before(dateDebut)) {
                this.statut = Statut.En_Attente;
            } else if (maintenant.after(dateFin)) {
                this.statut = Statut.Termine;
            } else {
                this.statut = Statut.En_Cours;
            }
            System.out.println("Statut mis à jour : " + this.statut);
        } else {
            throw new RuntimeException("Les dates de début et de fin doivent être définies pour gérer le statut.");
        }
    }

}
