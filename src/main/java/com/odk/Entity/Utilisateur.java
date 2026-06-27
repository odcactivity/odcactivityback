package com.odk.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Utilisateur implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nom;
    private String prenom;
    @Column(unique = true)
    private String email;
    private String phone;
    private String password;
    private String genre;
    @Column(nullable = true)
    private Boolean etat;

    public Utilisateur(Long id) {
        this.id = id;
    }

    @ManyToOne
    @JoinColumn(name = "role_id")
    @JsonIgnore
    private Role role;

    @ManyToOne
    @JoinColumn(name = "entite_id")
    @JsonIgnore
    private Entite entite;

    @Transient
    @JsonIgnore
    private Collection<? extends GrantedAuthority> authorities;
    
    @OneToMany(mappedBy = "superviseur", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<ActiviteValidation> validations = new ArrayList<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.role == null || this.role.getNom() == null) {
            return Collections.emptyList();
        }

        String roleName = normalizeRoleName(this.role.getNom());
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));

        // Compatibilité ADMIN ↔ SUPERADMIN
        if ("ADMIN".equals(roleName)) {
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_SUPERADMIN"));
        } else if ("SUPERADMIN".equals(roleName)) {
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        // Alias métier (API @PreAuthorize utilise parfois DCIRE, parfois DIRECTEUR)
        if ("DIRECTEUR".equals(roleName)) {
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_DCIRE"));
        } else if ("DCIRE".equals(roleName)) {
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_DIRECTEUR"));
        }

        return grantedAuthorities;
    }

    /** Nom de rôle stable pour Spring Security (MAJUSCULES + underscores). */
    public static String normalizeRoleName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
