package com.odk.Service.Interface.Service;

import com.odk.Entity.Activite;
import com.odk.Entity.Entite;
import com.odk.Repository.ActiviteRepository;
import com.odk.Repository.EntiteOdcRepository;
import com.odk.dto.ReportingHebdoActiviteDTO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ReportingHebdoService {

    private final ActiviteRepository activiteRepository;
    private final EntiteOdcRepository entiteRepository;

    public List<ReportingHebdoActiviteDTO> getActivitesHebdo(
            Long entiteId,
            Date dateDebut,
            Date dateFin
    ) {

        Entite entite = entiteRepository.findById(entiteId)
                .orElseThrow(() -> new RuntimeException("Entité introuvable"));

        List<Long> entiteIds = new ArrayList<>();

        // Ajouter la direction
        entiteIds.add(entite.getId());

        // Ajouter les services enfants si existent
        if (entite.getSousEntite() != null && !entite.getSousEntite().isEmpty()) {
            for (Entite service : entite.getSousEntite()) {
                entiteIds.add(service.getId());
            }
        }

        List<Activite> activites = activiteRepository
                .findByEntiteIdsAndWeek(entiteIds, dateDebut, dateFin);

        return activites.stream()
                .map(a -> new ReportingHebdoActiviteDTO(
                        a.getId(),
                        a.getNom(),
                        a.getTitre(),
                        a.getDateDebut(),
                        a.getDateFin(),
                        a.getStatut(),
                        a.getLieu(),
                        a.getDescription(),
                        a.getObjectifParticipation(),
                        a.getEntite() != null ? a.getEntite().getNom() : null,
                        a.getCreatedBy() != null
                                ? a.getCreatedBy().getNom() + " " + a.getCreatedBy().getPrenom()
                                : null,
                        a.getTypeActivite(),
                        a.getCandidatureRecu(),
                        a.getCandidatureFemme(),
                        a.getCible()
                ))
                .toList();
    }
}