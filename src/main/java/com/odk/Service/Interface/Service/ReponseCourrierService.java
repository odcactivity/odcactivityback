package com.odk.Service.Interface.Service;

import com.odk.Entity.Courrier;
import com.odk.Entity.ReponseCourrier;
import com.odk.Entity.Utilisateur;
import com.odk.Repository.CourrierRepository;
import com.odk.Repository.ReponseCourrierRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.dto.ReponseCourrierDTO;
import com.odk.exception.CourrierValidationException;
import com.odk.exception.FileValidationException;
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
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReponseCourrierService {

    private final ReponseCourrierRepository reponseCourrierRepository;
    private final CourrierRepository courrierRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EmailService emailService;
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

        // Envoyer les notifications par email
        envoyerNotificationsReponse(courrier, dto);

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

    /**
     * Envoie les notifications email lors d'une réponse à un courrier
     */
    private void envoyerNotificationsReponse(Courrier courrier, ReponseCourrierDTO dto) {
        try {
            // 1. Email au responsable de l'entité du courrier
            if (courrier.getEntite().getResponsable() != null && 
                courrier.getEntite().getResponsable().getEmail() != null) {
                
                String emailBody = buildEmailBodyReponse(
                    courrier.getExpediteur(),
                    courrier.getEntite().getNom(),
                    courrier.getObjet(),
                    dto.getEmail(),
                    dto.getMessage()
                );
                
                emailService.sendSimpleEmail(
                    courrier.getEntite().getResponsable().getEmail(),
                    "Réponse au courrier : " + courrier.getNumero(),
                    emailBody
                );
            }

            // 2. Email à l'utilisateur affecté au courrier
            if (courrier.getUtilisateurAffecte() != null && 
                courrier.getUtilisateurAffecte().getEmail() != null) {
                
                String emailBody = buildEmailBodyReponse(
                    courrier.getExpediteur(),
                    courrier.getEntite().getNom(),
                    courrier.getObjet(),
                    dto.getEmail(),
                    dto.getMessage()
                );
                
                emailService.sendSimpleEmail(
                    courrier.getUtilisateurAffecte().getEmail(),
                    "Réponse au courrier : " + courrier.getNumero(),
                    emailBody
                );
            }

            // 3. Email à l'expéditeur original si c'est un email interne
            if (courrier.getExpediteur() != null && 
                courrier.getExpediteur().contains("@")) {
                
                String emailBody = buildEmailBodyExpediteur(
                    courrier.getNumero(),
                    courrier.getObjet(),
                    dto.getEmail(),
                    dto.getMessage()
                );
                
                emailService.sendSimpleEmail(
                    courrier.getExpediteur(),
                    "Votre courrier a reçu une réponse : " + courrier.getNumero(),
                    emailBody
                );
            }

            log.info("Notifications email envoyées pour la réponse au courrier {}", courrier.getId());
            
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi des notifications email pour la réponse au courrier {} : {}", 
                     courrier.getId(), e.getMessage());
            // Ne pas lever d'exception pour ne pas bloquer le processus de réponse
        }
    }

    /**
     * Construit le corps de l'email pour la notification de réponse
     */
    private String buildEmailBodyReponse(String expediteur, String entite, String objetCourrier, 
                                        String repondeur, String messageReponse) {
        return "<!DOCTYPE html><html><body>"
                + "<div style='font-family: Arial, sans-serif; padding: 20px; background-color: #f5f5f5;'>"
                + "<div style='background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                + "<h2 style='color: #2c3e50; margin-bottom: 20px;'>📬 Réponse à un Courrier</h2>"
                + "<table style='width: 100%; border-collapse: collapse;'>"
                + "<tr style='background-color: #ecf0f1;'><td style='padding: 10px; font-weight: bold;'>Courrier original :</td><td style='padding: 10px;'>" + objetCourrier + "</td></tr>"
                + "<tr><td style='padding: 10px; font-weight: bold;'>Expéditeur :</td><td style='padding: 10px;'>" + expediteur + "</td></tr>"
                + "<tr style='background-color: #ecf0f1;'><td style='padding: 10px; font-weight: bold;'>Entité :</td><td style='padding: 10px;'>" + entite + "</td></tr>"
                + "<tr><td style='padding: 10px; font-weight: bold;'>Répondu par :</td><td style='padding: 10px;'>" + repondeur + "</td></tr>"
                + "</table>"
                + "<div style='margin: 20px 0; padding: 15px; background-color: #e8f5e8; border-left: 4px solid #27ae60;'>"
                + "<h3 style='color: #27ae60; margin-top: 0;'>Message de réponse :</h3>"
                + "<p style='margin: 0; white-space: pre-wrap;'>" + messageReponse + "</p>"
                + "</div>"
                + "<div style='margin-top: 20px; padding: 10px; background-color: #f8f9fa; border-radius: 4px;'>"
                + "<p style='margin: 0; font-size: 0.9em; color: #6c757d;'>"
                + "📅 Date : " + new Date() + "<br>"
                + "📧 Statut : Courrier répondu"
                + "</p>"
                + "</div>"
                + "<hr style='margin: 20px 0; border: none; border-top: 1px solid #dee2e6;'>"
                + "<p style='font-size: 0.9em; color: #6c757d; margin: 0;'>"
                + "Ceci est un email automatique. Merci de ne pas répondre à cet email."
                + "</p>"
                + "</div></div></body></html>";
    }

    /**
     * Construit le corps de l'email pour l'expéditeur original
     */
    private String buildEmailBodyExpediteur(String numeroCourrier, String objetCourrier, 
                                          String repondeur, String messageReponse) {
        return "<!DOCTYPE html><html><body>"
                + "<div style='font-family: Arial, sans-serif; padding: 20px; background-color: #f5f5f5;'>"
                + "<div style='background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                + "<h2 style='color: #27ae60; margin-bottom: 20px;'>✅ Votre Courrier a reçu une Réponse</h2>"
                + "<div style='background-color: #d4edda; padding: 15px; border-radius: 4px; margin-bottom: 20px;'>"
                + "<p style='margin: 0; font-weight: bold;'>Référence : " + numeroCourrier + "</p>"
                + "<p style='margin: 5px 0 0 0;'>Objet : " + objetCourrier + "</p>"
                + "</div>"
                + "<table style='width: 100%; border-collapse: collapse; margin-bottom: 20px;'>"
                + "<tr style='background-color: #ecf0f1;'><td style='padding: 10px; font-weight: bold;'>Répondu par :</td><td style='padding: 10px;'>" + repondeur + "</td></tr>"
                + "</table>"
                + "<div style='margin: 20px 0; padding: 15px; background-color: #e8f5e8; border-left: 4px solid #27ae60;'>"
                + "<h3 style='color: #27ae60; margin-top: 0;'>Réponse reçue :</h3>"
                + "<p style='margin: 0; white-space: pre-wrap;'>" + messageReponse + "</p>"
                + "</div>"
                + "<div style='margin-top: 20px; padding: 10px; background-color: #f8f9fa; border-radius: 4px;'>"
                + "<p style='margin: 0; font-size: 0.9em; color: #6c757d;'>"
                + "📅 Date : " + new Date() + "<br>"
                + "✅ Votre demande a été traitée"
                + "</p>"
                + "</div>"
                + "<hr style='margin: 20px 0; border: none; border-top: 1px solid #dee2e6;'>"
                + "<p style='font-size: 0.9em; color: #6c757d; margin: 0;'>"
                + "Ceci est un email automatique. Merci de ne pas répondre à cet email."
                + "</p>"
                + "</div></div></body></html>";
    }
}
