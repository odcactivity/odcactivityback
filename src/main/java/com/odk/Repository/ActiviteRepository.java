package com.odk.Repository;

import com.odk.Entity.Activite;
import com.odk.Enum.Statut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface ActiviteRepository extends JpaRepository<Activite, Long> {
    Optional<Activite> findByNom(String nom);
    Optional<Activite> findByNomIgnoreCase(String nom);
    long count();
    @Query("SELECT COUNT(DISTINCT e.activite) FROM Etape e WHERE e.statut = :statut")
    long countActivitesByStatut(@Param("statut") Statut statut);

    @Query("SELECT COUNT(*) FROM Activite a WHERE a.createdBy.id= :userId")
    long countActivitesByUserCustom(@Param("userId") Long userId);

    long countByStatut(Statut statut);

    List<Activite> findByDateDebutBetween(Date start, Date end);

    @Query("SELECT COUNT(a) FROM Activite a WHERE a.statut = :statut")
    long countByStatutCustom(@Param("statut") Statut statut);

    @Query("SELECT COUNT(a) FROM Activite a WHERE a.statut = :statut and a.createdBy.id=:userId")
    long countByUserByStatutCustom(@Param("statut") Statut statut, @Param("userId") Long userId);

  /* @Query("SELECT a FROM Activite a WHERE a.salleId.id = :salleId AND " +
           "((:dateDebut BETWEEN a.dateDebut AND a.dateFin) OR " +
           "(:dateFin BETWEEN a.dateDebut AND a.dateFin) OR " +
           "(a.dateDebut BETWEEN :dateDebut AND :dateFin)) AND " +
           "a.statut != com.odk.Enum.Statut.Termine")
   List<Activite> findConflictingActivites(Long salleId, Date dateDebut, Date dateFin);*/

    @Query("SELECT a FROM Activite a " +
            "WHERE a.salleId.id = :salleId " +
            "AND ((:dateDebut < a.dateFin AND :dateFin > a.dateDebut)) " +
            "AND a.statut <> :statutTermine")
    List<Activite> findConflictingActivites(
            @Param("salleId") Long salleId,
            @Param("dateDebut") Date dateDebut,
            @Param("dateFin") Date dateFin,
            @Param("statutTermine") com.odk.Enum.Statut statutTermine
    );

    @Query("SELECT a FROM Activite a " +
            "WHERE LOWER(a.nom) = LOWER(:nom) " +
            "AND ((:dateDebut < a.dateFin AND :dateFin > a.dateDebut)) " +
            "AND a.statut <> :statutTermine")
    List<Activite> findConflictingNomActivites(
            @Param("nom") String nom,
            @Param("dateDebut") Date dateDebut,
            @Param("dateFin") Date dateFin,
            @Param("statutTermine") com.odk.Enum.Statut statutTermine);

    @Query(
            value = """
            SELECT DISTINCT a.*
            FROM activite a
            JOIN activite_validation av ON av.activite_id = a.id
            WHERE av.utilisateur_id = :superviseurId
            """,
            nativeQuery = true
    )
    List<Activite> findAllBySuperviseurInValidation(@Param("superviseurId") Long superviseurId);

    @Query(value = """
    SELECT DISTINCT a.*
    FROM activite a
    LEFT JOIN activite_validation av ON av.activite_id = a.id
    WHERE (av.envoyeur_id = :superviseurId OR av.utilisateur_id = :superviseurId)
    """, nativeQuery = true)
    List<Activite> findBySuperviseurIdOrNull(@Param("superviseurId") Long superviseurId);

    @Query(
            value = """
            SELECT DISTINCT a.*
            FROM activite a
            JOIN activite_validation av ON av.activite_id = a.id
            WHERE av.utilisateur_id = :superviseurId and av.statut=1
            """,
            nativeQuery = true
    )
    List<Activite> findAttenteBySuperviseurInValidation(@Param("superviseurId") Long superviseurId);


    @Query(
            value = """
           SELECT * FROM activite a WHERE a.created_by_id=:userId ORDER BY a.date_debut DESC""",
            nativeQuery = true
    )
    List<Activite> findByUser(@Param("userId") Long userId);

    @Query("""
    SELECT a FROM Activite a
    WHERE a.entite.id = :entiteId
    AND a.dateDebut BETWEEN :start AND :end
""")
    List<Activite> findByEntiteAndWeek(
            @Param("entiteId") Long entiteId,
            @Param("start") Date start,
            @Param("end") Date end
    );

}
