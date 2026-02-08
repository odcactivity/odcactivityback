package com.odk.Entity;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.odk.Enum.StatutValidation;
import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActiviteValidation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "TEXT")
    private String commentaire;
    @Temporal(TemporalType.DATE)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date date = new Date();
    
    private StatutValidation statut;
    @Lob
    @Column(name = "fichier_chiffre", columnDefinition = "LONGBLOB")
    private byte[] fichierChiffre;
    private String fichierjoint; // Pour garder le nom original
    private Long envoyeurId;

    @ManyToOne
    @JoinColumn(name = "activite_id")
    @JsonIgnore
    private Activite activite;
    
    @ManyToOne
    @JoinColumn(name = "utilisateur_id",nullable = true)
    @JsonIgnore
    private Utilisateur superviseur;
   
   @Column(name="isRead")
   private Boolean isRead;

    // Ajout d'un constructeur prenant un ID pour la désérialisation
    public ActiviteValidation(Long id) {
        this.id = id;
    }

    public void mettreAJourStatut() {
        Date maintenant = new Date();
        
    }


}
