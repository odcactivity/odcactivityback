package com.odk.Repository;

import com.odk.Entity.HistoriqueSupportActivite;
import com.odk.Entity.SupportActivite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoriqueSupportActiviteRepository extends JpaRepository<HistoriqueSupportActivite, Long> {
    List<HistoriqueSupportActivite> findBySupportOrderByDateModificationDesc(SupportActivite support);
     // Récupère tous les historiques d'un support donné
    List<HistoriqueSupportActivite> findBySupportId(Long supportId);
}
