package com.odk.Service.Interface.Service;

import com.odk.Entity.Activite;
import com.odk.Entity.Etape;
import com.odk.Entity.Salle;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.DecisionDirecteurOdc;
import com.odk.Enum.Statut;
import com.odk.Repository.ActiviteRepository;
import com.odk.Repository.EtapeRepository;
import com.odk.Repository.SalleRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.Service.Interface.CrudService;
import com.odk.dto.ActiviteDTO;
import com.odk.dto.ActiviteMapper;
import com.odk.dto.EtapeDTO;
import com.odk.dto.EtapeDTOSansActivite;
import com.odk.dto.EtapeMapper;
import jakarta.transaction.Transactional;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ActiviteService implements CrudService<Activite, Long> {

    private static final Logger log = LoggerFactory.getLogger(ActiviteService.class);

    @Value("${app.frontend.base-url:https://odc-activite.com}")
    private String appFrontendBaseUrl;

    @Value("${app.frontend.directeur-validation-activites-path:/directeur-odc/validation-activites}")
    private String directeurValidationActivitesPath;

    private final ActiviteRepository activiteRepository;
    private final PersonnelService personnelService;
    private final EmailService emailService;
    private final UtilisateurService utilisateurService;
    private final UtilisateurRepository utilisateurRepository;
    private final SalleRepository salleRepository;
    private final EtapeRepository etapeRepository;
    private final ActiviteMapper activiteMapper;
    private final EtapeMapper etapeMapper;
     

    @Override
    public Activite add(Activite entity) {
        try {
//            System.out.println("ajout type=================="+entity.getTypeActivite().getId());
            // Récupérer l'utilisateur connecté
            String email1 = SecurityContextHolder.getContext().getAuthentication().getName();
            Utilisateur utilisateurPerso = utilisateurRepository.findByEmail(email1)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));

            // Associer l'utilisateur comme créateur
            entity.setCreatedBy(utilisateurPerso);

            if (entity.getSalleId() == null || entity.getSalleId().getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Salle requise.");
            }
            if (entity.getEntite() == null || entity.getEntite().getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entité requise.");
            }
            if (entity.getTypeActivite() == null || entity.getTypeActivite().getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type d'activité requis.");
            }
            
             List<Activite> nomconflits = activiteRepository.findConflictingNomActivites(entity.getNom(),entity.getDateDebut(),
                    entity.getDateFin(),
                    Statut.Termine,
                    Statut.Rejetee
            );

            if (!nomconflits.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Le nom de cette activité est déjà crée avec les memes dates.");
            }

            List<Activite> conflits = activiteRepository.findConflictingActivites(
                    entity.getSalleId().getId(),
                    entity.getDateDebut(),
                    entity.getDateFin(),
                    Statut.Termine,
                    Statut.Rejetee
            );

            if (!conflits.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "La salle est déjà réservée pour une activité en cours ou en attente.");
            }

            // Personnel → responsable ODK (salle, logistique) → directeur ODC
            entity.setStatut(Statut.En_Validation_Responsable_ODK);
            Activite activiteCree = activiteRepository.save(entity);
            try {
                envoiMailResponsableOdkPourActivite(activiteCree);
            } catch (RuntimeException mailEx) {
                log.warn("Notification e-mail directeur ODC ignorée (activité id={}) : {}",
                        activiteCree.getId(), mailEx.getMessage());
            }

            return activiteCree;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (DataAccessException e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Donnée refusée (contrainte base ou doublon).", e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Une erreur est survenue lors de la création de l'activité.", e);
        }
//        catch (Exception ee) {
//            ee.printStackTrace(); // Pour afficher l'exception complète
//           throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur est survenue. Veuillez réessayer.", e);
//        }
    }
public void envoiMail(Activite activiteCree){
    // Récupérer la liste des utilisateurs
    Date dateDebut = activiteCree.getDateDebut();
    Date dateFin = activiteCree.getDateFin();
    SimpleDateFormat form= new SimpleDateFormat("dd/MM/yyyy");    
    String date1=form.format(dateDebut);
    String date2=form.format(dateFin);
    Salle s=salleRepository.findById(activiteCree.getSalleId().getId()).get();    
    String salle=s.getNom();
//    System.err.println("la salle mail====="+ salle);
            List<Utilisateur> utilisateurs = utilisateurService.List(); // Assurez-vous d'avoir cette méthode

             //  Filtrer les utilisateurs ayant le rôle "personnel"
            List<String> emailsPersonnel = utilisateurs.stream()
                    .filter(utilisateur -> utilisateur.getRole().getNom().equals("PERSONNEL")) // Vérifiez que le rôle est bien défini
                    .map(Utilisateur::getEmail) // Récupérer les emails
                    .collect(Collectors.toList());
            // Construire le corps de l'email avec HTML pour une meilleure présentation
            StringBuilder emailBodyBuilder = new StringBuilder();
            emailBodyBuilder.append("<!DOCTYPE html>");
            emailBodyBuilder.append("<html lang=\"fr\">");
            emailBodyBuilder.append("<head>");
            emailBodyBuilder.append("<meta charset=\"UTF-8\">");
            emailBodyBuilder.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            emailBodyBuilder.append("<title>Activité validée — information ODC</title>");
            emailBodyBuilder.append("<style>");
            emailBodyBuilder.append("  body { font-family: Arial, sans-serif; background-color: #f39c12; margin: 0; padding: 20px; }");
            emailBodyBuilder.append("  .container { background-color: #ffffff; padding: 20px; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
            emailBodyBuilder.append("  .header { text-align: center; padding-bottom: 20px; }");
            emailBodyBuilder.append("  .content { line-height: 1.6; }");
            emailBodyBuilder.append("  .footer { margin-top: 20px; font-size: 0.9em; color: #555555; text-align: center; }");
            emailBodyBuilder.append("</style>");
            emailBodyBuilder.append("</head>");
            emailBodyBuilder.append("<body>");
            emailBodyBuilder.append("<div class=\"container\">");
            emailBodyBuilder.append("<div class=\"header\">");
            emailBodyBuilder.append("<h2>Activité validée par le directeur ODC</h2>");
            emailBodyBuilder.append("</div>");
            emailBodyBuilder.append("<div class=\"content\">");
            emailBodyBuilder.append("<p>Bonjour,</p>");
            emailBodyBuilder.append("<p>Une activité vient d'être <strong>validée par le directeur ODC</strong> ; cette notification est envoyée à tout le personnel ODC.</p>");
            emailBodyBuilder.append("<p><strong>Nom de l'activité :</strong> ").append(escapeHtml(activiteCree.getNom())).append("</p>");
            emailBodyBuilder.append("<p><strong>Description :</strong> ").append(escapeHtml(activiteCree.getDescription())).append("</p>");
            emailBodyBuilder.append("<p><strong>Date du:</strong> ").append(date1).append(" AU: ").append(date2).append("</p>");
            emailBodyBuilder.append("<p><strong>Dans la Salle :</strong> ").append(escapeHtml(salle)).append("</p>");
            String lienApp = buildFrontendUrl("/dashboardActivite");
            emailBodyBuilder.append("<p><a href=\"").append(lienApp).append("\">Ouvrir l'application (tableau des activités)</a></p>");
            emailBodyBuilder.append("</div>");
            emailBodyBuilder.append("<div class=\"footer\">");
            emailBodyBuilder.append("<p>L'équipe <strong>ODC</strong></p>");
            emailBodyBuilder.append("<p>Ceci est un email automatisé. Merci de ne pas y répondre.</p>");
            emailBodyBuilder.append("</div>");
            emailBodyBuilder.append("</div>");
            emailBodyBuilder.append("</body>");
            emailBodyBuilder.append("</html>");

            String emailBody = emailBodyBuilder.toString();
            String sujet = "[ODC Activité] Validée par le directeur : " + activiteCree.getNom();
//emailService.sendSimpleEmail("fatoumata.KALOGA@orangemali.com", sujet, emailBody);
// Envoyer un email HTML à chaque utilisateur ayant le rôle "personnel"
            for (String email : emailsPersonnel) {
                System.out.println("envoi mail====="+email);
                emailService.sendSimpleEmail(email, sujet, emailBody);
            }
}

    public void envoiMailResponsableOdkPourActivite(Activite activiteCree) {
        List<Utilisateur> responsables = utilisateurRepository.findByRole_Nom("RESPONSABLE_ODK");
        if (responsables == null || responsables.isEmpty()) {
            envoiMailDirecteurOdcPourActivite(activiteCree);
            return;
        }
        String createur = activiteCree.getCreatedBy() != null
                ? activiteCree.getCreatedBy().getPrenom() + " " + activiteCree.getCreatedBy().getNom()
                : "un personnel";
        String lien = buildFrontendUrl("/responsable-odk/dashboard");
        String corps = "<p>Bonjour,</p><p><strong>" + escapeHtml(createur)
                + "</strong> a créé l'activité <strong>" + escapeHtml(activiteCree.getNom())
                + "</strong>. Merci de vérifier la salle et la logistique avant transmission au directeur ODC.</p>"
                + "<p><a href=\"" + lien + "\">Tableau de bord responsable ODK</a></p>";
        String sujet = "[ODC Activité] À traiter (responsable ODK) : " + activiteCree.getNom();
        for (Utilisateur r : responsables) {
            if (r.getEmail() != null && !r.getEmail().isBlank()) {
                emailService.sendSimpleEmail(r.getEmail(), sujet,
                        "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>");
            }
        }
    }

    @Transactional
    public Activite transfererAuDirecteurOdcParResponsable(Long activiteId, String note) {
        Activite a = activiteRepository.findById(activiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité introuvable"));
        if (a.getStatut() != Statut.En_Validation_Responsable_ODK) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette activité n'est pas en attente chez le responsable ODK.");
        }
        if (note != null && !note.isBlank()) {
            a.setNoteResponsableOdk(note.trim());
        }
        a.setSuggestionDirecteurOdc(null);
        a.setStatut(Statut.En_Validation_Directeur_ODC);
        Activite saved = activiteRepository.save(a);
        envoiMailDirecteurOdcPourActivite(saved);
        return saved;
    }

    @Transactional
    public Activite retournerAuPersonnelParResponsable(Long activiteId, String note) {
        Activite a = activiteRepository.findById(activiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité introuvable"));
        if (a.getStatut() != Statut.En_Validation_Responsable_ODK) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette activité n'est pas en attente chez le responsable ODK.");
        }
        a.setNoteResponsableOdk(note != null ? note.trim() : "");
        a.setDirecteurOdcDecision(null);
        a.setDirecteurOdcTraiteLe(null);
        a.setStatut(Statut.En_Cours);
        Activite saved = activiteRepository.save(a);
        if (saved.getCreatedBy() != null && saved.getCreatedBy().getEmail() != null) {
            String sujet = "[ODC Activité] À corriger : " + saved.getNom();
            String corps = "<p>Bonjour,</p><p>Votre activité nécessite des ajustements (responsable ODK) :</p><p>"
                    + escapeHtml(saved.getNoteResponsableOdk()) + "</p>";
            emailService.sendSimpleEmail(saved.getCreatedBy().getEmail(), sujet,
                    "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>");
        }
        return saved;
    }

    public List<Activite> listerEnAttenteResponsableOdk() {
        return activiteRepository.findByStatut(Statut.En_Validation_Responsable_ODK);
    }

    public List<Activite> listerTransmisesDirecteurOdcParResponsable() {
        return activiteRepository.findByStatut(Statut.En_Validation_Directeur_ODC);
    }

    @Transactional
    public void supprimerParResponsableOdk(Long activiteId) {
        Activite a = activiteRepository.findById(activiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité introuvable"));
        if (a.getStatut() != Statut.En_Validation_Directeur_ODC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Seules les activités transmises au directeur ODC peuvent être supprimées par le responsable ODK.");
        }
        activiteRepository.delete(a);
    }

    /** Après correction personnel suite à un retour responsable ODK → renvoi en validation responsable. */
    private void renvoyerAuResponsableOdkSiCorrectionPersonnel(Activite a) {
        boolean noteCorrection = a.getNoteResponsableOdk() != null && !a.getNoteResponsableOdk().isBlank();
        boolean statutCorrigeable = a.getStatut() == Statut.En_Cours || a.getStatut() == Statut.En_Attente;
        if (noteCorrection && statutCorrigeable) {
            a.setStatut(Statut.En_Validation_Responsable_ODK);
            try {
                envoiMailResponsableOdkPourActivite(a);
            } catch (RuntimeException mailEx) {
                log.warn("Notification responsable ODK ignorée (activité id={}) : {}", a.getId(), mailEx.getMessage());
            }
        }
    }

    private void assertPersonnelEstCreateurEtPeutModifier(Activite a, Utilisateur utilisateur) {
        if (utilisateur == null || utilisateur.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Utilisateur non authentifié.");
        }
        Long createurId = a.getCreatedBy() != null ? a.getCreatedBy().getId() : null;
        if (createurId == null || !Objects.equals(createurId, utilisateur.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous n'avez pas le droit de modifier cette activité (réservée au créateur).");
        }
        if (a.getStatut() == Statut.En_Validation_Directeur_ODC) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cette activité est en validation chez le directeur ODC et ne peut pas être modifiée.");
        }
        if (a.getStatut() == Statut.En_Validation_Responsable_ODK) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cette activité est en validation chez le responsable ODK. Modifiez-la uniquement après un retour pour correction.");
        }
    }

    @Transactional
    public void supprimerParDirecteurOdc(Long activiteId) {
        Activite a = activiteRepository.findById(activiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité introuvable"));
        if (a.getStatut() == Statut.En_Validation_Responsable_ODK) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette activité est encore chez le responsable ODK et ne peut pas être supprimée par le directeur ODC.");
        }
        activiteRepository.delete(a);
    }

    @Transactional
    public Activite enregistrerSuggestionDirecteurOdcActivite(Long activiteId, String suggestion) {
        Activite a = activiteRepository.findById(activiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité introuvable"));
        if (a.getStatut() != Statut.En_Validation_Directeur_ODC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette activité n'est pas en attente de validation directeur ODC.");
        }
        a.setSuggestionDirecteurOdc(suggestion != null ? suggestion.trim() : "");
        a.setStatut(Statut.En_Validation_Responsable_ODK);
        Activite saved = activiteRepository.save(a);
        envoiMailResponsableOdkPourActivite(saved);
        return saved;
    }

    public void envoiMailDirecteurOdcPourActivite(Activite activiteCree) {
        List<Utilisateur> directeurs = utilisateurRepository.findByRole_Nom("DIRECTEUR_ODC");
        if (directeurs == null || directeurs.isEmpty()) {
            return;
        }
        String createur = activiteCree.getCreatedBy() != null
                ? activiteCree.getCreatedBy().getPrenom() + " " + activiteCree.getCreatedBy().getNom()
                : "un personnel";
        String lienConnexion = buildFrontendUrl("/authentication/signin");
        String lienValidation = buildFrontendUrl(directeurValidationActivitesPath);
        String corps = "<p>Bonjour,</p><p><strong>" + escapeHtml(createur)
                + "</strong> a créé une activité <strong>" + escapeHtml(activiteCree.getNom())
                + "</strong> en attente de votre validation.</p>"
                + "<p>Connectez-vous en directeur ODC, puis ouvrez la page de validation : "
                + "<a href=\"" + lienConnexion + "\">Connexion</a></p>"
                + "<p>Raccourci vers la liste à traiter : <a href=\"" + lienValidation + "\">"
                + lienValidation + "</a> (après connexion).</p>"
                + "<p>Vous pourrez valider ou refuser depuis le tableau de bord.</p>";
        String sujet = "[ODC Activité] Validation requise : " + activiteCree.getNom();
        for (Utilisateur d : directeurs) {
            if (d.getEmail() != null && !d.getEmail().isBlank()) {
                emailService.sendSimpleEmail(d.getEmail(), sujet,
                        "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>");
            }
        }
    }

    /** Aligné sur HashLocationStrategy du front Angular (voir app.config.ts) : base + /#/ + route. */
    private String buildFrontendUrl(String pathOrAbsolute) {
        String base = appFrontendBaseUrl == null ? "" : appFrontendBaseUrl.trim().replaceAll("/+$", "");
        if (pathOrAbsolute == null || pathOrAbsolute.isBlank()) {
            return base.isEmpty() ? "/#/" : base + "/#/";
        }
        String path = pathOrAbsolute.replaceFirst("^/+", "");
        return base + "/#/" + path;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Transactional
    public Activite validerParDirecteurOdc(Long activiteId) {
        Activite a = activiteRepository.findById(activiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité introuvable"));
        if (a.getStatut() != Statut.En_Validation_Directeur_ODC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette activité n'est pas en attente de validation par le directeur ODC (transmise par le responsable ODK).");
        }
        a.setSuggestionDirecteurOdc(null);
        a.setStatut(Statut.En_Attente);
        a.setDirecteurOdcDecision(DecisionDirecteurOdc.VALIDEE);
        a.setDirecteurOdcTraiteLe(new Date());
        a.mettreAJourStatut();
        Activite saved = activiteRepository.save(a);
        envoiMail(saved);
        return saved;
    }

    @Transactional
    public Activite rejeterParDirecteurOdc(Long activiteId) {
        Activite a = activiteRepository.findById(activiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité introuvable"));
        if (a.getStatut() != Statut.En_Validation_Directeur_ODC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette activité n'est pas en attente de validation par le directeur ODC.");
        }
        a.setStatut(Statut.Rejetee);
        a.setDirecteurOdcDecision(DecisionDirecteurOdc.REFUSEE);
        a.setDirecteurOdcTraiteLe(new Date());
        Activite saved = activiteRepository.save(a);
        if (saved.getCreatedBy() != null && saved.getCreatedBy().getEmail() != null) {
            String sujet = "[ODC Activité] Activité non validée : " + saved.getNom();
            String corps = "<p>Bonjour,</p><p>Votre activité <strong>" + escapeHtml(saved.getNom())
                    + "</strong> n'a pas été validée par le directeur ODC.</p>";
            emailService.sendSimpleEmail(saved.getCreatedBy().getEmail(), sujet,
                    "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif\">" + corps + "</body></html>");
        }
        return saved;
    }

    public List<Activite> listerEnAttenteValidationDirecteurOdc() {
        return activiteRepository.findByStatut(Statut.En_Validation_Directeur_ODC);
    }

    /** Activités affichées sur le calendrier : uniquement après validation directeur ODC. */
    public List<Activite> listerPourCalendrier() {
        return activiteRepository.findAll().stream()
                .filter(a -> a.getDirecteurOdcDecision() == DecisionDirecteurOdc.VALIDEE)
                .collect(Collectors.toList());
    }

    public List<Activite> listerHistoriqueValideesParDirecteurOdc() {
        return activiteRepository.findByDirecteurOdcDecisionOrderByDirecteurOdcTraiteLeDesc(DecisionDirecteurOdc.VALIDEE);
    }

    public List<Activite> listerHistoriqueRefuseesParDirecteurOdc() {
        return activiteRepository.findByDirecteurOdcDecisionOrderByDirecteurOdcTraiteLeDesc(DecisionDirecteurOdc.REFUSEE);
    }

    @Override
    public List<Activite> List() {
        return activiteRepository.findAll();
    }
    //Par user
    public List<Activite> ListByUser(Long userId) {
        return activiteRepository.findByUser(userId);
    }

    public List<Activite> list() {
        // Récupérer toutes les activités
        List<Activite> activites = activiteRepository.findAll();

        // Filtrer les activités dont l'étape a le statut 'EN_COURS'
        List<Activite> activitesEnCours = activites.stream()
                .filter(activite -> false) // Vérifie le statut
                .collect(Collectors.toList());

        return activitesEnCours;
    }

    @Override
    public Optional<Activite> findById(Long id) {
        return activiteRepository.findById(id);
    }
//Pas utiliser
    @Transactional
    @Override
    public Activite update(Activite activite, Long id) {
        // Récupérer l'utilisateur connecté
         System.out.println("update activite============="+activite.getEtapes().toString());
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));

        return activiteRepository.findById(id).map(a -> {
            assertPersonnelEstCreateurEtPeutModifier(a, utilisateur);

            // Mettre à jour les champs modifiables
            if (activite.getNom() != null) {
                a.setNom(activite.getNom());
            }
            if (activite.getTitre() != null) {
                a.setTitre(activite.getTitre());
            }
            if (activite.getDescription() != null) {
                a.setDescription(activite.getDescription());
            }
            if (activite.getDateDebut() != null) {
                a.setDateDebut(activite.getDateDebut());
            }
            if (activite.getLieu() != null) {
                a.setLieu(activite.getLieu());
            }
            if (activite.getObjectifParticipation() != null) {
                a.setObjectifParticipation(activite.getObjectifParticipation());
            }
            if (activite.getEntite() != null) {
                a.setEntite(activite.getEntite());
            }
            if (activite.getTypeActivite() != null) {
                a.setTypeActivite(activite.getTypeActivite());
            }
            if (activite.getSalleId() != null) {
                a.setSalleId(activite.getSalleId());
            }
            // DES MODIFICATION AFFAIRE ICI 
            if (activite.getEtapes() != null) {
                a.getEtapes().clear();
                a.getEtapes().addAll(activite.getEtapes());
                for (Etape e : activite.getEtapes()) {
                    System.out.println("etape===="+e);
//                    e.setActivite(a);
//                    etapeRepository.save(e);
                }
            }

           /* // Mettre à jour les étapes
            updateEtapes(a, activite.getEtapes());*/

            // Mettre à jour le statut
            renvoyerAuResponsableOdkSiCorrectionPersonnel(a);
            a.mettreAJourStatut();

            // Sauvegarder les modifications
            return activiteRepository.save(a);
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "L'activité avec l'ID spécifié n'existe pas."));
    }

@Transactional
public Activite updateDTO(ActiviteDTO activite, List<Long> etapesids, Long id) {

    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));

    return activiteRepository.findById(id).map(a -> {
        assertPersonnelEstCreateurEtPeutModifier(a, utilisateur);

        // Mise à jour des champs (MapStruct)
        activiteMapper.updateFromDto(activite, a);

        // Mise à jour des étapes
        if (etapesids != null) {
            System.out.println("mes etape================"+etapesids);
            for (Long etapeId : etapesids) {
                Etape etape = etapeRepository.findById(etapeId)
                        .orElseThrow(() -> new RuntimeException("Etape non trouvée"));
                etape.setActivite(a); // juste ça suffit
                 System.out.println("on etape================"+etape.getActivite().getNom());
                etapeRepository.save(etape);
                System.out.println("on etape================"+etape.getActivite().getNom());
            }
        }

        // Mise à jour du statut
        renvoyerAuResponsableOdkSiCorrectionPersonnel(a);
        a.mettreAJourStatut();
        return activiteRepository.save(a);
    }).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité introuvable")
    );
}


    @Transactional    
    public Activite updateDTOold(ActiviteDTO activite,List<Long> etapesids, Long id) {
        // Récupérer l'utilisateur connecté
        System.out.println("update activite ETAPES============="+activite.getEtapes());
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));

        return activiteRepository.findById(id).map(a -> {
            // Vérifier que l'utilisateur connecté est le créateur de l'activité
            if (!a.getCreatedBy().getEmail().equals(utilisateur.getEmail())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Vous n'êtes pas autorisé à modifier cette activité.");
            }

            // Mettre à jour les champs modifiables
            if (activite.getNom() != null) {
                a.setNom(activite.getNom());
            }
            if (activite.getTitre() != null) {
                a.setTitre(activite.getTitre());
            }
            if (activite.getDescription() != null) {
                a.setDescription(activite.getDescription());
            }
            if (activite.getDateDebut() != null) {
                a.setDateDebut(activite.getDateDebut());
            }
            if (activite.getLieu() != null) {
                a.setLieu(activite.getLieu());
            }
            if (activite.getObjectifParticipation() != 0) {
                a.setObjectifParticipation(activite.getObjectifParticipation());
            }
            if (activite.getEntite() != null) {
                a.setEntite(activite.getEntite());
            }
            if (activite.getTypeActivite() != null) {
                a.setTypeActivite(activite.getTypeActivite());
            }
            if (activite.getSalleId() != null) {
                a.setSalleId(activite.getSalleId());
            }
            //utiliser MapStruct pour mettre à jour seulement les champs non-nuls
             activiteMapper.updateFromDto(activite, a); // voir MapStruct plus bas
            // Mettre à jour le statut
            a.mettreAJourStatut();
    //  gérer les etapes explicitement (merge, ne pas remplacer la collection)
//    if (activite.getEtapes() != null) {
//        // approach: update existing list items, add new, remove missing
//        syncEtapesN(a, activite.getEtapes(),etapesids);
//        System.out.println("etapesSansActivite APRES TRAITEMENT======="+a.getEtapes());
//
//    }

            // DES MODIFICATION AFFAIRE ICI 
            if(etapesids!=null){
              System.out.println("update activite ETAPES IS NOT NULL ID============="+etapesids);
                for(Long i:etapesids){
                    Etape e=etapeRepository.findById(i).get();
                    e.setActivite(a);
                    EtapeDTOSansActivite etatsansact=etapeMapper.toSansActivite(e);
                    etatsansact.setActiviteid(e.getActivite().getId());
                    etapeRepository.save(etapeMapper.toEntitesansActivite(etatsansact));

                }

          }         

           /* // Mettre à jour les étapes
            updateEtapes(a, activite.getEtapes());*/
        
            ActiviteDTO adto=activiteMapper.ACTIVITE_DTO(a);
            adto.getEtapes();
            for(EtapeDTOSansActivite eT:adto.getEtapes()){
            eT.setActiviteid(a.getId());
            etapeRepository.save(etapeMapper.toEntitesansActivite(eT));
        }
// Sauvegarder les modifications
         System.out.println("ActiviteDTO adto save======="+adto.getEtapes());

            return activiteRepository.save(activiteMapper.toEntity(adto));
            
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "L'activité avec l'ID spécifié n'existe pas."));
    }
    
 /*   private void updateEtapes(Activite activite, List<Etape> nouvellesEtapes) {
        // Supprimer les étapes qui ne sont plus associées
        // Ajouter les nouvelles étapes
        activite.getEtapes().addAll(nouvellesEtapes);
    }*/
    @Transactional
private void syncEtapes(Activite activite, List<EtapeDTOSansActivite> etapesSansActivite,List<Long> etapesids){
    System.out.println("etapesSansActivite SEBUT======="+etapesSansActivite);
    List<Etape> listeEtapeObjet=(etapeRepository.findAllById(etapesids));
    System.out.println("etapesSansActivite AVEC OBJET======="+listeEtapeObjet);
    Map<Long,Etape> existingById=activite.getEtapes().stream().filter(e->e.getId()!=null).collect(Collectors.toMap(Etape::getId,Function.identity()));
    List<Etape> newlist=new ArrayList<>();
    for(EtapeDTOSansActivite ea: etapesSansActivite){
        if(ea.getId()!=null && existingById.containsKey(ea.getId())){
            Etape toUpdate=existingById.get(ea.getId());
            etapeMapper.updateFromDto(ea, toUpdate);
            newlist.add(toUpdate);
        }else{
            System.out.println("save ea activiteID======="+ea.getActiviteid());            
            Etape created=etapeMapper.toEntitesansActivite(ea);
                        created.setActivite(activiteRepository.findById(ea.getActiviteid()).get());
                        newlist.add(created);

        }
    }
    activite.getEtapes().clear();
    activite.getEtapes().addAll(newlist);
}

@Transactional
private void syncEtapesN(Activite activite, List<EtapeDTOSansActivite> etapesSansActivite,List<Long> etapesids){
    System.out.println("etapesSansActivite SEBUT======="+etapesids);
    
    List<Etape> existingEtapes=new ArrayList<>();
     for(Long i:etapesids){
         Etape e=etapeRepository.findById(i).get();
         e.setActivite(activite);
         existingEtapes.add(e);         
         System.out.println("etapesSansActivite AVEC OBJET======="+existingEtapes);
     }    
     
    for(Etape e:existingEtapes){
    e.setActivite(activite);
    System.out.println("etapesSansActivite avant save======="+e.getActivite());
    etapeRepository.save(e);
    System.out.println("etapesSansActivite apres save======="+e.getActivite());

  }
     
   List<Etape> newEtapes=etapesSansActivite.stream()
                        .filter(dto->dto.getId()==null)
                        .map(dto->{
                            Etape e=etapeMapper.toEntitesansActivite(dto);
                            e.setActivite(activite);
                            return e;
                            }).collect(Collectors.toList());
   
    
    activite.getEtapes().clear();
    activite.getEtapes().addAll(existingEtapes);
}


    @Override
    public void delete(Long id) {
        // Récupérer l'utilisateur connecté
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));
        System.out.println("com.odk.Service.Interface.Service.ActiviteService.delete()");
        // Récupérer l'activité
        Activite activite1 = activiteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Activité non trouvée"));
        System.out.println("com.delete()======="+activite1.getCreatedBy().getId().equals(utilisateur.getId()));

        // Vérifier si l'utilisateur est le créateur
        if (!activite1.getCreatedBy().getId().equals(utilisateur.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n'êtes pas autorisé à supprimer cette activité");
        }

        Optional<Activite> activiteOptional = activiteRepository.findById(id);
        activiteOptional.ifPresent(activite -> activiteRepository.delete(activite));
    }
    
    
    public List<Activite> getActivitesBySuperviseur(Long superviseurId) {
        System.out.println("je suisssssssss dans fonction===="+superviseurId);       
        return activiteRepository.findBySuperviseurIdOrNull(superviseurId);    

}
   public List<Activite> getActivitesBySuperviseurAttente(Long superviseurId) {
    return activiteRepository.findAttenteBySuperviseurInValidation(superviseurId);
} 
}
