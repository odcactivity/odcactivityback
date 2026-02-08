package com.odk.Service.Interface.Service;

import com.odk.Entity.Entite;
import com.odk.Entity.Utilisateur;
import com.odk.Repository.EntiteOdcRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.Service.Interface.CrudService;
import com.odk.dto.EntiteDTO;
import com.odk.dto.EntiteMapper;
import java.io.IOException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

@Service
@AllArgsConstructor
public class EntiteOdcService implements CrudService<Entite, Long> {

    private EntiteOdcRepository entiteOdcRepository;
    private UtilisateurRepository utilisateurRepository;
    @Override
    public Entite add(Entite entiteOdc) {
        return entiteOdcRepository.save(entiteOdc);
    }
   
    public Entite add2(Entite entiteOdc,MultipartFile File) {        
        return entiteOdcRepository.save(entiteOdc);
    }
    public EntiteDTO ajouter(EntiteDTO dto, MultipartFile fichier) throws IOException {
        Entite entite = EntiteMapper.toEntity(dto);

//        if (fichier != null && !fichier.isEmpty()) { 
//            
//                String imagePath = fileStorage.saveImage(fichier);
//                System.out.println("lien logo==="+imagePath);
//                dto.setLogo(imagePath);
//            
//            
//        }

        Entite saved = entiteOdcRepository.save(entite);
        return EntiteMapper.toDto(saved);
    }

    @Override
    public List<Entite> List() {
        return entiteOdcRepository.findAll();
    }

    public List<EntiteDTO> allList() {
        return entiteOdcRepository.findAll()
                .stream()
                .map(EntiteMapper::toDto)
                .toList();
    }

    @Override
    public Optional<Entite> findById(Long id) {
        return entiteOdcRepository.findById(id);
    }

    public Optional<EntiteDTO> findParId(Long id) {
        return entiteOdcRepository.findById(id)
                .map(EntiteMapper::toDto);
    }

    @Override
    public Entite update(Entite entity, Long id) {
        Optional<Entite> entiteOdcOpt = entiteOdcRepository.findById(id);

        if (entiteOdcOpt.isPresent()) {
            Entite existingEntite = entiteOdcOpt.get();

            // Mettre à jour les champs uniquement si de nouvelles valeurs sont fournies
            if (entity.getNom() != null) {
                existingEntite.setNom(entity.getNom());
            }
            if (entity.getDescription() != null) {
                existingEntite.setDescription(entity.getDescription());
            }
            if (entity.getLogo() != null) {
                existingEntite.setLogo(entity.getLogo());
            }
            if (entity.getResponsable() != null) {
                existingEntite.setResponsable(entity.getResponsable());
            }
            if (entity.getTypeActivitesIds()!= null) {
                existingEntite.setTypeActivitesIds(entity.getTypeActivitesIds());
            }

            // Sauvegarder les modifications dans la base de données
            return entiteOdcRepository.save(existingEntite);
        }

        // Retourne null si l'entité avec l'ID donné n'existe pas
        return null;
    }


    @Override
    public void delete(Long id) {
        Optional<Entite> entiteOdc = entiteOdcRepository.findById(id);
        if (entiteOdc.isPresent()) {
            entiteOdcRepository.deleteById(id);
        }
    }

    public Long getCountOfActivitiesByEntiteId(Long entiteId) {
        return entiteOdcRepository.countActivitiesByEntiteId(entiteId);
    }

    public List<Utilisateur> findUtilisateursByRole(String roleName) {
        return utilisateurRepository.findByRoleNom(roleName);
    }

}
