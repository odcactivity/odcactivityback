package com.odk.dto;

import com.odk.Entity.Entite;
import com.odk.Entity.TypeActivite;
import com.odk.Entity.Utilisateur;
import com.odk.dto.EntiteDTO;

import java.util.List;
import java.util.stream.Collectors;

public class EntiteMapper {

    // Convertir une Entite vers un EntiteDTO
    public static EntiteDTO toDto(Entite entite) {
        if (entite == null) return null;

        List<Long> typeActiviteIds = null;
        if (entite.getTypeActivitesIds()!= null) {
            typeActiviteIds = entite.getTypeActivitesIds()
                    .stream()
                    .map(TypeActivite::getId)
                    .collect(Collectors.toList());
        }

        Long responsableId = entite.getResponsable() != null 
                            ? entite.getResponsable().getId() : null;
        
        Long parentId = entite.getParent() != null 
                            ? entite.getParent().getId() : null;

         List<Long> sousEntiteIds = null;
         
           if (entite.getSousEntite() != null) {
               sousEntiteIds = entite.getSousEntite()
                .stream()
                .map(Entite::getId)
                .collect(Collectors.toList());
    }
        return new EntiteDTO(
                entite.getId(),
                entite.getNom(),
                entite.getLogo(),
                entite.getDescription(),
                parentId,
                sousEntiteIds,
                responsableId,
                typeActiviteIds
                
        );
    }

    // Convertir un EntiteDTO vers une Entite (partiellement, les relations doivent être complétées ensuite)
    public static Entite toEntity(EntiteDTO dto) {
        if (dto == null) return null;

        Entite entite = new Entite();
        entite.setId(dto.getId());
        entite.setNom(dto.getNom());
        entite.setLogo(dto.getLogo());
        entite.setDescription(dto.getDescription());

        // ===== Parent =====
        if (dto.getParentId() != null) {
            Entite parent = new Entite();
            parent.setId(dto.getParentId());
            entite.setParent(parent);
        }


        if (dto.getResponsable() != null) {
            Utilisateur responsable = new Utilisateur();
            responsable.setId(dto.getResponsable());
            entite.setResponsable(responsable);
        }

        if (dto.getTypeActivitesIds() != null) {
            List<TypeActivite> typeActivites = dto.getTypeActivitesIds().stream().map(id -> {
                TypeActivite ta = new TypeActivite();
                ta.setId(id);
                return ta;
            }).collect(Collectors.toList());
            entite.setTypeActivitesIds(typeActivites);
        }

        return entite;
    }
}
