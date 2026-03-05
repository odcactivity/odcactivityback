package com.odk.Service.Interface.Service;

import com.odk.Repository.ParticipantRepository;
import com.odk.dto.ReportingDTO;
import com.odk.Entity.Participant;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ReportingService {

    private final ParticipantRepository participantRepository;

    /**
     * Reporting complet avec filtres optionnels
     */
    public List<ReportingDTO> getParticipantsFiltered(
            Long entiteId,
            Long activiteId,
            Integer annee
    ) {

        return participantRepository.findAll().stream()
                .filter(p -> {

                    if (p.getActivite() == null) return false;

                    boolean okEntite = true;
                    boolean okActivite = true;
                    boolean okAnnee = true;

                    // ✅ FILTRE ENTITE (Direction ou Service)
                    if (entiteId != null) {
                        if (p.getActivite().getEntite() != null) {

                            Long currentEntiteId = p.getActivite().getEntite().getId();

                            Long parentId = p.getActivite().getEntite().getParent() != null
                                    ? p.getActivite().getEntite().getParent().getId()
                                    : null;

                            okEntite = entiteId.equals(currentEntiteId)
                                    || entiteId.equals(parentId);

                        } else {
                            okEntite = false;
                        }
                    }

                    // ✅ FILTRE ACTIVITE
                    if (activiteId != null) {
                        okActivite = activiteId.equals(p.getActivite().getId());
                    }

                    // ✅ FILTRE ANNEE
                    if (annee != null && p.getActivite().getDateDebut() != null) {

                        int year = p.getActivite().getDateDebut()
                                .toInstant()
                                .atZone(ZoneId.systemDefault())
                                .getYear();

                        okAnnee = year == annee;
                    }

                    return okEntite && okActivite && okAnnee;
                })
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Mapper Participant → ReportingDTO
     */
    private ReportingDTO mapToDTO(Participant p) {
        return new ReportingDTO(
                p.getNom(),
                p.getPrenom(),
                p.getEmail(),
                p.getPhone(),
                p.getGenre(),
                p.getActivite() != null ? p.getActivite().getNom() : "",
                p.getActivite() != null && p.getActivite().getEntite() != null
                        ? p.getActivite().getEntite().getNom()
                        : "",
                p.getAge(),
                p.getActivite() != null ? p.getActivite().getDateDebut() : null,
                p.getActivite() != null ? p.getActivite().getDateFin() : null
        );
    }
}