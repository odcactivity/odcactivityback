package com.odk.Controller;

import com.odk.Entity.Activite;
import com.odk.Entity.Etape;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.Statut;
import com.odk.Repository.ActiviteRepository;
import com.odk.Repository.EtapeRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.Service.Interface.Service.ActiviteService;
import com.odk.dto.ActiviteDTO;
import com.odk.dto.ActiviteMapper;
import com.odk.dto.ActiviteValidationDTO;
import com.odk.dto.EtapeMapper;
import com.odk.dto.ParticipantDTO;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;
@RestController
@AllArgsConstructor
@RequestMapping("/activite")
@CrossOrigin(origins = "http://localhost:4200")
public class ActiviteController {

    private final ActiviteRepository activiteRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;
    private final EtapeRepository etapeRepository;
    private final EtapeMapper etapeMapper;
     private final ActiviteMapper activiteMapper;
  
    

    @PostMapping
    @PreAuthorize("hasRole('PERSONNEL')")
    public Activite ajouter(@RequestBody Activite activite) {
        try {
            return activiteService.add(activite);
        } catch (ResponseStatusException e) {
            throw e; // Laissez passer l'exception si elle provient de la méthode add
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors de l'ajout de l'activité", e);
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    @ResponseStatus(HttpStatus.OK)
    public List<ActiviteDTO> listerActivite() {
        List<Activite> all = new ArrayList<>(activiteService.List());
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        utilisateurRepository.findByEmail(email).ifPresent(u -> {
            if (u.getRole() != null && "PERSONNEL".equalsIgnoreCase(u.getRole().getNom())) {
                all.removeIf(a -> (a.getStatut() == Statut.En_Validation_Directeur_ODC
                        || a.getStatut() == Statut.En_Validation_Responsable_ODK)
                        && (a.getCreatedBy() == null || !a.getCreatedBy().getId().equals(u.getId())));
            }
        });
        return activiteMapper.listeActivite(all);
    }

    @GetMapping("/en-attente-validation-directeur-odc")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public List<ActiviteDTO> listerEnAttenteValidationDirecteurOdc() {
        return activiteMapper.listeActivite(activiteService.listerEnAttenteValidationDirecteurOdc());
    }

    @GetMapping("/directeur-odc/historique-validees")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public List<ActiviteDTO> historiqueValideesDirecteurOdc() {
        return activiteMapper.listeActivite(activiteService.listerHistoriqueValideesParDirecteurOdc());
    }

    @GetMapping("/directeur-odc/historique-refusees")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public List<ActiviteDTO> historiqueRefuseesDirecteurOdc() {
        return activiteMapper.listeActivite(activiteService.listerHistoriqueRefuseesParDirecteurOdc());
    }

    @PostMapping("/{id}/valider-directeur-odc")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ActiviteDTO validerDirecteurOdc(@PathVariable Long id) {
        return activiteMapper.ACTIVITE_DTO(activiteService.validerParDirecteurOdc(id));
    }

    @PostMapping("/{id}/rejeter-directeur-odc")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ActiviteDTO rejeterDirecteurOdc(@PathVariable Long id) {
        return activiteMapper.ACTIVITE_DTO(activiteService.rejeterParDirecteurOdc(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    @ResponseStatus(HttpStatus.OK)
    public ActiviteDTO getActiviteParId(@PathVariable Long id) {
        try {
            Activite a = activiteService.findById(id).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité introuvable"));
            assertPersonnelPeutVoirActiviteEnValidation(a);
            return activiteMapper.ACTIVITE_DTO(a);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors de la récupération de l'activité par ID", e);
        }
    }

    private void assertPersonnelPeutVoirActiviteEnValidation(Activite activite) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Utilisateur u = utilisateurRepository.findByEmail(email).orElse(null);
        if (u == null || u.getRole() == null || !"PERSONNEL".equalsIgnoreCase(u.getRole().getNom())) {
            return;
        }
        if ((activite.getStatut() == Statut.En_Validation_Directeur_ODC
                || activite.getStatut() == Statut.En_Validation_Responsable_ODK)
                && (activite.getCreatedBy() == null || !activite.getCreatedBy().getId().equals(u.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé à cette activité.");
        }
    }

    @GetMapping("/responsable-odk/en-attente")
    @PreAuthorize("hasRole('RESPONSABLE_ODK')")
    public List<ActiviteDTO> listerEnAttenteResponsableOdk() {
        return activiteMapper.listeActivite(activiteService.listerEnAttenteResponsableOdk());
    }

    @PostMapping("/{id}/transmettre-directeur-odc")
    @PreAuthorize("hasRole('RESPONSABLE_ODK')")
    public ActiviteDTO transmettreDirecteurOdc(
            @PathVariable Long id,
            @RequestParam(required = false) String note) {
        return activiteMapper.ACTIVITE_DTO(activiteService.transfererAuDirecteurOdcParResponsable(id, note));
    }

    @PostMapping("/{id}/retour-personnel-responsable")
    @PreAuthorize("hasRole('RESPONSABLE_ODK')")
    public ActiviteDTO retourPersonnelResponsable(
            @PathVariable Long id,
            @RequestParam String note) {
        return activiteMapper.ACTIVITE_DTO(activiteService.retournerAuPersonnelParResponsable(id, note));
    }

    @PostMapping("/{id}/suggestion-directeur-odc")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public ActiviteDTO suggestionDirecteurOdcActivite(
            @PathVariable Long id,
            @RequestParam String suggestion) {
        return activiteMapper.ACTIVITE_DTO(activiteService.enregistrerSuggestionDirecteurOdcActivite(id, suggestion));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('PERSONNEL')")
    @ResponseStatus(HttpStatus.OK)
    public Activite modifier(@PathVariable Long id, @RequestBody Activite activite) {
            return activiteService.update(activite, id);
    }
   
    
    @PutMapping("/{id}/{listeEtape}")
    @PreAuthorize("hasRole('PERSONNEL')")
    @ResponseStatus(HttpStatus.OK)
    public Activite modifierP(@PathVariable Long id,@PathVariable List<Long> listeEtape, @RequestBody ActiviteDTO activite) {
        System.out.println("dans modifierP++++++++++"+listeEtape);
//            return activiteService.update(activite, id);
            return activiteService.updateDTO(activite,listeEtape, id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PERSONNEL')")
    public ResponseEntity<Map<String, String>> deleteActivite(@PathVariable Long id) {
        activiteService.delete(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Activité supprimée avec succès");
        return ResponseEntity.ok(response);
    }
   /* public void deleteActivite(@PathVariable Long id) {
        activiteService.delete(id);
    }*/


    @GetMapping("/enCours")
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    public List<ActiviteDTO> listerActiviteEncours() {
        return activiteService.List().stream()
                .map(activite -> {
                    System.out.println("Traitement de l'activité: " + activite.getNom());

                    List<ParticipantDTO> listeResultatDTO = new ArrayList<>();

                    // Filtrer les étapes en cours et remplir les listes de participants uniquement si l'étape est en cours
                    boolean hasEtapeEnCours = activite.getEtapes().stream()
                            .filter(etape -> Statut.En_Cours.equals(etape.getStatut()))
                            .peek(etape -> {
                                System.out.println("Étape valide en cours trouvée : " + etape.getNom());

                            })
                            .findAny()
                            .isPresent();

                    // Retourner l'ActiviteDTO seulement si une étape en cours est présente
                    if (hasEtapeEnCours) {
                        System.out.println("Activité avec étape EN_COURS trouvée: " + activite.getNom());
                        return new ActiviteDTO(
                                activite.getId(),
                                activite.getNom(),
                                activite.getTitre(),
                                activite.getDateDebut(),
                                activite.getDateFin(),
                                activite.getStatut(),
                                activite.getLieu(),
                                activite.getDescription(),
                                activite.getObjectifParticipation(),//                                
                                activite.getEntite(),
                               
                                activite.getSalleId(),
                                activite.getCreatedBy(),
                                activite.getTypeActivite()
//                               
                        );
                    }
                    System.out.println("Aucune étape EN_COURS pour l'activité: " + activite.getNom());
                    return null;
                })
                .filter(Objects::nonNull) // Supprimer les ActiviteDTO null (sans étape en cours)
                .collect(Collectors.toList());
    }

    @GetMapping("/nombre") // Pas de paramètres
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Long> getNombreActivite() {
        long count = activiteRepository.count();
        return ResponseEntity.ok(count); // Retourne le nombre d'utilisateurs
    }

    @GetMapping("/nombreActivitesEncours")
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Long> getNombreActivitesEncours() {
        long count = activiteRepository.countByStatut(Statut.En_Cours); // Compte les activités avec statut "En_Cours"
        return ResponseEntity.ok(count); // Retourne le nombre d'activités
    }

    @GetMapping("/nombreActivitesEnAttente")
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Long> getNombreActivitesEnAttente() {
        long count = activiteRepository.countByStatutIn(
                Arrays.asList(Statut.En_Attente, Statut.En_Validation_Directeur_ODC));
        return ResponseEntity.ok(count);
    }

    @GetMapping("/nombreActivitesTerminer")
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Long> getNombreActivitesTerminer() {
        long count = activiteRepository.countByStatut(Statut.Termine); // Compte les activités avec statut "En_Cours"
        return ResponseEntity.ok(count); // Retourne le nombre d'activités
    }
     @GetMapping("/superviseur/{id}")
    public ResponseEntity<List<ActiviteDTO>> getActivitesBySuperviseur(@PathVariable("id") Long superviseurId) {
        List<Activite> activites = activiteService.getActivitesBySuperviseur(superviseurId);
        List<ActiviteDTO> activiteDTOS = activites.stream().map(activite -> {
        // Mapper les validations associées
        List<ActiviteValidationDTO> validationsDTO = activite.getValidations().stream()
            .map(validation -> new ActiviteValidationDTO(
               validation.getId(),
                validation.getCommentaire(),
                validation.getDate(),
                validation.getStatut(),       // doit être StatutValidation
                validation.getFichierjoint(),
                validation.getEnvoyeurId(),
                activite.getId(),
                (validation.getSuperviseur()!=null) ? validation.getSuperviseur().getId() :null
                )).toList();

        // Créer le DTO de l'activité en incluant la liste mappée
        return new ActiviteDTO(
            activite.getId(),
            validationsDTO,  // ici la liste de DTO
            activite.getNom(),
            activite.getDateDebut(),
            activite.getDateFin(),
            activite.getStatut(),
            activite.getLieu(),
            activite.getDescription(),
            activite.getObjectifParticipation(),
            activite.getEntite(),
            activite.getSalleId(),
            activite.getCreatedBy(),
            activite.getTypeActivite()
        );
    }).toList();

    return ResponseEntity.ok(activiteDTOS); 
        
    }

    /**
     * ✅ Récupère toutes les activités en attente de validation pour un superviseur donné
     */
    @GetMapping("/superviseur/{id}/attente")
    public ResponseEntity<List<ActiviteDTO>> getActivitesEnAttenteBySuperviseur(@PathVariable("id") Long superviseurId) {
        List<Activite> activites = activiteService.getActivitesBySuperviseurAttente(superviseurId);
            List<ActiviteDTO> activiteDTOS = activites.stream().map(activite -> {
        // Mapper les validations associées
        List<ActiviteValidationDTO> validationsDTO = activite.getValidations().stream()
            .map(validation -> new ActiviteValidationDTO(
                validation.getId(),
                validation.getCommentaire(),
                validation.getDate(),
                validation.getStatut(),
                validation.getFichierjoint(),
                validation.getEnvoyeurId(),
                activite.getId(),
                validation.getSuperviseur().getId())).toList();

        // Créer le DTO de l'activité en incluant la liste mappée
        return new ActiviteDTO(
            activite.getId(),
            validationsDTO,  // ici la liste de DTO
            activite.getNom(),
            activite.getDateDebut(),
            activite.getDateFin(),
            activite.getStatut(),
            activite.getLieu(),
            activite.getDescription(),
            activite.getObjectifParticipation(),
            activite.getEntite(),
            activite.getSalleId(),
            activite.getCreatedBy(),
            activite.getTypeActivite()
        );
    }).toList();

    return ResponseEntity.ok(activiteDTOS); 
    }
    
    
    //Statistiques par USER
    
     @GetMapping("/enCours/{userId}")
    @PreAuthorize("hasRole('PERSONNEL') ")
    public List<ActiviteDTO> listerActiviteEncoursByUser(@PathVariable("userId") Long userId ) {
         System.out.println("activite by user========"+userId);
        return activiteService.ListByUser(userId).stream()
                .map(activite -> {
                    System.out.println("Traitement de l'activitéby user: " + activite.getNom());

//                    List<ParticipantDTO> listeDebutDTO = new ArrayList<>();
//                    List<ParticipantDTO> listeResultatDTO = new ArrayList<>();

                    // Filtrer les étapes en cours et remplir les listes de participants uniquement si l'étape est en cours
                    boolean hasEtapeEnCours = activite.getEtapes().stream()
                            .filter(etape -> Statut.En_Cours.equals(etape.getStatut()))
                            .peek(etape -> {
                                System.out.println("Étape valide en cours trouvée : " + etape.getNom());
//                                listeDebutDTO.addAll(etape.getListeDebut().stream()
//                                        .map(participant -> new ParticipantDTO(participant.getId(), participant.getNom()))
//                                        .toList());
//                                listeResultatDTO.addAll(etape.getListeResultat().stream()
//                                        .map(participant -> new ParticipantDTO(participant.getId(), participant.getNom()))
//                                        .toList());
                            })
                            .findAny()
                            .isPresent();

                    // Retourner l'ActiviteDTO seulement si une étape en cours est présente
                    if (hasEtapeEnCours) {
                        System.out.println("Activité avec étape EN_COURS trouvée: " + activite.getNom());
                        return new ActiviteDTO(
                                activite.getId(),
                                activite.getNom(),
                                activite.getTitre(),
                                activite.getDateDebut(),
                                activite.getDateFin(),
                                activite.getStatut(),
                                activite.getLieu(),
                                activite.getDescription(),
                                activite.getObjectifParticipation(),//                                
                                activite.getEntite(),
                               
                                activite.getSalleId(),
                                activite.getCreatedBy(),
                                activite.getTypeActivite()
//                               
                        );
                    }
                    System.out.println("Aucune étape EN_COURS pour l'activité: " + activite.getNom());
                    return null;
                })
                .filter(Objects::nonNull) // Supprimer les ActiviteDTO null (sans étape en cours)
                .collect(Collectors.toList());
    }

    @GetMapping("/nombre/{userId}") // Pas de paramètres
    @PreAuthorize("hasRole('PERSONNEL')")
    public ResponseEntity<Long> getNombreActivite(@PathVariable("userId") Long userId) {
        System.out.println("activite by user========"+userId);
        long count = activiteRepository.countActivitesByUserCustom(userId);
        return ResponseEntity.ok(count); // Retourne le nombre d'utilisateurs
    }

    @GetMapping("/nombreActivitesEncours/{userId}")
    @PreAuthorize("hasRole('PERSONNEL')")
    public ResponseEntity<Long> getNombreActivitesEncours(@PathVariable("userId") Long userId) {
        System.out.println("activite by user========"+userId);
        long count = activiteRepository.countByUserByStatutCustom(Statut.En_Cours,userId); // Compte les activités avec statut "En_Cours"
        return ResponseEntity.ok(count); // Retourne le nombre d'activités
    }

    @GetMapping("/nombreActivitesEnAttente/{userId}")
    public ResponseEntity<Long> getNombreActivitesEnAttente(@PathVariable("userId") Long userId) {
        System.out.println("activite by user========"+userId);
        long count = activiteRepository.countByUserByStatutCustom(Statut.En_Attente,userId); // Compte les activités avec statut "En_Cours"
        return ResponseEntity.ok(count); // Retourne le nombre d'activités
    }

    @GetMapping("/nombreActivitesTerminer/{userId}")
    @PreAuthorize("hasRole('PERSONNEL')")
    public ResponseEntity<Long> getNombreActivitesTerminer(@PathVariable("userId") Long userId) {
        System.out.println("activite by user========"+userId);
        long count = activiteRepository.countByUserByStatutCustom(Statut.Termine,userId); // Compte les activités avec statut "En_Cours"
        return ResponseEntity.ok(count); // Retourne le nombre d'activités
    }
}

