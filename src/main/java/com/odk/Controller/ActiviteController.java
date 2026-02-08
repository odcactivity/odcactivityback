package com.odk.Controller;

import com.odk.Entity.Activite;
import com.odk.Entity.Etape;
import com.odk.Enum.Statut;
import com.odk.Repository.ActiviteRepository;
import com.odk.Repository.EtapeRepository;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@AllArgsConstructor
@RequestMapping("/activite")
@CrossOrigin(origins = "http://localhost:4200")
public class ActiviteController {

    private final ActiviteRepository activiteRepository;
    private ActiviteService activiteService;
    private EtapeRepository etapeRepository;
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
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public List<ActiviteDTO> listerActivite() {
//        System.out.println("activite dto===="+activiteMapper.listeActivite(activiteService.List()));
        return activiteMapper.listeActivite(activiteService.List());

    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public ActiviteDTO getActiviteParId(@PathVariable Long id) {
        try {
            return activiteMapper.ACTIVITE_DTO(activiteService.findById(id).get());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors de la récupération de l'activité par ID", e);
        }
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
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN')")
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
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN')")
    public ResponseEntity<Long> getNombreActivite() {
        long count = activiteRepository.count();
        return ResponseEntity.ok(count); // Retourne le nombre d'utilisateurs
    }

    @GetMapping("/nombreActivitesEncours")
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN')")
    public ResponseEntity<Long> getNombreActivitesEncours() {
        long count = activiteRepository.countByStatut(Statut.En_Cours); // Compte les activités avec statut "En_Cours"
        return ResponseEntity.ok(count); // Retourne le nombre d'activités
    }

    @GetMapping("/nombreActivitesEnAttente")
    public ResponseEntity<Long> getNombreActivitesEnAttente() {
        long count = activiteRepository.countByStatut(Statut.En_Attente); // Compte les activités avec statut "En_Cours"
        return ResponseEntity.ok(count); // Retourne le nombre d'activités
    }

    @GetMapping("/nombreActivitesTerminer")
    @PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN')")
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

