package com.odk.Controller;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.odk.Entity.Courrier;
import com.odk.Entity.ReponseCourrier;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.StatutCourrier;
import com.odk.Service.Interface.Service.CourrierService;
import com.odk.Service.Interface.Service.ReponseCourrierService;
import com.odk.Service.Interface.Service.UtilisateurService;
import com.odk.dto.CourrierDTO;
import com.odk.dto.ReponseCourrierDTO;
import com.odk.exception.CourrierValidationException;

@RestController
@RequestMapping("/api/courriers")
public class CourrierController {

    private final CourrierService courrierService;
    private final UtilisateurService utilisateurService;
    private final ReponseCourrierService reponseCourrierService;

    public CourrierController(CourrierService courrierService,UtilisateurService utilisateurService, ReponseCourrierService reponseCourrierService) {
        this.courrierService = courrierService;
        this.utilisateurService = utilisateurService;
        this.reponseCourrierService = reponseCourrierService;
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

    /* ======================================================
     *  PARTIE 1 : RÉCEPTION / ENREGISTREMENT DU COURRIER
     * ====================================================== */
    @PostMapping("/reception")
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
    @PutMapping("/{id}/imputer")
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
            @RequestParam(required = false) List<MultipartFile> attachments
    ) throws IOException {
        
        ReponseCourrierDTO dto = new ReponseCourrierDTO();
        dto.setCourrierId(courrierId);
        dto.setEmail(email);
        dto.setObjet(objet);
        dto.setMessage(message);
        dto.setFile(file);
        dto.setAttachments(attachments);

        try {
            ReponseCourrier reponse = reponseCourrierService.repondreCourrier(dto);
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