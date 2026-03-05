package com.odk.Repository;


import com.odk.Entity.Entite;
import com.odk.Enum.TypeEntite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntiteOdcRepository extends JpaRepository<Entite, Long> {

    @Query("SELECT COUNT(a) FROM Activite a WHERE a.entite.id = :entiteId")
    Long countActivitiesByEntiteId(@Param("entiteId") Long entiteId);

    //Toutes les directions ...
    List<Entite> findByType(TypeEntite type);


    //Services d'une direction ...
    List<Entite> findByParentId(Long parentId);

    long count();

}