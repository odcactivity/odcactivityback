/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.odk.Service.Interface.Service;

import com.odk.Entity.ActiviteValidation;
import com.odk.Repository.ActiviteRepository;
import com.odk.Repository.ActiviteValidationRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.Service.Interface.CrudService;
import com.odk.dto.ActiviteValidationDTO;
import com.odk.dto.ActiviteValidationMapper;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author kaloga081009
 */
@Service
public class ActiviteValidationService{

 
    @Autowired
    private ActiviteValidationRepository validationRepository;

    @Autowired
    private ActiviteRepository activiteRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    // Ajouter une validation avec fichier
    public ActiviteValidationDTO ajouterValidation(ActiviteValidationDTO dto, MultipartFile fichier) throws IOException {
        ActiviteValidation validation = ActiviteValidationMapper.INSTANCE.toEntity(dto);

        if (fichier != null && !fichier.isEmpty()) {
            validation.setFichierChiffre(fichier.getBytes());
            validation.setFichierjoint(fichier.getOriginalFilename());
        }
        if (dto.getSuperviseurId() != null) {
        utilisateurRepository.findById(dto.getSuperviseurId()).ifPresent(validation::setSuperviseur);
    } else {
        validation.setSuperviseur(null);
    }

        ActiviteValidation saved = validationRepository.save(validation);
        return ActiviteValidationMapper.INSTANCE.toDto(saved);
    }

    // Liste toutes les validations
    public List<ActiviteValidationDTO> listeValidations() {
        return validationRepository.findAll()
                .stream()
                .map(ActiviteValidationMapper.INSTANCE::toDto)
                .collect(Collectors.toList());
    }

    // Récupérer une validation par ID
    public ActiviteValidationDTO getValidation(Long id) {
        Optional<ActiviteValidation> opt = validationRepository.findById(id);
        return opt.map(ActiviteValidationMapper.INSTANCE::toDto)
                  .orElseThrow(() -> new RuntimeException("Validation non trouvée"));
    }

    // Télécharger le fichier d'une validation
//    public byte[] getFichier(Long id) {
//        ActiviteValidation validation = validationRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Validation non trouvée"));
//
//        if (validation.getFichierChiffre() == null) {
//            throw new RuntimeException("Pas de fichier associé à cette validation");
//        }
//
//        return validation.getFichierChiffre();
//    }

    public String getNomFichier(Long id) {
        ActiviteValidation validation = validationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Validation non trouvée"));

        return validation.getFichierjoint();
    }
 
}
