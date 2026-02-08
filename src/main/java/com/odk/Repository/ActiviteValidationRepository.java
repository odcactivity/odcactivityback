/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.odk.Repository;

import com.odk.Entity.ActiviteValidation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 *
 * @author kaloga081009
 */

public interface ActiviteValidationRepository extends JpaRepository<ActiviteValidation,Long>{
    ActiviteValidation save(ActiviteValidation validation);
    Optional<ActiviteValidation> findById(Long id);
    @Query("SELECT a.id FROM ActiviteValidation a")
    List<Long> findAllQuery();
    List<ActiviteValidation> findAll();
    @Query("SELECT a FROM ActiviteValidation a WHERE a.activite.id=:activite")
    List<ActiviteValidation> findByActiviteId(@Param("activite")Long activite);
//    List<ActiviteValidation> findByActiviteById(Long activite);
}
