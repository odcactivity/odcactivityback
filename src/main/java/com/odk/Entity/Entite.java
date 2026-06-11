package com.odk.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.odk.Enum.TypeEntite;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "entite")
public class Entite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;
    private String logo;
    private String description;

    // Type: DIRECTION ou SERVICE
    @Enumerated(EnumType.STRING)
    @JsonIgnore
    private TypeEntite type;

    /**
     * Flag indicating whether this entity belongs to the ODC scope (set by admin).
     */
    @Column(name = "is_odc", nullable = false, columnDefinition = "boolean default false")
    private Boolean isOdc = false;

    @OneToMany(mappedBy = "entite")
    @JsonIgnore
    private List<Activite> activite;

    @ManyToOne
    @JoinColumn(name = "responsable_id")
    @JsonIgnore
    private Utilisateur responsable;

    // Reflexive association to represent parent direction or service
    @JsonIgnoreProperties({"sousEntite", "parent"})
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Entite parent;

    // Sub‑entities (services or units) under this entity
    @JsonIgnoreProperties({"parent", "sousEntite"})
    @OneToMany(mappedBy = "parent")
    private List<Entite> sousEntite;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "entite_type_activite",
            joinColumns = @JoinColumn(name = "entite_id"),
            inverseJoinColumns = @JoinColumn(name = "type_activite_id")
    )
    private List<TypeActivite> typeActivitesIds;

    // Constructor for deserialization by ID only
    public Entite(Long id) {
        this.id = id;
    }
}
