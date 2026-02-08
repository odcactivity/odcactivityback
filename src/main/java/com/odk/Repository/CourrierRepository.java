package com.odk.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
