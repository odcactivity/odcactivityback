package com.odk.Service.Interface.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
import com.odk.Enum.DestinataireCourrierOdc;
import com.odk.Enum.StatutCourrier;
import com.odk.Repository.CourrierRepository;
import com.odk.Repository.EntiteOdcRepository;
import com.odk.Repository.HistoriqueCourrierRepository;
import com.odk.Repository.ReponseCourrierRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.dto.CourrierDTO;
import com.odk.dto.CourrierMetadonneesDTO;
import com.odk.validation.CourrierValidator;
import com.odk.validation.FileValidationUtil;
import com.odk.exception.CourrierValidationException;
import com.odk.exception.FileValidationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourrierService {

    private static final Logger log = LoggerFactory.getLogger(CourrierService.class);

    @Value("${app.frontend.base-url:https://odc-activite.com}")
    private String appFrontendBaseUrl;

    /**
     * Optionnel : ID de l’entité Direction « hub » DCIRE si son nom en base ne matche pas les heuristiques
     * ({@link #nomIndiqueDcire}). Sur Elastic Beanstalk : variable d’environnement ou entrée dans
     * application.properties, ex. {@code app.courrier.dcire-direction-id=42}. 0 = désactivé.
     */
    @Value("${app.courrier.dcire-direction-id:0}")
    private long configuredDcireDirectionId;

    private final CourrierRepository courrierRepository;
    private final EntiteOdcRepository entiteRepository;
    private final HistoriqueCourrierRepository historiqueRepository;
    private final ReponseCourrierRepository reponseCourrierRepository;
    private final EmailService emailService;
    private final UtilisateurRepository utilisateurRepository;
    private final UploadFileService uploadFileService;
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

        File fichier = resolveCourrierFichier(courrier.getFichier());
        if (fichier != null && fichier.exists() && fichier.isFile()) {
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
                    .header("Access-Control-Expose-Headers", "Content-Disposition")
                    .contentLength(fichier.length())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        }

        if (courrier.getFichier() != null && !courrier.getFichier().isBlank()) {
            byte[] s3Bytes = uploadFileService.getFileBytesFromS3(courrier.getFichier());
            if (s3Bytes != null && s3Bytes.length > 0) {
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

                InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(s3Bytes));
                String filename = courrier.getFichier();
                if (filename.contains("/")) {
                    filename = filename.substring(filename.lastIndexOf("/") + 1);
                }
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                        .header("Access-Control-Expose-Headers", "Content-Disposition")
                        .contentLength(s3Bytes.length)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);
            }
        }

        throw new CourrierValidationException("Fichier non trouvé pour ce courrier.");
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

    @Transactional
    public void supprimerCourrier(Long courrierId) {
        if (!courrierRepository.existsById(courrierId)) {
            throw new CourrierValidationException("Courrier introuvable.");
        }
        reponseCourrierRepository.deleteByCourrierId(courrierId);
        historiqueRepository.deleteByCourrierId(courrierId);
        courrierRepository.deleteById(courrierId);
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
        String folderName = "courriers";
        String savedFileName = uploadFileService.uploadFile(fichier, folderName);
        return folderName + "/" + savedFileName;
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

        String folderName = "courriers";
        String savedFileName = uploadFileService.uploadFile(fichier, folderName);
        return folderName + "/" + savedFileName;
    }

    /** Résout le chemin disque d'une pièce jointe (absolu, uploads/courriers, legacy images/). */
    private File resolveCourrierFichier(String cheminStocke) {
        if (cheminStocke == null || cheminStocke.isBlank()) {
            return null;
        }
        String trimmed = cheminStocke.trim();
        File direct = new File(trimmed);
        if (direct.isFile() && direct.exists()) {
            return direct;
        }
        File sousUploads = Paths.get(uploadDir, trimmed).toAbsolutePath().normalize().toFile();
        if (sousUploads.isFile() && sousUploads.exists()) {
            return sousUploads;
        }
        String fileName = Paths.get(trimmed).getFileName().toString();
        File parNom = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(fileName).toFile();
        if (parNom.isFile() && parNom.exists()) {
            return parNom;
        }
        File legacyImages = new File("images", fileName);
        if (legacyImages.isFile() && legacyImages.exists()) {
            return legacyImages;
        }
        File legacyImagesFull = new File("images/" + trimmed);
        if (legacyImagesFull.isFile() && legacyImagesFull.exists()) {
            return legacyImagesFull;
        }
        return null;
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

    /**
     * DCIRE : courriers émis par le hub ({@link Courrier#getStructureOrigine()}) ou détenus au hub / ses services.
     */
    @Transactional(readOnly = true)
    public List<Courrier> listerPourDcire() {
        return listerPourDcire(null);
    }

    @Transactional(readOnly = true)
    public List<Courrier> listerPourDcire(Long entiteId) {
        List<Courrier> base;
        Optional<Entite> dcireOpt = resolveDcireDirectionOptional();
        if (dcireOpt.isPresent()) {
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            ids.add(dcireOpt.get().getId());
            for (Entite division : listerCiblesEmissionDcire()) {
                if (division != null && division.getId() != null) {
                    ids.add(division.getId());
                }
            }
            if (ids.isEmpty()) {
                return List.of();
            }
            base = courrierRepository.findPourVueDcireDivision(ids);
        } else {
            base = courrierRepository.findAllOrderByDateReceptionDesc().stream()
                    .filter(c -> c != null && c.getDestinataireOdc() == DestinataireCourrierOdc.EXTERNE)
                    .toList();
        }
        List<Courrier> filtered = base.stream()
                .filter(c -> !estEmissionEmailDirectOdc(c))
                .toList();
        if (entiteId == null) {
            return filtered;
        }
        return filtered.stream()
                .filter(c -> courrierConcerneEntiteOuDescendants(c, entiteId))
                .toList();
    }

    /**
     * Courrier émis par le directeur ODC vers un email externe (hors circuit DCIRE).
     * Ne doit pas apparaître sur le dashboard DCIRE.
     */
    private boolean estEmissionEmailDirectOdc(Courrier c) {
        if (c == null || c.getDestinataireOdc() != DestinataireCourrierOdc.EXTERNE) {
            return false;
        }
        String precision = c.getExternePrecision();
        if (precision == null || !precision.contains("@")) {
            return false;
        }
        if (precision.toLowerCase(Locale.ROOT).startsWith("délégué par e-mail")) {
            return false;
        }
        return c.getStatut() == StatutCourrier.REPONDU
                && c.getStructureOrigine() != null
                && estDirectionOdc(c.getStructureOrigine());
    }

    private boolean courrierConcerneEntiteOuDescendants(Courrier c, Long entiteId) {
        if (c == null || entiteId == null) {
            return false;
        }
        if (c.getEntite() != null && entiteLiee(c.getEntite(), entiteId)) {
            return true;
        }
        if (c.getServiceOdcAffecte() != null && entiteLiee(c.getServiceOdcAffecte(), entiteId)) {
            return true;
        }
        if (c.getStructureOrigine() != null && entiteLiee(c.getStructureOrigine(), entiteId)) {
            return true;
        }
        return c.getDirectionInitial() != null && entiteLiee(c.getDirectionInitial(), entiteId);
    }

    private boolean entiteLiee(Entite e, Long cibleId) {
        if (e == null || cibleId == null) {
            return false;
        }
        if (cibleId.equals(e.getId())) {
            return true;
        }
        Entite cur = e.getParent();
        int guard = 0;
        while (cur != null && guard++ < 16) {
            if (cibleId.equals(cur.getId())) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    public List<Courrier> listerPourOdc(Long directionId, String vue) {
        Entite dir = entiteRepository.findById(directionId)
                .orElseThrow(() -> new CourrierValidationException("Direction ODC introuvable"));
        if (dir.getType() != TypeEntite.DIRECTION) {
            throw new CourrierValidationException("L'identifiant doit correspondre à une direction.");
        }
        if ("VALIDATION".equalsIgnoreCase(vue)) {
            List<Courrier> raw = courrierRepository.findEnAttenteValidationOdc(directionId, List.of(
                    StatutCourrier.ATTENTE_VALIDATION_ODC,
                    StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC,
                    StatutCourrier.EN_REVISION_ADMIN_COURRIER));
            return filtrerListePourPilierOdc(raw, directionId);
        }
        final List<StatutCourrier> statuts;
        if ("TOUS".equalsIgnoreCase(vue)) {
            statuts = Arrays.asList(StatutCourrier.values());
        } else if ("ARCHIVES".equalsIgnoreCase(vue)) {
            statuts = List.of(StatutCourrier.ARCHIVER);
        } else if ("REPONDUS".equalsIgnoreCase(vue)) {
            statuts = List.of(StatutCourrier.REPONDU);
        } else {
            statuts = List.of(
                    StatutCourrier.ENVOYER,
                    StatutCourrier.IMPUTER,
                    StatutCourrier.EN_COURS,
                    StatutCourrier.TRANSMIS_DCIRE,
                    StatutCourrier.REPONDU,
                    StatutCourrier.EN_REVISION_ADMIN_COURRIER,
                    StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_STRUCTURE,
                    StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC,
                    StatutCourrier.ATTENTE_VALIDATION_ODC);
        }
        return filtrerListePourPilierOdc(
                courrierRepository.findVisiblePourDirectionOdc(directionId, statuts), directionId);
    }

    private List<Courrier> filtrerListePourPilierOdc(List<Courrier> list, Long pilierOdcId) {
        return list.stream().filter(c -> visibleCourrierPourPilierOdc(c, pilierOdcId)).toList();
    }

    /**
     * Exclut les flux purement internes Fondation / RSE / DCI qui ne concernent pas ce pilier ODC.
     */
    private boolean visibleCourrierPourPilierOdc(Courrier c, Long pilierOdcId) {
        Entite so = c.getStructureOrigine();
        Entite ent = c.getEntite();
        if (so != null && Objects.equals(so.getId(), pilierOdcId)) {
            return true;
        }
        if (ent != null) {
            if (Objects.equals(ent.getId(), pilierOdcId)) {
                return true;
            }
            if (ent.getParent() != null && Objects.equals(ent.getParent().getId(), pilierOdcId)) {
                return true;
            }
        }
        if (so == null && c.getDirectionInitial() != null && Objects.equals(c.getDirectionInitial().getId(), pilierOdcId)) {
            return true;
        }
        if (so != null && estStructureDivisionHorsOdcDcire(so)) {
            return false;
        }
        return true;
    }

    private boolean estStructureDivisionHorsOdcDcire(Entite e) {
        if (e == null || nomIndiqueDcire(e)) {
            return false;
        }
        if (estDirectionOdc(e)) {
            return false;
        }
        return estMembreDivisionDcire(e);
    }

    /**
     * Directions autorisées pour un brouillon ODC (même règles que {@link #creerBrouillonOdc}).
     */
    public List<Entite> listerDirectionsOdcPourBrouillon() {
        List<Entite> list = entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(e -> !nomIndiqueDcire(e))
                .filter(this::estDirectionOdc)
                .toList();
        // Fallback: si l’heuristique de nommage ne détecte rien, on évite une liste vide.
        if (list == null || list.isEmpty()) {
            return entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                    .filter(e -> !nomIndiqueDcire(e))
                    .toList();
        }
        return list;
    }

    public Courrier creerBrouillonOdc(Long odcDirectionId, CourrierDTO dto) throws IOException {
        throw new CourrierValidationException(
                "Seule la DCIRE émet des courriers. Utilisez POST /api/courriers/dcire/emission.");
    }

    /**
     * Cibles internes pour un brouillon / formulaire ODC (hors direction d’origine).
     */
    public List<Entite> listerDirectionsCiblesInternesPourOdc(Long origineDirectionId) {
        Entite origine = entiteRepository.findById(origineDirectionId)
                .orElseThrow(() -> new CourrierValidationException("Direction d'origine introuvable."));
        if (origine.getType() != TypeEntite.DIRECTION) {
            throw new CourrierValidationException("L'origine doit être une direction.");
        }
        return entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(e -> !nomIndiqueDcire(e))
                .filter(this::estMembreDivisionDcire)
                .filter(e -> !Objects.equals(e.getId(), origine.getId()))
                .sorted(Comparator.comparing(e -> normalizeNomEntite(e.getNom()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Destinataires division (Fondation, RSE, DCI, piliers ODC) — émission réservée à la DCIRE. */
    public List<Entite> listerCiblesEmissionDcire() {
        return entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(e -> !nomIndiqueDcire(e))
                .filter(this::estMembreDivisionDcire)
                .sorted(Comparator.comparing(e -> normalizeNomEntite(e.getNom()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Émission d'un courrier par la DCIRE vers une structure de la division.
     * <ul>
     *   <li><b>ODC</b> : workflow produit ODCActivité (responsable ODK, affectation service, etc.).</li>
     *   <li><b>Fondation / RSE / DCI</b> : réception directe ({@link StatutCourrier#ENVOYER}) — reçu + réponse uniquement, sans validation type ODC.</li>
     * </ul>
     */
    @Transactional
    public Courrier emettreCourrierParDcire(Long cibleDirectionId, CourrierDTO dto) throws IOException {
        Entite dcire = resolveDcireDirectionOptional()
                .orElseThrow(() -> new CourrierValidationException("Direction DCIRE introuvable en base."));
        Entite cible = entiteRepository.findById(cibleDirectionId)
                .orElseThrow(() -> new CourrierValidationException("Direction destinataire introuvable."));
        if (cible.getType() != TypeEntite.DIRECTION) {
            throw new CourrierValidationException("La cible doit être une direction de la division.");
        }
        if (nomIndiqueDcire(cible) || !estMembreDivisionDcire(cible)) {
            throw new CourrierValidationException("Destinataire invalide pour une émission DCIRE.");
        }

        if (dto.getDirectionId() == null) {
            dto.setDirectionId(cibleDirectionId);
        }
        if (dto.getNumero() == null || dto.getNumero().isBlank()) {
            dto.setNumero("DCIRE-" + System.currentTimeMillis());
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
        if (dto.getExternePrecision() != null && !dto.getExternePrecision().isBlank()) {
            courrier.setExternePrecision(dto.getExternePrecision().trim());
        }
        courrier.setExpediteur("KEÏTA DCIRE");
        courrier.setStructureOrigine(dcire);
        courrier.setDirectionInitial(cible);
        courrier.setCibleInterneDirection(cible);
        courrier.setFichier(cheminFichier);
        courrier.setDateReception(new Date());
        courrier.setDateLimite(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000));
        courrier.setDateRelance(new Date(System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000));

        if (estDirectionOdc(cible)) {
            courrier.setEntite(cible);
            courrier.setStatut(StatutCourrier.ENVOYER);
            courrierRepository.save(courrier);
            enregistrerHistoriqueCourrier(courrier, cible, null, StatutCourrier.ENVOYER,
                    "Émis par la DCIRE (expédition KEÏTA) — remis au directeur ODC",
                    dcire, cible);
            notifierDirecteursOdcNouveauCourrier(courrier);
        } else {
            // Fondation / RSE / DCI : pas de workflow validation ODC — courrier immédiatement « reçu » sur la structure.
            courrier.setEntite(cible);
            courrier.setStatut(StatutCourrier.ENVOYER);
            courrierRepository.save(courrier);
            enregistrerHistoriqueCourrier(courrier, cible, null, StatutCourrier.ENVOYER,
                    "Émis par la DCIRE — reçu sur la structure destinataire (réponse possible)",
                    dcire, cible);
            notifierDirecteursStructureReception(courrier, destinataireStructureDepuisDirection(cible));
        }
        return courrier;
    }

    public List<Courrier> listerCourriersEnAttenteResponsableOdk() {
        return courrierRepository.findByStatutOrderByDateReceptionDesc(
                StatutCourrier.ATTENTE_TRAITEMENT_RESPONSABLE_ODK);
    }

    /**
     * Responsable ODK : récupérer les courriers délégués par le directeur ODC (préparation hors application).
     * Aucun droit de répondre ici : la réponse officielle est saisie par le directeur ODC.
     */
    public List<Courrier> listerCourriersDeleguesResponsableOdk() {
        return courrierRepository.findByDelegueResponsableOdkTrueOrderByDateReceptionDesc();
    }

    /** Courriers délégués au responsable connecté (par entité ou flag ODK). */
    public List<Courrier> listerCourriersDeleguesPourResponsableEntite(Utilisateur responsable) {
        ResponsableEntiteSupport.assertRoleResponsableEntite(responsable);
        if (ResponsableEntiteSupport.ROLE_ODK.equals(ResponsableEntiteSupport.roleNom(responsable))) {
            List<Courrier> odk = listerCourriersDeleguesResponsableOdk();
            List<Long> entiteIds = resoudreEntiteIdsCourrierPourResponsable(responsable);
            if (entiteIds.isEmpty()) {
                return odk;
            }
            List<Courrier> service = courrierRepository.findDeleguesPourEntites(
                    entiteIds, StatutCourrier.ARCHIVER);
            LinkedHashSet<Long> seen = new LinkedHashSet<>();
            List<Courrier> merged = new ArrayList<>();
            for (Courrier c : odk) {
                if (c != null && c.getId() != null && seen.add(c.getId())) {
                    merged.add(c);
                }
            }
            for (Courrier c : service) {
                if (c != null && c.getId() != null && seen.add(c.getId())) {
                    merged.add(c);
                }
            }
            return merged;
        }
        List<Long> entiteIds = resoudreEntiteIdsCourrierPourResponsable(responsable);
        if (entiteIds.isEmpty()) {
            return List.of();
        }
        return courrierRepository.findDeleguesPourEntites(entiteIds, StatutCourrier.ARCHIVER);
    }

    public List<Courrier> listerCourriersArchivesPourResponsableEntite(Utilisateur responsable) {
        ResponsableEntiteSupport.assertRoleResponsableEntite(responsable);
        List<Long> entiteIds = resoudreEntiteIdsCourrierPourResponsable(responsable);
        if (entiteIds.isEmpty()) {
            return List.of();
        }
        return courrierRepository.findArchivesPourEntites(entiteIds, StatutCourrier.ARCHIVER);
    }

    @Transactional
    public void archiverCourrierParResponsableEntite(Long courrierId, Utilisateur responsable, MultipartFile fichierArchive) throws IOException {
        ResponsableEntiteSupport.assertRoleResponsableEntite(responsable);
        Courrier courrier = getCourrier(courrierId);
        List<Entite> entitesRole = listerEntitesPourRole(ResponsableEntiteSupport.roleNom(responsable));
        if (!ResponsableEntiteSupport.utilisateurPeutArchiverCourrier(courrier, responsable, entitesRole)
                && !(ResponsableEntiteSupport.ROLE_ODK.equals(ResponsableEntiteSupport.roleNom(responsable))
                && courrier.isDelegueResponsableOdk())) {
            throw new CourrierValidationException(
                    "Vous ne pouvez archiver que les courriers délégués à votre entité.");
        }
        // Sauvegarder le fichier d'archive si fourni
        if (fichierArchive != null && !fichierArchive.isEmpty()) {
            String cheminArchive = sauvegarderFichier(fichierArchive);
            courrier.setFichierArchive(cheminArchive);
        }
        archiverCourrier(courrierId, responsable);
    }

    /**
     * Télécharger le fichier déposé lors de l'archivage d'un courrier.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> telechargerFichierArchive(Long courrierId) throws IOException {
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getFichierArchive() == null || courrier.getFichierArchive().isBlank()) {
            throw new CourrierValidationException("Aucun fichier d'archive pour ce courrier.");
        }
        File fichier = new File(courrier.getFichierArchive());
        if (!fichier.exists()) {
            throw new CourrierValidationException("Fichier d'archive introuvable sur le serveur.");
        }
        String nomAffichage = Paths.get(fichier.getName()).getFileName().toString();
        InputStreamResource resource = new InputStreamResource(new FileInputStream(fichier));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomAffichage + "\"")
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .contentLength(fichier.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private List<Long> resoudreEntiteIdsCourrierPourResponsable(Utilisateur responsable) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (responsable.getEntite() != null && responsable.getEntite().getId() != null) {
            ids.add(responsable.getEntite().getId());
        }
        for (Entite e : listerEntitesPourRole(ResponsableEntiteSupport.roleNom(responsable))) {
            if (e != null && e.getId() != null) {
                ids.add(e.getId());
            }
        }
        return List.copyOf(ids);
    }

    private List<Entite> listerEntitesPourRole(String roleNom) {
        return entiteRepository.findAll().stream()
                .filter(e -> ResponsableEntiteSupport.entiteCorrespondAuRole(e, roleNom))
                .toList();
    }

    /** Services Orange Digital Center : Kalanso, FabLab, Multimedia, Orange Fab. */
    public List<Entite> listerServicesOdcPourResponsable() {
        List<Entite> services = entiteRepository.findByType(TypeEntite.SERVICE).stream()
                .filter(this::estServiceOdcDivision)
                .sorted(Comparator.comparing(e -> normalizeNomEntite(e.getNom()), String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (!services.isEmpty()) {
            return services;
        }
        return entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(this::estDirectionOdc)
                .filter(e -> !nomIndiqueDcire(e))
                .sorted(Comparator.comparing(e -> normalizeNomEntite(e.getNom()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public Courrier affecterCourrierAuServiceParResponsable(Long courrierId, Long serviceEntiteId, String note)
            throws IOException {
        throw new CourrierValidationException(
                "Le responsable ODK ne traite plus les courriers dans l'application. "
                        + "Le directeur ODC délègue physiquement puis saisit la réponse officielle.");
    }

    /** Courrier reçu au hub DCIRE (pas émis par la DCIRE). */
    public boolean estCourrierRecuAuHubDcire(Courrier courrier) {
        if (courrier == null) {
            return false;
        }
        Optional<Entite> dcireOpt = resolveDcireDirectionOptional();
        if (dcireOpt.isEmpty()) {
            return false;
        }
        Long dcireId = dcireOpt.get().getId();
        if (courrier.getStructureOrigine() != null
                && Objects.equals(dcireId, courrier.getStructureOrigine().getId())) {
            return false;
        }
        if (courrier.getEntite() != null && Objects.equals(dcireId, courrier.getEntite().getId())) {
            return true;
        }
        return courrier.getStatut() == StatutCourrier.TRANSMIS_DCIRE
                && courrier.getEntite() != null
                && Objects.equals(dcireId, courrier.getEntite().getId());
    }

    public boolean requiertValidationReponseDirecteurOdc(Courrier courrier) {
        if (courrier == null) {
            return false;
        }
        if (estCourrierEmissionDcireVersOdc(courrier)) {
            return true;
        }
        if (courrier.getStructureOrigine() != null && nomIndiqueDcire(courrier.getStructureOrigine())) {
            return false;
        }
        if (courrier.getServiceOdcAffecte() != null) {
            return true;
        }
        if (courrier.getEntite() != null && estDirectionOdc(courrier.getEntite())) {
            return true;
        }
        if (courrier.getEntite() != null && estServiceOdcDivision(courrier.getEntite())) {
            return true;
        }
        Entite cible = courrier.getCibleInterneDirection();
        return cible != null && estDirectionOdc(cible);
    }

    /**
     * Finalise une réponse saisie par le directeur ODC (décharge DCIRE ou retour direct si flux interne).
     */
    @Transactional
    public Courrier appliquerReponseDirecteurOdcValidee(Long courrierId) {
        return appliquerReponseDirecteurOdcValidee(courrierId, null);
    }

    @Transactional
    public Courrier appliquerReponseDirecteurOdcValidee(Long courrierId, Utilisateur auteur) {
        Courrier courrier = getCourrier(courrierId);
        if (auteur != null) {
            courrier.setUtilisateurAffecte(auteur);
            if (auteur.getEntite() != null) {
                courrier.setServiceOdcAffecte(auteur.getEntite());
            }
        }
        reponseCourrierRepository.findReponsesByCourrierId(courrierId).stream()
                .filter(r -> !Boolean.TRUE.equals(r.getValideeDirecteurOdc()))
                .forEach(r -> {
                    r.setValideeDirecteurOdc(true);
                    r.setStatut(StatutCourrier.REPONDU);
                    reponseCourrierRepository.save(r);
                });
        
        String nomAuteur = (auteur != null) ? (auteur.getPrenom() + " " + auteur.getNom()) : "directeur ODC";

        if (requiertDechargeDcirePourReponse(courrier)) {
            Entite dcire = resolveDcireDirectionOptional()
                    .orElseThrow(() -> new CourrierValidationException("Direction DCIRE introuvable."));
            courrier.setEntite(dcire);
            courrier.setStatut(StatutCourrier.TRANSMIS_DCIRE);
            courrierRepository.save(courrier);
            enregistrerHistoriqueCourrier(courrier, dcire, auteur, StatutCourrier.TRANSMIS_DCIRE,
                    "Réponse " + nomAuteur + " — transmission à la DCIRE pour décharge / scan avant envoi",
                    courrier.getDirectionInitial(), dcire);
            notifierDirecteursDcireDechargeReponse(courrier, dcire);
            return courrier;
        }
        courrier.setStatut(StatutCourrier.REPONDU);
        Entite dcireHub = resolveDcireDirectionOptional().orElse(null);
        if (dcireHub != null) {
            courrier.setEntite(dcireHub);
        }
        courrierRepository.save(courrier);
        enregistrerHistoriqueCourrier(courrier, dcireHub, auteur, StatutCourrier.REPONDU,
                "Réponse " + nomAuteur + " — retour à la DCIRE",
                courrier.getDirectionInitial(), dcireHub);
        notifierDirecteursDcireHub(courrier, courrier.getEntite());
        return courrier;
    }

    @Transactional
    public Courrier validerReponseParDirecteurOdc(Long courrierId, String suggestion) {
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.ATTENTE_VALIDATION_REPONSE_DIRECTEUR_ODC) {
            throw new CourrierValidationException("Aucune réponse en attente de validation directeur ODC.");
        }
        if (suggestion != null && !suggestion.isBlank()) {
            courrier.setSuggestionDirecteur(suggestion.trim());
            Entite odc = courrier.getDirectionInitial();
            if (odc == null || !estDirectionOdc(odc)) {
                odc = entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                        .filter(this::estDirectionOdc)
                        .filter(e -> !nomIndiqueDcire(e))
                        .findFirst()
                        .orElse(courrier.getEntite());
            }
            courrier.setEntite(odc);
            courrier.setStatut(StatutCourrier.ENVOYER);
            courrierRepository.save(courrier);
            historiqueSimple(courrier, odc, StatutCourrier.ENVOYER,
                    "Note directeur ODC (révision hors application) : " + suggestion.trim());
            return courrier;
        }
        return appliquerReponseDirecteurOdcValidee(courrierId);
    }

    /** Courrier émis par la DCIRE vers une direction ODC (hub division → pilier ODC). */
    public boolean estCourrierEmissionDcireVersOdc(Courrier courrier) {
        if (courrier == null || courrier.getStructureOrigine() == null || courrier.getDirectionInitial() == null) {
            return false;
        }
        Optional<Entite> dcireOpt = resolveDcireDirectionOptional();
        if (dcireOpt.isEmpty()) {
            return false;
        }
        if (!Objects.equals(dcireOpt.get().getId(), courrier.getStructureOrigine().getId())) {
            return false;
        }
        return estDirectionOdc(courrier.getDirectionInitial());
    }

    /**
     * Courrier émis par la DCIRE (KEÏTA) vers l'ODC : réponse sans circuit décharge / scan.
     */
    public boolean estCourrierFluxInterneDcireVersOdc(Courrier courrier) {
        if (!estCourrierEmissionDcireVersOdc(courrier)) {
            return false;
        }
        String exp = courrier.getExpediteur() != null
                ? courrier.getExpediteur().toUpperCase(Locale.ROOT)
                : "";
        return exp.contains("KEÏTA") || exp.contains("DCIRE");
    }

    /** Réponse ODC devant passer par la DCIRE (décharge) avant envoi physique. */
    public boolean requiertDechargeDcirePourReponse(Courrier courrier) {
        return !estCourrierFluxInterneDcireVersOdc(courrier);
    }

    @Transactional
    public Courrier deleguerCourrierAuServiceParDirecteurOdc(Long courrierId, Long serviceEntiteId, String note) {
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.ENVOYER && courrier.getStatut() != StatutCourrier.IMPUTER) {
            throw new CourrierValidationException(
                    "Seuls les courriers reçus au directeur ODC peuvent être délégués à une entité.");
        }
        Entite service = entiteRepository.findById(serviceEntiteId)
                .orElseThrow(() -> new CourrierValidationException("Entité ODC introuvable."));
        if (!estServiceOdcDivision(service) && !estDirectionOdc(service)) {
            throw new CourrierValidationException("Entité non reconnue dans la division Orange Digital Center.");
        }
        Entite ancienne = courrier.getEntite();
        courrier.setServiceOdcAffecte(service);
        courrier.setEntite(service);
        if (note != null && !note.isBlank()) {
            courrier.setNoteResponsableOdk(note.trim());
        }
        courrier.setStatut(StatutCourrier.IMPUTER);
        courrierRepository.save(courrier);
        historiqueSimple(courrier, service, StatutCourrier.IMPUTER,
                "Délégué par le directeur ODC à : " + service.getNom()
                        + (note != null && !note.isBlank() ? " — " + note.trim() : ""));
        notifierEntiteResponsable(courrier, service);
        return courrier;
    }

    /** Directeur ODC : délégation fixe au responsable ODK (préparation Word / papier hors application). */
    @Transactional
    public Courrier deleguerAuResponsableOdkParDirecteurOdc(Long courrierId, String note) {
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.ENVOYER && courrier.getStatut() != StatutCourrier.IMPUTER) {
            throw new CourrierValidationException("Ce courrier n'est pas dans une étape déléguable.");
        }
        // Marquer la délégation “physique”
        courrier.setDelegueResponsableOdk(true);
        if (note != null && !note.isBlank()) {
            courrier.setNoteResponsableOdk(note.trim());
        }
        courrierRepository.save(courrier);
        historiqueSimple(courrier, courrier.getEntite(), courrier.getStatut(),
                "Délégué au responsable ODK (préparation hors application)"
                        + (note != null && !note.isBlank() ? " — " + note.trim() : ""));
        notifierResponsablesOdkNouveauCourrier(courrier);
        return courrier;
    }

    @Transactional
    public Courrier deleguerCourrierEmailParDirecteurStructure(Long courrierId, String email, String note, Utilisateur u) {
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.ENVOYER && courrier.getStatut() != StatutCourrier.IMPUTER && courrier.getStatut() != StatutCourrier.EN_COURS) {
            throw new CourrierValidationException("Ce courrier ne peut pas être délégué.");
        }
        if (courrier.getExternePrecision() != null
                && courrier.getExternePrecision().trim().startsWith("Délégué par e-mail")) {
            throw new CourrierValidationException("Ce courrier a déjà été délégué par e-mail.");
        }
        
        courrier.setStatut(StatutCourrier.IMPUTER);
        if (note != null && !note.isBlank()) {
            courrier.setNoteResponsableOdk(note.trim());
        }
        
        courrier.setExternePrecision("Délégué par e-mail à: " + email.trim());
        courrierRepository.save(courrier);
        
        Entite structure = u.getEntite() != null && u.getEntite().getId() != null
                ? entiteRepository.findById(u.getEntite().getId()).orElse(u.getEntite())
                : courrier.getEntite();
        enregistrerHistoriqueCourrier(courrier, structure, u, StatutCourrier.IMPUTER,
                "Courrier délégué par e-mail à : " + email.trim()
                        + (note != null && !note.isBlank() ? " — Note : " + note.trim() : ""),
                structure, null);
                
        try {
            Entite direction = u.getEntite() != null && u.getEntite().getId() != null
                    ? entiteRepository.findById(u.getEntite().getId()).orElse(u.getEntite())
                    : null;
            DivisionMarque marque = marquePourDirection(direction);
            String structureNom = direction != null && direction.getNom() != null
                    ? direction.getNom().trim()
                    : "votre direction";
            String sujet = "Délégation de courrier: " + courrier.getObjet();
            StringBuilder corps = new StringBuilder();
            corps.append("Bonjour,<br/><br/>");
            corps.append("Le directeur de <strong>").append(structureNom).append("</strong> vous a délégué le courrier suivant :<br/><br/>");
            corps.append("<ul>");
            corps.append("<li><strong>Numéro :</strong> ").append(courrier.getNumero()).append("</li>");
            corps.append("<li><strong>Objet :</strong> ").append(courrier.getObjet()).append("</li>");
            corps.append("<li><strong>Expéditeur :</strong> ").append(courrier.getExpediteur()).append("</li>");
            corps.append("</ul>");
            if (note != null && !note.isBlank()) {
                corps.append("<br/><strong>Note du directeur :</strong><br/>");
                corps.append("<p style='white-space: pre-wrap;'>").append(note.trim()).append("</p>");
            }
            corps.append("<br/><br/>").append(marque.signatureHtml());
            corps.append("<br/><span style='font-size:0.85em;color:#666;'>").append(marque.piedPage()).append("</span>");
            
            java.io.File file = resolveCourrierFichier(courrier.getFichier());
            boolean isTemp = false;
            if (file == null && courrier.getFichier() != null && !courrier.getFichier().isBlank()) {
                byte[] s3Bytes = uploadFileService.getFileBytesFromS3(courrier.getFichier());
                if (s3Bytes != null && s3Bytes.length > 0) {
                    String filename = courrier.getFichier();
                    if (filename.contains("/")) {
                        filename = filename.substring(filename.lastIndexOf("/") + 1);
                    }
                    java.io.File tempFile = java.io.File.createTempFile("s3-attach-", filename);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                        fos.write(s3Bytes);
                    }
                    file = tempFile;
                    isTemp = true;
                }
            }
            if (file != null) {
                emailService.sendEmailWithAttachments(email.trim(), sujet, corps.toString(), java.util.List.of(file), u.getPrenom(), u.getEmail());
                if (isTemp) {
                    try {
                        file.delete();
                    } catch (Exception ex) {}
                }
            } else {
                emailService.sendSimpleEmail(email.trim(), sujet, corps.toString(), u.getPrenom(), u.getEmail());
            }
        } catch (Exception e) {
            System.err.println("Échec de l'envoi de l'email de délégation: " + e.getMessage());
        }
        
        return courrier;
    }

    /** DCIRE : décharge / scan validés — retour du courrier-réponse à l'ODC pour envoi physique. */
    @Transactional
    public Courrier validerDechargeReponseParDcire(Long courrierId, Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        if (u.getRole() == null || !"DIRECTEUR".equalsIgnoreCase(u.getRole().getNom().trim())) {
            throw new CourrierValidationException("Action réservée au directeur DCIRE.");
        }
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.TRANSMIS_DCIRE) {
            throw new CourrierValidationException("Ce courrier n'est pas en attente de décharge à la DCIRE.");
        }
        Entite odc = courrier.getDirectionInitial();
        if (odc == null || !estDirectionOdc(odc)) {
            odc = entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                    .filter(this::estDirectionOdc)
                    .filter(e -> !nomIndiqueDcire(e))
                    .findFirst()
                    .orElseThrow(() -> new CourrierValidationException("Direction ODC introuvable pour le retour."));
        }
        Entite ancienne = courrier.getEntite();
        courrier.setEntite(odc);
        courrier.setStatut(StatutCourrier.ENVOYER);
        courrierRepository.save(courrier);
        historiqueSimple(courrier, odc, StatutCourrier.ENVOYER,
                "DCIRE : décharge validée / scan enregistré — retour ODC pour envoi physique");
        notifierDirecteursOdcNouveauCourrier(courrier);
        return courrier;
    }

    /** Directeur ODC : envoi physique effectué (expéditeur externe géré hors application). */
    @Transactional
    public Courrier confirmerEnvoiPhysiqueParDirecteurOdc(Long courrierId) {
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.ENVOYER && courrier.getStatut() != StatutCourrier.REPONDU) {
            throw new CourrierValidationException("Ce courrier n'est pas prêt pour clôture d'envoi physique.");
        }
        courrier.setStatut(StatutCourrier.ARCHIVER);
        courrier.setDateArchivage(new Date());
        courrierRepository.save(courrier);
        historiqueSimple(courrier, courrier.getEntite(), StatutCourrier.ARCHIVER,
                "Directeur ODC : envoi physique confirmé (expéditeur externe hors application)");
        return courrier;
    }

    private void notifierDirecteursDcireDechargeReponse(Courrier courrier, Entite dcire) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (dcire != null && dcire.getResponsable() != null && dcire.getResponsable().getEmail() != null) {
            emails.add(dcire.getResponsable().getEmail().trim());
        }
        for (Utilisateur u : utilisateurRepository.findByRole_Nom("DIRECTEUR")) {
            if (u.getEmail() != null && !u.getEmail().isBlank()) {
                emails.add(u.getEmail().trim());
            }
        }
        if (emails.isEmpty()) {
            return;
        }
        String lien = lienFrontendHash("courrier");
        String sujet = "[Courrier] Décharge / scan requis — " + courrier.getNumero();
        String corps = "<p>L'ODC a validé une réponse. Merci d'enregistrer la décharge et le scan avant retour ODC.</p>"
                + "<p><strong>Objet :</strong> " + escapeHtmlCourrier(courrier.getObjet()) + "</p>"
                + "<p><a href=\"" + lien + "\">Ouvrir</a></p>";
        String html = "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>";
        for (String em : emails) {
            try {
                emailService.sendSimpleEmail(em, sujet, html);
            } catch (RuntimeException ex) {
                log.warn("E-mail décharge DCIRE non envoyé (courrier id={}) : {}", courrier.getId(), ex.getMessage());
            }
        }
    }

    public List<Courrier> listerCourriersReponseEnAttenteDirecteurOdc() {
        return courrierRepository.findByStatutOrderByDateReceptionDesc(
                StatutCourrier.ATTENTE_VALIDATION_REPONSE_DIRECTEUR_ODC);
    }

    private void historiqueSimple(Courrier courrier, Entite entite, StatutCourrier statut, String commentaire) {
        enregistrerHistoriqueCourrier(courrier, entite, null, statut, commentaire,
                courrier.getEntite(), entite);
    }

    private void enregistrerHistoriqueCourrier(
            Courrier courrier,
            Entite entite,
            Utilisateur utilisateur,
            StatutCourrier statut,
            String commentaire,
            Entite ancienneEntite,
            Entite nouvelleEntite
    ) {
        HistoriqueCourrier h = new HistoriqueCourrier();
        h.setCourrier(courrier);
        h.setEntite(entite);
        h.setUtilisateur(utilisateur);
        h.setStatut(statut);
        h.setCommentaire(commentaire);
        h.setDateAction(new Date());
        h.setAncienneEntite(ancienneEntite);
        h.setNouvelleEntite(nouvelleEntite);
        historiqueRepository.save(h);
    }

    private void notifierEntiteResponsable(Courrier courrier, Entite entite) {
        if (entite == null) {
            return;
        }
        String roleCible = ResponsableEntiteSupport.rolePourEntiteActivite(entite);
        String lien = buildFrontendUrl(ResponsableEntiteSupport.cheminDashboardFrontend(roleCible) + "/courriers");
        String sujet = "[ODC Courrier] Courrier délégué : " + courrier.getObjet();
        String corps = "<p>Un courrier vous a été délégué par le directeur ODC (objet : "
                + courrier.getObjet() + ").</p>"
                + "<p><a href=\"" + lien + "\">Ouvrir vos courriers délégués</a></p>";
        String html = "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>";
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (entite.getResponsable() != null && entite.getResponsable().getEmail() != null) {
            emails.add(entite.getResponsable().getEmail().trim());
        }
        for (Utilisateur u : utilisateurRepository.findByRole_Nom(roleCible)) {
            if (u.getEmail() != null && !u.getEmail().isBlank()) {
                emails.add(u.getEmail().trim());
            }
        }
        for (String em : emails) {
            emailService.sendSimpleEmail(em, sujet, html);
        }
    }

    private void notifierResponsablesOdkNouveauCourrier(Courrier courrier) {
        List<Utilisateur> responsables = utilisateurRepository.findByRole_Nom("RESPONSABLE_ODK");
        String lien = buildFrontendUrl("/responsable-odk/dashboard");
        String sujet = "[ODC Courrier] À traiter (responsable ODK) : " + courrier.getObjet();
        String corps = "<p>Un courrier émis par la DCIRE attend votre affectation vers un service ODC.</p>"
                + "<p><a href=\"" + lien + "\">Ouvrir le tableau de bord</a></p>";
        for (Utilisateur u : responsables) {
            if (u.getEmail() != null && !u.getEmail().isBlank()) {
                emailService.sendSimpleEmail(u.getEmail(), sujet,
                        "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>");
            }
        }
    }

    private boolean estServiceOdcDivision(Entite e) {
        if (e == null) {
            return false;
        }
        String n = normalizeNomEntite(e.getNom());
        return n.contains("KALANSO")
                || n.contains("FABLAB")
                || n.contains("FAB LAB")
                || n.contains("MULTIMEDIA")
                || n.contains("ORANGE FAB")
                || (n.contains("FAB") && !n.contains("FABLAB"));
    }

    private String buildFrontendUrl(String path) {
        String base = appFrontendBaseUrl == null ? "" : appFrontendBaseUrl.trim().replaceAll("/+$", "");
        String p = path == null ? "" : path.replaceFirst("^/+", "");
        return base + "/#/" + p;
    }

    private boolean estUtilisateurConnecteDirecteurOdc() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Utilisateur u)) {
            return false;
        }
        if (u.getRole() == null || u.getRole().getNom() == null) {
            return false;
        }
        return "DIRECTEUR_ODC".equalsIgnoreCase(u.getRole().getNom().trim());
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
        courrier.setStatut(StatutCourrier.ATTENTE_VALIDATION_ODC);
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(courrier.getEntite());
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.ATTENTE_VALIDATION_ODC);
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
            throw new CourrierValidationException(
                    "Ce courrier n'est pas en attente de validation par le directeur ODC avant transmission.");
        }
        DestinataireCourrierOdc dest =
                courrier.getDestinataireOdc() != null ? courrier.getDestinataireOdc() : DestinataireCourrierOdc.EXTERNE;
        Entite ancienne = courrier.getEntite();

        if (dest == DestinataireCourrierOdc.EXTERNE) {
            Entite dcire = resolveDcireDirectionOptional().orElse(null);
            if (dcire != null) {
                courrier.setEntite(dcire);
            }
            courrier.setStatut(StatutCourrier.TRANSMIS_DCIRE);
            courrierRepository.save(courrier);

            HistoriqueCourrier historique = new HistoriqueCourrier();
            historique.setCourrier(courrier);
            historique.setEntite(dcire);
            historique.setUtilisateur(null);
            historique.setStatut(StatutCourrier.TRANSMIS_DCIRE);
            historique.setCommentaire("Validé côté ODC — transmission à la DCIRE (destinataire externe / hors division)");
            historique.setDateAction(new Date());
            historique.setAncienneEntite(ancienne);
            historique.setNouvelleEntite(dcire);
            historiqueRepository.save(historique);
            notifierDirecteursDcireHub(courrier, dcire);
            return courrier;
        }

        Entite cible = resolveDirectionDivisionCible(dest);
        courrier.setEntite(cible);
        courrier.setStatut(StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_STRUCTURE);
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(cible);
        historique.setUtilisateur(null);
        historique.setStatut(StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_STRUCTURE);
        historique.setCommentaire("Validé côté ODC — transmission interne vers : " + cible.getNom());
        historique.setDateAction(new Date());
        historique.setAncienneEntite(ancienne);
        historique.setNouvelleEntite(cible);
        historiqueRepository.save(historique);

        notifierDirecteursStructure(courrier, dest);
        return courrier;
    }

    /**
     * Liste des courriers en attente de validation par le ou la directeur·rice de la direction connectée.
     */
    public List<Courrier> listerPourValidationDirecteurStructure(Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        if (u.getEntite() == null || u.getEntite().getId() == null) {
            return List.of();
        }
        return courrierRepository.findByEntiteIdAndStatut(
                u.getEntite().getId(), StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_STRUCTURE);
    }

    /** Courriers émis par la direction de l'utilisateur (tous statuts, y compris archivés). */
    public List<Courrier> listerEmisPourMaStructure(Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        if (u.getEntite() == null || u.getEntite().getId() == null) {
            return List.of();
        }
        return courrierRepository.findByStructureOrigineIdOrderByDateReceptionDesc(u.getEntite().getId());
    }

    /** Vue exhaustive : tout courrier lié à la direction (origine, détention ou service rattaché). */
    public List<Courrier> listerToutPourMaStructure(Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        if (u.getEntite() == null || u.getEntite().getId() == null) {
            return List.of();
        }
        return courrierRepository.findTousVisiblesPourDirection(u.getEntite().getId());
    }

    public Map<String, List<Courrier>> tableauStructureCourriers(Utilisateur u) {
        Map<String, List<Courrier>> m = new LinkedHashMap<>();
        m.put("enAttenteValidation", listerPourValidationDirecteurStructure(u));
        m.put("recus", listerRecusOperationnelsMaStructure(u));
        m.put("emis", listerEmisPourMaStructure(u));
        m.put("archives", listerArchivesPourMaStructure(u));
        m.put("tout", listerToutPourMaStructure(u));
        return m;
    }

    public List<Courrier> listerArchivesPourMaStructure(Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        if (u.getEntite() == null || u.getEntite().getId() == null) {
            return List.of();
        }
        return courrierRepository.findTousVisiblesPourDirection(u.getEntite().getId()).stream()
                .filter(c -> c.getStatut() == StatutCourrier.ARCHIVER)
                .toList();
    }

    public List<Entite> listerDirectionsCiblesInternesPourStructure(Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        if (u.getEntite() == null || u.getEntite().getId() == null) {
            return List.of();
        }
        Long monId = u.getEntite().getId();
        return entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(e -> !nomIndiqueDcire(e))
                .filter(this::estMembreDivisionDcire)
                .filter(e -> !Objects.equals(e.getId(), monId))
                .toList();
    }

    public Courrier enregistrerCourrierInterneDepuisMaStructure(
            Long cibleDirectionId, CourrierDTO dto, Utilisateur principal) throws IOException {
        throw new CourrierValidationException(
                "Seule la DCIRE émet des courriers. Les structures reçoivent et répondent uniquement.");
    }

    @SuppressWarnings("unused")
    private Courrier enregistrerCourrierInterneDepuisMaStructureLegacy(
            Long cibleDirectionId, CourrierDTO dto, Utilisateur principal) throws IOException {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        if (u.getEntite() == null || u.getEntite().getId() == null) {
            throw new CourrierValidationException("Votre compte n'est pas rattaché à une direction.");
        }
        if (cibleDirectionId == null) {
            throw new CourrierValidationException("Direction cible obligatoire.");
        }
        return enregistrerCourrierInterneDivision(u.getEntite().getId(), cibleDirectionId, dto);
    }

    /**
     * Courrier sortant externe depuis une direction de la division (hors ODC) : dépôt sur le hub, statut {@link StatutCourrier#TRANSMIS_DCIRE}.
     */
    public Courrier enregistrerCourrierExterneDepuisMaStructure(CourrierDTO dto, Utilisateur principal) throws IOException {
        return enregistrerCourrierExterneDepuisMaStructureLegacy(dto, principal);
    }

    private Courrier enregistrerCourrierExterneDepuisMaStructureLegacy(CourrierDTO dto, Utilisateur principal)
            throws IOException {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        if (u.getEntite() == null || u.getEntite().getId() == null) {
            throw new CourrierValidationException("Votre compte n'est pas rattaché à une direction.");
        }
        Entite origine = entiteRepository.findById(u.getEntite().getId())
                .orElseThrow(() -> new CourrierValidationException("Direction d'origine introuvable"));
        if (origine.getType() != TypeEntite.DIRECTION) {
            throw new CourrierValidationException("Seules les directions peuvent émettre.");
        }
        if (nomIndiqueDcire(origine)) {
            throw new CourrierValidationException("Action non applicable depuis le hub.");
        }
        if (estDirectionOdc(origine)) {
            throw new CourrierValidationException("Utilisez le brouillon ODC pour ce périmètre.");
        }
        if (!estMembreDivisionDcire(origine)) {
            throw new CourrierValidationException("Émission réservée aux directions de la division.");
        }
        dto.setDirectionId(origine.getId());
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

        Entite dcire = resolveDcireDirectionOptional().orElse(null);
        Courrier courrier = new Courrier();
        courrier.setNumero(dto.getNumero());
        courrier.setObjet(dto.getObjet());
        courrier.setExpediteur(dto.getExpediteur());
        courrier.setStructureOrigine(origine);
        courrier.setDirectionInitial(dcire);
        courrier.setEntite(dcire);
        courrier.setDestinataireOdc(DestinataireCourrierOdc.EXTERNE);
        if (dto.getExternePrecision() != null && !dto.getExternePrecision().isBlank()) {
            courrier.setExternePrecision(dto.getExternePrecision().trim());
        }
        courrier.setFichier(cheminFichier);
        courrier.setStatut(StatutCourrier.TRANSMIS_DCIRE);
        courrier.setDateReception(new Date());
        courrier.setDateLimite(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000));
        courrier.setDateRelance(new Date(System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000));
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(dcire);
        historique.setUtilisateur(u);
        historique.setStatut(StatutCourrier.TRANSMIS_DCIRE);
        historique.setCommentaire(
                "Courrier transmis à la DCIRE — en attente d'enregistrement et de suivi");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(origine);
        historique.setNouvelleEntite(dcire);
        historiqueRepository.save(historique);

        notifierDirecteursDcireHub(courrier, dcire);
        return courrier;
    }

    /**
     * Hub : accusé de réception sur un courrier sortant externe — prise en charge côté DCIRE.
     * L’envoi physique vers l’interlocuteur final reste hors application ; statut {@link StatutCourrier#EN_COURS}.
     */
    @Transactional
    public Courrier validerExpeditionExterneParDcire(Long courrierId, Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        Entite dcire = resolveDcireDirectionOptional().orElse(null);
        if (u.getRole() == null || !"DIRECTEUR".equalsIgnoreCase(u.getRole().getNom().trim())) {
            throw new CourrierValidationException("Action réservée au directeur du hub.");
        }
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.TRANSMIS_DCIRE) {
            throw new CourrierValidationException("Ce courrier n'est pas en attente d'accusé de réception hub.");
        }
        if (dcire != null) {
            if (u.getEntite() == null || u.getEntite().getId() == null) {
                throw new CourrierValidationException("Profil sans direction.");
            }
            if (!Objects.equals(dcire.getId(), u.getEntite().getId())) {
                throw new CourrierValidationException("Action réservée au hub.");
            }
            if (courrier.getEntite() == null || !Objects.equals(dcire.getId(), courrier.getEntite().getId())) {
                throw new CourrierValidationException("Le courrier n'est pas sur le hub.");
            }
        }
        DestinataireCourrierOdc dco =
                courrier.getDestinataireOdc() != null ? courrier.getDestinataireOdc() : DestinataireCourrierOdc.EXTERNE;
        if (dco != DestinataireCourrierOdc.EXTERNE) {
            throw new CourrierValidationException("Ce flux n'est pas un envoi externe sortant.");
        }
        Entite ancienne = courrier.getEntite();
        courrier.setStatut(StatutCourrier.EN_COURS);
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(dcire);
        historique.setUtilisateur(u);
        historique.setStatut(StatutCourrier.EN_COURS);
        historique.setCommentaire("Hub : accusé de réception — prise en charge (envoi physique hors application)");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(ancienne);
        historique.setNouvelleEntite(dcire);
        historiqueRepository.save(historique);

        notifierStructureOrigineAccuseReceptionHub(courrier);
        return courrier;
    }

    /**
     * Notifie la structure à l’origine du courrier externe qu’une réponse (ex. pièce du tiers) a été enregistrée via le hub.
     */
    public void notifierStructureOrigineReponseCourrierExterne(
            Long courrierId, String emailRepondeur, String objetReponse, String corpsMessage) {
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStructureOrigine() == null || courrier.getStructureOrigine().getId() == null) {
            return;
        }
        if (courrier.getDestinataireOdc() != DestinataireCourrierOdc.EXTERNE) {
            return;
        }
        Entite origine = entiteRepository.findById(courrier.getStructureOrigine().getId()).orElse(courrier.getStructureOrigine());
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        collectEmailsDirecteursEtResponsable(origine, emails);
        if (emails.isEmpty()) {
            log.warn("Aucun e-mail pour notifier la structure d'origine (réponse courrier externe id={})", courrierId);
            return;
        }
        String chemin = estDirectionOdc(origine) ? "courrier" : "structure/courriers";
        String lien = lienFrontendHash(chemin);
        String sujet = "[Courrier] Réponse enregistrée (via hub) — " + courrier.getNumero();
        String html = "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">"
                + "<p>Le hub a déposé une réponse sur votre courrier sortant externe.</p>"
                + "<p><strong>Objet du courrier :</strong> " + escapeHtmlCourrier(courrier.getObjet()) + "</p>"
                + "<p><strong>Objet de la réponse :</strong> " + escapeHtmlCourrier(objetReponse) + "</p>"
                + "<p><strong>Déposé par :</strong> " + escapeHtmlCourrier(emailRepondeur) + "</p>"
                + "<p style=\"white-space:pre-wrap\">" + escapeHtmlCourrier(corpsMessage) + "</p>"
                + "<p><a href=\"" + lien + "\">Ouvrir dans l’application</a></p>"
                + "</body></html>";
        for (String em : emails) {
            try {
                emailService.sendSimpleEmail(em, sujet, html);
            } catch (RuntimeException ex) {
                log.warn("E-mail structure d'origine non envoyé (courrier id={}) : {}", courrier.getId(), ex.getMessage());
            }
        }
    }

    private void notifierStructureOrigineAccuseReceptionHub(Courrier courrier) {
        if (courrier.getStructureOrigine() == null || courrier.getStructureOrigine().getId() == null) {
            return;
        }
        Entite origine = entiteRepository.findById(courrier.getStructureOrigine().getId()).orElse(courrier.getStructureOrigine());
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        collectEmailsDirecteursEtResponsable(origine, emails);
        if (emails.isEmpty()) {
            log.warn("Aucun e-mail pour notifier l'accusé de réception hub (courrier id={})", courrier.getId());
            return;
        }
        String chemin = estDirectionOdc(origine) ? "courrier" : "structure/courriers";
        String lien = lienFrontendHash(chemin);
        String sujet = "[Courrier] Accusé de réception hub — " + courrier.getNumero();
        String html = "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">"
                + "<p>Le hub a accusé réception de votre courrier sortant externe et en assure le suivi. "
                + "L’envoi vers l’interlocuteur ciblé se fait hors application.</p>"
                + "<p><strong>Objet :</strong> " + escapeHtmlCourrier(courrier.getObjet()) + "</p>"
                + "<p><a href=\"" + lien + "\">Voir dans l’application</a></p>"
                + "</body></html>";
        for (String em : emails) {
            try {
                emailService.sendSimpleEmail(em, sujet, html);
            } catch (RuntimeException ex) {
                log.warn("E-mail accusé réception hub non envoyé (courrier id={}) : {}", courrier.getId(), ex.getMessage());
            }
        }
    }

    /** Courrier sortant externe avec trace de la structure d’origine (notif. réponse vers l’expéditeur métier). */
    public boolean estFluxExterneSortantAvecOrigine(Courrier courrier) {
        if (courrier == null || courrier.getStructureOrigine() == null) {
            return false;
        }
        /* Uniquement si EXTERNE est explicite (null = flux interne ou réception, pas ce circuit). */
        return courrier.getDestinataireOdc() == DestinataireCourrierOdc.EXTERNE;
    }

    private void collectEmailsDirecteursEtResponsable(Entite origine, LinkedHashSet<String> emails) {
        if (origine.getResponsable() != null
                && origine.getResponsable().getEmail() != null
                && !origine.getResponsable().getEmail().isBlank()) {
            emails.add(origine.getResponsable().getEmail().trim());
        }
        for (Utilisateur x : utilisateurRepository.findByEntite_Id(origine.getId())) {
            if (x.getRole() != null
                    && x.getRole().getNom() != null
                    && x.getRole().getNom().toUpperCase(Locale.ROOT).contains("DIRECTEUR")
                    && x.getEmail() != null
                    && !x.getEmail().isBlank()) {
                emails.add(x.getEmail().trim());
            }
        }
    }

    /**
     * Accusé de réception opérationnel : passage ENVOYER → EN_COURS (visible côté expéditeur dans les listes ODC).
     */
    public Courrier accuserReceptionOperationnelle(Long courrierId, Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId())
                .orElseThrow(() -> new CourrierValidationException("Utilisateur introuvable."));
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.ENVOYER) {
            throw new CourrierValidationException(
                    "L'accusé de réception n'est possible que pour un courrier déjà validé sur votre structure (statut ENVOYER).");
        }
        if (u.getEntite() == null
                || courrier.getEntite() == null
                || !Objects.equals(u.getEntite().getId(), courrier.getEntite().getId())) {
            throw new CourrierValidationException("Ce courrier n'est pas en possession de votre direction.");
        }
        Entite entite = courrier.getEntite();
        courrier.setStatut(StatutCourrier.EN_COURS);
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(entite);
        historique.setUtilisateur(u);
        historique.setStatut(StatutCourrier.EN_COURS);
        historique.setCommentaire("Accusé de réception / prise en charge opérationnelle par la structure destinataire");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(entite);
        historique.setNouvelleEntite(entite);
        historiqueRepository.save(historique);
        return courrier;
    }

    /**
     * Courriers reçus par la direction de l’utilisateur (hors brouillon / attente validation directeur structure).
     */
    public List<Courrier> listerRecusOperationnelsMaStructure(Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        if (u.getEntite() == null || u.getEntite().getId() == null) {
            return List.of();
        }
        return courrierRepository.findByEntiteIdAndStatutIn(
                u.getEntite().getId(),
                List.of(
                        StatutCourrier.ENVOYER,
                        StatutCourrier.IMPUTER,
                        StatutCourrier.EN_COURS,
                        StatutCourrier.REPONDU))
                .stream()
                .filter(c -> !estEmissionEmailSortanteDepuisDirection(c, u.getEntite().getId()))
                .toList();
    }

    /** Courrier émis par email direct depuis la direction (sortant) — ne doit pas apparaître dans « Reçus ». */
    private boolean estEmissionEmailSortanteDepuisDirection(Courrier c, Long directionId) {
        if (directionId == null || c.getStructureOrigine() == null || c.getStructureOrigine().getId() == null) {
            return false;
        }
        if (!Objects.equals(c.getStructureOrigine().getId(), directionId)) {
            return false;
        }
        DestinataireCourrierOdc dest = c.getDestinataireOdc() != null
                ? c.getDestinataireOdc()
                : DestinataireCourrierOdc.EXTERNE;
        if (dest != DestinataireCourrierOdc.EXTERNE) {
            return false;
        }
        String precision = c.getExternePrecision();
        return precision != null && precision.contains("@");
    }

    public Courrier validerReceptionParDirecteurStructure(Long courrierId, Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId())
                .orElseThrow(() -> new CourrierValidationException("Utilisateur introuvable."));
        Courrier courrier = getCourrier(courrierId);
        if (courrier.getStatut() != StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_STRUCTURE) {
            throw new CourrierValidationException("Ce courrier n'est pas en attente de validation par votre structure.");
        }
        if (u.getEntite() == null
                || courrier.getEntite() == null
                || !Objects.equals(u.getEntite().getId(), courrier.getEntite().getId())) {
            throw new CourrierValidationException("Vous ne pouvez valider que les courriers adressés à votre direction.");
        }
        Entite entite = courrier.getEntite();
        courrier.setStatut(StatutCourrier.ENVOYER);
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(entite);
        historique.setUtilisateur(u);
        historique.setStatut(StatutCourrier.ENVOYER);
        historique.setCommentaire(
                "Validé par le ou la directeur·rice de la structure destinataire — courrier disponible pour traitement");
        historique.setDateAction(new Date());
        historique.setAncienneEntite(entite);
        historique.setNouvelleEntite(entite);
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

        Entite dcire = resolveDcireDirectionOptional().orElse(null);
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
        Entite dcire = resolveDcireDirectionOptional().orElse(null);
        if (dcire != null
                && (courrier.getEntite() == null || !Objects.equals(dcire.getId(), courrier.getEntite().getId()))) {
            throw new CourrierValidationException("Le courrier doit être présent sur la direction DCIRE pour être transmis à l'ODC.");
        }
        if (courrier.getDestinataireOdc() == DestinataireCourrierOdc.EXTERNE) {
            throw new CourrierValidationException(
                    "Courrier externe sortant : pas de transmission ODC. Utilisez l’accusé de réception hub puis, "
                            + "pour la réponse du tiers, la fonction « Répondre » avec pièce jointe.");
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

        notifierDirecteursOdcNouveauCourrier(courrier);
        return courrier;
    }

    private Entite resolveDirectionDivisionCible(DestinataireCourrierOdc dest) {
        if (dest == null || dest == DestinataireCourrierOdc.EXTERNE) {
            throw new CourrierValidationException("Destinataire interne invalide pour la résolution de direction.");
        }
        return entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(e -> !nomIndiqueDcire(e))
                .filter(e -> estDirectionCibleDivision(dest, e))
                .findFirst()
                .orElseThrow(() -> new CourrierValidationException(
                        "Aucune direction correspondant au destinataire « " + dest
                                + " » : créez une entité Direction dont le nom contient "
                                + libelleAttenduPourDestinataire(dest) + "."));
    }

    private static String libelleAttenduPourDestinataire(DestinataireCourrierOdc dest) {
        return switch (dest) {
            case FONDATION -> "« Fondation »";
            case RSE -> "« RSE »";
            case DCI -> "« DCI » (hors DCIRE)";
            default -> "le libellé attendu";
        };
    }

    private boolean estDirectionCibleDivision(DestinataireCourrierOdc dest, Entite e) {
        String n = normalizeNomEntite(e.getNom());
        return switch (dest) {
            case FONDATION -> n.contains("FONDATION");
            case RSE -> n.contains("RSE");
            case DCI -> n.contains("DCI") && !nomIndiqueDcire(e);
            default -> false;
        };
    }

    private void notifierDirecteursDcireHub(Courrier courrier, Entite dcire) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (dcire != null && dcire.getId() != null) {
            Entite hub = entiteRepository.findById(dcire.getId()).orElse(dcire);
            if (hub.getResponsable() != null
                    && hub.getResponsable().getEmail() != null
                    && !hub.getResponsable().getEmail().isBlank()) {
                emails.add(hub.getResponsable().getEmail().trim());
            }
            for (Utilisateur x : utilisateurRepository.findByEntite_Id(hub.getId())) {
                if (x.getRole() != null
                        && "DIRECTEUR".equalsIgnoreCase(x.getRole().getNom().trim())
                        && x.getEmail() != null
                        && !x.getEmail().isBlank()) {
                    emails.add(x.getEmail().trim());
                }
            }
        } else {
            // Mode "profil" : pas d’entité DCIRE => notifier tous les DIRECTEUR (hub).
            for (Utilisateur x : utilisateurRepository.findByRole_Nom("DIRECTEUR")) {
                if (x.getEmail() != null && !x.getEmail().isBlank()) {
                    emails.add(x.getEmail().trim());
                }
            }
        }
        if (emails.isEmpty()) {
            log.warn("Aucun e-mail pour notifier le hub (courrier id={})", courrier.getId());
            return;
        }
        String lien = lienFrontendHash("courrier");
        String sujet = "[Courrier] Hub — à traiter : " + courrier.getNumero();
        String corps = "<p>Nouveau courrier sortant à traiter sur le hub.</p>"
                + "<p><strong>Objet :</strong> " + escapeHtmlCourrier(courrier.getObjet()) + "</p>"
                + "<p><a href=\"" + lien + "\">Ouvrir</a></p>";
        String html = "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>";
        for (String em : emails) {
            try {
                emailService.sendSimpleEmail(em, sujet, html);
            } catch (RuntimeException ex) {
                log.warn("E-mail hub non envoyé (courrier id={}) : {}", courrier.getId(), ex.getMessage());
            }
        }
    }

    private void notifierDirecteursStructure(Courrier courrier, DestinataireCourrierOdc dest) {
        if (courrier.getEntite() == null || courrier.getEntite().getId() == null) {
            return;
        }
        Entite cible = entiteRepository.findById(courrier.getEntite().getId()).orElse(courrier.getEntite());
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (cible.getResponsable() != null
                && cible.getResponsable().getEmail() != null
                && !cible.getResponsable().getEmail().isBlank()) {
            emails.add(cible.getResponsable().getEmail().trim());
        }
        String roleAttendu = roleNomPourDestinataireStructure(dest);
        if (roleAttendu == null && cible.getNom() != null) {
            roleAttendu = roleNomDepuisNomDirection(cible.getNom());
        }
        if (roleAttendu != null) {
            for (Utilisateur u : utilisateurRepository.findByEntite_Id(cible.getId())) {
                if (u.getRole() != null
                        && roleAttendu.equals(u.getRole().getNom())
                        && u.getEmail() != null
                        && !u.getEmail().isBlank()) {
                    emails.add(u.getEmail().trim());
                }
            }
        }
        if (emails.isEmpty()) {
            log.warn(
                    "Aucun e-mail pour notifier la structure destinataire (courrier id={}, entite id={})",
                    courrier.getId(),
                    cible.getId());
            return;
        }
        String lien = lienFrontendHash("structure/courriers");
        String sujet = "[Courrier] Validation demandée — " + courrier.getNumero();
        String corps = "<p>Un courrier validé par le directeur ODC attend votre validation avant mise en traitement dans votre structure.</p>"
                + "<p><strong>Objet :</strong> " + escapeHtmlCourrier(courrier.getObjet()) + "</p>"
                + "<p><a href=\"" + lien + "\">Ouvrir l’espace courriers</a></p>";
        String html = "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>";
        for (String em : emails) {
            try {
                emailService.sendSimpleEmail(em, sujet, html);
            } catch (RuntimeException ex) {
                log.warn("E-mail directeur de structure non envoyé (courrier id={}) : {}", courrier.getId(), ex.getMessage());
            }
        }
    }

    /** Notification Fondation / RSE / DCI : courrier déjà reçu (pas d’étape validation type ODC). */
    private void notifierDirecteursStructureReception(Courrier courrier, DestinataireCourrierOdc dest) {
        if (courrier.getEntite() == null || courrier.getEntite().getId() == null) {
            return;
        }
        Entite cible = entiteRepository.findById(courrier.getEntite().getId()).orElse(courrier.getEntite());
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (cible.getResponsable() != null
                && cible.getResponsable().getEmail() != null
                && !cible.getResponsable().getEmail().isBlank()) {
            emails.add(cible.getResponsable().getEmail().trim());
        }
        String roleAttendu = roleNomPourDestinataireStructure(dest);
        if (roleAttendu == null && cible.getNom() != null) {
            roleAttendu = roleNomDepuisNomDirection(cible.getNom());
        }
        if (roleAttendu != null) {
            for (Utilisateur u : utilisateurRepository.findByEntite_Id(cible.getId())) {
                if (u.getRole() != null
                        && roleAttendu.equals(u.getRole().getNom())
                        && u.getEmail() != null
                        && !u.getEmail().isBlank()) {
                    emails.add(u.getEmail().trim());
                }
            }
        }
        if (emails.isEmpty()) {
            log.warn(
                    "Aucun e-mail pour notifier la réception structure (courrier id={}, entite id={})",
                    courrier.getId(),
                    cible.getId());
            return;
        }
        String lien = lienFrontendHash("structure/courriers");
        String sujet = "[Courrier] Nouveau courrier reçu — " + courrier.getNumero();
        String corps = "<p>La DCIRE vous a adressé un courrier, disponible dans votre espace (réception / réponse).</p>"
                + "<p><strong>Objet :</strong> " + escapeHtmlCourrier(courrier.getObjet()) + "</p>"
                + "<p><a href=\"" + lien + "\">Ouvrir Gestion Courriers</a></p>";
        String html = "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>";
        for (String em : emails) {
            try {
                emailService.sendSimpleEmail(em, sujet, html);
            } catch (RuntimeException ex) {
                log.warn("E-mail réception structure non envoyé (courrier id={}) : {}", courrier.getId(), ex.getMessage());
            }
        }
    }

    private static String roleNomPourDestinataireStructure(DestinataireCourrierOdc dest) {
        if (dest == null) {
            return null;
        }
        return switch (dest) {
            case FONDATION -> "DIRECTEUR_FONDATION";
            case RSE -> "DIRECTEUR_RSE";
            case DCI -> "DIRECTEUR_DCI";
            default -> null;
        };
    }

    private static DestinataireCourrierOdc destinataireStructureDepuisDirection(Entite direction) {
        if (direction == null || direction.getNom() == null) {
            return null;
        }
        String n = direction.getNom().toUpperCase(Locale.ROOT);
        if (n.contains("FONDATION")) {
            return DestinataireCourrierOdc.FONDATION;
        }
        if (n.contains("RSE")) {
            return DestinataireCourrierOdc.RSE;
        }
        if (n.contains("DCI") && !n.contains("DCIRE")) {
            return DestinataireCourrierOdc.DCI;
        }
        return null;
    }

    private static String roleNomDepuisNomDirection(String nomDirection) {
        if (nomDirection == null || nomDirection.isBlank()) {
            return null;
        }
        Entite stub = new Entite();
        stub.setNom(nomDirection);
        return roleNomPourDestinataireStructure(destinataireStructureDepuisDirection(stub));
    }

    private Entite resolveDcireDirection() {
        return resolveDcireDirectionOptional().orElseThrow(() -> new CourrierValidationException(
                "Aucune direction hub (DCIRE) détectée. Soit renommez l’entité Direction en base pour qu’elle "
                        + "contienne « DCIRE » ou « DCI RE », soit renseignez l’ID : propriété "
                        + "app.courrier.dcire-direction-id (sur AWS Elastic Beanstalk : variable "
                        + "APP_COURIER_DCIRE_DIRECTION_ID). Repérez l’id dans la table entite (type Direction)."));
    }

    private Optional<Entite> resolveDcireDirectionOptional() {
        if (configuredDcireDirectionId > 0) {
            Entite e = entiteRepository
                    .findById(configuredDcireDirectionId)
                    .orElseThrow(() -> new CourrierValidationException(
                            "Configuration app.courrier.dcire-direction-id="
                                    + configuredDcireDirectionId
                                    + " : aucune entité avec cet identifiant."));
            if (e.getType() != TypeEntite.DIRECTION) {
                throw new CourrierValidationException(
                        "app.courrier.dcire-direction-id doit référencer une entité de type Direction.");
            }
            return Optional.of(e);
        }
        Optional<Entite> byNom = entiteRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(this::nomIndiqueDcire)
                .findFirst();
        if (byNom.isPresent()) {
            return byNom;
        }
        return utilisateurRepository.findByEmail("dcire@gmail.com")
                .map(Utilisateur::getEntite)
                .filter(e -> e != null && e.getId() != null && e.getType() == TypeEntite.DIRECTION);
    }

    private boolean nomIndiqueDcire(Entite e) {
        if (e == null || e.getNom() == null) {
            return false;
        }
        String n = normalizeNomEntite(e.getNom());
        if (n.contains("DCIRE")) {
            return true;
        }
        String withSpaces = n.replace('-', ' ');
        return withSpaces.contains("DCI RE");
    }

    private String normalizeNomEntite(String nom) {
        if (nom == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(nom, Normalizer.Form.NFD);
        String sansAccents = decomposed.replaceAll("\\p{M}+", "");
        return sansAccents.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
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
        throw new CourrierValidationException(
                "Seule la DCIRE émet des courriers. Utilisez POST /api/courriers/dcire/emission.");
    }

    @SuppressWarnings("unused")
    private Courrier enregistrerCourrierInterneDivisionLegacy(Long origineDirectionId, Long cibleDirectionId,
            CourrierDTO dto) throws IOException {
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
        String lien = lienFrontendHash("courrier");
        String expediteur = courrier.getExpediteur() != null && !courrier.getExpediteur().isBlank()
                ? courrier.getExpediteur().trim()
                : "DCIRE";
        String sujet = "[ODC Courrier] Nouveau courrier : " + courrier.getNumero();
        String corps = "<p>Vous avez reçu un nouveau courrier.</p>"
                + "<p><strong>Expéditeur :</strong> " + escapeHtmlCourrier(expediteur) + "</p>"
                + "<p><strong>Objet :</strong> " + escapeHtmlCourrier(courrier.getObjet()) + "</p>"
                + "<p><a href=\"" + lien + "\">Accéder à votre espace</a></p>";
        String html = "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>";
        for (Utilisateur d : directeurs) {
            if (d.getEmail() != null && !d.getEmail().isBlank()) {
                try {
                    emailService.sendSimpleEmail(d.getEmail(), sujet, html, expediteur, null);
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

    /**
     * Télécharge la pièce jointe pour le directeur ODC sans modifier le statut ni l’historique
     * (consultation avant validation ou suggestions).
     */
    public ResponseEntity<InputStreamResource> telechargerPourValidationDirecteurOdc(Long courrierId) throws IOException {
        Courrier courrier = getCourrier(courrierId);
        List<StatutCourrier> ok = Arrays.asList(
                StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC,
                StatutCourrier.EN_REVISION_ADMIN_COURRIER,
                StatutCourrier.ATTENTE_VALIDATION_ODC);
        if (!ok.contains(courrier.getStatut())) {
            throw new CourrierValidationException("Ce courrier n'est pas consultable à cette étape.");
        }
        if (courrier.getFichier() == null || courrier.getFichier().isBlank()) {
            throw new CourrierValidationException("Aucune pièce jointe pour ce courrier.");
        }
        File fichier = new File(courrier.getFichier());
        if (!fichier.exists()) {
            throw new CourrierValidationException("Fichier introuvable sur le serveur.");
        }
        InputStreamResource resource = new InputStreamResource(new FileInputStream(fichier));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fichier.getName() + "\"")
                .contentLength(fichier.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private boolean peutGererMetadonneesOuSuppressionCourrier(Utilisateur u, Courrier c) {
        Utilisateur full = utilisateurRepository.findById(u.getId()).orElse(u);
        if (full.getRole() == null || full.getRole().getNom() == null) {
            return false;
        }
        String role = full.getRole().getNom().trim().toUpperCase();
        if ("SUPERADMIN".equals(role) || "ADMIN".equals(role) || "DIRECTEUR_ODC".equals(role)) {
            return true;
        }
        Long entId = full.getEntite() != null ? full.getEntite().getId() : null;
        if (entId == null) {
            return false;
        }
        if ("DIRECTEUR".equals(role)) {
            Optional<Entite> dcireOpt = resolveDcireDirectionOptional();
            if (dcireOpt.isPresent()) {
                Entite dcire = dcireOpt.get();
                if (c.getEntite() != null && Objects.equals(dcire.getId(), c.getEntite().getId())) {
                    return true;
                }
                if (c.getStructureOrigine() != null && Objects.equals(dcire.getId(), c.getStructureOrigine().getId())) {
                    return true;
                }
                return false;
            }
            // Mode profil : le hub gère les flux externes.
            return c.getDestinataireOdc() == DestinataireCourrierOdc.EXTERNE;
        }
        if (role.startsWith("DIRECTEUR_") && !"DIRECTEUR_ODC".equals(role)) {
            return listerToutPourMaStructure(full).stream().anyMatch(x -> x.getId().equals(c.getId()));
        }
        return false;
    }

    @Transactional
    public Courrier mettreAJourMetadonneesCourrier(Long courrierId, CourrierMetadonneesDTO dto, Utilisateur principal) {
        if (dto == null) {
            throw new CourrierValidationException("Données invalides.");
        }
        Courrier c = getCourrier(courrierId);
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        if (!peutGererMetadonneesOuSuppressionCourrier(u, c)) {
            throw new CourrierValidationException("Vous n'avez pas le droit de modifier ce courrier.");
        }
        if (c.getStatut() == StatutCourrier.ARCHIVER) {
            throw new CourrierValidationException("Un courrier archivé ne peut pas être modifié.");
        }
        if (dto.getNumero() != null && !dto.getNumero().isBlank()) {
            c.setNumero(dto.getNumero().trim());
        }
        if (dto.getObjet() != null && !dto.getObjet().isBlank()) {
            c.setObjet(dto.getObjet().trim());
        }
        if (dto.getExpediteur() != null && !dto.getExpediteur().isBlank()) {
            c.setExpediteur(dto.getExpediteur().trim());
        }
        return courrierRepository.save(c);
    }

    @Transactional
    public void supprimerCourrierParDirecteurStructure(Long courrierId, Utilisateur principal) {
        Utilisateur u = utilisateurRepository.findById(principal.getId()).orElse(principal);
        String roleNom = u.getRole() != null && u.getRole().getNom() != null
                ? u.getRole().getNom().trim().toUpperCase()
                : "";
        if ("SUPERADMIN".equals(roleNom) || "ADMIN".equals(roleNom) || "DIRECTEUR_ODC".equals(roleNom)) {
            supprimerCourrier(courrierId);
            return;
        }
        Courrier c = getCourrier(courrierId);
        if (!peutGererMetadonneesOuSuppressionCourrier(u, c)) {
            throw new CourrierValidationException("Vous n'avez pas le droit de supprimer ce courrier.");
        }
        supprimerCourrier(courrierId);
    }

    public List<Courrier> listerTousLesCourriers() {
        return courrierRepository.findAllOrderByDateReceptionDesc();
    }

    @Transactional
    public void ajouterFichierArchive(Long courrierId, MultipartFile file, Utilisateur utilisateur) throws IOException {
        Courrier c = getCourrier(courrierId);
        if (file != null && !file.isEmpty()) {
            String path = sauvegarderFichierSecurise(file);
            c.setFichierArchive(path);
            courrierRepository.save(c);
            
            HistoriqueCourrier h = new HistoriqueCourrier();
            h.setCourrier(c);
            h.setUtilisateur(utilisateur);
            h.setEntite(c.getEntite());
            h.setStatut(c.getStatut());
            h.setCommentaire("Mise à jour du fichier d'archive");
            h.setDateAction(new Date());
            h.setAncienneEntite(c.getEntite());
            h.setNouvelleEntite(c.getEntite());
            historiqueRepository.save(h);
        }
    }

    /**
     * Émission email direct depuis une direction de la division (ODC, Fondation, RSE, DCI).
     */
    @Transactional
    public Courrier emettreCourrierDivisionParEmail(
            String numero,
            String expediteur,
            String objet,
            String emailDestinataire,
            Long directionId,
            MultipartFile fichier,
            Utilisateur auteur
    ) throws IOException {
        if (objet == null || objet.isBlank()) {
            throw new CourrierValidationException("L'objet est obligatoire.");
        }
        if (expediteur == null || expediteur.isBlank()) {
            throw new CourrierValidationException("L'expéditeur est obligatoire.");
        }
        if (emailDestinataire == null || emailDestinataire.isBlank() || !emailDestinataire.contains("@")) {
            throw new CourrierValidationException("Email destinataire invalide.");
        }

        Entite direction = entiteRepository.findById(directionId)
                .orElseThrow(() -> new CourrierValidationException("Direction de votre division introuvable."));
        if (direction.getType() != TypeEntite.DIRECTION) {
            throw new CourrierValidationException("L'identifiant doit correspondre à une direction.");
        }
        assertUtilisateurPeutEmettrePourDirection(auteur, direction);

        String cheminFichier = null;
        if (fichier != null && !fichier.isEmpty()) {
            try {
                cheminFichier = sauvegarderFichierSecurise(fichier);
            } catch (FileValidationException e) {
                throw new CourrierValidationException("Erreur de validation du fichier : " + e.getMessage(), e);
            }
        }

        Courrier courrier = new Courrier();
        courrier.setNumero(numero != null && !numero.isBlank() ? numero : prefixNumeroDirection(direction) + "-" + System.currentTimeMillis());
        courrier.setObjet(objet);
        courrier.setExpediteur(expediteur);
        courrier.setEntite(direction);
        courrier.setDirectionInitial(direction);
        courrier.setStructureOrigine(direction);
        courrier.setFichier(cheminFichier);
        courrier.setStatut(StatutCourrier.ENVOYER);
        courrier.setDateReception(new Date());
        courrier.setDestinataireOdc(DestinataireCourrierOdc.EXTERNE);
        courrier.setExternePrecision(emailDestinataire.trim());
        courrierRepository.save(courrier);

        HistoriqueCourrier historique = new HistoriqueCourrier();
        historique.setCourrier(courrier);
        historique.setEntite(direction);
        historique.setUtilisateur(auteur);
        historique.setStatut(StatutCourrier.ENVOYER);
        historique.setCommentaire("Courrier émis par email direct vers " + emailDestinataire.trim());
        historique.setDateAction(new Date());
        historique.setAncienneEntite(direction);
        historique.setNouvelleEntite(null);
        historiqueRepository.save(historique);

        List<java.io.File> filesToAttach = new java.util.ArrayList<>();
        java.io.File tempFileToDelete = null;
        File resolved = resolveCourrierFichier(cheminFichier);
        if (resolved != null) {
            filesToAttach.add(resolved);
        } else if (cheminFichier != null && !cheminFichier.isBlank()) {
            byte[] s3Bytes = uploadFileService.getFileBytesFromS3(cheminFichier);
            if (s3Bytes != null && s3Bytes.length > 0) {
                String filename = cheminFichier;
                if (filename.contains("/")) {
                    filename = filename.substring(filename.lastIndexOf("/") + 1);
                }
                java.io.File tempFile = java.io.File.createTempFile("s3-attach-", filename);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                    fos.write(s3Bytes);
                }
                filesToAttach.add(tempFile);
                tempFileToDelete = tempFile;
            }
        }

        DivisionMarque marque = marquePourDirection(direction);
        String emailBody = "<!DOCTYPE html><html><body>"
                + "<div style='font-family: Arial, sans-serif; border: 2px solid #ff7900; padding: 20px; border-radius: 8px; max-width: 600px; margin: 0 auto;'>"
                + "<div style='background-color: #ff7900; color: white; padding: 15px; font-size: 22px; font-weight: bold; text-align: center; border-top-left-radius: 6px; border-top-right-radius: 6px;'>"
                + marque.enTete()
                + "</div>"
                + "<div style='padding: 20px; background-color: #ffffff; color: #2c3e50; line-height: 1.6;'>"
                + "<h3 style='color: #2c3e50; border-bottom: 1px solid #dee2e6; padding-bottom: 10px;'>Envoi de courrier</h3>"
                + "<p>Bonjour,</p>"
                + "<p>Nous vous transmettons un courrier émis par <b>" + expediteur + "</b>"
                + (direction.getNom() != null ? " — <b>" + direction.getNom() + "</b>" : "")
                + ".</p>"
                + "<div style='background-color: #f8f9fa; border-left: 4px solid #ff7900; padding: 15px; margin: 20px 0; border-radius: 4px;'>"
                + "<p style='margin: 0; font-weight: bold; color: #2c3e50; margin-bottom: 10px;'>Objet :</p>"
                + "<p style='margin: 0;'>" + objet + "</p>"
                + "</div>"
                + "<p>Veuillez trouver ci-joint le document correspondant.</p>"
                + "<p>" + marque.signatureHtml() + "</p>"
                + "</div>"
                + "<hr style='border: none; border-top: 1px solid #dee2e6; margin: 20px 0;'>"
                + "<p style='font-size: 0.8em; color: #888888; text-align: center; margin: 0;'>"
                + marque.piedPage()
                + "</p>"
                + "</div></body></html>";

        String auteurLibelle = buildExpediteurLibelleAuteur(auteur, direction);
        String displayName = EmailService.resolveDisplayName(expediteur, auteurLibelle);
        String replyTo = EmailService.resolveReplyTo(expediteur, auteur != null ? auteur.getEmail() : null);
        
        try {
            emailService.sendEmailWithAttachments(
                    emailDestinataire.trim(),
                    objet,
                    emailBody,
                    filesToAttach,
                    displayName,
                    replyTo);
        } finally {
            if (tempFileToDelete != null) {
                try {
                    tempFileToDelete.delete();
                } catch (Exception ex) {}
            }
        }

        return courrier;
    }

    /** Alias historique (directeur ODC). */
    @Transactional
    public Courrier emettreCourrierOdcParEmail(
            String numero,
            String expediteur,
            String objet,
            String emailDestinataire,
            Long directionId,
            MultipartFile fichier,
            Utilisateur auteur
    ) throws IOException {
        return emettreCourrierDivisionParEmail(
                numero, expediteur, objet, emailDestinataire, directionId, fichier, auteur);
    }

    private String prefixNumeroDirection(Entite direction) {
        if (direction == null || direction.getNom() == null) {
            return "DIV";
        }
        String n = normalizeNomEntite(direction.getNom());
        if (n.contains("FONDATION")) {
            return "FONDATION";
        }
        if (n.contains("RSE")) {
            return "RSE";
        }
        if (n.contains("DCI") && !n.contains("DCIRE")) {
            return "DCI";
        }
        if (estDirectionOdc(direction)) {
            return "ODC";
        }
        return "DIV";
    }

    private void assertUtilisateurPeutEmettrePourDirection(Utilisateur auteur, Entite direction) {
        if (direction == null || direction.getId() == null) {
            throw new CourrierValidationException("Direction invalide.");
        }
        if (auteur == null) {
            return;
        }
        if (auteur.getRole() != null) {
            String role = auteur.getRole().getNom() != null
                    ? auteur.getRole().getNom().trim().toUpperCase(java.util.Locale.ROOT)
                    : "";
            if ("SUPERADMIN".equals(role) || "ADMIN".equals(role)) {
                return;
            }
        }
        if (auteur.getEntite() != null && Objects.equals(auteur.getEntite().getId(), direction.getId())) {
            return;
        }
        throw new CourrierValidationException(
                "Vous ne pouvez émettre des courriers que pour votre propre direction.");
    }

    private String buildExpediteurLibelleAuteur(Utilisateur auteur, Entite direction) {
        if (auteur != null) {
            String prenom = auteur.getPrenom() != null ? auteur.getPrenom().trim() : "";
            String nom = auteur.getNom() != null ? auteur.getNom().trim() : "";
            String full = (prenom + " " + nom).trim();
            if (!full.isBlank()) {
                return full;
            }
        }
        return direction != null && direction.getNom() != null ? direction.getNom() : marquePourDirection(direction).enTete();
    }

    private record DivisionMarque(String enTete, String signatureHtml, String piedPage) {}

    private DivisionMarque marquePourDirection(Entite direction) {
        if (direction == null || direction.getNom() == null) {
            return new DivisionMarque(
                    "Plateforme courriers",
                    "Cordialement.",
                    "Message automatique — merci de ne pas répondre à cet email.");
        }
        String n = normalizeNomEntite(direction.getNom());
        if (n.contains("FONDATION")) {
            return new DivisionMarque(
                    "Fondation Orange",
                    "Cordialement,<br/>Direction Fondation",
                    "Envoi automatique — Direction Fondation.");
        }
        if (n.contains("RSE")) {
            return new DivisionMarque(
                    "RSE Orange",
                    "Cordialement,<br/>Direction RSE",
                    "Envoi automatique — Direction RSE.");
        }
        if (n.contains("DCI") && !n.contains("DCIRE")) {
            return new DivisionMarque(
                    "DCI Orange",
                    "Cordialement,<br/>Direction DCI",
                    "Envoi automatique — Direction DCI.");
        }
        return new DivisionMarque(
                "Orange Digital Center",
                "Cordialement,<br/>Orange Digital Center",
                "Envoi automatique — Orange Digital Center.");
    }
}