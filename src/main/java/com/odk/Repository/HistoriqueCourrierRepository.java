package com.odk.Repository;

import com.odk.Entity.HistoriqueCourrier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriqueCourrierRepository extends JpaRepository<HistoriqueCourrier,Long> {
    
    List<HistoriqueCourrier> findByCourrierId(Long courrierId); //Suivre toutes les modifications sur un courrier donné ...
    List<HistoriqueCourrier> findByEntiteId(Long entiteId);     //Toutes les actions liées à une entité ...

     // Historique complet d’un courrier
    List<HistoriqueCourrier> findByCourrierIdOrderByDateActionAsc(Long courrierId);
}
