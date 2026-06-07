package com.odk.Controller;

import com.odk.Entity.Role;
import com.odk.Entity.Utilisateur;
import com.odk.Repository.UtilisateurRepository;
import com.odk.Service.Interface.Service.RoleService;
import com.odk.Service.Interface.Service.UtilisateurService;
import com.odk.dto.UtilisateurDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/utilisateur")
@AllArgsConstructor
@Slf4j
public class UtilisateurController {

    private final UtilisateurRepository utilisateurRepository;
    private UtilisateurService utilisateurService;
    private RoleService roleService;
    private static final Logger logger = LoggerFactory.getLogger(UtilisateurController.class);


    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UtilisateurDTO ajouter(@RequestBody UtilisateurDTO utilisateur){
        return utilisateurService.add2(utilisateur);
    }

    @GetMapping("/roles-formulaire")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public List<Role> rolesFormulaire() {
        return roleService.List();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PERSONNEL','SUPERADMIN','ADMIN','DIRECTEUR','DIRECTEUR_ODC','DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI')")
    @ResponseStatus(HttpStatus.OK)
    public List<UtilisateurDTO> Liste(){
        return utilisateurService.getAllUtilisateur();
    }

    @GetMapping("{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public Optional<Utilisateur> getPersonnelParId(@PathVariable Long id){
        return utilisateurService.findById(id);
    }

    @PatchMapping("{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','PERSONNEL','DIRECTEUR','DIRECTEUR_ODC','DIRECTEUR_FONDATION','DIRECTEUR_RSE','DIRECTEUR_DCI')")
    @ResponseStatus(HttpStatus.CREATED)
    public Utilisateur Modifier(@PathVariable Long id, @RequestBody UtilisateurDTO utilisateur ){
        return utilisateurService.updateDTO(utilisateur,id);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void  supprimer(@PathVariable Long id){

        utilisateurService.delete(id);
    }

    @GetMapping("/nombre") // Pas de paramètres
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('DIRECTEUR') or hasRole('DIRECTEUR_ODC')")
    public ResponseEntity<Long> getNombreUtilisateurs() {
        long count = utilisateurService.getNombreUtilisateurs();
        return ResponseEntity.ok(count); // Retourne le nombre d'utilisateurs
    }

//   @PostMapping("/change-password")
//   public void modifierMotDePasse(@RequestBody Map<String, String> activation) {
//       this.utilisateurService.modifierMotDePasse(activation);
//   }

    @PutMapping("/modifierMotDePasse")
    public ResponseEntity<String> modifierMotDePasse(@RequestBody Map<String, String> parametres) {
        try {
            // Assurez-vous que les clés correspondent avec celles envoyées du frontend
            String ancienMotDePasse = parametres.get("currentPassword");
            String nouveauMotDePasse = parametres.get("newPassword");

            // Appel à la méthode de modification de mot de passe
            utilisateurService.modifierMotDePasse(Map.of("ancienPassword", ancienMotDePasse, "newPassword", nouveauMotDePasse));
            return ResponseEntity.ok("Mot de passe modifié avec succès.");
        } catch (IllegalArgumentException | NoSuchElementException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la modification du mot de passe", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Une erreur interne s'est produite.");
        }
    }

}
