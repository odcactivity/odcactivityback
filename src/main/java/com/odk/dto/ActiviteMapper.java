package com.odk.dto;

import com.odk.Entity.Activite;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ActiviteMapper {

//    ActiviteMapper INSTANCE = Mappers.getMapper(ActiviteMapper.class);

    // Convert Mission entity to DTO
//    @Mapping(source = "listeDebut", target = "listeDebut", ignore = true)
    @Mapping(source = "validations",target = "activitevalidation" )
    ActiviteDTO ACTIVITE_DTO(Activite activite);
    
    Activite toEntity(ActiviteDTO activite);
    
    List<ActiviteDTO> listeActivite(List<Activite> activite);
     // méthode essentielle pour PATCH / update non-destructif
    void updateFromDto(ActiviteDTO dto, @MappingTarget Activite entity);

    }
