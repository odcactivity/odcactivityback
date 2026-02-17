package com.odk.Service.Interface.Service;

import com.odk.Entity.Courrier;
import com.odk.Entity.ReponseCourrier;
import com.odk.Entity.Utilisateur;
import com.odk.Repository.CourrierRepository;
import com.odk.Repository.ReponseCourrierRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.dto.ReponseCourrierDTO;
import com.odk.exception.CourrierValidationException;
import com.odk.validation.FileValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReponseCourrierService {

    private final ReponseCourrierRepository reponseCourrierRepository;
    private final CourrierRepository courrierRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final String uploadDir = "uploads/reponses";

    /**
     * Enregistre une réponse à un courrier avec validation stricte des fichiers
     */
    @Transactional
    public ReponseCourrier repondreCourrier(ReponseCourrierDTO dto) throws IOException {
        // Validation des données obligatoires
        validerReponseCourrier(dto);

        // Récupération du courrier
        Courrier courrier = courrierRepository.findById(dto.getCourrierId())
                .orElseThrow(() -> new CourrierValidationException("Courrier non trouvé"));

        // Validation des fichiers joints
        List<String> fichiersJoints = new ArrayList<>();
        
        // Traitement du fichier principal
        if (dto.getFile() != null && !dto.getFile().isEmpty()) {
            String cheminFichier = sauvegarderFichierSecurise(dto.getFile());
            fichiersJoints.add(cheminFichier);
        }

        // Traitement des fichiers multiples
        if (dto.getAttachments() != null && !dto.getAttachments().isEmpty()) {
            for (MultipartFile attachment : dto.getAttachments()) {
                if (attachment != null && !attachment.isEmpty()) {
                    String cheminFichier = sauvegarderFichierSecurise(attachment);
                    fichiersJoints.add(cheminFichier);
                }
            }
        }

        // Création de la réponse
        ReponseCourrier reponse = new ReponseCourrier();
        reponse.setCourrier(courrier);
        reponse.setEmail(dto.getEmail());
        reponse.setObjet(dto.getObjet());
        reponse.setMessage(dto.getMessage());

        // Gestion des fichiers joints
        if (!fichiersJoints.isEmpty()) {
            if (fichiersJoints.size() == 1) {
                reponse.setFichierJoint(fichiersJoints.get(0));
            } else {
                reponse.setFichiersMultiples(String.join(";", fichiersJoints));
            }
        }

        // Association à l'utilisateur si existant
        utilisateurRepository.findByEmail(dto.getEmail())
                .ifPresent(reponse::setUtilisateur);

        // Sauvegarde
        ReponseCourrier savedReponse = reponseCourrierRepository.save(reponse);

        // Mise à jour du statut du courrier original
        courrier.setStatut(com.odk.Enum.StatutCourrier.REPONDU);
        courrierRepository.save(courrier);

        log.info("Réponse enregistrée pour le courrier {} par {}", courrier.getId(), dto.getEmail());
        return savedReponse;
    }

    /**
     * Récupère toutes les réponses pour un courrier
     */
    public List<ReponseCourrier> getReponsesByCourrier(Long courrierId) {
        return reponseCourrierRepository.findReponsesByCourrierId(courrierId);
    }

    /**
     * Vérifie si un utilisateur a déjà répondu à un courrier
     */
    public boolean hasUserResponded(Long courrierId, String email) {
        return reponseCourrierRepository.hasUserResponded(courrierId, email);
    }

    /**
     * Validation stricte des données de réponse
     */
    private void validerReponseCourrier(ReponseCourrierDTO dto) {
        if (dto.getCourrierId() == null) {
            throw new CourrierValidationException("L'ID du courrier est obligatoire");
        }

        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new CourrierValidationException("L'email est obligatoire");
        }

        if (!dto.getEmail().matches("^[A-Za-z0-9+_.-]+@[^\\s@]+[^\\s@]+\\.[^\\s@]+$")) {
            throw new CourrierValidationException("Format d'email invalide");
        }

        if (dto.getObjet() == null || dto.getObjet().trim().isEmpty()) {
            throw new CourrierValidationException("L'objet de la réponse est obligatoire");
        }

        if (dto.getMessage() == null || dto.getMessage().trim().isEmpty()) {
            throw new CourrierValidationException("Le message de la réponse est obligatoire");
        }

        // Validation des fichiers
        if (dto.getFile() != null && !dto.getFile().isEmpty()) {
            FileValidationUtil.ValidationResult validation = FileValidationUtil.validateFile(dto.getFile());
            if (!validation.isValid()) {
                throw new CourrierValidationException("Erreur de validation du fichier : " + validation.getErrorMessage());
            }
        }

        if (dto.getAttachments() != null) {
            for (MultipartFile attachment : dto.getAttachments()) {
                if (attachment != null && !attachment.isEmpty()) {
                    FileValidationUtil.ValidationResult validation = FileValidationUtil.validateFile(attachment);
                    if (!validation.isValid()) {
                        throw new CourrierValidationException("Erreur de validation du fichier joint : " + validation.getErrorMessage());
                    }
                }
            }
        }
    }

    /**
     * Sauvegarde sécurisée des fichiers avec validation
     */
    private String sauvegarderFichierSecurise(MultipartFile fichier) throws IOException {
        FileValidationUtil.ValidationResult validation = FileValidationUtil.validateFile(fichier);
        if (!validation.isValid()) {
            throw new CourrierValidationException("Erreur de validation du fichier : " + validation.getErrorMessage());
        }

        if (fichier == null || fichier.isEmpty()) {
            throw new CourrierValidationException("Aucun fichier fourni");
        }

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        String originalFilename = fichier.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String nomFichier = System.currentTimeMillis() + "_" +
                FileValidationUtil.normalizeFilename(originalFilename.substring(0, originalFilename.lastIndexOf("."))) + extension;

        Path destination = uploadPath.resolve(nomFichier);

        if (!destination.startsWith(uploadPath)) {
            throw new CourrierValidationException("Tentative de chemin de fichier non autorisée");
        }

        fichier.transferTo(destination.toFile());

        if (!Files.exists(destination) || !Files.isReadable(destination)) {
            throw new CourrierValidationException("Échec de la sauvegarde du fichier");
        }

        return destination.toString();
    }
}
