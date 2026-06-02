package com.odk.Controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.odk.Entity.Courrier;
import com.odk.Entity.ReponseCourrier;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.StatutCourrier;
import com.odk.Service.Interface.Service.CourrierDashboardService;
import com.odk.Service.Interface.Service.CourrierService;
import com.odk.Service.Interface.Service.ReponseCourrierService;
import com.odk.Service.Interface.Service.UtilisateurService;
import com.odk.Entity.Entite;
import com.odk.dto.CourrierDTO;
import com.odk.dto.CourrierMetadonneesDTO;
import com.odk.dto.CourrierDashboardSerieDTO;
import com.odk.dto.CourrierDashboardTotalsDTO;
import com.odk.dto.EntiteDTO;
import com.odk.dto.EntiteMapper;
import com.odk.dto.ReponseCourrierDTO;
import com.odk.exception.CourrierValidationException;

@RestController
@RequestMapping("/api/courriers")
public class CourrierController {

    private final CourrierService courrierService;
    private final UtilisateurService utilisateurService;
    private final ReponseCourrierService reponseCourrierService;
    private final CourrierDashboardService courrierDashboardService;

    public CourrierController(
            CourrierService courrierService,
            UtilisateurService utilisateurService,
            ReponseCourrierService reponseCourrierService,
            CourrierDashboardService courrierDashboardService) {
        this.courrierService = courrierService;
        this.utilisateurService = utilisateurService;
        this.reponseCourrierService = reponseCourrierService;
        this.courrierDashboardService = courrierDashboardService;
    }

    /* ======================================================
     *  GESTION GLOBALE DES EXCEPTIONS DE VALIDATION
     * ====================================================== */
    @ExceptionHandler(CourrierValidationException.class)
    public ResponseEntity<String> handleCourrierValidationException(CourrierValidationException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("{\"error\": \"Validation échouée\", \"message\": \"" + e.getMessage() + "\"}");
    }

    @GetMapping("/dashboard/totaux")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','DIRECTEUR','DCIRE','DIRECTEUR_ODC','DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI')")
    public CourrierDashboardTotalsDTO courrierDashboardTotaux(
            @RequestParam(required = false) Long structureId,
            @AuthenticationPrincipal Utilisateur principal) {
        return courrierDashboardService.totaux(structureId, principal);
    }

    @GetMapping("/dashboard/serie")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','DIRECTEUR','DCIRE','DIRECTEUR_ODC','DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI')")
    public CourrierDashboardSerieDTO courrierDashboardSerie(
            @RequestParam(defaultValue = "semaine") String periode,
            @RequestParam(required = false) Long structureId,
            @AuthenticationPrincipal Utilisateur principal) {
        return courrierDashboardService.serie(periode, structureId, principal);
    }

    /* ======================================================
     *  PARTIE 1 : RÉCEPTION / ENREGISTREMENT DU COURRIER
     * ====================================================== */
    @PostMapping("/reception")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Courrier> receptionCourrier(
            @RequestParam String numero,
            @RequestParam String objet,
            @RequestParam String expediteur,
            @RequestParam Long directionId,
            @RequestParam(required = false) MultipartFile fichier
    ) throws IOException {

        CourrierDTO dto = new CourrierDTO();
        dto.setNumero(numero);
        dto.setObjet(objet);
        dto.setExpediteur(expediteur);
        dto.setDirectionId(directionId);
        dto.setFichier(fichier);

        Courrier courrier = courrierService.enregistrerCourrier(dto);
        return ResponseEntity.ok(courrier);
    }

    /* ======================================================
     *  PARTIE 2 : IMPUTATION PAR LE DIRECTEUR
     * ====================================================== */
    @GetMapping("/odc/directions-emission")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<List<EntiteDTO>> listerDirectionsEmissionOdc() {
        List<Entite> dirs = courrierService.listerDirectionsOdcPourBrouillon();
        return ResponseEntity.ok(
                dirs.stream().map(EntiteMapper::toDto).collect(Collectors.toList()));
    }

    @PostMapping("/odc/brouillon")
    @PreAuthorize("hasRole('DIRECTEUR')")
    public ResponseEntity<Courrier> brouillonOdc(
            @RequestParam Long odcDirectionId,
            @RequestParam String numero,
            @RequestParam String objet,
            @RequestParam String expediteur,
            @RequestParam(required = false) String destinataireOdc,
            @RequestParam(required = false) String externePrecision,
            @RequestParam(required = false) MultipartFile fichier
    ) throws IOException {
        CourrierDTO dto = new CourrierDTO();
        dto.setNumero(numero);
        dto.setObjet(objet);
        dto.setExpediteur(expediteur);
        dto.setDirectionId(odcDirectionId);
        dto.setDestinataireOdc(destinataireOdc);
        dto.setExternePrecision(externePrecision);
        dto.setFichier(fichier);
        return ResponseEntity.ok(courrierService.creerBrouillonOdc(odcDirectionId, dto));
    }

    @PostMapping("/odc/{id}/valider-transmission-dcire")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Courrier> validerTransmissionDcire(@PathVariable Long id) {
        return ResponseEntity.ok(courrierService.validerTransmissionVersDcire(id));
    }

    @PostMapping("/odc/{id}/resoumettre-revision")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Courrier> resoumettreRevision(@PathVariable Long id) {
        return ResponseEntity.ok(courrierService.resoumettreApresRevisionAdmin(id));
    }

    @GetMapping("/odc-directeur/en-cours-validation")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<List<Courrier>> listerCourriersValidationDirecteurOdc() {
        return ResponseEntity.ok(courrierService.listerPourValidationDirecteurOdc());
    }

    @PostMapping("/odc-directeur/{id}/suggestion")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Courrier> suggestionDirecteurOdc(
            @PathVariable Long id,
            @RequestParam String texte) {
        return ResponseEntity.ok(courrierService.enregistrerSuggestionDirecteurOdc(id, texte));
    }

    @PostMapping("/odc-directeur/{id}/annuler")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Courrier> annulerCourrierDirecteurOdc(@PathVariable Long id) {
        return ResponseEntity.ok(courrierService.annulerCourrierParDirecteurOdc(id));
    }

    /** Pièce jointe en consultation seule (sans changer le statut du courrier). */
    @GetMapping("/odc-directeur/{id}/fichier")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<InputStreamResource> fichierValidationDirecteurOdc(@PathVariable Long id) throws IOException {
        return courrierService.telechargerPourValidationDirecteurOdc(id);
    }

    @GetMapping("/structure-directeur/en-attente-validation")
    @PreAuthorize("hasAnyRole('DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI','SUPERADMIN','ADMIN')")
    public ResponseEntity<List<Courrier>> listerAttenteValidationStructure(
            @AuthenticationPrincipal Utilisateur utilisateur) {
        return ResponseEntity.ok(courrierService.listerPourValidationDirecteurStructure(utilisateur));
    }

    @GetMapping("/structure-directeur/tableau")
    @PreAuthorize("hasAnyRole('DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI','SUPERADMIN','ADMIN')")
    public ResponseEntity<Map<String, List<Courrier>>> tableauStructure(@AuthenticationPrincipal Utilisateur u) {
        return ResponseEntity.ok(courrierService.tableauStructureCourriers(u));
    }

    @GetMapping("/structure-directeur/cibles-internes")
    @PreAuthorize("hasAnyRole('DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI','SUPERADMIN','ADMIN')")
    public ResponseEntity<List<EntiteDTO>> ciblesInternesStructure(@AuthenticationPrincipal Utilisateur u) {
        return ResponseEntity.ok(
                courrierService.listerDirectionsCiblesInternesPourStructure(u).stream()
                        .map(EntiteMapper::toDto)
                        .collect(Collectors.toList()));
    }

    @PostMapping("/structure-directeur/{id}/valider-reception")
    @PreAuthorize("hasAnyRole('DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI','SUPERADMIN','ADMIN')")
    public ResponseEntity<Courrier> validerReceptionStructure(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur utilisateur) {
        return ResponseEntity.ok(courrierService.validerReceptionParDirecteurStructure(id, utilisateur));
    }

    @PostMapping("/structure-directeur/{id}/accuser-reception")
    @PreAuthorize("hasAnyRole('DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI','SUPERADMIN','ADMIN')")
    public ResponseEntity<Courrier> accuserReceptionStructure(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur utilisateur) {
        return ResponseEntity.ok(courrierService.accuserReceptionOperationnelle(id, utilisateur));
    }

    @PostMapping("/structure-directeur/courrier-interne")
    @PreAuthorize("hasAnyRole('DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI','SUPERADMIN','ADMIN')")
    public ResponseEntity<Courrier> courrierInterneDepuisStructure(
            @RequestParam Long cibleDirectionId,
            @RequestParam String numero,
            @RequestParam String objet,
            @RequestParam String expediteur,
            @RequestParam(required = false) MultipartFile fichier,
            @AuthenticationPrincipal Utilisateur utilisateur
    ) throws IOException {
        CourrierDTO dto = new CourrierDTO();
        dto.setNumero(numero);
        dto.setObjet(objet);
        dto.setExpediteur(expediteur);
        dto.setDirectionId(cibleDirectionId);
        dto.setFichier(fichier);
        return ResponseEntity.ok(
                courrierService.enregistrerCourrierInterneDepuisMaStructure(cibleDirectionId, dto, utilisateur));
    }

    @PostMapping("/structure-directeur/courrier-externe")
    @PreAuthorize("hasAnyRole('DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI','SUPERADMIN','ADMIN')")
    public ResponseEntity<Courrier> courrierExterneDepuisStructure(
            @RequestParam String numero,
            @RequestParam String objet,
            @RequestParam String expediteur,
            @RequestParam(required = false) String externePrecision,
            @RequestParam(required = false) MultipartFile fichier,
            @AuthenticationPrincipal Utilisateur utilisateur
    ) throws IOException {
        CourrierDTO dto = new CourrierDTO();
        dto.setNumero(numero);
        dto.setObjet(objet);
        dto.setExpediteur(expediteur);
        dto.setExternePrecision(externePrecision);
        dto.setFichier(fichier);
        return ResponseEntity.ok(courrierService.enregistrerCourrierExterneDepuisMaStructure(dto, utilisateur));
    }

    @GetMapping("/odc/cibles-internes")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<List<EntiteDTO>> ciblesInternesOdc(@RequestParam Long origineDirectionId) {
        return ResponseEntity.ok(
                courrierService.listerDirectionsCiblesInternesPourOdc(origineDirectionId).stream()
                        .map(EntiteMapper::toDto)
                        .collect(Collectors.toList()));
    }

    @PostMapping("/division-interne")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC') "
            + "or hasRole('DIRECTEUR_FONDATION') or hasRole('DIRECTEUR_RSE') or hasRole('DIRECTEUR_DCI')")
    public ResponseEntity<Courrier> courrierInterneDivision(
            @RequestParam Long origineDirectionId,
            @RequestParam Long cibleDirectionId,
            @RequestParam String numero,
            @RequestParam String objet,
            @RequestParam String expediteur,
            @RequestParam(required = false) MultipartFile fichier
    ) throws IOException {
        CourrierDTO dto = new CourrierDTO();
        dto.setNumero(numero);
        dto.setObjet(objet);
        dto.setExpediteur(expediteur);
        dto.setDirectionId(cibleDirectionId);
        dto.setFichier(fichier);
        return ResponseEntity.ok(courrierService.enregistrerCourrierInterneDivision(origineDirectionId, cibleDirectionId, dto));
    }

    @GetMapping("/odc/{directionId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<List<Courrier>> listerPourOdc(
            @PathVariable Long directionId,
            @RequestParam(defaultValue = "OPERATIONNEL") String vue
    ) {
        return ResponseEntity.ok(courrierService.listerPourOdc(directionId, vue));
    }

    @GetMapping("/dcire")
    @PreAuthorize("hasRole('DIRECTEUR')")
    public ResponseEntity<List<Courrier>> listerPourDcire() {
        return ResponseEntity.ok(courrierService.listerPourDcire());
    }

    @GetMapping("/dcire/cibles-division")
    @PreAuthorize("hasRole('DIRECTEUR')")
    public ResponseEntity<List<EntiteDTO>> ciblesEmissionDcire() {
        return ResponseEntity.ok(
                courrierService.listerCiblesEmissionDcire().stream()
                        .map(EntiteMapper::toDto)
                        .collect(Collectors.toList()));
    }

    /** Seule la DCIRE émet des courriers vers les structures de la division. */
    @PostMapping("/dcire/emission")
    @PreAuthorize("hasRole('DIRECTEUR')")
    public ResponseEntity<Courrier> emissionDcire(
            @RequestParam Long cibleDirectionId,
            @RequestParam(required = false) String numero,
            @RequestParam String objet,
            @RequestParam(required = false) String expediteur,
            @RequestParam(required = false) MultipartFile fichier
    ) throws IOException {
        CourrierDTO dto = new CourrierDTO();
        dto.setNumero(numero);
        dto.setObjet(objet);
        dto.setExpediteur(expediteur != null ? expediteur : "KEÏTA DCIRE");
        dto.setDirectionId(cibleDirectionId);
        dto.setFichier(fichier);
        return ResponseEntity.ok(courrierService.emettreCourrierParDcire(cibleDirectionId, dto));
    }

    @GetMapping("/responsable-odk/courriers/en-attente")
    @PreAuthorize("hasRole('RESPONSABLE_ODK')")
    public ResponseEntity<List<Courrier>> courriersEnAttenteResponsableOdk() {
        return ResponseEntity.ok(courrierService.listerCourriersEnAttenteResponsableOdk());
    }

    @GetMapping("/responsable-odk/courriers-delegues")
    @PreAuthorize("hasRole('RESPONSABLE_ODK')")
    public ResponseEntity<List<Courrier>> courriersDeleguesResponsableOdk() {
        return ResponseEntity.ok(courrierService.listerCourriersDeleguesResponsableOdk());
    }

    @GetMapping("/responsable-odk/services-odc")
    @PreAuthorize("hasRole('RESPONSABLE_ODK')")
    public ResponseEntity<List<EntiteDTO>> servicesOdcResponsable() {
        return ResponseEntity.ok(
                courrierService.listerServicesOdcPourResponsable().stream()
                        .map(EntiteMapper::toDto)
                        .collect(Collectors.toList()));
    }

    @PostMapping("/responsable-odk/courriers/{id}/affecter-service")
    @PreAuthorize("hasRole('RESPONSABLE_ODK')")
    public ResponseEntity<Courrier> affecterServiceResponsableOdk(
            @PathVariable Long id,
            @RequestParam Long serviceEntiteId,
            @RequestParam(required = false) String note
    ) throws IOException {
        return ResponseEntity.ok(courrierService.affecterCourrierAuServiceParResponsable(id, serviceEntiteId, note));
    }

    @GetMapping("/odc-directeur/services-odc")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<List<EntiteDTO>> servicesOdcPourDirecteur() {
        return ResponseEntity.ok(
                courrierService.listerServicesOdcPourResponsable().stream()
                        .map(EntiteMapper::toDto)
                        .collect(Collectors.toList()));
    }

    @GetMapping("/odc-directeur/reponses-en-attente")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<List<Courrier>> reponsesEnAttenteDirecteurOdc() {
        return ResponseEntity.ok(courrierService.listerCourriersReponseEnAttenteDirecteurOdc());
    }

    @PostMapping("/odc-directeur/{id}/valider-reponse")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Courrier> validerReponseDirecteurOdc(
            @PathVariable Long id,
            @RequestParam(required = false) String suggestion) {
        return ResponseEntity.ok(courrierService.validerReponseParDirecteurOdc(id, suggestion));
    }

    @PostMapping("/odc-directeur/{id}/deleguer-service")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Courrier> deleguerServiceDirecteurOdc(
            @PathVariable Long id,
            @RequestParam Long serviceEntiteId,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(courrierService.deleguerCourrierAuServiceParDirecteurOdc(id, serviceEntiteId, note));
    }

    @PostMapping("/odc-directeur/{id}/deleguer-responsable-odk")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Courrier> deleguerResponsableOdkDirecteurOdc(
            @PathVariable Long id,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(courrierService.deleguerAuResponsableOdkParDirecteurOdc(id, note));
    }

    @PostMapping("/odc-directeur/{id}/confirmer-envoi-physique")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Courrier> confirmerEnvoiPhysiqueDirecteurOdc(@PathVariable Long id) {
        return ResponseEntity.ok(courrierService.confirmerEnvoiPhysiqueParDirecteurOdc(id));
    }

    @PostMapping("/dcire/{id}/valider-decharge-reponse")
    @PreAuthorize("hasRole('DIRECTEUR')")
    public ResponseEntity<Courrier> validerDechargeReponseDcire(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur utilisateur) {
        return ResponseEntity.ok(courrierService.validerDechargeReponseParDcire(id, utilisateur));
    }

    @PostMapping("/dcire/reception-externe")
    @PreAuthorize("hasRole('DIRECTEUR')")
    public ResponseEntity<Courrier> receptionExterneDcire(
            @RequestParam Long structureOrigineId,
            @RequestParam String numero,
            @RequestParam String objet,
            @RequestParam String expediteur,
            @RequestParam(required = false) MultipartFile fichier
    ) throws IOException {
        CourrierDTO dto = new CourrierDTO();
        dto.setNumero(numero);
        dto.setObjet(objet);
        dto.setExpediteur(expediteur);
        dto.setDirectionId(structureOrigineId);
        dto.setFichier(fichier);
        return ResponseEntity.ok(courrierService.receptionExterneDepuisStructure(structureOrigineId, dto));
    }

    @PostMapping("/dcire/{id}/transmettre-odc")
    @PreAuthorize("hasRole('DIRECTEUR')")
    public ResponseEntity<Courrier> transmettreVersOdc(
            @PathVariable Long id,
            @RequestParam Long odcDirectionId
    ) {
        return ResponseEntity.ok(courrierService.transmettreVersOdc(id, odcDirectionId));
    }

    @PostMapping("/dcire/{id}/valider-expedition-externe")
    @PreAuthorize("hasRole('DIRECTEUR')")
    public ResponseEntity<Courrier> validerExpeditionExterneDcire(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur utilisateur) {
        return ResponseEntity.ok(courrierService.validerExpeditionExterneParDcire(id, utilisateur));
    }

    @PutMapping("/{id}/imputer")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Courrier> imputerCourrier(
            @PathVariable Long id,
            @RequestParam Long entiteCibleId,
            @RequestParam(required = false) Utilisateur utilisateurCible
    ){
        Courrier courrier = courrierService.imputerCourrier(id, entiteCibleId, utilisateurCible);
        return ResponseEntity.ok(courrier);

    }

    /* ======================================================
     *  PARTIE 3 : OUVERTURE / DÉBUT DE TRAITEMENT
     * ====================================================== */
    @GetMapping("/{id}/ouvrir")
    public ResponseEntity<InputStreamResource> ouvrirCourrier(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur utilisateur
    ) throws IOException {
        return courrierService.ouvrirCourrier(id, utilisateur);
    }

    /* ======================================================
     *  PARTIE 4 : ARCHIVAGE
     * ====================================================== */
    @PatchMapping("/archiver/{id}")
    public ResponseEntity<Void> archiverCourrier(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur utilisateur
    ) {
        courrierService.archiverCourrier(id, utilisateur);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('DIRECTEUR_ODC') or hasRole('DIRECTEUR')")
    public ResponseEntity<Void> supprimerCourrier(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur utilisateur) {
        courrierService.supprimerCourrierParDirecteurStructure(id, utilisateur);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/structure-directeur/{id}")
    @PreAuthorize("hasAnyRole('DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI','SUPERADMIN','ADMIN')")
    public ResponseEntity<Void> supprimerCourrierStructureDirecteur(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur utilisateur) {
        courrierService.supprimerCourrierParDirecteurStructure(id, utilisateur);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/metadonnees")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','DIRECTEUR','DIRECTEUR_ODC','DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI')")
    public ResponseEntity<Courrier> patchMetadonneesCourrier(
            @PathVariable Long id,
            @RequestBody CourrierMetadonneesDTO dto,
            @AuthenticationPrincipal Utilisateur utilisateur) {
        return ResponseEntity.ok(courrierService.mettreAJourMetadonneesCourrier(id, dto, utilisateur));
    }

    /* ======================================================
     *  PARTIE 5 : LISTE DES COURRIERS ACTIFS
     * ====================================================== */
    @GetMapping("/actifs/{entiteId}")
    public ResponseEntity<List<Courrier>> courriersActifs(@PathVariable Long entiteId) {
        List<Courrier> courriers = courrierService.courriersActifs(entiteId);
        return ResponseEntity.ok(courriers);
    }

    /* ======================================================
     *  PARTIE 6 : LISTE DES COURRIERS ARCHIVÉS
     * ====================================================== */
    @GetMapping("/archives/{entiteId}")
    public ResponseEntity<List<Courrier>> courriersArchives(@PathVariable Long entiteId) {
        List<Courrier> courriers = courrierService.courriersArchives(entiteId);
        return ResponseEntity.ok(courriers);
    }

    /* ======================================================
     *  PARTIE 7 : RÉPONSE AUX COURRIERS
     * ====================================================== */
    @PostMapping("/reponse")
    public ResponseEntity<ReponseCourrier> repondreCourrier(
            @RequestParam Long courrierId,
            @RequestParam String email,
            @RequestParam String objet,
            @RequestParam String message,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) List<MultipartFile> attachments,
            @AuthenticationPrincipal Utilisateur utilisateur
    ) throws IOException {

        ReponseCourrierDTO dto = new ReponseCourrierDTO();
        dto.setCourrierId(courrierId);
        dto.setEmail(email);
        dto.setObjet(objet);
        dto.setMessage(message);
        dto.setFile(file);
        dto.setAttachments(attachments);

        try {
            ReponseCourrier reponse = reponseCourrierService.repondreCourrier(dto, utilisateur);
            return ResponseEntity.ok(reponse);
        } catch (CourrierValidationException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(null);
        }
    }

    @GetMapping("/{courrierId}/reponses")
    public ResponseEntity<List<ReponseCourrier>> getReponses(@PathVariable Long courrierId) {
        List<ReponseCourrier> reponses = reponseCourrierService.getReponsesByCourrier(courrierId);
        return ResponseEntity.ok(reponses);
    }

    @GetMapping("/{courrierId}/has-reponded")
    public ResponseEntity<Boolean> hasUserResponded(
            @PathVariable Long courrierId,
            @RequestParam String email
    ) {
        boolean hasResponded = reponseCourrierService.hasUserResponded(courrierId, email);
        return ResponseEntity.ok(hasResponded);
    }

    /* ======================================================
     *  PARTIE 8 : FILTRAGE DES COURRIERS PAR STATUT
     * ====================================================== */
    @GetMapping("/{statut}/{entiteId}")
    public ResponseEntity<List<Courrier>> getCourriersByStatut(
            @PathVariable String statut,
            @PathVariable Long entiteId
    ) {
        try {
            StatutCourrier statutCourrier = StatutCourrier.valueOf(statut.toUpperCase());
            List<Courrier> courriers = courrierService.getCourriersByStatutAndEntite(statutCourrier, entiteId);
            return ResponseEntity.ok(courriers);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(null);
        }
    }
}