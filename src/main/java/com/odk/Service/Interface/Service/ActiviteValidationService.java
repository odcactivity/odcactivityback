/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.odk.Service.Interface.Service;

import com.odk.Entity.Activite;
import com.odk.Entity.ActiviteValidation;
import com.odk.Entity.Utilisateur;
import com.odk.Repository.ActiviteRepository;
import com.odk.Repository.ActiviteValidationRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.Service.Interface.CrudService;
import com.odk.dto.ActiviteValidationDTO;
import com.odk.dto.ActiviteValidationMapper;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    private ActiviteValidationMapper activiteValidationMapper;


    // Ajouter une validation avec fichier
    public ActiviteValidationDTO ajouterValidation(ActiviteValidationDTO dto, MultipartFile fichier) throws IOException {
        ActiviteValidation validation = activiteValidationMapper.toEntity(dto);

        // Charger l'entité Activite managée depuis le repository
        if (dto.getActiviteId() != null) {
            activiteRepository.findById(dto.getActiviteId()).ifPresent(validation::setActivite);
        }

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
        return activiteValidationMapper.toDto(saved);
    }

    // Liste toutes les validations
    public List<ActiviteValidationDTO> listeValidations() {
        return validationRepository.findAll()
                .stream()
                .map(activiteValidationMapper::toDto)
                .collect(Collectors.toList());
    }

    // Récupérer une validation par ID
    public ActiviteValidationDTO getValidation(Long id) {
        Optional<ActiviteValidation> opt = validationRepository.findById(id);
        return opt.map(activiteValidationMapper::toDto)
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

    /** Remplace la pièce jointe d'une validation (correction personnel après retour responsable). */
    @Transactional
    public ActiviteValidationDTO mettreAJourFichier(Long validationId, MultipartFile fichier, Utilisateur utilisateur)
            throws IOException {
        ActiviteValidation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Validation introuvable"));

        Long activiteId = null;
        if (validation.getActivite() != null) {
            activiteId = validation.getActivite().getId();
        }
        if (activiteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune activité associée à cette pièce jointe.");
        }

        Activite activite = activiteRepository.findById(activiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité introuvable"));

        if (activite.getCreatedBy() == null || utilisateur == null || utilisateur.getId() == null
                || !Objects.equals(activite.getCreatedBy().getId(), utilisateur.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez modifier que les pièces jointes de vos propres activités.");
        }
        if (fichier == null || fichier.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier requis.");
        }

        validation.setFichierChiffre(fichier.getBytes());
        validation.setFichierjoint(fichier.getOriginalFilename());
        validation.setDate(new Date());
        ActiviteValidation saved = validationRepository.save(validation);

        ActiviteValidationDTO dto = new ActiviteValidationDTO();
        dto.setId(saved.getId());
        dto.setActiviteId(activiteId);
        dto.setFichierjoint(saved.getFichierjoint());
        dto.setCommentaire(saved.getCommentaire());
        dto.setDate(saved.getDate());
        dto.setEnvoyeurId(saved.getEnvoyeurId());
        return dto;
    }

}
