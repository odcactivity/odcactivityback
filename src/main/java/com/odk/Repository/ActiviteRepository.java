package com.odk.Repository;

import com.odk.Entity.Activite;
import com.odk.Enum.DecisionDirecteurOdc;
import com.odk.Enum.Statut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface ActiviteRepository extends JpaRepository<Activite, Long> {

    Optional<Activite> findByNom(String nom);
    Optional<Activite> findByNomIgnoreCase(String nom);
    long count();

    @Query("SELECT COUNT(DISTINCT e.activite) FROM Etape e WHERE e.statut = :statut")
    long countActivitesByStatut(@Param("statut") Statut statut);

    @Query("SELECT COUNT(a) FROM Activite a WHERE a.createdBy.id= :userId")
    long countActivitesByUserCustom(@Param("userId") Long userId);

    long countByStatut(Statut statut);

    /** Pour le dashboard : « en attente » = en file + en validation directeur ODC (aligné sur le graphe). */
    long countByStatutIn(Collection<Statut> statuts);

    List<Activite> findByDateDebutBetween(Date start, Date end);

    @Query("SELECT COUNT(a) FROM Activite a WHERE a.statut = :statut")
    long countByStatutCustom(@Param("statut") Statut statut);

    @Query("SELECT COUNT(a) FROM Activite a WHERE a.statut = :statut and a.createdBy.id=:userId")
    long countByUserByStatutCustom(@Param("statut") Statut statut, @Param("userId") Long userId);

    @Query("SELECT a FROM Activite a " +
            "WHERE a.salleId.id = :salleId " +
            "AND ((:dateDebut < a.dateFin AND :dateFin > a.dateDebut)) " +
            "AND a.statut <> :statutTermine AND a.statut <> :statutRejetee")
    List<Activite> findConflictingActivites(
            @Param("salleId") Long salleId,
            @Param("dateDebut") Date dateDebut,
            @Param("dateFin") Date dateFin,
            @Param("statutTermine") Statut statutTermine,
            @Param("statutRejetee") Statut statutRejetee
    );

    @Query("SELECT a FROM Activite a " +
            "WHERE LOWER(a.nom) = LOWER(:nom) " +
            "AND ((:dateDebut < a.dateFin AND :dateFin > a.dateDebut)) " +
            "AND a.statut <> :statutTermine AND a.statut <> :statutRejetee")
    List<Activite> findConflictingNomActivites(
            @Param("nom") String nom,
            @Param("dateDebut") Date dateDebut,
            @Param("dateFin") Date dateFin,
            @Param("statutTermine") Statut statutTermine,
            @Param("statutRejetee") Statut statutRejetee
    );

    // --- Méthodes pour validation et supervision ---
    @Query(
            value = "SELECT DISTINCT a.* FROM activite a JOIN activite_validation av ON av.activite_id = a.id WHERE av.utilisateur_id = :superviseurId",
            nativeQuery = true
    )
    List<Activite> findAllBySuperviseurInValidation(@Param("superviseurId") Long superviseurId);

    @Query(value = "SELECT DISTINCT a.* FROM activite a LEFT JOIN activite_validation av ON av.activite_id = a.id WHERE (av.envoyeur_id = :superviseurId OR av.utilisateur_id = :superviseurId)", nativeQuery = true)
    List<Activite> findBySuperviseurIdOrNull(@Param("superviseurId") Long superviseurId);

    @Query(value = "SELECT DISTINCT a.* FROM activite a JOIN activite_validation av ON av.activite_id = a.id WHERE av.utilisateur_id = :superviseurId AND av.statut=1", nativeQuery = true)
    List<Activite> findAttenteBySuperviseurInValidation(@Param("superviseurId") Long superviseurId);

    @Query(value = "SELECT * FROM activite a WHERE a.created_by_id=:userId ORDER BY a.date_debut DESC", nativeQuery = true)
    List<Activite> findByUser(@Param("userId") Long userId);

    @Query("SELECT a FROM Activite a WHERE a.entite.id = :entiteId AND a.dateDebut BETWEEN :start AND :end")
    List<Activite> findByEntiteAndWeek(
            @Param("entiteId") Long entiteId,
            @Param("start") Date start,
            @Param("end") Date end
    );

    //  Nouvelle méthode : toutes les activités d'une liste d'entités (direction + services)
    @Query("SELECT a FROM Activite a WHERE a.entite.id IN :entiteIds AND a.dateDebut BETWEEN :start AND :end")
    List<Activite> findByEntiteIdsAndWeek(
            @Param("entiteIds") List<Long> entiteIds,
            @Param("start") Date start,
            @Param("end") Date end
    );

    List<Activite> findByStatut(Statut statut);

    @Query("SELECT a FROM Activite a WHERE a.statut = :statut AND a.entite.id IN :entiteIds ORDER BY a.dateDebut DESC")
    List<Activite> findByStatutAndEntiteIdIn(
            @Param("statut") Statut statut,
            @Param("entiteIds") Collection<Long> entiteIds);

    @Query("SELECT a FROM Activite a WHERE (a.transmiseDirecteurOdcLe IS NOT NULL "
            + "OR a.directeurOdcDecision IS NOT NULL "
            + "OR a.statut = :statutEnValidationDirecteur) "
            + "AND a.entite.id IN :entiteIds "
            + "ORDER BY COALESCE(a.transmiseDirecteurOdcLe, a.directeurOdcTraiteLe, a.dateDebut) DESC")
    List<Activite> findHistoriqueTransmissionsDirecteurOdcPourEntites(
            @Param("statutEnValidationDirecteur") Statut statutEnValidationDirecteur,
            @Param("entiteIds") Collection<Long> entiteIds);

    List<Activite> findByTransmiseDirecteurOdcLeIsNotNullOrderByTransmiseDirecteurOdcLeDesc();

    @Query("SELECT a FROM Activite a WHERE a.transmiseDirecteurOdcLe IS NOT NULL "
            + "OR a.directeurOdcDecision IS NOT NULL "
            + "OR a.statut = :statutEnValidationDirecteur "
            + "ORDER BY COALESCE(a.transmiseDirecteurOdcLe, a.directeurOdcTraiteLe, a.dateDebut) DESC")
    List<Activite> findHistoriqueTransmissionsDirecteurOdc(
            @Param("statutEnValidationDirecteur") Statut statutEnValidationDirecteur);

    List<Activite> findByDirecteurOdcDecisionOrderByDirecteurOdcTraiteLeDesc(DecisionDirecteurOdc decision);

    @Query("SELECT a FROM Activite a WHERE (:entiteId IS NULL OR a.entite.id = :entiteId) "
            + "AND (:activiteId IS NULL OR a.id = :activiteId) "
            + "AND a.dateDebut >= :debut AND a.dateDebut < :fin")
    List<Activite> findPourRapportGlobal(
            @Param("entiteId") Long entiteId,
            @Param("activiteId") Long activiteId,
            @Param("debut") Date debut,
            @Param("fin") Date fin
    );
}