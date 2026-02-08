/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.odk.dto;

/**
 *
 * @author kaloga081009
 */
import com.odk.Entity.ActiviteValidation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ActiviteValidationMapper {

    ActiviteValidationMapper INSTANCE = Mappers.getMapper(ActiviteValidationMapper.class);

    // Convertir Entité → DTO
    @Mapping(source = "activite.id", target = "activiteId")
    @Mapping(source = "superviseur.id", target = "superviseurId")
    ActiviteValidationDTO toDto(ActiviteValidation validation);

    // Convertir liste Entité → DTO
    List<ActiviteValidationDTO> toDtoList(List<ActiviteValidation> validations);

    // Convertir DTO → Entité
    @Mapping(source = "activiteId", target = "activite.id")
    @Mapping(source = "superviseurId", target = "superviseur.id")
    ActiviteValidation toEntity(ActiviteValidationDTO dto);
}

