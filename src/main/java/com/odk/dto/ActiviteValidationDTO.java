/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.odk.dto;

/**
 *
 * @author kaloga081009
 */
import com.odk.Enum.StatutValidation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor

public class ActiviteValidationDTO {

    private Long id;
    private String commentaire;
    private Date date;
    private StatutValidation statut;
    private String fichierjoint;
    private Long envoyeurId;
    private Long activiteId;     
    private Long superviseurId;  
    private Boolean isRead;
    public ActiviteValidationDTO(Long id, String commentaire, Date date, StatutValidation statut, String fichierjoint,Long envoyeurId, Long activiteId, Long superviseurId) {
        this.id = id;
        this.commentaire = commentaire;
        this.date = date;
        this.statut = statut;
        this.fichierjoint = fichierjoint;
        this.envoyeurId=envoyeurId;
        this.activiteId = activiteId;
        this.superviseurId = superviseurId;
    }
  
    
}