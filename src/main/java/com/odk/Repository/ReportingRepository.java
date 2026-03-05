package com.odk.Repository;

import com.odk.dto.ReportingDTO;
import com.odk.Entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportingRepository extends JpaRepository<Participant, Long> {

    @Query("""
SELECT new com.odk.dto.ReportingDTO(
    p.nom,
    p.prenom,
    p.email,
    p.phone,
    p.genre,
    a.nom,
    e.nom,
    p.age,
    a.dateDebut,
    a.dateFin
)
FROM Participant p
JOIN p.activite a
JOIN a.entite e
WHERE (
    :entiteId IS NULL
    OR e.parent.id = :entiteId
)
AND (:activiteId IS NULL OR a.id = :activiteId)
AND (:annee IS NULL OR FUNCTION('YEAR', a.dateDebut) = :annee)
""")
    List<ReportingDTO> findReportingFiltered(
            @Param("entiteId") Long entiteId,
            @Param("activiteId") Long activiteId,
            @Param("annee") Integer annee
    );
}