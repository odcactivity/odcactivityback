package com.odk.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.odk.Enum.Statut;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RapportActiviteApercuDTO {
    private Long id;
    private String nom;
    private String titre;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateDebut;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateFin;
    private Statut statut;
    private String entiteNom;
    private String lieu;
}
