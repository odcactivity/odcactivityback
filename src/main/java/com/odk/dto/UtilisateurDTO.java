package com.odk.dto;

import com.odk.Entity.Entite;
import com.odk.Entity.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UtilisateurDTO {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String phone;
    private String genre;    
    private String password;
    private String newpassword;
    private Role role;
    private Entite entite;
    private Boolean etat;
}
