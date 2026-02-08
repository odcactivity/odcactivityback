package com.odk.dto;


import com.odk.Entity.Liste;

import org.mapstruct.Mapper;
import java.util.List;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring",uses=ParticipantMapper.class)
public interface ListeMapper {
   
     // ENTITY → DTO
//    @Mapping(source = "id", target = "id")
    @Mapping(source = "etape.id", target = "etape.id")
    @Mapping(source = "listeDebut", target = "listeDebut")
    @Mapping(source = "listeResultat", target = "listeResultat")
//    @Mapping(source = "participants", target = "participants")  // évite loop et erreurs de mapping
    ListeDTO toDto(Liste entity);


    // DTO → ENTITY
//    @Mapping(source = "listeId", target = "id")
    @Mapping(target = "etape", ignore = true)          // Chargé manuellement dans le service
    @Mapping(target = "participants", ignore = true)   // On injecte les participants à la main
    Liste toEntity(ListeDTO dto);

    List<ListeDTO> liste(List<Liste> liste);
    
}
