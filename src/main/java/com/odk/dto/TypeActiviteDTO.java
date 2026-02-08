package com.odk.dto;

import com.odk.Entity.Utilisateur;
import lombok.Data;

@Data
public class TypeActiviteDTO {
    private Long id;
    private String type;
    private Utilisateur created_by;
}