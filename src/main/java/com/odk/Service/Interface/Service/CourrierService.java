package com.odk.Service.Interface.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.odk.Enum.TypeEntite;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.odk.Entity.Courrier;
import com.odk.Entity.Entite;
import com.odk.Entity.HistoriqueCourrier;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.StatutCourrier;
import com.odk.Repository.CourrierRepository;
import com.odk.Repository.EntiteOdcRepository;
import com.odk.Repository.HistoriqueCourrierRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.dto.CourrierDTO;
import com.odk.validation.CourrierValidator;
import com.odk.validation.FileValidationUtil;
import com.odk.exception.CourrierValidationException;
import com.odk.exception.FileValidationException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourrierService {

    private final CourrierRepository courrierRepository;
    private final EntiteOdcRepository entiteRepository;
    private final HistoriqueCourrierRepository historiqueRepository;
    private final EmailService emailService;
    private final UtilisateurRepository utilisateurRepository;
    private final String uploadDir = "uploads/courriers";

    /* ======================================================
     *  RÉCEPTION / ENREGISTREMENT DU COURRIER
     * ====================================================== */
    public Courrier enregistrerCourrier(CourrierDTO dto) throws IOException {
        // Validation stricte des données et du fichier
        CourrierValidator.ValidationResult validation = CourrierValidator.validateCourrierData(dto, dto.getFichier());
        if (!validation.isValid()) {
            throw new CourrierValidationException(validation.getErrorMessage());
        }

        // Validation spécifique du fichier avec gestion d'erreur
        String cheminFichier;
        try {
            cheminFichier = sauvegarderFichierSecurise(dto.getFichier());
        } catch (FileValidationException e) {
            throw new CourrierValidationException("Erreur de validation du fichier : " + e.getMessage(), e);
        } catch (IOException e) {
            throw new CourrierValidationException("Erreur lors de la sauvegarde du fichier : " + e.getMessage(), e);
        }

        Entite direction = entiteRepository.findById(dto.getDirectionId())
                .orElseThrow(() -> new CourrierValidationException("Direction non trouvée"));

        if(direction.getType() != TypeEntite.DIRECTION) {
            throw new CourrierValidationException("Le courrier doit aller uniquement à une direction");
        }

        Courrier courrier = new Courrier();
        courrier.setNumero(dto.getNumero());
        courrier.setObjet(dto.getObjet());
        courrier.setExpediteur(dto.getExpediteur());
        courrier.setEntite(direction);
        courrier.setDirectionInitial(direction);
        courrier.setFichier(cheminFichier);
        courrier.setStatut(StatutCourrier.ENVOYER);
        courrier.setDateReception(new Date());
        courrier.setDateLimite(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000));
        courrier.setDateRelance(new Date(System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000));
        courrierRepository.save(courrier);

        // Historique
        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(direction);
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.ENVOYER);
        historique.setCommentaire("Réception du courrier à la direction");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(null);
        historique.setNouvelleEntite(direction);
        historiqueRepository.save(historique);

        // Email
        if(direction.getResponsable() != null && direction.getResponsable().getEmail() != null){
            String emailBody = buildEmailBody(
                    courrier.getExpediteur(),
                    direction.getNom(),
                    direction.getType(),
                    "Un nouveau courrier a été reçu à votre attention : " + courrier.getObjet()
            );
            String sujet = "Nouveau courrier reçu : " + courrier.getNumero();
            emailService.sendSimpleEmail(direction.getResponsable().getEmail(), sujet, emailBody);
        }

        return courrier;
    }

    /* ======================================================
     *  IMPUTATION / PARTAGE / ENVOI
     * ====================================================== */
    public Courrier imputerCourrier(Long courrierId, Long entiteCibleId, Utilisateur utilisateurCible) {

        Courrier courrier = getCourrier(courrierId);

        Entite service = entiteRepository.findById(entiteCibleId)
                .orElseThrow(() -> new RuntimeException("Entité cible non trouvée"));

        if(service.getType() != TypeEntite.SERVICE) {
            throw new RuntimeException("Imputation vers service seulement");
        }

        if(service.getParent() == null || !service.getParent().getId().equals(courrier.getEntite().getId())) {
            throw new RuntimeException("Service hors portée de la direction");
        }

        Utilisateur utilisateur = null;
        if(utilisateurCible != null && utilisateurCible.getId() != null) {
            utilisateur = utilisateurRepository.findById(utilisateurCible.getId())
                    .orElseThrow(() -> new RuntimeException("Utilisateur cible non trouvé"));
        }

        // Historique
        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setUtilisateur(utilisateur);
        historique.setEntite(service);
        historique.setStatut(StatutCourrier.IMPUTER);
        historique.setCommentaire(utilisateur != null ?
                "Courrier affecté à : " + utilisateur.getNom() :
                "Courrier imputé à " + service.getNom());
        historique.setDateAction(new Date());
        historique.setAncienneEntite(courrier.getEntite());
        historique.setNouvelleEntite(service);
        historiqueRepository.save(historique);

        // Mettre à jour le courrier
        courrier.setEntite(service);
        courrier.setUtilisateurAffecte(utilisateur);
        courrier.setStatut(StatutCourrier.IMPUTER);
        if(courrier.getDateRelance() == null){
            courrier.setDateRelance(new Date(courrier.getDateReception().getTime() + 2L * 24 * 60 * 60 * 1000));
        }
        courrier.setRappelEnvoye(false);
        courrier.setAlerteEnvoyee(false);
        courrierRepository.save(courrier);

        // Email
        String corpsMessage = utilisateur != null ?
                "Un courrier vous a été affecté : " + courrier.getNumero() :
                "Un courrier a été imputé à votre service : " + service.getNom();

        String emailBody = buildEmailBody(
                courrier.getExpediteur(),
                service.getNom(),
                service.getType(),
                corpsMessage
        );

        if(utilisateur != null && utilisateur.getEmail() != null){
            emailService.sendSimpleEmail(utilisateur.getEmail(), "Courrier à traiter", emailBody);
        } else if(service.getResponsable() != null && service.getResponsable().getEmail() != null){
            emailService.sendSimpleEmail(service.getResponsable().getEmail(), "Courrier imputé à votre service", emailBody);
        }

        return courrier;
    }
// ======================================================
//  PARTIE 5 : CONSULTATION DES COURRIERS
// ======================================================
    /**
     * Liste des courriers actifs (non archivés) pour une entité donnée
     */
    public List<Courrier> courriersActifs(Long entiteId) {
        return courrierRepository.findByEntiteIdAndStatutNot(entiteId, StatutCourrier.ARCHIVER);
    }

    /**
     * Liste des courriers archivés pour une entité donnée
     */
    public List<Courrier> courriersArchives(Long entiteId) {
        return courrierRepository.findByEntiteIdAndStatut(entiteId, StatutCourrier.ARCHIVER);
    }
    /* ======================================================
     *  OUVERTURE / TRAITEMENT
     * ====================================================== */
    public ResponseEntity<InputStreamResource> ouvrirCourrier(Long courrierId, Utilisateur utilisateur) throws IOException {
        Courrier courrier = getCourrier(courrierId);

        File fichier = new File(courrier.getFichier());
        if(!fichier.exists()) throw new RuntimeException("Fichier non trouvé");

        // Historique ouverture
        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setUtilisateur(utilisateur);
        historique.setEntite(courrier.getEntite());
        historique.setStatut(StatutCourrier.EN_COURS);
        historique.setCommentaire("Courrier ouvert et en cours de traitement");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(courrier.getEntite());
        historique.setNouvelleEntite(courrier.getEntite());
        historiqueRepository.save(historique);

        // Mettre le statut en cours
        if(courrier.getStatut() == StatutCourrier.IMPUTER){
            courrier.setStatut(StatutCourrier.EN_COURS);
            courrierRepository.save(courrier);
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(fichier));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fichier.getName() + "\"")
                .contentLength(fichier.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /* ======================================================
     *  ARCHIVAGE
     * ====================================================== */
    public void archiverCourrier(Long courrierId, Utilisateur utilisateur){
        Courrier courrier = getCourrier(courrierId);
        courrier.setStatut(StatutCourrier.ARCHIVER);
        courrier.setDateArchivage(new Date());
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setUtilisateur(utilisateur);
        historique.setEntite(courrier.getEntite());
        historique.setStatut(StatutCourrier.ARCHIVER);
        historique.setCommentaire("Courrier archivé");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(courrier.getEntite());
        historique.setNouvelleEntite(courrier.getEntite());
        historiqueRepository.save(historique);

        // Email
        if(utilisateur.getEmail() != null){
            String emailBody = buildEmailBody(
                    courrier.getExpediteur(),
                    courrier.getEntite().getNom(),
                    courrier.getEntite().getType(),
                    "Le courrier a été archivé : " + courrier.getObjet()
            );
            emailService.sendSimpleEmail(utilisateur.getEmail(), "Courrier archivé", emailBody);
        }
    }

    /* ======================================================
     *  MÉTHODES UTILITAIRES
     * ====================================================== */
    private Courrier getCourrier(Long id){
        return courrierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Courrier non trouvé"));
    }

    private String sauvegarderFichier(MultipartFile fichier) throws IOException{
        if(fichier == null || fichier.isEmpty()) return null;
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        String nomFichier = System.currentTimeMillis() + "_" + Paths.get(fichier.getOriginalFilename()).getFileName();
        Path destination = uploadPath.resolve(nomFichier);
        fichier.transferTo(destination.toFile());
        return destination.toString();
    }

    /**
     * Sauvegarde sécurisée du fichier avec validation stricte
     */
    private String sauvegarderFichierSecurise(MultipartFile fichier) throws IOException {
        // Validation préliminaire
        FileValidationUtil.ValidationResult validation = FileValidationUtil.validateFile(fichier);
        if (!validation.isValid()) {
            throw new FileValidationException(validation.getErrorMessage());
        }

        if (fichier == null || fichier.isEmpty()) {
            throw new FileValidationException("Aucun fichier fourni");
        }

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        // Création du répertoire sécurisé
        Files.createDirectories(uploadPath);

        // Génération d'un nom de fichier sécurisé
        String originalFilename = fichier.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String nomFichier = System.currentTimeMillis() + "_" +
                FileValidationUtil.normalizeFilename(originalFilename.substring(0, originalFilename.lastIndexOf("."))) + extension;

        Path destination = uploadPath.resolve(nomFichier);

        // Vérification que le chemin est bien dans le répertoire autorisé
        if (!destination.startsWith(uploadPath)) {
            throw new FileValidationException("Tentative de chemin de fichier non autorisée");
        }

        // Sauvegarde du fichier
        fichier.transferTo(destination.toFile());

        // Vérification finale que le fichier existe et est accessible
        if (!Files.exists(destination) || !Files.isReadable(destination)) {
            throw new FileValidationException("Échec de la sauvegarde du fichier");
        }

        return destination.toString();
    }

    private String buildEmailBody(String expediteur, String departement, TypeEntite role, String message){
        return "<!DOCTYPE html><html><body>"
                + "<div style='font-family: Arial, sans-serif; padding: 20px;'>"
                + "<img src='cid:logo' alt='Logo' style='width:100px;height:50px;'/><br>"
                + "<b>Expéditeur :</b> " + expediteur + "<br>"
                + "<b>Département :</b> " + departement + "<br>"
                + "<b>Rôle :</b> " + role + "<br><br>"
                + "<p>" + message + "</p>"
                + "<hr><p style='font-size:0.9em;'>Ceci est un email automatique. Merci de ne pas répondre.</p>"
                + "</div></body></html>";
    }

    /**
     * Récupère les courriers par statut et entité
     */
    public List<Courrier> getCourriersByStatutAndEntite(StatutCourrier statut, Long directionInitial) {
        Optional<Entite> entite=entiteRepository.findById(directionInitial);
        return courrierRepository.findByDirectionInitial(entite.get());
                //findByEntiteIdAndStatut(entiteId, statut);
    }
}