package com.odk.Service.Interface.Service;

import com.odk.Entity.Critere;
import com.odk.Entity.Etape;
import com.odk.Entity.Liste;
import com.odk.Entity.Participant;
import com.odk.Enum.Statut;
import com.odk.Repository.*;
import com.odk.Service.Interface.CrudService;
import com.odk.dto.CritereDTO;
import com.odk.dto.EtapeDTO;
import com.odk.dto.EtapeDTOSansActivite;
import com.odk.dto.EtapeMapper;
import com.odk.dto.ImportReponse;
import com.odk.helper.ExcelHelper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

@Service
@AllArgsConstructor
public class EtapeService implements CrudService<Etape, Long> {

    private EtapeRepository etapeRepository;
    private ActiviteRepository activiteRepository;
    private ParticipantRepository participantRepository;
    private ActiviteParticipantRepository activiteParticipantRepository;
    private CritereRepository critereRepository;
    private ListeRepository listeRepository;
    private final EtapeMapper etapeMapper;
    private BlackListRepository blackListRepository;

    private static final Logger logger = LoggerFactory.getLogger(EtapeService.class);

    // Convertit une entité Etape en DTO
    public EtapeDTO convertToDto(Etape etape) {
        EtapeDTO dto = new EtapeDTO();
        dto.setId(etape.getId());
        dto.setNom(etape.getNom());
        dto.setDateDebut(etape.getDateDebut());
        dto.setDateFin(etape.getDateFin());
        dto.setStatut(etape.getStatut());

        // Initialisation des listes si elles ne le sont pas déjà
//        dto.setListeDebut(new ArrayList<>());
//        dto.setListeResultat(new ArrayList<>());
        dto.setCreated_by(etape.getCreated_by());

        

        // Convertir les Critere en CritereDTO si nécessaire
        if (etape.getCritere() != null) {
            dto.setCritere(etape.getCritere());
//            dto.setCritere(etape.getCritere().stream()
//                    .map(this::convertToCritereDto) // Créez une méthode convertToCritereDto
//                    .collect(Collectors.toList()));
        }

        return dto;
    }

    // Méthode pour convertir Critere en CritereDTO (à implémenter)
    private CritereDTO convertToCritereDto(Critere critere) {
        CritereDTO critereDTO = new CritereDTO();
        critereDTO.setId(critere.getId());
        // Définir les autres propriétés de CritereDTO
        return critereDTO;
    }

    @Override
    @Transactional
    public Etape add(Etape etape) {
        etape.mettreAJourStatut();
         return etapeRepository.save(etape);
    }
   
    @Transactional
    public EtapeDTO addDTO(EtapeDTO etapeDTO) {
        System.err.println("ajjout ===========etap"+etapeDTO.getCreated_by().getNom());
        Etape etape=etapeMapper.toEntity(etapeDTO);
        etape.mettreAJourStatut();
        Etape saved = etapeRepository.save(etape);
         return etapeMapper.toDto(saved);
    }



    @Override
    public List<Etape> List() {
        return etapeRepository.findAll();
    }



    public List<EtapeDTO> getByIdEtapes(Long id) {
        List<Etape> etapes = etapeRepository.findAll();
        return etapes.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<EtapeDTO> getAllEtapes() {
        List<Etape> etapes = etapeRepository.findAll();
        return etapeMapper.listeEtape(etapes);
//        return etapes.stream()
//                .map(this::convertToDto)
//                .collect(Collectors.toList());
    }
    public List<EtapeDTOSansActivite> getAllEtapesSansActivite() {
        List<Etape> etapes = etapeRepository.findAll();
//        return etapes.stream()
//                .map(this::convertToDto)
//                .collect(Collectors.toList());
        return etapeMapper.listeEtapeSansActivite(etapes);
    }


    @Override
    public Optional<Etape> findById(Long id) {
        return etapeRepository.findById(id);
    }
    @Override
    public Etape update(Etape entity, Long id) {
        return etapeRepository.findById(id).map(e -> {
            // Si le nom n'est pas nul, mettre à jour
            if (entity.getNom() != null) {
                e.setNom(entity.getNom());
            }

            // Si le statut n'est pas nul, mettre à jour
            if (entity.getStatut() != null) {
                e.setStatut(entity.getStatut());
            }

            if (entity.getDateDebut() != null) {
                e.setDateDebut(entity.getDateDebut());
            }

            if (entity.getDateFin() != null) {
                e.setDateFin(entity.getDateFin());
            }

            // Si l'activité est définie, la mettre à jour (vérifier si elle existe)
            if (entity.getActivite() != null) {
                if (activiteRepository.existsById(entity.getActivite().getId())) {
                    e.setActivite(entity.getActivite());
                } else {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activité non trouvée");
                }
            }

            // Si le critère est défini, le mettre à jour (vérifier s'il existe)
            if (entity.getCritere() != null && !entity.getCritere().isEmpty()) {
                for (Critere critere : entity.getCritere()) {
                    if (critereRepository.existsById(critere.getId())) {
                        e.getCritere().add(critere); // Ajoute le critère à l'entité cible
                    } else {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Critère non trouvé : ID " + critere.getId());
                    }
                }
            }


            // Mise à jour du statut dynamiquement
            e.mettreAJourStatut();

            // Sauvegarder l'entité mise à jour
            return etapeRepository.save(e);
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "L'id n'est pas disponible"));
    }




    @Override
    public void delete(Long id) {
        Optional<Etape> optionalEtape = etapeRepository.findById(id);
        optionalEtape.ifPresent(etape -> etapeRepository.delete(etape));
    }

    @Transactional
    public void addParticipantsToEtape(Long id, MultipartFile file, boolean toListeDebut) throws IOException {
        // Log de débogage
//        System.out.println("toListeDebut : " + toListeDebut);

// Récupérer l'étape par ID
        Etape etape = etapeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Étape non trouvée avec l'ID : " + id));

        // Convertir le fichier Excel en une liste de participants
        List<Participant> participants = ExcelHelper.excelToTutorials(etape,file, activiteRepository, activiteParticipantRepository, participantRepository,blackListRepository);

        
//        // Récupérer la liste associée à cette étape
//        Liste liste = listeRepository.findById(id)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Liste non trouvée pour cette étape"));


        List<Liste> listes = etape.getListes(); // Récupérer le Set de Listes
        Liste listeAMettreAJour = null;

        if (toListeDebut) {
            listeAMettreAJour = listes.stream().filter(Liste::isListeDebut).findFirst().orElse(null);
            if (listeAMettreAJour == null) {
                listeAMettreAJour = new Liste();
                listeAMettreAJour.setEtape(etape);
                listeAMettreAJour.setListeDebut(true);
                listeAMettreAJour.setDateHeure(LocalDateTime.now());
                listes.add(listeAMettreAJour); // Ajouter la nouvelle Liste au Set
            }
        } else {
            listeAMettreAJour = listes.stream().filter(Liste::isListeResultat).findFirst().orElse(null);
            if (listeAMettreAJour == null) {
                listeAMettreAJour = new Liste();
                listeAMettreAJour.setEtape(etape);
                listeAMettreAJour.setListeResultat(true);
                listeAMettreAJour.setDateHeure(LocalDateTime.now());
                listes.add(listeAMettreAJour); // Ajouter la nouvelle Liste au Set
            }
        }

        if (listeAMettreAJour != null) { // S'assurer que vous avez trouvé ou créé une Liste
            for (Participant participant : participants) {
                participant.setListe(listeAMettreAJour);
            }

//            if (toListeDebut) {
//                etape.addParticipantsToListeDebut(participants);
//            } else {
//                etape.addParticipantsToListeResultat(participants);
//            }

            listeRepository.save(listeAMettreAJour); // Enregistrer la Liste mise à jour
            etapeRepository.save(etape); // Enregistrer l'Etape (se propage en cascade)
        }

        // Sauvegarder les participants et l'étape dans la base de données
        participantRepository.saveAll(participants);
        etapeRepository.save(etape);
    }

//pour retourner une reponse correcte
    @Transactional
    public ImportReponse addParticipantsToEtapeNew(Long id, MultipartFile file, boolean toListeDebut) throws IOException {
       
// Récupérer l'étape par ID
        Etape etape = etapeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Étape non trouvée avec l'ID : " + id));

        // Convertir le fichier Excel en une liste de participants
//        List<Participant> participants = ExcelHelper.excelToTutorials(etape,file, activiteRepository, activiteParticipantRepository, participantRepository,blackListRepository);
        ImportReponse retourimport = ExcelHelper.excelToTutorialsNew(etape,file, activiteRepository, activiteParticipantRepository, participantRepository,blackListRepository,toListeDebut,listeRepository,etapeRepository);

        

        
        return retourimport ;
    }
    
    
    

    public List<EtapeDTO> getEtapeDTO(Long id) {
        return etapeMapper.INSTANCE.listeEtape(etapeRepository.findEtapeById(id));
    }

    public boolean isEtapeModifiable(Long etapeId) {
        Etape etape = etapeRepository.findById(etapeId)
                .orElseThrow(() -> new RuntimeException("Étape non trouvée"));

        Date maintenant = new Date();

        // Vérifier si l'étape est terminée par la date
        if (etape.getDateFin() != null && maintenant.after(etape.getDateFin())) {
            return false;
        }

        // Vérifier si l'étape a le statut terminé
        if (etape.getStatut() == Statut.Termine) {
            return false;
        }

        return true;
    }
public boolean isEtapeLierActivite(Long etapeId) {
    Etape e=etapeRepository.findById(etapeId).get();
//    if(e.getActivite().getId()!=0)
    return true;
}
    public void validateEtapeForModification(Long etapeId) {
        if (!isEtapeModifiable(etapeId)) {
            throw new EtapeTermineeException("L'étape est terminée et ne peut plus être modifiée");
        }
    }
     public void validateEtapeForActivite(Long etapeId) {
        if (!isEtapeLierActivite(etapeId)) {
            throw new EtapeTermineeException("L'étape n'est pas liée à l'activite indiquée");
        }
    }

    public static class EtapeTermineeException extends RuntimeException {
        public EtapeTermineeException(String message) {
            super(message);
        }

    }
}
