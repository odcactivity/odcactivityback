package com.odk.Repository;

import java.time.LocalDate;
import java.util.List;

import com.odk.Entity.Entite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.odk.Entity.Courrier;
import com.odk.Enum.StatutCourrier;

import java.util.Collection;

@Repository
public interface CourrierRepository extends JpaRepository<Courrier,Long> {

    // Récupérer tous les courriers actifs (tous sauf ARCHIVER)
    List<Courrier> findByStatutNot(StatutCourrier statut);

    // Récupérer tous les courriers actifs sans rappel envoyé
    List<Courrier> findByStatutNotAndRappelEnvoyeFalse(StatutCourrier statut);

    // Récupérer tous les courriers actifs sans alerte envoyée
    List<Courrier> findByStatutNotAndAlerteEnvoyeeFalse(StatutCourrier statut);

    //Récupéré les courriers actifs (tous sauf ARCHIVE)
    List<Courrier> findByEntiteIdAndStatutNot(Long entiteId, StatutCourrier statut);

    //Récupérer les courriers archivés
    List<Courrier> findByEntiteIdAndStatut(Long entiteId, StatutCourrier statut);

    List<Courrier> findByStructureOrigineIdAndStatutNotOrderByDateReceptionDesc(
            Long structureOrigineId, StatutCourrier statut);
    List<Courrier> findByDirectionInitialAndStatut(Entite directionInitialId, StatutCourrier statut);
    List<Courrier> findByDirectionInitial(Entite directionInitialId);

    List<Courrier> findByEntiteIdAndStatutIn(Long entiteId, Collection<StatutCourrier> statuts);

    @Query("SELECT c FROM Courrier c WHERE c.statut IN :statuts AND (" +
            "(c.structureOrigine IS NOT NULL AND c.structureOrigine.id = :dirId) OR " +
            "(c.structureOrigine IS NULL AND c.directionInitial IS NOT NULL AND c.directionInitial.id = :dirId) OR " +
            "(c.entite IS NOT NULL AND c.entite.id = :dirId) OR " +
            "(c.entite IS NOT NULL AND c.entite.parent IS NOT NULL AND c.entite.parent.id = :dirId)" +
            ") ORDER BY c.dateReception DESC")
    List<Courrier> findVisiblePourDirectionOdc(@Param("dirId") Long dirId, @Param("statuts") Collection<StatutCourrier> statuts);

    @Query("SELECT c FROM Courrier c WHERE c.statut IN :statuts AND c.structureOrigine IS NOT NULL AND c.structureOrigine.id = :dirId " +
            "ORDER BY c.dateReception DESC")
    List<Courrier> findEnAttenteValidationOdc(@Param("dirId") Long dirId, @Param("statuts") Collection<StatutCourrier> statuts);

    List<Courrier> findByStatutInOrderByDateReceptionDesc(Collection<StatutCourrier> statuts);

    List<Courrier> findByStatutOrderByDateReceptionDesc(StatutCourrier statut);

    @Query("SELECT c FROM Courrier c ORDER BY c.dateReception DESC")
    List<Courrier> findAllOrderByDateReceptionDesc();

    List<Courrier> findByEntiteIdOrderByDateReceptionDesc(Long entiteId);

    @Query("SELECT DISTINCT c FROM Courrier c "
            + "LEFT JOIN FETCH c.entite "
            + "LEFT JOIN FETCH c.structureOrigine "
            + "LEFT JOIN FETCH c.directionInitial "
            + "WHERE c.entite.id = :eid ORDER BY c.dateReception DESC")
    List<Courrier> findPourHubDcire(@Param("eid") Long eid);

    /**
     * Vue dashboard DCIRE (division) :
     * - courriers émis par DCIRE ou par une direction fille (ODC/Fondation/RSE/DCI),
     * - courriers détenus par DCIRE / directions filles / services sous directions filles,
     * - et ceux dont la directionInitial est DCIRE ou une direction fille.
     */
    /**
     * Vue DCIRE : toutes les directions de la division (hub + ODC / Fondation / RSE / DCI) et services rattachés.
     */
    @Query("SELECT DISTINCT c FROM Courrier c "
            + "LEFT JOIN FETCH c.entite "
            + "LEFT JOIN FETCH c.structureOrigine "
            + "LEFT JOIN FETCH c.directionInitial "
            + "WHERE (c.structureOrigine IS NOT NULL AND c.structureOrigine.id IN :ids) OR "
            + "(c.entite IS NOT NULL AND c.entite.id IN :ids) OR "
            + "(c.directionInitial IS NOT NULL AND c.directionInitial.id IN :ids) OR "
            + "(c.entite IS NOT NULL AND c.entite.parent IS NOT NULL AND c.entite.parent.id IN :ids) "
            + "ORDER BY c.dateReception DESC")
    List<Courrier> findPourVueDcireDivision(@Param("ids") Collection<Long> ids);

    List<Courrier> findByDelegueResponsableOdkTrueOrderByDateReceptionDesc();

    List<Courrier> findByStructureOrigineIdOrderByDateReceptionDesc(Long structureOrigineId);

    @Query("SELECT DISTINCT c FROM Courrier c WHERE "
            + "(c.structureOrigine IS NOT NULL AND c.structureOrigine.id = :eid) OR "
            + "(c.entite IS NOT NULL AND c.entite.id = :eid) OR "
            + "(c.entite IS NOT NULL AND c.entite.parent IS NOT NULL AND c.entite.parent.id = :eid) "
            + "ORDER BY c.dateReception DESC")
    List<Courrier> findTousVisiblesPourDirection(@Param("eid") Long entiteId);

    /**
     * Trouve les courriers qui nécessitent un rappel (date limite dans 7 jours ou moins)
     */
    @Query("SELECT c FROM Courrier c WHERE c.statut NOT IN ('ARCHIVER', 'REPONDU', 'ATTENTE_VALIDATION_ODC', "
            + "'ATTENTE_VALIDATION_DIRECTEUR_ODC', 'EN_REVISION_ADMIN_COURRIER', 'ATTENTE_VALIDATION_DIRECTEUR_STRUCTURE') " +
            "AND c.rappelEnvoye = false " +
            "AND c.dateLimite <= :dateRappel " +
            "ORDER BY c.dateLimite ASC")
    List<Courrier> findCourriersPourRappel(@Param("dateRappel") LocalDate dateRappel);

    @Query("SELECT DISTINCT c FROM Courrier c "
            + "LEFT JOIN FETCH c.entite e "
            + "LEFT JOIN FETCH e.parent "
            + "LEFT JOIN FETCH c.structureOrigine so "
            + "LEFT JOIN FETCH so.parent "
            + "LEFT JOIN FETCH c.directionInitial di "
            + "LEFT JOIN FETCH di.parent "
            + "WHERE c.statut <> :arch")
    List<Courrier> findAllNonArchivedForDashboard(@Param("arch") StatutCourrier arch);
}