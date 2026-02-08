package com.odk.Controller;

import com.odk.Entity.SupportActivite;
import com.odk.Enum.StatutSupport;
import com.odk.Enum.TypeSupport;
import com.odk.Repository.SupportActiviteRepository;
import com.odk.Service.Interface.Service.SupportActiviteService;
import com.odk.dto.HistoriqueSupportActiviteDTO;
import com.odk.dto.SupportActiviteResponseDTO;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/supports")
public class SupportActiviteController {

    private final SupportActiviteService supportService;

    private final SupportActiviteRepository supportActiviteRepository;

    public SupportActiviteController(SupportActiviteService supportService, SupportActiviteRepository supportActiviteRepository) {
        this.supportService = supportService;
        this.supportActiviteRepository=supportActiviteRepository;
    }

    // -------------------------- UPLOAD/Telechargement de Fichier -------------------------- //
    // ------------------------------------------------------------------------------------- //
    @PostMapping("/upload")
    public ResponseEntity<SupportActiviteResponseDTO> uploadSupport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("idActivite") Long idActivite,
            @RequestParam("description") String description,
            @RequestParam("utilisateurId") Long utilisateurId
    ) throws IOException {
        // 🔥 Récupérer l'utilisateur qui upload / est affecté
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SupportActivite support = supportService.saveSupport(file, idActivite, description, utilisateurId, username);
        return ResponseEntity.ok(supportService.convertToDTO(support));
    }

    // ------------------------- GET ALL --------------------------------------------- //
    // ------------------------------------------------------------------------------ //
    @GetMapping
    public ResponseEntity<List<SupportActiviteResponseDTO>> getAllSupports() {
        List<SupportActiviteResponseDTO> supports = supportService.getAllSupports();
        return ResponseEntity.ok(supports);
    }

    // ------------------------ GET BY ID/Afficher un support par ID------------------------- //
    // ------------------------------------------------------------------------------------- //
    @GetMapping("/{id}")
    public ResponseEntity<SupportActiviteResponseDTO> getSupportById(@PathVariable Long id) {
        SupportActiviteResponseDTO dto = supportService.getSupportById(id);
        return ResponseEntity.ok(dto);
    }

    // ----------------------- UPDATE STATUT ------------------------------------------------- //
    // -------------------------------------------------------------------------------------- //
    @PatchMapping("/update/{id}")
    public ResponseEntity<SupportActiviteResponseDTO> updateStatut(
            @PathVariable Long id,
            @RequestParam("statut") StatutSupport statut,
            @RequestParam(value = "commentaire", required = false) String commentaire
    ) {

        // 🔥 Verifie que le commentaire n'est pas vide && nul...
        if((statut==StatutSupport.A_CORRIGER||statut==StatutSupport.REFUSER)&&(commentaire==null||commentaire.trim().isEmpty())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le commentaire est obligatoire...");
        }

        // 🔥 Verifie que le satus est fourni ...
        if(statut==null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Le status est obligatoire... ");
        }

        // 🔥 Recuperer le context de sécurité avant la modification...
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SupportActivite updated = supportService.updateStatut(id, statut, commentaire, username);
        return ResponseEntity.ok(supportService.convertToDTO(updated));
    }

    // --------------------------- DELETE ------------------------------------------------- //
    // ----------------------------------------------------------------------------------- //
   @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteSupport(@PathVariable Long id) throws IOException {
    // 🔥 Récupération de l'utilisateur connecté ...
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

    // 🔥 Appel du service avec l'id du support et le username
        supportService.deleteSupport(id, username);

    return ResponseEntity.noContent().build();
}

// ------------------------------ Afficher Historique Fichier ------------------------------- //
//------------------------------------------------------------------------------------------- //
@GetMapping("/historique/{id}")
public ResponseEntity<List<HistoriqueSupportActiviteDTO>> getHistorique(@PathVariable Long id) {
    List<HistoriqueSupportActiviteDTO> historiques = supportService.getHistorique(id);
    return ResponseEntity.ok(historiques);
}

// ------------------------------ Doawload/Telechargement-----------------------------------------------------//
@GetMapping("/doawload/{id}")
public ResponseEntity<Resource> doawloadSupport(@PathVariable Long id, Principal principal) throws IOException {
    
    // 🔥 Récupérer le support par son id
    SupportActivite support = supportActiviteRepository.findById(id)
       .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND, "Document non trouvé")
        );

       // 🔥 Verification de l'utilisateur connecté est aitorisé
       String username = SecurityContextHolder.getContext().getAuthentication().getName();
       if(!support.getUtilisateurAutorise().getUsername().equals(username)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n'etes pas autorisé à télécharger ce fichier");
       }

        // 🔥 Récuperer le chemin physique securiser du fichier via ton service
            Path filePath = supportService.getFilePath(support);
        // 🔥 Récupérer le chemin du fichier
            System.out.println("CHEMIN DU FICHIER = " + filePath.toAbsolutePath());

            Resource resource = new UrlResource(filePath.toUri());

       // 🔥 Vérifier que le fichier existe
            if(!resource.exists()){
             throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable");
            }

            String contentType = Files.probeContentType(filePath);
        
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filePath.getFileName().toString() + "\"")
                    .body(resource);

    //   // 🔥 Créer la resource en échappant correctement le Path pour UrlResource
    //        URI fileUri = filePath.toUri(); // transforme le Path en URI compatible
    //        Resource resource = new UrlResource(fileUri);
    //       // Resource resource = new UrlResource(filePath.toUri());
    //   // 🔥 Déterminer le type de contenu (MIME type)
    //       String contentType= Files.probeContentType(filePath);
    //  // 🔥 Retourner le fichier


}

 // ---------------- Upload nouveau avec validation ----------------
    @PostMapping("/upload-validated")
    public ResponseEntity<SupportActiviteResponseDTO> uploadSupportValidated(
            @RequestParam("file") MultipartFile file,
            @RequestParam("idActivite") Long idActivite,
            @RequestParam("description") String description
    ) throws IOException {
        SupportActiviteResponseDTO dto = supportService.convertToDTO(
                supportService.saveSupportWithValidation(file, idActivite, 1L, description)
        );
        return ResponseEntity.ok(dto);
    }

    // ---------------- Filtrer par type de fichier----------------
    @GetMapping("/type/{type}")
    public ResponseEntity<List<SupportActiviteResponseDTO>> getSupportsByType(@PathVariable TypeSupport type){
        return ResponseEntity.ok(supportService.getSupportsByType(type));
    }

//---------------------------Rechercher un Media par nom, date & Statut----------------//
//------------------------------------------------------------------------------------// 
@GetMapping("/recherche")
public ResponseEntity<List<SupportActiviteResponseDTO>> rechercherSupports(
        @RequestParam(required = false) String nom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) StatutSupport statut) {

    List<SupportActivite> supports = supportService.rechercherSupports(nom, date, statut);
    List<SupportActiviteResponseDTO> dtos = supports.stream()
            .map(supportService::convertToDTO)
            .collect(Collectors.toList());
    return ResponseEntity.ok(dtos);
} 
   
}