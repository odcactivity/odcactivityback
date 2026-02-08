/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.odk.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.odk.Entity.Critere;
import com.odk.Entity.Liste;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.Statut;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @author kaloga081009
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EtapeDTOSansActivite {
    private Long id;
    private String nom;
    private Statut statut;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateDebut;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateFin;   
    private List<Critere> critere;
    private List<Liste> listes;
    private Utilisateur created_by;
    private Long activiteid;
}
