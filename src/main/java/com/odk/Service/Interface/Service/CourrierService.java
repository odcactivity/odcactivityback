package com.odk.Service.Interface.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.odk.Enum.TypeEntite;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class CourrierService {

    private static final Logger log = LoggerFactory.getLogger(CourrierService.class);

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String appFrontendBaseUrl;

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
        courrier.setStructureOrigine(direction);
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
     * Récupère les courriers par statut et entité (détenteur courant = entité).
     */
    public List<Courrier> getCourriersByStatutAndEntite(StatutCourrier statut, Long entiteId) {
        entiteRepository.findById(entiteId)
                .orElseThrow(() -> new RuntimeException("Entité introuvable"));
        return courrierRepository.findByEntiteIdAndStatut(entiteId, statut);
    }

    public List<Courrier> listerPourDcire() {
        return courrierRepository.findAllOrderByDateReceptionDesc();
    }

    public List<Courrier> listerPourOdc(Long directionId, String vue) {
        entiteRepository.findById(directionId)
                .orElseThrow(() -> new CourrierValidationException("Direction ODC introuvable"));
        if ("VALIDATION".equalsIgnoreCase(vue)) {
            return courrierRepository.findEnAttenteValidationOdc(directionId, List.of(
                    StatutCourrier.ATTENTE_VALIDATION_ODC,
                    StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC,
                    StatutCourrier.EN_REVISION_ADMIN_COURRIER));
        }
        if ("ARCHIVES".equalsIgnoreCase(vue)) {
            return courrierRepository.findVisiblePourDirectionOdc(directionId, List.of(StatutCourrier.ARCHIVER));
        }
        if ("REPONDUS".equalsIgnoreCase(vue)) {
            return courrierRepository.findVisiblePourDirectionOdc(directionId, List.of(StatutCourrier.REPONDU));
        }
        return courrierRepository.findVisiblePourDirectionOdc(directionId, List.of(
                StatutCourrier.ENVOYER,
                StatutCourrier.IMPUTER,
                StatutCourrier.EN_COURS,
                StatutCourrier.TRANSMIS_DCIRE,
                StatutCourrier.EN_REVISION_ADMIN_COURRIER));
    }

    public Courrier creerBrouillonOdc(Long odcDirectionId, CourrierDTO dto) throws IOException {
        Entite odcDir = entiteRepository.findById(odcDirectionId)
                .orElseThrow(() -> new CourrierValidationException("Direction ODC introuvable"));
        if (odcDir.getType() != TypeEntite.DIRECTION) {
            throw new CourrierValidationException("L'identifiant doit correspondre à une direction.");
        }
        if (nomIndiqueDcire(odcDir)) {
            throw new CourrierValidationException("Pour un envoi depuis l'ODC, choisissez une direction ODC, pas la DCIRE.");
        }
        if (!estDirectionOdc(odcDir)) {
            throw new CourrierValidationException("La direction sélectionnée n'est pas reconnue comme périmètre ODC.");
        }

        CourrierValidator.ValidationResult validation = CourrierValidator.validateCourrierData(dto, dto.getFichier());
        if (!validation.isValid()) {
            throw new CourrierValidationException(validation.getErrorMessage());
        }
        String cheminFichier;
        try {
            cheminFichier = sauvegarderFichierSecurise(dto.getFichier());
        } catch (FileValidationException e) {
            throw new CourrierValidationException("Erreur de validation du fichier : " + e.getMessage(), e);
        } catch (IOException e) {
            throw new CourrierValidationException("Erreur lors de la sauvegarde du fichier : " + e.getMessage(), e);
        }

        Courrier courrier = new Courrier();
        courrier.setNumero(dto.getNumero());
        courrier.setObjet(dto.getObjet());
        courrier.setExpediteur(dto.getExpediteur());
        courrier.setStructureOrigine(odcDir);
        courrier.setDirectionInitial(odcDir);
        courrier.setEntite(odcDir);
        courrier.setFichier(cheminFichier);
        courrier.setStatut(StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC);
        courrier.setDateReception(new Date());
        courrier.setDateLimite(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000));
        courrier.setDateRelance(new Date(System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000));
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(odcDir);
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC);
        historique.setCommentaire("Courrier créé par l'admin — en attente de validation du directeur ODC");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(null);
        historique.setNouvelleEntite(odcDir);
        historiqueRepository.save(historique);

        notifierDirecteursOdcNouveauCourrier(courrier);
        return courrier;
    }

    public List<Courrier> listerPourValidationDirecteurOdc() {
        return courrierRepository.findByStatutInOrderByDateReceptionDesc(Arrays.asList(
                StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC,
                StatutCourrier.EN_REVISION_ADMIN_COURRIER,
                StatutCourrier.ATTENTE_VALIDATION_ODC));
    }

    public Courrier enregistrerSuggestionDirecteurOdc(Long courrierId, String suggestion) {
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC
                && courrier.getStatut() != StatutCourrier.ATTENTE_VALIDATION_ODC) {
            throw new CourrierValidationException("Ce courrier n'est pas en attente de votre relecture.");
        }
        courrier.setSuggestionDirecteur(suggestion != null ? suggestion : "");
        courrier.setStatut(StatutCourrier.EN_REVISION_ADMIN_COURRIER);
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(courrier.getEntite());
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.EN_REVISION_ADMIN_COURRIER);
        historique.setCommentaire("Suggestions directeur ODC : " + (suggestion != null ? suggestion : ""));
        historique.setDateAction(new Date());
        historique.setAncienneEntite(courrier.getEntite());
        historique.setNouvelleEntite(courrier.getEntite());
        historiqueRepository.save(historique);

        notifierSuperAdminsRevisionCourrier(courrier);
        return courrier;
    }

    public Courrier resoumettreApresRevisionAdmin(Long courrierId) {
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.EN_REVISION_ADMIN_COURRIER) {
            throw new CourrierValidationException("Ce courrier n'est pas en révision côté admin.");
        }
        courrier.setStatut(StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC);
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(courrier.getEntite());
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC);
        historique.setCommentaire("Courrier resoumis par l'admin après prise en compte des suggestions");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(courrier.getEntite());
        historique.setNouvelleEntite(courrier.getEntite());
        historiqueRepository.save(historique);

        notifierDirecteursOdcNouveauCourrier(courrier);
        return courrier;
    }

    public Courrier annulerCourrierParDirecteurOdc(Long courrierId) {
        Courrier courrier = getCourrier(courrierId);
        StatutCourrier st = courrier.getStatut();
        if (st != StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC
                && st != StatutCourrier.EN_REVISION_ADMIN_COURRIER
                && st != StatutCourrier.ATTENTE_VALIDATION_ODC) {
            throw new CourrierValidationException("Ce courrier ne peut pas être annulé depuis cet écran.");
        }
        courrier.setStatut(StatutCourrier.ARCHIVER);
        courrier.setDateArchivage(new Date());
        courrierRepository.save(courrier);
        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(courrier.getEntite());
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.ARCHIVER);
        historique.setCommentaire("Annulé / archivé par le directeur ODC");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(courrier.getEntite());
        historique.setNouvelleEntite(courrier.getEntite());
        historiqueRepository.save(historique);
        return courrier;
    }

    public Courrier validerTransmissionVersDcire(Long courrierId) {
        Courrier courrier = getCourrier(courrierId);
        StatutCourrier st = courrier.getStatut();
        if (st != StatutCourrier.ATTENTE_VALIDATION_ODC && st != StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC) {
            throw new CourrierValidationException("Ce courrier n'est pas en attente de validation avant envoi à la DCIRE.");
        }
        Entite dcire = resolveDcireDirection();
        Entite ancienne = courrier.getEntite();
        courrier.setEntite(dcire);
        courrier.setStatut(StatutCourrier.TRANSMIS_DCIRE);
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(dcire);
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.TRANSMIS_DCIRE);
        historique.setCommentaire("Validé côté ODC — transmission à la DCIRE");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(ancienne);
        historique.setNouvelleEntite(dcire);
        historiqueRepository.save(historique);

        return courrier;
    }

    public Courrier receptionExterneDepuisStructure(Long structureOrigineId, CourrierDTO dto) throws IOException {
        Entite origine = entiteRepository.findById(structureOrigineId)
                .orElseThrow(() -> new CourrierValidationException("Structure d'origine introuvable"));
        if (origine.getType() != TypeEntite.DIRECTION) {
            throw new CourrierValidationException("La structure d'origine doit être une direction.");
        }
        if (nomIndiqueDcire(origine)) {
            throw new CourrierValidationException("La DCIRE ne peut pas être expéditeur sur cette entrée.");
        }
        if (estDirectionOdc(origine)) {
            throw new CourrierValidationException(
                    "Les courriers émis par l'ODC passent par le circuit interne (brouillon + validation), pas par la réception DCIRE.");
        }

        CourrierValidator.ValidationResult validation = CourrierValidator.validateCourrierData(dto, dto.getFichier());
        if (!validation.isValid()) {
            throw new CourrierValidationException(validation.getErrorMessage());
        }
        String cheminFichier;
        try {
            cheminFichier = sauvegarderFichierSecurise(dto.getFichier());
        } catch (FileValidationException e) {
            throw new CourrierValidationException("Erreur de validation du fichier : " + e.getMessage(), e);
        } catch (IOException e) {
            throw new CourrierValidationException("Erreur lors de la sauvegarde du fichier : " + e.getMessage(), e);
        }

        Entite dcire = resolveDcireDirection();
        Courrier courrier = new Courrier();
        courrier.setNumero(dto.getNumero());
        courrier.setObjet(dto.getObjet());
        courrier.setExpediteur(dto.getExpediteur());
        courrier.setStructureOrigine(origine);
        courrier.setDirectionInitial(dcire);
        courrier.setEntite(dcire);
        courrier.setFichier(cheminFichier);
        courrier.setStatut(StatutCourrier.ENVOYER);
        courrier.setDateReception(new Date());
        courrier.setDateLimite(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000));
        courrier.setDateRelance(new Date(System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000));
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(dcire);
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.ENVOYER);
        historique.setCommentaire("Réception à la DCIRE — origine : " + origine.getNom());
        historique.setDateAction(new Date());
        historique.setAncienneEntite(null);
        historique.setNouvelleEntite(dcire);
        historiqueRepository.save(historique);

        return courrier;
    }

    public Courrier transmettreVersOdc(Long courrierId, Long odcDirectionId) {
        Courrier courrier = getCourrier(courrierId);
        Entite dcire = resolveDcireDirection();
        if (courrier.getEntite() == null || !Objects.equals(dcire.getId(), courrier.getEntite().getId())) {
            throw new CourrierValidationException("Le courrier doit être présent sur la direction DCIRE pour être transmis à l'ODC.");
        }
        Entite odc = entiteRepository.findById(odcDirectionId)
                .orElseThrow(() -> new CourrierValidationException("Direction ODC introuvable"));
        if (odc.getType() != TypeEntite.DIRECTION) {
            throw new CourrierValidationException("La cible doit être une direction ODC.");
        }
        if (nomIndiqueDcire(odc) || !estDirectionOdc(odc)) {
            throw new CourrierValidationException("Sélectionnez une direction du périmètre ODC.");
        }

        Entite ancienne = courrier.getEntite();
        courrier.setEntite(odc);
        courrier.setUtilisateurAffecte(null);
        courrier.setStatut(StatutCourrier.ENVOYER);
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(odc);
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.ENVOYER);
        historique.setCommentaire("Transmis par la DCIRE vers l'ODC : " + odc.getNom());
        historique.setDateAction(new Date());
        historique.setAncienneEntite(ancienne);
        historique.setNouvelleEntite(odc);
        historiqueRepository.save(historique);

        return courrier;
    }

    private Entite resolveDcireDirection() {
        return entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(this::nomIndiqueDcire)
                .findFirst()
                .orElseThrow(() -> new CourrierValidationException(
                        "Aucune direction DCIRE trouvée : créez une direction dont le nom contient « DCIRE »."));
    }

    private boolean nomIndiqueDcire(Entite e) {
        return normalizeNomEntite(e.getNom()).contains("DCIRE");
    }

    private String normalizeNomEntite(String nom) {
        if (nom == null) {
            return "";
        }
        return nom.toUpperCase().replaceAll("\\s+", " ").trim();
    }

    /**
     * Périmètre ODC (piliers / ODC), aligné avec le filtrage métier côté front.
     */
    private boolean estDirectionOdc(Entite e) {
        String n = normalizeNomEntite(e.getNom());
        return n.contains("ORANGE DIGITAL KALANSO")
                || n.contains("ODK")
                || n.contains("ORANGE DIGITAL MULTIMEDIA")
                || n.contains("MULTIMEDIA")
                || n.contains("ORANGE FABLAB")
                || n.contains("FABLAB")
                || n.contains("ORANGE FAB")
                || n.contains("ORANGE DIGITAL CENTER")
                || n.contains("DIGITAL CENTER")
                || n.contains("ODC");
    }

    /**
     * ODC (piliers), Fondation, RSE, DCI — sans passage obligatoire par l’entité DCIRE pour les échanges entre eux.
     */
    public boolean estMembreDivisionDcire(Entite e) {
        if (e == null || nomIndiqueDcire(e)) {
            return false;
        }
        String n = normalizeNomEntite(e.getNom());
        boolean dciHorsDcire = n.contains("DCI") && !n.contains("DCIRE");
        return estDirectionOdc(e) || n.contains("FONDATION") || n.contains("RSE") || dciHorsDcire;
    }

    /**
     * Courrier direct d’une direction de la division vers une autre (sans hub DCIRE).
     */
    public Courrier enregistrerCourrierInterneDivision(Long origineDirectionId, Long cibleDirectionId, CourrierDTO dto)
            throws IOException {
        Entite origine = entiteRepository.findById(origineDirectionId)
                .orElseThrow(() -> new CourrierValidationException("Direction d'origine introuvable"));
        Entite cible = entiteRepository.findById(cibleDirectionId)
                .orElseThrow(() -> new CourrierValidationException("Direction cible introuvable"));
        if (origine.getType() != TypeEntite.DIRECTION || cible.getType() != TypeEntite.DIRECTION) {
            throw new CourrierValidationException("L'origine et la cible doivent être des directions.");
        }
        if (!estMembreDivisionDcire(origine) || !estMembreDivisionDcire(cible)) {
            throw new CourrierValidationException(
                    "Échange interne réservé aux structures ODC / Fondation / RSE / DCI. Hors de ce périmètre, passez par la DCIRE.");
        }

        CourrierValidator.ValidationResult validation = CourrierValidator.validateCourrierData(dto, dto.getFichier());
        if (!validation.isValid()) {
            throw new CourrierValidationException(validation.getErrorMessage());
        }
        String cheminFichier;
        try {
            cheminFichier = sauvegarderFichierSecurise(dto.getFichier());
        } catch (FileValidationException e) {
            throw new CourrierValidationException("Erreur de validation du fichier : " + e.getMessage(), e);
        } catch (IOException e) {
            throw new CourrierValidationException("Erreur lors de la sauvegarde du fichier : " + e.getMessage(), e);
        }

        Courrier courrier = new Courrier();
        courrier.setNumero(dto.getNumero());
        courrier.setObjet(dto.getObjet());
        courrier.setExpediteur(dto.getExpediteur());
        courrier.setStructureOrigine(origine);
        courrier.setDirectionInitial(cible);
        courrier.setEntite(cible);
        courrier.setFichier(cheminFichier);
        courrier.setStatut(StatutCourrier.ENVOYER);
        courrier.setDateReception(new Date());
        courrier.setDateLimite(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000));
        courrier.setDateRelance(new Date(System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000));
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(cible);
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.ENVOYER);
        historique.setCommentaire("Réception interne division — de : " + origine.getNom());
        historique.setDateAction(new Date());
        historique.setAncienneEntite(null);
        historique.setNouvelleEntite(cible);
        historiqueRepository.save(historique);

        return courrier;
    }

    private void notifierDirecteursOdcNouveauCourrier(Courrier courrier) {
        List<Utilisateur> directeurs = utilisateurRepository.findByRole_Nom("DIRECTEUR_ODC");
        if (directeurs == null || directeurs.isEmpty()) {
            return;
        }
        String lien = lienFrontendHash("directeur-odc/validation-courriers");
        String sujet = "[ODC Courrier] À valider : " + courrier.getNumero();
        String corps = "<p>Un nouveau courrier attend votre validation ou votre annulation.</p>"
                + "<p><strong>Objet :</strong> " + escapeHtmlCourrier(courrier.getObjet()) + "</p>"
                + "<p><a href=\"" + lien + "\">Accéder à votre espace</a></p>";
        String html = "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>";
        for (Utilisateur d : directeurs) {
            if (d.getEmail() != null && !d.getEmail().isBlank()) {
                try {
                    emailService.sendSimpleEmail(d.getEmail(), sujet, html);
                } catch (RuntimeException ex) {
                    log.warn("E-mail directeur ODC non envoyé (courrier id={}) : {}", courrier.getId(), ex.getMessage());
                }
            }
        }
    }

    private void notifierSuperAdminsRevisionCourrier(Courrier courrier) {
        Map<Long, Utilisateur> parId = new LinkedHashMap<>();
        for (Utilisateur u : utilisateurRepository.findByRole_Nom("SUPERADMIN")) {
            if (u != null && u.getId() != null) {
                parId.put(u.getId(), u);
            }
        }
        for (Utilisateur u : utilisateurRepository.findByRole_Nom("ADMIN")) {
            if (u != null && u.getId() != null) {
                parId.putIfAbsent(u.getId(), u);
            }
        }
        if (parId.isEmpty()) {
            return;
        }
        String lien = lienFrontendHash("courrier");
        String sujet = "[ODC Courrier] Corrections demandées : " + courrier.getNumero();
        String corps = "<p>Le directeur ODC a laissé des suggestions sur un courrier.</p>"
                + "<p><strong>Suggestions :</strong> " + escapeHtmlCourrier(courrier.getSuggestionDirecteur()) + "</p>"
                + "<p><a href=\"" + lien + "\">Ouvrir la gestion des courriers</a></p>";
        String html = "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>";
        for (Utilisateur a : parId.values()) {
            if (a.getEmail() != null && !a.getEmail().isBlank()) {
                emailService.sendSimpleEmail(a.getEmail(), sujet, html);
            }
        }
    }

    /** Lien vers une route Angular en mode hash (#/). */
    private String lienFrontendHash(String cheminSansSlashInitial) {
        String base = appFrontendBaseUrl != null ? appFrontendBaseUrl.trim() : "";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = cheminSansSlashInitial == null ? "" : cheminSansSlashInitial.replaceFirst("^/+", "");
        return base + "/#/" + path;
    }

    private static String escapeHtmlCourrier(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}