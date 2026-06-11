package com.odk.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RapportCourrierApercuDTO {
    private Long id;
    private String numero;
    private String objet;
    private String expediteur;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateReception;
    private String statut;
    private String entiteNom;
}
