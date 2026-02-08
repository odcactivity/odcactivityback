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
     * Récupérer tous les participants
     */
    public List<ReportingDTO> getAllParticipants() {
        return participantRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Récupérer les participants filtrés par entité et année
     */
    public List<ReportingDTO> getParticipantsFiltered(Long entiteId, Integer annee) {
        return participantRepository.findAll().stream()
                .filter(p -> {
                    boolean okEntite = entiteId == null ||
                            (p.getActivite() != null &&
                                    p.getActivite().getEntite() != null &&
                                    p.getActivite().getEntite().getId().equals(entiteId));

                    boolean okAnnee = annee == null ||
                            (p.getActivite() != null &&
                                    p.getActivite().getDateDebut() != null &&
                                    p.getActivite().getDateDebut().toInstant()
                                            .atZone(ZoneId.systemDefault())
                                            .getYear() == annee);

                    return okEntite && okAnnee;
                })
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Mapper un participant en DTO
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
