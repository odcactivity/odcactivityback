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
    List<Courrier> findByDirectionInitialAndStatut(Entite directionInitialId, StatutCourrier statut);
    List<Courrier> findByDirectionInitial(Entite directionInitialId);

    /**
     * Trouve les courriers qui nécessitent un rappel (date limite dans 7 jours ou moins)
     */
    @Query("SELECT c FROM Courrier c WHERE c.statut NOT IN ('ARCHIVER', 'REPONDU') " +
            "AND c.rappelEnvoye = false " +
            "AND c.dateLimite <= :dateRappel " +
            "ORDER BY c.dateLimite ASC")
    List<Courrier> findCourriersPourRappel(@Param("dateRappel") LocalDate dateRappel);
}