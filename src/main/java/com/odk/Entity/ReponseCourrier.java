package com.odk.Entity;

import com.odk.Enum.StatutCourrier;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "reponse_courrier")
public class ReponseCourrier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "courrier_id")
    private Courrier courrier;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String objet;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "fichier_joint")
    private String fichierJoint;

    @Column(name = "fichiers_multiples")
    private String fichiersMultiples;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCourrier statut;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_reponse")
    private Date dateReponse;

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @PrePersist
    protected void onCreate() {
        this.dateReponse = new Date();
        this.statut = StatutCourrier.REPONDU;
    }
}