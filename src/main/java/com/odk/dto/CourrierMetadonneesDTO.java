package com.odk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourrierMetadonneesDTO {
    private String numero;
    private String objet;
    private String expediteur;
}
