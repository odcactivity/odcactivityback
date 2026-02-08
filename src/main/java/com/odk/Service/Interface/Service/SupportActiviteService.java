package com.odk.Service.Interface.Service;

import com.odk.Entity.Activite;
import com.odk.Entity.HistoriqueSupportActivite;
import com.odk.Entity.SupportActivite;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.StatutSupport;
import com.odk.Enum.TypeSupport;
import com.odk.Repository.ActiviteRepository;
import com.odk.Repository.HistoriqueSupportActiviteRepository;
import com.odk.Repository.SupportActiviteRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.dto.HistoriqueSupportActiviteDTO;
import com.odk.dto.SupportActiviteResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupportActiviteService {

  

    // private final String uploadDir = "C:/Users/sodia.diallo/desktop/ODC_Projet_Back/uploads/supports";
    private final String uploadDir = "uploads/supports";

    @Autowired
    private SupportActiviteRepository supportActiviteRepository;

    @Autowired
    private ActiviteRepository activiteRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private HistoriqueSupportActiviteRepository historiqueRepository;


// ---------------- Upload d’un support ou telechargement d'un fichier dans notre espace de stockage ----------------------------------------------//
//------------------------------------------------------------------------------------------------------------------------------------------------//
    public SupportActivite saveSupport(MultipartFile file, Long idActivite, String username, Long utilisateurId, String description) throws IOException {
        // 🔥 Créer le dossier sil est inexistant
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 🔥 Nom unique du fichier

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 🔥 Récupérer l’activité
        Activite activite = activiteRepository.findById(idActivite)
                .orElseThrow(() -> new RuntimeException("Activité non trouvée"));

        // 🔥 Récupérer l'utilisateur affecté
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // 🔥 Créer l’objet support
        SupportActivite support = new SupportActivite();
        support.setNom(file.getOriginalFilename());
        support.setTypeMime(file.getContentType());
        support.setUrl("http://localhost:8080/files/" + fileName);
        support.setStatut(StatutSupport.En_ATTENTE);
        support.setActivite(activite);
        support.setUtilisateurAutorise(utilisateur);  // l'utilisateur qui peut modifier/commenter
        support.setDateAjout(new Date());
        support.setDescription(description);
         // 🔥 Enregistrer la taille du fichier !
        support.setTaille(file.getSize());

        SupportActivite saved = supportActiviteRepository.save(support);

        // 🔥 Ajouter l’historique initial
        HistoriqueSupportActivite historique = new HistoriqueSupportActivite();
        historique.setSupport(saved);
        historique.setStatut(saved.getStatut());
        historique.setCommentaire(saved.getCommentaire());
        historique.setDateModification(saved.getDateAjout());
        historique.setEmailAuteur(utilisateur.getEmail()); // <-- email de l'utilisateur autorisé
        historiqueRepository.save(historique);

        return saved;
    }

    // ------------------ Nouveau save avec classification + taille max 15G ------------------
    public SupportActivite saveSupportWithValidation(MultipartFile file, Long idActivite, Long utilisateurId, String description) throws IOException {

        long maxSize = 15L * 1024 * 1024 * 1024; // 15 Go
        if(file.getSize() > maxSize) throw new RuntimeException("Taille max 15 Go");

        Path uploadPath = Paths.get(uploadDir);
        if(!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        Activite activite = activiteRepository.findById(idActivite)
                .orElseThrow(() -> new RuntimeException("Activité non trouvée"));

        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        String contentType = file.getContentType();
        TypeSupport typeSupport;

        if(contentType.equals("application/msword") || contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")){
            typeSupport = TypeSupport.RAPPORT;
        } else if(contentType.startsWith("image/")){
            typeSupport = TypeSupport.IMAGE;
        } else if(contentType.startsWith("video/")){
            typeSupport = TypeSupport.VIDEO;
        } else {
            throw new RuntimeException("Type non supporté");
        }

        SupportActivite support = new SupportActivite();
        support.setNom(file.getOriginalFilename());
        support.setUrl(filePath.toAbsolutePath().toString());
        support.setType(typeSupport);
        support.setTypeMime(contentType);
        support.setTaille(file.getSize());
        support.setStatut(StatutSupport.En_ATTENTE);
        support.setActivite(activite);
        support.setUtilisateurAutorise(utilisateur);
        support.setDateAjout(new Date());
        support.setDescription(description);

        SupportActivite saved = supportActiviteRepository.save(support);

        HistoriqueSupportActivite historique = new HistoriqueSupportActivite();
        historique.setSupport(saved);
        historique.setStatut(saved.getStatut());
        historique.setCommentaire(saved.getCommentaire());
        historique.setDateModification(saved.getDateAjout());
        historique.setEmailAuteur(utilisateur.getEmail());
        historiqueRepository.save(historique);

        return saved;
    }
        
// --------------- Mise à jour du statut dans l'historique des supports existants---------------------------------//
// ---------------------------------------------------------------------------------------------------------------//
    public SupportActivite updateStatut(Long supportId, StatutSupport statut, String commentaire, String username) {
        SupportActivite support = supportActiviteRepository.findById(supportId)
                .orElseThrow(() -> new RuntimeException("Support non trouvé"));

        // 🔥 Vérifier que l’utilisateur est autorisé
        if (!support.getUtilisateurAutorise().getUsername().equals(username)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à modifier ce support");
        }

        support.setStatut(statut);
        support.setCommentaire(commentaire);
        support.setDateAjout(new Date());

      // 🔥 Permet d'enregistrer une mise à jour dans Historique
        HistoriqueSupportActivite historique = HistoriqueSupportActivite.builder()
                .support(support)
                .statut(statut)
                .commentaire(commentaire)
                .dateModification(new Date())
                .emailAuteur(username) // <-- On recupere l'email de utilisateur qui fait la modification...
                .build();
        historiqueRepository.save(historique);

        return supportActiviteRepository.save(support);
    }

// ---------------- Liste des supports créer dans notre base de donnée ------------------//
//--------------------------------------------------------------------------------------//
    public List<SupportActiviteResponseDTO> getAllSupports() {
        return supportActiviteRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
// ---------------- Recuperer un Support par ID -------------------------------------//
//----------------------------------------------------------------------------------//
    public SupportActiviteResponseDTO getSupportById(Long id) {
        SupportActivite support = supportActiviteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Support non trouvé"));
        return convertToDTO(support);
    }
// --- DELETE Support/Supprimer le support de la base de donnée ------------------//
//-------------------------------------------------------------------------------//
    public void deleteSupport(Long supportId, String username) throws IOException {
        // 🔥 Recuperer le support...
        SupportActivite support = supportActiviteRepository.findById(supportId)
                .orElseThrow(() -> new RuntimeException("Support non trouvé"));

        // 🔥 Vérification que l'utilisateur connecté est autorisé
        if (!support.getUtilisateurAutorise().getUsername().equals(username)) {
            throw new RuntimeException("Vous n'êtes pas autorisé à supprimer ce support");
        }


        
        // 🔥 Ajouter un historique de suppression ...
              //HistoriqueSupportActivite historique= new HistoriqueSupportActivite();
              //historique.setSupport(support);
              //historique.setStatut(StatutSupport.SUPRIMER);
              //historique.setCommentaire("Supprimer par :"+username);
             // historique.setDateModification(new Date());
              //historique.setEmailAuteur(username);
              //historiqueRepository.save(historique);

        // 🔥 Suppression du fichier physique
           // Path filePath = Paths.get(support.getUrl()); // <-- Stocke toujours le chemin local... 
           // if (Files.exists(filePath)) {
           // Files.delete(filePath);
       // }

        // 🔥 Suppression de l'entité en base
        supportActiviteRepository.delete(support);

    }

      // ------------------ Filtrer par type ------------------
     public List<SupportActiviteResponseDTO> getSupportsByType(TypeSupport type){
        return supportActiviteRepository.findByType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
//------------- Méthode pour récupérer l'historique d'un support--------------------------------------//
//---------------------------------------------------------------------------------------------------//
    public List<HistoriqueSupportActiviteDTO> getHistorique(Long supportId) {
        List<HistoriqueSupportActivite> historiques = historiqueRepository.findBySupportId(supportId);
        return historiques.stream()
                .map(h -> new HistoriqueSupportActiviteDTO(
                        h.getId(),
                        h.getStatut(),
                        h.getCommentaire(),
                        h.getDateModification(),
                        h.getEmailAuteur()
                ))
                .collect(Collectors.toList());
    }

// ----------------------------------
// Retourne le chemin exact du fichier...

   public Path getFilePath(SupportActivite support){
    // String fileName= support.getUrl().substring(support.getUrl().lastIndexOf("/")+1);
    String fileName= support.getUrl();//.substring(support.getUrl());
    System.out.println("---> ----> "+fileName);
    return Paths.get(uploadDir).resolve(fileName).toAbsolutePath().normalize();
    
   }
// ---------------- Conversion Entité → DTO -----------------------------------------//
//----------------------------------------------------------------------------------//
    public SupportActiviteResponseDTO convertToDTO(SupportActivite support) {
        SupportActiviteResponseDTO dto = new SupportActiviteResponseDTO();
        dto.setId(support.getId());
        dto.setNom(support.getNom());
        dto.setType(support.getType().name());
        dto.setUrl(support.getUrl());
        dto.setStatut(support.getStatut());
        dto.setDescription(support.getDescription());
        dto.setCommentaire(support.getCommentaire());
        dto.setDateAjout(support.getDateAjout());
        dto.setActiviteId(support.getActivite().getId());
        dto.setActiviteNom(support.getActivite().getNom());
        dto.setEmailutilisateurAutorise(support.getUtilisateurAutorise().getEmail());

        // 🔥 Ajout des historiques...
        dto.setHistoriques(getHistorique(support.getId()));
        return dto;
    }


    public List<SupportActivite> rechercherSupports(String nom, LocalDate date, StatutSupport statut) {

        List<SupportActivite> supports = supportActiviteRepository.findAll();

        return supports.stream()
                .filter(s -> {
                    // 🔹 Filtre par nom si fourni
                    if (StringUtils.hasText(nom)) {
                        return s.getNom() != null &&
                               s.getNom().toLowerCase().contains(nom.toLowerCase());
                    }
                    return true;
                })
                .filter(s -> {
                    // 🔹 Filtre par statut si fourni
                    if (statut != null) {
                        return statut.equals(s.getStatut());
                    }
                    return true;
                })
                .filter(s -> {
                    // 🔹 Filtre par date si fourni
                    if (date != null && s.getDateAjout() != null) {
                        LocalDate dateAjout = s.getDateAjout()
                                                .toInstant()
                                                .atZone(ZoneId.systemDefault())
                                                .toLocalDate();
                        return date.equals(dateAjout);
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

}