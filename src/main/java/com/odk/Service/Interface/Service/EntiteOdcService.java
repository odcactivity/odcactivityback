package com.odk.Service.Interface.Service;

import com.odk.Entity.Entite;
import com.odk.Entity.TypeActivite;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.TypeEntite;
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
import java.util.stream.Collectors;
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
        // Validation de la logique hiérarchique
        validerHierarchieEntite(dto);

        Entite entite = EntiteMapper.toEntity(dto);

        // Gestion du responsable
        if (dto.getResponsable() != null) {
            utilisateurRepository.findById(dto.getResponsable())
                    .ifPresent(entite::setResponsable);
        }

        // Gestion du parent pour les services
        if (dto.getParentId() != null && dto.getType() == TypeEntite.SERVICE) {
            entiteOdcRepository.findById(dto.getParentId())
                    .filter(parent -> parent.getType() == TypeEntite.DIRECTION)
                    .ifPresentOrElse(
                            entite::setParent,
                            () -> {
                                throw new IllegalArgumentException("Le parent doit être une direction de type DIRECTION");
                            }
                    );
        }

        Entite saved = entiteOdcRepository.save(entite);
        return EntiteMapper.toDto(saved);
    }

    /**
     * Valide la logique hiérarchique des entités
     * - DIRECTION: parentId doit être null
     * - SERVICE: parentId doit être non null et doit pointer vers une DIRECTION
     */
    private void validerHierarchieEntite(EntiteDTO dto) {
        if (dto.getType() == null) {
            throw new IllegalArgumentException("Le type d'entité est obligatoire (DIRECTION ou SERVICE)");
        }

        if (dto.getType() == TypeEntite.DIRECTION && dto.getParentId() != null) {
            throw new IllegalArgumentException("Une direction ne peut pas avoir de parent (parentId doit être null)");
        }

        if (dto.getType() == TypeEntite.SERVICE) {
            if (dto.getParentId() == null) {
                throw new IllegalArgumentException("Un service doit avoir un parent (parentId obligatoire)");
            }

            // Vérifier que le parent existe et est bien une direction
            Optional<Entite> parentOpt = entiteOdcRepository.findById(dto.getParentId());
            if (parentOpt.isEmpty()) {
                throw new IllegalArgumentException("L'entité parent avec l'ID " + dto.getParentId() + " n'existe pas");
            }

            Entite parent = parentOpt.get();
            if (parent.getType() != TypeEntite.DIRECTION) {
                throw new IllegalArgumentException("Un service doit avoir comme parent une direction, pas un autre service");
            }
        }
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

    /**
     * Récupère toutes les directions (entités de type DIRECTION)
     */
    public List<EntiteDTO> findDirections() {
        List<Entite> directions = entiteOdcRepository.findByType(TypeEntite.DIRECTION);
        return directions.stream()
                .map(EntiteMapper::toDto)
                .collect(Collectors.toList());
    }
    // Récupère tous les services (sans les directions)
    public List<EntiteDTO> findAllServices() {
        return entiteOdcRepository.findByType(TypeEntite.SERVICE)
                .stream()
                .map(EntiteMapper::toDto)
                .collect(Collectors.toList());
    }

    // Récupérer les services d'une direction parente
    public List<EntiteDTO> findServicesByParent(Long parentId) {
        List<Entite> services = entiteOdcRepository.findByParentId(parentId);
        return services.stream()
                .map(EntiteMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Test de la liaison responsable-entité
     */
    public String testLiaisonResponsable(Long entiteId) {
        Optional<Entite> entiteOpt = entiteOdcRepository.findById(entiteId);
        if (entiteOpt.isEmpty()) {
            return "Entité non trouvée";
        }

        Entite entite = entiteOpt.get();
        StringBuilder result = new StringBuilder();
        result.append("Entité: ").append(entite.getNom()).append("\n");
        result.append("Type: ").append(entite.getType()).append("\n");

        if (entite.getResponsable() != null) {
            result.append("Responsable ID: ").append(entite.getResponsable().getId()).append("\n");
            result.append("Responsable Nom: ").append(entite.getResponsable().getNom()).append("\n");
            result.append("Responsable Email: ").append(entite.getResponsable().getEmail()).append("\n");
        } else {
            result.append("Responsable: NULL ❌\n");
        }

        return result.toString();
    }

}



