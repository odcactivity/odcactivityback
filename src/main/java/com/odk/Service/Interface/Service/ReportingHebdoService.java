package com.odk.Service.Interface.Service;

import com.odk.Entity.Activite;
import com.odk.Repository.ActiviteRepository;
import com.odk.dto.ReportingHebdoActiviteDTO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ReportingHebdoService {

    private final ActiviteRepository activiteRepository;

    /**
     * Récupère la liste des activités par entité et période
     */
    public List<ReportingHebdoActiviteDTO> getActivitesHebdo(
            Long entiteId,
            Date dateDebut,
            Date dateFin
    ) {
        List<Activite> activites =
                activiteRepository.findByEntiteAndWeek(entiteId, dateDebut, dateFin);

        return activites.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Mapping Activite -> DTO
     */
    private ReportingHebdoActiviteDTO mapToDTO(Activite a) {
        return new ReportingHebdoActiviteDTO(
                a.getId(),
                a.getNom(),
                a.getTitre(),
                a.getDateDebut(),
                a.getDateFin(),
                a.getStatut(),
                a.getLieu(),
                a.getDescription(),
                a.getObjectifParticipation(),
                a.getEntite(),
                a.getCreatedBy() != null
                        ? a.getCreatedBy().getNom() + " " + a.getCreatedBy().getPrenom()
                        : null,
                a.getTypeActivite(),
                a.getCandidatureRecu(),   //
                a.getCandidatureFemme(),  //
                a.getCible()
        );
    }
}
