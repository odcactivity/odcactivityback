package com.odk.Entity;

import com.odk.Enum.StatutSupport;
import com.odk.Enum.TypeSupport;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportActivite {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nom;
    private String typeMime;
    @Enumerated(EnumType.STRING)
    private TypeSupport type;
    private String url;
    @Column(name = "taille",nullable = true)
    private Long taille; // <-- la taille des fichiers uploads ...

    @Column(length = 100)
    private String description;

    @Temporal(TemporalType.TIMESTAMP)
    private Date dateAjout;

    @Enumerated(EnumType.STRING)
    private StatutSupport statut=StatutSupport.En_ATTENTE;

    @Column(length = 100)
    private String commentaire;

    @ManyToOne
    @JoinColumn(name = "activte_id")
    private Activite activite;

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateurAutorise;

    @OneToMany(mappedBy = "support", cascade = CascadeType.ALL, fetch = FetchType.LAZY,orphanRemoval = true)
    private List<HistoriqueSupportActivite> historiques;

    public void mettreAJourStatut(StatutSupport nouveauStatut, String commentaire) {
            this.statut = nouveauStatut;
            this.commentaire=commentaire;
    }

}
