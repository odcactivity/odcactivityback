/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.odk.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odk.Entity.Activite;
import com.odk.Entity.ActiviteValidation;
import com.odk.Entity.Salle;
import com.odk.Entity.Utilisateur;
import com.odk.Repository.ActiviteRepository;
import com.odk.Repository.ActiviteValidationRepository;
import com.odk.Repository.SalleRepository;
import com.odk.Service.Interface.Service.ActiviteValidationService;
import com.odk.Service.Interface.Service.EmailService;
import com.odk.Service.Interface.Service.UtilisateurService;
import com.odk.dto.ActiviteValidationDTO;
import com.odk.dto.ActiviteValidationMapper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author kaloga081009
 */
@RestController
@RequestMapping("/activitevalidation")
@CrossOrigin(origins = "http://localhost:4200")
public class ActiviteValidationController {
  @Autowired
    private ActiviteValidationService activiteValidationService;
    @Autowired
    private ActiviteValidationRepository activiteValidationRepository;
    @Autowired
    private ActiviteRepository activiteRepository;
  @Autowired
    private ObjectMapper objectMapper;
  @Autowired
  private SalleRepository salleRepository;
  @Autowired
  private UtilisateurService utilisateurService;
  @Autowired
  private EmailService emailService;
  private  ActiviteValidationMapper activiteValidationMapper ;
  
    @GetMapping("/HELLO")    
    public String hello(){
        return "bonjour";
    }
    // Créer une validation avec fichier
    
    @PostMapping(value = "/create/{createOrreponse}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    //@PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> createValidation(
            @RequestPart("validation") String validationJson,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier,@PathVariable String createOrreponse) {

        try {      
            System.out.println("reponse ou create========="+createOrreponse);
            // Convertir la chaîne JSON reçue en DTO
            ActiviteValidationDTO dto = objectMapper.readValue(validationJson, ActiviteValidationDTO.class);
               dto.setDate(new Date()); 
               dto.setIsRead(false);
               // envoir de mail pour la validation 
               ActiviteValidationDTO actSave=activiteValidationService.ajouterValidation(dto, fichier);
               System.out.println("ACTIVALIDATION DTO+++++++++"+actSave);               
               envoiMailValidation(actSave,createOrreponse);
//            return ResponseEntity.ok(activiteValidationService.ajouterValidation(dto, fichier));
            return ResponseEntity.ok(actSave);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Erreur interne : " + e.getMessage()));
        }
    }

    public void envoiMailValidation(ActiviteValidationDTO activiteCree,String t1){
//recuperer l'envoyeur de la dernière validation pour activite encours
List<ActiviteValidation> listvalidationbyActivite=activiteValidationRepository.findByActiviteId(activiteCree.getActiviteId());
int taille=listvalidationbyActivite.size();
Long idEnvoyeur = null;
System.out.println("mes validation==="+listvalidationbyActivite);
if(taille!=0){
    idEnvoyeur =listvalidationbyActivite.get(taille-1).getEnvoyeurId();
}


// Récupérer la liste des utilisateurs
     String titre="Bonjour";
     if(t1.equalsIgnoreCase("DESIGNATION")){
         titre="Une nouvelle activité vient d’être enregistrée dans notre système. Vous avez été désigné(e) comme superviseur de celle-ci.";
     }else{
        titre="Vous avez eu une reponse à votre VALIDATION enregistrée dans notre système.";
  
     }     
    Date dateCreation = activiteCree.getDate();        
    Date dateDebut = activiteRepository.findById(activiteCree.getActiviteId()).get().getDateDebut();
    Date dateFin = activiteRepository.findById(activiteCree.getActiviteId()).get().getDateFin();
    SimpleDateFormat form= new SimpleDateFormat("dd/MM/yyyy");    
    String date1=form.format(dateDebut);
    String date2=form.format(dateFin);
    String date3=form.format(dateCreation);
    Salle s=salleRepository.findById(activiteRepository.findById(activiteCree.getActiviteId()).get().getSalleId().getId()).get();
    String salle=s.getNom();
    String commentaire=activiteCree.getCommentaire();
    String fichier=activiteCree.getFichierjoint();   
    List<Utilisateur> utilisateurs = utilisateurService.List(); // Assurez-vous d'avoir cette méthode

             //  Filtrer les utilisateurs ayant le rôle "personnel"
    List<String> emailsPersonnel = utilisateurs.stream()
                    .filter(utilisateur -> utilisateur.getRole().getNom().equals("PERSONNEL")) // Vérifiez que le rôle est bien défini
                    .map(Utilisateur::getEmail) // Récupérer les emails
                    .collect(Collectors.toList());
            List<String> emailvalidation=new ArrayList<>();
            for (String mail:emailsPersonnel){
                if(mail.equalsIgnoreCase(utilisateurService.findById(activiteCree.getSuperviseurId()).get().getEmail())){
                    emailvalidation.add(mail);
                }
            }
            if(idEnvoyeur!=null && activiteCree.getSuperviseurId()!=idEnvoyeur && !t1.equalsIgnoreCase("DESIGNATION")){
            emailvalidation.add(utilisateurService.findById(idEnvoyeur).get().getEmail());
}
            // Construire le corps de l'email avec HTML pour une meilleure présentation
            StringBuilder emailBodyBuilder = new StringBuilder();
            emailBodyBuilder.append("<!DOCTYPE html>");
            emailBodyBuilder.append("<html lang=\"fr\">");
            emailBodyBuilder.append("<head>");
            emailBodyBuilder.append("<meta charset=\"UTF-8\">");
            emailBodyBuilder.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            emailBodyBuilder.append("<title>").append(titre).append("</title>");
            emailBodyBuilder.append("<style>");
            emailBodyBuilder.append("  body { font-family: Arial, sans-serif;background-color: #F5F5F5;margin: 0;padding: 40px; }");
            emailBodyBuilder.append(" .container { background-color: #FFFFFF;max-width: 650px;margin: auto;padding: 32px;border-radius: 4px;border-left: 6px solid #FF7900;box-shadow: 0 2px 10px rgba(0,0,0,0.07);");
            emailBodyBuilder.append(" .header {  text-align: left; margin-bottom: 25px;border-bottom: 2px solid #FF7900;padding-bottom: 12px; }");
            emailBodyBuilder.append(" .header h2 { margin: 0;color: #000000;font-size: 20px;font-weight: 700;}");
            emailBodyBuilder.append(" .content {color: #333333; font-size: 15px; line-height: 1.7; margin-top: 15px; }");
            emailBodyBuilder.append(" .highlight {color: #FF7900;font-weight: bold;}");
            emailBodyBuilder.append(".cta {margin-top:30px}");
//            emailBodyBuilder.append(".cta a {}");
            emailBodyBuilder.append("  .footer { margin-top: 32px; font-size: 0.9em; color: #5A5A5A; text-align: center; }");
            emailBodyBuilder.append("</style>");
            emailBodyBuilder.append("</head>");
            emailBodyBuilder.append("<body>");
            emailBodyBuilder.append("<div class=\"container\">");
            emailBodyBuilder.append("<div class=\"header\">");
            emailBodyBuilder.append("<h2>Nouvelle Validation Créée à la date du : ").append(date3).append("</h2>");
            emailBodyBuilder.append("</div>");
            emailBodyBuilder.append("<div class=\"content\">");
            emailBodyBuilder.append("<p>Bonjour,</p>");
            emailBodyBuilder.append("<p>Une nouvelle activité a été créée dans notre système dont vous etes choisis comme SUPERVISEUR.</p>");
            emailBodyBuilder.append("<p><strong>Nom de l'activité :</strong> ").append(activiteRepository.findById(activiteCree.getActiviteId()).get().getNom()).append("</p>");
            emailBodyBuilder.append("<p><strong>Description :</strong> ").append(activiteRepository.findById(activiteCree.getActiviteId()).get().getDescription()).append("</p>");
            emailBodyBuilder.append("<p><strong>Date du:</strong> ").append(date1).append(" AU: ").append(date2).append("</p>");
            emailBodyBuilder.append("<p><strong>Dans la Salle :</strong> ").append(salle).append("</p>");
            
            emailBodyBuilder.append("<p><strong>Commentaire pour la validation :</strong> ").append(commentaire).append("</p>");
            emailBodyBuilder.append("<p><strong>Piéce Jointe :</strong> ").append(fichier).append("</p>");

            emailBodyBuilder.append("<p>Nous vous invitons à consulter cette activitéValidation, pour plus de détails.</p>");
            emailBodyBuilder.append("</div>");
            emailBodyBuilder.append("<div class=\"footer\">");
            emailBodyBuilder.append("<p>L'équipe <strong>ODC</strong></p>");
            emailBodyBuilder.append("<p>Ceci est un email automatisé. Merci de ne pas y répondre.</p>");
            emailBodyBuilder.append("</div>");
            emailBodyBuilder.append("</div>");
            emailBodyBuilder.append("</body>");
            emailBodyBuilder.append("</html>");

            String emailBody = emailBodyBuilder.toString();
            String sujet = "Assignation de nouvelle VALIDATION pour l'activite : " + activiteRepository.findById(activiteCree.getActiviteId()).get().getNom();
//emailService.sendSimpleEmail("fatoumata.KALOGA@orangemali.com", sujet, emailBody);
// Envoyer un email HTML à chaque utilisateur ayant le rôle "personnel"
            for (String email : emailvalidation) {
                 emailService.sendSimpleEmail(email, sujet, emailBody);
            }
}

    // Liste toutes les validations
    @GetMapping
    //@PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN')")
    public List<ActiviteValidationDTO> listeValidations() {
        return activiteValidationService.listeValidations();
    }

    // Récupérer une validation par ID
    @GetMapping("/{id}")
    //@PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN')")
    public ActiviteValidationDTO getValidation(@PathVariable Long id) {
        return activiteValidationService.getValidation(id);
    }

    // Télécharger le fichier d'une validation
        @GetMapping("/{id}/fichier")
           //@PreAuthorize("hasRole('PERSONNEL') or hasRole('SUPERADMIN')")
    public ResponseEntity<byte[]> telechargerFichier(@PathVariable Long id) {
    ActiviteValidation validation = activiteValidationRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Validation non trouvée"));

    byte[] fichier = validation.getFichierChiffre(); // @Lob, contenu réel du fichier
    String nomFichier = validation.getFichierjoint(); // nom du fichier original

    if (fichier == null || fichier.length == 0) {
        return ResponseEntity.noContent().build();
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomFichier + "\"")
        .header("Access-Control-Expose-Headers", "Content-Disposition")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(fichier);
}
    
    @DeleteMapping("/{id}")
public ResponseEntity<?> delete(@PathVariable Long id) {
    try {
        if (!activiteValidationRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body(Map.of("message", "Entité introuvable"));
        }

        activiteValidationRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Validation supprimée avec succès"));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(Map.of("message", "Erreur interne : " + e.getMessage()));
    }
}

}
