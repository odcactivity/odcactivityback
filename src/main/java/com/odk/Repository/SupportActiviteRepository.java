
package com.odk.Repository;

import com.odk.Entity.SupportActivite;
import com.odk.Enum.StatutSupport;
import com.odk.Enum.TypeSupport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SupportActiviteRepository extends JpaRepository<SupportActivite, Long>{

  List<SupportActivite> findByType(TypeSupport type);

      // Filtrer par statut
    List<SupportActivite> findByStatut(StatutSupport statut);
    
    // Filtrer par nom contenant une chaîne
    List<SupportActivite> findByNomContainingIgnoreCase(String nom);
    
    // Filtrer par date
    List<SupportActivite> findByDateAjout(LocalDate date);
    
   // Combinaison des filtres (nom, statut, date)
    List<SupportActivite> findByNomContainingIgnoreCaseAndStatutAndDateAjout(String nom, StatutSupport statut, LocalDate date);
}