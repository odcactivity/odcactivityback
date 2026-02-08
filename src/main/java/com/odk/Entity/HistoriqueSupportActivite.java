package com.odk.Entity;


import com.odk.Enum.StatutSupport;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoriqueSupportActivite {
    
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "support_id")
    private SupportActivite support;

    @Enumerated(EnumType.STRING)
    private StatutSupport statut;

    @Column(length = 1000)
    private String commentaire;

    @Temporal(TemporalType.TIMESTAMP)
    private Date dateModification;

    private String emailAuteur;

   
}
