package com.odk.dto;

import com.odk.Entity.Etape;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import java.util.List;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;


@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface EtapeMapper {

   EtapeMapper INSTANCE = Mappers.getMapper(EtapeMapper.class);    

    // Convert MissionDTO to entity
   // Etape etapeDTO(EtapeDTO etapeDTO);
//    @Mapping(target = "activite.activitevalidation", ignore = true)
    
    @Mapping(target = "listes", source ="listes",defaultExpression ="java(new ArrayList<>())")
    EtapeDTO toDto(Etape etape);
    
    @Mapping(target = "activite.validations", ignore = true)
    
    @Mapping(target = "listes", ignore = true) // géré par service lors de la création
    Etape toEntity(EtapeDTO dto);
    
    @Mapping(source = "activite.id", target = "activiteid") // géré par service
    EtapeDTOSansActivite toSansActivite(Etape etape);
    
    @Mapping(source = "activiteid", target = "activite.id")
    Etape toEntitesansActivite(EtapeDTOSansActivite etape);

    
    List<EtapeDTO> listeEtape(List<Etape> etapes);
    List<EtapeDTOSansActivite> listeEtapeSansActivite(List<Etape> etapes);
    List<Etape> listeEtapeSansAc(List<EtapeDTOSansActivite> etapes);
     void updateFromDto(EtapeDTOSansActivite dto, @MappingTarget Etape entity);


}





