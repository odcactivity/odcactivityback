package com.odk.Repository;

import com.odk.Entity.Participant;
import java.time.LocalDate;
import java.util.Date;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    @Query("SELECT u FROM Utilisateur u JOIN u.role r WHERE r.nom = 'Participant'")
    List<Participant> findParticipants();

    // Compte le nombre total d'enregistrements
    @Query("SELECT COUNT(p) FROM Participant p")
    long countTotal();

    // Compte les enregistrements de l'année en cours
    @Query("SELECT COUNT(p) FROM Participant p WHERE YEAR(p.createdAt) = :currentYear")
    long countByCurrentYear(@Param("currentYear") int currentYear);

    @Modifying
    @Query("DELETE FROM Participant p WHERE p.liste.id = :listeId")
    void deleteByListeId(@Param("listeId") Long listeId);
    
// @Query(name="SELECT p FROM Participant p,Activite a,Entite e WHERE a.entite.id=e.id and a.id=p.activite.id and (p.activite.id = :activiteId or p.activite.entite.id = :entiteId)")
//List<Participant>findParCritereCustom(@Param("dateDebut") String dateDebut,@Param("dateFin") String dateFin,@Param("activiteId") Long activiteId,@Param("entiteId") Long entiteId);

@Query("""
    SELECT p 
    FROM Participant p WHERE p.activite.entite.id= :entiteId
  """)        
List<Participant> findParCritereCustom1(
         @Param("entiteId") Long entiteId
);
@Query("""
    SELECT p 
    FROM Participant p WHERE p.activite.id = :activiteId AND p.activite.entite.id = :entiteId     
""")        
List<Participant> findParCritereCustom2(
        @Param("activiteId") Long activiteId,
        @Param("entiteId") Long entiteId
);
@Query("""
    SELECT p 
    FROM Participant p   
    WHERE p.activite.id = :activiteId AND p.activite.entite.id = :entiteId 
    AND (p.activite.dateDebut >= :dateDebut)
      
""")
        
List<Participant> findParCritereCustom3(
        @Param("dateDebut") LocalDate dateDebut,
        @Param("activiteId") Long activiteId,
        @Param("entiteId") Long entiteId
);
    @Query("""
    SELECT p 
    FROM Participant p
    JOIN p.activite a
    JOIN a.entite e
    WHERE ( a.id = :activiteId)
      AND ( e.id = :entiteId)
      AND ( a.dateDebut >= :dateDebut)
      AND ( a.dateFin <= :dateFin)
""")
        
List<Participant> findParCritereCustom(
        @Param("dateDebut") LocalDate dateDebut,
        @Param("dateFin") LocalDate dateFin,
        @Param("activiteId") Long activiteId,
        @Param("entiteId") Long entiteId
);
@Query("""
    SELECT p 
    FROM Participant p    
    WHERE (:activiteId IS NULL OR p.activite.id = :activiteId)
      AND (:entiteId IS NULL OR p.activite.entite.id = :entiteId)
      AND (:dateDebut IS NULL OR p.activite.dateDebut >= :dateDebut)
      AND (:dateFin IS NULL OR p.activite.dateFin <= :dateFin)
       AND (:etapeId IS NULL OR p.liste.etape.id <= :etapeId)
""")
List<Participant>searcDynamic(@Param("dateDebut") LocalDate dateDebut,
        @Param("dateFin") LocalDate dateFin,
        @Param("activiteId") Long activiteId,
        @Param("entiteId") Long entiteId,@Param("etapeId") Long etapeId);

//    List<Participant> findByEtapeDebut(Long etapeDebutId);
//    List<Participant> findByEtapeResultat(Long etapeResultatId);
}
